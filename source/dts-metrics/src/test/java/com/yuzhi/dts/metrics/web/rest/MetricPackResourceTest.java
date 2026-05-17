package com.yuzhi.dts.metrics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.service.MetricArtifactGenerationService;
import com.yuzhi.dts.metrics.service.MetricFormulaSqlGenerator;
import com.yuzhi.dts.metrics.service.MetricPackValidationService;
import com.yuzhi.dts.metrics.service.PlatformContractClient;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPreviewResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

class MetricPackResourceTest {

    @Test
    void previewArtifactsUsesForwardAuthHeadersAsPreviewActor() {
        CapturingArtifactService artifactService = new CapturingArtifactService();
        MetricPackResource resource = new MetricPackResource(new MetricPackValidationService(), artifactService);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-DTS-User", "ptrdemo");
        headers.add("X-DTS-Roles", "ROLE_PTR, ROLE_VIEWER");
        headers.add("X-DTS-Dept-Code", "D01");
        headers.add("X-DTS-Personnel-Level", "INTERNAL");

        resource.previewArtifacts("pack_id: demo", headers);

        assertThat(artifactService.actor).isNotNull();
        assertThat(artifactService.actor.username()).isEqualTo("ptrdemo");
        assertThat(artifactService.actor.userRoles()).containsExactly("ROLE_PTR", "ROLE_VIEWER");
        assertThat(artifactService.actor.userDeptCode()).isEqualTo("D01");
        assertThat(artifactService.actor.userClassification()).isEqualTo("INTERNAL");
    }

    @Test
    void previewArtifactsRejectsMissingForwardAuthIdentity() {
        CapturingArtifactService artifactService = new CapturingArtifactService();
        MetricPackResource resource = new MetricPackResource(new MetricPackValidationService(), artifactService);

        var response = resource.previewArtifacts("pack_id: demo", new HttpHeaders());

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().valid()).isFalse();
        assertThat(response.getBody().errors()).contains("forward-auth identity headers are required");
        assertThat(artifactService.actor).isNull();
    }

    @Test
    void importPackRejectsMissingForwardAuthIdentity() {
        CapturingArtifactService artifactService = new CapturingArtifactService();
        MetricPackResource resource = new MetricPackResource(new MetricPackValidationService(), artifactService);

        var response = resource.importPack(validMinimalManifest(), new HttpHeaders());

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).containsEntry("accepted", false);
        assertThat(response.getBody()).containsEntry("error", "forward-auth identity headers are required");
        assertThat(artifactService.actor).isNull();
    }

    private static String validMinimalManifest() {
        return """
            pack_id: demo
            pack_name: Demo Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            files:
              domains: domains.yml
              business_objects: business-objects.yml
              dimensions: dimensions.yml
              metrics: metrics.yml
              models: models.yml
              datasets: datasets.yml
            dependencies:
              platform_assets: []
            """;
    }

    private static final class CapturingArtifactService extends MetricArtifactGenerationService {

        private PreviewActor actor;

        private CapturingArtifactService() {
            super(
                new MetricPackValidationService(),
                new MetricFormulaSqlGenerator(),
                new PlatformContractClient(new DtsMetricsProperties(), RestClient.builder().build())
            );
        }

        @Override
        public MetricArtifactPreviewResult preview(String manifestContent, PreviewActor actor) {
            this.actor = actor;
            return MetricArtifactPreviewResult.valid(Map.of(), Map.of(), List.of());
        }
    }
}
