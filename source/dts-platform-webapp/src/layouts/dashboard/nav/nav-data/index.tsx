import { useMemo } from "react";
import { Icon } from "@/components/icon";
import type { NavItemDataProps, NavProps } from "@/components/nav/types";
import type { MenuTree } from "#/entity";
import { PermissionType } from "#/enum";
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

const DEFAULT_MENU_ICON = "solar:menu-dots-bold-duotone";
const MENU_ICON_OVERRIDES: Record<string, string> = {
	// Root sections
	catalog: "solar:book-bold-duotone",
	modeling: "solar:documents-bold-duotone",
	governance: "solar:shield-check-bold-duotone",
	explore: "solar:compass-bold-duotone",
	// Section entries
	"catalog.assets": "solar:database-bold-duotone",
	"modeling.standards": "solar:document-text-bold-duotone",
	"governance.rules": "solar:checklist-bold-duotone",
	"governance.compliance": "solar:shield-warning-bold-duotone",
	"explore.workbench": "solar:code-square-bold-duotone",
	"explore.savedqueries": "solar:folder-with-files-bold-duotone",
	"explore.saved.queries": "solar:folder-with-files-bold-duotone",
	"savesavedqueries": "solar:folder-with-files-bold-duotone",
	"savedqueries": "solar:folder-with-files-bold-duotone",
};

const normalizeAuthCode = (value: unknown): string => {
	if (typeof value === "string") return value;
	if (value && typeof value === "object" && "code" in value && typeof (value as any).code === "string") {
		return (value as any).code as string;
	}
	return "";
};

const resolveMenuIcon = (node: MenuTree, meta: Record<string, any> | null) => {
	const explicitIcon =
		(typeof node.icon === "string" && node.icon.trim().length > 0 ? node.icon.trim() : undefined) ??
		(typeof meta?.icon === "string" && meta.icon.trim().length > 0 ? meta.icon.trim() : undefined);
	if (explicitIcon) {
		return explicitIcon;
	}
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
	for (const key of candidates) {
		const normalizedKey = normalizeIconLookupKey(key);
		if (normalizedKey && MENU_ICON_OVERRIDES[normalizedKey]) {
			return MENU_ICON_OVERRIDES[normalizedKey];
		}
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

const buildNavItems = (nodes: MenuTree[]): NavItemDataProps[] => {
	return buildNavItemsInternal(nodes, undefined, new Set<string>());
};

const buildNavItemsInternal = (
	nodes: MenuTree[],
	parentIcon: string | undefined,
	visited: Set<string>,
): NavItemDataProps[] => {
	if (!Array.isArray(nodes) || nodes.length === 0) {
		return [];
	}
	const items: NavItemDataProps[] = [];
	for (const node of nodes) {
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

const buildNavGroups = (menus: MenuTree[]): NavProps["data"] => {
	if (!Array.isArray(menus) || menus.length === 0) {
		return [];
	}
	const items = buildNavItems(menus);
	if (items.length === 0) {
		return [];
	}
	return [
		{
			name: undefined,
			items,
		},
	];
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
			allowedRoutes.paths.size === 0 ||
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

	return useMemo(() => filterNavData(navGroups, authCodes, allowedRoutes), [navGroups, authCodes, allowedRoutes]);
};
