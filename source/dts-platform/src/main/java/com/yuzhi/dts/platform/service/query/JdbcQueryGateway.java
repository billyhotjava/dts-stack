package com.yuzhi.dts.platform.service.query;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.sql.JdbcSqlExecutor;
import com.yuzhi.dts.platform.service.sql.JdbcSqlExecutor.ExecutionResult;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * {@link QueryGateway} implementation backed by {@link JdbcSqlExecutor}.
 * Marked {@code @Primary} so {@link SqlExecutionService} routes all SQL IDE executions through
 * this gateway instead of the legacy Hive/Noop gateways.
 *
 * <p>Datasource resolution order (matching legacy HiveQueryGateway behaviour):
 * <ol>
 *   <li>If {@code datasourceId} parses as a UUID — look up via {@link InfraDataSourceRepository}.</li>
 *   <li>If not found or non-UUID string — fall back to the first active POSTGRESQL datasource.</li>
 *   <li>If still not found — fall back to the first datasource in the repo.</li>
 * </ol>
 */
@Service
@Primary
public class JdbcQueryGateway implements QueryGateway {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcQueryGateway.class);
    private static final int ROW_LIMIT = 100_000;

    private final JdbcSqlExecutor jdbcSqlExecutor;
    private final InfraDataSourceRepository infraDataSourceRepository;

    public JdbcQueryGateway(
        JdbcSqlExecutor jdbcSqlExecutor,
        InfraDataSourceRepository infraDataSourceRepository
    ) {
        this.jdbcSqlExecutor = jdbcSqlExecutor;
        this.infraDataSourceRepository = infraDataSourceRepository;
    }

    // -----------------------------------------------------------------------
    // QueryGateway implementation
    // -----------------------------------------------------------------------

    @Override
    public Map<String, Object> execute(String effectiveSql) {
        return execute(effectiveSql, (UUID) null, (UUID) null);
    }

    @Override
    public Map<String, Object> execute(String effectiveSql, UUID datasourceId) {
        return execute(effectiveSql, datasourceId, (UUID) null);
    }

    /**
     * Full three-arg implementation: resolves datasource, delegates to {@link JdbcSqlExecutor},
     * and returns a payload map with the same keys as {@link HiveQueryGateway} so that
     * {@code SqlExecutionService.executeQueued} can consume it unchanged.
     *
     * <p>When {@code executionId} is non-null, {@link JdbcSqlExecutor} persists result chunks
     * directly — the caller must NOT double-persist them (it checks for duplicate chunks).
     */
    @Override
    public Map<String, Object> execute(String effectiveSql, UUID datasourceId, UUID executionId) {
        InfraDataSource ds = resolveDatasource(datasourceId);
        long start = System.currentTimeMillis();
        try {
            ExecutionResult result = jdbcSqlExecutor.execute(
                ds,
                effectiveSql,
                executionId,
                ROW_LIMIT,
                /* progressHook */ null,
                /* cancelFlag  */ new AtomicBoolean(false)
            );

            long elapsedMs = System.currentTimeMillis() - start;
            List<String> headers = result.columns().stream()
                .map(JdbcSqlExecutor.ColumnMeta::name)
                .toList();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("headers", headers);
            // JdbcSqlExecutor already persisted chunks when executionId != null.
            // Return empty rows list so SqlExecutionService does not double-write chunk records,
            // but headers must be non-empty so SqlExecutionService creates the ResultSet metadata.
            payload.put("rows", List.of());
            payload.put("rowCount", result.rowCount());
            payload.put("truncated", result.truncated());
            payload.put("connectMillis", 0L);   // JdbcSqlExecutor bundles connect+query in elapsedMs
            payload.put("queryMillis", elapsedMs);
            payload.put("effectiveSql", effectiveSql);
            payload.put("chunkCount", result.chunkCount());
            // Signal to SqlExecutionService that JdbcSqlExecutor already persisted chunks directly —
            // the service must NOT double-write them.  Checked explicitly instead of inferring from
            // finalRows.isEmpty() (which would conflate empty result sets from the legacy Hive path).
            payload.put("chunksPrePersisted", true);
            payload.put("executionContext", Map.of(
                "database", ds.getName() != null ? ds.getName() : ds.getJdbcUrl(),
                "timestamp", Instant.now()
            ));
            return payload;
        } catch (SQLException ex) {
            LOG.error("JdbcQueryGateway execute failed datasource={} sql='{}': {}",
                ds.getId(), effectiveSql, ex.getMessage(), ex);
            throw new IllegalStateException("JDBC 查询失败: " + ex.getMessage(), ex);
        }
    }

    // -----------------------------------------------------------------------
    // Datasource resolution
    // -----------------------------------------------------------------------

    private InfraDataSource resolveDatasource(UUID datasourceId) {
        if (datasourceId != null) {
            Optional<InfraDataSource> found = infraDataSourceRepository.findById(datasourceId);
            if (found.isPresent()) {
                return found.orElseThrow();
            }
            LOG.warn("JdbcQueryGateway: datasource {} not found, falling back to default", datasourceId);
        }

        // Fall back to first active POSTGRESQL datasource
        Optional<InfraDataSource> pg =
            infraDataSourceRepository.findFirstByTypeIgnoreCaseAndStatusIgnoreCase("POSTGRESQL", "ACTIVE");
        if (pg.isPresent()) {
            return pg.orElseThrow();
        }

        // Last resort: any datasource
        List<InfraDataSource> all = infraDataSourceRepository.findAll();
        if (!all.isEmpty()) {
            LOG.warn("JdbcQueryGateway: no active POSTGRESQL datasource found, using first available: {}",
                all.get(0).getName());
            return all.get(0);
        }

        throw new IllegalStateException("没有可用的数据源，请联系系统管理员");
    }
}
