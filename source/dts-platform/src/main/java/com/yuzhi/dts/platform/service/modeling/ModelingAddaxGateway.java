package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Production adapter from a modeling run to the existing Addax ingestion service. */
@Component
public class ModelingAddaxGateway implements ModelingRuntimeSubmissionService.RuntimeGateway {

    private final IngestionServiceClient ingestionServiceClient;

    public ModelingAddaxGateway(IngestionServiceClient ingestionServiceClient) {
        this.ingestionServiceClient = ingestionServiceClient;
    }

    @Override
    public ModelingRuntimeSubmissionService.GatewayResult submit(String id, Map<String, Object> payload) {
        final long taskId;
        try {
            taskId = Long.parseLong(id);
        } catch (NumberFormatException exception) {
            return ModelingRuntimeSubmissionService.GatewayResult.rejected("Addax task id 必须是数字");
        }
        ApiResponse<Map<String, Object>> response = ingestionServiceClient.executeTaskAsync(taskId);
        if (response == null || response.getStatus() >= 400) {
            return ModelingRuntimeSubmissionService.GatewayResult.rejected(response == null ? "Addax 无响应" : response.getMessage());
        }
        String executionId = extract(response.getData(), "executionId", "execution_id", "id", "taskId");
        return ModelingRuntimeSubmissionService.GatewayResult.success(executionId == null ? id : executionId);
    }

    private static String extract(Map<String, Object> data, String... keys) {
        if (data == null) return null;
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }
}
