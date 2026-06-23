import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { assetService } from "@/mock/services/assetService";
import type { DataProduct } from "@/types/asset";
import { CompactTable, StatusDot } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";

export function DataProductsTab({ departmentId }: { departmentId: string }) {
	const [data, setData] = useState<DataProduct[]>([]);
	const [loading, setLoading] = useState(false);

	useEffect(() => {
		let alive = true;
		setLoading(true);
		void assetService.listDataProducts(departmentId).then((r) => {
			if (!alive) return;
			setData(unwrap(r));
			setLoading(false);
		});
		return () => {
			alive = false;
		};
	}, [departmentId]);

	const columns: CompactColumn<DataProduct>[] = [
		{ key: "name", title: "数据产品", render: (_v, r) => <span style={{ fontWeight: 600 }}>{r.name}</span> },
		{
			key: "status",
			title: "状态",
			width: 96,
			render: (_v, r) => <StatusDot tone={r.status === "published" ? "success" : "muted"} label={r.status === "published" ? "已发布" : "草稿"} />,
		},
		{ key: "datasets", title: "包含数据集", width: 110, align: "right", render: (_v, r) => `${r.datasetIds.length} 个` },
		{ key: "description", title: "描述", render: (_v, r) => r.description ?? "—" },
	];

	return (
		<div>
			<div style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)", marginBottom: 12 }}>
				数据产品把本部门数据集打包对外提供，供数据服务/驾驶舱消费。
			</div>
			<CompactTable<DataProduct> columns={columns} data={data} rowKey="id" loading={loading} />
		</div>
	);
}
