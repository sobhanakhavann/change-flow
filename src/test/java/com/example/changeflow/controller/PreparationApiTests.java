package com.example.changeflow.controller;

import com.example.changeflow.support.ApiTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PreparationApiTests extends ApiTestSupport {
    @Test
    void createsIncompleteDraftAndThenEditsAndSubmitsWithExactHeaders() throws Exception {
        var response = mvc.perform(as(post("/api/v1/requests"), "owner").content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("Draft"))
                .andExpect(jsonPath("$.version").value("0")).andExpect(jsonPath("$.title").value(""))
                .andExpect(jsonPath("$.requesterId").value(db.owner.toString())).andReturn().getResponse();
        String location = response.getHeader("Location");
        mvc.perform(as(post(location + "/submit"), "owner").header("If-Match", response.getHeader("ETag")).content("{}"))
                .andExpect(status().isBadRequest());
        var edit = mvc.perform(as(put(location), "owner").header("If-Match", response.getHeader("ETag")).content(CONTENT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value("1")).andReturn().getResponse();
        mvc.perform(as(post(location + "/submit"), "owner").header("If-Match", edit.getHeader("ETag")).content("{}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(as(get(location), "reviewer")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PendingReview"));
    }

    @Test
    void replacementDefaultsAndInputTypesCannotInjectOwnershipOrStatus() throws Exception {
        UUID id = db.request(db.owner, "Draft", 0);
        for (String body : new String[]{"[]", "null", "{\"title\":4}", "{\"status\":\"Approved\"}", "{\"requesterId\":\"x\"}"}) {
            mvc.perform(as(put("/api/v1/requests/" + id), "owner").header("If-Match", tag(id, 0)).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(as(put("/api/v1/requests/" + id), "owner").header("If-Match", tag(id, 0)).content("{\"title\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value(""))
                .andExpect(jsonPath("$.changeDescription").value(""));
    }

    @Test
    void listFiltersBeforePaginationAndValidatesCursors() throws Exception {
        UUID owned = db.request(db.owner, "Draft", 0);
        db.request(db.other, "Draft", 0);
        mvc.perform(as(get("/api/v1/requests?limit=1"), "owner")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(owned.toString()));
        mvc.perform(as(get("/api/v1/requests?limit=0"), "owner")).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/v1/requests?after=bad"), "owner")).andExpect(status().isBadRequest());
    }
}
