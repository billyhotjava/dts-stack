import { CaretRightOutlined } from "@ant-design/icons";
import { Button, Tabs } from "antd";
import { CompactTable, StatusDot } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";
import { type RunRecord, useTransformGraphStore } from "./transformGraphStore";

const RUN_COLUMNS: CompactColumn<RunRecord>[] = [
	{ key: "startedAt", title: "开始时间", dataIndex: "startedAt", width: 180 },
	{
		key: "status",
		title: "状态",
		width: 100,
		render: (_v, r) => <StatusDot tone={r.status === "success" ? "success" : "error"} label={r.status === "success" ? "成功" : "失败"} />,
	},
	{ key: "rows", title: "行数", dataIndex: "rows", align: "right", width: 110, render: (v) => (v as number).toLocaleString() },
	{ key: "durationMs", title: "耗时", dataIndex: "durationMs", align: "right", width: 100, render: (v) => `${v}ms` },
];

/** 运行 dock：运行按钮 + 运行历史 + 日志。 */
export function RunDock() {
	const running = useTransformGraphStore((s) => s.running);
	const runs = useTransformGraphStore((s) => s.runs);
	const logs = useTransformGraphStore((s) => s.logs);
	const run = useTransformGraphStore((s) => s.run);

	return (
		<div style={{ border: "1px solid var(--hairline)", borderRadius: "var(--radius-md)", background: "var(--surface)", marginTop: 16 }}>
			<div style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 14px", borderBottom: "1px solid var(--hairline)" }}>
				<Button type="primary" icon={<CaretRightOutlined />} loading={running} onClick={run}>
					{running ? "运行中…" : "运行"}
				</Button>
				<span style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}>
					运行整图转换（mock 执行）；dbt 在底层自动生成并调度。
				</span>
			</div>
			<div style={{ padding: "0 14px 8px" }}>
				<Tabs
					defaultActiveKey="history"
					size="small"
					items={[
						{
							key: "history",
							label: `运行历史${runs.length ? ` (${runs.length})` : ""}`,
							children: <CompactTable<RunRecord> columns={RUN_COLUMNS} data={runs} rowKey="id" />,
						},
						{
							key: "logs",
							label: "日志",
							children: (
								<pre
									style={{
										margin: 0,
										maxHeight: 160,
										overflow: "auto",
										fontFamily: "var(--font-mono, monospace)",
										fontSize: 12,
										color: "var(--ink-muted)",
										background: "var(--surface-sunken)",
										padding: 12,
										borderRadius: "var(--radius-md)",
									}}
								>
									{logs.length ? logs.join("\n") : "（尚未运行）"}
								</pre>
							),
						},
					]}
				/>
			</div>
		</div>
	);
}
