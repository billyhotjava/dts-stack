import { Button, Collapse, DatePicker, Form, Input, Modal, Select, Space, Spin, Table, Tag, Tooltip, message } from "antd";
import type { ColumnsType, TablePaginationConfig, TableProps } from "antd/es/table";
import type { Dayjs } from "dayjs";
import dayjs from "dayjs";
import { Eye } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { AuditLog, AuditLogDetail } from "#/entity";
import AuditLogService from "@/api/services/auditLogService";
import { useUserRoles } from "@/store/userStore";
import { Card, CardContent, CardHeader } from "@/ui/card";
import { Text } from "@/ui/typography";
import { DetailItem, DetailSection } from "@/components/detail/DetailSection";

const { RangePicker } = DatePicker;
const DEFAULT_PAGE_SIZE = 20;

type DateRangeValue = [Dayjs | null, Dayjs | null] | null;

type AuditLogFilters = {
	dateRange?: DateRangeValue;
	module?: string;
	ip?: string;
	actor?: string;
	person?: string;
	targetType?: string;
	targetId?: string;
	result?: string;
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

const RESULT_LABELS: Record<string, { label: string; color: string }> = {
	SUCCESS: { label: "成功", color: "green" },
	FAILURE: { label: "失败", color: "red" },
	ERROR: { label: "错误", color: "red" },
};

export default function AuditLogPage() {
	const [form] = Form.useForm<AuditLogFilters>();
	const [logs, setLogs] = useState<AuditLog[]>([]);
	const [pagination, setPagination] = useState<{ current: number; pageSize: number; total: number }>({
		current: 1,
		pageSize: DEFAULT_PAGE_SIZE,
		total: 0,
	});
	const [filtersState, setFiltersState] = useState<AuditLogFilters>({});
	const [loading, setLoading] = useState(false);
	const [exporting, setExporting] = useState(false);
	const [detailModalVisible, setDetailModalVisible] = useState(false);
	const [detailLoading, setDetailLoading] = useState(false);
	const [selectedLog, setSelectedLog] = useState<AuditLogDetail | null>(null);
	const roles = useUserRoles();

	const actorOptions = useMemo(() => {
		const opts: { label: string; value: string }[] = [];
		const normalizeRole = (value: string) => value.toUpperCase();
		if (roles.some((role: any) => normalizeRole(typeof role === "string" ? role : role?.name || role?.code || "") === "AUTHADMIN")) {
			opts.push({ label: "系统管理员", value: "sysadmin" }, { label: "安全审计员", value: "auditadmin" });
		}
		if (roles.some((role: any) => normalizeRole(typeof role === "string" ? role : role?.name || role?.code || "") === "AUDITADMIN")) {
			opts.push(
				{ label: "系统管理员", value: "sysadmin" },
				{ label: "授权管理员", value: "authadmin" },
				{ label: "业务系统", value: "business" },
			);
		}
		if (opts.length === 0) {
			opts.push({ label: "系统管理员", value: "sysadmin" }, { label: "安全审计员", value: "auditadmin" });
		}
		const dedup = new Map<string, string>();
		opts.forEach((item) => dedup.set(item.value, item.label));
		return Array.from(dedup.entries()).map(([value, label]) => ({ value, label }));
	}, [roles]);

	const moduleOptions = useMemo(() => {
		return Array.from(new Set(logs.map((log) => log.module).filter(Boolean))).map((module) => ({
			value: module,
			label: formatLabel(module!),
		}));
	}, [logs]);

	const targetTypeOptions = useMemo(() => {
		return Array.from(new Set(logs.map((log) => log.resourceType).filter(Boolean))).map((type) => ({
			value: type as string,
			label: formatLabel(type as string),
		}));
	}, [logs]);

	const resultOptions = useMemo(
		() => [
			{ value: "SUCCESS", label: "成功" },
			{ value: "FAILURE", label: "失败" },
			{ value: "ERROR", label: "错误" },
		],
		[],
	);

	const fetchLogs = useCallback(async (page: number, pageSize: number, formValues: AuditLogFilters) => {
		setLoading(true);
		try {
			const params = buildQueryParams(formValues);
			const response = await AuditLogService.getAuditLogs(page - 1, pageSize, "occurredAt,desc", params);
			setLogs(response.content ?? []);
			setPagination({
				current: response.page + 1,
				pageSize: response.size,
				total: response.totalElements,
			});
			setFiltersState(formValues);
		} catch (err: any) {
			message.error(err?.message || "加载审计日志失败，请稍后重试");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		fetchLogs(1, DEFAULT_PAGE_SIZE, {});
	}, [fetchLogs]);

	const handleSearch = useCallback(
		(values: AuditLogFilters) => {
			fetchLogs(1, pagination.pageSize, values);
		},
		[fetchLogs, pagination.pageSize],
	);

	const handleReset = useCallback(() => {
		form.resetFields();
		fetchLogs(1, pagination.pageSize, {});
	}, [fetchLogs, form, pagination.pageSize]);

	const handleExport = useCallback(async () => {
		setExporting(true);
		try {
			const blob = await AuditLogService.exportAuditLogs(buildQueryParams(filtersState));
			const url = window.URL.createObjectURL(blob);
			const anchor = document.createElement("a");
			anchor.href = url;
			anchor.download = `audit-logs-${dayjs().format("YYYYMMDD-HHmmss")}.csv`;
			document.body.appendChild(anchor);
			anchor.click();
			document.body.removeChild(anchor);
			window.URL.revokeObjectURL(url);
			message.success("已导出审计日志");
		} catch (err: any) {
			message.error(err?.message || "导出审计日志失败");
		} finally {
			setExporting(false);
		}
	}, [filtersState]);

	const handleViewDetail = useCallback(async (record: AuditLog) => {
		setDetailModalVisible(true);
		setDetailLoading(true);
		setSelectedLog(record as AuditLogDetail);
		try {
			const detail = await AuditLogService.getAuditLogById(record.id);
			setSelectedLog(detail);
		} catch (err: any) {
			message.error(err?.message || "加载审计日志详情失败");
		} finally {
			setDetailLoading(false);
		}
	}, []);

	const handleTableChange = useCallback(
		(nextPagination: TablePaginationConfig) => {
			const current = nextPagination.current ?? 1;
			const pageSize = nextPagination.pageSize ?? pagination.pageSize;
			fetchLogs(current, pageSize, filtersState);
		},
		[fetchLogs, filtersState, pagination.pageSize],
	);

	const tableOnChange: TableProps<AuditLog>["onChange"] = (nextPagination) => {
		handleTableChange(nextPagination as TablePaginationConfig);
	};

	const columns: ColumnsType<AuditLog> = useMemo(
		() => [
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
				render: (module?: string) => formatLabel(module),
			},
			{
				title: "操作类型",
				dataIndex: "action",
				width: 150,
				render: (action: string) => {
					const meta = resolveActionMeta(action);
					return <Tag color={meta.color}>{meta.label}</Tag>;
				},
			},
			{
				title: "目标类型",
				dataIndex: "resourceType",
				width: 150,
				render: (type?: string) => (type ? <Tag color="blue">{formatLabel(type)}</Tag> : <span>-</span>),
			},
			{
				title: "目标标识",
				dataIndex: "resourceId",
				width: 220,
				ellipsis: true,
				render: (resourceId?: string) => (
					<Tooltip title={resourceId || "-"}>
						<Text variant="body2" className="font-mono">
							{resourceId || "-"}
						</Text>
					</Tooltip>
				),
			},
			{
				title: "操作者",
				dataIndex: "actor",
				width: 140,
			},
			{
				title: "IP 地址",
				dataIndex: "clientIp",
				width: 160,
				render: (ip?: string) => (
					<Text variant="body2" className="font-mono">
						{ip || "-"}
					</Text>
				),
			},
			{
				title: "结果",
				dataIndex: "result",
				width: 120,
				render: (result: string) => {
					const meta = RESULT_LABELS[result?.toUpperCase?.() || ""];
					if (!meta) {
						return result || "-";
					}
					return <Tag color={meta.color}>{meta.label}</Tag>;
				},
			},
			{
				title: "内容摘要",
				dataIndex: "payloadPreview",
				width: 240,
				render: (preview?: string) => (
					<Tooltip title={preview || "-"}>
						<span>{preview || "-"}</span>
					</Tooltip>
				),
			},
			{
				title: "发生时间",
				dataIndex: "occurredAt",
				width: 200,
				render: (date: string) => dayjs(date).format("YYYY-MM-DD HH:mm:ss"),
			},
			{
				title: "操作",
				key: "action",
				width: 120,
				render: (_, record) => (
					<Button type="link" icon={<Eye className="h-4 w-4" />} onClick={() => handleViewDetail(record)}>
						查看详情
					</Button>
				),
			},
		],
		[handleViewDetail],
	);

	return (
		<div className="p-4">
			<Card>
				<CardHeader>
					<div className="flex items-center justify-between">
						<div>
							<h2 className="text-2xl font-bold">日志审计</h2>
							<p className="text-muted-foreground">查看与检索系统操作日志</p>
						</div>
					</div>
				</CardHeader>
				<CardContent>
					<Form
						form={form}
						layout="inline"
						className="mb-4 flex flex-wrap gap-4"
						onFinish={handleSearch}
					>
						<div className="flex flex-wrap gap-4">
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
								<Select allowClear placeholder="请选择模块" options={moduleOptions} className="min-w-[180px]" />
							</Form.Item>
							<Form.Item label="IP 地址" name="ip">
								<Input allowClear placeholder="请输入 IP 地址" style={{ width: 200 }} />
							</Form.Item>
							<Form.Item label="角色" name="actor">
								<Select allowClear placeholder="请选择角色" options={actorOptions} className="min-w-[160px]" />
							</Form.Item>
							<Form.Item label="目标类型" name="targetType">
								<Select allowClear placeholder="请选择目标类型" options={targetTypeOptions} className="min-w-[160px]" />
							</Form.Item>
						</div>
						<Collapse
							bordered={false}
							expandIconPosition="end"
							className="w-full rounded-md bg-muted/20 px-3 py-2"
							items={[
								{
									key: "more-filters",
									label: <span className="font-medium text-sm">更多筛选</span>,
									children: (
										<div className="flex flex-wrap gap-4 pt-2">
											<Form.Item label="操作结果" name="result">
												<Select allowClear placeholder="请选择结果" options={resultOptions} className="min-w-[160px]" />
											</Form.Item>
											<Form.Item label="操作者" name="person">
												<Input allowClear placeholder="支持模糊搜索操作者" style={{ width: 200 }} />
											</Form.Item>
											<Form.Item label="目标标识" name="targetId">
												<Input allowClear placeholder="支持模糊查询，例如 user:alice" style={{ width: 220 }} />
											</Form.Item>
										</div>
								),
							},
						]}
					/>
						<Form.Item>
							<Space>
								<Button type="primary" htmlType="submit" loading={loading}>
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
						dataSource={logs}
						loading={loading}
						rowClassName={() => "text-sm"}
						scroll={{ x: 1500 }}
						pagination={{
							current: pagination.current,
							pageSize: pagination.pageSize,
							total: pagination.total,
							defaultPageSize: DEFAULT_PAGE_SIZE,
							pageSizeOptions: ["10", "20", "50", "100"],
							showSizeChanger: true,
							showQuickJumper: true,
							showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
						}}
						rowKey="id"
						onChange={tableOnChange}
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
				width={860}
			>
				{selectedLog ? (
					<Spin spinning={detailLoading}>
						<div className="space-y-4">
							{(() => {
								const actionMeta = resolveActionMeta(selectedLog.action);
								return (
									<DetailSection title="基础信息">
										<DetailItem label="记录编号" value={`#${selectedLog.id}`} monospace />
										<DetailItem label="功能模块" value={formatLabel(selectedLog.module)} />
										<DetailItem label="操作类型" value={<Tag color={actionMeta.color}>{actionMeta.label}</Tag>} />
										<DetailItem label="操作者" value={selectedLog.actor} />
										<DetailItem label="结果" value={renderResult(selectedLog.result)} />
										<DetailItem label="发生时间" value={dayjs(selectedLog.occurredAt).format("YYYY-MM-DD HH:mm:ss")} />
									</DetailSection>
								);
							})()}

							<DetailSection title="资源信息">
								<DetailItem label="目标类型" value={formatLabel(selectedLog.resourceType)} />
								<DetailItem label="目标标识" value={selectedLog.resourceId || "-"} monospace />
							</DetailSection>

							<DetailSection title="请求上下文">
								<DetailItem label="HTTP 方法" value={selectedLog.httpMethod || "-"} />
								<DetailItem label="IP 地址" value={selectedLog.clientIp || "-"} monospace />
								<DetailItem label="User-Agent" value={selectedLog.clientAgent || "-"} full />
								<DetailItem label="标签" value={selectedLog.extraTags || "-"} full />
							</DetailSection>

							<DetailSection title="请求内容" columns={1}>
								<DetailItem
									label="Payload"
									value={
										<pre className="whitespace-pre-wrap break-all font-mono text-xs leading-relaxed">
											{formatPayload(selectedLog.payload) || selectedLog.payloadPreview || "-"}
										</pre>
									}
									full
								/>
							</DetailSection>
						</div>
					</Spin>
				) : (
					<Spin spinning />
				)}
			</Modal>
		</div>
	);
}

function resolveActionMeta(action: string) {
	if (!action) {
		return { label: "-", color: "default" };
	}
	const upper = action.toUpperCase();
	const direct = ACTION_TYPE_CONFIG[upper];
	if (direct) {
		return direct;
	}
	const label = upper
		.split(/[_\s]+/)
		.filter(Boolean)
		.map((segment) => segment.charAt(0) + segment.slice(1).toLowerCase())
		.join(" ");
	return { label: label || action, color: "default" };
}

function formatLabel(value?: string | null) {
	if (!value) {
		return "-";
	}
	return value
		.split(/[_\s]+/)
		.filter(Boolean)
		.map((segment) => segment.charAt(0) + segment.slice(1).toLowerCase())
		.join(" ");
}

function buildQueryParams(filters: AuditLogFilters): Record<string, string> {
	const params: Record<string, string> = {};
	const [start, end] = filters.dateRange ?? [];
	if (start) {
		params.from = start.toISOString();
	}
	if (end) {
		params.to = end.toISOString();
	}
	if (filters.module) {
		params.module = filters.module;
	}
	const actorKeyword = filters.person?.trim();
	const actorSelect = filters.actor?.trim();
	const actorFilter = actorKeyword || actorSelect;
	if (actorFilter) {
		params.actor = actorFilter;
	}
	if (filters.ip?.trim()) {
		params.clientIp = filters.ip.trim();
	}
	if (filters.targetType) {
		params.resourceType = filters.targetType;
	}
	if (filters.targetId?.trim()) {
		params.resource = filters.targetId.trim();
	}
	if (filters.result) {
		params.result = filters.result;
	}
	return params;
}

function renderResult(result?: string) {
	if (!result) {
		return "-";
	}
	const meta = RESULT_LABELS[result.toUpperCase?.() || ""];
	if (!meta) {
		return result;
	}
	return <Tag color={meta.color}>{meta.label}</Tag>;
}

function formatPayload(payload: unknown): string {
	if (payload == null) {
		return "";
	}
	if (typeof payload === "string") {
		return payload;
	}
	try {
		return JSON.stringify(payload, null, 2);
	} catch (err) {
		return String(payload);
	}
}
