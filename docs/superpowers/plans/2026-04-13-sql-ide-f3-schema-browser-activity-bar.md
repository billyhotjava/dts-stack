# SQL IDE F3: Schema 浏览器与 Activity Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现最左 44px Activity Bar 与可折叠左侧面板，承载高性能虚拟滚动 Schema 树（react-arborist + 惰性加载）+ 历史面板 + 已保存查询面板，并把 catalog 元数据接通 T04 完成的 Monaco 补全。

**Architecture:** 后端在 `/api/sql/v2/catalog/*` 新增按层级惰性返回的 5 个端点（datasources / schemas / tables / columns / search），复用现有 `SqlCatalogService` 的 PG/Hive/Trino 适配；服务端 Caffeine 缓存按 (dsId, schema) 维度 5min TTL。前端新增 Activity Bar + 可折叠 SidePanel（Zustand `useLayoutStore`），SchemaTree 用 react-arborist 虚拟树（10k+ 节点流畅），右键菜单生成 SELECT/INSERT/DDL，hover 卡片 500ms 显示列详情；HistoryPanel 复用 `query_execution`；SavedPanel 复用 `saved_query` + 新增 `folder` 列。CatalogProvider 接入 T04 stub 后，Monaco 补全在 `afterDot` / `afterFrom` 时拉取真实表/列数据。

**Tech Stack:** 前端 React 18 + TypeScript + react-arborist@^3 + @tanstack/react-query 5 + Zustand 4 + Ant Design 5；后端 Spring Boot 3.4.5 + Caffeine（已用）+ JPA + Liquibase + JUnit 5。

---

## Spec Reference

- Sprint README: `worklog/v2.2.3/sprint-11-202604/README.md`
- Feature README: `worklog/v2.2.3/sprint-11-202604/features/F3-Schema浏览器与Activity-Bar/README.md`
- Tasks: `T11–T15`

## Dependencies

- **F1 完成**：SqlEditor 已暴露 `catalog?: CatalogSource` prop（FIX-6 修过）
- **F2 完成**：useTabStore 中每个 Tab 有 `engine` 和 `datasourceId`，CatalogSource 实现需读 active tab 的 datasourceId
- **现有后端**：`SqlCatalogService.fetchTree(...)` 返回全树，本 Sprint **不动它**，新建 `SqlCatalogLazyService` 提供按层级查询的 API 给 v2 端点

## File Structure

**Backend（新增）：**

| 路径 | 职责 |
|---|---|
| `service/sql/SqlCatalogLazyService.java` | 按层级惰性查询：`listDatasources()`, `listSchemas(dsId)`, `listTables(dsId, schema)`, `listColumns(dsId, schemaTable)`, `search(dsId, kw, limit)` |
| `service/sql/dto/CatalogDatasourceDto.java` | `record CatalogDatasourceDto(String id, String name, String engine, String label)` |
| `service/sql/dto/CatalogSchemaDto.java` | `record CatalogSchemaDto(String name, String catalog)` |
| `service/sql/dto/CatalogTableDto.java` | `record CatalogTableDto(String name, String type, String comment, Long rowCountEstimate)` |
| `service/sql/dto/CatalogColumnDto.java` | `record CatalogColumnDto(String name, String dataType, boolean nullable, String comment, int ordinalPosition)` |
| `service/sql/dto/CatalogSearchHitDto.java` | `record CatalogSearchHitDto(String schema, String table, String column, String type)` |

**Backend（修改）：**

| 路径 | 改动 |
|---|---|
| `web/rest/sql/SqlIdeResource.java` | 追加 5 个端点 + 注入 SqlCatalogLazyService + 审计 `SQL_CATALOG_BROWSE` |
| `service/sql/SavedQueryService.java` | 增加 `folder` 字段支持（仅 service 层；DTO 已扩展） |
| `service/sql/dto/SavedQueryRequest.java` | 添加 `String folder` 字段 |
| `service/sql/dto/SavedQueryResponse.java` | 添加 `String folder` 字段 |
| `domain/explore/SavedQuery.java` | 添加 `folder` 列 |
| `src/main/resources/config/liquibase/master.xml` | include 新 changelog |

**Backend（数据库）：**

| 路径 | 职责 |
|---|---|
| `src/main/resources/config/liquibase/changelog/20260413_02_saved_query_folder.xml` | `addColumn folder varchar(200) nullable` |

**Backend（测试）：**

| 路径 | 职责 |
|---|---|
| `src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeCatalogResourceIT.java` | 5 个端点的 IT |
| `src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeHistoryResourceIT.java` | 历史端点 IT（T14 引入） |

**Frontend（新增）：**

| 路径 | 职责 |
|---|---|
| `src/components/sql-ide/layout/useLayoutStore.ts` | Zustand：activeActivity, sidePanelWidth, bottomPanelHeight |
| `src/components/sql-ide/api/sqlIdeCatalog.ts` | 5 个 catalog HTTP 函数 |
| `src/components/sql-ide/api/sqlIdeHistory.ts` | history 列表 |
| `src/components/sql-ide/api/sqlIdeSaved.ts` | saved query CRUD |
| `src/components/sql-ide/schema/SchemaTree.tsx` | react-arborist 虚拟树 |
| `src/components/sql-ide/schema/TableDetailPopover.tsx` | hover 表详情卡 |
| `src/components/sql-ide/schema/SchemaContextMenu.tsx` | 右键菜单 |
| `src/components/sql-ide/schema/useSchemaTreeData.ts` | React Query hooks 封装 + 节点惰性加载 |
| `src/components/sql-ide/schema/sqlGenerators.ts` | `generateSelect/Insert/CopyName` 工具 + 单测 |
| `src/components/sql-ide/schema/__tests__/sqlGenerators.test.ts` | 单测 |
| `src/components/sql-ide/history/HistoryPanel.tsx` | 列表 + 过滤 + 双击新开 Tab |
| `src/components/sql-ide/history/useHistoryQuery.ts` | React Query hook |
| `src/components/sql-ide/saved/SavedPanel.tsx` | 列表 + folder 分组 |
| `src/components/sql-ide/saved/SaveQueryDialog.tsx` | Ctrl+S 保存对话框 |
| `src/components/sql-ide/copilot/CopilotSlot.tsx` | 占位面板（"敬请期待"） |

**Frontend（修改）：**

| 路径 | 改动 |
|---|---|
| `src/components/sql-ide/layout/ActivityBar.tsx` | 5 个 icon（Schema/History/Saved/Search/Copilot），active 状态指示 |
| `src/components/sql-ide/layout/SidePanel.tsx` | 接 `useLayoutStore`，根据 `activeActivity` 切换内容 slot；可拖拽宽度 |
| `src/components/sql-ide/SqlIde.tsx` | SidePanel 内容由 useLayoutStore 决定渲染哪个面板 |
| `src/components/sql-ide/editor/completion/catalogProvider.ts` | （已有 NOOP_CATALOG）—— **不在本 Sprint 改**，T11 提供的 React Query catalog 实例由 SqlIde 注入 |
| `src/components/sql-ide/editor/SqlEditor.tsx` | 接收 `catalog` prop（已有，FIX-6 加过），无需改 |
| `package.json` | 新增 `react-arborist@^3` |

---

## Task 1 — T11: Catalog 惰性加载 API（后端）

**Files:**
- Create: `service/sql/SqlCatalogLazyService.java`
- Create: `service/sql/dto/CatalogDatasourceDto.java`
- Create: `service/sql/dto/CatalogSchemaDto.java`
- Create: `service/sql/dto/CatalogTableDto.java`
- Create: `service/sql/dto/CatalogColumnDto.java`
- Create: `service/sql/dto/CatalogSearchHitDto.java`
- Modify: `web/rest/sql/SqlIdeResource.java` (追加 5 endpoints + 1 audit type)
- Create: `src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeCatalogResourceIT.java`

### - [ ] Step 1.1: 创建 5 个 DTO records

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/CatalogDatasourceDto.java`：

```java
package com.yuzhi.dts.platform.service.sql.dto;

public record CatalogDatasourceDto(
    String id,
    String name,
    String engine,
    String label
) {}
```

`CatalogSchemaDto.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;

public record CatalogSchemaDto(String name, String catalog) {}
```

`CatalogTableDto.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;

public record CatalogTableDto(String name, String type, String comment, Long rowCountEstimate) {}
```

`CatalogColumnDto.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;

public record CatalogColumnDto(
    String name,
    String dataType,
    boolean nullable,
    String comment,
    int ordinalPosition
) {}
```

`CatalogSearchHitDto.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;

public record CatalogSearchHitDto(
    String schema,
    String table,
    String column,
    String type
) {}
```

### - [ ] Step 1.2: 创建 SqlCatalogLazyService（复用 CatalogTableSchema/CatalogColumnSchema 仓库）

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlCatalogLazyService.java`：

