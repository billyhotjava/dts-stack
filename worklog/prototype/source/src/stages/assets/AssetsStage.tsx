import { Empty, Tabs, Tag } from "antd";
import { useState } from "react";
import { useDepartmentStore } from "@/store/departmentStore";
import type { Dataset } from "@/types/asset";
import { SectionTitle } from "@/ui/components";
import { AssetCatalogTab } from "./AssetCatalogTab";
import { DataProductsTab } from "./DataProductsTab";
import { DatasetDetailDrawer } from "./DatasetDetailDrawer";
import { QualityTab } from "./QualityTab";

/**
 * 阶段③ 资产 —— 把转换产出沉淀为部门资产（资产归口部门）。
 * 子导航：资产目录 / 数据产品 / 质量。数据集详情含 字段/血缘/质量/权属。
 */
export function AssetsStage() {
	const dept = useDepartmentStore((s) => s.departments.find((d) => d.id === s.currentDepartmentId) ?? null);
	const [selected, setSelected] = useState<Dataset | null>(null);

	if (!dept) return <Empty description="请选择一个部门" style={{ marginTop: 80 }} />;

	return (
		<div style={{ maxWidth: 1080, margin: "0 auto" }}>
			<SectionTitle
				kicker="阶段 ③"
				title="资产"
				desc={`沉淀为可发现、可信任的部门数据资产。当前部门：${dept.name}`}
				extra={<Tag color="blue">S5</Tag>}
			/>
			<Tabs
				defaultActiveKey="catalog"
				items={[
					{ key: "catalog", label: "资产目录", children: <AssetCatalogTab departmentId={dept.id} onOpen={setSelected} /> },
					{ key: "products", label: "数据产品", children: <DataProductsTab departmentId={dept.id} /> },
					{ key: "quality", label: "质量", children: <QualityTab departmentId={dept.id} /> },
				]}
			/>
			<DatasetDetailDrawer dataset={selected} onClose={() => setSelected(null)} />
		</div>
	);
}
