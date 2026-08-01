package com.yuzhi.dts.ingestion.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IngestionSensitiveConfigSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNeverInheritOrAcceptRawSecretsInTaskConfiguration() throws Exception {
        JsonNode managed = objectMapper.readTree("""
            {
              "auth": {
                "clientId": "client-1",
                "clientSecret": "managed-secret",
                "nested": {"apiKey": "managed-key", "region": "cn"}
              },
              "items": [{"token": "managed-token", "name": "first"}]
            }
            """);

        JsonNode omitted = IngestionSensitiveConfigSupport.preserveAbsentSecrets(
            managed,
            objectMapper.readTree("""
                {"auth":{"clientId":"client-2","nested":{"region":"us"}},"items":[{"name":"changed"}]}
                """)
        );
        assertThat(omitted.path("auth").has("clientSecret")).isFalse();
        assertThat(omitted.path("auth").path("nested").has("apiKey")).isFalse();
        assertThat(omitted.path("items").get(0).has("token")).isFalse();

        JsonNode replaced = IngestionSensitiveConfigSupport.preserveAbsentSecrets(
            managed,
            objectMapper.readTree("{" + "\"auth\":{\"clientSecret\":\"new-secret\"}}")
        );
        assertThat(replaced.path("auth").has("clientSecret")).isFalse();

        JsonNode clearedNull = IngestionSensitiveConfigSupport.preserveAbsentSecrets(
            managed,
            objectMapper.readTree("{" + "\"auth\":{\"clientSecret\":null}}")
        );
        assertThat(clearedNull.path("auth").has("clientSecret")).isFalse();

        JsonNode clearedBlank = IngestionSensitiveConfigSupport.preserveAbsentSecrets(
            managed,
            objectMapper.readTree("{" + "\"auth\":{\"clientSecret\":\"\"}}")
        );
        assertThat(clearedBlank.path("auth").has("clientSecret")).isFalse();

        JsonNode references = objectMapper.readTree("""
            {"passwordRef":"vault/path","clientSecretReference":"secret-id","secretVersion":"v2"}
            """);
        assertThat(IngestionSensitiveConfigSupport.containsRawSecrets(references)).isFalse();
        assertThat(IngestionSensitiveConfigSupport.sanitize(references)).isEqualTo(references);
    }

    @Test
    void shouldRemoveSecretKeysRecursivelyAndRedactCredentialsEmbeddedInText() throws Exception {
        JsonNode sanitized = IngestionSensitiveConfigSupport.sanitize(objectMapper.readTree("""
            {
              "password":"p",
              "nested":{"client_secret":"s","safe":"ok"},
              "items":[{"authorization":"Bearer abc","url":"jdbc:mysql://user:pass@db/x?token=abc"}]
            }
            """));

        assertThat(sanitized.has("password")).isFalse();
        assertThat(sanitized.path("nested").has("client_secret")).isFalse();
        assertThat(sanitized.path("nested").path("safe").asText()).isEqualTo("ok");
        assertThat(sanitized.path("items").get(0).has("authorization")).isFalse();
        assertThat(sanitized.path("items").get(0).path("url").asText())
            .doesNotContain("user:pass")
            .doesNotContain("token=abc");
    }

    @Test
    void shouldRedactJsonAndLineStartSecretsInExternalErrorText() {
        String sanitized = IngestionSensitiveConfigSupport.sanitizeText("""
            {"token":"json-token","detail":"safe"}
            client_secret=line-secret
            authorization: Bearer header-secret
            """);

        assertThat(sanitized)
            .contains("\"token\":\"[redacted]\"")
            .contains("client_secret=[redacted]")
            .contains("authorization: [redacted]")
            .contains("\"detail\":\"safe\"")
            .doesNotContain("json-token", "line-secret", "header-secret");
    }

    @Test
    void shouldPreserveTokenFlowControlsWhileRemovingOnlyExactCredentialFields() throws Exception {
        JsonNode configuration = objectMapper.readTree("""
            {
              "tokenUrl":"https://id.example/oauth/token",
              "tokenPath":"$.access_token",
              "tokenPlacement":"HEADER",
              "tokenHeaderName":"X-Access-Token",
              "nextTokenPath":"$.paging.next",
              "checkpointToken":"cursor-42",
              "passwordRef":"vault/data/source",
              "clientSecretReference":"managed-secret-id",
              "secretVersion":"v3",
              "secrets":{"headerName":"X-API-Key","value":"raw-container-key"},
              "password":"raw-password",
              "access_token":"raw-access-token",
              "client-secret":"raw-client-secret"
            }
            """);

        JsonNode sanitized = IngestionSensitiveConfigSupport.stripRawSecrets(configuration);

        assertThat(sanitized.path("tokenUrl").asText()).isEqualTo("https://id.example/oauth/token");
        assertThat(sanitized.path("tokenPath").asText()).isEqualTo("$.access_token");
        assertThat(sanitized.path("tokenPlacement").asText()).isEqualTo("HEADER");
        assertThat(sanitized.path("tokenHeaderName").asText()).isEqualTo("X-Access-Token");
        assertThat(sanitized.path("nextTokenPath").asText()).isEqualTo("$.paging.next");
        assertThat(sanitized.path("checkpointToken").asText()).isEqualTo("cursor-42");
        assertThat(sanitized.path("passwordRef").asText()).isEqualTo("vault/data/source");
        assertThat(sanitized.path("clientSecretReference").asText()).isEqualTo("managed-secret-id");
        assertThat(sanitized.path("secretVersion").asText()).isEqualTo("v3");
        assertThat(sanitized.has("secrets")).isFalse();
        assertThat(sanitized.has("password")).isFalse();
        assertThat(sanitized.has("access_token")).isFalse();
        assertThat(sanitized.has("client-secret")).isFalse();
        assertThat(IngestionSensitiveConfigSupport.containsRawSecrets(sanitized)).isFalse();
    }

    @Test
    void shouldNormalizeAliasesAndUnicodeSeparatorsWithoutBroadTokenFalsePositives() throws Exception {
        JsonNode input = objectMapper.readTree("""
            {
              " cre.den_tial ":"raw-credential",
              "Ｓｅｃｒｅｔ－Ｋｅｙ":"raw-secret-key",
              "id token":"raw-id-token",
              "tokenUrl":"https://id.example/oauth/token",
              "passwordRef":"vault/data/source",
              "clientSecretReference":"managed-secret-id"
            }
            """);

        JsonNode sanitized = IngestionSensitiveConfigSupport.sanitize(input);

        assertThat(sanitized.has(" cre.den_tial ")).isFalse();
        assertThat(sanitized.has("Ｓｅｃｒｅｔ－Ｋｅｙ")).isFalse();
        assertThat(sanitized.has("id token")).isFalse();
        assertThat(sanitized.path("tokenUrl").asText()).isEqualTo("https://id.example/oauth/token");
        assertThat(sanitized.path("passwordRef").asText()).isEqualTo("vault/data/source");
        assertThat(sanitized.path("clientSecretReference").asText()).isEqualTo("managed-secret-id");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "database_password",
        "sourcePassword",
        "target.passwd",
        "apiKeyValue",
        "apiTokenValue",
        "clientSecretValue",
        "privateKeyMaterial",
        "credentialValue"
    })
    void shouldFailClosedForCompositeSecretAliases(String key) {
        JsonNode input = objectMapper.createObjectNode().put(key, "raw-secret-value");

        assertThat(IngestionSensitiveConfigSupport.isRawSecretKeyName(key)).isTrue();
        assertThat(IngestionSensitiveConfigSupport.sanitize(input).has(key)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "tokenUrl",
        "tokenPath",
        "tokenPlacement",
        "tokenHeaderName",
        "tokenParam",
        "nextTokenPath",
        "checkpointToken",
        "passwordRef",
        "clientSecretReference",
        "apiKeyRef",
        "tokenRef",
        "secretVersion"
    })
    void shouldPreserveExplicitControlsAndAllowlistedReferences(String key) {
        JsonNode input = objectMapper.createObjectNode().put(key, "safe-reference-or-control");

        assertThat(IngestionSensitiveConfigSupport.isRawSecretKeyName(key)).isFalse();
        assertThat(IngestionSensitiveConfigSupport.sanitize(input).has(key)).isTrue();
    }

    @Test
    void shouldRemoveSensitiveHeaderMapsAndEntryObjectsWhileKeepingSafeHeaders() throws Exception {
        JsonNode input = objectMapper.readTree("""
            {
              "headers": {
                "Authorization":"opaque-value",
                "X-API-Key":"raw-api-key",
                "Cookie":"session=raw-cookie",
                "X-Correlation-Id":"trace-42"
              },
              "requestHeaders": [
                {"headerName":"Proxy-Authorization","value":"plain-proxy-secret"},
                {"name":"X-Credential","headerValue":"plain-credential"},
                {"name":"Accept","value":"application/json"}
              ]
            }
            """);

        JsonNode sanitized = IngestionSensitiveConfigSupport.sanitize(input);

        assertThat(sanitized.path("headers").has("Authorization")).isFalse();
        assertThat(sanitized.path("headers").has("X-API-Key")).isFalse();
        assertThat(sanitized.path("headers").has("Cookie")).isFalse();
        assertThat(sanitized.path("headers").path("X-Correlation-Id").asText()).isEqualTo("trace-42");
        assertThat(sanitized.path("requestHeaders").size()).isEqualTo(1);
        assertThat(sanitized.path("requestHeaders").get(0).path("name").asText()).isEqualTo("Accept");
        assertThat(IngestionSensitiveConfigSupport.containsRawSecrets(input)).isTrue();
        assertThat(IngestionSensitiveConfigSupport.containsRawSecrets(sanitized)).isFalse();
        assertThat(IngestionSensitiveConfigSupport.sanitize(sanitized)).isEqualTo(sanitized);
    }

    @Test
    void shouldDropUnknownOrMalformedSecretReferences() throws Exception {
        JsonNode sanitized = IngestionSensitiveConfigSupport.sanitize(objectMapper.readTree("""
            {
              "passwordRef":"vault/path with spaces",
              "clientSecretReference":{"value":"not-a-reference"},
              "passwordReference":"raw-secret",
              "safe":"ok"
            }
            """));

        assertThat(sanitized.has("passwordRef")).isFalse();
        assertThat(sanitized.has("clientSecretReference")).isFalse();
        assertThat(sanitized.has("passwordReference")).isFalse();
        assertThat(sanitized.path("safe").asText()).isEqualTo("ok");
    }
}
