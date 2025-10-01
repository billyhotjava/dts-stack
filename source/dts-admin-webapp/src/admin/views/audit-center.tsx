import { useCallback, useContext, useMemo, useState } from "react";
import { AdminSessionContext } from "@/admin/lib/session-context";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

interface AuditLogEntry {
	id: string;
	timestamp: string;
	module: string;
	type?: string;
	action: string;
	operator: string;
	ip: string;
	result: "成功" | "失败";
	detail: string;
	resource?: string;
}

interface FilterState {
	from?: string;
	to?: string;
	module?: string;
	ip?: string;
	operator?: string;
	resource?: string;
	type?: string;
	adminRole?: string; // sysadmin | auditadmin
}

const OPERATOR_LABEL_MAP: Record<string, string> = {
	sysadmin: "系统管理员",
	authadmin: "授权管理员",
	auditadmin: "安全审计员",
	dba: "数据库管理员",
	dataops: "数据运维",
	"analytics.user": "业务分析员",
};

const ADMIN_OPERATOR_SET = new Set(["sysadmin", "authadmin", "auditadmin", "opadmin"]);

function isBusinessOperator(operator: string | undefined) {
	if (!operator) {
		return false;
	}
	return !ADMIN_OPERATOR_SET.has(operator.toLowerCase());
}

