package com.yuzhi.dts.platform.service.modeling;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Pure business contract for a reusable dimension definition. */
public final class DimensionDefinitionContract {

    public static final Set<String> CREATE_FIELDS = Set.of(
        "domainId",
        "name",
        "abbreviation",
        "definition",
        "ownerId",
        "reuseScope",
        "hierarchies",
        "idempotencyKey",
        "scopeType",
        "dataMartId",
        "attributes"
    );

    public static final Set<String> UPDATE_FIELDS = Set.of(
        "name",
        "abbreviation",
        "definition",
        "ownerId",
        "reuseScope",
        "hierarchies",
        "scopeType",
        "dataMartId",
        "attributes"
    );

    private static final Pattern SEMANTIC_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private DimensionDefinitionContract() {}

    public enum Status {
        DRAFT,
        CURRENT,
        RETIRED,
    }

    public enum ReuseScope {
        PLAN,
        DOMAIN,
        TENANT,
    }

    public enum ScopeType {
        DOMAIN,
        DATA_MART,
    }

    public record HierarchyLevelSemantic(String code, String name, int order) {}

    public record HierarchySemantic(String code, String name, List<HierarchyLevelSemantic> levels) {}

    public record AttributeSemantic(
        String code,
        String name,
        String definition,
        boolean primaryKey,
        String standardRef,
        String standardVersion,
        int order
    ) {}

    public record FieldIssue(String field, String code, String message) {}

    public record CreateCommand(
        UUID domainId,
        String name,
        String abbreviation,
        String definition,
        String ownerId,
        ReuseScope reuseScope,
        List<HierarchySemantic> hierarchies,
        String idempotencyKey,
        ScopeType scopeType,
        UUID dataMartId,
        List<AttributeSemantic> attributes
    ) {
        public CreateCommand(
            UUID domainId,
            String name,
            String abbreviation,
            String definition,
            String ownerId,
            ReuseScope reuseScope,
            List<HierarchySemantic> hierarchies,
            String idempotencyKey
        ) {
            this(
                domainId,
                name,
                abbreviation,
                definition,
                ownerId,
                reuseScope,
                hierarchies,
                idempotencyKey,
                null,
                null,
                List.of()
            );
        }

        public CreateCommand(
            UUID domainId,
            String name,
            String definition,
            String ownerId,
            ReuseScope reuseScope,
            List<HierarchySemantic> hierarchies,
            String idempotencyKey
        ) {
            this(domainId, name, null, definition, ownerId, reuseScope, hierarchies, idempotencyKey, null, null, List.of());
        }
    }

    public record UpdateCommand(
        String name,
        String abbreviation,
        String definition,
        String ownerId,
        ReuseScope reuseScope,
        List<HierarchySemantic> hierarchies,
        ScopeType scopeType,
        UUID dataMartId,
        List<AttributeSemantic> attributes
    ) {
        public UpdateCommand(
            String name,
            String abbreviation,
            String definition,
            String ownerId,
            ReuseScope reuseScope,
            List<HierarchySemantic> hierarchies
        ) {
            this(name, abbreviation, definition, ownerId, reuseScope, hierarchies, null, null, null);
        }

        public UpdateCommand(String name, String definition, String ownerId, ReuseScope reuseScope, List<HierarchySemantic> hierarchies) {
            this(name, null, definition, ownerId, reuseScope, hierarchies, null, null, null);
        }
    }

    public record View(
        UUID id,
        String systemCode,
        UUID domainId,
        String name,
        String abbreviation,
        String definition,
        String ownerId,
        ReuseScope reuseScope,
        List<HierarchySemantic> hierarchies,
        Status status,
        int revision,
        String checksum,
        long usageCount,
        Instant createdAt,
        Instant updatedAt,
        ScopeType scopeType,
        UUID dataMartId,
        List<AttributeSemantic> attributes
    ) {
        public View {
            hierarchies = hierarchies == null ? List.of() : List.copyOf(hierarchies);
            scopeType = scopeType == null ? ScopeType.DOMAIN : scopeType;
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
        }

        public View(
            UUID id,
            String systemCode,
            UUID domainId,
            String name,
            String abbreviation,
            String definition,
            String ownerId,
            ReuseScope reuseScope,
            List<HierarchySemantic> hierarchies,
            Status status,
            int revision,
            String checksum,
            long usageCount,
            Instant createdAt,
            Instant updatedAt
        ) {
            this(
                id,
                systemCode,
                domainId,
                name,
                abbreviation,
                definition,
                ownerId,
                reuseScope,
                hierarchies,
                status,
                revision,
                checksum,
                usageCount,
                createdAt,
                updatedAt,
                ScopeType.DOMAIN,
                null,
                List.of()
            );
        }
    }

    public static List<FieldIssue> validateCreate(CreateCommand command) {
        if (command == null) {
            return List.of(new FieldIssue("command", "DIMENSION_DEFINITION_DOMAIN_REQUIRED", "Dimension definition is required"));
        }

        List<FieldIssue> issues = new java.util.ArrayList<>();
        if (command.domainId() == null) {
            issues.add(new FieldIssue("domainId", "DIMENSION_DEFINITION_DOMAIN_REQUIRED", "Domain is required"));
        }
        issues.addAll(validateBusinessFields(
            command.name(),
            command.definition(),
            command.ownerId(),
            command.reuseScope(),
            command.hierarchies(),
            command.scopeType(),
            command.dataMartId(),
            command.attributes()
        ));
        if (isBlank(command.idempotencyKey())) {
            issues.add(new FieldIssue("idempotencyKey", "DIMENSION_DEFINITION_IDEMPOTENCY_KEY_REQUIRED", "Idempotency key is required"));
        }
        return List.copyOf(issues);
    }

