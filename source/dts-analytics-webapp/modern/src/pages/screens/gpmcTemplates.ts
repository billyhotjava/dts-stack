import type { ScreenComponent, ScreenComponentAction, ScreenGlobalVariable } from './types';
import type { ScreenTemplate } from './screenTemplates';
import { SCREEN_SCHEMA_VERSION } from './specV2';
import { flattenAssistCardBody, getGpmcAssistContent } from '../gpmc/gpmcAssistContent';
import type { GpmcScreenId } from '../gpmc/GpmcApp';

const SCREEN_WIDTH = 1920;
const SCREEN_HEIGHT = 1080;
const BG = '#eef5fb';
const PANEL_BG = 'rgba(255, 255, 255, 0.96)';
const PANEL_BORDER = 'rgba(148, 163, 184, 0.24)';
const TITLE_COLOR = '#16324f';
const SUBTITLE_COLOR = '#5b7088';
const BODY_COLOR = '#35526b';
const ACCENT = '#3b82f6';
const SUCCESS = '#16a34a';
const WARNING = '#f59e0b';
const DANGER = '#ef4444';
const INFO = '#38bdf8';
const KPI_BG = '#ffffff';
const INPUT_BG = 'rgba(255, 255, 255, 0.98)';
const INPUT_BORDER = 'rgba(148, 163, 184, 0.42)';
const TABLE_HEADER_BG = 'rgba(219, 234, 254, 0.96)';
const TABLE_BODY_BG = 'rgba(255, 255, 255, 0.98)';
const TABLE_EVEN_ROW_BG = 'rgba(241, 245, 249, 0.98)';
const ANALYTICS_BASENAME = '/analytics';
const SCREEN_REF_PREFIX = 'screen-ref:';
const STRATEGIC_BG = '#050a14';
const STRATEGIC_PANEL_BG = 'rgba(10, 22, 40, 0.86)';
const STRATEGIC_PANEL_BORDER = 'rgba(34, 195, 255, 0.16)';
const STRATEGIC_TEXT = '#e6f0ff';
const STRATEGIC_MUTED = '#6b8ab5';
const STRATEGIC_NAV_BG = 'rgba(14, 32, 54, 0.92)';
const STRATEGIC_NAV_ACTIVE_BG = 'rgba(34, 195, 255, 0.16)';

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
	actions?: ScreenComponentAction[],
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
		...(actions?.length ? { actions } : {}),
	};
}

function jumpAction(urlTemplate: string): ScreenComponentAction {
	return { type: 'jump-url', jumpUrlTemplate: urlTemplate, jumpOpenMode: 'new-tab' };
}

function jumpSelfAction(urlTemplate: string): ScreenComponentAction {
	return { type: 'jump-url', jumpUrlTemplate: urlTemplate, jumpOpenMode: 'self' };
}

function buildScreenReference(screenName: string, fallbackPath: string) {
	return `${SCREEN_REF_PREFIX}${encodeURIComponent(screenName)}|${encodeURIComponent(`${ANALYTICS_BASENAME}${fallbackPath}`)}`;
}

function buildGpmcTemplateJump(path: string) {
	return `${ANALYTICS_BASENAME}${path}`;
}

function openPanelAction(title: string, body: string): ScreenComponentAction {
	return { type: 'open-panel', panelTitle: title, panelBodyTemplate: body };
}

function withActions(component: ScreenComponent, ...actions: ScreenComponentAction[]) {
	return { ...component, actions };
}

function withConfig(component: ScreenComponent, config: Record<string, unknown>): ScreenComponent {
	return {
		...component,
		config: {
			...component.config,
			...config,
		},
	};
}

function createVariable(key: string, label: string, type: ScreenGlobalVariable['type']): ScreenGlobalVariable {
	return { key, label, type, defaultValue: '' };
}

function createTitle(id: string, text: string, y = 24): ScreenComponent {
	return createComponent(id, 'title', text, 44, y, 720, 44, 60, {
		text,
		fontSize: 36,
		fontWeight: '700',
		color: TITLE_COLOR,
		textAlign: 'flex-start',
	});
}

function createStrategicTitle(id: string, text: string, x: number, y: number, width: number, fontSize = 34): ScreenComponent {
	return createComponent(id, 'title', text, x, y, width, 42, 60, {
		text,
		fontSize,
		fontWeight: '800',
		color: STRATEGIC_TEXT,
		textAlign: 'flex-start',
	});
}

function createStrategicSubtitle(id: string, text: string, x: number, y: number, width: number): ScreenComponent {
	return createComponent(id, 'title', text, x, y, width, 24, 60, {
		text,
		fontSize: 16,
		fontWeight: '500',
		color: STRATEGIC_MUTED,
		textAlign: 'flex-start',
	});
}

function createPanel(id: string, x: number, y: number, width: number, height: number): ScreenComponent {
	return createComponent(id, 'shape', id, x, y, width, height, 1, {
		shapeType: 'rect',
		fillColor: PANEL_BG,
		borderColor: PANEL_BORDER,
		borderWidth: 1,
		radius: 18,
	});
}

function createStrategicPanel(id: string, x: number, y: number, width: number, height: number): ScreenComponent {
	return createComponent(id, 'shape', id, x, y, width, height, 1, {
		shapeType: 'rect',
		fillColor: STRATEGIC_PANEL_BG,
		borderColor: STRATEGIC_PANEL_BORDER,
		borderWidth: 1,
		radius: 18,
	});
}

function createNumberCard(
	id: string,
	title: string,
	x: number,
	y: number,
	width: number,
	value: number,
	options?: {
		suffix?: string;
		precision?: number;
		valueColor?: string;
		titleColor?: string;
	},
	actions?: ScreenComponentAction[],
): ScreenComponent {
	return createComponent(id, 'number-card', title, x, y, width, 112, 10, {
		title,
		value,
		suffix: options?.suffix ?? '',
		precision: options?.precision ?? 0,
		titleFontSize: 17,
		valueFontSize: 34,
		titleColor: options?.titleColor ?? SUBTITLE_COLOR,
		valueColor: options?.valueColor ?? TITLE_COLOR,
		backgroundColor: KPI_BG,
	}, actions);
}

function createStrategicNumberCard(
	id: string,
	title: string,
	x: number,
	y: number,
	width: number,
	value: number,
	options?: {
		suffix?: string;
		precision?: number;
		valueColor?: string;
	},
	actions?: ScreenComponentAction[],
): ScreenComponent {
	return createComponent(id, 'number-card', title, x, y, width, 118, 12, {
		title,
		value,
		suffix: options?.suffix ?? '',
		precision: options?.precision ?? 0,
		titleFontSize: 17,
		valueFontSize: 34,
		titleColor: '#ffffff',
		valueColor: options?.valueColor ?? STRATEGIC_TEXT,
		backgroundColor: 'rgba(11, 24, 42, 0.94)',
	}, actions);
}

