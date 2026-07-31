import { Download, FileCheck2, FileInput, FileOutput, ScanSearch, ShieldCheck, Upload } from "lucide-react";
import {
	ActionButton,
	BackendPendingButton,
	DataTable,
	Panel,
	StatusTag,
	UiStageNotice,
	WorkspacePage,
} from "../components/WorkspacePage";
import type { DemoRow, TableColumn, WorkspacePageProps } from "../types";
import "./tools-graphs.css";

const TOOL_CARDS = [
	{
		title: "模型批量导入",
		description: "读取受支持的模型模板并在提交前完成结构预检。",
		icon: FileInput,
	},
	{
		title: "模型批量导出",
		description: "按建模空间、分层和模型类型生成可交付文件。",
		icon: FileOutput,
	},
	{
		title: "字段标准检查",
		description: "检查技术名称、数据类型和字段标准映射完整性。",
		icon: ShieldCheck,
	},
	{
		title: "模型差异比较",
		description: "比较模型草稿、已发布版本和上游结构之间的差异。",
		icon: ScanSearch,
	},
	{
		title: "DDL 结构解析",
		description: "解析建表语句并形成待确认的字段结构草稿。",
		icon: FileCheck2,
	},
	{
		title: "交付清单生成",
		description: "汇总模型、标准、指标和物化前置检查项。",
		icon: Download,
	},
];

const HISTORY_COLUMNS: TableColumn[] = [
	{ key: "task", title: "任务名称" },
	{ key: "type", title: "类型", width: 120 },
	{ key: "scope", title: "对象范围" },
	{ key: "operator", title: "发起人", width: 120 },
	{ key: "time", title: "发起时间", width: 170 },
	{ key: "status", title: "状态", width: 110 },
];

const IMPORT_ROWS: DemoRow[] = [
	{
		id: "IMP-DEMO-001",
		task: "模型模板预检（界面示例）",
		type: "导入",
		scope: "公共层 / 维度表",
		operator: "示例用户",
		time: "2026-07-31 10:20",
		status: "待接入",
	},
];

const EXPORT_ROWS: DemoRow[] = [
	{
		id: "EXP-DEMO-001",
		task: "模型交付包（界面示例）",
		type: "导出",
		scope: "应用层 / 汇总表",
		operator: "示例用户",
		time: "2026-07-31 09:40",
		status: "待接入",
	},
];

function Toolbox() {
	return (
		<>
			<div className="dm-card-list dm-tool-grid">
				{TOOL_CARDS.map((tool) => {
					const Icon = tool.icon;
					return (
						<article className="dm-card dm-tool-card" key={tool.title}>
							<div className="dm-tool-card__icon">
								<Icon aria-hidden="true" size={21} />
							</div>
							<h3>{tool.title}</h3>
							<p>{tool.description}</p>
							<div className="dm-card__footer">
								<StatusTag tone="info">后台待接入</StatusTag>
								<ActionButton disabled title="后台能力将在界面评审通过后接入">
									打开
								</ActionButton>
							</div>
						</article>
					);
				})}
			</div>
			<Panel title="最近执行" subtitle="仅用于本轮界面布局评审，不代表真实任务记录。">
				<DataTable columns={HISTORY_COLUMNS} rowKey="id" rows={[...IMPORT_ROWS, ...EXPORT_ROWS]} />
			</Panel>
		</>
	);
}

function RecordList({ type }: { type: "imports" | "exports" }) {
	const isImport = type === "imports";
	const rows = isImport ? IMPORT_ROWS : EXPORT_ROWS;
	return (
		<Panel
			actions={
				<BackendPendingButton>
					{isImport ? <Upload aria-hidden="true" size={15} /> : <Download aria-hidden="true" size={15} />}
					{isImport ? "新建导入" : "新建导出"}
				</BackendPendingButton>
			}
			subtitle="任务执行、文件上传和结果下载将在后台阶段接入。"
			title={isImport ? "导入任务" : "导出任务"}
		>
			<div className="dm-toolbar">
				<input
					aria-label="搜索任务名称"
					className="dm-input dm-toolbar__search"
					placeholder="搜索任务名称"
					type="search"
				/>
				<select aria-label="任务状态" className="dm-select dm-record-filter" defaultValue="all">
					<option value="all">全部状态</option>
					<option value="pending">待接入</option>
					<option value="running">执行中</option>
					<option value="done">已完成</option>
				</select>
			</div>
			<DataTable columns={HISTORY_COLUMNS} rowKey="id" rows={rows} />
		</Panel>
	);
}

export function ToolsWorkspace({ route }: WorkspacePageProps) {
	return (
		<WorkspacePage
			actions={route.view === "toolbox" ? <BackendPendingButton>执行工具</BackendPendingButton> : undefined}
			description={route.description}
			eyebrow="通用工具"
			title={route.title}
		>
			<UiStageNotice />
			{route.view === "toolbox" ? <Toolbox /> : <RecordList type={route.view === "imports" ? "imports" : "exports"} />}
		</WorkspacePage>
	);
}