    public static List<FieldIssue> validateUpdate(UpdateCommand command) {
        if (command == null) {
            return List.of(new FieldIssue("command", "DIMENSION_DEFINITION_NAME_REQUIRED", "Dimension definition is required"));
        }
        return List.copyOf(
            validateBusinessFields(
                command.name(),
                command.definition(),
                command.ownerId(),
                command.reuseScope(),
                command.hierarchies(),
                command.scopeType(),
                command.dataMartId(),
                command.attributes()
            )
        );
    }

    private static List<FieldIssue> validateBusinessFields(
        String name,
        String definition,
        String ownerId,
        ReuseScope reuseScope,
        List<HierarchySemantic> hierarchies,
        ScopeType scopeType,
        UUID dataMartId,
        List<AttributeSemantic> attributes
    ) {
        List<FieldIssue> issues = new java.util.ArrayList<>();
        if (isBlank(name)) {
            issues.add(new FieldIssue("name", "DIMENSION_DEFINITION_NAME_REQUIRED", "Name is required"));
        }
        if (isBlank(definition)) {
            issues.add(new FieldIssue("definition", "DIMENSION_DEFINITION_DEFINITION_REQUIRED", "Definition is required"));
        }
        if (isBlank(ownerId)) {
            issues.add(new FieldIssue("ownerId", "DIMENSION_DEFINITION_OWNER_REQUIRED", "Owner is required"));
        }
        if (reuseScope == null) {
            issues.add(new FieldIssue("reuseScope", "DIMENSION_DEFINITION_REUSE_SCOPE_REQUIRED", "Reuse scope is required"));
        }
        if (!hasValidHierarchies(hierarchies)) {
            issues.add(new FieldIssue("hierarchies", "DIMENSION_DEFINITION_HIERARCHY_INVALID", "Hierarchy semantics are invalid"));
        }
        ScopeType effectiveScope = scopeType == null ? ScopeType.DOMAIN : scopeType;
        if (effectiveScope == ScopeType.DATA_MART && dataMartId == null) {
            issues.add(
                new FieldIssue(
                    "dataMartId",
                    "DIMENSION_DEFINITION_DATA_MART_REQUIRED",
                    "Data mart is required for a data-mart-scoped dimension"
                )
            );
        }
        if (effectiveScope == ScopeType.DOMAIN && dataMartId != null) {
            issues.add(
                new FieldIssue(
                    "dataMartId",
                    "DIMENSION_DEFINITION_DATA_MART_NOT_ALLOWED",
                    "Domain-scoped dimension cannot reference a data mart"
                )
            );
        }
        if (!hasValidAttributes(attributes)) {
            issues.add(
                new FieldIssue(
                    "attributes",
                    "DIMENSION_DEFINITION_ATTRIBUTES_INVALID",
                    "Dimension attributes must use unique semantic codes and continuous ordering"
                )
            );
        }
        return issues;
    }

    private static boolean hasValidAttributes(List<AttributeSemantic> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return true;
        }
        if (attributes.size() > 200) {
            return false;
        }
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        int primaryKeys = 0;
        for (AttributeSemantic attribute : attributes) {
            if (
                attribute == null ||
                !isSemanticCode(attribute.code()) ||
                isBlank(attribute.name()) ||
                isBlank(attribute.definition()) ||
                attribute.order() < 1 ||
                !codes.add(attribute.code()) ||
                !orders.add(attribute.order()) ||
                isBlank(attribute.standardRef()) != isBlank(attribute.standardVersion())
            ) {
                return false;
            }
            if (attribute.primaryKey()) {
                primaryKeys++;
            }
        }
        for (int order = 1; order <= attributes.size(); order++) {
            if (!orders.contains(order)) {
                return false;
            }
        }
        return primaryKeys <= 1;
    }

    private static boolean hasValidHierarchies(List<HierarchySemantic> hierarchies) {
        if (hierarchies == null) {
            return true;
        }
        Set<String> hierarchyCodes = new HashSet<>();
        for (HierarchySemantic hierarchy : hierarchies) {
            if (
                hierarchy == null ||
                !isSemanticCode(hierarchy.code()) ||
                isBlank(hierarchy.name()) ||
                hierarchy.levels() == null ||
                hierarchy.levels().isEmpty() ||
                !hierarchyCodes.add(hierarchy.code()) ||
                !hasValidLevels(hierarchy.levels())
            ) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasValidLevels(List<HierarchyLevelSemantic> levels) {
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (HierarchyLevelSemantic level : levels) {
            if (
                level == null ||
                !isSemanticCode(level.code()) ||
                isBlank(level.name()) ||
                level.order() < 1 ||
                !codes.add(level.code()) ||
                !orders.add(level.order())
            ) {
                return false;
            }
        }
        for (int order = 1; order <= levels.size(); order++) {
            if (!orders.contains(order)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSemanticCode(String value) {
        return value != null && SEMANTIC_CODE.matcher(value).matches();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
