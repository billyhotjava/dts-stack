package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.repository.infra.InfraAirbyteSourceRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.etl.AirbyteClient;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.openmetadata.OpenMetadataAdapter;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionTaskResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final AirbyteClient airbyteClient;
    private final AirbyteResource airbyteResource;
    private final InfraAirbyteSourceRepository sourceRepository;
    private final AuditService auditService;
    private final OpenMetadataAdapter openMetadataAdapter;
    private final AirflowAdapter airflowAdapter;

    public IngestionTaskResource(
        AirbyteClient airbyteClient,
        AirbyteResource airbyteResource,
        InfraAirbyteSourceRepository sourceRepository,
        AuditService auditService,
        OpenMetadataAdapter openMetadataAdapter,
        AirflowAdapter airflowAdapter
    ) {
        this.airbyteClient = airbyteClient;
        this.airbyteResource = airbyteResource;
        this.sourceRepository = sourceRepository;
        this.auditService = auditService;
        this.openMetadataAdapter = openMetadataAdapter;
        this.airflowAdapter = airflowAdapter;
    }

    public record IngestionTaskRequest(
        String name,
        String owner,
        String description,
        SourceSpec source,
        DestinationSpec destination,
        SyncSpec sync,
        StreamsSpec streams,
        SchemaChangeSpec schemaChanges,
        LineageSpec lineage,
        AirflowSpec airflow,
        Boolean runNow
    ) {}

    public record SourceSpec(String type, String definitionId, String existingSourceId, Map<String, Object> config) {}

    public record DestinationSpec(Boolean usePlatformDefault, String definitionId, String existingDestinationId, Map<String, Object> config) {}

    public record SyncSpec(String mode, String destinationMode, ScheduleSpec schedule, NamespaceSpec namespace, String prefix) {}

    public record ScheduleSpec(String type, String cron, Integer intervalMinutes) {}

    public record NamespaceSpec(String definition, String format) {}

    public record StreamsSpec(String selection, List<String> include, List<String> exclude) {}

    public record SchemaChangeSpec(String mode) {}

    public record LineageSpec(Boolean enabled, String domain, List<String> tags, String owner) {}

    public record AirflowSpec(Boolean enabled, String dagId, String scheduleType, String cron, Integer intervalMinutes) {}

    @PostMapping("/tasks")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createTask(@Valid @RequestBody IngestionTaskRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务名称不能为空");
        }
        if (request.source() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少源端配置");
        }
        String sourceDefinitionId = resolveSourceDefinitionId(request.source());
        if (!StringUtils.hasText(sourceDefinitionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未匹配到可用的源端类型");
        }
        String sourceId = normalize(request.source().existingSourceId());
        ApiResponse<Map<String, Object>> sourceResponse;
        AirbyteResource.AirbyteSourceRequest sourceRequest = new AirbyteResource.AirbyteSourceRequest(
            request.name(),
            sourceDefinitionId,
            sourceId,
            request.source().config(),
            Boolean.TRUE,
            normalize(request.owner()),
            normalize(request.description())
        );
        if (StringUtils.hasText(sourceId)) {
            sourceResponse = sourceRepository
                .findBySourceId(sourceId)
                .map(existing -> airbyteResource.updateSource(existing.getId(), sourceRequest))
                .orElseGet(() -> airbyteResource.createSource(sourceRequest));
        } else {
            sourceResponse = airbyteResource.createSource(sourceRequest);
        }
        Map<String, Object> sourcePayload = requirePayload(sourceResponse, "源端配置创建失败");
        String infraSourceId = stringVal(sourcePayload.get("id"));
        sourceId = stringVal(sourcePayload.get("sourceId"));
        if (!StringUtils.hasText(sourceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "源端创建失败");
        }

        List<String> selectedStreams = resolveSelectedStreams(sourceId, request.streams());
        String scheduleType = normalizeScheduleType(request.sync());
        String scheduleCron = normalizeScheduleCron(request.sync());
        String namespaceFormat = resolveNamespaceFormat(request.sync());

        AirbyteResource.AirbyteConnectionRequest connectionRequest = new AirbyteResource.AirbyteConnectionRequest(
            parseUuid(infraSourceId),
            request.name(),
            sourceDefinitionId,
            sourceId,
            null,
            resolveDestinationId(request.destination()),
            normalize(request.destination() == null ? null : request.destination().definitionId()),
            request.destination() == null ? null : request.destination().config(),
            normalize(request.sync() == null ? null : request.sync().mode()),
            scheduleType,
            scheduleCron,
            namespaceFormat,
            normalize(request.sync() == null ? null : request.sync().prefix()),
            Boolean.TRUE,
            normalize(request.owner()),
            normalize(request.description()),
            selectedStreams,
            request.schemaChanges() == null ? null : normalize(request.schemaChanges().mode()),
            null
        );
        ApiResponse<Map<String, Object>> connectionResponse = airbyteResource.createConnection(connectionRequest);
        Map<String, Object> connectionPayload = requirePayload(connectionResponse, "接入任务创建失败");
        String connectionId = stringVal(connectionPayload.get("id"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", sourcePayload);
        result.put("connection", connectionPayload);
        if (Boolean.TRUE.equals(request.runNow()) && StringUtils.hasText(connectionId)) {
            ApiResponse<Map<String, Object>> sync = airbyteResource.triggerSync(parseUuid(connectionId));
            result.put("job", sync.getData());
        }

        Map<String, Object> lineageResult = openMetadataAdapter.registerLineage(
            toLineageRequest(request.lineage()),
            new OpenMetadataAdapter.LineageContext(
                request.name(),
                request.source().type(),
                namespaceFormat,
                normalize(request.sync() == null ? null : request.sync().prefix()),
                asMap(sourcePayload.get("config")),
                asMap(connectionPayload.get("destinationConfig")),
                resolveStreamRefs(sourceId, request.streams())
            )
        );
        if (lineageResult != null && !lineageResult.isEmpty()) {
            result.put("lineage", lineageResult);
        }

        Map<String, Object> ingestionResult = openMetadataAdapter.ensureMetadataIngestion(
            new OpenMetadataAdapter.IngestionContext(
                request.name(),
                asMap(connectionPayload.get("destinationConfig")),
                scheduleCron,
                Boolean.TRUE.equals(request.runNow())
            )
        );
        if (ingestionResult != null && !ingestionResult.isEmpty()) {
            result.put("openmetadataIngestion", ingestionResult);
        }

        Map<String, Object> airflowResult = airflowAdapter.triggerIfRequested(
            toAirflowRequest(request.airflow()),
            connectionId,
            sourceId,
            request.name(),
            Boolean.TRUE.equals(request.runNow())
        );
        if (airflowResult != null && !airflowResult.isEmpty()) {
            result.put("airflow", airflowResult);
        }

        auditService.auditAction(
            "INGESTION_TASK_CREATE",
            AuditStage.SUCCESS,
            connectionId != null ? connectionId : "ingestion",
            Map.of("summary", "创建入湖任务", "name", request.name())
        );
        return ApiResponses.ok(result);
    }

    private String resolveSourceDefinitionId(SourceSpec source) {
        if (source == null) {
            return null;
        }
        String definitionId = normalize(source.definitionId());
        if (StringUtils.hasText(definitionId)) {
            return definitionId;
        }
        String type = normalize(source.type());
        if (!StringUtils.hasText(type)) {
            return null;
        }
        List<Map<String, Object>> definitions = extractList(airbyteClient.listSourceDefinitions(), "sourceDefinitions");
        for (Map<String, Object> item : definitions) {
            String name = normalize(item.get("name"));
            String repo = normalize(item.get("dockerRepository"));
            if (matchesType(type, name) || matchesType(type, repo)) {
                String id = stringVal(item.get("sourceDefinitionId"));
                if (StringUtils.hasText(id)) {
                    return id;
                }
            }
        }
        return null;
    }

    private boolean matchesType(String type, String candidate) {
        if (!StringUtils.hasText(type) || !StringUtils.hasText(candidate)) {
            return false;
        }
        String needle = type.toLowerCase(Locale.ROOT);
        String haystack = candidate.toLowerCase(Locale.ROOT);
        return haystack.equals(needle) || haystack.contains(needle);
    }

    private String resolveDestinationId(DestinationSpec destination) {
        if (destination == null) {
            return null;
        }
        String existing = normalize(destination.existingDestinationId());
        if (StringUtils.hasText(existing)) {
            return existing;
        }
        if (Boolean.FALSE.equals(destination.usePlatformDefault())) {
            return null;
        }
        return null;
    }

    private List<String> resolveSelectedStreams(String sourceId, StreamsSpec streams) {
        if (streams == null || !StringUtils.hasText(sourceId)) {
            return List.of();
        }
        String selection = normalize(streams.selection());
        if (!StringUtils.hasText(selection) || "all".equals(selection)) {
            return List.of();
        }
        if ("include".equals(selection)) {
            return normalizeList(streams.include());
        }
        if ("exclude".equals(selection)) {
            Map<String, Object> payload = airbyteClient.discoverSchema(sourceId).orElse(Map.of());
            List<String> allStreams = extractStreamNames(payload);
            List<String> exclude = normalizeList(streams.exclude());
            if (allStreams.isEmpty() || exclude.isEmpty()) {
                return List.of();
            }
            List<String> filtered = new ArrayList<>();
            for (String stream : allStreams) {
                if (!exclude.contains(stream)) {
                    filtered.add(stream);
                }
            }
            return filtered;
        }
        return List.of();
    }

    private List<OpenMetadataAdapter.StreamRef> resolveStreamRefs(String sourceId, StreamsSpec streams) {
        if (!StringUtils.hasText(sourceId)) {
            return List.of();
        }
        Map<String, Object> payload = airbyteClient.discoverSchema(sourceId).orElse(Map.of());
        List<OpenMetadataAdapter.StreamRef> refs = extractStreamRefs(payload);
        if (streams == null || refs.isEmpty()) {
            return refs;
        }
        String selection = normalize(streams.selection());
        if (!StringUtils.hasText(selection) || "all".equals(selection)) {
            return refs;
        }
        List<String> include = normalizeList(streams.include());
        List<String> exclude = normalizeList(streams.exclude());
        List<OpenMetadataAdapter.StreamRef> filtered = new ArrayList<>();
        for (OpenMetadataAdapter.StreamRef ref : refs) {
            if ("include".equals(selection) && !include.isEmpty()) {
                if (include.contains(ref.name())) {
                    filtered.add(ref);
                }
                continue;
            }
            if ("exclude".equals(selection) && !exclude.isEmpty()) {
                if (!exclude.contains(ref.name())) {
                    filtered.add(ref);
                }
                continue;
            }
        }
        return filtered.isEmpty() ? refs : filtered;
    }

    private List<OpenMetadataAdapter.StreamRef> extractStreamRefs(Map<String, Object> payload) {
        if (payload == null) {
            return List.of();
        }
        Object catalogObj = payload.get("catalog");
        if (!(catalogObj instanceof Map<?, ?> catalog)) {
            return List.of();
        }
        Object streamsObj = catalog.get("streams");
        if (!(streamsObj instanceof List<?> streamList)) {
            return List.of();
        }
        List<OpenMetadataAdapter.StreamRef> refs = new ArrayList<>();
        for (Object item : streamList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object streamObj = map.get("stream");
            if (!(streamObj instanceof Map<?, ?> streamMap)) {
                continue;
            }
            String name = stringVal(streamMap.get("name"));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String namespace = stringVal(streamMap.get("namespace"));
            refs.add(new OpenMetadataAdapter.StreamRef(name, namespace));
        }
        return refs;
    }

    private List<String> extractStreamNames(Map<String, Object> payload) {
        if (payload == null) {
            return List.of();
        }
        Object catalogObj = payload.get("catalog");
        if (!(catalogObj instanceof Map<?, ?> catalog)) {
            return List.of();
        }
        Object streamsObj = catalog.get("streams");
        if (!(streamsObj instanceof List<?> streamList)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object item : streamList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object streamObj = map.get("stream");
            if (streamObj instanceof Map<?, ?> streamMap) {
                String name = stringVal(streamMap.get("name"));
                if (StringUtils.hasText(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private String normalizeScheduleType(SyncSpec sync) {
        if (sync == null || sync.schedule() == null) {
            return null;
        }
        String type = normalize(sync.schedule().type());
        if (!StringUtils.hasText(type)) {
            return null;
        }
        if ("cron".equals(type)) {
            return "cron";
        }
        if ("manual".equals(type)) {
            return "manual";
        }
        return null;
    }

    private String normalizeScheduleCron(SyncSpec sync) {
        if (sync == null || sync.schedule() == null) {
            return null;
        }
        String cron = normalize(sync.schedule().cron());
        return StringUtils.hasText(cron) ? cron : null;
    }

    private String resolveNamespaceFormat(SyncSpec sync) {
        if (sync == null || sync.namespace() == null) {
            return null;
        }
        String format = normalize(sync.namespace().format());
        return StringUtils.hasText(format) ? format : null;
    }

    private Map<String, Object> requirePayload(ApiResponse<Map<String, Object>> response, String message) {
        if (response == null || response.getData() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
        }
        return response.getData();
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private List<String> normalizeList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String item : items) {
            if (StringUtils.hasText(item)) {
                normalized.add(item.trim());
            }
        }
        return normalized;
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

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                out.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return out;
    }

    private OpenMetadataAdapter.LineageRequest toLineageRequest(LineageSpec spec) {
        if (spec == null) {
            return null;
        }
        return new OpenMetadataAdapter.LineageRequest(spec.enabled(), spec.domain(), spec.tags(), spec.owner());
    }

    private AirflowAdapter.AirflowRequest toAirflowRequest(AirflowSpec spec) {
        if (spec == null) {
            return null;
        }
        return new AirflowAdapter.AirflowRequest(spec.enabled(), spec.dagId(), spec.scheduleType(), spec.cron(), spec.intervalMinutes());
    }
}
