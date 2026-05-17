import { useEffect, useMemo, useState } from "react";
import { Button, Input, Progress, Select, Space, Tag, Timeline, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";
import { AlertTriangle, Boxes, CheckCircle2, DatabaseZap, GitBranch, RadioTower, RefreshCw, Send, TimerReset } from "lucide-react";
import { useNavigate } from "react-router";
import {
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import {
	getSprint27EventsConsole,
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
	const navigate = useNavigate();
	const [summary, setSummary] = useState<PlatformEventSummary | null>(null);
	const [page, setPage] = useState<PlatformEventPage>({ content: [], total: 0, page: 0, size: 20, totalPages: 0 });
	const [loading, setLoading] = useState(false);
	const [domain, setDomain] = useState("");
	const [eventType, setEventType] = useState("");
	const [status, setStatus] = useState("");
	const [dispatchStatus, setDispatchStatus] = useState("");
	const [detailRow, setDetailRow] = useState<PlatformEventDto | null>(null);

	const loadData = async (
		nextPage = 0,
		nextSize = page.size,
		overrides: Partial<{
			domain: string;
			eventType: string;
			status: string;
			dispatchStatus: string;
		}> = {},
	) => {
		const nextDomain = overrides.domain ?? domain;
		const nextEventType = overrides.eventType ?? eventType;
		const nextStatus = overrides.status ?? status;
		const nextDispatchStatus = overrides.dispatchStatus ?? dispatchStatus;
		setLoading(true);
		try {
			const snapshot = await getSprint27EventsConsole({
				page: nextPage,
				size: nextSize,
				domain: nextDomain.trim() || undefined,
				eventType: nextEventType.trim() || undefined,
				status: nextStatus || undefined,
				dispatchStatus: nextDispatchStatus || undefined,
			});
			if (snapshot?.summary) setSummary(snapshot.summary);
			setPage(snapshot?.page || { content: [], total: 0, page: nextPage, size: nextSize, totalPages: 0 });
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
	const severityRows = useMemo(() => topEntries(summary?.bySeverity), [summary?.bySeverity]);

	const dispatchTotal = Math.max(1, Number(summary?.pending || 0) + Number(summary?.sent || 0) + Number(summary?.failed || 0) + Number(summary?.skipped || 0));
	const lifecycleItems = [
		{ key: "pending", title: "写入 Outbox", count: summary?.pending ?? 0, status: (summary?.pending ?? 0) > 0 ? "processing" : "success" },
		{ key: "sent", title: "Kafka 外送", count: summary?.sent ?? 0, status: (summary?.sent ?? 0) > 0 ? "success" : "default" },
		{ key: "failed", title: "失败待查", count: summary?.failed ?? 0, status: (summary?.failed ?? 0) > 0 ? "warning" : "success" },
		{ key: "skipped", title: "策略跳过", count: summary?.skipped ?? 0, status: (summary?.skipped ?? 0) > 0 ? "default" : "success" },
	];

	const jumpToDispatchStatus = (value: string) => {
		setDispatchStatus(value);
		void loadData(0, page.size, { dispatchStatus: value });
	};

	const baseColumns: ColumnsType<PlatformEventDto> = [
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
		{ title: "动作", dataIndex: "action", key: "action", width: 100, render: (v) => v || "-" },
		{ title: "审计动作", dataIndex: "auditActionCode", key: "auditActionCode", width: 180, ellipsis: true, render: (v) => v || "-" },
		{ title: "发生时间", dataIndex: "occurredAt", key: "occurredAt", width: 190, render: formatDateTime , sorter: (a, b) => { const ta = a.occurredAt ? new Date(a.occurredAt as any).getTime() : 0; const tb = b.occurredAt ? new Date(b.occurredAt as any).getTime() : 0; return ta - tb; } },
		{ title: "错误", dataIndex: "dispatchError", key: "dispatchError", width: 220, ellipsis: true, render: (v) => v || "-" },
	];

	const columns = useMemo(
		() => appendDetailAction(baseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[],
	);

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="事件观测"
				actions={
					<Space wrap>
						<Button onClick={() => navigate("/explore/etl")}>ELT 控制台</Button>
						<Button onClick={() => navigate("/bi-apps/metrics/operations")}>指标运营台</Button>
						<Button onClick={() => navigate("/ops/audit-evidence")}>审计证据链</Button>
						<Button onClick={() => navigate("/ops/release-governance")}>发布治理</Button>
						<Button icon={<RefreshCw className="h-4 w-4" />} loading={loading} onClick={() => void loadData(page.page)}>
							刷新
						</Button>
					</Space>
				}
			/>

			<PlatformSummaryCards items={summaryCards} />

			<div className="grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
				<PlatformSectionCard title="分发生命周期">
					<div className="grid gap-3 md:grid-cols-4">
						{lifecycleItems.map((item) => (
							<button
								key={item.key}
								type="button"
								onClick={() => jumpToDispatchStatus(item.key.toUpperCase())}
								className="rounded-lg border border-border/70 bg-background p-4 text-left transition hover:border-primary/60 hover:bg-primary/5"
							>
								<div className="mb-3 flex items-center justify-between gap-2">
									<span className="text-sm font-medium text-foreground">{item.title}</span>
									<Tag color={item.status === "warning" ? "orange" : item.status === "processing" ? "blue" : item.status === "success" ? "green" : "default"}>
										{item.count}
									</Tag>
								</div>
								<Progress
									percent={Math.round((Number(item.count || 0) / dispatchTotal) * 100)}
									showInfo={false}
									status={item.status === "warning" ? "exception" : item.status === "processing" ? "active" : item.status === "success" ? "success" : "normal"}
								/>
							</button>
						))}
					</div>
				</PlatformSectionCard>

				<PlatformSectionCard title="风险分布">
					{severityRows.length || statusRows.length ? (
						<Timeline
							items={[...severityRows, ...statusRows].slice(0, 5).map(([key, value]) => ({
								color: ["FAILED", "ERROR", "BLOCKED", "HIGH", "CRITICAL"].includes(String(key).toUpperCase()) ? "red" : statusColor(key),
								children: (
									<div className="flex items-center justify-between gap-3">
										<Typography.Text>{key}</Typography.Text>
										<Tag color={statusColor(key)}>{value}</Tag>
									</div>
								),
							}))}
						/>
					) : (
						<div className="flex min-h-32 items-center justify-center rounded-lg border border-dashed border-border/70 text-sm text-muted-foreground">
							暂无风险分布
						</div>
					)}
				</PlatformSectionCard>
			</div>

			<div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
				<PlatformSectionCard title="观测入口">
					<Space direction="vertical" size={12} className="w-full">
						<Button block icon={<DatabaseZap className="h-4 w-4" />} onClick={() => navigate("/explore/etl")}>
							ELT 链路
						</Button>
						<Button block icon={<Boxes className="h-4 w-4" />} onClick={() => navigate("/bi-apps/metrics/operations")}>
							指标链路
						</Button>
						<Button block icon={<GitBranch className="h-4 w-4" />} onClick={() => navigate("/catalog/lineage/impact")}>
							血缘影响
						</Button>
						<Button block icon={<RadioTower className="h-4 w-4" />} onClick={() => navigate("/ops/release-governance")}>
							发布治理
						</Button>
					</Space>
				</PlatformSectionCard>

				<PlatformSectionCard title="域分布">
					<Space wrap>
						{domainRows.length ? (
							domainRows.map(([key, value]) => (
								<Button
									key={key}
									size="small"
									onClick={() => {
										setDomain(key);
										void loadData(0, page.size, { domain: key });
									}}
								>
									{`${key}: ${value}`}
								</Button>
							))
						) : (
							<Typography.Text type="secondary">暂无数据</Typography.Text>
						)}
					</Space>
				</PlatformSectionCard>
			</div>

			<PlatformSectionCard title="事件列表" bodyClassName="space-y-4">
				<div className="flex flex-col gap-3 lg:flex-row lg:items-center">
					<Input allowClear placeholder="事件域" value={domain} onChange={(e) => setDomain(e.target.value)} className="lg:max-w-48" />
					<Input allowClear placeholder="事件类型" value={eventType} onChange={(e) => setEventType(e.target.value)} className="lg:max-w-64" />
					<Select options={STATUS_OPTIONS} value={status} onChange={setStatus} className="lg:w-44" />
					<Select options={DISPATCH_STATUS_OPTIONS} value={dispatchStatus} onChange={setDispatchStatus} className="lg:w-44" />
					<Button type="primary" onClick={() => void loadData(0)}>查询</Button>
					<Button
						onClick={() => {
							setDomain("");
							setEventType("");
							setStatus("");
							setDispatchStatus("");
							void loadData(0, page.size, { domain: "", eventType: "", status: "", dispatchStatus: "" });
						}}
					>
						重置
					</Button>
				</div>
				<CompactTable<PlatformEventDto>
					rowKey={(record) => record.id || record.eventId || ""}
					loading={loading}
					columns={columns}
					dataSource={page.content || []}
					pagination={{
						current: (page.page || 0) + 1,
						pageSize: page.size || 10,
						total: page.total || 0,
						onChange: (nextPage, nextSize) => void loadData(nextPage - 1, nextSize),
					}}
				/>
			</PlatformSectionCard>
			<RecordDetailDrawer<PlatformEventDto>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={baseColumns}
				title="事件详情"
			/>
		</div>
	);
}
