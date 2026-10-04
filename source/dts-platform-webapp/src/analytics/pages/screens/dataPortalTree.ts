import type {
	DashboardListItem,
	DataPortalBindingItem,
	DataPortalDirectoryItem,
	ScreenListItem,
} from "../../api/analyticsApi";

export type DataPortalDirectory = DataPortalDirectoryItem;
export type DataPortalBinding = DataPortalBindingItem;
export type DataPortalContentType = DataPortalBindingItem["content_type"];

export type DataPortalTreeNode = {
	key: string;
	title: string;
	nodeType: "DIRECTORY" | "CONTENT";
	directoryId?: number;
	bindingId?: number;
	contentType?: DataPortalContentType;
	contentId?: number;
	availability?: "AVAILABLE" | "UNAVAILABLE";
	screen?: ScreenListItem;
	dashboard?: DashboardListItem;
	children?: DataPortalTreeNode[];
};

type BuildOptions = {
	includeUnavailable?: boolean;
};

function normalize(value: unknown): string {
	return value == null ? "" : String(value).trim();
}

function normalizedSearch(value: unknown): string {
	return normalize(value).toLocaleLowerCase();
}

function compareOrdered(
	left: { sort_order?: number | null; id: number },
	right: { sort_order?: number | null; id: number },
): number {
	const sortDelta = Number(left.sort_order ?? 0) - Number(right.sort_order ?? 0);
	return sortDelta !== 0 ? sortDelta : left.id - right.id;
}

function contentLeaf(
	binding: DataPortalBinding,
	screens: Map<string, ScreenListItem>,
	dashboards: Map<string, DashboardListItem>,
	includeUnavailable: boolean,
): DataPortalTreeNode | null {
	const id = String(binding.content_id);
	const screen = binding.content_type === "SCREEN" ? screens.get(id) : undefined;
	const dashboard = binding.content_type === "DASHBOARD" ? dashboards.get(id) : undefined;
	const content = screen ?? dashboard;
	if (!content && !includeUnavailable) return null;

	return {
		key: `binding:${binding.id}`,
		title: content?.name?.trim() || `已失效${binding.content_type === "SCREEN" ? "大屏" : "看板"}（绑定 ${binding.id}）`,
		nodeType: "CONTENT",
		bindingId: binding.id,
		contentType: binding.content_type,
		contentId: binding.content_id,
		availability: content ? "AVAILABLE" : "UNAVAILABLE",
		screen,
		dashboard,
	};
}

export function buildDataPortalTree(
	directories: DataPortalDirectory[],
	bindings: DataPortalBinding[],
	screens: ScreenListItem[],
	dashboards: DashboardListItem[],
	searchKeyword = "",
	options: BuildOptions = {},
): DataPortalTreeNode[] {
	const keyword = normalizedSearch(searchKeyword);
	const includeUnavailable = options.includeUnavailable === true;
	const screenById = new Map(screens.map((screen) => [String(screen.id), screen]));
	const dashboardById = new Map(dashboards.map((dashboard) => [String(dashboard.id), dashboard]));
	const childrenByParent = new Map<number | null, DataPortalDirectory[]>();
	const bindingsByDirectory = new Map<number, DataPortalBinding[]>();

	for (const directory of [...directories].sort(compareOrdered)) {
		const parentId = directory.parent_id ?? null;
		const bucket = childrenByParent.get(parentId) ?? [];
		bucket.push(directory);
		childrenByParent.set(parentId, bucket);
	}
	for (const binding of [...bindings].sort(compareOrdered)) {
		const bucket = bindingsByDirectory.get(binding.directory_id) ?? [];
		bucket.push(binding);
		bindingsByDirectory.set(binding.directory_id, bucket);
	}

	const buildBranches = (
		parentId: number | null,
		ancestorMatched: boolean,
		path: Set<number>,
	): DataPortalTreeNode[] => {
		const result: DataPortalTreeNode[] = [];
		for (const directory of childrenByParent.get(parentId) ?? []) {
			if (path.has(directory.id)) continue;
			const nextPath = new Set(path);
			nextPath.add(directory.id);
			const directoryMatched = ancestorMatched || normalizedSearch(directory.name).includes(keyword);
			const childDirectories = buildBranches(directory.id, directoryMatched, nextPath);
			const contentLeaves = (bindingsByDirectory.get(directory.id) ?? [])
				.map((binding) => contentLeaf(binding, screenById, dashboardById, includeUnavailable))
				.filter((leaf): leaf is DataPortalTreeNode => leaf !== null)
				.filter((leaf) => {
					if (!keyword || directoryMatched) return true;
					const description = leaf.screen?.description ?? leaf.dashboard?.description;
					return normalizedSearch(`${leaf.title} ${normalize(description)}`).includes(keyword);
				});
			const children = [...childDirectories, ...contentLeaves];
			if (!keyword || directoryMatched || children.length > 0) {
				result.push({
					key: `directory:${directory.id}`,
					title: directory.name,
					nodeType: "DIRECTORY",
					directoryId: directory.id,
					children: children.length > 0 ? children : undefined,
				});
			}
		}
		return result;
	};

	return buildBranches(null, false, new Set());
}

export function countContentLeaves(nodes: DataPortalTreeNode[]): number {
	let count = 0;
	for (const node of nodes) {
		if (node.nodeType === "CONTENT" && node.availability === "AVAILABLE") count += 1;
		if (node.children) count += countContentLeaves(node.children);
	}
	return count;
}

export function findFirstAvailableContent(nodes: DataPortalTreeNode[]): DataPortalTreeNode | null {
	for (const node of nodes) {
		if (node.nodeType === "CONTENT" && node.availability === "AVAILABLE") return node;
		const child = node.children ? findFirstAvailableContent(node.children) : null;
		if (child) return child;
	}
	return null;
}

export function findContentNode(
	nodes: DataPortalTreeNode[],
	contentType: string | undefined,
	contentId: string | undefined,
): DataPortalTreeNode | null {
	for (const node of nodes) {
		if (
			node.nodeType === "CONTENT"
			&& node.availability === "AVAILABLE"
			&& node.contentType === contentType?.toUpperCase()
			&& String(node.contentId) === String(contentId)
		) {
			return node;
		}
		const child = node.children ? findContentNode(node.children, contentType, contentId) : null;
		if (child) return child;
	}
	return null;
}

export function findContentNodeByBinding(
	nodes: DataPortalTreeNode[],
	bindingId: string | undefined,
): DataPortalTreeNode | null {
	for (const node of nodes) {
		if (
			node.nodeType === "CONTENT"
			&& node.availability === "AVAILABLE"
			&& String(node.bindingId) === String(bindingId)
		) {
			return node;
		}
		const child = node.children ? findContentNodeByBinding(node.children, bindingId) : null;
		if (child) return child;
	}
	return null;
}
