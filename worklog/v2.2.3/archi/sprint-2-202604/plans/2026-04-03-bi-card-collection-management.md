# BI 分析卡片文件夹管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 BI 分析卡片页面增加左侧文件夹树，支持嵌套文件夹分类管理卡片。

**Architecture:** 改造现有 CardsPage 为左右分栏布局（对齐 DbtFileBrowserPage）。左侧新建 CollectionTree 组件管理文件夹树，右侧保留现有卡片表格。新建 MoveToCollectionModal 用于卡片移动。前端补充 Collection CRUD API 封装，后端不改动。

**Tech Stack:** React 19 + antd 5 + Tailwind CSS + Metabase Collection REST API

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/analytics/api/analyticsApi.ts` | Modify:5-12, 1737-1739 | 补充 `parent_id` 类型 + Collection CRUD 方法 |
| `src/analytics/i18n.ts` | Modify | 新增文件夹相关 i18n key |
| `src/analytics/components/CollectionTree.tsx` | Create | 左侧文件夹树组件 |
| `src/analytics/components/MoveToCollectionModal.tsx` | Create | 移动卡片到文件夹弹窗 |
| `src/analytics/pages/CardsPage.tsx` | Modify | 集成左右分栏布局 |

> 以下所有路径相对于 `source/dts-platform-webapp/`

---

### Task 1: analyticsApi 补充 Collection CRUD

**Files:**
- Modify: `src/analytics/api/analyticsApi.ts:5-12` (CollectionListItem 类型)
- Modify: `src/analytics/api/analyticsApi.ts:1737-1739` (API 方法)

- [ ] **Step 1: 给 CollectionListItem 增加 parent_id 字段**

在 `src/analytics/api/analyticsApi.ts` 第 5-12 行，将 `CollectionListItem` 类型修改为：

```typescript
export type CollectionListItem = {
	id: number | "root";
	name?: string;
	description?: string | null;
	archived?: boolean;
	location?: string | null;
	parent_id?: number | null;
	can_write?: boolean;
};
```

即在 `location` 之后、`can_write` 之前添加 `parent_id?: number | null;`。

- [ ] **Step 2: 添加 Collection CRUD API 方法**

在 `src/analytics/api/analyticsApi.ts` 第 1739 行（`getCollectionItems` 之后），插入以下三个方法：

```typescript
	createCollection: (body: { name: string; parent_id?: number | null; description?: string | null }) =>
		sendJson<CollectionListItem>("/bi/api/collection", body),
	updateCollection: (id: number, body: { name?: string; parent_id?: number | null; description?: string | null }) =>
		requestJson<CollectionListItem>(`/bi/api/collection/${encodeURIComponent(String(id))}`, "PUT", body),
	deleteCollection: (id: number) =>
		requestJson<void>(`/bi/api/collection/${encodeURIComponent(String(id))}`, "DELETE"),
```

- [ ] **Step 3: 验证 TypeScript 编译**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | head -20`
Expected: 无与 collection 相关的类型错误

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/api/analyticsApi.ts
git commit -m "feat(F1/T01): analyticsApi 补充 Collection CRUD 方法"
```

---

### Task 2: 新增 i18n key

**Files:**
- Modify: `src/analytics/i18n.ts`

- [ ] **Step 1: 在中文 locale 中添加文件夹相关 key**

在 `src/analytics/i18n.ts` 中文 locale 的 `"collections.itemsSubtitle"` 行之后（约第 80 行），添加：

```typescript
		"collections.allCards": "全部卡片",
		"collections.uncategorized": "未分类",
		"collections.newFolder": "新建文件夹",
		"collections.newSubFolder": "新建子文件夹",
		"collections.rename": "重命名",
		"collections.delete": "删除文件夹",
		"collections.deleteConfirm": "确定删除文件夹",
		"collections.deleteHasContent": "该文件夹下包含卡片和/或子文件夹。",
		"collections.moveToUncategorized": "将内容移至「未分类」",
		"collections.deleteAll": "一并删除所有内容",
		"collections.folderName": "文件夹名称",
		"collections.moveTo": "移动到...",
		"collections.moveToFolder": "移动到文件夹",
		"collections.moveSuccess": "移动成功",
		"collections.createSuccess": "文件夹创建成功",
		"collections.renameSuccess": "重命名成功",
		"collections.deleteSuccess": "文件夹已删除",
