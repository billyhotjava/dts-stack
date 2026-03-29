import { useState, useEffect, useMemo } from "react";

// ═══════════════════════════════════════════════════════════════
// MOCK DATA — 辅助余额表（按个人/职工维度）
// ═══════════════════════════════════════════════════════════════
const BALANCE_DATA = [
  { id: 1, code: "1122.01", name: "备用金", dept: "制造部", employee: "张伟", balance: 5000 },
  { id: 2, code: "1122.02", name: "差旅费借款", dept: "制造部", employee: "张伟", balance: 12800 },
  { id: 3, code: "1122.01", name: "备用金", dept: "电力部", employee: "李娜", balance: 3000 },
  { id: 4, code: "1122.03", name: "采购预付款", dept: "采购部", employee: "赵敏", balance: 85000 },
  { id: 5, code: "1122.02", name: "差旅费借款", dept: "采购部", employee: "赵敏", balance: 9600 },
  { id: 6, code: "2211.01", name: "工资应付", dept: "制造部", employee: "刘洋", balance: -18500 },
  { id: 7, code: "2211.02", name: "奖金应付", dept: "制造部", employee: "刘洋", balance: -6200 },
  { id: 8, code: "1122.04", name: "培训费借款", dept: "IT部", employee: "孙磊", balance: 24000 },
  { id: 9, code: "1122.02", name: "差旅费借款", dept: "IT部", employee: "孙磊", balance: 18500 },
  { id: 10, code: "1122.01", name: "备用金", dept: "IT部", employee: "王强", balance: 5000 },
  { id: 11, code: "1122.05", name: "设备采购借款", dept: "IT部", employee: "王强", balance: 156000 },
  { id: 12, code: "1122.02", name: "差旅费借款", dept: "IT部", employee: "王强", balance: 22400 },
  { id: 13, code: "2211.01", name: "工资应付", dept: "电力部", employee: "李娜", balance: -21000 },
  { id: 14, code: "1122.01", name: "备用金", dept: "研发部", employee: "吴杰", balance: 8000 },
  { id: 15, code: "1122.06", name: "实验材料借款", dept: "研发部", employee: "吴杰", balance: 67000 },
  { id: 16, code: "1122.02", name: "差旅费借款", dept: "研发部", employee: "吴杰", balance: 15200 },
  { id: 17, code: "2211.03", name: "社保代扣", dept: "安环部", employee: "周芳", balance: -4800 },
  { id: 18, code: "1122.01", name: "备用金", dept: "安环部", employee: "周芳", balance: 3000 },
  { id: 19, code: "1122.02", name: "差旅费借款", dept: "营销部", employee: "郑华", balance: 31500 },
  { id: 20, code: "1122.07", name: "业务招待借款", dept: "营销部", employee: "郑华", balance: 18000 },
  { id: 21, code: "1122.01", name: "备用金", dept: "营销部", employee: "郑华", balance: 5000 },
  { id: 22, code: "2211.01", name: "工资应付", dept: "质量部", employee: "陈静", balance: -16800 },
  { id: 23, code: "1122.01", name: "备用金", dept: "质量部", employee: "陈静", balance: 3000 },
  { id: 24, code: "1122.02", name: "差旅费借款", dept: "制造部", employee: "刘洋", balance: 8900 },
  { id: 25, code: "1122.08", name: "项目预支款", dept: "电力部", employee: "李娜", balance: 45000 },
  { id: 26, code: "2211.02", name: "奖金应付", dept: "IT部", employee: "孙磊", balance: -8500 },
  { id: 27, code: "1122.02", name: "差旅费借款", dept: "采购部", employee: "马超", balance: 7800 },
  { id: 28, code: "1122.01", name: "备用金", dept: "采购部", employee: "马超", balance: 3000 },
  { id: 29, code: "1122.09", name: "外出学习借款", dept: "研发部", employee: "钱波", balance: 35000 },
  { id: 30, code: "1122.02", name: "差旅费借款", dept: "研发部", employee: "钱波", balance: 11200 },
];

const EMPLOYEES_LIST = [...new Set(BALANCE_DATA.map(d => d.employee))];
const DEPTS_LIST = [...new Set(BALANCE_DATA.map(d => d.dept))];

// ═══════════════════════════════════════════════════════════════
// THEME
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
  return <div style={{ background: T.card, border: `1px solid ${T.cardBorder}`, borderRadius: 8, padding: 18, boxShadow: T.cardShadow, ...style }}>{children}</div>;
}

