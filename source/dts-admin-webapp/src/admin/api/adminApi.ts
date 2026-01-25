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
	UpsertWorkflowTemplatePayload,
	WorkflowTemplateConfig,
} from "@/admin/types";
import type {
	ConnectionTestLog,
	HiveConnectionPersistRequest,
	HiveConnectionTestRequest,
	HiveConnectionTestResult,
	InceptorConfig,
	InfraServiceSettingsPayload,
	InfraServiceTestResult,
	InfraDataSource,
	JdbcConnectionTestRequest,
	JdbcDriverInfo,
	UpsertInfraDataSourcePayload,
} from "@/types/infra";
import apiClient from "@/api/apiClient";

type AuditSilentOption = { auditSilent?: boolean };
const ADMIN_USER_PAGE_SIZE = 200;

type AdminUsersPage = PagedResult<AdminUser> | AdminUser[] | null | undefined;

const normalizeAdminUsersPage = (
	page: AdminUsersPage,
	fallbackSize: number = ADMIN_USER_PAGE_SIZE,
): { items: AdminUser[]; total: number; size: number } => {
	if (Array.isArray(page)) {
		const list = (page as AdminUser[]).filter(Boolean);
		return {
			items: list,
			total: list.length,
			size: list.length || fallbackSize,
		};
	}
	if (!page || typeof page !== "object") {
		return { items: [], total: 0, size: fallbackSize };
	}
	const content = Array.isArray((page as any).content)
		? ((page as any).content as AdminUser[])
		: Array.isArray((page as any).records)
			? ((page as any).records as AdminUser[])
			: [];
	const total =
		(page as any).totalElements ??
		(page as any).total ??
		(page as any).totalRecords ??
		content.length;
	const size = (page as any).size;
	return {
		items: content,
		total: typeof total === "number" ? total : content.length,
		size: typeof size === "number" && size > 0 ? size : fallbackSize,
	};
};

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

	getInceptorConfig: () =>
		apiClient.get<InceptorConfig | null>({
			url: "/admin/infra/inceptor",
		}),

	testInceptorConnection: (payload: HiveConnectionTestRequest, dataSourceId?: string) =>
		apiClient.post<HiveConnectionTestResult>({
			url: "/admin/infra/inceptor/test",
			data: payload,
			params: dataSourceId ? { dataSourceId } : undefined,
		}),

	publishInceptorConfig: (payload: HiveConnectionPersistRequest) =>
		apiClient.post<Record<string, unknown>>({
			url: "/admin/infra/inceptor/publish",
			data: payload,
		}),

	getDataLakeTestLogs: (dataSourceId?: string) =>
		apiClient.get<ConnectionTestLog[]>({
			url: "/admin/infra/data-lakes/test-logs",
			params: dataSourceId ? { dataSourceId } : undefined,
		}),

	getDataLakeJdbcDrivers: () =>
		apiClient.get<JdbcDriverInfo[]>({
			url: "/admin/infra/data-lakes/jdbc-drivers",
		}),
	getIntegrationSettings: (service: string) =>
		apiClient.get<InfraServiceSettingsPayload>({
			url: `/admin/infra/settings/${service}`,
		}),

	updateIntegrationSettings: (service: string, payload: Record<string, any>) =>
		apiClient.post<InfraServiceSettingsPayload>({
			url: `/admin/infra/settings/${service}`,
			data: payload,
		}),

	testIntegrationSettings: (service: string, payload: Record<string, any>) =>
		apiClient.post<InfraServiceTestResult>({
			url: `/admin/infra/settings/${service}/test`,
			data: payload,
		}),

	listDataLakes: () =>
		apiClient.get<InfraDataSource[]>({
			url: "/admin/infra/data-lakes",
		}),

	createDataLake: (payload: UpsertInfraDataSourcePayload) =>
		apiClient.post<InfraDataSource>({
			url: "/admin/infra/data-lakes",
			data: payload,
		}),

	updateDataLake: (id: string, payload: UpsertInfraDataSourcePayload) =>
		apiClient.put<Record<string, unknown>>({
			url: `/admin/infra/data-lakes/${id}`,
			data: payload,
		}),

	deleteDataLake: (id: string) =>
		apiClient.delete<InfraDataSource>({
			url: `/admin/infra/data-lakes/${id}`,
		}),

	setDefaultDataLake: (id: string) =>
		apiClient.post<InfraDataSource>({
			url: `/admin/infra/data-lakes/${id}/default`,
		}),

	testDataLakeConnection: (id: string, payload?: JdbcConnectionTestRequest) =>
		apiClient.post<HiveConnectionTestResult>({
			url: `/admin/infra/data-lakes/${id}/test`,
			data: payload ?? {},
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
	deletePortalMenuHard: (id: number) =>
		apiClient.delete<PortalMenuCollection>({
			url: `/admin/portal/menus/${id}`,
			params: { hard: true },
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

	getAdminUsers: (options?: { page?: number; size?: number; keyword?: string; status?: 0 | 1 }) =>
		apiClient.get<PagedResult<AdminUser>>({
			url: "/admin/users",
			params: {
				page: options?.page ?? 0,
				size: options?.size ?? ADMIN_USER_PAGE_SIZE,
				keyword: options?.keyword,
				status: options?.status,
			},
		}),

	getAllAdminUsers: async () => {
		const collected = new Map<string, AdminUser>();
		let page = 0;
		let expectedTotal = Number.POSITIVE_INFINITY;
		while (page === 0 || collected.size < expectedTotal) {
			const response = await adminApi.getAdminUsers({ page, size: ADMIN_USER_PAGE_SIZE });
			const { items, total, size } = normalizeAdminUsersPage(response);
			if (Number.isFinite(total)) {
				expectedTotal = total;
			} else if (!Number.isFinite(expectedTotal)) {
				expectedTotal = Math.max(items.length, collected.size);
			}
			if (!items.length) {
				break;
			}
			for (const user of items) {
				const username = user?.username?.trim();
				if (!username) continue;
				const key = username.toLowerCase();
				if (!collected.has(key)) {
					collected.set(key, user);
				}
			}
			page += 1;
			if (items.length < size || page >= 50) {
				break;
			}
		}
		return Array.from(collected.values());
	},

	resolveUserDisplayNames: (usernames: string[]) =>
		apiClient.get<Record<string, string>>({
			url: "/admin/users/display-names",
			params: { usernames: usernames.join(",") },
		}),

	resolveUserMdmEnabled: (usernames: string[]) =>
		apiClient.post<Record<string, number>>({
			url: "/admin/users/mdm-enabled",
			data: { usernames },
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

	getWorkflowTemplates: (workflowType: string, enabledOnly: boolean = false) =>
		apiClient.get<WorkflowTemplateConfig[]>({
			url: "/workflows/templates",
			params: { type: workflowType, enabledOnly },
		}),

	getWorkflowTemplate: (id: string) =>
		apiClient.get<WorkflowTemplateConfig>({
			url: `/workflows/templates/${id}`,
		}),

	createWorkflowTemplate: (payload: UpsertWorkflowTemplatePayload) =>
		apiClient.post<WorkflowTemplateConfig>({
			url: "/workflows/templates",
			data: payload,
		}),

	updateWorkflowTemplate: (id: string, payload: UpsertWorkflowTemplatePayload) =>
		apiClient.put<WorkflowTemplateConfig>({
			url: `/workflows/templates/${id}`,
			data: payload,
		}),

	deleteWorkflowTemplate: (id: string) =>
		apiClient.delete<void>({
			url: `/workflows/templates/${id}`,
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
