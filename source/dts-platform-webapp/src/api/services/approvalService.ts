import type { KeycloakApiResponse, ApprovalRequest, ApprovalRequestDetail, ApprovalActionRequest } from "#/keycloak";
import apiClient from "../apiClient";

/**
 * Keycloak审批管理API服务
 */
export class KeycloakApprovalService {
	private static readonly BASE_URL = "/keycloak/approvals";

	/**
	 * 获取审批请求列表
	 */
	static getApprovalRequests(): Promise<ApprovalRequest[]> {
		return apiClient.get<ApprovalRequest[]>({
			url: `/approval-requests`,
		});
	}

	/**
	 * 根据ID获取审批请求详情
	 */
	static getApprovalRequestById(requestId: number): Promise<ApprovalRequestDetail> {
		return apiClient.get<ApprovalRequestDetail>({
			url: `/approval-requests/${requestId}`,
		});
	}

	/**
	 * 审批通过请求
	 */
	static approveRequest(requestId: number, data: ApprovalActionRequest): Promise<KeycloakApiResponse> {
		return apiClient.post<KeycloakApiResponse>({
			url: `${KeycloakApprovalService.BASE_URL}/${requestId}/approve`,
			data,
		});
	}

	/**
	 * 审批拒绝请求
	 */
	static rejectRequest(requestId: number, data: ApprovalActionRequest): Promise<KeycloakApiResponse> {
		return apiClient.post<KeycloakApiResponse>({
			url: `${KeycloakApprovalService.BASE_URL}/${requestId}/reject`,
			data,
		});
	}

	/**
	 * 处理已批准的请求并同步到Keycloak
	 */
	static processApprovedRequest(requestId: number): Promise<KeycloakApiResponse> {
		return apiClient.post<KeycloakApiResponse>({
			url: `${KeycloakApprovalService.BASE_URL}/${requestId}/process`,
		});
	}
}

/**
 * Keycloak用户同步API服务
 */
export class KeycloakUserSyncService {
	private static readonly BASE_URL = "/keycloak/user-sync";

	/**
	 * 处理审批通过的请求
	 */
	static processApprovedRequest(requestId: number): Promise<KeycloakApiResponse> {
		return apiClient.post<KeycloakApiResponse>({
			url: `${KeycloakUserSyncService.BASE_URL}/process/${requestId}`,
		});
	}
}

/**
 * 导出所有审批相关服务
 */
export default {
	approval: KeycloakApprovalService,
	sync: KeycloakUserSyncService,
};