```

- [ ] **Step 2: 在英文 locale 中添加对应 key**

在英文 locale 的 `"collections.itemsSubtitle"` 行之后（约第 405 行），添加：

```typescript
		"collections.allCards": "All Cards",
		"collections.uncategorized": "Uncategorized",
		"collections.newFolder": "New Folder",
		"collections.newSubFolder": "New Sub-folder",
		"collections.rename": "Rename",
		"collections.delete": "Delete Folder",
		"collections.deleteConfirm": "Delete folder",
		"collections.deleteHasContent": "This folder contains cards and/or sub-folders.",
		"collections.moveToUncategorized": "Move contents to Uncategorized",
		"collections.deleteAll": "Delete all contents",
		"collections.folderName": "Folder Name",
		"collections.moveTo": "Move to...",
		"collections.moveToFolder": "Move to Folder",
		"collections.moveSuccess": "Moved successfully",
		"collections.createSuccess": "Folder created",
		"collections.renameSuccess": "Renamed successfully",
		"collections.deleteSuccess": "Folder deleted",
```

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/i18n.ts
git commit -m "feat(F1/T02): 新增文件夹管理 i18n key"
```

---

### Task 3: CollectionTree 文件夹树组件

**Files:**
- Create: `src/analytics/components/CollectionTree.tsx`

- [ ] **Step 1: 创建 CollectionTree.tsx**

创建 `src/analytics/components/CollectionTree.tsx`，完整内容如下：

