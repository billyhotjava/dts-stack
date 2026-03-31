// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
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
const PANEL_BG = 'rgba(255, 255, 255, 0.96)';
const PANEL_BORDER = 'rgba(148, 163, 184, 0.24)';
const TITLE_COLOR = '#0f172a';
const SUBTITLE_COLOR = '#334155';
const TABLE_HEADER_BG = '#dbe9f6';
const TABLE_BODY_BG = 'rgba(255, 255, 255, 0.98)';
const TABLE_EVEN_ROW_BG = 'rgba(241, 245, 249, 0.98)';
const SUCCESS = '#1e8449';
const WARNING = '#d4850a';
const DANGER = '#c0392b';
const ACCENT = '#3b82f6';

// ── Helpers ──────────────────────────────────────────────

function createComponent(
	id: string,
	type: ScreenComponent['type'],
	name: string,
	x: number,
	y: number,
	width: number,
	height: number,
	zIndex: number,
	config: Record<string, unknown>,
): ScreenComponent {
	return {
		id,
		type,
		name,
		x,
		y,
		width,
		height,
		zIndex,
		locked: false,
		visible: true,
		config,
	};
}

function headerBar(id: string): ScreenComponent {
	return createComponent(id, 'shape', id, 0, 0, SCREEN_WIDTH, 56, 5, {
		shapeType: 'rect',
		fillColor: HEADER_BAR_BG,
		borderColor: HEADER_BAR_BG,
		borderWidth: 0,
		radius: 0,
	});
}

function titleText(id: string, text: string): ScreenComponent {
	return createComponent(id, 'title', text, 20, 12, 500, 32, 60, {
		text,
		fontSize: 22,
		fontWeight: '700',
		color: '#ffffff',
		textAlign: 'flex-start',
	});
}

function badge(id: string): ScreenComponent {
	return createComponent(id, 'title', '只读执行页', 1760, 14, 140, 28, 60, {
		text: '只读执行页',
		fontSize: 13,
		fontWeight: '600',
		color: '#ffffff',
		textAlign: 'center',
		backgroundColor: 'rgba(255, 255, 255, 0.18)',
		borderRadius: 14,
	});
}

function numberCard(
	id: string,
	title: string,
	value: string,
	unit: string,
	x: number,
	w: number,
	valueColor = ACCENT,
): ScreenComponent {
	const precision = value.includes('.') ? 1 : 0;
	return createComponent(id, 'number-card', title, x, 70, w, 90, 10, {
		title,
		value,
		unit,
		precision,
		titleColor: TITLE_COLOR,
		valueColor,
		backgroundColor: '#ffffff',
		borderRadius: 12,
		fontSize: 36,
	});
}

function table(
	id: string,
	name: string,
	x: number,
	y: number,
	w: number,
	h: number,
	columns: Array<{ key: string; title: string; width: number }>,
	staticData: unknown,
): ScreenComponent {
	return {
		id,
		type: 'table',
		name,
		x,
		y,
		width: w,
		height: h,
		zIndex: 15,
		locked: false,
		visible: true,
		config: {
			columns,
			headerBg: TABLE_HEADER_BG,
			bodyBg: TABLE_BODY_BG,
			evenRowBg: TABLE_EVEN_ROW_BG,
			borderRadius: 12,
			fontSize: 13,
			showPagination: false,
		},
		dataSource: { type: 'static' as const, staticData },
	};
}

