// @ts-nocheck — drill-down screen templates for GPMC execution layer
// 所有数据内联，不依赖 mockData.ts
import type { ScreenComponent, ScreenGlobalVariable } from './types';
import type { ScreenTemplate } from './screenTemplates';
import { SCREEN_SCHEMA_VERSION } from './specV2';

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
	id: string, type: ScreenComponent['type'], name: string,
	x: number, y: number, width: number, height: number,
	zIndex: number, config: Record<string, unknown>,
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

function drillTable(
	id: string, title: string,
	x: number, y: number, w: number, h: number,
	header: string[], data: Array<Array<string | number>>,
): ScreenComponent {
	return createComponent(id, 'table', title, x, y, w, h, 15, {
		title, header, data,
		fontSize: 16, headerAlign: 'center',
		headerColor: TITLE_COLOR, headerBackground: TABLE_HEADER_BG,
		bodyColor: BODY_COLOR, bodyBackground: TABLE_BODY_BG,
		oddRowBackground: TABLE_BODY_BG, evenRowBackground: TABLE_EVEN_ROW_BG,
		borderColor: PANEL_BORDER,
		enableSort: true, enablePagination: true, pageSize: 8, freezeHeader: true,
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
				[
					['需求分析', '制造协同平台', '100%', '低'],
					['系统设计', '制造协同平台', '85%', '低'],
					['核心开发', '制造协同平台', '45%', '中'],
					['基建施工', '新能源工厂', '35%', '高'],
					['设备采购', '新能源工厂', '60%', '中'],
					['原型验证', '核心器件研发', '70%', '中'],
					['批量测试', '核心器件研发', '20%', '高'],
				],
			),
			drillTable('drill-exec-side-panel', '资源负载摘要',
				1185, 175, 715, 420,
				['部门', '在岗/总人力', '超负荷', '空闲'],
				[
					['工程建设中心', '62/68', 8, 2],
					['数字化事业部', '48/52', 5, 3],
					['研发中心', '78/85', 12, 4],
					['生产制造中心', '40/45', 3, 2],
					['采购中心', '28/32', 0, 4],
					['质量管理部', '25/28', 0, 0],
				],
			),
			drillTable('drill-exec-support-table', '延期项目排行',
				20, 610, 1880, 440,
				['项目', '延期天数', '责任科室', '风险'],
				[
					['新能源工厂建设项目', '28 天', '工程建设中心', '高'],
					['核心器件研发验证项目', '21 天', '研发中心', '中'],
					['智能制造产线改造', '18 天', '生产制造中心', '高'],
					['供应链协同平台', '14 天', '采购中心', '中'],
					['质量追溯系统', '12 天', '质量管理部', '低'],
				],
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
				['项目', '问题名称', '原因分类', '状态', '责任单位'],
				[
					['核心器件研发', 'PCB布线设计缺陷', '设计', '未完成归零', '研发中心'],
					['智能制造产线', '焊接工艺参数偏差', '工艺', '已完成技术归零', '生产制造中心'],
					['新能源工厂', '控制器元器件批次不良', '元器件', '未完成归零', '采购中心'],
					['供应链平台', '接口协议不一致', '软件', '已完成管理归零', '数字化事业部'],
					['制造协同平台', '测试用例覆盖不足', '管理', '未完成归零', '质量管理部'],
				],
			),
			drillTable('drill-quality-side-panel', '质量闭环摘要',
				1185, 175, 715, 420,
				['指标', '数值'],
				[
					['现存质量问题', '47'],
					['归零完成率', '68.3%'],
					['已归零计划同步', '39'],
					['高优未关闭', '8'],
				],
			),
			drillTable('drill-quality-support-table', '跟进措施摘要',
				20, 610, 1880, 440,
				['项目', '问题名称', '措施类别', '跟进人', '闭环状态'],
				[
					['核心器件研发', 'PCB布线设计缺陷', '技术措施', '陈工', '跟进中'],
					['新能源工厂', '控制器元器件批次不良', '供应商管理', '刘工', '跟进中'],
					['制造协同平台', '测试用例覆盖不足', '管理措施', '王工', '待启动'],
					['智能制造产线', '焊接工艺参数偏差', '工艺改进', '李工', '已闭环'],
					['供应链平台', '接口协议不一致', '技术措施', '张工', '已闭环'],
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
				['技术状态项', '更改事项', '更改类别', '签署状态', '负责人'],
				[
					['控制器硬件V2', '硬件版本升级', 'I', '已签署', '陈工'],
					['通信协议V3', '协议变更', 'II', '评审中', '周工'],
					['结构件M1', '材料替代', 'I', '已签署', '马工'],
					['软件架构V2', '架构调整', 'II', '未评审', '孙工'],
				],
			),
			drillTable('drill-tech-side-panel', '状态分布摘要',
				1185, 175, 715, 420,
				['分类', '数量'],
				[
					['更改类别: Ⅰ类', '4'],
					['更改类别: Ⅱ类', '9'],
					['更改类别: Ⅲ类', '5'],
					['签署: 已签署', '12'],
					['签署: 评审中', '4'],
					['签署: 未评审', '2'],
				],
			),
			drillTable('drill-tech-support-table', '跟进措施摘要',
				20, 610, 1880, 440,
				['技术状态项', '措施类别', '跟进人', '闭环状态', '项目'],
				[
					['控制器硬件V2', '技术验证', '陈工', '已闭环', '核心器件研发'],
					['通信协议V3', '评审跟进', '周工', '跟进中', '制造协同平台'],
					['结构件M1', '验证测试', '马工', '已闭环', '新能源工厂'],
					['软件架构V2', '评审启动', '孙工', '待启动', '数据治理工程'],
				],
			),
		],
	},
};

