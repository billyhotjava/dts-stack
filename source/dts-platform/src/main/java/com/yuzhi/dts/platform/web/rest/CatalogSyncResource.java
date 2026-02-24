package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.infra.InfraCatalogSyncRunRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry;
import com.yuzhi.dts.platform.service.infra.InceptorIntegrationCoordinator;
import com.yuzhi.dts.platform.service.infra.JdbcCatalogSyncService;
import com.yuzhi.dts.platform.service.infra.JdbcCatalogSyncService.JdbcSyncResult;
import com.yuzhi.dts.platform.service.infra.JdbcIntegrationCoordinator;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient;
import com.yuzhi.dts.platform.service.infra.InceptorIntegrationCoordinator.IntegrationStatus;
import com.yuzhi.dts.platform.service.infra.JdbcIntegrationCoordinator.JdbcIntegrationStatus;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/catalog/sync")
public class CatalogSyncResource {

    private static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final InceptorIntegrationCoordinator inceptorCoordinator;
    private final JdbcIntegrationCoordinator jdbcCoordinator;
    private final InceptorDataSourceRegistry inceptorRegistry;
    private final JdbcCatalogSyncService jdbcSyncService;
    private final AuditService auditService;
    private final InfraCatalogSyncRunRepository syncRunRepository;
    private final InfraDataSourceRepository dataSourceRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogFeatureProperties catalogFeatures;
    private final ObjectMapper objectMapper;
    private final AdminInfraClient adminInfraClient;

    public CatalogSyncResource(
        InceptorIntegrationCoordinator inceptorCoordinator,
        JdbcIntegrationCoordinator jdbcCoordinator,
        InceptorDataSourceRegistry inceptorRegistry,
        JdbcCatalogSyncService jdbcSyncService,
        AuditService auditService,
        InfraCatalogSyncRunRepository syncRunRepository,
        InfraDataSourceRepository dataSourceRepository,
        CatalogDatasetRepository datasetRepository,
        CatalogFeatureProperties catalogFeatures,
        ObjectMapper objectMapper,
        AdminInfraClient adminInfraClient
    ) {
        this.inceptorCoordinator = inceptorCoordinator;
        this.jdbcCoordinator = jdbcCoordinator;
        this.inceptorRegistry = inceptorRegistry;
        this.jdbcSyncService = jdbcSyncService;
        this.auditService = auditService;
        this.syncRunRepository = syncRunRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.datasetRepository = datasetRepository;
        this.catalogFeatures = catalogFeatures;
        this.objectMapper = objectMapper;
        this.adminInfraClient = adminInfraClient;
    }

    public record SyncRequest(Boolean includePrimary, Boolean includeJdbc, String reason) {}
    public record SyncConfigRequest(Boolean autoSyncEnabled, String autoSyncCron) {}

