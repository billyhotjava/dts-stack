export type AdminRole = "SYSADMIN" | "OPADMIN" | "AUTHADMIN" | "AUDITADMIN";

export interface AdminWhoami {
	allowed: boolean;
	role?: string | null;
	username?: string;
	email?: string;
}

export function normalizeAdminRole(role: string | null | undefined): AdminRole | null {
	if (!role) {
		return null;
	}
	let normalized = role.trim().toUpperCase();
	if (normalized.startsWith("ROLE")) {
		normalized = normalized.replace(/^ROLE[_\-]?/, "");
	}
	switch (normalized) {
		case "SYSADMIN":
		case "SYS_ADMIN":
			return "SYSADMIN";
		case "OPADMIN":
		case "OP_ADMIN":
			return "OPADMIN";
		case "AUTHADMIN":
		case "AUTH_ADMIN":
		case "IAMADMIN":
		case "IAM_ADMIN":
		case "IAM-ADMIN":
		case "IAMADMINISTRATOR":
		case "IAM_ADMINISTRATOR":
			return "AUTHADMIN";
		case "AUDITADMIN":
		case "AUDIT_ADMIN":
		case "AUDITORADMIN":
		case "AUDITOR_ADMIN":
		case "SECURITYAUDITOR":
		case "SECURITY_AUDITOR":
			return "AUDITADMIN";
		default:
			return null;
	}
}

export interface ChangeRequest {
	id: number;
	resourceType: string;
	resourceId?: string | null;
	action: string;
	payloadJson?: string;
	diffJson?: string;
	status: string;
	requestedBy: string;
	requestedAt?: string;
	decidedBy?: string;
	decidedAt?: string;
	requestedByDisplayName?: string;
	decidedByDisplayName?: string;
	reason?: string;
	category?: string;
	originalValue?: unknown;
	updatedValue?: unknown;
	lastError?: string;
	sourcePrimaryKey?: string | number | null;
	sourceTable?: string | null;
}

export interface AuditEvent {
	id: number;
	occurredAt: string;
	sourceSystem?: string;
	sourceSystemText?: string;
	module?: string;
	moduleKey?: string;
	buttonCode?: string;
	action?: string;
	operationCode?: string;
	operationTypeCode?: string;
	operationType?: string;
	operationContent?: string;
	summary?: string;
	result?: string;
	resultText?: string;
	logTypeText?: string;
	eventClass?: string;
	eventType?: string;
	operationGroup?: string;
	actor?: string;
	actorName?: string;
	actorRole?: string | null;
	actorRoles?: string[];
	clientIp?: string;
	clientAgent?: string;
	requestUri?: string;
	httpMethod?: string;
	resourceType?: string;
	resourceId?: string;
	targetTable?: string;
	targetTableLabel?: string;
	targetId?: string;
	targetIds?: string[];
	targetLabels?: Record<string, string>;
	changeRequestRef?: string;
	requestId?: unknown;
	approvalSummary?: unknown;
	operatorId?: string;
	operatorName?: string;
	operatorRoles?: string;
	orgCode?: string;
	orgName?: string;
	departmentName?: string;
	metadata?: Record<string, unknown>;
	extraAttributes?: Record<string, unknown>;
	details?: unknown;
	payload?: unknown;
	sourcePrimaryKey?: string | number | null;
	sourceTable?: string | null;
}

export type AuditLog = AuditEvent;

export interface SystemConfigItem {
	id?: number;
	key: string;
	value?: string;
	description?: string;
}

/** 运维配置项（含脱敏值和元数据） */
export interface OpsConfigItem {
	id: number;
	key: string;
	value: string; // 脱敏后的值
	description?: string;
	category: string;
	sensitive: boolean;
	dataType: "STRING" | "BOOLEAN" | "INTEGER" | "JSON";
	editable: boolean;
	sortOrder: number;
	displayName?: string;
	scope?: "RUNTIME" | "RUNTIME_RESTART" | "BOOTSTRAP";
	restartRequired?: boolean;
	validationRule?: string;
	owner?: string;
	groupKey?: string;
	groupLabel?: string;
	groupOrder?: number;
	lastModified?: string;
	lastModifiedBy?: string;
}

/** 运维配置分类 */
export interface OpsConfigCategory {
	key: string;
	label: string;
	items: OpsConfigItem[];
}

/** 运维配置响应 */
export interface OpsConfigResponse {
	categories: OpsConfigCategory[];
	total: number;
}

export type WorkflowOwnerScope = "ANY" | "INST" | "DEPT";

export interface WorkflowStepConfig {
	stepOrder: number;
	approverRole: string;
	deptBinding?: boolean;
}

