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
	reason?: string;
	category?: string;
	originalValue?: unknown;
	updatedValue?: unknown;
	lastError?: string;
}

export interface AuditEvent {
	id: number;
	occurredAt: string;
	actor?: string;
	module?: string;
	action?: string;
	resourceType?: string;
	resourceId?: string;
	clientIp?: string;
	clientAgent?: string;
	httpMethod?: string;
	result?: string;
	extraTags?: string;
	payloadPreview?: string;
}

export interface SystemConfigItem {
	id?: number;
	key: string;
	value?: string;
	description?: string;
}

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

export type SecurityLevel = "NON_SECRET" | "GENERAL" | "IMPORTANT" | "CORE";
export type OrgDataLevel = "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_SECRET" | "DATA_CONFIDENTIAL";

export type DataOperation = "read" | "write" | "export";

export interface OrganizationNode {
	id: number;
	name: string;
	parentId?: number | null;
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
}

export interface OrganizationUpdatePayload {
    name?: string;
    description?: string;
    parentId?: number | null;
}

export interface AdminUser {
	id: number;
	username: string;
	fullName?: string;
	displayName?: string;
	email?: string;
	orgPath?: string[];
	roles: string[];
	securityLevel: string;
	status: string;
	lastLoginAt?: string;
}

export interface AdminRoleDetail {
	id: number;
	name: string;
	description?: string;
	securityLevel: string;
	permissions: string[];
	memberCount: number;
	approvalFlow: string;
	updatedAt: string;
	scope?: "DEPARTMENT" | "INSTITUTE";
	operations?: DataOperation[];
	source?: string;
	// Extended fields for richer role presentation (optional, backend-provided)
	code?: string;
	roleId?: string;
	nameZh?: string;
	nameEn?: string;
	zone?: "DEPT" | "INST";
	canRead?: boolean;
	canWrite?: boolean;
	canExport?: boolean;
	canManage?: boolean;
	legacyName?: string;
	kcMemberCount?: number;
	menuBindings?: number;
	customRole?: boolean;
	customRoleId?: number | null;
	assignments?: AdminRoleAssignment[];
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
	scope: "DEPARTMENT" | "INSTITUTE";
	operations: DataOperation[];
	maxRows?: number | null;
	allowDesensitizeJson?: boolean;
	description?: string;
	createdBy: string;
	createdAt: string;
}

export interface AdminRoleAssignment {
	id: number;
	role: string;
	username: string;
	displayName: string;
	userSecurityLevel: SecurityLevel;
	scopeOrgId: number | null;
	scopeOrgName: string;
	datasetIds: number[];
	operations: DataOperation[];
	grantedBy: string;
	grantedAt: string;
}

export interface CreateCustomRolePayload {
	name: string;
	scope: "DEPARTMENT" | "INSTITUTE";
	operations: DataOperation[];
	maxRows?: number | null;
	allowDesensitizeJson?: boolean;
	description?: string;
	titleCn?: string;
	nameZh?: string;
	displayName?: string;
	titleEn?: string;
	reason?: string;
}

export interface CreateRoleAssignmentPayload {
	role: string;
	username: string;
	displayName: string;
	userSecurityLevel: SecurityLevel;
	scopeOrgId: number | null;
	datasetIds: number[];
	operations: DataOperation[];
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
