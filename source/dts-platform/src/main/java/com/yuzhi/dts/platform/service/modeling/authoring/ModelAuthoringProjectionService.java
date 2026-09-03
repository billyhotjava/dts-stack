package com.yuzhi.dts.platform.service.modeling.authoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.etl.DbtSqlProjectionParser;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringProjection;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.ProjectionNode;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.BundleFileView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleView;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedNode;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedProject;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ProjectionCoverage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Conservative adapter from the frozen dbt bundle to visual/raw authoring nodes. */
@Service
public class ModelAuthoringProjectionService {

    private final AdvancedDbtDraftStaticValidator validator;
    private final ObjectMapper objectMapper;

    public ModelAuthoringProjectionService(AdvancedDbtDraftStaticValidator validator, ObjectMapper objectMapper) {
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public AuthoringProjection project(SourceBundleView source) {
        return project(source, null);
    }

    public AuthoringProjection project(SourceBundleView source, List<ModelField> modelSnapshotFields) {
        if (source == null || source.files().isEmpty()) {
            return AuthoringProjection.unknown("MODEL_AUTHORING_SOURCE_BUNDLE_UNAVAILABLE");
        }
        Set<String> snapshotColumns = modelSnapshotFields != null
            ? modelSnapshotFields
                .stream()
                .filter(Objects::nonNull)
                .map(ModelField::name)
                .filter(Objects::nonNull)
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
            : Set.of();
        boolean modelSnapshotAvailable = !snapshotColumns.isEmpty();
        Map<String, BundleFileView> files = new LinkedHashMap<>();
        source.files().forEach(file -> files.put(file.path(), file));
        ValidatedProject project;
        try {
            project = validator.validate(
                source.files().stream().collect(
                    java.util.stream.Collectors.toMap(
                        BundleFileView::path,
                        BundleFileView::content,
                        (left, right) -> left,
                        LinkedHashMap::new
                    )
                )
            );
        } catch (RuntimeException failure) {
            return rawFallback(source, failureCode(failure));
        }

        List<String> managedPaths = new ArrayList<>();
        List<ProjectionNode> rawNodes = new ArrayList<>();
        Set<String> reasons = new LinkedHashSet<>();
        List<ValidatedNode> models = project.nodes().stream().filter(node -> "MODEL".equals(node.nodeKind())).toList();
        boolean snapshotAppliesToProjectedModel = modelSnapshotAvailable && models.size() == 1;
        for (ValidatedNode node : models) {
            Map<String, String> expressions = DbtSqlProjectionParser.selectExpressionsByAlias(node.sql());
            Set<String> expectedColumns = new LinkedHashSet<>(schemaColumns(node.schema()));
            if (snapshotAppliesToProjectedModel) expectedColumns.addAll(snapshotColumns);
            boolean snapshotFieldsCovered =
                snapshotAppliesToProjectedModel &&
                !snapshotColumns.isEmpty() &&
                expressions.keySet().containsAll(snapshotColumns);
            List<String> structuralReasons = node
                .reasonCodes()
                .stream()
                .filter(reason -> !(snapshotAppliesToProjectedModel && "SOURCE_SEMANTICS_INCOMPLETE".equals(reason)))
                .filter(reason -> !(snapshotFieldsCovered && "SOURCE_FIELDS_UNVERIFIED".equals(reason)))
                .toList();
            boolean addressable =
                structuralReasons.isEmpty() &&
                !expressions.isEmpty() &&
                !containsTopLevelWildcard(node.sql()) &&
                (expectedColumns.isEmpty() || expressions.keySet().containsAll(expectedColumns));
            if (addressable) {
                managedPaths.add(node.resourcePath());
                continue;
            }
            reasons.addAll(structuralReasons);
            if (expressions.isEmpty()) reasons.add("MODEL_AUTHORING_PROJECTION_ALIAS_UNAVAILABLE");
            if (containsTopLevelWildcard(node.sql())) reasons.add("MODEL_AUTHORING_PROJECTION_WILDCARD_UNSAFE");
            if (!expectedColumns.isEmpty() && !expressions.keySet().containsAll(expectedColumns)) {
                reasons.add("MODEL_AUTHORING_PROJECTION_SCHEMA_INCOMPLETE");
            }
            rawNodes.add(rawNode(node, files.get(node.resourcePath())));
        }
        ProjectionCoverage coverage = managedPaths.isEmpty()
            ? ProjectionCoverage.NONE
            : rawNodes.isEmpty() ? ProjectionCoverage.FULL : ProjectionCoverage.PARTIAL;
        if (models.isEmpty()) {
            reasons.add("MODEL_AUTHORING_PROJECTION_MODEL_UNAVAILABLE");
            return rawFallback(source, reasons.iterator().next());
        }
        return new AuthoringProjection(
            coverage,
            source.lossless() && coverage == ProjectionCoverage.FULL,
            managedPaths,
            rawNodes,
            List.copyOf(reasons)
        );
    }

    private Set<String> schemaColumns(String schema) {
        if (schema == null || schema.isBlank()) return Set.of();
        try {
            JsonNode columns = objectMapper.readTree(schema).path("columns");
            if (!columns.isArray()) return Set.of();
            Set<String> result = new LinkedHashSet<>();
            columns.forEach(column -> {
                String name = column.path("name").asText("").trim().toLowerCase(Locale.ROOT);
                if (!name.isEmpty()) result.add(name);
            });
            return Set.copyOf(result);
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private static boolean containsTopLevelWildcard(String sql) {
        if (sql == null) return false;
        return sql.matches("(?is).*\\bselect\\s+(?:distinct\\s+)?(?:[a-zA-Z_][a-zA-Z0-9_]*\\s*\\.\\s*)?\\*.*");
    }

    private static ProjectionNode rawNode(ValidatedNode node, BundleFileView file) {
        return new ProjectionNode(
            node.dbtUniqueId(),
            "RAW_SQL",
            true,
            node.resourcePath(),
            1,
            1,
            file == null ? node.sqlChecksum() : file.checksum()
        );
    }

    private static AuthoringProjection rawFallback(SourceBundleView source, String reason) {
        List<ProjectionNode> nodes = source
            .files()
            .stream()
            .filter(file -> file.path().toLowerCase(Locale.ROOT).endsWith(".sql"))
            .map(file -> new ProjectionNode(file.path(), "RAW_SQL", true, file.path(), 1, 1, file.checksum()))
            .toList();
        return new AuthoringProjection(
            ProjectionCoverage.NONE,
            false,
            List.of(),
            nodes,
            List.of(reason == null || reason.isBlank() ? "MODEL_AUTHORING_PROJECTION_UNAVAILABLE" : reason)
        );
    }

    private static String failureCode(RuntimeException failure) {
        if (failure instanceof AdvancedDbtDraftStaticValidator.StaticValidationException validation) {
            return validation.code();
        }
        return Objects.toString(failure.getClass().getSimpleName(), "MODEL_AUTHORING_PROJECTION_UNAVAILABLE");
    }
}
