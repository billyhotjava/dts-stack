package com.yuzhi.dts.analytics.service.projectcockpit;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProjectCockpitWarehouseConfigIT {

    @Autowired
    @Qualifier("dataSource")
    private DataSource mainDataSource;

    @Autowired
    @Qualifier("projectCockpitWarehouseJdbcTemplate")
    private JdbcTemplate warehouseJdbcTemplate;

    @Test
    void warehouseShouldNotReplacePrimaryDatasource() {
        assertThat(mainDataSource).isNotNull();
        assertThat(warehouseJdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
    }
}
