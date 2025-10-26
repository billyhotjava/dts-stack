import type { NavItemDataProps } from "@/components/nav/types";
import type { BasicStatus, PermissionType } from "./enum";

export interface UserToken {
	accessToken?: string;
	refreshToken?: string;
	adminAccessToken?: string;
	adminRefreshToken?: string;
	adminAccessTokenExpiresAt?: string;
	adminRefreshTokenExpiresAt?: string;
}

export interface UserInfo {
    id: string;
    email: string;
    username: string;
    password?: string;
    avatar?: string;
    firstName?: string;
    lastName?: string;
    // Optional computed full name used by profile pages
    fullName?: string;
    // Optional Keycloak-style attributes map
    attributes?: Record<string, string[]>;
    department?: string;
    enabled?: boolean;
    roles?: Role[] | string[]; // 支持两种格式：对象数组或字符串数组
    status?: BasicStatus;
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
    // Optional display name if provided by backend menus
    displayName?: string;
    code: string;
    order?: number;
    type: PermissionType;
    metadata?: string;
    deleted?: boolean;
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
	occurredAt: string;
	module: string;
	action: string;
	actor: string;
	actorRole?: string;
	resourceType?: string;
	resourceId?: string;
	clientIp?: string;
	clientAgent?: string;
	requestUri?: string;
	httpMethod?: string;
	result: string;
	latencyMs?: number;
	extraTags?: string;
	payloadPreview?: string;
}

export interface AuditLogPageResponse {
	content: AuditLog[];
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
}
