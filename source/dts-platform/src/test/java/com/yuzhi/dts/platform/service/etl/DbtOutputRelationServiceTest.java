package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DbtOutputRelationServiceTest {

    private final ModelingSqlModelRepository modelRepository = mock(ModelingSqlModelRepository.class);
    private final DbtTargetConnectionFactory connectionFactory = mock(DbtTargetConnectionFactory.class);

    private DbtOutputRelationService service;
    private DbtTargetConnectionFactory.TargetWarehouse target;
    private ModelingSqlModel model;

    @BeforeEach
    void setUp() {
        service = new DbtOutputRelationService(modelRepository, connectionFactory);
        target = new DbtTargetConnectionFactory.TargetWarehouse(
            UUID.randomUUID(),
            "biadmin",
            "public",
            "postgres",
            "jdbc:postgresql://warehouse/biadmin",
            "biadmin",
            "secret"
        );
        model = new ModelingSqlModel();
        model.setId(UUID.randomUUID());
        model.setName("biz_ads_major_project_overview");
        model.setAlias("major_project_overview");
        model.setSchemaName("public");
        model.setMaterialized("table");
        model.setDagSelector("tag:project-management");
        when(modelRepository.findById(model.getId())).thenReturn(Optional.of(model));
        when(modelRepository.findAll()).thenReturn(List.of(model));
        when(connectionFactory.resolveTarget()).thenReturn(target);
    }

    @Test
    void analyze_shouldDescribeExistingTableAndDownstreamRefs() throws Exception {
        ModelingSqlModel downstream = new ModelingSqlModel();
        downstream.setId(UUID.randomUUID());
        downstream.setSqlText("select * from {{ ref('biz_ads_major_project_overview') }}");
        when(modelRepository.findAll()).thenReturn(List.of(model, downstream));

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);
        when(connectionFactory.open(target)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(null, "public", "major_project_overview", new String[] { "TABLE", "VIEW" })).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_TYPE")).thenReturn("TABLE");

        DbtOutputRelationService.DbtOutputRelationSummary summary = service.analyze(model.getId());

        assertThat(summary.exists()).isTrue();
        assertThat(summary.relationType()).isEqualTo("TABLE");
        assertThat(summary.truncateAllowed()).isTrue();
        assertThat(summary.downstreamRefCount()).isEqualTo(1);
        assertThat(summary.qualifiedName()).isEqualTo("\"public\".\"major_project_overview\"");
    }

    @Test
    void truncate_shouldExecuteTruncateForExistingTable() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);
        Statement statement = mock(Statement.class);
        when(connectionFactory.open(target)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(null, "public", "major_project_overview", new String[] { "TABLE", "VIEW" })).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(connection.createStatement()).thenReturn(statement);

        DbtOutputRelationService.DbtOutputRelationActionResult result = service.truncate(model.getId());

        assertThat(result.executed()).isTrue();
        verify(statement).execute("TRUNCATE TABLE \"public\".\"major_project_overview\"");
    }

    @Test
    void truncate_shouldRejectViewRelation() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);
        model.setMaterialized("view");
        when(connectionFactory.open(target)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(null, "public", "major_project_overview", new String[] { "TABLE", "VIEW" })).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_TYPE")).thenReturn("VIEW");

        assertThatThrownBy(() -> service.truncate(model.getId())).isInstanceOf(IllegalStateException.class).hasMessageContaining("视图");
    }

    @Test
    void prepareRebuild_shouldReturnFullRefreshPlanWithoutDroppingRelation() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);
        when(connectionFactory.open(target)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(null, "public", "major_project_overview", new String[] { "TABLE", "VIEW" })).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_TYPE")).thenReturn("TABLE");

        DbtOutputRelationService.DbtOutputRelationActionResult result = service.prepareRebuild(model.getId());

        assertThat(result.relationExists()).isTrue();
        assertThat(result.executed()).isFalse();
        assertThat(result.selector()).isEqualTo("tag:project-management");
        assertThat(result.message()).contains("dbt --full-refresh");
    }
}
