import { Drawer, Input, Select, Tabs, Tag } from "antd";
import { useMemo } from "react";
import { CompactTable } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";
import type { TransformNodeKind } from "@/types/transform";
import { useTransformGraphStore } from "./transformGraphStore";

const KIND_LABEL: Record<TransformNodeKind, string> = {
	source: "源表",
	clean: "清洗",
	join: "连接",
	aggregate: "聚合",
	output: "输出",
};

interface PreviewRow {
	idx: number;
	order_no: string;
	cust: string;
	amount: number;
}

/** 生成 20 行 mock 预览数据（确定性）。 */
function previewRows(seed: string): PreviewRow[] {
	const base = seed.length * 7 + 1000;
	return Array.from({ length: 20 }, (_v, i) => ({
		idx: i + 1,
		order_no: `SO-${base + i}`,
		cust: `客户${((base + i) % 50) + 1}`,
		amount: ((base * (i + 3)) % 9000) + 100,
	}));
}

const PREVIEW_COLUMNS: CompactColumn<PreviewRow>[] = [
	{ key: "idx", title: "#", dataIndex: "idx", width: 48, align: "right" },
	{ key: "order_no", title: "order_no", dataIndex: "order_no" },
	{ key: "cust", title: "cust", dataIndex: "cust" },
	{ key: "amount", title: "amount", dataIndex: "amount", align: "right", render: (v) => (v as number).toLocaleString() },
];

/** 节点配置属性抽屉。随选中节点切换，编辑直接写回画布 store。 */
export function NodeConfigDrawer() {
	const selectedId = useTransformGraphStore((s) => s.selectedId);
	const node = useTransformGraphStore((s) => s.nodes.find((n) => n.id === s.selectedId) ?? null);
	const setSelected = useTransformGraphStore((s) => s.setSelected);
	const updateNodeData = useTransformGraphStore((s) => s.updateNodeData);

	const rows = useMemo(() => previewRows(selectedId ?? "x"), [selectedId]);

	const patch = (key: string, value: unknown) => {
		if (selectedId) updateNodeData(selectedId, { [key]: value });
	};
	const str = (key: string) => (node?.data[key] as string | undefined) ?? "";

	const renderConfig = () => {
		if (!node) return null;
		const kind = node.data.kind;
		const field = (label: string, el: React.ReactNode) => (
			<div style={{ marginBottom: 14 }}>
				<div style={{ fontSize: 12, color: "var(--ink-muted)", marginBottom: 4 }}>{label}</div>
				{el}
			</div>
		);
		return (
			<div>
				{field("节点名称", <Input value={node.data.label} onChange={(e) => patch("label", e.target.value)} />)}
				{kind === "source"
					? field("表名 / 来源", <Input value={node.data.sub ?? ""} placeholder="如 plm.orders" onChange={(e) => patch("sub", e.target.value)} />)
					: null}
				{kind === "clean" ? (
					<>
						{field("去重主键", <Input value={str("dedupKey")} placeholder="order_id" onChange={(e) => patch("dedupKey", e.target.value)} />)}
						{field(
							"策略",
							<Select
								style={{ width: "100%" }}
								value={str("strategy") || "latest"}
								onChange={(v) => patch("strategy", v)}
								options={[
									{ value: "latest", label: "保留最新" },
									{ value: "earliest", label: "保留最早" },
								]}
							/>,
						)}
					</>
				) : null}
				{kind === "join"
					? field("连接条件", <Input value={str("joinOn")} placeholder="a.cust_id = b.id" onChange={(e) => patch("joinOn", e.target.value)} />)
					: null}
				{kind === "aggregate" ? (
					<>
						{field("分组字段", <Input value={str("groupBy")} placeholder="dept, month" onChange={(e) => patch("groupBy", e.target.value)} />)}
						{field("聚合度量", <Input value={str("measure")} placeholder="sum(amount)" onChange={(e) => patch("measure", e.target.value)} />)}
					</>
				) : null}
				{kind === "output" ? (
					<>
						{field("目标表", <Input value={node.data.sub ?? ""} placeholder="ods_sales_wide" onChange={(e) => patch("sub", e.target.value)} />)}
						{field(
							"写入模式",
							<Select
								style={{ width: "100%" }}
								value={str("writeMode") || "overwrite"}
								onChange={(v) => patch("writeMode", v)}
								options={[
									{ value: "overwrite", label: "全量覆盖" },
									{ value: "append", label: "追加" },
									{ value: "merge", label: "增量合并" },
								]}
							/>,
						)}
					</>
				) : null}
			</div>
		);
	};

	return (
		<Drawer
			open={Boolean(node)}
			width={420}
			onClose={() => setSelected(null)}
			title={node ? <span><Tag color="blue">{KIND_LABEL[node.data.kind]}</Tag>{node.data.label}</span> : ""}
			mask={false}
		>
			{node ? (
				<Tabs
					defaultActiveKey="config"
					items={[
						{ key: "config", label: "配置", children: renderConfig() },
						{
							key: "preview",
							label: "数据预览",
							children: (
								<div>
									<div style={{ fontSize: 12, color: "var(--ink-subtle)", marginBottom: 8 }}>预览前 20 行（mock）</div>
									<CompactTable<PreviewRow> columns={PREVIEW_COLUMNS} data={rows} rowKey="idx" defaultPageSize={20} />
								</div>
							),
						},
					]}
				/>
			) : null}
		</Drawer>
	);
}
