# BI 数据管理重构 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 BI 分析"数据管理"从卡片网格改为双表格布局，删除独立数据库连接管理，复用平台数据源列表，Excel/CSV 上传改为弹窗。

**Architecture:** 重写 DataPage.tsx 为单页面双表格（数据湖列表 + 我的上传）。上传弹窗复用 UploadedDataEditor 组件。删除 DatabaseNewPage / DatabaseEditPage 及其路由。后端零变更，全部复用现有 API。

**Tech Stack:** React 18, antd Table/Modal, analyticsApi (fetch-based), react-router

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| **Rewrite** | `src/analytics/pages/DataPage.tsx` | 双表格页面（数据湖列表 + 我的上传 + 上传弹窗） |
| **Delete** | `src/analytics/pages/DatabaseNewPage.tsx` | 不再需要 |
| **Delete** | `src/analytics/pages/DatabaseEditPage.tsx` | 不再需要 |
| **Modify** | `src/routes/sections/dashboard/static-routes.tsx` | 删除 2 条路由 + 2 个 lazy import |
| **No change** | `src/analytics/components/UploadedDataEditor.tsx` | 复用，props 已满足需求 |
| **No change** | `src/analytics/api/analyticsApi.ts` | 所有 API 已存在 |

---

### Task 1: 重写 DataPage — 数据湖列表表格

**Files:**
- Rewrite: `source/dts-platform-webapp/src/analytics/pages/DataPage.tsx`

- [ ] **Step 1: 清空 DataPage，搭建页面骨架**

```tsx
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { Table, Tag, Button, Input, Modal, message, Spin } from "antd";
import type { ColumnsType } from "antd/es/table";
import { analyticsApi, type PlatformDataSourceItem, type DatabaseListItem, type CurrentUser, type MyUploadItem } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import UploadedDataEditor from "../components/UploadedDataEditor";
import { getEffectiveLocale, t } from "../i18n";

interface LoadState<T> { loading: boolean; data: T | null; error: unknown }

export default function DataPage() {
	const locale = getEffectiveLocale();

	// ── state ──
	const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
	const [dsState, setDsState] = useState<LoadState<PlatformDataSourceItem[]>>({ loading: true, data: null, error: null });
	const [databases, setDatabases] = useState<DatabaseListItem[]>([]);
	const [dataLakeId, setDataLakeId] = useState<number | null>(null);
	const [uploadsState, setUploadsState] = useState<LoadState<MyUploadItem[]>>({ loading: true, data: null, error: null });
	const [uploadOpen, setUploadOpen] = useState(false);
	const [dsSearch, setDsSearch] = useState("");
	const [uploadSearch, setUploadSearch] = useState("");

	const isDataAdmin = currentUser?.is_data_admin || currentUser?.is_superuser;

	// ── load data ──
	useEffect(() => {
		analyticsApi.currentUser().then(setCurrentUser).catch(() => {});
		loadDataSources();
		loadDatabasesAndUploads();
	}, []);

	async function loadDataSources() {
		setDsState({ loading: true, data: null, error: null });
		try {
			const data = await analyticsApi.listPlatformDataSources();
			setDsState({ loading: false, data, error: null });
		} catch (e) {
			setDsState({ loading: false, data: null, error: e });
		}
	}

	async function loadDatabasesAndUploads() {
		try {
			const dbRes = await analyticsApi.listDatabases();
			const dbs = dbRes?.data ?? [];
			setDatabases(dbs);
			const lake = dbs.find((d: DatabaseListItem) => d.is_system === true);
			if (lake) {
				setDataLakeId(lake.id);
				loadUploads(lake.id);
			} else {
				setUploadsState({ loading: false, data: [], error: null });
			}
		} catch (e) {
			setUploadsState({ loading: false, data: null, error: e });
		}
	}

	async function loadUploads(lakeId: number) {
		setUploadsState({ loading: true, data: null, error: null });
		try {
			const data = await analyticsApi.listMyUploads(lakeId);
			setUploadsState({ loading: false, data, error: null });
		} catch (e) {
			setUploadsState({ loading: false, data: null, error: e });
		}
	}

	// ── placeholder: tables rendered in next steps ──
	return (
		<PageContainer>
			<PageHeader title={t(locale, "data.title")} />
			<div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
				{/* 数据湖列表 — Task 1 Step 2 */}
				{/* 我的上传 — Task 2 */}
			</div>
		</PageContainer>
	);
}
```

