import { Tag } from "antd";
import { useEffect, useMemo, useState } from "react";
import { unwrap } from "@/mock/client";
import { assetService } from "@/mock/services/assetService";
import type { QualityRule } from "@/types/asset";
import { CompactTable, StatusDot, Surface } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";

const TONE = { pass: "success", warn: "warning", fail: "error" } as const;
const LABEL = { pass: "通过", warn: "预警", fail: "失败" } as const;

function StatCard({ label, value, tone }: { label: string; value: number; tone: "success" | "warning" | "error" }) {
	return (
		<Surface pad="md" style={{ flex: 1 }}>
			<div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}>
				<StatusDot tone={tone} /> {label}
			</div>
			<div className="tnum" style={{ fontSize: "var(--text-xl)", fontWeight: 700, marginTop: 4 }}>{value}</div>
		</Surface>
	);
}

export function QualityTab({ departmentId }: { departmentId: string }) {
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [loading, setLoading] = useState(false);

	useEffect(() => {
		let alive = true;
		setLoading(true);
		void assetService.listQualityByDepartment(departmentId).then((r) => {
			if (!alive) return;
			setRules(unwrap(r));
			setLoading(false);
		});
		return () => {
			alive = false;
		};
	}, [departmentId]);

	const stats = useMemo(
		() => ({
			pass: rules.filter((r) => r.status === "pass").length,
			warn: rules.filter((r) => r.status === "warn").length,
			fail: rules.filter((r) => r.status === "fail").length,
		}),
		[rules],
	);

	const columns: CompactColumn<QualityRule>[] = [
		{ key: "name", title: "规则", dataIndex: "name" },
		{ key: "dimension", title: "维度", width: 90, render: (_v, r) => <Tag>{r.dimension}</Tag> },
		{ key: "status", title: "结果", width: 90, render: (_v, r) => <StatusDot tone={TONE[r.status]} label={LABEL[r.status]} /> },
		{ key: "lastRun", title: "最近运行", dataIndex: "lastRun", width: 120 },
	];

	return (
		<div>
			<div style={{ display: "flex", gap: 12, marginBottom: 16 }}>
				<StatCard label="通过" value={stats.pass} tone="success" />
				<StatCard label="预警" value={stats.warn} tone="warning" />
				<StatCard label="失败" value={stats.fail} tone="error" />
			</div>
			<CompactTable<QualityRule> columns={columns} data={rules} rowKey="id" loading={loading} />
		</div>
	);
}
