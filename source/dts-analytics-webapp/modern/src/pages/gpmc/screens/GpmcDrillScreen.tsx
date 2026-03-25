import { type CSSProperties } from "react";
import { useGpmc, type GpmcBoardScreenId } from "../GpmcApp";
import {
	costKpis,
	delayTop10,
	deptBudget,
	deptResourceLoad,
	executionKpis,
	ganttTasks,
	monthlySpend,
	qualityIssueList,
	qualityKpis,
	riskByCategory,
	riskKpis,
	riskList,
	techByChangeType,
	techSignatureStatus,
	techStateChanges,
	techStateKpis,
} from "../mockData";

type SummaryCard = {
	label: string;
	value: string;
	hint?: string;
	tone?: "danger" | "warn" | "info" | "ok";
};

type TableColumn = {
	key: string;
	label: string;
	align?: "left" | "right" | "center";
};

type TableRow = Record<string, string | number>;

type SideStat = {
	label: string;
	value: string;
	color?: string;
};

type DrillConfig = {
	title: string;
	subtitle: string;
	summary: SummaryCard[];
	detailTitle: string;
	detailSubtitle: string;
	detailColumns: TableColumn[];
	detailRows: TableRow[];
	sideTitle: string;
	sideSubtitle: string;
	sideStats: SideStat[];
	supportTitle: string;
	supportSubtitle: string;
	supportColumns: TableColumn[];
	supportRows: TableRow[];
};

function getKpiHint(item: { trend?: string; sub?: string }) {
	return item.trend ?? item.sub;
}

const toneStyles: Record<NonNullable<SummaryCard["tone"]>, CSSProperties> = {
	danger: { color: "var(--gpmc-danger)" },
	warn: { color: "var(--gpmc-warn)" },
	info: { color: "var(--gpmc-primary)" },
	ok: { color: "var(--gpmc-success)" },
};

