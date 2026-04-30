package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.common.audit.HmacDictionaryVerifier;
import com.yuzhi.dts.common.audit.HmacDictionaryVerifier.Decision;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the HMAC dictionary verifier behaviour across the four decision branches the runtime
 * relies on plus the rotation and tampering scenarios that motivated P3-1.
 */
class HmacDictionaryVerifierTest {

    private static final String ACTIVE_KEY = "112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00";
    private static final String LEGACY_KEY = "ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100";
    private static final byte[] CONTENT = "{\"version\":\"2026-04-30\"}".getBytes();

    @Test
    @DisplayName("正确 sig + active key → VERIFIED")
    void verifiedWithActiveKey() {
        String sig = HmacDictionaryVerifier.sign(CONTENT, ACTIVE_KEY);
        Decision decision = HmacDictionaryVerifier.verify(CONTENT, sig, ACTIVE_KEY, List.of());
        assertThat(decision).isEqualTo(Decision.VERIFIED);
    }

    @Test
    @DisplayName("用 legacy key 签的 sig + 同时配置了 active + legacy → VERIFIED（滚动期）")
    void verifiedWithLegacyKeyDuringRotation() {
        String sigByLegacy = HmacDictionaryVerifier.sign(CONTENT, LEGACY_KEY);
        Decision decision = HmacDictionaryVerifier.verify(CONTENT, sigByLegacy, ACTIVE_KEY, List.of(LEGACY_KEY));
        assertThat(decision).isEqualTo(Decision.VERIFIED);
    }

    @Test
    @DisplayName("篡改内容 + 正确 key → SIGNATURE_INVALID")
    void tamperedContentRejected() {
        String sig = HmacDictionaryVerifier.sign(CONTENT, ACTIVE_KEY);
        byte[] tampered = "{\"version\":\"2099-01-01\"}".getBytes();
        Decision decision = HmacDictionaryVerifier.verify(tampered, sig, ACTIVE_KEY, List.of());
        assertThat(decision).isEqualTo(Decision.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("用错误 key 签的 sig → SIGNATURE_INVALID")
    void wrongKeyRejected() {
        String sigByLegacy = HmacDictionaryVerifier.sign(CONTENT, LEGACY_KEY);
        Decision decision = HmacDictionaryVerifier.verify(CONTENT, sigByLegacy, ACTIVE_KEY, List.of());
        assertThat(decision).isEqualTo(Decision.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("缺 sig → SIGNATURE_MISSING")
    void missingSignature() {
        Decision blank = HmacDictionaryVerifier.verify(CONTENT, "", ACTIVE_KEY, List.of());
        Decision nullSig = HmacDictionaryVerifier.verify(CONTENT, null, ACTIVE_KEY, List.of());

        assertThat(blank).isEqualTo(Decision.SIGNATURE_MISSING);
        assertThat(nullSig).isEqualTo(Decision.SIGNATURE_MISSING);
    }

    @Test
    @DisplayName("无任何 key 配置 → NO_KEY_CONFIGURED")
    void noKeyConfigured() {
        String sig = HmacDictionaryVerifier.sign(CONTENT, ACTIVE_KEY);
        Decision decision = HmacDictionaryVerifier.verify(CONTENT, sig, "", List.of());
        assertThat(decision).isEqualTo(Decision.NO_KEY_CONFIGURED);
    }

    @Test
    @DisplayName("sig 字段非 hex → SIGNATURE_INVALID")
    void invalidHexSignature() {
        Decision decision = HmacDictionaryVerifier.verify(CONTENT, "not-a-hex-string", ACTIVE_KEY, List.of());
        assertThat(decision).isEqualTo(Decision.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("sign 拒绝空 key（防误操作）")
    void signRejectsBlankKey() {
        assertThatThrownBy(() -> HmacDictionaryVerifier.sign(CONTENT, ""))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