```tsx
import { useCallback, useEffect, useMemo, useState } from "react";
import { Tree, Button, Dropdown, Modal, Input, Radio, message, Spin } from "antd";
import {
	FolderOutlined,
	FolderOpenOutlined,
	PlusOutlined,
	AppstoreOutlined,
	InboxOutlined,
} from "@ant-design/icons";
import type { DataNode, EventDataNode } from "antd/es/tree";
import { analyticsApi, type CollectionListItem } from "../api/analyticsApi";
import { getEffectiveLocale, t, type Locale } from "../i18n";

// ── Types ──────────────────────────────────────────────────

export type CollectionTreeProps = {
	selectedKey: string;
	onSelect: (key: string) => void;
	onCollectionsChange?: () => void;
};

type CollectionNode = CollectionListItem & { children: CollectionNode[] };

// ── Helpers ────────────────────────────────────────────────

function buildTree(items: CollectionListItem[]): CollectionNode[] {
	const map = new Map<number, CollectionNode>();
	const roots: CollectionNode[] = [];

	for (const item of items) {
		if (item.id === "root" || item.archived) continue;
		map.set(item.id as number, { ...item, children: [] });
	}

	for (const node of map.values()) {
		const parentId = node.parent_id;
		if (parentId != null && map.has(parentId)) {
			map.get(parentId)!.children.push(node);
		} else {
			roots.push(node);
		}
	}

	const sortByName = (a: CollectionNode, b: CollectionNode) =>
		(a.name ?? "").localeCompare(b.name ?? "");
	const sortRecursive = (nodes: CollectionNode[]): CollectionNode[] => {
		nodes.sort(sortByName);
		for (const n of nodes) sortRecursive(n.children);
		return nodes;
	};

	return sortRecursive(roots);
}

function toTreeData(nodes: CollectionNode[]): DataNode[] {
	return nodes.map((n) => ({
		key: String(n.id),
		title: n.name ?? "-",
		icon: n.children.length > 0 ? <FolderOpenOutlined /> : <FolderOutlined />,
		children: n.children.length > 0 ? toTreeData(n.children) : undefined,
	}));
}

/** Recursively collect all descendant collection IDs (inclusive). */
export function collectDescendantIds(
	nodes: CollectionNode[],
	targetId: number,
): number[] {
	const result: number[] = [];
	function walk(list: CollectionNode[]): boolean {
		for (const n of list) {
			if ((n.id as number) === targetId) {
				collect(n);
				return true;
			}
			if (walk(n.children)) return true;
		}
		return false;
	}
	function collect(n: CollectionNode) {
		result.push(n.id as number);
		for (const c of n.children) collect(c);
	}
	walk(nodes);
	return result;
}

// ── Component ──────────────────────────────────────────────

export default function CollectionTree({
	selectedKey,
	onSelect,
	onCollectionsChange,
}: CollectionTreeProps) {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [collections, setCollections] = useState<CollectionListItem[]>([]);
	const [loading, setLoading] = useState(true);
	const [expandedKeys, setExpandedKeys] = useState<string[]>([]);

	// Modal state
	const [createModalOpen, setCreateModalOpen] = useState(false);
	const [createParentId, setCreateParentId] = useState<number | null>(null);
	const [createName, setCreateName] = useState("");
	const [creating, setCreating] = useState(false);

	const [renameModalOpen, setRenameModalOpen] = useState(false);
	const [renameTarget, setRenameTarget] = useState<{ id: number; name: string } | null>(null);
	const [renameName, setRenameName] = useState("");
	const [renaming, setRenaming] = useState(false);

	const [deleteModalOpen, setDeleteModalOpen] = useState(false);
	const [deleteTarget, setDeleteTarget] = useState<{ id: number; name: string } | null>(null);
	const [deleteMode, setDeleteMode] = useState<"move" | "delete">("move");
	const [deleting, setDeleting] = useState(false);

	const loadCollections = useCallback(() => {
		setLoading(true);
		analyticsApi
			.listCollections()
			.then((items) => setCollections(Array.isArray(items) ? items : []))
			.catch(() => setCollections([]))
			.finally(() => setLoading(false));
	}, []);

	useEffect(() => {
		loadCollections();
	}, [loadCollections]);

	const tree = useMemo(() => buildTree(collections), [collections]);
	const treeData = useMemo(() => toTreeData(tree), [tree]);

	// Export tree for parent component to use collectDescendantIds
	// We attach it via a ref-like pattern on the component
	const treeRef = useMemo(() => ({ tree }), [tree]);
	(CollectionTree as any).__treeRef = treeRef;

	const handleSelect = (keys: React.Key[]) => {
		if (keys.length > 0) onSelect(String(keys[0]));
	};

	// ── Right-click context menu ──

	const handleRightClick = ({
		node,
	}: {
		event: React.MouseEvent;
		node: EventDataNode<DataNode>;
	}) => {
		const key = String(node.key);
		if (key === "__all__" || key === "__uncategorized__") return;
		// Context menu is handled via Dropdown trigger on each node
	};

	const contextMenuItems = (nodeId: number, nodeName: string) => [
		{
			key: "newSub",
			label: t(locale, "collections.newSubFolder"),
			onClick: () => {
				setCreateParentId(nodeId);
				setCreateName("");
				setCreateModalOpen(true);
			},
		},
		{
			key: "rename",
			label: t(locale, "collections.rename"),
			onClick: () => {
				setRenameTarget({ id: nodeId, name: nodeName });
				setRenameName(nodeName);
				setRenameModalOpen(true);
			},
		},
		{ type: "divider" as const },
		{
			key: "delete",
			label: t(locale, "collections.delete"),
			danger: true,
			onClick: () => {
				setDeleteTarget({ id: nodeId, name: nodeName });
				setDeleteMode("move");
				setDeleteModalOpen(true);
			},
		},
	];

	const renderTreeNode = (nodeData: DataNode): DataNode => ({
		...nodeData,
		title: (
			<Dropdown
				menu={{ items: contextMenuItems(Number(nodeData.key), String(nodeData.title)) }}
				trigger={["contextMenu"]}
			>
				<span className="inline-block w-full">{nodeData.title as React.ReactNode}</span>
			</Dropdown>
		),
		children: nodeData.children?.map(renderTreeNode),
	});

	const treeDataWithContextMenu = useMemo(
		() => treeData.map(renderTreeNode),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[treeData, locale],
	);

	// ── Create folder ──

	const handleCreate = async () => {
		const trimmed = createName.trim();
		if (!trimmed) return;
		setCreating(true);
		try {
			await analyticsApi.createCollection({
				name: trimmed,
				parent_id: createParentId,
			});
			message.success(t(locale, "collections.createSuccess"));
			setCreateModalOpen(false);
			loadCollections();
			onCollectionsChange?.();
		} catch {
			message.error("Failed to create folder");
		} finally {
			setCreating(false);
		}
	};

	// ── Rename folder ──

	const handleRename = async () => {
		if (!renameTarget) return;
		const trimmed = renameName.trim();
		if (!trimmed) return;
		setRenaming(true);
		try {
			await analyticsApi.updateCollection(renameTarget.id, { name: trimmed });
			message.success(t(locale, "collections.renameSuccess"));
			setRenameModalOpen(false);
			loadCollections();
			onCollectionsChange?.();
		} catch {
			message.error("Failed to rename folder");
		} finally {
			setRenaming(false);
		}
	};

	// ── Delete folder ──

	const handleDelete = async () => {
		if (!deleteTarget) return;
		setDeleting(true);
		try {
			if (deleteMode === "move") {
				// Move all items in this collection to uncategorized (null)
				const items = await analyticsApi.getCollectionItems(deleteTarget.id);
				for (const item of items) {
					if (item.model === "card") {
						await analyticsApi.updateCard(item.id, { collection_id: null });
					}
				}
			}
			await analyticsApi.deleteCollection(deleteTarget.id);
			message.success(t(locale, "collections.deleteSuccess"));
			setDeleteModalOpen(false);
			// If the deleted collection was selected, switch to "all"
			if (selectedKey === String(deleteTarget.id)) {
				onSelect("__all__");
			}
			loadCollections();
			onCollectionsChange?.();
		} catch {
			message.error("Failed to delete folder");
		} finally {
			setDeleting(false);
		}
	};

	// ── Virtual nodes ──

	const virtualNodes: DataNode[] = [
		{
			key: "__all__",
			title: t(locale, "collections.allCards"),
			icon: <AppstoreOutlined />,
			isLeaf: true,
		},
		{
			key: "__uncategorized__",
			title: t(locale, "collections.uncategorized"),
			icon: <InboxOutlined />,
			isLeaf: true,
		},
	];

	return (
		<div className="w-64 min-w-[256px] max-w-[280px] border-r border-border bg-card flex flex-col overflow-hidden">
			{/* Virtual nodes */}
			<div className="px-3 pt-4 pb-1">
				<Tree
					treeData={virtualNodes}
					selectedKeys={
						selectedKey === "__all__" || selectedKey === "__uncategorized__"
							? [selectedKey]
							: []
					}
					onSelect={(keys) => {
						if (keys.length > 0) onSelect(String(keys[0]));
					}}
					showIcon
					blockNode
					selectable
				/>
			</div>

			{/* Divider */}
			<div className="mx-3 border-b border-border" />

			{/* Real collection tree */}
			<div className="flex-1 overflow-y-auto px-3 pt-2 pb-2">
				{loading ? (
					<div className="flex justify-center py-4">
						<Spin size="small" />
					</div>
				) : treeDataWithContextMenu.length === 0 ? (
					<div className="text-xs text-muted-foreground py-4 text-center">
						{t(locale, "common.empty")}
					</div>
				) : (
					<Tree
						treeData={treeDataWithContextMenu}
						selectedKeys={
							selectedKey !== "__all__" && selectedKey !== "__uncategorized__"
								? [selectedKey]
								: []
						}
						expandedKeys={expandedKeys}
						onExpand={(keys) => setExpandedKeys(keys.map(String))}
						onSelect={handleSelect}
						onRightClick={handleRightClick}
						showIcon
						blockNode
						selectable
						className="[&_.ant-tree-title]:block [&_.ant-tree-title]:whitespace-nowrap [&_.ant-tree-switcher]:flex-shrink-0"
					/>
				)}
			</div>

			{/* New folder button */}
			<div className="border-t border-border px-3 py-2">
				<Button
					type="text"
					size="small"
					icon={<PlusOutlined />}
					block
					onClick={() => {
						setCreateParentId(null);
						setCreateName("");
						setCreateModalOpen(true);
					}}
				>
					{t(locale, "collections.newFolder")}
				</Button>
			</div>

			{/* Create folder modal */}
			<Modal
				open={createModalOpen}
				title={createParentId ? t(locale, "collections.newSubFolder") : t(locale, "collections.newFolder")}
				onCancel={() => setCreateModalOpen(false)}
				onOk={handleCreate}
				confirmLoading={creating}
				okButtonProps={{ disabled: !createName.trim() }}
			>
				<div className="py-2">
					<div className="text-sm mb-2">{t(locale, "collections.folderName")}</div>
					<Input
						value={createName}
						onChange={(e) => setCreateName(e.target.value)}
						onPressEnter={handleCreate}
						placeholder={t(locale, "collections.folderName")}
						autoFocus
					/>
				</div>
			</Modal>

			{/* Rename modal */}
			<Modal
				open={renameModalOpen}
				title={t(locale, "collections.rename")}
				onCancel={() => setRenameModalOpen(false)}
				onOk={handleRename}
				confirmLoading={renaming}
				okButtonProps={{ disabled: !renameName.trim() }}
			>
				<div className="py-2">
					<div className="text-sm mb-2">{t(locale, "collections.folderName")}</div>
					<Input
						value={renameName}
						onChange={(e) => setRenameName(e.target.value)}
						onPressEnter={handleRename}
						autoFocus
					/>
				</div>
			</Modal>

			{/* Delete confirmation modal */}
			<Modal
				open={deleteModalOpen}
				title={`${t(locale, "collections.deleteConfirm")}「${deleteTarget?.name ?? ""}」？`}
				onCancel={() => setDeleteModalOpen(false)}
				onOk={handleDelete}
				confirmLoading={deleting}
				okText={t(locale, "collections.delete")}
				okButtonProps={{ danger: true }}
			>
				<div className="py-2">
					<p className="mb-3">{t(locale, "collections.deleteHasContent")}</p>
					<Radio.Group
						value={deleteMode}
						onChange={(e) => setDeleteMode(e.target.value)}
					>
						<div className="flex flex-col gap-2">
							<Radio value="move">
								{t(locale, "collections.moveToUncategorized")}
							</Radio>
							<Radio value="delete">
								{t(locale, "collections.deleteAll")}
							</Radio>
						</div>
					</Radio.Group>
				</div>
			</Modal>
		</div>
	);
}
```

