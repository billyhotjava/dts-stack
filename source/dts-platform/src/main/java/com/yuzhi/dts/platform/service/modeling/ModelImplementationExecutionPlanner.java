package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Canonical, side-effect-free execution capability planner for ordinary ModelImplementation.
 *
 * <p>The API validator, lifecycle gate and compiler all consume this decision. Unsupported
 * settings therefore cannot be saved successfully and then fail later under a different
 * interpretation in the compiler.
 */
public final class ModelImplementationExecutionPlanner {

    public static final String DEFAULT_ADAPTER = "postgres";
    public static final String SYSTEM_MANAGED_PROJECT_KEY = "dts";

    private static final Pattern TARGET_IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Pattern DBT_RESOURCE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Set<String> SUPPORTED_ADAPTERS = Set.of(DEFAULT_ADAPTER);

    private ModelImplementationExecutionPlanner() {}

    public static ValidationResult plan(
        ModelSpecView owner,
        SaveImplementationCommand command,
        String dbtUniqueId
    ) {
        List<String> keyFields = owner == null || owner.fields() == null
            ? List.of()
            : owner
                .fields()
                .stream()
                .filter(Objects::nonNull)
                .filter(field -> field.role() == FieldRole.KEY)
                .map(ModelSpecContract.ModelField::name)
                .filter(ModelImplementationExecutionPlanner::notBlank)
                .distinct()
                .toList();
        return command == null
            ? invalid(
                "MODEL_IMPLEMENTATION_INPUT_REQUIRED",
                "implementation",
                "Implementation input is required",
                "CONFIGURE_IMPLEMENTATION"
            )
            : plan(
                keyFields,
                command.settings(),
                command.materialization(),
                dbtUniqueId,
                DEFAULT_ADAPTER
            );
    }

    public static String systemManagedDbtUniqueId(ModelSpecView owner) {
        if (owner == null || owner.id() == null || owner.planId() == null) return "";
        String nodeName = "model_" + owner.id().toString().replace("-", "_");
        return "model." + systemManagedDbtProjectKey(owner) + "." + nodeName;
    }

    public static String systemManagedDbtProjectKey(ModelSpecView owner) {
        if (owner == null || owner.planId() == null) return "";
        return SYSTEM_MANAGED_PROJECT_KEY;
    }

