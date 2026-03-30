import { lazy, Suspense, useEffect } from "react";
import { Outlet, type RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";
import LoginAuthGuard from "@/routes/components/login-auth-guard";
import { startPlatformSessionHeartbeat } from "@/analytics/api/platformSession";

// Analytics layout (self-contained sidebar + header)
const AnalyticsLayout = lazy(() => import("@/analytics/layouts/AppLayout").then((m) => ({ default: m.AppLayout })));

// ── Full-screen pages (no sidebar) ──
const ScreenDesignerPage = lazy(() => import("@/analytics/pages/screens/ScreenDesignerPage"));
const ScreenPreviewPage = lazy(() => import("@/analytics/pages/screens/ScreenPreviewPage"));
const ScreenExportPage = lazy(() => import("@/analytics/pages/screens/ScreenExportPage"));
const PublicScreenPage = lazy(() => import("@/analytics/pages/screens/PublicScreenPage"));

// ── Layout pages ──
const HomePage = lazy(() => import("@/analytics/pages/HomePage"));
const CollectionsPage = lazy(() => import("@/analytics/pages/CollectionsPage"));
const CollectionItemsPage = lazy(() => import("@/analytics/pages/CollectionItemsPage"));
const DashboardsPage = lazy(() => import("@/analytics/pages/DashboardsPage"));
const DashboardDetailPage = lazy(() => import("@/analytics/pages/DashboardDetailPage"));
const DashboardEditorPage = lazy(() => import("@/analytics/pages/DashboardEditorPage"));
const CardsPage = lazy(() => import("@/analytics/pages/CardsPage"));
const CardDetailPage = lazy(() => import("@/analytics/pages/CardDetailPage"));
const CardEditorPage = lazy(() => import("@/analytics/pages/CardEditorPage"));
const DataPage = lazy(() => import("@/analytics/pages/DataPage"));
const DatabaseNewPage = lazy(() => import("@/analytics/pages/DatabaseNewPage"));
const DatabaseEditPage = lazy(() => import("@/analytics/pages/DatabaseEditPage"));
const DatabaseDetailPage = lazy(() => import("@/analytics/pages/DatabaseDetailPage"));
const TableDetailPage = lazy(() => import("@/analytics/pages/TableDetailPage"));
const FieldDetailPage = lazy(() => import("@/analytics/pages/FieldDetailPage"));
const ModelsPage = lazy(() => import("@/analytics/pages/ModelsPage"));
const MetricsPage = lazy(() => import("@/analytics/pages/MetricsPage"));
const TrashPage = lazy(() => import("@/analytics/pages/TrashPage"));
const PublicCardPage = lazy(() => import("@/analytics/pages/PublicCardPage"));
const PublicDashboardPage = lazy(() => import("@/analytics/pages/PublicDashboardPage"));
const ScreensPage = lazy(() => import("@/analytics/pages/screens/ScreensPage"));
const ProjectCockpitPage = lazy(() => import("@/analytics/pages/project-cockpit/ProjectCockpitPage"));
const GpmcDrillPage = lazy(() => import("@/analytics/pages/gpmc/GpmcDrillPage"));
const GpmcPage = lazy(() => import("@/analytics/pages/gpmc/GpmcPage"));
const ExploreSessionsPage = lazy(() => import("@/analytics/pages/ExploreSessionsPage"));
const ReportFactoryPage = lazy(() => import("@/analytics/pages/ReportFactoryPage"));
const MetricLensPage = lazy(() => import("@/analytics/pages/MetricLensPage"));
const Nl2SqlEvalPage = lazy(() => import("@/analytics/pages/Nl2SqlEvalPage"));
const SearchPage = lazy(() => import("@/analytics/pages/SearchPage"));
const NotFoundPage = lazy(() => import("@/analytics/pages/NotFoundPage"));

const S = ({ children }: { children: React.ReactNode }) => (
	<Suspense fallback={<LineLoading />}>{children}</Suspense>
);

/**
 * Thin shell that initializes analytics session heartbeat.
 */
function AnalyticsAppShell() {
	useEffect(() => {
		startPlatformSessionHeartbeat();
	}, []);
	return <Outlet />;
}

/**
 * Analytics routes — integrated into the platform router.
 *
 * URL structure preserved at /analytics/* for backward compatibility.
 */
export const analyticsRoutes: RouteObject[] = [
	// Public screen (no auth, no layout)
	{
		path: "analytics/public/screen/:uuid",
		element: <S><PublicScreenPage /></S>,
	},
	// Authenticated analytics routes
	{
		path: "analytics",
		element: <LoginAuthGuard><AnalyticsAppShell /></LoginAuthGuard>,
		children: [
			// Full-screen routes (screen designer — no sidebar)
			{ path: "screens/new", element: <S><ScreenDesignerPage /></S> },
			{ path: "screens/:id/edit", element: <S><ScreenDesignerPage /></S> },
			{ path: "screens/:id/preview", element: <S><ScreenPreviewPage /></S> },
			{ path: "screens/:id/export", element: <S><ScreenExportPage /></S> },
			// Layout routes (with analytics sidebar)
			{
				element: <S><AnalyticsLayout /></S>,
				children: [
					{ index: true, element: <S><HomePage /></S> },
					{ path: "collections", element: <S><CollectionsPage /></S> },
					{ path: "collections/:id", element: <S><CollectionItemsPage /></S> },
					{ path: "dashboards", element: <S><DashboardsPage /></S> },
					{ path: "dashboards/new", element: <S><DashboardEditorPage /></S> },
					{ path: "dashboards/:id", element: <S><DashboardDetailPage /></S> },
					{ path: "dashboards/:id/edit", element: <S><DashboardEditorPage /></S> },
					{ path: "questions", element: <S><CardsPage /></S> },
					{ path: "questions/new", element: <S><CardEditorPage /></S> },
					{ path: "questions/:id", element: <S><CardDetailPage /></S> },
					{ path: "questions/:id/edit", element: <S><CardEditorPage /></S> },
					{ path: "data", element: <S><DataPage /></S> },
					{ path: "data/new", element: <S><DatabaseNewPage /></S> },
					{ path: "data/:dbId/edit", element: <S><DatabaseEditPage /></S> },
					{ path: "data/:dbId", element: <S><DatabaseDetailPage /></S> },
					{ path: "data/:dbId/tables/:tableId", element: <S><TableDetailPage /></S> },
					{ path: "data/:dbId/tables/:tableId/fields/:fieldId", element: <S><FieldDetailPage /></S> },
					{ path: "models", element: <S><ModelsPage /></S> },
					{ path: "metrics", element: <S><MetricsPage /></S> },
					{ path: "trash", element: <S><TrashPage /></S> },
					{ path: "public/card/:uuid", element: <S><PublicCardPage /></S> },
					{ path: "public/dashboard/:uuid", element: <S><PublicDashboardPage /></S> },
					{ path: "screens", element: <S><ScreensPage /></S> },
					{ path: "project-cockpit", element: <S><ProjectCockpitPage /></S> },
					{ path: "gpmc/drill/:domain", element: <S><GpmcDrillPage /></S> },
					{ path: "gpmc/:screenId", element: <S><GpmcPage /></S> },
					{ path: "gpmc", element: <S><GpmcPage /></S> },
					{ path: "explore-sessions", element: <S><ExploreSessionsPage /></S> },
					{ path: "report-factory", element: <S><ReportFactoryPage /></S> },
					{ path: "metric-lens", element: <S><MetricLensPage /></S> },
					{ path: "nl2sql-eval", element: <S><Nl2SqlEvalPage /></S> },
					{ path: "search", element: <S><SearchPage /></S> },
					{ path: "*", element: <S><NotFoundPage /></S> },
				],
			},
		],
	},
];
