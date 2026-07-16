package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.etl.AirflowClient;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Production adapter from modeling runtime context to Airflow's DAG run API. */
@Component
public class ModelingAirflowGateway implements ModelingRuntimeSubmissionService.RuntimeGateway {

    private final AirflowClient airflowClient;

    public ModelingAirflowGateway(AirflowClient airflowClient) {
        this.airflowClient = airflowClient;
    }

    @Override
    public ModelingRuntimeSubmissionService.GatewayResult submit(String id, Map<String, Object> payload) {
        try {
            Optional<Map<String, Object>> response = airflowClient.triggerDag(id, Map.of("conf", payload));
            Map<String, Object> responseBody = response.orElseThrow(() -> new IllegalStateException("Airflow 未启用或无响应"));
            String runId = extract(responseBody, "dag_run_id", "run_id", "id");
            return ModelingRuntimeSubmissionService.GatewayResult.success(runId);
        } catch (RuntimeException exception) {
            return ModelingRuntimeSubmissionService.GatewayResult.rejected(exception.getMessage());
        }
    }

    private static String extract(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }
}
