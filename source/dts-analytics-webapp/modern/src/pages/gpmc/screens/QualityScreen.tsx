import { useGpmc } from "../GpmcApp";
import { qualityKpis, qualityByCategory, qualityIssueList } from "../mockData";

export default function QualityScreen() {
	const { layer, drillDown, setLayer } = useGpmc();

	const maxCategoryValue = Math.max(...qualityByCategory.map((c) => c.value));
	const issueStatusSummary = [
		{ label: "未完成归零", value: qualityIssueList.filter((item) => item.status === "未完成归零").length, tone: "danger" as const },
		{ label: "已完成技术归零", value: qualityIssueList.filter((item) => item.status === "已完成技术归零").length, tone: "ok" as const },
		{ label: "已完成管理归零", value: qualityIssueList.filter((item) => item.status === "已完成管理归零").length, tone: "info" as const },
	];

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
				{qualityKpis.map((kpi) => (
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
							{kpi.sub ? <span>{kpi.sub}</span> : null}
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
				{/* Left panel: 质量问题清单 */}
				<div className="gpmc__panel">
					<div className="gpmc__panel-header">
						<div>
							<div className="gpmc__panel-title">质量问题清单</div>
							<div className="gpmc__panel-subtitle">质量问题清单</div>
						</div>
						<div className="gpmc__panel-meta">
							共 {qualityIssueList.length} 项
						</div>
					</div>
					<div className="gpmc__panel-body" style={{ padding: 0 }}>
						<table className="gpmc__table">
							<thead>
								<tr>
									<th>项目</th>
									<th>问题描述</th>
									<th>分类</th>
									<th>状态</th>
									<th>滞留天数</th>
								</tr>
							</thead>
							<tbody>
								{qualityIssueList.map((item, idx) => (
									<tr key={idx} className="gpmc__table-row--clickable" onClick={drillDown}>
										<td style={{ fontSize: 12, color: "var(--gpmc-muted)" }}>{item.project}</td>
										<td style={{ fontWeight: 600 }}>{item.issue}</td>
										<td>
											<span className={`gpmc__kpi-badge gpmc__kpi-badge--${item.category === "元器件" || item.category === "设计" ? "danger" : "info"}`}>
												{item.category}
											</span>
										</td>
										<td>
											<span className={`gpmc__status gpmc__status--${item.days > 0 ? "danger" : "ok"}`}>
												{item.status}
											</span>
										</td>
										<td style={{ fontWeight: 600, color: item.days > 0 ? "var(--gpmc-danger)" : "var(--gpmc-text)" }}>
											{item.days}
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</div>

				{/* Right sidebar */}
				<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
					{/* 问题分类分布 */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">问题分类分布</div>
						</div>
						<div className="gpmc__panel-body">
							{qualityByCategory.map((cat, idx) => (
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

					{/* 问题状态概览 */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">问题状态概览</div>
						</div>
						<div className="gpmc__panel-body">
							{issueStatusSummary.map((item) => (
								<div key={item.label} style={{ marginBottom: 12 }}>
									<div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 4 }}>
										<span>{item.label}</span>
										<span style={{ fontWeight: 700 }}>{item.value}</span>
									</div>
									<div className="gpmc__progress">
										<div
											className={`gpmc__progress-fill gpmc__progress-fill--${item.tone === "danger" ? "danger" : item.tone === "ok" ? "success" : "primary"}`}
											style={{ width: `${Math.max(item.value * 20, 12)}%` }}
										/>
									</div>
								</div>
							))}
							<div className="gpmc__panel-subtitle">技术状态已拆分为独立看板，质量页仅保留质量问题与闭环信息。</div>
						</div>
					</div>
				</div>
			</div>
		</div>
	);
}
