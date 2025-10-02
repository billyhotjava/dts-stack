package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import com.yuzhi.dts.platform.service.governance.QualityRuleService;
import com.yuzhi.dts.platform.service.governance.QualityRunService;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleBindingDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Transactional
public class QualityResource {

    private final QualityRuleService qualityRuleService;
    private final QualityRunService qualityRunService;

    public QualityResource(QualityRuleService qualityRuleService, QualityRunService qualityRunService) {
        this.qualityRuleService = qualityRuleService;
        this.qualityRunService = qualityRunService;
    }

    /**
     * POST /api/data-quality-runs/trigger
     * Triggers a quality rule execution resolved by dataset or explicit rule.
     */
    @PostMapping("/data-quality-runs/trigger")
    public ApiResponse<Map<String, Object>> trigger(@RequestParam(required = false) UUID datasetId, @RequestParam(required = false) UUID ruleId) {
        QualityRuleDto rule = resolveRule(datasetId, ruleId);
        if (rule == null) {
            return ApiResponses.ok(Map.of("status", "NO_RULE"));
        }
        UUID bindingId = rule.getBindings().stream().map(QualityRuleBindingDto::getId).filter(Objects::nonNull).findFirst().orElse(null);
        if (bindingId == null) {
            return ApiResponses.ok(Map.of("status", "NO_BINDING"));
        }
        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(rule.getId());
        request.setBindingId(bindingId);
        List<QualityRunDto> runs = qualityRunService.trigger(request, currentUser());
        QualityRunDto run = runs.isEmpty() ? null : runs.get(0);
        if (run == null) {
            return ApiResponses.ok(Map.of("status", "QUEUED"));
        }
        return ApiResponses.ok(Map.of(
            "id",
            run.getId(),
            "datasetId",
            run.getDatasetId(),
            "status",
            run.getStatus(),
            "startedAt",
            run.getStartedAt(),
            "message",
            run.getMessage()
        ));
    }

    /**
     * GET /api/data-quality-runs/latest
     * Returns the latest run summary for a dataset.
     */
    @GetMapping("/data-quality-runs/latest")
    public ApiResponse<Map<String, Object>> latest(@RequestParam UUID datasetId) {
        List<QualityRunDto> runs = qualityRunService.recentByDataset(datasetId, 1);
        QualityRunDto latest = runs.isEmpty() ? null : runs.get(0);
        if (latest == null) {
            return ApiResponses.ok(Map.of("datasetId", datasetId, "status", "EMPTY"));
        }
        return ApiResponses.ok(
            Map.of(
                "datasetId",
                datasetId,
                "time",
                latest.getFinishedAt() != null ? latest.getFinishedAt() : latest.getStartedAt(),
                "status",
                latest.getStatus(),
                "message",
                latest.getMessage(),
                "runId",
                latest.getId()
            )
        );
    }

    private QualityRuleDto resolveRule(UUID datasetId, UUID ruleId) {
        if (ruleId != null) {
            return qualityRuleService.getRule(ruleId);
        }
        return qualityRuleService
            .findByDataset(datasetId)
            .stream()
            .findFirst()
            .orElse(null);
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }
}
