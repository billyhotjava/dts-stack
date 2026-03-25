import { useGpmc } from "../GpmcApp";
import { executionKpis, delayTop10, ganttTasks, workloadData, stageDistribution } from "../mockData";

export default function ExecutionScreen() {
	const { layer, drillDown, setLayer } = useGpmc();

	return (
		<div>
			{layer !== "strategic" && (
				<div className="gpmc__breadcrumb">
					<span className="gpmc__breadcrumb-link" onClick={() => setLayer("strategic")}>战略层</span>
					<span className="gpmc__breadcrumb-sep">/</span>
					{layer === "execution" && (
						<>
							<span className="gpmc__breadcrumb-link" onClick={() => setLayer("control")}>管控层</span>
							<span className="gpmc__breadcrumb-sep">/</span>
						</>
					)}
					<span className="gpmc__breadcrumb-current">{layer === "control" ? "管控层" : "执行层"}</span>
				</div>
			)}

			{/* KPI Row */}
			<div className="gpmc__kpi-row">
				{executionKpis.map((kpi) => (
					<div key={kpi.key} className="gpmc__kpi-card gpmc__kpi-card--clickable" onClick={drillDown}>
						<div className="gpmc__kpi-label">{kpi.label}</div>
						<div className="gpmc__kpi-value">
							{kpi.value}
							{kpi.unit ? <span style={{ fontSize: 18, fontWeight: 600, marginLeft: 2 }}>{kpi.unit}</span> : null}
						</div>
						<div className="gpmc__kpi-sub">
							{kpi.sub && <span>{kpi.sub}</span>}
							{kpi.trend && <span className={`gpmc__kpi-trend gpmc__kpi-trend--${kpi.trendDir}`}>{kpi.trend}</span>}
							{kpi.tone && <span className={`gpmc__kpi-badge gpmc__kpi-badge--${kpi.tone}`}>{kpi.tone === "danger" ? "需关注" : "注意"}</span>}
						</div>
					</div>
				))}
			</div>

			{/* Gantt + Delay TOP10 */}
			<div className="gpmc__two-col">
				{/* Left: Gantt-like view */}
				<div className="gpmc__panel">
					<div className="gpmc__panel-header">
						<div>
							<div className="gpmc__panel-title">任务执行甘特</div>
							<div className="gpmc__panel-subtitle">执行甘特看板</div>
						</div>
					</div>
					<div className="gpmc__panel-body" style={{ padding: "12px 20px" }}>
						{ganttTasks.map((task, idx) => {
							const barColor = task.risk === "高" ? "var(--gpmc-danger)" : task.risk === "中" ? "var(--gpmc-warn)" : "var(--gpmc-primary)";
							return (
								<div key={idx} style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 10 }}>
									<div style={{ width: 140, fontSize: 13, fontWeight: 600, flexShrink: 0 }}>{task.name}</div>
									<div style={{ width: 100, fontSize: 11, color: "var(--gpmc-muted)", flexShrink: 0 }}>{task.project}</div>
									<div style={{ flex: 1 }}>
										<div className="gpmc__progress" style={{ height: 16, borderRadius: 4 }}>
											<div
												style={{ height: "100%", borderRadius: 4, width: `${task.progress}%`, background: barColor, transition: "width 0.3s" }}
											/>
										</div>
									</div>
									<span style={{ fontSize: 12, fontWeight: 600, width: 40, textAlign: "right" }}>{task.progress}%</span>
									<span className={`gpmc__kpi-badge gpmc__kpi-badge--${task.risk === "高" ? "danger" : task.risk === "中" ? "warn" : "info"}`}>
										{task.risk}
									</span>
								</div>
							);
						})}
					</div>
				</div>

				{/* Right: Stage + Workload */}
				<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
					{/* Stage distribution */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">阶段分布</div>
						</div>
						<div className="gpmc__panel-body">
							{stageDistribution.map((s) => (
								<div key={s.name} style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
									<div style={{ width: 8, height: 8, borderRadius: "50%", background: s.color, flexShrink: 0 }} />
									<span style={{ flex: 1, fontSize: 13 }}>{s.name}</span>
									<span style={{ fontWeight: 700 }}>{s.value}</span>
								</div>
							))}
						</div>
					</div>

					{/* Workload */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">科室负载</div>
						</div>
						<div className="gpmc__panel-body">
							{workloadData.map((w) => (
								<div key={w.dept} style={{ marginBottom: 10 }}>
									<div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 3 }}>
										<span>{w.dept}</span>
										<span style={{ fontWeight: 600 }}>{w.active} 在办 / {w.overdue} 延期</span>
									</div>
									<div className="gpmc__progress">
										<div className="gpmc__progress-fill gpmc__progress-fill--primary" style={{ width: `${Math.min(w.active * 3, 100)}%` }} />
									</div>
								</div>
							))}
						</div>
					</div>
				</div>
			</div>

			{/* Delay TOP 10 */}
			{(layer === "control" || layer === "execution") && (
				<div className="gpmc__panel" style={{ marginTop: 20 }}>
					<div className="gpmc__panel-header">
						<div>
							<div className="gpmc__panel-title">延期 TOP 10 项目</div>
							<div className="gpmc__panel-subtitle">延期排名</div>
						</div>
					</div>
					<div className="gpmc__panel-body" style={{ padding: 0 }}>
						<table className="gpmc__table">
							<thead>
								<tr><th>#</th><th>项目名称</th><th>延期天数</th><th>责任科室</th><th>风险等级</th></tr>
							</thead>
							<tbody>
								{delayTop10.map((item, idx) => (
									<tr key={idx} className="gpmc__table-row--clickable" onClick={drillDown}>
										<td style={{ fontWeight: 700, color: "var(--gpmc-muted)" }}>{idx + 1}</td>
										<td style={{ fontWeight: 600 }}>{item.name}</td>
										<td style={{ fontWeight: 700, color: "var(--gpmc-danger)" }}>{item.delay} 天</td>
										<td>{item.dept}</td>
										<td>
											<span className={`gpmc__kpi-badge gpmc__kpi-badge--${item.risk === "高" ? "danger" : item.risk === "中" ? "warn" : "info"}`}>
												{item.risk}
											</span>
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</div>
			)}
		</div>
	);
}
