package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayRequestOptions;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTarget;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminInfraClient {

    private static final Logger log = LoggerFactory.getLogger(AdminInfraClient.class);
    private static final ParameterizedTypeReference<AdminInceptorConfig> RESPONSE_TYPE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AdminDataLakeConfig> DATA_LAKE_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};

    private final AdminGatewayTransport transport;
    private final PlatformOutboundAdminProperties properties;

    public AdminInfraClient(AdminGatewayTransport transport, PlatformOutboundAdminProperties properties) {
        this.properties = properties;
        this.transport = transport;
    }

    public Optional<AdminInceptorConfig> fetchActiveInceptor() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            AdminInceptorConfig response = transport.exchangeRaw(
                AdminGatewayTarget.API,
                org.springframework.http.HttpMethod.GET,
                "/platform/infra/inceptor",
                null,
                RESPONSE_TYPE,
                AdminGatewayRequestOptions.defaults()
            );
            if (response != null) {
                return Optional.of(response);
            }
        } catch (AdminGatewayException ex) {
            log.debug("Failed to fetch Inceptor data source from admin service: {}", ex.getMessage());
        }
        return Optional.empty();
    }

    public Optional<AdminDataLakeConfig> fetchDefaultDataLake() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            AdminDataLakeConfig response = transport.exchangeRaw(
                AdminGatewayTarget.API,
                org.springframework.http.HttpMethod.GET,
                "/platform/infra/data-lakes/default",
                null,
                DATA_LAKE_RESPONSE_TYPE,
                AdminGatewayRequestOptions.defaults()
            );
            if (response != null) {
                return Optional.of(response);
            }
        } catch (AdminGatewayException ex) {
            log.debug("Failed to fetch default data lake from admin service: {}", ex.getMessage());
        }
        return Optional.empty();
    }

    public boolean updateDefaultDataLakeDestination(AdminDataLakeDestinationUpdateRequest request) {
        if (!properties.isEnabled() || request == null) {
            return false;
        }
        try {
            AdminDataLakeConfig response = transport.exchangeRaw(
                AdminGatewayTarget.API,
                org.springframework.http.HttpMethod.POST,
                "/platform/infra/data-lakes/default/destination",
                request,
                DATA_LAKE_RESPONSE_TYPE,
                AdminGatewayRequestOptions.defaults()
            );
            return response != null;
        } catch (AdminGatewayException ex) {
            log.debug("Failed to update default data lake: {}", ex.getMessage());
        }
        return false;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdminDataLakeConfig {

        private UUID id;
        private String name;
        private String type;
        private String description;
        private String jdbcUrl;
        private String username;
        private String password;
        private Boolean defaulted;
        private Map<String, String> jdbcProperties = Collections.emptyMap();
        private String destinationId;
        private String destinationName;
        private String destinationDefinitionId;
        private Map<String, Object> destinationConfig = Collections.emptyMap();
        private String status;
        private String heartbeatStatus;
        private Integer heartbeatFailureCount;
        private String lastError;
        private Long lastTestElapsedMillis;
        private String engineVersion;
        private String driverVersion;
        private Instant lastVerifiedAt;
        private Instant lastHeartbeatAt;

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public Boolean getDefaulted() {
            return defaulted;
        }

        public Map<String, String> getJdbcProperties() {
            return jdbcProperties == null ? Collections.emptyMap() : jdbcProperties;
        }

        public String getDestinationId() {
            return destinationId;
        }

        public String getDestinationName() {
            return destinationName;
        }

        public String getDestinationDefinitionId() {
            return destinationDefinitionId;
        }

        public Map<String, Object> getDestinationConfig() {
            return destinationConfig == null ? Collections.emptyMap() : destinationConfig;
        }

        public String getStatus() {
            return status;
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

        public Instant getLastHeartbeatAt() {
            return lastHeartbeatAt;
        }
    }

    public record AdminDataLakeDestinationUpdateRequest(
        String destinationId,
        String destinationName,
        String destinationDefinitionId,
        Map<String, Object> destinationConfig
    ) {}
}
