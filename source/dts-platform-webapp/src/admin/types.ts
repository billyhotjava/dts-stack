export type AdminRole = "SYSADMIN" | "AUTHADMIN" | "AUDITADMIN";

export interface AdminWhoami {
	allowed: boolean;
	role?: AdminRole;
	username?: string;
	email?: string;
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
