import { useState, useEffect, useRef, useMemo } from "react";

const EC_CDN = "https://cdnjs.cloudflare.com/ajax/libs/echarts/5.5.0/echarts.min.js";
function useEC() { const [ok, setOk] = useState(!!window.echarts); useEffect(() => { if (window.echarts) { setOk(true); return; } const s = document.createElement("script"); s.src = EC_CDN; s.onload = () => setOk(true); document.head.appendChild(s); }, []); return ok; }
function EC({ option, h = 280 }) { const ref = useRef(null), c = useRef(null), ok = useEC(); useEffect(() => { if (!ok || !ref.current) return; if (!c.current) c.current = window.echarts.init(ref.current); c.current.setOption(option, true); const fn = () => c.current?.resize(); window.addEventListener("resize", fn); return () => window.removeEventListener("resize", fn); }, [ok, option]); useEffect(() => () => { c.current?.dispose(); c.current = null; }, []); return <div ref={ref} style={{ width: "100%", height: h }} />; }

const Brand = "#044B8C", BL = "#2980b9", Grn = "#1e8449", Org = "#d4850a", Red = "#c0392b", Pur = "#8e44ad";
const employees = [["张工", "制造部"], ["李工", "电力部"], ["王强", "IT部"], ["赵敏", "采购部"], ["刘洋", "制造部"], ["孙磊", "IT部"], ["吴杰", "研发部"], ["周芳", "安环部"], ["郑华", "营销部"], ["陈静", "质量部"], ["马超", "采购部"], ["钱波", "研发部"], ["黄磊", "IT部"], ["林涛", "制造部"], ["何敏", "电力部"]];
const pSubjects = [["1122.01", "备用金", true], ["1122.02", "差旅费借款", true], ["1122.03", "采购预付款", true], ["1122.04", "培训费借款", true], ["1122.05", "设备采购借款", true], ["1122.06", "实验材料借款", true], ["1122.07", "业务招待借款", true], ["1122.08", "项目预支款", true], ["2211.01", "工资应付", false], ["2211.02", "奖金应付", false], ["2211.03", "社保代扣", false]];
const seed = i => ((i * 9301 + 49297) % 233280) / 233280;
const DATA = Array.from({ length: 100 }, (_, i) => {
  const [en, ed] = employees[Math.floor(seed(i) * employees.length)];
  const [sc, sn, isD] = pSubjects[Math.floor(seed(i + 40) * pSubjects.length)];
  const bal = isD ? Math.round(seed(i + 77) * 199000 + 1000) : -Math.round(seed(i + 88) * 27000 + 3000);
  return { id: i + 1, code: sc, name: sn, dept: ed, employee: en, balance: bal };
});
const empList = [...new Set(DATA.map(d => d.employee))], deptList = [...new Set(DATA.map(d => d.dept))];
const fmt = n => { const a = Math.abs(n); const s = a >= 10000 ? (a / 10000).toFixed(2) + " 万" : a.toLocaleString(); return n < 0 ? "-" + s : s; };

