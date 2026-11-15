package com.example.changeflow.support;

import com.example.changeflow.controller.*;
import com.example.changeflow.domain.ChangeRequestService;
import com.example.changeflow.repository.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Real SQL/transactions and filter chain; no production database or test cleanup deletion. */
public abstract class ApiTestSupport {
    private static final ThreadLocal<SqliteTestSupport> INITIALIZING = new ThreadLocal<>();
    protected SqliteTestSupport db;
    protected MockMvc mvc;
    private AnnotationConfigWebApplicationContext context;
    protected static final String CONTENT = "{\"title\":\"title\",\"changeDescription\":\"description\",\"reasonForChange\":\"reason\"}";

    @BeforeEach
    void initializeApi() {
        db = new SqliteTestSupport();
        INITIALIZING.set(db);
        try {
            context = new AnnotationConfigWebApplicationContext();
            context.setServletContext(new MockServletContext());
            context.register(TestConfiguration.class, SecurityConfiguration.class, ApiExceptionHandler.class);
            context.refresh();
            mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        } finally { INITIALIZING.remove(); }
    }

    @AfterEach void closeApi() { if (context != null) { context.close(); } }

    protected MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String username) {
        return builder.with(httpBasic(username, SqliteTestSupport.PASSWORD))
                .header("X-ChangeFlow-Client", "api").contentType("application/json");
    }

    protected String tag(java.util.UUID id, long version) { return VersionPrecondition.etag(id, version); }
    protected IdentityRepository identityRepository() { return context.getBean(IdentityRepository.class); }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    static class TestConfiguration {
        @Bean DataSource dataSource() { return INITIALIZING.get().dataSource; }
        @Bean JdbcTransactionManager manager(DataSource source) { return new DataSourceConfiguration().transactionManager(source); }
        @Bean IdentityRepository identities(DataSource source) { return org.mockito.Mockito.spy(new IdentityRepository(source)); }
        @Bean ChangeRequestRepository requests(DataSource source) { return new ChangeRequestRepository(source); }
        @Bean ReturnReasonRepository reasons(DataSource source) { return new ReturnReasonRepository(source); }
        @Bean ChangeRequestService service(IdentityRepository identities, ChangeRequestRepository requests,
                ReturnReasonRepository reasons, JdbcTransactionManager manager) {
            return new ChangeRequestService(identities, requests, reasons, manager);
        }
        @Bean RequestPayloads payloads() { return new RequestPayloads(JsonMapper.builder().build()); }
        @Bean ChangeRequestController controller(ChangeRequestService service, RequestPayloads payloads) {
            return new ChangeRequestController(service, payloads);
        }
    }
}
