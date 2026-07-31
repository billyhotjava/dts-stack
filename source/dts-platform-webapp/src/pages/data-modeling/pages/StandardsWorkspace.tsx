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

type StandardsView = {
	createLabel: string;
	importLabel: string;
	catalogDescription: string;
	columns: TableColumn[];
	rows: DemoRow[];
};

const sampleColumn: TableColumn = { key: "sample", title: "数据说明", width: 110 };

const standardsViews: Record<string, StandardsView> = {
	fields: {
		createLabel: "新建字段标准",
		importLabel: "导入字段标准",
		catalogDescription: "统一查看字段的技术定义、业务含义、版本和生效状态。",
		columns: [
			{ key: "code", title: "标准编码", width: 180 },
			{ key: "name", title: "标准名称" },
			{ key: "dataType", title: "数据类型", width: 150 },
			{ key: "definition", title: "业务定义" },
			{ key: "version", title: "版本", width: 90 },
			{ key: "state", title: "状态", width: 130 },
			sampleColumn,
		],
		rows: [
			{
				code: "STD_ENTITY_ID",
				name: "示例实体标识",
				dataType: "STRING",
				definition: "演示稳定业务标识的字段标准",
				version: "v1",
				state: "已生效（示例）",
				sample: "界面示例",
			},
			{
				code: "STD_EVENT_TIME",
				name: "示例事件时间",
				dataType: "TIMESTAMP",
				definition: "演示业务事件发生时间的字段标准",
				version: "v1",
				state: "草稿（示例）",
				sample: "界面示例",
			},
		],
	},
	codes: {
		createLabel: "新建标准代码",
		importLabel: "导入代码集",
		catalogDescription: "管理代码集、代码值数量、版本和生效范围。",
		columns: [
			{ key: "code", title: "代码集编码", width: 210 },
			{ key: "name", title: "代码集名称" },
			{ key: "valueCount", title: "代码值数", width: 110 },
			{ key: "scope", title: "适用范围" },
			{ key: "version", title: "版本", width: 90 },
			{ key: "state", title: "状态", width: 130 },
			sampleColumn,
		],
		rows: [
			{
				code: "CODE_BOOLEAN_FLAG",
				name: "示例是非标志",
				valueCount: 2,
				scope: "公共字段（示例）",
				version: "v1",
				state: "已生效（示例）",
				sample: "界面示例",
			},
			{
				code: "CODE_RECORD_STATUS",
				name: "示例记录状态",
				valueCount: 3,
				scope: "公共模型（示例）",
				version: "v1",
				state: "草稿（示例）",
				sample: "界面示例",
			},
		],
	},
	roots: {
		createLabel: "新建词根",
		importLabel: "导入词根",
		catalogDescription: "沉淀技术命名中可复用的中英文基本词元。",
		columns: [
			{ key: "code", title: "词根编码", width: 170 },
			{ key: "englishRoot", title: "英文词根" },
			{ key: "chineseMeaning", title: "中文含义" },
			{ key: "category", title: "词根分类" },
			{ key: "state", title: "状态", width: 130 },
			sampleColumn,
		],
		rows: [
			{
				code: "ROOT_ENTITY",
				englishRoot: "entity",
				chineseMeaning: "示例实体",
				category: "业务对象（示例）",
				state: "已生效（示例）",
				sample: "界面示例",
			},
			{
				code: "ROOT_EVENT",
				englishRoot: "event",
				chineseMeaning: "示例事件",
				category: "业务过程（示例）",
				state: "草稿（示例）",
				sample: "界面示例",
			},
		],
	},
	dictionary: {
		createLabel: "新建命名词条",
		importLabel: "导入命名词典",
		catalogDescription: "对齐业务名称、技术名称以及词根组合规则。",
		columns: [
			{ key: "code", title: "词条编码", width: 180 },
			{ key: "businessName", title: "业务名称" },
			{ key: "technicalName", title: "技术名称" },
			{ key: "type", title: "命名类型" },
			{ key: "roots", title: "引用词根" },
			{ key: "state", title: "状态", width: 130 },
			sampleColumn,
		],
		rows: [
			{
				code: "TERM_ENTITY_ID",
				businessName: "示例实体标识",
				technicalName: "entity_id",
				type: "字段名称（示例）",
				roots: "entity + id",
				state: "已生效（示例）",
				sample: "界面示例",
			},
			{
				code: "TERM_EVENT_TIME",
				businessName: "示例事件时间",
				technicalName: "event_time",
				type: "字段名称（示例）",
				roots: "event + time",
				state: "草稿（示例）",
				sample: "界面示例",
			},
		],
	},
	mappings: {
		createLabel: "新建标准映射",
		importLabel: "导入映射关系",
		catalogDescription: "查看字段标准、模型对象和模型字段之间的绑定关系。",
		columns: [
			{ key: "code", title: "映射标识", width: 180 },
			{ key: "standard", title: "标准编码" },
			{ key: "model", title: "模型对象" },
			{ key: "field", title: "模型字段" },
			{ key: "method", title: "映射方式", width: 130 },
			{ key: "state", title: "状态", width: 130 },
			sampleColumn,
		],
		rows: [
			{
				code: "MAP_EXAMPLE_001",
				standard: "STD_ENTITY_ID",
				model: "dim_example_entity",
				field: "entity_id",
				method: "直接映射（示例）",
				state: "有效（示例）",
				sample: "界面示例",
			},
			{
				code: "MAP_EXAMPLE_002",
				standard: "STD_EVENT_TIME",
				model: "fct_example_event",
				field: "event_time",
				method: "直接映射（示例）",
				state: "待确认（示例）",
				sample: "界面示例",
			},
		],
	},
};

