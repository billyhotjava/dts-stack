import { Descriptions, Drawer, Tag } from "antd";
import { useMemo } from "react";
import { metricDbtSql } from "@/mock/services/metricService";
import type { Metric } from "@/types/metric";
import { Sparkline } from "@/ui/components";

/**
 * 指标详情抽屉。含口径/关联资产/走势，以及底层"生成的 dbt（只读）"——体现 dbt 隐藏。
 */
export function MetricDetailDrawer({ metric, onClose }: { metric: Metric | null; onClose: () => void }) {
	const sql = useMemo(() => (metric ? metricDbtSql(metric) : ""), [metric]);

	return (
		<Drawer
			open={Boolean(metric)}
			width={520}
			onClose={onClose}
			title={metric ? <span><Tag color={metric.status === "published" ? "green" : "default"}>{metric.status === "published" ? "已发布" : "草稿"}</Tag>{metric.name}</span> : ""}
		>
			{metric ? (
				<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
					<Descriptions column={1} size="small" bordered>
						<Descriptions.Item label="编码">
							<span style={{ fontFamily: "var(--font-mono, monospace)" }}>{metric.code}</span>
						</Descriptions.Item>
						<Descriptions.Item label="归口部门">{metric.owner}</Descriptions.Item>
						<Descriptions.Item label="口径">{metric.caliber}</Descriptions.Item>
						<Descriptions.Item label="表达式">
							<span style={{ fontFamily: "var(--font-mono, monospace)" }}>{metric.expression}</span>
						</Descriptions.Item>
						<Descriptions.Item label="单位">{metric.unit}</Descriptions.Item>
						<Descriptions.Item label="关联资产">{metric.datasetId}</Descriptions.Item>
						<Descriptions.Item label="当前值">
							<span className="tnum" style={{ fontWeight: 700 }}>{metric.currentValue ?? "—"}</span> {metric.unit}
						</Descriptions.Item>
						<Descriptions.Item label="走势">{metric.trend ? <Sparkline data={metric.trend} /> : "—"}</Descriptions.Item>
					</Descriptions>

					<div>
						<div style={{ fontSize: 12, color: "var(--ink-subtle)", marginBottom: 6 }}>
							底层 dbt 模型（<strong>自动生成 · 只读</strong>，普通流程无需手写）：
						</div>
						<pre
							style={{
								margin: 0,
								padding: 12,
								fontFamily: "var(--font-mono, monospace)",
								fontSize: 12,
								color: "var(--ink)",
								background: "var(--surface-sunken)",
								border: "1px solid var(--hairline)",
								borderRadius: "var(--radius-md)",
								overflow: "auto",
							}}
						>
							{sql}
						</pre>
					</div>
				</div>
			) : null}
		</Drawer>
	);
}
