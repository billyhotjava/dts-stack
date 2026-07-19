import { lazy, Suspense } from "react";
import { Navigate, useLocation, type RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";
import { metricsServiceHrefFromPlatformLocation } from "./metricsServiceRoutes";

// ── Platform pages ──
const TransformPage = lazy(() => import("@/pages/explore/etl/TransformPage"));
const EltConsolePage = lazy(() => import("@/pages/explore/etl/EltConsolePage"));
const TransformCreatePage = lazy(() => import("@/pages/explore/etl/TransformCreatePage"));
const TransformDetailPage = lazy(() => import("@/pages/explore/etl/TransformDetailPage"));
const TransformExecutionHistoryPage = lazy(() => import("@/pages/explore/etl/TransformExecutionHistoryPage"));
const DataSourceDetailPage = lazy(() => import("@/pages/foundation/DataSourceDetailPage"));
const StandardPackagePage = lazy(() => import("@/pages/foundation/StandardPackagePage"));
const AssetOverviewPage = lazy(() => import("@/pages/catalog/AssetOverviewPage"));
const DatasetsPage = lazy(() => import("@/pages/catalog/DatasetsPage"));
const MetadataManagementPage = lazy(() => import("@/pages/catalog/MetadataManagementPage"));
const AssetOwnershipPage = lazy(() => import("@/pages/governance/AssetOwnershipPage"));
const AssetGrantPage = lazy(() => import("@/pages/governance/AssetGrantPage"));
const MyGrantsPage = lazy(() => import("@/pages/governance/MyGrantsPage"));
const PermissionAuditPage = lazy(() => import("@/pages/governance/PermissionAuditPage"));
const LineagePage = lazy(() => import("@/pages/catalog/LineagePage"));
const PlatformEventObservabilityPage = lazy(() => import("@/pages/ops/PlatformEventObservabilityPage"));
const AuditEvidencePage = lazy(() => import("@/pages/ops/AuditEvidencePage"));
const ReleaseGovernancePage = lazy(() => import("@/pages/ops/ReleaseGovernancePage"));
const OpsOverviewPage = lazy(() => import("@/pages/ops/OpsOverviewPage"));
const OpsInstancesPage = lazy(() => import("@/pages/ops/OpsInstancesPage"));
const OpsAlertLogPage = lazy(() => import("@/pages/ops/OpsAlertLogPage"));
const OpsBackfillPage = lazy(() => import("@/pages/ops/OpsBackfillPage"));
const WorkflowCenterPage = lazy(() => import("@/pages/workbench/WorkflowCenterPage"));
const LowCodeDevelopmentPage = lazy(() => import("@/pages/modeling/LowCodeDevelopmentPage"));
const StudioProjectsPage = lazy(() => import("@/pages/modeling/ModelTemplatesPage"));
const SqlModelingPage = lazy(() => import("@/pages/modeling/SqlModelingPage"));
const DbtFileBrowserPage = lazy(() => import("@/pages/modeling/DbtFileBrowserPage"));
const MetricWorkbenchPage = lazy(() => import("@/pages/modeling/MetricWorkbenchPage"));
const ModelingWorkbenchPage = lazy(() => import("@/pages/modeling/ModelingWorkbenchPage"));
const WarehousePlanDetailPage = lazy(() => import("@/pages/modeling/WarehousePlanDetailPage"));
const DimensionCatalogPage = lazy(() => import("@/pages/modeling/DimensionCatalogPage"));
const ModelCenterPage = lazy(() => import("@/pages/modeling/ModelCenterPage"));
const ModelSpecDetailPage = lazy(() => import("@/pages/modeling/ModelSpecDetailPage"));
const SemanticSubjectsPage = lazy(() => import("@/pages/modeling/SemanticSubjectsPage"));
const SemanticObjectsPage = lazy(() => import("@/pages/modeling/SemanticObjectsPage"));
const SemanticMetricsPage = lazy(() => import("@/pages/modeling/SemanticMetricsPage"));
const SemanticModelsPage = lazy(() => import("@/pages/modeling/SemanticModelsPage"));
const SemanticPublishPage = lazy(() => import("@/pages/modeling/SemanticPublishPage"));
const SemanticRunsPage = lazy(() => import("@/pages/modeling/SemanticRunsPage"));

// ── Analytics pages (static routes — registered statically for reliability; menu controls visibility) ──
const AnalyticsHomePage = lazy(() => import("@/analytics/pages/HomePage"));
const ScreensPage = lazy(() => import("@/analytics/pages/screens/ScreensPage"));
const DashboardsPage = lazy(() => import("@/analytics/pages/DashboardsPage"));
const DashboardDetailPage = lazy(() => import("@/analytics/pages/DashboardDetailPage"));
const DashboardEditorPage = lazy(() => import("@/analytics/pages/DashboardEditorPage"));
const CardsPage = lazy(() => import("@/analytics/pages/CardsPage"));
const CardDetailPage = lazy(() => import("@/analytics/pages/CardDetailPage"));
const CardEditorRoutePage = lazy(() => import("@/analytics/pages/CardEditorRoutePage"));
const DataPage = lazy(() => import("@/analytics/pages/DataPage"));
const DatabaseDetailPage = lazy(() => import("@/analytics/pages/DatabaseDetailPage"));
const TableDetailPage = lazy(() => import("@/analytics/pages/TableDetailPage"));
const FieldDetailPage = lazy(() => import("@/analytics/pages/FieldDetailPage"));
const ModelsPage = lazy(() => import("@/analytics/pages/ModelsPage"));
const MetricsPage = lazy(() => import("@/analytics/pages/MetricsPage"));
const TrashPage = lazy(() => import("@/analytics/pages/TrashPage"));
const CollectionsPage = lazy(() => import("@/analytics/pages/CollectionsPage"));
const CollectionItemsPage = lazy(() => import("@/analytics/pages/CollectionItemsPage"));
const SearchPage = lazy(() => import("@/analytics/pages/SearchPage"));
const ProjectCockpitPage = lazy(() => import("@/analytics/pages/project-cockpit/ProjectCockpitPage"));

const ProfilePage = lazy(() => import("@/pages/settings/profile/ProfilePage"));
const ExploreSessionsPage = lazy(() => import("@/analytics/pages/ExploreSessionsPage"));
const ReportFactoryPage = lazy(() => import("@/analytics/pages/ReportFactoryPage"));
const MetricLensPage = lazy(() => import("@/analytics/pages/MetricLensPage"));
const Nl2SqlEvalPage = lazy(() => import("@/analytics/pages/Nl2SqlEvalPage"));
const SemanticExplorePage = lazy(() => import("@/analytics/pages/semantic/SemanticExplorePage"));
const SemanticCardEditorPage = lazy(() => import("@/analytics/pages/semantic/SemanticCardEditorPage"));
const SemanticVirtualDatasetsPage = lazy(() => import("@/analytics/pages/semantic/SemanticVirtualDatasetsPage"));

const S = ({ children }: { children: React.ReactNode }) => (
	<Suspense fallback={<LineLoading />}>{children}</Suspense>
);

const MetricsServiceRedirect = () => {
	const location = useLocation();
	const target = metricsServiceHrefFromPlatformLocation(location.pathname, location.search, location.hash);

	return <Navigate to={target} replace />;
};

const WorkbenchSectionRedirect = ({ section }: { section: "data-management" | "consumption" }) => {
	const location = useLocation();
	const next = new URLSearchParams(location.search);
	next.set("section", section);
	return <Navigate to={`/workbench?${next.toString()}`} replace />;
};

export const STATIC_DASHBOARD_ROUTES: RouteObject[] = [
	// ── Platform ──
	{ path: "explore/etl", element: <S><EltConsolePage /></S> },
	{ path: "explore/etl/console", element: <S><EltConsolePage /></S> },
	{ path: "explore/etl/transform", element: <S><TransformPage /></S> },
	{ path: "explore/etl/transform/new", element: <S><TransformCreatePage /></S> },
	{ path: "explore/etl/transform/:id", element: <S><TransformDetailPage /></S> },
	{ path: "explore/etl/transform/:id/edit", element: <S><TransformCreatePage /></S> },
	{ path: "explore/etl/transform/:id/executions", element: <S><TransformExecutionHistoryPage /></S> },
	{ path: "foundation/data-sources/:id", element: <S><DataSourceDetailPage /></S> },
	{ path: "foundation/standard-package", element: <S><StandardPackagePage /></S> },
	{ path: "catalog/assets", element: <S><AssetOverviewPage /></S> },
	{ path: "catalog/assets/ledger", element: <S><DatasetsPage /></S> },
	{ path: "catalog/metadata-management", element: <S><MetadataManagementPage /></S> },
	{ path: "settings/profile", element: <S><ProfilePage /></S> },
	{ path: "governance/asset-ownership", element: <S><AssetOwnershipPage /></S> },
	{ path: "governance/asset-grants", element: <S><AssetGrantPage /></S> },
	{ path: "my/asset-grants", element: <S><MyGrantsPage /></S> },
	{ path: "governance/permission-audit", element: <S><PermissionAuditPage /></S> },
	{ path: "catalog/lineage", element: <S><LineagePage /></S> },
	{ path: "catalog/lineage/impact", element: <S><LineagePage section="impact" /></S> },
	{ path: "catalog/lineage/graph", element: <S><LineagePage section="graph" /></S> },
	{ path: "catalog/lineage/columns", element: <S><LineagePage section="columns" /></S> },
	{ path: "catalog/lineage/import", element: <S><LineagePage section="import" /></S> },
	{ path: "catalog/lineage/diff", element: <S><LineagePage section="diff" /></S> },
	{ path: "metrics", element: <MetricsServiceRedirect /> },
	{ path: "metrics/*", element: <MetricsServiceRedirect /> },
	{ path: "bi-apps/metrics", element: <MetricsServiceRedirect /> },
	{ path: "bi-apps/metrics/*", element: <MetricsServiceRedirect /> },
	{ path: "ops/events", element: <S><PlatformEventObservabilityPage /></S> },
	{ path: "platform/events", element: <S><PlatformEventObservabilityPage /></S> },
	{ path: "ops/audit-evidence", element: <S><AuditEvidencePage /></S> },
	{ path: "platform/audit-evidence", element: <S><AuditEvidencePage /></S> },
	{ path: "ops/release-governance", element: <S><ReleaseGovernancePage /></S> },
	{ path: "platform/release-governance", element: <S><ReleaseGovernancePage /></S> },
	{ path: "ops/overview", element: <S><OpsOverviewPage /></S> },
	{ path: "ops/instances", element: <S><OpsInstancesPage /></S> },
	{ path: "ops/alerts", element: <S><OpsAlertLogPage /></S> },
	{ path: "ops/backfill", element: <S><OpsBackfillPage /></S> },
	{ path: "workbench/todo", element: <S><WorkflowCenterPage /></S> },
	{ path: "workbench/data-management", element: <WorkbenchSectionRedirect section="data-management" /> },
	{ path: "services/consumption", element: <WorkbenchSectionRedirect section="consumption" /> },
	{ path: "studio/low-code-development", element: <S><LowCodeDevelopmentPage /></S> },
	{ path: "studio/projects", element: <S><StudioProjectsPage /></S> },
	{ path: "studio/sql-modeling", element: <S><SqlModelingPage /></S> },
	{ path: "modeling/dbt-files", element: <S><DbtFileBrowserPage /></S> },
	{ path: "modeling/workbench", element: <S><ModelingWorkbenchPage /></S> },
	{ path: "modeling/plans/:planId/*", element: <S><WarehousePlanDetailPage /></S> },
	{ path: "modeling/dimensions", element: <S><DimensionCatalogPage /></S> },
	{ path: "modeling/models", element: <S><ModelCenterPage /></S> },
	{ path: "modeling/models/:modelSpecId", element: <S><ModelSpecDetailPage /></S> },
	{ path: "modeling/metric-workbench", element: <S><MetricWorkbenchPage /></S> },
	{ path: "modeling/semantic/subjects", element: <S><SemanticSubjectsPage /></S> },
	{ path: "modeling/semantic/objects", element: <S><SemanticObjectsPage /></S> },
	{ path: "modeling/semantic/metrics", element: <S><SemanticMetricsPage /></S> },
	{ path: "modeling/semantic/models", element: <S><SemanticModelsPage /></S> },
	{ path: "modeling/semantic/publish", element: <S><SemanticPublishPage /></S> },
	{ path: "modeling/semantic/runs", element: <S><SemanticRunsPage /></S> },
	{ path: "modeling/semantic-center", element: <MetricsServiceRedirect /> },
	{ path: "modeling/semantic-center/*", element: <MetricsServiceRedirect /> },
	{ path: "bi/semantic-modeling", element: <MetricsServiceRedirect /> },

	// ── Analytics (all routes statically registered — no dependency on menu API) ──
	{ path: "bi", element: <S><AnalyticsHomePage /></S> },
	{ path: "bi/home", element: <S><AnalyticsHomePage /></S> },
	{ path: "bi/screens", element: <S><ScreensPage /></S> },
	{ path: "bi/dashboards", element: <S><DashboardsPage /></S> },
	{ path: "bi/dashboards/new", element: <S><DashboardEditorPage /></S> },
	{ path: "bi/dashboards/:id", element: <S><DashboardDetailPage /></S> },
	{ path: "bi/dashboards/:id/edit", element: <S><DashboardEditorPage /></S> },
	{ path: "bi/questions", element: <S><CardsPage /></S> },
	{ path: "bi/questions/new", element: <S><SemanticCardEditorPage /></S> },
	{ path: "bi/questions/:id", element: <S><CardDetailPage /></S> },
	{ path: "bi/questions/:id/edit", element: <S><CardEditorRoutePage /></S> },
	{ path: "bi/explore", element: <S><SemanticExplorePage /></S> },
	{ path: "bi/card/new", element: <S><SemanticCardEditorPage /></S> },
	{ path: "bi/card/:id/edit", element: <S><SemanticCardEditorPage /></S> },
	{ path: "bi/virtual-datasets", element: <S><SemanticVirtualDatasetsPage /></S> },
	{ path: "bi/virtual-datasets/new", element: <S><SemanticCardEditorPage /></S> },
	{ path: "bi/virtual-datasets/:id", element: <S><SemanticCardEditorPage /></S> },
	{ path: "bi/data", element: <S><DataPage /></S> },
	{ path: "bi/data/:dbId", element: <S><DatabaseDetailPage /></S> },
	{ path: "bi/data/:dbId/tables/:tableId", element: <S><TableDetailPage /></S> },
	{ path: "bi/data/:dbId/tables/:tableId/fields/:fieldId", element: <S><FieldDetailPage /></S> },
	{ path: "bi/models", element: <S><ModelsPage /></S> },
	{ path: "bi/metrics", element: <S><MetricsPage /></S> },
	{ path: "bi/trash", element: <S><TrashPage /></S> },
	{ path: "bi/collections", element: <S><CollectionsPage /></S> },
	{ path: "bi/collections/:id", element: <S><CollectionItemsPage /></S> },
	{ path: "bi/search", element: <S><SearchPage /></S> },
	{ path: "bi/project-cockpit", element: <S><ProjectCockpitPage /></S> },

	{ path: "bi/explore-sessions", element: <S><ExploreSessionsPage /></S> },
	{ path: "bi/report-factory", element: <S><ReportFactoryPage /></S> },
	{ path: "bi/metric-lens", element: <S><MetricLensPage /></S> },
	{ path: "bi/nl2sql-eval", element: <S><Nl2SqlEvalPage /></S> },
];
