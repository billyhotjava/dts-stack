import { lazy, Suspense } from "react";
import type { RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";

const DataSourcesPage = lazy(() => import("@/pages/foundation/DataSourcesPage"));
const DataSourceDetailPage = lazy(() => import("@/pages/foundation/DataSourceDetailPage"));

export const STATIC_DASHBOARD_ROUTES: RouteObject[] = [
	{
		path: "foundation/data-sources",
		element: (
			<Suspense fallback={<LineLoading />}>
				<DataSourcesPage />
			</Suspense>
		),
	},
	{
		path: "foundation/data-sources/:id",
		element: (
			<Suspense fallback={<LineLoading />}>
				<DataSourceDetailPage />
			</Suspense>
		),
	},
];
