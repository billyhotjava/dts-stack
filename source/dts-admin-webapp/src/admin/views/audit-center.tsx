import { useCallback, useEffect, useMemo, useState, type ChangeEvent, type ReactNode } from "react";
import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { Calendar as CalendarIcon, Clock3 } from "lucide-react";
import { toast } from "sonner";
import { AuditLogService } from "@/api/services/auditLogService";
import type { AuditLog, AuditLogPageResponse, AuditLogDetail } from "#/entity";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Calendar } from "@/ui/calendar";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/ui/popover";
import { Text } from "@/ui/typography";
import { cn } from "@/utils";
import {
	ChangeDiffViewer,
	type ChangeSnapshotLike,
	type ChangeSummaryEntry,
} from "@/admin/components/change-diff-viewer";
import { MenuChangeViewer, type MenuChangeDisplayEntry } from "@/admin/components/menu-change-viewer";
import { buildChangeDisplayContext } from "@/admin/utils/change-detail-context";
import {
	extractMenuChanges,
	snapshotHasContent,
} from "@/admin/utils/menu-change-parser";

interface FilterState {
	from?: string;
	to?: string;
	sourceSystem?: string;
	module?: string;
	operationGroup?: string;
	actor?: string;
	resource?: string;
	clientIp?: string;
	action?: string;
}

// Removed unused types (AuditModuleCatalog, AuditCategoryItem) to satisfy strict TS settings

const ADMIN_LABELS: Record<string, string> = {
	sysadmin: "系统管理员",
	authadmin: "授权管理员",
	auditadmin: "安全审计员",
	opadmin: "运维管理员",
};

const MODULE_LABEL_OVERRIDES: Record<string, string> = {
	"admin.approvals": "任务审批",
	"admin.audit": "审计日志",
	"admin.audit-logs": "审计日志",
	"admin.auth": "认证登录",
	"admin.change-requests": "变更请求",
	"admin.custom-roles": "自定义角色",
	"admin.datasets": "资产视图",
	"admin.groups": "用户组",
	"admin.orgs": "组织管理",
	"admin.portal-menus": "门户菜单",
	"admin.role-assignments": "角色分配",
	"admin.roles": "角色管理",
	"admin.settings": "系统配置",
	"admin.users": "用户管理",
	"api.execute": "API 执行",
	"api.publish": "API 发布",
	"api.test": "API 调试",
	"approval-requests": "审批请求",
	"audit-logs": "审计日志",
	"catalog.assets": "数据资产",
	"catalog.classificationmapping": "分类映射",
	"catalog.column": "字段管理",
	"catalog.config": "资产配置",
	"catalog.dataset": "数据资产",
	"catalog.dataset.grant": "资产授权",
	"catalog.dataset.import": "资产导入",
	"catalog.domain": "资产目录",
	"catalog.domain.move": "目录调整",
	"catalog.domain.tree": "目录树",
	"catalog.masking": "脱敏策略",
	"catalog.masking.preview": "脱敏预览",
	"catalog.rowfilter": "行级过滤",
	"catalog.summary": "资产概览",
	"catalog.table": "数据表",
	"catalog.table.import": "表导入",
	"dashboard.list": "仪表盘列表",
	"dataset.schema": "数据模型",
	directory: "目录管理",
	"etl.job": "集成任务",
	"etl.run.status": "任务运行状态",
	"explore.execute": "执行查询",
	"explore.execution": "查询执行",
	"explore.explain": "执行计划",
	"explore.preview": "数据预览",
	"explore.result.cleanup": "结果清理",
	"explore.result.delete": "结果删除",
	"explore.result.save": "结果保存",
	"explore.result.sql": "SQL 结果",
	"explore.resultpreview": "结果预览",
	"explore.resultset": "结果集",
	"explore.resultset.cleanup": "结果集清理",
	"explore.savedqueries": "已保存查询",
	"explore.savedquery": "已保存查询",
	"explore.savedquery.run": "查询运行",
	"explore.saveresult": "保存结果",
	"explore.workbench": "数据开发工作台",
	"foundation.datasources": "数据源",
	"foundation.datastorage": "存储管理",
	"foundation.taskscheduling": "任务调度",
	"governance.compliance": "合规检查",
	"governance.compliance.batch": "合规批次",
	"governance.compliance.item": "合规事项",
	"governance.issue": "治理问题",
	"governance.issue.action": "问题处理",
	"governance.issue.close": "问题关闭",
	"governance.quality.run": "质量运行",
	"governance.rule": "质量规则",
	"governance.rule.toggle": "规则开关",
	"governance.rules": "质量规则",
	"iam.authorization": "权限授权",
	"iam.classification": "分类权限",
	"iam.permission": "权限策略",
	"iam.policy": "授权策略",
	"iam.policy.apply": "策略应用",
	"iam.policy.dataset": "数据集授权",
	"iam.policy.preview": "策略预览",
	"iam.policy.subject": "授权主体",
	"iam.policy.subjects": "授权主体",
	"iam.request": "权限申请",
	"iam.request.approve": "申请审批",
	"iam.request.reject": "申请驳回",
	"iam.requests": "权限申请",
	"iam.simulate": "权限模拟",
	"iam.simulation": "权限模拟",
	"infra.datasource": "数据源",
	"infra.datasource.inceptor": "Inceptor 数据源",
	"infra.datasource.postgres": "Postgres 数据源",
	"infra.datasource.testlogs": "数据源测试日志",
	"infra.datastorage": "存储配置",
	"infra.schedule": "调度中心",
	"modeling.standards": "数据标准",
	"portal.navigation": "门户导航",
	"services.api": "API 服务",
	"services.products": "数据产品",
	"services.tokens": "服务凭证",
	"sql.query": "SQL 查询",
	"svc.api": "API 服务",
	"svc.api.metrics": "API 指标",
	"svc.api.try": "API 调试",
	"svc.dataproduct": "数据产品",
	"svc.token": "服务令牌",
	"vis.cockpit": "可视化驾驶舱",
	"vis.dashboards": "仪表盘",
	"vis.finance": "财务看板",
	"vis.hr": "人力看板",
	"vis.projects": "项目看板",
	"vis.supply": "供应链看板",
	"visualization.cockpit": "驾驶舱",
	"visualization.dashboards": "仪表盘",
	"visualization.finance": "财务看板",
	"visualization.hr": "人力看板",
	"visualization.projects": "项目看板",
	"visualization.supplychain": "供应链看板",
};

