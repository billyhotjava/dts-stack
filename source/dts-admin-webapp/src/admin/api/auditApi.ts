import apiClient from "@/api/apiClient";
import type { AuditEvent } from "../types";

/**
 * 查询审计日志列表
 * @param filters 查询过滤条件
 * @returns 审计日志列表
 */
export async function fetchAuditEvents(filters?: {
	from?: string;
	to?: string;
	module?: string;
	ip?: string;
}): Promise<AuditEvent[]> {
	// 转换过滤条件为 URL 查询参数
	const params = new URLSearchParams();
	if (filters?.from) {
		params.set("from", filters.from);
	}
	if (filters?.to) {
		params.set("to", filters.to);
	}
	if (filters?.module) {
		params.set("action", filters.module); // 使用 action 查询参数匹配功能模块
	}
	if (filters?.ip) {
		const ipDetails = JSON.stringify({ ip: filters.ip });
		params.set("detailJson", ipDetails); // 使用 detailJson 查询 IP 地址
	}

	return apiClient.get<AuditEvent[]>({
		url: `/api/admin/audit?${params.toString()}`,
	});
}

/**
 * 导出审计日志数据
 * @param format 导出格式，支持 csv 和 json
 * @returns 导出文件的 Blob 对象
 */
export async function exportAuditLogs(format: "csv" | "json" = "csv"): Promise<Blob> {
	const response = await apiClient.get<Blob>({
		url: `/api/admin/audit/export?format=${format}`,
		responseType: "blob",
	});
	return response;
}
