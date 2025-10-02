import type { AuditLogPageResponse } from "#/entity";
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
	 * @param filters 筛选参数
	 * @returns 审计日志分页数据
	 */
	static getAuditLogs(
		page: number = 0,
		size: number = 20,
		sort: string = "occurredAt,desc",
		filters: Record<string, unknown> = {},
	): Promise<AuditLogPageResponse> {
		return apiClient.get<AuditLogPageResponse>({
			url: AuditLogService.BASE_URL,
			params: {
				page,
				size,
				sort,
				...filters,
			},
		});
	}
}

export default AuditLogService;