- [ ] **Step 2: 验证 TypeScript 编译**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | head -20`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/components/CollectionTree.tsx
git commit -m "feat(F1/T02): 新建 CollectionTree 文件夹树组件"
```

---

### Task 4: MoveToCollectionModal 移动弹窗组件

**Files:**
- Create: `src/analytics/components/MoveToCollectionModal.tsx`

- [ ] **Step 1: 创建 MoveToCollectionModal.tsx**

创建 `src/analytics/components/MoveToCollectionModal.tsx`，完整内容如下：

```tsx
import { useEffect, useMemo, useState } from "react";
import { Tree, Modal, Spin, message } from "antd";
import { FolderOutlined, InboxOutlined } from "@ant-design/icons";
import type { DataNode } from "antd/es/tree";
import { analyticsApi, type CollectionListItem } from "../api/analyticsApi";
import { getEffectiveLocale, t, type Locale } from "../i18n";

export type MoveToCollectionModalProps = {
	open: boolean;
	cardIds: number[];
	onClose: () => void;
	onSuccess: () => void;
};

type CollectionNode = CollectionListItem & { children: CollectionNode[] };

function buildTree(items: CollectionListItem[]): CollectionNode[] {
	const map = new Map<number, CollectionNode>();
	const roots: CollectionNode[] = [];

	for (const item of items) {
		if (item.id === "root" || item.archived) continue;
		map.set(item.id as number, { ...item, children: [] });
	}

	for (const node of map.values()) {
		const parentId = node.parent_id;
		if (parentId != null && map.has(parentId)) {
			map.get(parentId)!.children.push(node);
		} else {
			roots.push(node);
		}
	}

	const sortByName = (a: CollectionNode, b: CollectionNode) =>
		(a.name ?? "").localeCompare(b.name ?? "");
	const sortRecursive = (nodes: CollectionNode[]): CollectionNode[] => {
		nodes.sort(sortByName);
		for (const n of nodes) sortRecursive(n.children);
		return nodes;
	};

	return sortRecursive(roots);
}

function toTreeData(nodes: CollectionNode[]): DataNode[] {
	return nodes.map((n) => ({
		key: String(n.id),
		title: n.name ?? "-",
		icon: <FolderOutlined />,
		children: n.children.length > 0 ? toTreeData(n.children) : undefined,
	}));
}

export default function MoveToCollectionModal({
	open,
	cardIds,
	onClose,
	onSuccess,
}: MoveToCollectionModalProps) {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [collections, setCollections] = useState<CollectionListItem[]>([]);
	const [loading, setLoading] = useState(false);
	const [selectedKey, setSelectedKey] = useState<string | null>(null);
	const [moving, setMoving] = useState(false);

	useEffect(() => {
		if (!open) return;
		setLoading(true);
		setSelectedKey(null);
		analyticsApi
			.listCollections()
			.then((items) => setCollections(Array.isArray(items) ? items : []))
			.catch(() => setCollections([]))
			.finally(() => setLoading(false));
	}, [open]);

	const tree = useMemo(() => buildTree(collections), [collections]);
	const treeData = useMemo((): DataNode[] => {
		const uncategorized: DataNode = {
			key: "__uncategorized__",
			title: t(locale, "collections.uncategorized"),
			icon: <InboxOutlined />,
			isLeaf: true,
		};
		return [uncategorized, ...toTreeData(tree)];
	}, [tree, locale]);

	const handleOk = async () => {
		if (selectedKey == null || cardIds.length === 0) return;
		setMoving(true);
		const collectionId = selectedKey === "__uncategorized__" ? null : Number(selectedKey);
		try {
			let ok = 0;
			for (const id of cardIds) {
				await analyticsApi.updateCard(id, { collection_id: collectionId });
				ok++;
			}
			message.success(`${t(locale, "collections.moveSuccess")} (${ok})`);
			onSuccess();
			onClose();
		} catch {
			message.error("Failed to move cards");
		} finally {
			setMoving(false);
		}
	};

	return (
		<Modal
			open={open}
			title={t(locale, "collections.moveToFolder")}
			width={400}
			onCancel={onClose}
			onOk={handleOk}
			confirmLoading={moving}
			okButtonProps={{ disabled: selectedKey == null }}
		>
			{loading ? (
				<div className="flex justify-center py-8">
					<Spin />
				</div>
			) : (
				<div className="py-2 max-h-[400px] overflow-y-auto">
					<Tree
						treeData={treeData}
						selectedKeys={selectedKey ? [selectedKey] : []}
						onSelect={(keys) => {
							setSelectedKey(keys.length > 0 ? String(keys[0]) : null);
						}}
						showIcon
						blockNode
						defaultExpandAll
					/>
				</div>
			)}
		</Modal>
	);
}
```