const MODULE_TOKEN_TRANSLATIONS: Record<string, string> = {
	action: "操作",
	admin: "管理",
	api: "API",
	apply: "应用",
	approval: "审批",
	approvals: "审批",
	approve: "审批",
	assets: "资产",
	assignments: "分配",
	attachment: "附件",
	audit: "审计",
	auth: "认证",
	authorization: "授权",
	batch: "批次",
	catalog: "目录",
	change: "变更",
	classification: "分类",
	classificationmapping: "分类映射",
	cleanup: "清理",
	close: "关闭",
	cockpit: "驾驶舱",
	column: "字段",
	compliance: "合规",
	config: "配置",
	custom: "自定义",
	dashboard: "仪表盘",
	dashboards: "仪表盘",
	dataproduct: "数据产品",
	dataset: "数据集",
	datasets: "数据集",
	datasource: "数据源",
	datasources: "数据源",
	datastorage: "存储",
	delete: "删除",
	directory: "目录",
	domain: "领域",
	etl: "集成",
	execute: "执行",
	execution: "执行",
	explain: "分析",
	explore: "探索",
	finance: "财务",
	foundation: "基础",
	governance: "治理",
	grant: "授权",
	groups: "用户组",
	hr: "人力",
	iam: "权限",
	import: "导入",
	inceptor: "Inceptor",
	infra: "基础设施",
	issue: "问题",
	item: "条目",
	job: "任务",
	list: "列表",
	logs: "日志",
	masking: "脱敏",
	menus: "菜单",
	metrics: "指标",
	modeling: "模型",
	move: "移动",
	navigation: "导航",
	orgs: "组织",
	permission: "权限",
	policy: "策略",
	portal: "门户",
	postgres: "Postgres",
	preview: "预览",
	products: "产品",
	projects: "项目",
	publish: "发布",
	quality: "质量",
	query: "查询",
	reject: "驳回",
	request: "请求",
	requests: "请求",
	result: "结果",
	resultpreview: "结果预览",
	resultset: "结果集",
	role: "角色",
	roles: "角色",
	rowfilter: "行过滤",
	rule: "规则",
	rules: "规则",
	run: "运行",
	save: "保存",
	savedqueries: "已保存查询",
	savedquery: "已保存查询",
	saveresult: "保存结果",
	schedule: "调度",
	schema: "模型",
	services: "服务",
	settings: "设置",
	simulate: "模拟",
	simulation: "模拟",
	sql: "SQL",
	standard: "标准",
	standards: "标准",
	status: "状态",
	subject: "主体",
	subjects: "主体",
	summary: "概览",
	supply: "供应",
	supplychain: "供应链",
	svc: "服务",
	table: "数据表",
	taskscheduling: "任务调度",
	test: "测试",
	testlogs: "测试日志",
	toggle: "切换",
	token: "令牌",
	tokens: "令牌",
	tree: "树",
	try: "调试",
	users: "用户",
	version: "版本",
	vis: "可视化",
	visualization: "可视化",
	workbench: "工作台",
};

const DEFAULT_PAGE_SIZE = 20;
const MODULE_FILTER_ENABLED = false;

function translateModuleLabel(key?: string, fallback?: string): string {
	const normalizedKey = key?.trim().toLowerCase();
	const normalizedFallback = fallback?.trim();
	if (!normalizedKey) {
		return normalizedFallback || "-";
	}
	const override = MODULE_LABEL_OVERRIDES[normalizedKey];
	if (override) {
		return override;
	}
	const shouldTranslate = !normalizedFallback || normalizedFallback.toLowerCase() === normalizedKey;
	if (!shouldTranslate) {
		return normalizedFallback ?? normalizedKey;
	}
	const tokens = normalizedKey.split(/[./-]/).filter(Boolean);
	if (tokens.length > 0) {
		const translated: string[] = [];
		for (const token of tokens) {
			const mapped = MODULE_TOKEN_TRANSLATIONS[token];
			if (!mapped) {
				return normalizedFallback ?? key ?? "-";
			}
			if (translated[translated.length - 1] !== mapped) {
				translated.push(mapped);
			}
		}
		if (translated.length > 0) {
			return translated.join("");
		}
	}
	return normalizedFallback ?? key ?? "-";
}

