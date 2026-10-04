package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.service.sql.dto.QueryHistoryItemDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SqlIdeHistoryService {

    private final QueryExecutionRepository repository;

    public SqlIdeHistoryService(QueryExecutionRepository repository) {
        this.repository = repository;
    }

    public List<QueryHistoryItemDto> listForUser(
        String userLogin,
        String status,
        String connection,
        String q,
        int limit
    ) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        String safeStatus = isBlank(status) ? null : status.toUpperCase();
        String safeConnection = isBlank(connection) ? null : connection;
        String safeQ = isBlank(q) ? null : q;
        return repository
            .findHistoryByUser(userLogin, safeStatus, safeConnection, safeQ, safeLimit)
            .stream()
            .map(e -> new QueryHistoryItemDto(
                e.getId(),
                e.getSqlText(),
                e.getEngine() != null ? e.getEngine().name() : null,
                e.getConnection(),
                e.getStatus() != null ? e.getStatus().name() : null,
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getRowCount(),
                e.getElapsedMs(),
                e.getCreatedBy()
            ))
            .toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
