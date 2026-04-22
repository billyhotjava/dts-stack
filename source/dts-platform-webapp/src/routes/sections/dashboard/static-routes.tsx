import { lazy, Suspense } from "react";
import type { RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";

// ── Platform pages ──
const TransformPage = lazy(() => import("@/pages/explore/etl/TransformPage"));
const TransformCreatePage = lazy(() => import("@/pages/explore/etl/TransformCreatePage"));
const TransformDetailPage = lazy(() => import("@/pages/explore/etl/TransformDetailPage"));
const TransformExecutionHistoryPage = lazy(() => import("@/pages/explore/etl/TransformExecutionHistoryPage"));
const AssetOwnershipPage = lazy(() => import("@/pages/governance/AssetOwnershipPage"));
const AssetGrantPage = lazy(() => import("@/pages/governance/AssetGrantPage"));
const MyGrantsPage = lazy(() => import("@/pages/governance/MyGrantsPage"));
const PermissionAuditPage = lazy(() => import("@/pages/governance/PermissionAuditPage"));

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

export const STATIC_DASHBOARD_ROUTES: RouteObject[] = [
	// ── Platform ──
	{ path: "explore/etl/transform", element: <S><TransformPage /></S> },
	{ path: "explore/etl/transform/new", element: <S><TransformCreatePage /></S> },
	{ path: "explore/etl/transform/:id", element: <S><TransformDetailPage /></S> },
	{ path: "explore/etl/transform/:id/edit", element: <S><TransformCreatePage /></S> },
	{ path: "explore/etl/transform/:id/executions", element: <S><TransformExecutionHistoryPage /></S> },
	{ path: "settings/profile", element: <S><ProfilePage /></S> },
	{ path: "governance/asset-ownership", element: <S><AssetOwnershipPage /></S> },
	{ path: "governance/asset-grants", element: <S><AssetGrantPage /></S> },
	{ path: "my/asset-grants", element: <S><MyGrantsPage /></S> },
	{ path: "governance/permission-audit", element: <S><PermissionAuditPage /></S> },

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
