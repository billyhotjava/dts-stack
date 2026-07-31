import { Search } from "lucide-react";
import { useMemo, useState } from "react";
import {
	BackendPendingButton,
	DataTable,
	Panel,
	StatusTag,
	UiStageNotice,
	WorkspacePage,
} from "../components/WorkspacePage";
import type { DemoRow, TableColumn, WorkspacePageProps } from "../types";

type PlanningView = {
	action: string;
	scope: string;
	boundary: string;
	governance: string;
	columns: TableColumn[];
	rows: DemoRow[];
};

const sampleColumn: TableColumn = { key: "sample", title: "数据说明", width: 110 };

const planningViews: Record<string, PlanningView> = {
	"business-categories": {
		action: "新建业务分类",
		scope: "企业业务板块与业务线",
		boundary: "作为数据域、业务过程和模型对象的上级归属",
		governance: "统一编码、名称和启停状态",
		columns: [
			{ key: "code", title: "英文缩写", width: 170 },
			{ key: "name", title: "中文名称" },
			{ key: "englishName", title: "英文名称" },
			{ key: "description", title: "说明" },
			sampleColumn,
		],
		rows: [
			{
				code: "example_core",
				name: "示例核心业务",
				englishName: "example core business",
				description: "演示业务分类的页面布局",
				sample: "界面示例",
			},
			{
				code: "example_support",
				name: "示例支撑业务",
				englishName: "example support business",
				description: "演示多业务分类的列表形态",
				sample: "界面示例",
			},
		],
	},
	layers: {
		action: "新建数仓分层",
		scope: "贴源、公共加工与应用交付层",
		boundary: "约束不同模型类型的加工责任和交付位置",
		governance: "分层编码、层级类型和命名前缀",
		columns: [
			{ key: "code", title: "分层缩写", width: 130 },
			{ key: "name", title: "分层名称" },
			{ key: "type", title: "分层类型", width: 150 },
			{ key: "description", title: "职责说明" },
			{ key: "state", title: "状态", width: 130 },
			sampleColumn,
		],
		rows: [
			{
				code: "ODS",
				name: "贴源层",
				type: "贴源层",
				description: "保留上游结构并记录接入批次",
				state: "系统预置（示例）",
				sample: "界面示例",
			},
			{
				code: "DWD",
				name: "明细层",
				type: "公共层",
				description: "沉淀可复用的业务过程明细",
				state: "系统预置（示例）",
				sample: "界面示例",
			},
			{
				code: "ADS",
				name: "应用层",
				type: "应用层",
				description: "面向消费场景组织交付模型",
				state: "系统预置（示例）",
				sample: "界面示例",
			},
		],
	},
	domains: {
		action: "新建数据域",
		scope: "公共层业务主题集合",
		boundary: "约束业务过程、维度、模型和指标的归属范围",
		governance: "业务分类归属、负责人和发布状态",
		columns: [
			{ key: "code", title: "英文缩写", width: 160 },
			{ key: "name", title: "中文名称" },
			{ key: "englishName", title: "英文名称" },
			{ key: "category", title: "业务分类" },
			{ key: "state", title: "状态", width: 130 },
			sampleColumn,
		],
		rows: [
			{
				code: "example_domain",
				name: "示例数据域",
				englishName: "example data domain",
				category: "示例核心业务",
				state: "草稿（示例）",
				sample: "界面示例",
			},
			{
				code: "common_domain",
				name: "公共数据域",
				englishName: "common data domain",
				category: "示例支撑业务",
				state: "已发布（示例）",
				sample: "界面示例",
			},
		],
	},
	processes: {
		action: "新建业务过程",
		scope: "可度量的企业业务活动事件",
		boundary: "为明细事实、原子指标和分析过程提供统一语义",
		governance: "过程编码、数据域归属和业务定义",
		columns: [
			{ key: "code", title: "英文缩写", width: 180 },
			{ key: "name", title: "中文名称" },
			{ key: "englishName", title: "英文名称" },
			{ key: "domain", title: "数据域" },
			{ key: "state", title: "状态", width: 130 },
			sampleColumn,
		],
		rows: [
			{
				code: "example_event",
				name: "示例事件处理",
				englishName: "example event processing",
				domain: "示例数据域",
				state: "草稿（示例）",
				sample: "界面示例",
			},
			{
				code: "example_delivery",
				name: "示例结果交付",
				englishName: "example result delivery",
				domain: "公共数据域",
				state: "已发布（示例）",
				sample: "界面示例",
			},
		],
	},
	marts: {
		action: "新建数据集市",
		scope: "部门或消费场景的数据交付集合",
		boundary: "承载应用层模型、指标服务和报表消费",
		governance: "服务对象、管理边界和发布状态",
		columns: [
			{ key: "code", title: "英文缩写", width: 180 },
			{ key: "name", title: "中文名称" },
			{ key: "englishName", title: "英文名称" },
			{ key: "audience", title: "服务对象" },
			{ key: "state", title: "状态", width: 130 },
			sampleColumn,
		],
		rows: [
			{
				code: "example_analysis_mart",
				name: "示例分析集市",
				englishName: "example analytics mart",
				audience: "示例分析场景",
				state: "草稿（示例）",
				sample: "界面示例",
			},
			{
				code: "common_service_mart",
				name: "公共服务集市",
				englishName: "common service mart",
				audience: "公共消费场景",
				state: "已发布（示例）",
				sample: "界面示例",
			},
		],
	},
	subjects: {
		action: "新建主题域",
		scope: "数据集市内的具体分析主题",
		boundary: "约束应用表、指标看板和数据服务的主题归属",
		governance: "集市归属、主题编码和生命周期",
		columns: [
			{ key: "code", title: "英文缩写", width: 180 },
			{ key: "name", title: "中文名称" },
			{ key: "englishName", title: "英文名称" },
			{ key: "mart", title: "所属集市" },
			{ key: "state", title: "状态", width: 130 },
			sampleColumn,
		],
		rows: [
			{
				code: "example_overview",
				name: "示例综合分析",
				englishName: "example overview",
				mart: "示例分析集市",
				state: "草稿（示例）",
				sample: "界面示例",
			},
			{
				code: "common_monitoring",
				name: "公共运行监控",
				englishName: "common monitoring",
				mart: "公共服务集市",
				state: "已发布（示例）",
				sample: "界面示例",
			},
		],
	},
	spaces: {
		action: "新建建模空间",
		scope: "团队协作和模型对象隔离边界",
		boundary: "组织空间成员、默认数据源及规划对象",
		governance: "空间标识、成员权限和启停状态",
		columns: [
			{ key: "code", title: "空间标识", width: 190 },
			{ key: "name", title: "空间名称" },
			{ key: "owner", title: "负责人" },
			{ key: "source", title: "默认数据源" },
			{ key: "members", title: "成员数", width: 90 },
			{ key: "state", title: "状态", width: 100 },
			sampleColumn,
		],
		rows: [
			{
				code: "example_workspace",
				name: "示例建模空间",
				owner: "示例负责人",
				source: "示例数据源",
				members: 3,
				state: "启用（示例）",
				sample: "界面示例",
			},
		],
	},
	system: {
		action: "新增配置",
		scope: "建模模块的受控系统参数",
		boundary: "只展示建模编码、默认对象和初始化规则",
		governance: "配置变更需经过权限和审计控制",
		columns: [
			{ key: "code", title: "配置编码", width: 210 },
			{ key: "name", title: "配置名称" },
			{ key: "value", title: "示例值" },
			{ key: "scope", title: "作用范围", width: 150 },
			{ key: "state", title: "状态", width: 110 },
			sampleColumn,
		],
		rows: [
			{
				code: "MODEL_CODE_PATTERN",
				name: "模型编码规则",
				value: "{layer}_{subject}",
				scope: "建模空间（示例）",
				state: "启用（示例）",
				sample: "界面示例",
			},
			{
				code: "DEFAULT_OBJECT_POLICY",
				name: "默认对象策略",
				value: "受控创建",
				scope: "租户（示例）",
				state: "启用（示例）",
				sample: "界面示例",
			},
		],
	},
};

