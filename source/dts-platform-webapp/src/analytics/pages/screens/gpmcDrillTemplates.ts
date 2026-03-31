// @ts-nocheck — drill-down screen templates for GPMC execution layer
import type { ScreenComponent, ScreenGlobalVariable } from './types';
import type { ScreenTemplate } from './screenTemplates';
import { SCREEN_SCHEMA_VERSION } from './specV2';
import {
	ganttTasks,
	deptResourceLoad,
	delayTop10,
	qualityIssueList,
	techStateChanges,
	techByChangeType,
	techSignatureStatus,
	deptBudget,
	monthlySpend,
	riskList,
	riskByCategory,
} from '../gpmc/mockData';

// ── Style constants ──────────────────────────────────────
const SCREEN_WIDTH = 1920;
const SCREEN_HEIGHT = 1080;
const BG = '#eaf2fb';
const HEADER_BAR_BG = '#044B8C';
const TITLE_COLOR = '#0f172a';
const BODY_COLOR = '#334155';
const TABLE_HEADER_BG = '#dbe9f6';
const TABLE_BODY_BG = 'rgba(255, 255, 255, 0.98)';
const TABLE_EVEN_ROW_BG = 'rgba(241, 245, 249, 0.98)';
const PANEL_BORDER = 'rgba(148, 163, 184, 0.24)';
const SUCCESS = '#1e8449';
const WARNING = '#d4850a';
const DANGER = '#c0392b';
const ACCENT = '#3b82f6';

// ── Helpers ──────────────────────────────────────────────

function createComponent(
	id: string,
	type: ScreenComponent['type'],
	name: string,
	x: number, y: number, width: number, height: number,
	zIndex: number,
	config: Record<string, unknown>,
): ScreenComponent {
	return { id, type, name, x, y, width, height, zIndex, locked: false, visible: true, config };
}

function headerBar(id: string): ScreenComponent {
	return createComponent(id, 'shape', id, 0, 0, SCREEN_WIDTH, 56, 5, {
		shapeType: 'rect', fillColor: HEADER_BAR_BG, borderColor: HEADER_BAR_BG, borderWidth: 0, radius: 0,
	});
}

function titleText(id: string, text: string): ScreenComponent {
	return createComponent(id, 'title', text, 20, 12, 500, 32, 60, {
		text, fontSize: 22, fontWeight: '700', color: '#ffffff', textAlign: 'flex-start',
	});
}

function badgeComp(id: string): ScreenComponent {
	return createComponent(id, 'title', '只读执行页', 1760, 14, 140, 28, 60, {
		text: '只读执行页', fontSize: 13, fontWeight: '600', color: '#ffffff',
		textAlign: 'center', backgroundColor: 'rgba(255, 255, 255, 0.18)', borderRadius: 14,
	});
}

function numberCard(
	id: string, title: string, value: string, unit: string, x: number, w: number, valueColor = ACCENT,
): ScreenComponent {
	return createComponent(id, 'number-card', title, x, 70, w, 90, 10, {
		title, value, unit, precision: value.includes('.') ? 1 : 0,
		titleColor: TITLE_COLOR, valueColor, backgroundColor: '#ffffff', borderRadius: 12, fontSize: 36,
	});
}

/** 匹配 gpmcTemplates.ts 的 createTable 格式：header + data 二维数组 */
function drillTable(
	id: string, title: string,
	x: number, y: number, w: number, h: number,
	header: string[],
	data: Array<Array<string | number>>,
): ScreenComponent {
	return createComponent(id, 'table', title, x, y, w, h, 15, {
		title,
		header,
		data,
		fontSize: 16,
		headerAlign: 'center',
		headerColor: TITLE_COLOR,
		headerBackground: TABLE_HEADER_BG,
		bodyColor: BODY_COLOR,
		bodyBackground: TABLE_BODY_BG,
		oddRowBackground: TABLE_BODY_BG,
		evenRowBackground: TABLE_EVEN_ROW_BG,
		borderColor: PANEL_BORDER,
		enableSort: true,
		enablePagination: true,
		pageSize: 8,
		freezeHeader: true,
	});
}

