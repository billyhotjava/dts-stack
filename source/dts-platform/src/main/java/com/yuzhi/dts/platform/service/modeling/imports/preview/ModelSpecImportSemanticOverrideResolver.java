package com.yuzhi.dts.platform.service.modeling.imports.preview;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import com.yuzhi.dts.platform.service.modeling.imports.classifier.ModelConversionClassifier;
import com.yuzhi.dts.platform.service.modeling.imports.classifier.ModelConversionClassifier.ClassificationInput;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Column;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionResult;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Grain;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Kind;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.ModelSpecImportPreviewException;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.SemanticOverride;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.SemanticStandardBindingOverride;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resolves the D13 semantic allowlist without mutating inspected technical facts. */
final class ModelSpecImportSemanticOverrideResolver {

    private static final Set<String> FIELD_ROLES = Set.of(
        "KEY",
        "BUSINESS_KEY",
        "ATTRIBUTE",
        "DIMENSION",
        "TIME",
        "DATE",
        "TIMESTAMP",
        "MEASURE",
        "METRIC"
    );

    Map<String, SemanticOverride> index(List<SemanticOverride> overrides, Map<String, PackageModel> models) {
        Map<String, SemanticOverride> result = new LinkedHashMap<>();
        for (SemanticOverride override : overrides == null ? List.<SemanticOverride>of() : overrides) {
            if (override == null || text(override.modelUniqueId()) == null || !models.containsKey(text(override.modelUniqueId()))) {
                throw invalid("semanticOverrides must reference an inspected model uniqueId");
            }
            String uniqueId = text(override.modelUniqueId());
            if (result.putIfAbsent(uniqueId, override) != null) {
                throw invalid("semanticOverrides cannot contain duplicate model uniqueIds");
            }
        }
        return Map.copyOf(result);
    }

    ResolvedSemanticOverride resolve(PackageModel source, SemanticOverride override) {
        if (override == null) {
            return new ResolvedSemanticOverride(source, List.of());
        }
        if (source == null || !source.dbtUniqueId().equals(text(override.modelUniqueId()))) {
            throw invalid("semantic override does not match the inspected model");
        }

        Set<String> columns = new LinkedHashSet<>();
        for (Column column : safe(source.columns())) {
            if (column != null && text(column.name()) != null) {
                columns.add(column.name());
            }
        }
        requireReferences(columns, override.grain() == null ? List.of() : override.grain().keys(), "grain.keys");
        requireReferences(columns, override.businessKeys(), "businessKeys");
        requireReferences(columns, override.fieldRoles() == null ? List.of() : override.fieldRoles().keySet(), "fieldRoles");

        Map<String, String> roles = new LinkedHashMap<>();
        SemanticMetadata current = source.semantics();
        if (current != null && current.fieldRoles() != null) {
            roles.putAll(current.fieldRoles());
        }
        if (override.fieldRoles() != null) {
            override.fieldRoles().forEach((field, role) -> roles.put(field, normalizedRole(role)));
        }
        for (String field : safe(override.businessKeys())) {
            String existing = roles.get(field);
            if (existing != null && !Set.of("KEY", "BUSINESS_KEY").contains(normalizedRole(existing))) {
                throw invalid("businessKeys conflicts with fieldRoles for " + field);
            }
            roles.put(field, "KEY");
        }

        List<Column> projectedColumns = safe(source.columns())
            .stream()
            .map(column ->
                roles.containsKey(column.name())
                    ? new Column(column.name(), column.description(), column.dataType(), roles.get(column.name()), column.tests())
                    : column
            )
            .toList();
        Grain grain = override.grain() == null
            ? current == null ? null : current.grain()
            : new Grain(text(override.grain().statement()), immutableText(override.grain().keys()));
        String modelType = override.modelType() == null
            ? current == null ? null : current.modelType()
            : enumName(ModelType.class, override.modelType(), "modelType");
        String layer = override.layer() == null
            ? current == null ? null : current.layer()
            : enumName(Layer.class, override.layer(), "layer");
        List<String> scenarios = override.consumptionScenarios() == null
            ? current == null ? List.of() : safe(current.consumptionScenarios())
            : immutableText(override.consumptionScenarios());
        SemanticMetadata semantics = new SemanticMetadata(
            modelType,
            layer,
            grain,
            current == null ? null : current.factShape(),
            current == null ? null : current.timeSemantics(),
            current == null ? null : current.domainCode(),
            current == null ? List.of() : safe(current.sourceRefs()),
            scenarios,
            Map.copyOf(roles),
            current == null ? null : current.dimensionStrategy(),
            current == null ? null : current.dimensionDefinitionCode(),
            "preview.semanticOverrides",
            current != null && current.technicalOnly()
        );
        PackageModel projected = new PackageModel(
            source.dbtUniqueId(),
            override.businessName() == null ? source.name() : requiredText(override.businessName(), "businessName"),
            override.businessDefinition() == null ? source.description() : requiredText(override.businessDefinition(), "businessDefinition"),
            source.resourcePath(),
            source.sql(),
            source.materialization(),
            source.config(),
            source.tags(),
            projectedColumns,
            source.tests(),
            source.dependencies(),
            semantics,
            reclassify(source, semantics)
        );
        return new ResolvedSemanticOverride(projected, standardBindings(columns, override.standardBindings()));
    }

