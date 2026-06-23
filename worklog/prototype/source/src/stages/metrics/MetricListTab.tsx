import { PlusOutlined } from "@ant-design/icons";
import { App as AntApp, Button, Space } from "antd";
import { useCallback, useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { assetService } from "@/mock/services/assetService";
import { metricService } from "@/mock/services/metricService";
import type { Dataset } from "@/types/asset";
import type { Metric } from "@/types/metric";
import { CompactTable, StatusDot } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";
import { MetricDetailDrawer } from "./MetricDetailDrawer";
import { MetricFormModal, type MetricFormValues } from "./MetricFormModal";

export function MetricListTab({ departmentId }: { departmentId: string }) {
	const { message } = AntApp.useApp();
	const [metrics, setMetrics] = useState<Metric[]>([]);
	const [datasets, setDatasets] = useState<Dataset[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [detail, setDetail] = useState<Metric | null>(null);

	const refresh = useCallback(async () => {
		setLoading(true);
		setMetrics(unwrap(await metricService.listByDepartment(departmentId)));
		setLoading(false);
	}, [departmentId]);

	useEffect(() => {
		void refresh();
	}, [refresh]);
	useEffect(() => {
		void assetService.listDatasets(departmentId).then((r) => setDatasets(unwrap(r)));
	}, [departmentId]);

	const onCreate = async (v: MetricFormValues) => {
		unwrap(await metricService.create(departmentId, { ...v, owner: "本部门", updatedAt: new Date().toISOString().slice(0, 10) }));
		message.success("已创建（草稿）");
		setModalOpen(false);
		await refresh();
	};
	const onPublish = async (m: Metric) => {
		unwrap(await metricService.publish(m.id));
		message.success(`${m.name} 已发布`);
		await refresh();
	};

	const columns: CompactColumn<Metric>[] = [
		{
			key: "name",
			title: "指标",
			width: 150,
			render: (_v, r) => (
				<button type="button" onClick={() => setDetail(r)} style={{ all: "unset", cursor: "pointer", color: "var(--accent)", fontWeight: 600 }}>
					{r.name}
				</button>
			),
		},
		{ key: "code", title: "编码", width: 160, render: (_v, r) => <span style={{ fontFamily: "var(--font-mono, monospace)", fontSize: 12 }}>{r.code}</span> },
		{ key: "caliber", title: "口径", render: (_v, r) => r.caliber },
		{ key: "unit", title: "单位", width: 60, align: "center" },
		{
			key: "status",
			title: "状态",
			width: 96,
			render: (_v, r) => <StatusDot tone={r.status === "published" ? "success" : "muted"} label={r.status === "published" ? "已发布" : "草稿"} />,
		},
		{
			key: "actions",
			title: "操作",
			width: 150,
			render: (_v, r) => (
				<Space size={4}>
					<Button size="small" type="link" onClick={() => setDetail(r)}>详情</Button>
					<Button size="small" type="link" disabled={r.status === "published"} onClick={() => onPublish(r)}>
						发布
					</Button>
				</Space>
			),
		},
	];

	return (
		<div>
			<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
				<span style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}>指标归口部门，基于已发布数据集设计；dbt 底层自动生成。</span>
				<Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
					新建指标
				</Button>
			</div>
			<CompactTable<Metric> columns={columns} data={metrics} rowKey="id" loading={loading} />
			<MetricFormModal open={modalOpen} datasets={datasets} onSubmit={onCreate} onClose={() => setModalOpen(false)} />
			<MetricDetailDrawer metric={detail} onClose={() => setDetail(null)} />
		</div>
	);
}
