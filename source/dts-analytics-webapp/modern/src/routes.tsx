import { useEffect } from "react";
import { createBrowserRouter, useNavigate } from "react-router";
import type { Locale } from "./i18n";
import { AppLayout } from "./layouts/AppLayout";
import CollectionsPage from "./pages/CollectionsPage";
import CollectionItemsPage from "./pages/CollectionItemsPage";
import DashboardDetailPage from "./pages/DashboardDetailPage";
import DashboardsPage from "./pages/DashboardsPage";
import HomePage from "./pages/HomePage";
import CardsPage from "./pages/CardsPage";
import CardDetailPage from "./pages/CardDetailPage";
import SearchPage from "./pages/SearchPage";

function ModernAliasRedirect() {
	const navigate = useNavigate();
	useEffect(() => {
		navigate("/", { replace: true });
	}, [navigate]);
	return null;
}

export function createRoutes(locale: Locale) {
	return createBrowserRouter(
		[
			{
				Component: () => <AppLayout locale={locale} />,
				children: [
					{ path: "/", Component: HomePage },
					{ path: "/modern", Component: ModernAliasRedirect },
					{ path: "/collections", Component: CollectionsPage },
					{ path: "/collections/:id", Component: CollectionItemsPage },
					{ path: "/dashboards", Component: DashboardsPage },
					{ path: "/dashboards/:id", Component: DashboardDetailPage },
					{ path: "/questions", Component: CardsPage },
					{ path: "/questions/:id", Component: CardDetailPage },
					{ path: "/search", Component: SearchPage },
				],
			},
		],
		{
			basename: "/analytics",
		},
	);
}
