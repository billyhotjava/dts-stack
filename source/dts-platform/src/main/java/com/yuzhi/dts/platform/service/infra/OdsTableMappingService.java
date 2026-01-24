package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.infra.InfraAirbyteConnection;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.infra.InfraAirbyteConnectionRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OdsTableMappingService {

    private final InfraOdsTableMappingRepository mappingRepository;
    private final InfraAirbyteConnectionRepository connectionRepository;
    private final CatalogDatasetRepository datasetRepository;

    public OdsTableMappingService(
        InfraOdsTableMappingRepository mappingRepository,
        InfraAirbyteConnectionRepository connectionRepository,
        CatalogDatasetRepository datasetRepository
    ) {
        this.mappingRepository = mappingRepository;
        this.connectionRepository = connectionRepository;
        this.datasetRepository = datasetRepository;
    }

    public List<InfraOdsTableMapping> list(UUID connectionId) {
        if (connectionId == null) {
            return mappingRepository.listAllSorted();
        }
        return mappingRepository.findByConnectionIdOrderByCreatedDateDesc(connectionId);
    }

    public List<InfraOdsTableMapping> upsertBatch(List<OdsMappingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<InfraOdsTableMapping> results = new ArrayList<>();
        for (OdsMappingRequest request : requests) {
            results.add(upsert(request));
        }
        return results;
    }

    public InfraOdsTableMapping upsert(OdsMappingRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }
        UUID connectionId = request.connectionId();
        if (connectionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少连接标识");
        }
        InfraAirbyteConnection connection = connectionRepository
            .findById(connectionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "接入配置不存在"));

        String streamName = ensureLength(normalizeRequired(request.streamName(), "流名称不能为空"), 256, "流名称过长");
        String streamNamespace = normalizeStreamNamespace(request.streamNamespace());
        String systemCode = ensureLength(normalizeToken(request.systemCode(), "systemCode 不能为空"), 64, "systemCode 过长");
        String bizCode = ensureLength(normalizeToken(request.bizCode(), "bizCode 不能为空"), 64, "bizCode 过长");
        String entityCode = ensureLength(normalizeToken(request.entityCode(), "entityCode 不能为空"), 128, "entityCode 过长");
        String odsSchema = ensureLength(normalizeSchema(request.odsSchema()), 128, "odsSchema 过长");
        String odsTable = ensureLength(buildOdsTable(systemCode, bizCode, entityCode), 128, "ODS 表名过长");

        Optional<InfraOdsTableMapping> conflict = mappingRepository.findFirstByOdsSchemaIgnoreCaseAndOdsTableIgnoreCase(
            odsSchema,
            odsTable
        );
        if (conflict.isPresent()) {
            InfraOdsTableMapping existing = conflict.get();
            if (existing.getId() == null || !existing.getId().equals(request.id())) {
                if (!existing.getConnectionId().equals(connectionId) || !equalsIgnoreCase(existing.getStreamName(), streamName)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ODS 表名已被其他流占用");
                }
            }
        }

        InfraOdsTableMapping mapping = locateExisting(request, connectionId, streamName, streamNamespace);
        mapping.setConnectionId(connection.getId());
        mapping.setStreamName(streamName);
        mapping.setStreamNamespace(streamNamespace);
        mapping.setSystemCode(systemCode);
        mapping.setBizCode(bizCode);
        mapping.setEntityCode(entityCode);
        mapping.setOdsSchema(odsSchema);
        mapping.setOdsTable(odsTable);
        mapping.setOwner(normalizeText(request.owner()));
        mapping.setOwnerDept(normalizeText(request.ownerDept()));
        mapping.setDescription(normalizeText(request.description()));
        mapping.setEnabled(request.enabled() != null ? request.enabled() : Boolean.TRUE);
        if (request.datasetId() != null) {
            mapping.setDatasetId(request.datasetId());
        }

        InfraOdsTableMapping saved = mappingRepository.save(mapping);
        if (saved.getDatasetId() == null) {
            CatalogDataset dataset = ensureOdsDataset(saved);
            if (dataset != null && dataset.getId() != null) {
                saved.setDatasetId(dataset.getId());
                saved = mappingRepository.save(saved);
            }
        }
        return saved;
    }

    public void delete(UUID id) {
        if (id == null) return;
        mappingRepository.deleteById(id);
    }

    private InfraOdsTableMapping locateExisting(
        OdsMappingRequest request,
        UUID connectionId,
        String streamName,
        String streamNamespace
    ) {
        if (request.id() != null) {
            return mappingRepository.findById(request.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "映射不存在"));
        }
        return mappingRepository
            .findFirstByConnectionIdAndStreamNameIgnoreCaseAndStreamNamespaceIgnoreCase(connectionId, streamName, streamNamespace)
            .orElseGet(InfraOdsTableMapping::new);
    }

    private CatalogDataset ensureOdsDataset(InfraOdsTableMapping mapping) {
        if (mapping == null) return null;
        String schema = mapping.getOdsSchema();
        String table = mapping.getOdsTable();
        if (!StringUtils.hasText(schema) || !StringUtils.hasText(table)) {
            return null;
        }
        CatalogDataset dataset = datasetRepository
            .findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema, table)
            .orElseGet(CatalogDataset::new);

        if (dataset.getId() == null) {
            dataset.setName(table);
            dataset.setType("hive");
            dataset.setHiveDatabase(schema);
            dataset.setHiveTable(table);
            dataset.setWarehouseLayer("ODS");
            dataset.setEnabled(Boolean.TRUE);
        }
        if (!StringUtils.hasText(dataset.getOwner()) && StringUtils.hasText(mapping.getOwner())) {
            dataset.setOwner(mapping.getOwner());
        }
        if (!StringUtils.hasText(dataset.getOwnerDept()) && StringUtils.hasText(mapping.getOwnerDept())) {
            dataset.setOwnerDept(mapping.getOwnerDept());
        }
        if (!StringUtils.hasText(dataset.getDescription()) && StringUtils.hasText(mapping.getDescription())) {
            dataset.setDescription(mapping.getDescription());
        }
        return datasetRepository.save(dataset);
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStreamNamespace(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return ensureLength(value.trim(), 256, "流命名空间过长");
    }

    private String normalizeSchema(String value) {
        if (!StringUtils.hasText(value)) {
            return "ods";
        }
        return normalizeToken(value, "odsSchema 不能为空");
    }

    private String normalizeToken(String value, String message) {
        String trimmed = normalizeRequired(value, message).toLowerCase(Locale.ROOT);
        String normalized = trimmed.replaceAll("[^a-z0-9_]", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+", "");
        normalized = normalized.replaceAll("_+$", "");
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "编码格式不合法");
        }
        return normalized;
    }

    private String buildOdsTable(String systemCode, String bizCode, String entityCode) {
        return String.format("ods_%s_%s_%s", systemCode, bizCode, entityCode);
    }

    private String ensureLength(String value, int max, String message) {
        if (value == null) return null;
        if (value.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        return left.equalsIgnoreCase(right);
    }

    public record OdsMappingRequest(
        UUID id,
        UUID connectionId,
        String streamName,
        String streamNamespace,
        String systemCode,
        String bizCode,
        String entityCode,
        String odsSchema,
        String owner,
        String ownerDept,
        String description,
        Boolean enabled,
        UUID datasetId
    ) {}
}
