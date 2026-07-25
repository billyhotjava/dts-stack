package com.yuzhi.dts.platform.service.modeling.imports.contract;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cross-environment contract for a single, self-contained DTS model package.
 *
 * <p>Tenant-owned UUIDs deliberately do not appear here. Stable dbt identifiers and
 * explicit business semantics are resolved against the target environment during preview.
 */
public final class ModelPackageContract {

    public static final String SCHEMA_VERSION = "dts.model-package/v1";
    public static final Pattern DIMENSION_DEFINITION_CODE = Pattern.compile("^dim_[0-9a-f]{32}$");

    private ModelPackageContract() {}

    public enum ConversionMode {
        DESIGNER_GENERATED,
        DBT_BACKED,
        BLOCKED,
        TECHNICAL_ONLY,
    }

    public record ModelPackage(
        String schemaVersion,
        String packageId,
        String packageChecksum,
        DbtMetadata dbt,
        Defaults defaults,
        List<SourceNode> sources,
        List<TechnicalNode> technicalNodes,
        List<PackageModel> models,
        List<ImportIssue> issues
    ) {}

    public record DbtMetadata(String projectName, String projectVersion, String manifestVersion, String adapterType) {}

    public record Defaults(String planRef, String domainRef) {}

    public record SourceNode(String dbtUniqueId, String name, String resourcePath, List<Column> columns) {}

    public record TechnicalNode(
        String dbtUniqueId,
        String name,
        String resourceType,
        String resourcePath,
        SqlArtifact sql,
        Map<String, Object> config,
        List<String> dependencies,
        List<String> tags,
        ConversionResult conversion
    ) {}

    public record PackageModel(
        String dbtUniqueId,
        String name,
        String description,
        String resourcePath,
        SqlArtifact sql,
        String materialization,
        Map<String, Object> config,
        List<String> tags,
        List<Column> columns,
        List<String> tests,
        List<String> dependencies,
        SemanticMetadata semantics,
        ConversionResult conversion
    ) {}

    /**
     * SQL text is embedded because v1 is one JSON document rather than a directory or ZIP.
     * Each representation has its own checksum and the selected effective representation is explicit.
     */
    public record SqlArtifact(
        String rawSql,
        String rawSqlChecksum,
        String compiledSql,
        String compiledSqlChecksum,
        String effectiveSql,
        String effectiveSqlChecksum,
        String effectiveSource
    ) {}

    public record Column(String name, String description, String dataType, String role, List<String> tests) {}

    public record SemanticMetadata(
        String modelType,
        String layer,
        Grain grain,
        String factShape,
        TimeSemantics timeSemantics,
        String domainCode,
        List<SourceRef> sourceRefs,
        List<String> consumptionScenarios,
        Map<String, String> fieldRoles,
        String dimensionStrategy,
        String dimensionDefinitionCode,
        String overrideSource,
        boolean technicalOnly
    ) {
        /** Compatibility constructor for packages produced before stable dimension references. */
        public SemanticMetadata(
            String modelType,
            String layer,
            Grain grain,
            String factShape,
            TimeSemantics timeSemantics,
            String domainCode,
            List<SourceRef> sourceRefs,
            List<String> consumptionScenarios,
            Map<String, String> fieldRoles,
            String dimensionStrategy,
            String overrideSource,
            boolean technicalOnly
        ) {
            this(
                modelType,
                layer,
                grain,
                factShape,
                timeSemantics,
                domainCode,
                sourceRefs,
                consumptionScenarios,
                fieldRoles,
                dimensionStrategy,
                null,
                overrideSource,
                technicalOnly
            );
        }
    }

    public record Grain(String statement, List<String> keys) {}

    public record TimeSemantics(String type, List<String> fields) {}

    public record SourceRef(String kind, String ref, String layer) {}

    public record ConversionResult(ConversionMode mode, List<String> reasonCodes) {}

    public record ImportIssue(
        String code,
        String severity,
        String fieldPath,
        String modelUniqueId,
        String message,
        String recoveryAction
    ) {}

    public static boolean isDimensionDefinitionCode(String value) {
        return value != null && DIMENSION_DEFINITION_CODE.matcher(value).matches();
    }
}