- [ ] **Step 2: 验证 TypeScript 编译**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | head -20`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/components/MoveToCollectionModal.tsx
git commit -m "feat(F1/T03): 新建 MoveToCollectionModal 移动弹窗组件"
```

---

### Task 5: CardsPage 集成改造

**Files:**
- Modify: `src/analytics/pages/CardsPage.tsx`

- [ ] **Step 1: 更新 import**

将 `CardsPage.tsx` 开头的 import 区替换为：

```typescript
import { Link } from "react-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { analyticsApi, type CardListItem } from "../api/analyticsApi";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Button, Card, Input, Modal, Select, Space, Spin, Table, Tag, message } from "antd";
import { PlusOutlined, EyeOutlined, EditOutlined, DeleteOutlined, UploadOutlined, FolderOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import CollectionTree, { collectDescendantIds } from "../components/CollectionTree";
import MoveToCollectionModal from "../components/MoveToCollectionModal";
```

变更：增加了 `FolderOutlined` 图标导入，以及 `CollectionTree`、`collectDescendantIds`、`MoveToCollectionModal` 导入。

- [ ] **Step 2: 在 CardsPage 组件中增加文件夹相关状态**

在 `CardsPage` 组件的现有状态声明区（`const [batchImportOpen, setBatchImportOpen]` 之后），添加：

