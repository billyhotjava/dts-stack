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
