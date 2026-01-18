import type { RouteObject } from "react-router";
import DatasetDetailPage from "@/pages/catalog/DatasetDetailPage";
import DataStandardDetailPage from "@/pages/modeling/DataStandardDetailPage";
import DatasetAccessApprovalPage from "@/pages/security/DatasetAccessApprovalPage";
import WorkflowCenterPage from "@/pages/workbench/WorkflowCenterPage";
import MetadataStandardsPage from "@/pages/modeling/MetadataStandardsPage";
import QualityPage from "@/pages/catalog/QualityPage";

export const STATIC_DASHBOARD_ROUTES: RouteObject[] = [
	{
		path: "catalog/datasets/:id",
		element: <DatasetDetailPage />,
	},
	{
		path: "governance/quality",
		element: <QualityPage />,
	},
	{
		path: "security/dataset-access",
		element: <DatasetAccessApprovalPage />,
	},
	{
		path: "workbench/workflow-center",
		element: <WorkflowCenterPage />,
	},
	{
		path: "modeling/metadata-standards",
		element: <MetadataStandardsPage />,
	},
	{
		path: "modeling/standards/:id",
		element: <DataStandardDetailPage />,
	},
];
