import { useState, useEffect, useMemo } from "react";

// ═══════════════════════════════════════════════════════════════
// MOCK DATA — 辅助余额表（按项目维度）
// ═══════════════════════════════════════════════════════════════
const BALANCE_DATA = [
  { id: 1, projectId: "PRJ-001", project: "新一代涡轮组装", code: "5001.01", name: "原材料-钢材", dept: "制造部", contract: "钢材采购合同-2025A", balance: 1280000 },
  { id: 2, projectId: "PRJ-001", project: "新一代涡轮组装", code: "5001.02", name: "原材料-铝合金", dept: "制造部", contract: "铝合金采购合同-2025B", balance: 860000 },
  { id: 3, projectId: "PRJ-001", project: "新一代涡轮组装", code: "5101.01", name: "外协加工费", dept: "制造部", contract: "精密加工服务合同", balance: 2150000 },
  { id: 4, projectId: "PRJ-001", project: "新一代涡轮组装", code: "5201.01", name: "设备折旧", dept: "制造部", contract: "—", balance: 430000 },
  { id: 5, projectId: "PRJ-001", project: "新一代涡轮组装", code: "5301.01", name: "检测试验费", dept: "质量部", contract: "第三方检测合同", balance: 320000 },
  { id: 6, projectId: "PRJ-002", project: "智能电网升级", code: "5001.03", name: "原材料-电缆", dept: "电力部", contract: "电缆采购框架协议", balance: 3450000 },
  { id: 7, projectId: "PRJ-002", project: "智能电网升级", code: "5001.04", name: "原材料-变压器", dept: "电力部", contract: "变压器采购合同", balance: 5200000 },
  { id: 8, projectId: "PRJ-002", project: "智能电网升级", code: "5101.02", name: "安装调试费", dept: "电力部", contract: "电网安装服务合同", balance: 1870000 },
  { id: 9, projectId: "PRJ-002", project: "智能电网升级", code: "5401.01", name: "设计咨询费", dept: "电力部", contract: "电网设计咨询合同", balance: 680000 },
  { id: 10, projectId: "PRJ-003", project: "数据中心迁移", code: "5001.05", name: "服务器设备", dept: "IT部", contract: "服务器采购合同-DC", balance: 4100000 },
  { id: 11, projectId: "PRJ-003", project: "数据中心迁移", code: "5001.06", name: "网络设备", dept: "IT部", contract: "网络设备采购合同", balance: 1560000 },
  { id: 12, projectId: "PRJ-003", project: "数据中心迁移", code: "5101.03", name: "数据迁移服务", dept: "IT部", contract: "数据迁移外包合同", balance: 920000 },
  { id: 13, projectId: "PRJ-003", project: "数据中心迁移", code: "5501.01", name: "机房租赁费", dept: "IT部", contract: "IDC机房租赁协议", balance: 2400000 },
  { id: 14, projectId: "PRJ-004", project: "供应链数字化", code: "5101.04", name: "软件开发费", dept: "采购部", contract: "SCM系统开发合同", balance: 1850000 },
  { id: 15, projectId: "PRJ-004", project: "供应链数字化", code: "5101.05", name: "实施部署费", dept: "采购部", contract: "SCM实施服务合同", balance: 640000 },
  { id: 16, projectId: "PRJ-004", project: "供应链数字化", code: "5601.01", name: "培训费", dept: "采购部", contract: "SCM培训服务合同", balance: 180000 },
  { id: 17, projectId: "PRJ-006", project: "生产线自动化改造", code: "5001.07", name: "自动化设备", dept: "制造部", contract: "产线设备采购合同", balance: 6800000 },
  { id: 18, projectId: "PRJ-006", project: "生产线自动化改造", code: "5001.08", name: "PLC控制器", dept: "制造部", contract: "PLC采购合同", balance: 1240000 },
  { id: 19, projectId: "PRJ-006", project: "生产线自动化改造", code: "5101.06", name: "产线集成费", dept: "制造部", contract: "自动化集成服务合同", balance: 2350000 },
  { id: 20, projectId: "PRJ-007", project: "ERP系统升级", code: "5101.07", name: "ERP许可证", dept: "IT部", contract: "ERP软件许可合同", balance: 3200000 },
  { id: 21, projectId: "PRJ-007", project: "ERP系统升级", code: "5101.08", name: "定制开发费", dept: "IT部", contract: "ERP定制开发合同", balance: 1450000 },
  { id: 22, projectId: "PRJ-007", project: "ERP系统升级", code: "5601.02", name: "用户培训费", dept: "IT部", contract: "ERP培训服务合同", balance: 260000 },
  { id: 23, projectId: "PRJ-009", project: "新材料研发", code: "5001.09", name: "实验材料", dept: "研发部", contract: "实验材料采购协议", balance: 890000 },
  { id: 24, projectId: "PRJ-009", project: "新材料研发", code: "5301.02", name: "实验室使用费", dept: "研发部", contract: "实验室租赁合同", balance: 560000 },
  { id: 25, projectId: "PRJ-009", project: "新材料研发", code: "5701.01", name: "专利申请费", dept: "研发部", contract: "知识产权服务合同", balance: 120000 },
];

