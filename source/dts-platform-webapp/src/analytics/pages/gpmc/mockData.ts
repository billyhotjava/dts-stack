/**
 * GPMC Mock Data — Phase 1 demo data for all 6 screens.
 * Will be replaced by real API in Phase 2.
 */

// ══════════════════════════════════════════
// Screen 1: 综合态势总览
// ══════════════════════════════════════════
export const overviewKpis = [
	{ key: "totalProjects", label: "项目总数", value: "128", sub: "在建 96 / 储备 32", trend: "+6.2%", trendDir: "up" as const },
	{ key: "activeProjects", label: "进行中项目", value: "74", sub: "核心项目 21 个", trend: "+3", trendDir: "up" as const },
	{ key: "delayedProjects", label: "延期项目", value: "11", sub: "重大延期 4 个", trend: "+2", trendDir: "up" as const, tone: "danger" as const },
	{ key: "annualBudget", label: "年度预算总额", value: "36.8", unit: "亿元", sub: "执行 71%", badge: "执行", badgeTone: "info" as const },
	{ key: "avgProgress", label: "平均进度达成率", value: "78", unit: "%", sub: "计划进度偏差 4.6%", trend: "↑ 1.8%", trendDir: "up" as const },
	{ key: "highRiskRate", label: "高风险项目占比", value: "9.4", unit: "%", sub: "预警总数 37 条", badge: "需关注", badgeTone: "danger" as const },
];

export const healthMatrix = [
	{ id: "GZ-2026-018", name: "集团制造协同平台二期", dept: "数字化事业部 / A类重点项目", owner: "张伟 / PMO", actualRate: 82, planRate: 86, execRate: 76, status: "正常推进", statusTone: "ok" as const, risk: "中" },
	{ id: "NY-2026-006", name: "新能源工厂建设项目", dept: "工程建设中心 / 战略级项目", owner: "刘工 / 项目总", actualRate: 61, planRate: 79, execRate: 88, status: "延期预警", statusTone: "danger" as const, risk: "高" },
	{ id: "RD-2026-011", name: "核心器件研发验证项目", dept: "研发中心 / 技术攻关类", owner: "陈工 / 技术负责人", actualRate: 46, planRate: 44, execRate: 39, status: "风险可控", statusTone: "warn" as const, risk: "中" },
	{ id: "IT-2026-003", name: "集团统一主数据治理工程", dept: "信息化管理部 / 平台类项目", owner: "王工 / 产品经理", actualRate: 73, planRate: 75, execRate: 68, status: "按计划执行", statusTone: "ok" as const, risk: "低" },
	{ id: "SC-2026-009", name: "供应链数字化转型项目", dept: "采购中心 / 核心项目", owner: "李工 / 项目经理", actualRate: 88, planRate: 85, execRate: 92, status: "正常推进", statusTone: "ok" as const, risk: "低" },
];

export const deptCost = [
	{ dept: "工程建设中心", actual: 8.6, budget: 9.5 },
	{ dept: "数字化事业部", actual: 5.2, budget: 6.0 },
	{ dept: "研发中心", actual: 6.8, budget: 8.2 },
	{ dept: "生产制造中心", actual: 10.4, budget: 10.0 },
];

export const costSummary = { overrun: "+0.72", efficiency: "0.93", burnRate: "71%" };

export const alerts = [
	{ title: "关键里程碑逾期", count: 12, tone: "danger" as const },
	{ title: "需求变更频繁", count: 8, tone: "warn" as const },
	{ title: "资源缺口预警", count: 5, tone: "warn" as const },
	{ title: "质量问题待闭环", count: 3, tone: "danger" as const },
];

// ══════════════════════════════════════════
// Screen 2: 项目执行监控
// ══════════════════════════════════════════
export const executionKpis = [
	{ key: "completionRate", label: "整体完成率", value: "72.4", unit: "%", trend: "+2.1%", trendDir: "up" as const },
	{ key: "milestoneRate", label: "里程碑达成率", value: "68", unit: "%", trend: "-1.5%", trendDir: "down" as const, tone: "warn" as const },
	{ key: "criticalDeviation", label: "关键路径偏差", value: "4.6", unit: "%", tone: "warn" as const },
	{ key: "overdueCount", label: "延期任务数", value: "37", trend: "+5", trendDir: "up" as const, tone: "danger" as const },
	{ key: "maxDelay", label: "最大延期天数", value: "28", tone: "danger" as const },
	{ key: "dueSoon", label: "近期到期", value: "15", sub: "7天内" },
];