export default function AuditCenterView() {
	const [filters, setFilters] = useState<FilterState>({});
	const [logs, setLogs] = useState<AuditLog[]>([]);
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
	const [totalElements, setTotalElements] = useState(0);
	const [exporting, setExporting] = useState(false);
	const [rowDetails, setRowDetails] = useState<Record<number, AuditLogDetail | null>>({});
	const [rowLoading, setRowLoading] = useState<Record<number, boolean>>({});
	const [moduleOptions, setModuleOptions] = useState<Array<{ value: string; label: string }>>([
		{ value: "", label: "全部模块" },
	]);
	const [groupOptions, setGroupOptions] = useState<Array<{ value: string; label: string }>>([
		{ value: "", label: "全部功能分组" },
	]);

	const loadLogs = useCallback(
		async (nextPage: number, nextSize: number) => {
			setLoading(true);
			setPage(nextPage);
			setSize(nextSize);
			try {
				const params = buildQuery(filters);
				const response: AuditLogPageResponse = await AuditLogService.getAuditLogs(
					nextPage,
					nextSize,
					"occurredAt,desc",
					params,
				);
				setLogs(response.content);
				setPage(response.page);
				setSize(response.size);
				setTotalElements(response.totalElements);
				// totalPages is not used in UI; skip storing
				// Note: 功能模块下拉仅使用审计目录（/audit-logs/modules），不再从日志内容动态补全，
				// 以确保不同角色（含 auditadmin）看到一致的大类选项。
			} catch (error) {
				console.error("Failed to load audit logs", error);
				toast.error("加载审计日志失败");
			} finally {
				setLoading(false);
			}
		},
		[filters],
	);

	useEffect(() => {
		loadLogs(page, size);
	}, [loadLogs, page, size]);

	useEffect(() => {
		if (!MODULE_FILTER_ENABLED) {
			return;
		}
		AuditLogService.getAuditModules()
			.then((modules) => {
				const mapped = modules.map((item) => {
					const rawKey = item.key?.trim() ?? "";
					const rawTitle = item.title?.trim();
					const value = rawKey || rawTitle || "";
					const label = translateModuleLabel(rawKey, rawTitle);
					return { value, label };
				});
				setModuleOptions([{ value: "", label: "全部模块" }, ...mapped]);
			})
			.catch((error) => {
				console.error("Failed to load module catalog", error);
				toast.error("加载模块字典失败");
			});
	}, []);

	useEffect(() => {
	AuditLogService.getAuditGroups()
		.then((groups) => {
			const seen = new Set<string>();
			const orderLabels = ["系统管理", "业务管理"];
			const itemsWithMeta: Array<{
				value: string;
				label: string;
				sourceLabel: string;
				order: number;
			}> = [];
			for (const item of groups) {
				const rawKey = (item.key ?? "").trim();
				const rawTitle = (item.title ?? "").trim();
				if (!rawKey || seen.has(rawKey)) {
					continue;
				}
				seen.add(rawKey);
				const label = rawTitle || rawKey;
				const sourceLabelRaw =
					(item as Record<string, unknown>).sourceSystemLabel ??
					(item as Record<string, unknown>).sourceLabel ??
					"";
				const normalizedSource =
					typeof sourceLabelRaw === "string" && sourceLabelRaw.trim().length > 0
						? sourceLabelRaw.trim()
						: orderLabels.find((prefix) => label.startsWith(prefix)) ?? "其他";
				itemsWithMeta.push({
					value: rawKey,
					label,
					sourceLabel: normalizedSource,
					order: itemsWithMeta.length,
				});
			}
			const prioritized: Array<{ value: string; label: string }> = [];
			const usedValues = new Set<string>();
			const pushBucket = (bucketLabel: string) => {
				itemsWithMeta
					.filter((entry) => entry.sourceLabel === bucketLabel)
					.sort((a, b) => a.order - b.order)
					.forEach((entry) => {
						if (usedValues.has(entry.value)) {
							return;
						}
						prioritized.push({ value: entry.value, label: entry.label });
						usedValues.add(entry.value);
					});
			};
			orderLabels.forEach((label) => pushBucket(label));
			itemsWithMeta
				.filter((entry) => !usedValues.has(entry.value))
				.sort((a, b) => a.order - b.order)
				.forEach((entry) => {
					prioritized.push({ value: entry.value, label: entry.label });
					usedValues.add(entry.value);
				});
			setGroupOptions([{ value: "", label: "全部功能分组" }, ...prioritized]);
		})
		.catch((error) => {
			console.error("Failed to load audit groups", error);
			toast.error("加载功能模块分组失败");
		});
	}, []);

	const handleRefresh = useCallback(() => {
		loadLogs(0, size);
	}, [loadLogs, size]);

	const handleExport = useCallback(async () => {
		try {
			setExporting(true);
			const params = buildQuery(filters);
			const query = new URLSearchParams(params).toString();
			const exportUrl = buildExportUrl(query);
			const token = resolveAccessToken();
			const response = await fetch(exportUrl, {
				headers: token ? { Authorization: `Bearer ${token}` } : undefined,
			});
			if (!response.ok) {
				throw new Error(`导出失败: ${response.status}`);
			}
			const blob = await response.blob();
			downloadBlob(blob, `audit-logs-${Date.now()}.csv`);
			toast.success("已导出筛选后的审计日志");
		} catch (error) {
			console.error("Export audit logs failed", error);
			toast.error("导出失败，请稍后重试");
		} finally {
			setExporting(false);
		}
	}, [filters]);

	const handleExpandRow = useCallback(
		async (expanded: boolean, record: AuditLog) => {
			if (!expanded) return;
			const id = record.id;
			if (rowDetails[id] !== undefined) return; // already fetched
			setRowLoading((prev) => ({ ...prev, [id]: true }));
			try {
				const detail = await AuditLogService.getAuditLogById(id);
				setRowDetails((prev) => ({ ...prev, [id]: detail }));
			} catch (e) {
				console.error("加载详情失败", e);
				setRowDetails((prev) => ({ ...prev, [id]: null }));
			} finally {
				setRowLoading((prev) => ({ ...prev, [id]: false }));
			}
		},
		[rowDetails],
	);

	// 使用 Antd Table 的分页展示，无需单独显示分页文案

	const columns = useMemo<ColumnsType<AuditLog>>(
		() => [
			{
				title: "操作人",
				dataIndex: "operatorName",
				key: "operatorName",
				width: 140,
				render: (_: string, r) => r.operatorName || formatOperatorName(r.actor),
			},
			{ title: "操作人编码", dataIndex: "actor", key: "actor", width: 140 },
			{ title: "IP地址", dataIndex: "clientIp", key: "clientIp", width: 130, render: (v?: string) => v || "127.0.0.1" },
			{
				title: "操作时间",
				dataIndex: "occurredAt",
				key: "occurredAt",
				width: 180,
				render: (v: string) => <span className="text-sm">{formatDateTime(v)}</span>,
			},
			{
				title: "模块名称",
				dataIndex: "sourceSystemText",
				key: "sourceSystemText",
				width: 120,
				render: (_: string, r) => r.sourceSystemText || r.sourceSystem || "-",
			},
			{
				title: "操作内容",
				dataIndex: "operationContent",
				key: "operationContent",
				width: 200,
				render: (_: string, r) => r.operationContent || r.summary || r.action,
			},
			{
				title: "操作类型",
				dataIndex: "operationType",
				key: "operationType",
				width: 110,
				render: (_: string, r) => r.operationType || "操作",
			},
			{
				title: "操作结果",
				dataIndex: "result",
				key: "result",
				width: 100,
				render: (_: string, r) => {
					const raw = (r.result || "").toUpperCase();
					const isFail = raw === "FAILED" || raw === "FAILURE";
					const label = r.resultText || (raw === "SUCCESS" ? "成功" : isFail ? "失败" : r.result || "-");
					return <Badge variant={isFail ? "destructive" : "secondary"}>{label}</Badge>;
				},
			},
			{
				title: "日志类型",
				dataIndex: "logTypeText",
				key: "logTypeText",
				width: 110,
				render: (_: string, r) =>
					r.logTypeText || (r.eventClass && r.eventClass.toLowerCase() === "securityevent" ? "安全审计" : "操作审计"),
			},
		],
		[],
	);

	return (
		<div className="mx-auto w-full px-6 py-6">
			<Card>
				<CardHeader className="gap-4 md:grid-cols-[minmax(0,1fr)_auto]">
					<div className="space-y-1.5">
						<CardTitle className="text-lg">日志审计</CardTitle>
						<CardDescription>
							支持按时间范围、来源系统、功能模块、操作人、目标位置与 IP 筛选审计记录。
						</CardDescription>
					</div>
					<CardAction>
						<Button onClick={handleExport} disabled={exporting || logs.length === 0}>
							{exporting ? "正在导出..." : "导出日志"}
						</Button>
					</CardAction>
				</CardHeader>
				<CardContent className="space-y-6">
					<section className="space-y-4 rounded-lg border border-border/60 bg-muted/20 p-4">
						<Text variant="subTitle1" color="secondary" className="block">
							查询条件
						</Text>
						<div className="grid gap-4 md:grid-cols-2 xl:grid-cols-6">
							<DateTimeField
								label="起始时间"
								value={filters.from}
								onChange={(value) => setFilters((prev) => ({ ...prev, from: value }))}
							/>
							<DateTimeField
								label="终止时间"
								value={filters.to}
								onChange={(value) => setFilters((prev) => ({ ...prev, to: value }))}
							/>
							<SelectField
								label="来源系统"
								value={filters.sourceSystem || ""}
								onChange={(value) => setFilters((prev) => ({ ...prev, sourceSystem: value || undefined }))}
								options={[
									{ value: "", label: "全部来源" },
									{ value: "admin", label: "系统管理" },
									{ value: "platform", label: "业务管理" },
								]}
							/>
							{MODULE_FILTER_ENABLED && (
								<SelectField
									label="功能模块"
									value={filters.module || ""}
									onChange={(value) => {
										const trimmed = value.trim();
										setFilters((prev) => ({ ...prev, module: trimmed ? trimmed : undefined }));
									}}
									options={moduleOptions}
								/>
							)}
							<SelectField
								label="功能分组"
								value={filters.operationGroup || ""}
								onChange={(value) => {
									const trimmed = value.trim();
									setFilters((prev) => ({ ...prev, operationGroup: trimmed ? trimmed : undefined }));
								}}
								options={groupOptions}
							/>
							<InputField
								label="操作者"
								placeholder="如 系统管理员 或 sysadmin"
								value={filters.actor ?? ""}
								onChange={(value) => setFilters((prev) => ({ ...prev, actor: value || undefined }))}
							/>
							<InputField
								label="目标位置"
								placeholder="例如 /admin/approval"
								value={filters.resource ?? ""}
								onChange={(value) => setFilters((prev) => ({ ...prev, resource: value || undefined }))}
							/>
							<InputField
								label="IP 地址"
								placeholder="例如 10.10."
								value={filters.clientIp ?? ""}
								onChange={(value) => setFilters((prev) => ({ ...prev, clientIp: value || undefined }))}
							/>
							<InputField
								label="操作关键词"
								placeholder="创建/审批/导出"
								value={filters.action ?? ""}
								onChange={(value) => setFilters((prev) => ({ ...prev, action: value || undefined }))}
							/>
							{/* 事件类型筛选已移除 */}
						</div>
						<div className="flex flex-wrap gap-3">
							<Button type="button" variant="secondary" className="w-32 text-black" onClick={() => setFilters({})}>
								重置条件
							</Button>
							<Button type="button" variant="secondary" className="w-32 text-black" onClick={handleRefresh} disabled={loading}>
								{loading ? "刷新中..." : "刷新"}
							</Button>
						</div>
					</section>

					<section className="space-y-4">
						<Text variant="subTitle1" color="secondary" className="block">
							日志记录
						</Text>
						<div className="overflow-x-auto rounded-lg border border-border/60 bg-card">
							<Table<AuditLog>
								rowKey="id"
								columns={columns}
								dataSource={logs}
								loading={loading}
							expandable={{
								columnWidth: 48,
								expandRowByClick: true,
								onExpand: handleExpandRow,
								expandedRowRender: (record) => {
									const loadingRow = rowLoading[record.id];
									const detail = rowDetails[record.id];
									if (loadingRow) {
										return <div className="text-xs text-muted-foreground">加载详情...</div>;
									}
									return renderAuditDetail(record, detail);
								},
							}}
								pagination={{
									current: page + 1,
									pageSize: size,
									total: totalElements,
									showSizeChanger: true,
									pageSizeOptions: [10, 20, 50, 100],
									showQuickJumper: true,
									showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
									onChange: (current, pageSize) => {
										setPage(current - 1);
										setSize(pageSize);
									},
								}}
								size="small"
								scroll={{ x: "max-content" }}
							/>
						</div>
					</section>
				</CardContent>
			</Card>
		</div>
	);
}

