import { Navigate, type RouteObject } from "react-router";
import DashboardLayout from "@/layouts/dashboard";
import AdminGuard from "@/admin/lib/guard";
import ApprovalCenterView from "@/admin/views/approval-center";
import AuditCenterView from "@/admin/views/audit-center";
import OpsConfigView from "@/admin/views/ops-config";
import PortalMenusView from "@/admin/views/portal-menus";
import OrgManagementView from "@/admin/views/org-management";
import UserManagementView from "@/admin/views/user-management";
import UserDetailView from "@/admin/views/user-detail";
import MyChangesView from "@/admin/views/my-changes";
import RoleManagementView from "@/admin/views/role-management";
import RoleDetailView from "@/admin/views/role-detail";
import WorkflowConfigView from "@/admin/views/workflow-config";
import DataLakeConfigView from "@/admin/views/data-lake-config";
import { getMenusByRole } from "@/admin/config/menus";
import { useAdminSession } from "@/admin/lib/session-context";

function AdminIndexRedirect() {
	const session = useAdminSession();
	const menus = getMenusByRole(session.role);
	if (!menus.length) {
		return <Navigate to="/403" replace />;
	}
	return <Navigate to={menus[0].path} replace />;
}

export const adminRoutes: RouteObject[] = [
	{
		path: "admin",
		element: (
			<AdminGuard>
		<DashboardLayout />
	</AdminGuard>
	),
		children: [
			{ index: true, element: <AdminIndexRedirect /> },
			{ path: "system", element: <Navigate to="/admin/ops" replace /> },
			{ path: "system/*", element: <Navigate to="/admin/ops" replace /> },
			{ path: "my-changes", element: <MyChangesView /> },
			{ path: "users", element: <UserManagementView /> },
			{ path: "users/:id", element: <UserDetailView /> },
			{ path: "roles", element: <RoleManagementView /> },
			{ path: "roles/:roleId", element: <RoleDetailView /> },
			{ path: "roles/:roleId/edit", element: <RoleDetailView /> },
			{ path: "portal-menus", element: <PortalMenusView /> },
			{ path: "orgs", element: <OrgManagementView /> },
			{ path: "data-lake", element: <DataLakeConfigView /> },
			{ path: "approval", element: <ApprovalCenterView /> },
			{ path: "audit", element: <AuditCenterView /> },
			{ path: "ops", element: <OpsConfigView /> },
			{ path: "workflows", element: <WorkflowConfigView /> },
			{ path: "*", element: <Navigate to="/403" replace /> },
		],
	},
];