function createFilterSelect(
	id: string,
	label: string,
	variableKey: string,
	x: number,
	y: number,
	width: number,
	options: string[],
): ScreenComponent {
	return createComponent(id, 'filter-select', label, x, y, width, 58, 65, {
		label,
		variableKey,
		options,
		placeholder: '全部',
		labelColor: '#ffffff',
		inputBackground: INPUT_BG,
		inputBorderColor: INPUT_BORDER,
		inputTextColor: TITLE_COLOR,
	});
}

function createFilterInput(
	id: string,
	label: string,
	variableKey: string,
	x: number,
	y: number,
	width: number,
): ScreenComponent {
	return createComponent(id, 'filter-input', label, x, y, width, 58, 65, {
		label,
		variableKey,
		placeholder: '输入关键词',
		labelColor: '#ffffff',
		inputBackground: INPUT_BG,
		inputBorderColor: INPUT_BORDER,
		inputTextColor: TITLE_COLOR,
	});
}

function createDateRange(id: string, x: number, y: number, width: number): ScreenComponent {
	return createComponent(id, 'filter-date-range', '统计周期', x, y, width, 58, 65, {
		label: '统计周期',
		startKey: 'dateFrom',
		endKey: 'dateTo',
		labelColor: '#ffffff',
		inputBackground: INPUT_BG,
		inputBorderColor: INPUT_BORDER,
		inputTextColor: TITLE_COLOR,
	});
}

function createAssistTrigger(
	id: string,
	label: string,
	x: number,
	y: number,
	accentColor: string,
	panelTitle: string,
	panelBody: string,
): ScreenComponent[] {
	const action = openPanelAction(panelTitle, panelBody);
	return [
		createComponent(`${id}-shape`, 'shape', label, x, y, 68, 30, 66, {
			shapeType: 'rect',
			fillColor: 'rgba(255, 255, 255, 0.92)',
			borderColor: accentColor,
			borderWidth: 1,
			radius: 999,
		}, [action]),
		createComponent(`${id}-label`, 'title', label, x, y, 68, 30, 67, {
			text: label,
			fontSize: 16,
			fontWeight: '700',
			color: accentColor,
			textAlign: 'center',
		}, [action]),
	];
}

const gpmcTopicTabs: Array<{ screen: GpmcScreenId; label: string; href: string }> = [
	{ screen: 'overview', label: '综合态势', href: '/gpmc' },
	{ screen: 'execution', label: '执行监控', href: '/gpmc/execution' },
	{ screen: 'quality', label: '质量跟进', href: '/gpmc/quality' },
	{ screen: 'tech-state', label: '技术状态', href: '/gpmc/tech-state' },
	{ screen: 'cost', label: '成本控制', href: '/gpmc/cost' },
	{ screen: 'risk', label: '风险预警', href: '/gpmc/risk' },
];

const gpmcScreenNames: Record<GpmcScreenId, string> = {
	overview: 'GPMC 战略层大屏',
	execution: 'GPMC 项目执行监控',
	quality: 'GPMC 质量信息与跟进措施',
	'tech-state': 'GPMC 技术状态与跟进',
	cost: 'GPMC 成本与预算控制',
	risk: 'GPMC 风险与预警中心',
};

function createTopicTabs(screen: GpmcScreenId, x: number, y: number, dark = false): ScreenComponent[] {
	const width = dark ? 132 : 116;
	const gap = dark ? 12 : 10;
	const activeFill = dark ? STRATEGIC_NAV_ACTIVE_BG : 'rgba(59, 130, 246, 0.12)';
	const inactiveFill = dark ? STRATEGIC_NAV_BG : 'rgba(255, 255, 255, 0.96)';
	const activeBorder = dark ? 'rgba(34, 195, 255, 0.42)' : 'rgba(59, 130, 246, 0.30)';
	const inactiveBorder = dark ? 'rgba(34, 195, 255, 0.10)' : 'rgba(148, 163, 184, 0.22)';
	const activeText = dark ? '#b9ecff' : ACCENT;
	const inactiveText = dark ? '#d8e6fb' : TITLE_COLOR;
	return gpmcTopicTabs.flatMap((item, index) => {
		const active = item.screen === screen;
		const left = x + index * (width + gap);
		const actions = [jumpSelfAction(buildScreenReference(gpmcScreenNames[item.screen], item.href))];
		return [
			createComponent(`${screen}-topic-${item.screen}-shape`, 'shape', item.label, left, y, width, 36, 24, {
				shapeType: 'rect',
				fillColor: active ? activeFill : inactiveFill,
				borderColor: active ? activeBorder : inactiveBorder,
				borderWidth: 1,
				radius: 12,
			}, actions),
			createComponent(`${screen}-topic-${item.screen}-label`, 'title', item.label, left, y, width, 36, 25, {
				text: item.label,
				fontSize: dark ? 17 : 16,
				fontWeight: active ? '700' : '600',
				color: active ? activeText : inactiveText,
				textAlign: 'center',
			}, actions),
		];
	});
}

function createLineChart(
	id: string,
	title: string,
	x: number,
	y: number,
	width: number,
	height: number,
	xAxisData: string[],
	series: Array<{ name: string; data: number[] }>,
	actions?: ScreenComponentAction[],
): ScreenComponent {
	return createComponent(id, 'line-chart', title, x, y, width, height, 15, {
		title,
		xAxisData,
		series,
		lineSmooth: true,
		areaStyle: true,
		seriesColors: [ACCENT, INFO, WARNING],
	}, actions);
}

function createBarChart(
	id: string,
	title: string,
	x: number,
	y: number,
	width: number,
	height: number,
	xAxisData: string[],
	series: Array<{ name: string; data: number[] }>,
	actions?: ScreenComponentAction[],
): ScreenComponent {
	return createComponent(id, 'bar-chart', title, x, y, width, height, 15, {
		title,
		xAxisData,
		series,
		seriesColors: [ACCENT, WARNING, SUCCESS],
	}, actions);
}

function createPieChart(
	id: string,
	title: string,
	x: number,
	y: number,
	width: number,
	height: number,
	data: Array<{ name: string; value: number }>,
	actions?: ScreenComponentAction[],
): ScreenComponent {
	return createComponent(id, 'pie-chart', title, x, y, width, height, 15, {
		title,
		data,
		seriesColors: [ACCENT, INFO, WARNING, DANGER, '#8b5cf6'],
	}, actions);
}

function createTable(
	id: string,
	title: string,
	x: number,
	y: number,
	width: number,
	height: number,
	header: string[],
	data: Array<Array<string | number>>,
	actions?: ScreenComponentAction[],
): ScreenComponent {
	return createComponent(id, 'table', title, x, y, width, height, 15, {
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
		pageSize: 6,
		freezeHeader: true,
	}, actions);
}

