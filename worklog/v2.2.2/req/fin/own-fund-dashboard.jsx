import { useState, useEffect, useRef, useCallback } from "react";

// ═══════════════════════════════════════════════════════════════
// ECHARTS LOADER — load from CDN
// ═══════════════════════════════════════════════════════════════
const ECHARTS_CDN = "https://cdnjs.cloudflare.com/ajax/libs/echarts/5.5.0/echarts.min.js";

function useECharts() {
  const [ready, setReady] = useState(!!window.echarts);
  useEffect(() => {
    if (window.echarts) { setReady(true); return; }
    const s = document.createElement("script");
    s.src = ECHARTS_CDN;
    s.onload = () => setReady(true);
    document.head.appendChild(s);
  }, []);
  return ready;
}

function EChart({ option, height = 300, style }) {
  const ref = useRef(null);
  const chartRef = useRef(null);
  const ready = useECharts();

  useEffect(() => {
    if (!ready || !ref.current) return;
    if (!chartRef.current) {
      chartRef.current = window.echarts.init(ref.current);
    }
    chartRef.current.setOption(option, true);
    const onResize = () => chartRef.current?.resize();
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, [ready, option]);

  useEffect(() => {
    return () => { chartRef.current?.dispose(); chartRef.current = null; };
  }, []);

  return <div ref={ref} style={{ width: "100%", height, ...style }} />;
}

// ═══════════════════════════════════════════════════════════════
// MOCK DATA
// ═══════════════════════════════════════════════════════════════
const FUND_DATA = [
  { period: "2026年初", type: "opening", career: 1200, careerNote: "上年结转", deprec: 850, deprecNote: "上年结转", welfare: 320, safety: 180 },
  { period: "2026年预计增加", type: "increase", career: 600, careerNote: "预算拨付+投资收益", deprec: 480, deprecNote: "本年计提", welfare: 150, safety: 95 },
  { period: "2026年预计使用", type: "usage", career: 450, careerNote: "科研项目支出", deprec: 520, deprecNote: "设备更新", welfare: 200, safety: 120 },
  { period: "2026年余额", type: "balance", career: 1350, careerNote: "= 1200+600-450", deprec: 810, deprecNote: "= 850+480-520", welfare: 270, safety: 155 },
  { period: "2027年初", type: "opening", career: 1350, careerNote: "承接2026年余额", deprec: 810, deprecNote: "承接2026年余额", welfare: 270, safety: 155 },
  { period: "2027年预计增加", type: "increase", career: 720, careerNote: "预算拨付+投资收益", deprec: 510, deprecNote: "本年计提", welfare: 180, safety: 110 },
  { period: "2027年预计使用", type: "usage", career: 580, careerNote: "科研项目+基建支出", deprec: 490, deprecNote: "设备更新+维修", welfare: 160, safety: 130 },
  { period: "2027年余额", type: "balance", career: 1490, careerNote: "= 1350+720-580", deprec: 830, deprecNote: "= 810+510-490", welfare: 290, safety: 135 },
];
const DATA = FUND_DATA.map(d => ({ ...d, total: d.career + d.deprec + d.welfare + d.safety }));
const YEARS = ["2026", "2027"];
const FUND_KEYS = ["career", "deprec", "welfare", "safety"];
const FUND_LABELS = ["事业基金", "折旧基金", "职工福利基金", "安全生产基金"];
const FUND_COLORS = ["#2980b9", "#27ae60", "#e67e22", "#8e44ad"];

// ═══════════════════════════════════════════════════════════════
// THEME
// ═══════════════════════════════════════════════════════════════
const T = {
  bg: "#eaf2fb",
  headerBg: "linear-gradient(135deg, #044B8C 0%, #0a5599 50%, #044B8C 100%)",
  card: "#ffffff", cardBorder: "#d6e4f0", cardShadow: "0 1px 4px rgba(26,82,118,0.08)",
  primary: "#044B8C", primaryLight: "#2980b9", accent: "#2471a3",
  good: "#1e8449", warning: "#d4850a", critical: "#c0392b",
  text: "#1c2833", textSec: "#566573", textMuted: "#909eab",
  border: "#dce6f0", rowAlt: "#f5f9fd", tagBg: "#eaf2fa",
  goodBg: "#e8f8f0", warningBg: "#fef5e7", criticalBg: "#fdedec",
};

function fmt(n) { return n.toFixed(0).replace(/\B(?=(\d{3})+(?!\d))/g, ","); }

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

// ═══════════════════════════════════════════════════════════════
// ECHARTS OPTIONS BUILDERS
// ═══════════════════════════════════════════════════════════════
function buildDonutOption(segments) {
  const total = segments.reduce((s, seg) => s + seg.value, 0);
  return {
    tooltip: { trigger: "item", formatter: "{b}: {c} 万 ({d}%)" },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11, color: T.textSec } },
    series: [{
      type: "pie", radius: ["50%", "75%"], center: ["50%", "45%"],
      avoidLabelOverlap: true,
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 13, fontWeight: 600 } },
      data: segments.map((s, i) => ({
        value: s.value, name: s.label,
        itemStyle: { color: FUND_COLORS[i] },
      })),
    }],
    graphic: [{
      type: "text", left: "center", top: "38%",
      style: { text: fmt(total), fontSize: 20, fontWeight: 700, fill: T.primary, textAlign: "center" },
    }, {
      type: "text", left: "center", top: "50%",
      style: { text: "万元", fontSize: 11, fill: T.textMuted, textAlign: "center" },
    }],
  };
}

