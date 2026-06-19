import { ArrowRightOutlined, CheckCircleFilled } from "@ant-design/icons";
import { Button, Empty } from "antd";
import { useNavigate } from "react-router";
import { useProjectStore } from "@/store/projectStore";
import { SectionTitle, StatusDot, Surface } from "@/ui/components";
import { currentStage, deriveStageStatuses, STAGES } from "@/shell/stages";
import type { StageStatus } from "@/types/project";

const STATUS_LABEL: Record<StageStatus, string> = { done: "完成", active: "进行中", todo: "待开始" };
const STATUS_TONE: Record<StageStatus, "done" | "active" | "todo"> = {
	done: "done",
	active: "active",
	todo: "todo",
};

export function ProjectPortal() {
	const navigate = useNavigate();
	const project = useProjectStore((s) => s.projects.find((p) => p.id === s.currentId) ?? null);

	if (!project) {
		return <Empty description="请选择一个项目" style={{ marginTop: 80 }} />;
	}

	const statuses = deriveStageStatuses(project);
	const next = currentStage(project);
	const allDone = STAGES.every((s) => s.isComplete(project.metrics));
	const metricValue: Record<string, number> = {
		connect: project.metrics.connectedSources,
		integrate: project.metrics.transformRunsSucceeded,
		assets: project.metrics.publishedDatasets,
		metrics: project.metrics.publishedIndicators,
	};
	const metricUnit: Record<string, string> = {
		connect: "已连通源",
		integrate: "跑通作业",
		assets: "发布数据集",
		metrics: "发布指标",
	};

	return (
		<div style={{ maxWidth: 1080, margin: "0 auto" }}>
			<SectionTitle kicker="项目门户" title={project.name} desc={project.description} />

			{/* 你的下一步 —— 引导卡 */}
			<Surface
				pad="lg"
				style={{
					borderLeft: "3px solid var(--accent)",
					display: "flex",
					alignItems: "center",
					justifyContent: "space-between",
					gap: 24,
					marginBottom: 24,
				}}
			>
				<div>
					<div
						style={{
							fontSize: 11,
							fontWeight: 600,
							letterSpacing: "0.08em",
							textTransform: "uppercase",
							color: "var(--accent)",
							marginBottom: 6,
						}}
					>
						{allDone ? "主线已贯通" : "你的下一步"}
					</div>
					<div style={{ fontSize: "var(--text-md)", fontWeight: 600, color: "var(--ink)" }}>
						{allDone ? (
							<span>
								<CheckCircleFilled style={{ color: "var(--success)", marginRight: 8 }} />
								连接 → 集成 → 资产 → 指标 已全部完成，可继续完善或发布。
							</span>
						) : (
							<span>
								阶段 {next.index} · {next.label}：{next.nextAction(project)}
							</span>
						)}
					</div>
				</div>
				<Button type="primary" size="large" onClick={() => navigate(next.path)}>
					{allDone ? "查看指标" : `前往${next.label}`} <ArrowRightOutlined />
				</Button>
			</Surface>

			{/* 4 阶段总览 */}
			<div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16 }}>
				{STAGES.map((stage) => {
					const status = statuses[stage.key];
					return (
						<Surface
							key={stage.key}
							pad="md"
							style={{ cursor: "pointer", transition: "border-color var(--dur) var(--ease-out)" }}
							className="portal-stage-card"
						>
							<button
								type="button"
								onClick={() => navigate(stage.path)}
								style={{
									all: "unset",
									cursor: "pointer",
									display: "block",
									width: "100%",
								}}
							>
								<div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
									<span style={{ fontSize: 11, color: "var(--ink-subtle)" }}>阶段 {stage.index}</span>
									<StatusDot tone={STATUS_TONE[status]} label={STATUS_LABEL[status]} pulse={status === "active"} />
								</div>
								<div style={{ fontSize: "var(--text-lg)", fontWeight: 650, marginTop: 8 }}>{stage.label}</div>
								<div style={{ display: "flex", alignItems: "baseline", gap: 6, marginTop: 12 }}>
									<span className="tnum" style={{ fontSize: "var(--text-xl)", fontWeight: 700, color: "var(--ink)" }}>
										{metricValue[stage.key]}
									</span>
									<span style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}>
										{metricUnit[stage.key]}
									</span>
								</div>
								<div style={{ fontSize: "var(--text-xs)", color: "var(--ink-subtle)", marginTop: 8 }}>
									{stage.doneWhen}
								</div>
							</button>
						</Surface>
					);
				})}
			</div>
		</div>
	);
}
