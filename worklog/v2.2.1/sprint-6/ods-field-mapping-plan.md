# Excel 入湖关联 ODS 表字段映射 — 实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Excel 入湖配置时，支持关联 ODS 表自动按位置映射字段名，替代手动逐个修改 29 个 field_N 占位符。

**Architecture:** 纯前端方案 + 后端字典扩展。前端在字段编辑表格上方新增 ODS 表选择器，选择后调用已有 `listTables`/`listColumns` API 获取 ODS 列定义，按位置覆盖 Excel 自动生成的字段名。后端仅扩展 `SqlFieldNameResolver.EXACT_TRANSLATIONS` 字典。

**Tech Stack:** React + antd (Select, Button, Table, Tag) + 已有 sql-workbench API

---

## 前置信息

### 关键文件

| 文件 | 用途 |
|------|------|
| `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx` | 入湖向导，字段编辑在 Step 0 (file flow) |
| `source/dts-platform-webapp/src/api/sql-workbench.ts` | 已有 `listTables()` / `listColumns()` API |
| `source/dts-platform-webapp/src/api/ingestion.ts` | `FileUploadResult` / `DefaultDestinationStatus` 类型 |
| `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/SqlFieldNameResolver.java` | 字段名解析字典 |

### datasourceId 获取方案

`DefaultDestinationStatus` 不包含 `datasourceId`。通过前端已加载的 `dataSources` 状态（`InfraDataSource[]`）按名称匹配 `defaultDestinationStatus.destinationName` 获取：

```typescript
const lakeDatasourceId = useMemo(() => {
  if (!defaultDestinationStatus?.destinationName || !dataSources.length) return null;
  const match = dataSources.find(ds => ds.name === defaultDestinationStatus.destinationName);
  return match?.id ?? null;
}, [dataSources, defaultDestinationStatus]);
```

### 列编辑表格位置

`TransformCreatePage.tsx` 第 2999-3147 行，`<Table>` 组件，`dataSource={fileUploadResult.columns || []}`。

### 已有 API

```typescript
// sql-workbench.ts
listTables(datasourceId: string) → TableInfo[] // { schema, name, type }
listColumns(datasourceId: string, schema: string, table: string) → ColumnInfo[] // { name, type, nullable }
```

---

## Task 1: 新增 ODS 关联状态变量

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`

**Step 1: 添加 import**

在已有 import 区域添加 sql-workbench API 导入（如尚未导入）：

```typescript
import { listTables, listColumns, type TableInfo, type ColumnInfo } from "@/api/sql-workbench";
```

**Step 2: 添加状态变量**

在 `fileUploadResult` 状态附近（约第 1192 行）添加 ODS 关联相关状态：

```typescript
// ODS 表关联
const [odsTableList, setOdsTableList] = useState<TableInfo[]>([]);
const [odsTableLoading, setOdsTableLoading] = useState(false);
const [selectedOdsTable, setSelectedOdsTable] = useState<string | undefined>(undefined);
const [odsColumns, setOdsColumns] = useState<ColumnInfo[]>([]);
const [odsColumnsLoading, setOdsColumnsLoading] = useState(false);
const [odsMatchApplied, setOdsMatchApplied] = useState(false);
```

**Step 3: 添加 lakeDatasourceId 派生值**

在 `selectedDataSource` memo 附近（约第 1229 行）添加：

```typescript
const lakeDatasourceId = useMemo(() => {
  if (!defaultDestinationStatus?.destinationName || !dataSources.length) return null;
  const match = dataSources.find(ds => ds.name === defaultDestinationStatus.destinationName);
  return match?.id ?? null;
}, [dataSources, defaultDestinationStatus]);
```

**Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx
git commit -m "feat(S6-012): add ODS table association state variables"
```

---

