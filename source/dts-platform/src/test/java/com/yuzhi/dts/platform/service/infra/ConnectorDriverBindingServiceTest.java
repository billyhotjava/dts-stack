package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraConnector;
import com.yuzhi.dts.platform.service.infra.dto.ConnectorDriverBindingDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraJdbcDriverDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ConnectorDriverBindingServiceTest {

    @Mock
    private InfraJdbcDriverService jdbcDriverService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConnectorDriverBindingService service;

    @BeforeEach
    void setUp() {
        service = new ConnectorDriverBindingService(jdbcDriverService, objectMapper);
    }

    @Test
    void resolveBundledDriverReportsReadyMetadata() throws Exception {
        InfraConnector connector = databaseConnector("mysql", "MySQL", "BUNDLED", "com.mysql.cj.jdbc.Driver");
        InfraJdbcDriverDto driver = driver("mysql-connector-j-9.7.0.jar", "com.mysql.cj.jdbc.Driver", "9.7.0", false);
        when(jdbcDriverService.listDrivers()).thenReturn(List.of(driver));

        ConnectorDriverBindingDto result = service.resolve(connector);

        assertThat(result.policy()).isEqualTo("BUNDLED");
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.driverClass()).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(result.fileName()).isEqualTo("mysql-connector-j-9.7.0.jar");
        assertThat(result.version()).isEqualTo("9.7.0");
    }

    @Test
    void resolveBundledDriverReportsMissingWhenJarIsAbsent() throws Exception {
        InfraConnector connector = databaseConnector("postgresql", "PostgreSQL", "BUNDLED", "org.postgresql.Driver");
        when(jdbcDriverService.listDrivers()).thenReturn(List.of());

        ConnectorDriverBindingDto result = service.resolve(connector);

        assertThat(result.policy()).isEqualTo("BUNDLED");
        assertThat(result.status()).isEqualTo("MISSING");
        assertThat(result.message()).contains("内置驱动未就绪").contains("org.postgresql.Driver");
    }

    @Test
    void resolveNonDatabaseConnectorDoesNotRequireJdbcDriver() {
        InfraConnector connector = new InfraConnector();
        connector.setConnectorKey("csv");
        connector.setName("CSV");
        connector.setCategory("FILE");

        ConnectorDriverBindingDto result = service.resolve(connector);

        assertThat(result.policy()).isEqualTo("NOT_REQUIRED");
        assertThat(result.status()).isEqualTo("NOT_REQUIRED");
    }

    @Test
    void requireUsableDriverAcceptsInstalledDriverForGenericJdbc() throws Exception {
        InfraConnector connector = databaseConnector("jdbc", "通用 JDBC", "CUSTOM", "");
        InfraJdbcDriverDto driver = driver("vendor-jdbc-1.2.3.jar", "com.vendor.Driver", "1.2.3", false);
        when(jdbcDriverService.listDrivers()).thenReturn(List.of(driver));

        service.requireUsable(connector, Map.of("driverClass", "com.vendor.Driver", "driverVersion", "vendor-jdbc-1.2.3.jar"));
    }

    @Test
    void requireUsableDriverRejectsMissingConnectorDriver() throws Exception {
        InfraConnector connector = databaseConnector("oracle", "Oracle", "ADMIN_PROVIDED", "oracle.jdbc.OracleDriver");
        when(jdbcDriverService.listDrivers()).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireUsable(connector, Map.of()))
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                    assertThat(ex.getReason()).contains("Oracle").contains("JDBC 驱动");
                }
            );
    }

    private InfraConnector databaseConnector(String key, String name, String policy, String driverClass) throws Exception {
        InfraConnector connector = new InfraConnector();
        connector.setConnectorKey(key);
        connector.setName(name);
        connector.setCategory("DATABASE");
        connector.setCompatibilityPayload(
            objectMapper.writeValueAsString(Map.of("driverPolicy", policy, "driverClass", driverClass))
        );
        connector.setConfigSchemaPayload(
            objectMapper.writeValueAsString(Map.of("defaults", Map.of("driverClass", driverClass)))
        );
        return connector;
    }

    private InfraJdbcDriverDto driver(String fileName, String driverClass, String version, boolean missing) {
        return new InfraJdbcDriverDto(
            UUID.randomUUID(),
            fileName,
            "/opt/dts/drivers/" + fileName,
            driverClass,
            version,
            "17",
            null,
            null,
            missing
        );
    }
}
