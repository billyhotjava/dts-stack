package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CatalogGovernanceWorkbenchServiceTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void exposesTerminalSemanticDeliveryFailuresThroughTheExistingGovernanceIssueReadModel() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        CatalogGovernanceWorkbenchService service = new CatalogGovernanceWorkbenchService(
            jdbc,
            mock(CatalogClassificationService.class),
            mock(CatalogLifecycleProjectionService.class),
            mock(CatalogLifecycleControlService.class),
            mock(CatalogClassificationPropagationService.class)
        );

        List<CatalogGovernanceWorkbenchService.GovernanceIssueView> issues = service.governanceIssues(500);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.issueType()).isEqualTo("SEMANTIC_DELIVERY");
            assertThat(issue.status()).isEqualTo("SYNC_FAILED");
            assertThat(issue.repairRoute())
                .isEqualTo("/modeling/models/" + MODEL_ID + "?activeStage=logical&tab=design");
        });
        assertThat(jdbc.sql())
            .contains("from modeling_catalog_model_serving_projection")
            .contains("sync_status='SYNC_FAILED' and next_sync_at is null");
        assertThat(jdbc.limit()).isEqualTo(200);
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private String sql;
        private int limit;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.limit = (Integer) args[0];
            ResultSet row = mock(ResultSet.class);
            try {
                when(row.getString("issue_type")).thenReturn("SEMANTIC_DELIVERY");
                when(row.getString("issue_status")).thenReturn("SYNC_FAILED");
                when(row.getString("subject_ref")).thenReturn("semantic-model:" + MODEL_ID);
                when(row.getString("error_message")).thenReturn("ANALYTICS_SEMANTIC_PUBLISH_UNAVAILABLE");
                when(row.getTimestamp("occurred_at"))
                    .thenReturn(Timestamp.from(Instant.parse("2026-08-17T00:00:00Z")));
                when(row.getString("responsible_owner")).thenReturn(null);
                when(row.getString("responsible_dept")).thenReturn(null);
                when(row.getObject("dataset_id", UUID.class)).thenReturn(null);
                when(row.getObject("model_spec_id", UUID.class)).thenReturn(MODEL_ID);
                return List.of(rowMapper.mapRow(row, 0));
            } catch (SQLException failure) {
                throw new IllegalStateException(failure);
            }
        }

        private String sql() {
            return sql;
        }

        private int limit() {
            return limit;
        }
    }
}
