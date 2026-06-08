package com.yuzhi.dts.metrics.web.rest;

import com.yuzhi.dts.metrics.service.MetricModelLifecycleService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics/models")
public class MetricModelResource {

    private final MetricModelLifecycleService lifecycleService;

    public MetricModelResource(MetricModelLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/{modelId}/artifacts")
    public Map<String, Object> generateArtifacts(@PathVariable String modelId, @RequestBody(required = false) Map<String, Object> request) {
        return lifecycleService.generateArtifacts(modelId, request != null ? request : Map.of());
    }

    @PostMapping("/{modelId}/validate")
    public Map<String, Object> validateModel(@PathVariable String modelId, @RequestBody(required = false) Map<String, Object> request) {
        return lifecycleService.validateModel(modelId, request != null ? request : Map.of());
    }

    @PostMapping("/{modelId}/submit-review")
    public Map<String, Object> submitReview(@PathVariable String modelId) {
        return lifecycleService.submitReview(modelId);
    }

    @PostMapping("/{modelId}/publish-dry-run")
    public Map<String, Object> publishDryRun(@PathVariable String modelId) {
        return lifecycleService.publishDryRun(modelId);
    }

    @PostMapping("/{modelId}/publish")
    public Map<String, Object> publish(@PathVariable String modelId) {
        return lifecycleService.publish(modelId);
    }

    @PostMapping("/{modelId}/rollback")
    public Map<String, Object> rollback(@PathVariable String modelId, @RequestBody(required = false) Map<String, Object> request) {
        return lifecycleService.rollback(modelId, request != null ? request : Map.of());
    }

    @GetMapping("/{modelId}/versions")
    public Map<String, Object> versionHistory(@PathVariable String modelId) {
        return lifecycleService.versionHistory(modelId);
    }
}
