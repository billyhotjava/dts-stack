import { Button, Modal, Select, Table, Tag, Tooltip } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Eye } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import type { AuditLog } from "#/entity";
import { AuditLogService } from "@/api/services/auditLogService";
import { Card, CardContent, CardHeader } from "@/ui/card";
import { Text } from "@/ui/typography";
import { DetailItem, DetailSection } from "@/components/detail/DetailSection";
import { useUserRoles } from "@/store/userStore";

export default function AuditLogPage() {
	const [loading, setLoading] = useState(false);
	const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
	const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);
	const [detailModalVisible, setDetailModalVisible] = useState(false);
	const [roleFilter, setRoleFilter] = useState<string>(""); // sysadmin | authadmin | auditadmin | business
	const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
	const roles = useUserRoles();
	const isAuthAdmin = roles.some(
		(r: any) => (typeof r === "string" ? r : r?.name || r?.code)?.toUpperCase?.() === "AUTHADMIN",
	);
	const isAuditAdmin = roles.some(
		(r: any) => (typeof r === "string" ? r : r?.name || r?.code)?.toUpperCase?.() === "AUDITADMIN",
	);
	const actorOptions = (() => {
		const opts: { label: string; value: string }[] = [];
		if (isAuthAdmin)
			opts.push({ label: "系统管理员", value: "sysadmin" }, { label: "安全审计员", value: "auditadmin" });
		if (isAuditAdmin)
			opts.push(
				{ label: "系统管理员", value: "sysadmin" },
				{ label: "授权管理员", value: "authadmin" },
				{ label: "业务系统", value: "business" },
			);
		if (!isAuthAdmin && !isAuditAdmin)
			opts.push({ label: "系统管理员", value: "sysadmin" }, { label: "安全审计员", value: "auditadmin" });
		const map = new Map<string, string>();
		for (const o of opts) map.set(o.value, o.label);
		return Array.from(map.entries()).map(([value, label]) => ({ value, label }));
	})();

	// 加载审计日志列表
	const loadAuditLogs = useCallback(async (page = 1, pageSize = 10) => {
		setLoading(true);
		try {
			const response = await AuditLogService.getAuditLogs(page - 1, pageSize, "occurredAt,desc");
			setAuditLogs(response.content);
			setPagination((prev) => {
				const next = {
					current: response.page + 1,
					pageSize: response.size,
					total: response.totalElements,
				};
				if (
					prev.current === next.current &&
					prev.pageSize === next.pageSize &&
					prev.total === next.total
				) {
					return prev;
				}
				return next;
			});
		} catch (error: any) {
			console.error("获取审计日志失败:", error);
			toast.error(`加载审计日志失败: ${error.message || "未知错误"}`);
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		loadAuditLogs();
	}, [loadAuditLogs]);

	const handleTableChange = (tablePagination: any) => {
		loadAuditLogs(tablePagination.current, tablePagination.pageSize);
	};

	const handleViewDetail = (record: AuditLog) => {
		setSelectedLog(record);
		setDetailModalVisible(true);
	};

	const getActionTagColor = (action: string) => {
		switch (action.toLowerCase()) {
			case "create":
			case "import":
				return "green";
			case "update":
			case "approve":
			case "export":
				return "blue";
			case "delete":
			case "reject":
				return "red";
			default:
				return "default";
		}
	};

	// 表格列定义
	const columns: ColumnsType<AuditLog> = [
		{
			title: "记录编号",
			dataIndex: "id",
			width: 80,
			render: (id: number) => (
				<Text variant="body2" className="font-mono">
					#{id}
				</Text>
			),
		},
		{
			title: "模块 / 操作",
			dataIndex: "module",
			width: 180,
			render: (_: string, record) => (
				<div className="space-y-1">
					<Text variant="body2" className="font-medium">
						{record.module ?? "-"}
					</Text>
					<Tag color={getActionTagColor(record.action)}>{record.action}</Tag>
				</div>
			),
		},
		{
			title: "操作者 / IP",
			dataIndex: "actor",
			width: 200,
			render: (_: string, record) => (
				<div className="text-xs text-muted-foreground space-y-1">
					<div>操作者：{record.actor || "-"}</div>
					{record.actorRole ? <div>角色：{record.actorRole}</div> : null}
					<div>IP：{record.clientIp || "-"}</div>
				</div>
			),
		},
		{
			title: "目标",
			dataIndex: "resourceId",
			width: 220,
			render: (_: string | undefined, record) => {
				const resource = [record.resourceType, record.resourceId].filter(Boolean).join(" · ") || "-";
				const requestInfo = record.requestUri ? `${record.httpMethod ?? "GET"} ${record.requestUri}` : undefined;
				return (
					<Tooltip title={requestInfo}>
						<span>{resource}</span>
					</Tooltip>
				);
			},
		},
		{
			title: "结果",
			dataIndex: "result",
			width: 100,
			render: (result: string) => (
				<Tag color={result?.toLowerCase() === "failure" ? "red" : "green"}>{result}</Tag>
			),
		},
		{
			title: "时间",
			dataIndex: "occurredAt",
			width: 180,
			render: (value: string, record) => (
				<div className="space-y-1">
					<Text variant="body2">{new Date(value).toLocaleString("zh-CN", { hour12: false })}</Text>
					{typeof record.latencyMs === "number" ? (
						<Text variant="body3" className="text-muted-foreground">
							耗时：{record.latencyMs} ms
						</Text>
					) : null}
				</div>
			),
		},
		{
			title: "摘要",
			dataIndex: "payloadPreview",
			ellipsis: true,
			render: (value?: string) => (
				<Tooltip title={value || ""}>
					<span>{value || "-"}</span>
				</Tooltip>
			),
		},
		{
			title: "操作",
			key: "action",
			width: 100,
			render: (_, record) => (
				<Button type="link" icon={<Eye className="h-4 w-4" />} onClick={() => handleViewDetail(record)}>
					查看详情
				</Button>
			),
		},
	];

	return (
		<div className="p-4">
			<Card>
				<CardHeader>
					<div className="flex items-center justify-between gap-3">
						<div>
							<h2 className="text-2xl font-bold">日志审计</h2>
							<p className="text-muted-foreground">查看系统操作日志</p>
						</div>
						<div className="flex items-center gap-2">
							<label className="text-sm text-muted-foreground">角色筛选</label>
							<Select
								allowClear
								placeholder="请选择角色"
								options={actorOptions}
								className="min-w-[160px]"
								value={roleFilter || undefined}
								onChange={(val) => setRoleFilter(val || "")}
							/>
						</div>
					</div>
				</CardHeader>
				<CardContent>
				<Table
					columns={columns}
					dataSource={
						roleFilter
							? roleFilter === "business"
								? auditLogs.filter(
										(x) => !["sysadmin", "authadmin", "auditadmin"].includes((x.actor || "").toLowerCase()),
								)
								: auditLogs.filter((x) => (x.actor || "").toLowerCase() === roleFilter.toLowerCase())
							: auditLogs
						}
					loading={loading}
					pagination={{
						current: pagination.current,
						pageSize: pagination.pageSize,
						total: pagination.total,
						showSizeChanger: true,
						showQuickJumper: true,
						showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
					}}
					onChange={handleTableChange}
					rowKey="id"
				/>
				</CardContent>
			</Card>

			<Modal
				title="审计日志详情"
				open={detailModalVisible}
				onCancel={() => {
					setDetailModalVisible(false);
					setSelectedLog(null);
				}}
				footer={null}
				width={800}
			>
				{selectedLog && (
					<div className="space-y-4">
						<DetailSection title="基础信息">
							<DetailItem label="记录编号" value={`#${selectedLog.id}`} monospace />
							<DetailItem label="模块" value={selectedLog.module || "-"} />
							<DetailItem
								label="操作"
								value={<Tag color={getActionTagColor(selectedLog.action)}>{selectedLog.action || "-"}</Tag>}
							/>
							<DetailItem
								label="结果"
								value={
									<Tag color={(selectedLog.result || "").toLowerCase() === "failure" ? "red" : "green"}>
										{selectedLog.result || "-"}
									</Tag>
								}
							/>
							<DetailItem label="发生时间" value={formatDateTime(selectedLog.occurredAt)} />
							<DetailItem label="耗时 (ms)" value={selectedLog.latencyMs ?? "-"} />
						</DetailSection>

						<DetailSection title="操作者信息">
							<DetailItem label="操作者" value={selectedLog.actor || "-"} />
							<DetailItem label="操作者角色" value={selectedLog.actorRole || "-"} />
						</DetailSection>

						<DetailSection title="资源信息">
							<DetailItem label="目标类型" value={selectedLog.resourceType || "-"} />
							<DetailItem label="目标标识" value={selectedLog.resourceId || "-"} monospace />
						</DetailSection>

						<DetailSection title="请求上下文" columns={1}>
							<DetailItem
								label="请求"
								value={`${selectedLog.httpMethod || "GET"} ${selectedLog.requestUri || "-"}`}
								monospace
								full
							/>
							<DetailItem label="客户端 IP" value={selectedLog.clientIp || "-"} monospace />
							<DetailItem label="User-Agent" value={selectedLog.clientAgent || "-"} full />
						</DetailSection>

						{selectedLog.payloadPreview && (
							<DetailSection title="摘要" columns={1}>
								<DetailItem
									label="摘要"
									value={<div className="rounded-md bg-muted px-3 py-2 text-sm leading-relaxed">{selectedLog.payloadPreview}</div>}
								/>
							</DetailSection>
						)}

						{selectedLog.extraTags && (
							<DetailSection title="附加标签" columns={1}>
								<DetailItem
									label="标签"
									value={<div className="rounded-md bg-muted px-3 py-2 text-xs break-all">{selectedLog.extraTags}</div>}
								/>
							</DetailSection>
						)}
					</div>
				)}
			</Modal>
		</div>
	);
}

function formatDateTime(value?: string | null): string {
	if (!value) {
		return "-";
	}
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value;
	}
	return date.toLocaleString("zh-CN", { hour12: false });
}
