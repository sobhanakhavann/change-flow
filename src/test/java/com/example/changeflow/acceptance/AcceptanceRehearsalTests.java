package com.example.changeflow.acceptance;

import com.example.changeflow.controller.AcceptanceTool;
import com.example.changeflow.controller.SecurityConfiguration;
import com.example.changeflow.repository.IdentityRepository;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceRehearsalTests {
    @Test void actualToolServerHasVerifiedHttpsRealBasicAndNoTestProbeRoutes() throws Exception {
        Path root=fresh();AcceptanceTool.initialize(root);
        char[] secret=UUID.randomUUID().toString().toCharArray();
        try {
            AcceptanceTool.createAccount(root,"requester-eval",secret);AcceptanceTool.changeRole(root,"requester-eval","Requester",true);
            AcceptanceTool.tls(root,secret);
            try(var context=AcceptanceTool.startServer(root,secret,"0")) {
                int port=((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
                String auth="Basic "+Base64.getEncoder().encodeToString(("requester-eval:"+new String(secret)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var client=AcceptanceTool.client(root);
                var list=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://localhost:"+port+"/api/v1/requests"))
                        .header("Authorization",auth).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200,list.statusCode());
                if(System.getProperty("os.name").startsWith("Windows")) {
                    var curl=new ProcessBuilder(Path.of(System.getenv("SystemRoot"),"System32","curl.exe").toString(),
                            "--silent","--show-error","--cacert",root.resolve("tls.crt").toString(),"--write-out","\nSTATUS=%{http_code}",
                            "https://localhost:"+port+"/api/v1/requests").redirectErrorStream(true).start();
                    String result=new String(curl.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                    assertEquals(0,curl.waitFor(),"curl must validate the explicit per-run certificate");
                    assertTrue(result.contains("STATUS=401"));
                }
                var probe=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://localhost:"+port+"/probe"))
                        .header("Authorization",auth).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(404,probe.statusCode());
                var mapping=context.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
                assertFalse(mapping.getHandlerMethods().keySet().toString().contains("/probe"));
            }
        } finally {Arrays.fill(secret,'\0');}
    }
    @Test void freshGuardedDatabaseAccountsActualEncoderRolesFixturesAndRefusalOfReuse() throws Exception {
        Path root = fresh(); AcceptanceTool.initialize(root);
        char[] secret = UUID.randomUUID().toString().toCharArray();
        try {
            AcceptanceTool.createAccount(root,"requester-eval",secret);
            AcceptanceTool.createAccount(root,"reviewer-eval",secret);
            var identity = new IdentityRepository(AcceptanceTool.open(root)).findForAuthentication("requester-eval").orElseThrow();
            assertTrue(identity.enabled());
            // Actual stored hash stays in process; assertions never render it.
            var encoderMethod = SecurityConfiguration.class.getDeclaredMethod("passwordEncoder"); encoderMethod.setAccessible(true);
            var encoder = (org.springframework.security.crypto.password.PasswordEncoder)encoderMethod.invoke(new SecurityConfiguration());
            assertTrue(encoder.matches(new String(secret),identity.passwordHash()));
            assertThrows(Exception.class, () -> AcceptanceTool.createAccount(root,"requester-eval",secret));
            AcceptanceTool.changeRole(root,"requester-eval","Requester",true);
            AcceptanceTool.changeRole(root,"reviewer-eval","Reviewer",true);
            AcceptanceTool.seed(root,"requester-eval","reviewer-eval");
            var sql = AcceptanceTool.jdbc(root);
            assertEquals(8,sql.queryForObject("SELECT count(*) FROM change_request",Integer.class));
            assertEquals(1,sql.queryForObject("SELECT count(*) FROM return_reason",Integer.class));
            int accounts = sql.queryForObject("SELECT count(*) FROM app_user",Integer.class);
            AcceptanceTool.changeRole(root,"requester-eval","Requester",false);
            assertEquals(0,sql.queryForObject("SELECT count(*) FROM user_role WHERE user_id=?",Integer.class,identity.userId().toString()));
            assertTrue(new IdentityRepository(AcceptanceTool.open(root)).findForAuthentication("requester-eval").orElseThrow().enabled());
            assertEquals(accounts+1,sql.queryForObject("SELECT count(*) FROM app_user",Integer.class));
            assertThrows(Exception.class, () -> AcceptanceTool.initialize(root));
            assertThrows(Exception.class, () -> AcceptanceTool.seed(root,"requester-eval","reviewer-eval"));
            assertEquals(8,sql.queryForObject("SELECT count(*) FROM change_request",Integer.class));
        } finally { Arrays.fill(secret,'\0'); }
        var unguarded = fresh(); Files.createDirectory(unguarded);
        assertThrows(Exception.class, () -> AcceptanceTool.open(unguarded));
    }

    public static Path fresh() throws Exception {
        var parent = Path.of("build","acceptance-rehearsals").toAbsolutePath(); Files.createDirectories(parent);
        return parent.resolve(UUID.randomUUID().toString());
    }
}
