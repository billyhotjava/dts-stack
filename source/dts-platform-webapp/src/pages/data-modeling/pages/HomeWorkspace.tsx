import { Boxes, CheckCircle2, ClipboardList, Database, Layers3, Route } from "lucide-react";
import { Link } from "react-router";
import {
	BackendPendingButton,
	DataTable,
	Panel,
	StatusTag,
	UiStageNotice,
	WorkspacePage,
} from "../components/WorkspacePage";
import type { DemoRow, TableColumn, WorkspacePageProps } from "../types";
import "./home-planning-standards.css";

const recentColumns: TableColumn[] = [
	{ key: "name", title: "模型名称" },
	{ key: "type", title: "模型类型", width: 130 },
	{ key: "domain", title: "数据域", width: 140 },
	{ key: "version", title: "版本", width: 90 },
	{ key: "status", title: "状态", width: 120 },
	{ key: "updatedAt", title: "最近访问", width: 150 },
	{ key: "sample", title: "数据说明", width: 110 },
];

const recentRows: DemoRow[] = [
	{
		name: "示例主题维度表",
		type: "维度表",
		domain: "示例数据域",
		version: "v2",
		status: "已发布（示例）",
		updatedAt: "今天 09:30",
		sample: "界面示例",
	},
	{
		name: "示例事件明细表",
		type: "明细表",
		domain: "示例数据域",
		version: "v1",
		status: "草稿（示例）",
		updatedAt: "昨天 16:20",
		sample: "界面示例",
	},
	{
		name: "示例日汇总表",
		type: "汇总表",
		domain: "公共数据域",
		version: "v1",
		status: "待评审（示例）",
		updatedAt: "昨天 14:05",
		sample: "界面示例",
	},
];

const taskColumns: TableColumn[] = [
	{ key: "task", title: "待处理事项" },
	{ key: "object", title: "对象", width: 220 },
	{ key: "stage", title: "所处阶段", width: 150 },
	{ key: "priority", title: "优先级", width: 100 },
	{ key: "sample", title: "数据说明", width: 110 },
];

const taskRows: DemoRow[] = [
	{
		task: "补充模型字段定义",
		object: "示例事件明细表",
		stage: "逻辑设计（示例）",
		priority: "高（示例）",
		sample: "界面示例",
	},
	{
		task: "检查字段标准映射",
		object: "示例主题维度表",
		stage: "标准映射（示例）",
		priority: "中（示例）",
		sample: "界面示例",
	},
	{
		task: "确认模型发布范围",
		object: "示例日汇总表",
		stage: "发布检查（示例）",
		priority: "普通（示例）",
		sample: "界面示例",
	},
];

const overviewStats = [
	{ label: "数据域", value: "2", meta: "界面示例", icon: Database },
	{ label: "业务维度", value: "4", meta: "界面示例", icon: Boxes },
	{ label: "逻辑模型", value: "6", meta: "界面示例", icon: Layers3 },
	{ label: "数据标准", value: "8", meta: "界面示例", icon: CheckCircle2 },
];

const deliveryStages = [
	{ label: "规划完成", value: 100, count: 2 },
	{ label: "标准映射", value: 75, count: 6 },
	{ label: "模型评审", value: 50, count: 3 },
	{ label: "发布就绪", value: 33, count: 2 },
];

function SampleMarker() {
	return <StatusTag tone="info">界面示例</StatusTag>;
}

function RecentModelsPanel() {
	return (
		<Panel actions={<SampleMarker />} title="最近访问">
			<DataTable columns={recentColumns} rowKey="name" rows={recentRows} />
		</Panel>
	);
}

function TasksPanel() {
	return (
		<Panel actions={<SampleMarker />} title="我的任务">
			<DataTable columns={taskColumns} rowKey="task" rows={taskRows} />
		</Panel>
	);
}

function WorkspaceOverview() {
	return (
		<>
			<div className="dm-grid dm-grid--4 dm-home-stats">
				{overviewStats.map(({ label, value, meta, icon: Icon }) => (
					<article className="dm-stat dm-home-stat" key={label}>
						<div className="dm-home-stat__top">
							<span className="dm-stat__label">{label}</span>
							<Icon aria-hidden="true" size={18} />
						</div>
						<div className="dm-stat__value">{value}</div>
						<div className="dm-stat__meta">{meta}</div>
					</article>
				))}
			</div>

			<div className="dm-grid dm-grid--2 dm-home-overview-row">
				<Panel actions={<SampleMarker />} title="交付状态">
					<div className="dm-delivery-list">
						{deliveryStages.map((stage) => (
							<div className="dm-delivery-item" key={stage.label}>
								<div>
									<span>{stage.label}</span>
									<strong>{stage.count}</strong>
								</div>
								<div
									aria-label={`${stage.label} ${stage.value}%（界面示例）`}
									aria-valuemax={100}
									aria-valuemin={0}
									aria-valuenow={stage.value}
									className="dm-delivery-progress"
									role="progressbar"
								>
									<span style={{ width: `${stage.value}%` }} />
								</div>
							</div>
						))}
					</div>
				</Panel>

				<Panel actions={<SampleMarker />} title="快速入口">
					<div className="dm-home-quick-links">
						<Link to="/data-modeling/planning/domains">
							<Route aria-hidden="true" size={17} />
							规划数据域
						</Link>
						<Link to="/data-modeling/standards/fields">
							<CheckCircle2 aria-hidden="true" size={17} />
							维护字段标准
						</Link>
						<Link to="/data-modeling/dimensions/workbench">
							<Layers3 aria-hidden="true" size={17} />
							进入模型工作台
						</Link>
						<Link to="/data-modeling/metrics/atomic">
							<ClipboardList aria-hidden="true" size={17} />
							定义原子指标
						</Link>
					</div>
				</Panel>
			</div>

			<RecentModelsPanel />
			<TasksPanel />
		</>
	);
}

export function HomeWorkspace({ route }: WorkspacePageProps) {
	return (
		<WorkspacePage
			actions={
				<>
					<SampleMarker />
					<BackendPendingButton>新建模型</BackendPendingButton>
				</>
			}
			description={route.description}
			eyebrow="数据建模 / 建模概览"
			title={route.title}
		>
			<UiStageNotice />
			<WorkspaceOverview />
		</WorkspacePage>
	);
}
