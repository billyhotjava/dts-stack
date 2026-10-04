package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.service.dto.MetricArtifactPreviewResult;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPublishResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MetricArtifactPublishService {

    private final MetricArtifactGenerationService generationService;
    private final PlatformContractClient platformContractClient;

    public MetricArtifactPublishService(
        MetricArtifactGenerationService generationService,
        PlatformContractClient platformContractClient
    ) {
        this.generationService = generationService;
        this.platformContractClient = platformContractClient;
    }

    public MetricArtifactPublishResult publishDryRun(
        String manifestContent,
        MetricArtifactGenerationService.PreviewActor actor
    ) {
        MetricArtifactPreviewResult preview = generationService.preview(manifestContent, actor);
        if (preview == null || !preview.valid()) {
            return MetricArtifactPublishResult.invalid(
                preview == null ? List.of("artifact preview returned empty result") : preview.errors(),
                preview == null ? Map.of() : preview.summary()
            );
        }

        MetricPolicyAuditSupport.PolicyMetadata policy = MetricPolicyAuditSupport.metadata(preview.artifacts().get("securityPolicyJson"));
        String modelName = String.valueOf(preview.summary().getOrDefault("modelName", "all"));
        Map<String, Object> releaseGate = platformContractClient.checkDbtReleaseGate(
            new PlatformContractClient.DbtReleaseGateRequest(
                modelName,
                null,
                null,
                true,
                policy.policySource(),
                policy.predicateHash()
            )
        );
        List<String> warnings = new ArrayList<>(preview.warnings());
        warnings.add("Publish dry-run re-resolved dts-platform security policy before dbt release gate.");
        return MetricArtifactPublishResult.valid(
            preview.summary(),
            preview.artifacts(),
            warnings,
            releaseGate,
            policy.policySource(),
            policy.predicateHash()
        );
    }
}
