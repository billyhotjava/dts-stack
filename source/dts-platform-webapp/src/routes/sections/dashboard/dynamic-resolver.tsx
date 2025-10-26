import { useMemo } from "react";
import { Navigate, useLocation } from "react-router";
import { LineLoading } from "@/components/loading";
import { useMenuStore } from "@/store/menuStore";
import {
	firstAccessibleChildPath,
	firstAccessibleMenuPath,
	findBestMenuMatch,
	isExternalPath,
	isMenuDeleted,
	isMenuDisabled,
	isMenuHidden,
	normalizeMenuPath,
	parseMenuMetadata,
	resolveMenuPath,
} from "@/utils/menuTree";
import { Component } from "./utils";
import { GLOBAL_CONFIG } from "@/global-config";

type Props = { base?: string };

export function DynamicMenuResolver({ base }: Props) {
	const location = useLocation();
	const menus = useMenuStore((s) => s.menus);

	const pathname = normalizeMenuPath(location.pathname || "/");
	const normalizedBase = base ? normalizeMenuPath(base) : "";
	const menusLoaded = Array.isArray(menus) && menus.length > 0;
	const fallbackMenuPath = useMemo(
		() => firstAccessibleMenuPath(Array.isArray(menus) ? menus : []),
		[menus],
	);
	const defaultRoute = GLOBAL_CONFIG.defaultRoute || "/dashboard/workbench";

	const redirectToFallback = () => {
		if (fallbackMenuPath && fallbackMenuPath !== pathname) {
			return <Navigate to={fallbackMenuPath} replace />;
		}
		if (defaultRoute && defaultRoute !== pathname) {
			return <Navigate to={defaultRoute} replace />;
		}
		return <Navigate to="/404" replace />;
	};

	const match = useMemo(() => {
		if (!menusLoaded) return null;
		if (normalizedBase && pathname && !pathname.startsWith(normalizedBase)) {
			return null;
		}
		return findBestMenuMatch(menus || [], pathname);
	}, [menus, menusLoaded, normalizedBase, pathname]);

	if (!menusLoaded) {
		return <LineLoading />;
	}

	if (!match) {
		return redirectToFallback();
	}

	const meta = parseMenuMetadata(match.metadata);
	if (isMenuDeleted(match) || isMenuHidden(match, meta) || isMenuDisabled(match, meta)) {
		return redirectToFallback();
	}

	const componentPath = typeof match.component === "string" ? match.component.trim() : "";
	if (componentPath) {
		return <>{Component(componentPath)}</>;
	}

	const redirectPath = firstAccessibleChildPath(match);
	if (redirectPath && redirectPath !== pathname && !isExternalPath(redirectPath)) {
		return <Navigate to={redirectPath} replace />;
	}

	const resolvedPath = resolveMenuPath(match, meta);
	if (resolvedPath && resolvedPath !== pathname && !isExternalPath(resolvedPath)) {
		return <Navigate to={resolvedPath} replace />;
	}

	return <Navigate to="/404" replace />;
}
