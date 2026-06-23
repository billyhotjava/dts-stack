import { Tabs } from "antd";
import { useDepartmentStore } from "@/store/departmentStore";
import { SectionTitle } from "@/ui/components";
import { ConnectorsTab } from "./ConnectorsTab";
import { DataSourcesTab } from "./DataSourcesTab";
import { JdbcDriversTab } from "./JdbcDriversTab";
import { SchedulingTab } from "./SchedulingTab";

/**
 * 阶段① 连接 —— 部门数据接入。
 * 子导航：数据源（本部门可见 = 平台共享 + 本部门本地）/ 连接器 / 驱动 / 接入与调度。
 * 部门为主：平台共享源只读，本部门本地源可管；资产最终归口部门。
 */
export function ConnectStage() {
	const dept = useDepartmentStore((s) => s.departments.find((d) => d.id === s.currentDepartmentId) ?? null);

	return (
		<div style={{ maxWidth: 1080, margin: "0 auto" }}>
			<SectionTitle
				kicker="阶段 ①"
				title="连接"
				desc={`接入并管理数据源，是黄金主线的起点。${dept ? `当前部门：${dept.name}` : ""}`}
			/>
			<Tabs
				defaultActiveKey="sources"
				items={[
					{ key: "sources", label: "数据源", children: <DataSourcesTab /> },
					{ key: "connectors", label: "连接器", children: <ConnectorsTab /> },
					{ key: "drivers", label: "JDBC 驱动", children: <JdbcDriversTab /> },
					{ key: "scheduling", label: "接入与调度", children: <SchedulingTab /> },
				]}
			/>
		</div>
	);
}