// mergeModuleOptionLists removed (unused)

function buildQuery(filters: FilterState): Record<string, string> {
	const params: Record<string, string> = {};
	if (filters.from) {
		params.from = new Date(filters.from).toISOString();
	}
	if (filters.to) {
		params.to = new Date(filters.to).toISOString();
	}
	if (filters.sourceSystem) {
		params.sourceSystem = filters.sourceSystem;
	}
	if (filters.module) {
		params.module = filters.module;
	}
	if (filters.operationGroup) {
		params.operationGroup = filters.operationGroup;
	}
	if (filters.actor) {
		params.actor = filters.actor;
	}
	if (filters.resource) {
		params.resource = filters.resource;
	}
	if (filters.clientIp) {
		params.clientIp = filters.clientIp;
	}
	if (filters.action) {
		params.action = filters.action;
	}
	return params;
}

type AuditDetailSource = {
	details?: unknown;
	metadata?: unknown;
	extraAttributes?: unknown;
};

interface ParsedAuditDetail {
	snapshot: SnapshotData | null;
	summaryRows: Array<{ field: string; label: string; before: unknown; after: unknown }>;
	infoEntries: Array<{ key: string; value: unknown }>;
	rawJson?: string;
	menuChanges: MenuChangeDisplayEntry[];
	layers: Record<string, unknown>[];
}