## Task 2: 加载 ODS 表列表

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`

**Step 1: 添加加载 ODS 表列表函数**

在组件内其他 handler 函数附近添加：

```typescript
const loadOdsTables = useCallback(async () => {
  if (!lakeDatasourceId) return;
  try {
    setOdsTableLoading(true);
    const tables = await listTables(lakeDatasourceId);
    // 只保留 ods_ 前缀的表
    const odsTables = (Array.isArray(tables) ? tables : []).filter(
      t => t.name?.toLowerCase().startsWith("ods_")
    );
    setOdsTableList(odsTables);
  } catch (err: any) {
    console.error("Failed to load ODS tables:", err);
    setOdsTableList([]);
  } finally {
    setOdsTableLoading(false);
  }
}, [lakeDatasourceId]);
```

**Step 2: 添加选择 ODS 表后加载列定义**

```typescript
const handleOdsTableSelect = useCallback(async (tableName: string | undefined) => {
  setSelectedOdsTable(tableName);
  setOdsColumns([]);
  setOdsMatchApplied(false);
  if (!tableName || !lakeDatasourceId) return;
  try {
    setOdsColumnsLoading(true);
    // 从表列表中获取 schema
    const tableInfo = odsTableList.find(t => t.name === tableName);
    const schema = tableInfo?.schema || "public";
    const cols = await listColumns(lakeDatasourceId, schema, tableName);
    setOdsColumns(Array.isArray(cols) ? cols : []);
  } catch (err: any) {
    console.error("Failed to load ODS columns:", err);
    setOdsColumns([]);
  } finally {
    setOdsColumnsLoading(false);
  }
}, [lakeDatasourceId, odsTableList]);
```

**Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx
git commit -m "feat(S6-012): add ODS table list loading and column fetching"
```

---

## Task 3: 实现自动匹配逻辑

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`

**Step 1: 添加自动匹配 handler**

```typescript
const applyOdsMapping = useCallback(() => {
  if (!odsColumns.length || !fileUploadResult?.columns?.length) return;

  const excelCols = [...fileUploadResult.columns];
  const odsLen = odsColumns.length;
  const excelLen = excelCols.length;

  // 按位置对齐：Excel 第 i 列的字段名替换为 ODS 第 i 列的 column_name
  for (let i = 0; i < Math.min(odsLen, excelLen); i++) {
    excelCols[i] = {
      ...excelCols[i],
      name: odsColumns[i].name,
      _odsMatched: true,  // 标记为已匹配
    };
  }

  // Excel 多余列标记为未关联
  for (let i = odsLen; i < excelLen; i++) {
    excelCols[i] = {
      ...excelCols[i],
      _odsMatched: false,
      _odsExtra: true,
    };
  }

  setFileUploadResult({ ...fileUploadResult, columns: excelCols });
  setOdsMatchApplied(true);
}, [odsColumns, fileUploadResult]);
```

**Step 2: 计算未匹配的 ODS 字段（ODS 有但 Excel 无）**

```typescript
const unmatchedOdsFields = useMemo(() => {
  if (!odsMatchApplied || !odsColumns.length) return [];
  const excelLen = fileUploadResult?.columns?.length || 0;
  if (odsColumns.length <= excelLen) return [];
  return odsColumns.slice(excelLen);
}, [odsMatchApplied, odsColumns, fileUploadResult?.columns?.length]);
```

**Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx
git commit -m "feat(S6-012): implement positional ODS field mapping logic"
```

---

