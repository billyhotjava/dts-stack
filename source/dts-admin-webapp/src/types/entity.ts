import type { NavItemDataProps } from "@/components/nav/types";
import type { BasicStatus, PermissionType } from "./enum";

export interface UserToken {
	accessToken?: string;
	refreshToken?: string;
	sessionTakeover?: boolean;
}

export interface UserInfo {
	id: string;
	email: string;
	username: string;
	password?: string;
	avatar?: string;
	firstName?: string;
	fullName?: string;
	lastName?: string;
	enabled?: boolean;
	roles?: Role[] | string[]; // 支持两种格式：对象数组或字符串数组
	status?: BasicStatus;
	attributes?: Record<string, string[]>;
	permissions?: Permission[] | string[]; // 支持两种格式：对象数组或字符串数组
	menu?: MenuTree[];
}

export interface Permission_Old {
	id: string;
	parentId: string;
	name: string;
	label: string;
	type: PermissionType;
	route: string;
	status?: BasicStatus;
	order?: number;
	icon?: string;
	component?: string;
	hide?: boolean;
	hideTab?: boolean;
	frameSrc?: URL;
	newFeature?: boolean;
	children?: Permission_Old[];
}

export interface Role_Old {
	id: string;
	name: string;
	code: string;
	status: BasicStatus;
	order?: number;
	desc?: string;
	permission?: Permission_Old[];
}

export interface CommonOptions {
	status?: BasicStatus;
	desc?: string;
	createdAt?: string;
	updatedAt?: string;
}
export interface User extends CommonOptions {
	id: string; // uuid
	username: string;
	password: string;
	email: string;
	phone?: string;
	avatar?: string;
}

export interface Role extends CommonOptions {
	id: string; // uuid
	name: string;
	code: string;
}

export interface Permission extends CommonOptions {
	id: string; // uuid
	name: string;
	code: string; // resource:action  example: "user-management:read"
}

export interface Menu extends CommonOptions, MenuMetaInfo {
	id: string; // uuid
	parentId: string;
	name: string;
	code: string;
	order?: number;
	type: PermissionType;
}

export type MenuMetaInfo = Partial<
	Pick<NavItemDataProps, "path" | "icon" | "caption" | "info" | "disabled" | "auth" | "hidden">
> & {
	externalLink?: URL;
	component?: string;
};

export type MenuTree = Menu & {
	children?: MenuTree[];
};

// 审计日志相关类型
export interface AuditLog {
	id: number;
	eventId?: string;
	occurredAt: string;
	sourceSystem?: string;
	sourceSystemText?: string;
	module?: string;
	moduleKey?: string;
	buttonCode?: string;
	action?: string;
	operationCode?: string;
	operationGroup?: string;
	operationTypeCode?: string;
	operationType?: string;
	operationContent?: string;
	summary?: string;
	result?: string;
	resultText?: string;
	logTypeText?: string;
	eventClass?: string;
	eventType?: string;
	actor?: string;
	actorName?: string;
	actorRole?: string | null;
	actorRoles?: string[];
	operatorId?: string;
	operatorName?: string;
	operatorRoles?: string;
	resourceType?: string;
	resourceId?: string;
	targetTable?: string;
	targetTableLabel?: string;
	targetId?: string;
	targetIds?: string[];
	targetLabels?: Record<string, string>;
	changeRequestRef?: string;
	requestId?: string;
	approvalSummary?: unknown;
	clientIp?: string;
	clientAgent?: string;
	httpMethod?: string;
	requestUri?: string;
	metadata?: Record<string, unknown>;
	extraAttributes?: Record<string, unknown>;
	orgCode?: string;
	orgName?: string;
	departmentName?: string;
}

export interface AuditLogDetail extends AuditLog {
	payload?: unknown;
	details?: unknown;
}

export interface AuditLogPageResponse {
	content: AuditLog[];
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
}
