import { useMemo } from "react";
import type { MenuTree } from "#/entity";
import { PermissionType } from "#/enum";
import { Icon } from "@/components/icon";
import type { NavItemDataProps, NavProps } from "@/components/nav/types";
import { useMenuStore } from "@/store/menuStore";
import { useUserPermissions, useUserRoles } from "@/store/userStore";
import { checkAny } from "@/utils";
import {
	firstAccessibleChildPath,
	hasMenuComponent,
	isContainerMenu,
	isExternalPath,
	isMenuDeleted,
	isMenuDisabled,
	isMenuHidden,
	normalizeMenuPath,
	parseMenuMetadata,
	resolveMenuPath,
} from "@/utils/menuTree";

type AllowedRouteIndex = {
	paths: Set<string>;
	codes: Set<string>;
};

const DEFAULT_MENU_ICON = "local:ic-menu";
const MENU_ICON_OVERRIDES: Record<string, string> = {
	// Root sections
	catalog: "local:ic-catalog",
	modeling: "local:ic-modeling",
	governance: "local:ic-governance",
	explore: "local:ic-explore",
	// Section entries
	"catalog.assets": "local:ic-assets",
	"modeling.standards": "local:ic-standards",
	"governance.rules": "local:ic-rules",
	"governance.compliance": "local:ic-compliance",
	"explore.workbench": "local:ic-workbench",
	scripts: "local:ic-scripts",
	"studio.scripts": "local:ic-scripts",
	"explore.savedqueries": "local:ic-savedqueries",
	"explore.saved.queries": "local:ic-savedqueries",
	savesavedqueries: "local:ic-savedqueries",
	savedqueries: "local:ic-savedqueries",
};

const normalizeAuthCode = (value: unknown): string => {
	if (typeof value === "string") return value;
	if (value && typeof value === "object" && "code" in value && typeof (value as any).code === "string") {
		return (value as any).code as string;
	}
	return "";
};