function SectionTitle({ title, sub, right }) {
  return (
    <div style={{ marginBottom: 14, display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
      <div>
        <div style={{ fontSize: 15, fontWeight: 600, color: T.primary, display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ width: 3, height: 16, background: T.primaryLight, borderRadius: 2, display: "inline-block" }} />{title}
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
      fontSize: 13, color: T.text, background: "#fff", outline: "none", cursor: "pointer", fontFamily: "inherit", ...style,
    }}>
      <option value="">{placeholder || "全部"}</option>
      {options.map(o => <option key={o} value={o}>{o}</option>)}
    </select>
  );
}

function fmt(n) {
  const abs = Math.abs(n);
  const str = abs >= 10000 ? (abs / 10000).toFixed(2) + " 万" : abs.toLocaleString();
  return n < 0 ? "-" + str : str;
}
function fmtWan(n) { return (n / 10000).toFixed(2); }

// ═══════════════════════════════════════════════════════════════
// CHARTS
// ═══════════════════════════════════════════════════════════════
function HBarChart({ data, labelKey, valueKey, color = T.primaryLight, maxHeight = 280 }) {
  const max = Math.max(...data.map(d => Math.abs(d[valueKey])));
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8, maxHeight, overflowY: "auto" }}>
      {data.map((d, i) => {
        const v = d[valueKey];
        const isNeg = v < 0;
        return (
          <div key={i}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 3 }}>
              <span style={{ color: T.text, fontWeight: 500 }}>{d[labelKey]}</span>
              <span style={{ color: isNeg ? T.good : T.textSec, fontWeight: 600 }}>{fmtWan(v)} 万</span>
            </div>
            <div style={{ height: 8, background: "#e8eff7", borderRadius: 4, overflow: "hidden" }}>
              <div style={{ width: `${(Math.abs(v) / max) * 100}%`, height: "100%", background: isNeg ? T.good : color, borderRadius: 4, transition: "width 0.5s ease" }} />
            </div>
          </div>
        );
      })}
    </div>
  );
}

