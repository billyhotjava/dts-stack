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
	const normalized = role.trim().toUpperCase();
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
}

export interface AuditEvent {
	id: number;
	timestamp: string;
	actor?: string;
	actorRoles?: string;
	ip?: string;
	action?: string;
	resource?: string;
	outcome?: string;
	detailJson?: string;
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
	children?: PortalMenuItem[];
}

export type OrgDataLevel = "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_SECRET" | "DATA_TOP_SECRET";

export type SecurityLevel = "NON_SECRET" | "GENERAL" | "IMPORTANT" | "CORE";

export type DataOperation = "read" | "write" | "export";

export interface OrganizationNode {
	id: number;
	name: string;
	dataLevel: OrgDataLevel;
	parentId?: number | null;
	contact?: string;
	phone?: string;
	description?: string;
	// legacy fields kept optional for compatibility with draft flows
	code?: string;
	leader?: string;
	memberCount?: number;
	securityDomains?: string[];
	sensitivity?: string;
	level?: number;
	children?: OrganizationNode[];
}

export interface OrganizationPayload {
	name: string;
	dataLevel: OrgDataLevel;
	parentId?: number | null;
	contact?: string;
	phone?: string;
	description?: string;
}

export interface AdminUser {
	id: number;
	username: string;
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
	maxDataLevel: OrgDataLevel;
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
	maxDataLevel: OrgDataLevel;
	description?: string;
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
