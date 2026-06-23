import { Empty, Tabs, Tag } from "antd";
import { useDepartmentStore } from "@/store/departmentStore";
import { SectionTitle } from "@/ui/components";
import { DictionaryTab } from "./DictionaryTab";
import { MetricDashboardTab } from "./MetricDashboardTab";
import { MetricListTab } from "./MetricListTab";
import { SemanticTab } from "./SemanticTab";

/**
 * 阶段④ 指标 —— 基于部门资产做可视化指标设计；语义建模支撑；dbt 隐藏在底层。
 * 指标归口部门；已发布指标对接领导驾驶舱（平台级跨部门聚合）。
 */
export function MetricsStage() {
	const dept = useDepartmentStore((s) => s.departments.find((d) => d.id === s.currentDepartmentId) ?? null);
	if (!dept) return <Empty description="请选择一个部门" style={{ marginTop: 80 }} />;

	return (
		<div style={{ maxWidth: 1080, margin: "0 auto" }}>
			<SectionTitle
				kicker="阶段 ④"
				title="指标"
				desc={`基于部门资产设计业务指标，dbt 在底层自动生成。当前部门：${dept.name}`}
				extra={<Tag color="blue">S6</Tag>}
			/>
			<Tabs
				defaultActiveKey="metrics"
				items={[
					{ key: "metrics", label: "指标", children: <MetricListTab departmentId={dept.id} /> },
					{ key: "dashboard", label: "指标看板", children: <MetricDashboardTab departmentId={dept.id} /> },
					{ key: "semantic", label: "语义建模", children: <SemanticTab departmentId={dept.id} /> },
					{ key: "dict", label: "字典", children: <DictionaryTab departmentId={dept.id} /> },
				]}
			/>
		</div>
	);
}
