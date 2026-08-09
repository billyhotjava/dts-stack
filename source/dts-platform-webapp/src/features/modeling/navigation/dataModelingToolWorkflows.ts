export type DataModelingToolWorkflowGroup = "import" | "governance" | "evidence";

export type DataModelingToolWorkflowKey =
	| "dbt-zip-import"
	| "standard-package-import"
	| "standard-code-governance"
	| "lineage-import"
	| "model-release-gates"
	| "audit-evidence";

export interface DataModelingToolWorkflow {
	key: DataModelingToolWorkflowKey;
	title: string;
	description: string;
	group: DataModelingToolWorkflowGroup;
	path: string;
	owner: string;
	resultOwner: "目标流程";
}

const WORKFLOWS: readonly DataModelingToolWorkflow[] = [
	{
		key: "dbt-zip-import",
		title: "dbt ZIP 建模",
		description: "统一导入 dbt ZIP，完成安全检查、业务语义补齐和预检，并生成可视化模型及模型级高级 dbt 实现。",
		group: "import",
		path: "/data-modeling/dimensions/reverse",
		owner: "ModelSpec / 高级 dbt 实现",
		resultOwner: "目标流程",
	},
	{
		key: "standard-package-import",
		title: "数据标准包导入",
		description: "进入字段标准目录，通过真实标准包预检和应用流程维护权威标准。",
		group: "import",
		path: "/data-modeling/standards/fields",
		owner: "数据标准",
		resultOwner: "目标流程",
	},
	{
		key: "lineage-import",
		title: "血缘证据导入",
		description: "由资产血缘模块导入 dbt manifest 或同步 Addax 血缘，结果留在血缘 owner。",
		group: "import",
		path: "/catalog/lineage/import",
		owner: "Catalog 血缘",
		resultOwner: "目标流程",
	},
	{
		key: "standard-code-governance",
		title: "标准代码维护",
		description: "查询、维护和废弃标准代码集；权限、版本和审计由标准模块负责。",
		group: "governance",
		path: "/data-modeling/standards/codes",
		owner: "数据标准",
		resultOwner: "目标流程",
	},
	{
		key: "model-release-gates",
		title: "模型门禁与交付",
		description: "在模型工作台校验阶段门禁，并通过 build/publish intent 完成受控物化和发布。",
		group: "governance",
		path: "/data-modeling/dimensions/workbench",
		owner: "ModelSpec 发布候选",
		resultOwner: "目标流程",
	},
	{
		key: "audit-evidence",
		title: "平台审计证据",
		description: "查看服务端公共审计证据；本页不复制日志，也不生成前端模拟记录。",
		group: "evidence",
		path: "/ops/audit-evidence",
		owner: "平台审计",
		resultOwner: "目标流程",
	},
];

export const getDataModelingToolWorkflows = (group?: DataModelingToolWorkflowGroup) =>
	group ? WORKFLOWS.filter((workflow) => workflow.group === group) : [...WORKFLOWS];