const auditLogSamples: AuditLogEntry[] = [
	{
		id: "L-202405-021",
		timestamp: "2024-05-14T08:35:00+08:00",
		module: "安全审计",
		action: "巡检完成 日志留存验证",
		operator: "auditadmin",
		ip: "10.10.7.6",
		result: "成功",
		detail: "完成凌晨巡检并确认日志留存完整可追溯。",
	},
	{
		id: "L-202405-022",
		timestamp: "2024-05-14T08:55:00+08:00",
		module: "安全审计",
		action: "触发风险告警复核",
		operator: "auditadmin",
		ip: "10.10.7.6",
		result: "成功",
		detail: "复核 AI 风控规则命中的异常登录告警，判定为误报。",
	},
	{
		id: "L-202405-023",
		timestamp: "2024-05-14T09:20:00+08:00",
		module: "安全审计",
		action: "导出审计报表",
		operator: "auditadmin",
		ip: "10.10.7.8",
		result: "成功",
		detail: "生成近 24 小时关键操作审计报表并发送至安全负责人。",
	},
	{
		id: "L-202405-001",
		timestamp: "2024-05-12T09:05:00+08:00",
		module: "用户管理",
		action: "创建用户 dataops",
		operator: "sysadmin",
		ip: "10.10.8.12",
		result: "成功",
		detail: "为数据平台组创建 dataops 账号",
	},
	{
		id: "L-202405-002",
		timestamp: "2024-05-12T09:08:00+08:00",
		module: "用户管理",
		action: "绑定角色 dataops → SYSADMIN",
		operator: "sysadmin",
		ip: "10.10.8.12",
		result: "成功",
		detail: "绑定系统管理员角色以便提交审批",
	},
	{
		id: "L-202405-003",
		timestamp: "2024-05-13T10:20:00+08:00",
		module: "用户管理",
		action: "停用用户 legacy.ops",
		operator: "sysadmin",
		ip: "10.10.8.13",
		result: "成功",
		detail: "停用长期未登录的运维账号",
	},
	{
		id: "L-202405-004",
		timestamp: "2024-05-11T08:55:00+08:00",
		module: "用户管理",
		action: "重置密码 finance.owner",
		operator: "sysadmin",
		ip: "10.10.8.11",
		result: "成功",
		detail: "为财务负责人重置登录密码",
	},
	{
		id: "L-202405-005",
		timestamp: "2024-05-12T10:15:00+08:00",
		module: "角色管理",
		action: "创建角色 DATA_STEWARD",
		operator: "sysadmin",
		ip: "10.10.8.11",
		result: "成功",
		detail: "新增数据管家角色并设置审批流程",
	},
	{
		id: "L-202405-006",
		timestamp: "2024-05-12T10:28:00+08:00",
		module: "角色管理",
		action: "更新权限 DATA_ANALYST",
		operator: "sysadmin",
		ip: "10.10.8.11",
		result: "成功",
		detail: "为数据分析师角色补充审批中心访问",
	},
	{
		id: "L-202405-007",
		timestamp: "2024-05-11T17:40:00+08:00",
		module: "角色管理",
		action: "删除角色 LEGACY_ADMIN",
		operator: "sysadmin",
		ip: "10.10.8.14",
		result: "失败",
		detail: "删除失败：缺少业务审批人确认",
	},
	{
		id: "L-202405-008",
		timestamp: "2024-05-11T18:05:00+08:00",
		module: "角色管理",
		action: "提交审批 角色权限调整",
		operator: "sysadmin",
		ip: "10.10.8.14",
		result: "成功",
		detail: "提交调整 DATA_ANALYST 权限的审批任务",
	},
	{
		id: "L-202405-009",
		timestamp: "2024-05-12T11:10:00+08:00",
		module: "菜单管理",
		action: "新增菜单 资产巡检",
		operator: "sysadmin",
		ip: "10.10.8.10",
		result: "成功",
		detail: "新增门户端资产巡检入口",
	},
	{
		id: "L-202405-010",
		timestamp: "2024-05-13T11:45:00+08:00",
		module: "菜单管理",
		action: "调整菜单顺序 数据地图",
		operator: "sysadmin",
		ip: "10.10.8.10",
		result: "成功",
		detail: "上移数据地图菜单以提升曝光",
	},
	{
		id: "L-202405-011",
		timestamp: "2024-05-09T16:40:00+08:00",
		module: "菜单管理",
		action: "删除菜单 旧版日志中心",
		operator: "sysadmin",
		ip: "10.10.8.15",
		result: "失败",
		detail: "审批被驳回：仍有历史系统依赖",
	},
	{
		id: "L-202405-012",
		timestamp: "2024-05-09T19:00:00+08:00",
		module: "菜单管理",
		action: "同步审批结果 旧版日志中心",
		operator: "authadmin",
		ip: "10.10.9.8",
		result: "成功",
		detail: "记录审批驳回原因并通知 sysadmin",
	},
	{
		id: "L-202405-013",
		timestamp: "2024-05-10T13:35:00+08:00",
		module: "任务审批",
		action: "审批通过 角色权限调整",
		operator: "authadmin",
		ip: "10.10.9.8",
		result: "成功",
		detail: "审批通过 DATA_ANALYST 权限调整任务",
	},
	{
		id: "L-202405-014",
		timestamp: "2024-05-10T13:50:00+08:00",
		module: "任务审批",
		action: "驳回 菜单删除任务",
		operator: "authadmin",
		ip: "10.10.9.8",
		result: "成功",
		detail: "驳回删除旧版日志中心菜单的申请",
	},
	{
		id: "L-202405-015",
		timestamp: "2024-05-13T09:10:00+08:00",
		module: "任务审批",
		action: "领取任务 菜单调整",
		operator: "authadmin",
		ip: "10.10.9.9",
		result: "成功",
		detail: "领取 sysadmin 提交的菜单调整审批",
	},
	{
		id: "L-202405-016",
		timestamp: "2024-05-13T09:45:00+08:00",
		module: "任务审批",
		action: "审批通过 菜单调整",
		operator: "authadmin",
		ip: "10.10.9.9",
		result: "成功",
		detail: "同意调整数据地图菜单显示顺序",
	},
	{
		id: "L-202405-017",
		timestamp: "2024-05-08T21:05:00+08:00",
		module: "系统运维",
		action: "更新配置 airflow.deployment",
		operator: "sysadmin",
		ip: "10.10.8.20",
		result: "成功",
		detail: "更新调度集群版本号至 v2.9.0",
	},
	{
		id: "L-202405-018",
		timestamp: "2024-05-08T21:15:00+08:00",
		module: "系统运维",
		action: "导出审计日志",
		operator: "auditadmin",
		ip: "10.10.7.6",
		result: "成功",
		detail: "导出最近 7 天的审计日志数据",
	},
	{
		id: "L-202405-019",
		timestamp: "2024-05-07T08:30:00+08:00",
		module: "系统运维",
		action: "执行集群巡检",
		operator: "auditadmin",
		ip: "10.10.7.6",
		result: "成功",
		detail: "完成数据平台晨检并生成报告",
	},
	{
		id: "L-202405-020",
		timestamp: "2024-05-07T09:05:00+08:00",
		module: "系统运维",
		action: "登录失败 auditadmin",
		operator: "auditadmin",
		ip: "10.10.7.6",
		result: "失败",
		detail: "口令输入错误连续三次触发告警",
	},
	{
		id: "L-202405-024",
		timestamp: "2024-05-13T15:22:00+08:00",
		module: "数据资产",
		action: "订阅数据集 ods_orders",
		operator: "analytics.user",
		ip: "10.10.18.15",
		result: "成功",
		detail: "业务分析员订阅月度订单指标数据集",
		resource: "/portal/datasets/ods_orders",
		type: "订阅",
	},
	{
		id: "L-202405-025",
		timestamp: "2024-05-13T16:05:00+08:00",
		module: "数据资产",
		action: "导出数据集 ods_orders",
		operator: "analytics.user",
		ip: "10.10.18.15",
		result: "成功",
		detail: "导出订单数据供业务会议分析",
		resource: "/portal/datasets/ods_orders/export",
		type: "导出",
	},
	{
		id: "L-202405-026",
		timestamp: "2024-05-13T17:45:00+08:00",
		module: "审批中心",
		action: "提交审批 数据资产导出",
		operator: "dataops",
		ip: "10.10.18.22",
		result: "成功",
		detail: "提交业务数据导出审批请求",
		resource: "/portal/approval/requests/823",
		type: "提交",
	},
	{
		id: "L-202405-027",
		timestamp: "2024-05-14T09:45:00+08:00",
		module: "审批中心",
		action: "审批通过 数据资产导出",
		operator: "authadmin",
		ip: "10.10.9.11",
		result: "成功",
		detail: "授权管理员批准数据导出申请",
		resource: "/admin/approval/detail/823",
		type: "审批",
	},
	{
		id: "L-202405-028",
		timestamp: "2024-05-14T11:12:00+08:00",
		module: "登录管理",
		action: "业务用户登录成功",
		operator: "dataops",
		ip: "10.10.18.22",
		result: "成功",
		detail: "业务运维人员登录业务门户",
		resource: "/portal/login",
		type: "登录",
	},
	{
		id: "L-202405-029",
		timestamp: "2024-05-14T11:18:00+08:00",
		module: "登录管理",
		action: "数据库管理员登录失败",
		operator: "dba",
		ip: "10.10.30.12",
		result: "失败",
		detail: "数据库管理员口令过期导致登录失败",
		resource: "/portal/login",
		type: "登录",
	},
];

