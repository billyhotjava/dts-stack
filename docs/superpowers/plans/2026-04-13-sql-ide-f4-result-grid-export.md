# SQL IDE F4: 专业结果表格 + 流式导出 + 全量审计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前 5000 行硬限制提升到 100k，新增 chunk 分块存储与按页/范围拉取端点；前端用 `@tanstack/react-table v8 + react-virtual` 重写结果表格（虚拟滚动 + 列宽拖拽 + 排序/过滤/冻结/隐藏 + 单元格选择复制）；执行轮询改为适应性退避；新增 CSV/Excel/JSON 流式导出端点（带频控）；全量审计 13 类动作（重写前/后 SQL 双记录）。

**Architecture:** 后端新增 `query_execution_chunk` 表分块存 JSONB（每块 2000 行），`SqlExecutionService` 改 JDBC 流式写入；新增 `/api/sql/v2/executions/{id}/{page,stream,meta,export}` 端点；导出走 `StreamingResponseBody` 不在 JVM 堆累积；审计走现有 `AuditService`，端到端 13 个埋点。前端 `useTabStore` 已就位，新增 `useSqlExecution` hook 管理执行轮询/取消，`ResultGrid` 用 `@tanstack/react-table` + `@tanstack/react-virtual` 渲染表格，列状态持久化到 Tab store。`onExecute` 被 `SqlEditor` Ctrl+Enter 触发后接 `useSqlExecution.submit()`。

**Tech Stack:** Spring Boot 3.4.5 + Java 21 + Liquibase + Apache POI SXSSFWorkbook（流式 xlsx）+ Jackson Streaming API；React 18 + TypeScript + @tanstack/react-table@^8 + @tanstack/react-virtual@^3 + Zustand + Ant Design 5。

---

## Spec Reference

- Sprint README: `worklog/v2.2.3/sprint-11-202604/README.md`
- Feature README: `worklog/v2.2.3/sprint-11-202604/features/F4-专业结果表格与导出/README.md`
- Tasks: T16–T20

## Dependencies

- **F1+F2+F3 全部完成**：SqlEditor / Tab store / Schema panel 都就位
- **后端**：依赖现有 `SqlExecutionService.submit/getStatus/getResultPage/cancel` 4 个核心方法（`/api/sql/*` 老接口），F4 在它基础上加新 `/v2` 端点 + 改造内部存储

## File Structure

**Backend (新增):**

| 路径 | 职责 |
|---|---|
| `domain/explore/QueryExecutionChunk.java` | JPA 实体 |
| `repository/explore/QueryExecutionChunkRepository.java` | findByExecutionIdOrderByChunkIndexAsc + bulk delete |
| `service/sql/SqlResultStreamServiceImpl.java` | 实现 SqlResultStreamService 接口（T01 占位现填）：分页拉取、流式范围拉取、CSV/JSON/Excel 导出 |
| `service/sql/SqlExecutionExportRateLimiter.java` | 用户级导出限流 (5次/10分钟)，Caffeine 计数 |
| `service/sql/dto/ResultPageDto.java` | record(rows, columns, page, pageSize, total, truncated) |
| `service/sql/dto/ResultMetaDto.java` | record(executionId, status, columns, totalRows, truncated, elapsedMs, bytesProcessed) |
| `service/sql/dto/ColumnMetaDto.java` | record(name, dataType, nullable) |
| `service/scheduling/QueryExecutionChunkCleaner.java` | @Scheduled 30 天后清理 chunk |
| `service/audit/SqlIdeAuditActions.java` | 13 类动作常量 |
| `web/rest/sql/SqlIdeExecutionController.java` | `/api/sql/v2/executions/{id}/*`：page、stream、meta、export、cancel-v2 |
| `web/rest/sql/SqlIdeAuditController.java` | `POST /api/sql/v2/audit/copy` 前端复制上报 |
| `src/main/resources/config/liquibase/changelog/20260413_03_query_execution_chunk.xml` | 建表 |

**Backend (修改):**

| 路径 | 改动 |
|---|---|
| `service/sql/SqlExecutionService.java` | 改造 `executeQueued` 改用 chunk 流式写入；MAX_ROWS 上限提升到 100_000；新增 `getMetaForExecution(id)` |
| `service/query/HiveQueryGateway.java` | `MAX_ROWS = 100_000`，保留 setFetchSize(2000) |
| `service/sql/SqlResultStreamService.java` | T01 留下的空接口，填充签名 |
| `web/rest/sql/SqlIdeResource.java` | 不动（保持 v2 现有功能） |
| `pom.xml` | 加 `org.apache.poi:poi-ooxml`（如果未引入）|
| `service/sql/SqlExecutionService.java` | 13 类审计埋点（部分动作复用 AuditService 已有，新增缺失的）|

**Frontend (新增):**

| 路径 | 职责 |
|---|---|
| `api/sqlIdeExecution.ts` | 5 函数：getMeta, getPage, getStream（导出用，返回 blob URL）, exportFile（返回 blob）, postCancel, postCopyAudit |
| `hooks/useSqlExecution.ts` | submit/cancel/poll，适应性退避（500/1500/3000/5000 ms） |
| `result/ResultGrid.tsx` | tanstack/react-table + react-virtual + 列管理 |
| `result/ResultGridColumnMenu.tsx` | 列右键菜单（hide/pin/unpin） |
| `result/ResultGridFilterPopover.tsx` | 列过滤弹层 |
| `result/ExportMenu.tsx` | 导出下拉（CSV/Excel/JSON）+ 频控 toast |
| `result/__tests__/columnState.test.ts` | 列宽/排序/过滤 reducer TDD |
| `result/columnState.ts` | 纯 reducer 函数 |
| `result/cellCopy.ts` | 单元格 TSV 序列化 |
| `result/__tests__/cellCopy.test.ts` | TDD |

**Frontend (修改):**

| 路径 | 改动 |
|---|---|
| `tabs/types.ts` | TabState 加 `gridState` 字段（columnWidths, columnFilters, hiddenColumns, pinnedColumns, sortBy）|
| `tabs/useTabStore.ts` | 新增 `updateGridState(tabId, partial)` |
| `SqlIde.tsx` | onExecute → useSqlExecution.submit；BottomPanel 替换为 ResultGrid + 状态提示 + ExportMenu |
| `package.json` | 新增 4 个依赖 |

---

## Task 1 — T16: 后端结果集上限 100k + chunk 分页

**Files:**
- Create: `src/main/resources/config/liquibase/changelog/20260413_03_query_execution_chunk.xml`
- Create: `domain/explore/QueryExecutionChunk.java`
- Create: `repository/explore/QueryExecutionChunkRepository.java`
- Create: `service/sql/dto/ResultPageDto.java`, `ResultMetaDto.java`, `ColumnMetaDto.java`
- Create: `web/rest/sql/SqlIdeExecutionController.java` (only meta + page endpoints in this Task; stream/export in T19)
- Create: `service/sql/SqlResultStreamServiceImpl.java` (only listChunks, getMeta, getPage in this Task)
- Modify: `service/sql/SqlResultStreamService.java` (T01 placeholder → real interface)
- Modify: `service/sql/SqlExecutionService.java` (chunk-streaming writeback in `executeQueued`; MAX_ROWS bump)
- Modify: `service/query/HiveQueryGateway.java` (MAX_ROWS → 100_000)
- Modify: `master.xml` (include changelog)
- Create: `src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeExecutionControllerIT.java`
- Create: `service/scheduling/QueryExecutionChunkCleaner.java`

### - [ ] Step 1.1: Liquibase changelog

`source/dts-platform/src/main/resources/config/liquibase/changelog/20260413_03_query_execution_chunk.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.22.xsd">
    <changeSet id="20260413_03_query_execution_chunk" author="platform">
        <createTable tableName="query_execution_chunk">
            <column name="id" type="${uuidType}">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="execution_id" type="${uuidType}">
                <constraints nullable="false"/>
            </column>
            <column name="chunk_index" type="integer">
                <constraints nullable="false"/>
            </column>
            <column name="rows_json" type="text">
                <constraints nullable="false"/>
            </column>
            <column name="row_start" type="bigint">
                <constraints nullable="false"/>
            </column>
            <column name="row_end" type="bigint">
                <constraints nullable="false"/>
            </column>
            <column name="created_date" type="timestamp"/>
        </createTable>
        <createIndex tableName="query_execution_chunk" indexName="idx_qec_exec_idx" unique="true">
            <column name="execution_id"/>
            <column name="chunk_index"/>
        </createIndex>
        <createIndex tableName="query_execution_chunk" indexName="idx_qec_created">
            <column name="created_date"/>
        </createIndex>
        <rollback>
            <dropIndex tableName="query_execution_chunk" indexName="idx_qec_created"/>
            <dropIndex tableName="query_execution_chunk" indexName="idx_qec_exec_idx"/>
            <dropTable tableName="query_execution_chunk"/>
        </rollback>
    </changeSet>
</databaseChangeLog>
```

