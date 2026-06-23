import type { AccessRequest, AccessToken, AlertEvent, ApiService, AuditEvent, BiLink, OpsJob } from "@/types/platform";

export const SEED_API_SERVICES: ApiService[] = [
	{ id: "api-1", name: "质量月报查询", method: "GET", path: "/api/quality/monthly", departmentId: "dept-quality", status: "online", calls: 12840 },
	{ id: "api-2", name: "销售宽表查询", method: "GET", path: "/api/sales/wide", departmentId: "dept-sales", status: "offline", calls: 0 },
	{ id: "api-3", name: "合格率指标", method: "GET", path: "/api/metrics/quality_pass_rate", departmentId: "dept-quality", status: "online", calls: 4521 },
];

export const SEED_TOKENS: AccessToken[] = [
	{ id: "tk-1", name: "驾驶舱只读令牌", scope: "metrics:read", createdAt: "2026-05-10", lastUsed: "2026-06-23", status: "active" },
	{ id: "tk-2", name: "BI 工具令牌", scope: "dataset:read", createdAt: "2026-04-20", lastUsed: "2026-06-22", status: "active" },
	{ id: "tk-3", name: "临时调试令牌", scope: "*", createdAt: "2026-06-01", status: "revoked" },
];

export const SEED_BI_LINKS: BiLink[] = [
	{ id: "bi-1", name: "质量月报看板", tool: "Superset", url: "https://bi/quality-monthly", departmentId: "dept-quality" },
	{ id: "bi-2", name: "领导驾驶舱", tool: "内置大屏", url: "/screen/leader", departmentId: "dept-quality" },
];

export const SEED_AUDIT_EVENTS: AuditEvent[] = [
	{ id: "au-1", at: "2026-06-23 14:02", actor: "测试用户", action: "发布数据集", resource: "ads_quality_monthly", result: "success" },
	{ id: "au-2", at: "2026-06-23 11:20", actor: "测试用户", action: "连通测试", resource: "销售本地台账", result: "deny" },
	{ id: "au-3", at: "2026-06-22 09:33", actor: "网信中心", action: "授权访问", resource: "dwd_quality_inspect → 销售处", result: "success" },
	{ id: "au-4", at: "2026-06-21 16:48", actor: "测试用户", action: "运行转换", resource: "销售准备/整图", result: "success" },
];

export const SEED_ACCESS_REQUESTS: AccessRequest[] = [
	{ id: "ar-1", dataset: "ads_quality_monthly", requester: "测试用户", requesterDept: "销售处", status: "pending", at: "2026-06-23" },
	{ id: "ar-2", dataset: "dwd_quality_inspect", requester: "网信中心", requesterDept: "网信中心", status: "approved", at: "2026-06-19" },
];

export const SEED_OPS_JOBS: OpsJob[] = [
	{ id: "job-1", name: "质量月报采集", type: "采集", status: "success", lastRun: "2026-06-23 02:00", durationMs: 48200 },
	{ id: "job-2", name: "销售宽表转换", type: "转换", status: "running", lastRun: "2026-06-23 15:30" },
	{ id: "job-3", name: "合格率指标发布", type: "发布", status: "success", lastRun: "2026-06-23 03:10", durationMs: 6100 },
	{ id: "job-4", name: "PLM 增量采集", type: "采集", status: "failed", lastRun: "2026-06-23 01:00", durationMs: 12000 },
	{ id: "job-5", name: "ERP 全量采集", type: "采集", status: "queued", lastRun: "—" },
];

export const SEED_ALERTS: AlertEvent[] = [
	{ id: "al-1", at: "2026-06-23 01:05", level: "error", source: "PLM 增量采集", message: "连接超时，任务失败" },
	{ id: "al-2", at: "2026-06-23 11:20", level: "warn", source: "销售本地台账", message: "连通测试失败：未配置连接信息" },
	{ id: "al-3", at: "2026-06-22 22:00", level: "info", source: "调度器", message: "夜间批处理开始" },
];
