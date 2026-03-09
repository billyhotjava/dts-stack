# 三级数据回退机制 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现三级数据回退机制（TRUNCATE / DROP+重建 / 全链路回退），支持任务级和数据源级粒度，覆盖 Excel/CSV 导入场景。

**Architecture:** dts-ingestion 负责物理表操作（TRUNCATE/DROP）和核心回退编排；dts-platform 负责代理 API + 级联清理 SQL 模型/dbt 文件/资产数据集；前端在采集详情、数据源管理、建模三个页面提供入口。所有回退操作先做 dryRun 影响分析再执行，确认策略可插拔。

**Tech Stack:** Spring Boot 3.4.5 / Java 21 / PostgreSQL JDBC / Liquibase / React + antd + Vite

**Sprint:** `worklog/v2.2.1/sprint-3/`
**Design:** `docs/plans/2026-03-09-data-rollback-design.md`

---

## 关键路径与文件索引

### dts-ingestion 核心路径
- 包: `com.yuzhi.dts.ingestion.service.etl`
- 现有可复用:
  - `TargetTableProvisioner` — 已有 `dropTable()`, `buildConnectionInfo()` 等方法
  - `JdbcMetadataService` — 已有 `openConnection()`, `listTables()`, `getTableColumns()`
  - `AddaxJobService.listWriterTablesFromJob()` — 从 Addax job 文件提取目标表
  - `IngestionTaskService.delete()` — 已有软删除 + 级联清理（执行记录、checkpoint、changeLog、Job、DAG）
  - `FileUploadService` — 上传文件路径为 `{jobDir}/uploads/{storedName}`
- Liquibase: `source/dts-ingestion/src/main/resources/config/liquibase/`

### dts-platform 核心路径
- `IngestionServiceClient` — 代理调用 dts-ingestion REST
- `IngestionTaskProxyResource` — 采集代理 API
- `ModelingSqlModelService` — SQL 模型 CRUD + dbt 文件管理
- `InfraOdsTableMappingRepository` — ODS 映射 `findByConnectionIdOrderByCreatedDateDesc()`
- `CatalogResource.deleteDataset()` — 资产数据集删除

### 前端核心路径
- `source/dts-platform-webapp/src/api/platformApi.ts`
- `source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx`
- `source/dts-platform-webapp/src/pages/foundation/DataSourcesPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`

---

## Task 1: RB-001 — Liquibase: rollback_audit_log 表

**Files:**
- Create: `source/dts-ingestion/src/main/resources/config/liquibase/changelog/20260309_02_rollback_audit_log.xml`
- Modify: `source/dts-ingestion/src/main/resources/config/liquibase/master.xml`

**Step 1: 创建 changelog**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="20260309-02-01" author="system">
        <createTable tableName="rollback_audit_log">
            <column name="id" type="bigint" autoIncrement="true">
                <constraints primaryKey="true"/>
            </column>
            <column name="operator" type="varchar(128)"/>
            <column name="level" type="int">
                <constraints nullable="false"/>
            </column>
            <column name="scope" type="varchar(32)">
                <constraints nullable="false"/>
            </column>
            <column name="task_id" type="bigint"/>
            <column name="data_source_id" type="uuid"/>
            <column name="request_json" type="text"/>
            <column name="impact_json" type="text"/>
            <column name="result_json" type="text"/>
            <column name="status" type="varchar(32)" defaultValue="SUCCESS"/>
            <column name="error_message" type="text"/>
            <column name="created_at" type="timestamp" defaultValueComputed="now()">
                <constraints nullable="false"/>
            </column>
        </createTable>
        <createIndex tableName="rollback_audit_log" indexName="idx_rollback_audit_task">
            <column name="task_id"/>
        </createIndex>
        <createIndex tableName="rollback_audit_log" indexName="idx_rollback_audit_ds">
            <column name="data_source_id"/>
        </createIndex>
        <createIndex tableName="rollback_audit_log" indexName="idx_rollback_audit_created">
            <column name="created_at"/>
        </createIndex>
    </changeSet>

