import { ToolOutlined } from "@ant-design/icons";
import { Surface } from "@/ui/components";

/** 接入与调度 —— 接入变更审批 + 采集任务调度（F3，原型从简）。 */
export function SchedulingTab() {
	const items = ["接入变更申请与审批", "采集任务调度（cron/依赖）", "增量水位与回填", "接入审计"];
	return (
		<Surface pad="lg">
			<div style={{ display: "flex", alignItems: "center", gap: 8, color: "var(--ink-muted)", marginBottom: 16 }}>
				<ToolOutlined />
				<span style={{ fontSize: "var(--text-sm)" }}>接入变更与采集调度（后续完善）：</span>
			</div>
			<div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 10 }}>
				{items.map((it) => (
					<div
						key={it}
						style={{
							display: "flex",
							alignItems: "center",
							gap: 10,
							padding: "10px 14px",
							border: "1px solid var(--hairline)",
							borderRadius: "var(--radius-md)",
							background: "var(--surface-sunken)",
							fontSize: "var(--text-sm)",
						}}
					>
						<span style={{ width: 6, height: 6, borderRadius: "50%", background: "var(--accent)", flex: "0 0 auto" }} />
						{it}
					</div>
				))}
			</div>
		</Surface>
	);
}
