# DBT 文件工作区树风格对齐 + 批量删除 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 DbtFileBrowserPage 左侧文件树的 UI 风格对齐 SqlModelingPage 的 ModelFileBrowser（搜索框、checkbox 多选、选中计数、批量操作栏），并增加批量删除文件功能。

**Architecture:** 在 DbtFileBrowserPage 现有的侧边栏中增加搜索输入框、checkbox 树、选中计数 + 批量操作栏。复用 ModelFileBrowser 的样式模式（`bg-card p-4`、`rounded-md border`选中框、antd `Tree checkable`）。批量删除调用已有的 `deleteDbtFile(path)` API，逐个删除选中的文件节点。

**Tech Stack:** React 19, antd Tree (checkable), Tailwind CSS, existing `deleteDbtFile` API

---

## 文件结构

### 修改文件
- `source/dts-platform-webapp/src/pages/modeling/DbtFileBrowserPage.tsx` — 侧边栏重构：搜索框 + checkbox 树 + 选中计数 + 批量删除

### 不变
- `source/dts-platform-webapp/src/api/platformApi.ts` — `deleteDbtFile(path)` 已存在
- `source/dts-platform-webapp/src/pages/modeling/components/ModelFileBrowser.tsx` — 仅作为样式参考，不修改

---

## Task 1: 添加搜索框和 checkbox 树状态

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/DbtFileBrowserPage.tsx:137-141`

- [ ] **Step 1: 添加搜索和多选状态变量**

在现有状态声明区（`// Tree state` 下方）追加：

```tsx
// Tree state
const [treeData, setTreeData] = useState<FileNode | null>(null);
const [treeLoading, setTreeLoading] = useState(false);
const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
const [selectedKey, setSelectedKey] = useState<string>("");
const [searchKeyword, setSearchKeyword] = useState("");
const [checkedKeys, setCheckedKeys] = useState<string[]>([]);
```

- [ ] **Step 2: 添加搜索过滤辅助函数**

在 `fileNodeToTreeData` 函数之后添加：

```tsx
/** Recursively filter tree nodes by keyword, keeping parent dirs if any child matches. */
function filterTreeByKeyword(node: FileNode, keyword: string): FileNode | null {
	const lower = keyword.toLowerCase();
	if (node.type === "file") {
		return node.name.toLowerCase().includes(lower) ? node : null;
	}
	// directory: keep if any child matches
	const filteredChildren = (node.children ?? [])
		.map((child) => filterTreeByKeyword(child, keyword))
		.filter((child): child is FileNode => child !== null);
	if (filteredChildren.length > 0 || node.name.toLowerCase().includes(lower)) {
		return { ...node, children: filteredChildren };
	}
	return null;
}

/** Collect all file (leaf) paths from a tree for "select all" */
function collectAllFilePaths(node: FileNode): string[] {
	if (node.type === "file") return [node.path];
	return (node.children ?? []).flatMap(collectAllFilePaths);
}
```

- [ ] **Step 3: 更新 antTreeData memo 加入搜索过滤**

修改现有的 `antTreeData` useMemo：

```tsx
const filteredTree = useMemo(() => {
	if (!treeData) return null;
	if (!searchKeyword.trim()) return treeData;
	return filterTreeByKeyword(treeData, searchKeyword.trim());
}, [treeData, searchKeyword]);

const antTreeData = useMemo(
	() => (filteredTree?.children ?? []).map(fileNodeToTreeData),
	[filteredTree],
);
```

- [ ] **Step 4: 验证编译通过**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit`
Expected: 无错误

---

## Task 2: 重构侧边栏 JSX — 搜索框 + 选中栏 + checkbox 树

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/DbtFileBrowserPage.tsx:550-585`

- [ ] **Step 1: 替换侧边栏 JSX**

将 `<div className="w-full border-b ...">` 到 `</div>` 的侧边栏区域替换为对齐 ModelFileBrowser 风格的新结构：

