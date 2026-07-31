package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.NodeKind;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipNode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Encodes, validates, and advances the opaque relationship graph cursor. */
final class WarehousePlanRelationshipGraphCursorCodec {

    private static final String CURSOR_VERSION = "1";
    private static final int MAX_CURSOR_LENGTH = 4096;
    private static final int MAX_CURSOR_NODE_ID_LENGTH = 2048;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CURSOR_KEY_DOMAIN =
        "dts:modeling:relationship-graph-cursor:v1";

    private final byte[] cursorSigningKey;

    WarehousePlanRelationshipGraphCursorCodec(String signingSecret) {
        this.cursorSigningKey = deriveCursorSigningKey(signingSecret);
    }

    GraphCursor parse(
        String cursor,
        String tenantId,
        UUID planId,
        String activeDepartmentId,
        NodeKind kind,
        String query
    ) {
        if (cursor == null) return null;
        try {
            if (cursor.isEmpty() || cursor.length() > MAX_CURSOR_LENGTH || !isBase64Url(cursor)) {
                throw new IllegalArgumentException("invalid cursor envelope");
            }
            String raw = decodeBase64Url(cursor);
            String[] parts = raw.split("\\.", -1);
            if (parts.length != 6 || !CURSOR_VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("unsupported cursor version");
            }
            String unsigned = String.join(
                ".",
                parts[0],
                parts[1],
                parts[2],
                parts[3],
                parts[4]
            );
            if (
                !isLowerHex(parts[4]) ||
                !isLowerHex(parts[5]) ||
                !constantTimeEquals(cursorSignature(unsigned), parts[5])
            ) {
                throw new IllegalArgumentException("invalid cursor integrity");
            }
            String expectedScope = cursorScope(tenantId, planId, activeDepartmentId, kind, query);
            if (!constantTimeEquals(expectedScope, parts[4])) {
                throw new IllegalArgumentException("cursor scope mismatch");
            }
            UUID rootAfter = parts[1].isEmpty() ? null : parseCanonicalUuid(parts[1]);
            String nodeAfter = parts[2].isEmpty() ? null : decodeBase64Url(parts[2]);
            String windowFingerprint = parts[3].isEmpty() ? null : parts[3];
            if (
                (rootAfter == null && nodeAfter == null) ||
                (nodeAfter != null && !validCursorNodeId(nodeAfter, kind)) ||
                (nodeAfter == null && windowFingerprint != null) ||
                (nodeAfter != null && (windowFingerprint == null || !isLowerHex(windowFingerprint)))
            ) {
                throw new IllegalArgumentException("invalid cursor position");
            }
            return new GraphCursor(rootAfter, nodeAfter, windowFingerprint);
        } catch (IllegalArgumentException invalid) {
            throw cursorInvalid();
        }
    }

    String encode(
        GraphCursor cursor,
        String tenantId,
        UUID planId,
        String activeDepartmentId,
        NodeKind kind,
        String query
    ) {
        String rootAfter = cursor.rootAfter() == null ? "" : cursor.rootAfter().toString();
        String nodeAfter = cursor.nodeAfter() == null ? "" : encodeBase64Url(cursor.nodeAfter());
        String windowFingerprint = cursor.windowFingerprint() == null
            ? ""
            : cursor.windowFingerprint();
        String unsigned = String.join(
            ".",
            CURSOR_VERSION,
            rootAfter,
            nodeAfter,
            windowFingerprint,
            cursorScope(tenantId, planId, activeDepartmentId, kind, query)
        );
        return encodeBase64Url(unsigned + "." + cursorSignature(unsigned));
    }

    int nodeStart(List<RelationshipNode> matchingNodes, GraphCursor cursor) {
        if (cursor == null || cursor.nodeAfter() == null) return 0;
        for (int index = 0; index < matchingNodes.size(); index++) {
            if (matchingNodes.get(index).id().equals(cursor.nodeAfter())) {
                return index + 1;
            }
        }
        throw cursorInvalid();
    }

    void requireCurrentWindow(
        GraphCursor cursor,
        String currentWindowFingerprint
    ) {
        if (
            cursor == null ||
            cursor.nodeAfter() == null ||
            constantTimeEquals(cursor.windowFingerprint(), currentWindowFingerprint)
        ) {
            return;
        }
        throw cursorStale();
    }

