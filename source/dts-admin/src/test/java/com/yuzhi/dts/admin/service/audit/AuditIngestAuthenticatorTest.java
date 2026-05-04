package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.admin.config.AuditIngestProperties;
import com.yuzhi.dts.admin.service.audit.AuditIngestAuthenticator.Decision;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavioural coverage for the audit ingest authenticator across the rotation states a
 * deployment goes through: no key, single key, dual key, mismatch, presence/absence of
 * service-name header. Reject reasons must include the presenter's token fingerprint so
 * runbooks can correlate without exposing the secret.
 */
class AuditIngestAuthenticatorTest {

    private static final String ACTIVE_TOKEN = "active-secret-aaaaaaaaaaaaaaaa";
    private static final String LEGACY_TOKEN = "legacy-secret-bbbbbbbbbbbbbbbb";
    private static final String FOREIGN_TOKEN = "foreign-secret-cccccccccccccccc";

    private static AuditIngestAuthenticator authenticatorWith(boolean require, List<String> tokens) {
        AuditIngestProperties props = new AuditIngestProperties();
        props.setRequireToken(require);
        props.setServiceTokens(tokens);
        return new AuditIngestAuthenticator(props);
    }

    @Test
    @DisplayName("正确 token 通过；服务名小写化")
    void validBearerAccepted() {
        AuditIngestAuthenticator auth = authenticatorWith(true, List.of(ACTIVE_TOKEN));

        Decision decision = auth.authenticate("Bearer " + ACTIVE_TOKEN, "DTS-Platform");

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.serviceName()).isEqualTo("dts-platform");
    }

    @Test
    @DisplayName("双 token 滚动期：legacy token 也通过")
    void legacyTokenAcceptedDuringRotation() {
        AuditIngestAuthenticator auth = authenticatorWith(true, List.of(ACTIVE_TOKEN, LEGACY_TOKEN));

        Decision a = auth.authenticate("Bearer " + ACTIVE_TOKEN, "platform");
        Decision b = auth.authenticate("Bearer " + LEGACY_TOKEN, "platform");

        assertThat(a.accepted()).isTrue();
        assertThat(b.accepted()).isTrue();
    }

    @Test
    @DisplayName("非配置 token → 拒绝；reason 含 presented fingerprint")
    void mismatchReasonIncludesFingerprint() {
        AuditIngestAuthenticator auth = authenticatorWith(true, List.of(ACTIVE_TOKEN));

        Decision decision = auth.authenticate("Bearer " + FOREIGN_TOKEN, "rogue");

        assertThat(decision.accepted()).isFalse();
        String expectedFingerprint = AuditIngestAuthenticator.fingerprint(FOREIGN_TOKEN);
        assertThat(decision.reason()).contains(expectedFingerprint);
        assertThat(decision.reason()).contains("token mismatch");
    }

    @Test
    @DisplayName("缺 Authorization → 拒绝")
    void missingBearerRejected() {
        AuditIngestAuthenticator auth = authenticatorWith(true, List.of(ACTIVE_TOKEN));

        Decision decision = auth.authenticate(null, "platform");

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reason()).contains("missing bearer token");
    }

    @Test
    @DisplayName("require=false 时直接通过（dev only）")
    void requireDisabledAlwaysAccepts() {
        AuditIngestAuthenticator auth = authenticatorWith(false, List.of());

        Decision decision = auth.authenticate(null, "platform");

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.reason()).contains("disabled");
    }

    @Test
    @DisplayName("require=true 但 token 列表空 → fail-closed 拒绝")
    void emptyTokenListFailsClosed() {
        AuditIngestAuthenticator auth = authenticatorWith(true, List.of());

        Decision decision = auth.authenticate("Bearer anything", "platform");

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reason()).contains("no service tokens configured");
    }

    @Test
    @DisplayName("computeFingerprints 跳过空白 token")
    void fingerprintComputationSkipsBlanks() {
        List<String> fps = AuditIngestAuthenticator.computeFingerprints(java.util.Arrays.asList(ACTIVE_TOKEN, "", "  ", null));

        assertThat(fps).hasSize(1);
        assertThat(fps.get(0)).isEqualTo(AuditIngestAuthenticator.fingerprint(ACTIVE_TOKEN));
        // fingerprint format: 16 hex chars (8 bytes of SHA-256)
        assertThat(fps.get(0)).hasSize(16);
        assertThat(fps.get(0)).matches("[0-9a-f]{16}");
    }

    @Test
    @DisplayName("两个不同 token 的 fingerprint 不冲突")
    void fingerprintsAreDistinct() {
        String a = AuditIngestAuthenticator.fingerprint(ACTIVE_TOKEN);
        String b = AuditIngestAuthenticator.fingerprint(LEGACY_TOKEN);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("recognises 'Bearer ' prefix variants and raw token")
    void bearerPrefixTolerant() {
        AuditIngestAuthenticator auth = authenticatorWith(true, List.of(ACTIVE_TOKEN));

        assertThat(auth.authenticate("bearer " + ACTIVE_TOKEN, "p").accepted()).isTrue();
        assertThat(auth.authenticate("BEARER " + ACTIVE_TOKEN, "p").accepted()).isTrue();
        assertThat(auth.authenticate(ACTIVE_TOKEN, "p").accepted()).isTrue();
    }
}
