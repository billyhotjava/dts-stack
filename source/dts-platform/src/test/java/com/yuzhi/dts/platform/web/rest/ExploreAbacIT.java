package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.explore.ExploreSavedQuery;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.ExploreSavedQueryRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@IntegrationTest
@Transactional
@TestPropertySource(properties = {
    // allow creating TRINO datasets in tests without multi-source restriction
    "dts.platform.catalog.multi-source-enabled=true"
})
class ExploreAbacIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CatalogDatasetRepository datasetRepository;

    @Autowired
    private ExploreSavedQueryRepository savedQueryRepository;

    @AfterEach
    void cleanUp() {
        savedQueryRepository.deleteAll();
        datasetRepository.deleteAll();
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTERNAL")
    void executeShouldDenyWhenDeptScopeMismatch() throws Exception {
        // Dataset visible but scoped to another dept
        CatalogDataset ds = new CatalogDataset();
        ds.setName("orders_dept_priv");
        ds.setType("TRINO");
        ds.setClassification("INTERNAL");
        ds.setOwnerDept("D001");
        ds.setHiveDatabase("mart");
        ds.setHiveTable("orders");
        CatalogDataset saved = datasetRepository.saveAndFlush(ds);

        Map<String, Object> body = Map.of(
            "datasetId", saved.getId().toString(),
            "sql", "select * from orders limit 5"
        );

        mockMvc
            .perform(
                post("/api/explore/execute")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Active-Dept", "D002") // mismatch
                    .content(objectMapper.writeValueAsBytes(body))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.code").value("dts-sec-0006")); // INVALID_CONTEXT
    }

    @Test
    @WithMockUser(authorities = "ROLE_TOP_SECRET")
    void executeShouldWrapSqlWithDeptFilterWhenAllowed() throws Exception {
        CatalogDataset ds = new CatalogDataset();
        ds.setName("orders_dept_priv2");
        ds.setType("TRINO");
        ds.setClassification("PUBLIC");
        ds.setOwnerDept("D001");
        ds.setHiveDatabase("mart");
        ds.setHiveTable("orders");
        CatalogDataset saved = datasetRepository.saveAndFlush(ds);

        Map<String, Object> body = Map.of(
            "datasetId", saved.getId().toString(),
            "sql", "select id, owner_dept from mart.orders"
        );

        MvcResult result = mockMvc
            .perform(
                post("/api/explore/execute")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Active-Dept", "D001")
                    .content(objectMapper.writeValueAsBytes(body))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.effectiveSql").exists())
            .andReturn();

        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        String effectiveSql = payload.path("data").path("effectiveSql").asText();
        assertThat(effectiveSql).isEqualTo("select id, owner_dept from mart.orders");
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTERNAL")
    void runSavedShouldRespectScopeGate() throws Exception {
        // INST resource but not shared → should be denied for INST scope
        CatalogDataset ds = new CatalogDataset();
        ds.setName("finance_core");
        ds.setType("TRINO");
        ds.setClassification("INTERNAL");
        ds.setOwnerDept("D009");
        ds.setHiveDatabase("mart");
        ds.setHiveTable("finance_core");
        CatalogDataset saved = datasetRepository.saveAndFlush(ds);

        ExploreSavedQuery q = new ExploreSavedQuery();
        q.setName("核心");
        q.setSqlText("select * from finance_core");
        q.setDatasetId(saved.getId());
        ExploreSavedQuery savedQuery = savedQueryRepository.saveAndFlush(q);

        mockMvc
            .perform(
                post("/api/explore/saved-queries/" + savedQuery.getId() + "/run")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Active-Dept", "D002")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.code").value("dts-sec-0006"));
    }
}
