package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitCommand;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitStatus;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Owns deterministic content hashing and immutable measurement-unit snapshots. */
@Component
public class MeasurementUnitSnapshotCodec {

    private final ObjectMapper objectMapper;
    private final ObjectWriter canonicalWriter;

    public MeasurementUnitSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.canonicalWriter = objectMapper
            .copy()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .writer();
    }

    public MeasurementUnitView toView(
        UUID id,
        MeasurementUnitCommand command,
        MeasurementUnitStatus status,
        int version,
        Instant createdAt,
        Instant updatedAt
    ) {
        MeasurementUnitCommand normalized = MeasurementUnitContract.normalize(command);
        return new MeasurementUnitView(
            id,
            normalized.code(),
            normalized.name(),
            normalized.symbol(),
            normalized.quantityKind(),
            normalized.conversionFactor(),
            normalized.baseUnitRef(),
            normalized.precision(),
            status,
            version,
            checksum(normalized, status),
            createdAt,
            updatedAt
        );
    }

    public String write(MeasurementUnitView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_SNAPSHOT_INVALID",
                "Measurement unit snapshot cannot be serialized",
                MeasurementUnitException.Kind.BAD_REQUEST
            );
        }
    }

    public MeasurementUnitView readView(String json) {
        try {
            return objectMapper.readValue(json, MeasurementUnitView.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_SNAPSHOT_INVALID",
                "Measurement unit snapshot cannot be read",
                MeasurementUnitException.Kind.CONFLICT
            );
        }
    }

    public String contentChecksum(MeasurementUnitView view) {
        return checksum(MeasurementUnitContract.fromView(view), view.status());
    }

    private String checksum(MeasurementUnitCommand command, MeasurementUnitStatus status) {
        try {
            return sha256(
                canonicalWriter.writeValueAsString(new UnitContent(MeasurementUnitContract.normalize(command), status))
            );
        } catch (JsonProcessingException exception) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_SNAPSHOT_INVALID",
                "Measurement unit content cannot be serialized for hashing",
                MeasurementUnitException.Kind.BAD_REQUEST
            );
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record UnitContent(MeasurementUnitCommand command, MeasurementUnitStatus status) {}
}