interface SnapshotData {
	before: Record<string, unknown>;
	after: Record<string, unknown>;
	changes: Array<{ field: string; label?: string; before: unknown; after: unknown }>;
}

const DETAIL_KEYS_TO_SKIP = new Set([
	"changeSnapshot",
	"change_snapshot",
	"changeSnapshots",
	"snapshot",
	"changeSummary",
	"change_summary",
	"changeSummaries",
	"change_summaries",
	"before",
	"after",
	"diff",
	"diffJson",
	"payload",
	"payloadJson",
	"context",
	"detail",
	"items",
	"menuChanges",
	"menu_changes",
]);

function renderAuditDetail(record: AuditLog, detail?: AuditLogDetail | null): ReactNode {
	const parsed = parseAuditDetail({
		details: detail?.details,
		metadata: detail?.metadata,
		extraAttributes: detail?.extraAttributes,
	});
	if (!parsed) {
		return <div className="text-xs text-muted-foreground">无详情</div>;
	}
	const baseSnapshot = convertSnapshotData(parsed.snapshot);
	const baseSummary: ChangeSummaryEntry[] = parsed.summaryRows.map((row, idx) => ({
		field: row.field ?? `field_${idx}`,
		label: row.label,
		before: row.before,
		after: row.after,
	}));
	const displayContext = buildChangeDisplayContext({
		layers: parsed.layers,
		baseSnapshot,
		baseSummary,
		fallbackDiff: parseRecord(detail?.details) ?? null,
	});
	const snapshot = displayContext.snapshot;
	const summary = displayContext.summary;
	const menuChanges = displayContext.menuChanges.length > 0 ? displayContext.menuChanges : parsed.menuChanges ?? [];
	const infoObject = parsed.infoEntries.length
		? parsed.infoEntries.reduce<Record<string, unknown>>((acc, entry) => {
			acc[entry.key] = entry.value;
			return acc;
		}, {})
		: null;

	const showDiffViewer = summary.length > 0 || snapshotHasContent(snapshot);

	return (
		<div className="space-y-3 text-xs leading-5">
			{showDiffViewer ? (
				<div className="space-y-2">
					<Text variant="body3" className="text-muted-foreground">
						变更详情
					</Text>
					<ChangeDiffViewer
						snapshot={snapshot}
						summary={summary.length > 0 ? summary : undefined}
						action={record.action || record.operationContent || record.operationCode || record.summary || record.buttonCode}
						operationTypeCode={record.operationTypeCode || record.operationType}
						className="text-xs"
					/>
				</div>
			) : null}
			{menuChanges.length > 0 ? (
				<div className="space-y-2">
					<Text variant="body3" className="text-muted-foreground">
						菜单变更
					</Text>
					<MenuChangeViewer entries={menuChanges} />
				</div>
			) : null}
			{infoObject ? (
				<div className="space-y-2">
					<Text variant="body3" className="text-muted-foreground">
						其他信息
					</Text>
					<pre className="rounded border border-border bg-muted/30 px-3 py-2 font-mono text-[11px] leading-5">
						{formatJson(infoObject)}
					</pre>
				</div>
			) : null}
			{parsed.rawJson ? (
				<details className="rounded border border-border/60 bg-muted/20 px-3 py-2">
					<summary className="cursor-pointer text-xs text-muted-foreground">查看原始详情</summary>
					<pre className="mt-2 max-h-72 overflow-auto whitespace-pre-wrap font-mono text-[11px] leading-5">
						{parsed.rawJson}
					</pre>
				</details>
			) : null}
		</div>
	);
}

