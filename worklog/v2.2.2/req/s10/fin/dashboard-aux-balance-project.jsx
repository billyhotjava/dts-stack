import { useState, useEffect, useRef, useMemo } from "react";

/* ═══════════════════════════════════════════
   ECharts CDN loader
   ═══════════════════════════════════════════ */
const EC_CDN = "https://cdnjs.cloudflare.com/ajax/libs/echarts/5.5.0/echarts.min.js";
function useEC() {
  const [ok, setOk] = useState(!!window.echarts);
  useEffect(() => {
    if (window.echarts) { setOk(true); return; }
    const s = document.createElement("script"); s.src = EC_CDN; s.onload = () => setOk(true); document.head.appendChild(s);
  }, []);
  return ok;
}
function EC({ option, h = 280, className = "" }) {
  const ref = useRef(null), chart = useRef(null), ok = useEC();
  useEffect(() => {
    if (!ok || !ref.current) return;
    if (!chart.current) chart.current = window.echarts.init(ref.current);
    chart.current.setOption(option, true);
    const fn = () => chart.current?.resize();
    window.addEventListener("resize", fn);
    return () => window.removeEventListener("resize", fn);
  }, [ok, option]);
  useEffect(() => () => { chart.current?.dispose(); chart.current = null; }, []);
  return <div ref={ref} style={{ width: "100%", height: h }} className={className} />;
}

/* ═══════════════════════════════════════════
   MOCK DATA
   ═══════════════════════════════════════════ */
const projects = ["新一代涡轮组装","智能电网升级","数据中心迁移","供应链数字化","质量管理体系认证","生产线自动化改造","ERP系统升级","环保合规改造","新材料研发","客户门户平台"];
const depts = ["制造部","电力部","IT部","采购部","质量部","安环部","研发部","营销部"];
const codes = [["5001.01","原材料-钢材"],["5001.02","原材料-铝合金"],["5001.03","电缆"],["5001.04","变压器"],["5001.05","服务器"],["5001.06","网络设备"],["5001.07","自动化设备"],["5101.01","外协加工费"],["5101.02","安装调试费"],["5101.03","数据迁移"],["5101.04","软件开发费"],["5201.01","设备折旧"],["5301.01","检测试验费"],["5401.01","设计咨询费"],["5501.01","机房租赁费"],["5601.01","培训费"],["5701.01","专利申请费"]];
const contracts = ["钢材采购合同-2025A","电缆采购框架协议","变压器采购合同","服务器采购合同","产线设备采购合同","精密加工服务合同","电网安装服务合同","SCM系统开发合同","ERP软件许可合同","第三方检测合同","电网设计咨询合同","IDC机房租赁协议","培训服务合同","知识产权服务合同","—"];
const seed = (i) => ((i * 9301 + 49297) % 233280) / 233280;
const DATA = Array.from({ length: 100 }, (_, i) => {
  const pi = Math.floor(seed(i) * projects.length), di = Math.floor(seed(i + 50) * depts.length);
  const ci = Math.floor(seed(i + 80) * codes.length), cti = Math.floor(seed(i + 30) * contracts.length);
  return { id: i + 1, code: codes[ci][0], name: codes[ci][1], projectId: `PRJ-${(pi + 1).toString().padStart(3, "0")}`, project: projects[pi], dept: depts[di], contract: contracts[cti], balance: Math.round(seed(i + 99) * 7500000 + 50000) };
});

/* ═══════════════════════════════════════════
   THEME COLORS
   ═══════════════════════════════════════════ */
const Brand = "#044B8C";
const BrandLight = "#2980b9";
const Green = "#1e8449";
const Orange = "#d4850a";
const Red = "#c0392b";
const Purple = "#8e44ad";

const fmt = n => { if (n >= 100000000) return (n / 100000000).toFixed(2) + " 亿"; if (n >= 10000) return (n / 10000).toFixed(2) + " 万"; return n.toLocaleString(); };

/* ═══════════════════════════════════════════
   COMPONENTS
   ═══════════════════════════════════════════ */
