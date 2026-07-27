package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DbtTargetConnectionFactory {

    private final DbtConfigService configService;
    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraSecretService secretService;

    public DbtTargetConnectionFactory(
        DbtConfigService configService,
        InfraDataSourceRepository dataSourceRepository,
        InfraSecretService secretService
    ) {
        this.configService = configService;
        this.dataSourceRepository = dataSourceRepository;
        this.secretService = secretService;
    }

    public TargetWarehouse resolveTarget() {
        DbtConfigService.DbtConfigView view = configService.loadConfig();
        if (view == null || view.config() == null || view.config().targetDataSourceId() == null) {
            throw new IllegalStateException("未配置目标数仓");
        }
        UUID targetId = view.config().targetDataSourceId();
        InfraDataSource source = dataSourceRepository.findById(targetId).orElse(null);
        if (source == null) {
            throw new IllegalStateException("目标数仓数据源不存在");
        }
        if (!StringUtils.hasText(source.getJdbcUrl())) {
            throw new IllegalStateException("目标数仓 JDBC 地址为空");
        }
        Map<String, Object> secrets = secretService.readSecrets(source);
        String password = secrets != null ? stringVal(secrets.get("password")) : null;
        return new TargetWarehouse(
            targetId,
            stringVal(view.config().database()),
            stringVal(view.config().schema()),
            source.getType(),
            source.getJdbcUrl(),
            source.getUsername(),
            password
        );
    }

    /**
     * Resolves the production dbt runtime target without accepting any request-supplied
     * connection field. Plaintext or unreadable secrets fail closed.
     */
    public RuntimeTarget resolveRuntimeTarget() {
        DbtConfigService.DbtWorkspaceConfig config =
            configService.loadRuntimeConfig();
        UUID targetId = config.targetDataSourceId();
        InfraDataSource source = dataSourceRepository
            .findById(targetId)
            .orElseThrow(() -> new IllegalStateException("目标数仓数据源不存在"));
        if (!StringUtils.hasText(source.getJdbcUrl())) {
            throw new IllegalStateException("目标数仓 JDBC 地址为空");
        }
        if (
            !StringUtils.hasText(source.getSecureKeyVersion()) ||
            "PLAINTEXT".equalsIgnoreCase(source.getSecureKeyVersion())
        ) {
            throw new IllegalStateException("目标数仓凭据不是受保护的加密 secret");
        }
        Map<String, Object> secrets = secretService.readSecrets(source);
        String password = secrets == null
            ? null
            : stringVal(secrets.get("password"));
        if (
            !StringUtils.hasText(source.getUsername()) ||
            !StringUtils.hasText(password)
        ) {
            throw new IllegalStateException("目标数仓凭据缺失或无法解密");
        }
        return new RuntimeTarget(
            targetId,
            stringVal(config.database()),
            stringVal(config.schema()),
            source.getType(),
            source.getJdbcUrl(),
            source.getUsername(),
            password,
            credentialVersionRef(source)
        );
    }

    public Connection open(TargetWarehouse target) throws SQLException {
        return open(
            target.jdbcUrl(),
            target.username(),
            target.password()
        );
    }

    public Connection open(RuntimeTarget target) throws SQLException {
        return open(
            target.jdbcUrl(),
            target.username(),
            target.password()
        );
    }

    private Connection open(
        String jdbcUrl,
        String username,
        String password
    ) throws SQLException {
        try {
            DriverManager.setLoginTimeout(5);
        } catch (RuntimeException ignored) {}
        String driverClass = inferDriverClass(jdbcUrl);
        if (StringUtils.hasText(driverClass)) {
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException ignored) {}
        }
        Properties properties = new Properties();
        if (StringUtils.hasText(username)) {
            properties.setProperty("user", username);
        }
        if (StringUtils.hasText(password)) {
            properties.setProperty("password", password);
        }
        String normalizedUrl = jdbcUrl == null ? "" : jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (normalizedUrl.startsWith("jdbc:postgresql:") && !normalizedUrl.contains("connecttimeout=")) {
            properties.setProperty("connectTimeout", "5");
        }
        if (normalizedUrl.startsWith("jdbc:sqlserver:") && !normalizedUrl.contains("logintimeout=")) {
            properties.setProperty("loginTimeout", "5");
        }
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    private String inferDriverClass(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return null;
        }
        String normalized = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        if (normalized.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (normalized.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        }
        if (normalized.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        if (normalized.startsWith("jdbc:dm:")) {
            return "dm.jdbc.driver.DmDriver";
        }
        return null;
    }

    private String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String credentialVersionRef(InfraDataSource source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(source.getId().toString().getBytes(StandardCharsets.UTF_8));
            digest.update(
                source
                    .getSecureKeyVersion()
                    .getBytes(StandardCharsets.UTF_8)
            );
            if (source.getLastModifiedDate() != null) {
                digest.update(
                    source
                        .getLastModifiedDate()
                        .toString()
                        .getBytes(StandardCharsets.UTF_8)
                );
            }
            if (source.getSecureProps() != null) {
                digest.update(source.getSecureProps());
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record TargetWarehouse(
        UUID dataSourceId,
        String database,
        String schema,
        String type,
        String jdbcUrl,
        String username,
        String password
    ) {}

    public record RuntimeTarget(
        UUID dataSourceId,
        String database,
        String schema,
        String type,
        String jdbcUrl,
        String username,
        String password,
        String credentialVersionRef
    ) {}
}
