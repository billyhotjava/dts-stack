import type { RouteObject } from "react-router";
import DatasetDetailPage from "@/pages/catalog/DatasetDetailPage";
import DataStandardDetailPage from "@/pages/modeling/DataStandardDetailPage";
import DatasetAccessApprovalPage from "@/pages/security/DatasetAccessApprovalPage";

export const STATIC_DASHBOARD_ROUTES: RouteObject[] = [
	{
		path: "catalog/datasets/:id",
		element: <DatasetDetailPage />,
	},
	{
		path: "security/dataset-access",
		element: <DatasetAccessApprovalPage />,
	},
	{
		path: "modeling/standards/:id",
		element: <DataStandardDetailPage />,
	},
];
