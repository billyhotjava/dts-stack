import { CloudServerOutlined, DeploymentUnitOutlined, PlusOutlined } from "@ant-design/icons";
import { App as AntApp, Button, Empty, Tag } from "antd";
import { useEffect, useState } from "react";
import { dataSourceService } from "@/mock/services/dataSourceService";
import type { AvailableSources } from "@/mock/services/dataSourceService";
import { unwrap } from "@/mock/client";
import { useDepartmentStore } from "@/store/departmentStore";
import type { DataSource, DataSourceStatus } from "@/types/datasource";
import { SectionTitle, StatusDot, Surface } from "@/ui/components";
import type { DotTone } from "@/ui/components";

const STATUS_TONE: Record<DataSourceStatus, DotTone> = { connected: "success", error: "error", untested: "muted" };
const STATUS_LABEL: Record<DataSourceStatus, string> = { connected: "已连通", error: "异常", untested: "未测试" };

function SourceRow({ ds, onBind }: { ds: DataSource; onBind: (ds: DataSource) => void }) {
	return (
		<div
			style={{
				display: "flex",
				alignItems: "center",
				gap: 12,
				padding: "10px 14px",
				border: "1px solid var(--hairline)",
				borderRadius: "var(--radius-md)",
				background: "var(--surface)",
			}}
		>
			<StatusDot tone={STATUS_TONE[ds.status]} />
			<div style={{ minWidth: 0, flex: 1 }}>
				<div style={{ fontWeight: 600, fontSize: "var(--text-base)" }}>{ds.name}</div>
				<div style={{ fontSize: "var(--text-xs)", color: "var(--ink-subtle)" }}>
					{ds.type} · {ds.connector} · {STATUS_LABEL[ds.status]} · {ds.owner}
				</div>
			</div>
			<Button size="small" icon={<PlusOutlined />} onClick={() => onBind(ds)}>
				绑定到项目空间
			</Button>
		</div>
	);
}

function Group({
	icon,
	title,
	hint,
	sources,
	onBind,
}: {
	icon: React.ReactNode;
	title: string;
	hint: string;
	sources: DataSource[];
	onBind: (ds: DataSource) => void;
}) {
	return (
		<Surface pad="md">
			<div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
				<span style={{ color: "var(--accent)" }}>{icon}</span>
				<span style={{ fontWeight: 650 }}>{title}</span>
				<Tag style={{ marginLeft: 4 }}>{sources.length}</Tag>
				<span style={{ marginLeft: "auto", fontSize: "var(--text-xs)", color: "var(--ink-subtle)" }}>{hint}</span>
			</div>
			{sources.length ? (
				<div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
					{sources.map((ds) => (
						<SourceRow key={ds.id} ds={ds} onBind={onBind} />
					))}
				</div>
			) : (
				<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可用源" />
			)}
		</Surface>
	);
}

/**
 * 阶段① 连接 —— 体现混合制归属与"选源绑定"语义：
 * 连接 = 从本部门可用源中勾选绑定（平台共享 + 本部门本地），
 * 物理注册/连通在平台/部门层；资产最终归口部门。
 */
export function ConnectStage() {
	const dept = useDepartmentStore((s) => s.departments.find((d) => d.id === s.currentDepartmentId) ?? null);
	const [sources, setSources] = useState<AvailableSources | null>(null);
	const { message } = AntApp.useApp();

	useEffect(() => {
		if (!dept) return;
		let alive = true;
		void dataSourceService.availableFor(dept.id).then((res) => {
			if (alive) setSources(unwrap(res));
		});
		return () => {
			alive = false;
		};
	}, [dept]);

	const onBind = (ds: DataSource) => message.info(`「${ds.name}」绑定 —— S2 实现完整绑定流程`);

	return (
		<div style={{ maxWidth: 1080, margin: "0 auto" }}>
			<SectionTitle
				kicker="阶段 ①"
				title="连接"
				desc="从本部门可用的数据源中选取并绑定；物理接入与密钥在平台/部门层统一管控，资产归口部门。"
				extra={<Tag color="blue">S2 完善</Tag>}
			/>
			<div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
				<Group
					icon={<CloudServerOutlined />}
					title="平台共享源"
					hint="网信中心统一登记 · 跨部门"
					sources={sources?.platform ?? []}
					onBind={onBind}
				/>
				<Group
					icon={<DeploymentUnitOutlined />}
					title={`本部门本地源${dept ? ` · ${dept.name}` : ""}`}
					hint="部门登记自管"
					sources={sources?.department ?? []}
					onBind={onBind}
				/>
			</div>
		</div>
	);
}