- [ ] **Step 2: 添加数据湖列表表格**

在 return 的 `{/* 数据湖列表 */}` 位置替换为：

```tsx
<section>
	<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
		<h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>数据湖列表</h3>
		<Input.Search
			placeholder="搜索名称或类型"
			allowClear
			style={{ width: 240 }}
			value={dsSearch}
			onChange={(e) => setDsSearch(e.target.value)}
		/>
	</div>
	<Table<PlatformDataSourceItem>
		rowKey="id"
		size="small"
		loading={dsState.loading}
		dataSource={filteredDataSources}
		pagination={false}
		columns={dsColumns}
		locale={{ emptyText: "暂无数据源" }}
	/>
</section>
```

在组件内、return 之前添加列定义和过滤逻辑：

```tsx
// ── 数据湖列表 ──
const filteredDataSources = useMemo(() => {
	const list = dsState.data ?? [];
	if (!dsSearch.trim()) return list;
	const q = dsSearch.toLowerCase();
	return list.filter((d) => (d.name ?? "").toLowerCase().includes(q) || (d.type ?? "").toLowerCase().includes(q));
}, [dsState.data, dsSearch]);

// 将 platform data source ID 映射到 analytics database ID（用于同步/查看）
const platformToAnalyticsDb = useMemo(() => {
	const map = new Map<string, number>();
	for (const db of databases) {
		// analytics database 的 details 中保存了 platformDataSourceId
		// 这里用 name 做近似匹配（平台数据源名 = analytics 库名）
		if (db.name) map.set(db.name, db.id);
	}
	return map;
}, [databases]);

async function handleSync(dbId: number) {
	try {
		await analyticsApi.syncDatabaseSchema(dbId);
		message.success("元数据同步完成");
	} catch {
		message.error("同步失败");
	}
}

const dsColumns: ColumnsType<PlatformDataSourceItem> = [
	{ title: "名称", dataIndex: "name", key: "name", ellipsis: true },
	{
		title: "类型", dataIndex: "type", key: "type", width: 120,
		render: (v: string) => <Tag>{(v ?? "").toUpperCase()}</Tag>,
	},
	{ title: "连接地址", dataIndex: "jdbcUrl", key: "jdbcUrl", ellipsis: true },
	{
		title: "状态", dataIndex: "status", key: "status", width: 100,
		render: (v: string) => (
			<Tag color={v === "ACTIVE" ? "success" : "default"}>
				{v === "ACTIVE" ? "已连接" : "未连接"}
			</Tag>
		),
	},
	{
		title: "操作", key: "actions", width: 180,
		render: (_: unknown, record: PlatformDataSourceItem) => {
			const analyticsDbId = platformToAnalyticsDb.get(record.name ?? "");
			return (
				<span style={{ display: "flex", gap: 8 }}>
					{isDataAdmin && analyticsDbId != null && (
						<Button type="link" size="small" onClick={() => handleSync(analyticsDbId)}>
							同步元数据
						</Button>
					)}
					{analyticsDbId != null && (
						<Link to={`/bi/data/${analyticsDbId}`}>
							<Button type="link" size="small">查看表</Button>
						</Link>
					)}
				</span>
			);
		},
	},
];
```

- [ ] **Step 3: 验证数据湖列表渲染**

Run: 在浏览器访问 `/bi/data`，确认：
- 表格加载并展示平台数据源
- 类型列显示 Tag
- 状态列显示"已连接"/"未连接"
- 管理员可见"同步元数据"
- "查看表"链接跳转正常

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/DataPage.tsx
git commit -m "refactor(analytics): rewrite DataPage with data lake table"
```

---

### Task 2: 添加"我的上传"表格和上传弹窗

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/DataPage.tsx`

