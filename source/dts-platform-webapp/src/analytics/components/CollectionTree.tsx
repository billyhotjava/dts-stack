import { useCallback, useEffect, useMemo, useState } from "react";
import { Tree, Button, Dropdown, Modal, Input, Radio, message, Spin } from "antd";
import {
	FolderOutlined,
	FolderOpenOutlined,
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

function localizeCollectionName(name: string | undefined): string {
	if (!name) return "-";
	return name.replace(/['']s Personal Collection$/, " 的个人收藏");
}

function toTreeData(nodes: CollectionNode[]): DataNode[] {
	return nodes.map((n) => ({
		key: String(n.id),
		title: localizeCollectionName(n.name),
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
	const treeRef = useMemo(() => ({ tree }), [tree]);
	(CollectionTree as any).__treeRef = treeRef;

	const handleSelect = (keys: React.Key[]) => {
		if (keys.length > 0) onSelect(String(keys[0]));
	};

	const handleRightClick = ({
		node,
	}: {
		event: React.MouseEvent;
		node: EventDataNode<DataNode>;
	}) => {
		const key = String(node.key);
		if (key === "__all__" || key === "__uncategorized__") return;
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