// ── Global variables ─────────────────────────────────────

const drillGlobalVariables: ScreenGlobalVariable[] = [
	{ key: 'dateFrom', label: '开始日期', type: 'date' },
	{ key: 'dateTo', label: '结束日期', type: 'date' },
	{ key: 'projectNo', label: '项目编号', type: 'string' },
	{ key: 'deptId', label: '事业部/科室', type: 'string' },
];

// ══════════════════════════════════════════════════════════
// Template 1: 执行层-项目执行详情
// ══════════════════════════════════════════════════════════

const executionDrillTemplate: ScreenTemplate = {
	id: 'gpmc-drill-execution',
	name: '执行层-项目执行详情',
	description: '项目执行监控钻取页面，展示关键任务节点、资源负载及延期项目排行。',
	thumbnail: '📋',
	category: 'project-management',
	tags: ['gpmc', 'drill', 'execution'],
	config: {
		name: '执行层-项目执行详情',
		description: '项目执行监控钻取页面',
		width: SCREEN_WIDTH, height: SCREEN_HEIGHT,
		backgroundColor: BG, theme: 'enterprise-light' as const,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		globalVariables: drillGlobalVariables,
		components: [
			headerBar('drill-exec-header-bar'),
			titleText('drill-exec-title', '执行层-项目执行详情'),
			badgeComp('drill-exec-badge'),
			numberCard('drill-exec-kpi-1', '整体完成率', '72.4', '%', 20, 456, ACCENT),
			numberCard('drill-exec-kpi-2', '里程碑达成率', '68', '%', 490, 456, WARNING),
			numberCard('drill-exec-kpi-3', '关键路径偏差', '4.6', '%', 960, 456, WARNING),
			numberCard('drill-exec-kpi-4', '延期任务数', '37', '', 1430, 470, DANGER),
			drillTable('drill-exec-detail-table', '关键任务与节点明细',
				20, 175, 1145, 420,
				['任务/节点', '所属项目', '完成率', '风险等级'],
				ganttTasks.map(t => [t.name, t.project, `${t.progress}%`, t.risk]),
			),
			drillTable('drill-exec-side-panel', '资源负载摘要',
				1185, 175, 715, 420,
				['部门', '在岗/总人力', '超负荷', '空闲'],
				deptResourceLoad.slice(0, 6).map(d => [d.dept, `${d.active}/${d.total}`, d.overloaded, d.idle]),
			),
			drillTable('drill-exec-support-table', '延期项目排行',
				20, 610, 1880, 440,
				['项目', '延期天数', '责任科室', '风险'],
				delayTop10.map(d => [d.name, `${d.delay} 天`, d.dept, d.risk]),
			),
		],
	},
};

// ══════════════════════════════════════════════════════════
// Template 2: 执行层-质量问题与跟进明细
// ══════════════════════════════════════════════════════════

const qualityDrillTemplate: ScreenTemplate = {
	id: 'gpmc-drill-quality',
	name: '执行层-质量问题与跟进明细',
	description: '质量问题钻取页面，展示质量问题清单、闭环摘要及跟进措施。',
	thumbnail: '🔍',
	category: 'project-management',
	tags: ['gpmc', 'drill', 'quality'],
	config: {
		name: '执行层-质量问题与跟进明细',
		description: '质量问题钻取页面',
		width: SCREEN_WIDTH, height: SCREEN_HEIGHT,
		backgroundColor: BG, theme: 'enterprise-light' as const,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		globalVariables: drillGlobalVariables,
		components: [
			headerBar('drill-quality-header-bar'),
			titleText('drill-quality-title', '执行层-质量问题与跟进明细'),
			badgeComp('drill-quality-badge'),
			numberCard('drill-quality-kpi-1', '新增质量问题', '23', '', 20, 456, DANGER),
			numberCard('drill-quality-kpi-2', '现存质量问题', '47', '', 490, 456, WARNING),
			numberCard('drill-quality-kpi-3', '归零完成率', '68.3', '%', 960, 456, ACCENT),
			numberCard('drill-quality-kpi-4', '未闭环待归零', '8', '', 1430, 470, DANGER),
			drillTable('drill-quality-detail-table', '质量问题清单',
				20, 175, 1145, 420,
				['项目', '问题', '分类', '状态', '滞留天数'],
				qualityIssueList.map(q => [q.project, q.issue, q.category, q.status, `${q.days} 天`]),
			),
			drillTable('drill-quality-side-panel', '质量闭环摘要',
				1185, 175, 715, 420,
				['指标', '数值'],
				[
					['现存质量问题', '47'],
					['归零完成率', '68.3%'],
					['未提交归零计划', '8'],
					['高优未关闭', '37'],
				],
			),
			drillTable('drill-quality-support-table', '跟进措施摘要',
				20, 610, 1880, 440,
				['项目', '建议措施', '责任界面', '闭环状态'],
				qualityIssueList.map(q => [q.project, `针对"${q.issue}"补充专项措施`, `${q.category}专题组`, q.status]),
			),
		],
	},
};

