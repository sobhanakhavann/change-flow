package com.example.changeflow.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** Transport mapping only. Services must establish visibility before reporting resource errors. */
@RestControllerAdvice
public class ApiExceptionHandler {
    public enum Code {
        INVALID_INPUT(400, "Invalid input"), INVALID_PRECONDITION(400, "Invalid precondition"),
        UNAUTHENTICATED(401, "Authentication required"), CLIENT_HEADER_REQUIRED(403, "Client header required"),
        PERMISSION_DENIED(403, "Permission denied"), REQUEST_NOT_FOUND(404, "Request not found"),
        INVALID_STATE(409, "Invalid state"), VERSION_CONFLICT(412, "Version conflict"),
        PRECONDITION_REQUIRED(428, "Precondition required"), SERVICE_UNAVAILABLE(503, "Service unavailable"),
        METHOD_NOT_ALLOWED(405, "Method not allowed"), INTERNAL_ERROR(500, "Internal error");

        final int status;
        final String title;
        Code(int status, String title) { this.status = status; this.title = title; }
    }

    /** Only safe predefined outcomes, never exception messages, request bodies or resource metadata. */
    public static final class Failure extends RuntimeException {
        private final Code code;
        public Failure(Code code) { super(code.title); this.code = code; }
    }

    public record Problem(String type, String title, int status, String code, String detail) { }

    private static Problem problem(Code code) {
        return new Problem("urn:changeflow:error:" + code.name(), code.title, code.status, code.name(), detail(code));
    }

    private static String detail(Code code) {
        return code == Code.SERVICE_UNAVAILABLE ? "Storage unavailable; operation outcome may be unknown" : code.title;
    }

    @ExceptionHandler(Failure.class)
    ResponseEntity<Problem> failure(Failure failure) { return response(failure.code); }

    @ExceptionHandler(com.example.changeflow.domain.RequestFailure.class)
    ResponseEntity<Problem> business(com.example.changeflow.domain.RequestFailure failure) {
        return response(Code.valueOf(failure.kind().name()));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, ServletRequestBindingException.class,
            BindException.class, HandlerMethodValidationException.class})
    ResponseEntity<Problem> input(Exception ignored) { return response(Code.INVALID_INPUT); }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Problem> storage(Exception ignored) { return response(Code.SERVICE_UNAVAILABLE); }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Problem> denied(Exception ignored) { return response(Code.PERMISSION_DENIED); }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Problem> authentication(AuthenticationException failure) {
        return response(failure instanceof AuthenticationServiceException
                ? Code.SERVICE_UNAVAILABLE : Code.UNAUTHENTICATED);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<Problem> missing(Exception ignored) { return response(Code.REQUEST_NOT_FOUND); }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Problem> unsupportedMethod(Exception ignored) { return response(Code.METHOD_NOT_ALLOWED); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Problem> unexpected(Exception ignored) { return response(Code.INTERNAL_ERROR); }

    private static ResponseEntity<Problem> response(Code code) {
        var builder = ResponseEntity.status(code.status)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (code == Code.UNAUTHENTICATED) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"ChangeFlow\"");
        }
        return builder.body(problem(code));
    }

    /** Same safe schema for security/filter failures before MVC dispatch. */
    public static void write(HttpServletResponse response, Code code) throws IOException {
        response.setStatus(code.status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (code == Code.UNAUTHENTICATED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"ChangeFlow\"");
        }
        // All interpolated values are fixed enum constants; no external text is serialized here.
        response.getWriter().write("{\"type\":\"urn:changeflow:error:" + code.name()
                + "\",\"title\":\"" + code.title + "\",\"status\":" + code.status
                + ",\"code\":\"" + code.name() + "\",\"detail\":\"" + detail(code) + "\"}");
    }
}
