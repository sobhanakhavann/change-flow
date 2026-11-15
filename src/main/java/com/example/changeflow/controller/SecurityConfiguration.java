package com.example.changeflow.controller;

import com.example.changeflow.repository.IdentityRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
    }

    @Bean
    AuthenticationProvider identityAuthenticationProvider(IdentityRepository identities, PasswordEncoder encoder) {
        String dummyHash = encoder.encode("unused-account-timing-check");
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication input) {
                try {
                    var identity = identities.findForAuthentication(input.getName());
                    String hash = identity.map(IdentityRepository.AuthenticationIdentity::passwordHash).orElse(dummyHash);
                    boolean matches;
                    try { matches = encoder.matches(String.valueOf(input.getCredentials()), hash); }
                    catch (IllegalArgumentException invalidHash) { matches = false; }
                    if (identity.isEmpty() || !matches || !identity.get().enabled()) {
                        throw new BadCredentialsException("Invalid credentials");
                    }
                    // No password/hash/role claims in the principal. Current roles remain a service lookup.
                    return UsernamePasswordAuthenticationToken.authenticated(identity.get().userId(), null, List.of());
                } catch (DataAccessException storage) {
                    throw new AuthenticationServiceException("Identity storage unavailable");
                }
            }

            @Override
            public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
        };
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, AuthenticationProvider provider) throws Exception {
        var entryPoint = (org.springframework.security.web.AuthenticationEntryPoint) (request, response, failure) ->
                ApiExceptionHandler.write(response, failure instanceof AuthenticationServiceException
                        ? ApiExceptionHandler.Code.SERVICE_UNAVAILABLE : ApiExceptionHandler.Code.UNAUTHENTICATED);
        return http.authenticationManager(new ProviderManager(provider))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(form -> form.disable()).logout(logout -> logout.disable())
                .rememberMe(remember -> remember.disable())
                .headers(headers -> headers.cacheControl(cache -> cache.disable()))
                // Replaced by mandatory non-simple header AND cross-origin denial below, not by statelessness.
                .csrf(csrf -> csrf.disable()).cors(cors -> cors.disable())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, failure) ->
                                ApiExceptionHandler.write(response, ApiExceptionHandler.Code.PERMISSION_DENIED)))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(new TransportBoundaryFilter(), BasicAuthenticationFilter.class)
                .addFilterAfter(new ApiClientHeaderFilter(), BasicAuthenticationFilter.class)
                .build();
    }

    /** Trust servlet transport only; arbitrary forwarded/identity headers never establish trust. */
    static final class TransportBoundaryFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            response.setHeader("Cache-Control", "no-store");
            String remote = request.getRemoteAddr();
            boolean loopback = "127.0.0.1".equals(remote) || "::1".equals(remote)
                    || "0:0:0:0:0:0:0:1".equals(remote);
            String origin = request.getHeader("Origin");
            String expected = request.getScheme() + "://" + request.getServerName()
                    + ((request.getServerPort() == 443 && request.isSecure())
                        || (request.getServerPort() == 80 && !request.isSecure()) ? "" : ":" + request.getServerPort());
            if ((!request.isSecure() && !loopback) || (origin != null && !origin.equals(expected))) {
                ApiExceptionHandler.write(response, ApiExceptionHandler.Code.PERMISSION_DENIED);
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
