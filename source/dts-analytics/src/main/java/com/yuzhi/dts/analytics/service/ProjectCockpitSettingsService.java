package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsSetting;
import com.yuzhi.dts.analytics.repository.AnalyticsSettingRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectCockpitSettingsService {

    static final String PROJECT_COCKPIT_PERIOD_KEY = "project-cockpit-period";

    private final AnalyticsSettingRepository settingRepository;
    private final ObjectMapper objectMapper;

    public ProjectCockpitSettingsService(AnalyticsSettingRepository settingRepository, ObjectMapper objectMapper) {
        this.settingRepository = settingRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<PublishedPeriod> getPublishedPeriod() {
        return settingRepository.findById(PROJECT_COCKPIT_PERIOD_KEY).flatMap(this::toPublishedPeriod);
    }

    @Transactional
    public PublishedPeriod savePublishedPeriod(LocalDate periodStart, LocalDate periodEnd, String updatedBy) {
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("统计周期必须包含开始和结束时间");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("统计结束时间不能早于开始时间");
        }

        AnalyticsSetting setting = settingRepository.findById(PROJECT_COCKPIT_PERIOD_KEY).orElseGet(() -> {
            AnalyticsSetting row = new AnalyticsSetting();
            row.setSettingKey(PROJECT_COCKPIT_PERIOD_KEY);
            return row;
        });
        setting.setSettingValue("""
                {
                  "periodStart": "%s",
                  "periodEnd": "%s",
                  "updatedBy": "%s"
                }
                """.formatted(periodStart, periodEnd, blankToEmpty(updatedBy)));
        AnalyticsSetting saved = settingRepository.save(setting);
        return toPublishedPeriod(saved).orElseThrow(() -> new IllegalStateException("项目看板统计周期保存失败"));
    }

    private Optional<PublishedPeriod> toPublishedPeriod(AnalyticsSetting setting) {
        if (setting.getSettingValue() == null || setting.getSettingValue().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(setting.getSettingValue());
            return Optional.of(new PublishedPeriod(
                    parseDate(root.path("periodStart").asText("")),
                    parseDate(root.path("periodEnd").asText("")),
                    blankToNull(root.path("updatedBy").asText("")),
                    setting.getUpdatedAt()));
        } catch (IOException exception) {
            throw new IllegalStateException("项目看板统计周期配置损坏", exception);
        }
    }

    private LocalDate parseDate(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : LocalDate.parse(normalized);
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record PublishedPeriod(LocalDate periodStart, LocalDate periodEnd, String updatedBy, Instant updatedAt) {}
}