function buildWaterfallOption(opening, increase, usage, balance, year) {
  return {
    tooltip: {
      trigger: "axis", axisPointer: { type: "shadow" },
      formatter: (params) => {
        const p = params.find(p => p.value > 0 && p.seriesIndex > 0) || params[0];
        return `${p.name}: ${fmt(p.value)} 万`;
      },
    },
    grid: { left: 50, right: 20, top: 30, bottom: 30 },
    xAxis: { type: "category", data: ["年初", "预计增加", "预计使用", "年末余额"], axisTick: { show: false }, axisLine: { lineStyle: { color: T.border } }, axisLabel: { color: T.textSec, fontSize: 11 } },
    yAxis: { type: "value", axisLabel: { color: T.textMuted, fontSize: 10 }, splitLine: { lineStyle: { color: T.border, type: "dashed" } }, axisLine: { show: false } },
    series: [
      // Invisible base for waterfall
      {
        type: "bar", stack: "w", silent: true,
        itemStyle: { color: "transparent", borderColor: "transparent" },
        data: [0, opening, opening + increase - usage, 0],
      },
      // Visible bars
      {
        type: "bar", stack: "w", barWidth: 40,
        data: [
          { value: opening, itemStyle: { color: T.primaryLight } },
          { value: increase, itemStyle: { color: T.good } },
          { value: usage, itemStyle: { color: T.critical } },
          { value: balance, itemStyle: { color: T.primary } },
        ],
        label: {
          show: true, position: "top", fontSize: 11, fontWeight: 600,
          formatter: (p) => {
            const v = p.value;
            if (p.dataIndex === 1) return "+" + fmt(v);
            if (p.dataIndex === 2) return "-" + fmt(v);
            return fmt(v);
          },
          color: T.text,
        },
      },
    ],
  };
}

function buildStackedBarOption(data2026, data2027) {
  return {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: (params) => {
      let s = params[0].name + "<br/>";
      params.forEach(p => { if (p.value) s += `${p.marker} ${p.seriesName}: ${fmt(p.value)} 万<br/>`; });
      return s;
    }},
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11, color: T.textSec } },
    grid: { left: 50, right: 20, top: 20, bottom: 40 },
    xAxis: { type: "category", data: FUND_LABELS, axisTick: { show: false }, axisLine: { lineStyle: { color: T.border } }, axisLabel: { color: T.textSec, fontSize: 11 } },
    yAxis: { type: "value", axisLabel: { color: T.textMuted, fontSize: 10 }, splitLine: { lineStyle: { color: T.border, type: "dashed" } }, axisLine: { show: false } },
    series: [
      {
        name: "2026年余额", type: "bar", barGap: "20%", barWidth: 28,
        data: FUND_KEYS.map((k, i) => ({ value: data2026[k], itemStyle: { color: FUND_COLORS[i], opacity: 0.5 } })),
      },
      {
        name: "2027年余额", type: "bar", barWidth: 28,
        data: FUND_KEYS.map((k, i) => ({ value: data2027[k], itemStyle: { color: FUND_COLORS[i] } })),
      },
    ],
  };
}

