package com.yuzhi.dts.platform.service.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.service.SvcApi;
import com.yuzhi.dts.platform.domain.service.SvcApiMetricHourly;
import com.yuzhi.dts.platform.repository.service.SvcApiMetricHourlyRepository;
import com.yuzhi.dts.platform.repository.service.SvcApiRepository;
import com.yuzhi.dts.platform.service.services.dto.*;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class ApiCatalogService {

    private static final Logger LOG = LoggerFactory.getLogger(ApiCatalogService.class);

    private final SvcApiRepository apiRepository;
    private final SvcApiMetricHourlyRepository metricRepository;
    private final ObjectMapper objectMapper;

    public ApiCatalogService(SvcApiRepository apiRepository, SvcApiMetricHourlyRepository metricRepository, ObjectMapper objectMapper) {
        this.apiRepository = apiRepository;
        this.metricRepository = metricRepository;
        this.objectMapper = objectMapper;
    }

    public List<ApiServiceSummaryDto> list(String keyword, String method, String status) {
        Stream<SvcApi> stream = apiRepository.findAll().stream();

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase(Locale.ROOT);
            stream = stream.filter(api -> matchesKeyword(api, kw));
        }
        if (StringUtils.hasText(method) && !"all".equalsIgnoreCase(method)) {
            stream = stream.filter(api -> method.equalsIgnoreCase(StringUtils.trimAllWhitespace(api.getMethod())));
        }
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            stream = stream.filter(api -> status.equalsIgnoreCase(StringUtils.trimAllWhitespace(api.getStatus())));
        }

        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        return stream
            .sorted(Comparator.comparing(SvcApi::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
            .map(api -> toSummary(api, since))
            .collect(Collectors.toList());
    }

    public ApiServiceDetailDto detail(UUID id) {
        SvcApi api = apiRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("API not found"));
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        return toDetail(api, since);
    }

    public ApiTryInvokeResponseDto tryInvoke(UUID id, ApiTryInvokeRequestDto request) {
        SvcApi api = apiRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("API not found"));
        List<ApiFieldDto> outputFields = parseFields(api.getResponseSchemaJson());
        if (outputFields.isEmpty()) {
            return new ApiTryInvokeResponseDto(List.of(), List.of(), List.of(), 0, List.of());
        }

        List<String> columns = outputFields.stream().map(ApiFieldDto::name).toList();
        List<String> masked = outputFields.stream().filter(ApiFieldDto::masked).map(ApiFieldDto::name).toList();

        Map<String, Object> row = outputFields
            .stream()
            .collect(Collectors.toMap(ApiFieldDto::name, f -> sampleValue(f, request)));

        ApiPolicyDto policy = parsePolicy(api.getPolicyJson());
        List<String> hits = new ArrayList<>();
        if (!CollectionUtils.isEmpty(policy.maskedColumns())) {
            policy.maskedColumns().forEach(col -> hits.add("MASK:" + col));
        }
        return new ApiTryInvokeResponseDto(columns, masked, List.of(row), 1, hits);
    }

    public ApiMetricsDto metrics(UUID id) {
        SvcApi api = apiRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("API not found"));
        List<SvcApiMetricHourly> metrics = metricRepository.findTop48ByApiIdOrderByBucketStartDesc(id);
        metrics.sort(Comparator.comparing(SvcApiMetricHourly::getBucketStart));

        List<ApiMetricsDto.SeriesPoint> series = metrics
            .stream()
            .map(m -> new ApiMetricsDto.SeriesPoint(
                m.getBucketStart().toEpochMilli(),
                m.getCallCount() != null ? m.getCallCount() : 0,
                m.getQpsPeak() != null ? m.getQpsPeak() : 0
            ))
            .toList();

        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long totalCalls = metricRepository.sumCallsSince(id, since);
        String minLevel = parsePolicy(api.getPolicyJson()).minLevel();
        if (!StringUtils.hasText(minLevel)) {
            minLevel = StringUtils.hasText(api.getClassification()) ? api.getClassification() : "未知";
        }

        ApiMetricsDto.DistributionSlice slice = new ApiMetricsDto.DistributionSlice(minLevel, totalCalls);

        return new ApiMetricsDto(series, List.of(slice), List.of());
    }

    @Transactional
    public ApiServiceDetailDto publish(UUID id, String version, String username) {
        SvcApi api = apiRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("API not found"));
        String resolvedVersion = StringUtils.hasText(version) ? version : nextVersion(api.getLatestVersion());
        api.setLatestVersion(resolvedVersion);
        api.setStatus("PUBLISHED");
        api.setLastPublishedAt(Instant.now());
        api.setLastModifiedBy(username);
        apiRepository.save(api);
        return toDetail(api, Instant.now().minus(24, ChronoUnit.HOURS));
    }

    public ApiTryInvokeResponseDto execute(UUID id, ApiTryInvokeRequestDto request) {
        return tryInvoke(id, request);
    }

    private boolean matchesKeyword(SvcApi api, String kw) {
        return Stream
            .of(api.getName(), api.getCode(), api.getDatasetName(), api.getPath())
            .filter(Objects::nonNull)
            .map(v -> v.toLowerCase(Locale.ROOT))
            .anyMatch(v -> v.contains(kw));
    }

    private ApiServiceSummaryDto toSummary(SvcApi api, Instant since) {
        List<SvcApiMetricHourly> metrics = metricRepository.findTop48ByApiIdOrderByBucketStartDesc(api.getId());
        long recentCalls = metricRepository.sumCallsSince(api.getId(), since);
        List<Integer> sparkline = metrics
            .stream()
            .limit(12)
            .map(m -> m.getCallCount() != null ? clampToInt(m.getCallCount()) : 0)
            .collect(Collectors.toCollection(ArrayList::new));
        // reverse to chronological order for sparkline
        java.util.Collections.reverse(sparkline);
        int qps = api.getCurrentQps() != null ? api.getCurrentQps() : 0;
        return new ApiServiceSummaryDto(
            api.getId(),
            api.getCode(),
            api.getName(),
            api.getDatasetId() != null ? api.getDatasetId().toString() : null,
            api.getDatasetName(),
            api.getMethod(),
            api.getPath(),
            api.getClassification(),
            qps,
            api.getQpsLimit() != null ? api.getQpsLimit() : 0,
            api.getDailyLimit() != null ? api.getDailyLimit() : 0,
            api.getStatus(),
            recentCalls,
            sparkline
        );
    }

    private ApiServiceDetailDto toDetail(SvcApi api, Instant since) {
        int qps = api.getCurrentQps() != null ? api.getCurrentQps() : 0;
        int qpsLimit = api.getQpsLimit() != null ? api.getQpsLimit() : 0;
        int dailyLimit = api.getDailyLimit() != null ? api.getDailyLimit() : 0;
        long calls = metricRepository.sumCallsSince(api.getId(), since);
        long masked = metricRepository.sumMaskedSince(api.getId(), since);
        long denies = metricRepository.sumDeniesSince(api.getId(), since);

        int remaining = dailyLimit > 0 ? (int) Math.max(dailyLimit - calls, 0) : dailyLimit;

        return new ApiServiceDetailDto(
            api.getId(),
            api.getCode(),
            api.getName(),
            api.getDatasetId() != null ? api.getDatasetId().toString() : null,
            api.getDatasetName(),
            api.getMethod(),
            api.getPath(),
            api.getClassification(),
            qps,
            qpsLimit,
            dailyLimit,
            api.getStatus(),
            parsePolicy(api.getPolicyJson()),
            parseFields(api.getRequestSchemaJson()),
            parseFields(api.getResponseSchemaJson()),
            new ApiQuotaDto(qpsLimit, dailyLimit, remaining),
            new ApiAuditStatsDto(calls, masked, denies),
            api.getLatestVersion(),
            api.getLastPublishedAt(),
            api.getDescription()
        );
    }

    private List<ApiFieldDto> parseFields(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<ApiFieldDto> list = new ArrayList<>();
            for (JsonNode item : node) {
                String name = textValue(item, "name");
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                String type = textValue(item, "type");
                boolean masked = item.path("masked").asBoolean(false);
                String description = textValue(item, "description");
                list.add(new ApiFieldDto(name, type, masked, description));
            }
            return list;
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse schema json: {}", e.getMessage());
            return List.of();
        }
    }

    private ApiPolicyDto parsePolicy(String json) {
        if (!StringUtils.hasText(json)) {
            return new ApiPolicyDto(null, List.of(), null);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String minLevel = textValue(node, "minLevel");
            List<String> masked = new ArrayList<>();
            JsonNode maskedNode = node.path("maskedColumns");
            if (maskedNode.isArray()) {
                maskedNode.forEach(m -> {
                    String value = m.asText(null);
                    if (StringUtils.hasText(value)) {
                        masked.add(value);
                    }
                });
            }
            String rowFilter = textValue(node, "rowFilter");
            return new ApiPolicyDto(minLevel, masked, rowFilter);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse policy json: {}", e.getMessage());
            return new ApiPolicyDto(null, List.of(), null);
        }
    }

    private int clampToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private String textValue(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asText(null);
    }

    private Object sampleValue(ApiFieldDto field, ApiTryInvokeRequestDto request) {
        String name = field.name();
        if (request != null && request.params() != null && request.params().containsKey(name)) {
            return request.params().get(name);
        }
        return switch (field.type() == null ? "string" : field.type().toLowerCase(Locale.ROOT)) {
            case "int", "integer", "bigint" -> 0;
            case "decimal", "double", "float" -> 0.0;
            case "boolean" -> Boolean.FALSE;
            case "date" -> java.time.LocalDate.now().toString();
            case "timestamp", "datetime" -> java.time.Instant.now().toString();
            default -> field.masked() ? "***" : ("sample_" + name);
        };
    }

    private String nextVersion(String current) {
        if (!StringUtils.hasText(current)) {
            return "v1";
        }
        String trimmed = current.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("v")) {
            try {
                int num = Integer.parseInt(trimmed.substring(1));
                return "v" + (num + 1);
            } catch (NumberFormatException ignored) {
                // fallthrough
            }
        }
        return current + ".1";
    }
}
