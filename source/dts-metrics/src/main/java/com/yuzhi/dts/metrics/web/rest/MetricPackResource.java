package com.yuzhi.dts.metrics.web.rest;

import com.yuzhi.dts.metrics.service.MetricArtifactGenerationService;
import com.yuzhi.dts.metrics.service.MetricPackValidationService;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPreviewResult;
import com.yuzhi.dts.metrics.service.dto.MetricPackValidationResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics/packs")
public class MetricPackResource {

    private final MetricPackValidationService validationService;
    private final MetricArtifactGenerationService artifactGenerationService;

    public MetricPackResource(
        MetricPackValidationService validationService,
        MetricArtifactGenerationService artifactGenerationService
    ) {
        this.validationService = validationService;
        this.artifactGenerationService = artifactGenerationService;
    }

    @PostMapping(value = "/validate", consumes = { "application/yaml", "text/yaml", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public MetricPackValidationResult validate(@RequestBody String manifestContent) {
        return validationService.validateManifest(manifestContent);
    }

    @PostMapping(value = "/import", consumes = { "application/yaml", "text/yaml", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<Map<String, Object>> importPack(@RequestBody String manifestContent) {
        MetricPackValidationResult validation = validationService.validateManifest(manifestContent);
        if (!validation.valid()) {
            return ResponseEntity.badRequest().body(importResult(false, validation, null));
        }
        MetricArtifactPreviewResult preview = artifactGenerationService.preview(manifestContent);
        if (!preview.valid()) {
            return ResponseEntity.badRequest().body(importResult(false, validation, preview));
        }
        return ResponseEntity.accepted().body(importResult(true, validation, preview));
    }

    @PostMapping(value = "/preview-artifacts", consumes = { "application/yaml", "text/yaml", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<MetricArtifactPreviewResult> previewArtifacts(@RequestBody String manifestContent) {
        MetricArtifactPreviewResult preview = artifactGenerationService.preview(manifestContent);
        return preview.valid() ? ResponseEntity.ok(preview) : ResponseEntity.badRequest().body(preview);
    }

    private Map<String, Object> importResult(
        boolean accepted,
        MetricPackValidationResult validation,
        MetricArtifactPreviewResult preview
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", accepted);
        result.put("mode", "DRY_RUN");
        result.put("validation", validation);
        result.put("preview", preview);
        result.put("diff", Map.of("added", List.of(), "changed", List.of(), "removed", List.of(), "conflicts", List.of()));
        result.put(
            "nextActions",
            accepted
                ? List.of("review generated artifacts", "run platform permission checks", "submit through platform/dbt release gate")
                : List.of("fix manifest errors and revalidate")
        );
        result.put("importedAt", Instant.now().toString());
        return result;
    }
}