function DonutChart({ segments, size = 130, thickness = 14 }) {
  const total = segments.reduce((s, seg) => s + Math.abs(seg.value), 0);
  const r = (size - thickness) / 2, circ = 2 * Math.PI * r;
  let offset = 0;
  return (
    <div style={{ position: "relative", width: size, height: size }}>
      <svg width={size} height={size} style={{ transform: "rotate(-90deg)" }}>
        <circle cx={size/2} cy={size/2} r={r} fill="none" stroke="#e0eaf4" strokeWidth={thickness} />
        {segments.map((seg, i) => {
          const dash = (Math.abs(seg.value) / total) * circ;
          const o = offset; offset += dash;
          return <circle key={i} cx={size/2} cy={size/2} r={r} fill="none" stroke={seg.color} strokeWidth={thickness} strokeDasharray={`${dash} ${circ - dash}`} strokeDashoffset={-o} strokeLinecap="butt" />;
        })}
      </svg>
      <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
        <div style={{ fontSize: 16, fontWeight: 700, color: T.primary }}>{segments.length}</div>
        <div style={{ fontSize: 10, color: T.textMuted }}>部门</div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// MAIN DASHBOARD
// ═══════════════════════════════════════════════════════════════
export default function PersonalBalanceDashboard() {
  const [time, setTime] = useState(new Date());
  const [filterEmployee, setFilterEmployee] = useState("");
  const [filterDept, setFilterDept] = useState("");
  const [searchText, setSearchText] = useState("");

  useEffect(() => { const t = setInterval(() => setTime(new Date()), 1000); return () => clearInterval(t); }, []);

  const filtered = useMemo(() => {
    return BALANCE_DATA.filter(d =>
      (!filterEmployee || d.employee === filterEmployee) &&
      (!filterDept || d.dept === filterDept) &&
      (!searchText || d.name.includes(searchText) || d.code.includes(searchText) || d.employee.includes(searchText))
    );
  }, [filterEmployee, filterDept, searchText]);

  const totalBalance = filtered.reduce((s, d) => s + d.balance, 0);
  const debitTotal = filtered.filter(d => d.balance > 0).reduce((s, d) => s + d.balance, 0);
  const creditTotal = filtered.filter(d => d.balance < 0).reduce((s, d) => s + d.balance, 0);
  const employeeCount = new Set(filtered.map(d => d.employee)).size;
  const subjectCount = new Set(filtered.map(d => d.code)).size;

  const byEmployee = useMemo(() => {
    const map = {};
    filtered.forEach(d => { map[d.employee] = (map[d.employee] || 0) + d.balance; });
    return Object.entries(map).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value);
  }, [filtered]);

  const byDept = useMemo(() => {
    const map = {};
    filtered.forEach(d => { map[d.dept] = (map[d.dept] || 0) + Math.abs(d.balance); });
    return Object.entries(map).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value);
  }, [filtered]);

  const byCategory = useMemo(() => {
    const map = {};
    filtered.forEach(d => {
      const cat = d.code.startsWith("1122") ? "其他应收-借款" : d.code.startsWith("2211") ? "应付职工薪酬" : "其他";
      map[cat] = (map[cat] || 0) + Math.abs(d.balance);
    });
    return Object.entries(map).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value);
  }, [filtered]);

  const deptColors = ["#2980b9", "#27ae60", "#e67e22", "#8e44ad", "#16a085", "#c0392b", "#2c3e50"];
  const donutSegments = byDept.map((d, i) => ({ ...d, color: deptColors[i % deptColors.length] }));

  // Top borrowers
  const topBorrowers = byEmployee.filter(e => e.value > 0).slice(0, 8);

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
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round"><circle cx="12" cy="8" r="4" /><path d="M4 20c0-4 4-7 8-7s8 3 8 7" /></svg>
          </div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.65)", letterSpacing: 1, lineHeight: 1.4 }}>DECISION TWINS<br/>PERSONAL LEDGER</div>
        </div>
        <div style={{ textAlign: "center" }}>
          <div style={{ fontSize: 22, fontWeight: 700, color: "#fff", letterSpacing: 4 }}>个人辅助余额大屏</div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.55)", letterSpacing: 2, marginTop: 2 }}>PERSONAL AUXILIARY BALANCE OVERVIEW</div>
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
          <Select value={filterEmployee} onChange={setFilterEmployee} options={EMPLOYEES_LIST} placeholder="全部职工" />
          <Select value={filterDept} onChange={setFilterDept} options={DEPTS_LIST} placeholder="全部部门" />
          <input value={searchText} onChange={e => setSearchText(e.target.value)} placeholder="搜索科目/职工..."
            style={{ padding: "6px 12px", borderRadius: 6, border: `1px solid ${T.cardBorder}`, fontSize: 13, outline: "none", width: 200, fontFamily: "inherit" }} />
          {(filterEmployee || filterDept || searchText) && (
            <button onClick={() => { setFilterEmployee(""); setFilterDept(""); setSearchText(""); }}
              style={{ padding: "6px 14px", borderRadius: 6, border: `1px solid ${T.cardBorder}`, background: T.tagBg, color: T.primary, fontSize: 12, cursor: "pointer", fontFamily: "inherit", fontWeight: 500 }}>
              清除筛选
            </button>
          )}
          <span style={{ fontSize: 12, color: T.textMuted, marginLeft: "auto" }}>共 {filtered.length} 条记录</span>
        </Card>

        {/* KPI Row */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 12 }}>
          <KPICard label="净余额" value={fmtWan(totalBalance)} unit="万" color={totalBalance >= 0 ? T.primary : T.critical} sub="借方-贷方" />
          <KPICard label="借方合计" value={fmtWan(debitTotal)} unit="万" color={T.warning} sub="应收/借款" />
          <KPICard label="贷方合计" value={fmtWan(Math.abs(creditTotal))} unit="万" color={T.good} sub="应付/代扣" />
          <KPICard label="涉及职工" value={employeeCount} unit="人" color={T.accent} />
          <KPICard label="科目数量" value={subjectCount} unit="个" color={T.primaryLight} />
        </div>

        {/* Charts Row */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16 }}>
          {/* By Employee */}
          <Card>
            <SectionTitle title="按职工分布" sub="个人净余额排名（借方为正）" />
            <HBarChart data={byEmployee} labelKey="label" valueKey="value" color={T.primaryLight} />
          </Card>

          {/* By Department - Donut */}
          <Card>
            <SectionTitle title="按部门分布" sub="各部门绝对金额占比" />
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
            <SectionTitle title="按科目类别分布" />
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {byCategory.map((d, i) => {
                const total = byCategory.reduce((s, x) => s + x.value, 0);
                const pct = ((d.value / total) * 100).toFixed(1);
                const cl = deptColors[i % deptColors.length];
                return (
                  <div key={i}>
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 4 }}>
                      <span style={{ color: T.text, fontWeight: 500 }}>{d.label}</span>
                      <span style={{ color: T.textSec }}>{fmtWan(d.value)} 万 ({pct}%)</span>
                    </div>
                    <div style={{ height: 20, background: "#e8eff7", borderRadius: 4, overflow: "hidden" }}>
                      <div style={{ width: `${pct}%`, height: "100%", background: cl, borderRadius: 4, transition: "width 0.5s", display: "flex", alignItems: "center", paddingLeft: 8 }}>
                        {parseFloat(pct) > 15 && <span style={{ fontSize: 10, color: "#fff", fontWeight: 600 }}>{pct}%</span>}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </Card>
        </div>

        {/* Bottom: Table + Top Borrowers */}
        <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 16 }}>
          {/* Main Table */}
          <Card>
            <SectionTitle title="个人辅助余额明细" sub="科目级别余额清单" />
            <div style={{ overflowX: "auto", overflowY: "auto", maxHeight: 400 }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                <thead><tr>
                  {["科目编号","科目名称","部门名称","职工名称","余额"].map((h, i) => (
                    <th key={i} style={{ textAlign: i === 4 ? "right" : "left", padding: "10px 12px", color: T.primary, borderBottom: `2px solid ${T.border}`, fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: 1, whiteSpace: "nowrap", position: "sticky", top: 0, background: T.card, zIndex: 1 }}>{h}</th>
                  ))}
                </tr></thead>
                <tbody>
                  {filtered.map((d, ri) => (
                    <tr key={d.id} style={{ background: ri % 2 === 0 ? T.card : T.rowAlt }}>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, color: T.link, fontWeight: 600, fontSize: 12 }}>{d.code}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, color: T.text }}>{d.name}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, color: T.textSec }}>{d.dept}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, color: T.text, fontWeight: 500 }}>{d.employee}</td>
                      <td style={{ padding: "9px 12px", borderBottom: `1px solid ${T.border}`, textAlign: "right", fontWeight: 600, color: d.balance < 0 ? T.good : d.balance > 50000 ? T.critical : d.balance > 10000 ? T.warning : T.text }}>{fmt(d.balance)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot><tr style={{ background: T.tagBg }}>
                  <td colSpan={4} style={{ padding: "10px 12px", fontWeight: 600, color: T.primary, borderTop: `2px solid ${T.border}` }}>合计</td>
                  <td style={{ padding: "10px 12px", textAlign: "right", fontWeight: 700, color: T.primary, borderTop: `2px solid ${T.border}`, fontSize: 14 }}>{fmt(totalBalance)}</td>
                </tr></tfoot>
              </table>
            </div>
          </Card>

          {/* Top Borrowers */}
          <Card>
            <SectionTitle title="借款余额 TOP 8" sub="按个人借方余额排序" />
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {topBorrowers.map((e, i) => {
                const empData = BALANCE_DATA.filter(d => d.employee === e.label);
                const dept = empData[0]?.dept || "";
                return (
                  <div key={i} style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <div style={{
                      width: 28, height: 28, borderRadius: 8, display: "flex", alignItems: "center", justifyContent: "center",
                      background: i < 3 ? T.primary : T.tagBg, color: i < 3 ? "#fff" : T.textSec,
                      fontSize: 12, fontWeight: 700,
                    }}>{i + 1}</div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 13, color: T.text, fontWeight: 500 }}>{e.label}</div>
                      <div style={{ fontSize: 11, color: T.textMuted }}>{dept}</div>
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 600, color: e.value > 100000 ? T.critical : e.value > 30000 ? T.warning : T.text, whiteSpace: "nowrap" }}>{fmtWan(e.value)} 万</div>
                  </div>
                );
              })}
            </div>
            {/* Legend */}
            <div style={{ marginTop: 16, padding: "10px 12px", background: T.tagBg, borderRadius: 6, fontSize: 11, color: T.textSec, lineHeight: 1.6 }}>
              <span style={{ fontWeight: 600, color: T.primary }}>说明：</span>
              正数为借方余额（应收/借款），负数为贷方余额（应付/代扣）。
              <span style={{ color: T.critical, fontWeight: 600 }}>红色</span>标识超5万元借款，
              <span style={{ color: T.warning, fontWeight: 600 }}>橙色</span>标识超1万元借款。
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
