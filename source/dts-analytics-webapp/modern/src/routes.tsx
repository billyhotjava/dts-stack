import { useEffect } from "react";
import { createBrowserRouter, useNavigate } from "react-router";
import { AppLayout } from "./layouts/AppLayout";
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
					{ path: "/data/:dbId", Component: DatabaseDetailPage },
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
