package com.example.changeflow.controller;

import com.example.changeflow.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReviewApiTests extends ApiTestSupport {
    @Test
    void feedbackIsRequiredAndReturnRemovesReviewerReadAccess() throws Exception {
        var id = db.request(db.owner, "PendingReview", 2);
        String path = "/api/v1/requests/" + id;
        for (String body : new String[]{"{}", "{\"reason\":\" \"}", "{\"reason\":null}"}) {
            mvc.perform(as(post(path + "/request-changes"), "reviewer").header("If-Match", tag(id, 2)).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(as(post(path + "/request-changes"), "reviewer").header("If-Match", tag(id, 2)).content("{\"reason\":\"revise\"}"))
                .andExpect(status().isNoContent()).andExpect(header().doesNotExist("ETag"));
        mvc.perform(as(get(path), "reviewer")).andExpect(status().isNotFound());
        mvc.perform(as(get(path), "owner")).andExpect(status().isOk()).andExpect(jsonPath("$.latestReturnReason.reason").value("revise"));
    }

    @Test
    void selfReviewAndApprovedMutationsAreDeniedAndNoDataChanges() throws Exception {
        var own = db.request(db.dual, "PendingReview", 1);
        for (String action : new String[]{"approve", "request-changes"}) {
            mvc.perform(as(post("/api/v1/requests/" + own + "/" + action), "dual").header("If-Match", tag(own, 1))
                    .content(action.equals("approve") ? "{}" : "{\"reason\":\"r\"}"))
                    .andExpect(status().isForbidden());
        }
        var approved = db.request(db.owner, "Approved", 4);
        mvc.perform(as(put("/api/v1/requests/" + approved), "owner").header("If-Match", tag(approved, 4)).content(CONTENT))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }
}
