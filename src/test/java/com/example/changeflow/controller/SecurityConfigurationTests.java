package com.example.changeflow.controller;

import com.example.changeflow.repository.IdentityRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Limited web/security test context with mocked identity storage; no Boot/datasource/server startup. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityConfigurationTests {
    private final UUID id = UUID.randomUUID();
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private IdentityRepository identities;
    private String encoded;

    @BeforeAll
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(SecurityConfiguration.class, TestConfiguration.class, ApiExceptionHandler.class);
        context.refresh();
        identities = context.getBean(IdentityRepository.class);
        encoded = context.getBean(PasswordEncoder.class).encode("fixture-password");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterAll void close() { context.close(); }

    @BeforeEach
    void user() {
        reset(identities);
        var identity = mock(IdentityRepository.AuthenticationIdentity.class);
        when(identity.userId()).thenReturn(id);
        when(identity.passwordHash()).thenReturn(encoded);
        when(identity.enabled()).thenReturn(true);
        when(identities.findForAuthentication("fixture-user")).thenReturn(Optional.of(identity));
    }

    @Test
    void validBasicHasOnlyVerifiedIdAndNoCredentialsRolesOrSession() throws Exception {
        var result = mvc.perform(get("/probe").with(httpBasic("fixture-user", "fixture-password")))
                .andExpect(status().isOk()).andExpect(content().string(id.toString()))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertNull(result.getRequest().getSession(false));
        verify(identities).findForAuthentication("fixture-user");
        verify(identities, never()).loadCurrentPermissions(any());
    }

    @Test
    void identityIsRereadForEachRequestAndPermissionRolesAreNotCached() throws Exception {
        mvc.perform(get("/probe").with(httpBasic("fixture-user", "fixture-password"))).andExpect(status().isOk());
        when(identities.findForAuthentication("fixture-user")).thenReturn(Optional.empty());
        mvc.perform(get("/probe").with(httpBasic("fixture-user", "fixture-password"))).andExpect(status().isUnauthorized());
        verify(identities, times(2)).findForAuthentication("fixture-user");
    }

    @Test
    void missingInvalidAndDisabledIdentityHaveTheExplicitChallenge() throws Exception {
        mvc.perform(get("/probe")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"ChangeFlow\""));
        mvc.perform(get("/probe").with(httpBasic("fixture-user", "wrong"))).andExpect(status().isUnauthorized());
        var disabled = mock(IdentityRepository.AuthenticationIdentity.class);
        when(disabled.passwordHash()).thenReturn(encoded);
        when(disabled.enabled()).thenReturn(false);
        when(identities.findForAuthentication("fixture-user")).thenReturn(Optional.of(disabled));
        mvc.perform(get("/probe").with(httpBasic("fixture-user", "fixture-password")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unsafeRequestsRequireExactlyOneCorrectHeaderAfterAuthentication() throws Exception {
        mvc.perform(post("/probe")).andExpect(status().isUnauthorized());
        mvc.perform(post("/probe").with(httpBasic("fixture-user", "fixture-password")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CLIENT_HEADER_REQUIRED"));
        mvc.perform(post("/probe").with(httpBasic("fixture-user", "fixture-password")).header("X-ChangeFlow-Client", "wrong"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/probe").with(httpBasic("fixture-user", "fixture-password")).header("X-ChangeFlow-Client", "api", "api"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/probe").with(httpBasic("fixture-user", "fixture-password")).header("X-ChangeFlow-Client", "api"))
                .andExpect(status().isOk());
    }

    @Test
    void deniesCrossOriginSimpleAndPreflightRequests() throws Exception {
        mvc.perform(get("/probe").with(httpBasic("fixture-user", "fixture-password")).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(options("/probe").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "X-ChangeFlow-Client"))
                .andExpect(status().isForbidden());
    }

    @Test
    void untrustedForwardedHeadersCannotBypassTransportOrSupplyIdentity() throws Exception {
        mvc.perform(get("/probe").with(request -> { request.setRemoteAddr("192.0.2.5"); return request; })
                .header("X-Forwarded-Proto", "https").header("Forwarded", "proto=https")
                .with(httpBasic("fixture-user", "fixture-password"))).andExpect(status().isForbidden());
        verifyNoInteractions(identities);
        mvc.perform(get("/probe").header("X-User-Id", id.toString()).header("X-Forwarded-User", "fixture-user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void servletSecureFlagAllowsRemoteButDoesNotProveRealTls() throws Exception {
        mvc.perform(get("/probe").secure(true)
                .with(request -> { request.setRemoteAddr("192.0.2.5"); return request; })
                .with(httpBasic("fixture-user", "fixture-password"))).andExpect(status().isOk());
    }

    @Test
    void cookieAndSessionSecurityContextCannotAuthenticate() throws Exception {
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(id, null, java.util.List.of())));
        mvc.perform(get("/probe").session(session).cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "fixture")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storageFailureIsSafe503RatherThanFalseInvalidPassword() throws Exception {
        when(identities.findForAuthentication("fixture-user"))
                .thenThrow(new DataAccessResourceFailureException("secret-fixture"));
        var result = mvc.perform(get("/probe").with(httpBasic("fixture-user", "fixture-password")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("secret-fixture"));
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    static class TestConfiguration {
        @Bean IdentityRepository identities() { return mock(IdentityRepository.class); }
        @Bean Probe probe() { return new Probe(); }
    }

    @org.springframework.boot.test.context.TestComponent
    @RestController
    static class Probe {
        @RequestMapping(value = "/probe", method = {RequestMethod.GET, RequestMethod.POST})
        String probe(Authentication authentication) {
            assertNull(authentication.getCredentials());
            assertTrue(authentication.getAuthorities().isEmpty());
            return authentication.getPrincipal().toString();
        }
    }
}
