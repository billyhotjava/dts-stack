import { Boxes, CheckCircle2, ClipboardList, Database, Layers3, RefreshCw, Route } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router";
import {
	classifyModelingLoadFailure,
	loadModelingHomeProjection,
	type ModelingHomeProjection,
	type ModelingLoadFailure,
} from "../adapters/planningHomeAdapter";
import { ActionButton, DataTable, EmptyState, Panel, StatusTag, WorkspacePage } from "../components/WorkspacePage";
import type { TableColumn, WorkspacePageProps } from "../types";
import "./home-planning-standards.css";

const recentColumns: TableColumn[] = [
	{ key: "name", title: "模型名称" },
	{ key: "type", title: "模型类型", width: 130 },
	{ key: "domain", title: "数据域", width: 190 },
	{ key: "version", title: "修订", width: 90 },
	{ key: "status", title: "状态", width: 130 },
	{ key: "updatedAt", title: "最近更新", width: 190 },
];

const taskColumns: TableColumn[] = [
	{ key: "task", title: "待处理事项" },
	{ key: "object", title: "建设计划", width: 220 },
	{ key: "stage", title: "阻塞阶段", width: 170 },
	{ key: "status", title: "状态", width: 100 },
	{ key: "next", title: "下一步", width: 180 },
];

const statIcons = [Database, Boxes, Layers3, CheckCircle2];

export function HomeWorkspace({ route }: WorkspacePageProps) {
	const [projection, setProjection] = useState<ModelingHomeProjection | null>(null);
	const [loading, setLoading] = useState(true);
	const [failure, setFailure] = useState<ModelingLoadFailure | null>(null);

	const refresh = useCallback(async () => {
		setLoading(true);
		setFailure(null);
		try {
			setProjection(await loadModelingHomeProjection());
		} catch (error) {
			setProjection(null);
			setFailure(classifyModelingLoadFailure(error, "建模概览加载失败，请稍后重新加载。"));
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void refresh();
	}, [refresh]);

	return (
		<WorkspacePage
			actions={
				<>
					<ActionButton disabled={loading} onClick={() => void refresh()} title="重新读取权威建模数据">
						<RefreshCw aria-hidden="true" size={15} />
						刷新
					</ActionButton>
					<Link className="dm-button dm-button--primary" to="/data-modeling/dimensions/workbench?create=1">
						新建模型
					</Link>
				</>
			}
			description={route.description}
			eyebrow="数据建模 / 建模概览"
			title={route.title}
		>
			{loading ? (
				<output className="dm-context-strip">
					<StatusTag tone="info">正在加载</StatusTag>
					正在汇总建设计划、模型、标准和指标的权威数据…
				</output>
			) : null}
			{failure ? (
				<Panel title={failure.kind === "permission" ? "无权访问建模概览" : "建模概览加载失败"}>
					<div className="dm-stage-notice" role="alert">
						<span>{failure.message}</span>
						<ActionButton onClick={() => void refresh()}>重新加载</ActionButton>
					</div>
				</Panel>
			) : null}

			{projection ? (
				<>
					<div className="dm-grid dm-grid--4 dm-home-stats">
						{projection.stats.map(({ label, value, meta }, index) => {
							const Icon = statIcons[index] || Database;
							return (
								<article className="dm-stat dm-home-stat" key={label}>
									<div className="dm-home-stat__top">
										<span className="dm-stat__label">{label}</span>
										<Icon aria-hidden="true" size={18} />
									</div>
									<div className="dm-stat__value">{value}</div>
									<div className="dm-stat__meta">{meta}</div>
								</article>
							);
						})}
					</div>

					<div className="dm-grid dm-grid--2 dm-home-overview-row">
						<Panel title="交付状态">
							{projection.delivery.every((stage) => stage.total === 0) ? (
								<EmptyState title="暂无交付证据" description="建设计划产生阶段证据后，这里将展示真实完成比例。" />
							) : (
								<div className="dm-delivery-list">
									{projection.delivery.map((stage) => (
										<div className="dm-delivery-item" key={stage.label}>
											<div>
												<span>{stage.label}</span>
												<strong>
													{stage.count}/{stage.total}
												</strong>
											</div>
											<div
												aria-label={`${stage.label} ${stage.percent}%`}
												aria-valuemax={100}
												aria-valuemin={0}
												aria-valuenow={stage.percent}
												className="dm-delivery-progress"
												role="progressbar"
											>
												<span style={{ width: `${stage.percent}%` }} />
											</div>
										</div>
									))}
								</div>
							)}
						</Panel>

						<Panel title="快速入口">
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

					<Panel title="最近更新模型">
						<DataTable
							columns={recentColumns}
							emptyText="暂无带权威更新时间的模型"
							rowKey="id"
							rows={projection.recentModels}
						/>
					</Panel>

					<Panel title="规划待办">
						<DataTable
							columns={taskColumns}
							emptyText="暂无由阶段门禁证明的待处理事项"
							rowKey="object"
							rows={projection.tasks}
						/>
					</Panel>
				</>
			) : null}
		</WorkspacePage>
	);
}