function createStrategicTable(
	id: string,
	title: string,
	x: number,
	y: number,
	width: number,
	height: number,
	header: string[],
	data: Array<Array<string | number>>,
	actions?: ScreenComponentAction[],
): ScreenComponent {
	return createComponent(id, 'table', title, x, y, width, height, 15, {
		title,
		header,
		data,
		fontSize: 16,
		headerAlign: 'center',
		headerColor: '#ffffff',
		headerBackground: 'rgba(17, 34, 56, 0.92)',
		bodyColor: '#d6e7ff',
		bodyBackground: 'rgba(10, 22, 40, 0.95)',
		oddRowBackground: 'rgba(10, 22, 40, 0.95)',
		evenRowBackground: 'rgba(13, 28, 46, 0.95)',
		borderColor: STRATEGIC_PANEL_BORDER,
		enableSort: true,
		enablePagination: true,
		pageSize: 6,
		freezeHeader: true,
	}, actions);
}

function createGanttBoard(
	id: string,
	title: string,
	x: number,
	y: number,
	width: number,
	height: number,
	tasks: Array<Record<string, unknown>>,
	actions?: ScreenComponentAction[],
): ScreenComponent {
	return createComponent(id, 'gantt-chart', title, x, y, width, height, 15, {
		title,
		renderMode: 'board',
		tasks,
	}, actions);
}

function createHeader(title: string, _subtitle: string, screen: GpmcScreenId) {
	const assist = getGpmcAssistContent(screen, screen === 'overview' ? 'strategic' : 'control');
	return [
		createTitle(`${title}-title`, title),
		...createTopicTabs(screen, 44, 78, false),
		createDateRange(`${title}-date`, 1260, 28, 320),
		createFilterSelect(`${title}-dept`, '事业部/科室', 'deptId', 940, 28, 150, ['全部', '工程建设中心', '数字化事业部', '研发中心', '生产制造中心']),
		createFilterInput(`${title}-project`, '项目编号/项目名称', 'projectNo', 1104, 28, 140),
		createFilterSelect(`${title}-risk`, '风险等级', 'riskLevel', 1596, 28, 120, ['全部', '高', '中', '低']),
		...createAssistTrigger(
			`${title}-explanation`,
			'说明',
			1732,
			34,
			ACCENT,
			assist.explanation.title,
			flattenAssistCardBody(assist.explanation),
		),
		...(assist.guide
			? createAssistTrigger(
				`${title}-guide`,
				'导览',
				1808,
				34,
				SUCCESS,
				assist.guide.title,
				flattenAssistCardBody(assist.guide),
			)
			: []),
	];
}

function createDarkHeader(title: string, screen: GpmcScreenId) {
	const assist = getGpmcAssistContent(screen, 'control');
	return [
		createStrategicTitle(`${title}-title`, title, 44, 24, 720, 32),
		...createTopicTabs(screen, 44, 78, true),
		createDateRange(`${title}-date`, 1260, 28, 320),
		createFilterSelect(`${title}-dept`, '事业部/科室', 'deptId', 940, 28, 150, ['全部', '工程建设中心', '数字化事业部', '研发中心', '生产制造中心']),
		createFilterInput(`${title}-project`, '项目编号/项目名称', 'projectNo', 1104, 28, 140),
		createFilterSelect(`${title}-risk`, '风险等级', 'riskLevel', 1596, 28, 120, ['全部', '高', '中', '低']),
		...createAssistTrigger(
			`${title}-explanation`,
			'说明',
			1732,
			34,
			'#60a5fa',
			assist.explanation.title,
			flattenAssistCardBody(assist.explanation),
		),
		...(assist.guide
			? createAssistTrigger(
				`${title}-guide`,
				'导览',
				1808,
				34,
				'#3ddc97',
				assist.guide.title,
				flattenAssistCardBody(assist.guide),
			)
			: []),
	];
}

const commonVariables: ScreenGlobalVariable[] = [
	createVariable('dateFrom', '开始日期', 'date'),
	createVariable('dateTo', '结束日期', 'date'),
	createVariable('deptId', '事业部/科室', 'string'),
	createVariable('projectNo', '项目编号/项目名称', 'string'),
	createVariable('riskLevel', '风险等级', 'string'),
];

function baseConfig(name: string, description: string, components: ScreenComponent[]) {
	return {
		name,
		description,
		width: SCREEN_WIDTH,
		height: SCREEN_HEIGHT,
		backgroundColor: BG,
		theme: 'glacier' as const,
		globalVariables: commonVariables,
		components,
		schemaVersion: SCREEN_SCHEMA_VERSION,
	};
}

function strategicBoardConfig(name: string, description: string, components: ScreenComponent[]) {
	return {
		name,
		description,
		width: SCREEN_WIDTH,
		height: SCREEN_HEIGHT,
		backgroundColor: STRATEGIC_BG,
		theme: 'legacy-dark' as const,
		globalVariables: commonVariables,
		components,
		schemaVersion: SCREEN_SCHEMA_VERSION,
	};
}