const DRILL_CONFIGS: Record<GpmcBoardScreenId, DrillConfig> = {
	execution: {
		title: "项目执行详情",
		subtitle: "执行层只读页，承载项目节点、延期排行和资源负载等执行控制信息。",
		summary: executionKpis.slice(0, 4).map((item) => ({
			label: item.label,
			value: `${item.value}${item.unit ?? ""}`,
			hint: item.trend ?? item.sub,
			tone: item.tone ?? "info",
		})),
		detailTitle: "关键任务与节点明细",
		detailSubtitle: "以任务和节点为粒度查看执行状态、完成率和风险等级。",
		detailColumns: [
			{ key: "task", label: "任务/节点" },
			{ key: "project", label: "所属项目" },
			{ key: "progress", label: "完成率", align: "right" },
			{ key: "risk", label: "风险等级", align: "center" },
		],
		detailRows: ganttTasks.map((item) => ({
			task: item.name,
			project: item.project,
			progress: `${item.progress}%`,
			risk: item.risk,
		})),
		sideTitle: "资源负载摘要",
		sideSubtitle: "资源负载不再独立成看板，收敛为执行层辅助视角。",
		sideStats: deptResourceLoad.slice(0, 6).map((item) => ({
			label: item.dept,
			value: `${item.active}/${item.total} 在办`,
			color: item.overloaded > 0 ? "var(--gpmc-danger)" : "var(--gpmc-primary)",
		})),
		supportTitle: "延期项目排行",
		supportSubtitle: "用于定位拖期项目、责任科室和风险等级。",
		supportColumns: [
			{ key: "project", label: "项目" },
			{ key: "delay", label: "延期天数", align: "right" },
			{ key: "dept", label: "责任科室" },
			{ key: "risk", label: "风险", align: "center" },
		],
		supportRows: delayTop10.map((item) => ({
			project: item.name,
			delay: `${item.delay} 天`,
			dept: item.dept,
			risk: item.risk,
		})),
	},
	quality: {
		title: "质量问题与跟进措施明细",
		subtitle: "执行层只读页，承载质量问题、闭环状态和配套跟进措施。",
		summary: qualityKpis.slice(0, 4).map((item) => ({
			label: item.label,
			value: `${item.value}${item.unit ?? ""}`,
			hint: item.trend ?? item.sub,
			tone: item.tone ?? "warn",
		})),
		detailTitle: "质量问题清单",
		detailSubtitle: "按问题项查看类别、状态和滞留天数。",
		detailColumns: [
			{ key: "project", label: "项目" },
			{ key: "issue", label: "问题" },
			{ key: "category", label: "分类" },
			{ key: "status", label: "状态" },
			{ key: "days", label: "滞留天数", align: "right" },
		],
		detailRows: qualityIssueList.map((item) => ({
			project: item.project,
			issue: item.issue,
			category: item.category,
			status: item.status,
			days: `${item.days} 天`,
		})),
		sideTitle: "质量闭环摘要",
		sideSubtitle: "快速识别闭环率、待归零项和高优未关问题。",
		sideStats: [
			{ label: "现存质量问题", value: "47", color: "var(--gpmc-danger)" },
			{ label: "归零完成率", value: "68.3%", color: "var(--gpmc-primary)" },
			{ label: "未提交归零计划", value: "8", color: "var(--gpmc-warn)" },
			{ label: "高优未关", value: "37", color: "var(--gpmc-danger)" },
		],
		supportTitle: "跟进措施摘要",
		supportSubtitle: "第一版保持只读，后续由真实措施表替换 demo 数据。",
		supportColumns: [
			{ key: "project", label: "项目" },
			{ key: "measure", label: "建议措施" },
			{ key: "owner", label: "责任界面" },
			{ key: "status", label: "闭环状态" },
		],
		supportRows: qualityIssueList.map((item) => ({
			project: item.project,
			measure: `针对“${item.issue}”补充专项措施与闭环交付物`,
			owner: `${item.category}专题组`,
			status: item.status,
		})),
	},
	"tech-state": {
		title: "技术状态与跟进明细",
		subtitle: "执行层只读页，承载技术状态变更、签署状态和跟进闭环信息。",
		summary: techStateKpis.slice(0, 4).map((item) => ({
			label: item.label,
			value: `${item.value}${item.unit ?? ""}`,
			hint: item.trend ?? item.sub,
			tone: item.tone ?? "info",
		})),
		detailTitle: "技术状态变更清单",
		detailSubtitle: "按技术状态对象查看变更类别、签署状态和项目归属。",
		detailColumns: [
			{ key: "name", label: "技术状态" },
			{ key: "project", label: "项目" },
			{ key: "type", label: "更改类别", align: "center" },
			{ key: "status", label: "签署状态" },
		],
		detailRows: techStateChanges.map((item) => ({
			name: item.name,
			project: item.project,
			type: item.changeType,
			status: item.status,
		})),
		sideTitle: "状态分布摘要",
		sideSubtitle: "变更类别与签署状态共同反映技术状态控制压力。",
		sideStats: [
			...techByChangeType.map((item) => ({
				label: `${item.name}更改`,
				value: `${item.value}`,
				color: item.color,
			})),
			...techSignatureStatus.map((item) => ({
				label: item.name,
				value: `${item.value}`,
				color: item.color,
			})),
		],
		supportTitle: "跟进措施摘要",
		supportSubtitle: "第一版用状态与签署动作模拟措施明细，后续替换为真实措施表。",
		supportColumns: [
			{ key: "name", label: "技术状态" },
			{ key: "measure", label: "跟进动作" },
			{ key: "project", label: "项目" },
			{ key: "status", label: "当前状态" },
		],
		supportRows: techStateChanges.map((item) => ({
			name: item.name,
			measure: item.status === "已签署" ? "进入签署归档与闭环确认" : "继续评审并补充签署资料",
			project: item.project,
			status: item.status,
		})),
	},
		cost: {
		title: "成本与预算控制明细",
		subtitle: "执行层只读页，承载部门预算执行、周期成本和偏差情况。",
			summary: costKpis.slice(0, 4).map((item) => ({
				label: item.label,
				value: `${item.value}${item.unit ?? ""}`,
				hint: getKpiHint(item),
				tone: item.tone ?? "info",
			})),
		detailTitle: "部门预算执行明细",
		detailSubtitle: "按责任科室查看预算、实际和执行率偏差。",
		detailColumns: [
			{ key: "dept", label: "责任科室" },
			{ key: "budget", label: "预算", align: "right" },
			{ key: "actual", label: "实际", align: "right" },
			{ key: "rate", label: "执行率", align: "right" },
		],
		detailRows: deptBudget.map((item) => ({
			dept: item.dept,
			budget: `${item.budget} 亿`,
			actual: `${item.actual} 亿`,
			rate: `${item.rate}%`,
		})),
		sideTitle: "成本控制摘要",
		sideSubtitle: "聚焦预算执行率、偏差额和燃尽率等关键控制指标。",
		sideStats: [
			{ label: "预算总额", value: "36.8 亿", color: "var(--gpmc-primary)" },
			{ label: "累计执行额", value: "26.1 亿", color: "var(--gpmc-text)" },
			{ label: "预算偏差", value: "+0.72 亿", color: "var(--gpmc-danger)" },
			{ label: "燃尽率", value: "71%", color: "var(--gpmc-warn)" },
		],
		supportTitle: "月度支出趋势",
		supportSubtitle: "按统计周期查看计划与实际支出的偏离情况。",
		supportColumns: [
			{ key: "month", label: "周期" },
			{ key: "plan", label: "计划支出", align: "right" },
			{ key: "actual", label: "实际支出", align: "right" },
			{ key: "gap", label: "偏差", align: "right" },
		],
		supportRows: monthlySpend.map((item) => ({
			month: item.month,
			plan: `${item.plan} 亿`,
			actual: item.actual == null ? "-" : `${item.actual} 亿`,
			gap: item.actual == null ? "-" : `${(item.actual - item.plan).toFixed(1)} 亿`,
		})),
	},
		risk: {
		title: "风险与预警中心明细",
		subtitle: "执行层只读页，承载风险清单、等级分布和跟进措施。",
			summary: riskKpis.slice(0, 4).map((item) => ({
				label: item.label,
				value: `${item.value}${item.unit ?? ""}`,
				hint: getKpiHint(item),
				tone: item.tone ?? "danger",
			})),
		detailTitle: "风险清单",
		detailSubtitle: "按风险对象查看等级、状态和滞留天数。",
		detailColumns: [
			{ key: "name", label: "风险" },
			{ key: "project", label: "项目" },
			{ key: "level", label: "等级", align: "center" },
			{ key: "status", label: "状态" },
			{ key: "days", label: "滞留天数", align: "right" },
		],
		detailRows: riskList.map((item) => ({
			name: item.name,
			project: item.project,
			level: item.level,
			status: item.status,
			days: `${item.days} 天`,
		})),
		sideTitle: "风险分类摘要",
		sideSubtitle: "用于快速判断高频风险类型与预警重心。",
		sideStats: riskByCategory.map((item) => ({
			label: item.name,
			value: `${item.value}`,
			color: item.color,
		})),
		supportTitle: "风险措施摘要",
		supportSubtitle: "第一版直接承载风险应对动作，后续替换成真实措施表。",
		supportColumns: [
			{ key: "project", label: "项目" },
			{ key: "risk", label: "风险" },
			{ key: "measure", label: "应对措施" },
			{ key: "status", label: "状态" },
		],
		supportRows: riskList.map((item) => ({
			project: item.project,
			risk: item.name,
			measure: item.measure,
			status: item.status,
		})),
	},
};