```typescript
	const [selectedCollection, setSelectedCollection] = useState("__all__");
	const [moveModalOpen, setMoveModalOpen] = useState(false);
	const [moveCardIds, setMoveCardIds] = useState<number[]>([]);
	const [collectionsVersion, setCollectionsVersion] = useState(0);
```

- [ ] **Step 3: 修改 filteredCards 逻辑**

将现有 `filteredCards` useMemo 替换为以下逻辑，在搜索过滤之前先按 collection 过滤：

```typescript
	const filteredCards = useMemo(() => {
		if (state.state !== "loaded") return [];
		let cards = state.value;

		// Filter by collection
		if (selectedCollection === "__uncategorized__") {
			cards = cards.filter((c) => c.collection_id == null);
		} else if (selectedCollection !== "__all__") {
			const treeRef = (CollectionTree as any).__treeRef;
			const tree = treeRef?.tree ?? [];
			const ids = collectDescendantIds(tree, Number(selectedCollection));
			if (ids.length > 0) {
				const idSet = new Set(ids);
				cards = cards.filter((c) => c.collection_id != null && idSet.has(c.collection_id));
			} else {
				cards = cards.filter((c) => c.collection_id === Number(selectedCollection));
			}
		}

		// Filter by search keyword
		const kw = searchQuery.trim().toLowerCase();
		if (kw) {
			cards = cards.filter((c) =>
				(c.name ?? "").toLowerCase().includes(kw) || (c.description ?? "").toLowerCase().includes(kw)
			);
		}
		return cards;
	}, [state, searchQuery, selectedCollection, collectionsVersion]);
```

