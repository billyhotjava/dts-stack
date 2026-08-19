package com.yuzhi.dts.platform.service.modeling.authoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelAuthoringSnapshotDecoderTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000092");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000092");
    private static final UUID SOURCE_ID = UUID.fromString("30000000-0000-0000-0000-000000000092");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ModelAuthoringSnapshotDecoder decoder = new ModelAuthoringSnapshotDecoder(objectMapper);

    @Test
    void decodesOneVersionedModelAndVisualImplementationSnapshot() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.set("modelSpec", objectMapper.valueToTree(modelCommand()));
        root.set(
            "visualImplementation",
            objectMapper.valueToTree(
                Map.of(
                    "projectKey",
                    "system_managed",
                    "dbtUniqueId",
                    "model.system_managed.orders",
                    "inputMode",
                    "PHYSICAL_ASSET",
                    "inputs",
                    List.of(Map.of("sourceBindingId", SOURCE_ID, "resolvedVersion", "binding-v1")),
                    "fieldMappings",
                    List.of(Map.of("sourceField", "src_1.order_id", "targetField", "order_id")),
                    "settings",
                    Map.of("targetPhysicalName", "dwd_orders", "loadStrategy", "FULL", "partitionFields", List.of()),
                    "ownership",
                    "DESIGNER_GENERATED",
                    "materialization",
                    "table",
                    "idempotencyKey",
                    "authoring-visual-92"
                )
            )
        );

        var decoded = decoder.decode(root);

        assertThat(decoded.valid()).isTrue();
        assertThat(decoded.modelSpec().name()).isEqualTo("orders");
        assertThat(decoded.visualImplementation()).isNotNull();
        assertThat(decoded.visualImplementation().projectKey()).isEqualTo("system_managed");
        assertThat(decoded.visualImplementation().command().inputs())
            .singleElement()
            .isEqualTo(new PhysicalAssetInput(SOURCE_ID, "binding-v1"));
        assertThat(decoded.visualImplementation().command().fieldMappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.sourceField()).isEqualTo("src_1.order_id");
            assertThat(mapping.targetField()).isEqualTo("order_id");
        });
    }

    @Test
    void keepsLegacyDirectModelSnapshotReadable() {
        var decoded = decoder.decode(objectMapper.valueToTree(modelCommand()));

        assertThat(decoded.valid()).isTrue();
        assertThat(decoded.visualImplementation()).isNull();
    }

    @Test
    void rejectsMalformedVisualImplementationWithoutGuessing() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.set("modelSpec", objectMapper.valueToTree(modelCommand()));
        root.set("visualImplementation", objectMapper.valueToTree(Map.of("inputMode", "PHYSICAL_ASSET", "inputs", List.of())));

        var decoded = decoder.decode(root);

        assertThat(decoded.valid()).isFalse();
        assertThat(decoded.issues()).extracting("code").containsExactly("MODEL_AUTHORING_VISUAL_IMPLEMENTATION_INVALID");
    }

    private static UpdateModelSpecCommand modelCommand() {
        return new UpdateModelSpecCommand(
            PLAN_ID,
            DOMAIN_ID,
            ModelType.DIMENSION,
            Layer.DWD,
            "orders",
            "Unified authoring model",
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per order", List.of("order_id")),
            null,
            null,
            List.of(new ModelField("order_id", "bigint", false, "src_1.order_id", FieldRole.KEY, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }
}
