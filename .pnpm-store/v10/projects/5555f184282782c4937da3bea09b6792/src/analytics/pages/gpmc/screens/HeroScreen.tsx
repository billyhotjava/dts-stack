// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { useGpmc } from "../GpmcApp";
import {
	overviewKpis, healthMatrix, deptCost, alerts,
	executionKpis, stageDistribution, workloadData, delayTop10,
	riskByCategory, qualityByCategory,
} from "../mockData";

// Weekly trend mock data
const WEEKLY_TREND = [
	{ week: "W1", completed: 42, delayed: 8, risk: 3 },
	{ week: "W2", completed: 48, delayed: 6, risk: 4 },
	{ week: "W3", completed: 55, delayed: 9, risk: 5 },
	{ week: "W4", completed: 51, delayed: 7, risk: 3 },
	{ week: "W5", completed: 62, delayed: 5, risk: 2 },
	{ week: "W6", completed: 58, delayed: 8, risk: 6 },
	{ week: "W7", completed: 65, delayed: 4, risk: 3 },
	{ week: "W8", completed: 72, delayed: 6, risk: 4 },
];

const FEED_ITEMS = [
	{ tag: "质量", cls: "quality", text: "批次 #9042 验证通过" },
	{ tag: "风险", cls: "risk", text: "PRJ-2026-006 风险等级升级" },
	{ tag: "进度", cls: "progress", text: "核心器件研发 里程碑M3 已交付" },
	{ tag: "成本", cls: "cost", text: "物流支出超预测 +12%" },
	{ tag: "质量", cls: "quality", text: "技术状态变更 TSC-0847 已闭环" },
	{ tag: "风险", cls: "risk", text: "供应链预警: 元器件交期延迟14天" },
];

