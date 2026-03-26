# Excel/CSV 导入重构计划：从大屏数据源 → 平台级数据导入

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Excel/CSV 导入从大屏设计器搬到 analytics "数据" 菜单，上传后自动在默认数仓 `biadmin` schema 下建表，数据变为可查询的 PostgreSQL 表，大屏通过现有 card 数据源访问。

**Architecture:** DatabaseNewPage 增加"其他数据源"Tab，用户上传 Excel/CSV 后前端解析 + 字段编辑，POST 到后端新端点，后端通过 `ExternalDatabaseDataSourceRegistry` 获取数仓 JDBC 连接，在 `biadmin` schema 下 CREATE TABLE + batch INSERT，然后调用 `MetadataSyncService` 同步元数据。上传表以 `upload_` 前缀标记为临时导入表。

**Tech Stack:** SheetJS (xlsx)、PapaParse（已安装）、JDBC DDL、MetadataSyncService、React

---

## 文件结构

### 回退/删除

| 文件 | 动作 |
|------|------|
| `screens/components/DatasetPicker.tsx` | 删除 |
| `screens/components/UploadedDataEditor.tsx` | 移动到 `components/UploadedDataEditor.tsx`（通用位置） |
| `PropertyPanel.tsx` | 回退 uploaded 相关代码 |
| `useCardDataSource.ts` | 回退 uploaded 分支 |
| `types.ts` | 移除 `'uploaded'` 类型和 `uploadedConfig` |
| `analyticsApi.ts` | 移除 screenDataset 方法，改为 uploadTable 方法 |
| `ScreenDatasetResource.java` | 删除 |
| `ScreenDatasetService.java` | 删除 |
| `AnalyticsScreenDataset.java` | 删除 |
| `AnalyticsScreenDatasetRepository.java` | 删除 |
| `0036_screen_uploaded_dataset.xml` | 新增 DROP TABLE changeset |

### 新建/修改

| 文件 | 动作 |
|------|------|
| `DatabaseNewPage.tsx` | 改为双 Tab：平台数据源 / 其他数据源(Excel/CSV) |
| `components/UploadedDataEditor.tsx` | 从 screens/ 移过来，调整 API 调用和 onBind 改为 onComplete |
| `analyticsApi.ts` | 新增 `uploadTable(dbId, body)` 方法 |
| `DatabaseResource.java` | 新增 `POST /api/database/{dbId}/upload-table` 端点 |
| `DatabaseUploadTableService.java` | 新建：JDBC DDL 建表 + INSERT + 调用 sync |
| `i18n.ts` | 新增国际化 key |

---

## Task 1: 后端 — DatabaseUploadTableService + REST 端点

**Files:**
- Create: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/DatabaseUploadTableService.java`
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/DatabaseResource.java`

- [ ] **Step 1: 创建 DatabaseUploadTableService**