## Task 4: 添加 ODS 关联 UI

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`

**Step 1: 在字段编辑表格上方添加 ODS 关联区域**

在第 2998 行 `</Space>` 和第 2999 行 `<Table` 之间插入：

```tsx
{/* ODS 表关联 */}
{lakeDatasourceId && fileUploadResult?.columns?.length > 0 && (
  <div style={{ marginBottom: 12, padding: "8px 12px", background: "#fafafa", borderRadius: 6, border: "1px solid #f0f0f0" }}>
    <Space wrap>
      <Text type="secondary">关联 ODS 表：</Text>
      <Select
        size="small"
        style={{ width: 280 }}
        placeholder="选择 ODS 表以自动匹配字段名"
        allowClear
        showSearch
        loading={odsTableLoading}
        value={selectedOdsTable}
        onFocus={() => { if (!odsTableList.length) loadOdsTables(); }}
        onChange={handleOdsTableSelect}
        options={odsTableList.map(t => ({ label: t.name, value: t.name }))}
        filterOption={(input, option) =>
          (option?.label as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
        }
      />
      <Button
        size="small"
        type="primary"
        loading={odsColumnsLoading}
        disabled={!odsColumns.length}
        onClick={applyOdsMapping}
      >
        自动匹配
      </Button>
      {odsMatchApplied && (
        <Text type="success" style={{ color: "#52c41a" }}>
          ✓ 已匹配 {Math.min(odsColumns.length, fileUploadResult.columns?.length || 0)} 个字段
        </Text>
      )}
    </Space>
  </div>
)}
```

**Step 2: Commit**

```bash
git add source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx
git commit -m "feat(S6-012): add ODS table selector UI above column editor"
```

---

## Task 5: 添加匹配状态标记和未匹配警告

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`

**Step 1: 修改字段名列渲染，添加匹配状态标记**

在字段编辑表格的"字段名"列（约第 3021-3036 行），修改 render 函数：

```tsx
{
  title: "字段名",
  dataIndex: "name",
  render: (value: string, record: any, index: number) => (
    <Space size={4}>
      <Input
        size="small"
        value={value}
        placeholder="英文字段名"
        style={record._odsExtra ? { color: "#999" } : undefined}
        onChange={(e) => {
          const cols = [...(fileUploadResult.columns || [])];
          cols[index] = { ...cols[index], name: e.target.value };
          setFileUploadResult({ ...fileUploadResult, columns: cols });
        }}
      />
      {odsMatchApplied && record._odsMatched && (
        <Tag color="green" style={{ margin: 0 }}>ODS</Tag>
      )}
      {odsMatchApplied && record._odsExtra && (
        <Tag color="default" style={{ margin: 0 }}>未关联</Tag>
      )}
    </Space>
  ),
},
```

**Step 2: 在表格下方添加未匹配 ODS 字段红色警告**

在列编辑 `<Table>` 的结束标签 `/>` 之后（约第 3147 行之后），添加：

```tsx
{unmatchedOdsFields.length > 0 && (
  <div style={{ marginTop: 8, padding: "8px 12px", background: "#fff2f0", border: "1px solid #ffccc7", borderRadius: 6 }}>
    <Text type="danger" strong style={{ display: "block", marginBottom: 4 }}>
      ⚠ 以下 ODS 字段缺少对应的 Excel 列（将导致下游数仓数据不完整）：
    </Text>
    <Space wrap>
      {unmatchedOdsFields.map((col, i) => (
        <Tag key={i} color="error">{col.name}</Tag>
      ))}
    </Space>
  </div>
)}
```

**Step 3: 确认 Tag 已导入**

检查文件顶部 antd import 中是否包含 `Tag`，如未包含则添加。

**Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx
git commit -m "feat(S6-012): add match status tags and unmatched ODS fields warning"
```

---

## Task 6: SqlFieldNameResolver 字典扩展 (S6-013)

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/SqlFieldNameResolver.java`

**Step 1: 在 `buildExactTranslations()` 方法中追加项目进度 Excel 29 字段**

在 `putExact(map, "状态", "state");` 之后、`return` 之前追加：

```java
// 项目进度 Excel 29 字段
putExact(map, "项目编号", "project_no");
putExact(map, "分系统/分任务", "subsystem");
putExact(map, "节点任务及目标", "node_task");
putExact(map, "节点计划时间", "plan_date");
putExact(map, "节点计划周数", "plan_week");
putExact(map, "节点类型", "node_type");
putExact(map, "负责人", "owner");
putExact(map, "责任科室", "dept");
putExact(map, "分管室领导", "dept_leader");
putExact(map, "完成情况", "completion_status");
putExact(map, "协同部门", "collab_dept");
putExact(map, "责任监管部门", "supervisor_dept");
putExact(map, "延期预计完成时间", "delay_expected_date");
putExact(map, "未完成原因及当前进展", "incomplete_reason");
putExact(map, "风险等级", "risk_level");
putExact(map, "主要风险内容及措施", "risk_content");
putExact(map, "延期影响分析", "delay_impact");
putExact(map, "实际完成时间", "actual_date");
putExact(map, "实际完成周数", "actual_week");
putExact(map, "所领导", "institute_leader");
putExact(map, "来源", "source");
putExact(map, "延期项目原计划时间", "original_plan_date");
putExact(map, "计划延误时间（已变更）", "delay_days_changed");
putExact(map, "计划延误时间（未变更）", "delay_days_unchanged");
putExact(map, "是否提交延期申请", "delay_applied");
putExact(map, "项目主管", "project_manager");
putExact(map, "最后更新时间", "last_update_time");
putExact(map, "填写人", "filled_by");
putExact(map, "亮点工作", "highlight");
```

**Step 2: 在 `buildTermTranslations()` 中补充通用术语**

在已有术语之后追加（避免重复）：

```java
map.put("项目", "project");
map.put("节点", "node");
map.put("任务", "task");
map.put("计划", "plan");
map.put("负责人", "owner");
map.put("科室", "dept");
map.put("领导", "leader");
map.put("完成", "completion");
map.put("情况", "status");
map.put("协同", "collab");
map.put("监管", "supervisor");
map.put("延期", "delay");
map.put("原因", "reason");
map.put("风险", "risk");
map.put("等级", "level");
map.put("措施", "measure");
map.put("影响", "impact");
map.put("分析", "analysis");
map.put("实际", "actual");
map.put("周数", "week");
map.put("填写", "filled");
map.put("亮点", "highlight");
map.put("工作", "work");
map.put("主管", "manager");
map.put("更新", "update");
map.put("变更", "changed");
map.put("申请", "applied");
```

**Step 3: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/SqlFieldNameResolver.java
git commit -m "feat(S6-013): extend SqlFieldNameResolver dictionary with project progress fields"
```

---

## Task 7: 重置 ODS 关联状态（文件重新上传时）

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`

**Step 1: 在文件上传/Sheet 切换成功后重置 ODS 状态**

找到 `setFileUploadResult(parsed)` 的位置（文件上传成功回调和 Sheet 切换回调），在其后添加：

```typescript
setSelectedOdsTable(undefined);
setOdsColumns([]);
setOdsMatchApplied(false);
```

这确保重新上传文件或切换 Sheet 后，ODS 关联状态被清除。

**Step 2: Commit**

```bash
git add source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx
git commit -m "feat(S6-012): reset ODS mapping state on file re-upload or sheet switch"
```

---

## Task 8: 构建验证

**Step 1: 前端构建**

```bash
cd source/dts-platform-webapp && npm run build
```

确认无 TypeScript/编译错误。

**Step 2: 后端构建**

```bash
cd source/dts-platform && ./mvnw compile -pl . -q
```

确认 SqlFieldNameResolver 编译通过。

**Step 3: Docker 重建并测试**

```bash
docker compose build platform webapp && docker compose up -d platform webapp
```

打开入湖配置页面 → 上传 Excel → 在字段编辑步骤验证：
1. 出现"关联 ODS 表"选择器
2. 能筛选并选择 ods_ 前缀表
3. 点击"自动匹配"后字段名被 ODS 列名覆盖
4. 已匹配字段显示绿色 ODS 标签
5. 如有未匹配 ODS 字段，表格下方出现红色警告

---

## 改动汇总

| 层 | 文件 | 改动 |
|---|---|---|
| 前端 | `TransformCreatePage.tsx` | 新增 ODS 关联 UI + 匹配逻辑 (~80 行) |
| 后端 | `SqlFieldNameResolver.java` | EXACT_TRANSLATIONS + TERM_TRANSLATIONS 扩展 (~60 行) |
| 前端 | `ingestion.ts` | 无改动 |
| 后端 | `DefaultDestinationSyncService.java` | 无改动 |
