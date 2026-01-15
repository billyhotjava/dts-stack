import { useEffect } from "react";
import { createBrowserRouter, useNavigate } from "react-router";
import { AppLayout } from "./layouts/AppLayout";
import AnalyzePage from "./pages/AnalyzePage";
import CollectionsPage from "./pages/CollectionsPage";
import CollectionItemsPage from "./pages/CollectionItemsPage";
import DashboardDetailPage from "./pages/DashboardDetailPage";
import DashboardsPage from "./pages/DashboardsPage";
import DashboardEditorPage from "./pages/DashboardEditorPage";
import HomePage from "./pages/HomePage";
import CardsPage from "./pages/CardsPage";
import CardDetailPage from "./pages/CardDetailPage";
import CardEditorPage from "./pages/CardEditorPage";
import SearchPage from "./pages/SearchPage";
import NotFoundPage from "./pages/NotFoundPage";
import DataPage from "./pages/DataPage";
import DatabaseDetailPage from "./pages/DatabaseDetailPage";
import TableDetailPage from "./pages/TableDetailPage";
import FieldDetailPage from "./pages/FieldDetailPage";
import ModelsPage from "./pages/ModelsPage";
import MetricsPage from "./pages/MetricsPage";
import TrashPage from "./pages/TrashPage";
import DatabaseNewPage from "./pages/DatabaseNewPage";
import PublicCardPage from "./pages/PublicCardPage";
import PublicDashboardPage from "./pages/PublicDashboardPage";

function ModernAliasRedirect() {
	const navigate = useNavigate();
	useEffect(() => {
		navigate("/", { replace: true });
	}, [navigate]);
	return null;
}

export function createRoutes() {
	return createBrowserRouter(
		[
			{
				Component: AppLayout,
				children: [
					{ path: "/", Component: HomePage },
					{ path: "/modern", Component: ModernAliasRedirect },
					{ path: "/analyze", Component: AnalyzePage },
					{ path: "/collections", Component: CollectionsPage },
					{ path: "/collections/:id", Component: CollectionItemsPage },
					{ path: "/dashboards", Component: DashboardsPage },
					{ path: "/dashboards/new", Component: DashboardEditorPage },
					{ path: "/dashboards/:id", Component: DashboardDetailPage },
					{ path: "/dashboards/:id/edit", Component: DashboardEditorPage },
					{ path: "/questions", Component: CardsPage },
					{ path: "/questions/new", Component: CardEditorPage },
						{ path: "/questions/:id", Component: CardDetailPage },
						{ path: "/questions/:id/edit", Component: CardEditorPage },
						{ path: "/data", Component: DataPage },
						{ path: "/data/new", Component: DatabaseNewPage },
						{ path: "/data/:dbId", Component: DatabaseDetailPage },
						{ path: "/data/:dbId/tables/:tableId", Component: TableDetailPage },
						{ path: "/data/:dbId/tables/:tableId/fields/:fieldId", Component: FieldDetailPage },
						{ path: "/models", Component: ModelsPage },
						{ path: "/metrics", Component: MetricsPage },
						{ path: "/trash", Component: TrashPage },
						{ path: "/public/card/:uuid", Component: PublicCardPage },
						{ path: "/public/dashboard/:uuid", Component: PublicDashboardPage },
						{ path: "/search", Component: SearchPage },
						{ path: "*", Component: NotFoundPage },
					],
				},
		],
		{
			basename: "/analytics",
		},
	);
}
