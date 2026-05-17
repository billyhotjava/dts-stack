package com.yuzhi.dts.metrics.web.rest;

import com.yuzhi.dts.metrics.service.MetricArtifactGenerationService;
import com.yuzhi.dts.metrics.service.MetricArtifactPublishService;
import com.yuzhi.dts.metrics.service.MetricPackValidationService;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPreviewResult;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPublishResult;
import com.yuzhi.dts.metrics.service.dto.MetricPackValidationResult;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics/packs")
public class MetricPackResource {

    private final MetricPackValidationService validationService;
    private final MetricArtifactGenerationService artifactGenerationService;
    private final MetricArtifactPublishService artifactPublishService;

    public MetricPackResource(
        MetricPackValidationService validationService,
        MetricArtifactGenerationService artifactGenerationService,
        MetricArtifactPublishService artifactPublishService
    ) {
        this.validationService = validationService;
        this.artifactGenerationService = artifactGenerationService;
        this.artifactPublishService = artifactPublishService;
    }

    @PostMapping(value = "/validate", consumes = { "application/yaml", "text/yaml", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public MetricPackValidationResult validate(@RequestBody String manifestContent) {
        return validationService.validateManifest(manifestContent);
    }

    @PostMapping(value = "/import", consumes = { "application/yaml", "text/yaml", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<Map<String, Object>> importPack(@RequestBody String manifestContent, @RequestHeader HttpHeaders headers) {
        if (!hasForwardAuthIdentity(headers)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(importDenied("forward-auth identity headers are required"));
        }
        MetricPackValidationResult validation = validationService.validateManifest(manifestContent);
        if (!validation.valid()) {
            return ResponseEntity.badRequest().body(importResult(false, validation, null));
        }
        MetricArtifactPreviewResult preview = artifactGenerationService.preview(manifestContent, previewActor(headers));
        if (!preview.valid()) {
            return ResponseEntity.badRequest().body(importResult(false, validation, preview));
        }
        return ResponseEntity.accepted().body(importResult(true, validation, preview));
    }

    @PostMapping(value = "/preview-artifacts", consumes = { "application/yaml", "text/yaml", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<MetricArtifactPreviewResult> previewArtifacts(@RequestBody String manifestContent, @RequestHeader HttpHeaders headers) {
        if (!hasForwardAuthIdentity(headers)) {
            return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(MetricArtifactPreviewResult.invalid(List.of("forward-auth identity headers are required"), Map.of()));
        }
        MetricArtifactPreviewResult preview = artifactGenerationService.preview(manifestContent, previewActor(headers));
        return preview.valid() ? ResponseEntity.ok(preview) : ResponseEntity.badRequest().body(preview);
    }

    @PostMapping(value = "/publish-dry-run", consumes = { "application/yaml", "text/yaml", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<MetricArtifactPublishResult> publishDryRun(@RequestBody String manifestContent, @RequestHeader HttpHeaders headers) {
        if (!hasForwardAuthIdentity(headers)) {
            return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(MetricArtifactPublishResult.invalid(List.of("forward-auth identity headers are required"), Map.of()));
        }
        MetricArtifactPublishResult result = artifactPublishService.publishDryRun(manifestContent, previewActor(headers));
        return result.valid() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    private boolean hasForwardAuthIdentity(HttpHeaders headers) {
        return StringUtils.hasText(firstHeader(headers, "X-DTS-User"));
    }

    private MetricArtifactGenerationService.PreviewActor previewActor(HttpHeaders headers) {
        String username = firstHeader(headers, "X-DTS-User");
        return new MetricArtifactGenerationService.PreviewActor(
            username,
            roles(firstHeader(headers, "X-DTS-Roles")),
            firstHeader(headers, "X-DTS-Dept-Code"),
            firstHeader(headers, "X-DTS-Personnel-Level")
        );
    }

    private static List<String> roles(String rawRoles) {
        if (!StringUtils.hasText(rawRoles)) {
            return List.of();
        }
        return Arrays
            .stream(rawRoles.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toList();
    }

    private static String firstHeader(HttpHeaders headers, String name) {
        return headers != null ? headers.getFirst(name) : null;
    }

    private Map<String, Object> importDenied(String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", false);
        result.put("mode", "DRY_RUN");
        result.put("error", error);
        result.put("importedAt", Instant.now().toString());
        return result;
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