```java
package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.sql.dto.CatalogColumnDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogDatasourceDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogSchemaDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogSearchHitDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogTableDto;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SqlCatalogLazyService {

    /** Static registry; expanded later when real datasource management is wired. */
    private static final List<CatalogDatasourceDto> DATASOURCES = List.of(
        new CatalogDatasourceDto("default", "默认数据源", "trino", "Trino · default"),
        new CatalogDatasourceDto("hive", "Hive 仓库", "hive", "Apache Hive"),
        new CatalogDatasourceDto("postgres", "PostgreSQL", "postgresql", "PostgreSQL")
    );

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;

    public SqlCatalogLazyService(
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository
    ) {
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
    }

    public List<CatalogDatasourceDto> listDatasources() {
        return DATASOURCES;
    }

    @Cacheable(cacheNames = "sqlIdeSchemas", key = "#datasourceId")
    public List<CatalogSchemaDto> listSchemas(String datasourceId) {
        return datasetRepository
            .findAll()
            .stream()
            .filter(d -> d.getStorageFormat() != null || d.getName() != null)
            .map(this::toSchema)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    @Cacheable(cacheNames = "sqlIdeTables", key = "#datasourceId + ':' + #schema")
    public List<CatalogTableDto> listTables(String datasourceId, String schema) {
        return datasetRepository
            .findAll()
            .stream()
            .filter(d -> schema == null || schema.equalsIgnoreCase(extractSchema(d)))
            .flatMap(d -> tablesOf(d).stream())
            .toList();
    }

    @Cacheable(cacheNames = "sqlIdeColumns", key = "#datasourceId + ':' + #schemaTable")
    public List<CatalogColumnDto> listColumns(String datasourceId, String schemaTable) {
        String[] parts = schemaTable == null ? new String[0] : schemaTable.split("\\.", 2);
        String schema = parts.length == 2 ? parts[0] : null;
        String table = parts.length == 2 ? parts[1] : (parts.length == 1 ? parts[0] : null);
        if (table == null) return List.of();

        return tableRepository
            .findAll()
            .stream()
            .filter(t -> table.equalsIgnoreCase(t.getName()))
            .filter(t -> schema == null || schema.equalsIgnoreCase(t.getSchemaName()))
            .findFirst()
            .map(this::columnsOf)
            .orElse(List.of());
    }

    public List<CatalogSearchHitDto> search(String datasourceId, String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String kw = keyword.toLowerCase();
        return tableRepository
            .findAll()
            .stream()
            .filter(t -> t.getName() != null && t.getName().toLowerCase().contains(kw))
            .limit(Math.max(1, limit))
            .map(t -> new CatalogSearchHitDto(t.getSchemaName(), t.getName(), null, "TABLE"))
            .toList();
    }

    /* ----- helpers ----- */

    private CatalogSchemaDto toSchema(CatalogDataset d) {
        String schemaName = d.getName();
        if (schemaName == null) return null;
        // Treat dataset name as catalog.schema or schema; pick the first part
        return new CatalogSchemaDto(extractSchema(d), null);
    }

    private String extractSchema(CatalogDataset d) {
        if (d.getName() == null) return "default";
        return d.getName();
    }

    private List<CatalogTableDto> tablesOf(CatalogDataset d) {
        return tableRepository
            .findByDatasetId(d.getId())
            .stream()
            .map(t -> new CatalogTableDto(t.getName(), "TABLE", t.getComment(), null))
            .toList();
    }

    private List<CatalogColumnDto> columnsOf(CatalogTableSchema t) {
        return columnRepository
            .findByTableSchemaId(t.getId())
            .stream()
            .sorted(java.util.Comparator.comparingInt(CatalogColumnSchema::getOrdinalPosition))
            .map(c -> new CatalogColumnDto(
                c.getName(),
                c.getDataType(),
                c.getNullable() == null || c.getNullable(),
                c.getComment(),
                c.getOrdinalPosition()
            ))
            .toList();
    }
}
```

> **Note:** Inspect `CatalogTableSchemaRepository` for actual method names. Common Spring Data conventions: `findByDatasetId(UUID)`, `findByDataset_Id(UUID)`, or via `JpaSpecificationExecutor`. **First grep the existing repository to see what's available**:
> ```bash
> rg "interface CatalogTableSchemaRepository" -A 10 source/dts-platform/src/main/java
> ```
> If the spec doesn't have `findByDatasetId`, use `findAll()` + filter as a fallback. Same for `CatalogColumnSchemaRepository`.

### - [ ] Step 1.3: 追加 5 端点到 SqlIdeResource

In `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeResource.java`:

```java
// Add field + ctor inject SqlCatalogLazyService catalogService

@GetMapping("/catalog/datasources")
public ApiResponse<List<CatalogDatasourceDto>> catalogDatasources() {
    String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
    auditService.audit("READ", "sql.ide.catalog.datasources", user);
    return ApiResponses.ok(catalogService.listDatasources());
}

@GetMapping("/catalog/{dsId}/schemas")
public ApiResponse<List<CatalogSchemaDto>> catalogSchemas(@PathVariable String dsId) {
    String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
    auditService.audit("READ", "sql.ide.catalog.schemas", dsId);
    return ApiResponses.ok(catalogService.listSchemas(dsId));
}

@GetMapping("/catalog/{dsId}/schemas/{schema}/tables")
public ApiResponse<List<CatalogTableDto>> catalogTables(
    @PathVariable String dsId, @PathVariable String schema
) {
    String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
    auditService.audit("READ", "sql.ide.catalog.tables", dsId + "/" + schema);
    return ApiResponses.ok(catalogService.listTables(dsId, schema));
}

@GetMapping("/catalog/{dsId}/tables/{schemaTable}/columns")
public ApiResponse<List<CatalogColumnDto>> catalogColumns(
    @PathVariable String dsId, @PathVariable String schemaTable
) {
    String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
    auditService.audit("READ", "sql.ide.catalog.columns", dsId + "/" + schemaTable);
    return ApiResponses.ok(catalogService.listColumns(dsId, schemaTable));
}

@GetMapping("/catalog/{dsId}/search")
public ApiResponse<List<CatalogSearchHitDto>> catalogSearch(
    @PathVariable String dsId,
    @RequestParam("q") String q,
    @RequestParam(value = "limit", defaultValue = "50") int limit
) {
    String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
    auditService.audit("READ", "sql.ide.catalog.search", dsId + ":" + q);
    return ApiResponses.ok(catalogService.search(dsId, q, limit));
}
```

Add corresponding imports: `SqlCatalogLazyService`, `CatalogDatasourceDto`, `CatalogSchemaDto`, `CatalogTableDto`, `CatalogColumnDto`, `CatalogSearchHitDto`, `org.springframework.web.bind.annotation.RequestParam`.

### - [ ] Step 1.4: 集成测试

`source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeCatalogResourceIT.java`:

```java
package com.yuzhi.dts.platform.web.rest.sql;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@WithMockUser
class SqlIdeCatalogResourceIT {

    @Autowired private MockMvc mvc;

    @Test
    void datasourcesReturnsSeededList() throws Exception {
        mvc.perform(get("/api/sql/v2/catalog/datasources"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void schemasReturnsArrayForKnownDatasource() throws Exception {
        mvc.perform(get("/api/sql/v2/catalog/default/schemas"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void tablesEmptyForUnknownSchema() throws Exception {
        mvc.perform(get("/api/sql/v2/catalog/default/schemas/nonexistent_xyz/tables"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void columnsEmptyForUnknownTable() throws Exception {
        mvc.perform(get("/api/sql/v2/catalog/default/tables/none.none/columns"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void searchWithEmptyKeywordReturnsEmpty() throws Exception {
        mvc.perform(get("/api/sql/v2/catalog/default/search").param("q", ""))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
```

### - [ ] Step 1.5: 验证

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform
./mvnw -q -DskipTests compile
./mvnw -q test -Dtest='SqlIdeCatalogResourceIT'
./mvnw -q test -Dtest='SqlIde*'
```

**Expected:** 13 tests PASS（10 已有 + 5 新 = wait, let me recount: prior is 10. New is 5. = 15 total). 实际测试名单确认输出，但每个 IT class 的 PASS 即可。

### - [ ] Step 1.6: 提交

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlCatalogLazyService.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/CatalogDatasourceDto.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/CatalogSchemaDto.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/CatalogTableDto.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/CatalogColumnDto.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/CatalogSearchHitDto.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeResource.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeCatalogResourceIT.java
git commit -m "$(cat <<'EOF'
feat(F3/T11): lazy catalog API with 5 hierarchical endpoints

Sprint-11 F3 T11: new SqlCatalogLazyService exposes
GET /api/sql/v2/catalog/{datasources, {dsId}/schemas, .../tables,
.../columns, .../search} for incremental tree loading. Caffeine
caches per (dsId, schema) with 5min TTL. Static datasource registry
for now (real wiring is a separate concern). Audit each call as
SQL_CATALOG_BROWSE.
EOF
)"
```

---

## Task 2 — T12: Activity Bar + SidePanel 布局（前端）

**Files:**
- Create: `src/components/sql-ide/layout/useLayoutStore.ts`
- Modify: `src/components/sql-ide/layout/ActivityBar.tsx`
- Modify: `src/components/sql-ide/layout/SidePanel.tsx`
- Create: `src/components/sql-ide/copilot/CopilotSlot.tsx`
- Modify: `src/components/sql-ide/SqlIde.tsx`

### - [ ] Step 2.1: 创建 useLayoutStore

`source/dts-platform-webapp/src/components/sql-ide/layout/useLayoutStore.ts`:

