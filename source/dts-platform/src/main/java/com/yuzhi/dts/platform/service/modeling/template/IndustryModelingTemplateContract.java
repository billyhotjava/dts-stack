package com.yuzhi.dts.platform.service.modeling.template;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Generic, versioned contract for optional modeling template packs. */
public final class IndustryModelingTemplateContract {

    public static final int VERSION = 1;
    public static final String EXPLICIT_INSTALLATION = "EXPLICIT";

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9-]{1,127}");

    private IndustryModelingTemplateContract() {}

    public record IndustryModelingTemplate(
        int contractVersion,
        String templateId,
        String version,
        String name,
        String description,
        String industry,
        boolean optional,
        String installationMode,
        List<BusinessProcessTemplate> businessProcesses,
        List<ConformedDimensionTemplate> conformedDimensions,
        List<ModelDiagnosticRule> diagnosticRules
    ) {
        public IndustryModelingTemplate {
            if (contractVersion != VERSION) {
                throw new IllegalArgumentException("Unsupported modeling template contract version: " + contractVersion);
            }
            templateId = requireIdentifier(templateId, "templateId");
            version = requireText(version, "version");
            name = requireText(name, "name");
            description = normalizeNullable(description);
            industry = normalizeNullable(industry);
            installationMode = requireText(installationMode, "installationMode");
            if (!optional || !EXPLICIT_INSTALLATION.equals(installationMode)) {
                throw new IllegalArgumentException("Modeling templates must be optional and explicitly installed");
            }
            businessProcesses = List.copyOf(Objects.requireNonNull(businessProcesses, "businessProcesses"));
            conformedDimensions = List.copyOf(Objects.requireNonNull(conformedDimensions, "conformedDimensions"));
            diagnosticRules = List.copyOf(Objects.requireNonNull(diagnosticRules, "diagnosticRules"));
        }
    }

    public record BusinessProcessTemplate(String processId, String name, String description) {
        public BusinessProcessTemplate {
            processId = requireIdentifier(processId, "processId");
            name = requireText(name, "name");
            description = normalizeNullable(description);
        }
    }

    public record ConformedDimensionTemplate(String dimensionId, String name, String sourceModel) {
        public ConformedDimensionTemplate {
            dimensionId = requireIdentifier(dimensionId, "dimensionId");
            name = requireText(name, "name");
            sourceModel = normalizeNullable(sourceModel);
        }
    }

    public record ModelDiagnosticRule(
        String modelName,
        List<String> findingsWhenEmpty,
        List<String> findingsWhenEmptyWithNonEmptyUpstream,
        List<String> recommendedQueries
    ) {
        public ModelDiagnosticRule {
            modelName = requireText(modelName, "modelName").toLowerCase(Locale.ROOT);
            findingsWhenEmpty = copyTextList(findingsWhenEmpty, "findingsWhenEmpty");
            findingsWhenEmptyWithNonEmptyUpstream = copyTextList(
                findingsWhenEmptyWithNonEmptyUpstream,
                "findingsWhenEmptyWithNonEmptyUpstream"
            );
            recommendedQueries = copyTextList(recommendedQueries, "recommendedQueries");
        }
    }

    private static String requireIdentifier(String value, String field) {
        String normalized = requireText(value, field).toLowerCase(Locale.ROOT);
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must use lowercase letters, digits, and hyphens");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> copyTextList(List<String> values, String field) {
        return Objects.requireNonNull(values, field).stream().map(value -> requireText(value, field)).toList();
    }
}