function matchesQuery(row: DemoRow, query: string) {
	const normalized = query.trim().toLocaleLowerCase();
	return !normalized || Object.values(row).some((value) => String(value).toLocaleLowerCase().includes(normalized));
}

export function PlanningWorkspace({ route }: WorkspacePageProps) {
	const [queries, setQueries] = useState<Record<string, string>>({});
	const config = planningViews[route.view] || planningViews["business-categories"];
	const query = queries[route.view] || "";
	const rows = useMemo(() => config.rows.filter((row) => matchesQuery(row, query)), [config.rows, query]);

	return (
		<WorkspacePage
			actions={<BackendPendingButton>{config.action}</BackendPendingButton>}
			description={route.description}
			eyebrow="数据建模 / 数仓规划"
			title={route.title}
		>
			<UiStageNotice />
			<Panel actions={<StatusTag tone="info">界面示例</StatusTag>} title="规划边界">
				<div className="dm-definition-grid">
					<div className="dm-definition-card">
						<span>管理范围</span>
						<strong>{config.scope}</strong>
					</div>
					<div className="dm-definition-card">
						<span>下游作用</span>
						<strong>{config.boundary}</strong>
					</div>
					<div className="dm-definition-card">
						<span>治理要求</span>
						<strong>{config.governance}</strong>
					</div>
				</div>
			</Panel>

			<Panel
				actions={
					<span className="dm-sample-caption">
						<StatusTag tone="info">界面示例</StatusTag>共 {rows.length} 条
					</span>
				}
				subtitle="当前列表仅用于确认信息密度、字段顺序和页面布局。"
				title={`${route.title}目录`}
			>
				<div className="dm-toolbar">
					<div className="dm-search-control">
						<Search aria-hidden="true" size={15} />
						<input
							aria-label={`搜索${route.title}`}
							className="dm-input"
							onChange={(event) => setQueries((current) => ({ ...current, [route.view]: event.target.value }))}
							placeholder="搜索名称、编码或说明"
							type="search"
							value={query}
						/>
					</div>
				</div>
				<DataTable columns={config.columns} rowKey="code" rows={rows} />
			</Panel>
		</WorkspacePage>
	);
}