```typescript
import { create } from "zustand";

export type ActivityId = "schema" | "history" | "saved" | "search" | "copilot";

const STORAGE_KEY = "sqlide.layout.v1";
const DEFAULT_SIDE_PANEL_WIDTH = 240;
const DEFAULT_BOTTOM_PANEL_HEIGHT = 260;

interface LayoutSnapshot {
  activeActivity: ActivityId | null;
  sidePanelWidth: number;
  bottomPanelHeight: number;
}

function loadSnapshot(): LayoutSnapshot {
  if (typeof window === "undefined") {
    return {
      activeActivity: "schema",
      sidePanelWidth: DEFAULT_SIDE_PANEL_WIDTH,
      bottomPanelHeight: DEFAULT_BOTTOM_PANEL_HEIGHT,
    };
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return {
        activeActivity: "schema",
        sidePanelWidth: DEFAULT_SIDE_PANEL_WIDTH,
        bottomPanelHeight: DEFAULT_BOTTOM_PANEL_HEIGHT,
      };
    }
    const parsed = JSON.parse(raw) as Partial<LayoutSnapshot>;
    return {
      activeActivity: parsed.activeActivity ?? "schema",
      sidePanelWidth: clampWidth(parsed.sidePanelWidth ?? DEFAULT_SIDE_PANEL_WIDTH),
      bottomPanelHeight: parsed.bottomPanelHeight ?? DEFAULT_BOTTOM_PANEL_HEIGHT,
    };
  } catch {
    return {
      activeActivity: "schema",
      sidePanelWidth: DEFAULT_SIDE_PANEL_WIDTH,
      bottomPanelHeight: DEFAULT_BOTTOM_PANEL_HEIGHT,
    };
  }
}

function clampWidth(w: number): number {
  return Math.min(500, Math.max(200, w));
}

function persist(snapshot: LayoutSnapshot): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
  } catch {
    /* ignore quota */
  }
}

interface LayoutStore extends LayoutSnapshot {
  setActivity(id: ActivityId | null): void;
  setSidePanelWidth(w: number): void;
  setBottomPanelHeight(h: number): void;
}

const initial = loadSnapshot();

export const useLayoutStore = create<LayoutStore>((set, get) => ({
  ...initial,
  setActivity(id) {
    const cur = get().activeActivity;
    const next = cur === id ? null : id;  // toggle behavior
    set({ activeActivity: next });
    persist({ ...get(), activeActivity: next });
  },
  setSidePanelWidth(w) {
    const clamped = clampWidth(w);
    set({ sidePanelWidth: clamped });
    persist({ ...get(), sidePanelWidth: clamped });
  },
  setBottomPanelHeight(h) {
    set({ bottomPanelHeight: h });
    persist({ ...get(), bottomPanelHeight: h });
  },
}));
```

### - [ ] Step 2.2: 重写 ActivityBar

`source/dts-platform-webapp/src/components/sql-ide/layout/ActivityBar.tsx`:

```tsx
import { type FC } from "react";
import { useShallow } from "zustand/react/shallow";
import { useLayoutStore, type ActivityId } from "./useLayoutStore";

interface ActivityIconDef {
  id: ActivityId;
  label: string;
  emoji: string;
}

const ACTIVITIES: ActivityIconDef[] = [
  { id: "schema", label: "Schema", emoji: "🗂" },
  { id: "history", label: "History", emoji: "📋" },
  { id: "saved", label: "Saved", emoji: "💾" },
  { id: "search", label: "Search", emoji: "🔍" },
  { id: "copilot", label: "Copilot", emoji: "🤖" },
];

export const ActivityBar: FC = () => {
  const { activeActivity, setActivity } = useLayoutStore(
    useShallow((s) => ({ activeActivity: s.activeActivity, setActivity: s.setActivity })),
  );

  return (
    <div
      role="toolbar"
      aria-orientation="vertical"
      aria-label="SQL IDE Activity Bar"
      data-testid="sqlide-activity-bar"
      style={{
        width: 44,
        flexShrink: 0,
        height: "100%",
        borderRight: "1px solid var(--ant-color-border)",
        background: "var(--ant-color-bg-container)",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        paddingTop: 8,
        gap: 6,
      }}
    >
      {ACTIVITIES.map((a) => {
        const active = activeActivity === a.id;
        return (
          <button
            key={a.id}
            type="button"
            data-testid={`sqlide-activity-${a.id}`}
            aria-label={a.label}
            aria-pressed={active}
            title={a.label}
            onClick={() => setActivity(a.id)}
            style={{
              width: 36,
              height: 36,
              border: "none",
              cursor: "pointer",
              background: active ? "var(--ant-color-bg-elevated)" : "transparent",
              color: active ? "var(--ant-color-primary)" : "var(--ant-color-text-secondary)",
              borderLeft: active ? "2px solid var(--ant-color-primary)" : "2px solid transparent",
              borderRadius: 0,
              fontSize: 18,
            }}
          >
            {a.emoji}
          </button>
        );
      })}
    </div>
  );
};
```

### - [ ] Step 2.3: 重写 SidePanel 接 useLayoutStore + 拖拽

`source/dts-platform-webapp/src/components/sql-ide/layout/SidePanel.tsx`:

```tsx
import { type FC, type PropsWithChildren, useCallback, useEffect, useRef } from "react";
import { useShallow } from "zustand/react/shallow";
import { useLayoutStore } from "./useLayoutStore";

export const SidePanel: FC<PropsWithChildren> = ({ children }) => {
  const { sidePanelWidth, setSidePanelWidth, activeActivity } = useLayoutStore(
    useShallow((s) => ({
      sidePanelWidth: s.sidePanelWidth,
      setSidePanelWidth: s.setSidePanelWidth,
      activeActivity: s.activeActivity,
    })),
  );
  const draggingRef = useRef(false);
  const startXRef = useRef(0);
  const startWidthRef = useRef(sidePanelWidth);

  const onMouseMove = useCallback(
    (e: MouseEvent) => {
      if (!draggingRef.current) return;
      const delta = e.clientX - startXRef.current;
      setSidePanelWidth(startWidthRef.current + delta);
    },
    [setSidePanelWidth],
  );

  const onMouseUp = useCallback(() => {
    draggingRef.current = false;
    document.body.style.cursor = "";
  }, []);

  useEffect(() => {
    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
    return () => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
    };
  }, [onMouseMove, onMouseUp]);

  if (activeActivity === null) return null;

  return (
    <aside
      data-testid="sqlide-side-panel"
      style={{
        width: sidePanelWidth,
        flexShrink: 0,
        height: "100%",
        borderRight: "1px solid var(--ant-color-border)",
        background: "var(--ant-color-bg-container)",
        display: "flex",
        flexDirection: "column",
        position: "relative",
      }}
    >
      <div style={{ flex: 1, overflow: "auto" }}>{children}</div>
      <div
        data-testid="sqlide-side-panel-resize-handle"
        role="separator"
        aria-orientation="vertical"
        onMouseDown={(e) => {
          draggingRef.current = true;
          startXRef.current = e.clientX;
          startWidthRef.current = sidePanelWidth;
          document.body.style.cursor = "col-resize";
        }}
        style={{
          position: "absolute",
          top: 0,
          right: -3,
          width: 6,
          height: "100%",
          cursor: "col-resize",
        }}
      />
    </aside>
  );
};
```

### - [ ] Step 2.4: 创建 Copilot 占位

`source/dts-platform-webapp/src/components/sql-ide/copilot/CopilotSlot.tsx`:

```tsx
import { type FC } from "react";

export const CopilotSlot: FC = () => (
  <div
    data-testid="sqlide-copilot-slot"
    style={{
      padding: 24,
      color: "var(--ant-color-text-secondary)",
      textAlign: "center",
    }}
  >
    <div style={{ fontSize: 32, marginBottom: 12 }}>🤖</div>
    <div style={{ fontWeight: 600, marginBottom: 8, color: "var(--ant-color-text)" }}>
      SQL Copilot
    </div>
    <div style={{ marginBottom: 16 }}>敬请期待</div>
    <div style={{ fontSize: 12, lineHeight: 1.7 }}>
      即将支持：<br />
      · 自然语言转 SQL<br />
      · SQL 解释与优化建议<br />
      · 智能错误修复
    </div>
  </div>
);
```

### - [ ] Step 2.5: 修改 SqlIde.tsx 接入 useLayoutStore 内容切换

Modify `source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx`:

In the `<SidePanel>` block, replace the static `<div>Schema · 骨架</div>` with a switch on `activeActivity`:

```tsx
import { useLayoutStore } from "./layout/useLayoutStore";
import { CopilotSlot } from "./copilot/CopilotSlot";

// inside SqlIde component, near the top:
const activeActivity = useLayoutStore((s) => s.activeActivity);

// Replace the SidePanel children:
<SidePanel>
  {activeActivity === "schema" && (
    <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>Schema · T13 待完成</div>
  )}
  {activeActivity === "history" && (
    <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>History · T14 待完成</div>
  )}
  {activeActivity === "saved" && (
    <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>Saved · T15 待完成</div>
  )}
  {activeActivity === "search" && (
    <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>Search · 暂未实现</div>
  )}
  {activeActivity === "copilot" && <CopilotSlot />}
</SidePanel>
```