export interface WorkflowTemplateConfig {
	id: string;
	workflowType: string;
	name: string;
	enabled?: boolean;
	priority?: number;
	ownerScope?: WorkflowOwnerScope | string;
	classificationMin?: string | null;
	classificationMax?: string | null;
	steps?: WorkflowStepConfig[];
}

export type UpsertWorkflowTemplatePayload = Omit<WorkflowTemplateConfig, "id">;

export interface PortalMenuItem {
	id?: number;
	name: string;
	path: string;
	component?: string;
	sortOrder?: number;
	metadata?: string;
	parentId?: number | null;
	icon?: string;
	displayName?: string;
	securityLevel?: SecurityLevel;
	deleted?: boolean;
	allowedRoles?: string[];
	children?: PortalMenuItem[];
}

export interface PortalMenuCollection {
	menus: PortalMenuItem[];
	allMenus?: PortalMenuItem[];
}

export type PortalMenuBulkVisibilityMode = "APPEND" | "REMOVE" | "REPLACE";

export interface PortalMenuBulkVisibilityPayload {
	menuIds: number[];
	roles: string[];
	mode: PortalMenuBulkVisibilityMode;
	reason?: string;
}

export type SecurityLevel = "GENERAL" | "IMPORTANT" | "CORE";
export type OrgDataLevel = "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_SECRET" | "DATA_CONFIDENTIAL";

export type DataOperation = "read" | "write" | "export";

export interface PagedResult<T> {
	content: T[];
	total?: number;
	totalElements?: number;
	page: number;
	size: number;
}

export interface OrganizationNode {
	id: number;
	name: string;
	parentId?: number | null;
	isRoot?: boolean;
	status?: string;
	contact?: string;
	phone?: string;
	description?: string;
	keycloakGroupId?: string;
	groupPath?: string;
	// legacy fields kept optional for compatibility with draft flows
	code?: string;
	leader?: string;
	memberCount?: number;
	securityDomains?: string[];
	sensitivity?: string;
	level?: number;
	children?: OrganizationNode[];
}

export interface OrganizationCreatePayload {
	name: string;
	description?: string;
	parentId?: number | null;
	isRoot?: boolean;
}

export interface OrganizationUpdatePayload {
	name?: string;
	description?: string;
	parentId?: number | null;
	isRoot?: boolean;
}

export interface AdminUser {
	id: number;
	keycloakId?: string;
	username: string;
	fullName?: string;
	displayName?: string;
	email?: string;
	orgPath?: string[];
	groupPaths?: string[];
	roles: string[];
	securityLevel: string;
	status: string;
	lastLoginAt?: string;
	realmRoles?: string[];
	enabled?: boolean;
	mdmEnabled?: number;
}

export interface RoleAssignmentUser {
	id?: number;
	keycloakId?: string;
	username: string;
	fullName?: string;
	email?: string;
	deptCode?: string;
	deptName?: string;
	groupPaths?: string[];
	enabled?: boolean;
	mdmEnabled?: number;
	inRole: boolean;
}

export interface RoleAssignmentUserQuery {
	page?: number;
	size?: number;
	username?: string;
	fullName?: string;
	deptPath?: string;
}

export interface AdminRoleDetail {
	id: number;
	name: string;
	description?: string;
	securityLevel?: string;
	memberCount: number;
	approvalFlow?: string;
	updatedAt: string;
	scope?: "DEPARTMENT" | "INSTITUTE";
	operations?: DataOperation[];
	source?: string;
	// Extended fields for richer role presentation (optional, backend-provided)
	code?: string;
	roleId?: string;
	displayName?: string;
	zone?: "DEPT" | "INST";
	canRead?: boolean;
	canWrite?: boolean;
	canExport?: boolean;
	canManage?: boolean;
	legacyName?: string;
	menuBindings?: number;
	customRole?: boolean;
	customRoleId?: number | null;
}

export interface AdminDataset {
	id: number;
	name: string;
	businessCode: string;
	description?: string;
	dataLevel: OrgDataLevel;
	ownerOrgId: number;
	ownerOrgName: string;
	isInstituteShared: boolean;
	rowCount: number;
	updatedAt: string;
}

export interface AdminCustomRole {
	id: number;
	name: string;
	displayName?: string;
	scope: "DEPARTMENT" | "INSTITUTE";
	operations?: DataOperation[];
	description?: string;
	createdBy: string;
	createdAt: string;
}

export interface CreateCustomRolePayload {
	name: string;
	scope: "DEPARTMENT" | "INSTITUTE";
	description?: string;
	displayName?: string;
	reason?: string;
}

export interface PermissionCatalogSection {
	category: string;
	description?: string;
	permissions: {
		code: string;
		name: string;
		description?: string;
		securityLevel?: string;
	}[];
}