```java
package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUploadTableService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUploadTableService.class);
    private static final String UPLOAD_SCHEMA = "biadmin";
    private static final String TABLE_PREFIX = "upload_";

    private final ExternalDatabaseDataSourceRegistry dataSourceRegistry;
    private final MetadataSyncService metadataSyncService;
    private final ObjectMapper objectMapper;

    public DatabaseUploadTableService(
            ExternalDatabaseDataSourceRegistry dataSourceRegistry,
            MetadataSyncService metadataSyncService,
            ObjectMapper objectMapper) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.metadataSyncService = metadataSyncService;
        this.objectMapper = objectMapper;
    }

    /**
     * Upload parsed data as a new table in biadmin schema.
     * @return the created table name (fully qualified)
     */
    public String uploadTable(long databaseId, String tableName,
            List<ColumnDef> columns, JsonNode rows) throws Exception {

        String safeTableName = sanitizeTableName(tableName);
        String qualifiedName = UPLOAD_SCHEMA + "." + quoteId(safeTableName);

        DataSource ds = dataSourceRegistry.get(databaseId);
        try (Connection conn = ds.getConnection()) {
            // 1. Ensure schema exists
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS " + UPLOAD_SCHEMA);
            }

            // 2. Drop if exists (one-time upload, replace semantics)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + qualifiedName);
            }

            // 3. CREATE TABLE
            String ddl = buildCreateTableDDL(qualifiedName, columns);
            log.info("Creating upload table: {}", ddl);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(ddl);
            }

            // 4. Batch INSERT
            if (rows.isArray() && rows.size() > 0) {
                String insertSql = buildInsertSQL(qualifiedName, columns);
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    int batch = 0;
                    for (JsonNode row : rows) {
                        for (int i = 0; i < columns.size(); i++) {
                            Object value = row.isArray() && i < row.size()
                                    ? extractValue(row.get(i), columns.get(i).type)
                                    : null;
                            ps.setObject(i + 1, value);
                        }
                        ps.addBatch();
                        if (++batch % 1000 == 0) {
                            ps.executeBatch();
                        }
                    }
                    if (batch % 1000 != 0) {
                        ps.executeBatch();
                    }
                }
                log.info("Inserted {} rows into {}", rows.size(), qualifiedName);
            }

            // 5. Add table comment to mark as upload
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("COMMENT ON TABLE " + qualifiedName
                        + " IS '临时导入表 (Excel/CSV upload)'");
            }
        }

        // 6. Sync metadata so table appears in analytics
        metadataSyncService.syncDatabaseSchema(databaseId);

        return safeTableName;
    }

    private String sanitizeTableName(String name) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String safe = name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fff]", "_")
                .replaceAll("_+", "_")
                .toLowerCase();
        if (safe.length() > 40) safe = safe.substring(0, 40);
        return TABLE_PREFIX + date + "_" + safe;
    }

    private String quoteId(String id) {
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }

    private String buildCreateTableDDL(String qualifiedName, List<ColumnDef> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(qualifiedName).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            ColumnDef col = columns.get(i);
            sb.append("  ").append(quoteId(col.name)).append(" ").append(mapType(col.type));
            if (i < columns.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(")");
        return sb.toString();
    }

    private String mapType(String type) {
        return switch (type) {
            case "number" -> "DOUBLE PRECISION";
            case "date" -> "DATE";
            case "boolean" -> "BOOLEAN";
            default -> "TEXT";
        };
    }

    private String buildInsertSQL(String qualifiedName, List<ColumnDef> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(qualifiedName).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(quoteId(columns.get(i).name));
            if (i < columns.size() - 1) sb.append(", ");
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            sb.append("?");
            if (i < columns.size() - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    private Object extractValue(JsonNode node, String type) {
        if (node == null || node.isNull()) return null;
        String text = node.asText();
        if (text.isEmpty()) return null;
        try {
            return switch (type) {
                case "number" -> node.isNumber() ? node.doubleValue() : Double.parseDouble(text);
                case "boolean" -> node.isBoolean() ? node.booleanValue()
                        : "true".equalsIgnoreCase(text) || "是".equals(text) || "1".equals(text);
                case "date" -> Date.valueOf(text.replace("/", "-"));
                default -> text;
            };
        } catch (Exception e) {
            return text; // fallback to text
        }
    }

    public record ColumnDef(String name, String displayName, String type) {}
}
```

- [ ] **Step 2: 在 DatabaseResource 中添加 upload-table 端点**

在 `DatabaseResource.java` 中添加新端点（参考现有的 MetabaseAuth 和 sessionService 模式）：

```java
@PostMapping("/{dbId}/upload-table")
@Transactional
public ResponseEntity<?> uploadTable(@PathVariable long dbId,
        @RequestBody JsonNode body, HttpServletRequest request) {
    // Auth check (same pattern as existing endpoints)
    Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
    if (user.isEmpty()) {
        return ResponseEntity.status(401).body("Unauthenticated");
    }

    try {
        String tableName = body.path("tableName").asText("imported_data");
        JsonNode columnsMeta = body.path("columns");
        JsonNode rows = body.path("rows");

        List<DatabaseUploadTableService.ColumnDef> columns = new ArrayList<>();
        if (columnsMeta.isArray()) {
            for (JsonNode col : columnsMeta) {
                columns.add(new DatabaseUploadTableService.ColumnDef(
                        col.path("name").asText(),
                        col.path("displayName").asText(),
                        col.path("type").asText("text")));
            }
        }

        String createdTable = uploadTableService.uploadTable(dbId, tableName, columns, rows);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("tableName", createdTable);
        result.put("schema", "biadmin");
        result.put("rowCount", rows.isArray() ? rows.size() : 0);
        return ResponseEntity.ok(result);
    } catch (Exception e) {
        log.error("Failed to upload table", e);
        ObjectNode err = objectMapper.createObjectNode();
        err.put("error", e.getMessage());
        return ResponseEntity.status(500).body(err);
    }
}
```