- [ ] **Step 4: 在操作列新增「移动」按钮**

在 `columns` 数组的 `actions` 列 render 函数中，在「编辑」Link 之后、「删除」Button 之前，添加：

```tsx
					<Button
						type="link"
						size="small"
						icon={<FolderOutlined />}
						onClick={() => {
							setMoveCardIds([record.id]);
							setMoveModalOpen(true);
						}}
					>
						移动
					</Button>
```

同时将 `width` 从 `150` 改为 `200` 以适应新增按钮。

- [ ] **Step 5: 在批量操作栏新增「移动到...」按钮**

在 `selectedRowKeys.length > 0` 条件块中的「批量删除」Button 之后，添加：

```tsx
								<Button
									size="small"
									icon={<FolderOutlined />}
									onClick={() => {
										setMoveCardIds(selectedRowKeys.map(Number));
										setMoveModalOpen(true);
									}}
								>
									{t(locale, "collections.moveTo")}
								</Button>
```

- [ ] **Step 6: 添加 handleCollectionsChange 回调**

在 `loadCards` 之后添加：

```typescript
	const handleCollectionsChange = useCallback(() => {
		setCollectionsVersion((v) => v + 1);
		loadCards();
	}, [loadCards]);

	const handleMoveSuccess = useCallback(() => {
		setMoveModalOpen(false);
		setMoveCardIds([]);
		setSelectedRowKeys([]);
		setCollectionsVersion((v) => v + 1);
		loadCards();
	}, [loadCards]);
```

- [ ] **Step 7: 改造 return JSX 为左右分栏布局**

将整个 `return` 替换为以下结构。保留 PageHeader 和 BatchImportCardsModal 在顶部，将卡片列表区域包裹在 flex 分栏中：

