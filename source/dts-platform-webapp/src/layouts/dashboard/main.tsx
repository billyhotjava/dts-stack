import { lazy, Suspense, useMemo } from "react";
import { Outlet, ScrollRestoration, useLocation } from "react-router";
import { AuthGuard } from "@/components/auth/auth-guard";
import { LineLoading } from "@/components/loading";
import { cn } from "@/utils";
import { useMenuStore } from "@/store/menuStore";
import type { MenuTree } from "#/entity";
import {
	isExternalPath,
	isMenuDeleted,
	isMenuDisabled,
	isMenuHidden,
	parseMenuMetadata,
	resolveMenuPath,
} from "@/utils/menuTree";

const Page403 = lazy(() => import("@/pages/sys/error/Page403"));

const normalizeAuthCode = (value: unknown): string => {
	if (typeof value === "string") return value;
	if (value && typeof value === "object" && "code" in value && typeof (value as any).code === "string") {
		return (value as any).code as string;
	}
	return "";
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

const buildAuthIndex = (menus: MenuTree[]): Map<string, string[]> => {
	const index = new Map<string, string[]>();
	const stack = Array.isArray(menus) ? [...menus] : [];
	while (stack.length) {
		const node = stack.pop();
		if (!node) continue;
		if (isMenuDeleted(node)) continue;
		const meta = parseMenuMetadata(node.metadata);
		if (isMenuHidden(node, meta) || isMenuDisabled(node, meta)) {
			continue;
		}
		const path = resolveMenuPath(node, meta);
		const auth = normalizeAuth(meta?.auth ?? node.auth);
		if (path && !isExternalPath(path) && auth && auth.length > 0) {
			index.set(path, auth);
		}
		if (Array.isArray(node.children)) {
			for (const child of node.children) {
				stack.push(child as MenuTree);
			}
		}
	}
	return index;
};

const resolveAuthForPath = (index: Map<string, string[]>, pathname: string): string[] => {
	let matchedAuth: string[] = [];
	let maxLength = -1;
	for (const [path, auth] of index.entries()) {
		if (!path || auth.length === 0) continue;
		if (path === pathname || pathname.startsWith(path.endsWith("/") ? path : `${path}/`)) {
			if (path.length > maxLength) {
				matchedAuth = auth;
				maxLength = path.length;
			}
		}
	}
	return matchedAuth;
};

/**
 * find auth by path
 * @param path
 * @returns
 */
/**
 * Collect all reachable paths from the menu tree (for route-level access check).
 * Only includes non-hidden, non-disabled, non-deleted leaf and branch paths.
 */
const collectMenuPaths = (menus: MenuTree[]): Set<string> => {
	const paths = new Set<string>();
	const stack = Array.isArray(menus) ? [...menus] : [];
	while (stack.length) {
		const node = stack.pop();
		if (!node) continue;
		if (isMenuDeleted(node)) continue;
		const meta = parseMenuMetadata(node.metadata);
		if (isMenuHidden(node, meta) || isMenuDisabled(node, meta)) continue;
		const path = resolveMenuPath(node, meta);
		if (path && !isExternalPath(path)) {
			paths.add(path.endsWith("/") ? path.slice(0, -1) : path);
		}
		if (Array.isArray(node.children)) {
			for (const child of node.children) {
				stack.push(child as MenuTree);
			}
		}
	}
	return paths;
};

/**
 * Paths that are always reachable regardless of menu configuration.
 * Platform core pages (workbench, settings, etc.) are not gated by menu visibility.
 * BI paths (/bi/*) are NOT in this list — they are subject to menu-based access control.
 */
const ALWAYS_ALLOWED_PREFIXES = ["/workbench", "/explore", "/governance", "/catalog",
	"/foundation", "/modeling", "/security", "/services", "/ops", "/my", "/settings"];

/** Check if pathname is reachable from any menu path (exact or prefix match). */
const isPathInMenuTree = (menuPaths: Set<string>, pathname: string): boolean => {
	if (menuPaths.size === 0) return true; // menus not loaded → graceful degradation
	const normalized = pathname.endsWith("/") ? pathname.slice(0, -1) : pathname;
	// Platform core paths are always allowed
	if (ALWAYS_ALLOWED_PREFIXES.some((p) => normalized === p || normalized.startsWith(p + "/"))) return true;
	if (menuPaths.has(normalized)) return true;
	// Prefix match: /bi/dashboards/123 is reachable via /bi/dashboards
	for (const menuPath of menuPaths) {
		if (normalized.startsWith(menuPath + "/")) return true;
	}
	return false;
};

const Main = () => {
	const menus = useMenuStore((s) => s.menus || []);

	const { pathname } = useLocation();
	const authIndex = useMemo(() => buildAuthIndex(menus), [menus]);
	const currentNavAuth = useMemo(() => resolveAuthForPath(authIndex, pathname), [authIndex, pathname]);
	const menuPaths = useMemo(() => collectMenuPaths(menus), [menus]);
	const pathReachable = useMemo(() => isPathInMenuTree(menuPaths, pathname), [menuPaths, pathname]);

	// If menus are loaded but the path is not in the menu tree, block access.
	if (!pathReachable) {
		return (
			<Suspense fallback={<LineLoading />}>
				<Page403 />
			</Suspense>
		);
	}

	return (
		<AuthGuard
			checkAny={currentNavAuth}
			fallback={
				<Suspense fallback={<LineLoading />}>
					<Page403 />
				</Suspense>
			}
		>
			<main
				data-slot="slash-layout-main"
				className={cn(
					"flex-auto w-full max-w-none min-w-0 overflow-x-hidden flex flex-col text-sm",
					"px-4 sm:px-6 pb-6 pt-5 sm:pb-8 sm:pt-6 md:px-8 md:pb-10",
				)}
			>
				<Suspense fallback={<LineLoading />}>
					<Outlet />
					<ScrollRestoration />
				</Suspense>
			</main>
		</AuthGuard>
	);
};

export default Main;