function parseAuditDetail(source: AuditDetailSource): ParsedAuditDetail | null {
	const root = parseRecord(source.details) ?? {};
	const detailLayer = parseRecord(root.detail);
	const contextLayer = parseRecord(root.context);
	const metadataLayer = parseRecord(source.metadata);
	const extraLayer = parseRecord(source.extraAttributes);
	const snapshotLayers = [root, detailLayer, contextLayer].filter(Boolean) as Record<string, unknown>[];
	const layers = [root, detailLayer, contextLayer, metadataLayer, extraLayer].filter(Boolean) as Record<string, unknown>[];
	const menuChanges = extractMenuChanges(layers);

	let snapshot = findSnapshot(snapshotLayers);
	if (!snapshot) {
		snapshot = parseChangeSnapshot(root);
	}
	const summaryRows = findSummaryRows(snapshotLayers);

	const ignoredKeys = new Set<string>(DETAIL_KEYS_TO_SKIP);
	summaryRows.forEach((row) => {
		if (row.field) {
			ignoredKeys.add(row.field);
			ignoredKeys.add(row.field.toLowerCase());
		}
	});
	if (menuChanges.length > 0) {
		ignoredKeys.add("menuChanges");
		ignoredKeys.add("menu_changes");
	}

	const infoEntries = collectInfoEntries(layers, ignoredKeys);
	const rawJson = Object.keys(root).length > 0 ? formatJson(root) : undefined;

	if (!snapshot && summaryRows.length === 0 && infoEntries.length === 0 && !rawJson && menuChanges.length === 0) {
		return null;
	}

	return {
		snapshot: snapshot ?? null,
		summaryRows,
		infoEntries,
		rawJson,
		menuChanges,
		layers,
	};
}

function findSnapshot(layers: Record<string, unknown>[]): SnapshotData | null {
	for (const layer of layers) {
		const direct = parseChangeSnapshot(layer["changeSnapshot"]);
		if (direct) {
			return direct;
		}
		const multi = layer["changeSnapshots"];
		if (Array.isArray(multi)) {
			for (const entry of multi) {
				const snapshot = parseChangeSnapshot(entry);
				if (snapshot) {
					return snapshot;
				}
			}
		}
	}
	return null;
}

function findSummaryRows(layers: Record<string, unknown>[]) {
	for (const layer of layers) {
		const summary = parseSummaryRows(layer["changeSummary"]);
		if (summary.length > 0) {
			return summary;
		}
	}
	return [];
}

function parseSummaryRows(value: unknown): Array<{ field: string; label: string; before: unknown; after: unknown }> {
	if (!Array.isArray(value)) {
		return [];
	}
	const rows: Array<{ field: string; label: string; before: unknown; after: unknown }> = [];
	for (const item of value) {
		const record = parseRecord(item);
		if (!record) continue;
		const fieldRaw = typeof record.field === "string" ? record.field : record.field != null ? String(record.field) : "";
		const field = fieldRaw.trim();
		const labelRaw = typeof record.label === "string" ? record.label : "";
		const label = labelRaw.trim() || deriveFieldLabel(field);
		rows.push({
			field: field || label,
			label,
			before: record.before,
			after: record.after,
		});
	}
	return rows;
}