// ══════════════════════════════════════════════════════════
// Template 3: 执行层-技术状态与跟进明细
// ══════════════════════════════════════════════════════════

const techStateDrillTemplate: ScreenTemplate = {
	id: 'gpmc-drill-tech-state',
	name: '执行层-技术状态与跟进明细',
	description: '技术状态变更钻取页面，展示变更清单、状态分布及跟进措施。',
	thumbnail: '🔧',
	category: 'project-management',
	tags: ['gpmc', 'drill', 'tech-state'],
	config: {
		name: '执行层-技术状态与跟进明细',
		description: '技术状态变更钻取页面',
		width: SCREEN_WIDTH, height: SCREEN_HEIGHT,
		backgroundColor: BG, theme: 'enterprise-light' as const,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		globalVariables: drillGlobalVariables,
		components: [
			headerBar('drill-tech-header-bar'),
			titleText('drill-tech-title', '执行层-技术状态与跟进明细'),
			badgeComp('drill-tech-badge'),
			numberCard('drill-tech-kpi-1', '变更总数', '18', '', 20, 456, ACCENT),
			numberCard('drill-tech-kpi-2', '已签署', '12', '', 490, 456, SUCCESS),
			numberCard('drill-tech-kpi-3', '待签署', '4', '', 960, 456, WARNING),
			numberCard('drill-tech-kpi-4', '评审中', '2', '', 1430, 470, ACCENT),
			drillTable('drill-tech-detail-table', '技术状态变更清单',
				20, 175, 1145, 420,
				['技术状态', '项目', '更改类别', '签署状态'],
				techStateChanges.map(t => [t.name, t.project, t.changeType, t.status]),
			),
			drillTable('drill-tech-side-panel', '状态分布摘要',
				1185, 175, 715, 420,
				['分类', '数量'],
				[
					...techByChangeType.map(t => [`更改类别: ${t.name}`, t.value]),
					...techSignatureStatus.map(t => [`签署: ${t.name}`, t.value]),
				],
			),
			drillTable('drill-tech-support-table', '跟进措施摘要',
				20, 610, 1880, 440,
				['技术状态', '跟进动作', '项目', '当前状态'],
				techStateChanges.map(t => [
					t.name,
					t.status === '已签署' ? '进入签署归档与闭环确认' : '继续评审并补充签署资料',
					t.project,
					t.status,
				]),
			),
		],
	},
};

// ══════════════════════════════════════════════════════════
// Template 4: 执行层-成本与预算控制明细
// ══════════════════════════════════════════════════════════