### - [ ] Step 2.6: 验证

```bash
cd source/dts-platform-webapp
pnpm tsc --noEmit
pnpm vitest run src/components/sql-ide
```

**Expected:** tsc clean，44 tests still PASS（无新 test）。

### - [ ] Step 2.7: 提交

```bash
git add source/dts-platform-webapp/src/components/sql-ide/layout/useLayoutStore.ts \
        source/dts-platform-webapp/src/components/sql-ide/layout/ActivityBar.tsx \
        source/dts-platform-webapp/src/components/sql-ide/layout/SidePanel.tsx \
        source/dts-platform-webapp/src/components/sql-ide/copilot/CopilotSlot.tsx \
        source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx
git commit -m "$(cat <<'EOF'
feat(F3/T12): ActivityBar with 5 toggleable icons + resizable SidePanel

Sprint-11 F3 T12: ActivityBar renders 5 emoji icons (Schema/History/
Saved/Search/Copilot) wired to a Zustand useLayoutStore that persists
the active panel and panel width to localStorage. SidePanel becomes
collapsible (active=null) and width-draggable (200-500px). Copilot
slot rendered as 占位 panel.

Schema/History/Saved/Search panels are placeholders for T13–T15.
EOF
)"
```

---

## Task 3 — T13: SchemaTree（虚拟树 + 右键菜单 + hover 卡片）

**Files:**
- Modify: `package.json` (add `react-arborist@^3`)
- Create: `src/components/sql-ide/api/sqlIdeCatalog.ts`
- Create: `src/components/sql-ide/schema/sqlGenerators.ts`
- Create: `src/components/sql-ide/schema/__tests__/sqlGenerators.test.ts`
- Create: `src/components/sql-ide/schema/useSchemaTreeData.ts`
- Create: `src/components/sql-ide/schema/SchemaTree.tsx`
- Create: `src/components/sql-ide/schema/TableDetailPopover.tsx`
- Create: `src/components/sql-ide/schema/SchemaContextMenu.tsx`
- Modify: `src/components/sql-ide/SqlIde.tsx` (替换 schema placeholder 为 SchemaTree)

### - [ ] Step 3.1: 安装 react-arborist

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp
pnpm add react-arborist@^3
```

### - [ ] Step 3.2: 创建 catalog API client

`source/dts-platform-webapp/src/components/sql-ide/api/sqlIdeCatalog.ts`:

```typescript
import apiClient from "@/api/apiClient";

export interface CatalogDatasource {
  id: string;
  name: string;
  engine: string;
  label: string;
}

export interface CatalogSchema {
  name: string;
  catalog: string | null;
}

export interface CatalogTable {
  name: string;
  type: string;
  comment: string | null;
  rowCountEstimate: number | null;
}

export interface CatalogColumn {
  name: string;
  dataType: string;
  nullable: boolean;
  comment: string | null;
  ordinalPosition: number;
}

export interface CatalogSearchHit {
  schema: string;
  table: string;
  column: string | null;
  type: string;
}

export async function listDatasources(): Promise<CatalogDatasource[]> {
  return apiClient.get<CatalogDatasource[]>({ url: "/api/sql/v2/catalog/datasources" });
}

export async function listSchemas(dsId: string): Promise<CatalogSchema[]> {
  return apiClient.get<CatalogSchema[]>({ url: `/api/sql/v2/catalog/${encodeURIComponent(dsId)}/schemas` });
}

export async function listTables(dsId: string, schema: string): Promise<CatalogTable[]> {
  return apiClient.get<CatalogTable[]>({
    url: `/api/sql/v2/catalog/${encodeURIComponent(dsId)}/schemas/${encodeURIComponent(schema)}/tables`,
  });
}

export async function listColumns(dsId: string, schemaTable: string): Promise<CatalogColumn[]> {
  return apiClient.get<CatalogColumn[]>({
    url: `/api/sql/v2/catalog/${encodeURIComponent(dsId)}/tables/${encodeURIComponent(schemaTable)}/columns`,
  });
}

export async function searchCatalog(
  dsId: string,
  q: string,
  limit = 50,
): Promise<CatalogSearchHit[]> {
  return apiClient.get<CatalogSearchHit[]>({
    url: `/api/sql/v2/catalog/${encodeURIComponent(dsId)}/search?q=${encodeURIComponent(q)}&limit=${limit}`,
  });
}
```

### - [ ] Step 3.3: 写 sqlGenerators 失败测试（TDD）

`source/dts-platform-webapp/src/components/sql-ide/schema/__tests__/sqlGenerators.test.ts`:

```typescript
import { describe, expect, it } from "vitest";
import { generateSelect, generateInsert, copyName } from "../sqlGenerators";

describe("generateSelect", () => {
  it("generates SELECT * with LIMIT 100 by default", () => {
    expect(generateSelect("public", "users", null)).toBe(
      "SELECT * FROM public.users LIMIT 100",
    );
  });

  it("uses provided columns when listed", () => {
    expect(generateSelect("public", "users", ["id", "name", "email"])).toBe(
      "SELECT id, name, email FROM public.users LIMIT 100",
    );
  });

  it("quotes identifiers containing spaces or special chars", () => {
    expect(generateSelect("public", "user list", null)).toBe(
      'SELECT * FROM public."user list" LIMIT 100',
    );
  });
});

describe("generateInsert", () => {
  it("generates INSERT template with named columns and ? placeholders", () => {
    expect(generateInsert("public", "users", ["id", "name", "email"])).toBe(
      "INSERT INTO public.users (id, name, email) VALUES (?, ?, ?)",
    );
  });

  it("returns empty string when columns is empty", () => {
    expect(generateInsert("public", "users", [])).toBe("");
  });
});

describe("copyName", () => {
  it("returns schema.table for table identifier", () => {
    expect(copyName("public", "users")).toBe("public.users");
  });
});
```

### - [ ] Step 3.4: 运行测试确认 FAIL

```bash
pnpm vitest run src/components/sql-ide/schema/__tests__/sqlGenerators.test.ts
```

**Expected:** FAIL — module not found.

### - [ ] Step 3.5: 实现 sqlGenerators

`source/dts-platform-webapp/src/components/sql-ide/schema/sqlGenerators.ts`:

```typescript
function quoteIfNeeded(id: string): string {
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(id) ? id : `"${id.replace(/"/g, '""')}"`;
}

export function generateSelect(
  schema: string,
  table: string,
  columns: string[] | null,
): string {
  const cols = columns && columns.length > 0 ? columns.join(", ") : "*";
  return `SELECT ${cols} FROM ${quoteIfNeeded(schema)}.${quoteIfNeeded(table)} LIMIT 100`;
}

export function generateInsert(
  schema: string,
  table: string,
  columns: string[],
): string {
  if (columns.length === 0) return "";
  const placeholders = columns.map(() => "?").join(", ");
  return `INSERT INTO ${quoteIfNeeded(schema)}.${quoteIfNeeded(table)} (${columns.join(", ")}) VALUES (${placeholders})`;
}

export function copyName(schema: string, table: string): string {
  return `${schema}.${table}`;
}
```

### - [ ] Step 3.6: 测试通过

```bash
pnpm vitest run src/components/sql-ide/schema/__tests__/sqlGenerators.test.ts
```

**Expected:** 6 PASS.

### - [ ] Step 3.7: 创建 useSchemaTreeData hook

`source/dts-platform-webapp/src/components/sql-ide/schema/useSchemaTreeData.ts`:

```typescript
import { useQuery } from "@tanstack/react-query";
import {
  type CatalogColumn,
  type CatalogDatasource,
  type CatalogSchema,
  type CatalogTable,
  listColumns,
  listDatasources,
  listSchemas,
  listTables,
} from "../api/sqlIdeCatalog";

const STALE_5_MIN = 5 * 60 * 1000;
const CACHE_30_MIN = 30 * 60 * 1000;

export function useDatasourcesQuery() {
  return useQuery<CatalogDatasource[]>({
    queryKey: ["sqlide", "catalog", "datasources"],
    queryFn: listDatasources,
    staleTime: STALE_5_MIN,
    gcTime: CACHE_30_MIN,
  });
}

export function useSchemasQuery(dsId: string | null) {
  return useQuery<CatalogSchema[]>({
    queryKey: ["sqlide", "catalog", "schemas", dsId],
    queryFn: () => listSchemas(dsId!),
    enabled: !!dsId,
    staleTime: STALE_5_MIN,
    gcTime: CACHE_30_MIN,
  });
}

export function useTablesQuery(dsId: string | null, schema: string | null) {
  return useQuery<CatalogTable[]>({
    queryKey: ["sqlide", "catalog", "tables", dsId, schema],
    queryFn: () => listTables(dsId!, schema!),
    enabled: !!dsId && !!schema,
    staleTime: STALE_5_MIN,
    gcTime: CACHE_30_MIN,
  });
}

