package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.domain.IngestionConnectorCapability;
import com.yuzhi.dts.ingestion.repository.IngestionConnectorCapabilityRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionConnectorCapabilityDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ConnectorCapabilityService {

    private static final Map<String, String> CAPABILITY_TO_SYNC_MODE = Map.of(
        "FULL",
        "full_refresh",
        "INCREMENTAL",
        "incremental",
        "CDC",
        "cdc",
        "BACKFILL",
        "backfill"
    );

    private final IngestionConnectorCapabilityRepository repository;
    private final ObjectMapper objectMapper;

    public ConnectorCapabilityService(IngestionConnectorCapabilityRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<IngestionConnectorCapabilityDTO> listEnabled() {
        return repository.findByEnabledTrueOrderByConnectorTypeAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<IngestionConnectorCapabilityDTO> getByConnectorType(String connectorType) {
        if (!StringUtils.hasText(connectorType)) {
            return Optional.empty();
        }
        return repository.findByConnectorTypeIgnoreCase(connectorType.trim()).map(this::toDto);
    }

    public void ensureDefaults() {
        seedConnector(
            "addax",
            List.of("FULL", "INCREMENTAL"),
            "6.0.8",
            Map.of(
                "contractVersion",
                "1.0.0",
                "supportsFile",
                true,
                "supportsJdbc",
                true,
                "supportsCdc",
                false,
                "fallbackSyncMode",
                "full_refresh",
                "syncModes",
                Map.of(
                    "full_refresh",
                    Map.of("enabled", true, "label", "全量同步"),
                    "incremental",
                    Map.of("enabled", true, "label", "增量同步", "requires", List.of("incrementalColumn")),
                    "cdc",
                    Map.of("enabled", false, "label", "实时同步(CDC)"),
                    "backfill",
                    Map.of("enabled", false, "label", "历史回灌")
                )
            )
        );
        seedConnector(
            "airbyte",
            List.of("FULL", "INCREMENTAL", "CDC", "BACKFILL"),
            "future",
            Map.of(
                "contractVersion",
                "1.0.0",
                "supportsFile",
                false,
                "supportsJdbc",
                true,
                "supportsCdc",
                true,
                "fallbackSyncMode",
                "full_refresh",
                "syncModes",
                Map.of(
                    "full_refresh",
                    Map.of("enabled", true, "label", "全量同步"),
                    "incremental",
                    Map.of("enabled", true, "label", "增量同步", "requires", List.of("incrementalColumn")),
                    "cdc",
                    Map.of("enabled", true, "label", "实时同步(CDC)"),
                    "backfill",
                    Map.of("enabled", true, "label", "历史回灌")
                )
            )
        );
        seedConnector(
            "file",
            List.of("FULL"),
            "n/a",
            Map.of(
                "contractVersion",
                "1.0.0",
                "supportsFile",
                true,
                "supportsJdbc",
                false,
                "supportsCdc",
                false,
                "fallbackSyncMode",
                "full_refresh",
                "syncModes",
                Map.of(
                    "full_refresh",
                    Map.of("enabled", true, "label", "全量同步"),
                    "incremental",
                    Map.of("enabled", false, "label", "增量同步"),
                    "cdc",
                    Map.of("enabled", false, "label", "实时同步(CDC)"),
                    "backfill",
                    Map.of("enabled", false, "label", "历史回灌")
                )
            )
        );
    }

    @Transactional(readOnly = true)
    public List<String> resolveSupportedSyncModes(String connectorType) {
        String normalizedConnectorType = normalizeConnectorType(connectorType);
        Optional<IngestionConnectorCapabilityDTO> dto = getByConnectorType(normalizedConnectorType);
        if (dto.isPresent()) {
            List<String> fromContract = extractModesFromContract(dto.get().constraints());
            if (!fromContract.isEmpty()) {
                return fromContract;
            }
            List<String> fromCapabilities = extractModesFromCapabilities(dto.get().capabilities());
            if (!fromCapabilities.isEmpty()) {
                return fromCapabilities;
            }
        }
        return defaultSupportedModes(normalizedConnectorType);
    }

    @Transactional(readOnly = true)
    public String resolveFallbackSyncMode(String connectorType) {
        String normalizedConnectorType = normalizeConnectorType(connectorType);
        Optional<IngestionConnectorCapabilityDTO> dto = getByConnectorType(normalizedConnectorType);
        if (dto.isPresent()) {
            String fromContract = normalizeSyncMode(readString(dto.get().constraints(), "fallbackSyncMode"));
            if (StringUtils.hasText(fromContract)) {
                return fromContract;
            }
        }
        List<String> supported = resolveSupportedSyncModes(normalizedConnectorType);
        return supported.isEmpty() ? "full_refresh" : supported.get(0);
    }

    @Transactional(readOnly = true)
    public void validateSyncModeOrThrow(String connectorType, String syncMode) {
        String normalizedMode = normalizeSyncMode(syncMode);
        List<String> supported = resolveSupportedSyncModes(connectorType);
        if (!supported.contains(normalizedMode)) {
            String label = StringUtils.hasText(connectorType) ? connectorType : "unknown";
            throw new IllegalArgumentException(
                "连接器 " + label + " 不支持同步模式 " + normalizedMode + "，支持模式: " + String.join(", ", supported)
            );
        }
    }

    public String normalizeSyncMode(String syncMode) {
        String value = syncMode == null ? null : syncMode.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(value)) {
            return "full_refresh";
        }
        return switch (value) {
            case "full", "full_refresh", "fullrefresh" -> "full_refresh";
            case "incremental", "incr", "delta" -> "incremental";
            case "cdc", "realtime", "real_time" -> "cdc";
            case "backfill", "history_backfill", "historical_backfill" -> "backfill";
            default -> value;
        };
    }

    private void seedConnector(String connectorType, List<String> capabilities, String version, Map<String, Object> constraints) {
        IngestionConnectorCapability entity = repository
            .findByConnectorTypeIgnoreCase(connectorType)
            .orElseGet(IngestionConnectorCapability::new);
        entity.setConnectorType(connectorType.toLowerCase(Locale.ROOT));
        entity.setCapabilities(toArrayNode(capabilities));
        entity.setConnectorVersion(version);
        entity.setConstraintsJson(objectMapper.valueToTree(constraints));
        entity.setEnabled(Boolean.TRUE);
        repository.save(entity);
    }

    private IngestionConnectorCapabilityDTO toDto(IngestionConnectorCapability entity) {
        return new IngestionConnectorCapabilityDTO(
            entity.getConnectorType(),
            readCapabilities(entity.getCapabilities()),
            entity.getConnectorVersion(),
            readObject(entity.getConstraintsJson()),
            Boolean.TRUE.equals(entity.getEnabled()),
            entity.getUpdatedAt()
        );
    }

    private ArrayNode toArrayNode(List<String> values) {
        ArrayNode node = objectMapper.createArrayNode();
        if (values == null) {
            return node;
        }
        Set<String> dedup = new java.util.LinkedHashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            dedup.add(value.trim().toUpperCase(Locale.ROOT));
        }
        dedup.forEach(node::add);
        return node;
    }

    private List<String> readCapabilities(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = item == null ? null : item.asText(null);
                if (StringUtils.hasText(text)) {
                    out.add(text.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    private Map<String, Object> readObject(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        ObjectNode objectNode = (ObjectNode) node;
        Map<String, Object> result = new LinkedHashMap<>();
        objectNode.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            result.put(entry.getKey(), objectMapper.convertValue(value, Object.class));
        });
        return result;
    }

    private String normalizeConnectorType(String connectorType) {
        if (!StringUtils.hasText(connectorType)) {
            return "addax";
        }
        return connectorType.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> extractModesFromContract(Map<String, Object> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return List.of();
        }
        Object syncModes = constraints.get("syncModes");
        if (!(syncModes instanceof Map<?, ?> map)) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String mode = normalizeSyncMode(entry.getKey() == null ? null : entry.getKey().toString());
            if (!StringUtils.hasText(mode)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> modeCfg) {
                Object enabled = modeCfg.get("enabled");
                if (enabled instanceof Boolean b && !b) {
                    continue;
                }
                if (enabled instanceof String text && "false".equalsIgnoreCase(text.trim())) {
                    continue;
                }
            }
            result.add(mode);
        }
        return List.copyOf(result);
    }

    private List<String> extractModesFromCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String capability : capabilities) {
            String key = StringUtils.hasText(capability) ? capability.trim().toUpperCase(Locale.ROOT) : null;
            String mode = key == null ? null : CAPABILITY_TO_SYNC_MODE.get(key);
            if (StringUtils.hasText(mode)) {
                result.add(mode);
            }
        }
        return List.copyOf(result);
    }

    private List<String> defaultSupportedModes(String connectorType) {
        String normalized = normalizeConnectorType(connectorType);
        if ("file".equals(normalized)) {
            return List.of("full_refresh");
        }
        if ("airbyte".equals(normalized)) {
            return List.of("full_refresh", "incremental", "cdc", "backfill");
        }
        return List.of("full_refresh", "incremental");
    }

    private String readString(Map<String, Object> map, String key) {
        if (map == null || map.isEmpty() || !StringUtils.hasText(key)) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