const strategicOverviewTemplate: ScreenTemplate = {
	id: 'gpmc-strategic-overview',
	name: 'GPMC 战略层大屏',
	description: '项目综合态势总览模板，面向集团领导的一屏全局态势感知。',
	thumbnail: '🛰️',
	category: 'project-management',
	tags: ['GPMC', '战略层', '态势感知', '项目总览'],
	recommendedVariables: commonVariables.map((item) => item.key),
	config: {
		name: 'GPMC 战略层大屏',
		description: '项目综合态势总览',
		width: SCREEN_WIDTH,
		height: SCREEN_HEIGHT,
		backgroundColor: STRATEGIC_BG,
		theme: 'legacy-dark' as const,
		globalVariables: commonVariables,
		schemaVersion: SCREEN_SCHEMA_VERSION,
		components: [
			createStrategicPanel('gpmc-overview-weekly-panel', 36, 136, 320, 236),
			createStrategicPanel('gpmc-overview-stage-panel', 36, 386, 320, 176),
			createStrategicPanel('gpmc-overview-quality-panel', 36, 576, 320, 142),
			createStrategicPanel('gpmc-overview-delay-panel', 36, 732, 320, 256),
			createStrategicPanel('gpmc-overview-gauge-panel', 376, 296, 560, 414),
			createStrategicPanel('gpmc-overview-quality-trend-panel', 952, 296, 592, 414),
			createStrategicPanel('gpmc-overview-risk-panel', 1564, 136, 320, 236),
			createStrategicPanel('gpmc-overview-cost-panel', 1564, 386, 320, 176),
			createStrategicPanel('gpmc-overview-tech-panel', 1564, 576, 320, 172),
			createStrategicPanel('gpmc-overview-alert-panel', 1564, 762, 320, 226),
			createStrategicPanel('gpmc-overview-project-panel', 376, 724, 1168, 264),
			createComponent('gpmc-overview-main-title', 'title', '项目运营管理中心', 360, 28, 1200, 40, 60, {
				text: '项目运营管理中心',
				fontSize: 36,
				fontWeight: '800',
				color: STRATEGIC_TEXT,
				textAlign: 'center',
			}),
			...createTopicTabs('overview', 534, 82, true),
			...createAssistTrigger('gpmc-overview-assist-explanation', '说明', 1742, 40, '#60a5fa', getGpmcAssistContent('overview', 'strategic').explanation.title, flattenAssistCardBody(getGpmcAssistContent('overview', 'strategic').explanation)),
			...createAssistTrigger('gpmc-overview-assist-guide', '导览', 1818, 40, '#3ddc97', getGpmcAssistContent('overview', 'strategic').guide!.title, flattenAssistCardBody(getGpmcAssistContent('overview', 'strategic').guide!)),
			createStrategicNumberCard('gpmc-overview-kpi-total', '项目总数', 376, 136, 184, 128, { valueColor: STRATEGIC_TEXT }, [jumpSelfAction(buildScreenReference(gpmcScreenNames.execution, '/gpmc/execution'))]),
			createStrategicNumberCard('gpmc-overview-kpi-active', '进行中项目', 572, 136, 184, 74, { valueColor: STRATEGIC_TEXT }, [jumpSelfAction(buildScreenReference(gpmcScreenNames.execution, '/gpmc/execution'))]),
			createStrategicNumberCard('gpmc-overview-kpi-delay', '延期项目', 768, 136, 184, 11, { valueColor: '#ff6b7a' }, [jumpSelfAction(buildScreenReference(gpmcScreenNames.execution, '/gpmc/execution'))]),
			createStrategicNumberCard('gpmc-overview-kpi-budget', '年度预算总额', 964, 136, 184, 36.8, { suffix: ' 亿元', precision: 1, valueColor: STRATEGIC_TEXT }, [jumpSelfAction(buildScreenReference(gpmcScreenNames.cost, '/gpmc/cost'))]),
			createStrategicNumberCard('gpmc-overview-kpi-progress', '平均进度达成率', 1160, 136, 184, 78, { suffix: '%', precision: 0, valueColor: '#5cf2a5' }, [jumpSelfAction(buildScreenReference(gpmcScreenNames.execution, '/gpmc/execution'))]),
			createStrategicNumberCard('gpmc-overview-kpi-risk', '高风险项目占比', 1356, 136, 184, 9.4, { suffix: '%', precision: 1, valueColor: '#ff6b7a' }, [jumpSelfAction(buildScreenReference(gpmcScreenNames.risk, '/gpmc/risk'))]),
			createBarChart(
				'gpmc-overview-weekly-chart',
				'周度完成趋势',
				52,
				160,
				288,
				186,
				['W1', 'W2', 'W3', 'W4', 'W5', 'W6', 'W7', 'W8'],
				[
					{ name: '完成数', data: [42, 48, 55, 51, 62, 58, 65, 72] },
					{ name: '延期数', data: [8, 6, 9, 7, 5, 8, 4, 6] },
				],
				[jumpSelfAction(buildScreenReference(gpmcScreenNames.execution, '/gpmc/execution'))],
			),
			createPieChart(
				'gpmc-overview-stage-chart',
				'项目阶段分布',
				52,
				410,
				288,
				138,
				[
					{ name: '策划中', value: 18 },
					{ name: '执行中', value: 52 },
					{ name: '验收中', value: 14 },
					{ name: '已完成', value: 32 },
					{ name: '已暂停', value: 6 },
				],
				[jumpSelfAction(buildScreenReference(gpmcScreenNames.execution, '/gpmc/execution'))],
			),
			createStrategicTable(
				'gpmc-overview-quality-tags',
				'质量问题分类',
				52,
				598,
				288,
				92,
				['类别', '数量'],
				[
					['设计', 12],
					['工艺', 8],
					['管理', 6],
					['元器件', 5],
				],
				[jumpSelfAction(buildScreenReference(gpmcScreenNames.quality, '/gpmc/quality'))],
			),
			createStrategicTable(
				'gpmc-overview-delay-top',
				'延期 TOP5',
				52,
				756,
				288,
				206,
				['项目', '延期'],
				[
					['新能源工厂建设项目', '28天'],
					['核心器件研发验证项目', '21天'],
					['智能制造产线改造', '18天'],
					['供应链协同平台', '14天'],
					['质量追溯系统', '12天'],
				],
				[jumpSelfAction(buildScreenReference(gpmcScreenNames.execution, '/gpmc/execution'))],
			),
			createComponent('gpmc-overview-health-gauge', 'gauge-chart', '综合健康指数', 488, 332, 336, 308, 16, {
				title: '综合健康指数',
				value: 84.6,
				min: 0,
				max: 100,
			}),
			createStrategicNumberCard('gpmc-overview-gauge-progress', '进度', 418, 598, 96, 78, { suffix: '%', valueColor: '#22c3ff' }),
			createStrategicNumberCard('gpmc-overview-gauge-quality', '质量', 528, 598, 96, 76, { suffix: '%', valueColor: '#3ddc97' }),
			createStrategicNumberCard('gpmc-overview-gauge-cost', '成本', 638, 598, 96, 93, { suffix: '%', valueColor: '#ffb74d' }),
			createStrategicNumberCard('gpmc-overview-gauge-risk', '风险', 748, 598, 96, 62, { suffix: '%', valueColor: '#ff6b7a' }),
			createStrategicNumberCard('gpmc-overview-gauge-resource', '资源', 858, 598, 96, 87, { suffix: '%', valueColor: '#54e3ff' }),
			createLineChart(
				'gpmc-overview-quality-trend',
				'质量闭环趋势',
				970,
				322,
				556,
				360,
				['1月', '2月', '3月', '4月', '5月', '6月'],
				[
					{ name: '新增', data: [32, 28, 35, 22, 25, 23] },
					{ name: '已闭环', data: [28, 30, 25, 32, 24, 18] },
				],
				[jumpSelfAction(buildScreenReference(gpmcScreenNames.quality, '/gpmc/quality'))],
			),
			withConfig(
				createBarChart(
					'gpmc-overview-risk-rank',
					'风险分类排名',
					1574,
					148,
					300,
					212,
					['技术风险', '进度风险', '成本风险', '外协风险', '质量风险'],
					[{ name: '风险数', data: [14, 11, 9, 7, 5] }],
					[jumpSelfAction(buildScreenReference(gpmcScreenNames.risk, '/gpmc/risk'))],
				),
				{
					titleFontSize: 19,
					axisFontSize: 15,
					chartPaddingTop: 22,
					chartPaddingRight: 14,
					chartPaddingBottom: 20,
					chartPaddingLeft: 20,
					xAxisLabelMaxLength: 5,
				},
			),
			withConfig(
				createBarChart(
					'gpmc-overview-cost-rank',
					'部门成本核算',
					1574,
					398,
					300,
					152,
					['工程建设', '数字化', '研发', '制造'],
					[{ name: '执行率', data: [86, 52, 68, 104] }],
					[jumpSelfAction(buildScreenReference(gpmcScreenNames.cost, '/gpmc/cost'))],
				),
				{
					titleFontSize: 19,
					axisFontSize: 15,
					chartPaddingTop: 22,
					chartPaddingRight: 14,
					chartPaddingBottom: 18,
					chartPaddingLeft: 18,
					seriesLabelStrategy: 'first-only',
				},
			),
			withConfig(
				createBarChart(
					'gpmc-overview-tech-change',
					'技术状态变更',
					1574,
					584,
					300,
					156,
					['M1', 'M2', 'M3', 'M4', 'M5', 'M6'],
					[{ name: '变更数', data: [5, 8, 3, 6, 4, 2] }],
					[jumpSelfAction(buildScreenReference(gpmcScreenNames['tech-state'], '/gpmc/tech-state'))],
				),
				{
					titleFontSize: 19,
					axisFontSize: 15,
					chartPaddingTop: 22,
					chartPaddingRight: 14,
					chartPaddingBottom: 18,
					chartPaddingLeft: 18,
					seriesLabelStrategy: 'first-only',
				},
			),
			withConfig(
				createStrategicTable(
					'gpmc-overview-alert-table',
					'预警统计',
					1574,
					774,
					300,
					204,
					['专题', '状态'],
					[
						['关键里程碑逾期', '需关注'],
						['需求变更频繁', '预警'],
						['资源接口屏蔽', '跟踪'],
						['高风险项目', '预警'],
						['外协交付波动', '跟踪'],
						['成本偏差放大', '需关注'],
					],
					[jumpSelfAction(buildScreenReference(gpmcScreenNames.risk, '/gpmc/risk'))],
				),
				{
					fontSize: 17,
					enablePagination: false,
				},
			),
			createStrategicTable(
				'gpmc-overview-status-table',
				'项目状态分布',
				394,
				746,
				1132,
				222,
				['项目', '健康度', '进度状态', '风险状态'],
				[
					['集团制造协同平台二期', '82%', '可控', '低'],
					['新能源工厂建设项目', '61%', '延期', '高'],
					['核心器件研发验证项目', '46%', '延期', '中'],
					['集团统一主数据治理工程', '73%', '可控', '中'],
					['供应链数字化转型项目', '88%', '可控', '低'],
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/execution'))],
			),
		],
	},
};

const executionBoardTemplate: ScreenTemplate = {
	id: 'gpmc-execution-board',
	name: 'GPMC 项目执行监控',
	description: '管控层项目执行监控模板，覆盖进度、里程碑、甘特和延期项目。',
	thumbnail: '📈',
	category: 'project-management',
	tags: ['GPMC', '执行监控', '管控层', '甘特'],
	recommendedVariables: commonVariables.map((item) => item.key),
	config: strategicBoardConfig(
		'GPMC 项目执行监控',
		'项目执行监控',
		[
			createStrategicPanel('gpmc-execution-top', 36, 110, 1848, 140),
			createStrategicPanel('gpmc-execution-left', 36, 268, 900, 500),
			createStrategicPanel('gpmc-execution-right-top', 954, 268, 930, 242),
			createStrategicPanel('gpmc-execution-right-bottom', 954, 528, 930, 240),
			createStrategicPanel('gpmc-execution-bottom', 36, 786, 1848, 220),
			...createDarkHeader('项目执行监控', 'execution'),
			createStrategicNumberCard('gpmc-execution-kpi-completion', '整体完成率', 58, 126, 260, 72.4, { suffix: '%', precision: 1, valueColor: '#22c3ff' }),
			createStrategicNumberCard('gpmc-execution-kpi-milestone', '里程碑达成率', 332, 126, 260, 68, { suffix: '%', valueColor: '#ffb74d' }),
			createStrategicNumberCard('gpmc-execution-kpi-overdue', '延期任务数', 606, 126, 260, 37, { valueColor: '#ff6b7a' }),
			createStrategicNumberCard('gpmc-execution-kpi-max-delay', '最大延期天数', 880, 126, 260, 28, { suffix: ' 天', valueColor: '#ff6b7a' }, [jumpAction(buildGpmcTemplateJump('/gpmc/drill/execution'))]),
			createStrategicNumberCard('gpmc-execution-kpi-due-soon', '近期到期', 1154, 126, 260, 15, { valueColor: STRATEGIC_TEXT }),
			createStrategicNumberCard('gpmc-execution-kpi-blocked', '阻塞链路', 1428, 126, 260, 6, { valueColor: '#ffb74d' }),
			createStrategicNumberCard('gpmc-execution-kpi-owner', '责任人聚焦', 1702, 126, 166, 12, { valueColor: '#54e3ff' }),
			createComponent('gpmc-execution-gantt', 'gantt-chart', '任务执行甘特', 56, 288, 860, 460, 15, {
				title: '任务执行甘特',
				renderMode: 'board',
				sideTextColor: '#e2e8f0',
				tasks: [
					{ name: '需求分析', type: '一般任务', planDate: '2026-01-06', actualDate: '2026-02-15', owner: '张伟', riskLevel: '低', majorProjectName: '制造协同平台', subprojectName: '平台基础', status: '已完成', isCompleted: true, isOverdue: false },
					{ name: '系统设计', type: '一般任务', planDate: '2026-02-10', actualDate: '2026-03-20', owner: '李娜', riskLevel: '低', majorProjectName: '制造协同平台', subprojectName: '平台基础', status: '已完成', isCompleted: true, isOverdue: false },
					{ name: '里程碑 M1', type: '里程碑节点', planDate: '2026-03-20', actualDate: '2026-03-20', owner: '张伟', riskLevel: '低', majorProjectName: '制造协同平台', status: '已完成', isCompleted: true, isOverdue: false },
					{ name: '核心开发', type: '一般任务', planDate: '2026-03-01', actualDate: '2026-05-30', owner: '陈宇峰', riskLevel: '中', majorProjectName: '制造协同平台', subprojectName: '核心模块', status: '进行中', isCompleted: false, isOverdue: false },
					{ name: '联调测试', type: '一般任务', planDate: '2026-05-15', actualDate: '2026-06-30', owner: '王薇', riskLevel: '中', majorProjectName: '制造协同平台', subprojectName: '核心模块', status: '进行中', isCompleted: false, isOverdue: false },
					{ name: '里程碑 M2', type: '里程碑节点', planDate: '2026-06-30', actualDate: '2026-06-30', owner: '张伟', riskLevel: '中', majorProjectName: '制造协同平台', status: '未开始' },
					{ name: '基建施工', type: '一般任务', planDate: '2026-01-15', actualDate: '2026-06-30', owner: '刘强', riskLevel: '高', majorProjectName: '新能源工厂', subprojectName: '厂房建设', status: '延期', isCompleted: false, isOverdue: true, isIncomplete: true, delayDays: 28 },
					{ name: '设备采购', type: '一般任务', planDate: '2026-03-01', actualDate: '2026-05-15', owner: '李明', riskLevel: '中', majorProjectName: '新能源工厂', subprojectName: '设备线', status: '进行中', isCompleted: false, isOverdue: false },
					{ name: '设备安装调试', type: '一般任务', planDate: '2026-05-01', actualDate: '2026-07-15', owner: '赵刚', riskLevel: '高', majorProjectName: '新能源工厂', subprojectName: '设备线', status: '延期', isCompleted: false, isOverdue: true, isIncomplete: true, delayDays: 18 },
					{ name: '里程碑 M3', type: '里程碑节点', planDate: '2026-07-15', actualDate: '2026-07-15', owner: '刘强', riskLevel: '高', majorProjectName: '新能源工厂', status: '未开始' },
					{ name: '数据建模', type: '一般任务', planDate: '2026-02-01', actualDate: '2026-04-10', owner: '孙丽', riskLevel: '低', majorProjectName: '数据治理工程', subprojectName: '主数据', status: '已完成', isCompleted: true, isOverdue: true, delayDays: 5 },
					{ name: '接口开发', type: '一般任务', planDate: '2026-04-01', actualDate: '2026-06-15', owner: '周涛', riskLevel: '中', majorProjectName: '数据治理工程', subprojectName: '主数据', status: '进行中', isCompleted: false, isOverdue: false },
				],
			}, [jumpAction(buildGpmcTemplateJump('/gpmc/drill/execution'))]),
			createBarChart(
				'gpmc-execution-stage',
				'阶段分布',
				976,
				288,
				430,
				202,
				['策划中', '执行中', '验收中', '已完成', '已暂停'],
				[{ name: '项目数', data: [18, 52, 14, 32, 6] }],
			),
			createBarChart(
				'gpmc-execution-workload',
				'责任科室负载',
				1424,
				288,
				440,
				202,
				['工程建设', '数字化', '研发', '生产制造', '采购'],
				[
					{ name: '在办任务', data: [24, 18, 22, 15, 12] },
					{ name: '延期任务', data: [8, 3, 6, 4, 2] },
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/execution'))],
			),
			createStrategicTable(
				'gpmc-execution-delay-top',
				'延期 TOP10 项目',
				976,
				548,
				888,
				200,
				['项目', '延期天数', '责任科室', '风险等级'],
				[
					['新能源工厂建设项目', 28, '工程建设中心', '高'],
					['核心器件研发验证项目', 21, '研发中心', '中'],
					['智能制造产线改造', 18, '生产制造中心', '高'],
					['供应链协同平台', 14, '采购中心', '中'],
					['质量追溯系统', 12, '质量管理部', '低'],
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/execution'))],
			),
			createStrategicTable(
				'gpmc-execution-block-chain',
				'任务阻塞链路与责任人分析',
				56,
				806,
				1808,
				180,
				['任务/节点', '阻塞原因', '责任人', '责任科室', '建议动作'],
				[
					['里程碑 M3', '外部审批滞后', '刘强', '工程建设中心', '升级协调'],
					['批量测试', '测试环境不稳定', '陈宇峰', '研发中心', '环境加固'],
					['采购签约', '供应商交期偏移', '李明', '采购中心', '切换备选供应商'],
					['主数据联调', '接口协议变更', '王薇', '数字化事业部', '补齐协议评审'],
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/execution'))],
			),
		],
	),
};

const qualityBoardTemplate: ScreenTemplate = {
	id: 'gpmc-quality-board',
	name: 'GPMC 质量信息与跟进措施',
	description: '管控层质量看板模板，覆盖问题规模、分类分布、闭环率与措施清单。',
	thumbnail: '🧪',
	category: 'project-management',
	tags: ['GPMC', '质量', '闭环', '跟进措施'],
	recommendedVariables: commonVariables.map((item) => item.key),
	config: strategicBoardConfig(
		'GPMC 质量信息与跟进措施',
		'质量信息与跟进措施',
		[
			createStrategicPanel('gpmc-quality-top', 36, 110, 1848, 140),
			createStrategicPanel('gpmc-quality-left', 36, 268, 860, 360),
			createStrategicPanel('gpmc-quality-right', 914, 268, 970, 360),
			createStrategicPanel('gpmc-quality-bottom', 36, 646, 1848, 360),
			...createDarkHeader('质量信息与跟进措施', 'quality'),
			createStrategicNumberCard('gpmc-quality-kpi-new', '新增质量问题', 58, 126, 260, 23, { valueColor: '#ff6b7a' }),
			createStrategicNumberCard('gpmc-quality-kpi-existing', '现存质量问题', 332, 126, 260, 47, { valueColor: '#ffb74d' }),
			createStrategicNumberCard('gpmc-quality-kpi-close', '归零完成率', 606, 126, 260, 68.3, { suffix: '%', precision: 1, valueColor: '#3ddc97' }),
			createStrategicNumberCard('gpmc-quality-kpi-no-plan', '未提交归零计划', 880, 126, 260, 8, { valueColor: '#ff6b7a' }, [jumpAction(buildGpmcTemplateJump('/gpmc/drill/quality'))]),
			createStrategicNumberCard('gpmc-quality-kpi-high', '高优未关', 1154, 126, 260, 37, { valueColor: '#ff6b7a' }),
			createStrategicNumberCard('gpmc-quality-kpi-measure', '措施覆盖率', 1428, 126, 260, 76, { suffix: '%', valueColor: '#54e3ff' }),
			createStrategicNumberCard('gpmc-quality-kpi-projects', '问题项目数', 1702, 126, 166, 15, { valueColor: STRATEGIC_TEXT }),
			createPieChart(
				'gpmc-quality-category',
				'质量问题分类',
				56,
				288,
				394,
				320,
				[
					{ name: '设计', value: 12 },
					{ name: '工艺', value: 8 },
					{ name: '管理', value: 6 },
					{ name: '元器件', value: 5 },
					{ name: '软件', value: 6 },
				],
			),
			createBarChart(
				'gpmc-quality-project-rank',
				'项目质量问题排名',
				468,
				288,
				408,
				320,
				['核心器件研发', '新能源工厂', '制造协同平台', '数据治理工程'],
				[{ name: '问题数', data: [14, 11, 9, 6] }],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/quality'))],
			),
			createLineChart(
				'gpmc-quality-close-trend',
				'质量闭环趋势',
				934,
				288,
				428,
				320,
				['1月', '2月', '3月', '4月', '5月', '6月'],
				[
					{ name: '新增问题', data: [32, 28, 35, 22, 25, 23] },
					{ name: '已闭环', data: [28, 30, 25, 32, 24, 18] },
				],
			),
			createStrategicTable(
				'gpmc-quality-measures',
				'质量问题与跟进措施摘要',
				1380,
				288,
				484,
				320,
				['项目', '问题', '状态', '建议措施'],
				[
					['核心器件研发', 'PCB 布线设计缺陷', '未完成归零', '补充专项措施与闭环交付物'],
					['新能源工厂', '元器件批次不良', '未完成归零', '质量复盘 + 供应商纠偏'],
					['供应链平台', '接口协议不一致', '已完成管理归零', '沉淀接口协议基线'],
					['制造协同平台', '测试覆盖不足', '未完成归零', '扩充自动化测试集'],
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/quality'))],
			),
			createStrategicTable(
				'gpmc-quality-detail',
				'质量问题清单',
				56,
				666,
				1808,
				320,
				['项目', '问题', '分类', '状态', '滞留天数'],
				[
					['核心器件研发', 'PCB布线设计缺陷', '设计', '未完成归零', '15'],
					['智能制造产线', '焊接工艺参数偏差', '工艺', '已完成技术归零', '0'],
					['新能源工厂', '控制器元器件批次不良', '元器件', '未完成归零', '22'],
					['供应链平台', '接口协议不一致', '软件', '已完成管理归零', '0'],
					['制造协同平台', '测试用例覆盖不足', '管理', '未完成归零', '8'],
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/quality'))],
			),
		],
	),
};

const techStateBoardTemplate: ScreenTemplate = {
	id: 'gpmc-tech-state-board',
	name: 'GPMC 技术状态与跟进',
	description: '管控层技术状态看板模板，覆盖技术状态变更、签署完成率和措施闭环。',
	thumbnail: '🧩',
	category: 'project-management',
	tags: ['GPMC', '技术状态', '签署', '跟进'],
	recommendedVariables: commonVariables.map((item) => item.key),
	config: strategicBoardConfig(
		'GPMC 技术状态与跟进',
		'技术状态与跟进',
		[
			createStrategicPanel('gpmc-tech-top', 36, 110, 1848, 140),
			createStrategicPanel('gpmc-tech-left', 36, 268, 860, 360),
			createStrategicPanel('gpmc-tech-right', 914, 268, 970, 360),
			createStrategicPanel('gpmc-tech-bottom', 36, 646, 1848, 360),
			...createDarkHeader('技术状态与跟进', 'tech-state'),
			createStrategicNumberCard('gpmc-tech-kpi-change', '技术状态变更数', 58, 126, 260, 18, { valueColor: '#ffb74d' }),
			createStrategicNumberCard('gpmc-tech-kpi-sign', '文件签署完成率', 332, 126, 260, 82, { suffix: '%', valueColor: '#3ddc97' }),
			createStrategicNumberCard('gpmc-tech-kpi-pending', '未闭环项', 606, 126, 260, 7, { valueColor: '#ff6b7a' }),
			createStrategicNumberCard('gpmc-tech-kpi-cover', '措施覆盖率', 880, 126, 260, 76, { suffix: '%', valueColor: '#54e3ff' }),
			createStrategicNumberCard('gpmc-tech-kpi-level1', 'Ⅰ类更改', 1154, 126, 260, 4, { valueColor: '#ff6b7a' }),
			createStrategicNumberCard('gpmc-tech-kpi-unsigned', '未签署项', 1428, 126, 260, 2, { valueColor: '#ffb74d' }),
			createStrategicNumberCard('gpmc-tech-kpi-projects', '涉及项目', 1702, 126, 166, 9, { valueColor: STRATEGIC_TEXT }),
			createStrategicTable(
				'gpmc-tech-change-list',
				'技术状态变更清单',
				56,
				288,
				840,
				320,
				['技术状态', '项目', '更改类别', '签署状态'],
				[
					['控制器硬件版本升级', '核心器件研发', 'Ⅰ类', '已签署'],
					['通信协议变更', '制造协同平台', 'Ⅱ类', '评审中'],
					['结构件材料替代', '新能源工厂', 'Ⅰ类', '已签署'],
					['软件架构调整', '数据治理工程', 'Ⅱ类', '未评审'],
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/tech-state'))],
			),
			createPieChart(
				'gpmc-tech-type-pie',
				'更改类别分布',
				934,
				288,
				430,
				320,
				[
					{ name: 'Ⅰ类', value: 4 },
					{ name: 'Ⅱ类', value: 9 },
					{ name: 'Ⅲ类', value: 5 },
				],
			),
			createPieChart(
				'gpmc-tech-sign-pie',
				'签署状态分布',
				1380,
				288,
				484,
				320,
				[
					{ name: '已签署', value: 12 },
					{ name: '评审中', value: 4 },
					{ name: '未评审', value: 2 },
				],
			),
			createStrategicTable(
				'gpmc-tech-measures',
				'技术状态跟进动作',
				56,
				666,
				1808,
				320,
				['技术状态', '项目', '跟进动作', '当前状态'],
				[
					['控制器硬件版本升级', '核心器件研发', '进入签署归档与闭环确认', '已签署'],
					['通信协议变更', '制造协同平台', '继续评审并补充签署资料', '评审中'],
					['结构件材料替代', '新能源工厂', '闭环归档与影响跟踪', '已签署'],
					['软件架构调整', '数据治理工程', '补发评审通知并更新状态', '未评审'],
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/tech-state'))],
			),
		],
	),
};

const costBoardTemplate: ScreenTemplate = {
	id: 'gpmc-cost-board',
	name: 'GPMC 成本与预算控制',
	description: '管控层成本看板模板，覆盖预算执行、偏差、月度支出和部门控制。',
	thumbnail: '💰',
	category: 'project-management',
	tags: ['GPMC', '成本', '预算', '执行率'],
	recommendedVariables: commonVariables.map((item) => item.key),
	config: strategicBoardConfig(
		'GPMC 成本与预算控制',
		'成本与预算控制',
		[
			createStrategicPanel('gpmc-cost-top', 36, 110, 1848, 140),
			createStrategicPanel('gpmc-cost-left', 36, 268, 900, 360),
			createStrategicPanel('gpmc-cost-right', 954, 268, 930, 360),
			createStrategicPanel('gpmc-cost-bottom', 36, 646, 1848, 360),
			...createDarkHeader('成本与预算控制', 'cost'),
			createStrategicNumberCard('gpmc-cost-kpi-budget', '年度预算总额', 58, 126, 260, 36.8, { suffix: ' 亿', precision: 1, valueColor: STRATEGIC_TEXT }),
			createStrategicNumberCard('gpmc-cost-kpi-actual', '累计执行额', 332, 126, 260, 26.1, { suffix: ' 亿', precision: 1, valueColor: '#22c3ff' }),
			createStrategicNumberCard('gpmc-cost-kpi-rate', '预算执行率', 606, 126, 260, 70.9, { suffix: '%', precision: 1, valueColor: '#3ddc97' }),
			createStrategicNumberCard('gpmc-cost-kpi-gap', '预算偏差', 880, 126, 260, 0.72, { suffix: ' 亿', precision: 2, valueColor: '#ff6b7a' }),
			createStrategicNumberCard('gpmc-cost-kpi-eff', '预算效率', 1154, 126, 260, 0.93, { precision: 2, valueColor: '#54e3ff' }),
			createStrategicNumberCard('gpmc-cost-kpi-burn', '燃尽率', 1428, 126, 260, 71, { suffix: '%', valueColor: '#ffb74d' }),
			createStrategicNumberCard('gpmc-cost-kpi-projects', '偏差项目数', 1702, 126, 166, 8, { valueColor: '#ff6b7a' }),
			withConfig(
				createLineChart(
				'gpmc-cost-monthly',
				'月度支出与偏差趋势',
				56,
				288,
				860,
				320,
				['1月', '2月', '3月', '4月', '5月', '6月'],
				[
					{ name: '计划支出', data: [2.8, 3.1, 3.5, 3.2, 3.6, 3.8] },
					{ name: '实际支出', data: [2.6, 3.3, 3.8, 3.0, 3.4, 3.7] },
					{ name: '预算偏差', data: [-0.2, 0.2, 0.3, -0.2, -0.2, -0.1] },
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/cost'))],
				),
				{
					seriesColors: ['#54e3ff', '#3ddc97', '#ffb74d'],
				},
			),
			createBarChart(
				'gpmc-cost-dept-budget',
				'部门预算执行',
				954,
				288,
				430,
				320,
				['工程建设', '数字化', '研发', '生产制造', '采购'],
				[
					{ name: '预算', data: [9.5, 6.0, 8.2, 10.0, 3.1] },
					{ name: '实际', data: [8.6, 5.2, 6.8, 10.4, 2.8] },
				],
			),
			createStrategicTable(
				'gpmc-cost-rank',
				'成本偏差预警榜',
				1400,
				288,
				464,
				320,
				['项目', '偏差', '执行状态', '预警'],
				[
					['新能源工厂建设项目', '+0.40 亿', '超预算', '高'],
					['供应链数字化转型项目', '+0.12 亿', '超预算', '中'],
					['集团制造协同平台二期', '-0.04 亿', '可控', '低'],
					['核心器件研发验证项目', '-0.12 亿', '可控', '低'],
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/cost'))],
			),
			createStrategicTable(
				'gpmc-cost-detail',
				'项目预算执行明细',
				56,
				666,
				1808,
				320,
				['项目', '责任科室', '预算', '实际', '执行率', '偏差'],
				[
					['工程建设中心', '工程建设中心', '9.5 亿', '8.6 亿', '90.5%', '-0.9 亿'],
					['数字化事业部', '数字化事业部', '6.0 亿', '5.2 亿', '86.7%', '-0.8 亿'],
					['研发中心', '研发中心', '8.2 亿', '6.8 亿', '82.9%', '-1.4 亿'],
					['生产制造中心', '生产制造中心', '10.0 亿', '10.4 亿', '104.0%', '+0.4 亿'],
					['采购中心', '采购中心', '3.1 亿', '2.8 亿', '90.3%', '-0.3 亿'],
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/cost'))],
			),
		],
	),
};

const riskBoardTemplate: ScreenTemplate = {
	id: 'gpmc-risk-board',
	name: 'GPMC 风险与预警中心',
	description: '管控层风险看板模板，覆盖风险矩阵、分类分布、清单和措施摘要。',
	thumbnail: '🚨',
	category: 'project-management',
	tags: ['GPMC', '风险', '预警', '矩阵'],
	recommendedVariables: commonVariables.map((item) => item.key),
	config: strategicBoardConfig(
		'GPMC 风险与预警中心',
		'风险与预警中心',
		[
			createStrategicPanel('gpmc-risk-top', 36, 110, 1848, 140),
			createStrategicPanel('gpmc-risk-left', 36, 268, 860, 360),
			createStrategicPanel('gpmc-risk-right', 914, 268, 970, 360),
			createStrategicPanel('gpmc-risk-bottom', 36, 646, 1848, 360),
			...createDarkHeader('风险与预警中心', 'risk'),
			createStrategicNumberCard('gpmc-risk-kpi-total', '风险总数', 58, 126, 260, 89, { valueColor: STRATEGIC_TEXT }),
			createStrategicNumberCard('gpmc-risk-kpi-high', '高风险', 332, 126, 260, 14, { valueColor: '#ff6b7a' }),
			createStrategicNumberCard('gpmc-risk-kpi-mid', '中风险', 606, 126, 260, 31, { valueColor: '#ffb74d' }),
			createStrategicNumberCard('gpmc-risk-kpi-close', '风险闭环率', 880, 126, 260, 62.4, { suffix: '%', precision: 1, valueColor: '#3ddc97' }),
			createStrategicNumberCard('gpmc-risk-kpi-freq', '变更频率', 1154, 126, 260, 3.2, { suffix: ' 次/周', precision: 1, valueColor: '#54e3ff' }),
			createStrategicNumberCard('gpmc-risk-kpi-days', '平均闭环天数', 1428, 126, 260, 18.5, { suffix: ' 天', precision: 1, valueColor: '#ffb74d' }),
			createStrategicNumberCard('gpmc-risk-kpi-alert', '预警项目数', 1702, 126, 166, 12, { valueColor: '#ff6b7a' }),
			createBarChart(
				'gpmc-risk-matrix',
				'风险矩阵（概率 × 影响）',
				56,
				288,
				860,
				320,
				['高×高', '高×中', '中×高', '中×中', '低×中', '低×低'],
				[{ name: '风险数', data: [4, 6, 5, 12, 10, 15] }],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/risk'))],
			),
			createPieChart(
				'gpmc-risk-category',
				'风险分类分布',
				934,
				288,
				430,
				320,
				[
					{ name: '技术风险', value: 28 },
					{ name: '进度风险', value: 22 },
					{ name: '成本风险', value: 15 },
					{ name: '外协风险', value: 12 },
					{ name: '质量风险', value: 8 },
				],
			),
			createStrategicTable(
				'gpmc-risk-measure',
				'风险与措施摘要',
				1380,
				288,
				484,
				320,
				['项目', '风险', '等级', '措施'],
				[
					['核心器件研发', '核心芯片供货延迟', '高', '启用备选供应商'],
					['新能源工厂', '施工许可证审批滞后', '高', '加速审批协调'],
					['制造协同平台', '关键技术人员流失', '中', '激励方案 + 备份人员'],
					['供应链平台', '原材料价格上涨', '中', '锁价合同'],
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/risk'))],
			),
			createStrategicTable(
				'gpmc-risk-detail',
				'风险清单',
				56,
				666,
				1808,
				320,
				['风险', '项目', '等级', '状态', '滞留天数', '应对措施'],
				[
					['核心芯片供货延迟', '核心器件研发', '高', '跟进中', '35', '启用备选供应商'],
					['施工许可证审批滞后', '新能源工厂', '高', '跟进中', '28', '加速审批协调'],
					['关键技术人员流失', '制造协同平台', '中', '跟进中', '15', '激励方案 + 备份人员'],
					['原材料价格上涨', '供应链平台', '中', '已闭环', '0', '锁价合同'],
					['测试环境不稳定', '数据治理工程', '低', '跟进中', '8', '环境容器化'],
				],
				[jumpAction(buildGpmcTemplateJump('/gpmc/drill/risk'))],
			),
		],
	),
};

export const gpmcTemplates: ScreenTemplate[] = [
	strategicOverviewTemplate,
	executionBoardTemplate,
	qualityBoardTemplate,
	techStateBoardTemplate,
	costBoardTemplate,
	riskBoardTemplate,
];
