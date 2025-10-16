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

        when(accessChecker.resolveAllowedDataLevels()).thenReturn(List.of(DataLevel.DATA_TOP_SECRET, DataLevel.DATA_SECRET));
        when(metadataResolver.findDataLevelColumn(dataset)).thenReturn(Optional.of("data_level"));

        String rewritten = rewriter.guard("SELECT id, amount FROM ods_orders WHERE status = 'DONE';", dataset);

        assertThat(rewritten).contains("SELECT id, amount, `data_level` FROM ods_orders WHERE status = 'DONE'");
        assertThat(rewritten).contains("WHERE");
        assertThat(rewritten).contains("UPPER(TRIM(");
        assertThat(rewritten).contains("TOP_SECRET");
        assertThat(rewritten).doesNotEndWith(";");
    }

    @Test
    void guardShouldInjectGuardColumnWhenProjectionMissing() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        dataset.setHiveTable("ods_orders");

        when(accessChecker.resolveAllowedDataLevels()).thenReturn(List.of(DataLevel.DATA_INTERNAL));
        when(metadataResolver.findDataLevelColumn(dataset)).thenReturn(Optional.of("data_level"));

        String rewritten = rewriter.guard(
            "SELECT category_id, COUNT(*) FROM ods_orders GROUP BY category_id",
            dataset
        );

        assertThat(rewritten).contains("SELECT category_id, COUNT(*), `data_level` FROM ods_orders");
        assertThat(rewritten).contains("GROUP BY category_id, `data_level`");
        assertThat(rewritten).contains("UPPER(TRIM(");
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
    void guardShouldFallbackWhenColumnMissing() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setHiveTable("ods_orders");

        when(accessChecker.resolveAllowedDataLevels()).thenReturn(List.of(DataLevel.DATA_INTERNAL));
        when(metadataResolver.findDataLevelColumn(dataset)).thenReturn(Optional.empty());

        String rewritten = rewriter.guard("SELECT id FROM ods_orders", dataset);

        assertThat(rewritten).isEqualTo("SELECT id FROM ods_orders");
    }
}
