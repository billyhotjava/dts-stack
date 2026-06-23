import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { platformService } from "@/mock/services/platformService";
import type { AuditEvent } from "@/types/platform";
import { CompactTable, SectionTitle, StatusDot, Surface } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";

function Stat({ label, value }: { label: string; value: string | number }) {
	return (
		<Surface pad="md" style={{ flex: 1 }}>
			<div style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}>{label}</div>
			<div className="tnum" style={{ fontSize: "var(--text-xl)", fontWeight: 700, marginTop: 4 }}>{value}</div>
		</Surface>
	);
}

export function GovernStage() {
	const [events, setEvents] = useState<AuditEvent[]>([]);
	useEffect(() => {
		void platformService.auditEvents().then((r) => setEvents(unwrap(r)));
	}, []);

	const cols: CompactColumn<AuditEvent>[] = [
		{ key: "at", title: "时间", width: 150, dataIndex: "at" },
		{ key: "actor", title: "操作人", width: 110, dataIndex: "actor" },
		{ key: "action", title: "操作", width: 120, dataIndex: "action" },
		{ key: "resource", title: "资源", dataIndex: "resource" },
		{ key: "result", title: "结果", width: 90, render: (_v, r) => <StatusDot tone={r.result === "success" ? "success" : "error"} label={r.result === "success" ? "成功" : "拒绝"} /> },
	];

	return (
		<div style={{ maxWidth: 1080, margin: "0 auto" }}>
			<SectionTitle kicker="平台 · 旁路" title="治理" desc="跨阶段的治理与合规视角（平台级）。" />
			<div style={{ display: "flex", gap: 12, marginBottom: 16 }}>
				<Stat label="纳管资产" value={3} />
				<Stat label="质量规则" value={6} />
				<Stat label="审计事件(近7日)" value={events.length} />
				<Stat label="待审批" value={1} />
			</div>
			<div style={{ fontWeight: 650, marginBottom: 8 }}>权限审计</div>
			<CompactTable<AuditEvent> columns={cols} data={events} rowKey="id" />
		</div>
	);
}
