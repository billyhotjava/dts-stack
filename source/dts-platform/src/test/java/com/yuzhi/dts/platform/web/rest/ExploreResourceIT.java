package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.domain.explore.ExploreSavedQuery;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.repository.explore.ExploreSavedQueryRepository;
import com.yuzhi.dts.platform.service.audit.AuditTrailService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.ArgumentCaptor;

@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class ExploreResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CatalogDatasetRepository datasetRepository;

    @Autowired
    private ResultSetRepository resultSetRepository;

    @Autowired
    private QueryExecutionRepository executionRepository;

    @Autowired
    private ExploreSavedQueryRepository savedQueryRepository;

    @MockBean
    private AuditTrailService auditTrailService;

    @AfterEach
    void cleanUp() {
        savedQueryRepository.deleteAll();
        executionRepository.deleteAll();
        resultSetRepository.deleteAll();
        datasetRepository.deleteAll();
    }

    @Test
    @WithMockUser(authorities = "ROLE_TOP_SECRET")
    void executeSaveAndCleanupFlow() throws Exception {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setName("sales_orders");
        dataset.setClassification("INTERNAL");
        dataset.setHiveDatabase("mart");
        dataset.setHiveTable("sales_orders");
        dataset.setOwner("dataops");
        CatalogDataset persisted = datasetRepository.saveAndFlush(dataset);

        Map<String, Object> request = Map.of(
            "datasetId",
            persisted.getId().toString(),
            "sql",
            "select * from sales_orders limit 10"
        );

        MvcResult executeResult = mockMvc
            .perform(
                post("/api/explore/execute")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.executionId").isNotEmpty())
            .andExpect(jsonPath("$.data.headers").isArray())
            .andReturn();

        JsonNode node = objectMapper.readTree(executeResult.getResponse().getContentAsString());
        String executionId = node.path("data").path("executionId").asText();
        assertThat(executionId).isNotBlank();

        UUID resultSetId = UUID.fromString(executionId);
        ResultSet storedResult = resultSetRepository.findById(resultSetId).orElse(null);
        assertThat(storedResult).isNotNull();
        List<QueryExecution> executions = executionRepository.findByResultSetId(resultSetId);
        assertThat(executions).hasSize(1);
        assertThat(executions.get(0).getDatasetId()).isEqualTo(persisted.getId());

        mockMvc
            .perform(
                post("/api/explore/save-result/" + executionId)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Map.of("ttlDays", 5)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.storageUri").value(containsString(executionId)));

        ResultSet refreshed = resultSetRepository.findById(resultSetId).orElse(null);
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.getTtlDays()).isEqualTo(5);
        assertThat(refreshed.getExpiresAt()).isNotNull();

        mockMvc
            .perform(get("/api/explore/result-sets").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data[0].id").value(executionId))
            .andExpect(jsonPath("$.data[0].datasetName").value("sales_orders"))
            .andExpect(jsonPath("$.data[0].classification").value("内部"));

        mockMvc
            .perform(delete("/api/explore/result-sets/" + executionId).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        assertThat(resultSetRepository.findById(resultSetId)).isEmpty();
        assertThat(executionRepository.findByResultSetId(resultSetId)).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTERNAL")
    void executeShouldRejectDatasetWithoutPermission() throws Exception {
        CatalogDataset lockedDataset = new CatalogDataset();
        lockedDataset.setName("finance_plans");
        lockedDataset.setClassification("CONFIDENTIAL");
        lockedDataset.setHiveDatabase("core");
        lockedDataset.setHiveTable("finance_plans");
        CatalogDataset saved = datasetRepository.saveAndFlush(lockedDataset);

        Map<String, Object> request = Map.of(
            "datasetId",
            saved.getId().toString(),
            "sql",
            "select 1"
        );

        mockMvc
            .perform(
                post("/api/explore/execute")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.message").value("Access denied for dataset"));

        assertThat(resultSetRepository.findAll()).isEmpty();
        assertThat(executionRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "optest1", authorities = "ROLE_TOP_SECRET")
    void saveResultShouldEmitAuditEvent() throws Exception {
        ResultSet resultSet = new ResultSet();
        resultSet.setStorageUri("memory://result/audit");
        resultSet.setStorageFormat(ResultSet.StorageFormat.PARQUET);
        resultSet.setColumns("col_a,col_b");
        resultSet.setRowCount(2L);
        resultSet.setChunkCount(1);
        ResultSet saved = resultSetRepository.saveAndFlush(resultSet);

        reset(auditTrailService);

        mockMvc
            .perform(
                post("/api/explore/save-result/" + saved.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Map.of("ttlDays", 5, "name", "审计结果集")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.name").value("审计结果集"));

        ArgumentCaptor<AuditTrailService.PendingAuditEvent> captor = ArgumentCaptor.forClass(
            AuditTrailService.PendingAuditEvent.class
        );
        verify(auditTrailService, atLeastOnce()).record(captor.capture());

        AuditTrailService.PendingAuditEvent auditEvent = captor
            .getAllValues()
            .stream()
            .filter(event -> "explore.saveResult".equals(event.module))
            .findFirst()
            .orElseThrow(() -> new AssertionError("未捕获保存结果集的审计事件"));

        assertThat(auditEvent.actor).isEqualTo("optest1");
        assertThat(auditEvent.action).isEqualTo("UPDATE");
        assertThat(auditEvent.operationType).isEqualTo("UPDATE");
        assertThat(auditEvent.result).isEqualTo("SUCCESS");
        assertThat(auditEvent.summary).contains("保存查询结果集");
        assertThat(auditEvent.resourceId).isEqualTo(saved.getId().toString());
    }

    @Test
    @WithMockUser(authorities = "ROLE_TOP_SECRET")
    void cleanupResultSetsRemovesExpiredOnly() throws Exception {
        ResultSet expired = new ResultSet();
        expired.setStorageUri("memory://result/expired");
        expired.setStorageFormat(ResultSet.StorageFormat.PARQUET);
        expired.setColumns("col_a,col_b");
        expired.setRowCount(12L);
        expired.setChunkCount(1);
        expired.setTtlDays(3);
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        ResultSet expiredSaved = resultSetRepository.saveAndFlush(expired);

        QueryExecution expiredExec = new QueryExecution();
        expiredExec.setEngine(ExecEnums.ExecEngine.TRINO);
        expiredExec.setSqlText("select 1");
        expiredExec.setStatus(ExecEnums.ExecStatus.SUCCESS);
        expiredExec.setStartedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        expiredExec.setFinishedAt(Instant.now().minus(4, ChronoUnit.MINUTES));
        expiredExec.setRowCount(10L);
        expiredExec.setElapsedMs(123L);
        expiredExec.setResultSetId(expiredSaved.getId());
        executionRepository.saveAndFlush(expiredExec);

        ResultSet active = new ResultSet();
        active.setStorageUri("memory://result/active");
        active.setStorageFormat(ResultSet.StorageFormat.PARQUET);
        active.setColumns("col_a,col_b");
        active.setRowCount(25L);
        active.setChunkCount(1);
        active.setTtlDays(5);
        active.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));
        ResultSet activeSaved = resultSetRepository.saveAndFlush(active);

        QueryExecution activeExec = new QueryExecution();
        activeExec.setEngine(ExecEnums.ExecEngine.TRINO);
        activeExec.setSqlText("select 2");
        activeExec.setStatus(ExecEnums.ExecStatus.SUCCESS);
        activeExec.setStartedAt(Instant.now().minus(3, ChronoUnit.MINUTES));
        activeExec.setFinishedAt(Instant.now().minus(2, ChronoUnit.MINUTES));
        activeExec.setRowCount(5L);
        activeExec.setElapsedMs(80L);
        activeExec.setResultSetId(activeSaved.getId());
        executionRepository.saveAndFlush(activeExec);

        mockMvc
            .perform(post("/api/explore/result-sets/cleanup").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.deleted").value(1));

        assertThat(resultSetRepository.findById(expiredSaved.getId())).isEmpty();
        assertThat(resultSetRepository.findById(activeSaved.getId())).isPresent();
        assertThat(executionRepository.findByResultSetId(expiredSaved.getId())).isEmpty();
        assertThat(executionRepository.findByResultSetId(activeSaved.getId())).hasSize(1);
    }

    @Test
    @WithMockUser(authorities = "ROLE_TOP_SECRET")
    void previewResultSetReturnsStoredRows() throws Exception {
        Map<String, Object> payload = Map.of(
            "rows",
            List.of(Map.of("name", "Alice", "score", 95)),
            "masking",
            Map.of("maskedColumns", List.of("name"))
        );
        ResultSet record = new ResultSet();
        record.setStorageUri("memory://result/preview");
        record.setStorageFormat(ResultSet.StorageFormat.PARQUET);
        record.setColumns("name,score");
        record.setRowCount(95L);
        record.setChunkCount(1);
        record.setPreviewColumns(objectMapper.writeValueAsString(payload));
        ResultSet saved = resultSetRepository.saveAndFlush(record);

        mockMvc
            .perform(get("/api/explore/result-preview/" + saved.getId()).with(csrf()).param("rows", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.headers[0]").value("name"))
            .andExpect(jsonPath("$.data.rows[0].name").value("Alice"))
            .andExpect(jsonPath("$.data.masking.maskedColumns[0]").value("name"));

        mockMvc
            .perform(get("/api/explore/result-preview/" + UUID.randomUUID()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1));
    }

    @Test
    @WithMockUser(authorities = "ROLE_TOP_SECRET")
    void saveResultShouldReturnErrorWhenResultMissing() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc
            .perform(
                post("/api/explore/save-result/" + randomId)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Map.of("ttlDays", 3)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.message").value("Result set not found"));

        assertThat(resultSetRepository.findById(randomId)).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "ROLE_TOP_SECRET")
    void saveResultWithInvalidTtlFallsBackToDefault() throws Exception {
        ResultSet record = new ResultSet();
        record.setStorageUri("memory://result/ttl-invalid");
        record.setStorageFormat(ResultSet.StorageFormat.PARQUET);
        record.setColumns("col_a,col_b");
        record.setRowCount(12L);
        record.setChunkCount(1);
        ResultSet saved = resultSetRepository.saveAndFlush(record);

        mockMvc
            .perform(
                post("/api/explore/save-result/" + saved.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Map.of("ttlDays", "invalid")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.id").value(saved.getId().toString()))
            .andExpect(jsonPath("$.data.storageUri").value(containsString(saved.getId().toString())));

        ResultSet refreshed = resultSetRepository.findById(saved.getId()).orElseThrow();
        assertThat(refreshed.getTtlDays()).isEqualTo(7);
        assertThat(refreshed.getExpiresAt()).isNotNull();
        assertThat(refreshed.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTERNAL")
    void previewShouldRejectWhenDatasetAccessDenied() throws Exception {
        CatalogDataset restricted = new CatalogDataset();
        restricted.setName("finance_budget");
        restricted.setClassification("CONFIDENTIAL");
        restricted.setHiveDatabase("core");
        restricted.setHiveTable("finance_budget");
        CatalogDataset saved = datasetRepository.saveAndFlush(restricted);

        Map<String, Object> request = Map.of(
            "datasetId",
            saved.getId().toString(),
            "sql",
            "select 1"
        );

        mockMvc
            .perform(
                post("/api/explore/query/preview")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.message").value("Access denied for dataset"));

        assertThat(resultSetRepository.findAll()).isEmpty();
        assertThat(executionRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "ROLE_TOP_SECRET")
    void runSavedQueryExecutesSuccessfully() throws Exception {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setName("marketing_events");
        dataset.setClassification("INTERNAL");
        dataset.setHiveDatabase("mart");
        dataset.setHiveTable("marketing_events");
        CatalogDataset persisted = datasetRepository.saveAndFlush(dataset);

        ExploreSavedQuery query = new ExploreSavedQuery();
        query.setName("最新活动");
        query.setSqlText("select * from marketing_events limit 20");
        query.setDatasetId(persisted.getId());
        ExploreSavedQuery savedQuery = savedQueryRepository.saveAndFlush(query);

        MvcResult result = mockMvc
            .perform(post("/api/explore/saved-queries/" + savedQuery.getId() + "/run").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.executionId").isNotEmpty())
            .andReturn();

        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        String executionId = payload.path("data").path("executionId").asText();
        assertThat(executionId).isNotBlank();

        UUID resultSetId = UUID.fromString(executionId);
        assertThat(resultSetRepository.findById(resultSetId)).isPresent();
        assertThat(executionRepository.findByResultSetId(resultSetId)).hasSize(1);

        mockMvc
            .perform(get("/api/explore/saved-queries").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data[*].id", hasItem(savedQuery.getId().toString())));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTERNAL")
    void runSavedQueryShouldRespectDatasetPermission() throws Exception {
        CatalogDataset restricted = new CatalogDataset();
        restricted.setName("finance_payroll");
        restricted.setClassification("CONFIDENTIAL");
        restricted.setHiveDatabase("core");
        restricted.setHiveTable("finance_payroll");
        CatalogDataset savedDataset = datasetRepository.saveAndFlush(restricted);

        ExploreSavedQuery query = new ExploreSavedQuery();
        query.setName("工资查询");
        query.setSqlText("select * from finance_payroll");
        query.setDatasetId(savedDataset.getId());
        ExploreSavedQuery savedQuery = savedQueryRepository.saveAndFlush(query);

        mockMvc
            .perform(post("/api/explore/saved-queries/" + savedQuery.getId() + "/run").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.message").value("Access denied for dataset"));

        assertThat(resultSetRepository.findAll()).isEmpty();
        assertThat(executionRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "ROLE_TOP_SECRET")
    void createSavedQueryPersistsAndLists() throws Exception {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setName("customer_profile");
        dataset.setClassification("INTERNAL");
        dataset.setHiveDatabase("mart");
        dataset.setHiveTable("customer_profile");
        CatalogDataset persisted = datasetRepository.saveAndFlush(dataset);

        Map<String, Object> payload = Map.of(
            "name",
            "客户画像抽样",
            "sqlText",
            "select * from customer_profile limit 50",
            "datasetId",
            persisted.getId().toString()
        );

        MvcResult createResult = mockMvc
            .perform(
                post("/api/explore/saved-queries")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(payload))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andExpect(jsonPath("$.data.datasetId").value(persisted.getId().toString()))
            .andReturn();

        JsonNode node = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String id = node.path("data").path("id").asText();
        assertThat(id).isNotBlank();

        ExploreSavedQuery stored = savedQueryRepository.findById(UUID.fromString(id)).orElse(null);
        assertThat(stored).isNotNull();
        assertThat(stored.getName()).isEqualTo("客户画像抽样");

        mockMvc
            .perform(get("/api/explore/saved-queries").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data[*].id", hasItem(id)));
    }

    @Test
    @WithMockUser(authorities = "ROLE_TOP_SECRET")
    void deleteSavedQueryIsIdempotent() throws Exception {
        ExploreSavedQuery query = new ExploreSavedQuery();
        query.setName("订单复盘");
        query.setSqlText("select * from orders");
        ExploreSavedQuery saved = savedQueryRepository.saveAndFlush(query);

        mockMvc
            .perform(delete("/api/explore/saved-queries/" + saved.getId()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        assertThat(savedQueryRepository.findById(saved.getId())).isEmpty();

        mockMvc
            .perform(delete("/api/explore/saved-queries/" + saved.getId()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        assertThat(savedQueryRepository.findAll()).isEmpty();
    }
}
