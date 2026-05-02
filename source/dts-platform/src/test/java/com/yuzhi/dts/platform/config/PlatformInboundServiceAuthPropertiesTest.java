package com.yuzhi.dts.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformInboundServiceAuthPropertiesTest {

    @Test
    void resolveExpectedTokenPrefersPerPairSecretWhenPresent() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
        props.setSharedSecret("shared-fallback");
        Map<String, String> map = new LinkedHashMap<>();
        map.put("dts-ingestion", "ingestion-only-secret");
        map.put("dts-analytics", "analytics-only-secret");
        props.setTrustedServices(map);

        assertThat(props.resolveExpectedToken("dts-ingestion")).isEqualTo("ingestion-only-secret");
        assertThat(props.resolveExpectedToken("dts-analytics")).isEqualTo("analytics-only-secret");
    }

    @Test
    void resolveExpectedTokenIsCaseInsensitiveOnServiceName() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
        Map<String, String> map = new LinkedHashMap<>();
        map.put("dts-ingestion", "ingestion-secret");
        props.setTrustedServices(map);

        assertThat(props.resolveExpectedToken("DTS-Ingestion")).isEqualTo("ingestion-secret");
    }

    @Test
    void resolveExpectedTokenFallsBackToSharedSecretWhenMapValueEmpty() {
        // 模拟运维只设了 DTS_ADMIN_SERVICE_TOKEN(走 fallback 链),Map 包含 key 但 value 为空。
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
        props.setSharedSecret("legacy-shared");
        Map<String, String> map = new LinkedHashMap<>();
        map.put("dts-ingestion", "");
        map.put("dts-airflow", "  "); // whitespace 也视为空
        props.setTrustedServices(map);

        assertThat(props.resolveExpectedToken("dts-ingestion")).isEqualTo("legacy-shared");
        assertThat(props.resolveExpectedToken("dts-airflow")).isEqualTo("legacy-shared");
    }

    @Test
    void resolveExpectedTokenFallsBackToSharedSecretForUnknownService() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
        props.setSharedSecret("shared-fallback");
        Map<String, String> map = new LinkedHashMap<>();
        map.put("dts-ingestion", "ingestion-secret");
        props.setTrustedServices(map);

        assertThat(props.resolveExpectedToken("dts-unknown")).isEqualTo("shared-fallback");
    }

    @Test
    void resolveExpectedTokenReturnsNullWhenServiceNameBlank() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
        props.setSharedSecret("shared");
        assertThat(props.resolveExpectedToken(null)).isNull();
        assertThat(props.resolveExpectedToken("")).isNull();
        assertThat(props.resolveExpectedToken("   ")).isNull();
    }

    @Test
    void isTrustedServiceNameMatchesMapKeysFirst() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
        Map<String, String> map = new LinkedHashMap<>();
        map.put("dts-ingestion", "secret");
        props.setTrustedServices(map);

        assertThat(props.isTrustedServiceName("dts-ingestion")).isTrue();
        assertThat(props.isTrustedServiceName("DTS-INGESTION")).isTrue();
        assertThat(props.isTrustedServiceName("dts-unknown")).isFalse();
    }

    @Test
    void isTrustedServiceNameAlsoMatchesLegacyListWhenMapEmpty() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
        props.setTrustedServiceNames(List.of("dts-ingestion", "dts-airflow"));
        // trustedServices 默认空 Map → 仅靠 trustedServiceNames 判断

        assertThat(props.isTrustedServiceName("dts-ingestion")).isTrue();
        assertThat(props.isTrustedServiceName("dts-airflow")).isTrue();
        assertThat(props.isTrustedServiceName("dts-unknown")).isFalse();
    }

    @Test
    void canonicalServiceNamePreservesOriginalCasing() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
        Map<String, String> map = new LinkedHashMap<>();
        map.put("dts-ingestion", "secret");
        props.setTrustedServices(map);

        assertThat(props.canonicalServiceName("DTS-INGESTION")).isEqualTo("dts-ingestion");
    }

    @Test
    void legacyHeaderOnlyModeDefaultsToFalse() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
        assertThat(props.isLegacyHeaderOnlyMode()).isFalse();
    }
}
