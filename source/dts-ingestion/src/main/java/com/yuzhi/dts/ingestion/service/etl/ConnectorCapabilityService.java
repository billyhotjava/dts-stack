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
            List.of("FULL", "INCREMENTAL", "BACKFILL"),
            "6.0.8",
            Map.of("supportsFile", true, "supportsJdbc", true, "supportsCdc", false)
        );
        seedConnector(
            "airbyte",
            List.of("FULL", "INCREMENTAL", "CDC", "BACKFILL"),
            "future",
            Map.of("supportsFile", false, "supportsJdbc", true, "supportsCdc", true)
        );
        seedConnector(
            "file",
            List.of("FULL"),
            "n/a",
            Map.of("supportsFile", true, "supportsJdbc", false, "supportsCdc", false)
        );
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
}
