package com.yuzhi.dts.admin.service.infra;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.InfraDataSource;
import com.yuzhi.dts.admin.repository.InfraDataSourceRepository;
import com.yuzhi.dts.admin.service.infra.dto.UpsertInfraDataSourcePayload;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class InfraAdminServiceTest {

    @Mock
    private PlatformInfraClient platformInfraClient;

    @Mock
    private IngestionInfraClient ingestionInfraClient;

    @Mock
    private JdbcConnectionTestService jdbcConnectionTestService;

    @Mock
    private JdbcDriverCatalogService jdbcDriverCatalogService;

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private InfraSecretService secretService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private InfraAdminService service;

    @BeforeEach
    void setUp() {
        service = new InfraAdminService(
            platformInfraClient,
            ingestionInfraClient,
            jdbcConnectionTestService,
            jdbcDriverCatalogService,
            dataSourceRepository,
            secretService,
            jdbcTemplate,
            new ObjectMapper(),
            false,
            5000
        );
    }

    @Test
    void createDataSource_rejectsDuplicateJdbcConnectionSignature() {
        InfraDataSource existing = source(
            UUID.randomUUID(),
            "数仓 (biadmin)",
            "POSTGRESQL",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "biadmin"
        );
        lenient().when(dataSourceRepository.findAll()).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createDataSource(
                payload(
                    "默认湖仓副本",
                    "JDBC",
                    " jdbc:postgresql://dts-pg:5432/biadmin ",
                    "BIADMIN"
                ),
                "operator"
            ))
            .hasMessageContaining("数据源连接已存在");
    }

    @Test
    void updateDataSource_rejectsChangingToDuplicateJdbcConnectionSignature() {
        UUID currentId = UUID.randomUUID();
        InfraDataSource current = source(
            currentId,
            "报表库",
            "POSTGRESQL",
            "jdbc:postgresql://report-db:5432/report",
            "report"
        );
        InfraDataSource existing = source(
            UUID.randomUUID(),
            "数仓 (biadmin)",
            "POSTGRESQL",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "biadmin"
        );

        when(dataSourceRepository.findById(currentId)).thenReturn(Optional.of(current));
        lenient().when(dataSourceRepository.findAll()).thenReturn(List.of(current, existing));

        assertThatThrownBy(() -> service.updateDataSource(
                currentId,
                payload("报表库", "JDBC", "jdbc:postgresql://dts-pg:5432/biadmin", "BIADMIN"),
                "operator"
            ))
            .hasMessageContaining("数据源连接已存在");
    }

    private InfraDataSource source(UUID id, String name, String type, String jdbcUrl, String username) {
        InfraDataSource source = new InfraDataSource();
        source.setId(id);
        source.setName(name);
        source.setType(type);
        source.setJdbcUrl(jdbcUrl);
        source.setUsername(username);
        source.setStatus("ACTIVE");
        return source;
    }

    private UpsertInfraDataSourcePayload payload(String name, String type, String jdbcUrl, String username) {
        UpsertInfraDataSourcePayload payload = new UpsertInfraDataSourcePayload();
        payload.setName(name);
        payload.setType(type);
        payload.setJdbcUrl(jdbcUrl);
        payload.setUsername(username);
        payload.setDescription("测试连接");
        payload.setProps(Map.of());
        payload.setSecrets(Map.of("password", "secret"));
        payload.setDefaulted(false);
        return payload;
    }
}
