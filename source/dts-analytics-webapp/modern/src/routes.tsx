import { useEffect } from "react";
import { createBrowserRouter, useNavigate } from "react-router";
import { AppLayout } from "./layouts/AppLayout";

function ModernAliasRedirect() {
	const navigate = useNavigate();
	useEffect(() => {
		navigate("/", { replace: true });
	}, [navigate]);
	return null;
}

const lazyComponent = (importer: () => Promise<{ default: unknown }>) => async () => {
	const mod = await importer();
	return { Component: mod.default as never };
};

export function createRoutes() {
	return createBrowserRouter(
		[
			// Fullscreen routes — no sidebar/layout wrapper
			{ path: "/screens/new", lazy: lazyComponent(() => import("./pages/screens/ScreenDesignerPage")) },
			{ path: "/screens/:id/edit", lazy: lazyComponent(() => import("./pages/screens/ScreenDesignerPage")) },
			{ path: "/screens/:id/preview", lazy: lazyComponent(() => import("./pages/screens/ScreenPreviewPage")) },
			{ path: "/screens/:id/export", lazy: lazyComponent(() => import("./pages/screens/ScreenExportPage")) },
			{ path: "/public/screen/:uuid", lazy: lazyComponent(() => import("./pages/screens/PublicScreenPage")) },
			{
				Component: AppLayout,
				children: [
					{ path: "/", lazy: lazyComponent(() => import("./pages/HomePage")) },
					{ path: "/modern", Component: ModernAliasRedirect },
		{ path: "/collections", lazy: lazyComponent(() => import("./pages/CollectionsPage")) },
					{ path: "/collections/:id", lazy: lazyComponent(() => import("./pages/CollectionItemsPage")) },
					{ path: "/dashboards", lazy: lazyComponent(() => import("./pages/DashboardsPage")) },
					{ path: "/dashboards/new", lazy: lazyComponent(() => import("./pages/DashboardEditorPage")) },
					{ path: "/dashboards/:id", lazy: lazyComponent(() => import("./pages/DashboardDetailPage")) },
					{ path: "/dashboards/:id/edit", lazy: lazyComponent(() => import("./pages/DashboardEditorPage")) },
					{ path: "/questions", lazy: lazyComponent(() => import("./pages/CardsPage")) },
					{ path: "/questions/new", lazy: lazyComponent(() => import("./pages/CardEditorPage")) },
					{ path: "/questions/:id", lazy: lazyComponent(() => import("./pages/CardDetailPage")) },
					{ path: "/questions/:id/edit", lazy: lazyComponent(() => import("./pages/CardEditorPage")) },
					{ path: "/data", lazy: lazyComponent(() => import("./pages/DataPage")) },
					{ path: "/data/new", lazy: lazyComponent(() => import("./pages/DatabaseNewPage")) },
					{ path: "/data/:dbId/edit", lazy: lazyComponent(() => import("./pages/DatabaseEditPage")) },
					{ path: "/data/:dbId", lazy: lazyComponent(() => import("./pages/DatabaseDetailPage")) },
					{ path: "/data/:dbId/tables/:tableId", lazy: lazyComponent(() => import("./pages/TableDetailPage")) },
					{
						path: "/data/:dbId/tables/:tableId/fields/:fieldId",
						lazy: lazyComponent(() => import("./pages/FieldDetailPage")),
					},
					{ path: "/models", lazy: lazyComponent(() => import("./pages/ModelsPage")) },
					{ path: "/metrics", lazy: lazyComponent(() => import("./pages/MetricsPage")) },
					{ path: "/trash", lazy: lazyComponent(() => import("./pages/TrashPage")) },
					{ path: "/public/card/:uuid", lazy: lazyComponent(() => import("./pages/PublicCardPage")) },
					{ path: "/public/dashboard/:uuid", lazy: lazyComponent(() => import("./pages/PublicDashboardPage")) },
					{ path: "/screens", lazy: lazyComponent(() => import("./pages/screens/ScreensPage")) },
					{ path: "/project-cockpit", lazy: lazyComponent(() => import("./pages/project-cockpit/ProjectCockpitPage")) },
					{ path: "/gpmc/drill/:domain", lazy: lazyComponent(() => import("./pages/gpmc/GpmcDrillPage")) },
					{ path: "/gpmc/:screenId", lazy: lazyComponent(() => import("./pages/gpmc/GpmcPage")) },
					{ path: "/gpmc", lazy: lazyComponent(() => import("./pages/gpmc/GpmcPage")) },
					{ path: "/explore-sessions", lazy: lazyComponent(() => import("./pages/ExploreSessionsPage")) },
					{ path: "/report-factory", lazy: lazyComponent(() => import("./pages/ReportFactoryPage")) },
					{ path: "/metric-lens", lazy: lazyComponent(() => import("./pages/MetricLensPage")) },
					{ path: "/nl2sql-eval", lazy: lazyComponent(() => import("./pages/Nl2SqlEvalPage")) },
					{ path: "/search", lazy: lazyComponent(() => import("./pages/SearchPage")) },
					{ path: "*", lazy: lazyComponent(() => import("./pages/NotFoundPage")) },
				],
			},
		],
		{
			basename: "/analytics",
		},
	);
}
