import { useMemo } from "react";
import { Navigate, useLocation } from "react-router";
import { LineLoading } from "@/components/loading";
import { GLOBAL_CONFIG } from "@/global-config";
import { useMenuStore } from "@/store/menuStore";
import {
	findBestMenuMatch,
	findMenuByPath,
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

const workbenchComponentPath = GLOBAL_CONFIG.enableSqlIdeV2
	? "/pages/explore/SqlIdePage"
	: "/pages/explore/QueryWorkbenchPage";

const PATH_COMPONENT_OVERRIDES: Record<string, string> = {
	// ── Platform pages ──
	"/governance": "/pages/governance/GovernanceCenterPage",
	"/catalog/assets": "/pages/catalog/DatasetsPage",
	"/catalog/asset-detail": "/pages/catalog/AssetDetailPage",
	"/catalog/search": "/pages/catalog/DataSearchPage",
	"/catalog/metadata": "/pages/catalog/MetadataPage",
	"/catalog/lineage": "/pages/catalog/LineagePage",
	"/catalog/lineage/impact": "/pages/catalog/LineageImpactPage",
	"/catalog/lineage/graph": "/pages/catalog/LineageGraphPage",
	"/catalog/lineage/columns": "/pages/catalog/LineageColumnsPage",
	"/catalog/lineage/import": "/pages/catalog/LineageImportPage",
	"/catalog/lineage/diff": "/pages/catalog/LineageDiffPage",
	"/catalog/quality": "/pages/catalog/QualityPage",
	"/catalog/data-products": "/pages/catalog/DataProductsPage",
	"/foundation/access-changes": "/pages/foundation/AccessChangesPage",
	"/foundation/connectors": "/pages/foundation/ConnectorRegistryPage",
	"/foundation/data-sources": "/pages/foundation/DataSourcesPage",
	"/foundation/jdbc-drivers": "/pages/foundation/JdbcDriversPage",
	"/explore/etl": "/pages/explore/etl/EltConsolePage",
	"/explore/etl/console": "/pages/explore/etl/EltConsolePage",
	"/explore/etl/scripts": "/pages/explore/etl/ScriptStudioPage",
	"/explore/etl/orchestration": "/pages/explore/etl/OrchestrationPage",
	"/explore/workbench": workbenchComponentPath,
	"/governance/subjects": "/pages/governance/SubjectAreasPage",
	"/governance/standards/glossary": "/pages/governance/GlossaryPage",
	"/governance/standards/elements": "/pages/governance/ElementsPage",
	"/governance/standards/reference": "/pages/governance/ReferenceCodesPage",
	"/governance/templates": "/pages/governance/TemplatesPage",
	"/governance/indicators/dictionary": "/pages/governance/IndicatorsPage",
	"/governance/rules": "/pages/governance/QualityRulesPage",
	"/governance/quality": "/pages/governance/QualityReportPage",
	"/governance/indicator-center": "/pages/governance/IndicatorCenterPage",
	"/governance/indicator-dashboard": "/pages/governance/IndicatorDashboardPage",
	"/governance/indicator-templates": "/pages/governance/IndicatorTemplatePage",
	"/governance/indicator-wizard": "/pages/governance/IndicatorTemplatePage",
	"/governance/indicator-list": "/pages/governance/IndicatorListPage",
	"/governance/indicator-store": "/pages/governance/IndicatorStorePage",
	"/governance/my-indicators": "/pages/governance/MyIndicatorDashboard",
	"/security/data-security": "/pages/security/data-security",
	"/security/dataset-access-approval": "/pages/security/DatasetAccessApprovalPage",
	"/services/apis": "/pages/services/ApiServicesPage",
	"/services/products": "/pages/services/DataProductsPage",
	"/services/tokens": "/pages/services/TokensPage",
	"/modeling/dbt-files": "/pages/modeling/DbtFileBrowserPage",
	"/ops/events": "/pages/ops/PlatformEventObservabilityPage",
	"/platform/events": "/pages/ops/PlatformEventObservabilityPage",
	"/ops/audit-evidence": "/pages/ops/AuditEvidencePage",
	"/platform/audit-evidence": "/pages/ops/AuditEvidencePage",
	"/ops/release-governance": "/pages/ops/ReleaseGovernancePage",
	"/platform/release-governance": "/pages/ops/ReleaseGovernancePage",
	"/ops/logs": "/pages/ops/OpsLogCenterPage",
	"/catalog/datasets/:id": "/pages/catalog/DatasetDetailPage",
	// Analytics pages are statically registered in static-routes.tsx — no overrides needed.
};

export const resolveDashboardComponentOverride = (path?: string) => {
	const normalized = normalizeMenuPath(path || "");
	if (!normalized) return "";
	const exact = PATH_COMPONENT_OVERRIDES[normalized];
	if (exact) return exact;
	const pathSegments = normalized.split("/").filter(Boolean);
	const matched = Object.entries(PATH_COMPONENT_OVERRIDES).find(([pattern]) => {
		if (!pattern.includes(":")) return false;
		const patternSegments = normalizeMenuPath(pattern).split("/").filter(Boolean);
		return (
			patternSegments.length === pathSegments.length &&
			patternSegments.every((segment, index) => segment.startsWith(":") || segment === pathSegments[index])
		);
	});
	return matched?.[1] || "";
};

const isWithinBase = (pathname: string, normalizedBase?: string) => {
	if (!normalizedBase) return true;
	return pathname === normalizedBase || pathname.startsWith(`${normalizedBase}/`);
};

const directOverrideParentPath = (pathname: string) => {
	if (pathname.startsWith("/catalog/datasets/")) return "/catalog/assets";
	return "";
};

export function DynamicMenuResolver({ base }: Props) {
	const location = useLocation();
	const menus = useMenuStore((s) => s.menus);

	const pathname = normalizeMenuPath(location.pathname || "/");
	const normalizedBase = base ? normalizeMenuPath(base) : "";
	const menusLoaded = Array.isArray(menus) && menus.length > 0;
	const directOverridePath = isWithinBase(pathname, normalizedBase) ? resolveDashboardComponentOverride(pathname) : "";
	const overrideParentPath = directOverrideParentPath(pathname);
	const fallbackMenuPath = useMemo(() => firstAccessibleMenuPath(Array.isArray(menus) ? menus : []), [menus]);
	const defaultRoute = GLOBAL_CONFIG.defaultRoute || "/workbench";

	const redirectToFallback = () => {
		if (defaultRoute && defaultRoute !== pathname) {
			return <Navigate to={defaultRoute} replace />;
		}
		if (fallbackMenuPath && fallbackMenuPath !== pathname) {
			return <Navigate to={fallbackMenuPath} replace />;
		}
		return <Navigate to="/404" replace />;
	};

	const match = useMemo(() => {
		if (!menusLoaded) return null;
		if (!isWithinBase(pathname, normalizedBase)) {
			return null;
		}
		return findBestMenuMatch(menus || [], pathname);
	}, [menus, menusLoaded, normalizedBase, pathname]);
	const directOverrideParent = useMemo(() => {
		if (!menusLoaded || !overrideParentPath) return null;
		return findMenuByPath(menus || [], overrideParentPath);
	}, [menus, menusLoaded, overrideParentPath]);

	if (!menusLoaded) {
		if (directOverridePath) {
			return <>{Component(directOverridePath)}</>;
		}
		return <LineLoading />;
	}

	if (directOverridePath && !match) {
		// menu-backed routes may have deeper non-menu operational detail pages, such as asset details.
		if (overrideParentPath && !directOverrideParent) {
			return redirectToFallback();
		}
		return <>{Component(directOverridePath)}</>;
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
		resolvedPath && !isExternalPath(resolvedPath) ? resolveDashboardComponentOverride(resolvedPath) : "";
	if (componentPath || overridePath) {
		return <>{Component(overridePath || componentPath)}</>;
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
