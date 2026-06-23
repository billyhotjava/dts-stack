import { CaretRightOutlined } from "@ant-design/icons";
import { App as AntApp, Button, Space, Switch, Tabs, Tag } from "antd";
import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { ingestionService } from "@/mock/services/ingestionService";
import { useDepartmentStore } from "@/store/departmentStore";
import type { AccessChange, ScheduleJob } from "@/types/ingestion";
import { CompactTable, StatusDot } from "@/ui/components";
import type { CompactColumn, DotTone } from "@/ui/components";

const CHANGE_TONE: Record<AccessChange["status"], DotTone> = { pending: "warning", approved: "success", rejected: "error" };
const CHANGE_LABEL: Record<AccessChange["status"], string> = { pending: "待审", approved: "通过", rejected: "驳回" };
const JOB_TONE: Record<ScheduleJob["status"], DotTone> = { success: "success", failed: "error", idle: "muted" };
const JOB_LABEL: Record<ScheduleJob["status"], string> = { success: "成功", failed: "失败", idle: "空闲" };

function AccessChangesPanel({ departmentId }: { departmentId: string }) {
	const { message } = AntApp.useApp();
	const [rows, setRows] = useState<AccessChange[]>([]);
	useEffect(() => {
		void ingestionService.listAccessChanges(departmentId).then((r) => setRows(unwrap(r)));
	}, [departmentId]);

	const act = (r: AccessChange, ok: boolean) => {
		setRows((prev) => prev.map((x) => (x.id === r.id ? { ...x, status: ok ? "approved" : "rejected" } : x)));
		message.success(`${r.sourceName} · ${r.changeType} 已${ok ? "通过" : "驳回"}`);
	};

	const cols: CompactColumn<AccessChange>[] = [
		{ key: "sourceName", title: "数据源", render: (_v, r) => <span style={{ fontWeight: 600 }}>{r.sourceName}</span> },
		{ key: "changeType", title: "变更类型", width: 110, render: (_v, r) => <Tag>{r.changeType}</Tag> },
		{ key: "requester", title: "申请人", width: 110, dataIndex: "requester" },
		{ key: "at", title: "时间", width: 110, dataIndex: "at" },
		{ key: "status", title: "状态", width: 90, render: (_v, r) => <StatusDot tone={CHANGE_TONE[r.status]} label={CHANGE_LABEL[r.status]} /> },
		{
			key: "actions",
			title: "操作",
			width: 130,
			render: (_v, r) =>
				r.status === "pending" ? (
					<Space size={4}>
						<Button size="small" type="link" onClick={() => act(r, true)}>通过</Button>
						<Button size="small" type="link" danger onClick={() => act(r, false)}>驳回</Button>
					</Space>
				) : (
					<span style={{ color: "var(--ink-subtle)" }}>—</span>
				),
		},
	];
	return <CompactTable<AccessChange> columns={cols} data={rows} rowKey="id" />;
}

function SchedulesPanel({ departmentId }: { departmentId: string }) {
	const { message } = AntApp.useApp();
	const [rows, setRows] = useState<ScheduleJob[]>([]);
	useEffect(() => {
		void ingestionService.listSchedules(departmentId).then((r) => setRows(unwrap(r)));
	}, [departmentId]);

	const toggle = (r: ScheduleJob, enabled: boolean) => {
		setRows((prev) => prev.map((x) => (x.id === r.id ? { ...x, enabled } : x)));
		message.success(`${r.name} 已${enabled ? "启用" : "停用"}`);
	};

	const cols: CompactColumn<ScheduleJob>[] = [
		{ key: "name", title: "调度任务", width: 170, render: (_v, r) => <span style={{ fontWeight: 600 }}>{r.name}</span> },
		{ key: "sourceName", title: "数据源", render: (_v, r) => r.sourceName },
		{ key: "cron", title: "Cron", width: 110, render: (_v, r) => <span style={{ fontFamily: "var(--font-mono, monospace)", fontSize: 12 }}>{r.cron}</span> },
		{ key: "mode", title: "模式", width: 70, render: (_v, r) => <Tag color={r.mode === "增量" ? "blue" : "default"}>{r.mode}</Tag> },
		{ key: "watermark", title: "增量水位", render: (_v, r) => (r.watermark ? <span style={{ fontSize: 12, color: "var(--ink-muted)" }}>{r.watermark}</span> : "—") },
		{ key: "nextRun", title: "下次", width: 150, render: (_v, r) => r.nextRun ?? "—" },
		{ key: "status", title: "状态", width: 90, render: (_v, r) => <StatusDot tone={JOB_TONE[r.status]} label={JOB_LABEL[r.status]} /> },
		{
			key: "actions",
			title: "操作",
			width: 150,
			render: (_v, r) => (
				<Space size={6}>
					<Switch size="small" checked={r.enabled} onChange={(v) => toggle(r, v)} />
					<Button size="small" type="link" icon={<CaretRightOutlined />} onClick={() => message.info(`${r.name} 已触发立即运行`)}>
						运行
					</Button>
				</Space>
			),
		},
	];
	return <CompactTable<ScheduleJob> columns={cols} data={rows} rowKey="id" />;
}

/** 接入与调度 —— 接入变更审批 + 采集任务调度（按部门）。 */
export function SchedulingTab() {
	const deptId = useDepartmentStore((s) => s.currentDepartmentId);
	if (!deptId) return null;
	return (
		<Tabs
			defaultActiveKey="changes"
			items={[
				{ key: "changes", label: "接入变更", children: <AccessChangesPanel departmentId={deptId} /> },
				{ key: "schedules", label: "采集调度", children: <SchedulesPanel departmentId={deptId} /> },
			]}
		/>
	);
}