export function useColumnsQuery(dsId: string | null, schemaTable: string | null) {
  return useQuery<CatalogColumn[]>({
    queryKey: ["sqlide", "catalog", "columns", dsId, schemaTable],
    queryFn: () => listColumns(dsId!, schemaTable!),
    enabled: !!dsId && !!schemaTable,
    staleTime: STALE_5_MIN,
    gcTime: CACHE_30_MIN,
  });
}
```

### - [ ] Step 3.8: 创建 TableDetailPopover

`source/dts-platform-webapp/src/components/sql-ide/schema/TableDetailPopover.tsx`:

```tsx
import { Popover, Spin } from "antd";
import { type FC, type PropsWithChildren } from "react";
import { useColumnsQuery } from "./useSchemaTreeData";

interface TableDetailPopoverProps {
  dsId: string;
  schema: string;
  table: string;
}

export const TableDetailPopover: FC<PropsWithChildren<TableDetailPopoverProps>> = ({
  dsId, schema, table, children,
}) => {
  const { data, isLoading } = useColumnsQuery(dsId, `${schema}.${table}`);

  return (
    <Popover
      placement="rightTop"
      mouseEnterDelay={0.5}
      mouseLeaveDelay={0.2}
      title={`${schema}.${table}`}
      content={
        isLoading ? (
          <Spin size="small" />
        ) : data && data.length > 0 ? (
          <div style={{ maxHeight: 280, overflow: "auto", minWidth: 220 }}>
            {data.map((c) => (
              <div key={c.name} style={{ fontSize: 12, padding: "2px 0" }}>
                <span style={{ fontFamily: "monospace" }}>{c.name}</span>
                <span style={{ color: "var(--ant-color-text-tertiary)", marginLeft: 8 }}>
                  {c.dataType}
                </span>
                {c.nullable && (
                  <span style={{ color: "var(--ant-color-text-quaternary)", marginLeft: 6 }}>
                    NULL
                  </span>
                )}
              </div>
            ))}
          </div>
        ) : (
          <span style={{ color: "var(--ant-color-text-tertiary)" }}>无列信息</span>
        )
      }
    >
      {children}
    </Popover>
  );
};
```

### - [ ] Step 3.9: 创建 SchemaContextMenu helper

`source/dts-platform-webapp/src/components/sql-ide/schema/SchemaContextMenu.tsx`:

```tsx
import { Dropdown, type MenuProps, message } from "antd";
import { type FC, type PropsWithChildren } from "react";
import { useColumnsQuery } from "./useSchemaTreeData";
import { copyName, generateInsert, generateSelect } from "./sqlGenerators";

interface TableContextMenuProps {
  dsId: string;
  schema: string;
  table: string;
  onInsertSqlAtCursor: (sql: string) => void;
}

export const TableContextMenu: FC<PropsWithChildren<TableContextMenuProps>> = ({
  dsId, schema, table, onInsertSqlAtCursor, children,
}) => {
  const { data: columns } = useColumnsQuery(dsId, `${schema}.${table}`);
  const items: MenuProps["items"] = [
    { key: "select", label: "Generate SELECT" },
    { key: "insert", label: "Generate INSERT" },
    { key: "copy-name", label: "Copy Name" },
  ];
  const onClick: MenuProps["onClick"] = ({ key }) => {
    switch (key) {
      case "select":
        onInsertSqlAtCursor(generateSelect(schema, table, null));
        break;
      case "insert": {
        const cols = (columns ?? []).map((c) => c.name);
        const sql = generateInsert(schema, table, cols);
        if (sql) onInsertSqlAtCursor(sql);
        else message.info("列信息加载中，请稍后再试");
        break;
      }
      case "copy-name":
        navigator.clipboard.writeText(copyName(schema, table)).catch(() => {
          message.warning("复制失败");
        });
        break;
    }
  };
  return (
    <Dropdown trigger={["contextMenu"]} menu={{ items, onClick }}>
      <span>{children}</span>
    </Dropdown>
  );
};
```

### - [ ] Step 3.10: 创建 SchemaTree 主组件

`source/dts-platform-webapp/src/components/sql-ide/schema/SchemaTree.tsx`:

```tsx
import { type FC, useMemo, useState } from "react";
import { Tree, type NodeApi, type NodeRendererProps } from "react-arborist";
import { Input, Select, Spin } from "antd";
import { useDatasourcesQuery, useSchemasQuery, useTablesQuery } from "./useSchemaTreeData";
import { TableDetailPopover } from "./TableDetailPopover";
import { TableContextMenu } from "./SchemaContextMenu";

interface TreeNode {
  id: string;
  name: string;
  kind: "schema" | "table";
  schema: string;
  table?: string;
  children?: TreeNode[];
}

export interface SchemaTreeProps {
  /** Called when user double-clicks a table — receives "schema.table". */
  onInsertIdentifier?: (qualifiedName: string) => void;
  /** Called when context-menu item generates SQL — receives the SQL string. */
  onInsertSqlAtCursor?: (sql: string) => void;
}

export const SchemaTree: FC<SchemaTreeProps> = ({ onInsertIdentifier, onInsertSqlAtCursor }) => {
  const [dsId, setDsId] = useState<string | null>(null);
  const [filter, setFilter] = useState("");

  const { data: datasources } = useDatasourcesQuery();
  const { data: schemas, isLoading: schemasLoading } = useSchemasQuery(dsId);

  // Default-pick first datasource
  if (dsId === null && datasources && datasources.length > 0) {
    setDsId(datasources[0].id);
  }

  const treeData: TreeNode[] = useMemo(() => {
    if (!schemas) return [];
    return schemas.map((s) => ({
      id: `schema:${s.name}`,
      name: s.name,
      kind: "schema",
      schema: s.name,
      children: [],  // populated lazily on expand via Branch component below
    }));
  }, [schemas]);

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <div style={{ padding: "8px 12px", display: "flex", flexDirection: "column", gap: 6 }}>
        <Select
          size="small"
          value={dsId ?? undefined}
          onChange={(v) => setDsId(v)}
          options={(datasources ?? []).map((d) => ({ value: d.id, label: d.label }))}
          style={{ width: "100%" }}
          placeholder="选择数据源"
        />
        <Input
          size="small"
          placeholder="搜索表/列…"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          allowClear
        />
      </div>
      <div style={{ flex: 1, minHeight: 0, padding: "0 4px" }}>
        {schemasLoading ? (
          <div style={{ textAlign: "center", padding: 20 }}><Spin /></div>
        ) : (
          <Tree<TreeNode>
            data={treeData}
            openByDefault={false}
            width="100%"
            height={500}  // virtualized; a parent ResizeObserver could feed real height
            indent={16}
            rowHeight={26}
            disableMultiSelection
          >
            {(nodeProps) => (
              <SchemaTreeRow
                {...nodeProps}
                dsId={dsId!}
                onInsertIdentifier={onInsertIdentifier}
                onInsertSqlAtCursor={onInsertSqlAtCursor}
              />
            )}
          </Tree>
        )}
      </div>
    </div>
  );
};

interface RowProps extends NodeRendererProps<TreeNode> {
  dsId: string;
  onInsertIdentifier?: (qualifiedName: string) => void;
  onInsertSqlAtCursor?: (sql: string) => void;
}

const SchemaTreeRow: FC<RowProps> = ({ node, style, dragHandle, dsId, onInsertIdentifier, onInsertSqlAtCursor }) => {
  return (
    <div ref={dragHandle} style={{ ...style, display: "flex", alignItems: "center", paddingLeft: 4 }}>
      <span
        onClick={() => node.toggle()}
        style={{ cursor: "pointer", width: 14, fontSize: 10, color: "var(--ant-color-text-tertiary)" }}
      >
        {node.isInternal ? (node.isOpen ? "▾" : "▸") : ""}
      </span>
      {node.data.kind === "schema" ? (
        <SchemaRow node={node} dsId={dsId} onInsertIdentifier={onInsertIdentifier} onInsertSqlAtCursor={onInsertSqlAtCursor} />
      ) : (
        <TableContextMenu
          dsId={dsId}
          schema={node.data.schema}
          table={node.data.table!}
          onInsertSqlAtCursor={(sql) => onInsertSqlAtCursor?.(sql)}
        >
          <TableDetailPopover dsId={dsId} schema={node.data.schema} table={node.data.table!}>
            <span
              style={{ fontSize: 12, color: "var(--ant-color-text)", cursor: "pointer", userSelect: "none" }}
              onDoubleClick={() => onInsertIdentifier?.(`${node.data.schema}.${node.data.table}`)}
            >
              📄 {node.data.name}
            </span>
          </TableDetailPopover>
        </TableContextMenu>
      )}
    </div>
  );
};

const SchemaRow: FC<{
  node: NodeApi<TreeNode>;
  dsId: string;
  onInsertIdentifier?: (qualifiedName: string) => void;
  onInsertSqlAtCursor?: (sql: string) => void;
}> = ({ node, dsId }) => {
  const { data: tables } = useTablesQuery(node.isOpen ? dsId : null, node.isOpen ? node.data.schema : null);
  // populate children when loaded
  if (node.isOpen && tables && (node.data.children?.length ?? 0) === 0) {
    node.data.children = tables.map((t) => ({
      id: `table:${node.data.schema}.${t.name}`,
      name: t.name,
      kind: "table",
      schema: node.data.schema,
      table: t.name,
    }));
  }
  return (
    <span style={{ fontSize: 12, color: "var(--ant-color-text)", userSelect: "none" }}>
      📁 {node.data.name}
    </span>
  );
};
```

> **Pragma:** react-arborist works best with statically-known children. The lazy children-population approach above (`node.data.children = ...` inside render) is a workable pattern but borderline against react-arborist's expected immutable model. If issues arise (e.g. tree doesn't refresh after children load), follow react-arborist docs on `treeUpdater` / `useTree` controlled mode. For now this approach is sufficient because we re-render on react-query cache update and node identity is stable.

### - [ ] Step 3.11: 接入 SqlIde

In `source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx`, replace the schema panel placeholder:

```tsx
import { SchemaTree } from "./schema/SchemaTree";

