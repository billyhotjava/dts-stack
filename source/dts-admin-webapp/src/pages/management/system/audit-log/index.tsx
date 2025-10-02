import { Button, DatePicker, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { Dayjs } from "dayjs";
import dayjs from "dayjs";
import { Eye } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { AuditLog } from "#/entity";
import { Card, CardContent, CardHeader } from "@/ui/card";
import { Text } from "@/ui/typography";
import { SAMPLE_AUDIT_LOGS } from "./mock-data";
import { useUserRoles } from "@/store/userStore";

const { RangePicker } = DatePicker;

type DateRangeValue = [Dayjs | null, Dayjs | null] | null;

type AuditLogFilters = {
	dateRange?: DateRangeValue;
	module?: string;
	ip?: string;
	actor?: string; // sysadmin | auditadmin
	person?: string; // free-text operator search
	targetType?: string;
	targetUri?: string;
};

const ACTION_TYPE_CONFIG: Record<string, { label: string; color: string }> = {
	CREATE: { label: "创建", color: "green" },
	UPDATE: { label: "更新", color: "blue" },
	DELETE: { label: "删除", color: "red" },
	APPROVE: { label: "批准", color: "green" },
	REJECT: { label: "拒绝", color: "volcano" },
	EXPORT: { label: "导出", color: "purple" },
	LOGIN: { label: "登录", color: "cyan" },
	RESET_PASSWORD: { label: "重置密码", color: "orange" },
	ENABLE: { label: "启用", color: "lime" },
	DISABLE: { label: "停用", color: "magenta" },
};

const resolveActionMeta = (action: string) => {
	const meta = ACTION_TYPE_CONFIG[action.toUpperCase()];
	if (meta) {
		return meta;
	}
	return { label: action, color: "default" };
};

export default function AuditLogPage() {
	const [form] = Form.useForm<AuditLogFilters>();
	const [auditLogs] = useState<AuditLog[]>(() => SAMPLE_AUDIT_LOGS);
	const [filteredLogs, setFilteredLogs] = useState<AuditLog[]>(() => SAMPLE_AUDIT_LOGS);
	const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);
	const [detailModalVisible, setDetailModalVisible] = useState(false);
	const [exporting, setExporting] = useState(false);
	const roles = useUserRoles();

	const isAuthAdmin = useMemo(() => {
		return roles.some((r: any) => (typeof r === "string" ? r : r?.name || r?.code)?.toUpperCase?.() === "AUTHADMIN");
	}, [roles]);
	const isAuditAdmin = useMemo(() => {
		return roles.some((r: any) => (typeof r === "string" ? r : r?.name || r?.code)?.toUpperCase?.() === "AUDITADMIN");
	}, [roles]);

	const actorOptions = useMemo(() => {
		const opts: { label: string; value: string }[] = [];
		if (isAuthAdmin) {
			opts.push({ label: "系统管理员", value: "sysadmin" }, { label: "安全审计员", value: "auditadmin" });
		}
		if (isAuditAdmin) {
			opts.push(
				{ label: "系统管理员", value: "sysadmin" },
				{ label: "授权管理员", value: "authadmin" },
				{ label: "业务系统", value: "business" },
			);
		}
		if (!isAuthAdmin && !isAuditAdmin) {
			// 默认提供常见两类
			opts.push({ label: "系统管理员", value: "sysadmin" }, { label: "安全审计员", value: "auditadmin" });
		}
		// 去重
		const map = new Map<string, string>();
		for (const o of opts) map.set(o.value, o.label);
		return Array.from(map.entries()).map(([value, label]) => ({ value, label }));
	}, [isAuthAdmin, isAuditAdmin]);

	const moduleOptions = useMemo(
		() =>
			Array.from(new Set(auditLogs.map((log) => log.module))).map((module) => ({
				label: module,
				value: module,
			})),
		[auditLogs],
	);

	const targetTypeOptions = useMemo(
		() =>
			Array.from(new Set(auditLogs.map((log) => log.targetType).filter(Boolean))).map((type) => ({
				label: (type || "").replace(/_/g, " "),
				value: type as string,
			})),
		[auditLogs],
	);

	const applyFilters = useCallback(
		(values: AuditLogFilters) => {
			const { dateRange, module, ip, actor, person, targetType, targetUri } = values;
			let nextLogs = auditLogs;

			if (dateRange && Array.isArray(dateRange)) {
				const [start, end] = dateRange;
				if (start || end) {
					nextLogs = nextLogs.filter((log) => {
						const timestamp = dayjs(log.at);
						const afterStart = start ? timestamp.isSame(start, "second") || timestamp.isAfter(start) : true;
						const beforeEnd = end ? timestamp.isSame(end, "second") || timestamp.isBefore(end) : true;
						return afterStart && beforeEnd;
					});
				}
			}

			if (module) {
				nextLogs = nextLogs.filter((log) => log.module === module);
			}

			if (ip && ip.trim()) {
				const keyword = ip.trim();
				nextLogs = nextLogs.filter((log) => log.ip.includes(keyword));
			}

			if (actor && actor.trim()) {
				if (actor === "business") {
					nextLogs = nextLogs.filter(
						(log) => !["sysadmin", "authadmin", "auditadmin"].includes((log.actor || "").toLowerCase()),
					);
				} else {
					nextLogs = nextLogs.filter((log) => (log.actor || "").toLowerCase() === actor.toLowerCase());
				}
			}

			if (person && person.trim()) {
				const keyword = person.trim().toLowerCase();
				nextLogs = nextLogs.filter((log) => (log.actor || "").toLowerCase().includes(keyword));
			}

			if (targetType && targetType.trim()) {
				const normalized = targetType.trim().toLowerCase();
				nextLogs = nextLogs.filter((log) => (log.targetType || "").toLowerCase() === normalized);
			}

			if (targetUri && targetUri.trim()) {
				const keyword = targetUri.trim().toLowerCase();
				nextLogs = nextLogs.filter((log) => (log.targetUri || "").toLowerCase().includes(keyword));
			}

			setFilteredLogs(nextLogs);
		},
		[auditLogs],
	);

	useEffect(() => {
		applyFilters(form.getFieldsValue());
	}, [applyFilters, form]);

	const handleValuesChange = useCallback(
		(_: unknown, allValues: AuditLogFilters) => {
			applyFilters(allValues);
		},
		[applyFilters],
	);

	const handleSearch = useCallback(
		(values: AuditLogFilters) => {
			applyFilters(values);
		},
		[applyFilters],
	);

	const handleReset = useCallback(() => {
		form.resetFields();
		applyFilters({});
	}, [applyFilters, form]);

	const handleExport = useCallback(() => {
		if (filteredLogs.length === 0) {
			message.warning("当前筛选条件下没有可导出的日志");
			return;
		}
		setExporting(true);
		const filters = form.getFieldsValue();
		window.setTimeout(() => {
			const csv = buildCsvContent(filteredLogs, filters);
			const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
			const url = URL.createObjectURL(blob);
			const anchor = document.createElement("a");
			anchor.href = url;
			anchor.download = `audit-logs-${Date.now()}.csv`;
			document.body.appendChild(anchor);
			anchor.click();
			document.body.removeChild(anchor);
			URL.revokeObjectURL(url);
			message.success("已导出审计日志 CSV 文件");
			setExporting(false);
		}, 400);
	}, [filteredLogs, form]);

	const handleViewDetail = useCallback((record: AuditLog) => {
		setSelectedLog(record);
		setDetailModalVisible(true);
	}, []);

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
			title: "功能模块",
			dataIndex: "module",
			width: 160,
		},
		{
			title: "操作类型",
			dataIndex: "action",
			width: 120,
			render: (action: string) => {
				const meta = resolveActionMeta(action);
				return <Tag color={meta.color}>{meta.label}</Tag>;
			},
		},
		{
			title: "目标类型",
			dataIndex: "targetType",
			width: 140,
			render: (type: string | undefined) =>
				type ? <Tag color="blue">{type.replace(/_/g, " ")}</Tag> : <span>-</span>,
		},
		{
			title: "目标 URI",
			dataIndex: "targetUri",
			width: 220,
			ellipsis: true,
			render: (uri: string | undefined) => (
				<Tooltip title={uri || "-"}>
					<Text variant="body2" className="font-mono">
						{uri || "-"}
					</Text>
				</Tooltip>
			),
		},
		{
			title: "目标标识",
			dataIndex: "target",
			width: 200,
			ellipsis: true,
			render: (target: string | undefined) => (
				<Tooltip title={target || "-"}>
					<span>{target || "-"}</span>
				</Tooltip>
			),
		},
		{
			title: "操作人",
			dataIndex: "actor",
			width: 120,
		},
		{
			title: "IP地址",
			dataIndex: "ip",
			width: 140,
			render: (ip: string) => (
				<Text variant="body2" className="font-mono">
					{ip}
				</Text>
			),
		},
		{
			title: "内容",
			dataIndex: "details",
			ellipsis: true,
			render: (details: string) => (
				<Tooltip title={details}>
					<span>{details}</span>
				</Tooltip>
			),
		},
		{
			title: "创建时间",
			dataIndex: "at",
			width: 200,
			render: (date: string) => dayjs(date).format("YYYY-MM-DD HH:mm:ss"),
			sorter: (a, b) => dayjs(a.at).valueOf() - dayjs(b.at).valueOf(),
			defaultSortOrder: "descend",
			sortDirections: ["descend", "ascend"],
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
					<div className="flex items-center justify-between">
						<div>
							<h2 className="text-2xl font-bold">日志审计</h2>
							<p className="text-muted-foreground">查看系统操作日志</p>
						</div>
					</div>
				</CardHeader>
				<CardContent>
					<Form
						form={form}
						layout="inline"
						className="mb-4 flex flex-wrap gap-4"
						onValuesChange={handleValuesChange}
						onFinish={handleSearch}
					>
						<Form.Item label="起止时间" name="dateRange">
							<RangePicker
								allowClear
								showTime={{ format: "HH:mm" }}
								style={{ minWidth: 320 }}
								format="YYYY-MM-DD HH:mm"
								placeholder={["开始时间", "结束时间"]}
							/>
						</Form.Item>
						<Form.Item label="功能模块" name="module">
							<Select allowClear placeholder="请选择模块" options={moduleOptions} className="min-w-[160px]" />
						</Form.Item>
						<Form.Item label="IP地址" name="ip">
							<Input allowClear placeholder="请输入IP地址" style={{ width: 200 }} />
						</Form.Item>
						<Form.Item label="人员" name="person">
							<Input allowClear placeholder="支持模糊搜索操作者" style={{ width: 200 }} />
						</Form.Item>
						<Form.Item label="角色" name="actor">
							<Select allowClear placeholder="请选择角色" options={actorOptions} className="min-w-[160px]" />
						</Form.Item>
						<Form.Item label="目标类型" name="targetType">
							<Select allowClear placeholder="请选择目标类型" options={targetTypeOptions} className="min-w-[160px]" />
						</Form.Item>
						<Form.Item label="目标URI" name="targetUri">
							<Input allowClear placeholder="支持模糊查询如 /api/user" style={{ width: 220 }} />
						</Form.Item>
						<Form.Item>
							<Space>
								<Button type="primary" htmlType="submit">
									查询
								</Button>
								<Button onClick={handleReset}>重置</Button>
								<Button onClick={handleExport} loading={exporting}>
									导出日志
								</Button>
							</Space>
						</Form.Item>
					</Form>
					<Table
						columns={columns}
						dataSource={filteredLogs}
						pagination={{
							defaultPageSize: 10,
							pageSizeOptions: ["10", "20", "50"],
							showSizeChanger: true,
							showQuickJumper: true,
							showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
						}}
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
						<div className="grid grid-cols-2 gap-4">
							<div>
								<Text variant="body2" className="text-muted-foreground">
									记录编号
								</Text>
								<Text variant="body1">#{selectedLog.id}</Text>
							</div>
							<div>
								<Text variant="body2" className="text-muted-foreground">
									操作类型
								</Text>
								{(() => {
									const meta = resolveActionMeta(selectedLog.action);
									return <Tag color={meta.color}>{meta.label}</Tag>;
								})()}
							</div>
							<div>
								<Text variant="body2" className="text-muted-foreground">
									功能模块
								</Text>
								<Text variant="body1">{selectedLog.module}</Text>
							</div>
							<div>
								<Text variant="body2" className="text-muted-foreground">
									操作人
								</Text>
								<Text variant="body1">{selectedLog.actor}</Text>
							</div>
						<div>
							<Text variant="body2" className="text-muted-foreground">
								目标
							</Text>
							<Text variant="body1">{selectedLog.target || "-"}</Text>
						</div>
						<div>
							<Text variant="body2" className="text-muted-foreground">
								目标类型
							</Text>
							<Text variant="body1">{selectedLog.targetType || "-"}</Text>
						</div>
						<div>
							<Text variant="body2" className="text-muted-foreground">
								目标 URI
							</Text>
							<Text variant="body1" className="font-mono break-all">{selectedLog.targetUri || "-"}</Text>
						</div>
							<div>
								<Text variant="body2" className="text-muted-foreground">
									IP地址
								</Text>
								<Text variant="body1" className="font-mono">
									{selectedLog.ip}
								</Text>
							</div>
							<div className="col-span-2">
								<Text variant="body2" className="text-muted-foreground">
									创建时间
								</Text>
								<Text variant="body1">{dayjs(selectedLog.at).format("YYYY-MM-DD HH:mm:ss")}</Text>
							</div>
							{selectedLog.result && (
								<div className="col-span-2">
									<Text variant="body2" className="text-muted-foreground">
										结果
									</Text>
									<Text variant="body1">{selectedLog.result}</Text>
								</div>
							)}
						</div>
						<div>
							<Text variant="body2" className="text-muted-foreground">
								内容详情
							</Text>
							<div className="mt-2 rounded-md bg-muted p-3">
								<Text variant="body1">{selectedLog.details}</Text>
							</div>
						</div>
					</div>
				)}
			</Modal>
		</div>
	);
}

