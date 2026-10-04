package com.yuzhi.dts.platform.service.modeling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads one dbt model node from a manifest without taking ownership of its SQL.
 * The importer is deliberately side-effect free; persistence and idempotent write-back
 * are performed by the application service after this contract has passed validation.
 */
public final class ModelingDbtManifestImporter {

    private ModelingDbtManifestImporter() {}

    public record ImportedModel(
        String uniqueId,
        String name,
        String sql,
        List<String> fields,
        List<String> dependencies,
        String contentChecksum,
        String idempotencyKey
    ) {}

    public record ImportSummary(String projectId, String manifestVersion, List<ImportedModel> models, String idempotencyKey) {}

    public static final class ImportException extends IllegalArgumentException {

        private final DbtModelingContract.ErrorCode code;

        public ImportException(DbtModelingContract.ErrorCode code, String message) {
            super(message);
            this.code = code;
        }

        public DbtModelingContract.ErrorCode code() {
            return code;
        }
    }

    public static ImportSummary importModel(DbtModelingContract.ManifestImportRequest request) {
        List<String> requestIssues = DbtModelingContract.validateManifestImport(request);
        if (!requestIssues.isEmpty()) {
            DbtModelingContract.ErrorCode code = requestIssues.contains(DbtModelingContract.ErrorCode.DBT_MODEL_NOT_FOUND.name())
                ? DbtModelingContract.ErrorCode.DBT_MODEL_NOT_FOUND
                : DbtModelingContract.ErrorCode.DBT_MANIFEST_INVALID;
            throw new ImportException(code, String.join(",", requestIssues));
        }

        Map<String, Object> nodes = map(request.manifest().get("nodes"));
        Map<String, Object> rawNode = map(nodes.get(request.modelUniqueId()));
        if (rawNode.isEmpty() || !"model".equals(string(rawNode.get("resource_type")))) {
            throw new ImportException(DbtModelingContract.ErrorCode.DBT_MODEL_NOT_FOUND, "dbt manifest 中不存在目标 model 节点");
        }

        String sql = firstNonBlank(request.sql(), string(rawNode.get("compiled_code")), string(rawNode.get("raw_code")), string(rawNode.get("raw_sql")));
        if (sql == null) {
            throw new ImportException(DbtModelingContract.ErrorCode.DBT_ARTIFACT_UNREADABLE, "dbt model SQL 不可读");
        }

        String name = string(rawNode.get("name"));
        if (name == null) {
            throw new ImportException(DbtModelingContract.ErrorCode.DBT_MANIFEST_INVALID, "dbt model name 不能为空");
        }

        List<String> fields = readFields(rawNode.get("columns"));
        List<String> dependencies = readDependencies(rawNode.get("depends_on"));
        ImportedModel model = new ImportedModel(
            request.modelUniqueId(),
            name,
            sql,
            fields,
            dependencies,
            sha256(sql),
            request.idempotencyKey()
        );
        return new ImportSummary(request.projectId(), request.manifestVersion(), List.of(model), request.idempotencyKey());
    }

    private static List<String> readFields(Object value) {
        Map<String, Object> columns = map(value);
        List<String> fields = new ArrayList<>();
        columns.forEach((key, column) -> {
            Map<String, Object> columnMap = map(column);
            String name = firstNonBlank(string(columnMap.get("name")), key);
            if (name != null) fields.add(name);
        });
        return List.copyOf(fields);
    }

    private static List<String> readDependencies(Object value) {
        Map<String, Object> dependencyMap = map(value);
        Object nodes = dependencyMap.get("nodes");
        if (!(nodes instanceof List<?> list)) return List.of();
        return list.stream().map(ModelingDbtManifestImporter::string).filter(valueItem -> valueItem != null).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (key != null) result.put(String.valueOf(key), item);
        });
        return result;
    }

    private static String string(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
