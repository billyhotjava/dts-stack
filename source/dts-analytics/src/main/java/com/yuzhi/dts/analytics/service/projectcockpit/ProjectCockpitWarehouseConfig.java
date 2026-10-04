package com.yuzhi.dts.analytics.service.projectcockpit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class ProjectCockpitWarehouseConfig {

    @Bean(name = "projectCockpitWarehouseJdbcTemplate")
    @ConditionalOnProperty(prefix = "dts.analytics.project-cockpit.warehouse", name = "enabled", havingValue = "true")
    JdbcTemplate projectCockpitWarehouseJdbcTemplate(ProjectCockpitWarehouseProperties properties) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        if (properties.getDriverClassName() != null && !properties.getDriverClassName().isBlank()) {
            dataSource.setDriverClassName(properties.getDriverClassName());
        }
        return new JdbcTemplate(dataSource);
    }
}
