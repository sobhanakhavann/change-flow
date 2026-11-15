package com.example.changeflow.controller;

import com.example.changeflow.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RevisionApiTests extends ApiTestSupport {
    @Test void returnedVisibilityAndReplacementValidationFollowCurrentRole() throws Exception {
        var id = db.request(db.dual, "PendingReview", 1);
        String path = "/api/v1/requests/" + id;
        mvc.perform(as(post(path + "/request-changes"), "reviewer").header("If-Match", tag(id, 1)).content("{\"reason\":\"revise\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(as(get(path), "dual")).andExpect(status().isOk()).andExpect(jsonPath("$.latestReturnReason.reason").value("revise"));
        mvc.perform(as(put(path), "reviewer").content("invalid")).andExpect(status().isNotFound());
        mvc.perform(as(put(path), "dual").header("If-Match", tag(id, 2)).content("{\"title\":\"" + "a".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(put(path), "dual").header("If-Match", tag(id, 2)).content(CONTENT)).andExpect(status().isOk());
        mvc.perform(as(post(path + "/submit"), "dual").header("If-Match", tag(id, 3)).content("{}"))
                .andExpect(status().isNoContent());
        mvc.perform(as(get(path), "reviewer")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PendingReview"));
    }
}
