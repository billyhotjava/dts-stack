package com.yuzhi.dts.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformInboundServiceAuthPropertiesTest {

    @Test
    void resolveExpectedTokenPrefersPerPairSecretWhenPresent() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
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
    void resolveExpectedTokenFailsClosedWhenPairwiseCredentialIsMissing() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
        Map<String, String> map = new LinkedHashMap<>();
        map.put("dts-ingestion", " ");
        props.setTrustedServices(map);

        assertThat(props.resolveExpectedToken("dts-ingestion")).isNull();
        assertThat(props.resolveExpectedToken("dts-unknown")).isNull();
        assertThat(props.isTrustedServiceName("dts-ingestion")).isFalse();
    }

    @Test
    void resolveExpectedTokenReturnsNullWhenServiceNameBlank() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
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
    void startupValidationDropsBlankEntriesAndAllowsEmptyFailClosedConfiguration() {
        PlatformInboundServiceAuthProperties props = new PlatformInboundServiceAuthProperties();
        props.setTrustedServices(Map.of("dts-ingestion", " "));

        props.afterPropertiesSet();

        assertThat(props.getTrustedServices()).isEmpty();
        assertThat(props.isTrustedServiceName("dts-ingestion")).isFalse();
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
    void startupValidationRejectsWeakOrReusedPairwiseCredentials() {
        PlatformInboundServiceAuthProperties weak = new PlatformInboundServiceAuthProperties();
        weak.setTrustedServices(Map.of("dts-ingestion", "short"));
        PlatformInboundServiceAuthProperties duplicate = new PlatformInboundServiceAuthProperties();
        duplicate.setTrustedServices(
            Map.of("dts-ingestion", "a".repeat(32), "dts-analytics", "a".repeat(32))
        );

        assertThatThrownBy(weak::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32");
        assertThatThrownBy(duplicate::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate");
    }
}
