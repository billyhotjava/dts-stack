import { App as AntApp, Button, Space, Tag } from "antd";
import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { platformService } from "@/mock/services/platformService";
import type { AccessRequest } from "@/types/platform";
import { CompactTable, SectionTitle, StatusDot, Surface } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";

const CLASSIFICATION = [
	{ level: "公开", color: "default", desc: "可对外公开的数据" },
	{ level: "内部", color: "blue", desc: "限本所内部使用" },
	{ level: "秘密", color: "orange", desc: "限授权部门，脱敏后可用" },
	{ level: "机密", color: "red", desc: "强管控，密钥隔离，审计留痕" },
];

export function SecurityStage() {
	const { message } = AntApp.useApp();
	const [reqs, setReqs] = useState<AccessRequest[]>([]);
	useEffect(() => {
		void platformService.accessRequests().then((r) => setReqs(unwrap(r)));
	}, []);

	const act = (r: AccessRequest, ok: boolean) => {
		setReqs((prev) => prev.map((x) => (x.id === r.id ? { ...x, status: ok ? "approved" : "rejected" } : x)));
		message.success(`${r.dataset} 已${ok ? "通过" : "驳回"}`);
	};

	const cols: CompactColumn<AccessRequest>[] = [
		{ key: "dataset", title: "数据集", render: (_v, r) => <span style={{ fontWeight: 600, fontFamily: "var(--font-mono, monospace)" }}>{r.dataset}</span> },
		{ key: "requester", title: "申请人", width: 110, dataIndex: "requester" },
		{ key: "requesterDept", title: "申请部门", width: 110, dataIndex: "requesterDept" },
		{ key: "at", title: "时间", width: 110, dataIndex: "at" },
		{
			key: "status",
			title: "状态",
			width: 90,
			render: (_v, r) => <StatusDot tone={r.status === "approved" ? "success" : r.status === "rejected" ? "error" : "warning"} label={r.status === "pending" ? "待审" : r.status === "approved" ? "通过" : "驳回"} />,
		},
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

	return (
		<div style={{ maxWidth: 1080, margin: "0 auto" }}>
			<SectionTitle kicker="平台 · 旁路" title="安全" desc="数据分级、访问审批与密钥管控（平台级）。" />
			<div style={{ display: "flex", gap: 12, marginBottom: 16 }}>
				{CLASSIFICATION.map((c) => (
					<Surface key={c.level} pad="md" style={{ flex: 1 }}>
						<Tag color={c.color}>{c.level}</Tag>
						<div style={{ fontSize: 12, color: "var(--ink-muted)", marginTop: 6 }}>{c.desc}</div>
					</Surface>
				))}
			</div>
			<div style={{ fontWeight: 650, marginBottom: 8 }}>数据集访问审批</div>
			<CompactTable<AccessRequest> columns={cols} data={reqs} rowKey="id" />
		</div>
	);
}
