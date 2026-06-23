import { Descriptions, Drawer, Empty, Tabs, Tag } from "antd";
import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { assetService } from "@/mock/services/assetService";
import type { AssetGrant, Dataset, DatasetColumn, DatasetLineage, QualityRule } from "@/types/asset";
import { CompactTable, StatusDot } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";
import { LineageGraph } from "./LineageGraph";

const COL_COLUMNS: CompactColumn<DatasetColumn>[] = [
	{ key: "name", title: "字段", dataIndex: "name", render: (v) => <span style={{ fontFamily: "var(--font-mono, monospace)", fontWeight: 600 }}>{v as string}</span> },
	{ key: "type", title: "类型", dataIndex: "type", width: 120 },
	{ key: "comment", title: "注释", dataIndex: "comment" },
];
const QUAL_TONE = { pass: "success", warn: "warning", fail: "error" } as const;
const QUAL_LABEL = { pass: "通过", warn: "预警", fail: "失败" } as const;
const QUAL_COLUMNS: CompactColumn<QualityRule>[] = [
	{ key: "name", title: "规则", dataIndex: "name" },
	{ key: "dimension", title: "维度", dataIndex: "dimension", width: 90, render: (v) => <Tag>{v as string}</Tag> },
	{ key: "status", title: "结果", width: 90, render: (_v, r) => <StatusDot tone={QUAL_TONE[r.status]} label={QUAL_LABEL[r.status]} /> },
	{ key: "lastRun", title: "最近", dataIndex: "lastRun", width: 110 },
];
const GRANT_COLUMNS: CompactColumn<AssetGrant>[] = [
	{ key: "granteeDept", title: "被授权部门", dataIndex: "granteeDept" },
	{ key: "level", title: "级别", width: 90, render: (_v, r) => <Tag color={r.level === "write" ? "blue" : "default"}>{r.level === "write" ? "读写" : "只读"}</Tag> },
	{ key: "grantedAt", title: "授权时间", dataIndex: "grantedAt", width: 120 },
];

export function DatasetDetailDrawer({ dataset, onClose }: { dataset: Dataset | null; onClose: () => void }) {
	const [lineage, setLineage] = useState<DatasetLineage | null>(null);
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [grants, setGrants] = useState<AssetGrant[]>([]);

	useEffect(() => {
		if (!dataset) return;
		let alive = true;
		void Promise.all([
			assetService.getLineage(dataset.id),
			assetService.listQualityRules(dataset.id),
			assetService.listGrants(dataset.id),
		]).then(([l, q, g]) => {
			if (!alive) return;
			setLineage(unwrap(l));
			setRules(unwrap(q));
			setGrants(unwrap(g));
		});
		return () => {
			alive = false;
		};
	}, [dataset]);

	return (
		<Drawer
			open={Boolean(dataset)}
			width={620}
			onClose={onClose}
			title={dataset ? <span><Tag color={dataset.status === "published" ? "green" : "default"}>{dataset.status === "published" ? "已发布" : "草稿"}</Tag>{dataset.name}</span> : ""}
		>
			{dataset ? (
				<Tabs
					defaultActiveKey="overview"
					items={[
						{
							key: "overview",
							label: "概览",
							children: (
								<Descriptions column={1} size="small" bordered>
									<Descriptions.Item label="归口部门">{dataset.owner}</Descriptions.Item>
									<Descriptions.Item label="分层">{dataset.layer}</Descriptions.Item>
									<Descriptions.Item label="类型">{dataset.kind === "table" ? "表" : "视图"}</Descriptions.Item>
									<Descriptions.Item label="行数">{dataset.rowCount?.toLocaleString() ?? "—"}</Descriptions.Item>
									<Descriptions.Item label="质量分">{dataset.qualityScore ?? "—"}</Descriptions.Item>
									<Descriptions.Item label="更新">{dataset.updatedAt ?? "—"}</Descriptions.Item>
									<Descriptions.Item label="描述">{dataset.description ?? "—"}</Descriptions.Item>
								</Descriptions>
							),
						},
						{ key: "columns", label: `字段 (${dataset.columns.length})`, children: <CompactTable<DatasetColumn> columns={COL_COLUMNS} data={dataset.columns} rowKey="name" /> },
						{ key: "lineage", label: "血缘", children: lineage ? <LineageGraph lineage={lineage} /> : <Empty /> },
						{ key: "quality", label: `质量 (${rules.length})`, children: <CompactTable<QualityRule> columns={QUAL_COLUMNS} data={rules} rowKey="id" /> },
						{
							key: "grant",
							label: `权属 (${grants.length})`,
							children: (
								<div>
									<div style={{ fontSize: 12, color: "var(--ink-subtle)", marginBottom: 8 }}>
										归口部门：<strong>{dataset.owner}</strong>。跨部门取数通过以下授权。
									</div>
									{grants.length ? <CompactTable<AssetGrant> columns={GRANT_COLUMNS} data={grants} rowKey="id" /> : <Empty description="暂无跨部门授权" />}
								</div>
							),
						},
					]}
				/>
			) : null}
		</Drawer>
	);
}
