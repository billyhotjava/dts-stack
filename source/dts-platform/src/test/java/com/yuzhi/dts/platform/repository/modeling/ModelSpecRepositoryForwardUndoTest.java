package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ModelSpecRepositoryForwardUndoTest {

    @Test
    void appendsARevisionWithoutChangingCanonicalContentOrLifecycleState() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        ModelSpecRepository repository = new ModelSpecRepository(jdbcTemplate, new ObjectMapper());
        ModelSpecView replacement = mock(ModelSpecView.class);
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000083");
        String checksum = "a".repeat(64);
        Instant now = Instant.parse("2026-08-02T00:00:00Z");
        when(replacement.id()).thenReturn(modelId);
        when(replacement.status()).thenReturn(ModelStatus.DRAFT);
        when(replacement.revision()).thenReturn(12);
        when(replacement.checksum()).thenReturn(checksum);
        when(replacement.updatedAt()).thenReturn(now);

        int changed = repository.appendUnchangedV2RevisionForForwardUndo(
            "tenant-a", "alice", 11, checksum, replacement, "{\"revision\":12}"
        );

        assertThat(changed).isEqualTo(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Integer.class), arguments.capture());
        assertThat(sql.getValue())
            .contains("set revision = ?, version = version + 1, last_modified_date = ?")
            .contains("revision = ? and current_checksum = ? and status = 'DRAFT'")
            .contains("insert into modeling_model_spec_revision")
            .doesNotContain("set current_checksum")
            .doesNotContain("set status");
        assertThat(sql.getValue().chars().filter(character -> character == '?').count())
            .isEqualTo((long) arguments.getValue().length);
    }
}
