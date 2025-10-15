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
import AdminDataSourcesView from "@/admin/views/system/data-sources";
import AdminOtherConfigView from "@/admin/views/system/other-config";
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
			{ path: "my-changes", element: <MyChangesView /> },
			{ path: "users", element: <UserManagementView /> },
			{ path: "users/:id", element: <UserDetailView /> },
			{ path: "roles", element: <RoleManagementView /> },
			{ path: "portal-menus", element: <PortalMenusView /> },
			{ path: "orgs", element: <OrgManagementView /> },
			{ path: "system", element: <Navigate to="/admin/system/data-sources" replace /> },
			{ path: "system/data-sources", element: <AdminDataSourcesView /> },
			{ path: "system/other", element: <AdminOtherConfigView /> },
			{ path: "approval", element: <ApprovalCenterView /> },
			{ path: "audit", element: <AuditCenterView /> },
			{ path: "ops", element: <OpsConfigView /> },
			{ path: "*", element: <Navigate to="/403" replace /> },
		],
	},
];