export default function HeroScreen() {
	const { setScreen, drillDown } = useGpmc();
	const maxTrend = Math.max(...WEEKLY_TREND.map((w) => w.completed));
	const maxWorkload = Math.max(...workloadData.map((w) => w.active));
	const maxDelay = Math.max(...delayTop10.map((d) => d.delay));
	const maxRiskCat = Math.max(...riskByCategory.map((r) => r.value));
	const totalStage = stageDistribution.reduce((s, d) => s + d.value, 0);

	return (
		<div style={{ display: "flex", flexDirection: "column", gap: 16, paddingBottom: 48 }}>
			{/* ── Row 1: KPI Cards ── */}
			<div className="gpmc__kpi-row">
				{overviewKpis.map((kpi) => (
					<div key={kpi.key} className="gpmc__kpi-card gpmc__kpi-card--clickable" onClick={() => { setScreen("overview"); drillDown(); }}>
						<div className="gpmc__kpi-label">{kpi.label}</div>
						<div className="gpmc__kpi-value">
							{kpi.value}
							{kpi.unit ? <span style={{ fontSize: 16, fontWeight: 600, marginLeft: 2 }}>{kpi.unit}</span> : null}
						</div>
						<div className="gpmc__kpi-sub">
							{kpi.sub && <span>{kpi.sub}</span>}
							{kpi.trend && <span className={`gpmc__kpi-trend gpmc__kpi-trend--${kpi.trendDir}`}>{kpi.trend}</span>}
						</div>
					</div>
				))}
			</div>

			{/* ── Row 2: Three-column dense layout ── */}
			<div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16 }}>
				{/* ── Left Column ── */}
				<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
					{/* 周度完成趋势 (柱状+折线) */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">周度完成趋势</div>
						</div>
						<div className="gpmc__panel-body" style={{ padding: "12px 16px" }}>
							<div style={{ display: "flex", alignItems: "flex-end", gap: 4, height: 120 }}>
								{WEEKLY_TREND.map((w, i) => (
									<div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 2 }}>
										<div style={{
											width: "100%", borderRadius: "3px 3px 0 0",
											height: `${(w.completed / maxTrend) * 100}%`,
											background: "linear-gradient(180deg, var(--gpmc-primary), rgba(34,195,255,0.3))",
											position: "relative",
										}}>
											{/* Delay dot overlay */}
											<div style={{
												position: "absolute", top: `${100 - (w.delayed / maxTrend) * 100}%`, left: "50%",
												transform: "translateX(-50%)", width: 6, height: 6, borderRadius: "50%",
												background: "var(--gpmc-warn)", boxShadow: "0 0 4px var(--gpmc-warn)",
											}} />
										</div>
									</div>
								))}
							</div>
							<div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, color: "var(--gpmc-muted)", marginTop: 6 }}>
								{WEEKLY_TREND.map((w) => <span key={w.week}>{w.week}</span>)}
							</div>
							<div style={{ display: "flex", gap: 16, fontSize: 10, color: "var(--gpmc-muted)", marginTop: 8 }}>
								<span><span style={{ display: "inline-block", width: 8, height: 8, background: "var(--gpmc-primary)", borderRadius: 2, marginRight: 4 }} />完成数</span>
								<span><span style={{ display: "inline-block", width: 6, height: 6, background: "var(--gpmc-warn)", borderRadius: "50%", marginRight: 4 }} />延期数</span>
							</div>
						</div>
					</div>

					{/* 项目阶段结构 (环形图) */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">项目阶段结构</div>
						</div>
						<div className="gpmc__panel-body" style={{ display: "flex", alignItems: "center", gap: 20, padding: "16px 20px" }}>
							{/* SVG donut */}
							<div style={{ position: "relative", width: 120, height: 120, flexShrink: 0 }}>
								<svg viewBox="0 0 100 100" style={{ width: "100%", height: "100%", transform: "rotate(-90deg)" }}>
									{(() => {
										let offset = 0;
										return stageDistribution.map((s) => {
											const pct = (s.value / totalStage) * 100;
											const dash = (pct / 100) * 283;
											const el = (
												<circle key={s.name} cx="50" cy="50" r="45" fill="none"
													stroke={s.color} strokeWidth="8"
													strokeDasharray={`${dash} ${283 - dash}`}
													strokeDashoffset={`${-offset * 2.83}`}
													style={{ filter: `drop-shadow(0 0 3px ${s.color})` }}
												/>
											);
											offset += pct;
											return el;
										});
									})()}
								</svg>
								<div style={{ position: "absolute", top: "50%", left: "50%", transform: "translate(-50%,-50%)", textAlign: "center" }}>
									<div style={{ fontSize: 20, fontWeight: 800, color: "var(--gpmc-text)" }}>{totalStage}</div>
									<div style={{ fontSize: 9, color: "var(--gpmc-muted)" }}>总项目</div>
								</div>
							</div>
							{/* Legend */}
							<div style={{ flex: 1, fontSize: 12 }}>
								{stageDistribution.map((s) => (
									<div key={s.name} style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
										<span style={{ display: "flex", alignItems: "center", gap: 6 }}>
											<span style={{ width: 8, height: 8, borderRadius: 2, background: s.color }} />
											{s.name}
										</span>
										<span style={{ fontWeight: 700, color: s.color }}>{s.value}</span>
									</div>
								))}
							</div>
						</div>
					</div>

					{/* 延期项目 TOP5 */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">延期项目 TOP5</div>
						</div>
						<div className="gpmc__panel-body" style={{ padding: "8px 16px" }}>
							{delayTop10.slice(0, 5).map((d, i) => (
								<div key={i} style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
									<span style={{ width: 16, fontSize: 11, fontWeight: 700, color: "var(--gpmc-muted)" }}>{i + 1}</span>
									<span style={{ flex: 1, fontSize: 12, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{d.name}</span>
									<div style={{ width: 80 }}>
										<div className="gpmc__progress" style={{ height: 8 }}>
											<div className="gpmc__progress-fill gpmc__progress-fill--danger" style={{ width: `${(d.delay / maxDelay) * 100}%` }} />
										</div>
									</div>
									<span style={{ fontWeight: 700, fontSize: 13, color: "var(--gpmc-danger)", width: 40, textAlign: "right" }}>{d.delay}天</span>
								</div>
							))}
						</div>
					</div>
				</div>

				{/* ── Center Column ── */}
				<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
					{/* Health Gauge + Alerts */}
					<div className="gpmc__panel" style={{ textAlign: "center" }}>
						<div className="gpmc__panel-body" style={{ padding: "20px" }}>
							<div className="gpmc__health-gauge" style={{ margin: "0 auto 12px" }}>
								<svg viewBox="0 0 200 200">
									<circle className="gpmc__health-gauge-bg" cx="100" cy="100" r="80" />
									<circle className="gpmc__health-gauge-fill" cx="100" cy="100" r="80"
										strokeDasharray={`${(84.6 / 100) * 502.65} ${502.65}`}
									/>
								</svg>
								<div className="gpmc__health-gauge-value">
									<div className="gpmc__health-gauge-number">84.6</div>
									<div className="gpmc__health-gauge-label">综合健康指数</div>
								</div>
							</div>
							<div style={{ fontSize: 12, color: "var(--gpmc-muted)" }}>
								较上周提升 2.8%，总体运行平稳
							</div>
						</div>
					</div>

					{/* 项目健康矩阵 (mini) */}
					<div className="gpmc__panel" onClick={() => setScreen("overview")} style={{ cursor: "pointer" }}>
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">项目健康矩阵</div>
						</div>
						<div className="gpmc__panel-body" style={{ padding: 0 }}>
							<table className="gpmc__table" style={{ fontSize: 12 }}>
								<thead>
									<tr><th>编号</th><th>项目</th><th>完成率</th><th>状态</th><th>风险</th></tr>
								</thead>
								<tbody>
									{healthMatrix.map((m) => (
										<tr key={m.id} className="gpmc__table-row--clickable">
											<td style={{ color: "var(--gpmc-primary)", fontWeight: 600, fontSize: 11 }}>{m.id}</td>
											<td style={{ maxWidth: 140, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{m.name}</td>
											<td>
												<div className="gpmc__progress" style={{ width: 50, display: "inline-block", verticalAlign: "middle" }}>
													<div className={`gpmc__progress-fill gpmc__progress-fill--${m.actualRate >= 70 ? "success" : "danger"}`} style={{ width: `${m.actualRate}%` }} />
												</div>
												<span style={{ fontSize: 11, marginLeft: 4 }}>{m.actualRate}%</span>
											</td>
											<td><span className={`gpmc__status gpmc__status--${m.statusTone}`} style={{ fontSize: 11, padding: "2px 6px" }}>{m.status}</span></td>
											<td style={{ fontSize: 11 }}>{m.risk}</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					</div>

					{/* 预警摘要 */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">预警摘要</div>
						</div>
						<div className="gpmc__panel-body" style={{ padding: "10px 16px" }}>
							{alerts.map((a, i) => (
								<div key={i} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "6px 0", borderBottom: i < alerts.length - 1 ? "1px solid var(--gpmc-line)" : "none" }}>
									<span style={{ fontSize: 13, fontWeight: 500 }}>{a.title}</span>
									<span style={{ fontWeight: 800, fontSize: 18, color: `var(--gpmc-${a.tone})` }}>{a.count}</span>
								</div>
							))}
						</div>
					</div>
				</div>

				{/* ── Right Column ── */}
				<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
					{/* 部门成本核算 */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">部门成本核算</div>
						</div>
						<div className="gpmc__panel-body" style={{ padding: "10px 16px" }}>
							{deptCost.map((d) => (
								<div key={d.dept} style={{ marginBottom: 10 }}>
									<div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 3 }}>
										<span>{d.dept}</span>
										<span style={{ fontWeight: 600 }}>{d.actual}亿 / {d.budget}亿</span>
									</div>
									<div className="gpmc__progress">
										<div className={`gpmc__progress-fill gpmc__progress-fill--${d.actual > d.budget ? "danger" : "primary"}`}
											style={{ width: `${Math.min((d.actual / d.budget) * 100, 100)}%` }} />
									</div>
								</div>
							))}
						</div>
					</div>

					{/* 风险分类排名 */}
					<div className="gpmc__panel" onClick={() => setScreen("risk")} style={{ cursor: "pointer" }}>
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">风险分类排名</div>
						</div>
						<div className="gpmc__panel-body" style={{ padding: "10px 16px" }}>
							{riskByCategory.map((r) => (
								<div key={r.name} style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
									<span style={{ width: 56, fontSize: 12, flexShrink: 0 }}>{r.name}</span>
									<div style={{ flex: 1 }}>
										<div className="gpmc__progress" style={{ height: 14, borderRadius: 3 }}>
											<div style={{ height: "100%", borderRadius: 3, width: `${(r.value / maxRiskCat) * 100}%`, background: r.color, boxShadow: `0 0 6px ${r.color}` }} />
										</div>
									</div>
									<span style={{ fontWeight: 700, fontSize: 13, width: 24, textAlign: "right" }}>{r.value}</span>
								</div>
							))}
						</div>
					</div>

					{/* 科室负载 */}
					<div className="gpmc__panel" onClick={() => setScreen("execution")} style={{ cursor: "pointer" }}>
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">科室负载</div>
						</div>
						<div className="gpmc__panel-body" style={{ padding: "10px 16px" }}>
							{workloadData.slice(0, 5).map((w) => (
								<div key={w.dept} style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
									<span style={{ width: 80, fontSize: 11, flexShrink: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{w.dept}</span>
									<div style={{ flex: 1, display: "flex", gap: 2 }}>
										<div className="gpmc__progress" style={{ flex: 1, height: 12, borderRadius: 2 }}>
											<div className="gpmc__progress-fill gpmc__progress-fill--primary" style={{ width: `${(w.active / maxWorkload) * 100}%`, borderRadius: 2 }} />
										</div>
										<div className="gpmc__progress" style={{ width: 30, height: 12, borderRadius: 2 }}>
											<div className="gpmc__progress-fill gpmc__progress-fill--danger" style={{ width: `${(w.overdue / maxWorkload) * 100}%`, borderRadius: 2 }} />
										</div>
									</div>
									<span style={{ fontSize: 11, fontWeight: 600, width: 20, textAlign: "right" }}>{w.active}</span>
								</div>
							))}
							<div style={{ display: "flex", gap: 12, fontSize: 10, color: "var(--gpmc-muted)", marginTop: 4 }}>
								<span><span style={{ display: "inline-block", width: 8, height: 4, background: "var(--gpmc-primary)", borderRadius: 1, marginRight: 4 }} />在办</span>
								<span><span style={{ display: "inline-block", width: 8, height: 4, background: "var(--gpmc-danger)", borderRadius: 1, marginRight: 4 }} />延期</span>
							</div>
							<div className="gpmc__panel-subtitle">资源负载不再独立成看板，统一并入项目执行监控与执行层下钻。</div>
						</div>
					</div>

					{/* 质量问题分类 */}
					<div className="gpmc__panel" onClick={() => setScreen("quality")} style={{ cursor: "pointer" }}>
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">质量问题分类</div>
						</div>
						<div className="gpmc__panel-body" style={{ padding: "10px 16px" }}>
							<div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
								{qualityByCategory.map((q) => (
									<div key={q.name} style={{
										padding: "6px 12px", borderRadius: 6,
										background: `${q.color}22`, border: `1px solid ${q.color}44`,
										fontSize: 12, fontWeight: 600, color: q.color,
									}}>
										{q.name} <span style={{ fontWeight: 800 }}>{q.value}</span>
									</div>
								))}
							</div>
						</div>
					</div>
				</div>
			</div>

			{/* ── Live Feed ── */}
			<div className="gpmc__live-feed">
				<div className="gpmc__live-feed-indicator">
					<div className="gpmc__live-feed-dot" />
					实时动态
				</div>
				<div className="gpmc__live-feed-scroll">
					<span className="gpmc__live-feed-text">
						{FEED_ITEMS.map((item, i) => (
							<span key={i} style={{ marginRight: 40 }}>
								<span className={`gpmc__live-feed-tag gpmc__live-feed-tag--${item.cls}`}>{item.tag}</span>
								{item.text}
							</span>
						))}
					</span>
				</div>
			</div>
		</div>
	);
}
