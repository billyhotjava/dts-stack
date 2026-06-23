import { SearchOutlined } from "@ant-design/icons";
import { Input, Tag } from "antd";
import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { assetService } from "@/mock/services/assetService";
import type { Dataset } from "@/types/asset";
import { CompactTable, StatusDot } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";

const LAYER_COLOR: Record<string, string> = { ODS: "default", DWD: "blue", ADS: "green" };

export function AssetCatalogTab({ departmentId, onOpen }: { departmentId: string; onOpen: (d: Dataset) => void }) {
	const [keyword, setKeyword] = useState("");
	const [data, setData] = useState<Dataset[]>([]);
	const [loading, setLoading] = useState(false);

	useEffect(() => {
		let alive = true;
		setLoading(true);
		void assetService.listDatasets(departmentId, keyword).then((r) => {
			if (!alive) return;
			setData(unwrap(r));
			setLoading(false);
		});
		return () => {
			alive = false;
		};
	}, [departmentId, keyword]);

	const columns: CompactColumn<Dataset>[] = [
		{
			key: "name",
			title: "数据集",
			width: 200,
			render: (_v, r) => (
				<button type="button" onClick={() => onOpen(r)} style={{ all: "unset", cursor: "pointer", color: "var(--accent)", fontWeight: 600, fontFamily: "var(--font-mono, monospace)" }}>
					{r.name}
				</button>
			),
		},
		{ key: "layer", title: "分层", width: 80, render: (_v, r) => <Tag color={LAYER_COLOR[r.layer]}>{r.layer}</Tag> },
		{ key: "kind", title: "类型", width: 70, render: (_v, r) => (r.kind === "table" ? "表" : "视图") },
		{
			key: "status",
			title: "状态",
			width: 96,
			render: (_v, r) => <StatusDot tone={r.status === "published" ? "success" : "muted"} label={r.status === "published" ? "已发布" : "草稿"} />,
		},
		{ key: "rowCount", title: "行数", width: 100, align: "right", render: (_v, r) => r.rowCount?.toLocaleString() ?? "—" },
		{ key: "qualityScore", title: "质量分", width: 80, align: "right", render: (_v, r) => r.qualityScore ?? "—" },
		{ key: "owner", title: "归口部门", dataIndex: "owner", width: 100 },
	];

	return (
		<div>
			<Input
				allowClear
				prefix={<SearchOutlined style={{ color: "var(--ink-subtle)" }} />}
				placeholder="搜索数据集名称/描述"
				value={keyword}
				onChange={(e) => setKeyword(e.target.value)}
				style={{ maxWidth: 320, marginBottom: 12 }}
			/>
			<CompactTable<Dataset> columns={columns} data={data} rowKey="id" loading={loading} />
		</div>
	);
}