export default function App() {
  const [time, setTime] = useState(new Date()), [fEmp, setFEmp] = useState(""), [fDept, setFDept] = useState(""), [search, setSearch] = useState("");
  useEffect(() => { const t = setInterval(() => setTime(new Date()), 1000); return () => clearInterval(t); }, []);

  const filtered = useMemo(() => DATA.filter(d => (!fEmp || d.employee === fEmp) && (!fDept || d.dept === fDept) && (!search || d.name.includes(search) || d.code.includes(search) || d.employee.includes(search))), [fEmp, fDept, search]);
  const net = filtered.reduce((s, d) => s + d.balance, 0);
  const debit = filtered.filter(d => d.balance > 0).reduce((s, d) => s + d.balance, 0);
  const credit = filtered.filter(d => d.balance < 0).reduce((s, d) => s + d.balance, 0);
  const empCount = new Set(filtered.map(d => d.employee)).size, subjCount = new Set(filtered.map(d => d.code)).size;

  const byEmp = useMemo(() => { const m = {}; filtered.forEach(d => { m[d.employee] = (m[d.employee] || 0) + d.balance; }); return Object.entries(m).map(([n, v]) => ({ name: n, value: v })).sort((a, b) => b.value - a.value); }, [filtered]);
  const byDept = useMemo(() => { const m = {}; filtered.forEach(d => { m[d.dept] = (m[d.dept] || 0) + Math.abs(d.balance); }); return Object.entries(m).map(([n, v]) => ({ name: n, value: v })).sort((a, b) => b.value - a.value); }, [filtered]);
  const byCat = useMemo(() => { const m = {}; filtered.forEach(d => { const c = d.code.startsWith("1122") ? "其他应收-借款" : d.code.startsWith("2211") ? "应付职工薪酬" : "其他"; m[c] = (m[c] || 0) + Math.abs(d.balance); }); return Object.entries(m).map(([n, v]) => ({ name: n, value: v })).sort((a, b) => b.value - a.value); }, [filtered]);
  const topBorrowers = byEmp.filter(e => e.value > 0).slice(0, 8);

  const barOpt = {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    grid: { left: 60, right: 50, top: 10, bottom: 10 },
    xAxis: { type: "value", axisLabel: { fontSize: 10, color: "#909eab", formatter: v => (v / 10000).toFixed(0) + "万" }, splitLine: { lineStyle: { type: "dashed", color: "#e8eff7" } } },
    yAxis: { type: "category", data: byEmp.map(d => d.name).reverse(), axisLabel: { fontSize: 11, color: "#566573" }, axisTick: { show: false }, axisLine: { show: false } },
    series: [{ type: "bar", data: byEmp.map(d => ({ value: d.value, itemStyle: { color: d.value >= 0 ? BL : Grn, borderRadius: d.value >= 0 ? [0, 3, 3, 0] : [3, 0, 0, 3] } })).reverse(), barWidth: 14, label: { show: true, position: "right", fontSize: 10, color: "#566573", formatter: p => (p.value / 10000).toFixed(1) + "万" } }],
  };
  const pieOpt = {
    tooltip: { trigger: "item", formatter: "{b}: {c} ({d}%)" },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: "#566573" } },
    color: [BL, Grn, Org, Pur, "#16a085", Red],
    series: [{ type: "pie", radius: ["45%", "72%"], center: ["50%", "42%"], label: { show: false }, emphasis: { label: { show: true, fontSize: 13, fontWeight: 600 } }, data: byDept.map(d => ({ name: d.name, value: d.value })) }],
    graphic: [{ type: "text", left: "center", top: "36%", style: { text: byDept.length.toString(), fontSize: 22, fontWeight: 700, fill: Brand, textAlign: "center" } }, { type: "text", left: "center", top: "50%", style: { text: "部门", fontSize: 11, fill: "#909eab", textAlign: "center" } }],
  };
  const catTotal = byCat.reduce((s, d) => s + d.value, 0);
  const catBarOpt = {
    tooltip: { trigger: "axis", formatter: p => `${p[0].name}: ${(p[0].value / 10000).toFixed(2)}万 (${(p[0].value / catTotal * 100).toFixed(1)}%)` },
    grid: { left: 100, right: 50, top: 10, bottom: 10 },
    xAxis: { type: "value", max: catTotal, axisLabel: { show: false }, splitLine: { show: false }, axisTick: { show: false }, axisLine: { show: false } },
    yAxis: { type: "category", data: byCat.map(d => d.name).reverse(), axisLabel: { fontSize: 12, color: "#566573", fontWeight: 500 }, axisTick: { show: false }, axisLine: { show: false } },
    series: [{ type: "bar", data: byCat.map((d, i) => ({ value: d.value, itemStyle: { color: [BL, Grn, Org][i % 3], borderRadius: [0, 4, 4, 0] } })).reverse(), barWidth: 24, label: { show: true, position: "right", fontSize: 11, color: "#566573", formatter: p => (p.value / catTotal * 100).toFixed(1) + "%" } }],
  };

  const sel = "h-8 px-3 rounded-md border border-[#d6e4f0] text-[13px] text-gray-700 bg-white outline-none cursor-pointer";

  return (
    <div className="min-h-screen" style={{ background: "#eaf2fb", fontFamily: "'Noto Sans SC', -apple-system, sans-serif" }}>
      <div className="flex items-center justify-between px-7 py-3.5" style={{ background: `linear-gradient(135deg, ${Brand} 0%, #0a5599 50%, ${Brand} 100%)` }}>
        <div className="flex items-center gap-3 min-w-[180px]">
          <div className="w-9 h-9 rounded-lg bg-white/15 border border-white/25 flex items-center justify-center">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round"><circle cx="12" cy="8" r="4" /><path d="M4 20c0-4 4-7 8-7s8 3 8 7" /></svg>
          </div>
          <div className="text-[11px] text-white/65 tracking-wider leading-tight">DECISION TWINS<br />PERSONAL LEDGER</div>
        </div>
        <div className="text-center"><div className="text-[22px] font-bold text-white tracking-[4px]">个人辅助余额大屏</div><div className="text-[11px] text-white/55 tracking-[2px] mt-0.5">PERSONAL AUXILIARY BALANCE OVERVIEW</div></div>
        <div className="text-right min-w-[180px] text-white"><div className="text-[16px] font-semibold">{time.toLocaleTimeString("zh-CN", { hour12: false })}</div><div className="text-[11px] text-white/60 mt-0.5">{time.toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric", weekday: "short" })}</div></div>
      </div>
      <div className="max-w-[1480px] mx-auto p-5 flex flex-col gap-4">
        <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm px-5 py-3 flex items-center gap-4 flex-wrap">
          <span className="text-[13px] font-semibold" style={{ color: Brand }}>筛选条件</span>
          <select value={fEmp} onChange={e => setFEmp(e.target.value)} className={sel}><option value="">全部职工</option>{empList.map(e => <option key={e}>{e}</option>)}</select>
          <select value={fDept} onChange={e => setFDept(e.target.value)} className={sel}><option value="">全部部门</option>{deptList.map(d => <option key={d}>{d}</option>)}</select>
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="搜索科目/职工..." className="h-8 px-3 rounded-md border border-[#d6e4f0] text-[13px] outline-none w-48" />
          {(fEmp || fDept || search) && <button onClick={() => { setFEmp(""); setFDept(""); setSearch(""); }} className="h-8 px-4 rounded-md border border-[#d6e4f0] bg-blue-50 text-[12px] font-medium cursor-pointer" style={{ color: Brand }}>清除筛选</button>}
          <span className="text-[12px] text-gray-400 ml-auto">共 {filtered.length} 条</span>
        </div>
        <div className="grid grid-cols-5 gap-3">
          <KPI label="净余额" value={(net / 10000).toFixed(2)} unit="万" color={net >= 0 ? Brand : Red} sub="借方-贷方" />
          <KPI label="借方合计" value={(debit / 10000).toFixed(2)} unit="万" color={Org} sub="应收/借款" />
          <KPI label="贷方合计" value={(Math.abs(credit) / 10000).toFixed(2)} unit="万" color={Grn} sub="应付/代扣" />
          <KPI label="涉及职工" value={empCount} unit="人" color={BL} />
          <KPI label="科目数量" value={subjCount} unit="个" color={Brand} />
        </div>
        <div className="grid grid-cols-3 gap-4">
          <Card title="按职工分布" sub="个人净余额排名"><EC option={barOpt} h={300} /></Card>
          <Card title="按部门分布" sub="各部门绝对金额占比"><EC option={pieOpt} h={300} /></Card>
          <Card title="按科目类别分布"><EC option={catBarOpt} h={300} /></Card>
        </div>
        <div className="grid grid-cols-[2fr_1fr] gap-4">
          <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4">
            <div className="text-[15px] font-semibold mb-3 flex items-center gap-2" style={{ color: Brand }}><span className="w-[3px] h-4 rounded-sm inline-block" style={{ background: BL }} />个人辅助余额明细</div>
            <div className="overflow-auto max-h-[400px]">
              <table className="w-full text-[13px] border-collapse">
                <thead><tr className="border-b-2 border-[#dce6f0]">
                  {["科目编号", "科目名称", "部门名称", "职工名称", "余额"].map((h, i) => (<th key={i} className={`py-2.5 px-3 text-[11px] font-semibold uppercase tracking-wider sticky top-0 bg-white z-10 ${i === 4 ? "text-right" : "text-left"}`} style={{ color: Brand }}>{h}</th>))}
                </tr></thead>
                <tbody>{filtered.slice(0, 50).map((d, ri) => (
                  <tr key={d.id} className={`border-b border-[#dce6f0]/50 ${ri % 2 ? "bg-[#f5f9fd]" : ""}`}>
                    <td className="py-2 px-3 font-semibold text-[12px]" style={{ color: "#2e86c1" }}>{d.code}</td>
                    <td className="py-2 px-3 text-gray-800">{d.name}</td>
                    <td className="py-2 px-3 text-gray-500">{d.dept}</td>
                    <td className="py-2 px-3 text-gray-800 font-medium">{d.employee}</td>
                    <td className={`py-2 px-3 text-right font-semibold ${d.balance < 0 ? "text-green-700" : d.balance > 50000 ? "text-red-600" : d.balance > 10000 ? "text-orange-600" : "text-gray-800"}`}>{fmt(d.balance)}</td>
                  </tr>
                ))}</tbody>
                <tfoot><tr className="bg-blue-50/60 border-t-2 border-[#dce6f0]">
                  <td colSpan={4} className="py-2.5 px-3 font-semibold" style={{ color: Brand }}>合计</td>
                  <td className="py-2.5 px-3 text-right font-bold text-[14px]" style={{ color: Brand }}>{fmt(net)}</td>
                </tr></tfoot>
              </table>
            </div>
          </div>
          <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4">
            <div className="text-[15px] font-semibold mb-3 flex items-center gap-2" style={{ color: Brand }}><span className="w-[3px] h-4 rounded-sm inline-block" style={{ background: BL }} />借款余额 TOP 8</div>
            <div className="flex flex-col gap-3">
              {topBorrowers.map((e, i) => {
                const ed = DATA.find(d => d.employee === e.name)?.dept || ""; return (
                  <div key={i} className="flex items-center gap-3">
                    <div className={`w-7 h-7 rounded-lg flex items-center justify-center text-[12px] font-bold ${i < 3 ? "text-white" : "text-gray-500 bg-gray-100"}`} style={i < 3 ? { background: Brand } : {}}>{i + 1}</div>
                    <div className="flex-1 min-w-0"><div className="text-[13px] text-gray-800 font-medium">{e.name}</div><div className="text-[11px] text-gray-400">{ed}</div></div>
                    <div className={`text-[13px] font-semibold whitespace-nowrap ${e.value > 100000 ? "text-red-600" : e.value > 30000 ? "text-orange-600" : ""}`} style={e.value <= 30000 ? { color: Brand } : {}}>{(e.value / 10000).toFixed(2)}万</div>
                  </div>
                );
              })}
            </div>
            <div className="mt-4 p-3 bg-blue-50/60 rounded-md text-[11px] text-gray-500 leading-relaxed">
              <span className="font-semibold" style={{ color: Brand }}>说明：</span>正数为借方（应收/借款），负数为贷方（应付/代扣）。<span className="text-red-600 font-semibold">红色</span>标识超5万，<span className="text-orange-600 font-semibold">橙色</span>标识超1万。
            </div>
          </div>
        </div>
      </div>
      <div className="text-center py-3 border-t border-[#d6e4f0] text-[11px] text-gray-400 bg-[#f0f5fb]">DECISION TWINS SYSTEM · V4.0 · 系统运行正常 · 数据实时刷新</div>
    </div>
  );
}

function KPI({ label, value, unit, color = Brand, sub }) { return (<div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4 text-center"><div className="text-[11px] text-gray-400 uppercase tracking-wider mb-2 font-medium">{label}</div><div className="text-[28px] font-bold leading-none" style={{ color }}>{value}<span className="text-[13px] text-gray-500 ml-1 font-normal">{unit}</span></div>{sub && <div className="text-[11px] text-gray-400 mt-1.5">{sub}</div>}</div>); }
function Card({ title, sub, children }) { return (<div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4"><div className="text-[15px] font-semibold mb-1 flex items-center gap-2" style={{ color: Brand }}><span className="w-[3px] h-4 rounded-sm inline-block" style={{ background: BL }} />{title}</div>{sub && <div className="text-[12px] text-gray-400 mb-3 pl-[11px]">{sub}</div>}{children}</div>); }
