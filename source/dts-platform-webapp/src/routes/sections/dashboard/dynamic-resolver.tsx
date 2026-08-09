import { useMemo } from "react";
import { Navigate, useLocation } from "react-router";
import { LineLoading } from "@/components/loading";
import { GLOBAL_CONFIG } from "@/global-config";
import { StandardPackageActions } from "@/pages/governance/StandardPackageActions";
import type { StandardPackageSource } from "@/pages/governance/standardOwnerNavigation";
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

const PATH_REDIRECT_OVERRIDES: Record<string, string> = {
	"/workbench/data-management": "/workbench?section=data-management",
	"/services/consumption": "/workbench?section=consumption",
};

const STANDARD_PACKAGE_SOURCE_BY_PATH: Partial<Record<string, StandardPackageSource>> = {
	"/governance/standards/glossary": "glossary",
	"/governance/standards/elements": "elements",
	"/governance/standards/reference": "reference",
};

const renderDashboardComponent = (componentPath: string, pathname: string) => {
	const standardPackageSource = STANDARD_PACKAGE_SOURCE_BY_PATH[pathname];
	return (
		<>
			{standardPackageSource ? <StandardPackageActions source={standardPackageSource} /> : null}
			{Component(componentPath)}
		</>
	);
};

const buildDirectRedirectPath = (redirectPath: string, currentSearch: string) => {
	const [targetPath, targetSearch = ""] = redirectPath.split("?");
	const targetParams = new URLSearchParams(targetSearch);
	const section = targetParams.get("section");
	const next = new URLSearchParams(currentSearch);
	if (section) next.set("section", section);
	targetParams.forEach((value, key) => {
		if (key !== "section") next.set(key, value);
	});
	const queryString = next.toString();
	return queryString ? `${targetPath}?${queryString}` : targetPath;
};

const PATH_COMPONENT_OVERRIDES: Record<string, string> = {
	// ── Platform pages ──
	"/governance": "/pages/governance/GovernanceCenterPage",
	"/catalog/assets": "/pages/catalog/AssetOverviewPage",
	"/catalog/assets/ledger": "/pages/catalog/DatasetsPage",
	"/catalog/asset-detail": "/pages/catalog/LegacyAssetDetailRedirect",
	"/catalog/search": "/pages/catalog/DataSearchPage",
	"/catalog/metadata-management": "/pages/catalog/MetadataManagementPage",
	"/catalog/metadata": "/pages/catalog/MetadataPage",
	"/catalog/lineage": "/pages/catalog/LineagePage",
	"/catalog/lineage/impact": "/pages/catalog/LineageImpactPage",
	"/catalog/lineage/graph": "/pages/catalog/LineageGraphPage",
	"/catalog/lineage/columns": "/pages/catalog/LineageColumnsPage",
	"/catalog/lineage/import": "/pages/catalog/LineageImportPage",
	"/catalog/lineage/diff": "/pages/catalog/LineageDiffPage",
	"/catalog/quality": "/pages/catalog/QualityPage",
	"/catalog/data-products": "/pages/catalog/DataProductsPage",
	"/foundation/access-changes": "/pages/foundation/access/LegacyDataIntegrationRedirect",
	"/foundation/connectors": "/pages/foundation/ConnectorRegistryPage",
	"/foundation/connections": "/pages/foundation/access/ConnectionProfilesPage",
	"/foundation/data-sources": "/pages/foundation/access/AccessWorkspacePage",
	"/foundation/jdbc-drivers": "/pages/foundation/JdbcDriversPage",
	"/foundation/standard-package": "/pages/foundation/StandardPackagePage",
	"/workbench/todo": "/pages/workbench/WorkflowCenterPage",
	"/explore/etl": "/pages/explore/etl/EltConsolePage",
	"/explore/etl/console": "/pages/explore/etl/EltConsolePage",
	"/explore/etl/scripts": "/pages/explore/etl/ScriptStudioPage",
	"/explore/etl/orchestration": "/pages/explore/etl/OrchestrationPage",
	"/explore/workbench": workbenchComponentPath,
	"/studio/low-code-development": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/studio/projects": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/studio/sql-modeling": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/governance/subjects": "/pages/governance/SubjectAreasPage",
	"/governance/standards/glossary": "/pages/governance/GlossaryPage",
	"/governance/standards/elements": "/pages/governance/ElementsPage",
	"/governance/standards/reference": "/pages/governance/ReferenceCodesPage",
	"/governance/standards/units": "/pages/governance/MeasurementUnitsPage",
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
	"/modeling/dbt-files": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/modeling/plans": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/modeling/dimensions": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/modeling/models": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/modeling/models/:modelSpecId": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/modeling/metric-workbench": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/modeling/semantic/subjects": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/modeling/semantic/objects": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/modeling/semantic/metrics": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/modeling/semantic/models": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/modeling/semantic/publish": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/modeling/semantic/runs": "/pages/data-modeling/LegacyDataModelingRedirect",
	"/ops/events": "/pages/ops/PlatformEventObservabilityPage",
	"/platform/events": "/pages/ops/PlatformEventObservabilityPage",
	"/ops/audit-evidence": "/pages/ops/AuditEvidencePage",
	"/platform/audit-evidence": "/pages/ops/AuditEvidencePage",
	"/ops/release-governance": "/pages/ops/ReleaseGovernancePage",
	"/platform/release-governance": "/pages/ops/ReleaseGovernancePage",
	"/ops/logs": "/pages/ops/OpsLogCenterPage",
	"/ops/overview": "/pages/ops/OpsOverviewPage",
	"/ops/instances": "/pages/ops/OpsInstancesPage",
	"/ops/alerts": "/pages/ops/OpsAlertLogPage",
	"/ops/backfill": "/pages/ops/OpsBackfillPage",
	"/catalog/datasets/:id": "/pages/catalog/DatasetDetailPage",
	// Analytics pages are statically registered in static-routes.tsx — no overrides needed.
};

export const resolveDashboardComponentOverride = (path?: string) => {
	const normalized = normalizeMenuPath(path || "");
	if (!normalized) return "";
	if (normalized === "/data-modeling" || normalized.startsWith("/data-modeling/")) {
		return "/pages/data-modeling/DataModelingPage";
	}
	if (
		normalized === "/modeling" ||
		normalized.startsWith("/modeling/") ||
		normalized === "/studio/modeling" ||
		normalized.startsWith("/studio/modeling/")
	) {
		return "/pages/data-modeling/LegacyDataModelingRedirect";
	}
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
	const directRedirectPath = PATH_REDIRECT_OVERRIDES[pathname];
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

	if (directRedirectPath) {
		return <Navigate to={buildDirectRedirectPath(directRedirectPath, location.search)} replace />;
	}

	if (!menusLoaded) {
		if (directOverridePath) {
			return renderDashboardComponent(directOverridePath, pathname);
		}
		return <LineLoading />;
	}

	if (directOverridePath && !match) {
		// menu-backed routes may have deeper non-menu operational detail pages, such as asset details.
		if (overrideParentPath && !directOverrideParent) {
			return redirectToFallback();
		}
		return renderDashboardComponent(directOverridePath, pathname);
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
	if (
		directOverridePath &&
		overrideParentPath &&
		directOverrideParent &&
		pathname !== resolvedPath &&
		directOverridePath !== (overridePath || componentPath)
	) {
		return renderDashboardComponent(directOverridePath, pathname);
	}
	if (componentPath || overridePath) {
		return renderDashboardComponent(overridePath || componentPath, pathname);
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
