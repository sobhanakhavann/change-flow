package com.example.changeflow.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/** Non-simple header CSRF defense; deliberately registered only inside the security chain. */
public final class ApiClientHeaderFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !SAFE.contains(request.getMethod())) {
            var values = Collections.list(request.getHeaders("X-ChangeFlow-Client"));
            if (values.size() != 1 || !"api".equals(values.getFirst())) {
                ApiExceptionHandler.write(response, ApiExceptionHandler.Code.CLIENT_HEADER_REQUIRED);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