function Header({ title, sub, time }) {
  return (
    <div className="flex items-center justify-between px-7 py-3.5" style={{ background: `linear-gradient(135deg, ${Brand} 0%, #0a5599 50%, ${Brand} 100%)` }}>
      <div className="flex items-center gap-3.5 min-w-[180px]">
        <div className="w-9 h-9 rounded-lg bg-white/15 border border-white/25 flex items-center justify-center">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round"><rect x="3" y="3" width="18" height="18" rx="2" /><path d="M3 9h18M9 3v18" /></svg>
        </div>
        <div className="text-[11px] text-white/65 tracking-wider leading-tight">DECISION TWINS<br/>PROJECT CENTER</div>
      </div>
      <div className="text-center">
        <div className="text-[22px] font-bold text-white tracking-[4px]">{title}</div>
        <div className="text-[11px] text-white/55 tracking-[2px] mt-0.5">{sub}</div>
      </div>
      <div className="text-right min-w-[180px] text-white">
        <div className="text-[16px] font-semibold">{time.toLocaleTimeString("zh-CN", { hour12: false })}</div>
        <div className="text-[11px] text-white/60 mt-0.5">{time.toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric", weekday: "short" })}</div>
      </div>
    </div>
  );
}

function KPI({ label, value, unit, color = Brand, sub }) {
  return (
    <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4 text-center">
      <div className="text-[11px] text-gray-400 uppercase tracking-wider mb-2 font-medium">{label}</div>
      <div className="text-[28px] font-bold leading-none" style={{ color }}>{value}<span className="text-[13px] text-gray-500 ml-1 font-normal">{unit}</span></div>
      {sub && <div className="text-[11px] text-gray-400 mt-1.5">{sub}</div>}
    </div>
  );
}

function Tag({ children, color }) {
  const bg = color === Green ? "bg-green-50 text-green-700" : color === Red ? "bg-red-50 text-red-700" : color === Orange ? "bg-orange-50 text-orange-700" : color === Purple ? "bg-purple-50 text-purple-700" : "bg-blue-50 text-blue-700";
  return <span className={`text-[11px] font-semibold px-2.5 py-0.5 rounded ${bg}`}>{children}</span>;
}

/* ═══════════════════════════════════════════
   MAIN
   ═══════════════════════════════════════════ */
export default function App() {
  const [time, setTime] = useState(new Date());
  const [fProject, setFProject] = useState("");
  const [fDept, setFDept] = useState("");
  const [search, setSearch] = useState("");
  useEffect(() => { const t = setInterval(() => setTime(new Date()), 1000); return () => clearInterval(t); }, []);

  const filtered = useMemo(() => DATA.filter(d =>
    (!fProject || d.project === fProject) && (!fDept || d.dept === fDept) &&
    (!search || d.name.includes(search) || d.code.includes(search) || d.contract.includes(search))
  ), [fProject, fDept, search]);

  const totalBal = filtered.reduce((s, d) => s + d.balance, 0);
  const projCount = new Set(filtered.map(d => d.projectId)).size;
  const subjCount = new Set(filtered.map(d => d.code)).size;
  const contCount = new Set(filtered.filter(d => d.contract !== "—").map(d => d.contract)).size;

  const byProject = useMemo(() => {
    const m = {}; filtered.forEach(d => { m[d.project] = (m[d.project] || 0) + d.balance; });
    return Object.entries(m).map(([n, v]) => ({ name: n, value: v })).sort((a, b) => b.value - a.value);
  }, [filtered]);

  const byDept = useMemo(() => {
    const m = {}; filtered.forEach(d => { m[d.dept] = (m[d.dept] || 0) + d.balance; });
    return Object.entries(m).map(([n, v]) => ({ name: n, value: v })).sort((a, b) => b.value - a.value);
  }, [filtered]);

  const byCat = useMemo(() => {
    const m = {}; filtered.forEach(d => {
      const cat = d.code.startsWith("5001") ? "原材料/设备" : d.code.startsWith("5101") ? "外协/服务" : d.code.startsWith("5201") ? "折旧" : d.code.startsWith("5301") ? "检测试验" : d.code.startsWith("5401") ? "设计咨询" : d.code.startsWith("5501") ? "租赁" : d.code.startsWith("5601") ? "培训" : "其他";
      m[cat] = (m[cat] || 0) + d.balance;
    });
    return Object.entries(m).map(([n, v]) => ({ name: n, value: v })).sort((a, b) => b.value - a.value);
  }, [filtered]);

  const topContracts = useMemo(() => {
    const m = {}; filtered.forEach(d => { if (d.contract === "—") return; if (!m[d.contract]) m[d.contract] = { contract: d.contract, project: d.project, balance: 0 }; m[d.contract].balance += d.balance; });
    return Object.values(m).sort((a, b) => b.balance - a.balance).slice(0, 8);
  }, [filtered]);

  const projList = [...new Set(DATA.map(d => d.project))];
  const deptList = [...new Set(DATA.map(d => d.dept))];

  /* ECharts options */
  const barOpt = {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    grid: { left: 110, right: 40, top: 10, bottom: 10 },
    xAxis: { type: "value", axisLabel: { fontSize: 10, color: "#909eab", formatter: v => (v / 10000).toFixed(0) + "万" }, splitLine: { lineStyle: { type: "dashed", color: "#e8eff7" } } },
    yAxis: { type: "category", data: byProject.map(d => d.name).reverse(), axisLabel: { fontSize: 11, color: "#566573" }, axisTick: { show: false }, axisLine: { show: false } },
    series: [{ type: "bar", data: byProject.map(d => d.value).reverse(), barWidth: 16, itemStyle: { color: BrandLight, borderRadius: [0, 3, 3, 0] }, label: { show: true, position: "right", fontSize: 10, color: "#566573", formatter: p => (p.value / 10000).toFixed(0) + "万" } }],
  };

  const pieOpt = {
    tooltip: { trigger: "item", formatter: "{b}: {c} ({d}%)" },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: "#566573" } },
    color: [BrandLight, Green, Orange, Purple, "#16a085", Red, "#2c3e50"],
    series: [{ type: "pie", radius: ["45%", "72%"], center: ["50%", "42%"], label: { show: false }, emphasis: { label: { show: true, fontSize: 13, fontWeight: 600 } },
      data: byDept.map(d => ({ name: d.name, value: d.value })) }],
    graphic: [{ type: "text", left: "center", top: "36%", style: { text: (totalBal / 10000).toFixed(0), fontSize: 20, fontWeight: 700, fill: Brand, textAlign: "center" } },
      { type: "text", left: "center", top: "48%", style: { text: "万元", fontSize: 11, fill: "#909eab", textAlign: "center" } }],
  };

  const treemapOpt = {
    tooltip: { formatter: p => `${p.name}: ${(p.value / 10000).toFixed(2)}万 (${(p.value / byCat.reduce((s, d) => s + d.value, 0) * 100).toFixed(1)}%)` },
    color: [BrandLight, Green, Orange, Purple, "#16a085", Red, "#2c3e50"],
    series: [{ type: "treemap", width: "100%", height: "85%", roam: false, nodeClick: false, breadcrumb: { show: false },
      label: { show: true, fontSize: 12, fontWeight: 500, color: "#fff", formatter: p => `${p.name}\n${(p.value / 10000).toFixed(0)}万` },
      data: byCat.map(d => ({ name: d.name, value: d.value })) }],
  };

  const selCls = "h-8 px-3 rounded-md border border-[#d6e4f0] text-[13px] text-gray-700 bg-white outline-none cursor-pointer";

  return (
    <div className="min-h-screen" style={{ background: "#eaf2fb", fontFamily: "'Noto Sans SC', 'PingFang SC', -apple-system, sans-serif" }}>
      <Header title="辅助余额大屏" sub="AUXILIARY BALANCE OVERVIEW" time={time} />

      <div className="max-w-[1480px] mx-auto p-5 flex flex-col gap-4">
        {/* Filters */}
        <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm px-5 py-3 flex items-center gap-4 flex-wrap">
          <span className="text-[13px] font-semibold" style={{ color: Brand }}>筛选条件</span>
          <select value={fProject} onChange={e => setFProject(e.target.value)} className={selCls}>
            <option value="">全部项目</option>
            {projList.map(p => <option key={p}>{p}</option>)}
          </select>
          <select value={fDept} onChange={e => setFDept(e.target.value)} className={selCls}>
            <option value="">全部部门</option>
            {deptList.map(d => <option key={d}>{d}</option>)}
          </select>
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="搜索科目/合同..."
            className="h-8 px-3 rounded-md border border-[#d6e4f0] text-[13px] outline-none w-48" />
          {(fProject || fDept || search) && (
            <button onClick={() => { setFProject(""); setFDept(""); setSearch(""); }}
              className="h-8 px-4 rounded-md border border-[#d6e4f0] bg-blue-50 text-[12px] font-medium cursor-pointer" style={{ color: Brand }}>清除筛选</button>
          )}
          <span className="text-[12px] text-gray-400 ml-auto">共 {filtered.length} 条记录</span>
        </div>

        {/* KPIs */}
        <div className="grid grid-cols-4 gap-3">
          <KPI label="余额合计" value={(totalBal / 10000).toFixed(2)} unit="万" color={Brand} sub="全部筛选项汇总" />
          <KPI label="涉及项目" value={projCount} unit="个" color={BrandLight} />
          <KPI label="科目数量" value={subjCount} unit="个" color="#2471a3" />
          <KPI label="合同数量" value={contCount} unit="份" color={Purple} />
        </div>

        {/* Charts */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4">
            <div className="text-[15px] font-semibold mb-3 flex items-center gap-2" style={{ color: Brand }}>
              <span className="w-[3px] h-4 rounded-sm inline-block" style={{ background: BrandLight }} />按项目分布
            </div>
            <EC option={barOpt} h={280} />
          </div>
          <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4">
            <div className="text-[15px] font-semibold mb-3 flex items-center gap-2" style={{ color: Brand }}>
              <span className="w-[3px] h-4 rounded-sm inline-block" style={{ background: BrandLight }} />按部门分布
            </div>
            <EC option={pieOpt} h={280} />
          </div>
          <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4">
            <div className="text-[15px] font-semibold mb-3 flex items-center gap-2" style={{ color: Brand }}>
              <span className="w-[3px] h-4 rounded-sm inline-block" style={{ background: BrandLight }} />按费用类别分布
            </div>
            <EC option={treemapOpt} h={280} />
          </div>
        </div>

        {/* Table + Top Contracts */}
        <div className="grid grid-cols-[2fr_1fr] gap-4">
          <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4">
            <div className="text-[15px] font-semibold mb-3 flex items-center gap-2" style={{ color: Brand }}>
              <span className="w-[3px] h-4 rounded-sm inline-block" style={{ background: BrandLight }} />辅助余额明细
            </div>
            <div className="overflow-auto max-h-[400px]">
              <table className="w-full text-[13px] border-collapse">
                <thead><tr className="border-b-2 border-[#dce6f0]">
                  {["科目编号","科目名称","所属项目","部门","合同名称","余额"].map((h, i) => (
                    <th key={i} className={`py-2.5 px-3 text-[11px] font-semibold uppercase tracking-wider sticky top-0 bg-white z-10 ${i === 5 ? "text-right" : "text-left"}`} style={{ color: Brand }}>{h}</th>
                  ))}
                </tr></thead>
                <tbody>
                  {filtered.slice(0, 50).map((d, ri) => (
                    <tr key={d.id} className={`border-b border-[#dce6f0]/50 ${ri % 2 ? "bg-[#f5f9fd]" : ""}`}>
                      <td className="py-2 px-3 font-semibold text-[12px]" style={{ color: "#2e86c1" }}>{d.code}</td>
                      <td className="py-2 px-3 text-gray-800">{d.name}</td>
                      <td className="py-2 px-3 text-gray-800">{d.project}</td>
                      <td className="py-2 px-3 text-gray-500">{d.dept}</td>
                      <td className="py-2 px-3 text-gray-500 max-w-[180px] truncate">{d.contract}</td>
                      <td className={`py-2 px-3 text-right font-semibold ${d.balance > 3000000 ? "text-red-600" : d.balance > 1000000 ? "text-orange-600" : "text-gray-800"}`}>{fmt(d.balance)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot><tr className="bg-blue-50/60 border-t-2 border-[#dce6f0]">
                  <td colSpan={5} className="py-2.5 px-3 font-semibold" style={{ color: Brand }}>合计</td>
                  <td className="py-2.5 px-3 text-right font-bold text-[14px]" style={{ color: Brand }}>{fmt(totalBal)}</td>
                </tr></tfoot>
              </table>
            </div>
          </div>

          <div className="bg-white rounded-lg border border-[#d6e4f0] shadow-sm p-4">
            <div className="text-[15px] font-semibold mb-3 flex items-center gap-2" style={{ color: Brand }}>
              <span className="w-[3px] h-4 rounded-sm inline-block" style={{ background: BrandLight }} />合同金额 TOP 8
            </div>
            <div className="flex flex-col gap-3">
              {topContracts.map((c, i) => (
                <div key={i} className="flex items-center gap-3">
                  <div className={`w-6 h-6 rounded-md flex items-center justify-center text-[12px] font-bold ${i < 3 ? "text-white" : "text-gray-500 bg-gray-100"}`}
                    style={i < 3 ? { background: Brand } : {}}>{i + 1}</div>
                  <div className="flex-1 min-w-0">
                    <div className="text-[12px] text-gray-800 font-medium truncate">{c.contract}</div>
                    <div className="text-[11px] text-gray-400">{c.project}</div>
                  </div>
                  <div className="text-[13px] font-semibold whitespace-nowrap" style={{ color: Brand }}>{(c.balance / 10000).toFixed(0)}万</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="text-center py-3 border-t border-[#d6e4f0] text-[11px] text-gray-400 bg-[#f0f5fb]">
        DECISION TWINS SYSTEM · V4.0 · 系统运行正常 · 数据实时刷新
      </div>
    </div>
  );
}
