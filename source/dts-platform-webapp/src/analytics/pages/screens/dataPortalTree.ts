import type { ScreenListItem } from "../../api/analyticsApi";

export type DataPortalDomainNode = {
	id?: string | number | null;
	key?: string | number | null;
	code?: string | null;
	name?: string | null;
	nameZh?: string | null;
	label?: string | null;
	children?: DataPortalDomainNode[];
};

export type DataPortalTreeNode = {
	key: string;
	title: string;
	domainId?: string;
	screenId?: ScreenListItem["id"];
	classification?: string | null;
	publishedVersionNo?: number | null;
	children?: DataPortalTreeNode[];
};

const UNCLASSIFIED_KEY = "domain:__unclassified__";

function normalize(value: unknown): string {
	return value == null ? "" : String(value).trim();
}

function domainId(node: DataPortalDomainNode): string {
	return normalize(node.id ?? node.key ?? node.code);
}

function domainTitle(node: DataPortalDomainNode): string {
	return normalize(node.name ?? node.nameZh ?? node.label ?? node.code) || "未命名主题域";
}

function screenTitle(screen: ScreenListItem): string {
	return normalize(screen.name) || `大屏 ${String(screen.id)}`;
}

function sortScreens(screens: ScreenListItem[]): ScreenListItem[] {
	return [...screens].sort((left, right) =>
		screenTitle(left).localeCompare(screenTitle(right), "zh-CN", { numeric: true, sensitivity: "base" }),
	);
}

function matchesScreen(screen: ScreenListItem, keyword: string): boolean {
	if (!keyword) return true;
	return `${screenTitle(screen)} ${normalize(screen.description)}`.toLocaleLowerCase().includes(keyword);
}

function screenLeaf(screen: ScreenListItem): DataPortalTreeNode {
	return {
		key: `screen:${String(screen.id)}`,
		title: screenTitle(screen),
		screenId: screen.id,
		classification: screen.classification,
		publishedVersionNo: screen.publishedVersionNo,
	};
}

function collectKnownDomainIds(nodes: DataPortalDomainNode[], target: Set<string>): void {
	for (const node of nodes) {
		const id = domainId(node);
		if (id) target.add(id);
		if (Array.isArray(node.children)) collectKnownDomainIds(node.children, target);
	}
}

function buildDomainBranches(
	nodes: DataPortalDomainNode[],
	screensByDomain: Map<string, ScreenListItem[]>,
	keyword: string,
): DataPortalTreeNode[] {
	const branches: DataPortalTreeNode[] = [];
	for (const [index, node] of nodes.entries()) {
		const id = domainId(node);
		const title = domainTitle(node);
		const domainMatches = Boolean(keyword) && title.toLocaleLowerCase().includes(keyword);
		const childKeyword = domainMatches ? "" : keyword;
		const children = buildDomainBranches(Array.isArray(node.children) ? node.children : [], screensByDomain, childKeyword);
		const leaves = sortScreens(screensByDomain.get(id) ?? [])
			.filter((screen) => matchesScreen(screen, childKeyword))
			.map(screenLeaf);

		if (!keyword || domainMatches || children.length > 0 || leaves.length > 0) {
			branches.push({
				key: `domain:${id || `fallback-${index}`}`,
				title,
				domainId: id || undefined,
				children: children.length > 0 || leaves.length > 0 ? [...children, ...leaves] : undefined,
			});
		}
	}
	return branches;
}

export function buildDataPortalTree(
	domains: DataPortalDomainNode[],
	screens: ScreenListItem[],
	searchKeyword = "",
): DataPortalTreeNode[] {
	const keyword = normalize(searchKeyword).toLocaleLowerCase();
	const knownDomainIds = new Set<string>();
	collectKnownDomainIds(domains, knownDomainIds);

	const screensByDomain = new Map<string, ScreenListItem[]>();
	const unclassified: ScreenListItem[] = [];
	for (const screen of screens) {
		const id = normalize(screen.domainId);
		if (!id || !knownDomainIds.has(id)) {
			unclassified.push(screen);
			continue;
		}
		const bucket = screensByDomain.get(id) ?? [];
		bucket.push(screen);
		screensByDomain.set(id, bucket);
	}

	const tree = buildDomainBranches(domains, screensByDomain, keyword);
	const unclassifiedLeaves = sortScreens(unclassified).filter((screen) => matchesScreen(screen, keyword)).map(screenLeaf);
	if (unclassifiedLeaves.length > 0) {
		tree.push({ key: UNCLASSIFIED_KEY, title: "未归类", children: unclassifiedLeaves });
	}
	return tree;
}

export function countScreenLeaves(nodes: DataPortalTreeNode[]): number {
	let count = 0;
	for (const node of nodes) {
		if (node.screenId !== undefined) count += 1;
		if (node.children) count += countScreenLeaves(node.children);
	}
	return count;
}
