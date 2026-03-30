import { useState, useEffect, useRef, useMemo } from "react";

const EC_CDN = "https://cdnjs.cloudflare.com/ajax/libs/echarts/5.5.0/echarts.min.js";
function useEC() { const [ok, setOk] = useState(!!window.echarts); useEffect(() => { if (window.echarts) { setOk(true); return; } const s = document.createElement("script"); s.src = EC_CDN; s.onload = () => setOk(true); document.head.appendChild(s); }, []); return ok; }
function EC({ option, h = 260 }) { const ref = useRef(null), c = useRef(null), ok = useEC(); useEffect(() => { if (!ok || !ref.current) return; if (!c.current) c.current = window.echarts.init(ref.current); c.current.setOption(option, true); const fn = () => c.current?.resize(); window.addEventListener("resize", fn); return () => window.removeEventListener("resize", fn); }, [ok, option]); useEffect(() => () => { c.current?.dispose(); c.current = null; }, []); return <div ref={ref} style={{ width: "100%", height: h }} />; }

const B = "#044B8C", BL = "#2980b9", Grn = "#1e8449", Org = "#d4850a", Red = "#c0392b", Pur = "#8e44ad";
const pms = ["张工", "李工", "王强", "赵敏", "陈静", "刘洋", "孙磊", "周芳", "吴杰", "郑华"];
const depts = ["制造部", "电力部", "IT部", "采购部", "质量部", "安环部", "研发部", "营销部"];
const pnames = ["涡轮组装", "电网升级", "数据中心", "供应链平台", "质量认证", "产线改造", "ERP升级", "环保改造", "新材料研发", "客户门户"];
const seed = i => ((i * 9301 + 49297) % 233280) / 233280;

const RAW = Array.from({ length: 30 }, (_, i) => {
  const tf = Math.round(seed(i) * 12000 + 1000);
  const dc = Math.round(tf * (0.7 + seed(i + 10) * 0.15));
  const ds = Math.round(dc * (0.3 + seed(i + 20) * 0.85));
  const is_ = Math.round((tf - dc) * (0.1 + seed(i + 30) * 0.8));
  const months = Math.round(6 + seed(i + 40) * 24);
  const elapsed = Math.round(seed(i + 50) * months);
  return { id: `PRJ-${(i + 1).toString().padStart(3, "0")}`, name: pnames[i % pnames.length] + `-${i + 1}`, dept: depts[Math.floor(seed(i + 60) * depts.length)], pm: pms[Math.floor(seed(i + 70) * pms.length)], cycle: `2025.${(1 + Math.floor(seed(i + 80) * 12)).toString().padStart(2, "0")}-2026.${(1 + Math.floor(seed(i + 90) * 12)).toString().padStart(2, "0")}`, months, elapsed, totalFund: tf, directCtrl: dc, directSpent: ds, indirectSpent: is_ };
});

const PROJECTS = RAW.map(p => {
  const ri = p.totalFund - p.directCtrl, dr = p.directCtrl > 0 ? p.directSpent / p.directCtrl * 100 : 0;
  const ts = p.directSpent + p.indirectSpent, tr = p.totalFund > 0 ? ts / p.totalFund * 100 : 0;
  const tp = p.months > 0 ? p.elapsed / p.months * 100 : 0, dev = dr - tp;
  const health = dr > 100 ? "overrun" : Math.abs(dev) <= 10 ? "good" : dev > 10 ? "fast" : "slow";
  return { ...p, reserveIndirect: ri, directRate: dr, totalSpent: ts, totalRate: tr, timeRate: tp, health };
});

const hColor = h => h === "good" ? Grn : h === "overrun" ? Red : h === "fast" ? Org : "#7f8c8d";
const hLabel = h => h === "good" ? "正常" : h === "overrun" ? "超支" : h === "fast" ? "偏快" : "偏慢";
const hBg = h => h === "good" ? "bg-green-50 text-green-700" : h === "overrun" ? "bg-red-50 text-red-700" : h === "fast" ? "bg-orange-50 text-orange-700" : "bg-gray-100 text-gray-500";
const fmt = n => n.toFixed(0).replace(/\B(?=(\d{3})+(?!\d))/g, ",");

