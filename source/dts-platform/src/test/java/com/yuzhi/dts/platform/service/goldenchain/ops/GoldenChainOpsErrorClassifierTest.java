package com.yuzhi.dts.platform.service.goldenchain.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GoldenChainOpsErrorClassifierTest {

    private final GoldenChainOpsErrorClassifier classifier = new GoldenChainOpsErrorClassifier();

    @Test
    void classifiesConnectionAndCredentialFailuresAsRetryableOperations() {
        GoldenChainOpsErrorClassification network = classifier.classify("Connection timed out while calling upstream API");
        GoldenChainOpsErrorClassification credential = classifier.classify("401 unauthorized: invalid token");

        assertThat(network.category()).isEqualTo(GoldenChainOpsErrorCategory.CONNECTION_FAILED);
        assertThat(network.retryAction()).isEqualTo(GoldenChainOpsRetryAction.RETRY_INGESTION);
        assertThat(network.recoverable()).isTrue();
        assertThat(credential.category()).isEqualTo(GoldenChainOpsErrorCategory.CREDENTIAL_FAILED);
        assertThat(credential.retryAction()).isEqualTo(GoldenChainOpsRetryAction.ROTATE_CREDENTIAL);
    }

    @Test
    void classifiesQualityModelLineageAndPermissionFailures() {
        assertThat(classifier.classify("dbt test failed: not_null_orders").category())
            .isEqualTo(GoldenChainOpsErrorCategory.DATA_QUALITY_FAILED);
        assertThat(classifier.classify("dbt build failed: relation not found").category())
            .isEqualTo(GoldenChainOpsErrorCategory.MODEL_BUILD_FAILED);
        assertThat(classifier.classify("lineage parse failed: cannot resolve source").category())
            .isEqualTo(GoldenChainOpsErrorCategory.LINEAGE_FAILED);
        assertThat(classifier.classify("permission denied by RLS policy").category())
            .isEqualTo(GoldenChainOpsErrorCategory.PERMISSION_FAILED);
    }

    @Test
    void unknownErrorFallsBackToSystemExceptionWithManualReview() {
        GoldenChainOpsErrorClassification classification = classifier.classify("NullPointerException at worker");

        assertThat(classification.category()).isEqualTo(GoldenChainOpsErrorCategory.SYSTEM_EXCEPTION);
        assertThat(classification.retryAction()).isEqualTo(GoldenChainOpsRetryAction.MANUAL_REVIEW);
        assertThat(classification.suggestedAction()).contains("排查");
    }
}
