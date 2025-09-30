import type { AuditLog } from "#/entity";
import apiClient from "../apiClient";

/**
 * 审计日志API服务
 */
export class AuditLogService {
	private static readonly BASE_URL = "/audit-logs";

	/**
	 * 获取审计日志列表
	 * @param page 页码(从0开始)
	 * @param size 每页大小
	 * @param sort 排序字段
	 * @returns 审计日志分页数据
	 */
	static getAuditLogs(page: number = 0, size: number = 10, sort: string = "id,desc"): Promise<AuditLog[]> {
		return apiClient.get<AuditLog[]>({
			url: AuditLogService.BASE_URL,
			params: {
				page,
				size,
				sort,
			},
		});
	}

	/**
	 * 根据ID获取审计日志详情
	 * @param id 审计日志ID
	 * @returns 审计日志详情
	 */
	static getAuditLogById(id: number): Promise<AuditLog> {
		return apiClient.get<AuditLog>({
			url: `${AuditLogService.BASE_URL}/${id}`,
		});
	}

	/**
	 * 创建审计日志
	 * @param auditLog 审计日志数据
	 * @returns 创建的审计日志
	 */
	static createAuditLog(auditLog: Omit<AuditLog, "id" | "createdAt" | "updatedAt">): Promise<AuditLog> {
		return apiClient.post<AuditLog>({
			url: AuditLogService.BASE_URL,
			data: auditLog,
		});
	}

	/**
	 * 更新审计日志
	 * @param id 审计日志ID
	 * @param auditLog 审计日志数据
	 * @returns 更新后的审计日志
	 */
	static updateAuditLog(id: number, auditLog: Partial<AuditLog>): Promise<AuditLog> {
		return apiClient.put<AuditLog>({
			url: `${AuditLogService.BASE_URL}/${id}`,
			data: auditLog,
		});
	}

	/**
	 * 删除审计日志
	 * @param id 审计日志ID
	 */
	static deleteAuditLog(id: number): Promise<void> {
		return apiClient.delete({
			url: `${AuditLogService.BASE_URL}/${id}`,
		});
	}
}

export default AuditLogService;