const resolveMenuIcon = (node: MenuTree, meta: Record<string, any> | null) => {
	const candidates: string[] = [];
	const sectionKey =
		typeof meta?.sectionKey === "string" && meta.sectionKey.trim().length > 0
			? meta.sectionKey
			: typeof meta?.key === "string" && meta.key.trim().length > 0
				? meta.key
				: undefined;
	const entryKey = typeof meta?.entryKey === "string" && meta.entryKey.trim().length > 0 ? meta.entryKey : undefined;
	const normalizedPath = resolveMenuPath(node, meta);
	if (sectionKey && entryKey) {
		candidates.push(`${sectionKey}.${entryKey}`);
	}
	if (sectionKey) {
		candidates.push(sectionKey);
	}
	if (entryKey) {
		candidates.push(entryKey);
	}
	if (typeof node.code === "string" && node.code.trim().length > 0) {
		candidates.push(node.code.trim());
	}
	if (normalizedPath) {
		const pathKey = normalizedPath.replace(/^\//, "").replace(/\/+/g, ".");
		if (pathKey) {
			candidates.push(pathKey);
		}
	}
	// Local overrides take priority over explicit icons from the database,
	// ensuring menu items always resolve to bundled icons in offline environments.
	for (const key of candidates) {
		const normalizedKey = normalizeIconLookupKey(key);
		if (normalizedKey && MENU_ICON_OVERRIDES[normalizedKey]) {
			return MENU_ICON_OVERRIDES[normalizedKey];
		}
	}
	const explicitIcon =
		(typeof node.icon === "string" && node.icon.trim().length > 0 ? node.icon.trim() : undefined) ??
		(typeof meta?.icon === "string" && meta.icon.trim().length > 0 ? meta.icon.trim() : undefined);
	if (explicitIcon) {
		return explicitIcon;
	}
	return undefined;
};

const normalizeIconLookupKey = (value: string | undefined | null): string => {
	if (!value) return "";
	return value
		.toString()
		.toLowerCase()
		.replace(/[^a-z0-9]+/g, ".")
		.replace(/^\.+|\.+$/g, "");
};

const getMenuTitle = (node: MenuTree, meta: Record<string, any> | null): string => {
	const candidate = meta?.title ?? meta?.label ?? meta?.titleKey;
	if (typeof candidate === "string" && candidate.trim().length > 0) {
		return candidate.trim();
	}
	if (typeof node.displayName === "string" && node.displayName.trim().length > 0) {
		return node.displayName.trim();
	}
	if (typeof node.name === "string" && node.name.trim().length > 0) {
		return node.name.trim();
	}
	return "未命名菜单";
};

const normalizeAuth = (value: unknown): string[] | undefined => {
	if (!value) return undefined;
	if (Array.isArray(value)) {
		const normalized = value.map((entry) => normalizeAuthCode(entry)).filter(Boolean);
		return normalized.length > 0 ? normalized : undefined;
	}
	if (typeof value === "string") {
		const normalized = normalizeAuthCode(value);
		return normalized ? [normalized] : undefined;
	}
	return undefined;
};

const isNavigableMenu = (node: MenuTree, meta: Record<string, any> | null): boolean => {
	if (!node || isMenuDeleted(node)) {
		return false;
	}
	if (isMenuHidden(node, meta) || isMenuDisabled(node, meta)) {
		return false;
	}
	const typeValue = typeof node.type === "number" ? node.type : undefined;
	const hasComponent = hasMenuComponent(node);
	const hasChildren = Array.isArray(node.children) && node.children.length > 0;
	const hasPath = !!resolveMenuPath(node, meta);
	if (typeValue === undefined) {
		return hasComponent || hasChildren || hasPath;
	}
	if (typeValue >= PermissionType.MENU) {
		return hasComponent || hasPath;
	}
	return hasComponent || hasChildren || hasPath;
};

const collectAllowedRoutes = (menus: MenuTree[]): AllowedRouteIndex => {
	const allowed: AllowedRouteIndex = {
		paths: new Set<string>(),
		codes: new Set<string>(),
	};
	const stack = Array.isArray(menus) ? [...menus] : [];
	while (stack.length) {
		const node = stack.pop();
		if (!node || isMenuDeleted(node)) continue;
		const meta = parseMenuMetadata(node.metadata);
		if (isNavigableMenu(node, meta)) {
			// DB stores full paths (e.g., "bi/home"), no parentPath joining needed.
			const path = resolveMenuPath(node, meta);
			if (path && !isExternalPath(path)) {
				allowed.paths.add(path);
			}
			if (node.code) {
				const code = String(node.code);
				if (code) {
					allowed.codes.add(code);
					allowed.codes.add(code.toLowerCase());
					allowed.codes.add(code.toUpperCase());
				}
			}
		}
		if (Array.isArray(node.children)) {
			for (const child of node.children) {
				stack.push(child as MenuTree);
			}
		}
	}
	return allowed;
};


const resolveOrderValue = (node: MenuTree, meta: Record<string, any> | null): number => {
	const candidates = [
		(node as any)?.order,
		meta?.order,
		(meta as any)?.sortOrder,
		(meta as any)?.sort,
		(meta as any)?.orderNum,
	];
	for (const value of candidates) {
		if (typeof value === "number" && Number.isFinite(value)) return value;
		if (typeof value === "string") {
			const parsed = Number.parseFloat(value);
			if (Number.isFinite(parsed)) return parsed;
		}
	}
	return Number.POSITIVE_INFINITY;
};

const isRootWorkbench = (node: MenuTree, meta: Record<string, any> | null): boolean => {
	const path = resolveMenuPath(node, meta);
	return path === "/workbench" || path === "/dashboard/workbench";
};

const buildNavItemsInternal = (
	nodes: MenuTree[],
	parentIcon: string | undefined,
	visited: Set<string>,
): NavItemDataProps[] => {
	if (!Array.isArray(nodes) || nodes.length === 0) {
		return [];
	}
	const isRootLevel = parentIcon === undefined;
	const sortedNodes = [...nodes].sort((a, b) => {
		const metaA = parseMenuMetadata(a?.metadata);
		const metaB = parseMenuMetadata(b?.metadata);

		if (isRootLevel) {
			const aIsWorkbench = isRootWorkbench(a, metaA);
			const bIsWorkbench = isRootWorkbench(b, metaB);
			if (aIsWorkbench !== bIsWorkbench) return aIsWorkbench ? -1 : 1;
		}

		const orderA = resolveOrderValue(a, metaA);
		const orderB = resolveOrderValue(b, metaB);
		if (orderA !== orderB) return orderA - orderB;

		const titleA = getMenuTitle(a, metaA);
		const titleB = getMenuTitle(b, metaB);
		if (titleA !== titleB) return titleA.localeCompare(titleB, "zh-Hans-CN");

		const idA = typeof a.id === "string" ? a.id : "";
		const idB = typeof b.id === "string" ? b.id : "";
		return idA.localeCompare(idB);
	});
	const items: NavItemDataProps[] = [];
	for (const node of sortedNodes) {
		const navItem = createNavItem(node, parentIcon, visited);
		if (navItem) {
			items.push(navItem);
		}
	}
	return items;
};

const createNavItem = (
	node: MenuTree,
	parentIcon: string | undefined,
	visited: Set<string>,
): NavItemDataProps | null => {
	if (!node || isMenuDeleted(node)) {
		return null;
	}
	const meta = parseMenuMetadata(node.metadata);
	if (isMenuHidden(node, meta) || isMenuDisabled(node, meta)) {
		return null;
	}

	const dedupeKey = resolveDedupeKey(node, meta);
	if (dedupeKey && visited.has(dedupeKey)) {
		return null;
	}
	if (dedupeKey) {
		visited.add(dedupeKey);
	}

	const explicitOrMappedIcon = resolveMenuIcon(node, meta) ?? parentIcon ?? DEFAULT_MENU_ICON;
	const children = buildNavItemsInternal(
		Array.isArray(node.children) ? node.children : [],
		explicitOrMappedIcon,
		visited,
	);

	// DB stores full paths (e.g., "bi/home" not "home"), so no parentPath joining needed.
	const rawPath = resolveMenuPath(node, meta);
	let path = rawPath;
	if (!path && children.length > 0) {
		path = children[0]?.path ?? firstAccessibleChildPath(node) ?? "";
	}

	const lacksComponent = !hasMenuComponent(node);
	const noChildren = children.length === 0;
	if ((isContainerMenu(node) && noChildren) || (!path && lacksComponent && noChildren)) {
		return null;
	}
	if (!path) {
		return null;
	}

	const item: NavItemDataProps = {
		title: getMenuTitle(node, meta),
		path,
		icon: <Icon icon={explicitOrMappedIcon} size="24" />,
		caption: meta?.caption ?? node.caption,
		info: meta?.info ?? node.info,
		auth: normalizeAuth(meta?.auth ?? node.auth),
		hidden: Boolean(node.hidden),
		disabled: false,
		children: children.length > 0 ? children : undefined,
	};
	return item;
};

const resolveDedupeKey = (node: MenuTree, meta: Record<string, any> | null): string | null => {
	if (node.id) {
		return `id:${node.id}`;
	}
	const normalizedPath = resolveMenuPath(node, meta);
	if (normalizedPath) {
		return `path:${normalizedPath}`;
	}
	if (node.code) {
		return `code:${node.code}`;
	}
	return null;
};

/**
 * Menu category definitions — each group is separated by a divider in the sidebar.
 * `keys` match the `sectionKey` (or `key`) from menu metadata.
 * `flatten` strips children so the item renders as a single external link.
 */
const NAV_CATEGORY_GROUPS: { name?: string; keys: string[]; flatten?: boolean }[] = [
	{ name: undefined, keys: ["workbench"] },
	{ name: "数据集成", keys: ["resource"] },
	{ name: "数据开发与运维", keys: ["studio"] },
	{ name: "数据治理", keys: ["governance"] },
	{ name: "数据分析与服务", keys: ["consumption"] },
];

const resolveSectionKey = (node: MenuTree): string => {
	const meta = parseMenuMetadata(node.metadata);
	const raw = meta?.sectionKey ?? meta?.key;
	return typeof raw === "string" ? raw.trim().toLowerCase() : "";
};

const buildNavGroups = (menus: MenuTree[]): NavProps["data"] => {
	if (!Array.isArray(menus) || menus.length === 0) {
		return [];
	}

	// Index top-level nodes by section key
	const keyToNodes = new Map<string, MenuTree[]>();
	const uncategorized: MenuTree[] = [];
	for (const node of menus) {
		const sk = resolveSectionKey(node);
		if (!sk) {
			uncategorized.push(node);
			continue;
		}
		const list = keyToNodes.get(sk);
		if (list) {
			list.push(node);
		} else {
			keyToNodes.set(sk, [node]);
		}
	}

	const visited = new Set<string>();
	const groups: NavProps["data"] = [];

	for (const { name, keys, flatten } of NAV_CATEGORY_GROUPS) {
		const nodes: MenuTree[] = [];
		for (const key of keys) {
			const matched = keyToNodes.get(key);
			if (matched) {
				nodes.push(...matched);
				keyToNodes.delete(key);
			}
		}
		if (nodes.length === 0) continue;

		const items = buildNavItemsInternal(nodes, undefined, visited);
		if (items.length === 0) continue;

		// Flatten: strip children so item acts as a single (external) link.
		// Use the first child's path when the parent's own path isn't external.
		if (flatten) {
			for (const item of items) {
				if (item.children?.length && !isExternalPath(item.path)) {
					item.path = item.children[0].path;
				}
				item.children = undefined;
			}
		}

		groups.push({ name, items });
	}

	// Append any nodes whose sectionKey didn't match a defined category
	const remaining: MenuTree[] = [...uncategorized];
	for (const nodes of keyToNodes.values()) {
		remaining.push(...nodes);
	}
	if (remaining.length > 0) {
		const items = buildNavItemsInternal(remaining, undefined, visited);
		if (items.length > 0) {
			groups.push({ name: undefined, items });
		}
	}

	return groups;
};

const isPathAllowed = (path: string, allowedPaths: Set<string>): boolean => {
	if (!path) {
		return false;
	}
	if (isExternalPath(path)) {
		return true;
	}
	if (allowedPaths.has(path)) {
		return true;
	}
	for (const candidate of allowedPaths) {
		if (!candidate) continue;
		const normalizedCandidate = candidate.endsWith("/") ? candidate.slice(0, -1) : candidate;
		if (!normalizedCandidate) continue;
		const prefix = normalizedCandidate === "/" ? "/" : `${normalizedCandidate}/`;
		if (path === normalizedCandidate || path.startsWith(prefix)) {
			return true;
		}
	}
	return false;
};

const filterItems = (
	items: NavItemDataProps[],
	permissions: string[],
	allowedRoutes: AllowedRouteIndex,
): NavItemDataProps[] => {
	return items.reduce<NavItemDataProps[]>((acc, item) => {
		const filteredChildren = item.children ? filterItems(item.children, permissions, allowedRoutes) : [];
		const hasPermission = item.auth ? checkAny(item.auth, permissions) : true;
		const normalizedPath = normalizeMenuPath(item.path);
		const isRouteAllowed =
			isExternalPath(normalizedPath) ||
			isPathAllowed(normalizedPath, allowedRoutes.paths) ||
			filteredChildren.length > 0;

		if (!hasPermission || !isRouteAllowed) {
			return acc;
		}

		acc.push({
			...item,
			children: filteredChildren.length > 0 ? filteredChildren : undefined,
		});
		return acc;
	}, []);
};

const filterNavData = (
	groups: NavProps["data"],
	permissions: string[],
	allowedRoutes: AllowedRouteIndex,
): NavProps["data"] => {
	return groups.reduce<NavProps["data"]>((acc, group) => {
		const filteredItems = filterItems(group.items, permissions, allowedRoutes);
		if (filteredItems.length === 0) {
			return acc;
		}
		acc.push({
			...group,
			items: filteredItems,
		});
		return acc;
	}, []);
};

/**
 * Fallback navigation when the menu API is unavailable.
 * Ensures the platform is still navigable even if dts-admin is down.
 */
/**
 * Minimal fallback when dts-admin is unreachable. Only contains platform-core
 * pages (workbench) — NO BI items, because BI visibility is role-gated.
 */
const FALLBACK_NAV_DATA: NavProps["data"] = [
	{
		name: "数据平台",
		items: [
			{ path: "/workbench", title: "工作台", icon: "solar:widget-3-bold-duotone" },
		],
	},
];

export const useFilteredNavData = () => {
	const roles = useUserRoles();
	const permissions = useUserPermissions();
	const menus = useMenuStore((s) => s.menus || []);

	const authCodes = useMemo(() => {
		const roleCodes = roles.map((role) => normalizeAuthCode(role)).filter(Boolean);
		const permissionCodes = permissions.map((permission) => normalizeAuthCode(permission)).filter(Boolean);
		return Array.from(new Set([...roleCodes, ...permissionCodes]));
	}, [roles, permissions]);

	const navGroups = useMemo(() => buildNavGroups(menus), [menus]);
	const allowedRoutes = useMemo(() => collectAllowedRoutes(menus), [menus]);

	const filtered = useMemo(() => filterNavData(navGroups, authCodes, allowedRoutes), [navGroups, authCodes, allowedRoutes]);

	// Fallback: if menu API returned nothing, show minimal nav so the platform is still usable.
	if (filtered.length === 0 && menus.length === 0) {
		return FALLBACK_NAV_DATA;
	}
	return filtered;
};
