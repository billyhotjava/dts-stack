import { Card, Tabs } from "antd";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import { GLOBAL_CONFIG } from "@/global-config";
import { SqlWorkbenchExperimental } from "@/components/sql/SqlWorkbenchExperimental";
import { QueryDatasetManager } from "@/components/sql/QueryDatasetManager";

export default function Page() {
	return (
		<div className="space-y-6">
			<PageHeader title="数据开发中心 / 即席查询" />
			<Card>
				{GLOBAL_CONFIG.enableSqlWorkbench ? (
					<Tabs
						defaultActiveKey="workbench"
						items={[
							{
								key: "workbench",
								label: "即席查询",
								children: <SqlWorkbenchExperimental />,
							},
							{
								key: "datasets",
								label: "查询数据集",
								children: <QueryDatasetManager />,
							},
						]}
					/>
				) : (
					<EmptyState
						title="SQL Workbench 未启用"
						description="请在环境变量中开启 VITE_ENABLE_SQL_WORKBENCH=true，或联系管理员。"
					/>
				)}
			</Card>
		</div>
	);
}
