import { useState, useEffect, useMemo } from "react";

// ═══════════════════════════════════════════════════════════════
// MOCK DATA — 项目经费表
// ═══════════════════════════════════════════════════════════════
// 字段说明：
//   totalFund        = 总经费
//   directCtrl       = 直接成本控制数
//   reserveIndirect  = 预留间接费用和收益  （= 总经费 - 直接成本控制数）
//   directSpent      = 直接成本支出金额
//   directRate       = 直接成本执行率（%） （= 直接成本支出金额 / 直接成本控制数 × 100）
//   indirectSpent    = 间接费用支出和收益总额
//
// 派生指标（大屏自动计算）：
//   剩余直接成本     = 直接成本控制数 - 直接成本支出金额
//   总支出           = 直接成本支出金额 + 间接费用支出和收益总额
//   总经费执行率     = 总支出 / 总经费 × 100
//   间接费用执行率   = 间接费用支出和收益总额 / 预留间接费用和收益 × 100
//   经费健康度       = 根据直接成本执行率与研制周期进度的偏差判定

const RAW = [
  { id: "PRJ-001", name: "新一代涡轮组装", dept: "制造部", pm: "张伟", cycle: "2025.03-2026.06", months: 16, elapsed: 12, totalFund: 4800, directCtrl: 3840, directSpent: 3200, indirectSpent: 520 },
  { id: "PRJ-002", name: "智能电网升级", dept: "电力部", pm: "李娜", cycle: "2025.01-2025.12", months: 12, elapsed: 12, totalFund: 12000, directCtrl: 9600, directSpent: 9800, indirectSpent: 1680 },
  { id: "PRJ-003", name: "数据中心迁移", dept: "IT部", pm: "王强", cycle: "2025.06-2026.09", months: 16, elapsed: 9, totalFund: 6500, directCtrl: 5200, directSpent: 4100, indirectSpent: 780 },
  { id: "PRJ-004", name: "供应链数字化", dept: "采购部", pm: "赵敏", cycle: "2025.02-2025.11", months: 10, elapsed: 10, totalFund: 3200, directCtrl: 2560, directSpent: 2800, indirectSpent: 380 },
  { id: "PRJ-005", name: "质量管理体系认证", dept: "质量部", pm: "陈静", cycle: "2025.04-2025.10", months: 7, elapsed: 7, totalFund: 1500, directCtrl: 1200, directSpent: 1350, indirectSpent: 130 },
  { id: "PRJ-006", name: "生产线自动化改造", dept: "制造部", pm: "刘洋", cycle: "2025.05-2026.08", months: 16, elapsed: 10, totalFund: 8800, directCtrl: 7040, directSpent: 5200, indirectSpent: 960 },
  { id: "PRJ-007", name: "ERP系统升级", dept: "IT部", pm: "孙磊", cycle: "2025.01-2025.09", months: 9, elapsed: 9, totalFund: 5500, directCtrl: 4400, directSpent: 4200, indirectSpent: 720 },
  { id: "PRJ-008", name: "环保合规改造", dept: "安环部", pm: "周芳", cycle: "2025.03-2025.12", months: 10, elapsed: 10, totalFund: 2200, directCtrl: 1760, directSpent: 1650, indirectSpent: 280 },
  { id: "PRJ-009", name: "新材料研发", dept: "研发部", pm: "吴杰", cycle: "2025.07-2026.12", months: 18, elapsed: 8, totalFund: 7200, directCtrl: 5760, directSpent: 3800, indirectSpent: 540 },
  { id: "PRJ-010", name: "客户门户平台", dept: "营销部", pm: "郑华", cycle: "2025.08-2026.03", months: 8, elapsed: 7, totalFund: 2800, directCtrl: 2240, directSpent: 1100, indirectSpent: 180 },
];

