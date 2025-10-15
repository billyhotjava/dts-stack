import type { RouteObject } from "react-router";
import { Navigate } from "react-router";
import { Component } from "./utils";
// Use version B admin views directly to replace legacy pages
import ApprovalCenterView from "@/admin/views/approval-center";
import AuditCenterView from "@/admin/views/audit-center";
import OrgManagementView from "@/admin/views/org-management";
import UserManagementView from "@/admin/views/user-management";
import RoleManagementView from "@/admin/views/role-management";
import PortalMenusView from "@/admin/views/portal-menus";

export function getFrontendDashboardRoutes(): RouteObject[] {
    const frontendDashboardRoutes: RouteObject[] = [
        { path: "workbench", element: <Navigate to="/admin/my-changes" replace /> },
        {
            path: "management",
            children: [
                { index: true, element: <Navigate to="user" replace /> },
                {
                    path: "user",
                    children: [
                        { index: true, element: <Navigate to="profile" replace /> },
                        { path: "profile", element: Component("/pages/management/user/profile") },
                    ],
                },
                {
                    path: "system",
                    children: [
                        { index: true, element: <Navigate to="user" replace /> },
                        // Migrate legacy A pages to use B views directly
                        { path: "permission", element: <PortalMenusView /> },
                        { path: "menu", element: <PortalMenusView /> },
                        { path: "role", element: <RoleManagementView /> },
                        { path: "group", element: <OrgManagementView /> },
                        { path: "user", element: <UserManagementView /> },
                        { path: "user/:id", element: <Navigate to="/admin/users/:id" replace /> },
                        { path: "approval", element: <ApprovalCenterView /> },
                        { path: "audit-log", element: <AuditCenterView /> },
                    ],
                },
            ],
        },
    ];
    return frontendDashboardRoutes;
}