</databaseChangeLog>
```

**Step 2: 注册到 master.xml**

在 `master.xml` 的 `20260309_01` include 之后添加:
```xml
<include file="config/liquibase/changelog/20260309_02_rollback_audit_log.xml"/>
```

**Step 3: Commit**
```
feat(ingestion): add rollback_audit_log table schema
```

---

## Task 2: RB-002 — TableOperationService

**Files:**
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/TableOperationService.java`

**实现:** 封装物理表 TRUNCATE/DROP 操作，复用 `TargetTableProvisioner.buildConnectionInfo()` 和 `JdbcMetadataService.openConnection()` 模式。

```java
package com.yuzhi.dts.ingestion.service.etl;

@Service
public class TableOperationService {

    private final JdbcMetadataService metadataService;
    private final AddaxJobService addaxJobService;
    private final ObjectMapper objectMapper;

    // 根据采集任务的 destinationConfig 构建 JDBC 连接信息
    public JdbcMetadataService.JdbcConnectionInfo resolveTargetConnectionInfo(IngestionTask task);

    // TRUNCATE 指定表
    public void truncateTable(JdbcMetadataService.JdbcConnectionInfo connInfo, String schema, String table);

    // DROP 指定表
    public void dropTable(JdbcMetadataService.JdbcConnectionInfo connInfo, String schema, String table);

    // 查询任务关联的所有目标表（从 Addax job 文件 + tableMapping 推导）
    public List<String> resolveTargetTables(IngestionTask task);

    // 检查表是否存在
    public boolean tableExists(JdbcMetadataService.JdbcConnectionInfo connInfo, String schema, String table);
}
```

关键实现细节:
- `resolveTargetConnectionInfo` 从 `task.getDestinationConfig()` 提取 jdbcUrl/username/password，复用 `TargetTableProvisioner.buildConnectionInfo()` 的解析逻辑（解析 JSON config Map）
- `truncateTable` 执行 `TRUNCATE TABLE schema.table`，设置 30s 超时
- `dropTable` 执行 `DROP TABLE IF EXISTS schema.table CASCADE`
- `resolveTargetTables` 优先从 `addaxJobService.listWriterTablesFromJob()` 获取，fallback 到 `task.getTableMapping()` 解析

**Step: Commit**
```
feat(ingestion): add TableOperationService for TRUNCATE/DROP operations
```

---

## Task 3: RB-003 — ConfirmationPolicy 接口

**Files:**
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/ConfirmationPolicy.java`
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/ModalConfirmationPolicy.java`
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/RollbackLevel.java`

```java
// RollbackLevel.java
package com.yuzhi.dts.ingestion.service.etl.rollback;

public enum RollbackLevel {
    TRUNCATE_DATA(1),    // Level 1
    REBUILD_SCHEMA(2),   // Level 2
    FULL_CASCADE(3);     // Level 3

    private final int code;
    RollbackLevel(int code) { this.code = code; }
    public int code() { return code; }

    public static RollbackLevel fromCode(int code) {
        for (RollbackLevel l : values()) {
            if (l.code == code) return l;
        }
        throw new IllegalArgumentException("Unknown rollback level: " + code);
    }
}
```

```java
// ConfirmationPolicy.java
package com.yuzhi.dts.ingestion.service.etl.rollback;

public interface ConfirmationPolicy {
    /** 该级别是否需要前端确认（当前全部 true） */
    boolean requiresConfirmation(RollbackLevel level);
    /** 确认类型：MODAL / INPUT_NAME / ADMIN_APPROVE */
    String confirmationType(RollbackLevel level);
}
```

```java
// ModalConfirmationPolicy.java — 当前默认实现
package com.yuzhi.dts.ingestion.service.etl.rollback;

@Component
public class ModalConfirmationPolicy implements ConfirmationPolicy {
    @Override
    public boolean requiresConfirmation(RollbackLevel level) { return true; }
    @Override
    public String confirmationType(RollbackLevel level) { return "MODAL"; }
}
```

**Step: Commit**
```
feat(ingestion): add ConfirmationPolicy interface with pluggable modal default
```

---

## Task 4: RB-004 — RollbackAuditService

**Files:**
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/domain/RollbackAuditLog.java`
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/repository/RollbackAuditLogRepository.java`
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/RollbackAuditService.java`

