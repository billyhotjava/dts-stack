import { useGpmc } from "../GpmcApp";
import { costKpis, monthlySpend, deptBudget } from "../mockData";

export default function CostScreen() {
	const { layer, drillDown, setLayer } = useGpmc();

	const maxSpend = Math.max(...monthlySpend.map((m) => Math.max(m.plan, m.actual ?? 0)));

	return (
		<div>
			{/* Breadcrumb */}
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
				{costKpis.map((kpi) => (
					<div
						key={kpi.key}
						className="gpmc__kpi-card gpmc__kpi-card--clickable"
						onClick={drillDown}
					>
						<div className="gpmc__kpi-label">{kpi.label}</div>
						<div className="gpmc__kpi-value">
							{kpi.value}
							{kpi.unit === "%" ? <span style={{ fontSize: 18, fontWeight: 600 }}>%</span> : null}
						</div>
						<div className="gpmc__kpi-sub">
							{kpi.unit && kpi.unit !== "%" ? <span>{kpi.unit}</span> : null}
							{kpi.trend && (
								<span className={`gpmc__kpi-trend gpmc__kpi-trend--${kpi.trendDir}`}>{kpi.trend}</span>
							)}
							{kpi.tone && (
								<span className={`gpmc__kpi-badge gpmc__kpi-badge--${kpi.tone}`}>
									{kpi.tone === "danger" ? "超支" : "注意"}
								</span>
							)}
						</div>
					</div>
				))}
			</div>

			{/* Two-col layout */}
			<div className="gpmc__two-col">
				{/* Left panel: 月度支出趋势 */}
				<div className="gpmc__panel">
					<div className="gpmc__panel-header">
						<div>
							<div className="gpmc__panel-title">月度支出趋势</div>
							<div className="gpmc__panel-subtitle">月度支出趋势</div>
						</div>
						<div className="gpmc__panel-meta">
							<span style={{ display: "inline-flex", alignItems: "center", gap: 4, marginRight: 12 }}>
								<span style={{ display: "inline-block", width: 12, height: 4, backgroundColor: "var(--gpmc-primary)", borderRadius: 2 }} />
								<span style={{ fontSize: 11 }}>计划</span>
							</span>
							<span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
								<span style={{ display: "inline-block", width: 12, height: 4, backgroundColor: "var(--gpmc-success)", borderRadius: 2 }} />
								<span style={{ fontSize: 11 }}>实际</span>
							</span>
						</div>
					</div>
					<div className="gpmc__panel-body">
						<div style={{ display: "flex", alignItems: "flex-end", gap: 16, height: 180 }}>
							{monthlySpend.map((m, idx) => {
								const planH = (m.plan / maxSpend) * 150;
								const actualH = m.actual != null ? (m.actual / maxSpend) * 150 : 0;
								return (
									<div key={idx} style={{ display: "flex", flexDirection: "column", alignItems: "center", flex: 1 }}>
										<div style={{ display: "flex", alignItems: "flex-end", gap: 3, height: 155 }}>
											<div style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
												<span style={{ fontSize: 10, color: "var(--gpmc-muted)", marginBottom: 2 }}>{m.plan}</span>
												<div style={{
													width: 18,
													height: planH,
													backgroundColor: "var(--gpmc-primary)",
													borderRadius: "3px 3px 0 0",
													opacity: 0.7,
												}} />
											</div>
											<div style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
												<span style={{ fontSize: 10, color: "var(--gpmc-muted)", marginBottom: 2 }}>
													{m.actual != null ? m.actual : "-"}
												</span>
												<div style={{
													width: 18,
													height: actualH,
													backgroundColor: m.actual != null
														? m.actual > m.plan ? "var(--gpmc-danger)" : "var(--gpmc-success)"
														: "var(--gpmc-line)",
													borderRadius: "3px 3px 0 0",
													opacity: m.actual != null ? 0.85 : 0.3,
												}} />
											</div>
										</div>
										<span style={{ fontSize: 11, color: "var(--gpmc-muted)", marginTop: 6 }}>{m.month}</span>
									</div>
								);
							})}
						</div>
					</div>
				</div>

				{/* Right panel: 部门预算执行 */}
				<div className="gpmc__panel">
					<div className="gpmc__panel-header">
						<div>
							<div className="gpmc__panel-title">部门预算执行</div>
							<div className="gpmc__panel-subtitle">部门预算执行</div>
						</div>
					</div>
					<div className="gpmc__panel-body" style={{ padding: 0 }}>
						<table className="gpmc__table">
							<thead>
								<tr>
									<th>部门</th>
									<th>预算(亿)</th>
									<th>实际(亿)</th>
									<th>执行率</th>
									<th>进度</th>
								</tr>
							</thead>
							<tbody>
								{deptBudget.map((item, idx) => (
									<tr key={idx} className="gpmc__table-row--clickable" onClick={drillDown}>
										<td style={{ fontWeight: 600 }}>{item.dept}</td>
										<td>{item.budget}</td>
										<td style={{ color: item.actual > item.budget ? "var(--gpmc-danger)" : "var(--gpmc-text)" }}>
											{item.actual}
										</td>
										<td>
											<span className={`gpmc__status gpmc__status--${item.rate > 100 ? "danger" : item.rate >= 85 ? "ok" : "warn"}`}>
												{item.rate}%
											</span>
										</td>
										<td style={{ minWidth: 100 }}>
											<div className="gpmc__progress">
												<div
													className={`gpmc__progress-fill gpmc__progress-fill--${item.rate > 100 ? "danger" : item.rate >= 85 ? "success" : "primary"}`}
													style={{ width: `${Math.min(item.rate, 100)}%` }}
												/>
											</div>
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</div>
			</div>
		</div>
	);
}