    private static List<StandardBinding> standardBindings(
        Set<String> columns,
        List<SemanticStandardBindingOverride> overrides
    ) {
        List<StandardBinding> result = new ArrayList<>();
        for (SemanticStandardBindingOverride override : safe(overrides)) {
            if (override == null || text(override.fieldName()) == null || !columns.contains(text(override.fieldName()))) {
                throw invalid("standardBindings must reference an inspected technical field");
            }
            if (
                invalidVersion(override.standardElementVersion()) ||
                invalidVersion(override.referenceCodeVersion()) ||
                invalidVersion(override.measurementUnitVersion())
            ) {
                throw invalid("standard binding versions must be positive");
            }
            result.add(
                new StandardBinding(
                    text(override.fieldName()),
                    override.standardElementId(),
                    override.standardElementVersion(),
                    text(override.referenceCode()),
                    override.referenceCodeVersion(),
                    override.measurementUnitId(),
                    override.measurementUnitVersion(),
                    text(override.securityLevel())
                )
            );
        }
        return List.copyOf(result);
    }

    private static boolean invalidVersion(Integer value) {
        return value != null && value < 1;
    }

    private static ConversionResult reclassify(PackageModel source, SemanticMetadata semantics) {
        ConversionResult current = source.conversion();
        if (
            current == null ||
            current.mode() != ConversionMode.BLOCKED ||
            current.reasonCodes() == null ||
            current.reasonCodes().isEmpty() ||
            current.reasonCodes().stream().anyMatch(reason -> reason == null || !reason.startsWith("MISSING_"))
        ) {
            return current;
        }
        return new ModelConversionClassifier()
            .classify(
                new ClassificationInput(
                    "model",
                    source.materialization(),
                    source.sql() == null ? null : source.sql().effectiveSql(),
                    semantics,
                    false
                )
            );
    }

    private static void requireReferences(Set<String> columns, Iterable<String> references, String field) {
        for (String reference : references == null ? List.<String>of() : references) {
            if (text(reference) == null || !columns.contains(text(reference))) {
                throw invalid(field + " must reference inspected technical fields");
            }
        }
    }

    private static String normalizedRole(String value) {
        String role = text(value);
        if (role == null || !FIELD_ROLES.contains(role.toUpperCase(Locale.ROOT))) {
            throw invalid("fieldRoles contains an unsupported role");
        }
        return role.toUpperCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> String enumName(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, requiredText(value, field).toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " contains an unsupported value");
        }
    }

    private static String requiredText(String value, String field) {
        String normalized = text(value);
        if (normalized == null) {
            throw invalid(field + " cannot be blank");
        }
        return normalized;
    }

    private static List<String> immutableText(List<String> values) {
        List<String> result = safe(values).stream().map(ModelSpecImportSemanticOverrideResolver::text).toList();
        if (result.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("semantic override lists cannot contain blank values");
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static ModelSpecImportPreviewException invalid(String message) {
        return new ModelSpecImportPreviewException(
            "MODEL_IMPORT_SEMANTIC_OVERRIDE_INVALID",
            message,
            Kind.BAD_REQUEST,
            null
        );
    }

    record ResolvedSemanticOverride(PackageModel model, List<StandardBinding> standardBindings) {
        ResolvedSemanticOverride {
            standardBindings = standardBindings == null ? List.of() : List.copyOf(standardBindings);
        }
    }
}