// 计算派生字段
const PROJECTS = RAW.map(p => {
  const reserveIndirect = p.totalFund - p.directCtrl;
  const directRate = p.directCtrl > 0 ? (p.directSpent / p.directCtrl * 100) : 0;
  const directRemain = p.directCtrl - p.directSpent;
  const totalSpent = p.directSpent + p.indirectSpent;
  const totalRate = p.totalFund > 0 ? (totalSpent / p.totalFund * 100) : 0;
  const indirectRate = reserveIndirect > 0 ? (p.indirectSpent / reserveIndirect * 100) : 0;
  const timeRate = p.months > 0 ? (p.elapsed / p.months * 100) : 0;
  const deviation = directRate - timeRate;
  const health = directRate > 100 ? "overrun" : Math.abs(deviation) <= 10 ? "good" : deviation > 10 ? "fast" : "slow";
  return { ...p, reserveIndirect, directRate, directRemain, totalSpent, totalRate, indirectRate, timeRate, deviation, health };
});

// ═══════════════════════════════════════════════════════════════
// THEME
// ═══════════════════════════════════════════════════════════════
const T = {
  bg: "#eaf2fb",
  headerBg: "linear-gradient(135deg, #044B8C 0%, #0a5599 50%, #044B8C 100%)",
  card: "#ffffff", cardBorder: "#d6e4f0", cardShadow: "0 1px 4px rgba(26,82,118,0.08)",
  primary: "#044B8C", primaryLight: "#2980b9", accent: "#2471a3", link: "#2e86c1",
  good: "#1e8449", goodBg: "#e8f8f0",
  warning: "#d4850a", warningBg: "#fef5e7",
  critical: "#c0392b", criticalBg: "#fdedec",
  text: "#1c2833", textSec: "#566573", textMuted: "#909eab",
  border: "#dce6f0", rowAlt: "#f5f9fd", tagBg: "#eaf2fa",
};

const healthColor = h => h === "good" ? T.good : h === "overrun" ? T.critical : h === "fast" ? T.warning : "#7f8c8d";
const healthLabel = h => h === "good" ? "正常" : h === "overrun" ? "超支" : h === "fast" ? "偏快" : "偏慢";
const healthBg = h => h === "good" ? T.goodBg : h === "overrun" ? T.criticalBg : h === "fast" ? T.warningBg : "#f4f6f6";

// ═══════════════════════════════════════════════════════════════
// UI PRIMITIVES
// ═══════════════════════════════════════════════════════════════
function Card({ children, style }) {
  return <div style={{ background: T.card, border: `1px solid ${T.cardBorder}`, borderRadius: 8, padding: 18, boxShadow: T.cardShadow, ...style }}>{children}</div>;
}
function SectionTitle({ title, sub }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ fontSize: 15, fontWeight: 600, color: T.primary, display: "flex", alignItems: "center", gap: 8 }}>
        <span style={{ width: 3, height: 16, background: T.primaryLight, borderRadius: 2, display: "inline-block" }} />{title}
      </div>
      {sub && <div style={{ fontSize: 12, color: T.textMuted, marginTop: 2, paddingLeft: 11 }}>{sub}</div>}
    </div>
  );
}
function KPICard({ label, value, unit, color, sub }) {
  return (
    <Card style={{ textAlign: "center", padding: "16px 10px" }}>
      <div style={{ fontSize: 11, color: T.textMuted, textTransform: "uppercase", letterSpacing: 1.2, marginBottom: 8, fontWeight: 500 }}>{label}</div>
      <div style={{ fontSize: 28, fontWeight: 700, color: color || T.primary, lineHeight: 1 }}>{value}<span style={{ fontSize: 13, color: T.textSec, marginLeft: 2, fontWeight: 400 }}>{unit}</span></div>
      {sub && <div style={{ fontSize: 11, color: T.textMuted, marginTop: 6 }}>{sub}</div>}
    </Card>
  );
}
function Badge({ text, color, bg }) {
  return <span style={{ fontSize: 11, fontWeight: 600, padding: "3px 10px", borderRadius: 4, background: bg || T.tagBg, color, whiteSpace: "nowrap" }}>{text}</span>;
}
function MiniBar({ value, max = 100, color, height = 8 }) {
  const pct = Math.min(value / max * 100, 100);
  const overflow = value > max;
  return (
    <div style={{ height, background: "#e8eff7", borderRadius: height, overflow: "hidden", position: "relative" }}>
      <div style={{ width: `${pct}%`, height: "100%", background: overflow ? T.critical : color || T.primaryLight, borderRadius: height, transition: "width 0.6s ease" }} />
    </div>
  );
}
function fmt(n) { return n.toFixed(0).replace(/\B(?=(\d{3})+(?!\d))/g, ","); }