export const delayTop10 = [
	{ name: "新能源工厂建设项目", delay: 28, dept: "工程建设中心", risk: "高" },
	{ name: "核心器件研发验证项目", delay: 21, dept: "研发中心", risk: "中" },
	{ name: "智能制造产线改造", delay: 18, dept: "生产制造中心", risk: "高" },
	{ name: "供应链协同平台", delay: 14, dept: "采购中心", risk: "中" },
	{ name: "质量追溯系统", delay: 12, dept: "质量管理部", risk: "低" },
	{ name: "数据治理一期", delay: 10, dept: "信息化管理部", risk: "低" },
	{ name: "ERP升级项目", delay: 8, dept: "财务部", risk: "中" },
	{ name: "安全生产监控平台", delay: 7, dept: "安全环保部", risk: "高" },
	{ name: "人力资源数字化", delay: 5, dept: "人力资源部", risk: "低" },
	{ name: "办公自动化升级", delay: 3, dept: "行政管理部", risk: "低" },
];

export const ganttTasks = [
	{ name: "需求分析", project: "制造协同平台", start: "2026-01-06", end: "2026-02-15", progress: 100, risk: "低" },
	{ name: "系统设计", project: "制造协同平台", start: "2026-02-10", end: "2026-03-20", progress: 85, risk: "低" },
	{ name: "核心开发", project: "制造协同平台", start: "2026-03-01", end: "2026-05-30", progress: 45, risk: "中" },
	{ name: "基建施工", project: "新能源工厂", start: "2026-01-15", end: "2026-06-30", progress: 35, risk: "高" },
	{ name: "设备采购", project: "新能源工厂", start: "2026-03-01", end: "2026-05-15", progress: 60, risk: "中" },
	{ name: "原型验证", project: "核心器件研发", start: "2026-02-01", end: "2026-04-30", progress: 70, risk: "中" },
	{ name: "批量测试", project: "核心器件研发", start: "2026-04-01", end: "2026-06-30", progress: 20, risk: "高" },
];

export const workloadData = [
	{ dept: "工程建设中心", active: 24, overdue: 8 },
	{ dept: "数字化事业部", active: 18, overdue: 3 },
	{ dept: "研发中心", active: 22, overdue: 6 },
	{ dept: "生产制造中心", active: 15, overdue: 4 },
	{ dept: "采购中心", active: 12, overdue: 2 },
	{ dept: "质量管理部", active: 9, overdue: 1 },
];

export const stageDistribution = [
	{ name: "策划中", value: 18, color: "#94a3b8" },
	{ name: "执行中", value: 52, color: "#0b57d0" },
	{ name: "验收中", value: 14, color: "#f9ab00" },
	{ name: "已完成", value: 32, color: "#0f9d58" },
	{ name: "已暂停", value: 6, color: "#d93025" },
];

// ══════════════════════════════════════════
// Screen 3: 质量信息与跟进措施
// ══════════════════════════════════════════
export const qualityKpis = [
	{ key: "newIssues", label: "新增质量问题", value: "23", trend: "+5", trendDir: "up" as const, tone: "danger" as const },
	{ key: "existingIssues", label: "现存质量问题", value: "47", tone: "warn" as const },
	{ key: "closedRate", label: "归零完成率", value: "68.3", unit: "%", trend: "+3.2%", trendDir: "up" as const },
	{ key: "techChanges", label: "技术状态变更", value: "18", sub: "本月新增" },
	{ key: "signRate", label: "文件签署完成率", value: "82", unit: "%" },
	{ key: "noPlan", label: "未提交归零计划", value: "8", tone: "danger" as const },
];

export const qualityByCategory = [
	{ name: "设计", value: 12, color: "#0b57d0" },
	{ name: "工艺", value: 8, color: "#00acc1" },
	{ name: "管理", value: 6, color: "#f9ab00" },
	{ name: "元器件", value: 5, color: "#d93025" },
	{ name: "操作", value: 4, color: "#0f9d58" },
	{ name: "外协外购", value: 3, color: "#7c3aed" },
	{ name: "软件", value: 6, color: "#94a3b8" },
	{ name: "其他", value: 3, color: "#64748b" },
];

export const qualityIssueList = [
	{ project: "核心器件研发", issue: "PCB布线设计缺陷", category: "设计", status: "未完成归零", days: 15 },
	{ project: "智能制造产线", issue: "焊接工艺参数偏差", category: "工艺", status: "已完成技术归零", days: 0 },
	{ project: "新能源工厂", issue: "控制器元器件批次不良", category: "元器件", status: "未完成归零", days: 22 },
	{ project: "供应链平台", issue: "接口协议不一致", category: "软件", status: "已完成管理归零", days: 0 },
	{ project: "制造协同平台", issue: "测试用例覆盖不足", category: "管理", status: "未完成归零", days: 8 },
];