JPA 实体映射 `rollback_audit_log` 表。Service 提供:

```java
@Service
public class RollbackAuditService {
    // 记录回退操作
    public RollbackAuditLog record(String operator, RollbackLevel level, String scope,
                                    Long taskId, UUID dataSourceId,
                                    String requestJson, String impactJson,
                                    String resultJson, String status, String errorMessage);
    // 查询任务的回退记录
    public List<RollbackAuditLog> findByTaskId(Long taskId);
    // 查询数据源的回退记录
    public List<RollbackAuditLog> findByDataSourceId(UUID dataSourceId);
}
```

**Step: Commit**
```
feat(ingestion): add RollbackAuditService for audit logging
```

---

## Task 5: RB-005 — DataRollbackService 影响分析

**Files:**
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/DataRollbackService.java`
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/RollbackRequest.java`
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/RollbackImpact.java`

```java
// RollbackRequest.java
public record RollbackRequest(
    int level,                    // 1, 2, 3
    String scope,                 // "task" | "datasource"
    Long taskId,                  // scope=task 时必填
    UUID dataSourceId,            // scope=datasource 时必填
    List<String> tables,          // Level 1 可指定表，为空则自动推导
    boolean rebuildDbt,           // Level 2 是否 dbt run --full-refresh
    boolean dryRun                // true=只做分析不执行
) {}
```

```java
// RollbackImpact.java
public record RollbackImpact(
    int level,
    String scope,
    Long taskId,
    UUID dataSourceId,
    List<String> affectedTables,         // 将被 TRUNCATE 或 DROP 的表
    List<String> affectedOdsMappings,    // 将被删除的 ODS 映射
    List<String> affectedModels,         // 关联的 SQL 模型名
    List<String> affectedDbtFiles,       // 关联的 dbt 文件路径
    int affectedDatasets,                // 关联的资产数据集数
    List<String> uploadFiles,            // Excel/CSV 上传文件
    int executionRecords,                // 执行记录数
    String confirmationType,             // MODAL | INPUT_NAME | ...
    List<Long> cascadeTaskIds            // scope=datasource 时所有子任务 ID
) {}
```

```java
// DataRollbackService.java
@Service
public class DataRollbackService {

    private final IngestionTaskRepository taskRepository;
    private final IngestionExecutionRepository executionRepository;
    private final TableOperationService tableOpService;
    private final AddaxJobService addaxJobService;
    private final ConfirmationPolicy confirmationPolicy;
    private final RollbackAuditService auditService;

    /**
     * 影响分析 — 不执行任何破坏性操作，仅返回将被影响的资源清单
     */
    public RollbackImpact analyze(RollbackRequest request);

    /**
     * 执行回退 — 根据 level 分发到对应处理方法
     */
    public RollbackResult execute(RollbackRequest request, String operator);
}
```

`analyze()` 实现逻辑:
1. 根据 scope 定位 task(s): 单任务或 `taskRepository.findBySourceDataSourceId()`
2. 对每个 task，调用 `tableOpService.resolveTargetTables()` 获取物理表
3. 检查 `task.getSourceType()` 是否为 excel/csv，收集上传文件路径
4. 统计执行记录数 `executionRepository.countByTaskIdAndStatusesIgnoreCase()`
5. 从 `confirmationPolicy.confirmationType(level)` 获取确认类型
6. 构造 `RollbackImpact` 返回

**Step: Commit**
```
feat(ingestion): add DataRollbackService with impact analysis
```

---

## Task 6: RB-006 — Level 1 执行 (TRUNCATE)

**Files:**
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/DataRollbackService.java`

添加 `executeLevel1()` 方法:

```java
private RollbackResult executeLevel1(RollbackRequest request, List<IngestionTask> tasks, String operator) {
    List<String> truncated = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    for (IngestionTask task : tasks) {
        var connInfo = tableOpService.resolveTargetConnectionInfo(task);
        List<String> tables = request.tables() != null && !request.tables().isEmpty()
            ? request.tables()
            : tableOpService.resolveTargetTables(task);
        for (String table : tables) {
            try {
                // 解析 schema.table
                String[] parts = table.contains(".") ? table.split("\\.", 2) : new String[]{"public", table};
                tableOpService.truncateTable(connInfo, parts[0], parts[1]);
                truncated.add(table);
            } catch (Exception ex) {
                failed.add(table + ": " + ex.getMessage());
            }
        }
    }
    // 写审计
    auditService.record(operator, RollbackLevel.TRUNCATE_DATA, request.scope(), ...);
    return new RollbackResult(truncated, failed);
}
```

**Step: Commit**
```
feat(ingestion): implement Level 1 TRUNCATE execution
```

---

## Task 7: RB-007 — Level 2 执行 (DROP + 重跑)

**Files:**
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/DataRollbackService.java`

添加 `executeLevel2()` 方法:

```java
private RollbackResult executeLevel2(RollbackRequest request, List<IngestionTask> tasks, String operator) {
    List<String> dropped = new ArrayList<>();
    List<String> retriggered = new ArrayList<>();
    for (IngestionTask task : tasks) {
        var connInfo = tableOpService.resolveTargetConnectionInfo(task);
        List<String> tables = tableOpService.resolveTargetTables(task);
        // 1. DROP 所有目标表
        for (String table : tables) {
            String[] parts = parseSchemaTable(table);
            tableOpService.dropTable(connInfo, parts[0], parts[1]);
            dropped.add(table);
        }
        // 2. 将 syncMode 临时切为 full_refresh（确保下次 ensureTargetTables 会重建）
        String prevSyncMode = task.getSyncMode();
        task.setSyncMode("full_refresh");
        taskRepository.save(task);
        retriggered.add("task:" + task.getId());
        // 注意: 不自动重跑，只做表 DROP + syncMode 切换
        // 用户需要手动修正列映射后重跑（Excel 场景需要重新上传）
    }
    auditService.record(...);
    return new RollbackResult(dropped, retriggered);
}
```

**Step: Commit**
```
feat(ingestion): implement Level 2 DROP + syncMode switch
```

---

## Task 8: RB-008 — Level 2 dbt --full-refresh 扩展

**Files:**
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/DataRollbackService.java`

当 `request.rebuildDbt() == true` 时，在 Level 2 结果中标记需要 dbt full-refresh。
实际的 dbt 触发由 dts-platform 侧（RollbackCascadeService）通过 Airflow 完成。

`RollbackResult` 增加 `dbtFullRefreshNeeded` 标志。

**Step: Commit**
```
feat(ingestion): add dbt full-refresh flag to Level 2 result
```

---

## Task 9: RB-009 — Level 3 任务级全链路回退

**Files:**
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/DataRollbackService.java`

```java
private RollbackResult executeLevel3Task(IngestionTask task, String operator) {
    Long taskId = task.getId();
    List<String> actions = new ArrayList<>();

    // 1. DROP 所有 ODS 物理表
    var connInfo = tableOpService.resolveTargetConnectionInfo(task);
    for (String table : tableOpService.resolveTargetTables(task)) {
        try {
            String[] parts = parseSchemaTable(table);
            tableOpService.dropTable(connInfo, parts[0], parts[1]);
            actions.add("DROPPED: " + table);
        } catch (Exception ex) {
            actions.add("DROP_FAILED: " + table + " - " + ex.getMessage());
        }
    }

    // 2. 清理上传文件（Excel/CSV 场景）
    if (isFileSourceType(task.getSourceType())) {
        cleanupUploadFiles(task, actions);
    }

    // 3. 复用现有 delete 逻辑清理: 执行记录、checkpoint、changeLog、Addax Job、DAG
    try { executionRepository.deleteByTaskId(taskId); } catch (Exception ex) { /* log */ }
    try { incrementalSyncService.clearCheckpointByTaskId(taskId); } catch (Exception ex) { /* log */ }
    try { changeLogService.deleteByTaskId(taskId); } catch (Exception ex) { /* log */ }
    try { addaxJobService.deleteJobIfExists(task.getAddaxJobPath()); } catch (Exception ex) { /* log */ }
    try { airflowDagService.deleteDagForTask(task); } catch (Exception ex) { /* log */ }

    // 4. 软删除任务
    task.setStatus("deleted");
    taskRepository.save(task);
    actions.add("TASK_DELETED: " + taskId);

    auditService.record(operator, RollbackLevel.FULL_CASCADE, "task", taskId, ...);
    return new RollbackResult(actions);
}
```

**注意:** ODS 映射、SQL 模型、dbt 文件、DWD/DWS/ADS 产出表、资产数据集的清理由 dts-platform 侧完成（Task 12-14）。
`RollbackResult` 需包含足够信息让 dts-platform 侧做级联（taskId、sourceDataSourceId、目标表列表）。

**Step: Commit**
```
feat(ingestion): implement Level 3 task-level full cascade rollback
```

---

## Task 10: RB-010 — Level 3 数据源级回退

**Files:**
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/DataRollbackService.java`

