package com.example.changeflow.controller;

import com.example.changeflow.ChangeFlowApplication;
import com.example.changeflow.domain.*;
import com.example.changeflow.repository.*;
import java.io.Console;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.util.*;
import javax.net.ssl.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/** Local facilitator CLI. Test sources only; no public HTTP account/fault API. */
public final class AcceptanceTool {
    private AcceptanceTool() { }

    public static void main(String[] args) {
        try {
            if (args.length < 2) { throw new IllegalArgumentException(); }
            String command = args[0]; Path root = Path.of(args[1]).toAbsolutePath().normalize();
            switch (command) {
                case "init" -> initialize(root);
                case "create" -> {
                    if (args.length != 3) { throw new IllegalArgumentException(); }
                    char[] password = password("New account password: ");
                    char[] confirmation = null;
                    try {
                        confirmation = password("Repeat password: ");
                        if (!Arrays.equals(password, confirmation)) { throw new IllegalArgumentException(); }
                        createAccount(root, args[2], password);
                    } finally { Arrays.fill(password, '\0'); if(confirmation!=null){Arrays.fill(confirmation, '\0');} }
                }
                case "grant", "revoke" -> {
                    if (args.length != 4) { throw new IllegalArgumentException(); }
                    changeRole(root, args[2], args[3], command.equals("grant"));
                }
                case "list" -> System.out.println(jdbc(root).queryForList("SELECT a.name,r.role FROM acceptance_account a LEFT JOIN user_role r ON a.user_id=r.user_id ORDER BY a.name,r.role"));
                case "seed" -> { if (args.length != 4) { throw new IllegalArgumentException(); } seed(root, args[2], args[3]); }
                case "tls" -> {
                    char[] secret = password("New PKCS12 password: "); char[] repeat = null;
                    try { repeat=password("Repeat password: ");if (!Arrays.equals(secret, repeat)) { throw new IllegalArgumentException(); } tls(root, secret); }
                    finally { Arrays.fill(secret, '\0'); if(repeat!=null){Arrays.fill(repeat, '\0');} }
                }
                case "serve" -> {
                    char[] secret = password("PKCS12 password: ");
                    try {
                        var context = startServer(root,secret,"8443");
                        context.registerShutdownHook();
                        System.out.println("Acceptance HTTPS: https://localhost:8443/api/v1/requests");
                        new java.util.concurrent.CountDownLatch(1).await();
                    } finally { Arrays.fill(secret, '\0'); }
                }
                case "verify" -> {
                    var response = client(root).send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://localhost:8443/api/v1/requests")).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 401 || response.headers().firstValue("WWW-Authenticate").isEmpty()) { throw new IllegalStateException(); }
                    System.out.println("TLS chain/hostname verified with this run's certificate; Basic challenge received. No human acceptance recorded.");
                }
                case "controlled" -> {
                    open(root);
                    Path run = root.resolve("controlled-" + UUID.randomUUID());
                    System.setProperty("changeflow.acceptance.controlled-root", run.toString());
                    var summary = new SummaryGeneratingListener();
                    LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("com.example.changeflow.acceptance.AcceptanceControlledTests")).build(), summary);
                    if (summary.getSummary().getTestsFailedCount() != 0 || summary.getSummary().getTestsSucceededCount() != 2) { throw new IllegalStateException(); }
                    System.out.println("Automated controlled observations (not human answers): " + run);
                }
                default -> throw new IllegalArgumentException();
            }
        } catch (Exception failure) {
            System.err.println("Acceptance preparation failed safely. Check command, private run directory, prerequisites and interactive terminal. No human score recorded.");
            System.exit(2);
        }
    }

    private static char[] password(String prompt) {
        Console console = System.console();
        if (console == null) { throw new IllegalStateException("A real terminal is required; password arguments/stdin files are unsupported"); }
        char[] result = console.readPassword(prompt);
        if (result == null || result.length < 12) { if (result != null) { Arrays.fill(result, '\0'); } throw new IllegalArgumentException(); }
        return result;
    }

    // Explicit imports avoid scanning other test fixtures/probe controllers on the test classpath.
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.context.annotation.Import({DataSourceConfiguration.class,SecurityConfiguration.class,
            ChangeRequestController.class,RequestPayloads.class,ApiExceptionHandler.class,ChangeRequestService.class,
            IdentityRepository.class,ChangeRequestRepository.class,ReturnReasonRepository.class})
    public static class LocalServer { }

    public static org.springframework.context.ConfigurableApplicationContext startServer(Path root,char[] secret,String port) throws Exception {
        var properties=serverProperties(root,secret,port);
        return new SpringApplicationBuilder(LocalServer.class).initializers(application -> application.getEnvironment().getPropertySources()
                .addFirst(new org.springframework.core.env.MapPropertySource("locked-acceptance-settings",properties))).run();
    }

    /** Refuses every pre-existing directory; never resets/baselines an existing database. */
    public static void initialize(Path root) throws Exception {
        root = root.toAbsolutePath().normalize();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) || root.getParent() == null || !Files.isDirectory(root.getParent())) { throw new IllegalArgumentException(); }
        Files.createDirectory(root);
        var acl = Files.getFileAttributeView(root, AclFileAttributeView.class);
        if (acl != null) {
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(root))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).setFlags(AclEntryFlag.DIRECTORY_INHERIT,AclEntryFlag.FILE_INHERIT).build()));
        } else { Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------")); }
        String token = UUID.randomUUID().toString();
        Files.writeString(root.resolve(".acceptance-run"), token, StandardOpenOption.CREATE_NEW);
        var source = new DataSourceConfiguration().dataSource(root.resolve("acceptance.sqlite").toString(), 5000);
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").cleanDisabled(true).baselineOnMigrate(false).load();
        new DataSourceConfiguration().sqliteMigrationStrategy(source).migrate(flyway);
        var sql = new JdbcTemplate(source);
        sql.execute("CREATE TABLE acceptance_guard(token TEXT NOT NULL PRIMARY KEY) STRICT");
        sql.update("INSERT INTO acceptance_guard VALUES (?)", token);
        sql.execute("CREATE TABLE acceptance_account(name TEXT NOT NULL PRIMARY KEY,user_id TEXT NOT NULL UNIQUE REFERENCES app_user(id)) STRICT");
        sql.execute("CREATE TABLE acceptance_event(id TEXT NOT NULL PRIMARY KEY,action TEXT NOT NULL,user_id TEXT NOT NULL,role TEXT NOT NULL) STRICT");
        sql.execute("CREATE TABLE acceptance_fixture(label TEXT NOT NULL PRIMARY KEY,request_id TEXT NOT NULL) STRICT");
    }

    public static DataSource open(Path root) throws Exception {
        root = root.toAbsolutePath().normalize();
        if (!root.equals(root.toRealPath()) || !Files.isRegularFile(root.resolve(".acceptance-run"), LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(root.resolve("acceptance.sqlite"), LinkOption.NOFOLLOW_LINKS)) { throw new IllegalArgumentException(); }
        var source = new DataSourceConfiguration().dataSource(root.resolve("acceptance.sqlite").toString(), 5000);
        String guard = new JdbcTemplate(source).queryForObject("SELECT token FROM acceptance_guard", String.class);
        if (!Objects.equals(guard, Files.readString(root.resolve(".acceptance-run")))) { throw new IllegalArgumentException(); }
        return source;
    }

    public static JdbcTemplate jdbc(Path root) throws Exception { return new JdbcTemplate(open(root)); }

    public static void createAccount(Path root, String name, char[] password) throws Exception {
        if (!name.matches("[a-z][a-z0-9_-]{0,63}") || password.length < 12) { throw new IllegalArgumentException(); }
        var source = open(root);
        String id = UUID.randomUUID().toString();
        // Calls the real application encoder factory; never print/serialize credential material.
        String hash = new SecurityConfiguration().passwordEncoder().encode(new String(password));
        new org.springframework.transaction.support.TransactionTemplate(new DataSourceConfiguration().transactionManager(source)).execute(status -> {
            var sql = new JdbcTemplate(source);
            sql.update("INSERT INTO app_user VALUES (?,?,?,1)", id, name, hash);
            sql.update("INSERT INTO acceptance_account VALUES (?,?)", name, id);
            return null;
        });
    }

    public static void changeRole(Path root, String name, String role, boolean grant) throws Exception {
        ActorPermissions.Role.valueOf(role);
        var source = open(root);
        new org.springframework.transaction.support.TransactionTemplate(new DataSourceConfiguration().transactionManager(source)).execute(status -> {
            var sql = new JdbcTemplate(source);
            String id = sql.queryForObject("SELECT user_id FROM acceptance_account WHERE name=?", String.class, name);
            if (grant) { sql.update("INSERT INTO user_role VALUES (?,?) ON CONFLICT DO NOTHING", id, role); }
            else if (sql.queryForObject("SELECT count(*) FROM user_role WHERE user_id=? AND role=?", Integer.class, id, role) != 0) {
                // Preserve rows: move the grant to a disabled, unusable test-only sink account.
                String sink = UUID.randomUUID().toString();
                sql.update("INSERT INTO app_user VALUES (?,?,?,0)", sink, "revoked-" + sink, "!");
                sql.update("UPDATE user_role SET user_id=? WHERE user_id=? AND role=?", sink, id, role);
            }
            sql.update("INSERT INTO acceptance_event VALUES (?,?,?,?)", UUID.randomUUID().toString(), grant ? "grant" : "revoke", id, role);
            return null;
        });
    }

    public static void seed(Path root, String requester, String reviewer) throws Exception {
        var source = open(root); var sql = new JdbcTemplate(source);
        if (sql.queryForObject("SELECT count(*) FROM acceptance_fixture", Integer.class) != 0) { throw new IllegalStateException(); }
        sql.update("INSERT INTO acceptance_fixture VALUES ('seed-started','not-a-resource')");
        UUID owner = UUID.fromString(sql.queryForObject("SELECT user_id FROM acceptance_account WHERE name=?", String.class, requester));
        UUID judge = UUID.fromString(sql.queryForObject("SELECT user_id FROM acceptance_account WHERE name=?", String.class, reviewer));
        if (sql.queryForObject("SELECT count(*) FROM user_role WHERE user_id=? AND role='Requester'", Integer.class, judge.toString()) != 0) { throw new IllegalStateException(); }
        var service = new ChangeRequestService(new IdentityRepository(source),new ChangeRequestRepository(source),new ReturnReasonRepository(source),new DataSourceConfiguration().transactionManager(source));
        var complete = new ChangeRequestRules.Content("Evaluation proposal", "Evaluation description", "Evaluation reason");
        for (String label : List.of("race", "approved", "review", "revision", "revocation", "returned-sample")) {
            var request = service.create(owner, () -> complete).request();
            if (!label.equals("race")) {
                service.mutate(owner, request.id(), ChangeRequestService.Action.SUBMIT, () -> null, () -> null, () -> new ExpectedVersion(request.id(),0));
                if (label.equals("approved")) { service.mutate(judge, request.id(), ChangeRequestService.Action.APPROVE, () -> null, () -> null, () -> new ExpectedVersion(request.id(),1)); }
                if (label.equals("returned-sample")) { service.mutate(judge, request.id(), ChangeRequestService.Action.REQUEST_CHANGES, () -> null, () -> "Seed feedback", () -> new ExpectedVersion(request.id(),1)); }
            }
            sql.update("INSERT INTO acceptance_fixture VALUES (?,?)", label, request.id().toString());
        }
        changeRole(root, reviewer, "Requester", true);
        try {
            for (String label : List.of("private", "self-review")) {
                var request = service.create(judge, () -> complete).request();
                if (label.equals("self-review")) { service.mutate(judge, request.id(), ChangeRequestService.Action.SUBMIT, () -> null, () -> null, () -> new ExpectedVersion(request.id(),0)); }
                sql.update("INSERT INTO acceptance_fixture VALUES (?,?)", label, request.id().toString());
            }
        } finally { changeRole(root, reviewer, "Requester", false); }
        var bodies = Map.of("empty.json","{}", "incomplete.json","{\"title\":\"\"}",
                "content.json","{\"title\":\"Evaluation title\",\"changeDescription\":\"Evaluation description\",\"reasonForChange\":\"Evaluation reason\"}",
                "edit-a.json","{\"title\":\"Edit A\",\"changeDescription\":\"A description\",\"reasonForChange\":\"A reason\"}",
                "edit-b.json","{\"title\":\"Edit B\",\"changeDescription\":\"B description\",\"reasonForChange\":\"B reason\"}",
                "return.json","{\"reason\":\"Please revise\"}");
        for (var body:bodies.entrySet()) { Files.writeString(kit(root).resolve(body.getKey()),body.getValue(),StandardOpenOption.CREATE_NEW); }
        var manifest = sql.queryForList("SELECT label,request_id FROM acceptance_fixture WHERE label<>'seed-started' ORDER BY label");
        Files.writeString(root.resolve("facilitator-fixtures.json"),tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(manifest),StandardOpenOption.CREATE_NEW);
        System.out.println(sql.queryForList("SELECT label,request_id FROM acceptance_fixture WHERE label<>'seed-started' ORDER BY label"));
    }

    public static void tls(Path root, char[] secret) throws Exception {
        open(root);
        if (secret.length < 12 || Files.exists(root.resolve("tls.p12")) || Files.exists(root.resolve("tls.crt"))) { throw new IllegalArgumentException(); }
        keytool(root, secret, "-genkeypair", "-alias", "acceptance", "-keyalg", "RSA", "-keysize", "3072", "-validity", "14",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12");
        keytool(root, secret, "-exportcert", "-rfc", "-alias", "acceptance", "-file", root.resolve("tls.crt").toString());
        Files.copy(root.resolve("tls.crt"),kit(root).resolve("tls.crt"));
    }

    private static Path kit(Path root) throws Exception {
        Path directory=root.resolve("participant-kit");
        if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS)){Files.createDirectory(directory);}
        if(Files.isSymbolicLink(directory)||!directory.toRealPath().startsWith(root.toRealPath())){throw new IllegalArgumentException();}
        return directory;
    }

    private static void keytool(Path root, char[] secret, String... arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString());
        command.addAll(List.of(arguments)); command.addAll(List.of("-keystore",root.resolve("tls.p12").toString(),"-storepass:env","CHANGEFLOW_EVAL_KEY_PASSWORD"));
        var builder = new ProcessBuilder(command).inheritIO();
        builder.environment().put("CHANGEFLOW_EVAL_KEY_PASSWORD",new String(secret));
        try { if (builder.start().waitFor() != 0) { throw new IllegalStateException(); } }
        finally { builder.environment().remove("CHANGEFLOW_EVAL_KEY_PASSWORD"); }
    }

    public static Map<String,Object> serverProperties(Path root, char[] secret, String port) throws Exception {
        open(root);
        var properties = new HashMap<String,Object>();
        properties.put("changeflow.database.path",root.resolve("acceptance.sqlite").toAbsolutePath().toString());
        properties.put("server.address","127.0.0.1"); properties.put("server.port",port);
        properties.put("server.ssl.enabled",true); properties.put("server.ssl.key-store",root.resolve("tls.p12").toUri().toString());
        properties.put("server.ssl.key-store-type","PKCS12"); properties.put("server.ssl.key-alias","acceptance");
        properties.put("server.ssl.key-store-password",new String(secret));
        return properties;
    }

    public static java.net.http.HttpClient client(Path root) throws Exception {
        var parameters = new SSLParameters(); parameters.setEndpointIdentificationAlgorithm("HTTPS");
        return java.net.http.HttpClient.newBuilder().sslContext(trustContext(root)).sslParameters(parameters)
                .connectTimeout(java.time.Duration.ofSeconds(10)).build();
    }

    public static SSLContext trustContext(Path root) throws Exception {
        open(root);
        var trust = KeyStore.getInstance(KeyStore.getDefaultType()); trust.load(null,null);
        try (var input = Files.newInputStream(root.resolve("tls.crt"))) { trust.setCertificateEntry("acceptance",CertificateFactory.getInstance("X.509").generateCertificate(input)); }
        var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); factory.init(trust);
        var ssl = SSLContext.getInstance("TLS"); ssl.init(null,factory.getTrustManagers(),null);
        return ssl;
    }
}
