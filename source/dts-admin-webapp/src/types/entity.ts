import type { NavItemDataProps } from "@/components/nav/types";
import type { BasicStatus, PermissionType } from "./enum";

export interface UserToken {
	accessToken?: string;
	refreshToken?: string;
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
    eventId?: string;
    id: number;
    occurredAt: string;
    module: string;
    action: string;
    actor: string;
    resourceType?: string;
    resourceId?: string;
    clientIp?: string;
    clientAgent?: string;
    httpMethod?: string;
    result: string;
    // localized display-only fields (backend provided)
    resultText?: string; // SUCCESS/FAILED -> 成功/失败
    extraTags?: string;
    payloadPreview?: string;
    // extended fields (audit refactor)
    sourceSystem?: string;
    sourceSystemText?: string; // admin/platform -> 管理端/业务端
    eventClass?: string;
    eventType?: string;
    summary?: string;
    operatorId?: string;
    operatorName?: string;
    operatorRoles?: string; // JSON string from backend
    orgCode?: string;
    orgName?: string;
    departmentName?: string; // alias of orgName for display
    // convenience fields extracted from details
    requestId?: string;
    targetTable?: string;
    targetTableLabel?: string; // localized table label
    targetId?: string;
    targetRef?: string;
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
