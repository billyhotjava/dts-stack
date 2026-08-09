import { lazy, Suspense } from "react";
import { Navigate, type RouteObject, useLocation } from "react-router";
import { LineLoading } from "@/components/loading";
import { StandardPackageActions } from "@/pages/governance/StandardPackageActions";
import { metricsServiceHrefFromPlatformLocation } from "./metricsServiceRoutes";

// ── Platform pages ──
const EltConsolePage = lazy(() => import("@/pages/explore/etl/EltConsolePage"));
const AccessWorkspacePage = lazy(() => import("@/pages/foundation/access/AccessWorkspacePage"));
const AccessPlanWizardPage = lazy(() => import("@/pages/foundation/access/AccessPlanWizardPage"));
const AccessPlanDetailPage = lazy(() => import("@/pages/foundation/access/AccessPlanDetailPage"));
const AccessDefaultsPage = lazy(() => import("@/pages/foundation/access/AccessDefaultsPage"));
const LegacyDataIntegrationRedirect = lazy(() => import("@/pages/foundation/access/LegacyDataIntegrationRedirect"));
const LegacyConnectionProfileRedirect = lazy(() => import("@/pages/foundation/access/LegacyConnectionProfileRedirect"));
const ConnectionProfilesPage = lazy(() => import("@/pages/foundation/access/ConnectionProfilesPage"));
const ConnectionProfileDetailPage = lazy(() => import("@/pages/foundation/access/ConnectionProfileDetailPage"));
const StandardPackagePage = lazy(() => import("@/pages/foundation/StandardPackagePage"));
const AssetOverviewPage = lazy(() => import("@/pages/catalog/AssetOverviewPage"));
const LegacyAssetLedgerRedirect = lazy(() => import("@/pages/catalog/LegacyAssetLedgerRedirect"));
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
const DataModelingPage = lazy(() => import("@/pages/data-modeling/DataModelingPage"));
const LegacyDataModelingRedirect = lazy(() => import("@/pages/data-modeling/LegacyDataModelingRedirect"));
const MeasurementUnitsPage = lazy(() => import("@/pages/governance/MeasurementUnitsPage"));
const QualityRoutePage = lazy(() => import("@/features/data-quality/QualityRoutePage"));

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
const HelpCenterPage = lazy(() => import("@/features/help-center/HelpCenterPage"));
const ExploreSessionsPage = lazy(() => import("@/analytics/pages/ExploreSessionsPage"));
const ReportFactoryPage = lazy(() => import("@/analytics/pages/ReportFactoryPage"));
const MetricLensPage = lazy(() => import("@/analytics/pages/MetricLensPage"));
const Nl2SqlEvalPage = lazy(() => import("@/analytics/pages/Nl2SqlEvalPage"));
const SemanticExplorePage = lazy(() => import("@/analytics/pages/semantic/SemanticExplorePage"));
const SemanticCardEditorPage = lazy(() => import("@/analytics/pages/semantic/SemanticCardEditorPage"));
const SemanticVirtualDatasetsPage = lazy(() => import("@/analytics/pages/semantic/SemanticVirtualDatasetsPage"));

