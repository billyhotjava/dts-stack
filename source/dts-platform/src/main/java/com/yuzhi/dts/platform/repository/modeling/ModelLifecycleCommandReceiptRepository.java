package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Append-only, action-scoped idempotency receipts for canonical implementation writes. */
@Repository
public class ModelLifecycleCommandReceiptRepository {

    private static final TypeReference<List<FieldMapping>> FIELD_MAPPINGS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> SETTINGS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectWriter canonicalWriter;

    public ModelLifecycleCommandReceiptRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.canonicalWriter = objectMapper
            .copy()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .writer();
    }

    public String payloadHash(Object payload) {
        try {
            return sha256(canonicalWriter.writeValueAsBytes(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Lifecycle command payload cannot be serialized", exception);
        }
    }

    /** Serializes concurrent uses of the same receipt key for the duration of the caller transaction. */
    public void lockCommandKey(String tenantId, UUID modelSpecId, String action, String idempotencyKey) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 0))",
            resultSet -> null,
            receiptKey(tenantId, modelSpecId, action, idempotencyKey)
        );
    }

    public Optional<Receipt> find(String tenantId, UUID modelSpecId, String action, String idempotencyKey) {
        return jdbcTemplate
            .query(
                """
                select payload_hash, implementation_id, plan_id, model_revision, model_checksum,
                       ownership, project_key, dbt_unique_id, status, implementation_revision,
                       implementation_checksum, input_mode, inputs_json::text, field_mappings_json::text,
                       settings_json::text, materialization
                  from modeling_model_implementation_command_receipt
                 where tenant_id = ? and model_spec_id = ? and action = ? and idempotency_key = ?
                """,
                (row, rowNumber) -> {
                    InputMode inputMode = InputMode.valueOf(row.getString("input_mode"));
                    return new Receipt(
                        row.getString("payload_hash"),
                        new ImplementationView(
                            row.getObject("implementation_id", UUID.class),
                            modelSpecId,
                            row.getObject("plan_id", UUID.class),
                            row.getInt("model_revision"),
                            row.getString("model_checksum"),
                            ImplementationMode.valueOf(row.getString("ownership")),
                            row.getString("project_key"),
                            row.getString("dbt_unique_id"),
                            row.getString("status"),
                            row.getInt("implementation_revision"),
                            row.getString("implementation_checksum"),
                            inputMode,
                            readInputs(inputMode, row.getString("inputs_json")),
                            read(row.getString("field_mappings_json"), FIELD_MAPPINGS_TYPE),
                            read(row.getString("settings_json"), SETTINGS_TYPE),
                            row.getString("materialization")
                        )
                    );
                },
                requiredText(tenantId, "tenantId"),
                requiredUuid(modelSpecId, "modelSpecId"),
                requiredText(action, "action"),
                requiredText(idempotencyKey, "idempotencyKey")
            )
            .stream()
            .findFirst();
    }

    public void append(
        String tenantId,
        UUID modelSpecId,
        String action,
        String idempotencyKey,
        String payloadHash,
        ImplementationView result,
        String actorId,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            insert into modeling_model_implementation_command_receipt (
                id, tenant_id, model_spec_id, action, idempotency_key, payload_hash,
                implementation_id, plan_id, model_revision, model_checksum, ownership,
                project_key, dbt_unique_id, status, implementation_revision, implementation_checksum,
                input_mode, inputs_json, field_mappings_json, settings_json, materialization,
                created_by, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb),
                      cast(? as jsonb), cast(? as jsonb), ?, ?, ?)
            """,
            UUID.randomUUID(),
            requiredText(tenantId, "tenantId"),
            requiredUuid(modelSpecId, "modelSpecId"),
            requiredText(action, "action"),
            requiredText(idempotencyKey, "idempotencyKey"),
            requiredSha256(payloadHash, "payloadHash"),
            result.id(),
            result.planId(),
            result.revision(),
            result.modelChecksum(),
            result.ownership().name(),
            result.projectKey(),
            result.dbtUniqueId(),
            result.status(),
            result.implementationRevision(),
            result.implementationChecksum(),
            result.inputMode().name(),
            json(result.inputs()),
            json(result.fieldMappings()),
            json(result.settings()),
            result.materialization(),
            requiredText(actorId, "actorId"),
            Timestamp.from(now)
        );
    }

    private List<ImplementationInput> readInputs(InputMode mode, String json) {
        try {
            return switch (mode) {
                case PHYSICAL_ASSET -> List.copyOf(objectMapper.readValue(json, new TypeReference<List<PhysicalAssetInput>>() {}));
                case UPSTREAM_MODEL -> List.copyOf(objectMapper.readValue(json, new TypeReference<List<UpstreamModelInput>>() {}));
                case GENERATED -> List.copyOf(objectMapper.readValue(json, new TypeReference<List<GeneratedInput>>() {}));
            };
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored lifecycle command inputs are invalid", exception);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored lifecycle command result is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Lifecycle command result cannot be serialized", exception);
        }
    }

    private static String receiptKey(String tenantId, UUID modelSpecId, String action, String idempotencyKey) {
        return String.join(
            "\u001f",
            requiredText(tenantId, "tenantId"),
            requiredUuid(modelSpecId, "modelSpecId").toString(),
            requiredText(action, "action"),
            requiredText(idempotencyKey, "idempotencyKey")
        );
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static UUID requiredUuid(UUID value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String requiredSha256(String value, String name) {
        String result = requiredText(value, name).toLowerCase();
        if (!result.matches("^[0-9a-f]{64}$")) throw new IllegalArgumentException(name + " must be SHA-256");
        return result;
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Receipt(String payloadHash, ImplementationView result) {}
}