```tsx
	return (
		<div className="space-y-4">
			<PageHeader
				title={t(locale, "questions.title")}
				actions={
					<Space>
						<Button icon={<UploadOutlined />} onClick={() => setBatchImportOpen(true)}>
							批量导入 SQL
						</Button>
						<Link to="/bi/questions/new">
							<Button type="primary" icon={<PlusOutlined />}>
								{t(locale, "questions.new")}
							</Button>
						</Link>
					</Space>
				}
			/>
			<BatchImportCardsModal
				open={batchImportOpen}
				onClose={() => setBatchImportOpen(false)}
				onSuccess={loadCards}
			/>

			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}

			<Card styles={{ body: { padding: 0 } }}>
				<div className="flex min-h-[720px]">
					{/* Left: Collection tree */}
					<CollectionTree
						selectedKey={selectedCollection}
						onSelect={setSelectedCollection}
						onCollectionsChange={handleCollectionsChange}
					/>

					{/* Right: Cards list */}
					<div className="flex min-w-0 flex-1 flex-col">
						{state.state === "loading" ? (
							<div className="flex justify-center items-center flex-1">
								<Spin size="large" />
							</div>
						) : state.state === "loaded" ? (
							<div className="p-4">
								<div className="mb-4 flex items-center justify-between flex-wrap gap-3">
									<div className="flex items-center gap-3 flex-wrap">
										<Input.Search
											placeholder={t(locale, "common.search")}
											value={searchQuery}
											onChange={(e) => setSearchQuery(e.target.value)}
											allowClear
											style={{ width: 300 }}
										/>
										{selectedRowKeys.length > 0 && (
											<Space size="small">
												<span className="text-xs text-text-secondary">
													已选 {selectedRowKeys.length} 项
												</span>
												<Button size="small" onClick={() => setSelectedRowKeys([])}>
													取消选择
												</Button>
												<Button
													size="small"
													danger
													icon={<DeleteOutlined />}
													loading={batchDeleting}
													onClick={handleBatchDelete}
												>
													批量删除
												</Button>
												<Button
													size="small"
													icon={<FolderOutlined />}
													onClick={() => {
														setMoveCardIds(selectedRowKeys.map(Number));
														setMoveModalOpen(true);
													}}
												>
													{t(locale, "collections.moveTo")}
												</Button>
											</Space>
										)}
									</div>
									<Tag color="blue">{filteredCards.length} 张卡片</Tag>
								</div>
								{filteredCards.length === 0 ? (
									<EmptyState
										title={
											searchQuery
												? t(locale, "common.noResults")
												: t(locale, "common.empty")
										}
									/>
								) : (
									<Table<CardListItem>
										columns={columns}
										dataSource={filteredCards}
										rowKey={(r) => r.id}
										rowSelection={{
											selectedRowKeys,
											onChange: (keys) => setSelectedRowKeys(keys),
										}}
										pagination={{
											pageSize: 20,
											showSizeChanger: true,
											showQuickJumper: true,
											showTotal: (total) => `共 ${total} 条`,
										}}
									/>
								)}
							</div>
						) : null}
					</div>
				</div>
			</Card>

			<MoveToCollectionModal
				open={moveModalOpen}
				cardIds={moveCardIds}
				onClose={() => {
					setMoveModalOpen(false);
					setMoveCardIds([]);
				}}
				onSuccess={handleMoveSuccess}
			/>
		</div>
	);
```

- [ ] **Step 8: 验证 TypeScript 编译**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | head -20`
Expected: 无错误

- [ ] **Step 9: 本地 dev 验证**

Run: `cd source/dts-platform-webapp && pnpm dev`

手动验证：
1. 打开 `/bi/questions` 页面，确认左右分栏布局正常
2. 点击「全部卡片」→ 显示所有卡片
3. 点击「未分类」→ 只显示未归类卡片
4. 右键文件夹 → 菜单出现
5. 创建文件夹 → 树中出现
6. 卡片操作列「移动」→ 弹窗出现并可移动
7. 批量选中 →「移动到...」按钮可用

- [ ] **Step 10: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/CardsPage.tsx
git commit -m "feat(F1/T04): CardsPage 集成文件夹树左右分栏布局"
```
