package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.ingestion.config.IngestionProperties;
import org.junit.jupiter.api.Test;

class ExecutionFailureClassifierTest {

    @Test
    void shouldClassifyAirflowTimetableImportErrorAsRuntimeError() {
        String message =
            "Airflow 触发失败: [AIRFLOW_DAG_NOT_READY_TIMEOUT] DAG 导入失败: /opt/airflow/dags/task_dm.py | " +
            "AirflowTimetableInvalid: Exactly 5 or 6 columns has to be specified for iterator expression.";

        assertThat(ExecutionFailureClassifier.classify(message)).isEqualTo(ExecutionFailureClassifier.CATEGORY_RUNTIME);
    }

    @Test
    void shouldKeepGenericInvalidFormatAsDataQualityError() {
        assertThat(ExecutionFailureClassifier.classify("invalid date format in source row"))
            .isEqualTo(ExecutionFailureClassifier.CATEGORY_DATA_QUALITY);
    }

    @Test
    void shouldClassifyStructuredApiRuntimeErrors() {
        assertThat(ExecutionFailureClassifier.classify("API_RUNTIME_AUTH: HTTP 401"))
            .isEqualTo(ExecutionFailureClassifier.CATEGORY_PERMISSION);
        assertThat(ExecutionFailureClassifier.classify("API_RUNTIME_RATE_LIMIT: HTTP 429"))
            .isEqualTo(ExecutionFailureClassifier.CATEGORY_CONNECTION);
        assertThat(ExecutionFailureClassifier.classify("API_RUNTIME_NETWORK: timeout"))
            .isEqualTo(ExecutionFailureClassifier.CATEGORY_CONNECTION);
        assertThat(ExecutionFailureClassifier.classify("API_RUNTIME_SERVER: HTTP 503"))
            .isEqualTo(ExecutionFailureClassifier.CATEGORY_CONNECTION);
        assertThat(ExecutionFailureClassifier.classify("API_RUNTIME_BLOCKED_URL: private address"))
            .isEqualTo(ExecutionFailureClassifier.CATEGORY_GOVERNANCE_LIMIT);
        assertThat(ExecutionFailureClassifier.classify("API_RUNTIME_CONFIG: baseUrl is required"))
            .isEqualTo(ExecutionFailureClassifier.CATEGORY_GOVERNANCE_LIMIT);
        assertThat(ExecutionFailureClassifier.classify("API_RUNTIME_SCHEMA: recordPath did not resolve"))
            .isEqualTo(ExecutionFailureClassifier.CATEGORY_DATA_QUALITY);
        assertThat(ExecutionFailureClassifier.classify("API_RUNTIME_RESPONSE_TOO_LARGE: maxResponseBytes exceeded"))
            .isEqualTo(ExecutionFailureClassifier.CATEGORY_DATA_QUALITY);
        assertThat(ExecutionFailureClassifier.classify("API_RUNTIME_RESPONSE_PARSE: invalid json"))
            .isEqualTo(ExecutionFailureClassifier.CATEGORY_DATA_QUALITY);
    }

    @Test
    void shouldOnlyAutoRetryTransientApiRuntimeCategoriesByDefault() {
        assertThat(new IngestionProperties().getAutoRetry().getRetryableCategorySet())
            .contains(ExecutionFailureClassifier.CATEGORY_CONNECTION, ExecutionFailureClassifier.CATEGORY_RUNTIME)
            .doesNotContain(
                ExecutionFailureClassifier.CATEGORY_PERMISSION,
                ExecutionFailureClassifier.CATEGORY_DATA_QUALITY,
                ExecutionFailureClassifier.CATEGORY_GOVERNANCE_LIMIT
            );
    }
}
