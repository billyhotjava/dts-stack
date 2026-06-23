import { Drawer, Empty, Tag } from "antd";
import { useMemo } from "react";
import { generateDbtModels } from "./dbt";
import { useTransformGraphStore } from "./transformGraphStore";

/**
 * “查看生成的 dbt” 只读抽屉。
 * 体现 dbt 隐藏：用户在画布上搭转换，dbt 模型由系统自动生成，此处仅只读查看。
 */
export function DbtPreviewDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
	const nodes = useTransformGraphStore((s) => s.nodes);
	const edges = useTransformGraphStore((s) => s.edges);
	const models = useMemo(() => (open ? generateDbtModels(nodes, edges) : []), [open, nodes, edges]);

	return (
		<Drawer open={open} width={560} onClose={onClose} title="查看生成的 dbt（只读）">
			<div style={{ fontSize: 12, color: "var(--ink-subtle)", marginBottom: 16 }}>
				以下 dbt 模型由画布转换图<strong>自动生成</strong>，普通流程无需手写；此处仅供查看与排查。
			</div>
			{models.length === 0 ? (
				<Empty description="画布暂无可生成的模型" />
			) : (
				<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
					{models.map((m) => (
						<div key={m.name} style={{ border: "1px solid var(--hairline)", borderRadius: "var(--radius-md)", overflow: "hidden" }}>
							<div style={{ display: "flex", alignItems: "center", gap: 8, padding: "8px 12px", background: "var(--surface-sunken)", borderBottom: "1px solid var(--hairline)" }}>
								<span style={{ fontWeight: 650, fontFamily: "var(--font-mono, monospace)" }}>{m.name}.sql</span>
								<Tag color={m.materialization === "table" ? "blue" : "default"}>{m.materialization}</Tag>
								{m.dependsOn.length ? <span style={{ marginLeft: "auto", fontSize: 11, color: "var(--ink-subtle)" }}>依赖 {m.dependsOn.length}</span> : null}
							</div>
							<pre
								style={{
									margin: 0,
									padding: 12,
									fontFamily: "var(--font-mono, monospace)",
									fontSize: 12,
									color: "var(--ink)",
									background: "var(--surface)",
									overflow: "auto",
								}}
							>
								{m.sql}
							</pre>
						</div>
					))}
				</div>
			)}
		</Drawer>
	);
}
