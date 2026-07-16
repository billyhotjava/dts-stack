package com.yuzhi.dts.platform.service.modeling;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Boundary validation for a modeling run before anything is submitted to Airflow.
 * It keeps Addax batch, Airflow, dbt and PostgreSQL identifiers in one request context.
 */
public final class ModelingRunRequestContract {

    private ModelingRunRequestContract() {}

    public enum ErrorCode {
        MODEL_SPEC_REQUIRED,
        REVISION_INVALID,
        IDEMPOTENCY_REQUIRED,
        SOURCE_BATCH_NOT_FOUND,
        DBT_SELECTOR_REQUIRED,
        TARGET_TABLE_REQUIRED,
    }

    public record ExternalContext(
        String sourceBatchId,
        String addaxTaskId,
        String airflowDagId,
        String airflowRunId,
        String dbtRunId,
        String dbtSelector,
        String targetTable
    ) {}

    public record RunRequest(String modelSpecId, int revision, String idempotencyKey, ExternalContext externalContext) {}

    public record Issue(ErrorCode code, String message) {}

    public static List<Issue> validate(RunRequest request, Set<String> knownSourceBatches) {
        List<Issue> issues = new ArrayList<>();
        if (request == null || blank(request.modelSpecId())) issues.add(new Issue(ErrorCode.MODEL_SPEC_REQUIRED, "ModelSpec 必须先编译并登记"));
        if (request == null || request.revision() < 1) issues.add(new Issue(ErrorCode.REVISION_INVALID, "运行 revision 必须大于 0"));
        if (request == null || blank(request.idempotencyKey())) issues.add(new Issue(ErrorCode.IDEMPOTENCY_REQUIRED, "运行请求必须携带幂等键"));
        ExternalContext context = request == null ? null : request.externalContext();
        if (context == null || blank(context.dbtSelector())) issues.add(new Issue(ErrorCode.DBT_SELECTOR_REQUIRED, "运行请求必须携带 dbt selector"));
        if (context == null || blank(context.targetTable())) issues.add(new Issue(ErrorCode.TARGET_TABLE_REQUIRED, "运行请求必须携带 PostgreSQL 目标表"));
        if (context != null && !blank(context.sourceBatchId()) && (knownSourceBatches == null || !knownSourceBatches.contains(context.sourceBatchId()))) {
            issues.add(new Issue(ErrorCode.SOURCE_BATCH_NOT_FOUND, "Addax 批次不存在，无法提交运行"));
        }
        return List.copyOf(issues);
    }

    public static RunRequest requireValid(RunRequest request, Set<String> knownSourceBatches) {
        List<Issue> issues = validate(request, knownSourceBatches);
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.stream().map(issue -> issue.code().name()).toList().toString());
        return request;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
