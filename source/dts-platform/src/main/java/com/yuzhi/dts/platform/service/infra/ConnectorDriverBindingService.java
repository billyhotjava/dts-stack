package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraConnector;
import com.yuzhi.dts.platform.service.infra.dto.ConnectorDriverBindingDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraJdbcDriverDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConnectorDriverBindingService {

    static final String POLICY_BUNDLED = "BUNDLED";
    static final String POLICY_ADMIN_PROVIDED = "ADMIN_PROVIDED";
    static final String POLICY_CUSTOM = "CUSTOM";
    static final String POLICY_NOT_REQUIRED = "NOT_REQUIRED";

    static final String STATUS_READY = "READY";
    static final String STATUS_MISSING = "MISSING";
    static final String STATUS_CUSTOM_REQUIRED = "CUSTOM_REQUIRED";
    static final String STATUS_NOT_REQUIRED = "NOT_REQUIRED";

    private static final Set<String> BUNDLED_CONNECTORS = Set.of("postgresql", "mysql", "dm", "hive", "inceptor");

    private final InfraJdbcDriverService jdbcDriverService;
    private final ObjectMapper objectMapper;

    public ConnectorDriverBindingService(InfraJdbcDriverService jdbcDriverService, ObjectMapper objectMapper) {
        this.jdbcDriverService = jdbcDriverService;
        this.objectMapper = objectMapper;
    }

    public ConnectorDriverBindingDto resolve(InfraConnector connector) {
        if (!requiresJdbcDriver(connector)) {
            return notRequired();
        }
        return resolve(connector, jdbcDriverService.listDrivers());
    }

    public List<InfraJdbcDriverDto> installedDrivers() {
        return jdbcDriverService.listDrivers();
    }

    static String defaultPolicy(String connectorKey) {
        String normalized = connectorKey == null ? "" : connectorKey.trim().toLowerCase(Locale.ROOT);
        if ("jdbc".equals(normalized)) {
            return POLICY_CUSTOM;
        }
        return BUNDLED_CONNECTORS.contains(normalized) ? POLICY_BUNDLED : POLICY_ADMIN_PROVIDED;
    }

    public ConnectorDriverBindingDto resolve(InfraConnector connector, List<InfraJdbcDriverDto> drivers) {
        if (!requiresJdbcDriver(connector)) {
            return notRequired();
        }
        String policy = resolvePolicy(connector);
        String driverClass = resolveDriverClass(connector);
        if (POLICY_CUSTOM.equals(policy)) {
            return new ConnectorDriverBindingDto(
                policy,
                STATUS_CUSTOM_REQUIRED,
                driverClass,
                null,
                null,
                null,
                "通用 JDBC 连接器需要选择管理员已安装的驱动"
            );
        }
        InfraJdbcDriverDto matched = findByDriverClass(drivers, driverClass);
        if (matched != null) {
            return new ConnectorDriverBindingDto(
                policy,
                STATUS_READY,
                driverClass,
                matched.fileName(),
                matched.version(),
                matched.jdkSpec(),
                "连接器驱动已就绪"
            );
        }
        String connectorName = displayName(connector);
        String message = POLICY_BUNDLED.equals(policy)
            ? connectorName + " 内置驱动未就绪，请联系管理员检查部署包: " + driverClass
            : connectorName + " 需要管理员补充 JDBC 驱动: " + driverClass;
        return new ConnectorDriverBindingDto(policy, STATUS_MISSING, driverClass, null, null, null, message);
    }

    public void requireUsable(InfraConnector connector, Map<String, Object> requestProps) {
        if (!requiresJdbcDriver(connector)) {
            return;
        }
        List<InfraJdbcDriverDto> drivers = jdbcDriverService.listDrivers();
        ConnectorDriverBindingDto binding = resolve(connector, drivers);
        if (STATUS_READY.equals(binding.status()) || STATUS_NOT_REQUIRED.equals(binding.status())) {
            return;
        }
        if (STATUS_CUSTOM_REQUIRED.equals(binding.status()) && findCustomDriver(drivers, requestProps) != null) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, binding.message());
    }

    private ConnectorDriverBindingDto notRequired() {
        return new ConnectorDriverBindingDto(
            POLICY_NOT_REQUIRED,
            STATUS_NOT_REQUIRED,
            null,
            null,
            null,
            null,
            "该连接器无需 JDBC 驱动"
        );
    }

    private boolean requiresJdbcDriver(InfraConnector connector) {
        return connector != null && "DATABASE".equalsIgnoreCase(normalize(connector.getCategory()));
    }

    private String resolvePolicy(InfraConnector connector) {
        Map<String, Object> compatibility = readMap(connector.getCompatibilityPayload());
        String configured = normalize(compatibility.get("driverPolicy"));
        if (
            POLICY_BUNDLED.equals(configured) ||
            POLICY_ADMIN_PROVIDED.equals(configured) ||
            POLICY_CUSTOM.equals(configured)
        ) {
            return configured;
        }
        return defaultPolicy(connector.getConnectorKey());
    }

    private String resolveDriverClass(InfraConnector connector) {
        Map<String, Object> compatibility = readMap(connector.getCompatibilityPayload());
        String driverClass = normalize(compatibility.get("driverClass"));
        if (StringUtils.hasText(driverClass)) {
            return driverClass;
        }
        Map<String, Object> configSchema = readMap(connector.getConfigSchemaPayload());
        Object defaultsValue = configSchema.get("defaults");
        if (defaultsValue instanceof Map<?, ?> defaults) {
            return normalize(defaults.get("driverClass"));
        }
        return "";
    }

    private InfraJdbcDriverDto findByDriverClass(List<InfraJdbcDriverDto> drivers, String driverClass) {
        if (!StringUtils.hasText(driverClass) || drivers == null) {
            return null;
        }
        return drivers
            .stream()
            .filter(driver -> driver != null && !driver.missing())
            .filter(driver -> driverClass.equalsIgnoreCase(normalize(driver.driverClass())))
            .findFirst()
            .orElse(null);
    }

    private InfraJdbcDriverDto findCustomDriver(List<InfraJdbcDriverDto> drivers, Map<String, Object> requestProps) {
        if (drivers == null || requestProps == null) {
            return null;
        }
        String requestedClass = normalize(requestProps.get("driverClass"));
        String requestedVersion = normalize(requestProps.get("driverVersion"));
        if (!StringUtils.hasText(requestedClass) && !StringUtils.hasText(requestedVersion)) {
            return null;
        }
        return drivers
            .stream()
            .filter(driver -> driver != null && !driver.missing())
            .filter(driver -> !StringUtils.hasText(requestedClass) || requestedClass.equalsIgnoreCase(normalize(driver.driverClass())))
            .filter(driver ->
                !StringUtils.hasText(requestedVersion) ||
                requestedVersion.equalsIgnoreCase(normalize(driver.fileName())) ||
                requestedVersion.equalsIgnoreCase(normalize(driver.version()))
            )
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> readMap(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String displayName(InfraConnector connector) {
        if (connector != null && StringUtils.hasText(connector.getName())) {
            return connector.getName().trim();
        }
        return connector == null ? "连接器" : normalize(connector.getConnectorKey());
    }

    private String normalize(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
