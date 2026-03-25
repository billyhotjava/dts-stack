import { useGpmc } from "../GpmcApp";
import {
	overviewKpis, healthMatrix, deptCost, alerts,
	stageDistribution, delayTop10,
	riskByCategory, qualityByCategory,
} from "../mockData";

const WEEKLY = [
	{ w: "W1", done: 42, delay: 8 }, { w: "W2", done: 48, delay: 6 },
	{ w: "W3", done: 55, delay: 9 }, { w: "W4", done: 51, delay: 7 },
	{ w: "W5", done: 62, delay: 5 }, { w: "W6", done: 58, delay: 8 },
	{ w: "W7", done: 65, delay: 4 }, { w: "W8", done: 72, delay: 6 },
];

const FEED = [
	{ tag: "进度", cls: "progress", text: "里程碑M3 已交付" },
	{ tag: "质量", cls: "quality", text: "批次 #9042 验证通过" },
	{ tag: "风险", cls: "risk", text: "PRJ-2026-006 风险升级" },
	{ tag: "成本", cls: "cost", text: "物流支出超预测 +12%" },
];

const OVERVIEW_KPI_TARGETS = {
	totalProjects: "execution",
	activeProjects: "execution",
	delayedProjects: "execution",
	annualBudget: "cost",
	avgProgress: "execution",
	highRiskRate: "risk",
} as const;

