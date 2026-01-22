package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.AirbyteProperties;
import com.yuzhi.dts.platform.domain.infra.InfraAirbyteConnection;
import com.yuzhi.dts.platform.domain.infra.InfraAirbyteSource;
import com.yuzhi.dts.platform.repository.infra.InfraAirbyteConnectionRepository;
import com.yuzhi.dts.platform.repository.infra.InfraAirbyteSourceRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.etl.AirbyteClient;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/infra/airbyte")
public class AirbyteResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";
    private static final String MASKED_SECRET = "******";
    private static final String DEFAULT_AIRBYTE_ORG_ID = "00000000-0000-0000-0000-000000000000";
    private static final List<AllowedSourceDefinition> ALLOWED_SOURCE_DEFS = List.of(
        new AllowedSourceDefinition("mysql", List.of("mysql"), "MySQL"),
        new AllowedSourceDefinition("oracle", List.of("oracle db", "oracle"), "Oracle"),
        new AllowedSourceDefinition("postgres", List.of("postgres"), "Postgres"),
        new AllowedSourceDefinition(
            "file",
            List.of("file (csv, json, excel, feather, parquet)", "airbyte/source-file"),
            "File (CSV, Excel)"
        ),
        new AllowedSourceDefinition("dameng", List.of("dameng", "dm8", "达梦"), "Dameng"),
        new AllowedSourceDefinition("mssql", List.of("microsoft sql server (mssql)", "mssql", "sql server"), "Microsoft SQL Server")
    );

    private final AirbyteClient airbyteClient;
    private final AirbyteProperties properties;
    private final InfraAirbyteConnectionRepository connectionRepository;
    private final InfraAirbyteSourceRepository sourceRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final InfraSecretService secretService;
    private final IngestionServiceClient ingestionClient;

    public AirbyteResource(
        AirbyteClient airbyteClient,
        AirbyteProperties properties,
        InfraAirbyteConnectionRepository connectionRepository,
        InfraAirbyteSourceRepository sourceRepository,
        AuditService auditService,
        ObjectMapper objectMapper,
        InfraSecretService secretService,
        IngestionServiceClient ingestionClient
    ) {
        this.airbyteClient = airbyteClient;
        this.properties = properties;
        this.connectionRepository = connectionRepository;
        this.sourceRepository = sourceRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.secretService = secretService;
        this.ingestionClient = ingestionClient;
    }

    public record AirbyteSourceRequest(
        String name,
        String sourceDefinitionId,
        String sourceId,
        Map<String, Object> config,
        Boolean enabled,
        String owner,
        String description
    ) {}

    public record AirbyteConnectionRequest(
        UUID infraSourceId,
        String name,
        String sourceDefinitionId,
        String sourceId,
        Map<String, Object> sourceConfig,
        String destinationId,
        String destinationDefinitionId,
        Map<String, Object> destinationConfig,
        String syncMode,
        String scheduleType,
        String scheduleCron,
        String namespace,
        String prefix,
        Boolean enabled,
        String owner,
        String description,
        List<String> selectedStreams,
        String schemaStrategy,
        String reconcileRule
    ) {}

    @GetMapping("/definitions/sources")
    public ApiResponse<List<Map<String, Object>>> listSourceDefinitions() {
        if (ingestionClient.isEnabled()) {
            return ingestionClient.listSourceDefinitions();
        }
        List<Map<String, Object>> list = extractList(airbyteClient.listSourceDefinitions(), "sourceDefinitions");
        list = filterAllowedSourceDefinitions(list);
        auditService.auditAction("AIRBYTE_SOURCE_DEF_LIST", AuditStage.SUCCESS, "airbyte", Map.of("summary", "获取源端定义"));
        return ApiResponses.ok(list);
    }

    @GetMapping("/sources")
    public ApiResponse<List<Map<String, Object>>> listSources() {
        if (ingestionClient.isEnabled()) {
            return ingestionClient.listSources();
        }
        List<InfraAirbyteSource> list = new ArrayList<>(sourceRepository.findAll());
        list.sort(Comparator.comparing(InfraAirbyteSource::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        List<Map<String, Object>> payload = list.stream().map(this::toSourceDto).toList();
        auditService.auditAction("AIRBYTE_SOURCE_LIST", AuditStage.SUCCESS, "airbyte", Map.of("summary", "查看数据源"));
        return ApiResponses.ok(payload);
    }

    @PostMapping("/sources")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createSource(@Valid @RequestBody AirbyteSourceRequest request) {
        if (ingestionClient.isEnabled()) {
            return ingestionClient.createSource(request);
        }
        InfraAirbyteSource saved = saveSource(null, request);
        auditService.auditAction(
            "AIRBYTE_SOURCE_CREATE",
            AuditStage.SUCCESS,
            saved.getId() != null ? saved.getId().toString() : "airbyte",
            Map.of("summary", "创建数据源", "name", saved.getName())
        );
        return ApiResponses.ok(toSourceDto(saved));
    }

    @PutMapping("/sources/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> updateSource(@PathVariable UUID id, @Valid @RequestBody AirbyteSourceRequest request) {
        if (ingestionClient.isEnabled()) {
            return ingestionClient.updateSource(id.toString(), request);
        }
        InfraAirbyteSource saved = saveSource(id, request);
        auditService.auditAction(
            "AIRBYTE_SOURCE_UPDATE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "更新数据源", "name", saved.getName())
        );
        return ApiResponses.ok(toSourceDto(saved));
    }

    @PostMapping("/sources/{id}/check")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> checkSource(@PathVariable UUID id) {
        if (ingestionClient.isEnabled()) {
            return ingestionClient.checkSource(id.toString());
        }
        InfraAirbyteSource source = sourceRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
        if (!StringUtils.hasText(source.getSourceId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Airbyte SourceId");
        }
        Map<String, Object> payload = airbyteClient.checkSourceConnection(source.getSourceId()).orElse(Map.of());
        String status = stringVal(payload.get("status"));
        if (StringUtils.hasText(status)) {
            source.setStatus(status.toUpperCase(Locale.ROOT));
        }
        source.setLastCheckedAt(Instant.now());
        sourceRepository.save(source);
        auditService.auditAction(
            "AIRBYTE_SOURCE_CHECK",
            AuditStage.SUCCESS,
            id.toString(),
            buildAuditMeta("测试数据源连接", "status", status)
        );
        return ApiResponses.ok(payload);
    }

    @PostMapping("/sources/{id}/discover")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> discoverSource(@PathVariable UUID id) {
        if (ingestionClient.isEnabled()) {
            return ingestionClient.discoverSource(id.toString());
        }
        InfraAirbyteSource source = sourceRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
        if (!StringUtils.hasText(source.getSourceId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Airbyte SourceId");
        }
        Map<String, Object> payload = airbyteClient.discoverSchema(source.getSourceId()).orElse(Map.of());
        source.setLastDiscoveredAt(Instant.now());
        sourceRepository.save(source);
        auditService.auditAction(
            "AIRBYTE_SOURCE_DISCOVER",
            AuditStage.SUCCESS,
            id.toString(),
            buildAuditMeta("刷新源端 Schema", null, null)
        );
        return ApiResponses.ok(payload);
    }

    @DeleteMapping("/sources/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Void> deleteSource(@PathVariable UUID id) {
        if (ingestionClient.isEnabled()) {
            ApiResponse<Map<String, Object>> response = ingestionClient.deleteSource(id.toString());
            if (response == null) {
                return new ApiResponse<>(500, "ingestion service error", null);
            }
            return new ApiResponse<>(response.getStatus(), response.getMessage(), null);
        }
        InfraAirbyteSource source = sourceRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
        long boundConnections = connectionRepository.countByInfraSourceId(id);
        if (boundConnections > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据源已被入湖配置引用，请先删除入湖任务");
        }
        if (properties.isEnabled() && StringUtils.hasText(source.getSourceId())) {
            Map<String, Object> resp = airbyteClient.deleteSource(source.getSourceId()).orElse(null);
            if (resp == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "删除 Airbyte 数据源失败");
            }
        }
        sourceRepository.delete(source);
        auditService.auditAction(
            "AIRBYTE_SOURCE_DELETE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "删除数据源", "name", source.getName())
        );
        return ApiResponses.ok(null);
    }

    @GetMapping("/definitions/destinations")
    public ApiResponse<List<Map<String, Object>>> listDestinationDefinitions() {
        if (ingestionClient.isEnabled()) {
            return ingestionClient.listDestinationDefinitions();
        }
        List<Map<String, Object>> list = extractList(airbyteClient.listDestinationDefinitions(), "destinationDefinitions");
        auditService.auditAction("AIRBYTE_DEST_DEF_LIST", AuditStage.SUCCESS, "airbyte", Map.of("summary", "获取目标端定义"));
        return ApiResponses.ok(list);
    }

    @GetMapping("/connections")
    public ApiResponse<List<Map<String, Object>>> listConnections(@RequestParam(defaultValue = "false") boolean refresh) {
        if (ingestionClient.isEnabled()) {
            return ingestionClient.listConnections(refresh);
        }
        List<InfraAirbyteConnection> list = new ArrayList<>(connectionRepository.findAll());
        list.sort(Comparator.comparing(InfraAirbyteConnection::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        if (refresh) {
            for (InfraAirbyteConnection entry : list) {
                refreshJobStatus(entry);
            }
        }
        List<Map<String, Object>> payload = list.stream().map(this::toDto).toList();
        auditService.auditAction("AIRBYTE_CONNECTION_LIST", AuditStage.SUCCESS, "airbyte", Map.of("summary", "查看接入配置"));
        return ApiResponses.ok(payload);
    }

    @PostMapping("/connections")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createConnection(@Valid @RequestBody AirbyteConnectionRequest request) {
        if (ingestionClient.isEnabled()) {
            return ingestionClient.createConnection(request);
        }
        InfraAirbyteConnection saved = saveConnection(null, request);
        auditService.auditAction(
            "AIRBYTE_CONNECTION_CREATE",
            AuditStage.SUCCESS,
            saved.getId() != null ? saved.getId().toString() : "airbyte",
            Map.of("summary", "创建入湖接入", "name", saved.getName())
        );
        return ApiResponses.ok(toDto(saved));
    }

    @PutMapping("/connections/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> updateConnection(
        @PathVariable UUID id,
        @Valid @RequestBody AirbyteConnectionRequest request
    ) {
        if (ingestionClient.isEnabled()) {
            return ingestionClient.updateConnection(id.toString(), request);
        }
        InfraAirbyteConnection saved = saveConnection(id, request);
        auditService.auditAction(
            "AIRBYTE_CONNECTION_UPDATE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "更新入湖接入", "name", saved.getName())
        );
        return ApiResponses.ok(toDto(saved));
    }

    @PostMapping("/connections/{id}/sync")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> triggerSync(@PathVariable UUID id) {
        if (ingestionClient.isEnabled()) {
            return ingestionClient.syncConnection(id.toString());
        }
        InfraAirbyteConnection connection = connectionRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "接入配置不存在"));
        if (!StringUtils.hasText(connection.getConnectionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Airbyte ConnectionId");
        }
        Map<String, Object> payload = airbyteClient.syncConnection(connection.getConnectionId()).orElse(Map.of());
        String jobId = stringVal(payload.get("jobId"));
        if (!StringUtils.hasText(jobId)) {
            jobId = stringVal(payload.get("job"));
        }
        if (payload.get("job") instanceof Map<?, ?> jobMap) {
            jobId = stringVal(((Map<?, ?>) jobMap).get("id"));
            connection.setLastJobStatus(stringVal(((Map<?, ?>) jobMap).get("status")));
        }
        if (StringUtils.hasText(jobId)) {
            connection.setLastJobId(jobId);
            connection.setLastJobStatus(StringUtils.hasText(connection.getLastJobStatus()) ? connection.getLastJobStatus() : "RUNNING");
            connection.setLastSyncAt(Instant.now());
            connectionRepository.save(connection);
        }
        auditService.auditAction(
            "AIRBYTE_CONNECTION_SYNC",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "触发入湖同步", "jobId", jobId)
        );
        return ApiResponses.ok(Map.of("jobId", jobId, "payload", payload));
    }

    @GetMapping("/connections/{id}/jobs")
    public ApiResponse<List<Map<String, Object>>> listJobs(@PathVariable UUID id, @RequestParam(defaultValue = "10") int limit) {
        if (ingestionClient.isEnabled()) {
            ApiResponse<List<Map<String, Object>>> response = ingestionClient.listJobs(id.toString(), limit);
            return response != null ? response : ApiResponses.ok(List.of());
        }
        InfraAirbyteConnection connection = connectionRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "接入配置不存在"));
        List<Map<String, Object>> jobs = extractList(airbyteClient.listJobs(connection.getConnectionId(), limit), "jobs");
        auditService.auditAction(
            "AIRBYTE_CONNECTION_JOB_LIST",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看入湖任务记录", "limit", limit)
        );
        return ApiResponses.ok(jobs);
    }

    private InfraAirbyteConnection saveConnection(UUID id, AirbyteConnectionRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        String workspaceId = resolveWorkspaceId();
        InfraAirbyteConnection entity = id == null
            ? new InfraAirbyteConnection()
            : connectionRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "接入配置不存在"));

        InfraAirbyteSource linkedSource = null;
        if (request.infraSourceId() != null) {
            linkedSource = sourceRepository.findById(request.infraSourceId()).orElse(null);
        }
        String sourceId = StringUtils.hasText(request.sourceId()) ? request.sourceId().trim() : null;
        if (!StringUtils.hasText(sourceId) && StringUtils.hasText(entity.getSourceId())) {
            sourceId = entity.getSourceId();
        }
        String sourceDefinitionId = normalize(request.sourceDefinitionId());
        if (!StringUtils.hasText(sourceDefinitionId) && StringUtils.hasText(entity.getSourceDefinitionId())) {
            sourceDefinitionId = entity.getSourceDefinitionId();
        }
        Map<String, Object> sourceConfig = request.sourceConfig();
        if (linkedSource != null) {
            if (!StringUtils.hasText(sourceId)) {
                sourceId = normalize(linkedSource.getSourceId());
            }
            if (!StringUtils.hasText(sourceDefinitionId)) {
                sourceDefinitionId = normalize(linkedSource.getSourceDefinitionId());
            }
            if (sourceConfig == null || sourceConfig.isEmpty()) {
                sourceConfig = parseJson(linkedSource.getConfigJson());
            }
        }
        Map<String, Object> existingSourceConfig = parseJson(entity.getSourceConfigJson());
        sourceConfig = mergeSecretConfig(sourceConfig, existingSourceConfig);
        if (!StringUtils.hasText(sourceId)) {
            if (!StringUtils.hasText(sourceDefinitionId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择源端类型");
            }
            if (sourceConfig == null || sourceConfig.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写源端连接配置");
            }
            Map<String, Object> resp = airbyteClient
                .createSource(workspaceId, sourceDefinitionId, request.name(), sourceConfig)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "创建源端失败"));
            sourceId = stringVal(resp.get("sourceId"));
        }

        String destinationId = normalize(request.destinationId());
        if (!StringUtils.hasText(destinationId) && StringUtils.hasText(entity.getDestinationId())) {
            destinationId = entity.getDestinationId();
        }
        if (!StringUtils.hasText(destinationId)) {
            destinationId = normalize(properties.getDefaultDestinationId());
        }
        if (!StringUtils.hasText(destinationId)) {
            String destinationDefinitionId = normalize(request.destinationDefinitionId());
            String fallbackDefinitionId = normalize(properties.getDefaultDestinationDefinitionId());
            String definitionId = StringUtils.hasText(destinationDefinitionId) ? destinationDefinitionId : fallbackDefinitionId;
            Map<String, Object> destinationConfig = request.destinationConfig();
            if (destinationConfig == null || destinationConfig.isEmpty()) {
                destinationConfig = parseJson(properties.getDefaultDestinationConfigJson());
            }
            if (!StringUtils.hasText(definitionId) || destinationConfig == null || destinationConfig.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少目标端配置");
            }
            Map<String, Object> resp = airbyteClient
                .createDestination(workspaceId, definitionId, properties.getDefaultDestinationName(), destinationConfig)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "创建目标端失败"));
            destinationId = stringVal(resp.get("destinationId"));
            if (StringUtils.hasText(destinationId)) {
                properties.setDefaultDestinationId(destinationId);
            }
        }

        Map<String, Object> catalogPayload = airbyteClient
            .discoverSchema(sourceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "源端 Schema 探查失败"));
        Map<String, Object> syncCatalog = buildSyncCatalog(catalogPayload, request.syncMode(), request.selectedStreams());

        String scheduleType = normalize(request.scheduleType());
        Map<String, Object> scheduleData = buildScheduleData(scheduleType, request.scheduleCron());
        String status = Boolean.FALSE.equals(request.enabled()) ? "inactive" : "active";

        Map<String, Object> connectionPayload = new LinkedHashMap<>();
        connectionPayload.put("name", request.name());
        connectionPayload.put("sourceId", sourceId);
        connectionPayload.put("destinationId", destinationId);
        connectionPayload.put("syncCatalog", syncCatalog);
        connectionPayload.put("status", status);
        if (StringUtils.hasText(request.namespace())) {
            connectionPayload.put("namespaceDefinition", "customformat");
            connectionPayload.put("namespaceFormat", request.namespace());
        } else {
            connectionPayload.put("namespaceDefinition", "destination");
        }
        if (StringUtils.hasText(request.prefix())) {
            connectionPayload.put("prefix", request.prefix());
        }
        if (StringUtils.hasText(scheduleType)) {
            connectionPayload.put("scheduleType", scheduleType);
        }
        if (scheduleData != null) {
            connectionPayload.put("scheduleData", scheduleData);
        }

        String connectionId = entity.getConnectionId();
        if (StringUtils.hasText(connectionId)) {
            connectionPayload.put("connectionId", connectionId);
            airbyteClient.updateConnection(connectionPayload);
        } else {
            Map<String, Object> resp = airbyteClient
                .createConnection(connectionPayload)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "创建接入任务失败"));
            connectionId = stringVal(resp.get("connectionId"));
            if (!StringUtils.hasText(connectionId)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "创建接入任务失败");
            }
        }

        entity.setName(request.name().trim());
        entity.setInfraSourceId(request.infraSourceId());
        entity.setSourceDefinitionId(sourceDefinitionId);
        entity.setSourceId(sourceId);
        entity.setDestinationId(destinationId);
        entity.setConnectionId(connectionId);
        entity.setSyncMode(normalize(request.syncMode()));
        entity.setScheduleType(scheduleType);
        entity.setScheduleCron(normalize(request.scheduleCron()));
        entity.setNamespace(normalize(request.namespace()));
        entity.setPrefix(normalize(request.prefix()));
        entity.setOwner(normalize(request.owner()));
        entity.setStatus(status.toUpperCase(Locale.ROOT));
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        entity.setDescription(normalize(request.description()));
        entity.setSchemaStrategy(normalize(request.schemaStrategy()));
        entity.setReconcileRule(normalize(request.reconcileRule()));
        entity.setSelectedStreamsJson(writeJsonValue(request.selectedStreams()));
        String sourceConfigJson = writeJson(sourceConfig);
        if (sourceConfigJson == null && StringUtils.hasText(entity.getSourceConfigJson())) {
            sourceConfigJson = entity.getSourceConfigJson();
        }
        Map<String, Object> existingDestinationConfig = parseJson(entity.getDestinationConfigJson());
        Map<String, Object> resolvedDestinationConfig = mergeSecretConfig(request.destinationConfig(), existingDestinationConfig);
        String destinationConfigJson = writeJson(resolvedDestinationConfig);
        if (destinationConfigJson == null && StringUtils.hasText(entity.getDestinationConfigJson())) {
            destinationConfigJson = entity.getDestinationConfigJson();
        }
        entity.setSourceConfigJson(sourceConfigJson);
        entity.setDestinationConfigJson(destinationConfigJson);
        return connectionRepository.save(entity);
    }

    private InfraAirbyteSource saveSource(UUID id, AirbyteSourceRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        String workspaceId = resolveWorkspaceId();
        InfraAirbyteSource entity = id == null
            ? new InfraAirbyteSource()
            : sourceRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));

        String sourceId = normalize(request.sourceId());
        if (!StringUtils.hasText(sourceId) && StringUtils.hasText(entity.getSourceId())) {
            sourceId = entity.getSourceId();
        }
        String sourceDefinitionId = normalize(request.sourceDefinitionId());
        if (!StringUtils.hasText(sourceDefinitionId) && StringUtils.hasText(entity.getSourceDefinitionId())) {
            sourceDefinitionId = entity.getSourceDefinitionId();
        }
        Map<String, Object> incomingConfig = request.config();
        Map<String, Object> existingConfig = parseJson(entity.getConfigJson());
        Map<String, Object> existingSecrets = secretService.readSecrets(entity);
        Map<String, Object> existingFull = mergeMaps(existingConfig, existingSecrets);
        Map<String, Object> resolvedConfig = incomingConfig;
        if (resolvedConfig == null || resolvedConfig.isEmpty()) {
            resolvedConfig = existingFull;
        }
        resolvedConfig = mergeSecretConfig(resolvedConfig, existingFull);
        if (!StringUtils.hasText(sourceId)) {
            if (!StringUtils.hasText(sourceDefinitionId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择源端类型");
            }
            if (resolvedConfig == null || resolvedConfig.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写源端连接配置");
            }
            Map<String, Object> resp = airbyteClient
                .createSource(workspaceId, sourceDefinitionId, request.name(), resolvedConfig)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "创建源端失败"));
            sourceId = stringVal(resp.get("sourceId"));
        } else if (resolvedConfig == null || resolvedConfig.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写源端连接配置");
        } else {
            airbyteClient.updateSource(sourceId, sourceDefinitionId, request.name(), resolvedConfig);
        }

        entity.setName(request.name().trim());
        entity.setSourceDefinitionId(sourceDefinitionId);
        entity.setSourceId(sourceId);
        entity.setOwner(normalize(request.owner()));
        entity.setDescription(normalize(request.description()));
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        Map<String, Object> sanitizedConfig = stripSecrets(resolvedConfig);
        Map<String, Object> resolvedSecrets = extractSecrets(resolvedConfig);
        secretService.applySecrets(entity, resolvedSecrets);
        String configJson = writeJson(sanitizedConfig);
        entity.setConfigJson(configJson);
        entity.setStatus(entity.getEnabled() ? "ACTIVE" : "INACTIVE");
        return sourceRepository.save(entity);
    }

    private void refreshJobStatus(InfraAirbyteConnection entry) {
        if (entry == null || !StringUtils.hasText(entry.getConnectionId())) {
            return;
        }
        Map<String, Object> payload = airbyteClient.listJobs(entry.getConnectionId(), 1).orElse(Map.of());
        List<Map<String, Object>> jobs = extractList(Optional.of(payload), "jobs");
        if (jobs.isEmpty()) {
            return;
        }
        Map<String, Object> job = jobs.get(0);
        String status = stringVal(job.get("status"));
        String jobId = stringVal(job.get("id"));
        String createdAt = stringVal(job.get("createdAt"));
        entry.setLastJobId(jobId);
        entry.setLastJobStatus(status);
        if (StringUtils.hasText(createdAt)) {
            try {
                entry.setLastSyncAt(Instant.ofEpochMilli(Long.parseLong(createdAt)));
            } catch (Exception ignore) {}
        }
        connectionRepository.save(entry);
    }

    private String resolveWorkspaceId() {
        String workspaceId = properties.getWorkspaceId();
        if (StringUtils.hasText(workspaceId)) {
            if (airbyteClient.getWorkspace(workspaceId).isPresent()) {
                return workspaceId;
            }
            if (shouldCleanupWorkspaceIds(workspaceId)) {
                cleanupStaleAirbyteIds();
            }
            properties.setWorkspaceId(null);
        }
        String organizationId = resolveOrganizationId();
        List<Map<String, Object>> workspaces = extractList(airbyteClient.listWorkspacesByOrganizationId(organizationId), "workspaces");
        if (!workspaces.isEmpty()) {
            workspaceId = stringVal(workspaces.get(0).get("workspaceId"));
            if (StringUtils.hasText(workspaceId)) {
                properties.setWorkspaceId(workspaceId);
                return workspaceId;
            }
        }
        Map<String, Object> created = airbyteClient
            .createWorkspace("dts-platform", organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "创建 Airbyte workspace 失败"));
        workspaceId = stringVal(created.get("workspaceId"));
        if (StringUtils.hasText(workspaceId)) {
            properties.setWorkspaceId(workspaceId);
            return workspaceId;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Airbyte workspace 未配置，请在配置中设置 DTS_AIRBYTE_WORKSPACE_ID");
    }

    private boolean shouldCleanupWorkspaceIds(String workspaceId) {
        Optional<Map<String, Object>> payloadOpt = airbyteClient.listWorkspaces();
        if (payloadOpt.isEmpty()) {
            return false;
        }
        List<Map<String, Object>> workspaces = extractList(payloadOpt, "workspaces");
        if (workspaces.isEmpty()) {
            return true;
        }
        for (Map<String, Object> workspace : workspaces) {
            if (workspaceId.equals(stringVal(workspace.get("workspaceId")))) {
                return false;
            }
        }
        return true;
    }

    private void cleanupStaleAirbyteIds() {
        List<InfraAirbyteSource> sources = sourceRepository.findAll();
        boolean sourceChanged = false;
        for (InfraAirbyteSource source : sources) {
            if (!StringUtils.hasText(source.getSourceId())) {
                continue;
            }
            source.setSourceId(null);
            source.setStatus("STALE");
            source.setLastCheckedAt(null);
            source.setLastDiscoveredAt(null);
            sourceChanged = true;
        }
        if (sourceChanged) {
            sourceRepository.saveAll(sources);
        }

        List<InfraAirbyteConnection> connections = connectionRepository.findAll();
        boolean connectionChanged = false;
        for (InfraAirbyteConnection connection : connections) {
            if (
                !StringUtils.hasText(connection.getConnectionId()) &&
                !StringUtils.hasText(connection.getSourceId()) &&
                !StringUtils.hasText(connection.getDestinationId())
            ) {
                continue;
            }
            connection.setConnectionId(null);
            connection.setSourceId(null);
            connection.setDestinationId(null);
            connection.setLastJobId(null);
            connection.setLastJobStatus(null);
            connection.setLastSyncAt(null);
            connection.setStatus("STALE");
            connectionChanged = true;
        }
        if (connectionChanged) {
            connectionRepository.saveAll(connections);
        }
    }

    private String resolveOrganizationId() {
        String organizationId = properties.getOrganizationId();
        if (StringUtils.hasText(organizationId)) {
            return organizationId;
        }
        List<Map<String, Object>> defaultWorkspaces = extractList(
            airbyteClient.listWorkspacesByOrganizationId(DEFAULT_AIRBYTE_ORG_ID),
            "workspaces"
        );
        if (!defaultWorkspaces.isEmpty()) {
            properties.setOrganizationId(DEFAULT_AIRBYTE_ORG_ID);
            return DEFAULT_AIRBYTE_ORG_ID;
        }
        String userId = resolveAirbyteUserId();
        List<Map<String, Object>> organizations = extractList(airbyteClient.listOrganizationsByUserId(userId), "organizations");
        if (!organizations.isEmpty()) {
            organizationId = stringVal(organizations.get(0).get("organizationId"));
            if (StringUtils.hasText(organizationId)) {
                properties.setOrganizationId(organizationId);
                return organizationId;
            }
        }
        Map<String, Object> created = airbyteClient
            .createOrganization(userId, properties.getOrganizationName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "创建 Airbyte organization 失败"));
        organizationId = stringVal(created.get("organizationId"));
        if (StringUtils.hasText(organizationId)) {
            properties.setOrganizationId(organizationId);
            return organizationId;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Airbyte organization 未配置，请在配置中设置 DTS_AIRBYTE_ORGANIZATION_ID");
    }

    private String resolveAirbyteUserId() {
        String authUserId = properties.getAuthUserId();
        Map<String, Object> payload = airbyteClient
            .getUserByAuthId(authUserId)
            .orElseGet(
                () ->
                    airbyteClient
                        .getOrCreateUserByAuthId(authUserId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "创建 Airbyte 用户失败"))
            );
        String userId = stringVal(payload.get("userId"));
        if (!StringUtils.hasText(userId)) {
            Map<String, Object> userRead = asMap(payload.get("userRead"));
            userId = stringVal(userRead.get("userId"));
        }
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Airbyte 用户未创建成功，请检查配置");
    }

    private List<Map<String, Object>> extractList(Optional<Map<String, Object>> payloadOpt, String key) {
        if (payloadOpt == null || payloadOpt.isEmpty()) {
            return List.of();
        }
        Object value = payloadOpt.get().get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(new LinkedHashMap(map));
            }
        }
        return out;
    }

    private Map<String, Object> buildSyncCatalog(Map<String, Object> payload, String requestedMode, List<String> selectedStreams) {
        Map<String, Object> catalog = asMap(payload.get("catalog"));
        List<Map<String, Object>> streams = asListOfMaps(catalog.get("streams"));
        if (streams.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未发现可同步的表结构");
        }
        java.util.Set<String> allowed = normalizeStreams(selectedStreams);
        List<Map<String, Object>> configured = new ArrayList<>();
        for (Map<String, Object> streamItem : streams) {
            Map<String, Object> stream = asMap(streamItem.get("stream"));
            String streamName = stringVal(stream.get("name"));
            if (!allowed.isEmpty() && (streamName == null || !allowed.contains(streamName))) {
                continue;
            }
            List<String> supportedModes = asStringList(streamItem.get("supported_sync_modes"));
            String syncMode = resolveSyncMode(requestedMode, supportedModes);
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("sync_mode", syncMode);
            config.put("destination_sync_mode", "incremental".equals(syncMode) ? "append" : "overwrite");
            config.put("selected", true);
            List<List<String>> cursorField = toStringLists(streamItem.get("default_cursor_field"));
            if (!cursorField.isEmpty() && "incremental".equals(syncMode)) {
                config.put("cursor_field", cursorField.get(0));
            }
            List<List<String>> primaryKey = toStringLists(stream.get("source_defined_primary_key"));
            if (!primaryKey.isEmpty()) {
                config.put("primary_key", primaryKey);
            }
            configured.add(Map.of("stream", stream, "config", config));
        }
        if (configured.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未选择需要同步的表");
        }
        return Map.of("streams", configured);
    }

    private Map<String, Object> buildScheduleData(String scheduleType, String cron) {
        if (!StringUtils.hasText(scheduleType)) {
            return null;
        }
        String normalized = scheduleType.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("cron")) {
            return null;
        }
        String expression = StringUtils.hasText(cron) ? cron.trim() : null;
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        return Map.of("cron", Map.of("cronExpression", expression, "cronTimeZone", "UTC"));
    }

    private String resolveSyncMode(String requestedMode, List<String> supportedModes) {
        String target = normalize(requestedMode);
        if (target == null) {
            target = "FULL_REFRESH";
        }
        String normalized = target.toUpperCase(Locale.ROOT);
        boolean supportsIncremental = supportedModes.stream().anyMatch(mode -> "INCREMENTAL".equalsIgnoreCase(mode));
        if ("INCREMENTAL".equals(normalized) || "CDC".equals(normalized)) {
            return supportsIncremental ? "incremental" : "full_refresh";
        }
        return "full_refresh";
    }

    private Map<String, Object> toDto(InfraAirbyteConnection entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (entity.getId() != null) {
            dto.put("id", entity.getId().toString());
        }
        dto.put("name", entity.getName());
        dto.put("infraSourceId", entity.getInfraSourceId());
        dto.put("sourceDefinitionId", entity.getSourceDefinitionId());
        dto.put("sourceId", entity.getSourceId());
        dto.put("destinationId", entity.getDestinationId());
        dto.put("connectionId", entity.getConnectionId());
        dto.put("syncMode", entity.getSyncMode());
        dto.put("scheduleType", entity.getScheduleType());
        dto.put("scheduleCron", entity.getScheduleCron());
        dto.put("namespace", entity.getNamespace());
        dto.put("prefix", entity.getPrefix());
        dto.put("owner", entity.getOwner());
        dto.put("status", entity.getStatus());
        dto.put("lastJobId", entity.getLastJobId());
        dto.put("lastJobStatus", entity.getLastJobStatus());
        dto.put("lastSyncAt", entity.getLastSyncAt());
        dto.put("enabled", entity.getEnabled());
        dto.put("description", entity.getDescription());
        dto.put("schemaStrategy", entity.getSchemaStrategy());
        dto.put("reconcileRule", entity.getReconcileRule());
        dto.put("selectedStreams", parseJsonList(entity.getSelectedStreamsJson()));
        dto.put("sourceConfig", maskSecrets(parseJson(entity.getSourceConfigJson())));
        dto.put("destinationConfig", maskSecrets(parseJson(entity.getDestinationConfigJson())));
        return dto;
    }

    private Map<String, Object> toSourceDto(InfraAirbyteSource entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (entity.getId() != null) {
            dto.put("id", entity.getId().toString());
        }
        dto.put("name", entity.getName());
        dto.put("sourceDefinitionId", entity.getSourceDefinitionId());
        dto.put("sourceId", entity.getSourceId());
        dto.put("status", entity.getStatus());
        dto.put("lastCheckedAt", entity.getLastCheckedAt());
        dto.put("lastDiscoveredAt", entity.getLastDiscoveredAt());
        dto.put("owner", entity.getOwner());
        dto.put("enabled", entity.getEnabled());
        dto.put("description", entity.getDescription());
        Map<String, Object> config = parseJson(entity.getConfigJson());
        Map<String, Object> secrets = secretService.readSecrets(entity);
        Map<String, Object> maskedSecrets = maskSecrets(secrets);
        dto.put("config", mergeMaps(config, maskedSecrets));
        return dto;
    }

    private Map<String, Object> mergeSecretConfig(Map<String, Object> incoming, Map<String, Object> existing) {
        if ((incoming == null || incoming.isEmpty()) && (existing == null || existing.isEmpty())) {
            return incoming;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (existing != null) {
            result.putAll(existing);
        }
        if (incoming == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            String key = entry.getKey();
            Object incomingValue = entry.getValue();
            Object existingValue = existing != null ? existing.get(key) : null;
            if (incomingValue instanceof Map<?, ?> incomingMap && existingValue instanceof Map<?, ?> existingMap) {
                result.put(key, mergeSecretConfig(asMap(incomingMap), asMap(existingMap)));
                continue;
            }
            if (isSecretKey(key)) {
                if (incomingValue == null) {
                    if (existingValue != null) {
                        result.put(key, existingValue);
                    } else {
                        result.put(key, null);
                    }
                    continue;
                }
                String text = String.valueOf(incomingValue).trim();
                if (text.isEmpty() || MASKED_SECRET.equals(text)) {
                    if (existingValue != null) {
                        result.put(key, existingValue);
                    } else {
                        result.put(key, incomingValue);
                    }
                    continue;
                }
            } else if (incomingValue == null && existingValue != null) {
                result.put(key, existingValue);
                continue;
            }
            result.put(key, incomingValue);
        }
        return result;
    }

    private Map<String, Object> mergeMaps(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (base != null) {
            result.putAll(base);
        }
        if (override == null || override.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            String key = entry.getKey();
            Object next = mergeNodes(result.get(key), entry.getValue());
            result.put(key, next);
        }
        return result;
    }

    private Object mergeNodes(Object base, Object override) {
        if (override == null) {
            return base;
        }
        if (base instanceof Map<?, ?> baseMap && override instanceof Map<?, ?> overrideMap) {
            return mergeMaps(asMap(baseMap), asMap(overrideMap));
        }
        if (base instanceof List<?> baseList && override instanceof List<?> overrideList) {
            return mergeLists(baseList, overrideList);
        }
        return override;
    }

    private List<Object> mergeLists(List<?> base, List<?> override) {
        int size = Math.max(base != null ? base.size() : 0, override != null ? override.size() : 0);
        List<Object> merged = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Object baseValue = base != null && i < base.size() ? base.get(i) : null;
            Object overrideValue = override != null && i < override.size() ? override.get(i) : null;
            merged.add(mergeNodes(baseValue, overrideValue));
        }
        return merged;
    }

    private Map<String, Object> extractSecrets(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSecretKey(key)) {
                out.put(key, value);
                continue;
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> child = extractSecrets(asMap(map));
                if (!child.isEmpty()) {
                    out.put(key, child);
                }
                continue;
            }
            if (value instanceof List<?> list) {
                List<Object> child = extractSecretList(list);
                if (!child.isEmpty()) {
                    out.put(key, child);
                }
            }
        }
        return out;
    }

    private List<Object> extractSecretList(List<?> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Object> out = new ArrayList<>(list.size());
        boolean hasSecrets = false;
        for (Object item : list) {
            Object extracted = null;
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> child = extractSecrets(asMap(map));
                if (!child.isEmpty()) {
                    extracted = child;
                    hasSecrets = true;
                }
            } else if (item instanceof List<?> inner) {
                List<Object> child = extractSecretList(inner);
                if (!child.isEmpty()) {
                    extracted = child;
                    hasSecrets = true;
                }
            }
            out.add(extracted);
        }
        return hasSecrets ? out : List.of();
    }

    private Map<String, Object> stripSecrets(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSecretKey(key)) {
                continue;
            }
            if (value instanceof Map<?, ?> map) {
                out.put(key, stripSecrets(asMap(map)));
                continue;
            }
            if (value instanceof List<?> list) {
                out.put(key, stripSecretsList(list));
                continue;
            }
            out.put(key, value);
        }
        return out;
    }

    private List<Object> stripSecretsList(List<?> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Object> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(stripSecrets(asMap(map)));
            } else if (item instanceof List<?> inner) {
                out.add(stripSecretsList(inner));
            } else {
                out.add(item);
            }
        }
        return out;
    }

    private Map<String, Object> buildAuditMeta(String summary, String key, Object value) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("summary", summary);
        if (StringUtils.hasText(key) && value != null) {
            meta.put(key, value);
        }
        return meta;
    }

    private Map<String, Object> maskSecrets(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return payload;
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSecretKey(key) && value != null) {
                masked.put(key, MASKED_SECRET);
                continue;
            }
            if (value instanceof Map<?, ?> map) {
                masked.put(key, maskSecrets(asMap(map)));
                continue;
            }
            if (value instanceof List<?> list) {
                masked.put(key, maskListSecrets(list));
                continue;
            }
            masked.put(key, value);
        }
        return masked;
    }

    private List<Object> maskListSecrets(List<?> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Object> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(maskSecrets(asMap(map)));
            } else if (item instanceof List<?> inner) {
                out.add(maskListSecrets(inner));
            } else {
                out.add(item);
            }
        }
        return out;
    }

    private List<Map<String, Object>> filterAllowedSourceDefinitions(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (AllowedSourceDefinition allowed : ALLOWED_SOURCE_DEFS) {
            Map<String, Object> match = findDefinition(list, allowed);
            if (match == null) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>(match);
            if (StringUtils.hasText(allowed.displayName())) {
                normalized.put("name", allowed.displayName());
            }
            filtered.add(normalized);
        }
        return filtered;
    }

    private Map<String, Object> findDefinition(List<Map<String, Object>> list, AllowedSourceDefinition allowed) {
        for (Map<String, Object> item : list) {
            String name = normalizeLookup(item.get("name"));
            String repo = normalizeLookup(item.get("dockerRepository"));
            if (allowed.matches(name, repo)) {
                return item;
            }
        }
        return null;
    }

    private String normalizeLookup(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text.toLowerCase(Locale.ROOT) : "";
    }

    private record AllowedSourceDefinition(String key, List<String> matchers, String displayName) {
        boolean matches(String name, String repo) {
            if (!StringUtils.hasText(name) && !StringUtils.hasText(repo)) {
                return false;
            }
            for (String matcher : matchers) {
                String needle = StringUtils.hasText(matcher) ? matcher.trim().toLowerCase(Locale.ROOT) : "";
                if (!StringUtils.hasText(needle)) {
                    continue;
                }
                if (StringUtils.hasText(name) && (name.equals(needle) || name.contains(needle))) {
                    return true;
                }
                if (StringUtils.hasText(repo) && repo.contains(needle)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean isSecretKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("primary_key") || normalized.contains("foreign_key")) {
            return false;
        }
        return normalized.contains("password")
            || normalized.contains("passwd")
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("api_key")
            || normalized.contains("access_key")
            || normalized.contains("secret_key")
            || normalized.contains("client_secret")
            || normalized.equals("key")
            || normalized.endsWith("_key");
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap(map);
        }
        return Map.of();
    }

    private List<Map<String, Object>> asListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(new LinkedHashMap(map));
            }
        }
        return out;
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item == null) continue;
            out.add(String.valueOf(item));
        }
        return out;
    }

    private List<List<String>> toStringLists(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<List<String>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof List<?> inner) {
                List<String> innerList = new ArrayList<>();
                for (Object sub : inner) {
                    if (sub != null) {
                        innerList.add(String.valueOf(sub));
                    }
                }
                out.add(innerList);
            }
        }
        return out;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String writeJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String writeJsonValue(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof java.util.Collection<?> collection && collection.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignore) {
            return null;
        }
    }

    private Map<String, Object> parseJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignore) {
            return null;
        }
    }

    private List<String> parseJsonList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return list == null ? List.of() : list;
        } catch (Exception ignore) {
            return List.of();
        }
    }

    private java.util.Set<String> normalizeStreams(List<String> streams) {
        if (streams == null || streams.isEmpty()) {
            return java.util.Set.of();
        }
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (String stream : streams) {
            if (stream == null) continue;
            String trimmed = stream.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