```java
private RollbackResult executeLevel3DataSource(RollbackRequest request, String operator) {
    UUID dsId = request.dataSourceId();
    List<IngestionTask> tasks = taskRepository.findBySourceDataSourceId(dsId);
    // 过滤已删除的任务
    tasks = tasks.stream()
        .filter(t -> !"deleted".equalsIgnoreCase(t.getStatus()))
        .toList();
    if (tasks.isEmpty()) {
        return RollbackResult.empty("该数据源下无活跃任务");
    }
    List<RollbackResult> subResults = new ArrayList<>();
    for (IngestionTask task : tasks) {
        subResults.add(executeLevel3Task(task, operator));
    }
    return RollbackResult.merge(subResults);
}
```

**Step: Commit**
```
feat(ingestion): implement Level 3 datasource-level batch rollback
```

---

## Task 11: RB-011 — REST API (dts-ingestion)

**Files:**
- Create: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/RollbackResource.java`

```java
@RestController
@RequestMapping("/api/ingestion/rollback")
public class RollbackResource {

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<RollbackImpact>> analyze(@RequestBody RollbackRequest request);

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<RollbackResult>> execute(@RequestBody RollbackRequest request);

    @GetMapping("/audit-log")
    public ResponseEntity<ApiResponse<List<RollbackAuditLog>>> getAuditLog(
        @RequestParam(required = false) Long taskId,
        @RequestParam(required = false) UUID dataSourceId);
}
```

**Step: Commit**
```
feat(ingestion): add RollbackResource REST endpoints
```

---

## Task 12: RB-012 — dts-platform 代理 API + RollbackProxyResource

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/RollbackProxyResource.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/ingestion/IngestionServiceClient.java`

`IngestionServiceClient` 增加三个方法:
```java
public <T> T rollbackAnalyze(Object request, Class<T> responseType);
public <T> T rollbackExecute(Object request, Class<T> responseType);
public <T> T getRollbackAuditLog(Map<String, String> params, Class<T> responseType);
```

`RollbackProxyResource` 代理 `/api/rollback/*`，在 execute 成功后触发级联清理。

**Step: Commit**
```
feat(platform): add RollbackProxyResource with IngestionServiceClient proxy
```

---

## Task 13: RB-013 + RB-014 — RollbackCascadeService (SQL 模型 + dbt 文件 + 产出表 + 资产)

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/RollbackCascadeService.java`

```java
@Service
public class RollbackCascadeService {

    private final ModelingSqlModelService sqlModelService;
    private final InfraOdsTableMappingRepository odsMappingRepo;
    private final DatasetRepository datasetRepo;
    private final DbtPreviewService dbtPreviewService; // 复用其 JDBC 能力 DROP dbt 产出表