function parseChangeSnapshot(value: unknown): SnapshotData | null {
	const record = parseRecord(value);
	if (!record) {
		return null;
	}
	const before = parseRecord(record.before) ?? {};
	const after = parseRecord(record.after) ?? {};
	const changesRaw = Array.isArray(record.changes) ? record.changes : [];
	const changes: Array<{ field: string; label?: string; before: unknown; after: unknown }> = [];
	for (const item of changesRaw) {
		const entry = parseRecord(item);
		if (!entry) continue;
		const fieldRaw = typeof entry.field === "string" ? entry.field : entry.field != null ? String(entry.field) : "";
		const field = fieldRaw.trim();
		if (!field) continue;
		const labelRaw = typeof entry.label === "string" ? entry.label : "";
		const label = labelRaw.trim() || deriveFieldLabel(field);
		changes.push({
			field,
			label,
			before: entry.before,
			after: entry.after,
		});
	}
	return { before, after, changes };
}

function convertSnapshotData(snapshot?: SnapshotData | null): ChangeSnapshotLike | null {
	if (!snapshot) {
		return null;
	}
	return {
		before: snapshot.before,
		after: snapshot.after,
		changes: snapshot.changes?.map((change) => ({
			field: change.field,
			label: change.label,
			before: change.before,
			after: change.after,
		})),
	};
}

function collectInfoEntries(
	sources: Array<Record<string, unknown> | null | undefined>,
	ignoreKeys: Set<string>,
): Array<{ key: string; value: unknown }> {
	const entries: Array<{ key: string; value: unknown }> = [];
	const seen = new Set<string>();
	for (const source of sources) {
		if (!source) continue;
		for (const [key, raw] of Object.entries(source)) {
			if (!key || ignoreKeys.has(key)) continue;
			if (raw === null || raw === undefined) continue;
			if (seen.has(key)) continue;
			const normalized = normalizeInfoValue(raw);
			const display = formatDisplayValue(normalized);
			if (!display) continue;
			entries.push({ key, value: normalized });
			seen.add(key);
		}
	}
	return entries;
}

function normalizeInfoValue(value: unknown): unknown {
	if (value === null || value === undefined) {
		return value;
	}
	if (typeof value === "string") {
		const trimmed = value.trim();
		if (!trimmed) {
			return "";
		}
		if (
			(trimmed.startsWith("{") && trimmed.endsWith("}")) ||
			(trimmed.startsWith("[") && trimmed.endsWith("]"))
		) {
			try {
				return JSON.parse(trimmed);
			} catch {
				return trimmed;
			}
		}
		return trimmed;
	}
	if (Array.isArray(value)) {
		return value.map((item) => normalizeInfoValue(item));
	}
	if (typeof value === "object") {
		return value;
	}
	return value;
}

function parseRecord(value: unknown): Record<string, unknown> | null {
	const direct = asRecord(value);
	if (direct) {
		return direct;
	}
	if (typeof value === "string") {
		const trimmed = value.trim();
		if (!trimmed) {
			return null;
		}
		if (
			(trimmed.startsWith("{") && trimmed.endsWith("}")) ||
			(trimmed.startsWith("[") && trimmed.endsWith("]"))
		) {
			try {
				const parsed = JSON.parse(trimmed);
				return asRecord(parsed);
			} catch {
				return null;
			}
		}
	}
	return null;
}

function asRecord(value: unknown): Record<string, unknown> | null {
	if (value && typeof value === "object" && !Array.isArray(value)) {
		return value as Record<string, unknown>;
	}
	return null;
}

function formatDisplayValue(value: unknown): string {
	if (value === null || value === undefined) {
		return "";
	}
	if (typeof value === "string") {
		return value;
	}
	if (typeof value === "number" || typeof value === "boolean") {
		return String(value);
	}
	if (Array.isArray(value)) {
		if (value.length === 0) {
			return "[]";
		}
		const primitives = value.every(
			(item) => item === null || ["string", "number", "boolean"].includes(typeof item),
		);
		if (primitives) {
			return value.map((item) => (item === null || item === undefined ? "" : String(item))).join("，");
		}
		return formatJson(value);
	}
	const record = asRecord(value);
	if (record) {
		return formatJson(record);
	}
	return String(value);
}

function formatJson(value: unknown): string {
	if (typeof value === "string") {
		const trimmed = value.trim();
		if (!trimmed) {
			return "";
		}
		if (
			(trimmed.startsWith("{") && trimmed.endsWith("}")) ||
			(trimmed.startsWith("[") && trimmed.endsWith("]"))
		) {
			try {
				return JSON.stringify(JSON.parse(trimmed), null, 2);
			} catch {
				return value;
			}
		}
		return value;
	}
	try {
		return JSON.stringify(value, null, 2);
	} catch {
		return String(value ?? "");
	}
}

function deriveFieldLabel(field: string | undefined): string {
	if (!field) {
		return "-";
	}
	const normalized = field.replace(/[_\-\.]+/g, " ").trim();
	if (!normalized) {
		return field;
	}
	return normalized
		.split(" ")
		.filter((token) => token.length > 0)
		.map((token) => token.charAt(0).toUpperCase() + token.slice(1))
		.join(" ");
}

