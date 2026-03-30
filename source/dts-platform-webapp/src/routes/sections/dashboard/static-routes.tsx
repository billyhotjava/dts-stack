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

// ── Analytics pages (static routes — independent of menu API) ──
const AnalyticsHomePage = lazy(() => import("@/analytics/pages/HomePage"));
const ScreensPage = lazy(() => import("@/analytics/pages/screens/ScreensPage"));
const DashboardsPage = lazy(() => import("@/analytics/pages/DashboardsPage"));
const DashboardDetailPage = lazy(() => import("@/analytics/pages/DashboardDetailPage"));
const DashboardEditorPage = lazy(() => import("@/analytics/pages/DashboardEditorPage"));
const CardsPage = lazy(() => import("@/analytics/pages/CardsPage"));
const CardDetailPage = lazy(() => import("@/analytics/pages/CardDetailPage"));
const CardEditorPage = lazy(() => import("@/analytics/pages/CardEditorPage"));
const DataPage = lazy(() => import("@/analytics/pages/DataPage"));
const DatabaseNewPage = lazy(() => import("@/analytics/pages/DatabaseNewPage"));
const DatabaseDetailPage = lazy(() => import("@/analytics/pages/DatabaseDetailPage"));
const DatabaseEditPage = lazy(() => import("@/analytics/pages/DatabaseEditPage"));
const TableDetailPage = lazy(() => import("@/analytics/pages/TableDetailPage"));
const FieldDetailPage = lazy(() => import("@/analytics/pages/FieldDetailPage"));
const ModelsPage = lazy(() => import("@/analytics/pages/ModelsPage"));
const MetricsPage = lazy(() => import("@/analytics/pages/MetricsPage"));
const TrashPage = lazy(() => import("@/analytics/pages/TrashPage"));
const CollectionsPage = lazy(() => import("@/analytics/pages/CollectionsPage"));
const CollectionItemsPage = lazy(() => import("@/analytics/pages/CollectionItemsPage"));
const SearchPage = lazy(() => import("@/analytics/pages/SearchPage"));
const ProjectCockpitPage = lazy(() => import("@/analytics/pages/project-cockpit/ProjectCockpitPage"));
const GpmcPage = lazy(() => import("@/analytics/pages/gpmc/GpmcPage"));
const GpmcDrillPage = lazy(() => import("@/analytics/pages/gpmc/GpmcDrillPage"));
const ExploreSessionsPage = lazy(() => import("@/analytics/pages/ExploreSessionsPage"));
const ReportFactoryPage = lazy(() => import("@/analytics/pages/ReportFactoryPage"));
const MetricLensPage = lazy(() => import("@/analytics/pages/MetricLensPage"));
const Nl2SqlEvalPage = lazy(() => import("@/analytics/pages/Nl2SqlEvalPage"));

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
	{ path: "governance/asset-ownership", element: <S><AssetOwnershipPage /></S> },
	{ path: "governance/asset-grants", element: <S><AssetGrantPage /></S> },
	{ path: "my/asset-grants", element: <S><MyGrantsPage /></S> },
	{ path: "governance/permission-audit", element: <S><PermissionAuditPage /></S> },

	// ── Analytics (all routes statically registered — no dependency on menu API) ──
	{ path: "analytics", element: <S><AnalyticsHomePage /></S> },
	{ path: "analytics/home", element: <S><AnalyticsHomePage /></S> },
	{ path: "analytics/screens", element: <S><ScreensPage /></S> },
	{ path: "analytics/dashboards", element: <S><DashboardsPage /></S> },
	{ path: "analytics/dashboards/new", element: <S><DashboardEditorPage /></S> },
	{ path: "analytics/dashboards/:id", element: <S><DashboardDetailPage /></S> },
	{ path: "analytics/dashboards/:id/edit", element: <S><DashboardEditorPage /></S> },
	{ path: "analytics/questions", element: <S><CardsPage /></S> },
	{ path: "analytics/questions/new", element: <S><CardEditorPage /></S> },
	{ path: "analytics/questions/:id", element: <S><CardDetailPage /></S> },
	{ path: "analytics/questions/:id/edit", element: <S><CardEditorPage /></S> },
	{ path: "analytics/data", element: <S><DataPage /></S> },
	{ path: "analytics/data/new", element: <S><DatabaseNewPage /></S> },
	{ path: "analytics/data/:dbId", element: <S><DatabaseDetailPage /></S> },
	{ path: "analytics/data/:dbId/edit", element: <S><DatabaseEditPage /></S> },
	{ path: "analytics/data/:dbId/tables/:tableId", element: <S><TableDetailPage /></S> },
	{ path: "analytics/data/:dbId/tables/:tableId/fields/:fieldId", element: <S><FieldDetailPage /></S> },
	{ path: "analytics/models", element: <S><ModelsPage /></S> },
	{ path: "analytics/metrics", element: <S><MetricsPage /></S> },
	{ path: "analytics/trash", element: <S><TrashPage /></S> },
	{ path: "analytics/collections", element: <S><CollectionsPage /></S> },
	{ path: "analytics/collections/:id", element: <S><CollectionItemsPage /></S> },
	{ path: "analytics/search", element: <S><SearchPage /></S> },
	{ path: "analytics/project-cockpit", element: <S><ProjectCockpitPage /></S> },
	{ path: "analytics/gpmc", element: <S><GpmcPage /></S> },
	{ path: "analytics/gpmc/:screenId", element: <S><GpmcPage /></S> },
	{ path: "analytics/gpmc/drill/:domain", element: <S><GpmcDrillPage /></S> },
	{ path: "analytics/explore-sessions", element: <S><ExploreSessionsPage /></S> },
	{ path: "analytics/report-factory", element: <S><ReportFactoryPage /></S> },
	{ path: "analytics/metric-lens", element: <S><MetricLensPage /></S> },
	{ path: "analytics/nl2sql-eval", element: <S><Nl2SqlEvalPage /></S> },
];
