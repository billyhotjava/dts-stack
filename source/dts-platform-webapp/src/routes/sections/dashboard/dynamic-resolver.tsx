import { useMemo } from "react";
import { Navigate, useLocation } from "react-router";
import { LineLoading } from "@/components/loading";
import { GLOBAL_CONFIG } from "@/global-config";
import { useMenuStore } from "@/store/menuStore";
import {
	findBestMenuMatch,
	firstAccessibleChildPath,
	firstAccessibleMenuPath,
	isExternalPath,
	isMenuDeleted,
	isMenuDisabled,
	isMenuHidden,
	normalizeMenuPath,
	parseMenuMetadata,
	resolveMenuPath,
} from "@/utils/menuTree";
import { Component } from "./utils";

type Props = { base?: string };

const PATH_COMPONENT_OVERRIDES: Record<string, string> = {
	"/catalog/assets": "/pages/catalog/DatasetsPage",
	"/catalog/search": "/pages/catalog/DataSearchPage",
	"/catalog/metadata": "/pages/catalog/MetadataPage",
	"/catalog/lineage": "/pages/catalog/LineagePage",
	"/catalog/quality": "/pages/catalog/QualityPage",
	"/foundation/access-changes": "/pages/foundation/AccessChangesPage",
	"/foundation/data-sources": "/pages/foundation/DataSourcesPage",
	"/foundation/jdbc-drivers": "/pages/foundation/JdbcDriversPage",
	"/explore/etl/scripts": "/pages/explore/etl/ScriptStudioPage",
	"/explore/etl/orchestration": "/pages/explore/etl/OrchestrationPage",
	"/explore/workbench": "/pages/explore/QueryWorkbenchPage",
	"/governance/subjects": "/pages/governance/SubjectAreasPage",
	"/governance/standards/glossary": "/pages/governance/GlossaryPage",
	"/governance/standards/elements": "/pages/governance/ElementsPage",
	"/governance/standards/reference": "/pages/governance/ReferenceCodesPage",
	"/governance/templates": "/pages/governance/TemplatesPage",
	"/governance/indicators/dictionary": "/pages/governance/IndicatorsPage",
	"/governance/rules": "/pages/governance/QualityRulesPage",
	"/governance/quality": "/pages/catalog/QualityPage",
	"/security/data-security": "/pages/security/data-security",
	"/security/dataset-access-approval": "/pages/security/DatasetAccessApprovalPage",
	"/services/apis": "/pages/services/ApiServicesPage",
	"/services/products": "/pages/services/DataProductsPage",
	"/services/tokens": "/pages/services/TokensPage",
	"/services/bi-links": "/pages/services/BiLinksPage",
};

export function DynamicMenuResolver({ base }: Props) {
	const location = useLocation();
	const menus = useMenuStore((s) => s.menus);

	const pathname = normalizeMenuPath(location.pathname || "/");
	const normalizedBase = base ? normalizeMenuPath(base) : "";
	const menusLoaded = Array.isArray(menus) && menus.length > 0;
	const fallbackMenuPath = useMemo(() => firstAccessibleMenuPath(Array.isArray(menus) ? menus : []), [menus]);
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

	const resolvedPath = resolveMenuPath(match, meta);
	const componentPath = typeof match.component === "string" ? match.component.trim() : "";
	const overridePath =
		!componentPath && resolvedPath && !isExternalPath(resolvedPath) ? PATH_COMPONENT_OVERRIDES[resolvedPath] : "";
	if (componentPath || overridePath) {
		return <>{Component(componentPath || overridePath)}</>;
	}

	const redirectPath = firstAccessibleChildPath(match);
	if (redirectPath && redirectPath !== pathname && !isExternalPath(redirectPath)) {
		return <Navigate to={redirectPath} replace />;
	}

	if (resolvedPath && resolvedPath !== pathname && !isExternalPath(resolvedPath)) {
		return <Navigate to={resolvedPath} replace />;
	}

	return <Navigate to="/404" replace />;
}
