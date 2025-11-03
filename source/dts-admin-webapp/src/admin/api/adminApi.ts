import type {
	AdminCustomRole,
	AdminDataset,
	AdminRoleDetail,
	AdminUser,
	AdminWhoami,
	ChangeRequest,
	CreateCustomRolePayload,
	OrganizationNode,
	OrganizationCreatePayload,
	OrganizationUpdatePayload,
	PagedResult,
	PermissionCatalogSection,
	PortalMenuCollection,
	PortalMenuItem,
	SystemConfigItem,
} from "@/admin/types";
import apiClient from "@/api/apiClient";

type AuditSilentOption = { auditSilent?: boolean };

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
	getChangeRequestDetail: (id: number, options?: AuditSilentOption) =>
		apiClient.get<ChangeRequest>({
			url: `/admin/change-requests/${id}`,
			headers: options?.auditSilent ? { "X-Audit-Silent": "true" } : undefined,
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

	getPortalMenus: (options?: AuditSilentOption) =>
		apiClient.get<PortalMenuCollection>({
			url: "/admin/portal/menus",
			headers: options?.auditSilent ? { "X-Audit-Silent": "true" } : undefined,
		}),

	createPortalMenu: (menu: PortalMenuItem) =>
		apiClient.post<PortalMenuCollection>({
			url: "/admin/portal/menus",
			data: menu,
		}),

	updatePortalMenu: (id: number, menu: Partial<PortalMenuItem>) =>
		apiClient.put<PortalMenuCollection>({
			url: `/admin/portal/menus/${id}`,
			data: menu,
		}),

	deletePortalMenu: (id: number) =>
		apiClient.delete<PortalMenuCollection>({
			url: `/admin/portal/menus/${id}`,
		}),

	// 角色删除前预检
	getRolePreDeleteCheck: (name: string) =>
		apiClient.get<Record<string, unknown>>({
			url: `/admin/roles/${encodeURIComponent(name)}/pre-delete-check`,
		}),

	resetPortalMenus: () =>
		apiClient.post<PortalMenuCollection>({
			url: "/admin/portal/menus/reset",
		}),

	getOrganizations: (options?: AuditSilentOption) =>
		apiClient.get<OrganizationNode[]>({
			url: "/admin/orgs",
			headers: options?.auditSilent ? { "X-Audit-Silent": "true" } : undefined,
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

	getAdminUsers: (options?: { page?: number; size?: number; keyword?: string }) =>
		apiClient.get<PagedResult<AdminUser>>({
			url: "/admin/users",
			params: {
				page: options?.page ?? 0,
				size: options?.size ?? 200,
				keyword: options?.keyword,
			},
		}),

	resolveUserDisplayNames: (usernames: string[]) =>
		apiClient.get<Record<string, string>>({
			url: "/admin/users/display-names",
			params: { usernames: usernames.join(",") },
		}),

	getAdminRoles: () =>
		apiClient.get<AdminRoleDetail[]>({
			url: "/admin/roles",
		}),

	getRoleMembers: (role: string) =>
		apiClient.get<Array<{ username: string; displayName: string }>>({
			url: `/admin/roles/${encodeURIComponent(role)}/members`,
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
		apiClient.post<ChangeRequest>({
			url: "/admin/custom-roles",
			data: payload,
		}),

};
