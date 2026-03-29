import type { ReactNode } from "react";
import type { DataNode } from "antd/es/tree";

export type ModelFileBrowserNodeType = "space" | "layer" | "model";

export type ModelFileBrowserTreeNode = DataNode & {
	key: string;
	title: ReactNode;
	nodeType: ModelFileBrowserNodeType;
	isLeaf?: boolean;
	children?: ModelFileBrowserTreeNode[];
};

const normalizeKeys = (keys: Array<string | null | undefined>) =>
	Array.from(new Set(keys.map((key) => String(key || "").trim()).filter(Boolean)));

export function collectExpandableTreeKeys(nodes: ModelFileBrowserTreeNode[]): string[] {
	const keys: string[] = [];
	const visit = (items: ModelFileBrowserTreeNode[]) => {
		items.forEach((node) => {
			if (node.children?.length) {
				keys.push(String(node.key));
				visit(node.children);
			}
		});
	};
	visit(nodes);
	return normalizeKeys(keys);
}

export function deriveDefaultExpandedTreeKeys(
	nodes: ModelFileBrowserTreeNode[],
	activeSpaceKey: string | null,
): string[] {
	if (!activeSpaceKey) {
		return [];
	}
	const matchedSpace = nodes.find((node) => String(node.key) === activeSpaceKey);
	if (!matchedSpace) {
		return [];
	}
	return normalizeKeys([
		String(matchedSpace.key),
		...(matchedSpace.children || []).filter((child) => child.children?.length).map((child) => String(child.key)),
	]);
}

export function mergeExpandedTreeKeys(
	currentKeys: string[],
	nodes: ModelFileBrowserTreeNode[],
	preferredKeys: string[] = [],
): string[] {
	const expandable = new Set(collectExpandableTreeKeys(nodes));
	return normalizeKeys([...currentKeys, ...preferredKeys]).filter((key) => expandable.has(key));
}
