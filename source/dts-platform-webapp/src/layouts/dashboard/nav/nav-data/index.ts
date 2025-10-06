import { useMemo } from "react";
import type { NavItemDataProps, NavProps } from "@/components/nav/types";
import type { MenuTree } from "#/entity";
import { useUserPermissions, useUserRoles } from "@/store/userStore";
import { useMenuStore } from "@/store/menuStore";
import { checkAny } from "@/utils";
import { frontendNavData } from "./nav-data-frontend";

type AllowedRouteIndex = {
	paths: Set<string>;
	codes: Set<string>;
};

const normalizeAuthCode = (value: unknown): string => {
	if (typeof value === "string") return value;
	if (value && typeof value === "object" && "code" in value && typeof (value as any).code === "string") {
		return (value as any).code as string;
	}
	return "";
};

const ensureLeadingSlash = (path: string): string => {
	const trimmed = path.trim();
	if (!trimmed) return "";
	const normalized = trimmed.replace(/^#+/, "").replace(/^\/+/, "").replace(/\/+$/, "");
	return normalized ? `/${normalized}` : "";
};

const collectAllowedRoutes = (menus: MenuTree[]): AllowedRouteIndex => {
	const allowed: AllowedRouteIndex = {
		paths: new Set<string>(),
		codes: new Set<string>(),
	};
	const stack: Array<{ node: MenuTree; parentPath: string }> = menus.map((node) => ({
		node,
		parentPath: "",
	}));

	while (stack.length) {
		const current = stack.pop();
		if (!current) continue;
		const { node, parentPath } = current;
		const resolvedPath = resolveMenuPath(node.path, parentPath);
		if (resolvedPath) {
			allowed.paths.add(resolvedPath);
		}
		if (node.code) {
			const code = String(node.code);
			if (code) {
				allowed.codes.add(code);
				allowed.codes.add(code.toLowerCase());
				allowed.codes.add(code.toUpperCase());
			}
		}
		if (Array.isArray(node.children) && node.children.length > 0) {
			const nextParent = resolvedPath || parentPath;
			for (const child of node.children) {
				stack.push({ node: child as MenuTree, parentPath: nextParent });
			}
		}
	}
	return allowed;
};

const resolveMenuPath = (path: unknown, parentPath: string): string => {
	if (!path || typeof path !== "string") {
		return "";
	}
	const trimmed = path.trim();
	if (!trimmed) return "";
	if (/^(https?:|mailto:|tel:)/i.test(trimmed)) {
		return "";
	}
	const withoutHash = trimmed.replace(/^#+/, "");
	if (!withoutHash) return "";
	if (withoutHash.startsWith("/")) {
		return ensureLeadingSlash(withoutHash);
	}
	const base = parentPath ? parentPath.replace(/\/+$/, "") : "";
	const childSegment = withoutHash.replace(/^\/+/, "");
	const combined = base ? `${base}/${childSegment}` : childSegment;
	return ensureLeadingSlash(combined);
};

/**
 * 递归处理导航数据，过滤掉没有权限的项目
 * @param items 导航项目数组
 * @param permissions 权限列表
 * @param allowedRoutes 后端授权的路由集合
 * @returns 过滤后的导航项目数组
 */
const filterItems = (
	items: NavItemDataProps[],
	permissions: string[],
	allowedRoutes: AllowedRouteIndex,
): NavItemDataProps[] => {
	return items.reduce<NavItemDataProps[]>((acc, item) => {
		const filteredChildren = item.children
			? filterItems(item.children, permissions, allowedRoutes)
			: [];
		const hasPermission = item.auth ? checkAny(item.auth, permissions) : true;
		const normalizedPath = item.path ? ensureLeadingSlash(item.path) : "";
		const isRouteAllowed =
			allowedRoutes.paths.size === 0 ||
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

/**
 *
 * 根据权限过滤导航数据
 * @param permissions 权限列表
 * @param allowedRoutes 后端授权的路由集合
 * @returns 过滤后的导航数据
 */
export const filterNavData = (permissions: string[], allowedRoutes: AllowedRouteIndex): NavProps["data"] => {
	return frontendNavData
		.map((group) => {
			const filteredItems = filterItems(group.items, permissions, allowedRoutes);
			if (filteredItems.length === 0) {
				return null;
			}
			return {
				...group,
				items: filteredItems,
			};
		})
		.filter((group): group is NonNullable<typeof group> => group !== null);
};

/**
 * Hook to get filtered navigation data based on user permissions
 * @returns Filtered navigation data
 */
export const useFilteredNavData = () => {
	const roles = useUserRoles();
	const permissions = useUserPermissions();
	const menus = useMenuStore((s) => s.menus);

	const authCodes = useMemo(() => {
		const roleCodes = roles.map((role) => normalizeAuthCode(role)).filter(Boolean);
		const permissionCodes = permissions.map((permission) => normalizeAuthCode(permission)).filter(Boolean);
		return Array.from(new Set([...roleCodes, ...permissionCodes]));
	}, [roles, permissions]);

	const allowedRoutes = useMemo(() => collectAllowedRoutes(menus || []), [menus]);

	return useMemo(() => filterNavData(authCodes, allowedRoutes), [authCodes, allowedRoutes]);
};

const isPathAllowed = (path: string, allowedPaths: Set<string>): boolean => {
	if (!path) {
		return false;
	}
	if (allowedPaths.has(path)) {
		return true;
	}
	for (const candidate of allowedPaths) {
		if (!candidate || !candidate.startsWith("/")) {
			continue;
		}
		const normalizedCandidate = candidate.endsWith("/") ? candidate.slice(0, -1) : candidate;
		if (!normalizedCandidate) {
			continue;
		}
		const prefix = `${normalizedCandidate}/`;
		if (path.startsWith(prefix)) {
			return true;
		}
	}
	return false;
};