- [ ] **Step 1: 添加"我的上传"表格**

在 return 的 `{/* 我的上传 */}` 位置替换为：

```tsx
<section>
	<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
		<h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>我的上传</h3>
		<div style={{ display: "flex", gap: 8, alignItems: "center" }}>
			<Input.Search
				placeholder="搜索表名或文件名"
				allowClear
				style={{ width: 240 }}
				value={uploadSearch}
				onChange={(e) => setUploadSearch(e.target.value)}
			/>
			<Button type="primary" disabled={dataLakeId == null} onClick={() => setUploadOpen(true)}>
				上传 Excel/CSV
			</Button>
		</div>
	</div>
	<Table<MyUploadItem>
		rowKey="id"
		size="small"
		loading={uploadsState.loading}
		dataSource={filteredUploads}
		pagination={false}
		columns={uploadColumns}
		locale={{ emptyText: "暂无上传数据" }}
	/>

	{/* 上传弹窗 */}
	<Modal
		title="上传 Excel/CSV"
		open={uploadOpen}
		onCancel={() => setUploadOpen(false)}
		footer={null}
		width={720}
		destroyOnClose
	>
		{dataLakeId != null && (
			<UploadedDataEditor
				databaseId={dataLakeId}
				onComplete={(result) => {
					setUploadOpen(false);
					message.success(`已上传 ${result.tableName}，共 ${result.rowCount} 行`);
					loadUploads(dataLakeId);
					analyticsApi.syncDatabaseSchema(dataLakeId).catch(() => {});
				}}
			/>
		)}
	</Modal>
</section>
```

- [ ] **Step 2: 添加上传表格列定义和过滤**

在组件内、return 之前，dsColumns 之后添加：

```tsx
// ── 我的上传 ──
const filteredUploads = useMemo(() => {
	const list = uploadsState.data ?? [];
	if (!uploadSearch.trim()) return list;
	const q = uploadSearch.toLowerCase();
	return list.filter((u) =>
		(u.name ?? "").toLowerCase().includes(q) ||
		(u.display_name ?? "").toLowerCase().includes(q),
	);
}, [uploadsState.data, uploadSearch]);

async function handleDeleteUpload(tableName: string) {
	if (dataLakeId == null) return;
	try {
		await analyticsApi.deleteUploadTable(dataLakeId, tableName);
		message.success("已删除");
		loadUploads(dataLakeId);
	} catch {
		message.error("删除失败");
	}
}

const uploadColumns: ColumnsType<MyUploadItem> = [
	{ title: "表名", dataIndex: "name", key: "name", ellipsis: true },
	{ title: "显示名", dataIndex: "display_name", key: "display_name", ellipsis: true },
	{ title: "Schema", dataIndex: "schema", key: "schema", width: 100 },
	{
		title: "上传时间", dataIndex: "created_at", key: "created_at", width: 160,
		render: (v: string) => v ? new Date(v).toLocaleString("zh-CN") : "-",
	},
	{
		title: "操作", key: "actions", width: 140,
		render: (_: unknown, record: MyUploadItem) => (
			<span style={{ display: "flex", gap: 8 }}>
				{dataLakeId != null && (
					<Link to={`/bi/data/${dataLakeId}`}>
						<Button type="link" size="small">查看</Button>
					</Link>
				)}
				<Button
					type="link"
					size="small"
					danger
					onClick={() => {
						Modal.confirm({
							title: "确认删除",
							content: `确定要删除上传表「${record.name}」吗？此操作不可撤销。`,
							okText: "删除",
							okType: "danger",
							cancelText: "取消",
							onOk: () => handleDeleteUpload(record.name),
						});
					}}
				>
					删除
				</Button>
			</span>
		),
	},
];
```

- [ ] **Step 3: 验证上传流程**

