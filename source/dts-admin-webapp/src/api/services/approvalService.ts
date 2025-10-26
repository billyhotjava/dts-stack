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
	 * 根据变更请求ID查找对应的审批请求ID
	 * 兼容现有后端：遍历列表并按需拉取详情以判断payload中的 changeRequestId
	 */
	static async findApprovalIdByChangeRequestId(changeRequestId: number): Promise<number | null> {
		// Scan list and match by embedded payload.changeRequestId
		// 注：避免直接探测 /approval-requests/{id} 触发 404 弹窗
		const list = await this.getApprovalRequests();
		for (const item of list) {
			try {
				const detail = await this.getApprovalRequestById(item.id);
				for (const it of detail.items || []) {
					if (!it?.payload) continue;
					try {
						const payload = JSON.parse(it.payload);
						const cid = Number(payload?.changeRequestId);
						if (cid === changeRequestId) {
							return item.id;
						}
					} catch {}
				}
			} catch {}
		}
		return null;
	}

	/**
	 * 审批通过（按变更请求ID）
	 */
	static async approveByChangeRequest(changeRequestId: number, approver: string, note?: string) {
		const approvalId = await this.findApprovalIdByChangeRequestId(changeRequestId);
		if (approvalId == null) {
			throw new Error("未找到匹配的审批请求");
		}
		return this.approveRequest(approvalId, { approver, note: note || "" });
	}

	/**
	 * 审批拒绝（按变更请求ID）
	 */
	static async rejectByChangeRequest(changeRequestId: number, approver: string, note?: string) {
		const approvalId = await this.findApprovalIdByChangeRequestId(changeRequestId);
		if (approvalId == null) {
			throw new Error("未找到匹配的审批请求");
		}
		return this.rejectRequest(approvalId, { approver, note: note || "" });
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