```tsx
<div className="w-64 min-w-[256px] max-w-[280px] border-r border-border bg-card p-4 overflow-x-auto overflow-y-auto [&_.ant-tree-title]:block [&_.ant-tree-title]:whitespace-nowrap [&_.ant-tree-switcher]:flex-shrink-0">
	<div className="mb-3 text-xs font-bold uppercase text-muted-foreground">项目目录</div>
	<Input
		size="small"
		placeholder="搜索文件..."
		value={searchKeyword}
		onChange={(e) => setSearchKeyword(e.target.value)}
		allowClear
		className="mb-2"
	/>
	<div className="mb-3 rounded-md border border-border bg-background px-2 py-2">
		<div className="mb-2 flex items-center justify-between gap-2">
			<div className="min-w-0">
				<Text className="text-xs text-muted-foreground">已选 {checkedKeys.length} 项</Text>
			</div>
			{checkedKeys.length > 0 ? (
				<Button type="link" size="small" className="px-0" onClick={() => setCheckedKeys([])}>
					清空选择
				</Button>
			) : null}
		</div>
		<div className="flex flex-wrap gap-2">
			<Button
				size="small"
				onClick={() => {
					const allPaths = filteredTree ? collectAllFilePaths(filteredTree) : [];
					setCheckedKeys(allPaths);
				}}
			>
				全选当前结果
			</Button>
			<Button
				size="small"
				danger
				icon={<DeleteOutlined />}
				disabled={checkedKeys.length === 0 || batchDeleting}
				onClick={handleBatchDelete}
			>
				{batchDeleting ? "删除中..." : "删除所选"}
			</Button>
		</div>
	</div>
	<Spin spinning={treeLoading} size="small">
		{antTreeData.length > 0 ? (
			<Dropdown menu={{ items: contextMenuItems }} trigger={["contextMenu"]}>
				<div>
					<Tree
						checkable
						showIcon
						blockNode
						treeData={antTreeData}
						expandedKeys={expandedKeys}
						selectedKeys={selectedKey ? [selectedKey] : []}
						checkedKeys={checkedKeys}
						onExpand={(keys) => setExpandedKeys(keys)}
						onSelect={(_keys, info) => {
							const node = (info.node as any)?.data as FileNode | undefined;
							if (node?.type === "file") {
								loadFile(node.path);
							}
						}}
						onCheck={(keys) => {
							const checked = (Array.isArray(keys) ? keys : keys.checked).map(String);
							// Only keep file paths (exclude directories)
							setCheckedKeys(checked.filter((k) => !k.endsWith("/")));
						}}
						onRightClick={({ node }) => {
							const data = (node as any)?.data as FileNode | undefined;
							if (data) setContextNode(data);
						}}
					/>
				</div>
			</Dropdown>
		) : (
			!treeLoading && (
				<div className="px-4 py-10 text-center text-sm text-muted-foreground">
					{searchKeyword.trim() ? "无匹配结果" : "暂无文件，请先新建目录或文件。"}
				</div>
			)
		)}
	</Spin>
</div>
```

- [ ] **Step 2: 验证编译通过**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit`
Expected: 无错误（`batchDeleting` 和 `handleBatchDelete` 在 Task 3 添加，此步可能有临时错误）

---

## Task 3: 实现批量删除逻辑

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/DbtFileBrowserPage.tsx` — 在状态声明区添加

- [ ] **Step 1: 添加批量删除状态和处理函数**

在 `editorRef` 之后添加：

```tsx
const [batchDeleting, setBatchDeleting] = useState(false);

const handleBatchDelete = useCallback(async () => {
	if (checkedKeys.length === 0) return;
	Modal.confirm({
		title: `确认删除 ${checkedKeys.length} 个文件？`,
		icon: <ExclamationCircleOutlined />,
		content: "删除后无法恢复，请确认选中的文件不再需要。",
		okText: "确认删除",
		okType: "danger",
		cancelText: "取消",
		onOk: async () => {
			setBatchDeleting(true);
			let successCount = 0;
			let failCount = 0;
			for (const path of checkedKeys) {
				try {
					await deleteDbtFile(path);
					successCount++;
				} catch {
					failCount++;
				}
			}
			setBatchDeleting(false);
			setCheckedKeys([]);
			if (failCount === 0) {
				toast.success(`已删除 ${successCount} 个文件`);
			} else {
				toast.warning(`删除完成：成功 ${successCount}，失败 ${failCount}`);
			}
			// If the currently open file was deleted, clear editor
			if (activeFile && checkedKeys.includes(activeFile.path)) {
				setActiveFile(null);
				setEditorValue("");
				setDirty(false);
				setSelectedKey("");
			}
			await loadTree();
		},
	});
}, [checkedKeys, activeFile, loadTree]);
```

- [ ] **Step 2: 验证编译通过**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit`
Expected: 无错误

- [ ] **Step 3: 手动测试**

1. 打开 DBT 文件工作区
2. 搜索框输入关键词 → 树只显示匹配的文件和它们的父目录
3. 勾选多个文件 → "已选 N 项" 显示正确的计数
4. 点"全选当前结果" → 所有可见文件被勾选
5. 点"删除所选" → 确认弹窗 → 确认后文件被删除 → 树刷新
6. 点"清空选择" → 勾选清空

---

## Task 4: 清理旧样式残留

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/DbtFileBrowserPage.tsx`

- [ ] **Step 1: 移除旧侧边栏的 `rounded-[20px]` wrapper**

旧代码有 `<div className="rounded-[20px] bg-background px-2 py-2">` 包裹 Tree，新版不需要这个包裹。确认在 Task 2 的替换中已移除。

- [ ] **Step 2: 确保 `min-h-[720px]` 的 flex 容器兼容新宽度**

旧的 `xl:w-[280px]` 改成了 `w-64 min-w-[256px] max-w-[280px]`。确认主内容区的 `flex-1 min-w-0` 仍然正确填充剩余空间。

- [ ] **Step 3: 验证编译 + 构建**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit && npx vite build`
Expected: 零错误，构建成功
