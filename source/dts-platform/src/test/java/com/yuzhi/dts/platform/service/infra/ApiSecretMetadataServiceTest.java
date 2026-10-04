package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.infra.dto.ApiSecretSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiSecretMetadataServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-26T12:00:00Z");

    private final ApiSecretMetadataService service = new ApiSecretMetadataService(
        Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
    );

    @Test
    @DisplayName("first write of apiKey provider produces v1 entry with masked value")
    void firstWriteProducesV1Entry() {
        Map<String, Object> secrets = Map.of("value", "sk-abcdwxyz");

        ApiSecretMetadata metadata = service.computeMetadata("apiKey", secrets, null);

        assertThat(metadata.providerId()).isEqualTo("apiKey");
        assertThat(metadata.fields()).hasSize(1);
        ApiSecretMetadata.FieldEntry entry = metadata.fields().get(0);
        assertThat(entry.fieldName()).isEqualTo("value");
        assertThat(entry.maskedDisplay()).isEqualTo("sk***wxyz");
        assertThat(entry.secretVersion()).isEqualTo("v1");
        assertThat(entry.rotatedAt()).isEqualTo(FIXED_NOW);
        assertThat(entry.status()).isEqualTo(ApiSecretMetadata.STATUS_ACTIVE);
    }

    @Test
    @DisplayName("update bumps version when a sensitive field is re-supplied")
    void updateBumpsVersion() {
        ApiSecretMetadata previous = new ApiSecretMetadata(
            "apiKey",
            List.of(new ApiSecretMetadata.FieldEntry(
                "value",
                "sk***wxyz",
                "v3",
                Instant.parse("2026-04-01T00:00:00Z"),
                ApiSecretMetadata.STATUS_ACTIVE
            ))
        );

        ApiSecretMetadata next = service.computeMetadata("apiKey", Map.of("value", "newSecret123"), previous);

        assertThat(next.fields()).hasSize(1);
        assertThat(next.fields().get(0).secretVersion()).isEqualTo("v4");
        assertThat(next.fields().get(0).rotatedAt()).isEqualTo(FIXED_NOW);
        assertThat(next.fields().get(0).maskedDisplay()).isEqualTo("ne***t123");
    }

    @Test
    @DisplayName("provider switch resets versions to v1")
    void providerSwitchResetsVersions() {
        ApiSecretMetadata previous = new ApiSecretMetadata(
            "apiKey",
            List.of(new ApiSecretMetadata.FieldEntry(
                "value",
                "sk***wxyz",
                "v5",
                Instant.parse("2026-04-01T00:00:00Z"),
                ApiSecretMetadata.STATUS_ACTIVE
            ))
        );

        ApiSecretMetadata next = service.computeMetadata("bearerToken", Map.of("token", "abcdwxyz1234"), previous);

        assertThat(next.providerId()).isEqualTo("bearerToken");
        assertThat(next.fields()).hasSize(1);
        assertThat(next.fields().get(0).fieldName()).isEqualTo("token");
        assertThat(next.fields().get(0).secretVersion()).isEqualTo("v1");
    }

    @Test
    @DisplayName("omitted fields preserve previous metadata unchanged")
    void omittedFieldsPreservePrevious() {
        ApiSecretMetadata previous = new ApiSecretMetadata(
            "basic",
            List.of(new ApiSecretMetadata.FieldEntry(
                "password",
                "ab***wxyz",
                "v2",
                Instant.parse("2026-04-10T00:00:00Z"),
                ApiSecretMetadata.STATUS_ACTIVE
            ))
        );

        ApiSecretMetadata next = service.computeMetadata("basic", Map.of(), previous);

        assertThat(next.fields()).hasSize(1);
        assertThat(next.fields().get(0).secretVersion()).isEqualTo("v2");
        assertThat(next.fields().get(0).rotatedAt()).isEqualTo(Instant.parse("2026-04-10T00:00:00Z"));
    }

    @Test
    @DisplayName("non-sensitive keys in the secrets map are ignored")
    void nonSensitiveKeysIgnored() {
        Map<String, Object> secrets = Map.of("location", "header", "name", "X-API-Key");

        ApiSecretMetadata metadata = service.computeMetadata("apiKey", secrets, null);

        assertThat(metadata.fields()).isEmpty();
    }

    @Test
    @DisplayName("jwtLogin tracks arbitrary login secret placeholders")
    void jwtLoginTracksArbitraryLoginSecretPlaceholders() {
        Map<String, Object> secrets = Map.of("password", "passw0rd-1234", "tenantSecret", "tenant-secret-xyz");

        ApiSecretMetadata metadata = service.computeMetadata("jwtLogin", secrets, null);

        assertThat(metadata.providerId()).isEqualTo("jwtLogin");
        assertThat(metadata.fields())
            .extracting(ApiSecretMetadata.FieldEntry::fieldName)
            .contains("password", "tenantSecret");
        assertThat(metadata.fields())
            .filteredOn(entry -> "tenantSecret".equals(entry.fieldName()))
            .singleElement()
            .satisfies(entry -> {
                assertThat(entry.secretVersion()).isEqualTo("v1");
                assertThat(entry.maskedDisplay()).isEqualTo("te***-xyz");
            });
    }

    @Test
    @DisplayName("provider 'none' yields empty metadata regardless of supplied secrets")
    void providerNoneYieldsEmptyMetadata() {
        ApiSecretMetadata metadata = service.computeMetadata("none", Map.of("value", "anything"), null);

        assertThat(metadata.fields()).isEmpty();
    }

    @Test
    @DisplayName("short secret values mask without revealing length-precise prefix")
    void shortSecretsMasked() {
        assertThat(ApiSecretMetadataService.maskedDisplay("ab")).isEqualTo("***");
        assertThat(ApiSecretMetadataService.maskedDisplay("abc")).isEqualTo("***");
        assertThat(ApiSecretMetadataService.maskedDisplay("abcd")).isEqualTo("a***");
        assertThat(ApiSecretMetadataService.maskedDisplay("abcdefg")).isEqualTo("a***");
        assertThat(ApiSecretMetadataService.maskedDisplay("abcdefgh")).isEqualTo("ab***efgh");
    }

    @Test
    @DisplayName("metadata round-trips through props serialization")
    void metadataRoundTripsThroughProps() {
        ApiSecretMetadata original = service.computeMetadata("bearerToken", Map.of("token", "abcdwxyz1234"), null);
        Map<String, Object> props = new LinkedHashMap<>();
        service.writeIntoProps(props, original);

        assertThat(props).containsKey(ApiSecretMetadataService.PROPS_METADATA_KEY);
        ApiSecretMetadata roundTripped = service.readFromProps(props);

        assertThat(roundTripped.providerId()).isEqualTo("bearerToken");
        assertThat(roundTripped.fields()).hasSize(1);
        ApiSecretMetadata.FieldEntry entry = roundTripped.fields().get(0);
        assertThat(entry.fieldName()).isEqualTo("token");
        assertThat(entry.secretVersion()).isEqualTo("v1");
        assertThat(entry.rotatedAt()).isEqualTo(FIXED_NOW);
        assertThat(entry.maskedDisplay()).isEqualTo("ab***1234");
        assertThat(entry.status()).isEqualTo(ApiSecretMetadata.STATUS_ACTIVE);
    }

    @Test
    @DisplayName("writeIntoProps removes sidecar when metadata is empty")
    void writeRemovesSidecarWhenEmpty() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(ApiSecretMetadataService.PROPS_METADATA_KEY, Map.of("providerId", "apiKey"));

        service.writeIntoProps(props, ApiSecretMetadata.empty());

        assertThat(props).doesNotContainKey(ApiSecretMetadataService.PROPS_METADATA_KEY);
    }

    @Test
    @DisplayName("toSummaries renders provider/field/masked metadata for the detail endpoint")
    void toSummariesRendersDetailRow() {
        ApiSecretMetadata metadata = service.computeMetadata("oauth2ClientCredentials", Map.of("clientSecret", "abcdwxyz1234"), null);

        List<ApiSecretSummary> summaries = service.toSummaries(metadata);

        assertThat(summaries).hasSize(1);
        ApiSecretSummary summary = summaries.get(0);
        assertThat(summary.providerId()).isEqualTo("oauth2ClientCredentials");
        assertThat(summary.fieldName()).isEqualTo("clientSecret");
        assertThat(summary.maskedDisplay()).isEqualTo("ab***1234");
        assertThat(summary.secretVersion()).isEqualTo("v1");
        assertThat(summary.rotatedAt()).isEqualTo(FIXED_NOW);
        assertThat(summary.status()).isEqualTo(ApiSecretMetadata.STATUS_ACTIVE);
    }
}
