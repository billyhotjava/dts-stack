import { Suspense, useMemo } from "react";
import { Outlet, ScrollRestoration, useLocation } from "react-router";
import { AuthGuard } from "@/components/auth/auth-guard";
import { LineLoading } from "@/components/loading";
import Page403 from "@/pages/sys/error/Page403";
import { useSettings } from "@/store/settingStore";
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
const Main = () => {
	const { themeStretch } = useSettings();
	const menus = useMenuStore((s) => s.menus || []);

	const { pathname } = useLocation();
	const authIndex = useMemo(() => buildAuthIndex(menus), [menus]);
	const currentNavAuth = useMemo(() => resolveAuthForPath(authIndex, pathname), [authIndex, pathname]);

	return (
		<AuthGuard checkAny={currentNavAuth} fallback={<Page403 />}>
			<main
				data-slot="slash-layout-main"
				className={cn(
					"flex-auto w-full flex flex-col text-sm",
					"transition-[max-width] duration-300 ease-in-out",
					"px-4 sm:px-6 py-4 sm:py-6 md:px-8 mx-auto",
					{
						"max-w-full": themeStretch,
						"xl:max-w-screen-xl": !themeStretch,
					},
				)}
				style={{
					willChange: "max-width",
				}}
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
