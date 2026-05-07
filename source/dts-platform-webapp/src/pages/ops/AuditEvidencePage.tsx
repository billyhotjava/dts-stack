import { useEffect, useMemo, useState } from "react";
import { Button, Space, Tag, Timeline, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";
import { CheckCircle2, FileSearch, RefreshCw, ShieldCheck, Sigma } from "lucide-react";
import { useNavigate } from "react-router";
import {
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import {
	getSprint27AuditEvidence,
	type PlatformEventDto,
	type Sprint27SourceStatus,
} from "@/api/platformApi";

type EvidenceRow = {
	key: string;
	domain: string;
	action: string;
	auditActionCode: string;
	count: number;
	lastOccurredAt?: string;
	lastEventId?: string;
	status?: string;
	dispatchStatus?: string;
};

const formatDateTime = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
};

const tagColor = (value?: string) => {
	const normalized = String(value || "").toUpperCase();
	if (["SUCCESS", "SENT", "READY"].includes(normalized)) return "green";
	if (["FAILED", "ERROR", "BLOCKED"].includes(normalized)) return "red";
	if (["PENDING", "RUNNING"].includes(normalized)) return "blue";
	return "default";
};

const groupEvidence = (events: PlatformEventDto[]): EvidenceRow[] => {
	const grouped = new Map<string, EvidenceRow>();
	for (const event of events) {
		const domain = event.domain || "UNKNOWN";
		const action = event.action || event.eventType || "UNKNOWN";
		const auditActionCode = event.auditActionCode || "未绑定审计动作";
		const key = `${domain}:${action}:${auditActionCode}`;
		const current = grouped.get(key);
		if (!current) {
			grouped.set(key, {
				key,
				domain,
				action,
				auditActionCode,
				count: 1,
				lastOccurredAt: event.occurredAt,
				lastEventId: event.eventId,
				status: event.status,
				dispatchStatus: event.dispatchStatus,
			});
			continue;
		}
		current.count += 1;
		if (!current.lastOccurredAt || String(event.occurredAt || "") > String(current.lastOccurredAt || "")) {
			current.lastOccurredAt = event.occurredAt;
			current.lastEventId = event.eventId;
			current.status = event.status;
			current.dispatchStatus = event.dispatchStatus;
		}
	}
	return Array.from(grouped.values()).sort((a, b) => String(b.lastOccurredAt || "").localeCompare(String(a.lastOccurredAt || "")));
};

export default function AuditEvidencePage() {
	const navigate = useNavigate();
	const [loading, setLoading] = useState(false);
	const [events, setEvents] = useState<PlatformEventDto[]>([]);
	const [rows, setRows] = useState<EvidenceRow[]>([]);
	const [summary, setSummary] = useState<Record<string, any>>({});
	const [sources, setSources] = useState<Record<string, Sprint27SourceStatus>>({});
	const [detailRow, setDetailRow] = useState<EvidenceRow | null>(null);

	const loadData = async () => {
		setLoading(true);
		try {
			const snapshot = await getSprint27AuditEvidence();
			const nextEvents = Array.isArray(snapshot?.events) ? snapshot.events : [];
			setEvents(nextEvents);
			setRows(Array.isArray(snapshot?.rows) ? snapshot.rows : groupEvidence(nextEvents));
			setSummary(snapshot?.summary || {});
			setSources(snapshot?.sources || {});
		} catch {
			setEvents([]);
			setRows([]);
			setSummary({});
			setSources({});
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadData();
	}, []);

	const auditBound = Number(summary?.auditBound ?? events.filter((event) => !!event.auditActionCode).length);
	const failed = Number(summary?.failed ?? events.filter((event) => ["FAILED", "ERROR", "BLOCKED"].includes(String(event.status || "").toUpperCase())).length);
	const domains = useMemo(() => {
		if (summary?.domains && typeof summary.domains === "object") return Object.keys(summary.domains);
		return Array.from(new Set(events.map((event) => event.domain || "UNKNOWN")));
	}, [events, summary]);

	const summaryCards = [
		{
			label: "证据事件",
			value: events.length,
			note: "最近 100 条 outbox 事件",
			icon: <FileSearch className="h-5 w-5" />,
			tone: "info" as const,
		},
		{
			label: "审计绑定",
			value: auditBound,
			note: `${events.length ? Math.round((auditBound / events.length) * 100) : 0}% 已绑定 auditActionCode`,
			icon: <ShieldCheck className="h-5 w-5" />,
			tone: "success" as const,
		},
		{
			label: "覆盖域",
			value: domains.length,
			note: domains.slice(0, 4).join(" / ") || "暂无",
			icon: <Sigma className="h-5 w-5" />,
		},
		{
			label: "异常事件",
			value: failed,
			note: "失败、阻断或错误状态",
			icon: <CheckCircle2 className="h-5 w-5" />,
			tone: failed ? "warning" as const : "success" as const,
		},
	];

	const baseColumns: ColumnsType<EvidenceRow> = [
		{ title: "域", dataIndex: "domain", key: "domain", width: 120, render: (value) => <Tag>{value}</Tag> },
		{ title: "动作", dataIndex: "action", key: "action", width: 140 },
		{ title: "审计动作", dataIndex: "auditActionCode", key: "auditActionCode", ellipsis: true },
		{ title: "事件数", dataIndex: "count", key: "count", width: 100 },
		{ title: "业务状态", dataIndex: "status", key: "status", width: 110, render: (value) => <Tag color={tagColor(value)}>{value || "-"}</Tag> },
		{ title: "分发状态", dataIndex: "dispatchStatus", key: "dispatchStatus", width: 110, render: (value) => <Tag color={tagColor(value)}>{value || "-"}</Tag> },
		{ title: "最近发生", dataIndex: "lastOccurredAt", key: "lastOccurredAt", width: 190, render: formatDateTime , sorter: (a, b) => { const ta = a.lastOccurredAt ? new Date(a.lastOccurredAt as any).getTime() : 0; const tb = b.lastOccurredAt ? new Date(b.lastOccurredAt as any).getTime() : 0; return ta - tb; } },
		{ title: "最近事件", dataIndex: "lastEventId", key: "lastEventId", width: 220, ellipsis: true },
	];

	const columns = useMemo(
		() => appendDetailAction(baseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[],
	);

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="审计证据链"
				actions={
					<Space wrap>
						<Button onClick={() => navigate("/ops/events")}>事件观测</Button>
						<Button onClick={() => navigate("/ops/release-governance")}>发布治理</Button>
						<Button icon={<RefreshCw className="h-4 w-4" />} loading={loading} onClick={() => void loadData()}>
							刷新
						</Button>
					</Space>
				}
			/>

			<PlatformSummaryCards items={summaryCards} />

			<PlatformSectionCard title="数据源状态">
				<Space wrap>
					{Object.entries(sources).length ? Object.entries(sources).map(([key, source]) => (
						<Tag key={key} color={tagColor(source.status)}>
							{key}: {source.status}
						</Tag>
					)) : <Typography.Text type="secondary">暂无后端数据源状态</Typography.Text>}
				</Space>
			</PlatformSectionCard>

			<div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
				<PlatformSectionCard title="证据链口径">
					<Timeline
						items={[
							{ color: "green", children: "业务动作先记录审计动作，形成可追溯操作证据。" },
							{ color: "blue", children: "关键动作同步写入 outbox，保留 eventId、domain、aggregate、status 和 auditActionCode。" },
							{ color: failed ? "red" : "green", children: failed ? `当前有 ${failed} 条异常事件需要核查。` : "当前最近事件未发现失败或阻断状态。" },
						]}
					/>
				</PlatformSectionCard>

				<PlatformSectionCard title="覆盖域">
					<Space wrap>
						{domains.length ? domains.map((domain) => <Tag key={domain}>{domain}</Tag>) : <Typography.Text type="secondary">暂无事件域</Typography.Text>}
					</Space>
				</PlatformSectionCard>
			</div>

			<PlatformSectionCard title="审计动作覆盖" bodyClassName="pt-0">
				<CompactTable<EvidenceRow>
					rowKey="key"
					loading={loading}
					columns={columns}
					dataSource={rows}
					scroll={{ x: 1100 }}
				/>
			</PlatformSectionCard>
			<RecordDetailDrawer<EvidenceRow>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={baseColumns}
				title="审计动作详情"
			/>
		</div>
	);
}
