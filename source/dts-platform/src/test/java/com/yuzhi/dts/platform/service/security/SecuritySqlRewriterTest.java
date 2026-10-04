package com.yuzhi.dts.platform.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecuritySqlRewriterTest {

    @Mock
    private AccessChecker accessChecker;

    @Mock
    private DatasetSecurityMetadataResolver metadataResolver;

    private SecuritySqlRewriter rewriter;

    @BeforeEach
    void setUp() {
        DatasetSqlBuilder datasetSqlBuilder = new DatasetSqlBuilder(accessChecker, metadataResolver);
        rewriter = new SecuritySqlRewriter(accessChecker, metadataResolver, datasetSqlBuilder);
    }

    @Test
    void guardShouldWrapSqlWithClassificationPredicate() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        dataset.setHiveTable("ods_orders");

        when(accessChecker.resolveAllowedDataLevels()).thenReturn(List.of(DataLevel.DATA_CONFIDENTIAL, DataLevel.DATA_SECRET));
        when(metadataResolver.findDataLevelColumnInfo(dataset))
            .thenReturn(Optional.of(new DatasetSecurityMetadataResolver.ResolvedColumn("data_level", "STRING", false)));
        when(metadataResolver.findDataLevelColumn(dataset)).thenReturn(Optional.of("data_level"));
        when(metadataResolver.findDeptColumn(dataset)).thenReturn(Optional.of("dept_code"));

        String rewritten = rewriter.guard("SELECT id, amount FROM ods_orders WHERE status = 'DONE';", dataset, "D1");

        assertThat(rewritten).contains("SELECT * FROM (");
        assertThat(rewritten).contains("SELECT id, amount, `data_level`, `dept_code` FROM ods_orders WHERE status = 'DONE'");
        assertThat(rewritten).contains("WHERE");
        assertThat(rewritten).contains("UPPER(TRIM(");
        assertThat(rewritten).contains("CONFIDENTIAL");
        assertThat(rewritten).contains("'D1'");
        assertThat(rewritten).doesNotEndWith(";");
    }

    @Test
    void guardShouldInjectGuardColumnWhenProjectionMissing() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        dataset.setHiveTable("ods_orders");

        when(accessChecker.resolveAllowedDataLevels()).thenReturn(List.of(DataLevel.DATA_INTERNAL));
        when(metadataResolver.findDataLevelColumnInfo(dataset))
            .thenReturn(Optional.of(new DatasetSecurityMetadataResolver.ResolvedColumn("data_level", "STRING", false)));
        when(metadataResolver.findDataLevelColumn(dataset)).thenReturn(Optional.of("data_level"));
        when(metadataResolver.findDeptColumn(dataset)).thenReturn(Optional.of("dept_code"));

        String rewritten = rewriter.guard(
            "SELECT category_id, COUNT(*) FROM ods_orders GROUP BY category_id",
            dataset,
            "D1"
        );

        assertThat(rewritten).contains("SELECT category_id, COUNT(*), `data_level`, `dept_code` FROM ods_orders");
        assertThat(rewritten).contains("GROUP BY category_id, `data_level`, `dept_code`");
        assertThat(rewritten).contains("UPPER(TRIM(");
        assertThat(rewritten).contains("'D1'");
    }

    @Test
    void guardShouldFailWhenNoDataLevelPermission() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setHiveTable("ods_orders");

        when(accessChecker.resolveAllowedDataLevels()).thenReturn(List.of());

        assertThatThrownBy(() -> rewriter.guard("SELECT * FROM ods_orders", dataset))
            .isInstanceOf(SecurityGuardException.class)
            .hasMessageContaining("当前账号未配置可访问的数据密级");
    }

    @Test
    void guardShouldTrimTrailingSemicolonsForAdhocSql() {
        String rewritten = rewriter.guard("SELECT 1;;", null);
        assertThat(rewritten).isEqualTo("SELECT 1");
    }

    @Test
    void guardShouldFailWhenDataLevelColumnMissing() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setHiveTable("ods_orders");

        when(accessChecker.resolveAllowedDataLevels()).thenReturn(List.of(DataLevel.DATA_INTERNAL));
        when(metadataResolver.findDataLevelColumn(dataset)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rewriter.guard("SELECT id FROM ods_orders", dataset, "D1"))
            .isInstanceOf(SecurityGuardException.class)
            .hasMessageContaining("数据集缺少数据密级字段");
    }
}