const PROJECTS_LIST = [...new Set(BALANCE_DATA.map(d => d.project))];
const DEPTS_LIST = [...new Set(BALANCE_DATA.map(d => d.dept))];

// ═══════════════════════════════════════════════════════════════
// THEME (same as project command center)
// ═══════════════════════════════════════════════════════════════
const T = {
  bg: "#eaf2fb",
  headerBg: "linear-gradient(135deg, #044B8C 0%, #0a5599 50%, #044B8C 100%)",
  card: "#ffffff",
  cardBorder: "#d6e4f0",
  cardShadow: "0 1px 4px rgba(26,82,118,0.08)",
  primary: "#044B8C",
  primaryLight: "#2980b9",
  accent: "#2471a3",
  link: "#2e86c1",
  good: "#1e8449",
  goodBg: "#e8f8f0",
  warning: "#d4850a",
  warningBg: "#fef5e7",
  critical: "#c0392b",
  criticalBg: "#fdedec",
  text: "#1c2833",
  textSec: "#566573",
  textMuted: "#909eab",
  border: "#dce6f0",
  rowAlt: "#f5f9fd",
  tagBg: "#eaf2fa",
};

// ═══════════════════════════════════════════════════════════════
// UI PRIMITIVES
// ═══════════════════════════════════════════════════════════════
function Card({ children, style }) {
  return (
    <div style={{ background: T.card, border: `1px solid ${T.cardBorder}`, borderRadius: 8, padding: 18, boxShadow: T.cardShadow, ...style }}>
      {children}
    </div>
  );
}

function SectionTitle({ title, sub, right }) {
  return (
    <div style={{ marginBottom: 14, display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
      <div>
        <div style={{ fontSize: 15, fontWeight: 600, color: T.primary, display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ width: 3, height: 16, background: T.primaryLight, borderRadius: 2, display: "inline-block" }} />
          {title}
        </div>
        {sub && <div style={{ fontSize: 12, color: T.textMuted, marginTop: 2, paddingLeft: 11 }}>{sub}</div>}
      </div>
      {right && <div>{right}</div>}
    </div>
  );
}

function KPICard({ label, value, unit, color, sub }) {
  const cl = color || T.primary;
  return (
    <Card style={{ textAlign: "center", padding: "16px 10px" }}>
      <div style={{ fontSize: 11, color: T.textMuted, textTransform: "uppercase", letterSpacing: 1.2, marginBottom: 8, fontWeight: 500 }}>{label}</div>
      <div style={{ fontSize: 30, fontWeight: 700, color: cl, lineHeight: 1 }}>
        {value}<span style={{ fontSize: 13, color: T.textSec, marginLeft: 2, fontWeight: 400 }}>{unit}</span>
      </div>
      {sub && <div style={{ fontSize: 11, color: T.textMuted, marginTop: 6 }}>{sub}</div>}
    </Card>
  );
}

function Select({ value, onChange, options, placeholder, style }) {
  return (
    <select value={value} onChange={e => onChange(e.target.value)} style={{
      padding: "6px 12px", borderRadius: 6, border: `1px solid ${T.cardBorder}`,
      fontSize: 13, color: T.text, background: "#fff", outline: "none", cursor: "pointer",
      fontFamily: "inherit", ...style,
    }}>
      <option value="">{placeholder || "全部"}</option>
      {options.map(o => <option key={o} value={o}>{o}</option>)}
    </select>
  );
}

function fmt(n) {
  if (n >= 10000) return (n / 10000).toFixed(2) + " 万";
  return n.toLocaleString();
}
function fmtWan(n) { return (n / 10000).toFixed(2); }

// ═══════════════════════════════════════════════════════════════
// CHARTS
// ═══════════════════════════════════════════════════════════════
function HBarChart({ data, labelKey, valueKey, color = T.primaryLight, maxHeight = 280 }) {
  const max = Math.max(...data.map(d => d[valueKey]));
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8, maxHeight, overflowY: "auto" }}>
      {data.map((d, i) => (
        <div key={i}>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 3 }}>
            <span style={{ color: T.text, fontWeight: 500 }}>{d[labelKey]}</span>
            <span style={{ color: T.textSec, fontWeight: 600 }}>{fmtWan(d[valueKey])} 万</span>
          </div>
          <div style={{ height: 8, background: "#e8eff7", borderRadius: 4, overflow: "hidden" }}>
            <div style={{ width: `${(d[valueKey] / max) * 100}%`, height: "100%", background: `linear-gradient(90deg, ${color}bb, ${color})`, borderRadius: 4, transition: "width 0.5s ease" }} />
          </div>
        </div>
      ))}
    </div>
  );
}