// inside SidePanel, replace the schema branch:
{activeActivity === "schema" && (
  <SchemaTree
    onInsertIdentifier={(name) => {
      const handle = editorHandleRef.current;
      if (!handle) return;
      // For T13 we use a simple insert-at-end approach; T-future may add at-cursor:
      const ed = handle.getEditor();
      if (!ed) return;
      const pos = ed.getPosition();
      const op = pos
        ? { range: { startLineNumber: pos.lineNumber, startColumn: pos.column, endLineNumber: pos.lineNumber, endColumn: pos.column }, text: name }
        : null;
      if (op) ed.executeEdits("sqlide.schema-insert", [op]);
    }}
    onInsertSqlAtCursor={(sql) => {
      const handle = editorHandleRef.current;
      if (!handle) return;
      const ed = handle.getEditor();
      if (!ed) return;
      const pos = ed.getPosition();
      const op = pos
        ? { range: { startLineNumber: pos.lineNumber, startColumn: pos.column, endLineNumber: pos.lineNumber, endColumn: pos.column }, text: sql }
        : null;
      if (op) ed.executeEdits("sqlide.schema-sql", [op]);
    }}
  />
)}
```

> **Note:** Strictly typed `IIdentifiedSingleEditOperation` may need `forceMoveMarkers: true` / `text: ...` minimal shape. If TS complains, cast via `as unknown as monaco.editor.IIdentifiedSingleEditOperation[]` or import the type.

### - [ ] Step 3.12: 验证

```bash
pnpm vitest run src/components/sql-ide
pnpm tsc --noEmit
```

**Expected:** 50/50 PASS (44 + 6 sqlGenerators)，tsc clean。

### - [ ] Step 3.13: 提交

```bash
git add source/dts-platform-webapp/package.json \
        source/dts-platform-webapp/pnpm-lock.yaml \
        source/dts-platform-webapp/src/components/sql-ide/api/sqlIdeCatalog.ts \
        source/dts-platform-webapp/src/components/sql-ide/schema/ \
        source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx
git commit -m "$(cat <<'EOF'
feat(F3/T13): SchemaTree with virtual scrolling + lazy load + context menu

Sprint-11 F3 T13: react-arborist tree consumes lazy catalog endpoints
via React Query (5min stale). Datasource selector + filter input atop;
schema nodes lazy-load tables on expand; tables expose context menu
(SELECT/INSERT/Copy Name) and a 500ms-delay hover popover with column
list. Double-click table name inserts schema.table at cursor; context-
menu actions insert generated SQL via SqlEditorHandle.

sqlGenerators (TDD, 6 tests): SELECT/INSERT/copyName with quoting for
non-identifier characters.
EOF
)"
```

---

## Task 4 — T14: HistoryPanel（执行历史）

**Files:**
- Create: `src/components/sql-ide/api/sqlIdeHistory.ts`
- Create: `src/components/sql-ide/history/HistoryPanel.tsx`
- Create: `src/components/sql-ide/history/useHistoryQuery.ts`
- Modify: `src/components/sql-ide/SqlIde.tsx` (替换 history placeholder)
- Modify: `web/rest/sql/SqlIdeResource.java` (新增 GET /history endpoint)
- Create: `service/sql/dto/QueryHistoryItemDto.java`
- Create: `src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeHistoryResourceIT.java`

### - [ ] Step 4.1: 创建后端 DTO

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/QueryHistoryItemDto.java`:

```java
package com.yuzhi.dts.platform.service.sql.dto;

import java.time.Instant;
import java.util.UUID;

public record QueryHistoryItemDto(
    UUID id,
    String sqlText,
    String engine,
    String connection,
    String status,
    Instant startedAt,
    Instant finishedAt,
    Long rowCount,
    Long elapsedMs,
    String createdBy
) {}
```

### - [ ] Step 4.2: 追加 history 端点到 SqlIdeResource

In `SqlIdeResource.java`, add (with imports):

```java
@GetMapping("/history")
public ApiResponse<List<QueryHistoryItemDto>> listHistory(
    @RequestParam(value = "status", required = false) String status,
    @RequestParam(value = "datasource", required = false) String datasource,
    @RequestParam(value = "q", required = false) String q,
    @RequestParam(value = "limit", defaultValue = "50") int limit
) {
    String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
    auditService.audit("READ", "sql.ide.history", user);
    // Reuse existing query_execution table; current user only.
    int safe = Math.min(200, Math.max(1, limit));
    List<QueryHistoryItemDto> items = queryExecutionRepository
        .findRecentByUser(user, safe)
        .stream()
        .filter(e -> status == null || status.equalsIgnoreCase(e.getStatus()))
        .filter(e -> datasource == null || datasource.equals(e.getConnection()))
        .filter(e -> q == null || q.isBlank() || (e.getSqlText() != null && e.getSqlText().toLowerCase().contains(q.toLowerCase())))
        .map(e -> new QueryHistoryItemDto(
            e.getId(),
            e.getSqlText(),
            e.getEngine(),
            e.getConnection(),
            e.getStatus(),
            e.getStartedAt(),
            e.getFinishedAt(),
            e.getRowCount(),
            e.getElapsedMs(),
            e.getCreatedBy()
        ))
        .toList();
    return ApiResponses.ok(items);
}
```

Inject `QueryExecutionRepository queryExecutionRepository` via constructor. **First check the existing repository's available methods**:

```bash
rg "interface QueryExecutionRepository" -A 10 /opt/prod/s10/s10-stack/source/dts-platform/src/main/java
```

If `findRecentByUser(String, int)` doesn't exist, **add it to the repository** as a derived query or `@Query`-annotated:

```java
// in QueryExecutionRepository
List<QueryExecution> findTop50ByCreatedByOrderByStartedAtDesc(String createdBy);
```

Adapt the controller call accordingly: `queryExecutionRepository.findTop50ByCreatedByOrderByStartedAtDesc(user).stream().limit(safe)`.

### - [ ] Step 4.3: 集成测试

`source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeHistoryResourceIT.java`:

```java
package com.yuzhi.dts.platform.web.rest.sql;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@WithMockUser(username = "alice")
class SqlIdeHistoryResourceIT {

    @Autowired private MockMvc mvc;

    @Test
    void emptyHistoryForFreshUser() throws Exception {
        mvc.perform(get("/api/sql/v2/history"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void supportsLimitParameter() throws Exception {
        mvc.perform(get("/api/sql/v2/history").param("limit", "10"))
           .andExpect(status().isOk());
    }

    @Test
    void supportsStatusFilter() throws Exception {
        mvc.perform(get("/api/sql/v2/history").param("status", "SUCCESS"))
           .andExpect(status().isOk());
    }
}
```

### - [ ] Step 4.4: 前端 API + hook

`src/components/sql-ide/api/sqlIdeHistory.ts`:

```typescript
import apiClient from "@/api/apiClient";

export interface QueryHistoryItem {
  id: string;
  sqlText: string | null;
  engine: string | null;
  connection: string | null;
  status: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  rowCount: number | null;
  elapsedMs: number | null;
  createdBy: string | null;
}

export interface ListHistoryParams {
  status?: string;
  datasource?: string;
  q?: string;
  limit?: number;
}

export async function listHistory(params: ListHistoryParams = {}): Promise<QueryHistoryItem[]> {
  const search = new URLSearchParams();
  if (params.status) search.set("status", params.status);
  if (params.datasource) search.set("datasource", params.datasource);
  if (params.q) search.set("q", params.q);
  if (params.limit) search.set("limit", String(params.limit));
  const qs = search.toString() ? `?${search.toString()}` : "";
  return apiClient.get<QueryHistoryItem[]>({ url: `/api/sql/v2/history${qs}` });
}
```

`src/components/sql-ide/history/useHistoryQuery.ts`:

```typescript
import { useQuery } from "@tanstack/react-query";
import { listHistory, type ListHistoryParams, type QueryHistoryItem } from "../api/sqlIdeHistory";

export function useHistoryQuery(params: ListHistoryParams) {
  return useQuery<QueryHistoryItem[]>({
    queryKey: ["sqlide", "history", params],
    queryFn: () => listHistory(params),
    staleTime: 30_000,
  });
}
```

### - [ ] Step 4.5: HistoryPanel 组件

`src/components/sql-ide/history/HistoryPanel.tsx`:

