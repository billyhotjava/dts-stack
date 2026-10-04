package com.yuzhi.dts.platform.service.security.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.security.SecurityBackupPlan;
import com.yuzhi.dts.platform.domain.security.SecurityBackupRun;
import com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill;
import com.yuzhi.dts.platform.repository.security.SecurityBackupPlanRepository;
import com.yuzhi.dts.platform.repository.security.SecurityBackupRunRepository;
import com.yuzhi.dts.platform.repository.security.SecurityDisasterRecoveryDrillRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class SecurityBackupService {

    private final SecurityBackupPlanRepository planRepository;
    private final SecurityBackupRunRepository runRepository;
    private final SecurityDisasterRecoveryDrillRepository drillRepository;
    private final ObjectMapper objectMapper;

    public SecurityBackupService(
        SecurityBackupPlanRepository planRepository,
        SecurityBackupRunRepository runRepository,
        SecurityDisasterRecoveryDrillRepository drillRepository,
        ObjectMapper objectMapper
    ) {
        this.planRepository = planRepository;
        this.runRepository = runRepository;
        this.drillRepository = drillRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SecurityBackupPlan> listPlans() {
        return planRepository.findAll();
    }

    public SecurityBackupPlan createPlan(SecurityBackupPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        normalizePlan(plan);
        if (!StringUtils.hasText(plan.getTargetKey())) {
            throw new IllegalArgumentException("targetKey 不能为空");
        }
        if (!StringUtils.hasText(plan.getTitle())) {
            throw new IllegalArgumentException("title 不能为空");
        }
        plan.setId(null);
        if (plan.getEnabled() == null) {
            plan.setEnabled(Boolean.TRUE);
        }
        return planRepository.save(plan);
    }

    public SecurityBackupPlan updatePlan(UUID id, SecurityBackupPlan patch) {
        SecurityBackupPlan existing = planRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("备份策略不存在"));
        if (patch == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (StringUtils.hasText(patch.getTargetKey())) {
            existing.setTargetKey(trimToLen(patch.getTargetKey(), 64));
        }
        if (StringUtils.hasText(patch.getTitle())) {
            existing.setTitle(trimToLen(patch.getTitle(), 128));
        }
        if (patch.getDescription() != null) {
            existing.setDescription(trimToLen(patch.getDescription(), 512));
        }
        if (patch.getSchedule() != null) {
            existing.setSchedule(trimToLen(patch.getSchedule(), 128));
        }
        if (patch.getRetentionDays() != null) {
            existing.setRetentionDays(patch.getRetentionDays());
        }
        if (patch.getEnabled() != null) {
            existing.setEnabled(patch.getEnabled());
        }
        if (patch.getNotes() != null) {
            existing.setNotes(trimToLen(patch.getNotes(), 2048));
        }
        return planRepository.save(existing);
    }

    public void deletePlan(UUID id) {
        planRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<SecurityBackupRun> listRuns(UUID planId) {
        return runRepository.findTop50ByPlanIdOrderByStartedAtDesc(planId);
    }

    public SecurityBackupRun recordRun(UUID planId, Map<String, Object> body) {
        SecurityBackupPlan plan = planRepository.findById(planId).orElseThrow(() -> new IllegalArgumentException("备份策略不存在"));
        Map<String, Object> payload = body == null ? Map.of() : new LinkedHashMap<>(body);
        String result = Objects.toString(payload.getOrDefault("result", payload.getOrDefault("status", "SUCCESS")), "SUCCESS");
        String normalizedResult = normalizeResult(result);
        SecurityBackupRun run = new SecurityBackupRun();
        run.setPlan(plan);
        run.setResult(normalizedResult);
        run.setSummary(trimToLen(text(payload.get("summary")), 1024));
        run.setArtifactUri(trimToLen(text(payload.get("artifactUri")), 512));
        run.setStartedAt(parseInstant(text(payload.get("startedAt"))).orElse(Instant.now()));
        run.setFinishedAt(parseInstant(text(payload.get("finishedAt"))).orElse(null));
        run.setDetailsJson(writeJson(payload.get("details")));
        SecurityBackupRun saved = runRepository.save(run);

        plan.setLastRunAt(saved.getFinishedAt() != null ? saved.getFinishedAt() : saved.getStartedAt());
        plan.setLastResult(saved.getResult());
        planRepository.save(plan);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SecurityDisasterRecoveryDrill> listDrills() {
        return drillRepository.findTop50ByOrderByDrillDateDesc();
    }

    public SecurityDisasterRecoveryDrill recordDrill(SecurityDisasterRecoveryDrill drill) {
        if (drill == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (drill.getDrillDate() == null) {
            drill.setDrillDate(LocalDate.now());
        }
        if (!StringUtils.hasText(drill.getScenario())) {
            throw new IllegalArgumentException("scenario 不能为空");
        }
        if (!StringUtils.hasText(drill.getResult())) {
            drill.setResult("SUCCESS");
        }
        drill.setId(null);
        drill.setScenario(trimToLen(drill.getScenario(), 256));
        drill.setTargetKey(trimToLen(drill.getTargetKey(), 64));
        drill.setResult(normalizeResult(drill.getResult()));
        drill.setSummary(trimToLen(drill.getSummary(), 1024));
        drill.setEvidenceUri(trimToLen(drill.getEvidenceUri(), 512));
        drill.setDetailsJson(trimToLen(drill.getDetailsJson(), 8192));
        return drillRepository.save(drill);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportReport() {
        List<SecurityBackupPlan> plans = planRepository.findAll();
        List<SecurityDisasterRecoveryDrill> drills = drillRepository.findTop50ByOrderByDrillDateDesc();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("plans", plans.stream().map(this::toPlanDto).toList());
        report.put("drills", drills.stream().map(this::toDrillDto).toList());
        report.put("markdown", buildMarkdown(plans, drills));
        return report;
    }

    public String normalizeResult(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase();
        if (v.isEmpty()) {
            return "SUCCESS";
        }
        return switch (v) {
            case "OK", "PASS", "SUCCESS" -> "SUCCESS";
            case "FAIL", "FAILED", "ERROR" -> "FAIL";
            case "RUNNING", "PENDING" -> v;
            default -> v;
        };
    }

    private Map<String, Object> toPlanDto(SecurityBackupPlan plan) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", plan.getId() != null ? plan.getId().toString() : null);
        dto.put("targetKey", plan.getTargetKey());
        dto.put("title", plan.getTitle());
        dto.put("description", plan.getDescription());
        dto.put("schedule", plan.getSchedule());
        dto.put("retentionDays", plan.getRetentionDays());
        dto.put("enabled", plan.getEnabled());
        dto.put("lastRunAt", plan.getLastRunAt() != null ? plan.getLastRunAt().toString() : null);
        dto.put("lastResult", plan.getLastResult());
        dto.put("notes", plan.getNotes());
        return dto;
    }

    private Map<String, Object> toDrillDto(SecurityDisasterRecoveryDrill drill) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", drill.getId() != null ? drill.getId().toString() : null);
        dto.put("drillDate", drill.getDrillDate() != null ? drill.getDrillDate().toString() : null);
        dto.put("scenario", drill.getScenario());
        dto.put("targetKey", drill.getTargetKey());
        dto.put("result", drill.getResult());
        dto.put("rpoMinutes", drill.getRpoMinutes());
        dto.put("rtoMinutes", drill.getRtoMinutes());
        dto.put("summary", drill.getSummary());
        dto.put("evidenceUri", drill.getEvidenceUri());
        dto.put("detailsJson", drill.getDetailsJson());
        return dto;
    }

    private void normalizePlan(SecurityBackupPlan plan) {
        plan.setTargetKey(trimToLen(plan.getTargetKey(), 64));
        plan.setTitle(trimToLen(plan.getTitle(), 128));
        plan.setDescription(trimToLen(plan.getDescription(), 512));
        plan.setSchedule(trimToLen(plan.getSchedule(), 128));
        plan.setNotes(trimToLen(plan.getNotes(), 2048));
    }

    private String buildMarkdown(List<SecurityBackupPlan> plans, List<SecurityDisasterRecoveryDrill> drills) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 备份恢复与灾备演练报告\n\n");
        sb.append("- 生成时间：").append(Instant.now()).append("\n");
        sb.append("- 备份策略数：").append(plans.size()).append("\n");
        sb.append("- 演练记录数：").append(drills.size()).append("\n\n");

        sb.append("## 备份策略清单\n\n");
        if (plans.isEmpty()) {
            sb.append("- （暂无）\n\n");
        } else {
            for (SecurityBackupPlan plan : plans) {
                sb.append("- ").append(Objects.toString(plan.getTitle(), "未命名")).append("（")
                    .append(Objects.toString(plan.getTargetKey(), "-")).append("）");
                sb.append("：启用=").append(Boolean.TRUE.equals(plan.getEnabled()) ? "是" : "否");
                if (plan.getLastRunAt() != null) {
                    sb.append("，最近执行=").append(plan.getLastRunAt());
                }
                if (StringUtils.hasText(plan.getLastResult())) {
                    sb.append("，结果=").append(plan.getLastResult());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 灾备演练记录\n\n");
        if (drills.isEmpty()) {
            sb.append("- （暂无）\n");
        } else {
            for (SecurityDisasterRecoveryDrill drill : drills) {
                sb.append("- ").append(drill.getDrillDate()).append("：").append(drill.getScenario());
                sb.append("（结果=").append(drill.getResult()).append("）");
                if (drill.getRtoMinutes() != null) {
                    sb.append("，RTO=").append(drill.getRtoMinutes()).append("min");
                }
                if (drill.getRpoMinutes() != null) {
                    sb.append("，RPO=").append(drill.getRpoMinutes()).append("min");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private Optional<Instant> parseInstant(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(raw.trim()));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private String trimToLen(String raw, int max) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            String trimmed = s.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