// ── Global variables (shared across all 5 templates) ────

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
		width: SCREEN_WIDTH,
		height: SCREEN_HEIGHT,
		backgroundColor: BG,
		theme: 'enterprise-light' as const,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		globalVariables: drillGlobalVariables,
		components: [
			headerBar('drill-exec-header-bar'),
			titleText('drill-exec-title', '执行层-项目执行详情'),
			badge('drill-exec-badge'),
			numberCard('drill-exec-kpi-1', '整体完成率', '72.4', '%', 20, 456, ACCENT),
			numberCard('drill-exec-kpi-2', '里程碑达成率', '68', '%', 490, 456, WARNING),
			numberCard('drill-exec-kpi-3', '关键路径偏差', '4.6', '%', 960, 456, WARNING),
			numberCard('drill-exec-kpi-4', '延期任务数', '37', '', 1430, 470, DANGER),
			table(
				'drill-exec-detail-table',
				'关键任务与节点明细',
				20, 175, 1145, 420,
				[
					{ key: 'name', title: '任务/节点', width: 280 },
					{ key: 'project', title: '所属项目', width: 280 },
					{ key: 'progress', title: '完成率', width: 200 },
					{ key: 'risk', title: '风险等级', width: 200 },
				],
				ganttTasks,
			),
			table(
				'drill-exec-side-panel',
				'资源负载摘要',
				1185, 175, 715, 420,
				[
					{ key: 'dept', title: '部门', width: 200 },
					{ key: 'total', title: '总人力', width: 100 },
					{ key: 'active', title: '在岗', width: 100 },
					{ key: 'overloaded', title: '超负荷', width: 100 },
					{ key: 'idle', title: '空闲', width: 100 },
				],
				deptResourceLoad,
			),
			table(
				'drill-exec-support-table',
				'延期项目排行',
				20, 610, 1880, 440,
				[
					{ key: 'name', title: '项目', width: 500 },
					{ key: 'delay', title: '延期天数', width: 300 },
					{ key: 'dept', title: '责任科室', width: 400 },
					{ key: 'risk', title: '风险', width: 300 },
				],
				delayTop10,
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
		width: SCREEN_WIDTH,
		height: SCREEN_HEIGHT,
		backgroundColor: BG,
		theme: 'enterprise-light' as const,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		globalVariables: drillGlobalVariables,
		components: [
			headerBar('drill-quality-header-bar'),
			titleText('drill-quality-title', '执行层-质量问题与跟进明细'),
			badge('drill-quality-badge'),
			numberCard('drill-quality-kpi-1', '新增质量问题', '23', '', 20, 456, DANGER),
			numberCard('drill-quality-kpi-2', '现存质量问题', '47', '', 490, 456, WARNING),
			numberCard('drill-quality-kpi-3', '归零完成率', '68.3', '%', 960, 456, ACCENT),
			numberCard('drill-quality-kpi-4', '未闭环待归零', '8', '', 1430, 470, DANGER),
			table(
				'drill-quality-detail-table',
				'质量问题清单',
				20, 175, 1145, 420,
				[
					{ key: 'project', title: '项目', width: 200 },
					{ key: 'issue', title: '问题', width: 280 },
					{ key: 'category', title: '分类', width: 160 },
					{ key: 'status', title: '状态', width: 200 },
					{ key: 'days', title: '滞留天数', width: 140 },
				],
				qualityIssueList,
			),
			table(
				'drill-quality-side-panel',
				'质量闭环摘要',
				1185, 175, 715, 420,
				[
					{ key: 'label', title: '指标', width: 350 },
					{ key: 'value', title: '数值', width: 300 },
				],
				[
					{ label: '现存质量问题', value: '47' },
					{ label: '归零完成率', value: '68.3%' },
					{ label: '未提交归零计划', value: '8' },
					{ label: '高优未关闭', value: '37' },
				],
			),
			table(
				'drill-quality-support-table',
				'跟进措施摘要',
				20, 610, 1880, 440,
				[
					{ key: 'project', title: '项目', width: 400 },
					{ key: 'measure', title: '建议措施', width: 500 },
					{ key: 'owner', title: '责任界面', width: 400 },
					{ key: 'status', title: '闭环状态', width: 300 },
				],
				[
					{ project: '核心器件研发', measure: '重新评审PCB布线方案', owner: '研发中心', status: '跟进中' },
					{ project: '新能源工厂', measure: '更换元器件供应商批次', owner: '采购中心', status: '跟进中' },
					{ project: '制造协同平台', measure: '补充测试用例并回归', owner: '质量管理部', status: '待启动' },
					{ project: '智能制造产线', measure: '修订焊接工艺规范', owner: '生产制造中心', status: '已闭环' },
					{ project: '供应链平台', measure: '统一接口协议版本', owner: '数字化事业部', status: '已闭环' },
				],
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
		width: SCREEN_WIDTH,
		height: SCREEN_HEIGHT,
		backgroundColor: BG,
		theme: 'enterprise-light' as const,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		globalVariables: drillGlobalVariables,
		components: [
			headerBar('drill-tech-header-bar'),
			titleText('drill-tech-title', '执行层-技术状态与跟进明细'),
			badge('drill-tech-badge'),
			numberCard('drill-tech-kpi-1', '变更总数', '18', '', 20, 456, ACCENT),
			numberCard('drill-tech-kpi-2', '已签署', '12', '', 490, 456, SUCCESS),
			numberCard('drill-tech-kpi-3', '待签署', '4', '', 960, 456, WARNING),
			numberCard('drill-tech-kpi-4', '评审中', '2', '', 1430, 470, ACCENT),
			table(
				'drill-tech-detail-table',
				'技术状态变更清单',
				20, 175, 1145, 420,
				[
					{ key: 'name', title: '技术状态', width: 300 },
					{ key: 'project', title: '项目', width: 280 },
					{ key: 'changeType', title: '更改类别', width: 200 },
					{ key: 'status', title: '签署状态', width: 200 },
				],
				techStateChanges,
			),
			table(
				'drill-tech-side-panel',
				'状态分布摘要',
				1185, 175, 715, 420,
				[
					{ key: 'label', title: '分类', width: 350 },
					{ key: 'value', title: '数量', width: 300 },
				],
				[
					...techByChangeType.map(item => ({ label: `更改类别: ${item.name}`, value: String(item.value) })),
					...techSignatureStatus.map(item => ({ label: `签署状态: ${item.name}`, value: String(item.value) })),
				],
			),
			table(
				'drill-tech-support-table',
				'跟进措施摘要',
				20, 610, 1880, 440,
				[
					{ key: 'techState', title: '技术状态', width: 400 },
					{ key: 'action', title: '跟进动作', width: 500 },
					{ key: 'project', title: '项目', width: 400 },
					{ key: 'status', title: '当前状态', width: 300 },
				],
				[
					{ techState: '控制器硬件版本升级', action: '完成签署后进入实施阶段', project: '核心器件研发', status: '已签署' },
					{ techState: '通信协议变更', action: '组织专家评审会', project: '制造协同平台', status: '评审中' },
					{ techState: '结构件材料替代', action: '完成验证测试报告', project: '新能源工厂', status: '已签署' },
					{ techState: '软件架构调整', action: '提交变更申请并启动评审', project: '数据治理工程', status: '未评审' },
				],
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
		width: SCREEN_WIDTH,
		height: SCREEN_HEIGHT,
		backgroundColor: BG,
		theme: 'enterprise-light' as const,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		globalVariables: drillGlobalVariables,
		components: [
			headerBar('drill-cost-header-bar'),
			titleText('drill-cost-title', '执行层-成本与预算控制明细'),
			badge('drill-cost-badge'),
			numberCard('drill-cost-kpi-1', '预算总额', '36.8', '亿', 20, 456, ACCENT),
			numberCard('drill-cost-kpi-2', '累计执行', '26.1', '亿', 490, 456, ACCENT),
			numberCard('drill-cost-kpi-3', '预算偏差', '+0.72', '亿', 960, 456, DANGER),
			numberCard('drill-cost-kpi-4', '燃尽率', '71', '%', 1430, 470, WARNING),
			table(
				'drill-cost-detail-table',
				'部门预算执行明细',
				20, 175, 1145, 420,
				[
					{ key: 'dept', title: '责任科室', width: 280 },
					{ key: 'budget', title: '预算', width: 200 },
					{ key: 'actual', title: '实际', width: 200 },
					{ key: 'rate', title: '执行率', width: 200 },
				],
				deptBudget,
			),
			table(
				'drill-cost-side-panel',
				'成本控制摘要',
				1185, 175, 715, 420,
				[
					{ key: 'label', title: '指标', width: 350 },
					{ key: 'value', title: '数值', width: 300 },
				],
				[
					{ label: '预算总额', value: '36.8 亿' },
					{ label: '累计执行', value: '26.1 亿' },
					{ label: '预算偏差', value: '+0.72 亿' },
					{ label: '燃尽率', value: '71%' },
				],
			),
			table(
				'drill-cost-support-table',
				'月度支出趋势',
				20, 610, 1880, 440,
				[
					{ key: 'month', title: '周期', width: 300 },
					{ key: 'plan', title: '计划支出', width: 400 },
					{ key: 'actual', title: '实际支出', width: 400 },
					{ key: 'deviation', title: '偏差', width: 400 },
				],
				monthlySpend.map(item => ({
					...item,
					deviation: item.actual != null ? ((item.actual - item.plan).toFixed(1)) : '-',
				})),
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
		width: SCREEN_WIDTH,
		height: SCREEN_HEIGHT,
		backgroundColor: BG,
		theme: 'enterprise-light' as const,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		globalVariables: drillGlobalVariables,
		components: [
			headerBar('drill-risk-header-bar'),
			titleText('drill-risk-title', '执行层-风险与预警中心明细'),
			badge('drill-risk-badge'),
			numberCard('drill-risk-kpi-1', '风险总数', '89', '', 20, 456, ACCENT),
			numberCard('drill-risk-kpi-2', '高风险', '14', '', 490, 456, DANGER),
			numberCard('drill-risk-kpi-3', '中风险', '31', '', 960, 456, WARNING),
			numberCard('drill-risk-kpi-4', '低风险', '44', '', 1430, 470, SUCCESS),
			table(
				'drill-risk-detail-table',
				'风险清单',
				20, 175, 1145, 420,
				[
					{ key: 'name', title: '风险', width: 280 },
					{ key: 'project', title: '项目', width: 220 },
					{ key: 'level', title: '等级', width: 140 },
					{ key: 'status', title: '状态', width: 160 },
					{ key: 'days', title: '滞留天数', width: 140 },
				],
				riskList,
			),
			table(
				'drill-risk-side-panel',
				'风险分类摘要',
				1185, 175, 715, 420,
				[
					{ key: 'name', title: '分类', width: 350 },
					{ key: 'value', title: '数量', width: 300 },
				],
				riskByCategory.map(item => ({ name: item.name, value: String(item.value) })),
			),
			table(
				'drill-risk-support-table',
				'风险措施摘要',
				20, 610, 1880, 440,
				[
					{ key: 'project', title: '项目', width: 400 },
					{ key: 'name', title: '风险', width: 450 },
					{ key: 'measure', title: '应对措施', width: 500 },
					{ key: 'status', title: '状态', width: 300 },
				],
				riskList,
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