Run: 在浏览器访问 `/bi/data`，确认：
- "我的上传"表格展示当前用户的上传记录
- 点击"上传 Excel/CSV"打开弹窗
- 上传 .xlsx 文件成功后弹窗关闭，表格刷新
- 上传 .csv 文件成功
- 删除确认弹窗正常，删除后表格刷新
- "查看"链接跳转到 DatabaseDetailPage

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/DataPage.tsx
git commit -m "feat(analytics): add upload table and modal to DataPage"
```

---

### Task 3: 删除旧页面文件

**Files:**
- Delete: `source/dts-platform-webapp/src/analytics/pages/DatabaseNewPage.tsx`
- Delete: `source/dts-platform-webapp/src/analytics/pages/DatabaseEditPage.tsx`

- [ ] **Step 1: 确认无其他引用**

Run:
```bash
grep -rn "DatabaseNewPage\|DatabaseEditPage" source/dts-platform-webapp/src/ --include='*.ts' --include='*.tsx' | grep -v 'static-routes.tsx' | grep -v '__tests__'
```

Expected: 无输出（仅路由文件引用，下一步处理）

- [ ] **Step 2: 删除文件**

```bash
rm source/dts-platform-webapp/src/analytics/pages/DatabaseNewPage.tsx
rm source/dts-platform-webapp/src/analytics/pages/DatabaseEditPage.tsx
```

- [ ] **Step 3: Commit**

```bash
git add -u source/dts-platform-webapp/src/analytics/pages/DatabaseNewPage.tsx
git add -u source/dts-platform-webapp/src/analytics/pages/DatabaseEditPage.tsx
git commit -m "refactor(analytics): remove DatabaseNewPage and DatabaseEditPage"
```

---

### Task 4: 清理路由

**Files:**
- Modify: `source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx`

- [ ] **Step 1: 删除 lazy import**

移除这两行：
```typescript
const DatabaseNewPage = lazy(() => import("@/analytics/pages/DatabaseNewPage"));
const DatabaseEditPage = lazy(() => import("@/analytics/pages/DatabaseEditPage"));
```

- [ ] **Step 2: 删除路由定义**

移除这两条路由：
```typescript
{ path: "bi/data/new", element: <S><DatabaseNewPage /></S> },
{ path: "bi/data/:dbId/edit", element: <S><DatabaseEditPage /></S> },
```

- [ ] **Step 3: 检查 DatabaseDetailPage 中的编辑链接**

Run:
```bash
grep -n 'edit\|new\|DatabaseNew\|DatabaseEdit' source/dts-platform-webapp/src/analytics/pages/DatabaseDetailPage.tsx
```

如有指向 `/bi/data/:id/edit` 或 `/bi/data/new` 的链接，移除对应 JSX。

- [ ] **Step 4: 验证构建**

Run:
```bash
cd source/dts-platform-webapp && npx vite build --mode development 2>&1 | tail -5
```

Expected: `✓ built in ...` 无错误

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx
git add source/dts-platform-webapp/src/analytics/pages/DatabaseDetailPage.tsx  # 如有修改
git commit -m "refactor(analytics): remove /bi/data/new and /bi/data/:id/edit routes"
```

---

### Task 5: 端到端验证

- [ ] **Step 1: 验证数据湖列表**

访问 `/bi/data`：
- 数据湖列表加载平台数据源
- 类型/状态 Tag 正确
- 管理员可见"同步元数据"
- "查看表"跳转正常

- [ ] **Step 2: 验证上传**

- 点击"上传 Excel/CSV"
- 上传 .xlsx → 弹窗关闭，toast 提示，表格刷新
- 上传 .csv → 同上
- 删除上传记录 → 确认弹窗 → 删除成功

- [ ] **Step 3: 验证路由清理**

- `/bi/data/new` → 404 或空白（不崩溃）
- `/bi/data/:id/edit` → 404 或空白
- `/bi/data/:id` → DatabaseDetailPage 正常
- `/bi/data/:id/tables/:tid` → TableDetailPage 正常

- [ ] **Step 4: 验证构建**

```bash
cd source/dts-platform-webapp && npx vite build --mode development 2>&1 | tail -5
```

Expected: `✓ built in ...`

- [ ] **Step 5: Final commit (如有修复)**

```bash
git add -A
git commit -m "fix(analytics): DataPage refactor polish"
```
