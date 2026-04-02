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