export default function App() {
  const [time, setTime] = useState(new Date()), [fDept, setFDept] = useState(""), [fH, setFH] = useState("");
  const ok = useEC();
  useEffect(() => { const t = setInterval(() => setTime(new Date()), 1000); return () => clearInterval(t); }, []);

  const filtered = useMemo(() => PROJECTS.filter(p => (!fDept || p.dept === fDept) && (!fH || p.health === fH)), [fDept, fH]);
  const stf = filtered.reduce((s, p) => s + p.totalFund, 0), sdc = filtered.reduce((s, p) => s + p.directCtrl, 0);
  const sds = filtered.reduce((s, p) => s + p.directSpent, 0), sis = filtered.reduce((s, p) => s + p.indirectSpent, 0);
  const sts = sds + sis, odr = sdc > 0 ? sds / sdc * 100 : 0, otr = stf > 0 ? sts / stf * 100 : 0;
  const overrun = filtered.filter(p => p.health === "overrun").length;

  const stackOpt = {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: ps => { let s = ps[0].axisValue + "<br/>"; ps.forEach(p => { if (p.value) s += `${p.marker} ${p.seriesName}: ${fmt(p.value)}万<br/>`; }); return s; } },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: "#566573" } },
    grid: { left: 50, right: 20, top: 10, bottom: 40 },
    xAxis: { type: "category", data: filtered.map(p => p.name.slice(0, 5)), axisLabel: { fontSize: 10, color: "#909eab", rotate: 30 }, axisTick: { show: false } },
    yAxis: { type: "value", axisLabel: { fontSize: 10, color: "#909eab" }, splitLine: { lineStyle: { type: "dashed", color: "#e8eff7" } } },
    series: [
      { name: "直接成本", type: "bar", stack: "t", data: filtered.map(p => p.directSpent), barWidth: 20, itemStyle: { color: BL } },
      { name: "间接费用", type: "bar", stack: "t", data: filtered.map(p => p.indirectSpent), itemStyle: { color: Pur } },
      { name: "剩余", type: "bar", stack: "t", data: filtered.map(p => Math.max(p.totalFund - p.directSpent - p.indirectSpent, 0)), itemStyle: { color: "#e0eaf4" } },
    ],
  };

  const dualOpt = {
    tooltip: { trigger: "axis" },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: "#566573" } },
    grid: { left: 50, right: 20, top: 10, bottom: 40 },
    xAxis: { type: "category", data: filtered.map(p => p.name.slice(0, 5)), axisLabel: { fontSize: 10, color: "#909eab", rotate: 30 }, axisTick: { show: false } },
    yAxis: { type: "value", axisLabel: { fontSize: 10, color: "#909eab", formatter: v => v + "%" }, splitLine: { lineStyle: { type: "dashed", color: "#e8eff7" } } },
    series: [
      { name: "时间进度", type: "bar", data: filtered.map(p => Math.round(p.timeRate)), barWidth: 14, barGap: "20%", itemStyle: { color: "#bdc3c7" } },
      { name: "成本执行率", type: "bar", data: filtered.map(p => ({ value: Math.round(p.directRate), itemStyle: { color: p.directRate > 100 ? Red : p.directRate > p.timeRate * 1.1 ? Org : BL } })), barWidth: 14 },
    ],
  };

  const healthCounts = { good: filtered.filter(p => p.health === "good").length, overrun: filtered.filter(p => p.health === "overrun").length, fast: filtered.filter(p => p.health === "fast").length, slow: filtered.filter(p => p.health === "slow").length };
  const gaugeOpt = (val, title, color) => ({ series: [{ type: "gauge", radius: "88%", startAngle: 210, endAngle: -30, min: 0, max: 100, axisLine: { lineStyle: { width: 14, color: [[Math.min(val, 100) / 100, color], [1, "#e8eff7"]] } }, axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false }, pointer: { show: false }, title: { show: true, offsetCenter: [0, "65%"], fontSize: 11, color: "#566573" }, detail: { valueAnimation: true, fontSize: 22, fontWeight: 700, color, offsetCenter: [0, "25%"], formatter: "{value}%" }, data: [{ value: val.toFixed(1), name: title }] }] });

  const sel = "h-8 px-3 rounded-md border border-[#d6e4f0] text-[13px] text-gray-700 bg-white outline-none cursor-pointer";

  return (
    <div className="min-h-screen" style={{ background: "#eaf2fb", fontFamily: "'Noto Sans SC', -apple-system, sans-serif" }}>
      <div className="flex items-center justify-between px-7 py-3.5" style={{ background: `linear-gradient(135deg, ${B} 0%, #0a5599 50%, ${B} 100%)` }}>
        <div className="flex items-center gap-3 min-w-[180px]">
          <div className="w-9 h-9 rounded-lg bg-white/15 border border-white/25 flex items-center justify-center">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round"><path d="M12 2v20M2 12h20M6 6l12 12M18 6L6 18" /></svg>
          </div>
          <div className="text-[11px] text-white/65 tracking-wider leading-tight">DECISION TWINS<br />FUND CONTROL</div>
        </div>
        <div className="text-center"><div className="text-[22px] font-bold text-white tracking-[4px]">项目经费大屏</div><div className="text-[11px] text-white/55 tracking-[2px] mt-0.5">PROJECT FUND OVERVIEW</div></div>
        <div className="text-right min-w-[180px] text-white"><div className="text-[16px] font-semibold">{time.toLocaleTimeString("zh-CN", { hour12: false })}</div><div className="text-[11px] text-white/60 mt-0.5">{time.toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric", weekday: "short" })}</div></div>
      </div>
      <div className="max-w-[1480px] mx-auto p-5 flex flex-col gap-4">
        <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm px-5 py-3 flex items-center gap-4 flex-wrap">
          <span className="text-[13px] font-semibold" style={{ color: B }}>筛选条件</span>
          <select value={fDept} onChange={e => setFDept(e.target.value)} className={sel}><option value="">全部部门</option>{depts.map(d => <option key={d}>{d}</option>)}</select>
          <select value={fH} onChange={e => setFH(e.target.value)} className={sel}><option value="">全部状态</option><option value="good">正常</option><option value="overrun">超支</option><option value="fast">偏快</option><option value="slow">偏慢</option></select>
          {(fDept || fH) && <button onClick={() => { setFDept(""); setFH(""); }} className="h-8 px-4 rounded-md border border-[#d6e4f0] bg-blue-50 text-[12px] font-medium cursor-pointer" style={{ color: B }}>清除</button>}
          <span className="text-[12px] text-gray-400 ml-auto">共 {filtered.length} 个项目</span>
        </div>

        <div className="grid grid-cols-6 gap-3">
          <KPI label="总经费" value={fmt(stf)} unit="万" color={B} />
          <KPI label="直接成本控制数" value={fmt(sdc)} unit="万" color={BL} />
          <KPI label="直接成本已支出" value={fmt(sds)} unit="万" color="#2471a3" />
          <KPI label="直接成本执行率" value={odr.toFixed(1)} unit="%" color={odr > 100 ? Red : BL} />
          <KPI label="总经费执行率" value={otr.toFixed(1)} unit="%" color={otr > 90 ? Org : BL} />
          <KPI label="超支项目" value={overrun} unit="个" color={overrun > 0 ? Red : Grn} sub={overrun > 0 ? "需立即关注" : "无超支"} />
        </div>

        <div className="grid grid-cols-3 gap-4">
          <Card title="经费结构分布" sub="直接支出 / 间接支出 / 剩余"><EC option={stackOpt} h={250} /></Card>
          <Card title="执行率 vs 时间进度" sub="灰=时间, 彩色=成本执行率"><EC option={dualOpt} h={250} /></Card>
          <Card title="经费健康度" sub="基于成本执行率与时间进度偏差">
            <div className="grid grid-cols-2 gap-3">
              {[{ l: "正常", k: "good", c: Grn, bg: "bg-green-50" }, { l: "超支", k: "overrun", c: Red, bg: "bg-red-50" }, { l: "偏快", k: "fast", c: Org, bg: "bg-orange-50" }, { l: "偏慢", k: "slow", c: "#7f8c8d", bg: "bg-gray-50" }].map(s => (
                <div key={s.k} className={`${s.bg} rounded-lg p-3.5 text-center border`} style={{ borderColor: s.c + "22" }}>
                  <div className="text-[28px] font-bold" style={{ color: s.c }}>{healthCounts[s.k]}</div>
                  <div className="text-[12px] font-medium mt-0.5" style={{ color: s.c }}>{s.l}</div>
                </div>
              ))}
            </div>
            <div className="mt-3 p-3 bg-blue-50/60 rounded-md text-[11px] text-gray-500 leading-relaxed">
              <span className="font-semibold" style={{ color: B }}>判定：</span>
              <span className="text-red-600">超支</span>=执行率&gt;100%，
              <span className="text-green-700">正常</span>=偏差≤10%，
              <span className="text-orange-600">偏快</span>=超前&gt;10%，
              <span className="text-gray-500">偏慢</span>=滞后&gt;10%
            </div>
          </Card>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <Card title="直接成本执行率">{ok && <EC option={gaugeOpt(odr, "整体执行率", odr > 100 ? Red : BL)} h={200} />}</Card>
          <Card title="总经费执行率">{ok && <EC option={gaugeOpt(otr, "整体执行率", otr > 90 ? Org : BL)} h={200} />}</Card>
        </div>

        <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4">
          <div className="text-[15px] font-semibold mb-3 flex items-center gap-2" style={{ color: B }}><span className="w-[3px] h-4 rounded-sm inline-block" style={{ background: BL }} />项目经费明细表 <span className="text-[12px] text-gray-400 font-normal ml-2">单位：万元</span></div>
          <div className="overflow-auto max-h-[440px]">
            <table className="w-full text-[12px] border-collapse whitespace-nowrap">
              <thead><tr className="border-b-2 border-[#dce6f0]">
                {["项目编号", "项目名称", "研制周期", "总经费", "直接成本控制数", "预留间接费用", "直接成本支出", "直接成本执行率", "间接费用支出", "总支出", "总执行率", "健康度"].map((h, i) => (
                  <th key={i} className={`py-2.5 px-2.5 text-[10px] font-semibold tracking-wider sticky top-0 bg-white z-10 ${i >= 3 ? "text-right" : "text-left"}`} style={{ color: B }}>{h}</th>
                ))}
              </tr></thead>
              <tbody>{filtered.map((p, ri) => (
                <tr key={p.id} className={`border-b border-[#dce6f0]/50 ${ri % 2 ? "bg-[#f5f9fd]" : ""}`}>
                  <td className="py-2 px-2.5 font-semibold" style={{ color: "#2e86c1" }}>{p.id}</td>
                  <td className="py-2 px-2.5 text-gray-800 font-medium">{p.name}</td>
                  <td className="py-2 px-2.5 text-gray-500 text-[11px]">{p.cycle}</td>
                  <td className="py-2 px-2.5 text-right font-semibold">{fmt(p.totalFund)}</td>
                  <td className="py-2 px-2.5 text-right">{fmt(p.directCtrl)}</td>
                  <td className="py-2 px-2.5 text-right text-gray-500">{fmt(p.reserveIndirect)}</td>
                  <td className={`py-2 px-2.5 text-right ${p.directSpent > p.directCtrl ? "text-red-600 font-bold" : ""}`}>{fmt(p.directSpent)}</td>
                  <td className="py-2 px-2.5 text-right">
                    <div className="flex items-center gap-1.5 justify-end">
                      <div className="w-12 h-1.5 bg-gray-200 rounded-full overflow-hidden"><div className="h-full rounded-full" style={{ width: `${Math.min(p.directRate, 100)}%`, background: p.directRate > 100 ? Red : p.directRate > 80 ? Org : BL }} /></div>
                      <span className={`font-semibold ${p.directRate > 100 ? "text-red-600" : ""}`}>{p.directRate.toFixed(1)}%</span>
                    </div>
                  </td>
                  <td className="py-2 px-2.5 text-right text-gray-500">{fmt(p.indirectSpent)}</td>
                  <td className="py-2 px-2.5 text-right font-semibold">{fmt(p.totalSpent)}</td>
                  <td className={`py-2 px-2.5 text-right ${p.totalRate > 90 ? "text-orange-600" : ""}`}>{p.totalRate.toFixed(1)}%</td>
                  <td className="py-2 px-2.5"><span className={`text-[11px] font-semibold px-2.5 py-0.5 rounded ${hBg(p.health)}`}>{hLabel(p.health)}</span></td>
                </tr>
              ))}</tbody>
              <tfoot><tr className="bg-blue-50/60 border-t-2 border-[#dce6f0]">
                <td colSpan={3} className="py-2.5 px-2.5 font-semibold" style={{ color: B }}>合计</td>
                <td className="py-2.5 px-2.5 text-right font-bold" style={{ color: B }}>{fmt(stf)}</td>
                <td className="py-2.5 px-2.5 text-right font-semibold">{fmt(sdc)}</td>
                <td className="py-2.5 px-2.5 text-right text-gray-500">{fmt(stf - sdc)}</td>
                <td className="py-2.5 px-2.5 text-right font-semibold">{fmt(sds)}</td>
                <td className="py-2.5 px-2.5 text-right font-semibold">{odr.toFixed(1)}%</td>
                <td className="py-2.5 px-2.5 text-right text-gray-500">{fmt(sis)}</td>
                <td className="py-2.5 px-2.5 text-right font-bold" style={{ color: B }}>{fmt(sts)}</td>
                <td className="py-2.5 px-2.5 text-right">{otr.toFixed(1)}%</td>
                <td></td>
              </tr></tfoot>
            </table>
          </div>
        </div>

        <div className="bg-blue-50/60 rounded-lg border border-[#d6e4f0] p-4">
          <div className="text-[15px] font-semibold mb-3 flex items-center gap-2" style={{ color: B }}><span className="w-[3px] h-4 rounded-sm inline-block" style={{ background: BL }} />指标计算说明</div>
          <div className="grid grid-cols-2 gap-6 text-[12px] text-gray-500 leading-relaxed">
            <div><div className="font-semibold mb-1" style={{ color: B }}>原始字段</div>
              <div><b className="text-gray-700">总经费</b> — 项目批复总额</div>
              <div><b className="text-gray-700">直接成本控制数</b> — 直接成本预算上限</div>
              <div><b className="text-gray-700">直接成本支出金额</b> — 已发生的直接成本</div>
              <div><b className="text-gray-700">间接费用支出</b> — 已发生的间接费用</div>
            </div>
            <div><div className="font-semibold mb-1" style={{ color: B }}>派生指标</div>
              <div><b className="text-gray-700">预留间接费用</b> = 总经费 − 直接成本控制数</div>
              <div><b className="text-gray-700">直接成本执行率</b> = 直接成本支出 ÷ 直接成本控制数 × 100%</div>
              <div><b className="text-gray-700">总支出</b> = 直接成本支出 + 间接费用支出</div>
              <div><b className="text-gray-700">总经费执行率</b> = 总支出 ÷ 总经费 × 100%</div>
              <div><b className="text-gray-700">健康度</b> = 对比执行率与时间进度偏差</div>
            </div>
          </div>
        </div>
      </div>
      <div className="text-center py-3 border-t border-[#d6e4f0] text-[11px] text-gray-400 bg-[#f0f5fb]">DECISION TWINS SYSTEM · V4.0 · 系统运行正常 · 数据实时刷新</div>
    </div>
  );
}
function KPI({ label, value, unit, color = "#044B8C", sub }) { return (<div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4 text-center"><div className="text-[11px] text-gray-400 uppercase tracking-wider mb-2 font-medium">{label}</div><div className="text-[26px] font-bold leading-none" style={{ color }}>{value}<span className="text-[12px] text-gray-500 ml-1 font-normal">{unit}</span></div>{sub && <div className="text-[11px] text-gray-400 mt-1.5">{sub}</div>}</div>); }
function Card({ title, sub, children }) { return (<div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4"><div className="text-[15px] font-semibold mb-1 flex items-center gap-2" style={{ color: "#044B8C" }}><span className="w-[3px] h-4 rounded-sm inline-block" style={{ background: "#2980b9" }} />{title}</div>{sub && <div className="text-[12px] text-gray-400 mb-3 pl-[11px]">{sub}</div>}{children}</div>); }