// ═══════════════════════════════════════════════════════════════
// CHARTS
// ═══════════════════════════════════════════════════════════════
function StackedBarChart({ data, height = 220 }) {
  const max = Math.max(...data.map(d => d.totalFund));
  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 10, height, padding: "0 4px" }}>
      {data.map((d, i) => {
        const directH = (d.directSpent / max) * (height - 36);
        const indirectH = (d.indirectSpent / max) * (height - 36);
        const remainH = (Math.max(d.totalFund - d.directSpent - d.indirectSpent, 0) / max) * (height - 36);
        return (
          <div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 0 }}>
            <div style={{ fontSize: 10, color: T.textMuted, marginBottom: 4 }}>{d.totalRate.toFixed(0)}%</div>
            <div style={{ width: "100%", display: "flex", flexDirection: "column" }}>
              <div style={{ height: remainH, background: "#e0eaf4", borderRadius: "3px 3px 0 0", minHeight: remainH > 0 ? 2 : 0 }} />
              <div style={{ height: indirectH, background: "#8e44ad", minHeight: indirectH > 0 ? 2 : 0 }} />
              <div style={{ height: directH, background: T.primaryLight, borderRadius: "0 0 3px 3px", minHeight: 2 }} />
            </div>
            <div style={{ fontSize: 9, color: T.textMuted, marginTop: 4, textAlign: "center", lineHeight: 1.2, height: 22, overflow: "hidden" }}>{d.name.slice(0, 4)}</div>
          </div>
        );
      })}
    </div>
  );
}

function DualBarChart({ data, height = 200 }) {
  const max = Math.max(...data.map(d => Math.max(d.directRate, d.timeRate)));
  const barW = 12;
  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 8, height, padding: "0 4px" }}>
      {data.map((d, i) => {
        const h1 = (d.timeRate / max) * (height - 28);
        const h2 = (d.directRate / max) * (height - 28);
        return (
          <div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 4 }}>
            <div style={{ display: "flex", gap: 2, alignItems: "flex-end" }}>
              <div style={{ width: barW, height: h1, background: "#bdc3c7", borderRadius: "2px 2px 0 0", minHeight: 2 }} />
              <div style={{ width: barW, height: h2, background: d.directRate > 100 ? T.critical : d.directRate > d.timeRate * 1.1 ? T.warning : T.primaryLight, borderRadius: "2px 2px 0 0", minHeight: 2 }} />
            </div>
            <div style={{ fontSize: 9, color: T.textMuted, textAlign: "center", lineHeight: 1.2, height: 22, overflow: "hidden" }}>{d.name.slice(0, 4)}</div>
          </div>
        );
      })}
    </div>
  );
}

