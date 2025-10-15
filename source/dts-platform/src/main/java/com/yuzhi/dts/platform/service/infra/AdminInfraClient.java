package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class AdminInfraClient {

    private static final Logger log = LoggerFactory.getLogger(AdminInfraClient.class);
    private static final ParameterizedTypeReference<AdminInceptorConfig> RESPONSE_TYPE = new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final DtsAdminProperties properties;

    public AdminInfraClient(RestTemplateBuilder builder, DtsAdminProperties properties) {
        this.properties = properties;
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
    }

    public Optional<AdminInceptorConfig> fetchActiveInceptor() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(properties.getServiceToken())) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getServiceToken());
        }
        if (StringUtils.hasText(properties.getServiceName())) {
            headers.set("X-DTS-Service", properties.getServiceName());
        }
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        List<String> candidates = candidateBaseUrls();
        for (String baseUrl : candidates) {
            URI uri = buildUri(baseUrl, properties.getApiPath(), "/platform/infra/inceptor");
            try {
                ResponseEntity<AdminInceptorConfig> response = restTemplate.exchange(uri, HttpMethod.GET, requestEntity, RESPONSE_TYPE);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return Optional.of(response.getBody());
                }
                log.debug("Admin infra endpoint {} returned status {}", uri, response.getStatusCode());
            } catch (Exception ex) {
                log.debug("Failed to fetch Inceptor data source from admin service at {}: {}", uri, ex.getMessage());
            }
        }
        log.warn("Unable to fetch active Inceptor configuration from any configured admin endpoints {}", candidates);
        return Optional.empty();
    }

    private List<String> candidateBaseUrls() {
        String configured = properties.getBaseUrl();
        if (!StringUtils.hasText(configured)) {
            configured = "http://dts-admin:8081";
        }
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
        urls.add(configured);
        deriveLocalFallback(configured).ifPresent(urls::add);
        return List.copyOf(urls);
    }

    private Optional<String> deriveLocalFallback(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (host == null || "dts-admin".equalsIgnoreCase(host)) {
                String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
                int port = uri.getPort();
                StringBuilder fallback = new StringBuilder(scheme).append("://localhost");
                if (port > 0) {
                    fallback.append(":").append(port);
                }
                return Optional.of(fallback.toString());
            }
        } catch (IllegalArgumentException ex) {
            log.debug("Failed to derive fallback admin URL from {}: {}", baseUrl, ex.getMessage());
        }
        return Optional.empty();
    }

    private URI buildUri(String baseUrl, String basePath, String suffix) {
        String normalizedBase = StringUtils.hasText(baseUrl) ? baseUrl : "http://dts-admin:8081";
        normalizedBase = normalizedBase.replaceAll("/+$", "");
        String path = basePath == null ? "" : basePath.trim();
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        String tail = suffix == null ? "" : suffix;
        return URI.create(normalizedBase + path + tail);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdminInceptorConfig {

        private UUID id;
        private String name;
        private String description;
        private String jdbcUrl;
        private String loginPrincipal;
        private String authMethod;
        private String krb5Conf;
        private String keytabBase64;
        private String keytabFileName;
        private String password;
        private Map<String, String> jdbcProperties = Collections.emptyMap();
        private String proxyUser;
        private String servicePrincipal;
        private String host;
        private Integer port;
        private String database;
        @JsonProperty("useHttpTransport")
        private Boolean useHttpTransport;
        private String httpPath;
        @JsonProperty("useSsl")
        private Boolean useSsl;
        @JsonProperty("useCustomJdbc")
        private Boolean useCustomJdbc;
        private String customJdbcUrl;
        private Long lastTestElapsedMillis;
        private String engineVersion;
        private String driverVersion;
        private Instant lastVerifiedAt;
        private Instant lastUpdatedAt;
        private Instant lastHeartbeatAt;
        private String heartbeatStatus;
        private Integer heartbeatFailureCount;
        private String lastError;

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public String getLoginPrincipal() {
            return loginPrincipal;
        }

        public String getAuthMethod() {
            return authMethod;
        }

        public String getKrb5Conf() {
            return krb5Conf;
        }

        public String getKeytabBase64() {
            return keytabBase64;
        }

        public String getKeytabFileName() {
            return keytabFileName;
        }

        public String getPassword() {
            return password;
        }

        public Map<String, String> getJdbcProperties() {
            return jdbcProperties == null ? Collections.emptyMap() : jdbcProperties;
        }

        public String getProxyUser() {
            return proxyUser;
        }

        public String getServicePrincipal() {
            return servicePrincipal;
        }

        public String getHost() {
            return host;
        }

        public Integer getPort() {
            return port;
        }

        public String getDatabase() {
            return database;
        }

        public Boolean getUseHttpTransport() {
            return useHttpTransport;
        }

        public String getHttpPath() {
            return httpPath;
        }

        public Boolean getUseSsl() {
            return useSsl;
        }

        public Boolean getUseCustomJdbc() {
            return useCustomJdbc;
        }

        public String getCustomJdbcUrl() {
            return customJdbcUrl;
        }

        public Long getLastTestElapsedMillis() {
            return lastTestElapsedMillis;
        }

        public String getEngineVersion() {
            return engineVersion;
        }

        public String getDriverVersion() {
            return driverVersion;
        }

        public Instant getLastVerifiedAt() {
            return lastVerifiedAt;
        }

        public Instant getLastUpdatedAt() {
            return lastUpdatedAt;
        }

        public Instant getLastHeartbeatAt() {
            return lastHeartbeatAt;
        }

        public String getHeartbeatStatus() {
            return heartbeatStatus;
        }

        public Integer getHeartbeatFailureCount() {
            return heartbeatFailureCount;
        }

        public String getLastError() {
            return lastError;
        }
    }
}