需要注入 `DatabaseUploadTableService` 到 `DatabaseResource` 的构造函数中。

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/DatabaseUploadTableService.java \
      source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/DatabaseResource.java
git commit -m "feat(data): add upload-table endpoint for Excel/CSV import to biadmin schema"
```

---

## Task 2: 后端 — 清理旧的 screen dataset 代码

**Files:**
- Delete: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenDatasetResource.java`
- Delete: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenDatasetService.java`
- Delete: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsScreenDataset.java`
- Delete: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsScreenDatasetRepository.java`
- Modify: `source/dts-analytics/src/main/resources/config/liquibase/changelog/0036_screen_uploaded_dataset.xml` — 新增 DROP TABLE changeset

- [ ] **Step 1: 在 Liquibase changelog 中添加 DROP TABLE**

在 `0036_screen_uploaded_dataset.xml` 末尾 `</databaseChangeLog>` 前追加：
```xml
    <changeSet id="0036-drop-screen-uploaded-dataset" author="billy">
        <dropTable tableName="screen_uploaded_dataset"/>
    </changeSet>
```

- [ ] **Step 2: 删除 Java 文件**

删除以下 4 个文件：
- `ScreenDatasetResource.java`
- `ScreenDatasetService.java`
- `AnalyticsScreenDataset.java`
- `AnalyticsScreenDatasetRepository.java`

- [ ] **Step 3: Commit**

```bash
git add -A source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenDatasetResource.java \
      source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenDatasetService.java \
      source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsScreenDataset.java \
      source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsScreenDatasetRepository.java \
      source/dts-analytics/src/main/resources/config/liquibase/changelog/0036_screen_uploaded_dataset.xml
git commit -m "refactor(data): remove screen dataset encrypted storage (replaced by upload-table)"
```

---

## Task 3: 前端 — 移动 UploadedDataEditor + 调整 API

**Files:**
- Move: `screens/components/UploadedDataEditor.tsx` → `components/UploadedDataEditor.tsx`
- Delete: `screens/components/DatasetPicker.tsx`
- Modify: `api/analyticsApi.ts` — 移除 screenDataset 方法，新增 uploadTable

- [ ] **Step 1: 移动 UploadedDataEditor**

将 `source/dts-analytics-webapp/modern/src/pages/screens/components/UploadedDataEditor.tsx`
移到 `source/dts-analytics-webapp/modern/src/components/UploadedDataEditor.tsx`

修改组件：
1. `onBind` prop 改为 `onComplete: (result: { tableName: string; schema: string; rowCount: number }) => void`
2. 上传调用从 `analyticsApi.createScreenDataset()` 改为 `analyticsApi.uploadTable(dbId, body)`
3. 新增 prop `databaseId: number` — 目标数据库 ID
4. 修改 import 路径：`analyticsApi` 从 `'../api/analyticsApi'` 导入

- [ ] **Step 2: 删除 DatasetPicker**

删除 `source/dts-analytics-webapp/modern/src/pages/screens/components/DatasetPicker.tsx`

- [ ] **Step 3: 更新 analyticsApi.ts**

移除：`createScreenDataset`, `listScreenDatasets`, `getScreenDatasetData`, `deleteScreenDataset`

新增：
```typescript
    uploadTable: (dbId: number | string, body: {
        tableName: string;
        columns: Array<{ name: string; displayName: string; type: string }>;
        rows: unknown[][];
    }) => sendJson<{ tableName: string; schema: string; rowCount: number }>(
        `/analytics/api/database/${dbId}/upload-table`, body),
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(data): move UploadedDataEditor to common components, update API"
```

---

## Task 4: 前端 — 回退大屏设计器中的 uploaded 集成

**Files:**
- Modify: `types.ts` — 移除 `'uploaded'` 和 `uploadedConfig`
- Modify: `useCardDataSource.ts` — 移除 uploaded 分支
- Modify: `PropertyPanel.tsx` — 移除 uploaded 下拉选项、setType case、编辑器渲染

- [ ] **Step 1: types.ts 回退**

从 `DataSourceType` 移除 `'uploaded'`，从 `QuerySourceType` 移除 `'uploaded'`，删除 `uploadedConfig` 字段。

- [ ] **Step 2: useCardDataSource.ts 回退**

移除 `'uploaded'` 相关的 resolveSourceType case、cache key 分支、fetch 分支。移除 `analyticsApi` import（如果只是为 uploaded 加的）。

- [ ] **Step 3: PropertyPanel.tsx 回退**

移除 `<option value="uploaded">文件上传</option>`、`setType` 中的 uploaded case、`dsType === 'uploaded'` 渲染块、UploadedDataEditor/DatasetPicker 的 import。

- [ ] **Step 4: 验证编译**

```bash
cd source/dts-analytics-webapp/modern && npx tsc --noEmit
```

- [ ] **Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/types.ts \
      source/dts-analytics-webapp/modern/src/pages/screens/hooks/useCardDataSource.ts \
      source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx
git commit -m "refactor(screen): remove uploaded data source type from screen designer"
```