    /**
     * Level 3 级联清理 — 在 ingestion 侧回退完成后调用
     * 1. 删除 ODS 映射 (infra_ods_table_mapping where connectionId matches)
     * 2. 找到关联 SQL 模型 → 删除定义 + dbt 文件
     * 3. DROP DWD/DWS/ADS 产出表
     * 4. 删除关联资产数据集
     */
    public CascadeResult cascadeForTask(Long taskId, UUID sourceDataSourceId, List<String> odsTables);

    /**
     * Level 2 dbt full-refresh — 触发 Airflow DAG
     */
    public void triggerDbtFullRefresh(String selector);
}
```

关键逻辑:
- 通过 `sourceDataSourceId` 找到 ODS 映射，再通过 ODS 映射的 `odsSchema.odsTable` 匹配 SQL 模型
- SQL 模型删除调用 `sqlModelService.delete(modelId, activeDeptHeader)`，它会同时删除 dbt 文件
- DWD/DWS/ADS 产出表通过 SQL 模型的 `schemaName + name` 推导，用 JDBC 直连 DROP
- 资产数据集通过 ODS 映射的 `datasetId` 关联删除

**Step: Commit**
```
feat(platform): add RollbackCascadeService for SQL model/dbt/dataset cleanup
```

---

## Task 14: RB-015 + RB-016 — Excel/CSV 特殊处理

**Files:**
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/DataRollbackService.java`
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/FileUploadService.java`

`FileUploadService` 增加方法:
```java
/** 清理指定任务的上传文件 */
public List<String> cleanupForTask(IngestionTask task);
```
从 task 的 sourceConfig 中提取文件路径 (`hostPath`)，删除文件。

`DataRollbackService` 在 Level 3 时调用 `fileUploadService.cleanupForTask()`。
Level 2 时不删文件（用户可能需要修改列映射后重新导入）。

`RollbackImpact` 的 `analyze()` 在 Excel/CSV 任务时，增加 `uploadFiles` 列表和提示信息:
- Level 2: "DROP 表后需重新上传文件并修正列映射"
- Level 3: "上传文件将被清理"

**Step: Commit**
```
feat(ingestion): handle Excel/CSV upload file cleanup in rollback
```

---

## Task 15: RB-017 — 前端 API 层

**Files:**
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`

```typescript
// Data rollback
export const analyzeRollback = (data: {
    level: number;
    scope: string;
    taskId?: number;
    dataSourceId?: string;
    tables?: string[];
    rebuildDbt?: boolean;
}) => api.post({ url: "/rollback/analyze", data: { ...data, dryRun: true } });

export const executeRollback = (data: {
    level: number;
    scope: string;
    taskId?: number;
    dataSourceId?: string;
    tables?: string[];
    rebuildDbt?: boolean;
}) => api.post({ url: "/rollback/execute", data: { ...data, dryRun: false } });

export const getRollbackAuditLog = (params: { taskId?: number; dataSourceId?: string }) =>
    api.get({ url: "/rollback/audit-log", params });
```

**Step: Commit**
```
feat(webapp): add rollback API functions
```

---

## Task 16: RB-018 — RollbackImpactModal 通用组件

**Files:**
- Create: `source/dts-platform-webapp/src/pages/components/RollbackImpactModal.tsx`

```tsx
/**
 * 通用回退影响分析弹窗
 * Props:
 *   open: boolean
 *   impact: RollbackImpact | null   — 从 analyzeRollback 返回
 *   loading: boolean
 *   executing: boolean
 *   onConfirm: () => void
 *   onCancel: () => void
 *
 * 内容展示:
 *   - 回退级别 Tag (Level 1/2/3)
 *   - 影响范围摘要 (scope: task/datasource)
 *   - 受影响的表列表 (Tag 标签)
 *   - 受影响的模型列表
 *   - 资产数据集数量
 *   - Excel/CSV 上传文件列表
 *   - 执行记录数
 *   - 确认按钮 (当前 confirmationType=MODAL 直接确认)
 *   - 预留: confirmationType=INPUT_NAME 时显示输入框
 */
```

使用 antd `Modal` + `Descriptions` + `Tag` + `Alert` 组合。
当 `confirmationType` 不是 `MODAL` 时，显示额外的 Input 确认框（预留，当前不激活）。

