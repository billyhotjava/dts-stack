import type { DataModelingRoute, DataModelingWorkspace } from "./types";

const ROUTES: Record<string, Omit<DataModelingRoute, "workspace" | "view">> = {
	"home/workspace": {
		title: "建模概览",
		description: "统一查看建模资产、交付状态和可执行的后续入口。",
	},
	"planning/business-categories": {
		title: "业务分类",
		description: "规划企业业务板块、业务线和主题边界。",
	},
	"planning/layers": {
		title: "数仓分层",
		description: "查看平台内置的贴源、公共和应用层统一分层规范。",
	},
	"planning/domains": {
		title: "数据域",
		description: "维护公共层的数据域及其业务职责。",
	},
	"planning/processes": {
		title: "业务过程",
		description: "抽象企业业务活动并建立分析过程目录。",
	},
	"planning/marts": {
		title: "数据集市",
		description: "规划面向消费场景的应用层数据集市。",
	},
	"planning/subjects": {
		title: "主题域",
		description: "维护应用层主题域及其服务对象。",
	},
	"planning/spaces": {
		title: "建模空间",
		description: "管理模型所属空间、负责人和协作边界。",
	},
	"planning/system": {
		title: "建模策略",
		description: "配置当前建模计划的默认业务分类、业务过程选择方式和交付策略。",
	},
	"standards/fields": {
		title: "字段标准",
		description: "统一字段名称、数据类型、长度和业务含义。",
	},
	"standards/codes": {
		title: "标准代码",
		description: "维护可复用的代码集、代码值和版本。",
	},
	"standards/roots": {
		title: "词根",
		description: "沉淀技术命名可复用的中英文词根。",
	},
	"standards/dictionary": {
		title: "命名词典",
		description: "管理业务名称、技术名称和词根组合规则。",
	},
	"standards/mappings": {
		title: "标准映射",
		description: "查看标准与模型字段之间的映射关系。",
	},
	"dimensions/workbench": {
		title: "模型工作台",
		description: "在一个工作区内完成对象选择、字段设计和交付检查。",
	},
	"dimensions/reverse": {
		title: "逆向建模",
		description: "导入外部 dbt 项目 ZIP，识别结构证据并生成可视化模型草稿。",
	},
	"metrics/composite": {
		title: "复合指标",
		description: "组合多个派生指标，表达跨过程分析口径。",
	},
	"metrics/derived": {
		title: "派生指标",
		description: "基于原子指标、修饰词和时间周期定义分析口径。",
	},
	"metrics/atomic": {
		title: "原子指标",
		description: "定义不可继续拆分的业务度量及其计算逻辑。",
	},
	"metrics/modifiers": {
		title: "修饰词",
		description: "维护限定指标统计范围的业务修饰词。",
	},
	"metrics/periods": {
		title: "时间周期",
		description: "统一指标统计和比较使用的时间周期。",
	},
	"tools/toolbox": {
		title: "工具箱",
		description: "使用建模导入、导出、校验和辅助生成工具。",
	},
	"tools/imports": {
		title: "导入记录",
		description: "查看模型和标准导入任务的执行记录。",
	},
	"tools/exports": {
		title: "导出记录",
		description: "查看模型和标准导出任务的执行记录。",
	},
	"graphs/models": {
		title: "模型关系",
		description: "查看规划、维度和模型对象之间的依赖关系。",
	},
	"graphs/standards": {
		title: "标准关系",
		description: "查看数据标准在模型和字段中的引用关系。",
	},
	"graphs/metrics": {
		title: "指标血缘",
		description: "追踪指标、模型字段和上游业务过程之间的血缘。",
	},
};

export const DEFAULT_DATA_MODELING_PATH = "/data-modeling/home/workspace";

export const DATA_MODELING_WORKSPACE_DEFAULTS: Record<DataModelingWorkspace, string> = {
	home: "workspace",
	planning: "business-categories",
	standards: "fields",
	dimensions: "workbench",
	metrics: "atomic",
	tools: "toolbox",
	graphs: "models",
};

const isWorkspace = (value: string): value is DataModelingWorkspace =>
	Object.hasOwn(DATA_MODELING_WORKSPACE_DEFAULTS, value);

export function resolveDataModelingRoute(pathname: string): DataModelingRoute {
	const segments = pathname
		.replace(/^\/+|\/+$/g, "")
		.split("/")
		.filter(Boolean);
	const workspaceCandidate = segments[1] || "home";
	const workspace: DataModelingWorkspace = isWorkspace(workspaceCandidate) ? workspaceCandidate : "home";
	const requestedView = segments[2] || DATA_MODELING_WORKSPACE_DEFAULTS[workspace];
	const requestedKey = `${workspace}/${requestedView}`;
	const fallbackView = DATA_MODELING_WORKSPACE_DEFAULTS[workspace];
	const fallbackKey = `${workspace}/${fallbackView}`;
	const resolved = ROUTES[requestedKey] || ROUTES[fallbackKey] || ROUTES["home/workspace"];

	return {
		workspace,
		view: ROUTES[requestedKey] ? requestedView : fallbackView,
		...resolved,
	};
}

export const dataModelingPath = (workspace: DataModelingWorkspace, view?: string) =>
	`/data-modeling/${workspace}/${view || DATA_MODELING_WORKSPACE_DEFAULTS[workspace]}`;
