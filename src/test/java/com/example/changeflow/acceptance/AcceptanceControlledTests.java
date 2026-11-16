package com.example.changeflow.acceptance;

import com.example.changeflow.controller.AcceptanceTool;
import com.example.changeflow.repository.IdentityRepository;
import com.example.changeflow.support.SqliteTestSupport;
import java.lang.reflect.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real HTTPS observations from test-only spies; never an application fault endpoint. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
public class AcceptanceControlledTests {
    static final Path ROOT;
    static final char[] KEY = UUID.randomUUID().toString().toCharArray();
    static {
        try {
            String configured = System.getProperty("changeflow.acceptance.controlled-root");
            ROOT = configured == null ? AcceptanceRehearsalTests.fresh() : Path.of(configured);
            AcceptanceTool.initialize(ROOT); AcceptanceTool.tls(ROOT,KEY);
        } catch(Exception failure) { throw new ExceptionInInitializerError("Cannot prepare fresh controlled acceptance fixture"); }
    }
    @DynamicPropertySource static void settings(DynamicPropertyRegistry registry) throws Exception {
        AcceptanceTool.serverProperties(ROOT,KEY,"0").forEach((key,value)->registry.add(key,()->value));
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean IdentityRepository identities;
    @MockitoSpyBean DataSource source;

    @Test void actualTlsVerificationAndAlreadyAuthorizedRoleRevocationObservations() throws Exception {
        // The new self-signed certificate MUST fail without the explicit per-run trust anchor.
        assertThrows(javax.net.ssl.SSLHandshakeException.class, () -> HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("https://localhost:"+port+"/api/v1/requests")).GET().build(),HttpResponse.BodyHandlers.ofString()));
        try (var tcp=new java.net.Socket("127.0.0.1",port);
                var wrong=(javax.net.ssl.SSLSocket)AcceptanceTool.trustContext(ROOT).getSocketFactory().createSocket(tcp,"wrong.invalid",port,true)) {
            var parameters=wrong.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");wrong.setSSLParameters(parameters);
            assertThrows(javax.net.ssl.SSLHandshakeException.class,wrong::startHandshake);
        }
        for(String role:List.of("Requester","Reviewer")) {
            String name = role.toLowerCase()+"-controlled";
            char[] password=UUID.randomUUID().toString().toCharArray();
            try {
                AcceptanceTool.createAccount(ROOT,name,password); AcceptanceTool.changeRole(ROOT,name,role,true);
                String actor=jdbc.queryForObject("SELECT user_id FROM acceptance_account WHERE name=?",String.class,name);
                String owner=role.equals("Requester")?actor:user(); String id=UUID.randomUUID().toString();
                jdbc.update("INSERT INTO change_request VALUES (?,?,?,?,?,?,?)",id,owner,"t","d","r",role.equals("Requester")?"Draft":"PendingReview",0);
                var first=new AtomicBoolean(true);
                doAnswer(call->{Object permissions=call.callRealMethod();
                    if(first.getAndSet(false)){AcceptanceTool.changeRole(ROOT,name,role,false);} return permissions;
                }).when(identities).loadCurrentPermissions(UUID.fromString(actor));
                var current=send("GET","/api/v1/requests/"+id,name,password,null,null);
                var next=send("GET","/api/v1/requests/"+id,name,password,null,null);
                assertEquals(200,current.statusCode()); assertEquals(404,next.statusCode());
                observation(role.equals("Requester")?"R7":"V6",List.of(current,next));
            } finally { Arrays.fill(password,'\0'); }
        }
    }

    @Test void actualLostCommitAcknowledgementHttpObservations() throws Exception {
        char[] password=UUID.randomUUID().toString().toCharArray();
        try {
            AcceptanceTool.createAccount(ROOT,"ack-reviewer",password); AcceptanceTool.changeRole(ROOT,"ack-reviewer","Reviewer",true);
            String owner=user(),id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO change_request VALUES (?,?,?,?,?,?,?)",id,owner,"t","d","r","PendingReview",0);
            var armed=new AtomicBoolean(true);
            doAnswer(call->{Connection real=(Connection)call.callRealMethod();
                return Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                    try {Object result=method.invoke(real,args);
                        if(method.getName().equals("commit")&&armed.getAndSet(false)){throw new SQLException("test-only lost acknowledgement");}
                        return result;
                    }catch(InvocationTargetException failure){throw failure.getCause();}
                });
            }).when(source).getConnection();
            var failed=send("POST","/api/v1/requests/"+id+"/approve","ack-reviewer",password,"{}","\"request-"+id+"-v0\"");
            var inspected=send("GET","/api/v1/requests/"+id,"ack-reviewer",password,null,null);
            assertEquals(503,failed.statusCode()); assertEquals(200,inspected.statusCode());
            assertTrue(inspected.body().contains("Approved"));
            assertEquals(1L,jdbc.queryForObject("SELECT version FROM change_request WHERE id=?",Long.class,id));
            observation("V7",List.of(failed,inspected));
        } finally {Arrays.fill(password,'\0');}
    }

    private String user(){String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO app_user VALUES (?,?,?,1)",id,id,"!");return id;}
    private HttpResponse<String> send(String method,String path,String name,char[] password,String body,String tag)throws Exception{
        String token=Base64.getEncoder().encodeToString((name+":"+new String(password)).getBytes(StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create("https://localhost:"+port+path)).timeout(java.time.Duration.ofSeconds(15))
                .header("Authorization","Basic "+token).header("X-ChangeFlow-Client","api").header("Content-Type","application/json");
        if(tag!=null){request.header("If-Match",tag);}
        return AcceptanceTool.client(ROOT).send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private void observation(String scenario,List<HttpResponse<String>> responses)throws Exception{
        var text=new StringBuilder("# "+scenario+" automated controlled observations\n\nNot participant answers; not a human score. Captured "+java.time.Instant.now()+".\n");
        for(var response:responses){text.append("\nHTTP ").append(response.statusCode()).append("\n```json\n").append(response.body()).append("\n```\n");}
        Files.writeString(ROOT.resolve(scenario+"-observations.md"),text,StandardOpenOption.CREATE_NEW);
    }
}