export const techStateChanges = [
	{ name: "控制器硬件版本升级", project: "核心器件研发", status: "已签署", changeType: "Ⅰ" },
	{ name: "通信协议变更", project: "制造协同平台", status: "评审中", changeType: "Ⅱ" },
	{ name: "结构件材料替代", project: "新能源工厂", status: "已签署", changeType: "Ⅰ" },
	{ name: "软件架构调整", project: "数据治理工程", status: "未评审", changeType: "Ⅱ" },
];

// ══════════════════════════════════════════
// Screen 4: 技术状态与跟进
// ══════════════════════════════════════════
export const techStateKpis = [
	{ key: "techChanges", label: "技术状态变更数", value: "18", trend: "+4", trendDir: "up" as const, tone: "warn" as const },
	{ key: "signatureRate", label: "文件签署完成率", value: "82", unit: "%", trend: "+3.5%", trendDir: "up" as const },
	{ key: "pendingClosure", label: "未闭环项", value: "7", tone: "danger" as const },
	{ key: "measuresCoverage", label: "措施覆盖率", value: "76", unit: "%", trend: "+5.0%", trendDir: "up" as const },
	{ key: "categoryLevelOne", label: "Ⅰ类更改", value: "4", sub: "重点关注" },
	{ key: "unsignedCount", label: "未签署项", value: "2", tone: "warn" as const },
];

export const techByChangeType = [
	{ name: "Ⅰ类", value: 4, color: "#d93025" },
	{ name: "Ⅱ类", value: 9, color: "#0b57d0" },
	{ name: "Ⅲ类", value: 5, color: "#0f9d58" },
];

export const techSignatureStatus = [
	{ name: "已签署", value: 12, color: "#0f9d58" },
	{ name: "评审中", value: 4, color: "#f9ab00" },
	{ name: "未评审", value: 2, color: "#d93025" },
];

// ══════════════════════════════════════════
// Screen 5: 成本与预算控制
// ══════════════════════════════════════════
export const costKpis = [
	{ key: "annualBudget", label: "年度预算总额", value: "36.8", unit: "亿元" },
	{ key: "executed", label: "累计执行额", value: "26.1", unit: "亿元", trend: "+2.3亿", trendDir: "up" as const },
	{ key: "execRate", label: "预算执行率", value: "70.9", unit: "%", trend: "+4.2%", trendDir: "up" as const },
	{ key: "deviation", label: "预算偏差", value: "+0.72", unit: "亿", tone: "danger" as const },
	{ key: "efficiency", label: "预算效率", value: "0.93" },
	{ key: "burnRate", label: "燃尽率", value: "71", unit: "%" },
];

export const monthlySpend = [
	{ month: "1月", plan: 2.8, actual: 2.6 },
	{ month: "2月", plan: 3.1, actual: 3.3 },
	{ month: "3月", plan: 3.5, actual: 3.8 },
	{ month: "4月", plan: 3.2, actual: 3.0 },
	{ month: "5月", plan: 3.6, actual: 3.4 },
	{ month: "6月", plan: 3.8, actual: null },
];

export const deptBudget = [
	{ dept: "工程建设中心", budget: 9.5, actual: 8.6, rate: 90.5 },
	{ dept: "数字化事业部", budget: 6.0, actual: 5.2, rate: 86.7 },
	{ dept: "研发中心", budget: 8.2, actual: 6.8, rate: 82.9 },
	{ dept: "生产制造中心", budget: 10.0, actual: 10.4, rate: 104.0 },
	{ dept: "采购中心", budget: 3.1, actual: 2.8, rate: 90.3 },
];

// ══════════════════════════════════════════
// Screen 6: 风险与预警中心
// ══════════════════════════════════════════
export const riskKpis = [
	{ key: "totalRisks", label: "风险总数", value: "89", trend: "+7", trendDir: "up" as const },
	{ key: "highRisk", label: "高风险", value: "14", tone: "danger" as const },
	{ key: "midRisk", label: "中风险", value: "31", tone: "warn" as const },
	{ key: "closedRate", label: "风险闭环率", value: "62.4", unit: "%", trend: "+5.1%", trendDir: "up" as const },
	{ key: "changeFreq", label: "变更频率", value: "3.2", unit: "次/周", tone: "warn" as const },
	{ key: "avgCloseDay", label: "平均闭环天数", value: "18.5", unit: "天" },
];