export default function OverviewScreen() {
	const { setScreen, drillDown } = useGpmc();
	const maxW = Math.max(...WEEKLY.map((w) => w.done));
	const maxDelay = Math.max(...delayTop10.map((d) => d.delay));
	const maxRisk = Math.max(...riskByCategory.map((r) => r.value));
	const totalStage = stageDistribution.reduce((s, d) => s + d.value, 0);
	const handleOverviewKpiClick = (key: keyof typeof OVERVIEW_KPI_TARGETS) => {
		setScreen(OVERVIEW_KPI_TARGETS[key]);
	};

	return (
		<div style={{ display: "flex", flexDirection: "column", gap: 14, paddingBottom: 48 }}>

			{/* ═══ 三栏主体: 左面板 | 中央核心 | 右面板 ═══ */}
			<div style={{ display: "grid", gridTemplateColumns: "280px 1fr 280px", gap: 14 }}>

				{/* ── 左栏 ── */}
				<div style={{ display: "flex", flexDirection: "column", gap: 12 }}>

					{/* 周度完成趋势 */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header"><div className="gpmc__panel-title">周度完成趋势</div></div>
						<div className="gpmc__panel-body" style={{ padding: "10px 14px" }}>
							<div style={{ display: "flex", alignItems: "flex-end", gap: 3, height: 90 }}>
								{WEEKLY.map((w, i) => (
									<div key={i} style={{ flex: 1, position: "relative" }}>
										<div style={{
											width: "100%", borderRadius: "2px 2px 0 0",
											height: `${(w.done / maxW) * 90}px`,
											background: "linear-gradient(180deg, var(--gpmc-primary), rgba(34,195,255,0.15))",
										}} />
										<div style={{ position: "absolute", bottom: `${(w.delay / maxW) * 90}px`, left: "50%", transform: "translateX(-50%)", width: 5, height: 5, borderRadius: "50%", background: "var(--gpmc-warn)" }} />
									</div>
								))}
							</div>
							<div style={{ display: "flex", justifyContent: "space-between", fontSize: 9, color: "var(--gpmc-muted)", marginTop: 4 }}>
								{WEEKLY.map((w) => <span key={w.w}>{w.w}</span>)}
							</div>
						</div>
					</div>

					{/* 项目阶段分布 */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header"><div className="gpmc__panel-title">项目阶段分布</div></div>
						<div className="gpmc__panel-body" style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 14px" }}>
							<div style={{ position: "relative", width: 80, height: 80, flexShrink: 0 }}>
								<svg viewBox="0 0 100 100" style={{ width: "100%", height: "100%", transform: "rotate(-90deg)" }}>
									{(() => { let off = 0; return stageDistribution.map((s) => { const pct = (s.value / totalStage) * 100; const d = (pct / 100) * 283; const el = <circle key={s.name} cx="50" cy="50" r="45" fill="none" stroke={s.color} strokeWidth="10" strokeDasharray={`${d} ${283-d}`} strokeDashoffset={`${-off*2.83}`} />; off += pct; return el; }); })()}
								</svg>
								<div style={{ position: "absolute", top: "50%", left: "50%", transform: "translate(-50%,-50%)", textAlign: "center" }}>
									<div style={{ fontSize: 16, fontWeight: 800 }}>{totalStage}</div>
									<div style={{ fontSize: 8, color: "var(--gpmc-muted)" }}>总项目</div>
								</div>
							</div>
							<div style={{ fontSize: 11 }}>
								{stageDistribution.map((s) => (
									<div key={s.name} style={{ display: "flex", justifyContent: "space-between", gap: 8, marginBottom: 3 }}>
										<span style={{ display: "flex", alignItems: "center", gap: 4 }}><span style={{ width: 6, height: 6, borderRadius: 1, background: s.color }} />{s.name}</span>
										<span style={{ fontWeight: 700, color: s.color }}>{s.value}</span>
									</div>
								))}
							</div>
						</div>
					</div>

					{/* 质量问题分类 */}
					<div className="gpmc__panel" onClick={() => setScreen("quality")} style={{ cursor: "pointer" }}>
						<div className="gpmc__panel-header"><div className="gpmc__panel-title">质量问题分类</div></div>
						<div className="gpmc__panel-body" style={{ padding: "8px 12px" }}>
							<div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
								{qualityByCategory.map((q) => (
									<div key={q.name} style={{ padding: "3px 8px", borderRadius: 4, background: `${q.color}22`, border: `1px solid ${q.color}33`, fontSize: 10, fontWeight: 600, color: q.color }}>
										{q.name} {q.value}
									</div>
								))}
							</div>
						</div>
					</div>

					{/* 延期 TOP5 */}
					<div className="gpmc__panel" onClick={() => setScreen("execution")} style={{ cursor: "pointer" }}>
						<div className="gpmc__panel-header"><div className="gpmc__panel-title">延期 TOP5</div></div>
						<div className="gpmc__panel-body" style={{ padding: "6px 12px" }}>
							{delayTop10.slice(0, 5).map((d, i) => (
								<div key={i} style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 5 }}>
									<span style={{ width: 12, fontSize: 10, fontWeight: 700, color: "var(--gpmc-muted)" }}>{i + 1}</span>
									<span style={{ flex: 1, fontSize: 10, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{d.name}</span>
									<div className="gpmc__progress" style={{ width: 40, height: 5 }}>
										<div className="gpmc__progress-fill gpmc__progress-fill--danger" style={{ width: `${(d.delay / maxDelay) * 100}%` }} />
									</div>
									<span style={{ fontWeight: 700, fontSize: 11, color: "var(--gpmc-danger)", width: 30, textAlign: "right" }}>{d.delay}天</span>
								</div>
							))}
						</div>
					</div>
				</div>

				{/* ── 中央核心区 ── */}
				<div style={{ display: "flex", flexDirection: "column", gap: 14 }}>

					{/* KPI 卡片行 */}
					<div className="gpmc__kpi-row" style={{ gridTemplateColumns: "repeat(6, 1fr)" }}>
						{overviewKpis.map((k) => (
							<div
								key={k.key}
								className="gpmc__kpi-card gpmc__kpi-card--clickable"
								onClick={() => handleOverviewKpiClick(k.key as keyof typeof OVERVIEW_KPI_TARGETS)}
							>
								<div className="gpmc__kpi-label">{k.label}</div>
								<div className="gpmc__kpi-value" style={{ fontSize: 28 }}>
									{k.value}{k.unit ? <span style={{ fontSize: 14, fontWeight: 600, marginLeft: 2 }}>{k.unit}</span> : null}
								</div>
								<div className="gpmc__kpi-sub">
									{k.trend && <span className={`gpmc__kpi-trend gpmc__kpi-trend--${k.trendDir}`}>{k.trend}</span>}
								</div>
							</div>
						))}
					</div>

					{/* 健康指数 + 预警 */}
					<div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
						{/* 健康指数 - 增强版 */}
						<div className="gpmc__panel" style={{ textAlign: "center", overflow: "visible" }}>
							<div className="gpmc__panel-body" style={{ padding: "16px 12px 12px", position: "relative" }}>
								{/* 多层光环仪表盘 */}
								<div style={{ position: "relative", width: 220, height: 220, margin: "0 auto 8px" }}>
									<svg viewBox="0 0 240 240" style={{ width: "100%", height: "100%", transform: "rotate(-90deg)" }}>
										{/* 外圈刻度 */}
										{Array.from({ length: 40 }, (_, i) => {
											const angle = (i / 40) * 360;
											const rad = (angle * Math.PI) / 180;
											const r = 115;
											const len = i % 5 === 0 ? 8 : 4;
											return <line key={i} x1={120 + r * Math.cos(rad)} y1={120 + r * Math.sin(rad)} x2={120 + (r - len) * Math.cos(rad)} y2={120 + (r - len) * Math.sin(rad)} stroke={i / 40 < 0.846 ? "rgba(34,195,255,0.6)" : "rgba(100,130,170,0.2)"} strokeWidth={i % 5 === 0 ? 2 : 1} />;
										})}
										{/* 外光环 (进度) */}
										<circle cx="120" cy="120" r="100" fill="none" stroke="rgba(34,195,255,0.06)" strokeWidth="3" />
										<circle cx="120" cy="120" r="100" fill="none" stroke="rgba(34,195,255,0.5)" strokeWidth="3" strokeDasharray={`${(84.6/100)*628.3} ${628.3}`} strokeLinecap="round" style={{ filter: "drop-shadow(0 0 6px rgba(34,195,255,0.4))" }} />
										{/* 主环 */}
										<circle cx="120" cy="120" r="88" fill="none" stroke="rgba(34,195,255,0.08)" strokeWidth="10" />
										<circle cx="120" cy="120" r="88" fill="none" stroke="#22c3ff" strokeWidth="10" strokeDasharray={`${(84.6/100)*553} ${553}`} strokeLinecap="round" style={{ filter: "drop-shadow(0 0 12px rgba(34,195,255,0.6))" }} />
										{/* 内光环 */}
										<circle cx="120" cy="120" r="76" fill="none" stroke="rgba(34,195,255,0.04)" strokeWidth="2" />
										<circle cx="120" cy="120" r="76" fill="none" stroke="rgba(34,195,255,0.25)" strokeWidth="2" strokeDasharray={`${(84.6/100)*477.5} ${477.5}`} />
									</svg>
									{/* 中心数字 */}
									<div style={{ position: "absolute", top: "50%", left: "50%", transform: "translate(-50%,-50%)", textAlign: "center" }}>
										<div style={{ fontSize: 48, fontWeight: 800, color: "#fff", lineHeight: 1, textShadow: "0 0 20px rgba(34,195,255,0.4)" }}>84.6</div>
										<div style={{ fontSize: 11, color: "#6b8ab5", letterSpacing: 1, marginTop: 4 }}>综合健康指数</div>
									</div>
									{/* 脉冲光点 (在主环上的84.6%位置) */}
									{(() => {
										const angle = (84.6 / 100) * 360 - 90;
										const rad = (angle * Math.PI) / 180;
										const cx = 120 + 88 * Math.cos(rad);
										const cy = 120 + 88 * Math.sin(rad);
										return <div style={{ position: "absolute", left: `${(cx/240)*100}%`, top: `${(cy/240)*100}%`, width: 10, height: 10, borderRadius: "50%", background: "#22c3ff", boxShadow: "0 0 12px 4px rgba(34,195,255,0.6)", transform: "translate(-50%,-50%)", animation: "gpmc-pulse 2s ease infinite" }} />;
									})()}
								</div>
								{/* 维度指标 */}
								<div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 6, marginTop: 4 }}>
									{[
										{ label: "进度", value: "78%", color: "#22c3ff" },
										{ label: "质量", value: "76%", color: "#3ddc97" },
										{ label: "成本", value: "93%", color: "#ffb74d" },
										{ label: "风险", value: "62%", color: "#ff6b7a" },
										{ label: "资源", value: "87%", color: "#54e3ff" },
										{ label: "协同", value: "81%", color: "#a78bfa" },
									].map((d) => (
										<div key={d.label} style={{ padding: "6px 4px", borderRadius: 6, background: "rgba(34,195,255,0.04)", border: "1px solid rgba(34,195,255,0.08)" }}>
											<div style={{ fontSize: 9, color: "var(--gpmc-muted)", marginBottom: 2 }}>{d.label}</div>
											<div style={{ fontSize: 16, fontWeight: 800, color: d.color }}>{d.value}</div>
										</div>
									))}
								</div>
								<div style={{ fontSize: 11, color: "var(--gpmc-muted)", marginTop: 8 }}>
									较上周 <span style={{ color: "#3ddc97", fontWeight: 700 }}>+2.8%</span>，运行平稳
								</div>
							</div>
						</div>

						{/* 质量闭环趋势 (柱状图) */}
						<div className="gpmc__panel" onClick={() => setScreen("quality")} style={{ cursor: "pointer" }}>
							<div className="gpmc__panel-header">
								<div className="gpmc__panel-title">质量闭环趋势</div>
								<div style={{ fontSize: 11, color: "var(--gpmc-muted)" }}>闭环率 <span style={{ color: "var(--gpmc-primary)", fontWeight: 700 }}>76%</span></div>
							</div>
							<div className="gpmc__panel-body" style={{ padding: "10px 14px" }}>
								{/* 柱状图：新增 vs 已闭环 */}
								<div style={{ display: "flex", alignItems: "flex-end", gap: 6, height: 120 }}>
									{[
										{ m: "1月", add: 32, closed: 28 },
										{ m: "2月", add: 28, closed: 30 },
										{ m: "3月", add: 35, closed: 25 },
										{ m: "4月", add: 22, closed: 32 },
										{ m: "5月", add: 25, closed: 24 },
										{ m: "6月", add: 23, closed: 18 },
									].map((d, i) => {
										const max = 40;
										return (
											<div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 2 }}>
												<div style={{ display: "flex", gap: 2, alignItems: "flex-end", height: 100, width: "100%" }}>
													{/* 新增 */}
													<div style={{ flex: 1, height: `${(d.add / max) * 100}%`, borderRadius: "2px 2px 0 0", background: "rgba(255,107,122,0.5)", boxShadow: "0 0 4px rgba(255,107,122,0.2)" }} />
													{/* 已闭环 */}
													<div style={{ flex: 1, height: `${(d.closed / max) * 100}%`, borderRadius: "2px 2px 0 0", background: "linear-gradient(180deg, var(--gpmc-primary), rgba(34,195,255,0.2))", boxShadow: "0 0 4px rgba(34,195,255,0.2)" }} />
												</div>
												<span style={{ fontSize: 9, color: "var(--gpmc-muted)" }}>{d.m}</span>
											</div>
										);
									})}
								</div>
								{/* 图例 */}
								<div style={{ display: "flex", gap: 14, fontSize: 10, color: "var(--gpmc-muted)", marginTop: 6 }}>
									<span><span style={{ display: "inline-block", width: 8, height: 8, borderRadius: 1, background: "rgba(255,107,122,0.5)", marginRight: 4 }} />新增</span>
									<span><span style={{ display: "inline-block", width: 8, height: 8, borderRadius: 1, background: "var(--gpmc-primary)", marginRight: 4 }} />已闭环</span>
								</div>
								{/* 摘要数据 */}
								<div style={{ display: "flex", justifyContent: "space-between", marginTop: 10, padding: "8px 0 0", borderTop: "1px solid var(--gpmc-line)" }}>
									{[
										{ label: "已闭环", value: "1,284", color: "var(--gpmc-primary)" },
										{ label: "待审核", value: "216", color: "var(--gpmc-text)" },
										{ label: "高优未关", value: "37", color: "var(--gpmc-danger)" },
									].map((s) => (
										<div key={s.label} style={{ textAlign: "center" }}>
											<div style={{ fontSize: 9, color: "var(--gpmc-muted)" }}>{s.label}</div>
											<div style={{ fontSize: 18, fontWeight: 800, color: s.color }}>{s.value}</div>
										</div>
									))}
								</div>
							</div>
						</div>
					</div>

					{/* 项目状态分布 (色块图替代表格) */}
					<div className="gpmc__panel" onClick={() => drillDown()} style={{ cursor: "pointer" }}>
						<div className="gpmc__panel-header"><div className="gpmc__panel-title">项目状态分布</div></div>
						<div className="gpmc__panel-body" style={{ padding: "12px 14px" }}>
							<div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 8 }}>
								{healthMatrix.map((m) => (
									<div key={m.id} style={{
										padding: "10px 8px", borderRadius: 8, textAlign: "center",
										background: m.statusTone === "danger" ? "rgba(255,107,122,0.12)" : m.statusTone === "warn" ? "rgba(255,183,77,0.12)" : "rgba(61,220,151,0.12)",
										border: `1px solid ${m.statusTone === "danger" ? "rgba(255,107,122,0.25)" : m.statusTone === "warn" ? "rgba(255,183,77,0.25)" : "rgba(61,220,151,0.25)"}`,
									}}>
										<div style={{ fontSize: 9, color: "var(--gpmc-muted)", marginBottom: 4, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{m.name}</div>
										<div style={{ fontSize: 22, fontWeight: 800, color: m.statusTone === "danger" ? "var(--gpmc-danger)" : m.statusTone === "warn" ? "var(--gpmc-warn)" : "var(--gpmc-success)" }}>{m.actualRate}%</div>
										<div className="gpmc__progress" style={{ marginTop: 6 }}>
											<div className={`gpmc__progress-fill gpmc__progress-fill--${m.statusTone === "danger" ? "danger" : m.statusTone === "warn" ? "warn" : "success"}`} style={{ width: `${m.actualRate}%` }} />
										</div>
									</div>
								))}
							</div>
						</div>
					</div>
				</div>

				{/* ── 右栏 ── */}
				<div style={{ display: "flex", flexDirection: "column", gap: 12 }}>

					{/* 风险分类排名 */}
					<div className="gpmc__panel" onClick={() => setScreen("risk")} style={{ cursor: "pointer" }}>
						<div className="gpmc__panel-header"><div className="gpmc__panel-title">风险分类排名</div></div>
						<div className="gpmc__panel-body" style={{ padding: "8px 12px" }}>
							{riskByCategory.map((r) => (
								<div key={r.name} style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 5 }}>
									<span style={{ width: 44, fontSize: 10 }}>{r.name}</span>
									<div style={{ flex: 1 }}><div className="gpmc__progress" style={{ height: 10, borderRadius: 2 }}><div style={{ height: "100%", borderRadius: 2, width: `${(r.value/maxRisk)*100}%`, background: r.color, boxShadow: `0 0 4px ${r.color}` }} /></div></div>
									<span style={{ fontWeight: 700, fontSize: 11, width: 18, textAlign: "right" }}>{r.value}</span>
								</div>
							))}
						</div>
					</div>

					{/* 部门成本核算 */}
					<div className="gpmc__panel" onClick={() => setScreen("cost")} style={{ cursor: "pointer" }}>
						<div className="gpmc__panel-header"><div className="gpmc__panel-title">部门成本核算</div></div>
						<div className="gpmc__panel-body" style={{ padding: "8px 12px" }}>
							{deptCost.map((d) => (
								<div key={d.dept} style={{ marginBottom: 7 }}>
									<div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, marginBottom: 2 }}>
										<span>{d.dept}</span>
										<span style={{ fontWeight: 600 }}>{d.actual}/{d.budget}亿</span>
									</div>
									<div className="gpmc__progress"><div className={`gpmc__progress-fill gpmc__progress-fill--${d.actual>d.budget?"danger":"primary"}`} style={{ width: `${Math.min((d.actual/d.budget)*100,100)}%` }} /></div>
								</div>
							))}
						</div>
					</div>

					{/* 技术状态变更 (柱状图) */}
					<div className="gpmc__panel" onClick={() => setScreen("tech-state")} style={{ cursor: "pointer" }}>
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">技术状态变更</div>
							<div style={{ fontSize: 10, color: "var(--gpmc-muted)" }}>近6月</div>
						</div>
						<div className="gpmc__panel-body" style={{ padding: "8px 12px" }}>
							<div style={{ display: "flex", alignItems: "flex-end", gap: 4, height: 70 }}>
								{[5, 8, 3, 6, 4, 2].map((v, i) => (
									<div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center" }}>
										<div style={{ width: "100%", borderRadius: "2px 2px 0 0", height: `${(v / 8) * 60}px`, background: "linear-gradient(180deg, var(--gpmc-primary), rgba(34,195,255,0.15))" }} />
										<span style={{ fontSize: 8, color: "var(--gpmc-muted)", marginTop: 2 }}>M{i + 1}</span>
									</div>
								))}
							</div>
							{/* 签署状态分布 */}
							<div style={{ display: "flex", gap: 4, marginTop: 8 }}>
								{[
									{ label: "已签署", count: 12, color: "var(--gpmc-success)" },
									{ label: "评审中", count: 4, color: "var(--gpmc-warn)" },
									{ label: "未评审", count: 2, color: "var(--gpmc-danger)" },
								].map((s) => (
									<div key={s.label} style={{ flex: 1, padding: "4px 0", borderRadius: 4, background: "rgba(34,195,255,0.04)", border: "1px solid var(--gpmc-line)", textAlign: "center" }}>
										<div style={{ fontSize: 14, fontWeight: 800, color: s.color }}>{s.count}</div>
										<div style={{ fontSize: 8, color: "var(--gpmc-muted)" }}>{s.label}</div>
									</div>
								))}
							</div>
						</div>
					</div>

					{/* 预警统计 (可视化) */}
					<div className="gpmc__panel">
						<div className="gpmc__panel-header"><div className="gpmc__panel-title">预警统计</div></div>
						<div className="gpmc__panel-body" style={{ padding: "8px 12px" }}>
							{alerts.map((a, i) => (
								<div key={i} style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 6 }}>
									<span style={{ fontSize: 10, flex: 1 }}>{a.title}</span>
									<div className="gpmc__progress" style={{ width: 50, height: 8 }}>
										<div className={`gpmc__progress-fill gpmc__progress-fill--${a.tone === "danger" ? "danger" : "warn"}`} style={{ width: `${Math.min(a.count * 6, 100)}%` }} />
									</div>
									<span style={{ fontWeight: 800, fontSize: 14, color: `var(--gpmc-${a.tone})`, width: 24, textAlign: "right" }}>{a.count}</span>
								</div>
							))}
						</div>
					</div>
				</div>
			</div>

			{/* ── 实时动态 ── */}
			<div className="gpmc__live-feed">
				<div className="gpmc__live-feed-indicator"><div className="gpmc__live-feed-dot" />实时动态</div>
				<div className="gpmc__live-feed-scroll">
					<span className="gpmc__live-feed-text">
						{FEED.map((f, i) => <span key={i} style={{ marginRight: 40 }}><span className={`gpmc__live-feed-tag gpmc__live-feed-tag--${f.cls}`}>{f.tag}</span>{f.text}</span>)}
					</span>
				</div>
			</div>
		</div>
	);
}
