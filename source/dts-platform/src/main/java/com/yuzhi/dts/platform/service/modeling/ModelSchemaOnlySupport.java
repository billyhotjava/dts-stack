package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Identifies a revision-pinned, source-free structure generator in the existing input contract. */
public final class ModelSchemaOnlySupport {
    public static final String GENERATOR = "SCHEMA_ONLY";
    private static final Set<String> SETTINGS = Set.of("targetPhysicalName", "loadStrategy", "partitionFields");

    private ModelSchemaOnlySupport() {}

    public static boolean isSchemaOnly(InputMode mode, List<ImplementationInput> inputs) {
        return mode == InputMode.GENERATED && inputs != null && inputs.size() == 1 &&
            inputs.getFirst() instanceof GeneratedInput input && GENERATOR.equals(input.generatorType());
    }

    public static boolean isSchemaOnly(ImplementationView implementation) {
        if (implementation == null) return false;
        if (isSchemaOnly(implementation.inputMode(), implementation.inputs())) return true;
        return implementation.ownership() == ModelSpecContract.ImplementationMode.DBT_MANAGED &&
            implementation.inputMode() == InputMode.GENERATED && implementation.inputs() != null && implementation.inputs().size() == 1 &&
            implementation.inputs().getFirst() instanceof GeneratedInput input && "DBT".equals(input.generatorType()) &&
            "SCHEMA_ONLY".equals(input.config().get("buildMode"));
    }

    public static boolean valid(SaveImplementationCommand command) {
        return command != null && isSchemaOnly(command.inputMode(), command.inputs()) &&
            command.inputs().getFirst() instanceof GeneratedInput input && input.config().isEmpty() &&
            command.fieldMappings().isEmpty() && validSettings(command.materialization(), command.settings());
    }

    public static boolean validSettings(String materialization, Map<String, Object> settings) {
        return "table".equals(materialization) && settings != null && SETTINGS.containsAll(settings.keySet()) &&
            "FULL".equals(settings.get("loadStrategy")) &&
            (!settings.containsKey("partitionFields") || settings.get("partitionFields") instanceof List<?> partitions && partitions.isEmpty());
    }

    public static String requirePhysicalColumnType(String value) {
        return ModelFieldPhysicalTypeContract.canonicalPostgresType(value);
    }

    public static String columnConfig(ModelSpecCompilerProjection.ImplementationProjection projection) {
        if (!validSettings(projection.materialization(), projection.settings()) || !projection.fieldMappings().isEmpty() ||
            !(projection.inputs().getFirst() instanceof GeneratedInput input) || !input.config().isEmpty()) {
            throw new ModelingDbtCompiler.CompileException("MODEL_SCHEMA_ONLY_CONFIGURATION_INVALID");
        }
        String columns = projection.typedFields().stream().map(field -> {
            if (projection.keyFields().contains(field.name()) && field.nullable()) {
                throw new ModelingDbtCompiler.CompileException("MODEL_SCHEMA_ONLY_KEY_NULLABLE");
            }
            String physicalType = ModelFieldPhysicalTypeContract.requireSupported(field.dataType()).postgresType();
            return "{'name':'" + field.name() + "','data_type':'" + physicalType + "','nullable':" +
                (field.nullable() ? "True" : "False") + "}";
        }).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String keys = projection.keyFields().stream().map(key -> "'" + key + "'")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return ", dts_columns=" + columns + ", dts_primary_keys=" + keys;
    }
}