const S = ({ children }: { children: React.ReactNode }) => <Suspense fallback={<LineLoading />}>{children}</Suspense>;

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
	{
		path: "explore/etl",
		element: (
			<S>
				<EltConsolePage />
			</S>
		),
	},
	{
		path: "explore/etl/console",
		element: (
			<S>
				<EltConsolePage />
			</S>
		),
	},
	{
		path: "explore/etl/transform",
		element: (
			<S>
				<LegacyDataIntegrationRedirect />
			</S>
		),
	},
	{
		path: "explore/etl/transform/new",
		element: (
			<S>
				<LegacyDataIntegrationRedirect />
			</S>
		),
	},
	{
		path: "explore/etl/transform/:id",
		element: (
			<S>
				<LegacyDataIntegrationRedirect />
			</S>
		),
	},
	{
		path: "explore/etl/transform/:id/edit",
		element: (
			<S>
				<LegacyDataIntegrationRedirect />
			</S>
		),
	},
	{
		path: "explore/etl/transform/:id/executions",
		element: (
			<S>
				<LegacyDataIntegrationRedirect />
			</S>
		),
	},
	{
		path: "foundation/access-changes",
		element: (
			<S>
				<LegacyDataIntegrationRedirect />
			</S>
		),
	},
	{
		path: "foundation/connections",
		element: (
			<S>
				<ConnectionProfilesPage />
			</S>
		),
	},
	{
		path: "foundation/connections/:id",
		element: (
			<S>
				<ConnectionProfileDetailPage />
			</S>
		),
	},
	{
		path: "foundation/data-sources",
		element: (
			<S>
				<AccessWorkspacePage />
			</S>
		),
	},
	{
		path: "foundation/data-sources/database",
		element: (
			<S>
				<AccessWorkspacePage />
			</S>
		),
	},
	{
		path: "foundation/data-sources/api",
		element: (
			<S>
				<AccessWorkspacePage />
			</S>
		),
	},
	{
		path: "foundation/data-sources/files",
		element: (
			<S>
				<AccessWorkspacePage />
			</S>
		),
	},
	{
		path: "foundation/data-sources/defaults",
		element: (
			<S>
				<AccessDefaultsPage />
			</S>
		),
	},
	{
		path: "foundation/data-sources/access/new",
		element: (
			<S>
				<AccessPlanWizardPage />
			</S>
		),
	},
	{
		path: "foundation/data-sources/access/:taskId",
		element: (
			<S>
				<AccessPlanDetailPage />
			</S>
		),
	},
	{
		path: "foundation/data-sources/:id",
		element: (
			<S>
				<LegacyConnectionProfileRedirect />
			</S>
		),
	},
	{
		path: "foundation/standard-package",
		element: (
			<S>
				<StandardPackagePage />
			</S>
		),
	},
	{
		path: "catalog/assets",
		element: (
			<S>
				<AssetOverviewPage />
			</S>
		),
	},
	{
		path: "catalog/assets/ledger",
		element: (
			<S>
				<LegacyAssetLedgerRedirect />
			</S>
		),
	},
	{
		path: "catalog/metadata-management",
		element: (
			<S>
				<MetadataManagementPage />
			</S>
		),
	},
	{
		path: "settings/profile",
		element: (
			<S>
				<ProfilePage />
			</S>
		),
	},
	{
		path: "settings/help",
		element: (
			<S>
				<HelpCenterPage />
			</S>
		),
	},
	{
		path: "governance/asset-ownership",
		element: (
			<S>
				<AssetOwnershipPage />
			</S>
		),
	},
	{
		path: "governance/asset-grants",
		element: (
			<S>
				<AssetGrantPage />
			</S>
		),
	},
	{
		path: "my/asset-grants",
		element: (
			<S>
				<MyGrantsPage />
			</S>
		),
	},
	{
		path: "governance/permission-audit",
		element: (
			<S>
				<PermissionAuditPage />
			</S>
		),
	},
	{
		path: "catalog/lineage",
		element: (
			<S>
				<LineagePage />
			</S>
		),
	},
	{
		path: "catalog/lineage/impact",
		element: (
			<S>
				<LineagePage section="impact" />
			</S>
		),
	},
	{
		path: "catalog/lineage/graph",
		element: (
			<S>
				<LineagePage section="graph" />
			</S>
		),
	},
	{
		path: "catalog/lineage/columns",
		element: (
			<S>
				<LineagePage section="columns" />
			</S>
		),
	},
	{
		path: "catalog/lineage/import",
		element: (
			<S>
				<LineagePage section="import" />
			</S>
		),
	},
	{
		path: "catalog/lineage/diff",
		element: (
			<S>
				<LineagePage section="diff" />
			</S>
		),
	},
	{ path: "metrics", element: <MetricsServiceRedirect /> },
	{ path: "metrics/*", element: <MetricsServiceRedirect /> },
	{ path: "bi-apps/metrics", element: <MetricsServiceRedirect /> },
	{ path: "bi-apps/metrics/*", element: <MetricsServiceRedirect /> },
	{
		path: "ops/events",
		element: (
			<S>
				<PlatformEventObservabilityPage />
			</S>
		),
	},
	{
		path: "platform/events",
		element: (
			<S>
				<PlatformEventObservabilityPage />
			</S>
		),
	},
	{
		path: "ops/audit-evidence",
		element: (
			<S>
				<AuditEvidencePage />
			</S>
		),
	},
	{
		path: "platform/audit-evidence",
		element: (
			<S>
				<AuditEvidencePage />
			</S>
		),
	},
	{
		path: "ops/release-governance",
		element: (
			<S>
				<ReleaseGovernancePage />
			</S>
		),
	},
	{
		path: "platform/release-governance",
		element: (
			<S>
				<ReleaseGovernancePage />
			</S>
		),
	},
	{
		path: "ops/overview",
		element: (
			<S>
				<OpsOverviewPage />
			</S>
		),
	},
	{
		path: "ops/instances",
		element: (
			<S>
				<OpsInstancesPage />
			</S>
		),
	},
	{
		path: "ops/alerts",
		element: (
			<S>
				<OpsAlertLogPage />
			</S>
		),
	},
	{
		path: "ops/backfill",
		element: (
			<S>
				<OpsBackfillPage />
			</S>
		),
	},
	{
		path: "workbench/todo",
		element: (
			<S>
				<WorkflowCenterPage />
			</S>
		),
	},
	{ path: "workbench/data-management", element: <WorkbenchSectionRedirect section="data-management" /> },
	{ path: "services/consumption", element: <WorkbenchSectionRedirect section="consumption" /> },
	{
		path: "studio/low-code-development",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "studio/projects",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "studio/sql-modeling",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "data-modeling/*",
		element: (
			<S>
				<DataModelingPage />
			</S>
		),
	},
	{
		path: "modeling/dbt-files",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/workbench",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/plans",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/plans/:planId/*",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/dimensions",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/models",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/models/:modelSpecId",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/metric-workbench",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "governance/standards/units",
		element: (
			<S>
				<StandardPackageActions source="units" />
				<MeasurementUnitsPage />
			</S>
		),
	},
	{
		path: "modeling/semantic/subjects",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/semantic/objects",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/semantic/metrics",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/semantic/models",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/semantic/publish",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/semantic/runs",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/semantic-center",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/semantic-center/*",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "bi/semantic-modeling",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "modeling/*",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},
	{
		path: "studio/modeling/*",
		element: (
			<S>
				<LegacyDataModelingRedirect />
			</S>
		),
	},

	// ── Analytics (all routes statically registered — no dependency on menu API) ──
	{
		path: "bi",
		element: (
			<S>
				<AnalyticsHomePage />
			</S>
		),
	},
	{
		path: "bi/home",
		element: (
			<S>
				<AnalyticsHomePage />
			</S>
		),
	},
	{
		path: "bi/screens",
		element: (
			<S>
				<ScreensPage />
			</S>
		),
	},
	{
		path: "bi/dashboards",
		element: (
			<S>
				<DashboardsPage />
			</S>
		),
	},
	{
		path: "bi/dashboards/new",
		element: (
			<S>
				<DashboardEditorPage />
			</S>
		),
	},
	{
		path: "bi/dashboards/:id",
		element: (
			<S>
				<DashboardDetailPage />
			</S>
		),
	},
	{
		path: "bi/dashboards/:id/edit",
		element: (
			<S>
				<DashboardEditorPage />
			</S>
		),
	},
	{
		path: "bi/questions",
		element: (
			<S>
				<CardsPage />
			</S>
		),
	},
	{
		path: "bi/questions/new",
		element: (
			<S>
				<SemanticCardEditorPage />
			</S>
		),
	},
	{
		path: "bi/questions/:id",
		element: (
			<S>
				<CardDetailPage />
			</S>
		),
	},
	{
		path: "bi/questions/:id/edit",
		element: (
			<S>
				<CardEditorRoutePage />
			</S>
		),
	},
	{
		path: "bi/explore",
		element: (
			<S>
				<SemanticExplorePage />
			</S>
		),
	},
	{
		path: "bi/card/new",
		element: (
			<S>
				<SemanticCardEditorPage />
			</S>
		),
	},
	{
		path: "bi/card/:id/edit",
		element: (
			<S>
				<SemanticCardEditorPage />
			</S>
		),
	},
	{
		path: "bi/virtual-datasets",
		element: (
			<S>
				<SemanticVirtualDatasetsPage />
			</S>
		),
	},
	{
		path: "bi/virtual-datasets/new",
		element: (
			<S>
				<SemanticCardEditorPage />
			</S>
		),
	},
	{
		path: "bi/virtual-datasets/:id",
		element: (
			<S>
				<SemanticCardEditorPage />
			</S>
		),
	},
	{
		path: "bi/data",
		element: (
			<S>
				<DataPage />
			</S>
		),
	},
	{
		path: "bi/data/:dbId",
		element: (
			<S>
				<DatabaseDetailPage />
			</S>
		),
	},
	{
		path: "bi/data/:dbId/tables/:tableId",
		element: (
			<S>
				<TableDetailPage />
			</S>
		),
	},
	{
		path: "bi/data/:dbId/tables/:tableId/fields/:fieldId",
		element: (
			<S>
				<FieldDetailPage />
			</S>
		),
	},
	{
		path: "bi/models",
		element: (
			<S>
				<ModelsPage />
			</S>
		),
	},
	{
		path: "bi/metrics",
		element: (
			<S>
				<MetricsPage />
			</S>
		),
	},
	{
		path: "bi/trash",
		element: (
			<S>
				<TrashPage />
			</S>
		),
	},
	{
		path: "bi/collections",
		element: (
			<S>
				<CollectionsPage />
			</S>
		),
	},
	{
		path: "bi/collections/:id",
		element: (
			<S>
				<CollectionItemsPage />
			</S>
		),
	},
	{
		path: "bi/search",
		element: (
			<S>
				<SearchPage />
			</S>
		),
	},
	{
		path: "bi/project-cockpit",
		element: (
			<S>
				<ProjectCockpitPage />
			</S>
		),
	},

	{
		path: "bi/explore-sessions",
		element: (
			<S>
				<ExploreSessionsPage />
			</S>
		),
	},
	{
		path: "bi/report-factory",
		element: (
			<S>
				<ReportFactoryPage />
			</S>
		),
	},
	{
		path: "bi/metric-lens",
		element: (
			<S>
				<MetricLensPage />
			</S>
		),
	},
	{
		path: "bi/nl2sql-eval",
		element: (
			<S>
				<Nl2SqlEvalPage />
			</S>
		),
	},
	// Data quality roots remain menu-driven dynamic routes. These deep routes share one workspace.
	{
		path: "governance/rules/catalog",
		element: (
			<S>
				<QualityRoutePage routeKey="rule-list" />
			</S>
		),
	},
	{
		path: "governance/rules/catalog/new",
		element: (
			<S>
				<QualityRoutePage routeKey="rule-editor" />
			</S>
		),
	},
	{
		path: "governance/rules/catalog/:ruleId/edit",
		element: (
			<S>
				<QualityRoutePage routeKey="rule-editor" />
			</S>
		),
	},
	{
		path: "governance/rules/catalog/:ruleId",
		element: (
			<S>
				<QualityRoutePage routeKey="rule-detail" />
			</S>
		),
	},
	{
		path: "governance/rules/templates",
		element: (
			<S>
				<QualityRoutePage routeKey="rule-template" />
			</S>
		),
	},
	{
		path: "governance/rules/templates/:templateId",
		element: (
			<S>
				<QualityRoutePage routeKey="template-detail" />
			</S>
		),
	},
	{
		path: "governance/rules/config/tables",
		element: (
			<S>
				<QualityRoutePage routeKey="rule-by-table" />
			</S>
		),
	},
	{
		path: "governance/rules/config/tables/:datasetId",
		element: (
			<S>
				<QualityRoutePage routeKey="table-detail" />
			</S>
		),
	},
	{
		path: "governance/rules/config/templates",
		element: (
			<S>
				<QualityRoutePage routeKey="rule-by-template" />
			</S>
		),
	},
	{
		path: "governance/rules/config/batch",
		element: (
			<S>
				<QualityRoutePage routeKey="batch-wizard" />
			</S>
		),
	},
	{
		path: "governance/rules/monitors",
		element: (
			<S>
				<QualityRoutePage routeKey="monitor" />
			</S>
		),
	},
	{
		path: "governance/rules/monitors/new",
		element: (
			<S>
				<QualityRoutePage routeKey="monitor-editor" />
			</S>
		),
	},
	{
		path: "governance/rules/monitors/:taskId/edit",
		element: (
			<S>
				<QualityRoutePage routeKey="monitor-editor" />
			</S>
		),
	},
	{
		path: "governance/rules/monitors/:taskId",
		element: (
			<S>
				<QualityRoutePage routeKey="monitor-detail" />
			</S>
		),
	},
	{
		path: "governance/rules/runs",
		element: (
			<S>
				<QualityRoutePage routeKey="run-records" />
			</S>
		),
	},
	{
		path: "governance/rules/runs/:runId",
		element: (
			<S>
				<QualityRoutePage routeKey="run-detail" />
			</S>
		),
	},
	{
		path: "governance/rules/noise",
		element: (
			<S>
				<QualityRoutePage routeKey="noise" />
			</S>
		),
	},
	{
		path: "governance/quality/reports/new",
		element: (
			<S>
				<QualityRoutePage routeKey="report-editor" />
			</S>
		),
	},
	{
		path: "governance/quality/reports/:reportId/edit",
		element: (
			<S>
				<QualityRoutePage routeKey="report-editor" />
			</S>
		),
	},
	{
		path: "governance/quality/preview",
		element: (
			<S>
				<QualityRoutePage routeKey="report-preview" />
			</S>
		),
	},
];