In `master.xml`, add include after `20260413_02_saved_query_folder.xml`:
```xml
<include file="config/liquibase/changelog/20260413_03_query_execution_chunk.xml" relativeToChangelogFile="false"/>
```

### - [ ] Step 1.2: JPA entity

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/explore/QueryExecutionChunk.java`:

```java
package com.yuzhi.dts.platform.domain.explore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_execution_chunk")
public class QueryExecutionChunk implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "execution_id", columnDefinition = "uuid", nullable = false)
    private UUID executionId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "rows_json", columnDefinition = "text", nullable = false)
    private String rowsJson;

    @Column(name = "row_start", nullable = false)
    private long rowStart;

    @Column(name = "row_end", nullable = false)
    private long rowEnd;

    @Column(name = "created_date")
    private Instant createdDate = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getRowsJson() { return rowsJson; }
    public void setRowsJson(String rowsJson) { this.rowsJson = rowsJson; }
    public long getRowStart() { return rowStart; }
    public void setRowStart(long rowStart) { this.rowStart = rowStart; }
    public long getRowEnd() { return rowEnd; }
    public void setRowEnd(long rowEnd) { this.rowEnd = rowEnd; }
    public Instant getCreatedDate() { return createdDate; }
    public void setCreatedDate(Instant createdDate) { this.createdDate = createdDate; }
}
```

### - [ ] Step 1.3: Repository

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/explore/QueryExecutionChunkRepository.java`:

```java
package com.yuzhi.dts.platform.repository.explore;

import com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryExecutionChunkRepository extends JpaRepository<QueryExecutionChunk, UUID> {

    List<QueryExecutionChunk> findByExecutionIdOrderByChunkIndexAsc(UUID executionId);

    @Modifying
    @Query("delete from QueryExecutionChunk c where c.executionId = :executionId")
    void deleteAllByExecutionId(UUID executionId);

    @Modifying
    @Query("delete from QueryExecutionChunk c where c.createdDate < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
```

### - [ ] Step 1.4: DTOs

`service/sql/dto/ColumnMetaDto.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;

public record ColumnMetaDto(String name, String dataType, boolean nullable) {}
```

`service/sql/dto/ResultMetaDto.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;
import java.util.UUID;

public record ResultMetaDto(
    UUID executionId,
    String status,
    List<ColumnMetaDto> columns,
    long totalRows,
    boolean truncated,
    Long elapsedMs,
    Long bytesProcessed
) {}
```

`service/sql/dto/ResultPageDto.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;
import java.util.Map;

public record ResultPageDto(
    List<Map<String, Object>> rows,
    List<ColumnMetaDto> columns,
    int page,
    int pageSize,
    long total,
    boolean truncated
) {}
```

### - [ ] Step 1.5: Fill SqlResultStreamService interface (T01 placeholder → real)

Replace `service/sql/SqlResultStreamService.java`:

```java
package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.ResultMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultPageDto;
import java.util.UUID;

public interface SqlResultStreamService {

    /** Returns metadata only (no rows). For meta endpoint. */
    ResultMetaDto getMeta(UUID executionId);

    /** Returns one page (1-indexed). pageSize clamped to [1, 500]. */
    ResultPageDto getPage(UUID executionId, int page, int pageSize);

    /** Streams rows in [from, to) (0-indexed inclusive-exclusive). For export use. */
    java.util.stream.Stream<java.util.Map<String, Object>> streamRange(UUID executionId, long from, long to);
}
```

