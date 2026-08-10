import { ArrowRight, BarChart3, Boxes, Database, GitBranch, Ruler, Sparkles } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { type CompactColumns, CompactTable } from "@/components/table";
import { dataModelingPath } from "../navigation";
import type { DataModelingRoute } from "../types";
import { Button, PageHeader, RequestState, Status } from "./PrototypePrimitives";
import {
	loadModelingOverviewProjection,
	type ModelingOverviewProjection,
	normalizeModelingRequestFailure,
} from "./services/planningProjectionService";

const summaryIcons = [Database, GitBranch, Ruler, BarChart3];

export function OverviewPage({ route }: { route: DataModelingRoute }) {
	const navigate = useNavigate();
	const [projection, setProjection] = useState<ModelingOverviewProjection | null>(null);
	const [loading, setLoading] = useState(true);
	const [failure, setFailure] = useState<{ kind: "permission" | "request"; message: string } | null>(null);
	const load = useCallback(async () => {
		setLoading(true);
		setFailure(null);
		try {
			setProjection(await loadModelingOverviewProjection());
		} catch (error) {
			setProjection(null);
			setFailure(normalizeModelingRequestFailure(error, "建模概览读取失败，请稍后重新加载。"));
		} finally {
			setLoading(false);
		}
	}, []);
	useEffect(() => {
		void load();
	}, [load]);

	return (
		<main className="dmx-page dmx-overview">
			<PageHeader
				actions={
					<Button onClick={() => navigate(dataModelingPath("graphs", "models"))}>
						查看全景关系 <ArrowRight size={15} />
					</Button>
				}
				description={route.description}
				title="建模概览"
				trail="数据建模"
			/>
			{loading ? (
				<RequestState description="正在并发读取规划、模型、标准和指标事实。" kind="loading" title="正在加载建模概览" />
			) : failure ? (
				<RequestState
					description={failure.message}
					kind={failure.kind === "permission" ? "permission" : "error"}
					onRetry={failure.kind === "request" ? () => void load() : undefined}
					title={failure.kind === "permission" ? "无权访问建模概览" : "建模概览加载失败"}
				/>
			) : projection ? (
				<OverviewContent navigate={navigate} projection={projection} />
			) : (
				<RequestState description="服务端未返回可展示的建模事实。" kind="empty" title="暂无建模数据" />
			)}
		</main>
	);
}

function OverviewContent({
	projection,
	navigate,
}: {
	projection: ModelingOverviewProjection;
	navigate: ReturnType<typeof useNavigate>;
}) {
	const recentModelColumns = useMemo<CompactColumns<ModelingOverviewProjection["recentModels"][number]>>(
		() => [
			{
				title: "模型名称",
				dataIndex: "name",
				render: (name: string, model) => (
					<Button
						onClick={() =>
							navigate(`${dataModelingPath("dimensions", "workbench")}?modelSpecId=${encodeURIComponent(model.id)}`)
						}
						type="link"
					>
						{name}
					</Button>
				),
			},
			{ title: "模型类型", dataIndex: "type" },
			{ title: "数据域", dataIndex: "domain" },
			{ title: "版本", dataIndex: "version" },
			{
				title: "状态",
				dataIndex: "status",
				render: (status: string) => <Status tone={status === "PUBLISHED" ? "success" : "warning"}>{status}</Status>,
			},
			{ title: "更新时间", dataIndex: "updatedAt" },
		],
		[navigate],
	);
	return (
		<>
			<section className="dmx-summary-strip" aria-label="建模资产摘要">
				{projection.stats.map((item, index) => {
					const Icon = summaryIcons[index] || Boxes;
					return (
						<button key={item.label} onClick={() => navigate(`/data-modeling/${item.target}`)} type="button">
							<span className="dmx-summary-icon">
								<Icon size={19} />
							</span>
							<span>
								<small>{item.label}</small>
								<strong>{item.value}</strong>
								<em>{item.meta}</em>
							</span>
						</button>
					);
				})}
			</section>
			<div className="dmx-overview-grid">
				<section className="dmx-panel dmx-panel--wide">
					<header>
						<h2>最近模型</h2>
						<Button onClick={() => navigate(dataModelingPath("dimensions", "workbench"))} type="link">
							进入维度建模 <ArrowRight size={14} />
						</Button>
					</header>
					{projection.recentModels.length ? (
						<CompactTable<ModelingOverviewProjection["recentModels"][number]>
							columns={recentModelColumns}
							dataSource={projection.recentModels}
							pagination={false}
							rowKey="id"
						/>
					) : (
						<RequestState description="当前租户尚无 ModelSpec。" kind="empty" title="暂无模型" />
					)}
				</section>
				<section className="dmx-panel">
					<header>
						<h2>交付状态</h2>
					</header>
					<div className="dmx-pipeline-list">
						{projection.delivery.map((item) => (
							<div key={item.label}>
								<span>
									{item.label}
									<b>{item.total ? `${item.count}/${item.total}` : "—"}</b>
								</span>
								<i>
									<em style={{ width: `${item.percent}%` }} />
								</i>
							</div>
						))}
					</div>
				</section>
				<section className="dmx-panel">
					<header>
						<h2>待处理</h2>
					</header>
					{projection.tasks.length ? (
						<ul className="dmx-task-list">
							{projection.tasks.map((task) => (
								<li key={task.id}>
									<b>!</b>
									<span title={`${task.object} · ${task.stage}`}>{task.task}</span>
								</li>
							))}
						</ul>
					) : (
						<RequestState description="当前没有待完善的模型草稿。" kind="empty" title="暂无待处理事项" />
					)}
				</section>
				<section className="dmx-panel dmx-panel--wide">
					<header>
						<h2>快速入口</h2>
						<Sparkles size={17} />
					</header>
					<div className="dmx-quick-actions">
						{[
							["规划数据域", "planning", "domains"],
							["维护字段标准", "standards", "fields"],
							["创建模型", "dimensions", "workbench"],
							["定义指标", "metrics", "atomic"],
							["查看关系图", "graphs", "models"],
						].map(([label, workspace, view]) => (
							<button
								key={label}
								onClick={() => navigate(dataModelingPath(workspace as Parameters<typeof dataModelingPath>[0], view))}
								type="button"
							>
								＋ {label}
							</button>
						))}
					</div>
				</section>
			</div>
		</>
	);
}
