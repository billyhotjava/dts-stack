package com.yuzhi.dts.metrics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.service.MetricArtifactGenerationService;
import com.yuzhi.dts.metrics.service.MetricArtifactPublishService;
import com.yuzhi.dts.metrics.service.MetricFormulaSqlGenerator;
import com.yuzhi.dts.metrics.service.MetricPackValidationService;
import com.yuzhi.dts.metrics.service.PlatformContractClient;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPreviewResult;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPublishResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

class MetricPackResourceTest {

    @Test
    void previewArtifactsUsesForwardAuthHeadersAsPreviewActor() {
        CapturingArtifactService artifactService = new CapturingArtifactService();
        MetricPackResource resource = new MetricPackResource(new MetricPackValidationService(), artifactService, new CapturingPublishService());
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
        MetricPackResource resource = new MetricPackResource(new MetricPackValidationService(), artifactService, new CapturingPublishService());

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
        MetricPackResource resource = new MetricPackResource(new MetricPackValidationService(), artifactService, new CapturingPublishService());

        var response = resource.importPack(validMinimalManifest(), new HttpHeaders());

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).containsEntry("accepted", false);
        assertThat(response.getBody()).containsEntry("error", "forward-auth identity headers are required");
        assertThat(artifactService.actor).isNull();
    }

    @Test
    void publishDryRunUsesForwardAuthHeadersAsPublishActor() {
        CapturingPublishService publishService = new CapturingPublishService();
        MetricPackResource resource = new MetricPackResource(new MetricPackValidationService(), new CapturingArtifactService(), publishService);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-DTS-User", "ptrdemo");
        headers.add("X-DTS-Roles", "ROLE_PTR, ROLE_VIEWER");
        headers.add("X-DTS-Dept-Code", "D01");
        headers.add("X-DTS-Personnel-Level", "INTERNAL");

        resource.publishDryRun(validMinimalManifest(), headers);

        assertThat(publishService.actor).isNotNull();
        assertThat(publishService.actor.username()).isEqualTo("ptrdemo");
        assertThat(publishService.actor.userRoles()).containsExactly("ROLE_PTR", "ROLE_VIEWER");
        assertThat(publishService.actor.userDeptCode()).isEqualTo("D01");
    }

    @Test
    void publishDryRunRejectsMissingForwardAuthIdentity() {
        CapturingPublishService publishService = new CapturingPublishService();
        MetricPackResource resource = new MetricPackResource(new MetricPackValidationService(), new CapturingArtifactService(), publishService);

        var response = resource.publishDryRun(validMinimalManifest(), new HttpHeaders());

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().valid()).isFalse();
        assertThat(response.getBody().errors()).contains("forward-auth identity headers are required");
        assertThat(publishService.actor).isNull();
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

    private static final class CapturingPublishService extends MetricArtifactPublishService {

        private MetricArtifactGenerationService.PreviewActor actor;

        private CapturingPublishService() {
            super(
                new MetricArtifactGenerationService(
                    new MetricPackValidationService(),
                    new MetricFormulaSqlGenerator(),
                    new PlatformContractClient(new DtsMetricsProperties(), RestClient.builder().build())
                ),
                new PlatformContractClient(new DtsMetricsProperties(), RestClient.builder().build())
            );
        }

        @Override
        public MetricArtifactPublishResult publishDryRun(String manifestContent, MetricArtifactGenerationService.PreviewActor actor) {
            this.actor = actor;
            return MetricArtifactPublishResult.valid(
                Map.of("modelName", "dws_demo_summary"),
                Map.of(),
                List.of(),
                Map.of("decision", "PASS"),
                "platform-row-filter",
                "sha256:test"
            );
        }
    }
}