function renderCellValue(value: string | number) {
	if (typeof value === "string" && ["高", "高风险", "需关注"].includes(value)) {
		return <span className="gpmc__kpi-badge gpmc__kpi-badge--danger">{value}</span>;
	}
	if (typeof value === "string" && ["中", "中风险", "预警", "跟进中"].includes(value)) {
		return <span className="gpmc__kpi-badge gpmc__kpi-badge--warn">{value}</span>;
	}
	if (typeof value === "string" && ["低", "已闭环", "已签署", "正常"].includes(value)) {
		return <span className="gpmc__kpi-badge gpmc__kpi-badge--info">{value}</span>;
	}
	return value;
}

function DataTable({
	columns,
	rows,
}: {
	columns: TableColumn[];
	rows: TableRow[];
}) {
	return (
		<div className="gpmc__panel-body" style={{ padding: 0 }}>
			<table className="gpmc__table">
				<thead>
					<tr>
						{columns.map((column) => (
							<th key={column.key} style={column.align ? { textAlign: column.align } : undefined}>
								{column.label}
							</th>
						))}
					</tr>
				</thead>
				<tbody>
					{rows.map((row, index) => (
						<tr key={`${index}-${String(row[columns[0]?.key ?? index])}`}>
							{columns.map((column) => (
								<td key={column.key} style={column.align ? { textAlign: column.align } : undefined}>
									{renderCellValue(row[column.key] ?? "-")}
								</td>
							))}
						</tr>
					))}
				</tbody>
			</table>
		</div>
	);
}