    String windowFingerprint(
        UUID rootAfter,
        Collection<ModelSpecView> rootModels,
        boolean hasMoreRoots,
        List<RelationshipNode> matchingNodes
    ) {
        StringBuilder canonical = new StringBuilder(
            "dts:modeling:relationship-graph-window:v1"
        );
        appendFingerprintPart(canonical, rootAfter == null ? null : rootAfter.toString());
        appendFingerprintPart(canonical, Boolean.toString(hasMoreRoots));
        appendFingerprintPart(canonical, Integer.toString(rootModels.size()));
        for (ModelSpecView root : rootModels) {
            appendFingerprintPart(canonical, root.id().toString());
            appendFingerprintPart(canonical, Integer.toString(root.revision()));
        }
        appendFingerprintPart(canonical, Integer.toString(matchingNodes.size()));
        for (RelationshipNode node : matchingNodes) {
            appendFingerprintPart(canonical, node.kind().name());
            appendFingerprintPart(canonical, node.label().toLowerCase(Locale.ROOT));
            appendFingerprintPart(canonical, node.id());
            appendFingerprintPart(canonical, node.label());
            appendFingerprintPart(canonical, node.status());
            appendFingerprintPart(canonical, node.route());
        }
        return sha256(canonical.toString());
    }

    static String runtimeSpecSigningKey(
        ModelMaterializationProperties materializationProperties
    ) {
        return Objects
            .requireNonNull(
                materializationProperties,
                "materializationProperties is required"
            )
            .getRuntimeSpecSigningKey();
    }

    static WarehousePlanException cursorInvalid() {
        return new WarehousePlanException(
            "RELATIONSHIP_GRAPH_CURSOR_INVALID",
            "Relationship graph cursor is invalid",
            null
        );
    }

    private static void appendFingerprintPart(
        StringBuilder target,
        String value
    ) {
        target.append(cursorScopePart(value)).append(';');
    }

    private static UUID parseCanonicalUuid(String value) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("non-canonical UUID");
        }
        return parsed;
    }

    private static boolean validCursorNodeId(String value, NodeKind kind) {
        if (
            value.isEmpty() ||
            value.length() > MAX_CURSOR_NODE_ID_LENGTH ||
            !value.equals(value.trim())
        ) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) return false;
        }
        if (kind != null) return value.startsWith(kind.name() + ":");
        for (NodeKind candidate : NodeKind.values()) {
            if (value.startsWith(candidate.name() + ":")) return true;
        }
        return false;
    }

    private static String cursorScope(
        String tenantId,
        UUID planId,
        String activeDepartmentId,
        NodeKind kind,
        String query
    ) {
        return sha256(
            cursorScopePart(tenantId) +
            cursorScopePart(planId.toString()) +
            cursorScopePart(activeDepartmentId) +
            cursorScopePart(kind == null ? null : kind.name()) +
            cursorScopePart(query)
        );
    }

    private static String cursorScopePart(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private static String encodeBase64Url(String value) {
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBase64Url(String value) {
        if (!isBase64Url(value)) {
            throw new IllegalArgumentException("invalid base64url");
        }
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) {
            throw new IllegalArgumentException("non-canonical base64url");
        }
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(decoded))
                .toString();
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("invalid UTF-8", invalid);
        }
    }

    private static boolean isBase64Url(String value) {
        if (value.isEmpty()) return false;
        for (int index = 0; index < value.length(); index++) {
            char candidate = value.charAt(index);
            if (
                (candidate >= 'a' && candidate <= 'z') ||
                (candidate >= 'A' && candidate <= 'Z') ||
                (candidate >= '0' && candidate <= '9') ||
                candidate == '-' ||
                candidate == '_'
            ) continue;
            return false;
        }
        return true;
    }

    private static boolean isLowerHex(String value) {
        if (value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char candidate = value.charAt(index);
            if (
                (candidate >= '0' && candidate <= '9') ||
                (candidate >= 'a' && candidate <= 'f')
            ) continue;
            return false;
        }
        return true;
    }

    private static String sha256(String value) {
        try {
            return HexFormat
                .of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
                );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String cursorSignature(String unsigned) {
        if (cursorSigningKey == null) {
            throw cursorSigningUnavailable();
        }
        return HexFormat
            .of()
            .formatHex(
                hmac(
                    cursorSigningKey,
                    unsigned.getBytes(StandardCharsets.UTF_8)
                )
            );
    }

    private static byte[] deriveCursorSigningKey(
        String signingSecret
    ) {
        if (
            signingSecret == null ||
            signingSecret.trim().length() < 32
        ) return null;
        return hmac(
            signingSecret.trim().getBytes(StandardCharsets.UTF_8),
            CURSOR_KEY_DOMAIN.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(value);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(
                "Relationship graph cursor signing is unavailable",
                impossible
            );
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(
            left.getBytes(StandardCharsets.US_ASCII),
            right.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static WarehousePlanException cursorStale() {
        return new WarehousePlanException(
            "RELATIONSHIP_GRAPH_CURSOR_STALE",
            "Relationship graph cursor is stale",
            null
        );
    }

    private static WarehousePlanException cursorSigningUnavailable() {
        return new WarehousePlanException(
            "RELATIONSHIP_GRAPH_CURSOR_SIGNING_UNAVAILABLE",
            "Relationship graph cursor signing is unavailable",
            null
        );
    }

    record GraphCursor(
        UUID rootAfter,
        String nodeAfter,
        String windowFingerprint
    ) {}
}
