package com.yuzhi.dts.platform.repository.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagBatchWriter.AssignmentKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.util.StringUtils;

@ExtendWith(MockitoExtension.class)
class CatalogAssetTagBatchWriterTest {

    private static final String VALUE_PLACEHOLDER = "(?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?)";

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CatalogAssetTagBatchWriter writer;

    @BeforeEach
    void setUp() {
        writer = new CatalogAssetTagBatchWriter(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chunksMaximumRequestIntoAtMostFiveHundredRowsPerStatement() {
        List<AssignmentKey> assignments = IntStream
            .range(0, 50_000)
            .mapToObj(index -> new AssignmentKey("METRIC", "metric:core/m" + index, new UUID(0, index + 1L)))
            .toList();
        when(
            jdbcTemplate.query(
                anyString(),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)
            )
        )
            .thenReturn(Set.of());

        Set<AssignmentKey> inserted = writer.insertIgnore(assignments, "alice", Instant.parse("2026-07-25T00:00:00Z"));

        assertThat(inserted).isEmpty();
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(100))
            .query(
                sqlCaptor.capture(),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)
            );
        assertThat(sqlCaptor.getAllValues())
            .allSatisfy(sql ->
                assertThat(StringUtils.countOccurrencesOf(sql, VALUE_PLACEHOLDER))
                    .isEqualTo(CatalogAssetTagBatchWriter.MAX_ROWS_PER_STATEMENT)
            );
    }

    @Test
    @SuppressWarnings("unchecked")
    void deduplicatesAssignmentsBeforeWritingAndCombinesCreatedKeysAcrossChunks() {
        AssignmentKey first = new AssignmentKey("METRIC", "metric:core/first", UUID.randomUUID());
        AssignmentKey last = new AssignmentKey("METRIC", "metric:core/last", UUID.randomUUID());
        List<AssignmentKey> assignments = java.util.stream.Stream.concat(
            IntStream
                .range(0, CatalogAssetTagBatchWriter.MAX_ROWS_PER_STATEMENT)
                .mapToObj(index -> new AssignmentKey("METRIC", "metric:core/m" + index, new UUID(0, index + 1L))),
            java.util.stream.Stream.of(last, first)
        )
            .toList();
        List<AssignmentKey> withDuplicate = new java.util.ArrayList<>(assignments);
        withDuplicate.add(first);
        when(
            jdbcTemplate.query(
                anyString(),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)
            )
        )
            .thenReturn(Set.of(first), Set.of(last));

        Set<AssignmentKey> inserted = writer.insertIgnore(
            withDuplicate,
            "alice",
            Instant.parse("2026-07-25T00:00:00Z")
        );

        assertThat(inserted).containsExactlyInAnyOrder(first, last);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2))
            .query(
                sqlCaptor.capture(),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)
            );
        assertThat(StringUtils.countOccurrencesOf(sqlCaptor.getAllValues().get(0), VALUE_PLACEHOLDER))
            .isEqualTo(CatalogAssetTagBatchWriter.MAX_ROWS_PER_STATEMENT);
        assertThat(StringUtils.countOccurrencesOf(sqlCaptor.getAllValues().get(1), VALUE_PLACEHOLDER)).isEqualTo(2);
    }
}