// ══════════════════════════════════════════════════════════
// Template 4: 执行层-风险与预警中心明细
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
				['风险名称', '项目', '风险等级', '风险状态', '风险阶段'],
				[
					['核心芯片供货延迟', '核心器件研发', '高', '跟进中', '研制'],
					['施工许可证审批滞后', '新能源工厂', '高', '跟进中', '建设'],
					['关键技术人员流失', '制造协同平台', '中', '跟进中', '研发'],
					['原材料价格上涨', '供应链平台', '中', '已释放', '采购'],
					['测试环境不稳定', '数据治理工程', '低', '跟进中', '开发'],
				],
			),
			drillTable('drill-risk-side-panel', '风险分类摘要',
				1185, 175, 715, 420,
				['分类', '数量'],
				[
					['技术风险', '28'],
					['进度风险', '22'],
					['供应链风险', '15'],
					['外协风险', '12'],
					['质量风险', '8'],
					['管理风险', '4'],
				],
			),
			drillTable('drill-risk-support-table', '风险措施摘要',
				20, 610, 1880, 440,
				['项目', '风险名称', '措施类别', '跟进人', '闭环状态'],
				[
					['核心器件研发', '核心芯片供货延迟', '供应商管理', '陈工', '跟进中'],
					['新能源工厂', '施工许可证审批滞后', '协调推进', '刘工', '跟进中'],
					['制造协同平台', '关键技术人员流失', '人员保障', '王工', '跟进中'],
					['供应链平台', '原材料价格上涨', '锁价合同', '李工', '已闭环'],
					['数据治理工程', '测试环境不稳定', '环境容器化', '孙工', '跟进中'],
				],
			),
		],
	},
};

// ══════════════════════════════════════════════════════════
// Template 5: 执行层-重要物料信息明细
// ══════════════════════════════════════════════════════════

const materialDrillTemplate: ScreenTemplate = {
	id: 'gpmc-drill-material',
	name: '执行层-重要物料信息明细',
	description: '重要物料信息钻取页面，展示物料清单、供应链风险及交期跟踪。',
	thumbnail: '📦',
	category: 'project-management',
	tags: ['gpmc', 'drill', 'material'],
	config: {
		name: '执行层-重要物料信息明细',
		description: '重要物料信息钻取页面',
		width: SCREEN_WIDTH, height: SCREEN_HEIGHT,
		backgroundColor: BG, theme: 'enterprise-light' as const,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		globalVariables: drillGlobalVariables,
		components: [
			headerBar('drill-material-header-bar'),
			titleText('drill-material-title', '执行层-重要物料信息明细'),
			badgeComp('drill-material-badge'),
			numberCard('drill-material-kpi-1', '物料总数', '156', '', 20, 456, ACCENT),
			numberCard('drill-material-kpi-2', '长周期物料', '42', '', 490, 456, WARNING),
			numberCard('drill-material-kpi-3', '外协占比', '63', '%', 960, 456, ACCENT),
			numberCard('drill-material-kpi-4', '有风险物料', '18', '', 1430, 470, DANGER),
			drillTable('drill-material-detail-table', '物料清单',
				20, 175, 1145, 420,
				['PBS编号', 'PBS名称', '自研/外协', '供应商', '长周期', '风险等级'],
				[
					['PBS-001', 'FPGA芯片', '外协', '芯片供应商A', '是', '高'],
					['PBS-002', '射频模块', '外协', '模块厂商B', '是', '中'],
					['PBS-003', '电路板', '自研', '-', '否', '-'],
					['PBS-004', '电池组', '外协', '电池厂商C', '是', '中'],
					['PBS-005', '壳体', '自研', '-', '否', '-'],
				],
			),
			drillTable('drill-material-side-panel', '供应链摘要',
				1185, 175, 715, 420,
				['指标', '数值'],
				[
					['物料总数', '156'],
					['长周期占比', '26.9%'],
					['外协占比', '63.5%'],
					['供应商数量', '48'],
					['有风险物料', '18'],
					['延期到货', '5'],
				],
			),
			drillTable('drill-material-support-table', '交期跟踪',
				20, 610, 1880, 440,
				['PBS编号', 'PBS名称', '合同到货时间', '实际到货时间', '延期影响', '风险等级'],
				[
					['PBS-001', 'FPGA芯片', '2026-06-30', '-', '影响主控单机集成', '高'],
					['PBS-002', '射频模块', '2026-05-15', '-', '影响通信单机联调', '中'],
					['PBS-004', '电池组', '2026-07-20', '-', '影响电源系统测试', '中'],
					['PBS-003', '电路板', '2026-04-10', '2026-04-08', '-', '-'],
					['PBS-005', '壳体', '2026-03-30', '2026-03-28', '-', '-'],
				],
			),
		],
	},
};

// ── Export ────────────────────────────────────────────────

export const gpmcDrillTemplates: ScreenTemplate[] = [
	executionDrillTemplate,
	qualityDrillTemplate,
	techStateDrillTemplate,
	riskDrillTemplate,
	materialDrillTemplate,
];