export const riskMatrix = [
	{ probability: "高", impact: "高", count: 4, color: "#d93025" },
	{ probability: "高", impact: "中", count: 6, color: "#f9ab00" },
	{ probability: "高", impact: "低", count: 2, color: "#f9ab00" },
	{ probability: "中", impact: "高", count: 5, color: "#f9ab00" },
	{ probability: "中", impact: "中", count: 12, color: "#0b57d0" },
	{ probability: "中", impact: "低", count: 8, color: "#0f9d58" },
	{ probability: "低", impact: "高", count: 3, color: "#f9ab00" },
	{ probability: "低", impact: "中", count: 10, color: "#0f9d58" },
	{ probability: "低", impact: "低", count: 15, color: "#94a3b8" },
];

export const riskByCategory = [
	{ name: "技术风险", value: 28, color: "#0b57d0" },
	{ name: "进度风险", value: 22, color: "#d93025" },
	{ name: "成本风险", value: 15, color: "#f9ab00" },
	{ name: "外协风险", value: 12, color: "#00acc1" },
	{ name: "质量风险", value: 8, color: "#7c3aed" },
	{ name: "管理风险", value: 4, color: "#94a3b8" },
];

export const riskList = [
	{ name: "核心芯片供货延迟", project: "核心器件研发", level: "高", status: "跟进中", days: 35, measure: "启用备选供应商" },
	{ name: "施工许可证审批滞后", project: "新能源工厂", level: "高", status: "跟进中", days: 28, measure: "加速审批协调" },
	{ name: "关键技术人员流失", project: "制造协同平台", level: "中", status: "跟进中", days: 15, measure: "激励方案+备份人员" },
	{ name: "原材料价格上涨", project: "供应链平台", level: "中", status: "已闭环", days: 0, measure: "锁价合同" },
	{ name: "测试环境不稳定", project: "数据治理工程", level: "低", status: "跟进中", days: 8, measure: "环境容器化" },
];

// ══════════════════════════════════════════
// Execution drill support: 资源负载与协同
// ══════════════════════════════════════════
export const resourceKpis = [
	{ key: "totalPeople", label: "总投入人力", value: "342", unit: "人", trend: "+12", trendDir: "up" as const },
	{ key: "utilization", label: "资源利用率", value: "87.3", unit: "%", trend: "+2.1%", trendDir: "up" as const },
	{ key: "overloaded", label: "超负荷人员", value: "28", tone: "danger" as const },
	{ key: "idle", label: "空闲人员", value: "15", tone: "warn" as const },
	{ key: "crossDept", label: "跨部门协同", value: "23", sub: "协同任务数" },
	{ key: "avgHours", label: "人均周工时", value: "44.2", unit: "h" },
];

export const deptResourceLoad = [
	{ dept: "工程建设中心", total: 68, active: 62, overloaded: 8, idle: 2 },
	{ dept: "数字化事业部", total: 52, active: 48, overloaded: 5, idle: 3 },
	{ dept: "研发中心", total: 85, active: 78, overloaded: 12, idle: 4 },
	{ dept: "生产制造中心", total: 45, active: 40, overloaded: 3, idle: 2 },
	{ dept: "采购中心", total: 32, active: 28, overloaded: 0, idle: 4 },
	{ dept: "质量管理部", total: 28, active: 25, overloaded: 0, idle: 0 },
	{ dept: "信息化管理部", total: 32, active: 30, overloaded: 0, idle: 0 },
];

export const collaborationMatrix = [
	{ from: "研发中心", to: "生产制造中心", tasks: 8 },
	{ from: "研发中心", to: "质量管理部", tasks: 5 },
	{ from: "工程建设中心", to: "采购中心", tasks: 6 },
	{ from: "数字化事业部", to: "信息化管理部", tasks: 4 },
	{ from: "采购中心", to: "生产制造中心", tasks: 3 },
];

export const personWorkload = [
	{ name: "张工", dept: "数字化事业部", projects: 3, hours: 52, status: "超负荷" },
	{ name: "刘工", dept: "工程建设中心", projects: 2, hours: 48, status: "正常" },
	{ name: "陈工", dept: "研发中心", projects: 4, hours: 56, status: "超负荷" },
	{ name: "王工", dept: "信息化管理部", projects: 2, hours: 42, status: "正常" },
	{ name: "李工", dept: "采购中心", projects: 1, hours: 38, status: "正常" },
];
