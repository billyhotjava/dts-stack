import type { RouteObject } from "react-router";
import DatasetDetailPage from "@/pages/catalog/DatasetDetailPage";
import DataStandardDetailPage from "@/pages/modeling/DataStandardDetailPage";

export const STATIC_DASHBOARD_ROUTES: RouteObject[] = [
	{
		path: "catalog/datasets/:id",
		element: <DatasetDetailPage />,
	},
	{
		path: "modeling/standards/:id",
		element: <DataStandardDetailPage />,
	},
];
