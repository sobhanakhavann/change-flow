package com.example.changeflow.controller;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static com.example.changeflow.controller.ApiExceptionHandler.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VersionAndErrorTests {
    private static final UUID ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private MockMvc mvc;

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.standaloneSetup(new ErrorProbe()).setControllerAdvice(new ApiExceptionHandler()).build(); }

    @Test
    void supportsZeroAndMaximumVersionAndPreservesOriginalTag() {
        for (long version : new long[]{0, 7, Long.MAX_VALUE}) {
            String tag = VersionPrecondition.etag(ID, version);
            assertEquals(version, VersionPrecondition.requireForVisibleResource(List.of(tag), ID, version));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "W/\"x\"", "\"x\"", "\"request-550e8400-e29b-41d4-a716-446655440000-v-1\"",
            "\"request-550e8400-e29b-41d4-a716-446655440000-v9223372036854775808\"",
            "\"request-550e8400-e29b-41d4-a716-446655440000-v01\"", "\"x\", \"y\""})
    void invalidTagsHaveSafe400(String tag) throws Exception {
        mvc.perform(get("/version").header("If-Match", tag)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRECONDITION"));
    }

    @Test
    void missingAndDuplicatePreconditionsAreDistinct() throws Exception {
        mvc.perform(get("/version")).andExpect(status().is(428));
        mvc.perform(get("/version").header("If-Match", VersionPrecondition.etag(ID, 7), VersionPrecondition.etag(ID, 7)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyHttpOptionalWhitespaceMaySurroundAnOtherwiseValidTag() {
        String tag = VersionPrecondition.etag(ID, 7);
        assertEquals(7, VersionPrecondition.requireForVisibleResource(List.of(" \t" + tag + "\t "), ID, 7));
        assertThrows(ApiExceptionHandler.Failure.class,
                () -> VersionPrecondition.requireForVisibleResource(List.of("\n" + tag), ID, 7));
        assertThrows(ApiExceptionHandler.Failure.class,
                () -> VersionPrecondition.requireForVisibleResource(List.of("\u3000" + tag), ID, 7));
    }

    @Test
    void unknownRoutesHaveTheSafeNotFoundSchema() throws Exception {
        mvc.perform(get("/route-that-does-not-exist")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
    }

    @Test
    void staleOrDifferentResourceTagsConflictWithoutDisclosingVersions() throws Exception {
        for (String tag : List.of(VersionPrecondition.etag(ID, 6), VersionPrecondition.etag(UUID.randomUUID(), 7))) {
            mvc.perform(get("/version").header("If-Match", tag)).andExpect(status().is(412))
                    .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                    .andExpect(jsonPath("$.version").doesNotExist());
        }
    }

    @Test
    void inaccessibleAndAbsentAreIdenticalBeforeMalformedPrecondition() throws Exception {
        String absent = mvc.perform(get("/unavailable/missing").header("If-Match", "bad"))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        String hidden = mvc.perform(get("/unavailable/private").header("If-Match", "bad"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(absent, hidden);
        assertFalse(hidden.contains("private"));
    }

    @Test
    void everyDefinedFailureHasTheSameSafeSchema() throws Exception {
        for (var code : ApiExceptionHandler.Code.values()) {
            mvc.perform(get("/failure/" + code.name())).andExpect(status().is(code.status))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.type").value("urn:changeflow:error:" + code.name()))
                    .andExpect(jsonPath("$.code").value(code.name()));
        }
    }

    @Test
    void doesNotEchoExceptionMessagesFromInputStorageOrUnexpectedErrors() throws Exception {
        for (String path : List.of("input", "storage", "unexpected")) {
            String body = mvc.perform(get("/raw/" + path)).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("secret-fixture"));
            assertFalse(body.contains("stackTrace"));
        }
    }

    @org.springframework.boot.test.context.TestComponent
    @RestController
    static class ErrorProbe {
        @GetMapping("/version") long version(jakarta.servlet.http.HttpServletRequest request) {
            return VersionPrecondition.requireForVisibleResource(
                    java.util.Collections.list(request.getHeaders("If-Match")), ID, 7);
        }
        // Simulates a service's single missing/private outcome; not a resource lookup or business controller.
        @GetMapping("/unavailable/{kind}") void unavailable() { throw new ApiExceptionHandler.Failure(REQUEST_NOT_FOUND); }
        @GetMapping("/failure/{code}") void failure(@PathVariable String code) {
            throw new ApiExceptionHandler.Failure(ApiExceptionHandler.Code.valueOf(code));
        }
        @GetMapping("/raw/{kind}") void raw(@PathVariable String kind) {
            switch (kind) {
                case "input" -> throw new IllegalArgumentException("secret-fixture");
                case "storage" -> throw new DataAccessResourceFailureException("secret-fixture");
                default -> throw new IllegalStateException("secret-fixture");
            }
        }
    }
}
