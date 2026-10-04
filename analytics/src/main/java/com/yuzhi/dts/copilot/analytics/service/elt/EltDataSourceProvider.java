package com.yuzhi.dts.copilot.analytics.service.elt;

import com.yuzhi.dts.copilot.analytics.config.EltSyncProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dts.elt.enabled", havingValue = "true")
public class EltDataSourceProvider {

    private final JdbcTemplate businessJdbcTemplate;

    public EltDataSourceProvider(EltSyncProperties properties) {
        EltSyncProperties.BusinessDb db = properties.getBusinessDb();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(db.getUrl());
        dataSource.setUsername(db.getUsername());
        dataSource.setPassword(db.getPassword());
        this.businessJdbcTemplate = new JdbcTemplate(dataSource);
    }

    public JdbcTemplate getBusinessJdbcTemplate() {
        return businessJdbcTemplate;
    }
}
