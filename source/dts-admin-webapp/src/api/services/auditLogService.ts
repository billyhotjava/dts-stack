import type { AuditLog, AuditLogDetail, AuditLogPageResponse } from "#/entity";
import apiClient from "../apiClient";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";

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

	/**
	 * 根据ID获取审计日志详情
	 * @param id 审计日志ID
	 * @returns 审计日志详情
	 */
	static getAuditLogById(id: number): Promise<AuditLogDetail> {
		return apiClient.get<AuditLogDetail>({
			url: `${AuditLogService.BASE_URL}/${id}`,
		});
	}

	/**
	 * 创建审计日志
	 * @param auditLog 审计日志数据
	 * @returns 创建的审计日志
	 */
	static createAuditLog(auditLog: Partial<AuditLog>): Promise<AuditLog> {
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

	static purgeAuditLogs(): Promise<{ removed: number }> {
		return apiClient.delete<{ removed: number }>({
			url: AuditLogService.BASE_URL,
		});
	}

	/** 获取审计模块分类（来自服务端审计目录） */
	static getAuditModules(): Promise<Array<{ key: string; title: string }>> {
		return apiClient.get<Array<{ key: string; title: string }>>({
			url: `${AuditLogService.BASE_URL}/modules`,
		});
	}

	/** 获取审计分类条目（模块下的细分条目，使用 entryKey/entryTitle） */
	static getAuditCategories(): Promise<Array<{ moduleKey: string; moduleTitle: string; entryKey: string; entryTitle: string }>> {
		return apiClient.get<Array<{ moduleKey: string; moduleTitle: string; entryKey: string; entryTitle: string }>>({
			url: `${AuditLogService.BASE_URL}/categories`,
		});
	}

	static async exportAuditLogs(filters: Record<string, string | number | boolean>): Promise<Blob> {
		const params = new URLSearchParams();
		Object.entries(filters).forEach(([key, value]) => {
			if (value !== undefined && value !== null && `${value}`.trim() !== "") {
				params.append(key, String(value));
			}
		});
		const base = GLOBAL_CONFIG.apiBaseUrl;
		const token = userStore.getState().userToken.accessToken;
		const url = `${base}${AuditLogService.BASE_URL}/export${params.toString() ? `?${params.toString()}` : ""}`;
		const response = await fetch(url, {
			headers: {
				Accept: "text/csv",
				...(token ? { Authorization: `Bearer ${token}` } : {}),
			},
		});
		if (!response.ok) {
			const text = await response.text();
			throw new Error(text || "导出审计日志失败");
		}
		return response.blob();
	}
}

export default AuditLogService;
