export type ProjectTreeNode = {
	id?: string;
	level?: "major" | "subproject" | "node";
	name?: string;
	children?: ProjectTreeNode[];
	[key: string]: unknown;
};

export function flattenProjectTree(tree: ProjectTreeNode[]) {
	const result: ProjectTreeNode[] = [];
	const visit = (node: ProjectTreeNode) => {
		result.push(node);
		for (const child of node.children ?? []) {
			visit(child);
		}
	};
	for (const node of tree) {
		visit(node);
	}
	return result;
}

export function findTreeNodeById(tree: ProjectTreeNode[], id: string): ProjectTreeNode | null {
	for (const node of tree) {
		if ((node.id ?? "") === id) {
			return node;
		}
		const childMatch = findTreeNodeById(node.children ?? [], id);
		if (childMatch) {
			return childMatch;
		}
	}
	return null;
}

export type BreadcrumbItem = {
	id: string;
	name: string;
	level: string;
};

export function buildBreadcrumb(tree: ProjectTreeNode[], selectedId: string): BreadcrumbItem[] {
	const path: BreadcrumbItem[] = [];
	const search = (nodes: ProjectTreeNode[]): boolean => {
		for (const node of nodes) {
			const nodeId = node.id ?? "";
			if (nodeId === selectedId) {
				path.push({ id: nodeId, name: node.name ?? "", level: node.level ?? "" });
				return true;
			}
			if (node.children && node.children.length > 0) {
				if (search(node.children)) {
					path.unshift({ id: nodeId, name: node.name ?? "", level: node.level ?? "" });
					return true;
				}
			}
		}
		return false;
	};
	search(tree);
	return path;
}
