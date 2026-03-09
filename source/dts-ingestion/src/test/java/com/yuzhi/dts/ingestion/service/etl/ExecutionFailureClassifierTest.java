package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;

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
}
