package com.yuzhi.dts.platform.service.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.service.SvcDataProduct;
import com.yuzhi.dts.platform.domain.service.SvcDataProductDataset;
import com.yuzhi.dts.platform.domain.service.SvcDataProductVersion;
import com.yuzhi.dts.platform.repository.service.SvcDataProductDatasetRepository;
import com.yuzhi.dts.platform.repository.service.SvcDataProductRepository;
import com.yuzhi.dts.platform.repository.service.SvcDataProductVersionRepository;
import com.yuzhi.dts.platform.service.services.dto.*;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class DataProductService {

    private static final Logger LOG = LoggerFactory.getLogger(DataProductService.class);

    private final SvcDataProductRepository productRepository;
    private final SvcDataProductVersionRepository versionRepository;
    private final SvcDataProductDatasetRepository datasetRepository;
    private final ObjectMapper objectMapper;

    public DataProductService(
        SvcDataProductRepository productRepository,
        SvcDataProductVersionRepository versionRepository,
        SvcDataProductDatasetRepository datasetRepository,
        ObjectMapper objectMapper
    ) {
        this.productRepository = productRepository;
        this.versionRepository = versionRepository;
        this.datasetRepository = datasetRepository;
        this.objectMapper = objectMapper;
    }

    public List<DataProductSummaryDto> list(String keyword, String type, String status) {
        Stream<SvcDataProduct> stream = productRepository.findAll().stream();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase(Locale.ROOT);
            stream = stream.filter(product -> matchesKeyword(product, kw));
        }
        if (StringUtils.hasText(type) && !"all".equalsIgnoreCase(type)) {
            stream = stream.filter(product -> type.equalsIgnoreCase(StringUtils.trimAllWhitespace(product.getProductType())));
        }
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            stream = stream.filter(product -> status.equalsIgnoreCase(StringUtils.trimAllWhitespace(product.getStatus())));
        }

        return stream
            .sorted(Comparator.comparing(SvcDataProduct::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
            .map(this::toSummary)
            .collect(Collectors.toList());
    }

    public DataProductDetailDto detail(UUID id) {
        SvcDataProduct product = productRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Data product not found"));
        List<String> datasets = datasetRepository
            .findByProductId(id)
            .stream()
            .map(ds -> StringUtils.hasText(ds.getDatasetName()) ? ds.getDatasetName() : null)
            .filter(Objects::nonNull)
            .toList();

        List<DataProductVersionDto> versions = versionRepository
            .findByProductIdOrderByReleasedAtDesc(id)
            .stream()
            .map(this::toVersionDto)
            .toList();

        return new DataProductDetailDto(
            product.getId(),
            product.getCode(),
            product.getName(),
            product.getProductType(),
            product.getClassification(),
            product.getStatus(),
            product.getSla(),
            product.getRefreshFrequency(),
            product.getLatencyObjective(),
            product.getFailurePolicy(),
            product.getSubscriptions() != null ? product.getSubscriptions() : 0,
            datasets,
            versions,
            product.getDescription()
        );
    }

    private DataProductSummaryDto toSummary(SvcDataProduct product) {
        List<String> datasets = datasetRepository
            .findByProductId(product.getId())
            .stream()
            .map(SvcDataProductDataset::getDatasetName)
            .filter(StringUtils::hasText)
            .toList();
        return new DataProductSummaryDto(
            product.getId(),
            product.getCode(),
            product.getName(),
            product.getProductType(),
            product.getClassification(),
            product.getStatus(),
            product.getSla(),
            product.getRefreshFrequency(),
            product.getCurrentVersion(),
            product.getSubscriptions() != null ? product.getSubscriptions() : 0,
            datasets
        );
    }

    private boolean matchesKeyword(SvcDataProduct product, String keyword) {
        return Stream
            .of(product.getName(), product.getCode(), product.getDescription())
            .filter(Objects::nonNull)
            .map(v -> v.toLowerCase(Locale.ROOT))
            .anyMatch(v -> v.contains(keyword));
    }

    private DataProductVersionDto toVersionDto(SvcDataProductVersion version) {
        List<DataProductFieldDto> fields = parseFields(version.getSchemaJson());
        DataProductConsumptionDto consumption = parseConsumption(version.getConsumptionJson());
        DataProductMetadataDto metadata = parseMetadata(version.getMetadataJson());
        Instant releasedAt = version.getReleasedAt();
        return new DataProductVersionDto(
            version.getVersion(),
            version.getStatus(),
            releasedAt,
            version.getDiffSummary(),
            fields,
            consumption,
            metadata
        );
    }

    private List<DataProductFieldDto> parseFields(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<DataProductFieldDto> result = new ArrayList<>();
            for (JsonNode item : node) {
                String name = textValue(item, "name");
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                String type = textValue(item, "type");
                String term = textValue(item, "term");
                boolean masked = item.path("masked").asBoolean(false);
                String description = textValue(item, "description");
                result.add(new DataProductFieldDto(name, type, term, masked, description));
            }
            return result;
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse product schema json: {}", e.getMessage());
            return List.of();
        }
    }

    private DataProductConsumptionDto parseConsumption(String json) {
        if (!StringUtils.hasText(json)) {
            return new DataProductConsumptionDto(null, null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            DataProductConsumptionDto.RestChannel rest = null;
            JsonNode restNode = node.path("rest");
            if (restNode.isObject()) {
                rest = new DataProductConsumptionDto.RestChannel(textValue(restNode, "endpoint"), textValue(restNode, "auth"));
            }
            DataProductConsumptionDto.JdbcChannel jdbc = null;
            JsonNode jdbcNode = node.path("jdbc");
            if (jdbcNode.isObject()) {
                jdbc = new DataProductConsumptionDto.JdbcChannel(textValue(jdbcNode, "driver"), textValue(jdbcNode, "url"));
            }
            DataProductConsumptionDto.FileChannel file = null;
            JsonNode fileNode = node.path("file");
            if (fileNode.isObject()) {
                List<String> formats = new ArrayList<>();
                JsonNode formatsNode = fileNode.path("formats");
                if (formatsNode.isArray()) {
                    formatsNode.forEach(f -> {
                        String value = f.asText(null);
                        if (StringUtils.hasText(value)) {
                            formats.add(value);
                        }
                    });
                }
                file = new DataProductConsumptionDto.FileChannel(textValue(fileNode, "objectStorePath"), textValue(fileNode, "sharedPath"), formats);
            }
            return new DataProductConsumptionDto(rest, jdbc, file);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse product consumption json: {}", e.getMessage());
            return new DataProductConsumptionDto(null, null, null);
        }
    }

    private DataProductMetadataDto parseMetadata(String json) {
        if (!StringUtils.hasText(json)) {
            return new DataProductMetadataDto(null, null, null, null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return new DataProductMetadataDto(
                textValue(node, "bloodlineSummary"),
                textValue(node, "classificationStrategy"),
                textValue(node, "maskingStrategy"),
                textValue(node, "latencyObjective"),
                textValue(node, "failurePolicy")
            );
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse product metadata json: {}", e.getMessage());
            return new DataProductMetadataDto(null, null, null, null, null);
        }
    }

    private String textValue(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asText(null);
    }
}
