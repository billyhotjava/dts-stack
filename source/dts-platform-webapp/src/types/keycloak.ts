/**
 * Keycloak相关类型定义
 * 对应后端DTO对象的TypeScript类型
 */

/**
 * Keycloak翻译词表
 */
export interface KeycloakTranslations {
	userManagement: Record<string, string>;
	roleManagement: Record<string, string>;
	groupManagement: Record<string, string>;
	commonActions: Record<string, string>;
	statusMessages: Record<string, string>;
	formLabels: Record<string, string>;
	pagination: Record<string, string>;
}

/**
 * Keycloak用户信息
 */
export interface KeycloakUser {
	id?: string;
	username: string;
	email?: string;
	firstName?: string;
	lastName?: string;
	enabled?: boolean;
	emailVerified?: boolean;
	attributes?: Record<string, string[]>;
	groups?: string[];
	realmRoles?: string[];
	clientRoles?: Record<string, string[]>;
	createdTimestamp?: number;
}

/**
 * Keycloak角色信息
 */
export interface KeycloakRole {
	id?: string;
	name: string;
	description?: string;
	composite?: boolean;
	clientRole?: boolean;
	containerId?: string;
	attributes?: Record<string, string>;
}

/**
 * Keycloak组信息
 */
export interface KeycloakGroup {
	id?: string;
	name: string;
	path?: string;
	attributes?: Record<string, string[]>;
	realmRoles?: string[];
	clientRoles?: Record<string, string[]>;
	subGroups?: KeycloakGroup[];
}

/**
 * 创建用户请求
 */
export interface CreateUserRequest {
	username: string;
	email?: string;
	firstName?: string;
	lastName?: string;
	enabled?: boolean;
	emailVerified?: boolean;
	attributes?: Record<string, string[]>;
}

/**
 * 更新用户请求
 */
export interface UpdateUserRequest {
	id?: string;
	username?: string;
	email?: string;
	firstName?: string;
	lastName?: string;
	enabled?: boolean;
	emailVerified?: boolean;
	attributes?: Record<string, string[]>;
}

/**
 * 重置密码请求
 */
export interface ResetPasswordRequest {
	password: string;
	temporary?: boolean;
}

/**
 * 设置用户启用状态请求
 */
export interface SetUserEnabledRequest {
	enabled: boolean;
}

/**
 * 创建角色请求
 */
export interface CreateRoleRequest {
	name: string;
	description?: string;
	composite?: boolean;
	clientRole?: boolean;
	attributes?: Record<string, string>;
}

/**
 * 更新角色请求
 */
export interface UpdateRoleRequest {
	name?: string;
	description?: string;
	composite?: boolean;
	clientRole?: boolean;
	attributes?: Record<string, string>;
}

/**
 * 创建组请求
 */
export interface CreateGroupRequest {
	name: string;
	path?: string;
	attributes?: Record<string, string[]>;
	realmRoles?: string[];
	clientRoles?: Record<string, string[]>;
}

/**
 * 更新组请求
 */
export interface UpdateGroupRequest {
	name?: string;
	path?: string;
	attributes?: Record<string, string[]>;
	realmRoles?: string[];
	clientRoles?: Record<string, string[]>;
}

/**
 * API响应通用格式
 */
export interface KeycloakApiResponse<T = any> {
	message?: string;
	error?: string;
	userId?: string;
	groupId?: string;
	data?: T;
	requestId?: number;
}

/**
 * 用户查询参数
 */
export interface UserQueryParams {
	first?: number;
	max?: number;
	username?: string;
}

/**
 * 表格分页参数
 */
export interface PaginationParams {
	current: number;
	pageSize: number;
	total?: number;
}

/**
 * 用户表格行数据
 */
export interface UserTableRow extends KeycloakUser {
	key: string;
}

/**
 * 角色表格行数据
 */
export interface RoleTableRow extends KeycloakRole {
	key: string;
}

/**
 * 组表格行数据
 */
export interface GroupTableRow extends KeycloakGroup {
	key: string;
	memberCount?: number;
}

/**
 * UserProfile必需性配置
 */
export interface UserProfileRequired {
	roles?: string[];
	scopes?: string[];
}

/**
 * UserProfile权限配置
 */
export interface UserProfilePermissions {
	view?: string[];
	edit?: string[];
}

/**
 * UserProfile选择器配置
 */
export interface UserProfileSelector {
	scopes?: string[];
}

/**
 * UserProfile属性定义
 */
export interface UserProfileAttribute {
	name: string;
	displayName: string;
	validations?: Record<string, any>;
	annotations?: Record<string, any>;
	required?: UserProfileRequired;
	permissions?: UserProfilePermissions;
	multivalued?: boolean;
	group?: string;
	selector?: UserProfileSelector;
}

/**
 * UserProfile组定义
 */
export interface UserProfileGroup {
	name: string;
	displayHeader?: string;
	displayDescription?: string;
	annotations?: Record<string, any>;
}

/**
 * UserProfile配置
 */
export interface UserProfileConfig {
	attributes?: UserProfileAttribute[];
	groups?: UserProfileGroup[];
	unmanagedAttributePolicy?: string;
}

/**
 * UserProfile测试响应
 */
export interface UserProfileTestResponse {
	configured: boolean;
	message: string;
	attributeCount?: number;
	attributes?: string[];
	error?: string;
}

/**
 * 审批请求信息（列表用，不包含items字段）
 */
export interface ApprovalRequest {
	id: number;
	requester: string;
	type: string;
	reason: string;
	createdAt: string;
	decidedAt?: string;
	status: string;
	approver?: string;
	decisionNote?: string;
	errorMessage?: string;
	// 注意：列表接口不返回items字段
}

/**
 * 审批项信息
 */
export interface ApprovalItem {
	id: number;
	targetKind: string;
	targetId: string;
	seqNumber: number;
	payload: string;
}

/**
 * 审批请求详情（包含items字段）
 */
export interface ApprovalRequestDetail extends ApprovalRequest {
	items: ApprovalItem[];
}

/**
 * 审批操作请求
 */
export interface ApprovalActionRequest {
	approver: string;
	note: string;
}
