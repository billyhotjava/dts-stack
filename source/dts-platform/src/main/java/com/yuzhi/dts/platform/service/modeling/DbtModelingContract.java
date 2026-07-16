package com.yuzhi.dts.platform.service.modeling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Contract objects for registering dbt-native models without taking ownership of their SQL. */
public final class DbtModelingContract {

    private DbtModelingContract() {}

    public enum ErrorCode {
        DBT_MANIFEST_INVALID,
        DBT_MODEL_NOT_FOUND,
        DBT_ARTIFACT_UNREADABLE,
    }

    public record ManifestImportRequest(
        String projectId,
        String manifestVersion,
        String modelUniqueId,
        Map<String, Object> manifest,
        String sql,
        String idempotencyKey
    ) {}

    public record ImportResult(String modelSpecId, String dbtUniqueId, String status, int artifactCount) {}

    public record DriftResult(String modelSpecId, String status, List<String> issues) {}

    public static List<String> validateManifestImport(ManifestImportRequest request) {
        List<String> issues = new ArrayList<>();
        if (
            request == null ||
            blank(request.projectId()) ||
            blank(request.manifestVersion()) ||
            request.manifest() == null ||
            request.manifest().isEmpty() ||
            blank(request.idempotencyKey())
        ) {
            issues.add(ErrorCode.DBT_MANIFEST_INVALID.name());
        }
        if (request == null || blank(request.modelUniqueId()) || !request.modelUniqueId().startsWith("model.")) {
            issues.add(ErrorCode.DBT_MODEL_NOT_FOUND.name());
        }
        return List.copyOf(issues);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