export default function AuditCenterView() {
	const sessionContext = useContext(AdminSessionContext);
	const session = sessionContext ?? { role: null as unknown as string };
	const [filters, setFilters] = useState<FilterState>({});
	const [exporting, setExporting] = useState(false);

	const moduleOptions = useMemo(() => {
		return Array.from(new Set(auditLogSamples.map((item) => item.module)));
	}, []);

	const scopedLogs = useMemo(() => {
		const role = sessionContext?.role?.toUpperCase();
		switch (role) {
			case "AUTHADMIN":
				return auditLogSamples.filter((item) =>
					["sysadmin", "auditadmin"].includes((item.operator || "").toLowerCase()),
				);
			case "AUDITADMIN":
				return auditLogSamples.filter((item) => {
					const operator = (item.operator || "").toLowerCase();
					if (operator === "auditadmin") {
						return false;
					}
					if (["sysadmin", "authadmin"].includes(operator)) {
						return true;
					}
					return isBusinessOperator(operator);
				});
			default:
				return auditLogSamples;
		}
	}, [sessionContext?.role]);

	const filteredLogs = useMemo(() => {
		const fromTime = filters.from ? new Date(filters.from).getTime() : NaN;
		const toTime = filters.to ? new Date(filters.to).getTime() : NaN;
		const hasFrom = !Number.isNaN(fromTime);
		const hasTo = !Number.isNaN(toTime);
		const moduleFilter = filters.module?.trim();
		const ipFilter = filters.ip?.trim();
		const operatorFilter = filters.operator?.trim();
		const resourceFilter = filters.resource?.trim();
		const typeFilter = filters.type?.trim();
		const adminRoleFilter = filters.adminRole?.trim();

		return scopedLogs.filter((item) => {
			const timestamp = new Date(item.timestamp).getTime();
			if (hasFrom && timestamp < fromTime) {
				return false;
			}
			if (hasTo && timestamp > toTime) {
				return false;
			}
			if (moduleFilter && item.module !== moduleFilter) {
				return false;
			}
			if (ipFilter && !item.ip.includes(ipFilter)) {
				return false;
			}
			if (operatorFilter && !item.operator.includes(operatorFilter)) {
				return false;
			}
			if (resourceFilter && !(item.resource || "").includes(resourceFilter)) {
				return false;
			}
			if (typeFilter && (item.type || "") !== typeFilter) {
				return false;
			}
			if (adminRoleFilter) {
				if (adminRoleFilter === "business") {
					if (["sysadmin", "authadmin", "auditadmin"].includes((item.operator || "").toLowerCase())) {
						return false;
					}
				} else if ((item.operator || "").toLowerCase() !== adminRoleFilter.toLowerCase()) {
					return false;
				}
			}
			return true;
		});
	}, [filters, scopedLogs]);

	const handleExport = useCallback(() => {
		if (filteredLogs.length === 0) {
			toast.error("当前条件下没有可导出的日志记录");
			return;
		}
		setExporting(true);
		window.setTimeout(() => {
			const csv = buildCsvContent(filteredLogs, filters);
			const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
			const url = URL.createObjectURL(blob);
			const link = document.createElement("a");
			link.href = url;
			link.download = `audit-logs-${Date.now()}.csv`;
			document.body.appendChild(link);
			link.click();
			document.body.removeChild(link);
			URL.revokeObjectURL(url);
			toast.success("已导出筛选后的审计日志");
			setExporting(false);
		}, 600);
	}, [filteredLogs, filters]);

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader className="space-y-2">
					<CardTitle>查询条件</CardTitle>
					<Text variant="body3" className="text-muted-foreground">
						支持按时间范围、功能模块、操作人、目标位置与类型筛选审计记录。
					</Text>
				</CardHeader>
				<CardContent className="space-y-4">
					<div className="grid gap-4 md:grid-cols-2 xl:grid-cols-6">
						<div className="space-y-2">
							<Text variant="body3" className="text-muted-foreground">
								起始时间
							</Text>
							<Input
								type="datetime-local"
								value={filters.from ?? ""}
								onChange={(event) => setFilters((prev) => ({ ...prev, from: event.target.value || undefined }))}
							/>
						</div>
						<div className="space-y-2">
							<Text variant="body3" className="text-muted-foreground">
								终止时间
							</Text>
							<Input
								type="datetime-local"
								value={filters.to ?? ""}
								onChange={(event) => setFilters((prev) => ({ ...prev, to: event.target.value || undefined }))}
							/>
						</div>
						<div className="space-y-2">
							<Text variant="body3" className="text-muted-foreground">
								功能模块
							</Text>
							<select
								className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm"
								value={filters.module ?? ""}
								onChange={(event) =>
									setFilters((prev) => ({
										...prev,
										module: event.target.value || undefined,
									}))
								}
							>
								<option value="">全部模块</option>
								{moduleOptions.map((option) => (
									<option key={option} value={option}>
										{option}
									</option>
								))}
							</select>
						</div>
						<div className="space-y-2">
							<Text variant="body3" className="text-muted-foreground">
								操作人
							</Text>
							<Input
								placeholder="如 sysadmin / alice"
								value={filters.operator ?? ""}
								onChange={(event) => setFilters((prev) => ({ ...prev, operator: event.target.value || undefined }))}
							/>
						</div>
						<div className="space-y-2">
							<Text variant="body3" className="text-muted-foreground">
								目标位置 URI
							</Text>
							<Input
								placeholder="例如 /admin/approval"
								value={filters.resource ?? ""}
								onChange={(event) => setFilters((prev) => ({ ...prev, resource: event.target.value || undefined }))}
							/>
						</div>
						<div className="space-y-2">
							<Text variant="body3" className="text-muted-foreground">
								IP 地址
							</Text>
							<Input
								placeholder="例如 10.10."
								value={filters.ip ?? ""}
								onChange={(event) => setFilters((prev) => ({ ...prev, ip: event.target.value || undefined }))}
							/>
						</div>
						<div className="space-y-2">
							<Text variant="body3" className="text-muted-foreground">
								类型
							</Text>
							<Input
								placeholder="如 创建/更新/批准"
								value={filters.type ?? ""}
								onChange={(event) => setFilters((prev) => ({ ...prev, type: event.target.value || undefined }))}
							/>
						</div>
					</div>
					<div className="flex flex-wrap gap-3">
						<Button type="button" variant="outline" onClick={() => setFilters({})}>
							重置条件
						</Button>
					</div>
				</CardContent>
			</Card>

			<Card>
				<CardHeader className="space-y-2 sm:flex sm:items-center sm:justify-between sm:space-y-0">
					<div>
						<CardTitle>日志记录</CardTitle>
						<Text variant="body3" className="text-muted-foreground">
							当前展示 {filteredLogs.length} / {auditLogSamples.length} 条记录。
						</Text>
					</div>
					<div className="flex items-center gap-2">
						{(() => {
							const role = (session.role || "").toUpperCase();
							const items: { value: string; label: string }[] = [{ value: "", label: "全部角色" }];
							if (role === "AUTHADMIN") {
								items.push({ value: "sysadmin", label: "系统管理员" });
								items.push({ value: "auditadmin", label: "安全审计员" });
							} else if (role === "AUDITADMIN") {
								items.push({ value: "sysadmin", label: "系统管理员" });
								items.push({ value: "authadmin", label: "授权管理员" });
								items.push({ value: "business", label: "业务用户" });
							} else {
								// 其他默认提供常见两类
								items.push({ value: "sysadmin", label: "系统管理员" });
								items.push({ value: "auditadmin", label: "安全审计员" });
							}
							return (
								<select
									className="h-9 rounded-md border border-border bg-background px-2 text-sm"
									value={filters.adminRole ?? ""}
									onChange={(e) => setFilters((prev) => ({ ...prev, adminRole: e.target.value || undefined }))}
									title="按角色筛选"
								>
									{items.map((opt) => (
										<option key={opt.value || "all"} value={opt.value}>
											{opt.label}
										</option>
									))}
								</select>
							);
						})()}
						<Button type="button" onClick={handleExport} disabled={exporting}>
							{exporting ? "正在导出..." : "导出日志"}
						</Button>
					</div>
				</CardHeader>
				<CardContent className="overflow-x-auto">
					<table className="min-w-full table-fixed text-sm">
						<thead className="bg-muted/60">
							<tr className="text-left">
								<th className="px-4 py-3 font-medium">日志编号</th>
								<th className="px-4 py-3 font-medium">操作时间</th>
								<th className="px-4 py-3 font-medium">功能模块</th>
								<th className="px-4 py-3 font-medium">类型</th>
								<th className="px-4 py-3 font-medium">操作详情</th>
								<th className="px-4 py-3 font-medium">操作者 / IP</th>
								<th className="px-4 py-3 font-medium">目标位置</th>
								<th className="px-4 py-3 font-medium">结果</th>
							</tr>
						</thead>
						<tbody>
							{filteredLogs.length === 0 ? (
								<tr>
									<td colSpan={6} className="px-4 py-6 text-center text-sm text-muted-foreground">
										暂无符合条件的日志记录。
									</td>
								</tr>
							) : (
								filteredLogs.map((log) => (
									<tr key={log.id} className="border-b align-top last:border-b-0">
										<td className="px-4 py-3 font-medium">{log.id}</td>
										<td className="px-4 py-3 text-sm">
											<div>{formatDateTime(log.timestamp)}</div>
										</td>
										<td className="px-4 py-3">{log.module}</td>
										<td className="px-4 py-3">{log.type || "-"}</td>
										<td className="px-4 py-3 text-sm">
											<div className="font-medium">{log.action}</div>
											<div className="text-xs text-muted-foreground">{log.detail}</div>
										</td>
										<td className="px-4 py-3 text-xs text-muted-foreground">
											<div>操作者：{formatOperatorName(log.operator)}</div>
											<div>IP：{log.ip}</div>
										</td>
										<td className="px-4 py-3 text-xs text-muted-foreground">{log.resource || "-"}</td>
										<td className="px-4 py-3">
											<Badge variant={log.result === "失败" ? "destructive" : "secondary"}>{log.result}</Badge>
										</td>
									</tr>
								))
							)}
						</tbody>
					</table>
				</CardContent>
			</Card>
		</div>
	);
}