---

## Task 5: 前端 — DatabaseNewPage 双 Tab 改造

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/DatabaseNewPage.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/i18n.ts` (新增 key)

- [ ] **Step 1: 添加 i18n key**

在 i18n.ts 的 zh 和 en 配置中添加：
```typescript
// zh
"data.tabPlatform": "平台数据源",
"data.tabOther": "其他数据源",
"data.uploadTitle": "导入 Excel / CSV",
"data.uploadDesc": "上传文件后自动建表，可用于临时分析和大屏展示",
"data.uploadSuccess": "导入成功，正在跳转...",
"data.selectDatabase": "选择目标数据库",

// en
"data.tabPlatform": "Platform Sources",
"data.tabOther": "Other Sources",
"data.uploadTitle": "Import Excel / CSV",
"data.uploadDesc": "Upload a file to create a table for analysis and dashboards",
"data.uploadSuccess": "Import successful, redirecting...",
"data.selectDatabase": "Select target database",
```

- [ ] **Step 2: 改造 DatabaseNewPage**

1. 添加 Tab 切换状态：`const [activeTab, setActiveTab] = useState<'platform' | 'other'>('platform')`
2. 顶部渲染 Tab 切换按钮
3. `activeTab === 'platform'` 时显示现有的平台数据源列表（保持不变）
4. `activeTab === 'other'` 时显示：
   - 目标数据库选择下拉（从 `analyticsApi.listDatabases()` 获取列表）
   - `<UploadedDataEditor databaseId={selectedDbId} onComplete={handleUploadComplete} />`
5. `handleUploadComplete` 回调：显示成功消息，然后导航到 `/data/${dbId}` 查看新表

- [ ] **Step 3: 验证编译**

```bash
cd source/dts-analytics-webapp/modern && npx tsc --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/DatabaseNewPage.tsx \
      source/dts-analytics-webapp/modern/src/i18n.ts
git commit -m "feat(data): add Excel/CSV import tab in DatabaseNewPage"
```

---

## Task 6: 端到端验证

- [ ] **Step 1: 重启后端容器**
- [ ] **Step 2: 验证完整流程**

1. 打开 analytics → 数据 → 添加数据
2. 切换到"其他数据源"Tab
3. 选择目标数据库
4. 上传 Excel 文件 → 字段编辑 → 确认导入
5. 跳转到数据库详情页 → 确认 biadmin schema 下出现 `upload_yyyyMMdd_xxx` 表
6. 基于该表创建 Card
7. 在大屏设计器中使用该 Card 作为数据源

- [ ] **Step 3: 验证旧代码清理无残留**

确认 `screen_uploaded_dataset` 表已 DROP，ScreenDatasetResource 不再响应请求。

---

## 注意事项

### biadmin schema
- 后端建表前先 `CREATE SCHEMA IF NOT EXISTS biadmin`
- 所有上传表以 `upload_` 前缀区分

### 临时表标记
- MetadataSyncService 同步后，上传表在 analytics_table 中有记录
- 通过表名前缀 `upload_` 或 COMMENT 区分临时导入表
- 可在 DatabaseDetailPage 中给 upload_ 表加"临时导入"Badge

### 安全
- upload-table 端点需要认证（MetabaseAuth）
- DDL 在服务端执行，用户无法注入 SQL（列名通过 quoteId 转义）
- 行数据通过 PreparedStatement 参数化插入
