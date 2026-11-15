package com.example.changeflow.controller;

import com.example.changeflow.support.ApiTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccessAndErrorContractTests extends ApiTestSupport {
    @Test void diagnosticRoleRevocationReturnsTheSameHttp404AsMissing() throws Exception {
        var id = db.request(db.dual, "Draft", 1);
        var sink = UUID.randomUUID(); db.user(sink, sink.toString());
        var before = db.storedState();
        var identities = identityRepository();
        var old = new java.util.concurrent.atomic.AtomicReference<java.sql.Connection>();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(call -> {
            var connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(db.dataSource);
            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection, db.dataSource);
            int number = calls.incrementAndGet();
            if (number == 1) { old.set(connection); }
            if (number == 2) {
                assertTrue(old.get().isClosed()); assertNotSame(old.get(), connection);
                db.externalUpdate("UPDATE user_role SET user_id=? WHERE user_id=? AND role='Requester'", sink.toString(), db.dual.toString());
            }
            return call.callRealMethod();
        }).when(identities).loadCurrentPermissions(org.mockito.ArgumentMatchers.any());
        var hidden = mvc.perform(as(put("/api/v1/requests/" + id), "dual").header("If-Match", tag(id, 0)).content(CONTENT))
                .andExpect(status().isNotFound()).andReturn().getResponse();
        var missing = mvc.perform(as(put("/api/v1/requests/" + UUID.randomUUID()), "dual").header("If-Match", tag(id, 0)).content(CONTENT))
                .andExpect(status().isNotFound()).andReturn().getResponse();
        assertEquals(hidden.getContentAsString(), missing.getContentAsString());
        assertEquals(hidden.getHeaderNames(), missing.getHeaderNames());
        for (String header : hidden.getHeaderNames()) { assertEquals(hidden.getHeaders(header), missing.getHeaders(header)); }
        assertEquals(before, db.storedState());
    }
    @Test void roleRevocationAfterBasicAuthenticationPrecedesResourceActionAndReadChecks() throws Exception {
        var pending = db.request(db.dual, "PendingReview", 0);
        var draft = db.request(db.dual, "Draft", 0);
        var othersPending = db.request(db.owner, "PendingReview", 0);
        var sink = UUID.randomUUID(); db.user(sink, sink.toString());
        var before = db.storedState();
        var identities = identityRepository();
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(call -> {
            if (first.getAndSet(false)) {
                org.mockito.Mockito.verify(identities).findForAuthentication("dual");
                db.externalUpdate("UPDATE user_role SET user_id=? WHERE user_id=? AND role='Requester'", sink.toString(), db.dual.toString());
            }
            return call.callRealMethod();
        }).when(identities).loadCurrentPermissions(org.mockito.ArgumentMatchers.any());
        mvc.perform(as(put("/api/v1/requests/" + pending), "dual").header("If-Match", tag(pending, 0)).content(CONTENT))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(as(get("/api/v1/requests/" + draft), "dual")).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/v1/requests/" + pending), "dual")).andExpect(status().isOk());
        var list = mvc.perform(as(get("/api/v1/requests"), "dual")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(list.contains(draft.toString())); assertTrue(list.contains(pending.toString())); assertTrue(list.contains(othersPending.toString()));
        assertEquals(before, db.storedState());
        assertEquals(1, db.jdbc.queryForObject("SELECT enabled FROM app_user WHERE id=?", Integer.class, db.dual.toString()));
    }
    @Test void roleStateReadAndMutationMatrixUsesRealJdbc() throws Exception {
        for (String state : new String[]{"Draft", "PendingReview", "ChangesRequested", "Approved"}) {
            var id = db.request(db.owner, state, 0);
            String path = "/api/v1/requests/" + id;
            boolean reviewVisible = state.equals("PendingReview") || state.equals("Approved");
            for (String role : new String[]{"owner", "reviewer", "dual", "other"}) {
                boolean visible = role.equals("owner") || (reviewVisible && (role.equals("reviewer") || role.equals("dual")));
                mvc.perform(as(get(path), role)).andExpect(status().is(visible ? 200 : 404));
                var list = mvc.perform(as(get("/api/v1/requests?limit=100"), role)).andExpect(status().isOk()).andReturn().getResponse();
                assertEquals(visible, list.getContentAsString().contains(id.toString()));
                int editStatus = !visible ? 404 : !role.equals("owner") ? 403 : reviewVisible ? 409 : 200;
                mvc.perform(as(put(path), role).header("If-Match", tag(id, 0)).content(CONTENT)).andExpect(status().is(editStatus));
            }
            // Separate resources keep each decision's original state/version independent.
            for (String role : new String[]{"owner", "reviewer", "dual", "other"}) {
                for (String action : new String[]{"submit", "approve", "request-changes"}) {
                    var target = db.request(db.owner, state, 0);
                    boolean visible = role.equals("owner") || (reviewVisible && (role.equals("reviewer") || role.equals("dual")));
                    boolean allowedRole = action.equals("submit") ? role.equals("owner") : role.equals("reviewer") || role.equals("dual");
                    boolean validState = action.equals("submit") ? !reviewVisible : state.equals("PendingReview");
                    int expected = !visible ? 404 : !allowedRole ? 403 : !validState ? 409 : 204;
                    mvc.perform(as(post("/api/v1/requests/" + target + "/" + action), role).header("If-Match", tag(target, 0))
                            .content(action.equals("request-changes") ? "{\"reason\":\"r\"}" : "{}"))
                            .andExpect(status().is(expected));
                    assertEquals(expected == 204 ? 1L : 0L, db.jdbc.queryForObject("SELECT version FROM change_request WHERE id=?", Long.class, target.toString()));
                }
            }
        }
    }

    @Test void privateAndMissingAreIndistinguishableBeforePayloadOrTagParsing() throws Exception {
        var privateId = db.request(db.owner, "Draft", 9);
        var absent = UUID.randomUUID();
        var a = mvc.perform(as(put("/api/v1/requests/" + privateId), "other").header("If-Match", "garbage").content("invalid"))
                .andExpect(status().isNotFound()).andReturn().getResponse();
        var b = mvc.perform(as(put("/api/v1/requests/" + absent), "other").header("If-Match", "garbage").content("invalid"))
                .andExpect(status().isNotFound()).andReturn().getResponse();
        assertEquals(a.getContentAsString(), b.getContentAsString());
        assertEquals(a.getHeaderNames(), b.getHeaderNames());
        for (String header : a.getHeaderNames()) { assertEquals(a.getHeaders(header), b.getHeaders(header)); }
        assertFalse(a.getContentAsString().contains(privateId.toString()));
    }

    @Test void preconditionsAndSecurityAreDistinctAndResponsesAreNotCached() throws Exception {
        var id = db.request(db.owner, "Draft", 1);
        String path = "/api/v1/requests/" + id;
        mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().exists("WWW-Authenticate"));
        mvc.perform(as(put(path), "owner").content(CONTENT)).andExpect(status().is(428));
        mvc.perform(as(put(path), "owner").header("If-Match", "*").content(CONTENT)).andExpect(status().isBadRequest());
        mvc.perform(as(put(path), "owner").header("If-Match", tag(id, 0)).content(CONTENT)).andExpect(status().is(412));
        mvc.perform(as(get(path), "owner")).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(as(post("/api/v1/requests"), "owner").header("Origin", "https://untrusted.example").content(CONTENT))
                .andExpect(status().isForbidden());
        mvc.perform(as(delete(path), "owner")).andExpect(status().is4xxClientError());
        assertEquals(1L, db.jdbc.queryForObject("SELECT version FROM change_request WHERE id=?", Long.class, id.toString()));
    }

    @Test void safeUnexpectedAndRealLockFailuresDoNotLeakOrPartiallyChangeData() throws Exception {
        var fullVersion = db.request(db.owner, "Draft", Long.MAX_VALUE);
        var response = mvc.perform(as(put("/api/v1/requests/" + fullVersion), "owner")
                .header("If-Match", tag(fullVersion, Long.MAX_VALUE)).content(CONTENT))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse();
        assertFalse(response.getContentAsString().contains("overflow"));
        var id = db.request(db.owner, "Draft", 0);
        try (var connection = db.dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
            try {
                var unavailable = mvc.perform(as(put("/api/v1/requests/" + id), "owner")
                        .header("If-Match", tag(id, 0)).content(CONTENT))
                        .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn().getResponse().getContentAsString();
                assertFalse(unavailable.contains(db.file.toString()));
                assertFalse(unavailable.contains("SQLITE"));
                assertFalse(unavailable.contains(id.toString()));
            } finally { statement.execute("ROLLBACK"); }
        }
        assertEquals(0L, db.jdbc.queryForObject("SELECT version FROM change_request WHERE id=?", Long.class, id.toString()));
        mvc.perform(put("/api/v1/requests/" + id)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("owner", com.example.changeflow.support.SqliteTestSupport.PASSWORD))
                .header("If-Match", tag(id, 0)).contentType("application/json").content(CONTENT))
                .andExpect(status().isForbidden());
    }
}
