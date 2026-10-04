package com.yuzhi.dts.platform.service.modeling.imports.proof;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stateless, tenant/actor-bound proof that a normalized dbt package passed archive inspection. */
@Component
public class ModelSpecImportInspectionProofCodec {

    public static final Duration PROOF_TTL = Duration.ofMinutes(30);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String PROOF_VERSION = "v1";
    private static final String CANONICALIZATION_VERSION = "model-package-checksum/v1";
    private static final String SIGNING_DOMAIN = "dts:model-import:inspection-proof:v1";
    private static final int MAX_PROOF_LENGTH = 4096;

    private final ModelMaterializationProperties materializationProperties;
    private final WarehousePlanActorProvider actorProvider;
    private final String tenantId;
    private final Clock clock;

    @Autowired
    public ModelSpecImportInspectionProofCodec(
        ModelMaterializationProperties materializationProperties,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this(materializationProperties, actorProvider, tenantId, Clock.systemUTC());
    }

    ModelSpecImportInspectionProofCodec(
        ModelMaterializationProperties materializationProperties,
        WarehousePlanActorProvider actorProvider,
        String tenantId,
        Clock clock
    ) {
        this.materializationProperties = Objects.requireNonNull(materializationProperties, "materializationProperties is required");
        this.actorProvider = Objects.requireNonNull(actorProvider, "actorProvider is required");
        this.tenantId = canonicalIdentity(tenantId, "tenant");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public IssuedProof issue(ModelPackage modelPackage) {
        Objects.requireNonNull(modelPackage, "modelPackage is required");
        String actorId = currentActorId();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(PROOF_TTL);
        Claims claims = new Claims(
            PROOF_VERSION,
            CANONICALIZATION_VERSION,
            tenantId,
            actorId,
            ModelPackageChecksum.compute(modelPackage),
            issuedAt.getEpochSecond(),
            expiresAt.getEpochSecond()
        );
        String payload = encode(claims.serialize());
        String unsigned = PROOF_VERSION + "." + payload;
        return new IssuedProof(unsigned + "." + signature(unsigned), expiresAt);
    }

    public void verify(String inspectionProof, JsonNode modelPackage) {
        if (inspectionProof == null || inspectionProof.isBlank() || inspectionProof.length() > MAX_PROOF_LENGTH || modelPackage == null) {
            throw invalid();
        }
        String[] token = inspectionProof.trim().split("\\.", -1);
        if (token.length != 3 || token[0].isBlank() || token[1].isBlank() || token[2].isBlank()) {
            throw invalid();
        }

        String unsigned = token[0] + "." + token[1];
        byte[] suppliedSignature = decodeCanonical(token[2]);
        byte[] expectedSignature = decodeCanonical(signature(unsigned));
        if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
            throw invalid();
        }

        Claims claims = Claims.parse(decodeText(token[1]));
        String expectedChecksum;
        try {
            expectedChecksum = ModelPackageChecksum.compute(modelPackage);
        } catch (RuntimeException exception) {
            throw invalid();
        }
        if (
            !constantTimeEquals(PROOF_VERSION, token[0]) ||
            !constantTimeEquals(PROOF_VERSION, claims.proofVersion()) ||
            !constantTimeEquals(CANONICALIZATION_VERSION, claims.canonicalizationVersion()) ||
            !constantTimeEquals(tenantId, claims.tenantId()) ||
            !constantTimeEquals(currentActorId(), claims.actorId()) ||
            !constantTimeEquals(expectedChecksum, claims.packageChecksum()) ||
            claims.expiresAtEpochSecond() - claims.issuedAtEpochSecond() != PROOF_TTL.toSeconds()
        ) {
            throw invalid();
        }
        if (!Instant.ofEpochSecond(claims.expiresAtEpochSecond()).isAfter(clock.instant())) {
            throw expired();
        }
    }

    private String currentActorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        if (actor == null || actor.ownerId() == null || actor.ownerId().isBlank()) {
            throw invalid();
        }
        return canonicalIdentity(actor.ownerId(), "actor");
    }

    private String signature(String unsigned) {
        String key = materializationProperties.getRuntimeSpecSigningKey();
        if (key == null || key.trim().length() < 32) {
            throw new IllegalStateException("Platform runtime spec signing key must contain at least 32 characters");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key.trim().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal((SIGNING_DOMAIN + "\n" + unsigned).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Inspection proof could not be signed", exception);
        }
    }

    private static byte[] decodeCanonical(String value) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (!constantTimeEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded), value)) {
                throw invalid();
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static String decodeText(String value) {
        return new String(decodeCanonical(value), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String canonicalIdentity(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " identity is required");
        }
        return value.trim();
    }

    private static InspectionProofException invalid() {
        return new InspectionProofException(
            "DBT_IMPORT_INSPECTION_PROOF_INVALID",
            "dbt archive inspection proof is invalid"
        );
    }

    private static InspectionProofException expired() {
        return new InspectionProofException(
            "DBT_IMPORT_INSPECTION_PROOF_EXPIRED",
            "dbt archive inspection proof has expired"
        );
    }

    public record IssuedProof(String inspectionProof, Instant proofExpiresAt) {
        public IssuedProof {
            if (inspectionProof == null || inspectionProof.isBlank() || proofExpiresAt == null) {
                throw new IllegalArgumentException("Issued inspection proof is invalid");
            }
        }
    }

    private record Claims(
        String proofVersion,
        String canonicalizationVersion,
        String tenantId,
        String actorId,
        String packageChecksum,
        long issuedAtEpochSecond,
        long expiresAtEpochSecond
    ) {
        String serialize() {
            return String.join(
                "\n",
                proofVersion,
                canonicalizationVersion,
                encode(tenantId),
                encode(actorId),
                packageChecksum,
                Long.toString(issuedAtEpochSecond),
                Long.toString(expiresAtEpochSecond)
            );
        }

        static Claims parse(String value) {
            try {
                String[] fields = value.split("\\n", -1);
                if (fields.length != 7) {
                    throw invalid();
                }
                return new Claims(
                    fields[0],
                    fields[1],
                    decodeText(fields[2]),
                    decodeText(fields[3]),
                    fields[4],
                    Long.parseLong(fields[5]),
                    Long.parseLong(fields[6])
                );
            } catch (InspectionProofException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw invalid();
            }
        }
    }

    public static final class InspectionProofException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final String code;

        public InspectionProofException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
