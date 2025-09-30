import type {
	CreateGroupRequest,
	CreateRoleRequest,
	CreateUserRequest,
	KeycloakApiResponse,
	KeycloakGroup,
	KeycloakRole,
	KeycloakUser,
	ResetPasswordRequest,
	SetUserEnabledRequest,
	UpdateGroupRequest,
	UpdateRoleRequest,
	UpdateUserRequest,
	UserProfileConfig,
	UserProfileTestResponse,
	UserQueryParams,
} from "#/keycloak";
import apiClient from "../apiClient";

/**
 * Keycloak用户管理API服务
 */
export class KeycloakUserService {
	private static readonly BASE_URL = "/keycloak/users";

	/**
	 * 获取所有用户
	 */
	static getAllUsers(params: UserQueryParams = {}): Promise<KeycloakUser[]> {
		return apiClient.get<KeycloakUser[]>({
			url: KeycloakUserService.BASE_URL,
			params: {
				first: params.first || 0,
				max: params.max || 100,
			},
		});
	}

	/**
	 * 根据用户名搜索用户
	 */
	static searchUsers(username: string): Promise<KeycloakUser[]> {
		return apiClient.get<KeycloakUser[]>({
			url: `${KeycloakUserService.BASE_URL}/search`,
			params: { username },
		});
	}

	/**
	 * 根据ID获取用户
	 */
	static getUserById(userId: string): Promise<KeycloakUser> {
		return apiClient.get<KeycloakUser>({
			url: `${KeycloakUserService.BASE_URL}/${userId}`,
		});
	}

	/**
	 * 创建新用户
	 */
	static createUser(user: CreateUserRequest): Promise<KeycloakApiResponse> {
		return apiClient.post<KeycloakApiResponse>({
			url: KeycloakUserService.BASE_URL,
			data: user,
		});
	}

	/**
	 * 更新用户信息
	 */
	static updateUser(userId: string, user: UpdateUserRequest): Promise<KeycloakApiResponse> {
		return apiClient.put<KeycloakApiResponse>({
			url: `${KeycloakUserService.BASE_URL}/${userId}`,
			data: user,
		});
	}

	/**
	 * 删除用户
	 */
	static deleteUser(userId: string): Promise<KeycloakApiResponse> {
		return apiClient.delete<KeycloakApiResponse>({
			url: `${KeycloakUserService.BASE_URL}/${userId}`,
		});
	}

	/**
	 * 重置用户密码
	 */
	static resetPassword(userId: string, passwordData: ResetPasswordRequest): Promise<KeycloakApiResponse> {
		return apiClient.post<KeycloakApiResponse>({
			url: `${KeycloakUserService.BASE_URL}/${userId}/reset-password`,
			data: passwordData,
		});
	}

	/**
	 * 启用/禁用用户
	 */
	static setUserEnabled(userId: string, enabledData: SetUserEnabledRequest): Promise<KeycloakApiResponse> {
		return apiClient.put<KeycloakApiResponse>({
			url: `${KeycloakUserService.BASE_URL}/${userId}/enabled`,
			data: enabledData,
		});
	}

	/**
	 * 获取用户的角色
	 */
	static getUserRoles(userId: string): Promise<KeycloakRole[]> {
		return apiClient.get<KeycloakRole[]>({
			url: `${KeycloakUserService.BASE_URL}/${userId}/roles`,
		});
	}

	/**
	 * 为用户分配角色
	 */
	static assignRolesToUser(userId: string, roles: KeycloakRole[]): Promise<KeycloakApiResponse> {
		return apiClient.post<KeycloakApiResponse>({
			url: `${KeycloakUserService.BASE_URL}/${userId}/roles`,
			data: roles,
		});
	}

	/**
	 * 移除用户的角色
	 */
	static removeRolesFromUser(userId: string, roles: KeycloakRole[]): Promise<KeycloakApiResponse> {
		return apiClient.delete<KeycloakApiResponse>({
			url: `${KeycloakUserService.BASE_URL}/${userId}/roles`,
			data: roles,
		});
	}
}

/**
 * Keycloak角色管理API服务
 */
export class KeycloakRoleService {
	private static readonly BASE_URL = "/keycloak/roles";

	/**
	 * 获取所有Realm角色
	 */
	static getAllRealmRoles(): Promise<KeycloakRole[]> {
		return apiClient.get<KeycloakRole[]>({
			url: KeycloakRoleService.BASE_URL,
		});
	}

	/**
	 * 根据名称获取角色
	 */
	static getRoleByName(roleName: string): Promise<KeycloakRole> {
		return apiClient.get<KeycloakRole>({
			url: `${KeycloakRoleService.BASE_URL}/${roleName}`,
		});
	}

	/**
	 * 创建新角色
	 */
	static createRole(role: CreateRoleRequest): Promise<KeycloakApiResponse> {
		return apiClient.post<KeycloakApiResponse>({
			url: KeycloakRoleService.BASE_URL,
			data: role,
		});
	}