    public static ValidationResult plan(
        List<String> keyFields,
        Map<String, Object> settings,
        String materialization,
        String dbtUniqueId,
        String adapter
    ) {
        Map<String, Object> safeSettings = settings == null ? Map.of() : settings;
        if (!ModelLifecycleContract.IMPLEMENTATION_SETTING_KEYS.containsAll(safeSettings.keySet())) {
            return invalid(
                "IMPLEMENTATION_SETTING_NOT_ALLOWED",
                "settings",
                "Implementation settings contain an unsupported key",
                "REMOVE_UNSUPPORTED_SETTING"
            );
        }
        String effectiveAdapter = text(adapter).toLowerCase();
        if (!SUPPORTED_ADAPTERS.contains(effectiveAdapter)) {
            return invalid(
                "IMPLEMENTATION_ADAPTER_UNSUPPORTED",
                "adapter",
                "The selected adapter has not passed the materialization capability gate",
                "SELECT_SUPPORTED_EXECUTION_TARGET"
            );
        }
        String targetIdentifier = text(safeSettings.get("targetPhysicalName"));
        if (targetIdentifier.isEmpty()) {
            return invalid(
                "IMPLEMENTATION_TARGET_REQUIRED",
                "settings.targetPhysicalName",
                "A target relation name is required",
                "SET_TARGET_IDENTIFIER"
            );
        }
        if (!TARGET_IDENTIFIER.matcher(targetIdentifier).matches()) {
            return invalid(
                "IMPLEMENTATION_TARGET_IDENTIFIER_INVALID",
                "settings.targetPhysicalName",
                "The target relation name must use lower-case snake_case",
                "SET_TARGET_IDENTIFIER"
            );
        }
        String loadStrategy = text(safeSettings.get("loadStrategy")).toUpperCase();
        if (loadStrategy.isEmpty()) {
            return invalid(
                "IMPLEMENTATION_LOAD_STRATEGY_REQUIRED",
                "settings.loadStrategy",
                "A load strategy is required",
                "SELECT_LOAD_STRATEGY"
            );
        }
        if ("SNAPSHOT".equals(loadStrategy)) {
            return invalid(
                "IMPLEMENTATION_SNAPSHOT_STRATEGY_REQUIRED",
                "settings.loadStrategy",
                "Snapshot execution is not supported by the current materialization contract",
                "USE_FULL_OR_INCREMENTAL"
            );
        }
        if (!"FULL".equals(loadStrategy) && !"INCREMENTAL".equals(loadStrategy)) {
            return invalid(
                "IMPLEMENTATION_LOAD_STRATEGY_UNSUPPORTED",
                "settings.loadStrategy",
                "The selected load strategy is not supported",
                "SELECT_SUPPORTED_LOAD_STRATEGY"
            );
        }
        List<String> partitionFields = stringList(safeSettings.get("partitionFields"));
        if (!partitionFields.isEmpty()) {
            return invalid(
                "IMPLEMENTATION_PARTITION_UNSUPPORTED",
                "settings.partitionFields",
                "The PostgreSQL dbt adapter contract does not translate partition fields",
                "REMOVE_PARTITION_FIELDS"
            );
        }
        String requestedMaterialization = text(materialization).toLowerCase();
        boolean incremental = "INCREMENTAL".equals(loadStrategy);
        if (
            (incremental && !"incremental".equals(requestedMaterialization)) ||
            (!incremental && !Set.of("table", "view").contains(requestedMaterialization))
        ) {
            return invalid(
                "IMPLEMENTATION_MATERIALIZATION_CONFLICT",
                "materialization",
                "Materialization and load strategy must describe the same execution behavior",
                "ALIGN_MATERIALIZATION_AND_LOAD_STRATEGY"
            );
        }
        List<String> uniqueKey = keyFields == null
            ? List.of()
            : keyFields.stream().filter(ModelImplementationExecutionPlanner::notBlank).distinct().toList();
        if (incremental && uniqueKey.isEmpty()) {
            return invalid(
                "IMPLEMENTATION_INCREMENTAL_KEY_REQUIRED",
                "fields",
                "Incremental materialization requires at least one canonical KEY field",
                "ADD_KEY_FIELD"
            );
        }
        String nodeUniqueId = text(dbtUniqueId);
        String selector = resourceName(nodeUniqueId);
        if (selector == null) {
            return invalid(
                "IMPLEMENTATION_DBT_UNIQUE_ID_INVALID",
                "dbtUniqueId",
                "The system-managed dbt node identity is invalid",
                "REFRESH_IMPLEMENTATION_IDENTITY"
            );
        }
        String capability = incremental
            ? "INCREMENTAL_UNIQUE_KEY"
            : "view".equals(requestedMaterialization) ? "FULL_VIEW" : "FULL_TABLE";
        return new ValidationResult(
            true,
            "MODEL_IMPLEMENTATION_VALID",
            List.of(),
            new ExecutionPlan(
                "DBT",
                effectiveAdapter,
                nodeUniqueId,
                selector,
                targetIdentifier,
                requestedMaterialization,
                true,
                uniqueKey,
                List.of(capability, "RELATION_PROBE")
            )
        );
    }

    private static ValidationResult invalid(
        String code,
        String field,
        String message,
        String repairAction
    ) {
        return new ValidationResult(
            false,
            code,
            List.of(new Blocker(code, field, message, repairAction)),
            null
        );
    }

    private static String resourceName(String dbtUniqueId) {
        if (dbtUniqueId == null || dbtUniqueId.isBlank()) return null;
        String[] parts = dbtUniqueId.split("\\.", -1);
        if (
            parts.length != 3 ||
            !"model".equals(parts[0]) ||
            !DBT_RESOURCE_IDENTIFIER.matcher(parts[1]).matches() ||
            !DBT_RESOURCE_IDENTIFIER.matcher(parts[2]).matches()
        ) {
            return null;
        }
        return parts[2];
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(Objects::nonNull).map(Object::toString).map(String::trim).filter(
            ModelImplementationExecutionPlanner::notBlank
        ).toList();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public record Blocker(String code, String field, String message, String repairAction) {}

    public record ExecutionPlan(
        String engine,
        String adapter,
        String nodeUniqueId,
        String selector,
        String targetIdentifier,
        String effectiveMaterialization,
        boolean physicalExpected,
        List<String> uniqueKey,
        List<String> capabilityCodes
    ) {
        public ExecutionPlan {
            uniqueKey = uniqueKey == null ? List.of() : List.copyOf(uniqueKey);
            capabilityCodes = capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes);
        }
    }

    public record ValidationResult(
        boolean valid,
        String code,
        List<Blocker> blockers,
        ExecutionPlan executionPlan
    ) {
        public ValidationResult {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }
}
