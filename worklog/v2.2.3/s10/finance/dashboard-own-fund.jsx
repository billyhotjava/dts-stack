import { useState, useEffect, useRef, useMemo } from "react";

const EC_CDN = "https://cdnjs.cloudflare.com/ajax/libs/echarts/5.5.0/echarts.min.js";
function useEC() { const [ok, setOk] = useState(!!window.echarts); useEffect(() => { if (window.echarts) { setOk(true); return; } const s = document.createElement("script"); s.src = EC_CDN; s.onload = () => setOk(true); document.head.appendChild(s); }, []); return ok; }
function EC({ option, h = 260 }) { const ref = useRef(null), c = useRef(null), ok = useEC(); useEffect(() => { if (!ok || !ref.current) return; if (!c.current) c.current = window.echarts.init(ref.current); c.current.setOption(option, true); const fn = () => c.current?.resize(); window.addEventListener("resize", fn); return () => window.removeEventListener("resize", fn); }, [ok, option]); useEffect(() => () => { c.current?.dispose(); c.current = null; }, []); return <div ref={ref} style={{ width: "100%", height: h }} />; }

const B = "#044B8C", BL = "#2980b9", Grn = "#1e8449", Org = "#d4850a", Red = "#c0392b", Pur = "#8e44ad";
const FK = ["career", "deprec", "welfare", "safety"];
const FL = ["事业基金", "折旧基金", "职工福利基金", "安全生产基金"];
const FC = [BL, Grn, Org, Pur];
const YEARS = ["2026", "2027"];
const fmt = n => n.toFixed(0).replace(/\B(?=(\d{3})+(?!\d))/g, ",");

// New schema: only period field (年度), fund values, notes, and total (合计)
// No type, year, or id fields — those are derived from parsing period text
const RAW = [
  { period: "2026年初",     career: 1200, cn: "上年结转",         deprec: 850, dn: "上年结转",         welfare: 320, safety: 180, total: 2550 },
  { period: "2026年预计增加", career: 600,  cn: "预算拨付+投资收益", deprec: 480, dn: "本年计提",         welfare: 150, safety: 95,  total: 1325 },
  { period: "2026年预计使用", career: 450,  cn: "科研项目支出",     deprec: 520, dn: "设备更新",         welfare: 200, safety: 120, total: 1290 },
  { period: "2026年余额",   career: 1350, cn: "=1200+600-450",   deprec: 810, dn: "=850+480-520",     welfare: 270, safety: 155, total: 2585 },
  { period: "2027年初",     career: 1350, cn: "承接2026余额",     deprec: 810, dn: "承接2026余额",     welfare: 270, safety: 155, total: 2585 },
  { period: "2027年预计增加", career: 720,  cn: "预算拨付+投资收益", deprec: 510, dn: "本年计提",         welfare: 180, safety: 110, total: 1520 },
  { period: "2027年预计使用", career: 580,  cn: "科研+基建支出",   deprec: 490, dn: "设备更新+维修",     welfare: 160, safety: 130, total: 1360 },
  { period: "2027年余额",   career: 1490, cn: "=1350+720-580",   deprec: 830, dn: "=810+510-490",     welfare: 290, safety: 135, total: 2745 },
];

// Derive year and type by parsing the period text
function parsePeriod(period) {
  const year = period.substring(0, 4);
  let type = "opening";
  if (period.includes("预计增加")) type = "increase";
  else if (period.includes("预计使用")) type = "usage";
  else if (period.includes("余额")) type = "balance";
  return { year, type };
}

const DATA = RAW.map(d => ({ ...d, ...parsePeriod(d.period) }));