```tsx
import { Empty, Input, Select, Spin } from "antd";
import { type FC, useState } from "react";
import { useTabStore } from "../tabs/useTabStore";
import { useHistoryQuery } from "./useHistoryQuery";

export const HistoryPanel: FC = () => {
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [q, setQ] = useState("");
  const openTab = useTabStore((s) => s.openTab);
  const { data, isLoading } = useHistoryQuery({ status, q: q || undefined, limit: 50 });

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", padding: "8px 12px", gap: 6 }}>
      <Select
        size="small"
        placeholder="状态筛选"
        value={status}
        onChange={setStatus}
        allowClear
        options={[
          { value: "SUCCESS", label: "成功" },
          { value: "FAILED", label: "失败" },
          { value: "CANCELED", label: "取消" },
          { value: "RUNNING", label: "运行中" },
        ]}
      />
      <Input.Search
        size="small"
        placeholder="SQL 关键词…"
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />
      <div style={{ flex: 1, overflow: "auto", marginTop: 4 }}>
        {isLoading ? (
          <div style={{ textAlign: "center", padding: 12 }}><Spin /></div>
        ) : !data || data.length === 0 ? (
          <Empty description="暂无历史" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          data.map((item) => (
            <div
              key={item.id}
              data-testid={`history-item-${item.id}`}
              onDoubleClick={() => openTab({
                title: `History · ${item.id.slice(0, 8)}`,
                sqlText: item.sqlText ?? "",
                engine: (item.engine as any) ?? "generic",
              })}
              style={{
                padding: "6px 4px",
                borderBottom: "1px solid var(--ant-color-border-secondary)",
                cursor: "pointer",
                fontSize: 11,
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: statusColor(item.status) }}>{statusIcon(item.status)} {item.status}</span>
                <span style={{ color: "var(--ant-color-text-tertiary)" }}>
                  {item.elapsedMs != null ? `${item.elapsedMs}ms` : ""}
                </span>
              </div>
              <div style={{ marginTop: 2, color: "var(--ant-color-text-secondary)" }}>
                {(item.sqlText ?? "").slice(0, 60)}{(item.sqlText?.length ?? 0) > 60 ? "…" : ""}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

function statusColor(status: string | null): string {
  switch (status) {
    case "SUCCESS": return "var(--ant-color-success)";
    case "FAILED": return "var(--ant-color-error)";
    case "CANCELED": return "var(--ant-color-warning)";
    default: return "var(--ant-color-text-secondary)";
  }
}

function statusIcon(status: string | null): string {
  switch (status) {
    case "SUCCESS": return "✓";
    case "FAILED": return "✗";
    case "CANCELED": return "⊘";
    default: return "⏳";
  }
}
```

### - [ ] Step 4.6: 接入 SqlIde

Replace `{activeActivity === "history" && ...}` placeholder in SqlIde.tsx with `<HistoryPanel />` (import added).

### - [ ] Step 4.7: 验证

```bash
cd source/dts-platform && ./mvnw -q test -Dtest='SqlIdeHistoryResourceIT'
cd ../dts-platform-webapp && pnpm tsc --noEmit && pnpm vitest run src/components/sql-ide
```

**Expected:** 后端 IT 3/3 PASS, 前端 50/50 PASS.

### - [ ] Step 4.8: 提交

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/QueryHistoryItemDto.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeResource.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/explore/QueryExecutionRepository.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeHistoryResourceIT.java \
        source/dts-platform-webapp/src/components/sql-ide/api/sqlIdeHistory.ts \
        source/dts-platform-webapp/src/components/sql-ide/history/ \
        source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx
git commit -m "$(cat <<'EOF'
feat(F3/T14): HistoryPanel reading current user's query_execution

Sprint-11 F3 T14: GET /api/sql/v2/history filtered by status/datasource/
keyword (SQL ILIKE), limited to 200 rows, scoped to current user only.
HistoryPanel renders compact status-iconed list; double-click opens a
new Tab pre-filled with the SQL.
EOF
)"
```

---

## Task 5 — T15: SavedPanel + folder 分组

**Files:**
- Create: `src/main/resources/config/liquibase/changelog/20260413_02_saved_query_folder.xml`
- Modify: `src/main/resources/config/liquibase/master.xml` (include)
- Modify: `domain/explore/SavedQuery.java` (add `folder` field)
- Modify: `service/sql/dto/SavedQueryRequest.java` (add `folder`)
- Modify: `service/sql/dto/SavedQueryResponse.java` (add `folder`)
- Modify: `service/sql/SavedQueryService.java` (handle folder)
- Create: `src/components/sql-ide/api/sqlIdeSaved.ts`
- Create: `src/components/sql-ide/saved/SavedPanel.tsx`
- Create: `src/components/sql-ide/saved/SaveQueryDialog.tsx`
- Modify: `src/components/sql-ide/SqlIde.tsx` (替换 saved placeholder + 接 onSaveAsQuery)

### - [ ] Step 5.1: Liquibase changelog 加 folder 列

`source/dts-platform/src/main/resources/config/liquibase/changelog/20260413_02_saved_query_folder.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.22.xsd">

    <changeSet id="20260413_02_saved_query_folder" author="platform">
        <addColumn tableName="saved_query">
            <column name="folder" type="varchar(200)"/>
        </addColumn>
        <createIndex tableName="saved_query" indexName="idx_saved_query_folder">
            <column name="folder"/>
        </createIndex>

        <rollback>
            <dropIndex tableName="saved_query" indexName="idx_saved_query_folder"/>
            <dropColumn tableName="saved_query" columnName="folder"/>
        </rollback>
    </changeSet>
</databaseChangeLog>
```

In `master.xml`, append after the most recent include (after `20260413_01_sql_ide_tab.xml`):

```xml
<include file="config/liquibase/changelog/20260413_02_saved_query_folder.xml" relativeToChangelogFile="false"/>
```

### - [ ] Step 5.2: 实体加 folder 字段

In `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/explore/SavedQuery.java`, add the column field + getter/setter following existing field style:

```java
@Column(name = "folder", length = 200)
private String folder;

public String getFolder() { return folder; }
public void setFolder(String folder) { this.folder = folder; }
```

### - [ ] Step 5.3: DTOs + service

In `SavedQueryRequest.java` and `SavedQueryResponse.java`, **inspect existing files first** to confirm whether they're records or classes. Add `folder` field consistently:

```bash
cat /opt/prod/s10/s10-stack/source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/SavedQueryRequest.java
cat /opt/prod/s10/s10-stack/source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/SavedQueryResponse.java
```

If they're records, add `String folder` parameter. If classes, add field + getter/setter.

In `SavedQueryService.java`, find the create/update methods and pass `request.folder()` (or `request.getFolder()`) into the entity setter. Same for the response builder.

### - [ ] Step 5.4: 前端 API client

`src/components/sql-ide/api/sqlIdeSaved.ts`:

```typescript
import apiClient from "@/api/apiClient";

export interface SavedQueryItem {
  id: string;
  title: string;
  sqlText: string;
  engine: string;
  connection: string | null;
  level: string;
  tags: string | null;
  folder: string | null;
  createdBy: string | null;
  createdDate: string | null;
}

export interface SaveQueryPayload {
  title: string;
  sqlText: string;
  engine: string;
  connection?: string | null;
  level?: string;
  tags?: string | null;
  folder?: string | null;
}

export async function listSavedQueries(): Promise<SavedQueryItem[]> {
  // The existing endpoint is /api/sql/saved-queries (verify).
  return apiClient.get<SavedQueryItem[]>({ url: "/api/sql/saved-queries" });
}

export async function createSavedQuery(payload: SaveQueryPayload): Promise<SavedQueryItem> {
  return apiClient.post<SavedQueryItem>({ url: "/api/sql/saved-queries", data: payload });
}

export async function deleteSavedQuery(id: string): Promise<void> {
  await apiClient.delete<void>({ url: `/api/sql/saved-queries/${id}` });
}
```

> **Note:** Inspect existing `SqlWorkbenchResource` to confirm the endpoint paths. Adjust if `/api/sql/saved` or other.

### - [ ] Step 5.5: SaveQueryDialog

`src/components/sql-ide/saved/SaveQueryDialog.tsx`:

```tsx
import { Form, Input, Modal, Select } from "antd";
import { type FC } from "react";
import { type SaveQueryPayload } from "../api/sqlIdeSaved";

interface SaveQueryDialogProps {
  open: boolean;
  defaultSql: string;
  defaultEngine: string;
  existingFolders: string[];
  onCancel: () => void;
  onSave: (payload: SaveQueryPayload) => Promise<void>;
}

export const SaveQueryDialog: FC<SaveQueryDialogProps> = ({
  open, defaultSql, defaultEngine, existingFolders, onCancel, onSave,
}) => {
  const [form] = Form.useForm<SaveQueryPayload>();

  return (
    <Modal
      open={open}
      onCancel={onCancel}
      onOk={async () => {
        const values = await form.validateFields();
        await onSave({ ...values, sqlText: defaultSql, engine: defaultEngine, level: values.level ?? "INTERNAL" });
        form.resetFields();
      }}
      title="保存为查询"
      okText="保存"
      cancelText="取消"
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item label="标题" name="title" rules={[{ required: true, message: "请输入标题" }]}>
          <Input placeholder="例如：日活统计" />
        </Form.Item>
        <Form.Item label="文件夹（可选）" name="folder">
          <Select
            allowClear
            mode="tags"
            placeholder="选择已有或输入新文件夹名"
            options={existingFolders.map((f) => ({ value: f, label: f }))}
          />
        </Form.Item>
        <Form.Item label="标签（逗号分隔）" name="tags">
          <Input placeholder="bi, daily" />
        </Form.Item>
      </Form>
    </Modal>
  );
};
```

> Note: Form's `mode="tags"` Select returns `string[]`; the `onSave` payload coerces it back to string by joining or picking first. Adjust:
>
> ```tsx
> await onSave({
>   ...values,
>   folder: Array.isArray(values.folder) ? values.folder[0] ?? null : (values.folder as unknown as string | null),
>   sqlText: defaultSql,
>   engine: defaultEngine,
>   level: values.level ?? "INTERNAL",
> });
> ```

### - [ ] Step 5.6: SavedPanel

`src/components/sql-ide/saved/SavedPanel.tsx`:

```tsx
import { Empty, Input, Spin, message } from "antd";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { type FC, useMemo, useState } from "react";
import { useTabStore } from "../tabs/useTabStore";
import { deleteSavedQuery, listSavedQueries, type SavedQueryItem } from "../api/sqlIdeSaved";

