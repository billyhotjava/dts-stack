package com.yuzhi.dts.platform.web.rest.sql;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.repository.sql.SqlIdeTabRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "alice")
class SqlIdeTabResourceIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private SqlIdeTabRepository repository;

    @BeforeEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void emptyListInitially() throws Exception {
        mvc.perform(get("/api/sql/v2/tabs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void createAndList() throws Exception {
        Map<String, Object> body = Map.of("title", "Query 1", "sqlText", "SELECT 1", "engine", "generic");
        mvc.perform(post("/api/sql/v2/tabs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Query 1"));

        mvc.perform(get("/api/sql/v2/tabs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].sqlText").value("SELECT 1"));
    }

    @Test
    void patchWithStaleUpdatedAtReturns409() throws Exception {
        Map<String, Object> createBody = Map.of("title", "Q", "sqlText", "SELECT 1");
        MvcResult created = mvc.perform(post("/api/sql/v2/tabs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(createBody)))
            .andReturn();
        Map<?, ?> resp = mapper.readValue(created.getResponse().getContentAsByteArray(), Map.class);
        Map<?, ?> data = (Map<?, ?>) resp.get("data");
        String id = (String) data.get("id");

        Map<String, Object> patchBody = Map.of(
            "sqlText", "SELECT 2",
            "updatedAt", "2000-01-01T00:00:00Z"  // stale
        );
        mvc.perform(patch("/api/sql/v2/tabs/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(patchBody)))
            .andExpect(status().isConflict());
    }

    @Test
    void deleteRemovesTab() throws Exception {
        Map<String, Object> body = Map.of("title", "Q", "sqlText", "SELECT 1");
        MvcResult created = mvc.perform(post("/api/sql/v2/tabs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body)))
            .andReturn();
        Map<?, ?> resp = mapper.readValue(created.getResponse().getContentAsByteArray(), Map.class);
        Map<?, ?> data = (Map<?, ?>) resp.get("data");
        String id = (String) data.get("id");

        mvc.perform(delete("/api/sql/v2/tabs/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/sql/v2/tabs")).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void over30TabsReturns429() throws Exception {
        for (int i = 0; i < 30; i++) {
            Map<String, Object> body = Map.of("title", "Q" + i, "sqlText", "SELECT 1", "sortOrder", i);
            mvc.perform(post("/api/sql/v2/tabs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isOk());
        }
        Map<String, Object> overflow = Map.of("title", "overflow", "sqlText", "SELECT 1");
        mvc.perform(post("/api/sql/v2/tabs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(overflow)))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void crossUserDeleteIsSilentNoOp() throws Exception {
        // A random UUID that doesn't exist — delete should silently succeed (no 404)
        mvc.perform(delete("/api/sql/v2/tabs/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isOk());
    }
}
