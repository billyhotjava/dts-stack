package com.yuzhi.dts.platform.service.goldenchain.ingestion;

import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainSourceKind;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GoldenChainIngestionTaskNormalizer {

    public GoldenChainIngestionTaskView normalize(Map<String, Object> task) {
        String taskId = text(first(task, "id", "taskId"));
        String taskName = defaultText(text(first(task, "name", "taskName")), "入湖任务负责人");
        GoldenChainSourceKind sourceKind = resolveSourceKind(task);
        Map<String, Object> latestExecution = map(first(task, "latestExecution", "execution", "lastExecution"));
        String executionId = text(first(latestExecution, "id", "executionId"));
        String status = normalizeStatus(text(first(latestExecution, "status", "state")));
        String evidenceRef = buildEvidenceRef(taskId, executionId);
        String checkpointRef = text(first(latestExecution, "checkpoint", "checkpointRef", "cursorValue"));
        List<String> odsOutputs = resolveOdsOutputs(task);

        GoldenChainStageSnapshot stageSnapshot = "failed".equals(status)
            ? GoldenChainStageSnapshot.blocked(
                GoldenChainStage.INGESTION_READY,
                taskName,
                GoldenChainBlockerCode.BLOCKED_INGESTION,
                defaultText(text(first(latestExecution, "errorMessage", "message", "reason")), "入湖任务执行失败")
            )
            : GoldenChainStageSnapshot.ready(GoldenChainStage.INGESTION_READY, taskName, evidenceRef);

        return new GoldenChainIngestionTaskView(
            sourceKind,
            "ingestion_task",
            taskId,
            sourceRefType(sourceKind),
            text(first(task, "sourceDataSourceId", "source_data_source_id", "sourceId")),
            List.copyOf(odsOutputs),
            checkpointRef,
            stageSnapshot
        );
    }

    private GoldenChainSourceKind resolveSourceKind(Map<String, Object> task) {
        String raw = text(first(task, "sourceType", "source_type", "connectorType"));
        if (raw == null) {
            Map<String, Object> sourceConfig = map(first(task, "sourceConfig", "source_config"));
            raw = text(first(sourceConfig, "sourceCategory", "connectorType", "readerType"));
        }
        String normalized = defaultText(raw, "jdbc").toLowerCase(Locale.ROOT);
        if (normalized.contains("api") || normalized.contains("http")) {
            return GoldenChainSourceKind.API;
        }
        if (normalized.contains("file") || normalized.contains("excel") || normalized.contains("csv")) {
            return GoldenChainSourceKind.FILE;
        }
        return GoldenChainSourceKind.JDBC;
    }

    private List<String> resolveOdsOutputs(Map<String, Object> task) {
        List<String> outputs = new ArrayList<>();
        Object tableMapping = first(task, "tableMapping", "table_mapping");
        if (tableMapping instanceof Iterable<?> items) {
            for (Object item : items) {
                Map<String, Object> mapping = map(item);
                addIfPresent(outputs, text(first(mapping, "target", "targetTable", "odsTable")));
            }
        }

        Map<String, Object> sourceConfig = map(first(task, "sourceConfig", "source_config"));
        Map<String, Object> resource = map(first(sourceConfig, "resource"));
        addIfPresent(outputs, text(first(resource, "targetTable", "odsTable")));

        Map<String, Object> destinationConfig = map(first(task, "destinationConfig", "destination_config"));
        Object table = first(destinationConfig, "table", "targetTable", "odsTable");
        if (table instanceof Iterable<?> tables) {
            for (Object value : tables) {
                addIfPresent(outputs, text(value));
            }
        } else {
            addIfPresent(outputs, text(table));
        }
        return outputs.stream().distinct().toList();
    }

    private String sourceRefType(GoldenChainSourceKind sourceKind) {
        return sourceKind == GoldenChainSourceKind.API ? "infra_api_data_source" : "infra_data_source";
    }

    private String buildEvidenceRef(String taskId, String executionId) {
        if (taskId == null || executionId == null) {
            return "ingestion://tasks/" + defaultText(taskId, "unknown");
        }
        return "ingestion://tasks/" + taskId + "/executions/" + executionId;
    }

    private String normalizeStatus(String status) {
        return defaultText(status, "success").trim().toLowerCase(Locale.ROOT);
    }

    private void addIfPresent(List<String> values, String value) {
        if (value != null) {
            values.add(value);
        }
    }

    private Object first(Map<String, Object> values, String... keys) {
        if (values == null) {
            return null;
        }
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