function DonutChart({ segments, size = 130, thickness = 14 }) {
  const total = segments.reduce((s, seg) => s + seg.value, 0);
  const r = (size - thickness) / 2, circ = 2 * Math.PI * r;
  let offset = 0;
  return (
    <div style={{ position: "relative", width: size, height: size }}>
      <svg width={size} height={size} style={{ transform: "rotate(-90deg)" }}>
        <circle cx={size/2} cy={size/2} r={r} fill="none" stroke="#e0eaf4" strokeWidth={thickness} />
        {segments.map((seg, i) => {
          const dash = (seg.value / total) * circ;
          const o = offset;
          offset += dash;
          return <circle key={i} cx={size/2} cy={size/2} r={r} fill="none" stroke={seg.color} strokeWidth={thickness} strokeDasharray={`${dash} ${circ - dash}`} strokeDashoffset={-o} strokeLinecap="butt" />;
        })}
      </svg>
      <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
        <div style={{ fontSize: 18, fontWeight: 700, color: T.primary }}>{fmtWan(total)}</div>
        <div style={{ fontSize: 10, color: T.textMuted }}>万元</div>
      </div>
    </div>
  );
}

function TreemapChart({ data, height = 200 }) {
  const total = data.reduce((s, d) => s + d.value, 0);
  const colors = ["#2980b9", "#27ae60", "#e67e22", "#8e44ad", "#16a085", "#c0392b", "#2c3e50"];
  return (
    <div style={{ display: "flex", gap: 2, height, borderRadius: 6, overflow: "hidden" }}>
      {data.map((d, i) => {
        const pct = d.value / total * 100;
        return (
          <div key={i} style={{
            width: `${pct}%`, background: colors[i % colors.length], minWidth: pct > 3 ? "auto" : 0,
            display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
            padding: pct > 8 ? "8px 4px" : "4px 2px", color: "#fff", transition: "width 0.5s",
          }}>
            {pct > 8 && <div style={{ fontSize: 11, fontWeight: 600, textAlign: "center", lineHeight: 1.3 }}>{d.label}</div>}
            {pct > 10 && <div style={{ fontSize: 10, opacity: 0.85, marginTop: 2 }}>{fmtWan(d.value)}万</div>}
            {pct > 10 && <div style={{ fontSize: 10, opacity: 0.7 }}>{pct.toFixed(1)}%</div>}
          </div>
        );
      })}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// MAIN DASHBOARD
// ═══════════════════════════════════════════════════════════════
export default function AuxBalanceDashboard() {
  const [time, setTime] = useState(new Date());
  const [filterProject, setFilterProject] = useState("");
  const [filterDept, setFilterDept] = useState("");
  const [searchText, setSearchText] = useState("");

  useEffect(() => { const t = setInterval(() => setTime(new Date()), 1000); return () => clearInterval(t); }, []);

  const filtered = useMemo(() => {
    return BALANCE_DATA.filter(d =>
      (!filterProject || d.project === filterProject) &&
      (!filterDept || d.dept === filterDept) &&
      (!searchText || d.name.includes(searchText) || d.code.includes(searchText) || d.contract.includes(searchText))
    );
  }, [filterProject, filterDept, searchText]);

  const totalBalance = filtered.reduce((s, d) => s + d.balance, 0);
  const projectCount = new Set(filtered.map(d => d.projectId)).size;
  const contractCount = new Set(filtered.filter(d => d.contract !== "—").map(d => d.contract)).size;
  const subjectCount = new Set(filtered.map(d => d.code)).size;

  // Aggregations
  const byProject = useMemo(() => {
    const map = {};
    filtered.forEach(d => { map[d.project] = (map[d.project] || 0) + d.balance; });
    return Object.entries(map).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value);
  }, [filtered]);

  const byDept = useMemo(() => {
    const map = {};
    filtered.forEach(d => { map[d.dept] = (map[d.dept] || 0) + d.balance; });
    return Object.entries(map).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value);
  }, [filtered]);

  const byCategory = useMemo(() => {
    const map = {};
    filtered.forEach(d => {
      const cat = d.code.startsWith("5001") ? "原材料/设备" : d.code.startsWith("5101") ? "外协/服务" : d.code.startsWith("5201") ? "折旧" : d.code.startsWith("5301") ? "检测试验" : d.code.startsWith("5401") ? "设计咨询" : d.code.startsWith("5501") ? "租赁" : d.code.startsWith("5601") ? "培训" : "其他";
      map[cat] = (map[cat] || 0) + d.balance;
    });
    return Object.entries(map).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value);
  }, [filtered]);

  const deptColors = ["#2980b9", "#27ae60", "#e67e22", "#8e44ad", "#16a085"];
  const donutSegments = byDept.map((d, i) => ({ ...d, color: deptColors[i % deptColors.length] }));

  const topContracts = useMemo(() => {
    const map = {};
    filtered.forEach(d => {
      if (d.contract === "—") return;
      if (!map[d.contract]) map[d.contract] = { contract: d.contract, project: d.project, balance: 0 };
      map[d.contract].balance += d.balance;
    });
    return Object.values(map).sort((a, b) => b.balance - a.balance).slice(0, 8);
  }, [filtered]);

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
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round"><rect x="3" y="3" width="18" height="18" rx="2" /><path d="M3 9h18M9 3v18" /></svg>
          </div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.65)", letterSpacing: 1, lineHeight: 1.4 }}>DECISION TWINS<br/>PROJECT CENTER</div>
        </div>
        <div style={{ textAlign: "center" }}>
          <div style={{ fontSize: 22, fontWeight: 700, color: "#fff", letterSpacing: 4 }}>辅助余额大屏</div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.55)", letterSpacing: 2, marginTop: 2 }}>AUXILIARY BALANCE OVERVIEW</div>
        </div>
        <div style={{ textAlign: "right", color: "#fff", minWidth: 180 }}>
          <div style={{ fontSize: 16, fontWeight: 600 }}>{time.toLocaleTimeString("zh-CN", { hour12: false })}</div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.6)", marginTop: 1 }}>{time.toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric", weekday: "short" })}</div>
        </div>
      </div>

      {/* Content */}
      <div style={{ padding: "18px 24px", maxWidth: 1480, margin: "0 auto", display: "flex", flexDirection: "column", gap: 16 }}>

        {/* Filters */}
        <Card style={{ padding: "12px 18px", display: "flex", alignItems: "center", gap: 16, flexWrap: "wrap" }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: T.primary }}>筛选条件</span>
          <Select value={filterProject} onChange={setFilterProject} options={PROJECTS_LIST} placeholder="全部项目" />
          <Select value={filterDept} onChange={setFilterDept} options={DEPTS_LIST} placeholder="全部部门" />
          <input value={searchText} onChange={e => setSearchText(e.target.value)} placeholder="搜索科目/合同..."
            style={{ padding: "6px 12px", borderRadius: 6, border: `1px solid ${T.cardBorder}`, fontSize: 13, outline: "none", width: 200, fontFamily: "inherit" }} />
          {(filterProject || filterDept || searchText) && (
            <button onClick={() => { setFilterProject(""); setFilterDept(""); setSearchText(""); }}
              style={{ padding: "6px 14px", borderRadius: 6, border: `1px solid ${T.cardBorder}`, background: T.tagBg, color: T.primary, fontSize: 12, cursor: "pointer", fontFamily: "inherit", fontWeight: 500 }}>
              清除筛选
            </button>
          )}
          <span style={{ fontSize: 12, color: T.textMuted, marginLeft: "auto" }}>
            共 {filtered.length} 条记录
          </span>
        </Card>

        {/* KPI Row */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12 }}>
          <KPICard label="余额合计" value={fmtWan(totalBalance)} unit="万" color={T.primary} sub="全部筛选项汇总" />
          <KPICard label="涉及项目" value={projectCount} unit="个" color={T.accent} />
          <KPICard label="科目数量" value={subjectCount} unit="个" color={T.primaryLight} />
          <KPICard label="合同数量" value={contractCount} unit="份" color="#8e44ad" />
        </div>

        {/* Charts Row */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16 }}>
          {/* By Project */}
          <Card>
            <SectionTitle title="按项目分布" sub="项目维度余额排名" />
            <HBarChart data={byProject} labelKey="label" valueKey="value" color={T.primaryLight} />
          </Card>

          {/* By Department - Donut */}
          <Card>
            <SectionTitle title="按部门分布" />
            <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: 20 }}>
              <DonutChart segments={donutSegments} />
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {donutSegments.map((s, i) => (
                  <div key={i} style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <div style={{ width: 10, height: 10, borderRadius: 3, background: s.color }} />
                    <span style={{ fontSize: 12, color: T.textSec, minWidth: 48 }}>{s.label}</span>
                    <span style={{ fontSize: 12, color: T.text, fontWeight: 600 }}>{fmtWan(s.value)}万</span>
                  </div>
                ))}
              </div>
            </div>
          </Card>

          {/* By Category */}
          <Card>
            <SectionTitle title="按费用类别分布" />
            <TreemapChart data={byCategory} height={180} />
            <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginTop: 12 }}>
              {byCategory.map((d, i) => {
                const colors = ["#2980b9", "#27ae60", "#e67e22", "#8e44ad", "#16a085", "#c0392b", "#2c3e50"];
                return (
                  <span key={i} style={{ fontSize: 11, display: "flex", alignItems: "center", gap: 4 }}>
                    <span style={{ width: 8, height: 8, borderRadius: 2, background: colors[i % colors.length], display: "inline-block" }} />
                    {d.label}
                  </span>
                );
              })}
            </div>
          </Card>
        </div>

        {/* Bottom: Table + Top Contracts */}
        <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 16 }}>
          {/* Main Table */}
          <Card>
            <SectionTitle title="辅助余额明细" sub="科目级别余额清单" />
            <div style={{ overflowX: "auto", overflowY: "auto", maxHeight: 400 }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                <thead><tr>
                  {["科目编号","科目名称","所属项目","部门","合同名称","余额"].map((h, i) => (
                    <th key={i} style={{ textAlign: i === 5 ? "right" : "left", padding: "10px 12px", color: T.primary, borderBottom: `2px solid ${T.border}`, fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: 1, whiteSpace: "nowrap", position: "sticky", top: 0, background: T.card, zIndex: 1 }}>{h}</th>
                  ))}
                </tr></thead>
                <tbody>
                  {filtered.map((d, ri) => (
                    <tr key={d.id} style={{ background: ri % 2 === 0 ? T.card : T.rowAlt }}>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, color: T.link, fontWeight: 600, fontSize: 12 }}>{d.code}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, color: T.text }}>{d.name}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, color: T.text }}>{d.project}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, color: T.textSec }}>{d.dept}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, color: T.textSec, maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{d.contract}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, textAlign: "right", fontWeight: 600, color: d.balance > 3000000 ? T.critical : d.balance > 1000000 ? T.warning : T.text }}>{fmt(d.balance)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot><tr style={{ background: T.tagBg }}>
                  <td colSpan={5} style={{ padding: "10px 12px", fontWeight: 600, color: T.primary, borderTop: `2px solid ${T.border}` }}>合计</td>
                  <td style={{ padding: "10px 12px", textAlign: "right", fontWeight: 700, color: T.primary, borderTop: `2px solid ${T.border}`, fontSize: 14 }}>{fmt(totalBalance)}</td>
                </tr></tfoot>
              </table>
            </div>
          </Card>

          {/* Top Contracts */}
          <Card>
            <SectionTitle title="合同金额 TOP 8" sub="按关联余额排序" />
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {topContracts.map((c, i) => (
                <div key={i} style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <div style={{
                    width: 24, height: 24, borderRadius: 6, display: "flex", alignItems: "center", justifyContent: "center",
                    background: i < 3 ? T.primary : T.tagBg, color: i < 3 ? "#fff" : T.textSec,
                    fontSize: 12, fontWeight: 700,
                  }}>{i + 1}</div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 12, color: T.text, fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{c.contract}</div>
                    <div style={{ fontSize: 11, color: T.textMuted }}>{c.project}</div>
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: T.primary, whiteSpace: "nowrap" }}>{fmtWan(c.balance)}万</div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>

      {/* Footer */}
      <div style={{ textAlign: "center", padding: "12px 24px", borderTop: `1px solid ${T.cardBorder}`, fontSize: 11, color: T.textMuted, background: "#f0f5fb" }}>
        DECISION TWINS SYSTEM · V4.0 · 系统运行正常 · 数据实时刷新
      </div>
    </div>
  );
}
