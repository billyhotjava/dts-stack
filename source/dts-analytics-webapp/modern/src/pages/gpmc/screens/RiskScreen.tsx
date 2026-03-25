import { useGpmc } from "../GpmcApp";
import { riskKpis, riskMatrix, riskByCategory, riskList } from "../mockData";

export default function RiskScreen() {
	const { layer, drillDown, setLayer } = useGpmc();

	const maxCategoryValue = Math.max(...riskByCategory.map((c) => c.value));

	// Build a lookup for the 3x3 grid
	const probLevels = ["高", "中", "低"] as const;
	const impactLevels = ["低", "中", "高"] as const;
	const matrixLookup = new Map(riskMatrix.map((m) => [`${m.probability}-${m.impact}`, m]));

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
				{riskKpis.map((kpi) => (
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
									{kpi.tone === "danger" ? "需关注" : "注意"}
								</span>
							)}
						</div>
					</div>
				))}
			</div>

			{/* Two-col layout */}
			<div className="gpmc__two-col">
				{/* Left sidebar: Matrix + Category */}
				<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
					{/* 风险概率x影响矩阵 */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div>
								<div className="gpmc__panel-title">风险概率×影响矩阵</div>
								<div className="gpmc__panel-subtitle">风险概率 × 影响</div>
							</div>
						</div>
						<div className="gpmc__panel-body">
							<div style={{ display: "flex", gap: 0 }}>
								{/* Y-axis label */}
								<div style={{ display: "flex", flexDirection: "column", justifyContent: "center", marginRight: 8 }}>
									<span style={{ fontSize: 11, color: "var(--gpmc-muted)", writingMode: "vertical-rl", textOrientation: "mixed" }}>概率</span>
								</div>
								<div style={{ flex: 1 }}>
									{/* Grid rows: probability high→low (top→bottom) */}
									{probLevels.map((prob) => (
										<div key={prob} style={{ display: "flex", alignItems: "center", gap: 0 }}>
											<span style={{ fontSize: 11, color: "var(--gpmc-muted)", minWidth: 20, textAlign: "right", marginRight: 6 }}>{prob}</span>
											{impactLevels.map((impact) => {
												const cell = matrixLookup.get(`${prob}-${impact}`);
												return (
													<div
														key={`${prob}-${impact}`}
														style={{
															flex: 1,
															height: 52,
															display: "flex",
															alignItems: "center",
															justifyContent: "center",
															backgroundColor: cell?.color ?? "var(--gpmc-line)",
															opacity: 0.85,
															margin: 1,
															borderRadius: 4,
															color: "#fff",
															fontWeight: 700,
															fontSize: 16,
														}}
													>
														{cell?.count ?? 0}
													</div>
												);
											})}
										</div>
									))}
									{/* X-axis labels */}
									<div style={{ display: "flex", marginTop: 4, paddingLeft: 26 }}>
										{impactLevels.map((impact) => (
											<span key={impact} style={{ flex: 1, textAlign: "center", fontSize: 11, color: "var(--gpmc-muted)" }}>{impact}</span>
										))}
									</div>
									<div style={{ textAlign: "center", fontSize: 11, color: "var(--gpmc-muted)", marginTop: 2 }}>影响</div>
								</div>
							</div>
						</div>
					</div>

					{/* 风险分类分布 */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">风险分类分布</div>
						</div>
						<div className="gpmc__panel-body">
							{riskByCategory.map((cat, idx) => (
								<div key={idx} style={{ display: "flex", alignItems: "center", marginBottom: 10 }}>
									<span style={{ fontSize: 12, minWidth: 56, color: "var(--gpmc-muted)" }}>{cat.name}</span>
									<div className="gpmc__progress" style={{ flex: 1, marginLeft: 8 }}>
										<div
											className="gpmc__progress-fill gpmc__progress-fill--primary"
											style={{ width: `${(cat.value / maxCategoryValue) * 100}%`, backgroundColor: cat.color }}
										/>
									</div>
									<span style={{ fontSize: 12, fontWeight: 600, minWidth: 24, textAlign: "right", marginLeft: 8 }}>{cat.value}</span>
								</div>
							))}
						</div>
					</div>
				</div>

				{/* Right panel: 风险跟进清单 */}
				<div className="gpmc__panel">
					<div className="gpmc__panel-header">
						<div>
							<div className="gpmc__panel-title">风险跟进清单</div>
							<div className="gpmc__panel-subtitle">风险跟进清单</div>
						</div>
						<div className="gpmc__panel-meta">
							共 {riskList.length} 项
						</div>
					</div>
					<div className="gpmc__panel-body" style={{ padding: 0 }}>
						<table className="gpmc__table">
							<thead>
								<tr>
									<th>风险名称</th>
									<th>项目</th>
									<th>等级</th>
									<th>状态</th>
									<th>滞留天数</th>
									<th>应对措施</th>
								</tr>
							</thead>
							<tbody>
								{riskList.map((item, idx) => (
									<tr key={idx} className="gpmc__table-row--clickable" onClick={drillDown}>
										<td style={{ fontWeight: 600 }}>{item.name}</td>
										<td style={{ fontSize: 12, color: "var(--gpmc-muted)" }}>{item.project}</td>
										<td>
											<span className={`gpmc__kpi-badge gpmc__kpi-badge--${item.level === "高" ? "danger" : item.level === "中" ? "warn" : "info"}`}>
												{item.level}
											</span>
										</td>
										<td>
											<span className={`gpmc__status gpmc__status--${item.status === "已闭环" ? "ok" : "warn"}`}>
												{item.status}
											</span>
										</td>
										<td style={{ fontWeight: 600, color: item.days > 0 ? "var(--gpmc-danger)" : "var(--gpmc-text)" }}>
											{item.days}
										</td>
										<td style={{ fontSize: 12, color: "var(--gpmc-muted)" }}>{item.measure}</td>
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