function DonutChart({ value, size = 80, thickness = 8, color = T.primaryLight, label }) {
  const r = (size - thickness) / 2, circ = 2 * Math.PI * r, offset = circ * (1 - Math.min(value, 100) / 100);
  return (
    <div style={{ position: "relative", width: size, height: size }}>
      <svg width={size} height={size} style={{ transform: "rotate(-90deg)" }}>
        <circle cx={size/2} cy={size/2} r={r} fill="none" stroke="#e0eaf4" strokeWidth={thickness} />
        <circle cx={size/2} cy={size/2} r={r} fill="none" stroke={value > 100 ? T.critical : color} strokeWidth={thickness} strokeDasharray={circ} strokeDashoffset={offset} strokeLinecap="round" style={{ transition: "stroke-dashoffset 1s" }} />
      </svg>
      <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
        <div style={{ fontSize: 15, fontWeight: 700, color: value > 100 ? T.critical : T.primary }}>{value.toFixed(1)}%</div>
        {label && <div style={{ fontSize: 9, color: T.textMuted }}>{label}</div>}
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// MAIN DASHBOARD
// ═══════════════════════════════════════════════════════════════
export default function ProjectFundDashboard() {
  const [time, setTime] = useState(new Date());
  const [filterDept, setFilterDept] = useState("");
  const [filterHealth, setFilterHealth] = useState("");
  useEffect(() => { const t = setInterval(() => setTime(new Date()), 1000); return () => clearInterval(t); }, []);

  const depts = [...new Set(PROJECTS.map(p => p.dept))];
  const filtered = useMemo(() => PROJECTS.filter(p =>
    (!filterDept || p.dept === filterDept) && (!filterHealth || p.health === filterHealth)
  ), [filterDept, filterHealth]);

  const sumTotal = filtered.reduce((s, p) => s + p.totalFund, 0);
  const sumDirectCtrl = filtered.reduce((s, p) => s + p.directCtrl, 0);
  const sumDirectSpent = filtered.reduce((s, p) => s + p.directSpent, 0);
  const sumIndirectSpent = filtered.reduce((s, p) => s + p.indirectSpent, 0);
  const sumTotalSpent = sumDirectSpent + sumIndirectSpent;
  const overallDirectRate = sumDirectCtrl > 0 ? (sumDirectSpent / sumDirectCtrl * 100) : 0;
  const overallTotalRate = sumTotal > 0 ? (sumTotalSpent / sumTotal * 100) : 0;
  const overrunCount = filtered.filter(p => p.health === "overrun").length;

  return (
    <div style={{ minHeight: "100vh", background: T.bg, color: T.text, fontFamily: "'Noto Sans SC', 'PingFang SC', -apple-system, sans-serif", fontSize: 13 }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@300;400;500;600;700&display=swap');
        * { box-sizing: border-box; margin: 0; padding: 0; }
        ::-webkit-scrollbar { width: 4px; height: 4px; }
        ::-webkit-scrollbar-track { background: #e8eff7; }
        ::-webkit-scrollbar-thumb { background: #b0c4de; border-radius: 4px; }
      `}</style>

      {/* Header */}
      <div style={{ background: T.headerBg, padding: "14px 28px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, minWidth: 180 }}>
          <div style={{ width: 36, height: 36, borderRadius: 8, background: "rgba(255,255,255,0.15)", display: "flex", alignItems: "center", justifyContent: "center", border: "1px solid rgba(255,255,255,0.25)" }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round"><path d="M12 2v20M2 12h20M6 6l12 12M18 6L6 18" /></svg>
          </div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.65)", letterSpacing: 1, lineHeight: 1.4 }}>DECISION TWINS<br/>FUND CONTROL</div>
        </div>
        <div style={{ textAlign: "center" }}>
          <div style={{ fontSize: 22, fontWeight: 700, color: "#fff", letterSpacing: 4 }}>项目经费大屏</div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.55)", letterSpacing: 2, marginTop: 2 }}>PROJECT FUND OVERVIEW</div>
        </div>
        <div style={{ textAlign: "right", color: "#fff", minWidth: 180 }}>
          <div style={{ fontSize: 16, fontWeight: 600 }}>{time.toLocaleTimeString("zh-CN", { hour12: false })}</div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.6)", marginTop: 1 }}>{time.toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric", weekday: "short" })}</div>
        </div>
      </div>

      <div style={{ padding: "18px 24px", maxWidth: 1480, margin: "0 auto", display: "flex", flexDirection: "column", gap: 16 }}>

        {/* Filters */}
        <Card style={{ padding: "12px 18px", display: "flex", alignItems: "center", gap: 16, flexWrap: "wrap" }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: T.primary }}>筛选条件</span>
          <select value={filterDept} onChange={e => setFilterDept(e.target.value)} style={{ padding: "6px 12px", borderRadius: 6, border: `1px solid ${T.cardBorder}`, fontSize: 13, color: T.text, background: "#fff", outline: "none", cursor: "pointer", fontFamily: "inherit" }}>
            <option value="">全部部门</option>
            {depts.map(d => <option key={d} value={d}>{d}</option>)}
          </select>
          <select value={filterHealth} onChange={e => setFilterHealth(e.target.value)} style={{ padding: "6px 12px", borderRadius: 6, border: `1px solid ${T.cardBorder}`, fontSize: 13, color: T.text, background: "#fff", outline: "none", cursor: "pointer", fontFamily: "inherit" }}>
            <option value="">全部状态</option>
            <option value="good">正常</option>
            <option value="overrun">超支</option>
            <option value="fast">偏快</option>
            <option value="slow">偏慢</option>
          </select>
          {(filterDept || filterHealth) && (
            <button onClick={() => { setFilterDept(""); setFilterHealth(""); }}
              style={{ padding: "6px 14px", borderRadius: 6, border: `1px solid ${T.cardBorder}`, background: T.tagBg, color: T.primary, fontSize: 12, cursor: "pointer", fontFamily: "inherit", fontWeight: 500 }}>清除筛选</button>
          )}
          <span style={{ fontSize: 12, color: T.textMuted, marginLeft: "auto" }}>共 {filtered.length} 个项目</span>
        </Card>

        {/* KPI Row */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(6, 1fr)", gap: 12 }}>
          <KPICard label="总经费" value={fmt(sumTotal)} unit="万" color={T.primary} />
          <KPICard label="直接成本控制数" value={fmt(sumDirectCtrl)} unit="万" color={T.accent} />
          <KPICard label="直接成本已支出" value={fmt(sumDirectSpent)} unit="万" color={T.primaryLight} />
          <KPICard label="直接成本执行率" value={overallDirectRate.toFixed(1)} unit="%" color={overallDirectRate > 100 ? T.critical : T.primaryLight} />
          <KPICard label="总经费执行率" value={overallTotalRate.toFixed(1)} unit="%" color={overallTotalRate > 90 ? T.warning : T.primaryLight} />
          <KPICard label="超支项目" value={overrunCount} unit="个" color={overrunCount > 0 ? T.critical : T.good} sub={overrunCount > 0 ? "需立即关注" : "无超支"} />
        </div>

        {/* Charts Row */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16 }}>
          <Card>
            <SectionTitle title="经费结构分布" sub="直接支出 / 间接支出 / 剩余" />
            <StackedBarChart data={filtered} height={200} />
            <div style={{ display: "flex", gap: 16, justifyContent: "center", marginTop: 10 }}>
              <span style={{ fontSize: 11, display: "flex", alignItems: "center", gap: 4 }}><span style={{ width: 10, height: 10, borderRadius: 2, background: T.primaryLight, display: "inline-block" }} />直接成本</span>
              <span style={{ fontSize: 11, display: "flex", alignItems: "center", gap: 4 }}><span style={{ width: 10, height: 10, borderRadius: 2, background: "#8e44ad", display: "inline-block" }} />间接费用</span>
              <span style={{ fontSize: 11, display: "flex", alignItems: "center", gap: 4 }}><span style={{ width: 10, height: 10, borderRadius: 2, background: "#e0eaf4", display: "inline-block" }} />剩余</span>
            </div>
          </Card>
          <Card>
            <SectionTitle title="执行率 vs 时间进度" sub="灰色=时间进度, 彩色=成本执行率" />
            <DualBarChart data={filtered} height={200} />
            <div style={{ display: "flex", gap: 16, justifyContent: "center", marginTop: 10 }}>
              <span style={{ fontSize: 11, display: "flex", alignItems: "center", gap: 4 }}><span style={{ width: 10, height: 10, borderRadius: 2, background: "#bdc3c7", display: "inline-block" }} />时间进度</span>
              <span style={{ fontSize: 11, display: "flex", alignItems: "center", gap: 4 }}><span style={{ width: 10, height: 10, borderRadius: 2, background: T.primaryLight, display: "inline-block" }} />成本执行率</span>
              <span style={{ fontSize: 11, display: "flex", alignItems: "center", gap: 4 }}><span style={{ width: 10, height: 10, borderRadius: 2, background: T.critical, display: "inline-block" }} />超支</span>
            </div>
          </Card>
          <Card>
            <SectionTitle title="经费健康度" sub="基于成本执行率与时间进度偏差" />
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              {[
                { label: "正常", key: "good", color: T.good, bg: T.goodBg },
                { label: "超支", key: "overrun", color: T.critical, bg: T.criticalBg },
                { label: "偏快", key: "fast", color: T.warning, bg: T.warningBg },
                { label: "偏慢", key: "slow", color: "#7f8c8d", bg: "#f4f6f6" },
              ].map(s => {
                const cnt = filtered.filter(p => p.health === s.key).length;
                return (
                  <div key={s.key} style={{ background: s.bg, borderRadius: 8, padding: "14px 12px", textAlign: "center", border: `1px solid ${s.color}22` }}>
                    <div style={{ fontSize: 28, fontWeight: 700, color: s.color }}>{cnt}</div>
                    <div style={{ fontSize: 12, color: s.color, fontWeight: 500, marginTop: 2 }}>{s.label}</div>
                  </div>
                );
              })}
            </div>
            <div style={{ marginTop: 14, padding: "10px 12px", background: T.tagBg, borderRadius: 6, fontSize: 11, color: T.textSec, lineHeight: 1.7 }}>
              <span style={{ fontWeight: 600, color: T.primary }}>判定逻辑：</span><br/>
              <span style={{ color: T.critical }}>超支</span>：直接成本执行率 &gt; 100%<br/>
              <span style={{ color: T.good }}>正常</span>：执行率与时间进度偏差 ≤ 10%<br/>
              <span style={{ color: T.warning }}>偏快</span>：执行率超前时间进度 &gt; 10%<br/>
              <span style={{ color: "#7f8c8d" }}>偏慢</span>：执行率落后时间进度 &gt; 10%
            </div>
          </Card>
        </div>

        {/* Main Table */}
        <Card>
          <SectionTitle title="项目经费明细表" sub="单位：万元 | 派生指标实时计算" />
          <div style={{ overflowX: "auto", overflowY: "auto", maxHeight: 440 }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12, whiteSpace: "nowrap" }}>
              <thead><tr>
                {["项目编号","项目名称","研制周期","总经费","直接成本控制数","预留间接费用和收益","直接成本支出","直接成本执行率","间接费用支出","总支出","总执行率","健康度"].map((h, i) => (
                  <th key={i} style={{ textAlign: i >= 3 ? "right" : "left", padding: "10px 10px", color: T.primary, borderBottom: `2px solid ${T.border}`, fontSize: 10, fontWeight: 600, letterSpacing: 0.5, position: "sticky", top: 0, background: T.card, zIndex: 1 }}>{h}</th>
                ))}
              </tr></thead>
              <tbody>
                {filtered.map((p, ri) => (
                  <tr key={p.id} style={{ background: ri % 2 === 0 ? T.card : T.rowAlt }}>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}`, color: T.link, fontWeight: 600 }}>{p.id}</td>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}`, color: T.text, fontWeight: 500 }}>{p.name}</td>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}`, color: T.textSec, fontSize: 11 }}>{p.cycle}</td>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}`, textAlign: "right", fontWeight: 600 }}>{fmt(p.totalFund)}</td>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}`, textAlign: "right" }}>{fmt(p.directCtrl)}</td>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}`, textAlign: "right", color: T.textSec }}>{fmt(p.reserveIndirect)}</td>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}`, textAlign: "right", color: p.directSpent > p.directCtrl ? T.critical : T.text, fontWeight: p.directSpent > p.directCtrl ? 700 : 400 }}>{fmt(p.directSpent)}</td>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}`, textAlign: "right" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 6, justifyContent: "flex-end" }}>
                        <div style={{ width: 50 }}><MiniBar value={p.directRate} color={p.directRate > 100 ? T.critical : p.directRate > 80 ? T.warning : T.primaryLight} height={6} /></div>
                        <span style={{ fontWeight: 600, color: p.directRate > 100 ? T.critical : T.text, minWidth: 42 }}>{p.directRate.toFixed(1)}%</span>
                      </div>
                    </td>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}`, textAlign: "right", color: T.textSec }}>{fmt(p.indirectSpent)}</td>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}`, textAlign: "right", fontWeight: 600 }}>{fmt(p.totalSpent)}</td>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}`, textAlign: "right", color: p.totalRate > 90 ? T.warning : T.textSec }}>{p.totalRate.toFixed(1)}%</td>
                    <td style={{ padding: "9px 10px", borderBottom: `1px solid ${T.border}` }}>
                      <Badge text={healthLabel(p.health)} color={healthColor(p.health)} bg={healthBg(p.health)} />
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot><tr style={{ background: T.tagBg }}>
                <td colSpan={3} style={{ padding: "10px 10px", fontWeight: 600, color: T.primary, borderTop: `2px solid ${T.border}` }}>合计</td>
                <td style={{ padding: "10px 10px", textAlign: "right", fontWeight: 700, color: T.primary, borderTop: `2px solid ${T.border}` }}>{fmt(sumTotal)}</td>
                <td style={{ padding: "10px 10px", textAlign: "right", fontWeight: 600, borderTop: `2px solid ${T.border}` }}>{fmt(sumDirectCtrl)}</td>
                <td style={{ padding: "10px 10px", textAlign: "right", borderTop: `2px solid ${T.border}`, color: T.textSec }}>{fmt(sumTotal - sumDirectCtrl)}</td>
                <td style={{ padding: "10px 10px", textAlign: "right", fontWeight: 600, borderTop: `2px solid ${T.border}` }}>{fmt(sumDirectSpent)}</td>
                <td style={{ padding: "10px 10px", textAlign: "right", fontWeight: 600, borderTop: `2px solid ${T.border}` }}>{overallDirectRate.toFixed(1)}%</td>
                <td style={{ padding: "10px 10px", textAlign: "right", borderTop: `2px solid ${T.border}`, color: T.textSec }}>{fmt(sumIndirectSpent)}</td>
                <td style={{ padding: "10px 10px", textAlign: "right", fontWeight: 700, color: T.primary, borderTop: `2px solid ${T.border}` }}>{fmt(sumTotalSpent)}</td>
                <td style={{ padding: "10px 10px", textAlign: "right", borderTop: `2px solid ${T.border}` }}>{overallTotalRate.toFixed(1)}%</td>
                <td style={{ padding: "10px 10px", borderTop: `2px solid ${T.border}` }}></td>
              </tr></tfoot>
            </table>
          </div>
        </Card>

        {/* Calculation Notes */}
        <Card style={{ background: T.tagBg, border: `1px solid ${T.cardBorder}` }}>
          <SectionTitle title="指标计算说明" />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, fontSize: 12, color: T.textSec, lineHeight: 1.8 }}>
            <div>
              <div style={{ fontWeight: 600, color: T.primary, marginBottom: 4 }}>原始字段</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>总经费</span> — 项目批复的总经费额度</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>直接成本控制数</span> — 直接成本的预算控制上限</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>直接成本支出金额</span> — 已实际发生的直接成本</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>间接费用支出和收益总额</span> — 已发生的间接费用</div>
            </div>
            <div>
              <div style={{ fontWeight: 600, color: T.primary, marginBottom: 4 }}>派生指标（实时计算）</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>预留间接费用和收益</span> = 总经费 − 直接成本控制数</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>直接成本执行率</span> = 直接成本支出 ÷ 直接成本控制数 × 100%</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>总支出</span> = 直接成本支出 + 间接费用支出</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>总经费执行率</span> = 总支出 ÷ 总经费 × 100%</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>经费健康度</span> = 对比直接成本执行率与时间进度（已用月数÷总月数），偏差 ≤10% 为正常，超前&gt;10% 为偏快，滞后&gt;10% 为偏慢，执行率&gt;100% 为超支</div>
            </div>
          </div>
        </Card>
      </div>

      {/* Footer */}
      <div style={{ textAlign: "center", padding: "12px 24px", borderTop: `1px solid ${T.cardBorder}`, fontSize: 11, color: T.textMuted, background: "#f0f5fb" }}>
        DECISION TWINS SYSTEM · V4.0 · 系统运行正常 · 数据实时刷新
      </div>
    </div>
  );
}