function formatDateTime(value: string) {
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value;
	}
	return date.toLocaleString("zh-CN", { hour12: false });
}

function formatOperatorName(value: string) {
	const normalized = value?.toLowerCase?.();
	const label = normalized
		? OPERATOR_LABEL_MAP[normalized as keyof typeof OPERATOR_LABEL_MAP]
		: undefined;
	if (label) {
		return `${label}（${value}）`;
	}
	if (isBusinessOperator(normalized)) {
		return `业务用户（${value}）`;
	}
	return value;
}

function buildCsvContent(logs: AuditLogEntry[], filters: FilterState) {
	const lines: string[] = [];
	const exportedAt = formatDateTime(new Date().toISOString());
	const filterSummary = `时间范围：${
		filters.from ? `${formatDateTime(filters.from)} 起` : "全部"
	} → ${filters.to ? `${formatDateTime(filters.to)} 止` : "全部"}；模块：${filters.module ?? "全部"}；操作人：${
		filters.operator ?? "全部"
	}；角色：${filters.adminRole ?? "全部"}；类型：${filters.type ?? "全部"}；URI：${filters.resource ?? "全部"}`;
	lines.push(`# 导出时间：${exportedAt}`);
	lines.push(`# 筛选条件：${filterSummary}`);
	lines.push("");
	lines.push("日志编号,操作时间,功能模块,类型,操作详情,操作者,IP,目标位置,结果,说明");
	for (const log of logs) {
		lines.push(
			[
				csvEscape(log.id),
				csvEscape(formatDateTime(log.timestamp)),
				csvEscape(log.module),
				csvEscape(log.type || ""),
				csvEscape(log.action),
				csvEscape(formatOperatorName(log.operator)),
				csvEscape(log.ip),
				csvEscape(log.resource || ""),
				csvEscape(log.result),
				csvEscape(log.detail),
			].join(","),
		);
	}
	return lines.join("\n");
}

function csvEscape(value: string) {
	if (value.includes('"') || value.includes(",") || value.includes("\n")) {
		return `"${value.replace(/"/g, '""')}"`;
	}
	return value;
}
