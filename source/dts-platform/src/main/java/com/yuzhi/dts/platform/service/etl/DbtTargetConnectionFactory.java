package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
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

    public Connection open(TargetWarehouse target) throws SQLException {
        return DriverManager.getConnection(target.jdbcUrl(), target.username(), target.password());
    }

    private String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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
}
