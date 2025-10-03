import type {
	AdminCustomRole,
	AdminDataset,
	AdminRoleAssignment,
	AdminRoleDetail,
	AdminUser,
	AdminWhoami,
	ChangeRequest,
	CreateCustomRolePayload,
	CreateRoleAssignmentPayload,
	OrganizationNode,
	OrganizationCreatePayload,
	OrganizationUpdatePayload,
	PermissionCatalogSection,
	PortalMenuCollection,
	PortalMenuItem,
	SystemConfigItem,
} from "@/admin/types";
import apiClient from "@/api/apiClient";

export interface ChangeRequestQuery {
	status?: string;
	type?: string;
}

export const adminApi = {
	getWhoami: () =>
		apiClient.get<AdminWhoami>({
			url: "/admin/whoami",
		}),

	getChangeRequests: (query?: ChangeRequestQuery) =>
		apiClient.get<ChangeRequest[]>({
			url: "/admin/change-requests",
			params: query,
		}),

	getMyChangeRequests: () =>
		apiClient.get<ChangeRequest[]>({
			url: "/admin/change-requests/mine",
		}),

	createChangeRequest: (payload: Partial<ChangeRequest>) =>
		apiClient.post<ChangeRequest>({
			url: "/admin/change-requests",
			data: payload,
		}),

	submitChangeRequest: (id: number) =>
		apiClient.post<ChangeRequest>({
			url: `/admin/change-requests/${id}/submit`,
		}),

	approveChangeRequest: (id: number, reason?: string) =>
		apiClient.post<ChangeRequest>({
			url: `/admin/change-requests/${id}/approve`,
			data: { reason },
		}),

	rejectChangeRequest: (id: number, reason?: string) =>
		apiClient.post<ChangeRequest>({
			url: `/admin/change-requests/${id}/reject`,
			data: { reason },
		}),

	getSystemConfig: () =>
		apiClient.get<SystemConfigItem[]>({
			url: "/admin/system/config",
		}),

	draftSystemConfig: (config: SystemConfigItem) =>
		apiClient.post<ChangeRequest>({
			url: "/admin/system/config",
			data: config,
		}),

	getPortalMenus: () =>
		apiClient.get<PortalMenuCollection>({
			url: "/admin/portal/menus",
		}),

	createPortalMenu: (menu: PortalMenuItem) =>
		apiClient.post<PortalMenuCollection>({
			url: "/admin/portal/menus",
			data: menu,
		}),

	updatePortalMenu: (id: number, menu: PortalMenuItem) =>
		apiClient.put<PortalMenuCollection>({
			url: `/admin/portal/menus/${id}`,
			data: menu,
		}),

	deletePortalMenu: (id: number) =>
		apiClient.delete<PortalMenuCollection>({
			url: `/admin/portal/menus/${id}`,
		}),

	resetPortalMenus: () =>
		apiClient.post<PortalMenuCollection>({
			url: "/admin/portal/menus/reset",
		}),

	getOrganizations: () =>
		apiClient.get<OrganizationNode[]>({
			url: "/admin/orgs",
		}),

	createOrganization: (payload: OrganizationCreatePayload) =>
		apiClient.post<OrganizationNode[]>({
			url: "/admin/orgs",
			data: payload,
		}),

	updateOrganization: (id: number, payload: OrganizationUpdatePayload) =>
		apiClient.put<OrganizationNode[]>({
			url: `/admin/orgs/${id}`,
			data: payload,
		}),

	deleteOrganization: (id: number) =>
		apiClient.delete<OrganizationNode[]>({
			url: `/admin/orgs/${id}`,
		}),

	syncOrganizations: () =>
		apiClient.post<OrganizationNode[]>({
			url: "/admin/orgs/sync",
		}),

	getAdminUsers: () =>
		apiClient.get<AdminUser[]>({
			url: "/admin/users",
		}),

	getAdminRoles: () =>
		apiClient.get<AdminRoleDetail[]>({
			url: "/admin/roles",
		}),

	getPermissionCatalog: () =>
		apiClient.get<PermissionCatalogSection[]>({
			url: "/admin/permissions/catalog",
		}),

	getDatasets: () =>
		apiClient.get<AdminDataset[]>({
			url: "/admin/datasets",
		}),

	getCustomRoles: () =>
		apiClient.get<AdminCustomRole[]>({
			url: "/admin/custom-roles",
		}),

	createCustomRole: (payload: CreateCustomRolePayload) =>
		apiClient.post<AdminCustomRole>({
			url: "/admin/custom-roles",
			data: payload,
		}),

	getRoleAssignments: () =>
		apiClient.get<AdminRoleAssignment[]>({
			url: "/admin/role-assignments",
		}),

	createRoleAssignment: (payload: CreateRoleAssignmentPayload) =>
		apiClient.post<AdminRoleAssignment>({
			url: "/admin/role-assignments",
			data: payload,
		}),
};