const costDrillTemplate: ScreenTemplate = {
	id: 'gpmc-drill-cost',
	name: '执行层-成本与预算控制明细',
	description: '成本预算钻取页面，展示部门预算执行、成本控制摘要及月度支出趋势。',
	thumbnail: '💰',
	category: 'project-management',
	tags: ['gpmc', 'drill', 'cost'],
	config: {
		name: '执行层-成本与预算控制明细',
		description: '成本预算钻取页面',
		width: SCREEN_WIDTH, height: SCREEN_HEIGHT,
		backgroundColor: BG, theme: 'enterprise-light' as const,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		globalVariables: drillGlobalVariables,
		components: [
			headerBar('drill-cost-header-bar'),
			titleText('drill-cost-title', '执行层-成本与预算控制明细'),
			badgeComp('drill-cost-badge'),
			numberCard('drill-cost-kpi-1', '预算总额', '36.8', '亿', 20, 456, ACCENT),
			numberCard('drill-cost-kpi-2', '累计执行', '26.1', '亿', 490, 456, ACCENT),
			numberCard('drill-cost-kpi-3', '预算偏差', '+0.72', '亿', 960, 456, DANGER),
			numberCard('drill-cost-kpi-4', '燃尽率', '71', '%', 1430, 470, WARNING),
			drillTable('drill-cost-detail-table', '部门预算执行明细',
				20, 175, 1145, 420,
				['责任科室', '预算 (亿)', '实际 (亿)', '执行率 (%)'],
				deptBudget.map(d => [d.dept, d.budget, d.actual, `${d.rate}%`]),
			),
			drillTable('drill-cost-side-panel', '成本控制摘要',
				1185, 175, 715, 420,
				['指标', '数值'],
				[
					['预算总额', '36.8 亿'],
					['累计执行额', '26.1 亿'],
					['预算偏差', '+0.72 亿'],
					['燃尽率', '71%'],
				],
			),
			drillTable('drill-cost-support-table', '月度支出趋势',
				20, 610, 1880, 440,
				['周期', '计划支出 (亿)', '实际支出 (亿)', '偏差 (亿)'],
				monthlySpend.map(m => [
					m.month,
					m.plan,
					m.actual ?? '-',
					m.actual != null ? (m.actual - m.plan).toFixed(1) : '-',
				]),
			),
		],
	},
};

// ══════════════════════════════════════════════════════════
// Template 5: 执行层-风险与预警中心明细
// ══════════════════════════════════════════════════════════

const riskDrillTemplate: ScreenTemplate = {
	id: 'gpmc-drill-risk',
	name: '执行层-风险与预警中心明细',
	description: '风险预警钻取页面，展示风险清单、风险分类摘要及应对措施。',
	thumbnail: '⚠️',
	category: 'project-management',
	tags: ['gpmc', 'drill', 'risk'],
	config: {
		name: '执行层-风险与预警中心明细',
		description: '风险预警钻取页面',
		width: SCREEN_WIDTH, height: SCREEN_HEIGHT,
		backgroundColor: BG, theme: 'enterprise-light' as const,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		globalVariables: drillGlobalVariables,
		components: [
			headerBar('drill-risk-header-bar'),
			titleText('drill-risk-title', '执行层-风险与预警中心明细'),
			badgeComp('drill-risk-badge'),
			numberCard('drill-risk-kpi-1', '风险总数', '89', '', 20, 456, ACCENT),
			numberCard('drill-risk-kpi-2', '高风险', '14', '', 490, 456, DANGER),
			numberCard('drill-risk-kpi-3', '中风险', '31', '', 960, 456, WARNING),
			numberCard('drill-risk-kpi-4', '低风险', '44', '', 1430, 470, SUCCESS),
			drillTable('drill-risk-detail-table', '风险清单',
				20, 175, 1145, 420,
				['风险', '项目', '等级', '状态', '滞留天数'],
				riskList.map(r => [r.name, r.project, r.level, r.status, `${r.days} 天`]),
			),
			drillTable('drill-risk-side-panel', '风险分类摘要',
				1185, 175, 715, 420,
				['分类', '数量'],
				riskByCategory.map(r => [r.name, r.value]),
			),
			drillTable('drill-risk-support-table', '风险措施摘要',
				20, 610, 1880, 440,
				['项目', '风险', '应对措施', '状态'],
				riskList.map(r => [r.project, r.name, r.measure, r.status]),
			),
		],
	},
};

// ── Export ────────────────────────────────────────────────

export const gpmcDrillTemplates: ScreenTemplate[] = [
	executionDrillTemplate,
	qualityDrillTemplate,
	techStateDrillTemplate,
	costDrillTemplate,
	riskDrillTemplate,
];