export default function App() {
  const [time, setTime] = useState(new Date()), [year, setYear] = useState("2026");
  const ok = useEC();
  useEffect(() => { const t = setInterval(() => setTime(new Date()), 1000); return () => clearInterval(t); }, []);

  const yd = DATA.filter(d => d.year === year);
  const op = yd.find(d => d.type === "opening"), inc = yd.find(d => d.type === "increase");
  const us = yd.find(d => d.type === "usage"), bal = yd.find(d => d.type === "balance");
  const b26 = DATA.find(d => d.period === "2026年余额"), b27 = DATA.find(d => d.period === "2027年余额");

  // total comes directly from the 合计 column
  const tO = op?.total || 0, tI = inc?.total || 0, tU = us?.total || 0, tB = bal?.total || 0;
  const uRate = tO + tI > 0 ? tU / (tO + tI) * 100 : 0;
  const gRate = tO > 0 ? (tB - tO) / tO * 100 : 0;

  const donutOpt = {
    tooltip: { trigger: "item", formatter: "{b}: {c}万 ({d}%)" },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: "#566573" } },
    color: FC,
    series: [{ type: "pie", radius: ["45%", "72%"], center: ["50%", "42%"], label: { show: false }, emphasis: { label: { show: true, fontSize: 13, fontWeight: 600 } },
      data: FL.map((l, i) => ({ name: l, value: bal?.[FK[i]] || 0 })) }],
    graphic: [{ type: "text", left: "center", top: "36%", style: { text: fmt(tB), fontSize: 20, fontWeight: 700, fill: B, textAlign: "center" } },
      { type: "text", left: "center", top: "48%", style: { text: "万元", fontSize: 11, fill: "#909eab", textAlign: "center" } }],
  };

  const waterfallOpt = {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: ps => { const p = ps.find(x => x.value > 0 && x.seriesIndex > 0) || ps[0]; return `${p.name}: ${fmt(p.value)}万`; } },
    grid: { left: 55, right: 20, top: 30, bottom: 30 },
    xAxis: { type: "category", data: ["年初", "预计增加", "预计使用", "年末余额"], axisTick: { show: false }, axisLabel: { fontSize: 11, color: "#566573" } },
    yAxis: { type: "value", axisLabel: { fontSize: 10, color: "#909eab" }, splitLine: { lineStyle: { type: "dashed", color: "#e8eff7" } } },
    series: [
      { type: "bar", stack: "w", silent: true, itemStyle: { color: "transparent", borderColor: "transparent" }, data: [0, tO, tO + tI - tU, 0] },
      { type: "bar", stack: "w", barWidth: 40, data: [
        { value: tO, itemStyle: { color: BL } }, { value: tI, itemStyle: { color: Grn } },
        { value: tU, itemStyle: { color: Red } }, { value: tB, itemStyle: { color: B } },
      ], label: { show: true, position: "top", fontSize: 11, fontWeight: 600, color: "#1c2833", formatter: p => { const v = p.value; if (p.dataIndex === 1) return "+" + fmt(v); if (p.dataIndex === 2) return "-" + fmt(v); return fmt(v); } } },
    ],
  };

  const compareOpt = {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: "#566573" } },
    grid: { left: 50, right: 20, top: 10, bottom: 40 },
    xAxis: { type: "category", data: FL, axisTick: { show: false }, axisLabel: { fontSize: 11, color: "#566573" } },
    yAxis: { type: "value", axisLabel: { fontSize: 10, color: "#909eab" }, splitLine: { lineStyle: { type: "dashed", color: "#e8eff7" } } },
    series: [
      { name: "2026年", type: "bar", barGap: "20%", barWidth: 24, data: FK.map((k, i) => ({ value: b26?.[k] || 0, itemStyle: { color: FC[i], opacity: 0.45 } })) },
      { name: "2027年", type: "bar", barWidth: 24, data: FK.map((k, i) => ({ value: b27?.[k] || 0, itemStyle: { color: FC[i] } })) },
    ],
  };

  const miniWF = (k, i) => ({
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    grid: { left: 40, right: 10, top: 20, bottom: 24 },
    xAxis: { type: "category", data: ["年初", "增加", "使用", "余额"], axisTick: { show: false }, axisLabel: { fontSize: 10, color: "#909eab" } },
    yAxis: { type: "value", axisLabel: { fontSize: 9, color: "#909eab" }, splitLine: { lineStyle: { type: "dashed", color: "#e8eff7" } } },
    series: [
      { type: "bar", stack: "w", silent: true, itemStyle: { color: "transparent" }, data: [0, op?.[k] || 0, (op?.[k] || 0) + (inc?.[k] || 0) - (us?.[k] || 0), 0] },
      { type: "bar", stack: "w", barWidth: 24, data: [
        { value: op?.[k] || 0, itemStyle: { color: FC[i] } }, { value: inc?.[k] || 0, itemStyle: { color: Grn } },
        { value: us?.[k] || 0, itemStyle: { color: Red } }, { value: bal?.[k] || 0, itemStyle: { color: FC[i] } },
      ], label: { show: true, position: "top", fontSize: 10, fontWeight: 600, color: "#566573", formatter: p => fmt(p.value) } },
    ],
  });

  const gaugeOpt = (val, title, color) => ({ series: [{ type: "gauge", radius: "88%", startAngle: 210, endAngle: -30, min: 0, max: 100, axisLine: { lineStyle: { width: 14, color: [[Math.min(val, 100) / 100, color], [1, "#e8eff7"]] } }, axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false }, pointer: { show: false }, title: { show: true, offsetCenter: [0, "65%"], fontSize: 11, color: "#566573" }, detail: { valueAnimation: true, fontSize: 22, fontWeight: 700, color, offsetCenter: [0, "25%"], formatter: "{value}%" }, data: [{ value: val.toFixed(1), name: title }] }] });

  return (
    <div className="min-h-screen" style={{ background: "#eaf2fb", fontFamily: "'Noto Sans SC', -apple-system, sans-serif" }}>
      <div className="flex items-center justify-between px-7 py-3.5" style={{ background: `linear-gradient(135deg, ${B} 0%, #0a5599 50%, ${B} 100%)` }}>
        <div className="flex items-center gap-3 min-w-[180px]">
          <div className="w-9 h-9 rounded-lg bg-white/15 border border-white/25 flex items-center justify-center">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round"><rect x="2" y="6" width="20" height="14" rx="2" /><path d="M2 10h20M12 6v-2" /></svg>
          </div>
          <div className="text-[11px] text-white/65 tracking-wider leading-tight">DECISION TWINS<br/>FUND MGMT</div>
        </div>
        <div className="text-center"><div className="text-[22px] font-bold text-white tracking-[4px]">自有资金大屏</div><div className="text-[11px] text-white/55 tracking-[2px] mt-0.5">OWN FUND OVERVIEW</div></div>
        <div className="text-right min-w-[180px] text-white"><div className="text-[16px] font-semibold">{time.toLocaleTimeString("zh-CN",{hour12:false})}</div><div className="text-[11px] text-white/60 mt-0.5">{time.toLocaleDateString("zh-CN",{year:"numeric",month:"long",day:"numeric",weekday:"short"})}</div></div>
      </div>

      <div className="max-w-[1480px] mx-auto p-5 flex flex-col gap-4">
        <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm px-5 py-3 flex items-center gap-4">
          <span className="text-[13px] font-semibold" style={{color:B}}>年度选择</span>
          <div className="flex bg-[#e0ecf5] rounded-md p-[3px]">
            {YEARS.map(y => (
              <button key={y} onClick={() => setYear(y)} className={`px-6 py-1.5 rounded text-[13px] transition-all ${year===y?"bg-white font-semibold shadow-sm":"text-gray-400"}`} style={year===y?{color:B}:{}}>{y}年</button>
            ))}
          </div>
          <span className="text-[12px] text-gray-400 ml-auto">数据周期：{year}年初 → {year}年末余额</span>
        </div>

        <div className="grid grid-cols-6 gap-3">
          <KPI label="年初合计" value={fmt(tO)} unit="万" color={B} />
          <KPI label="预计增加" value={fmt(tI)} unit="万" color={Grn} sub="拨付+计提+收益" />
          <KPI label="预计使用" value={fmt(tU)} unit="万" color={Red} sub="各类支出" />
          <KPI label="年末余额" value={fmt(tB)} unit="万" color={BL} />
          <KPI label="资金使用率" value={uRate.toFixed(1)} unit="%" color={uRate>80?Org:BL} sub="使用÷(年初+增加)" />
          <KPI label="余额增长率" value={(gRate>=0?"+":"")+gRate.toFixed(1)} unit="%" color={gRate>=0?Grn:Red} sub="(余额-年初)÷年初" />
        </div>

        <div className="grid grid-cols-3 gap-4">
          <Card title={`${year}年末余额构成`} sub="四类基金余额占比">{ok&&<EC option={donutOpt} h={270} />}</Card>
          <Card title={`${year}年资金流动`} sub="瀑布图：年初→增加→使用→余额">{ok&&<EC option={waterfallOpt} h={270} />}</Card>
          <Card title="年度余额对比" sub="2026 vs 2027 各基金余额">{ok&&<EC option={compareOpt} h={270} />}</Card>
        </div>

        <div className="grid grid-cols-4 gap-3">
          {FK.map((k, i) => (
            <Card key={k} title={FL[i]} titleColor={FC[i]}>{ok&&<EC option={miniWF(k, i)} h={160} />}</Card>
          ))}
        </div>

        <div className="grid grid-cols-2 gap-4">
          <Card title="资金使用率" sub="预计使用 ÷（年初+预计增加）× 100%">{ok&&<EC option={gaugeOpt(uRate,"使用率",uRate>80?Org:BL)} h={200} />}</Card>
          <Card title="余额增长率" sub="（年末余额−年初）÷ 年初 × 100%">{ok&&<EC option={gaugeOpt(Math.abs(gRate),gRate>=0?"正增长":"负增长",gRate>=0?Grn:Red)} h={200} />}</Card>
        </div>

        <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4">
          <div className="text-[15px] font-semibold mb-3 flex items-center gap-2" style={{color:B}}><span className="w-[3px] h-4 rounded-sm inline-block" style={{background:BL}} />自有资金明细表 <span className="text-[12px] text-gray-400 font-normal ml-2">单位：万元</span></div>
          <div className="overflow-auto">
            <table className="w-full text-[13px] border-collapse whitespace-nowrap">
              <thead>
                <tr>
                  <th rowSpan={2} className="py-2.5 px-3 text-left text-[11px] font-semibold tracking-wider sticky top-0 bg-white z-20 border-b-2 border-[#dce6f0] align-bottom" style={{color:B}}>年度</th>
                  {FL.map((l, i) => {
                    const cs = i < 2 ? 2 : 1;
                    return <th key={i} colSpan={cs} className="py-2 px-3 text-center text-[11px] font-semibold border-b border-[#dce6f0] sticky top-0 bg-white z-20" style={{color:FC[i]}}>{l}</th>;
                  })}
                  <th rowSpan={2} className="py-2.5 px-3 text-right text-[11px] font-bold tracking-wider sticky top-0 bg-white z-20 border-b-2 border-[#dce6f0] align-bottom" style={{color:B}}>合计</th>
                </tr>
                <tr>
                  <th className="py-1.5 px-3 text-right text-[10px] text-gray-500 border-b-2 border-[#dce6f0] sticky top-[34px] bg-white z-10">金额</th>
                  <th className="py-1.5 px-3 text-left text-[10px] text-gray-400 border-b-2 border-[#dce6f0] sticky top-[34px] bg-white z-10">备注</th>
                  <th className="py-1.5 px-3 text-right text-[10px] text-gray-500 border-b-2 border-[#dce6f0] sticky top-[34px] bg-white z-10">金额</th>
                  <th className="py-1.5 px-3 text-left text-[10px] text-gray-400 border-b-2 border-[#dce6f0] sticky top-[34px] bg-white z-10">备注</th>
                  <th className="py-1.5 px-3 text-right text-[10px] text-gray-500 border-b-2 border-[#dce6f0] sticky top-[34px] bg-white z-10">金额</th>
                  <th className="py-1.5 px-3 text-right text-[10px] text-gray-500 border-b-2 border-[#dce6f0] sticky top-[34px] bg-white z-10">金额</th>
                </tr>
              </thead>
              <tbody>
                {DATA.map((d, ri) => {
                  const isBal = d.type === "balance", isUse = d.type === "usage";
                  const tw = isBal ? "font-bold" : "", tc = isUse ? "text-red-600" : "text-gray-800";
                  const pre = isUse ? "-" : "";
                  return (
                    <tr key={ri} className={`border-b border-[#dce6f0]/50 ${isBal ? "bg-blue-50/60" : ri % 2 ? "bg-[#f5f9fd]" : ""}`}>
                      <td className={`py-2.5 px-3 font-semibold ${isBal ? "" : ""}`} style={{color:isBal?B:"#1c2833"}}>{d.period}</td>
                      <td className={`py-2 px-3 text-right ${tw} ${tc}`}>{pre}{fmt(d.career)}</td>
                      <td className="py-2 px-3 text-gray-400 text-[11px]">{d.cn}</td>
                      <td className={`py-2 px-3 text-right ${tw} ${tc}`}>{pre}{fmt(d.deprec)}</td>
                      <td className="py-2 px-3 text-gray-400 text-[11px]">{d.dn}</td>
                      <td className={`py-2 px-3 text-right ${tw} ${tc}`}>{pre}{fmt(d.welfare)}</td>
                      <td className={`py-2 px-3 text-right ${tw} ${tc}`}>{pre}{fmt(d.safety)}</td>
                      <td className={`py-2 px-3 text-right font-bold ${isUse?"text-red-600":""}`} style={isBal?{color:B}:{}}>{pre}{fmt(d.total)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        <div className="bg-blue-50/60 rounded-lg border border-[#d6e4f0] p-4">
          <div className="text-[15px] font-semibold mb-3 flex items-center gap-2" style={{color:B}}><span className="w-[3px] h-4 rounded-sm inline-block" style={{background:BL}} />指标计算说明</div>
          <div className="grid grid-cols-2 gap-6 text-[12px] text-gray-500 leading-relaxed">
            <div><div className="font-semibold mb-1" style={{color:B}}>表内计算</div>
              <div><b className="text-gray-700">年末余额</b> = 年初余额 + 预计增加 - 预计使用</div>
              <div><b className="text-gray-700">下年年初</b> = 上年年末余额（自动承接）</div>
              <div><b className="text-gray-700">合计列</b> = 直接取自数据表的合计字段（事业基金 + 折旧基金 + 职工福利基金 + 安全生产基金）</div>
            </div>
            <div><div className="font-semibold mb-1" style={{color:B}}>KPI 指标</div>
              <div><b className="text-gray-700">资金使用率</b> = 预计使用 ÷（年初 + 预计增加）× 100%</div>
              <div><b className="text-gray-700">余额增长率</b> =（年末余额 - 年初）÷ 年初 × 100%</div>
              <div className="text-[11px] text-gray-400 mt-1">正增长=资金规模扩大，负增长=资金净消耗</div>
            </div>
          </div>
        </div>
      </div>
      <div className="text-center py-3 border-t border-[#d6e4f0] text-[11px] text-gray-400 bg-[#f0f5fb]">DECISION TWINS SYSTEM · V4.0 · 系统运行正常 · 数据实时刷新</div>
    </div>
  );
}

function KPI({label,value,unit,color="#044B8C",sub}){return(<div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4 text-center"><div className="text-[11px] text-gray-400 uppercase tracking-wider mb-2 font-medium">{label}</div><div className="text-[28px] font-bold leading-none" style={{color}}>{value}<span className="text-[13px] text-gray-500 ml-1 font-normal">{unit}</span></div>{sub&&<div className="text-[11px] text-gray-400 mt-1.5">{sub}</div>}</div>);}
function Card({title,sub,children,titleColor}){return(<div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4"><div className="text-[15px] font-semibold mb-1 flex items-center gap-2" style={{color:titleColor||"#044B8C"}}><span className="w-[3px] h-4 rounded-sm inline-block" style={{background:titleColor||"#2980b9"}} />{title}</div>{sub&&<div className="text-[12px] text-gray-400 mb-3 pl-[11px]">{sub}</div>}{children}</div>);}
