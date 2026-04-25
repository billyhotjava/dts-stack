package com.yuzhi.dts.ingestion.service.etl.api;

import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import com.yuzhi.dts.ingestion.service.etl.connector.SourceConnector;
import com.yuzhi.dts.ingestion.service.etl.connector.SourceConnectorContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ApiHttpSourceConnector implements SourceConnector {

    @Override
    public String connectorType() {
        return ApiConnectorTypes.CONNECTOR_TYPE;
    }

    @Override
    public boolean supports(SourceConnectorContext context) {
        return context != null && ApiConnectorTypes.isApiSourceType(context.sourceType());
    }

    @Override
    public void validate(SourceConnectorContext context) {
        if (context == null) {
            throw new IllegalArgumentException("API source context 不能为空");
        }
        if (context.sourceDataSourceId() == null) {
            throw new IllegalArgumentException("API 数据源不能为空");
        }
        String syncMode = StringUtils.hasText(context.syncMode()) ? context.syncMode().trim() : "full_refresh";
        if (!"full_refresh".equalsIgnoreCase(syncMode) && !"incremental".equalsIgnoreCase(syncMode)) {
            throw new IllegalArgumentException("API 数据接入暂只支持 full_refresh/incremental");
        }
    }

    @Override
    public ExecutionPlan buildExecutionPlan(SourceConnectorContext context) {
        validate(context);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceDataSourceId", context.sourceDataSourceId().toString());
        payload.put("sourceType", ApiConnectorTypes.CONNECTOR_TYPE);
        payload.put("sourceConfig", context.sourceConfig() == null ? Map.of() : context.sourceConfig());
        payload.put("syncMode", StringUtils.hasText(context.syncMode()) ? context.syncMode() : "full_refresh");
        payload.put("syncConfig", context.syncConfig() == null ? Map.of() : context.syncConfig());
        payload.put("streams", context.streams() == null ? List.of() : context.streams());

        ExecutionPlan.CheckpointPolicy checkpointPolicy = new ExecutionPlan.CheckpointPolicy(
            "incremental".equalsIgnoreCase(String.valueOf(payload.get("syncMode"))) ? "cursor" : "none",
            null,
            "task_success"
        );
        return new ExecutionPlan(
            "api-http",
            ApiConnectorTypes.CONNECTOR_TYPE,
            ApiSourceContracts.CONTRACT_VERSION,
            null,
            payload,
            List.of(),
            checkpointPolicy,
            Map.of("connectorType", ApiConnectorTypes.CONNECTOR_TYPE, "engine", "api-http")
        );
    }
}

