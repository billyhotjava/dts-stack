import { useEffect, useMemo, useState } from "react";
import { Button, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { AlertTriangle, CheckCircle2, RadioTower, RefreshCw, Send, TimerReset } from "lucide-react";
import {
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import {
	getPlatformEventSummary,
	listPlatformEvents,
	type PlatformEventDto,
	type PlatformEventPage,
	type PlatformEventSummary,
} from "@/api/platformApi";

const DISPATCH_STATUS_OPTIONS = [
	{ label: "全部分发状态", value: "" },
	{ label: "待分发", value: "PENDING" },
	{ label: "已发送", value: "SENT" },
	{ label: "失败", value: "FAILED" },
	{ label: "跳过", value: "SKIPPED" },
];

const STATUS_OPTIONS = [
	{ label: "全部事件状态", value: "" },
	{ label: "成功", value: "SUCCESS" },
	{ label: "失败", value: "FAILED" },
	{ label: "运行中", value: "RUNNING" },
	{ label: "阻断", value: "BLOCKED" },
];

const statusColor = (value?: string) => {
	const status = String(value || "").toUpperCase();
	if (["SUCCESS", "SENT"].includes(status)) return "green";
	if (["FAILED", "ERROR", "BLOCKED"].includes(status)) return "red";
	if (["PENDING", "RUNNING"].includes(status)) return "blue";
	if (status === "SKIPPED") return "default";
	return "default";
};

const formatDateTime = (value?: string) => {
	if (!value) return "-";
	try {
		return new Date(value).toLocaleString();
	} catch {
		return value;
	}
};

const topEntries = (value?: Record<string, number>) =>
	Object.entries(value || {})
		.sort((a, b) => Number(b[1] || 0) - Number(a[1] || 0))
		.slice(0, 6);

export default function PlatformEventObservabilityPage() {
	const [summary, setSummary] = useState<PlatformEventSummary | null>(null);
	const [page, setPage] = useState<PlatformEventPage>({ content: [], total: 0, page: 0, size: 20, totalPages: 0 });
	const [loading, setLoading] = useState(false);
	const [domain, setDomain] = useState("");
	const [eventType, setEventType] = useState("");
	const [status, setStatus] = useState("");
	const [dispatchStatus, setDispatchStatus] = useState("");

	const loadData = async (nextPage = 0, nextSize = page.size) => {
		setLoading(true);
		try {
			const [summaryResp, pageResp] = await Promise.all([
				getPlatformEventSummary().catch(() => null),
				listPlatformEvents({
					page: nextPage,
					size: nextSize,
					domain: domain.trim() || undefined,
					eventType: eventType.trim() || undefined,
					status: status || undefined,
					dispatchStatus: dispatchStatus || undefined,
				}),
			]);
			if (summaryResp) setSummary(summaryResp);
			setPage(pageResp || { content: [], total: 0, page: nextPage, size: nextSize, totalPages: 0 });
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadData(0);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [status, dispatchStatus]);

	const summaryCards = [
		{
			label: "事件总数",
			value: summary?.total ?? 0,
			note: summary?.kafkaEnabled ? `Kafka ${summary.kafkaTopic || "-"}` : "Kafka 未启用",
			icon: <RadioTower className="h-5 w-5" />,
			tone: "info" as const,
		},
		{
			label: "待分发",
			value: summary?.pending ?? 0,
			note: "outbox pending",
			icon: <TimerReset className="h-5 w-5" />,
		},
		{
			label: "已发送",
			value: summary?.sent ?? 0,
			note: "Kafka dispatch sent",
			icon: <Send className="h-5 w-5" />,
			tone: "success" as const,
		},
		{
			label: "失败",
			value: summary?.failed ?? 0,
			note: "dispatch failed",
			icon: (summary?.failed ?? 0) > 0 ? <AlertTriangle className="h-5 w-5" /> : <CheckCircle2 className="h-5 w-5" />,
			tone: (summary?.failed ?? 0) > 0 ? "warning" as const : "success" as const,
		},
	];

	const domainRows = useMemo(() => topEntries(summary?.byDomain), [summary?.byDomain]);
	const statusRows = useMemo(() => topEntries(summary?.byStatus), [summary?.byStatus]);

	const columns: ColumnsType<PlatformEventDto> = [
		{
			title: "事件",
			dataIndex: "eventType",
			key: "eventType",
			render: (value, record) => (
				<div>
					<Typography.Text strong>{value || "-"}</Typography.Text>
					<div className="text-xs text-muted-foreground">{record.eventId || "-"}</div>
				</div>
			),
		},
		{ title: "域", dataIndex: "domain", key: "domain", width: 110, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "聚合", dataIndex: "aggregateType", key: "aggregateType", width: 130, render: (v, r) => r.aggregateName || r.aggregateId || v || "-" },
		{ title: "状态", dataIndex: "status", key: "status", width: 100, render: (v) => <Tag color={statusColor(v)}>{v || "-"}</Tag> },
		{ title: "分发", dataIndex: "dispatchStatus", key: "dispatchStatus", width: 100, render: (v) => <Tag color={statusColor(v)}>{v || "-"}</Tag> },
		{ title: "次数", dataIndex: "dispatchAttempts", key: "dispatchAttempts", width: 80, render: (v) => v ?? 0 },
		{ title: "发生时间", dataIndex: "occurredAt", key: "occurredAt", width: 190, render: formatDateTime },
		{ title: "错误", dataIndex: "dispatchError", key: "dispatchError", width: 220, ellipsis: true, render: (v) => v || "-" },
	];

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="事件观测"
				actions={
					<Button icon={<RefreshCw className="h-4 w-4" />} loading={loading} onClick={() => void loadData(page.page)}>
						刷新
					</Button>
				}
			/>

			<PlatformSummaryCards items={summaryCards} />

			<div className="grid gap-4 xl:grid-cols-[1fr_1fr]">
				<PlatformSectionCard title="域分布">
					<Space wrap>
						{domainRows.length ? domainRows.map(([key, value]) => <Tag key={key}>{`${key}: ${value}`}</Tag>) : <Typography.Text type="secondary">暂无数据</Typography.Text>}
					</Space>
				</PlatformSectionCard>
				<PlatformSectionCard title="状态分布">
					<Space wrap>
						{statusRows.length ? statusRows.map(([key, value]) => <Tag color={statusColor(key)} key={key}>{`${key}: ${value}`}</Tag>) : <Typography.Text type="secondary">暂无数据</Typography.Text>}
					</Space>
				</PlatformSectionCard>
			</div>

			<PlatformSectionCard title="事件列表" bodyClassName="space-y-4">
				<div className="flex flex-col gap-3 lg:flex-row lg:items-center">
					<Input allowClear placeholder="domain" value={domain} onChange={(e) => setDomain(e.target.value)} className="lg:max-w-48" />
					<Input allowClear placeholder="eventType" value={eventType} onChange={(e) => setEventType(e.target.value)} className="lg:max-w-64" />
					<Select options={STATUS_OPTIONS} value={status} onChange={setStatus} className="lg:w-44" />
					<Select options={DISPATCH_STATUS_OPTIONS} value={dispatchStatus} onChange={setDispatchStatus} className="lg:w-44" />
					<Button onClick={() => void loadData(0)}>查询</Button>
				</div>
				<Table
					rowKey={(record) => record.id || record.eventId || ""}
					loading={loading}
					columns={columns}
					dataSource={page.content || []}
					pagination={{
						current: (page.page || 0) + 1,
						pageSize: page.size || 20,
						total: page.total || 0,
						showSizeChanger: true,
						onChange: (nextPage, nextSize) => void loadData(nextPage - 1, nextSize),
					}}
				/>
			</PlatformSectionCard>
		</div>
	);
}