### - [ ] Step 1.6: Implement SqlResultStreamServiceImpl (only meta + page in this Task)

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlResultStreamServiceImpl.java`:

```java
package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionChunkRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.service.sql.dto.ColumnMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultPageDto;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SqlResultStreamServiceImpl implements SqlResultStreamService {

    private static final TypeReference<List<Map<String, Object>>> ROW_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ColumnMetaDto>> COLUMN_LIST_TYPE = new TypeReference<>() {};

    private final QueryExecutionRepository executionRepository;
    private final QueryExecutionChunkRepository chunkRepository;
    private final ResultSetRepository resultSetRepository;
    private final ObjectMapper objectMapper;

    public SqlResultStreamServiceImpl(
        QueryExecutionRepository executionRepository,
        QueryExecutionChunkRepository chunkRepository,
        ResultSetRepository resultSetRepository,
        ObjectMapper objectMapper
    ) {
        this.executionRepository = executionRepository;
        this.chunkRepository = chunkRepository;
        this.resultSetRepository = resultSetRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResultMetaDto getMeta(UUID executionId) {
        QueryExecution exec = executionRepository
            .findById(executionId)
            .orElseThrow(() -> new EntityNotFoundException("execution not found"));
        ResultSet rs = resultSetRepository
            .findByExecutionId(executionId)
            .orElse(null);
        List<ColumnMetaDto> cols = parseColumns(rs);
        long total = rs == null || rs.getRowCount() == null ? 0 : rs.getRowCount();
        boolean truncated = total >= 100_000;
        return new ResultMetaDto(
            executionId,
            exec.getStatus() == null ? null : exec.getStatus().name(),
            cols,
            total,
            truncated,
            exec.getElapsedMs(),
            exec.getBytesProcessed()
        );
    }

    @Override
    public ResultPageDto getPage(UUID executionId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(500, Math.max(1, pageSize));
        long fromRow = (long) (safePage - 1) * safePageSize;
        long toRow = fromRow + safePageSize;

        ResultMetaDto meta = getMeta(executionId);
        List<Map<String, Object>> rows = streamRange(executionId, fromRow, toRow).toList();
        return new ResultPageDto(rows, meta.columns(), safePage, safePageSize, meta.totalRows(), meta.truncated());
    }

    @Override
    public Stream<Map<String, Object>> streamRange(UUID executionId, long from, long to) {
        if (to <= from) return Stream.empty();
        List<QueryExecutionChunk> chunks = chunkRepository.findByExecutionIdOrderByChunkIndexAsc(executionId);
        if (chunks.isEmpty()) {
            // Fallback to legacy preview JSON if no chunks (pre-F4 executions)
            return legacyPreviewStream(executionId, from, to);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (QueryExecutionChunk chunk : chunks) {
            if (chunk.getRowEnd() <= from) continue;
            if (chunk.getRowStart() >= to) break;
            try {
                List<Map<String, Object>> chunkRows = objectMapper.readValue(chunk.getRowsJson(), ROW_LIST_TYPE);
                long chunkBase = chunk.getRowStart();
                for (int i = 0; i < chunkRows.size(); i++) {
                    long absolute = chunkBase + i;
                    if (absolute >= from && absolute < to) out.add(chunkRows.get(i));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out.stream();
    }

    private Stream<Map<String, Object>> legacyPreviewStream(UUID executionId, long from, long to) {
        ResultSet rs = resultSetRepository.findByExecutionId(executionId).orElse(null);
        if (rs == null || rs.getPreviewColumns() == null) return Stream.empty();
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(rs.getPreviewColumns(), ROW_LIST_TYPE);
            int rangeStart = (int) Math.max(0, Math.min(rows.size(), from));
            int rangeEnd = (int) Math.max(0, Math.min(rows.size(), to));
            return rows.subList(rangeStart, rangeEnd).stream();
        } catch (IOException e) {
            return Stream.empty();
        }
    }

    private List<ColumnMetaDto> parseColumns(ResultSet rs) {
        if (rs == null || rs.getColumns() == null) return Collections.emptyList();
        try {
            // Existing project stores `columns` as JSON [{name,dataType}, ...]
            List<Map<String, Object>> raw = objectMapper.readValue(rs.getColumns(), ROW_LIST_TYPE);
            List<ColumnMetaDto> dtos = new ArrayList<>(raw.size());
            for (Map<String, Object> col : raw) {
                String name = String.valueOf(col.getOrDefault("name", ""));
                String dt = String.valueOf(col.getOrDefault("dataType", "VARCHAR"));
                Object nullable = col.get("nullable");
                boolean isNullable = nullable == null || Boolean.parseBoolean(String.valueOf(nullable));
                dtos.add(new ColumnMetaDto(name, dt, isNullable));
            }
            return dtos;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
```

> **Note:** `ResultSetRepository.findByExecutionId(UUID)` may need to be added — first grep:
> ```bash
> rg "findByExecutionId" source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/explore/ResultSetRepository.java
> ```
> If missing, add a derived method:
> ```java
> Optional<ResultSet> findByExecutionId(UUID executionId);
> ```
> If `ResultSet` doesn't have an `executionId` field at all, fall back to JOIN-based query or via `QueryExecution.getResultSet()` reverse mapping. Adapt as needed.

### - [ ] Step 1.7: Modify SqlExecutionService — chunk streaming writeback

In `SqlExecutionService.executeQueued(...)`, after the JDBC `ResultSet` reads complete and rows are accumulated:

**Replace** the existing single-blob preview write (around the line that calls `resultSet.setPreviewColumns(...)` or sets a JSON blob) with chunk-batch write logic. Pseudocode (adapt to actual existing code):

```java
// after JDBC iteration completes, `rows` is the accumulated List<Map<String,Object>>
final int CHUNK_SIZE = 2000;
int chunkIndex = 0;
for (int from = 0; from < rows.size(); from += CHUNK_SIZE) {
    int to = Math.min(rows.size(), from + CHUNK_SIZE);
    List<Map<String, Object>> chunk = rows.subList(from, to);
    QueryExecutionChunk c = new QueryExecutionChunk();
    c.setExecutionId(execution.getId());
    c.setChunkIndex(chunkIndex++);
    c.setRowsJson(objectMapper.writeValueAsString(chunk));
    c.setRowStart(from);
    c.setRowEnd(to);
    chunkRepository.save(c);
}
// Keep existing preview (first 100 rows) for legacy /api/sql/result-page compatibility
List<Map<String, Object>> preview = rows.subList(0, Math.min(100, rows.size()));
resultSet.setPreviewColumns(objectMapper.writeValueAsString(preview));
resultSet.setRowCount((long) rows.size());
resultSet.setChunkCount(chunkIndex);
```

Inject `QueryExecutionChunkRepository chunkRepository` into the constructor.

> **Important:** Do NOT change the legacy `/api/sql/result-page/{id}` endpoint behavior. The legacy preview path still works for old UI; new chunk path serves new UI.

### - [ ] Step 1.8: HiveQueryGateway MAX_ROWS bump

In `service/query/HiveQueryGateway.java`:
- Change `private static final int MAX_ROWS = 5000;` → `private static final int MAX_ROWS = 100_000;`
- Three call sites: `stmt.setMaxRows(MAX_ROWS)` — all stay as-is, just pick up the new constant.
- Keep `stmt.setFetchSize(2000)` unchanged.

### - [ ] Step 1.9: New endpoints in SqlIdeExecutionController

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeExecutionController.java`:

```java
package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.sql.SqlResultStreamService;
import com.yuzhi.dts.platform.service.sql.dto.ResultMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultPageDto;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql/v2/executions")
public class SqlIdeExecutionController {

    private final SqlResultStreamService streamService;
    private final AuditService auditService;

    public SqlIdeExecutionController(SqlResultStreamService streamService, AuditService auditService) {
        this.streamService = streamService;
        this.auditService = auditService;
    }

    @GetMapping("/{id}/meta")
    public ApiResponse<ResultMetaDto> meta(@PathVariable UUID id) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        auditService.audit("READ", "sql.ide.execution.meta", id.toString());
        return ApiResponses.ok(streamService.getMeta(id));
    }

    @GetMapping("/{id}/page")
    public ApiResponse<ResultPageDto> page(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "200") int size
    ) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        auditService.audit("READ", "sql.ide.execution.page", id + "?page=" + page + "&size=" + size);
        return ApiResponses.ok(streamService.getPage(id, page, size));
    }
}
```

### - [ ] Step 1.10: Cleanup scheduler

`service/scheduling/QueryExecutionChunkCleaner.java`:

```java
package com.yuzhi.dts.platform.service.scheduling;

import com.yuzhi.dts.platform.repository.explore.QueryExecutionChunkRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class QueryExecutionChunkCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(QueryExecutionChunkCleaner.class);
    private static final Duration RETENTION = Duration.ofDays(30);

    private final QueryExecutionChunkRepository repository;

    public QueryExecutionChunkCleaner(QueryExecutionChunkRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 3 * * *")  // 3am daily
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(RETENTION);
        int deleted = repository.deleteOlderThan(cutoff);
        LOG.info("[chunk-cleaner] removed {} chunks older than {}", deleted, cutoff);
    }
}
```

> **Note:** Verify `@EnableScheduling` is present somewhere in the app (likely already enabled — `rg "@EnableScheduling" source/dts-platform/src/main/java`). If not, add to main app.

### - [ ] Step 1.11: Integration test

`source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeExecutionControllerIT.java`:

```java
package com.yuzhi.dts.platform.web.rest.sql;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@WithMockUser(username = "alice")
class SqlIdeExecutionControllerIT {

    @Autowired private MockMvc mvc;

    @Test
    void metaForUnknownExecutionReturns404() throws Exception {
        mvc.perform(get("/api/sql/v2/executions/{id}/meta", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void pageForUnknownExecutionReturns404() throws Exception {
        mvc.perform(get("/api/sql/v2/executions/{id}/page", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void pageClampsPageSizeTo500() throws Exception {
        // For unknown execution we expect 404 anyway; clamping verified via unit tests separately
        // This test just exercises the URL parsing for size param
        mvc.perform(
            get("/api/sql/v2/executions/{id}/page", UUID.randomUUID())
                .param("size", "9999")
        ).andExpect(status().isNotFound());
    }
}
```

> **Note:** `EntityNotFoundException` from `getMeta` should map to 404 by Spring Boot default — if the project has a global exception handler returning 500, may need a `@ResponseStatus(NOT_FOUND)` on `EntityNotFoundException` import alias OR `@ExceptionHandler` in a new `@ControllerAdvice`. Most JHipster setups handle this automatically. Verify by running the test.

### - [ ] Step 1.12: Verification

```bash
cd source/dts-platform
./mvnw -q -DskipTests compile
./mvnw -q test -Dtest='SqlIde*'
```

**Expected:** 18 prior + 3 new = 21 IT PASS.

### - [ ] Step 1.13: Commit

```bash
git add source/dts-platform/src/main/resources/config/liquibase/changelog/20260413_03_query_execution_chunk.xml \
        source/dts-platform/src/main/resources/config/liquibase/master.xml \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/explore/QueryExecutionChunk.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/explore/QueryExecutionChunkRepository.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/ColumnMetaDto.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/ResultMetaDto.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/ResultPageDto.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlResultStreamService.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlResultStreamServiceImpl.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlExecutionService.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/query/HiveQueryGateway.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeExecutionController.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/scheduling/QueryExecutionChunkCleaner.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeExecutionControllerIT.java
git commit -m "$(cat <<'EOF'
feat(F4/T16): chunked result storage + 100k row limit + new page/meta endpoints

Sprint-11 F4 T16: query_execution_chunk stores results in 2000-row
JSONB chunks (replaces single 5000-row preview blob). HiveQueryGateway
MAX_ROWS bumped to 100_000. SqlExecutionService writes chunks during
JDBC iteration; legacy preview (first 100 rows) retained for old UI.

New /api/sql/v2/executions/{id}/{meta,page} endpoints back the new
ResultGrid (T17). Page size clamps to [1,500]. Daily 3am cleaner
prunes chunks older than 30 days.
EOF
)"
```

---

## Task 2 — T17: ResultGrid (TDD: cellCopy + columnState)

**Files:**
- Modify: `package.json` (add `@tanstack/react-table@^8` + `@tanstack/react-virtual@^3`)
- Create: `src/components/sql-ide/api/sqlIdeExecution.ts` (getMeta, getPage)
- Create: `src/components/sql-ide/result/cellCopy.ts`
- Create: `src/components/sql-ide/result/__tests__/cellCopy.test.ts`
- Create: `src/components/sql-ide/result/columnState.ts`
- Create: `src/components/sql-ide/result/__tests__/columnState.test.ts`
- Create: `src/components/sql-ide/result/ResultGrid.tsx`
- Modify: `src/components/sql-ide/tabs/types.ts` (add `gridState`)
- Modify: `src/components/sql-ide/tabs/useTabStore.ts` (add `updateGridState`)
- Modify: `src/components/sql-ide/SqlIde.tsx` (replace BottomPanel placeholder with ResultGrid)

### - [ ] Step 2.1: Install deps

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp
pnpm add @tanstack/react-table@^8 @tanstack/react-virtual@^3
```

### - [ ] Step 2.2: API client

`src/components/sql-ide/api/sqlIdeExecution.ts`:

```typescript
import apiClient from "@/api/apiClient";

export interface ColumnMeta {
  name: string;
  dataType: string;
  nullable: boolean;
}

export interface ResultMeta {
  executionId: string;
  status: string | null;
  columns: ColumnMeta[];
  totalRows: number;
  truncated: boolean;
  elapsedMs: number | null;
  bytesProcessed: number | null;
}

export interface ResultPage {
  rows: Array<Record<string, unknown>>;
  columns: ColumnMeta[];
  page: number;
  pageSize: number;
  total: number;
  truncated: boolean;
}

export async function getExecutionMeta(executionId: string): Promise<ResultMeta> {
  return apiClient.get<ResultMeta>({ url: `/api/sql/v2/executions/${executionId}/meta` });
}

export async function getExecutionPage(
  executionId: string,
  page = 1,
  size = 200,
): Promise<ResultPage> {
  return apiClient.get<ResultPage>({
    url: `/api/sql/v2/executions/${executionId}/page?page=${page}&size=${size}`,
  });
}
```

### - [ ] Step 2.3: TDD cellCopy

`src/components/sql-ide/result/__tests__/cellCopy.test.ts`:

```typescript
import { describe, expect, it } from "vitest";
import { rowsToTSV, valueToCellString } from "../cellCopy";

describe("valueToCellString", () => {
  it("returns empty string for null/undefined", () => {
    expect(valueToCellString(null)).toBe("");
    expect(valueToCellString(undefined)).toBe("");
  });

  it("renders numbers and booleans", () => {
    expect(valueToCellString(42)).toBe("42");
    expect(valueToCellString(true)).toBe("true");
    expect(valueToCellString(false)).toBe("false");
  });

  it("escapes tab and newline by stripping", () => {
    expect(valueToCellString("a\tb\nc")).toBe("a b c");
  });

  it("stringifies objects as JSON", () => {
    expect(valueToCellString({ a: 1 })).toBe('{"a":1}');
  });
});

describe("rowsToTSV", () => {
  it("joins headers + rows with tabs and newlines", () => {
    const out = rowsToTSV([{ id: 1, name: "Alice" }, { id: 2, name: "Bob" }], ["id", "name"]);
    expect(out).toBe("id\tname\n1\tAlice\n2\tBob");
  });

  it("handles missing keys as empty cells", () => {
    const out = rowsToTSV([{ id: 1 }], ["id", "missing"]);
    expect(out).toBe("id\tmissing\n1\t");
  });
});
```

### - [ ] Step 2.4: Run tests → FAIL

```bash
pnpm vitest run src/components/sql-ide/result/__tests__/cellCopy.test.ts
```

**Expected:** FAIL — module not found.

### - [ ] Step 2.5: Implement cellCopy

`src/components/sql-ide/result/cellCopy.ts`:

```typescript
export function valueToCellString(v: unknown): string {
  if (v === null || v === undefined) return "";
  if (typeof v === "number" || typeof v === "boolean") return String(v);
  if (typeof v === "string") return v.replace(/\t/g, " ").replace(/\n/g, " ");
  try {
    return JSON.stringify(v);
  } catch {
    return String(v);
  }
}

export function rowsToTSV(rows: Array<Record<string, unknown>>, columnNames: string[]): string {
  const header = columnNames.join("\t");
  const body = rows
    .map((r) => columnNames.map((c) => valueToCellString(r[c])).join("\t"))
    .join("\n");
  return body ? `${header}\n${body}` : header;
}
```

### - [ ] Step 2.6: Verify cellCopy tests pass

```bash
pnpm vitest run src/components/sql-ide/result/__tests__/cellCopy.test.ts
```

**Expected:** 6/6 PASS.

### - [ ] Step 2.7: TDD columnState reducer

`src/components/sql-ide/result/__tests__/columnState.test.ts`:

```typescript
import { describe, expect, it } from "vitest";
import {
  applyColumnAction,
  type ColumnAction,
  type GridColumnState,
  emptyGridColumnState,
} from "../columnState";

describe("applyColumnAction", () => {
  it("setColumnWidth stores width by column name", () => {
    const next = applyColumnAction(emptyGridColumnState(), { type: "setWidth", name: "id", width: 80 });
    expect(next.columnWidths.id).toBe(80);
  });

  it("toggleHidden flips hidden state", () => {
    const s1 = applyColumnAction(emptyGridColumnState(), { type: "toggleHidden", name: "name" });
    expect(s1.hiddenColumns).toContain("name");
    const s2 = applyColumnAction(s1, { type: "toggleHidden", name: "name" });
    expect(s2.hiddenColumns).not.toContain("name");
  });

  it("setPin adds to pinned, setUnpin removes", () => {
    const s1 = applyColumnAction(emptyGridColumnState(), { type: "setPin", name: "id", side: "left" });
    expect(s1.pinnedColumns.left).toContain("id");
    const s2 = applyColumnAction(s1, { type: "setUnpin", name: "id" });
    expect(s2.pinnedColumns.left).not.toContain("id");
  });

  it("setSort replaces existing sort", () => {
    const s1 = applyColumnAction(emptyGridColumnState(), { type: "setSort", name: "amount", direction: "desc" });
    expect(s1.sort).toEqual({ name: "amount", direction: "desc" });
    const s2 = applyColumnAction(s1, { type: "setSort", name: "id", direction: "asc" });
    expect(s2.sort).toEqual({ name: "id", direction: "asc" });
  });

  it("clearSort removes sort", () => {
    const s1 = applyColumnAction(emptyGridColumnState(), { type: "setSort", name: "id", direction: "asc" });
    const s2 = applyColumnAction(s1, { type: "clearSort" });
    expect(s2.sort).toBeNull();
  });
});
```

### - [ ] Step 2.8: Run → FAIL

```bash
pnpm vitest run src/components/sql-ide/result/__tests__/columnState.test.ts
```

**Expected:** FAIL — module not found.

### - [ ] Step 2.9: Implement columnState

`src/components/sql-ide/result/columnState.ts`:

```typescript
export interface SortState {
  name: string;
  direction: "asc" | "desc";
}

export interface GridColumnState {
  columnWidths: Record<string, number>;
  hiddenColumns: string[];
  pinnedColumns: { left: string[]; right: string[] };
  sort: SortState | null;
}

export type ColumnAction =
  | { type: "setWidth"; name: string; width: number }
  | { type: "toggleHidden"; name: string }
  | { type: "setPin"; name: string; side: "left" | "right" }
  | { type: "setUnpin"; name: string }
  | { type: "setSort"; name: string; direction: "asc" | "desc" }
  | { type: "clearSort" };

export function emptyGridColumnState(): GridColumnState {
  return {
    columnWidths: {},
    hiddenColumns: [],
    pinnedColumns: { left: [], right: [] },
    sort: null,
  };
}

export function applyColumnAction(state: GridColumnState, action: ColumnAction): GridColumnState {
  switch (action.type) {
    case "setWidth":
      return { ...state, columnWidths: { ...state.columnWidths, [action.name]: action.width } };
    case "toggleHidden": {
      const isHidden = state.hiddenColumns.includes(action.name);
      return {
        ...state,
        hiddenColumns: isHidden
          ? state.hiddenColumns.filter((n) => n !== action.name)
          : [...state.hiddenColumns, action.name],
      };
    }
    case "setPin": {
      const left = action.side === "left"
        ? [...state.pinnedColumns.left.filter((n) => n !== action.name), action.name]
        : state.pinnedColumns.left.filter((n) => n !== action.name);
      const right = action.side === "right"
        ? [...state.pinnedColumns.right.filter((n) => n !== action.name), action.name]
        : state.pinnedColumns.right.filter((n) => n !== action.name);
      return { ...state, pinnedColumns: { left, right } };
    }
    case "setUnpin":
      return {
        ...state,
        pinnedColumns: {
          left: state.pinnedColumns.left.filter((n) => n !== action.name),
          right: state.pinnedColumns.right.filter((n) => n !== action.name),
        },
      };
    case "setSort":
      return { ...state, sort: { name: action.name, direction: action.direction } };
    case "clearSort":
      return { ...state, sort: null };
    default: {
      const _exhaustive: never = action;
      void _exhaustive;
      return state;
    }
  }
}
```

### - [ ] Step 2.10: Tests pass

```bash
pnpm vitest run src/components/sql-ide/result/__tests__/columnState.test.ts
```

**Expected:** 5/5 PASS.

### - [ ] Step 2.11: Extend TabState to carry gridState

In `src/components/sql-ide/tabs/types.ts`, add to the `TabState` interface:

```typescript
import type { GridColumnState } from "../result/columnState";

export interface TabState {
  // ... existing fields ...
  gridState: GridColumnState;
}
```

In `src/components/sql-ide/tabs/useTabStore.ts`:
- In `openTab` defaults, set `gridState: emptyGridColumnState()` (import from `../result/columnState`)
- Add a method `updateGridState(tabId: string, partial: Partial<GridColumnState>)` to `TabStore` interface and impl:

```typescript
updateGridState(tabId, partial) {
  const tabs = get().tabs.map((t) =>
    t.id === tabId ? { ...t, gridState: { ...t.gridState, ...partial }, dirty: true, updatedAt: new Date().toISOString() } : t,
  );
  set({ tabs });
  persistLocal(tabs);
  scheduleDebouncedSync(get);
},
```

Update existing `useTabStore.test.ts`: hydrate-merge tests' `makeLocal` factory should set `gridState: emptyGridColumnState()` to match new shape. Run tests to verify nothing breaks.

### - [ ] Step 2.12: ResultGrid component

`src/components/sql-ide/result/ResultGrid.tsx`:

```tsx
import { useQuery } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  type ColumnDef,
  useReactTable,
} from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";
import { Empty, Pagination, Spin, message } from "antd";
import { type FC, useMemo, useRef } from "react";
import { getExecutionPage, type ColumnMeta, type ResultPage } from "../api/sqlIdeExecution";
import { rowsToTSV } from "./cellCopy";
import { applyColumnAction, type GridColumnState } from "./columnState";

export interface ResultGridProps {
  executionId: string;
  gridState: GridColumnState;
  onGridStateChange: (next: GridColumnState) => void;
}

const ROW_HEIGHT = 28;
const DEFAULT_COL_WIDTH = 140;

export const ResultGrid: FC<ResultGridProps> = ({ executionId, gridState, onGridStateChange }) => {
  const [page, setPage] = useReactStatePage();
  const { data, isLoading, isError } = useQuery<ResultPage>({
    queryKey: ["sqlide", "execution", "page", executionId, page],
    queryFn: () => getExecutionPage(executionId, page, 200),
    enabled: !!executionId,
    staleTime: 60_000,
  });

  const visibleColumns = useMemo(
    () => (data?.columns ?? []).filter((c) => !gridState.hiddenColumns.includes(c.name)),
    [data, gridState.hiddenColumns],
  );

  const tableColumns: ColumnDef<Record<string, unknown>>[] = useMemo(
    () =>
      visibleColumns.map((col: ColumnMeta) => ({
        accessorKey: col.name,
        header: col.name,
        size: gridState.columnWidths[col.name] ?? DEFAULT_COL_WIDTH,
        cell: (info) => {
          const v = info.getValue();
          if (v === null || v === undefined) return <span style={{ color: "var(--ant-color-text-quaternary)", fontStyle: "italic" }}>NULL</span>;
          if (typeof v === "number") return <span style={{ textAlign: "right", display: "block" }}>{v}</span>;
          return String(v);
        },
      })),
    [visibleColumns, gridState.columnWidths],
  );

  const table = useReactTable({
    data: data?.rows ?? [],
    columns: tableColumns,
    getCoreRowModel: getCoreRowModel(),
    columnResizeMode: "onChange",
  });

  const containerRef = useRef<HTMLDivElement>(null);
  const rowVirtualizer = useVirtualizer({
    count: data?.rows.length ?? 0,
    getScrollElement: () => containerRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 8,
  });

  if (isLoading) {
    return <div style={{ display: "flex", justifyContent: "center", padding: 24 }}><Spin /></div>;
  }
  if (isError) {
    return <Empty description="加载失败" />;
  }
  if (!data || data.rows.length === 0) {
    return <Empty description="无数据" />;
  }

  const handleCopySelection = async () => {
    const tsv = rowsToTSV(data.rows, visibleColumns.map((c) => c.name));
    try {
      await navigator.clipboard.writeText(tsv);
      void message.success(`已复制 ${data.rows.length} 行 ${visibleColumns.length} 列`);
    } catch {
      void message.warning("复制失败");
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "4px 8px", fontSize: 11, borderBottom: "1px solid var(--ant-color-border-secondary)" }}>
        <span style={{ color: "var(--ant-color-text-secondary)" }}>
          {data.rows.length} rows · page {data.page}/{Math.max(1, Math.ceil(data.total / data.pageSize))} · total {data.total}
        </span>
        <button type="button" onClick={handleCopySelection} style={{ border: "none", background: "transparent", cursor: "pointer", color: "var(--ant-color-primary)" }}>
          复制全部 (TSV)
        </button>
      </div>
      <div ref={containerRef} style={{ flex: 1, overflow: "auto" }}>
        <table style={{ borderCollapse: "collapse", width: "100%", tableLayout: "fixed" }}>
          <thead style={{ position: "sticky", top: 0, background: "var(--ant-color-bg-elevated)", zIndex: 1 }}>
            {table.getHeaderGroups().map((hg) => (
              <tr key={hg.id}>
                {hg.headers.map((header) => (
                  <th
                    key={header.id}
                    style={{
                      width: header.getSize(),
                      padding: "4px 8px",
                      borderBottom: "1px solid var(--ant-color-border)",
                      fontSize: 11,
                      textAlign: "left",
                      cursor: "pointer",
                    }}
                    onClick={() => onGridStateChange(applyColumnAction(gridState, {
                      type: "setSort",
                      name: header.column.id,
                      direction: gridState.sort?.name === header.column.id && gridState.sort.direction === "asc" ? "desc" : "asc",
                    }))}
                  >
                    {flexRender(header.column.columnDef.header, header.getContext())}
                    {gridState.sort?.name === header.column.id && (
                      <span style={{ marginLeft: 4, color: "var(--ant-color-text-secondary)" }}>
                        {gridState.sort.direction === "asc" ? "▲" : "▼"}
                      </span>
                    )}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody style={{ position: "relative", height: rowVirtualizer.getTotalSize() }}>
            {rowVirtualizer.getVirtualItems().map((virtualRow) => {
              const row = table.getRowModel().rows[virtualRow.index];
              if (!row) return null;
              return (
                <tr
                  key={row.id}
                  style={{
                    position: "absolute",
                    top: virtualRow.start,
                    left: 0,
                    height: virtualRow.size,
                    width: "100%",
                  }}
                >
                  {row.getVisibleCells().map((cell) => (
                    <td
                      key={cell.id}
                      style={{
                        padding: "2px 8px",
                        borderBottom: "1px solid var(--ant-color-border-secondary)",
                        fontSize: 12,
                        whiteSpace: "nowrap",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        width: cell.column.getSize(),
                      }}
                    >
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div style={{ padding: "4px 8px", borderTop: "1px solid var(--ant-color-border-secondary)" }}>
        <Pagination
          current={page}
          pageSize={data.pageSize}
          total={data.total}
          onChange={(p) => setPage(p)}
          showSizeChanger={false}
          size="small"
        />
      </div>
    </div>
  );
};

// helper: track page state via React useState (avoid import collision)
import { useState } from "react";
function useReactStatePage() {
  return useState(1);
}
```

> **Note:** the local `useReactStatePage` helper avoids name confusion. Inline `useState(1)` is fine but kept here for readability.

### - [ ] Step 2.13: Wire ResultGrid into SqlIde

In `src/components/sql-ide/SqlIde.tsx`:
- Import `ResultGrid` and `applyColumnAction` (only `ResultGrid` actually needed)
- In the BottomPanel children, replace the placeholder:

```tsx
<BottomPanel>
  <div style={{ height: "100%", display: "flex", flexDirection: "column" }}>
    <div style={{ padding: "4px 8px", display: "flex", justifyContent: "space-between", alignItems: "center", borderBottom: "1px solid var(--ant-color-border-secondary)" }}>
      <span style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>
        {activeTab?.lastExecutionId ? `执行 ID: ${activeTab.lastExecutionId.slice(0, 8)}` : "未运行"}
      </span>
      <Button size="small" onClick={() => setHelpOpen(true)}>⌨ Shortcuts</Button>
    </div>
    <div style={{ flex: 1, minHeight: 0 }}>
      {activeTab?.lastExecutionId ? (
        <ResultGrid
          executionId={activeTab.lastExecutionId}
          gridState={activeTab.gridState}
          onGridStateChange={(next) => updateGridState(activeTab.id, next)}
        />
      ) : (
        <div style={{ padding: 12, color: "var(--ant-color-text-tertiary)" }}>运行 SQL 后结果出现在这里</div>
      )}
    </div>
  </div>
</BottomPanel>
```

Add `updateGridState` to the `useTabStore` selector destructure.

### - [ ] Step 2.14: Verification

```bash
pnpm tsc --noEmit
pnpm vitest run src/components/sql-ide
```

**Expected:** tsc clean, 50 + 6 + 5 = 61 tests PASS.

### - [ ] Step 2.15: Commit

```bash
git add source/dts-platform-webapp/package.json \
        source/dts-platform-webapp/pnpm-lock.yaml \
        source/dts-platform-webapp/src/components/sql-ide/api/sqlIdeExecution.ts \
        source/dts-platform-webapp/src/components/sql-ide/result/ \
        source/dts-platform-webapp/src/components/sql-ide/tabs/types.ts \
        source/dts-platform-webapp/src/components/sql-ide/tabs/useTabStore.ts \
        source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx
git commit -m "$(cat <<'EOF'
feat(F4/T17): ResultGrid with virtual scrolling + sort + copy

Sprint-11 F4 T17: ResultGrid uses @tanstack/react-table + react-virtual
to render up to 100k rows efficiently (28px row height, overscan 8).
Header click toggles sort (state lives in TabState.gridState, persisted
via useTabStore). "复制全部 (TSV)" button copies visible columns to
clipboard via cellCopy helpers (TDD: 11 new tests cover cellCopy and
columnState reducers).

Pagination at 200/page; ResultGrid auto-loads when activeTab has a
lastExecutionId. Empty/loading/error states handled.
EOF
)"
```

---

## Task 3 — T18: 适应性轮询 + cancel + 提交 SQL flow

**Files:**
- Modify: `src/components/sql-ide/api/sqlIdeExecution.ts` (add submitSql, getStatus, postCancel)
- Create: `src/components/sql-ide/hooks/useSqlExecution.ts`
- Modify: `src/components/sql-ide/SqlIde.tsx` (wire `onExecute` to `useSqlExecution.submit`)

### - [ ] Step 3.1: Extend API client

In `src/components/sql-ide/api/sqlIdeExecution.ts`, add:

```typescript
export interface SubmitResponse {
  executionId: string;
}

export interface ExecutionStatus {
  executionId: string;
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "CANCELED";
  elapsedMs: number | null;
  rows: number | null;
  errorMessage: string | null;
}

export async function submitSql(payload: {
  sqlText: string;
  engine: string;
  datasourceId?: string | null;
  catalog?: string | null;
}): Promise<SubmitResponse> {
  return apiClient.post<SubmitResponse>({ url: "/api/sql/submit", data: payload });
}

export async function getExecutionStatus(executionId: string): Promise<ExecutionStatus> {
  return apiClient.get<ExecutionStatus>({ url: `/api/sql/status/${executionId}` });
}

export async function cancelExecution(executionId: string): Promise<void> {
  await apiClient.post<void>({ url: `/api/sql/cancel/${executionId}` });
}
```

> **Note:** Verify endpoint paths. `/api/sql/submit`, `/api/sql/status/{id}`, `/api/sql/cancel/{id}` are existing legacy v1 endpoints from F1 era — confirm they exist in `SqlWorkbenchResource.java`. If field names differ, adapt the interfaces.

### - [ ] Step 3.2: useSqlExecution hook

`src/components/sql-ide/hooks/useSqlExecution.ts`:

```typescript
import { useCallback, useEffect, useRef, useState } from "react";
import {
  cancelExecution,
  getExecutionStatus,
  submitSql,
  type ExecutionStatus,
} from "../api/sqlIdeExecution";

const POLL_BACKOFF_MS = [500, 1500, 3000, 5000];

export interface UseSqlExecutionResult {
  state: "idle" | "running" | "success" | "failed" | "canceled";
  elapsedMs: number;
  rowCount: number | null;
  executionId: string | null;
  errorMessage: string | null;
  submit: (payload: {
    sqlText: string;
    engine: string;
    datasourceId?: string | null;
    catalog?: string | null;
  }) => Promise<string | null>;
  cancel: () => Promise<void>;
}

export function useSqlExecution(): UseSqlExecutionResult {
  const [state, setState] = useState<UseSqlExecutionResult["state"]>("idle");
  const [elapsedMs, setElapsedMs] = useState(0);
  const [rowCount, setRowCount] = useState<number | null>(null);
  const [executionId, setExecutionId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const cancelledRef = useRef(false);
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (pollTimerRef.current) clearTimeout(pollTimerRef.current);
    };
  }, []);

  const stopPolling = useCallback(() => {
    if (pollTimerRef.current) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  }, []);

  const submit = useCallback(
    async (payload: Parameters<UseSqlExecutionResult["submit"]>[0]): Promise<string | null> => {
      cancelledRef.current = false;
      setState("running");
      setElapsedMs(0);
      setRowCount(null);
      setErrorMessage(null);
      try {
        const resp = await submitSql(payload);
        setExecutionId(resp.executionId);
        await pollUntilDone(resp.executionId);
        return resp.executionId;
      } catch (err) {
        setState("failed");
        setErrorMessage(err instanceof Error ? err.message : "submit failed");
        return null;
      }
    },
    [],
  );

  const pollUntilDone = useCallback(async (id: string) => {
    const startedAt = Date.now();
    let attempt = 0;
    return new Promise<void>((resolve) => {
      const tick = async () => {
        if (cancelledRef.current) return resolve();
        try {
          const s: ExecutionStatus = await getExecutionStatus(id);
          setElapsedMs(Date.now() - startedAt);
          setRowCount(s.rows);
          if (s.status === "SUCCESS" || s.status === "FAILED" || s.status === "CANCELED") {
            setState(s.status === "SUCCESS" ? "success" : s.status === "FAILED" ? "failed" : "canceled");
            if (s.errorMessage) setErrorMessage(s.errorMessage);
            return resolve();
          }
        } catch (err) {
          setState("failed");
          setErrorMessage(err instanceof Error ? err.message : "poll failed");
          return resolve();
        }
        const delay = POLL_BACKOFF_MS[Math.min(attempt, POLL_BACKOFF_MS.length - 1)];
        attempt++;
        pollTimerRef.current = setTimeout(tick, delay);
      };
      tick();
    });
  }, []);

  const cancel = useCallback(async () => {
    if (!executionId) return;
    cancelledRef.current = true;
    stopPolling();
    setState("canceled");
    try {
      await cancelExecution(executionId);
    } catch {
      // ignore — server may have already finished
    }
  }, [executionId, stopPolling]);

  return { state, elapsedMs, rowCount, executionId, errorMessage, submit, cancel };
}
```

### - [ ] Step 3.3: Wire into SqlIde

In `src/components/sql-ide/SqlIde.tsx`:
- Import + call `const sqlExec = useSqlExecution();`
- Add `useEffect` to write `sqlExec.executionId` back to `activeTab.lastExecutionId` via `updateTab`:

```tsx
useEffect(() => {
  if (sqlExec.executionId && activeTab && sqlExec.executionId !== activeTab.lastExecutionId) {
    updateTab(activeTab.id, { lastExecutionId: sqlExec.executionId });
  }
}, [sqlExec.executionId, activeTab, updateTab]);
```

- Replace placeholder `onExecute={(s) => console.info(...)}` with:
  ```tsx
  onExecute={(s) => {
    if (!s.trim() || !activeTab) return;
    void sqlExec.submit({
      sqlText: s,
      engine: activeTab.engine,
      datasourceId: activeTab.datasourceId,
      catalog: activeTab.schemaContext,
    });
  }}
  ```

- In BottomPanel header, show status info from sqlExec:
  ```tsx
  <span style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>
    {sqlExec.state === "running"
      ? `运行中 · ${(sqlExec.elapsedMs / 1000).toFixed(1)}s`
      : sqlExec.state === "success"
      ? `成功 · ${sqlExec.rowCount ?? 0} 行 · ${(sqlExec.elapsedMs / 1000).toFixed(1)}s`
      : sqlExec.state === "failed"
      ? `失败 · ${sqlExec.errorMessage ?? "未知错误"}`
      : sqlExec.state === "canceled"
      ? "已取消"
      : "未运行"}
  </span>
  ```

- Add a Cancel button when running:
  ```tsx
  {sqlExec.state === "running" && (
    <Button danger size="small" onClick={() => void sqlExec.cancel()}>取消</Button>
  )}
  ```

### - [ ] Step 3.4: Verify

```bash
pnpm tsc --noEmit
pnpm vitest run src/components/sql-ide
```

**Expected:** tsc clean, 61/61 PASS.

### - [ ] Step 3.5: Commit

```bash
git add source/dts-platform-webapp/src/components/sql-ide/api/sqlIdeExecution.ts \
        source/dts-platform-webapp/src/components/sql-ide/hooks/useSqlExecution.ts \
        source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx
git commit -m "$(cat <<'EOF'
feat(F4/T18): adaptive backoff polling + cancel + execute wiring

Sprint-11 F4 T18: useSqlExecution hook calls /api/sql/submit, then
polls /api/sql/status with backoff 500ms / 1.5s / 3s / 5s (capped).
Cancel button stops polling and POSTs /api/sql/cancel. Execution ID
written back to active TabState.lastExecutionId so ResultGrid
auto-displays the result. Status line shows running time + row count
+ error message in the BottomPanel header.
EOF
)"
```

---

## Task 4 — T19: 流式导出 (CSV / Excel / JSON) + 频控

**Files:**
- Create: `service/sql/SqlExecutionExportRateLimiter.java`
- Modify: `service/sql/SqlResultStreamServiceImpl.java` (add streamCsv / streamJson / streamExcel methods)
- Modify: `service/sql/SqlResultStreamService.java` (add streaming method signatures)
- Modify: `web/rest/sql/SqlIdeExecutionController.java` (add `/export` endpoint)
- Modify: `pom.xml` (add poi-ooxml if not present)
- Create: `src/components/sql-ide/result/ExportMenu.tsx`
- Modify: `src/components/sql-ide/SqlIde.tsx` (mount ExportMenu in BottomPanel header)
- Modify: `src/components/sql-ide/api/sqlIdeExecution.ts` (add `downloadExport`)

### - [ ] Step 4.1: Add POI dependency (skip if already present)

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform
grep -q "poi-ooxml" pom.xml && echo "POI already present" || echo "Need to add POI"
```

If missing, in `pom.xml` add:
```xml
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
  <version>5.3.0</version>
</dependency>
```

### - [ ] Step 4.2: Rate limiter

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlExecutionExportRateLimiter.java`:

```java
package com.yuzhi.dts.platform.service.sql;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class SqlExecutionExportRateLimiter {

    private static final int MAX_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
        .expireAfterWrite(WINDOW)
        .maximumSize(10_000)
        .build();

    /** Returns true if user is allowed to proceed (and increments). */
    public boolean tryAcquire(String userLogin) {
        AtomicInteger counter = counters.get(userLogin, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= MAX_PER_WINDOW;
    }

    public int currentCount(String userLogin) {
        AtomicInteger counter = counters.getIfPresent(userLogin);
        return counter == null ? 0 : counter.get();
    }
}
```

### - [ ] Step 4.3: Add streaming method signatures to interface

In `service/sql/SqlResultStreamService.java`, add:

```java
import java.io.OutputStream;

void exportCsv(UUID executionId, OutputStream out);
void exportJson(UUID executionId, OutputStream out);
void exportExcel(UUID executionId, OutputStream out);
```

### - [ ] Step 4.4: Implement stream exports in SqlResultStreamServiceImpl

Add methods (full code; require imports for SXSSFWorkbook + POI utilities):

```java
@Override
public void exportCsv(UUID executionId, OutputStream out) {
    ResultMetaDto meta = getMeta(executionId);
    java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8));
    // BOM for Excel-friendly UTF-8
    try { out.write(0xEF); out.write(0xBB); out.write(0xBF); } catch (java.io.IOException ignored) {}
    // Header
    writer.println(meta.columns().stream().map(c -> escapeCsv(c.name())).reduce((a, b) -> a + "," + b).orElse(""));
    streamRange(executionId, 0, meta.totalRows()).forEach(row -> {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < meta.columns().size(); i++) {
            if (i > 0) sb.append(',');
            Object v = row.get(meta.columns().get(i).name());
            sb.append(escapeCsv(v == null ? "" : String.valueOf(v)));
        }
        writer.println(sb);
    });
    writer.flush();
}

private static String escapeCsv(String v) {
    if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
        return '"' + v.replace("\"", "\"\"") + '"';
    }
    return v;
}

@Override
public void exportJson(UUID executionId, OutputStream out) {
    ResultMetaDto meta = getMeta(executionId);
    com.fasterxml.jackson.core.JsonFactory factory = objectMapper.getFactory();
    try (com.fasterxml.jackson.core.JsonGenerator gen = factory.createGenerator(out, com.fasterxml.jackson.core.JsonEncoding.UTF8)) {
        gen.writeStartArray();
        streamRange(executionId, 0, meta.totalRows()).forEach(row -> {
            try {
                gen.writeStartObject();
                for (var col : meta.columns()) {
                    Object v = row.get(col.name());
                    gen.writeFieldName(col.name());
                    objectMapper.writeValue(gen, v);
                }
                gen.writeEndObject();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        gen.writeEndArray();
        gen.flush();
    } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException(e);
    }
}

@Override
public void exportExcel(UUID executionId, OutputStream out) {
    ResultMetaDto meta = getMeta(executionId);
    try (org.apache.poi.xssf.streaming.SXSSFWorkbook wb = new org.apache.poi.xssf.streaming.SXSSFWorkbook(100)) {
        org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("results");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        for (int i = 0; i < meta.columns().size(); i++) {
            header.createCell(i).setCellValue(meta.columns().get(i).name());
        }
        int[] rowIndex = { 1 };
        streamRange(executionId, 0, meta.totalRows()).forEach(row -> {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(rowIndex[0]++);
            for (int c = 0; c < meta.columns().size(); c++) {
                Object v = row.get(meta.columns().get(c).name());
                if (v == null) {
                    r.createCell(c).setBlank();
                } else if (v instanceof Number n) {
                    r.createCell(c).setCellValue(n.doubleValue());
                } else if (v instanceof Boolean b) {
                    r.createCell(c).setCellValue(b);
                } else {
                    r.createCell(c).setCellValue(String.valueOf(v));
                }
            }
        });
        wb.write(out);
        wb.dispose();
    } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException(e);
    }
}
```

### - [ ] Step 4.5: Add /export endpoint

In `SqlIdeExecutionController.java`, add:

```java
@GetMapping("/{id}/export")
public org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> export(
    @PathVariable UUID id,
    @RequestParam(defaultValue = "csv") String format
) {
    String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
    if (!rateLimiter.tryAcquire(user)) {
        return org.springframework.http.ResponseEntity.status(429).build();
    }
    String fmt = format.toLowerCase();
    String contentType;
    String ext;
    switch (fmt) {
        case "json" -> { contentType = "application/json"; ext = "json"; }
        case "xlsx", "excel" -> { contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; ext = "xlsx"; }
        default -> { contentType = "text/csv; charset=utf-8"; ext = "csv"; }
    }
    org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody body = out -> {
        switch (fmt) {
            case "json" -> streamService.exportJson(id, out);
            case "xlsx", "excel" -> streamService.exportExcel(id, out);
            default -> streamService.exportCsv(id, out);
        }
    };
    auditService.audit("EXPORT", "sql.ide.execution.export", id + ":" + ext);
    return org.springframework.http.ResponseEntity
        .ok()
        .header("Content-Type", contentType)
        .header("Content-Disposition", "attachment; filename=execution-" + id.toString().substring(0, 8) + "." + ext)
        .body(body);
}
```

Inject `SqlExecutionExportRateLimiter rateLimiter` into the controller constructor.

### - [ ] Step 4.6: Frontend export menu

`src/components/sql-ide/result/ExportMenu.tsx`:

```tsx
import { Dropdown, type MenuProps, message } from "antd";
import { type FC, useCallback } from "react";

interface ExportMenuProps {
  executionId: string;
}

export const ExportMenu: FC<ExportMenuProps> = ({ executionId }) => {
  const triggerDownload = useCallback((format: "csv" | "json" | "xlsx") => {
    const url = `/api/sql/v2/executions/${executionId}/export?format=${format}`;
    // Use simple anchor click; browser will follow Content-Disposition
    const a = document.createElement("a");
    a.href = url;
    a.rel = "noopener";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    void message.success(`已请求 ${format.toUpperCase()} 导出`);
  }, [executionId]);

  const items: MenuProps["items"] = [
    { key: "csv", label: "导出 CSV" },
    { key: "xlsx", label: "导出 Excel" },
    { key: "json", label: "导出 JSON" },
  ];

  return (
    <Dropdown menu={{ items, onClick: ({ key }) => triggerDownload(key as "csv" | "json" | "xlsx") }}>
      <button
        type="button"
        style={{ border: "none", background: "transparent", cursor: "pointer", color: "var(--ant-color-primary)", fontSize: 11 }}
      >
        导出 ▾
      </button>
    </Dropdown>
  );
};
```

### - [ ] Step 4.7: Wire ExportMenu

In `SqlIde.tsx`'s BottomPanel header, add `<ExportMenu>` next to the status line when `activeTab?.lastExecutionId` is non-null.

### - [ ] Step 4.8: Verify

```bash
cd source/dts-platform && ./mvnw -q -DskipTests compile && ./mvnw -q test -Dtest='SqlIde*'
cd ../dts-platform-webapp && pnpm tsc --noEmit && pnpm vitest run src/components/sql-ide
```

**Expected:** 21 backend IT PASS (no new IT in T19; export verified manually), frontend 61/61 PASS, tsc clean.

### - [ ] Step 4.9: Commit

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlExecutionExportRateLimiter.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlResultStreamService.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlResultStreamServiceImpl.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeExecutionController.java \
        source/dts-platform/pom.xml \
        source/dts-platform-webapp/src/components/sql-ide/result/ExportMenu.tsx \
        source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx
git commit -m "$(cat <<'EOF'
feat(F4/T19): streaming CSV/Excel/JSON export with 5/10min rate limit

Sprint-11 F4 T19: GET /api/sql/v2/executions/{id}/export?format=csv|json|xlsx
streams the full result set without buffering in JVM heap. POI
SXSSFWorkbook keeps a 100-row window; Jackson JsonGenerator writes
JSON token-by-token; CSV uses PrintWriter + BOM. Per-user rate limit
of 5 exports per 10-minute window via Caffeine; over-limit returns 429.

ExportMenu in the bottom panel header offers all three formats; click
triggers a browser anchor download honoring Content-Disposition.
EOF
)"
```

---

## Task 5 — T20: 全量审计 13 类动作

**Files:**
- Create: `service/audit/SqlIdeAuditActions.java`
- Create: `web/rest/sql/SqlIdeAuditController.java` (POST /audit/copy from frontend)
- Modify: `service/sql/SqlExecutionService.java` (add SQL_EXECUTE_SUBMIT/CANCEL/COMPLETE with rewritten SQL field)
- Modify: existing controllers — add audit call where missing per the 13-list
- Modify: `src/components/sql-ide/api/sqlIdeExecution.ts` (add `postCopyAudit`)
- Modify: `src/components/sql-ide/result/ResultGrid.tsx` (call postCopyAudit on copy)
- Create: `src/test/java/.../web/rest/sql/SqlIdeAuditControllerIT.java`

### Audit 13 action constants

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/audit/SqlIdeAuditActions.java`:

```java
package com.yuzhi.dts.platform.service.audit;

public final class SqlIdeAuditActions {
    private SqlIdeAuditActions() {}

    public static final String SQL_EXECUTE_SUBMIT   = "sql.ide.execute.submit";
    public static final String SQL_EXECUTE_CANCEL   = "sql.ide.execute.cancel";
    public static final String SQL_EXECUTE_COMPLETE = "sql.ide.execute.complete";
    public static final String SQL_RESULT_VIEW      = "sql.ide.result.view";
    public static final String SQL_RESULT_EXPORT    = "sql.ide.result.export";
    public static final String SQL_RESULT_COPY      = "sql.ide.result.copy";
    public static final String SQL_TEMP_VIEW_CREATE = "sql.ide.temp_view.create";   // F5
    public static final String SQL_SUBQUERY_EXECUTE = "sql.ide.subquery.execute";   // F5
    public static final String SQL_PLAN_VIEW        = "sql.ide.plan.view";          // F5
    public static final String SQL_IDE_TAB_SAVE     = "sql.ide.tab.save";           // already in F2
    public static final String SAVED_QUERY_LOAD     = "sql.workbench.saved-query.load";
    public static final String SQL_CATALOG_BROWSE   = "sql.ide.catalog.browse";     // family in F3
    public static final String SQL_HISTORY_VIEW     = "sql.ide.history";            // already in F3 (now removed; this constant only for future)
}
```

### - [ ] Step 5.1: Implement submit/cancel/complete audit with rewritten SQL

In `SqlExecutionService.submit(...)`, after `validationService.validate(...)` produces the rewritten SQL, audit BOTH:

```java
String userLogin = principal != null ? principal.getName() : "anonymous";
String rawSqlHash = Integer.toHexString(request.sqlText().hashCode());
String rewrittenSqlHash = Integer.toHexString(rewrittenSql.hashCode());
auditService.audit(
    SqlIdeAuditActions.SQL_EXECUTE_SUBMIT,
    "executionId=" + execution.getId() + " engine=" + execution.getEngine().name()
        + " sqlHash=" + rawSqlHash + " rewrittenHash=" + rewrittenSqlHash,
    userLogin
);
```

Adapt to actual `auditService.audit(String, String, String)` signature.

In `cancel(...)`:
```java
auditService.audit(SqlIdeAuditActions.SQL_EXECUTE_CANCEL, "executionId=" + id, userLogin);
```

In the completion code path (where status flips to SUCCESS/FAILED):
```java
auditService.audit(
    SqlIdeAuditActions.SQL_EXECUTE_COMPLETE,
    "executionId=" + execution.getId() + " status=" + status.name()
        + " rows=" + (rowCount == null ? -1 : rowCount)
        + " elapsedMs=" + (elapsedMs == null ? -1 : elapsedMs),
    userLogin
);
```

### - [ ] Step 5.2: Audit copy (frontend → backend)

Backend `web/rest/sql/SqlIdeAuditController.java`:

```java
package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.SqlIdeAuditActions;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql/v2/audit")
public class SqlIdeAuditController {

    private final AuditService auditService;

    public SqlIdeAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    public record CopyAuditRequest(String executionId, int cellCount) {}

    @PostMapping("/copy")
    public ApiResponse<Void> copy(@RequestBody CopyAuditRequest req) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        auditService.audit(
            SqlIdeAuditActions.SQL_RESULT_COPY,
            "executionId=" + req.executionId() + " cells=" + req.cellCount(),
            user
        );
        return ApiResponses.ok(null);
    }
}
```

Frontend `src/components/sql-ide/api/sqlIdeExecution.ts` add:

```typescript
export async function postCopyAudit(executionId: string, cellCount: number): Promise<void> {
  await apiClient.post<void>({ url: "/api/sql/v2/audit/copy", data: { executionId, cellCount } });
}
```

In `ResultGrid.tsx`'s `handleCopySelection`, after successful clipboard write call:
```typescript
import { postCopyAudit } from "../api/sqlIdeExecution";
// inside handleCopySelection after clipboard.writeText success:
void postCopyAudit(executionId, data.rows.length * visibleColumns.length);
```

### - [ ] Step 5.3: Audit page view

Already added in T16 Step 1.9 (`READ` `sql.ide.execution.page`). Confirm it stays.

### - [ ] Step 5.4: Audit export

Already added in T19 Step 4.5 (`EXPORT` `sql.ide.execution.export`). Confirm it stays.

### - [ ] Step 5.5: Add IT for audit copy

`src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeAuditControllerIT.java`:

```java
package com.yuzhi.dts.platform.web.rest.sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@WithMockUser
class SqlIdeAuditControllerIT {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void copyAuditAccepts200() throws Exception {
        Map<String, Object> body = Map.of("executionId", "00000000-0000-0000-0000-000000000000", "cellCount", 42);
        mvc.perform(post("/api/sql/v2/audit/copy").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body)))
            .andExpect(status().isOk());
    }
}
```

### - [ ] Step 5.6: Verify

```bash
cd source/dts-platform && ./mvnw -q test -Dtest='SqlIde*'
cd ../dts-platform-webapp && pnpm tsc --noEmit && pnpm vitest run src/components/sql-ide
```

**Expected:** 21 + 1 = 22 backend IT PASS, frontend 61/61 PASS, tsc clean.

### - [ ] Step 5.7: Commit

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/audit/SqlIdeAuditActions.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeAuditController.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlExecutionService.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeAuditControllerIT.java \
        source/dts-platform-webapp/src/components/sql-ide/api/sqlIdeExecution.ts \
        source/dts-platform-webapp/src/components/sql-ide/result/ResultGrid.tsx
git commit -m "$(cat <<'EOF'
feat(F4/T20): full audit coverage (13 actions) incl. copy via POST

Sprint-11 F4 T20: SqlIdeAuditActions enumerates the 13 plan-required
audit types. Submit/cancel/complete now record both raw and rewritten
SQL hashes (avoids 1MB SQL audit bloat). New /api/sql/v2/audit/copy
endpoint accepts the frontend's clipboard-copy event with cell count.
ResultGrid posts to it after successful copy. Page/export/catalog
audit calls were already in place from T16/T19/F3.
EOF
)"
```

---

## Final Verification

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform
./mvnw test -Dtest='SqlIde*' 2>&1 | grep -E "Tests run:" | tail -10
cd ../dts-platform-webapp
pnpm tsc --noEmit
pnpm vitest run src/components/sql-ide 2>&1 | tail -6
```

**Expected:** Backend 22+ IT PASS; frontend 61/61 PASS; tsc clean.

---

## Notes for the Executing Engineer

1. **Project conventions**
   - Java 禁用 `Optional.get()` → `Optional.orElseThrow()`
   - Liquibase changelog naming: `YYYYMMDD_NN_desc.xml`
   - Spring `@Transactional(readOnly=true)` at service class level for read-only services
   - JPA entities extend `AbstractAuditingEntity<UUID>` when audit fields needed (T16's QueryExecutionChunk skips this — it's a pure data table, not user-edited)

2. **Cache config**
   - Hazelcast is the cache backend; new caches registered via `MapConfig` in `CacheConfiguration` (see T11 fix for pattern)

3. **DO NOT TOUCH**
   - `/api/sql/*` legacy endpoints (preview path) — they back the old QueryWorkbenchPage which still works behind feature flag
   - F1/F2/F3 components — F4 only adds new files + extends TabState

4. **Subagent model**
   - Implementer: sonnet
   - Spec reviewer: sonnet
   - Code quality reviewer: **opus**

5. **Possible deviations**
   - `ResultSetRepository.findByExecutionId` may need adding (Step 1.6 note)
   - `apiClient.delete<void>` may need `request({method:"DELETE"})` if delete signature differs (F2 already handled this, follow same pattern)
   - Audit method signature on `AuditService.audit(...)` may differ — adapt to actual

6. **F5/F6 dependencies left intact**
   - `SQL_TEMP_VIEW_CREATE` / `SQL_SUBQUERY_EXECUTE` / `SQL_PLAN_VIEW` constants defined in T20 but only used in F5
   - ResultGrid has no Chart/Pivot tabs yet — F5 will add them as sibling tabs in the BottomPanel
