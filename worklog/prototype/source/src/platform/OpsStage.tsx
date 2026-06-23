import { Tabs, Tag } from "antd";
import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { platformService } from "@/mock/services/platformService";
import type { AlertEvent, OpsJob } from "@/types/platform";
import { CompactTable, SectionTitle, StatusDot, Surface } from "@/ui/components";
import type { CompactColumn, DotTone } from "@/ui/components";

const JOB_TONE: Record<OpsJob["status"], DotTone> = { running: "active", success: "success", failed: "error", queued: "muted" };
const JOB_LABEL: Record<OpsJob["status"], string> = { running: "运行中", success: "成功", failed: "失败", queued: "排队" };
const ALERT_TONE: Record<AlertEvent["level"], DotTone> = { info: "muted", warn: "warning", error: "error" };

export function OpsStage() {
	const [jobs, setJobs] = useState<OpsJob[]>([]);
	const [alerts, setAlerts] = useState<AlertEvent[]>([]);
	useEffect(() => {
		void platformService.opsJobs().then((r) => setJobs(unwrap(r)));
		void platformService.alerts().then((r) => setAlerts(unwrap(r)));
	}, []);

	const counts = {
		running: jobs.filter((j) => j.status === "running").length,
		success: jobs.filter((j) => j.status === "success").length,
		failed: jobs.filter((j) => j.status === "failed").length,
	};

	const jobCols: CompactColumn<OpsJob>[] = [
		{ key: "name", title: "作业", render: (_v, r) => <span style={{ fontWeight: 600 }}>{r.name}</span> },
		{ key: "type", title: "类型", width: 80, render: (_v, r) => <Tag>{r.type}</Tag> },
		{ key: "status", title: "状态", width: 96, render: (_v, r) => <StatusDot tone={JOB_TONE[r.status]} label={JOB_LABEL[r.status]} pulse={r.status === "running"} /> },
		{ key: "lastRun", title: "最近运行", width: 150, dataIndex: "lastRun" },
		{ key: "duration", title: "耗时", width: 100, align: "right", render: (_v, r) => (r.durationMs ? `${(r.durationMs / 1000).toFixed(1)}s` : "—") },
	];
	const alertCols: CompactColumn<AlertEvent>[] = [
		{ key: "at", title: "时间", width: 150, dataIndex: "at" },
		{ key: "level", title: "级别", width: 80, render: (_v, r) => <StatusDot tone={ALERT_TONE[r.level]} label={r.level === "error" ? "错误" : r.level === "warn" ? "警告" : "信息"} /> },
		{ key: "source", title: "来源", width: 150, dataIndex: "source" },
		{ key: "message", title: "消息", dataIndex: "message" },
	];

	return (
		<div style={{ maxWidth: 1080, margin: "0 auto" }}>
			<SectionTitle kicker="平台 · 旁路" title="运维" desc="平台运行的可观测与运维（平台级）。" />
			<div style={{ display: "flex", gap: 12, marginBottom: 16 }}>
				<Surface pad="md" style={{ flex: 1 }}><div style={{ display: "flex", gap: 6, alignItems: "center", fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}><StatusDot tone="active" />运行中</div><div className="tnum" style={{ fontSize: "var(--text-xl)", fontWeight: 700, marginTop: 4 }}>{counts.running}</div></Surface>
				<Surface pad="md" style={{ flex: 1 }}><div style={{ display: "flex", gap: 6, alignItems: "center", fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}><StatusDot tone="success" />成功</div><div className="tnum" style={{ fontSize: "var(--text-xl)", fontWeight: 700, marginTop: 4 }}>{counts.success}</div></Surface>
				<Surface pad="md" style={{ flex: 1 }}><div style={{ display: "flex", gap: 6, alignItems: "center", fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}><StatusDot tone="error" />失败</div><div className="tnum" style={{ fontSize: "var(--text-xl)", fontWeight: 700, marginTop: 4 }}>{counts.failed}</div></Surface>
			</div>
			<Tabs
				defaultActiveKey="jobs"
				items={[
					{ key: "jobs", label: "作业实例", children: <CompactTable<OpsJob> columns={jobCols} data={jobs} rowKey="id" /> },
					{ key: "alerts", label: `告警 (${alerts.length})`, children: <CompactTable<AlertEvent> columns={alertCols} data={alerts} rowKey="id" /> },
				]}
			/>
		</div>
	);
}