function buildCsvContent(logs: AuditLog[], filters: AuditLogFilters) {
	const lines: string[] = [];
	const now = dayjs().format("YYYY-MM-DD HH:mm:ss");
	const [start, end] = filters.dateRange ?? [];
	const module = filters.module ?? "全部";
	const ip = filters.ip ?? "全部";
	const actor = filters.actor ?? "全部";
	lines.push(`# 导出时间：${now}`);
	lines.push(
		`# 筛选条件：起始=${start ? start.format("YYYY-MM-DD HH:mm") : "全部"} → ${
			end ? end.format("YYYY-MM-DD HH:mm") : "全部"
		}, 模块=${module}, IP=${ip}, 角色=${actor}`,
	);
	lines.push("");
	lines.push("记录编号,功能模块,操作类型,目标类型,目标URI,目标标识,操作者,IP地址,创建时间,结果,内容");
	for (const log of logs) {
		lines.push(
			[
				csvEscape(`#${log.id}`),
				csvEscape(log.module),
				csvEscape(resolveActionMeta(log.action).label),
				csvEscape(log.targetType || "-"),
				csvEscape(log.targetUri || "-"),
				csvEscape(log.target || "-"),
				csvEscape(log.actor),
				csvEscape(log.ip),
				csvEscape(dayjs(log.at).format("YYYY-MM-DD HH:mm:ss")),
				csvEscape(log.result ?? "-"),
				csvEscape(log.details ?? ""),
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
