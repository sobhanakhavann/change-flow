package com.example.changeflow;

import com.example.changeflow.support.SqliteTestSupport;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** Real loopback HTTP, Boot security and file SQLite; does not establish production TLS. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpWorkflowTests {
    private static final java.nio.file.Path FILE = SqliteTestSupport.newFile();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { registry.add("changeflow.database.path", FILE::toString); }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test void invalidUtf8NeverMutatesReadableRequest() throws Exception {
        String user = seed("Requester");
        var created = send("POST", "/api/v1/requests", user, "{}", null);
        String path = created.headers().firstValue("Location").orElseThrow();
        String tag = created.headers().firstValue("ETag").orElseThrow();
        var before = jdbc.queryForList("SELECT * FROM change_request ORDER BY id");
        for (byte[] invalid : new byte[][]{{(byte)0xC3, 0x28}, {(byte)0xC3}, {(byte)0xC0, (byte)0xAF},
                {(byte)0xED, (byte)0xA0, (byte)0x80}, {(byte)0xF4, (byte)0x90, (byte)0x80, (byte)0x80}}) {
            var response = sendBytes("PUT", path, user, jsonBytes("title", invalid), tag);
            assertEquals(400, response.statusCode(), "malformed bytes must not become accepted replacement characters");
            assertTrue(response.body().contains("INVALID_INPUT"));
            assertEquals(before, jdbc.queryForList("SELECT * FROM change_request ORDER BY id"));
        }
        for (String escape : new String[]{"\\ud800", "\\udc00", "\\u0000"}) {
            assertEquals(400, send("PUT", path, user, "{\"title\":\"" + escape + "\"}", tag).statusCode());
            assertEquals(before, jdbc.queryForList("SELECT * FROM change_request ORDER BY id"));
        }
    }

    @Test void invalidUtf8CreateAndReturnAreRejected() throws Exception {
        String owner = seed("Requester"); String reviewer = seed("Reviewer");
        byte[] invalid = {(byte)0xED, (byte)0xA0, (byte)0x80};
        int count = jdbc.queryForObject("SELECT count(*) FROM change_request", Integer.class);
        assertEquals(400, sendBytes("POST", "/api/v1/requests", owner, jsonBytes("title", invalid), null).statusCode());
        assertEquals(count, jdbc.queryForObject("SELECT count(*) FROM change_request", Integer.class));
        var created = send("POST", "/api/v1/requests", owner, "{\"title\":\"t\",\"changeDescription\":\"d\",\"reasonForChange\":\"r\"}", null);
        String path = created.headers().firstValue("Location").orElseThrow();
        assertEquals(204, send("POST", path + "/submit", owner, "{}", created.headers().firstValue("ETag").orElseThrow()).statusCode());
        String tag = send("GET", path, reviewer, null, null).headers().firstValue("ETag").orElseThrow();
        var before = jdbc.queryForList("SELECT * FROM change_request ORDER BY id");
        var feedback = jdbc.queryForList("SELECT * FROM return_reason ORDER BY request_id,request_version");
        assertEquals(400, sendBytes("POST", path + "/request-changes", reviewer, jsonBytes("reason", invalid), tag).statusCode());
        assertEquals(before, jdbc.queryForList("SELECT * FROM change_request ORDER BY id"));
        assertEquals(feedback, jdbc.queryForList("SELECT * FROM return_reason ORDER BY request_id,request_version"));
    }

    @Test void malformedBytesKeepConcealmentAndExplicitReplacementScalarIsValid() throws Exception {
        String owner = seed("Requester"); String other = seed("Requester");
        String title = "\uD83D\uDE00".repeat(197) + "e\u0301\uFFFD"; // exactly 200 scalars
        var created = send("POST", "/api/v1/requests", owner, "{\"title\":\"" + title + "\"}", null);
        assertEquals(201, created.statusCode());
        String path = created.headers().firstValue("Location").orElseThrow();
        String tag = created.headers().firstValue("ETag").orElseThrow();
        assertEquals(title, jdbc.queryForObject("SELECT title FROM change_request WHERE id=?", String.class, path.substring(path.lastIndexOf('/') + 1)));
        assertEquals(400, send("PUT", path, owner, "{\"title\":\"" + title + "x\"}", tag).statusCode());
        byte[] invalid = jsonBytes("title", new byte[]{(byte)0xC3});
        var hidden = sendBytes("PUT", path, other, invalid, "invalid-tag");
        var missing = sendBytes("PUT", "/api/v1/requests/" + UUID.randomUUID(), other, invalid, "invalid-tag");
        assertEquals(404, hidden.statusCode()); assertEquals(404, missing.statusCode());
        assertEquals(hidden.body(), missing.body());
        assertEquals(hidden.headers().firstValue("Cache-Control"), missing.headers().firstValue("Cache-Control"));
        assertTrue(hidden.headers().firstValue("ETag").isEmpty());
    }

    private String seed(String role) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO app_user VALUES (?,?,?,1)", id, id, "{bcrypt}" + new BCryptPasswordEncoder().encode(SqliteTestSupport.PASSWORD));
        jdbc.update("INSERT INTO user_role VALUES (?,?)", id, role);
        return id;
    }

    private byte[] jsonBytes(String field, byte[] value) {
        var bytes = new java.io.ByteArrayOutputStream();
        bytes.writeBytes(("{\"" + field + "\":\"").getBytes(StandardCharsets.UTF_8));
        bytes.writeBytes(value); bytes.writeBytes("\"}".getBytes(StandardCharsets.UTF_8));
        return bytes.toByteArray();
    }

    @Test void completeJourneyOverActualHttp() throws Exception {
        for (String name : new String[]{"http-owner", "http-reviewer"}) {
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO app_user VALUES (?,?,?,1)", id, name, "{bcrypt}" + new BCryptPasswordEncoder().encode(SqliteTestSupport.PASSWORD));
            jdbc.update("INSERT INTO user_role VALUES (?,?)", id, name.endsWith("owner") ? "Requester" : "Reviewer");
        }
        String content = "{\"title\":\"title\",\"changeDescription\":\"description\",\"reasonForChange\":\"reason\"}";
        var created = send("POST", "/api/v1/requests", "http-owner", content, null);
        assertEquals(201, created.statusCode());
        String path = created.headers().firstValue("Location").orElseThrow();
        String tag = created.headers().firstValue("ETag").orElseThrow();
        assertEquals(204, send("POST", path + "/submit", "http-owner", "{}", tag).statusCode());
        tag = send("GET", path, "http-reviewer", null, null).headers().firstValue("ETag").orElseThrow();
        assertEquals(204, send("POST", path + "/request-changes", "http-reviewer", "{\"reason\":\"revise\"}", tag).statusCode());
        assertEquals(404, send("GET", path, "http-reviewer", null, null).statusCode());
        tag = send("GET", path, "http-owner", null, null).headers().firstValue("ETag").orElseThrow();
        var edited = send("PUT", path, "http-owner", content, tag);
        assertEquals(200, edited.statusCode());
        assertEquals(204, send("POST", path + "/submit", "http-owner", "{}", edited.headers().firstValue("ETag").orElseThrow()).statusCode());
        tag = send("GET", path, "http-reviewer", null, null).headers().firstValue("ETag").orElseThrow();
        assertEquals(204, send("POST", path + "/approve", "http-reviewer", "{}", tag).statusCode());
        assertEquals(412, send("POST", path + "/approve", "http-reviewer", "{}", tag).statusCode());
        assertTrue(send("GET", path, "http-owner", null, null).body().contains("Approved"));
    }

    private HttpResponse<String> send(String method, String path, String user, String body, String tag) throws Exception {
        return sendBytes(method, path, user, body == null ? null : body.getBytes(StandardCharsets.UTF_8), tag);
    }

    private HttpResponse<String> sendBytes(String method, String path, String user, byte[] body, String tag) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(java.time.Duration.ofSeconds(10))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":" + SqliteTestSupport.PASSWORD).getBytes(StandardCharsets.UTF_8)))
                .header("X-ChangeFlow-Client", "api").header("Content-Type", "application/json");
        if (tag != null) { builder.header("If-Match", tag); }
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
