import { createBrowserRouter } from "react-router";
import type { Locale } from "./i18n";
import { AppLayout } from "./layouts/AppLayout";
import CollectionsPage from "./pages/CollectionsPage";
import CollectionItemsPage from "./pages/CollectionItemsPage";
import DashboardDetailPage from "./pages/DashboardDetailPage";
import DashboardsPage from "./pages/DashboardsPage";
import HomePage from "./pages/HomePage";
import CardsPage from "./pages/CardsPage";
import CardDetailPage from "./pages/CardDetailPage";

export function createRoutes(locale: Locale) {
	return createBrowserRouter(
		[
			{
				Component: () => <AppLayout locale={locale} />,
				children: [
					{ path: "/", Component: HomePage },
					{ path: "/collections", Component: CollectionsPage },
					{ path: "/collections/:id", Component: CollectionItemsPage },
					{ path: "/dashboards", Component: DashboardsPage },
					{ path: "/dashboards/:id", Component: DashboardDetailPage },
					{ path: "/questions", Component: CardsPage },
					{ path: "/questions/:id", Component: CardDetailPage },
				],
			},
		],
		{
			basename: "/analytics/modern",
		},
	);
}