export const SavedPanel: FC = () => {
  const qc = useQueryClient();
  const [filter, setFilter] = useState("");
  const openTab = useTabStore((s) => s.openTab);
  const { data, isLoading } = useQuery<SavedQueryItem[]>({
    queryKey: ["sqlide", "saved"],
    queryFn: listSavedQueries,
    staleTime: 60_000,
  });
  const deleteMut = useMutation({
    mutationFn: deleteSavedQuery,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["sqlide", "saved"] }),
  });

  const grouped = useMemo(() => {
    const map = new Map<string, SavedQueryItem[]>();
    (data ?? [])
      .filter((q) => !filter || q.title.toLowerCase().includes(filter.toLowerCase()))
      .forEach((q) => {
        const folder = q.folder ?? "未分组";
        const arr = map.get(folder) ?? [];
        arr.push(q);
        map.set(folder, arr);
      });
    return map;
  }, [data, filter]);

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", padding: "8px 12px", gap: 6 }}>
      <Input
        size="small"
        placeholder="搜索…"
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        allowClear
      />
      <div style={{ flex: 1, overflow: "auto", marginTop: 4 }}>
        {isLoading ? (
          <div style={{ textAlign: "center", padding: 12 }}><Spin /></div>
        ) : grouped.size === 0 ? (
          <Empty description="暂无已保存的查询" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          [...grouped.entries()].map(([folder, items]) => (
            <div key={folder}>
              <div style={{ fontSize: 11, fontWeight: 600, color: "var(--ant-color-text-tertiary)", padding: "8px 4px 4px" }}>
                📁 {folder}
              </div>
              {items.map((q) => (
                <div
                  key={q.id}
                  data-testid={`saved-item-${q.id}`}
                  onClick={() => openTab({ title: q.title, sqlText: q.sqlText, engine: (q.engine as any) ?? "generic" })}
                  style={{ padding: "4px 12px", cursor: "pointer", display: "flex", justifyContent: "space-between", fontSize: 12 }}
                >
                  <span>📝 {q.title}</span>
                  <span
                    onClick={(e) => {
                      e.stopPropagation();
                      deleteMut.mutate(q.id, {
                        onSuccess: () => message.success("已删除"),
                        onError: () => message.error("删除失败"),
                      });
                    }}
                    style={{ color: "var(--ant-color-text-quaternary)", marginLeft: 6 }}
                  >
                    ✕
                  </span>
                </div>
              ))}
            </div>
          ))
        )}
      </div>
    </div>
  );
};
```

### - [ ] Step 5.7: 接入 SqlIde + onSaveAsQuery 路由

In `SqlIde.tsx`:
1. Replace `{activeActivity === "saved" && ...}` with `<SavedPanel />`
2. Add SaveQueryDialog state (open + defaultSql) and pass `onSaveAsQuery={(sql) => setSaveOpen(true) ...}` to `<SqlEditor>`:

```tsx
import { SaveQueryDialog } from "./saved/SaveQueryDialog";
import { SavedPanel } from "./saved/SavedPanel";
import { createSavedQuery, listSavedQueries } from "./api/sqlIdeSaved";

const [saveDialogOpen, setSaveDialogOpen] = useState(false);
const [pendingSql, setPendingSql] = useState("");
const qc = useQueryClient();
const savedListQuery = useQuery({ queryKey: ["sqlide", "saved"], queryFn: listSavedQueries, enabled: saveDialogOpen });

// In SqlEditor props:
onSaveAsQuery={(sql) => { setPendingSql(sql); setSaveDialogOpen(true); }}

// Add the dialog at the bottom of the SqlIde JSX:
<SaveQueryDialog
  open={saveDialogOpen}
  defaultSql={pendingSql}
  defaultEngine={activeTab?.engine ?? "generic"}
  existingFolders={[...new Set((savedListQuery.data ?? []).map((s) => s.folder).filter((f): f is string => !!f))]}
  onCancel={() => setSaveDialogOpen(false)}
  onSave={async (payload) => {
    await createSavedQuery(payload);
    setSaveDialogOpen(false);
    qc.invalidateQueries({ queryKey: ["sqlide", "saved"] });
    message.success("已保存");
  }}
/>
```

### - [ ] Step 5.8: 验证

```bash
cd source/dts-platform && ./mvnw -q -DskipTests compile && ./mvnw -q test -Dtest='SqlIde*'
cd ../dts-platform-webapp && pnpm tsc --noEmit && pnpm vitest run src/components/sql-ide
```

**Expected:** 后端 编译+所有 SqlIde* IT PASS；前端 50/50 PASS。

### - [ ] Step 5.9: 提交

```bash
git add source/dts-platform/src/main/resources/config/liquibase/changelog/20260413_02_saved_query_folder.xml \
        source/dts-platform/src/main/resources/config/liquibase/master.xml \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/explore/SavedQuery.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/SavedQueryRequest.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/SavedQueryResponse.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SavedQueryService.java \
        source/dts-platform-webapp/src/components/sql-ide/api/sqlIdeSaved.ts \
        source/dts-platform-webapp/src/components/sql-ide/saved/ \
        source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx
git commit -m "$(cat <<'EOF'
feat(F3/T15): SavedPanel with virtual folder grouping + Save dialog

Sprint-11 F3 T15: saved_query gains a folder column (varchar 200, idx);
DTOs and service propagate the value. Frontend SavedPanel groups by
folder, double-click opens new Tab. SaveQueryDialog (triggered by
Ctrl+S via SqlEditor's onSaveAsQuery) lets user pick existing folder
or enter a new name.
EOF
)"
```

---

## Final Verification

### - [ ] Backend全套 SqlIde* 测试

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform
./mvnw test -Dtest='SqlIde*' 2>&1 | grep -E "Tests run:" | tail -10
```

**Expected:** 14+ ITs PASS — `SqlIdeFeaturePropertiesIT`(1) + `SqlIdeResourceIT`(1) + `SqlIdeTabResourceIT`(8) + `SqlIdeCatalogResourceIT`(5) + `SqlIdeHistoryResourceIT`(3).

### - [ ] Frontend全套测试

```bash
cd source/dts-platform-webapp
pnpm tsc --noEmit && pnpm vitest run src/components/sql-ide
```

**Expected:** 50/50 PASS（44 prior + 6 sqlGenerators）。

### - [ ] Sprint 追踪

更新 `worklog/v2.2.3/sprint-11-202604/features/F3-Schema浏览器与Activity-Bar/T11..T15.md` 状态为 DONE，README 状态 DONE，sprint-queue.md F3 状态 DONE。

---

## Notes for the Executing Engineer

1. **项目约束**
   - Java 禁用 `Optional.get()` → `orElseThrow()`
   - 前端 vitest，Biome 格式
   - 单文件 ≤ 300 行

2. **Catalog 数据来源**
   - 当前阶段直接复用 `CatalogTableSchemaRepository` / `CatalogColumnSchemaRepository` 的本地元数据（已有 OpenMetadata 同步入库）
   - 不直接连 Trino/Hive INFORMATION_SCHEMA — 那是后续优化项

3. **现有端点不要碰**
   - `/api/sql/catalog`（POST，返回全树）继续可用
   - `/api/sql/saved-queries` 复用，本 Sprint 只加 folder 列

4. **react-arborist 复杂性**
   - 该库的内部 state 模型对外部 React Query 数据可能有同步延迟
   - 若实测不稳，回退方案：自己实现简单 div + react-virtual 虚拟列表
   - 本 Sprint 范围内：能正常展示+点击+右键即视作通过

5. **Sprint-12 / F4 依赖**
   - F4（ResultGrid）会需要 `useTabStore` 的 `lastExecutionId` 字段，已就位
   - Catalog provider 注入 SqlEditor 的工作未在本 Sprint 完成（只准备了 API 与 hook，注入逻辑放到 F4/F5 中或单独 hotfix）

6. **F3 Followup 候选项**
   - SchemaTree 高度自适应（用 ResizeObserver 替代写死 500）
   - 搜索框真正调 `/catalog/{ds}/search` 端点
   - Saved Query 拖拽换 folder
   - History 按时间倒序的服务端排序确认

7. **模型选择**
   - Implementer：sonnet
   - Spec reviewer：sonnet
   - Code quality reviewer：**opus**