function resolveAccessToken(): string {
	const { userToken } = userStore.getState();
	const raw = userToken?.accessToken;
	if (!raw) {
		return "";
	}
	const trimmed = String(raw).trim();
	if (!trimmed) {
		return "";
	}
	return trimmed.startsWith("Bearer ") ? trimmed.slice(7).trim() : trimmed;
}

function buildExportUrl(query: string): string {
	const base = GLOBAL_CONFIG.apiBaseUrl?.trim() || "/api";
	const normalizedBase = base.endsWith("/") ? base.slice(0, -1) : base;
	return `${normalizedBase}/audit-logs/export${query ? `?${query}` : ""}`;
}

function formatDateTime(value: string) {
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value;
	}
	return date.toLocaleString("zh-CN", { hour12: false });
}

function formatOperatorName(value?: string) {
	if (!value) {
		return "-";
	}
	const trimmed = value.trim();
	if (!trimmed) {
		return "-";
	}
	const normalized = trimmed.toLowerCase();
	const label = ADMIN_LABELS[normalized];
	if (label) {
		return label;
	}
	return trimmed;
}

interface FieldProps {
	label: string;
	value?: string;
	onChange: (value: string | undefined) => void;
	placeholder?: string;
}

function DateTimeField({ label, value, onChange, placeholder }: FieldProps) {
	const [open, setOpen] = useState(false);
	const parsed = value ? dayjs(value) : undefined;
	const displayText = parsed ? parsed.format("YYYY-MM-DD HH:mm") : (placeholder ?? "yyyy-mm-dd --:--");

	const handleDateSelect = useCallback(
		(selectedDate?: Date) => {
			if (!selectedDate) {
				onChange(undefined);
				return;
			}
			const base = dayjs(selectedDate);
			const next = base
				.hour(parsed?.hour() ?? 0)
				.minute(parsed?.minute() ?? 0)
				.second(parsed?.second() ?? 0)
				.millisecond(0);
			onChange(next.format("YYYY-MM-DDTHH:mm"));
		},
		[onChange, parsed],
	);

	const handleTimeChange = useCallback(
		(event: ChangeEvent<HTMLInputElement>) => {
			const timeValue = event.target.value;
			if (!timeValue) {
				if (parsed) {
					const cleared = parsed.hour(0).minute(0).second(0).millisecond(0);
					onChange(cleared.format("YYYY-MM-DDTHH:mm"));
				}
				return;
			}
			const [hour, minute] = timeValue.split(":").map((item) => Number.parseInt(item, 10));
			const base = parsed ?? dayjs();
			const next = base
				.hour(Number.isFinite(hour) ? hour : 0)
				.minute(Number.isFinite(minute) ? minute : 0)
				.second(0)
				.millisecond(0);
			onChange(next.format("YYYY-MM-DDTHH:mm"));
		},
		[onChange, parsed],
	);

	return (
		<div className="space-y-2">
			<Text variant="body3" className="text-muted-foreground">
				{label}
			</Text>
			<Popover open={open} onOpenChange={setOpen}>
				<PopoverTrigger asChild>
					<Button
						variant="outline"
						className={cn("w-full justify-start text-left font-normal", !parsed && "text-muted-foreground")}
					>
						<CalendarIcon className="mr-2 size-4" />
						{displayText}
					</Button>
				</PopoverTrigger>
				<PopoverContent className="w-72 space-y-4" align="start">
					<Calendar mode="single" selected={parsed?.toDate()} onSelect={handleDateSelect} initialFocus />
					<div className="flex items-center gap-2">
						<div className="relative w-full">
							<Input
								type="time"
								value={parsed ? parsed.format("HH:mm") : ""}
								onChange={handleTimeChange}
								className="pl-9"
							/>
							<Clock3 className="pointer-events-none absolute left-2 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
						</div>
						<Button
							variant="ghost"
							size="sm"
							onClick={() => {
								onChange(undefined);
								setOpen(false);
							}}
						>
							清除
						</Button>
					</div>
				</PopoverContent>
			</Popover>
		</div>
	);
}

function InputField({ label, value, onChange, placeholder }: FieldProps) {
	return (
		<div className="space-y-2">
			<Text variant="body3" className="text-muted-foreground">
				{label}
			</Text>
			<Input
				placeholder={placeholder}
				value={value ?? ""}
				onChange={(event) => onChange(event.target.value || undefined)}
			/>
		</div>
	);
}

interface SelectOption {
	value: string;
	label: string;
}
interface SelectProps {
	label: string;
	value: string;
	onChange: (value: string) => void;
	options: SelectOption[];
}

function SelectField({ label, value, onChange, options }: SelectProps) {
	return (
		<div className="space-y-2">
			<Text variant="body3" className="text-muted-foreground">
				{label}
			</Text>
			<select
				className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm"
				value={value}
				onChange={(event) => onChange(event.target.value)}
			>
				{options.map((opt, index) => (
					<option key={`${opt.value || "all"}-${index}`} value={opt.value}>
						{opt.label}
					</option>
				))}
			</select>
		</div>
	);
}

function downloadBlob(blob: Blob, filename: string) {
	const url = URL.createObjectURL(blob);
	const link = document.createElement("a");
	link.href = url;
	link.download = filename;
	document.body.appendChild(link);
	link.click();
	document.body.removeChild(link);
	URL.revokeObjectURL(url);
}