function matchesQuery(row: DemoRow, query: string) {
	const normalized = query.trim().toLocaleLowerCase();
	return !normalized || Object.values(row).some((value) => String(value).toLocaleLowerCase().includes(normalized));
}

export function StandardsWorkspace({ route }: WorkspacePageProps) {
	const [queries, setQueries] = useState<Record<string, string>>({});
	const [states, setStates] = useState<Record<string, string>>({});
	const config = standardsViews[route.view] || standardsViews.fields;
	const query = queries[route.view] || "";
	const selectedState = states[route.view] || "all";
	const stateOptions = useMemo(() => Array.from(new Set(config.rows.map((row) => String(row.state)))), [config.rows]);
	const rows = useMemo(
		() =>
			config.rows.filter((row) => matchesQuery(row, query) && (selectedState === "all" || row.state === selectedState)),
		[config.rows, query, selectedState],
	);

	return (
		<WorkspacePage
			actions={
				<>
					<BackendPendingButton>{config.importLabel}</BackendPendingButton>
					<BackendPendingButton>{config.createLabel}</BackendPendingButton>
				</>
			}
			description={route.description}
			eyebrow="数据建模 / 数据标准"
			title={route.title}
		>
			<UiStageNotice />
			<Panel
				actions={
					<span className="dm-sample-caption">
						<StatusTag tone="info">界面示例</StatusTag>共 {rows.length} 条
					</span>
				}
				subtitle={config.catalogDescription}
				title={`${route.title}目录`}
			>
				<div className="dm-toolbar">
					<div className="dm-search-control">
						<Search aria-hidden="true" size={15} />
						<input
							aria-label={`搜索${route.title}`}
							className="dm-input"
							onChange={(event) => setQueries((current) => ({ ...current, [route.view]: event.target.value }))}
							placeholder="搜索编码、名称或业务定义"
							type="search"
							value={query}
						/>
					</div>
					<select
						aria-label="标准状态"
						className="dm-select dm-compact-select"
						onChange={(event) => setStates((current) => ({ ...current, [route.view]: event.target.value }))}
						value={selectedState}
					>
						<option value="all">全部状态</option>
						{stateOptions.map((state) => (
							<option key={state} value={state}>
								{state}
							</option>
						))}
					</select>
				</div>
				<DataTable columns={config.columns} rowKey="code" rows={rows} />
			</Panel>
		</WorkspacePage>
	);
}
