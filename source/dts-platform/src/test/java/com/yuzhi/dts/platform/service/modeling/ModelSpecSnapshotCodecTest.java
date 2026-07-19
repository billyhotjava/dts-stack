package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecSnapshotCodecTest {

    private final ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(new ObjectMapper().findAndRegisterModules());

    @Test
    void hashesNormalizedContentWithoutIdempotencyOrServerManagedFields() {
        CreateModelSpecCommand first = command("idem-1");
        CreateModelSpecCommand replay = command("idem-2");

        assertThat(codec.requestHash(first)).isEqualTo(codec.requestHash(replay));
        assertThat(codec.contentChecksum(first)).hasSize(64);
        assertThat(codec.contentChecksum(codec.toCreatedView(UUID.randomUUID(), first, Instant.EPOCH)))
            .isEqualTo(codec.contentChecksum(first));
    }

    @Test
    void contentHashesDoNotDependOnEnvironmentJsonPrettyPrinting() {
        ObjectMapper prettyMapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
        ModelSpecSnapshotCodec prettyCodec = new ModelSpecSnapshotCodec(prettyMapper);
        CreateModelSpecCommand command = command("idem-profile-independent");

        assertThat(prettyCodec.contentChecksum(command)).isEqualTo(codec.contentChecksum(command));
        assertThat(prettyCodec.requestHash(command)).isEqualTo(codec.requestHash(command));
    }

    @Test
    void roundTripsTheExactResponseSnapshot() {
        CreateModelSpecCommand command = command("idem-1");
        Instant now = Instant.parse("2026-07-19T00:00:00Z");
        ModelSpecView view = codec.toCreatedView(UUID.fromString("30000000-0000-0000-0000-000000000001"), command, now);

        assertThat(codec.readView(codec.write(view))).isEqualTo(view);
    }

    private static CreateModelSpecCommand command(String idempotencyKey) {
        return new CreateModelSpecCommand(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            ModelType.FACT,
            Layer.DWD,
            " customer_detail ",
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per customer event", List.of("customer_id")),
            null,
            null,
            List.of(new ModelField("customer_id", "varchar", false, null, FieldRole.KEY, null)),
            List.of(
                new SourceRef(
                    SourceKind.TABLE,
                    "ods.customer",
                    Layer.ODS,
                    SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    0,
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    "v1"
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            idempotencyKey
        );
    }
}