    @PostMapping
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> triggerSync(@Valid @RequestBody(required = false) SyncRequest body) {
        boolean includePrimary = body == null || body.includePrimary == null || body.includePrimary.booleanValue();
        boolean includeJdbc = body == null || body.includeJdbc == null || body.includeJdbc.booleanValue();
        String reason = body != null && body.reason != null && !body.reason.isBlank() ? body.reason.trim() : "manual";

        if (includePrimary && !inceptorCoordinator.isSyncInProgress()) {
            inceptorCoordinator.synchronizeAsync("api:" + reason);
        }
        if (includeJdbc && !jdbcCoordinator.isSyncInProgress()) {
            jdbcCoordinator.synchronizeAsync("api:" + reason);
        }

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "触发元数据采集");
        auditPayload.put("includePrimary", includePrimary);
        auditPayload.put("includeJdbc", includeJdbc);
        auditPayload.put("reason", reason);
        auditService.auditAction("CATALOG_SYNC_TRIGGER", AuditStage.SUCCESS, reason, auditPayload);
        return ApiResponses.ok(statusPayload());
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        auditService.auditAction("CATALOG_SYNC_STATUS_VIEW", AuditStage.SUCCESS, "status", Map.of("summary", "查看采集状态"));
        return ApiResponses.ok(statusPayload());
    }

    @GetMapping("/config")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> getSyncConfig() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("autoSyncEnabled", catalogFeatures != null && catalogFeatures.isAutoSyncEnabled());
        payload.put("autoSyncCron", catalogFeatures != null ? catalogFeatures.getAutoSyncCron() : null);
        payload.put("cronRuntimeEditable", true);
        payload.put("message", "自动采集开关与 Cron 修改均为运行时生效。");
        auditService.auditAction("CATALOG_SYNC_CONFIG_VIEW", AuditStage.SUCCESS, "config", Map.of("summary", "查看采集配置"));
        return ApiResponses.ok(payload);
    }

    @PostMapping("/config")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> updateSyncConfig(@RequestBody(required = false) SyncConfigRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "配置不能为空");
        }
        if (body.autoSyncEnabled() != null && catalogFeatures != null) {
            catalogFeatures.setAutoSyncEnabled(body.autoSyncEnabled());
        }
        String cron = body.autoSyncCron();
        if (StringUtils.hasText(cron) && catalogFeatures != null) {
            if (!isValidCron(cron)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cron 表达式格式不正确（需 5-7 段）");
            }
            catalogFeatures.setAutoSyncCron(cron.trim());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("autoSyncEnabled", catalogFeatures != null && catalogFeatures.isAutoSyncEnabled());
        payload.put("autoSyncCron", catalogFeatures != null ? catalogFeatures.getAutoSyncCron() : null);
        payload.put("cronRuntimeEditable", true);
        payload.put("message", "自动采集启停与 Cron 更新已生效。");
        auditService.auditAction(
            "CATALOG_SYNC_CONFIG_UPDATE",
            AuditStage.SUCCESS,
            "config",
            Map.of(
                "summary",
                "更新采集配置",
                "autoSyncEnabled",
                payload.get("autoSyncEnabled"),
                "autoSyncCron",
                payload.get("autoSyncCron")
            )
        );
        return ApiResponses.ok(payload);
    }

    private boolean isValidCron(String cron) {
        if (!StringUtils.hasText(cron)) {
            return false;
        }
        try {
            CronExpression.parse(cron.trim());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @GetMapping("/runs")
    public ApiResponse<List<Map<String, Object>>> listRuns(
        @RequestParam(name = "integration", required = false, defaultValue = "INCEPTOR") String integration,
        @RequestParam(name = "limit", required = false, defaultValue = "20") int limit,
        @RequestParam(name = "includeDetails", required = false, defaultValue = "false") boolean includeDetails,
        @RequestParam(name = "sourceId", required = false) UUID sourceId
    ) {
        String normalized = StringUtils.hasText(integration) ? integration.trim().toUpperCase(java.util.Locale.ROOT) : "INCEPTOR";
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<InfraCatalogSyncRun> runs = syncRunRepository.findTop100ByIntegrationOrderByStartedAtDesc(normalized);
        List<Map<String, Object>> payload = new ArrayList<>();
        UUID filterSourceId = "JDBC".equalsIgnoreCase(normalized) ? sourceId : null;
        for (InfraCatalogSyncRun run : runs) {
            if (payload.size() >= safeLimit) {
                break;
            }
            Map<String, Object> dto = filterSourceId != null
                ? toJdbcDto(run, filterSourceId, includeDetails)
                : toDto(run, includeDetails);
            if (dto == null || dto.isEmpty()) {
                continue;
            }
            payload.add(dto);
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看采集运行历史");
        auditPayload.put("integration", normalized);
        auditPayload.put("limit", safeLimit);
        auditPayload.put("includeDetails", includeDetails);
        if (filterSourceId != null) {
            auditPayload.put("sourceId", filterSourceId.toString());
        }
        auditService.auditAction("CATALOG_SYNC_RUN_LIST", AuditStage.SUCCESS, normalized, auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/runs/{runId}/diagnostics")
    public ApiResponse<Map<String, Object>> getRunDiagnostics(
        @PathVariable UUID runId,
        @RequestParam(name = "sourceId", required = false) UUID sourceId
    ) {
        InfraCatalogSyncRun run = syncRunRepository
            .findById(runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采集运行记录不存在"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", runId.toString());
        payload.put("integration", run.getIntegration());
        payload.put("status", run.getStatus());
        payload.put("startedAt", run.getStartedAt());
        payload.put("finishedAt", run.getFinishedAt());
        payload.put("error", run.getError());
        payload.put("errorCategory", classifyError(run.getError()));
        payload.put("sourceId", sourceId != null ? sourceId.toString() : null);
        payload.put("logLines", extractRunLogLines(run, sourceId));
        auditService.auditAction(
            "CATALOG_SYNC_RUN_DIAG",
            AuditStage.SUCCESS,
            runId.toString(),
            Map.of("summary", "查看采集运行诊断日志", "runId", runId.toString(), "sourceId", sourceId != null ? sourceId.toString() : "")
        );
        return ApiResponses.ok(payload);
    }

    @GetMapping("/pipelines")
    public ApiResponse<List<Map<String, Object>>> listPipelines() {
        List<Map<String, Object>> pipelines = new ArrayList<>();
        if (inceptorRegistry.getActive().isPresent()) {
            pipelines.add(buildPrimaryPipeline());
        }
        pipelines.addAll(buildJdbcPipelines());
        auditService.auditAction("CATALOG_SYNC_PIPELINE_LIST", AuditStage.SUCCESS, "pipelines", Map.of("summary", "查看采集任务列表"));
        return ApiResponses.ok(pipelines);
    }

    public record SingleSyncRequest(String reason) {}

    @PostMapping("/jdbc/{sourceId}/run")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> triggerJdbcSync(
        @PathVariable UUID sourceId,
        @RequestBody(required = false) SingleSyncRequest body
    ) {
        InfraDataSource source = resolveJdbcSource(sourceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
        if (!isJdbcCandidate(source)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该数据源不支持元数据采集");
        }
        String reason = body != null && StringUtils.hasText(body.reason()) ? body.reason().trim() : "manual";

        InfraCatalogSyncRun run = new InfraCatalogSyncRun();
        run.setIntegration("JDBC");
        run.setReason("single:" + reason);
        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        run.setCatalogDatasetCountBefore(safeDatasetCount());
        try {
            syncRunRepository.save(run);
        } catch (Exception ignored) {}

        JdbcSyncResult result;
        try {
            // Manual single-source sync should reconcile stale metadata by default.
            result = jdbcSyncService.synchronize(source, run.getId(), Boolean.TRUE);
        } catch (RuntimeException ex) {
            result = JdbcSyncResult.failed(source.getId(), ex.getMessage());
        }
        run.setFinishedAt(Instant.now());
        run.setCatalogDatasetCountAfter(safeDatasetCount());
        run.setDatasetsCreated(result != null ? result.datasetsCreated() : null);
        run.setDatasetsUpdated(result != null ? result.datasetsUpdated() : null);
        run.setDatasetsRemoved(result != null ? result.datasetsRemoved() : null);
        run.setTablesCreated(result != null ? result.tablesCreated() : null);
        run.setColumnsImported(result != null ? result.columnsImported() : null);
        run.setError(result != null ? result.error() : "unknown");
        run.setStatus(result != null && "FAILED".equalsIgnoreCase(result.status()) ? "FAILED" : "SUCCESS");
        run.setDetailsJson(writeJson(Map.of("single", true, "result", result)));
        try {
            syncRunRepository.save(run);
        } catch (Exception ignored) {}

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", run.getId() != null ? run.getId().toString() : null);
        payload.put("status", run.getStatus());
        payload.put("finishedAt", run.getFinishedAt());
        payload.put("result", result);
        auditService.auditAction(
            "CATALOG_SYNC_TRIGGER",
            AuditStage.SUCCESS,
            sourceId.toString(),
            Map.of("summary", "触发单数据源采集", "sourceId", sourceId.toString(), "reason", reason)
        );
        return ApiResponses.ok(payload);
    }

    private Map<String, Object> statusPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("primary", Map.of(
            "inProgress", inceptorCoordinator.isSyncInProgress(),
            "last", inceptorCoordinator.currentStatus()
        ));
        payload.put("jdbc", Map.of(
            "inProgress", jdbcCoordinator.isSyncInProgress(),
            "last", jdbcCoordinator.currentStatus()
        ));
        return payload;
    }

    private Map<String, Object> toDto(InfraCatalogSyncRun run, boolean includeDetails) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (run == null) {
            return dto;
        }
        UUID id = run.getId();
        if (id != null) {
            dto.put("id", id.toString());
        }
        dto.put("integration", run.getIntegration());
        dto.put("reason", run.getReason());
        dto.put("status", run.getStatus());
        dto.put("startedAt", run.getStartedAt());
        dto.put("finishedAt", run.getFinishedAt());
        dto.put("error", run.getError());
        dto.put("catalogDatasetCountBefore", run.getCatalogDatasetCountBefore());
        dto.put("catalogDatasetCountAfter", run.getCatalogDatasetCountAfter());
        dto.put("tablesDiscovered", run.getTablesDiscovered());
        dto.put("datasetsCreated", run.getDatasetsCreated());
        dto.put("datasetsUpdated", run.getDatasetsUpdated());
        dto.put("datasetsRemoved", run.getDatasetsRemoved());
        dto.put("tablesCreated", run.getTablesCreated());
        dto.put("columnsImported", run.getColumnsImported());
        dto.put("errorCategory", classifyError(run.getError()));
        if (includeDetails) {
            dto.put("detailsJson", run.getDetailsJson());
        }
        return dto;
    }

    private Map<String, Object> toJdbcDto(InfraCatalogSyncRun run, UUID sourceId, boolean includeDetails) {
        if (run == null || sourceId == null) {
            return null;
        }
        JdbcSyncResult result = extractJdbcResult(run.getDetailsJson(), sourceId);
        if (result == null) {
            return null;
        }
        Map<String, Object> dto = toDto(run, includeDetails);
        dto.put("sourceId", sourceId.toString());
        dto.put("status", result.status());
        dto.put("error", result.error());
        dto.put("tablesDiscovered", result.tablesDiscovered());
        dto.put("datasetsCreated", result.datasetsCreated());
        dto.put("datasetsUpdated", result.datasetsUpdated());
        dto.put("datasetsRemoved", result.datasetsRemoved());
        dto.put("datasetsMarkedStale", result.datasetsMarkedStale());
        dto.put("datasetsPurged", result.datasetsPurged());
        dto.put("tablesCreated", result.tablesCreated());
        dto.put("columnsImported", result.columnsImported());
        dto.put("errorCategory", classifyError(result.error()));
        return dto;
    }

    private JdbcSyncResult extractJdbcResult(String detailsJson, UUID sourceId) {
        if (!StringUtils.hasText(detailsJson) || sourceId == null) {
            return null;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(detailsJson, new TypeReference<>() {});
            JdbcSyncResult single = extractSingleJdbcResult(raw, sourceId);
            if (single != null) {
                return single;
            }
            Object resultsObj = raw.get("results");
            List<JdbcSyncResult> results = objectMapper.convertValue(resultsObj, new TypeReference<List<JdbcSyncResult>>() {});
            if (results != null) {
                for (JdbcSyncResult result : results) {
                    if (result != null && sourceId.equals(result.sourceId())) {
                        return result;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private JdbcSyncResult extractSingleJdbcResult(Map<String, Object> raw, UUID sourceId) {
        if (raw == null || sourceId == null) {
            return null;
        }
        Object resultObj = raw.get("result");
        if (resultObj == null) {
            return null;
        }
        try {
            JdbcSyncResult result = objectMapper.convertValue(resultObj, JdbcSyncResult.class);
            return result != null && sourceId.equals(result.sourceId()) ? result : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Map<String, Object> buildPrimaryPipeline() {
        Map<String, Object> pipeline = new LinkedHashMap<>();
        IntegrationStatus status = inceptorCoordinator.currentStatus();
        InfraCatalogSyncRun lastRun = latestRun("INCEPTOR");
        boolean inProgress = inceptorCoordinator.isSyncInProgress();
        String schedule = catalogFeatures != null && catalogFeatures.isAutoSyncEnabled()
            ? "Cron: " + catalogFeatures.getAutoSyncCron()
            : "手动触发";
        String source = resolvePrimarySourceLabel();
        pipeline.put("id", "primary");
        pipeline.put("integration", "INCEPTOR");
        pipeline.put("name", "主数据采集");
        pipeline.put("source", source);
        pipeline.put("schedule", schedule);
        pipeline.put("lastRun", resolveTimestamp(status != null ? status.timestamp() : null, lastRun));
        pipeline.put("status", resolveStatus(inProgress, lastRun, status != null ? status.error() : null));
        pipeline.put("tablesFound", resolveTablesFound(lastRun, status != null ? status.tablesDiscovered() : null));
        pipeline.put("autoEnabled", catalogFeatures != null && catalogFeatures.isAutoSyncEnabled());
        pipeline.put("logLines", buildInceptorLogs(status));
        pipeline.put("error", status != null ? status.error() : (lastRun != null ? lastRun.getError() : null));
        return pipeline;
    }

    private List<Map<String, Object>> buildJdbcPipelines() {
        List<Map<String, Object>> pipelines = new ArrayList<>();
        List<InfraDataSource> sources = dataSourceRepository.findByStatusIgnoreCase("ACTIVE");
        if (sources == null) {
            sources = List.of();
        }
        InfraCatalogSyncRun lastRun = latestRun("JDBC");
        JdbcIntegrationStatus status = jdbcCoordinator.currentStatus();
        Map<UUID, JdbcSyncResult> resultMap = resolveJdbcResults(status, lastRun);
        boolean inProgress = jdbcCoordinator.isSyncInProgress();
        AdminInfraClient.AdminDataLakeConfig adminLake = adminInfraClient.fetchDefaultDataLake().orElse(null);
        String schedule = catalogFeatures != null && catalogFeatures.isAutoSyncEnabled()
            ? "Cron: " + catalogFeatures.getAutoSyncCron()
            : "手动触发";
        for (InfraDataSource source : sources) {
            if (!isJdbcCandidate(source)) {
                continue;
            }
            Map<String, Object> pipeline = new LinkedHashMap<>();
            UUID sourceId = source.getId();
            JdbcSyncResult result = sourceId != null ? resultMap.get(sourceId) : null;
            pipeline.put("id", sourceId != null ? sourceId.toString() : null);
            pipeline.put("sourceId", sourceId != null ? sourceId.toString() : null);
            pipeline.put("integration", "JDBC");
            String sourceName = source.getName();
            if (isSameAsAdminDefaultLake(source, adminLake) && StringUtils.hasText(sourceName)) {
                sourceName = sourceName + "（默认数据湖）";
            }
            pipeline.put("name", sourceName);
            pipeline.put("source", buildJdbcSourceLabel(source));
            pipeline.put("schedule", schedule);
            pipeline.put("lastRun", resolveTimestamp(status != null ? status.timestamp() : null, lastRun));
            pipeline.put("status", resolveJdbcStatus(inProgress, result, lastRun));
            pipeline.put("tablesFound", resolveJdbcTablesFound(result));
            pipeline.put("autoEnabled", catalogFeatures != null && catalogFeatures.isAutoSyncEnabled());
            pipeline.put("logLines", buildJdbcLogs(source, result));
            pipeline.put("error", result != null ? result.error() : (lastRun != null ? lastRun.getError() : null));
            pipelines.add(pipeline);
        }

        // If local infra_data_source does not include admin default data lake, expose it as a virtual JDBC pipeline.
        InfraDataSource virtualAdminSource = toVirtualAdminLakeSource(adminLake);
        if (virtualAdminSource != null && isJdbcCandidate(virtualAdminSource)) {
            UUID adminId = virtualAdminSource.getId();
            String adminJdbc = normalizeJdbcUrl(virtualAdminSource.getJdbcUrl());
            boolean duplicated = sources
                .stream()
                .filter(s -> s != null)
                .anyMatch(s ->
                    (adminId != null && adminId.equals(s.getId())) ||
                    (StringUtils.hasText(adminJdbc) && adminJdbc.equalsIgnoreCase(normalizeJdbcUrl(s.getJdbcUrl())))
                );
            if (!duplicated) {
                Map<String, Object> pipeline = new LinkedHashMap<>();
                JdbcSyncResult result = adminId != null ? resultMap.get(adminId) : null;
                pipeline.put("id", adminId != null ? adminId.toString() : null);
                pipeline.put("sourceId", adminId != null ? adminId.toString() : null);
                pipeline.put("integration", "JDBC");
                pipeline.put("name", virtualAdminSource.getName());
                pipeline.put("source", buildJdbcSourceLabel(virtualAdminSource));
                pipeline.put("schedule", schedule);
                pipeline.put("lastRun", resolveTimestamp(status != null ? status.timestamp() : null, lastRun));
                pipeline.put("status", resolveJdbcStatus(inProgress, result, lastRun));
                pipeline.put("tablesFound", resolveJdbcTablesFound(result));
                pipeline.put("autoEnabled", catalogFeatures != null && catalogFeatures.isAutoSyncEnabled());
                pipeline.put("logLines", buildJdbcLogs(virtualAdminSource, result));
                pipeline.put("error", result != null ? result.error() : (lastRun != null ? lastRun.getError() : null));
                pipelines.add(pipeline);
            }
        }

        return pipelines;
    }

    private boolean isSameAsAdminDefaultLake(InfraDataSource source, AdminInfraClient.AdminDataLakeConfig adminLake) {
        if (source == null || adminLake == null) {
            return false;
        }
        UUID adminId = adminLake.getId();
        if (adminId != null && adminId.equals(source.getId())) {
            return true;
        }
        String adminJdbc = normalizeJdbcUrl(adminLake.getJdbcUrl());
        String sourceJdbc = normalizeJdbcUrl(source.getJdbcUrl());
        return StringUtils.hasText(adminJdbc) && adminJdbc.equalsIgnoreCase(sourceJdbc);
    }

    private Optional<InfraDataSource> resolveJdbcSource(UUID sourceId) {
        if (sourceId == null) {
            return Optional.empty();
        }
        Optional<InfraDataSource> local = dataSourceRepository.findById(sourceId);
        if (local.isPresent()) {
            return local;
        }
        AdminInfraClient.AdminDataLakeConfig adminLake = adminInfraClient.fetchDefaultDataLake().orElse(null);
        InfraDataSource virtual = toVirtualAdminLakeSource(adminLake);
        if (virtual != null && sourceId.equals(virtual.getId())) {
            return Optional.of(virtual);
        }
        return Optional.empty();
    }

    private InfraDataSource toVirtualAdminLakeSource(AdminInfraClient.AdminDataLakeConfig adminLake) {
        if (adminLake == null || adminLake.getId() == null || !StringUtils.hasText(adminLake.getJdbcUrl())) {
            return null;
        }
        InfraDataSource source = new InfraDataSource();
        source.setId(adminLake.getId());
        source.setName(StringUtils.hasText(adminLake.getName()) ? adminLake.getName().trim() : "默认数据湖");
        source.setType(adminLake.getType());
        source.setJdbcUrl(adminLake.getJdbcUrl());
        source.setUsername(adminLake.getUsername());
        source.setStatus("ACTIVE");

        Map<String, Object> props = new LinkedHashMap<>();
        if (adminLake.getJdbcProperties() != null && !adminLake.getJdbcProperties().isEmpty()) {
            props.put("jdbcProperties", adminLake.getJdbcProperties());
        }
        if (StringUtils.hasText(adminLake.getPassword())) {
            // Virtual source fallback: JdbcCatalogSyncService can read plain password from props when secrets are unavailable.
            props.put("password", adminLake.getPassword());
        }
        if (StringUtils.hasText(adminLake.getDestinationDefinitionId())) {
            props.put("destinationDefinitionId", adminLake.getDestinationDefinitionId());
        }
        if (adminLake.getDestinationConfig() != null && !adminLake.getDestinationConfig().isEmpty()) {
            props.put("destinationConfig", adminLake.getDestinationConfig());
        }
        source.setProps(writeJson(props));
        return source;
    }

    private String normalizeJdbcUrl(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return null;
        }
        return jdbcUrl.trim();
    }

    private Map<UUID, JdbcSyncResult> resolveJdbcResults(JdbcIntegrationStatus status, InfraCatalogSyncRun lastRun) {
        Map<UUID, JdbcSyncResult> resultMap = new LinkedHashMap<>();
        if (status != null && status.results() != null && !status.results().isEmpty()) {
            for (JdbcSyncResult result : status.results()) {
                if (result != null && result.sourceId() != null) {
                    resultMap.put(result.sourceId(), result);
                }
            }
            return resultMap;
        }
        if (lastRun == null || !StringUtils.hasText(lastRun.getDetailsJson())) {
            return resultMap;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(lastRun.getDetailsJson(), new TypeReference<>() {});
            Object singleObj = raw.get("result");
            if (singleObj != null) {
                JdbcSyncResult single = objectMapper.convertValue(singleObj, JdbcSyncResult.class);
                if (single != null && single.sourceId() != null) {
                    resultMap.put(single.sourceId(), single);
                    return resultMap;
                }
            }
            Object resultsObj = raw.get("results");
            List<JdbcSyncResult> results = objectMapper.convertValue(resultsObj, new TypeReference<List<JdbcSyncResult>>() {});
            if (results != null) {
                for (JdbcSyncResult result : results) {
                    if (result != null && result.sourceId() != null) {
                        resultMap.put(result.sourceId(), result);
                    }
                }
            }
        } catch (Exception ignored) {}
        return resultMap;
    }

    private InfraCatalogSyncRun latestRun(String integration) {
        if (!StringUtils.hasText(integration)) {
            return null;
        }
        List<InfraCatalogSyncRun> runs = syncRunRepository.findTop100ByIntegrationOrderByStartedAtDesc(integration.trim().toUpperCase(Locale.ROOT));
        return runs.isEmpty() ? null : runs.get(0);
    }

    private String resolvePrimarySourceLabel() {
        return inceptorRegistry
            .getActive()
            .map(active -> {
                String database = active.database();
                if (StringUtils.hasText(database)) {
                    return "Inceptor / " + database.trim();
                }
                return "Inceptor / " + (StringUtils.hasText(active.name()) ? active.name().trim() : "default");
            })
            .orElse("Inceptor / 未配置");
    }

    private String buildJdbcSourceLabel(InfraDataSource source) {
        String type = StringUtils.hasText(source.getType()) ? source.getType().trim() : "JDBC";
        String database = extractJdbcDatabase(source.getJdbcUrl());
        if (StringUtils.hasText(database)) {
            return type + " / " + database;
        }
        return type;
    }

    private String extractJdbcDatabase(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) return null;
        String url = jdbcUrl.trim();
        int schemeIdx = url.indexOf("://");
        if (schemeIdx < 0) return null;
        String remainder = url.substring(schemeIdx + 3);
        int slashIdx = remainder.indexOf("/");
        if (slashIdx < 0) return null;
        String database = remainder.substring(slashIdx + 1);
        int qIdx = database.indexOf("?");
        if (qIdx >= 0) {
            database = database.substring(0, qIdx);
        }
        return StringUtils.hasText(database) ? database : null;
    }

    private String resolveStatus(boolean inProgress, InfraCatalogSyncRun lastRun, String error) {
        if (inProgress) return "RUNNING";
        if (lastRun != null && StringUtils.hasText(lastRun.getStatus())) {
            return lastRun.getStatus().toUpperCase(Locale.ROOT);
        }
        if (StringUtils.hasText(error)) return "FAILED";
        return "IDLE";
    }

    private String resolveJdbcStatus(boolean inProgress, JdbcSyncResult result, InfraCatalogSyncRun lastRun) {
        if (inProgress) return "RUNNING";
        if (result != null && StringUtils.hasText(result.status())) {
            return result.status().toUpperCase(Locale.ROOT);
        }
        if (lastRun != null && StringUtils.hasText(lastRun.getStatus())) {
            return lastRun.getStatus().toUpperCase(Locale.ROOT);
        }
        return "IDLE";
    }

    private Instant resolveTimestamp(Instant statusTime, InfraCatalogSyncRun lastRun) {
        if (statusTime != null) return statusTime;
        return lastRun != null ? lastRun.getStartedAt() : null;
    }

    private Integer resolveTablesFound(InfraCatalogSyncRun lastRun, Integer fallback) {
        if (lastRun != null && lastRun.getTablesDiscovered() != null) {
            return lastRun.getTablesDiscovered();
        }
        return fallback;
    }

    private Integer resolveJdbcTablesFound(JdbcSyncResult result) {
        if (result == null) return null;
        int total = 0;
        total += Math.max(result.tablesCreated(), 0);
        total += Math.max(result.datasetsCreated(), 0);
        total += Math.max(result.datasetsUpdated(), 0);
        return total;
    }

    private List<String> buildInceptorLogs(IntegrationStatus status) {
        if (status == null) return List.of("暂无运行记录");
        List<String> lines = new ArrayList<>();
        if (status.actions() != null && !status.actions().isEmpty()) {
            lines.addAll(status.actions());
        }
        if (StringUtils.hasText(status.error())) {
            lines.add("ERROR: " + status.error());
        }
        if (lines.isEmpty()) {
            lines.add("暂无运行记录");
        }
        return lines;
    }

    private List<String> buildJdbcLogs(InfraDataSource source, JdbcSyncResult result) {
        if (result == null) {
            return List.of("暂无运行记录");
        }
        List<String> lines = new ArrayList<>();
        String name = source != null && StringUtils.hasText(source.getName()) ? source.getName().trim() : "JDBC";
        lines.add(String.format("Source %s status: %s", name, normalizeText(result.status(), "UNKNOWN")));
        lines.add(String.format("Created %d, Updated %d, Removed %d", result.datasetsCreated(), result.datasetsUpdated(), result.datasetsRemoved()));
        lines.add(String.format("Marked stale %d, Purged %d", result.datasetsMarkedStale(), result.datasetsPurged()));
        lines.add(String.format("Tables %d, Columns %d", result.tablesCreated(), result.columnsImported()));
        if (StringUtils.hasText(result.error())) {
            lines.add(String.format("ERROR[%s]: %s", classifyError(result.error()), result.error()));
        }
        return lines;
    }

    private String normalizeText(String value, String fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> extractRunLogLines(InfraCatalogSyncRun run, UUID sourceId) {
        if (run == null) {
            return List.of("暂无运行记录");
        }
        String integration = normalizeText(run.getIntegration(), "");
        if ("INCEPTOR".equals(integration)) {
            IntegrationStatus detail = readInceptorStatus(run.getDetailsJson());
            if (detail != null && detail.actions() != null && !detail.actions().isEmpty()) {
                return detail.actions();
            }
            return StringUtils.hasText(run.getError()) ? List.of("ERROR: " + run.getError()) : List.of("暂无运行记录");
        }
        if ("JDBC".equals(integration)) {
            if (sourceId != null) {
                JdbcSyncResult result = extractJdbcResult(run.getDetailsJson(), sourceId);
                if (result != null) {
                    InfraDataSource source = dataSourceRepository.findById(sourceId).orElse(null);
                    return buildJdbcLogs(source, result);
                }
            }
            try {
                Map<String, Object> raw = objectMapper.readValue(
                    Optional.ofNullable(run.getDetailsJson()).orElse("{}"),
                    new TypeReference<>() {}
                );
                List<JdbcSyncResult> results = objectMapper.convertValue(raw.get("results"), new TypeReference<List<JdbcSyncResult>>() {});
                if (results != null && !results.isEmpty()) {
                    List<String> lines = new ArrayList<>();
                    for (JdbcSyncResult r : results) {
                        if (r == null) {
                            continue;
                        }
                        String sid = r.sourceId() != null ? r.sourceId().toString() : "unknown";
                        lines.add(String.format("[%s] %s", sid, normalizeText(r.status(), "UNKNOWN")));
                        if (StringUtils.hasText(r.error())) {
                            lines.add(String.format("[%s] ERROR[%s]: %s", sid, classifyError(r.error()), r.error()));
                        }
                    }
                    if (!lines.isEmpty()) {
                        return lines;
                    }
                }
            } catch (Exception ignored) {}
            return StringUtils.hasText(run.getError()) ? List.of("ERROR: " + run.getError()) : List.of("暂无运行记录");
        }
        return StringUtils.hasText(run.getError()) ? List.of("ERROR: " + run.getError()) : List.of("暂无运行记录");
    }

    private IntegrationStatus readInceptorStatus(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(detailsJson, IntegrationStatus.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String classifyError(String error) {
        if (!StringUtils.hasText(error)) {
            return "NONE";
        }
        String raw = error.toLowerCase(Locale.ROOT);
        if (raw.contains("permission denied") || raw.contains("access denied") || raw.contains("forbidden")) {
            return "AUTH";
        }
        if (raw.contains("timeout") || raw.contains("timed out")) {
            return "TIMEOUT";
        }
        if (raw.contains("connection refused") || raw.contains("connect") || raw.contains("network")) {
            return "NETWORK";
        }
        if (raw.contains("syntax") || raw.contains("sqlstate") || raw.contains("relation") || raw.contains("does not exist")) {
            return "SQL";
        }
        if (raw.contains("driver") || raw.contains("class not found") || raw.contains("plugin")) {
            return "DRIVER";
        }
        return "UNKNOWN";
    }

    private boolean isJdbcCandidate(InfraDataSource source) {
        if (source == null) return false;
        if (!StringUtils.hasText(source.getJdbcUrl())) return false;
        if (!StringUtils.hasText(source.getType())) return true;
        String normalized = source.getType().trim().toUpperCase(Locale.ROOT);
        if ("INCEPTOR".equals(normalized)) return false;
        Map<String, Object> props = readProps(source.getProps());
        String managedBy = stringProp(props, "managedBy");
        return !"PLATFORM".equalsIgnoreCase(managedBy);
    }

    private Map<String, Object> readProps(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String stringProp(Map<String, Object> props, String key) {
        if (props == null || key == null) return null;
        Object value = props.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private long safeDatasetCount() {
        try {
            return datasetRepository.count();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}