export default function GpmcDrillScreen({ domain }: { domain: GpmcBoardScreenId }) {
	const { setLayer } = useGpmc();
	const config = DRILL_CONFIGS[domain];

	return (
		<div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
			<div className="gpmc__breadcrumb">
				<span className="gpmc__breadcrumb-link" onClick={() => setLayer("strategic")}>
					战略层
				</span>
				<span className="gpmc__breadcrumb-sep">/</span>
				<span className="gpmc__breadcrumb-link" onClick={() => setLayer("control")}>
					管控层
				</span>
				<span className="gpmc__breadcrumb-sep">/</span>
				<span className="gpmc__breadcrumb-current">执行层</span>
			</div>

			<div className="gpmc__panel">
				<div className="gpmc__panel-body" style={{ padding: "18px 22px" }}>
					<div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16 }}>
						<div>
							<div className="gpmc__panel-title" style={{ marginBottom: 6 }}>
								{config.title}
							</div>
							<div className="gpmc__panel-subtitle">{config.subtitle}</div>
						</div>
						<span className="gpmc__kpi-badge gpmc__kpi-badge--info">只读执行页</span>
					</div>
				</div>
			</div>

			<div className="gpmc__kpi-row" style={{ gridTemplateColumns: "repeat(4, minmax(0, 1fr))" }}>
				{config.summary.map((item) => (
					<div key={item.label} className="gpmc__kpi-card">
						<div className="gpmc__kpi-label">{item.label}</div>
						<div className="gpmc__kpi-value" style={item.tone ? toneStyles[item.tone] : undefined}>
							{item.value}
						</div>
						<div className="gpmc__kpi-sub">{item.hint ?? "执行层明细视图"}</div>
					</div>
				))}
			</div>

			<div className="gpmc__two-col" style={{ gridTemplateColumns: "1.5fr 0.9fr" }}>
				<div className="gpmc__panel">
					<div className="gpmc__panel-header">
						<div>
							<div className="gpmc__panel-title">{config.detailTitle}</div>
							<div className="gpmc__panel-subtitle">{config.detailSubtitle}</div>
						</div>
					</div>
					<DataTable columns={config.detailColumns} rows={config.detailRows} />
				</div>

				<div className="gpmc__panel">
					<div className="gpmc__panel-header">
						<div>
							<div className="gpmc__panel-title">{config.sideTitle}</div>
							<div className="gpmc__panel-subtitle">{config.sideSubtitle}</div>
						</div>
					</div>
					<div className="gpmc__panel-body" style={{ display: "flex", flexDirection: "column", gap: 10 }}>
						{config.sideStats.map((item) => (
							<div
								key={`${item.label}-${item.value}`}
								style={{
									display: "flex",
									alignItems: "center",
									justifyContent: "space-between",
									gap: 12,
									padding: "10px 12px",
									borderRadius: 10,
									border: "1px solid var(--gpmc-line)",
									background: "var(--gpmc-panel-2)",
								}}
							>
								<span style={{ fontSize: 13 }}>{item.label}</span>
								<span style={{ fontSize: 16, fontWeight: 700, color: item.color ?? "var(--gpmc-text)" }}>
									{item.value}
								</span>
							</div>
						))}
					</div>
				</div>
			</div>

			<div className="gpmc__panel">
				<div className="gpmc__panel-header">
					<div>
						<div className="gpmc__panel-title">{config.supportTitle}</div>
						<div className="gpmc__panel-subtitle">{config.supportSubtitle}</div>
					</div>
				</div>
				<DataTable columns={config.supportColumns} rows={config.supportRows} />
			</div>
		</div>
	);
}
