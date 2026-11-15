package com.example.changeflow.controller;

import com.example.changeflow.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class StaleCommandTests extends ApiTestSupport {
    @Test
    void oldReviewAndSubmitCommandsNeverBecomeFreshIntentAfterAnotherCycle() throws Exception {
        var id = db.request(db.owner, "Draft", 0);
        String p = "/api/v1/requests/" + id;
        mvc.perform(as(post(p + "/submit"), "owner").header("If-Match", tag(id, 0)).content("{}")).andExpect(status().isNoContent());
        var afterSubmit = db.storedState();
        mvc.perform(as(post(p + "/submit"), "owner").header("If-Match", tag(id, 0)).content("{}")).andExpect(status().is(412));
        assertEquals(afterSubmit, db.storedState());
        mvc.perform(as(post(p + "/request-changes"), "reviewer").header("If-Match", tag(id, 1)).content("{\"reason\":\"r\"}"))
                .andExpect(status().isNoContent());
        var afterReturn = db.storedState();
        mvc.perform(as(post(p + "/request-changes"), "reviewer").header("If-Match", tag(id, 1)).content("{\"reason\":\"retry\"}"))
                .andExpect(status().isNotFound());
        assertEquals(afterReturn, db.storedState());
        mvc.perform(as(post(p + "/submit"), "owner").header("If-Match", tag(id, 2)).content("{}")).andExpect(status().isNoContent());
        var afterResubmit = db.storedState();
        mvc.perform(as(post(p + "/submit"), "owner").header("If-Match", tag(id, 0)).content("{}")).andExpect(status().is(412));
        assertEquals(afterResubmit, db.storedState());
        for (String action : new String[]{"request-changes", "approve"}) {
            mvc.perform(as(post(p + "/" + action), "reviewer").header("If-Match", tag(id, 1))
                    .content(action.equals("approve") ? "{}" : "{\"reason\":\"changed\"}"))
                    .andExpect(status().is(412));
            assertEquals(afterResubmit, db.storedState());
        }
        mvc.perform(as(post(p + "/approve"), "reviewer").header("If-Match", tag(id, 3)).content("{}")).andExpect(status().isNoContent());
        var afterApprove = db.storedState();
        mvc.perform(as(post(p + "/approve"), "reviewer").header("If-Match", tag(id, 3)).content("{}")).andExpect(status().is(412));
        assertEquals(afterApprove, db.storedState());
    }
}