function buildFundMiniOption(opening, increase, usage, balance, color) {
  return {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: (params) => {
      const p = params.find(p => p.value > 0 && p.seriesIndex > 0) || params[0];
      return `${p.name}: ${fmt(p.value)} 万`;
    }},
    grid: { left: 40, right: 10, top: 20, bottom: 24 },
    xAxis: { type: "category", data: ["年初", "增加", "使用", "余额"], axisTick: { show: false }, axisLine: { lineStyle: { color: T.border } }, axisLabel: { color: T.textMuted, fontSize: 10 } },
    yAxis: { type: "value", axisLabel: { color: T.textMuted, fontSize: 9 }, splitLine: { lineStyle: { color: T.border, type: "dashed" } }, axisLine: { show: false } },
    series: [
      { type: "bar", stack: "w", silent: true, itemStyle: { color: "transparent", borderColor: "transparent" }, data: [0, opening, opening + increase - usage, 0] },
      {
        type: "bar", stack: "w", barWidth: 28,
        data: [
          { value: opening, itemStyle: { color } },
          { value: increase, itemStyle: { color: T.good } },
          { value: usage, itemStyle: { color: T.critical } },
          { value: balance, itemStyle: { color } },
        ],
        label: { show: true, position: "top", fontSize: 10, fontWeight: 600, color: T.textSec, formatter: (p) => fmt(p.value) },
      },
    ],
  };
}

function buildGaugeOption(value, title, color) {
  return {
    series: [{
      type: "gauge", radius: "90%", startAngle: 210, endAngle: -30,
      min: 0, max: 100,
      axisLine: { lineStyle: { width: 12, color: [[value / 100, color], [1, "#e8eff7"]] } },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      pointer: { show: false },
      title: { show: true, offsetCenter: [0, "65%"], fontSize: 11, color: T.textSec, fontWeight: 500 },
      detail: { valueAnimation: true, fontSize: 22, fontWeight: 700, color: color, offsetCenter: [0, "30%"], formatter: "{value}%" },
      data: [{ value: value.toFixed(1), name: title }],
    }],
  };
}

