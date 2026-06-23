import { Empty, Tag } from "antd";
import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { metricService } from "@/mock/services/metricService";
import type { Metric } from "@/types/metric";
import { Sparkline, Surface } from "@/ui/components";

function MetricCard({ m }: { m: Metric }) {
	const pct = m.target ? Math.min(100, Math.round(((m.currentValue ?? 0) / m.target) * 100)) : null;
	return (
		<Surface pad="md">
			<div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
				<span style={{ fontWeight: 600, fontSize: "var(--text-sm)" }}>{m.name}</span>
				<Tag color={m.status === "published" ? "green" : "default"} style={{ marginInlineEnd: 0 }}>
					{m.status === "published" ? "已发布" : "草稿"}
				</Tag>
			</div>
			<div style={{ display: "flex", alignItems: "baseline", gap: 4, marginTop: 8 }}>
				<span className="tnum" style={{ fontSize: "var(--text-2xl)", fontWeight: 700, lineHeight: 1 }}>{m.currentValue ?? "—"}</span>
				<span style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}>{m.unit}</span>
			</div>
			<div style={{ marginTop: 8 }}>{m.trend ? <Sparkline data={m.trend} width={220} height={36} /> : null}</div>
			{pct !== null ? (
				<div style={{ marginTop: 8 }}>
					<div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "var(--ink-subtle)", marginBottom: 4 }}>
						<span>目标 {m.target}{m.unit}</span>
						<span className="tnum">{pct}%</span>
					</div>
					<div style={{ height: 6, borderRadius: 999, background: "var(--surface-sunken)", overflow: "hidden" }}>
						<div style={{ width: `${pct}%`, height: "100%", background: pct >= 95 ? "var(--success)" : "var(--accent)" }} />
					</div>
				</div>
			) : null}
		</Surface>
	);
}

export function MetricDashboardTab({ departmentId }: { departmentId: string }) {
	const [metrics, setMetrics] = useState<Metric[]>([]);

	useEffect(() => {
		let alive = true;
		void metricService.listByDepartment(departmentId).then((r) => {
			if (alive) setMetrics(unwrap(r));
		});
		return () => {
			alive = false;
		};
	}, [departmentId]);

	if (metrics.length === 0) return <Empty description="本部门暂无指标" style={{ marginTop: 40 }} />;

	return (
		<div>
			<div style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)", marginBottom: 12 }}>
				部门指标看板（已发布指标对接领导驾驶舱跨部门聚合）。
			</div>
			<div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 16 }}>
				{metrics.map((m) => (
					<MetricCard key={m.id} m={m} />
				))}
			</div>
		</div>
	);
}