	/**
	 * 更新角色
	 */
	static updateRole(roleName: string, role: UpdateRoleRequest): Promise<KeycloakApiResponse> {
		return apiClient.put<KeycloakApiResponse>({
			url: `${KeycloakRoleService.BASE_URL}/${roleName}`,
			data: role,
		});
	}

	/**
	 * 删除角色
	 */
	static deleteRole(roleName: string): Promise<KeycloakApiResponse> {
		return apiClient.delete<KeycloakApiResponse>({
			url: `${KeycloakRoleService.BASE_URL}/${roleName}`,
		});
	}
}

/**
 * Keycloak组管理API服务
 */
export class KeycloakGroupService {
	private static readonly BASE_URL = "/keycloak/groups";

	/**
	 * 获取所有组
	 */
	static getAllGroups(): Promise<KeycloakGroup[]> {
		return apiClient.get<KeycloakGroup[]>({
			url: KeycloakGroupService.BASE_URL,
		});
	}

	/**
	 * 根据ID获取组
	 */
	static getGroupById(groupId: string): Promise<KeycloakGroup> {
		return apiClient.get<KeycloakGroup>({
			url: `${KeycloakGroupService.BASE_URL}/${groupId}`,
		});
	}

	/**
	 * 创建新组
	 */
	static createGroup(group: CreateGroupRequest): Promise<KeycloakApiResponse> {
		return apiClient.post<KeycloakApiResponse>({
			url: KeycloakGroupService.BASE_URL,
			data: group,
		});
	}

	/**
	 * 更新组信息
	 */
	static updateGroup(groupId: string, group: UpdateGroupRequest): Promise<KeycloakApiResponse> {
		return apiClient.put<KeycloakApiResponse>({
			url: `${KeycloakGroupService.BASE_URL}/${groupId}`,
			data: group,
		});
	}

	/**
	 * 删除组
	 */
	static deleteGroup(groupId: string): Promise<KeycloakApiResponse> {
		return apiClient.delete<KeycloakApiResponse>({
			url: `${KeycloakGroupService.BASE_URL}/${groupId}`,
		});
	}

	/**
	 * 获取组的成员
	 */
	static getGroupMembers(groupId: string): Promise<string[]> {
		return apiClient.get<string[]>({
			url: `${KeycloakGroupService.BASE_URL}/${groupId}/members`,
		});
	}

	/**
	 * 将用户加入组
	 */
	static addUserToGroup(groupId: string, userId: string): Promise<KeycloakApiResponse> {
		return apiClient.post<KeycloakApiResponse>({
			url: `${KeycloakGroupService.BASE_URL}/${groupId}/members/${userId}`,
		});
	}

	/**
	 * 将用户从组中移除
	 */
	static removeUserFromGroup(groupId: string, userId: string): Promise<KeycloakApiResponse> {
		return apiClient.delete<KeycloakApiResponse>({
			url: `${KeycloakGroupService.BASE_URL}/${groupId}/members/${userId}`,
		});
	}

	/**
	 * 获取用户所属的组
	 */
	static getUserGroups(userId: string): Promise<KeycloakGroup[]> {
		return apiClient.get<KeycloakGroup[]>({
			url: `${KeycloakGroupService.BASE_URL}/user/${userId}`,
		});
	}
}

/**
 * Keycloak UserProfile管理API服务
 */
export class KeycloakUserProfileService {
	private static readonly BASE_URL = "/keycloak/userprofile";

	/**
	 * 获取UserProfile配置
	 */
	static getUserProfileConfig(): Promise<UserProfileConfig> {
		return apiClient.get<UserProfileConfig>({
			url: `${KeycloakUserProfileService.BASE_URL}/config`,
		});
	}

	/**
	 * 更新UserProfile配置
	 */
	static updateUserProfileConfig(config: UserProfileConfig): Promise<KeycloakApiResponse> {
		return apiClient.put<KeycloakApiResponse>({
			url: `${KeycloakUserProfileService.BASE_URL}/config`,
			data: config,
		});
	}

	/**
	 * 检查UserProfile是否已配置
	 */
	static isUserProfileConfigured(): Promise<{ configured: boolean }> {
		return apiClient.get<{ configured: boolean }>({
			url: `${KeycloakUserProfileService.BASE_URL}/configured`,
		});
	}

	/**
	 * 获取UserProfile中定义的所有属性名称
	 */
	static getUserProfileAttributeNames(): Promise<string[]> {
		return apiClient.get<string[]>({
			url: `${KeycloakUserProfileService.BASE_URL}/attributes`,
		});
	}

	/**
	 * 测试UserProfile服务连接
	 */
	static testUserProfileService(): Promise<UserProfileTestResponse> {
		return apiClient.get<UserProfileTestResponse>({
			url: `${KeycloakUserProfileService.BASE_URL}/test`,
		});
	}
}

/**
 * 导出所有Keycloak服务
 */
export default {
	user: KeycloakUserService,
	role: KeycloakRoleService,
	group: KeycloakGroupService,
	userProfile: KeycloakUserProfileService,
};
