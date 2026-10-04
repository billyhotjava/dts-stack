package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class IndicatorObservabilityService {

    private final IndicatorService indicatorService;

    public IndicatorObservabilityService(IndicatorService indicatorService) {
        this.indicatorService = indicatorService;
    }

    public Map<String, Object> overview(int hours, String activeDept) {
        int safeHours = normalizeHours(hours);
        Instant windowStart = Instant.now().minus(safeHours, ChronoUnit.HOURS);
        List<IndicatorDto> indicators = fetchAllIndicators(activeDept);

        int published = 0;
        int draft = 0;
        int archived = 0;
        int success = 0;
        int failed = 0;
        int never = 0;
        Map<String, Integer> failureCategoryCounter = new LinkedHashMap<>();

        for (IndicatorDto dto : indicators) {
            String status = normalize(dto.getStatus());
            if ("PUBLISHED".equals(status)) {
                published++;
            } else if ("ARCHIVED".equals(status)) {
                archived++;
            } else {
                draft++;
            }

            Instant validatedAt = dto.getLastValidatedAt();
            String validationStatus = normalize(dto.getLastValidationStatus());
            if (validatedAt == null || validatedAt.isBefore(windowStart)) {
                never++;
                continue;
            }
            if ("SUCCESS".equals(validationStatus)) {
                success++;
            } else if ("FAILED".equals(validationStatus)) {
                failed++;
                String category = classifyFailure(dto.getLastValidationMessage());
                failureCategoryCounter.merge(category, 1, Integer::sum);
            } else {
                never++;
            }
        }

        int total = indicators.size();
        int scored = Math.max(1, success + failed);
        List<Map<String, Object>> failureTop = failureCategoryCounter
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category", entry.getKey());
                row.put("count", entry.getValue());
                return row;
            })
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hours", safeHours);
        payload.put("totalIndicators", total);
        payload.put("statusDistribution", Map.of("published", published, "draft", draft, "archived", archived));
        payload.put(
            "validation",
            Map.of(
                "success",
                success,
                "failed",
                failed,
                "never",
                never,
                "successRate",
                roundTo2(success * 100.0 / scored),
                "failedRate",
                roundTo2(failed * 100.0 / scored)
            )
        );
        payload.put("failureTop", failureTop);
        return payload;
    }

    public List<Map<String, Object>> trend(int hours, int bucketHours, String activeDept) {
        int safeHours = normalizeHours(hours);
        int safeBucketHours = Math.max(1, Math.min(bucketHours, safeHours));
        List<IndicatorDto> indicators = fetchAllIndicators(activeDept);

        ZonedDateTime end = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime start = end.minusHours(safeHours);
        long bucketCount = Math.max(1, (safeHours + safeBucketHours - 1L) / safeBucketHours);
        Map<Long, Bucket> bucketMap = new LinkedHashMap<>();
        for (long i = 0; i < bucketCount; i++) {
            ZonedDateTime bucketStart = start.plusHours(i * safeBucketHours);
            bucketMap.put(bucketStart.toEpochSecond(), new Bucket(bucketStart, bucketStart.plusHours(safeBucketHours)));
        }

        for (IndicatorDto dto : indicators) {
            Instant validatedAt = dto.getLastValidatedAt();
            if (validatedAt == null || validatedAt.isBefore(start.toInstant()) || !validatedAt.isBefore(end.toInstant())) {
                continue;
            }
            long diffHours = ChronoUnit.HOURS.between(start.toInstant(), validatedAt);
            long bucketIndex = diffHours / safeBucketHours;
            ZonedDateTime bucketStart = start.plusHours(bucketIndex * safeBucketHours);
            Bucket bucket = bucketMap.get(bucketStart.toEpochSecond());
            if (bucket == null) {
                continue;
            }
            bucket.total++;
            String status = normalize(dto.getLastValidationStatus());
            if ("SUCCESS".equals(status)) {
                bucket.success++;
            } else if ("FAILED".equals(status)) {
                bucket.failed++;
            }
        }

        return bucketMap
            .values()
            .stream()
            .sorted(Comparator.comparing(bucket -> bucket.start))
            .map(bucket -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("bucketStart", bucket.start.toInstant().toString());
                row.put("bucketEnd", bucket.end.toInstant().toString());
                row.put("total", bucket.total);
                row.put("success", bucket.success);
                row.put("failed", bucket.failed);
                row.put("failedRate", bucket.total == 0 ? 0.0 : roundTo2(bucket.failed * 100.0 / bucket.total));
                return row;
            })
            .toList();
    }

    private List<IndicatorDto> fetchAllIndicators(String activeDept) {
        int page = 0;
        int size = 500;
        List<IndicatorDto> result = new ArrayList<>();
        while (true) {
            Pageable pageable = PageRequest.of(page, size);
            Page<IndicatorDto> data = indicatorService.list(null, null, pageable, activeDept);
            if (data == null || data.getContent().isEmpty()) {
                break;
            }
            result.addAll(data.getContent());
            page++;
            if (page >= data.getTotalPages()) {
                break;
            }
        }
        return result;
    }

    private int normalizeHours(int hours) {
        return Math.max(1, Math.min(hours, 24 * 30));
    }

    private String normalize(String input) {
        if (!StringUtils.hasText(input)) {
            return "";
        }
        return input.trim().toUpperCase(Locale.ROOT);
    }

    private String classifyFailure(String message) {
        String text = normalize(message);
        if (!StringUtils.hasText(text)) {
            return "unknown";
        }
        if (text.contains("上下文") || text.contains("部门")) {
            return "department_denied";
        }
        if (text.contains("权限")) {
            return "permission_denied";
        }
        if (text.contains("数据集") && text.contains("不存在")) {
            return "dataset_missing";
        }
        if (text.contains("SQL")) {
            return "sql_error";
        }
        if (text.contains("执行失败")) {
            return "query_failed";
        }
        return "other";
    }

    private double roundTo2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static class Bucket {

        private final ZonedDateTime start;
        private final ZonedDateTime end;
        private int total;
        private int success;
        private int failed;

        private Bucket(ZonedDateTime start, ZonedDateTime end) {
            this.start = start;
            this.end = end;
        }
    }
}