// ═══════════════════════════════════════════════════════════════
// MAIN DASHBOARD
// ═══════════════════════════════════════════════════════════════
export default function OwnFundDashboard() {
  const [time, setTime] = useState(new Date());
  const [selectedYear, setSelectedYear] = useState("2026");
  const echartsReady = useECharts();
  useEffect(() => { const t = setInterval(() => setTime(new Date()), 1000); return () => clearInterval(t); }, []);

  const yearData = DATA.filter(d => d.period.startsWith(selectedYear));
  const opening = yearData.find(d => d.type === "opening");
  const increase = yearData.find(d => d.type === "increase");
  const usage = yearData.find(d => d.type === "usage");
  const balance = yearData.find(d => d.type === "balance");

  const bal2026 = DATA.find(d => d.period === "2026年余额");
  const bal2027 = DATA.find(d => d.period === "2027年余额");

  const totalOpening = opening?.total || 0;
  const totalIncrease = increase?.total || 0;
  const totalUsage = usage?.total || 0;
  const totalBalance = balance?.total || 0;
  const usageRate = totalOpening + totalIncrease > 0 ? (totalUsage / (totalOpening + totalIncrease) * 100) : 0;
  const growthRate = totalOpening > 0 ? ((totalBalance - totalOpening) / totalOpening * 100) : 0;

  const donutSegments = FUND_LABELS.map((l, i) => ({ label: l, value: balance?.[FUND_KEYS[i]] || 0 }));

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
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round"><rect x="2" y="6" width="20" height="14" rx="2" /><path d="M2 10h20M12 6v-2" /></svg>
          </div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.65)", letterSpacing: 1, lineHeight: 1.4 }}>DECISION TWINS<br/>FUND MANAGEMENT</div>
        </div>
        <div style={{ textAlign: "center" }}>
          <div style={{ fontSize: 22, fontWeight: 700, color: "#fff", letterSpacing: 4 }}>自有资金大屏</div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.55)", letterSpacing: 2, marginTop: 2 }}>OWN FUND OVERVIEW</div>
        </div>
        <div style={{ textAlign: "right", color: "#fff", minWidth: 180 }}>
          <div style={{ fontSize: 16, fontWeight: 600 }}>{time.toLocaleTimeString("zh-CN", { hour12: false })}</div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.6)", marginTop: 1 }}>{time.toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric", weekday: "short" })}</div>
        </div>
      </div>

      <div style={{ padding: "18px 24px", maxWidth: 1480, margin: "0 auto", display: "flex", flexDirection: "column", gap: 16 }}>

        {/* Year Selector */}
        <Card style={{ padding: "12px 18px", display: "flex", alignItems: "center", gap: 16 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: T.primary }}>年度选择</span>
          <div style={{ display: "flex", gap: 0, background: "#e0ecf5", borderRadius: 6, padding: 3 }}>
            {YEARS.map(y => (
              <button key={y} onClick={() => setSelectedYear(y)} style={{
                background: selectedYear === y ? "#fff" : "transparent", color: selectedYear === y ? T.primary : T.textMuted,
                border: "none", borderRadius: 4, padding: "7px 24px", fontSize: 13, fontWeight: selectedYear === y ? 600 : 400,
                cursor: "pointer", boxShadow: selectedYear === y ? T.cardShadow : "none", fontFamily: "inherit", transition: "all 0.2s",
              }}>{y}年</button>
            ))}
          </div>
          <span style={{ fontSize: 12, color: T.textMuted, marginLeft: "auto" }}>数据周期：{selectedYear}年初 → {selectedYear}年末余额</span>
        </Card>

        {/* KPI Row */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(6, 1fr)", gap: 12 }}>
          <KPICard label="年初合计" value={fmt(totalOpening)} unit="万" color={T.primary} />
          <KPICard label="预计增加" value={fmt(totalIncrease)} unit="万" color={T.good} sub="拨付+计提+收益" />
          <KPICard label="预计使用" value={fmt(totalUsage)} unit="万" color={T.critical} sub="各类支出" />
          <KPICard label="年末余额" value={fmt(totalBalance)} unit="万" color={T.accent} />
          <KPICard label="资金使用率" value={usageRate.toFixed(1)} unit="%" color={usageRate > 80 ? T.warning : T.primaryLight} sub="使用÷(年初+增加)" />
          <KPICard label="余额增长率" value={(growthRate >= 0 ? "+" : "") + growthRate.toFixed(1)} unit="%" color={growthRate >= 0 ? T.good : T.critical} sub="(余额-年初)÷年初" />
        </div>

        {/* Charts Row 1 */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1.2fr 1fr", gap: 16 }}>
          <Card>
            <SectionTitle title={`${selectedYear}年末余额构成`} sub="四类基金余额占比" />
            {echartsReady && <EChart option={buildDonutOption(donutSegments)} height={260} />}
          </Card>
          <Card>
            <SectionTitle title={`${selectedYear}年资金流动`} sub="瀑布图：年初 → 增加 → 使用 → 余额" />
            {echartsReady && <EChart option={buildWaterfallOption(totalOpening, totalIncrease, totalUsage, totalBalance, selectedYear)} height={260} />}
          </Card>
          <Card>
            <SectionTitle title="年度余额对比" sub="2026 vs 2027 各基金余额" />
            {echartsReady && bal2026 && bal2027 && <EChart option={buildStackedBarOption(bal2026, bal2027)} height={260} />}
          </Card>
        </div>

        {/* Charts Row 2 — Per fund waterfall + gauges */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12 }}>
          {FUND_KEYS.map((k, i) => (
            <Card key={k}>
              <div style={{ fontSize: 13, fontWeight: 600, color: FUND_COLORS[i], marginBottom: 6, display: "flex", alignItems: "center", gap: 6 }}>
                <span style={{ width: 3, height: 14, background: FUND_COLORS[i], borderRadius: 2, display: "inline-block" }} />
                {FUND_LABELS[i]}
              </div>
              {echartsReady && <EChart option={buildFundMiniOption(
                opening?.[k] || 0, increase?.[k] || 0, usage?.[k] || 0, balance?.[k] || 0, FUND_COLORS[i]
              )} height={160} />}
            </Card>
          ))}
        </div>

        {/* Gauge Row */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
          <Card>
            <SectionTitle title="资金使用率仪表盘" sub="预计使用 ÷（年初 + 预计增加）× 100%" />
            {echartsReady && <EChart option={buildGaugeOption(usageRate, "使用率", usageRate > 80 ? T.warning : T.primaryLight)} height={200} />}
          </Card>
          <Card>
            <SectionTitle title="余额增长率仪表盘" sub="（年末余额 − 年初）÷ 年初 × 100%" />
            {echartsReady && <EChart option={buildGaugeOption(Math.abs(growthRate), growthRate >= 0 ? "正增长" : "负增长", growthRate >= 0 ? T.good : T.critical)} height={200} />}
          </Card>
        </div>

        {/* Main Table */}
        <Card>
          <SectionTitle title="自有资金明细表" sub="单位：万元 | 含全部年度数据" />
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13, whiteSpace: "nowrap" }}>
              <thead>
                <tr>
                  <th rowSpan={2} style={{ padding: "10px 12px", color: T.primary, borderBottom: `2px solid ${T.border}`, fontSize: 11, fontWeight: 600, position: "sticky", top: 0, background: T.card, zIndex: 2, textAlign: "left", verticalAlign: "bottom" }}>年度</th>
                  {FUND_LABELS.map((l, i) => {
                    const hasNote = i < 2;
                    return <th key={i} colSpan={hasNote ? 2 : 1} style={{ padding: "8px 12px", color: FUND_COLORS[i], borderBottom: `1px solid ${T.border}`, fontSize: 11, fontWeight: 600, textAlign: "center", background: T.card, position: "sticky", top: 0, zIndex: 2 }}>{l}</th>;
                  })}
                  <th rowSpan={2} style={{ padding: "8px 12px", color: T.primary, borderBottom: `2px solid ${T.border}`, fontSize: 11, fontWeight: 700, textAlign: "center", background: T.card, position: "sticky", top: 0, zIndex: 2, verticalAlign: "bottom" }}>合计</th>
                </tr>
                <tr>
                  <th style={{ padding: "6px 12px", color: T.textSec, borderBottom: `2px solid ${T.border}`, fontSize: 10, textAlign: "right", background: T.card, position: "sticky", top: 34, zIndex: 1 }}>金额</th>
                  <th style={{ padding: "6px 12px", color: T.textMuted, borderBottom: `2px solid ${T.border}`, fontSize: 10, textAlign: "left", background: T.card, position: "sticky", top: 34, zIndex: 1 }}>备注</th>
                  <th style={{ padding: "6px 12px", color: T.textSec, borderBottom: `2px solid ${T.border}`, fontSize: 10, textAlign: "right", background: T.card, position: "sticky", top: 34, zIndex: 1 }}>金额</th>
                  <th style={{ padding: "6px 12px", color: T.textMuted, borderBottom: `2px solid ${T.border}`, fontSize: 10, textAlign: "left", background: T.card, position: "sticky", top: 34, zIndex: 1 }}>备注</th>
                  <th style={{ padding: "6px 12px", color: T.textSec, borderBottom: `2px solid ${T.border}`, fontSize: 10, textAlign: "right", background: T.card, position: "sticky", top: 34, zIndex: 1 }}>金额</th>
                  <th style={{ padding: "6px 12px", color: T.textSec, borderBottom: `2px solid ${T.border}`, fontSize: 10, textAlign: "right", background: T.card, position: "sticky", top: 34, zIndex: 1 }}>金额</th>
                </tr>
              </thead>
              <tbody>
                {DATA.map((d, ri) => {
                  const isBalance = d.type === "balance";
                  const isUsage = d.type === "usage";
                  const rowBg = isBalance ? T.tagBg : ri % 2 === 0 ? T.card : T.rowAlt;
                  const tw = isBalance ? 700 : 400;
                  const tc = isUsage ? T.critical : T.text;
                  const prefix = isUsage ? "-" : "";
                  return (
                    <tr key={ri} style={{ background: rowBg }}>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, fontWeight: 600, color: isBalance ? T.primary : T.text }}>{d.period}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, textAlign: "right", fontWeight: tw, color: tc }}>{prefix}{fmt(d.career)}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, color: T.textMuted, fontSize: 11 }}>{d.careerNote}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, textAlign: "right", fontWeight: tw, color: tc }}>{prefix}{fmt(d.deprec)}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, color: T.textMuted, fontSize: 11 }}>{d.deprecNote}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, textAlign: "right", fontWeight: tw, color: tc }}>{prefix}{fmt(d.welfare)}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, textAlign: "right", fontWeight: tw, color: tc }}>{prefix}{fmt(d.safety)}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, textAlign: "right", fontWeight: 700, color: isBalance ? T.primary : tc }}>{prefix}{fmt(d.total)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </Card>

        {/* Calculation Notes */}
        <Card style={{ background: T.tagBg, border: `1px solid ${T.cardBorder}` }}>
          <SectionTitle title="指标计算说明" />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, fontSize: 12, color: T.textSec, lineHeight: 1.8 }}>
            <div>
              <div style={{ fontWeight: 600, color: T.primary, marginBottom: 4 }}>表内计算</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>年末余额</span> = 年初余额 + 预计增加 − 预计使用</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>下年年初</span> = 上年年末余额（自动承接）</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>合计列</span> = 事业基金 + 折旧基金 + 职工福利基金 + 安全生产基金</div>
            </div>
            <div>
              <div style={{ fontWeight: 600, color: T.primary, marginBottom: 4 }}>KPI 指标</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>资金使用率</span> = 预计使用 ÷（年初余额 + 预计增加）× 100%</div>
              <div><span style={{ color: T.text, fontWeight: 500 }}>余额增长率</span> =（年末余额 − 年初余额）÷ 年初余额 × 100%</div>
              <div style={{ color: T.textMuted, marginTop: 4, fontSize: 11 }}>正增长表示资金规模扩大，负增长表示资金净消耗</div>
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