**Step: Commit**
```
feat(webapp): add RollbackImpactModal component
```

---

## Task 17: RB-019 ~ RB-021 — 采集任务详情页回退按钮

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx`

在任务详情页的操作区域添加回退下拉菜单:

```tsx
<Dropdown menu={{
    items: [
        { key: "level1", label: "清空数据 (Level 1)", icon: <DeleteOutlined /> },
        { key: "level2", label: "重建表结构 (Level 2)", icon: <ReloadOutlined /> },
        { type: "divider" },
        { key: "level3", label: "全链路回退 (Level 3)", icon: <WarningOutlined />, danger: true },
    ],
    onClick: handleRollbackMenuClick,
}}>
    <Button danger icon={<RollbackOutlined />}>数据回退 <DownOutlined /></Button>
</Dropdown>
```

点击后:
1. 调用 `analyzeRollback({ level, scope: "task", taskId })` 获取影响分析
2. 打开 `RollbackImpactModal` 展示
3. 用户确认后调用 `executeRollback()` 执行
4. 成功后刷新页面状态

Level 1 额外支持表多选（从 impact.affectedTables 中勾选）。
Level 2 额外显示 `rebuildDbt` 开关。

**Step: Commit**
```
feat(webapp): add rollback controls to TransformDetailPage
```

---

## Task 18: RB-022 — 数据源管理页面 Level 3 入口

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/foundation/DataSourcesPage.tsx`

在数据源列表的操作列（或详情页）添加 "全链路回退" 按钮:

```tsx
{
    key: "rollback",
    label: "全链路回退",
    danger: true,
    icon: <WarningOutlined />,
    onClick: () => handleDataSourceRollback(record.id),
}
```

点击后调用 `analyzeRollback({ level: 3, scope: "datasource", dataSourceId })`，
展示所有子任务的影响分析，确认后批量执行。

**Step: Commit**
```
feat(webapp): add datasource-level Level 3 rollback to DataSourcesPage
```

---

## Task 19: RB-023 + RB-024 — 建模页面回退操作

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`

在模型列表的右键菜单或操作栏添加:
- "清空产出表" → Level 1 TRUNCATE（根据模型的 schemaName + name 推导表名）
- "重建产出表" → Level 2 触发 `dbt run --full-refresh --select model:xxx`

这两个操作走独立的 API 路径（不经过 ingestion 侧），直接调用 dbt 相关 API。

**Step: Commit**
```
feat(webapp): add dbt output table rollback to SqlModelingPage
```

---

## Task 20: RB-025 — 回退审计日志面板

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx`

在采集任务详情页底部增加"回退记录" Tab:

```tsx
{
    key: "rollback-audit",
    label: "回退记录",
    children: <RollbackAuditPanel taskId={taskId} />,
}
```

`RollbackAuditPanel` 调用 `getRollbackAuditLog({ taskId })` 展示 antd Table:
- 时间、操作人、级别、范围、影响摘要、状态

**Step: Commit**
```
feat(webapp): add rollback audit log panel
```

---

## 执行顺序与依赖关系

```
Task 1 (Liquibase) ──┐
Task 2 (TableOp)  ───┤
Task 3 (Policy)   ───┼→ Task 5 (Analyze) → Task 6 (L1) → Task 7 (L2) → Task 8 (L2 dbt)
Task 4 (Audit)    ───┘                         ↓
                                          Task 9 (L3 task) → Task 10 (L3 ds) → Task 11 (REST API)
                                                                                       ↓
Task 14 (Excel) ─────────────────────────────────────────────────────────────→ Task 11
                                                                                       ↓
                                          Task 12 (Proxy) → Task 13 (Cascade)
                                                                    ↓
                                          Task 15 (FE API) → Task 16 (Modal) → Task 17 (Detail)
                                                                                  → Task 18 (DS)
                                                                                  → Task 19 (Modeling)
                                                                                  → Task 20 (Audit Panel)
```

批次一 (Task 1-4) 和批次二 (Task 5-10) 可内部并行。
前端 (Task 15-20) 依赖后端 API 就绪。
