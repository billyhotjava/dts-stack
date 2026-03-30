// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import type { DataSourceConfig, ScreenComponent, ScreenComponentAction, ScreenGlobalVariable, ScreenPage } from './types';
import type { ScreenTemplate } from './screenTemplates';
import { SCREEN_SCHEMA_VERSION } from './specV2';

const SCREEN_WIDTH = 1920;
const SCREEN_HEIGHT = 1080;
const BG = '#eaf2fb';
const HEADER_BAR_BG = '#044B8C';
const PANEL_BG = '#ffffff';
const PANEL_BORDER = 'rgba(148, 163, 184, 0.24)';
const TITLE_COLOR = '#0f172a';
const SUBTITLE_COLOR = '#334155';
const BODY_COLOR = '#334155';
const ACCENT = '#044B8C';
const INPUT_BG = 'rgba(255, 255, 255, 0.18)';
const INPUT_BORDER = 'rgba(255, 255, 255, 0.32)';
const KPI_BG = '#ffffff';
const LINE_SERIES_COLORS = ['#044B8C', '#2f7bc4', '#f0a33e'];
const BAR_SERIES_COLORS = ['#044B8C', '#2f7bc4', '#f0a33e'];
const PIE_SERIES_COLORS = ['#044B8C', '#2f7bc4', '#5aa6d6', '#d86d5f', '#7a8fb8'];
const TABLE_HEADER_BG = '#dbe9f6';
const TABLE_BODY_BG = 'rgba(255, 255, 255, 0.98)';
const TABLE_EVEN_ROW_BG = '#f5f9fd';

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
    dataSource?: DataSourceConfig,
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
        ...(dataSource ? { dataSource } : {}),
        ...(actions?.length ? { actions } : {}),
    };
}

function jumpAction(urlTemplate: string): ScreenComponentAction {
    return { type: 'jump-url', jumpUrlTemplate: urlTemplate, jumpOpenMode: 'new-tab' };
}

function buildCockpitJumpUrl(
    theme: 'overview' | 'execution' | 'risk' | 'tree',
    overrides: Record<string, string> = {},
): string {
    const params = new Map<string, string>([
        ['theme', theme],
        ['majorProjectId', '{{majorProjectId}}'],
        ['dateFrom', '{{dateFrom}}'],
        ['dateTo', '{{dateTo}}'],
        ['deptId', '{{deptId}}'],
        ['riskLevel', '{{riskLevel}}'],
    ]);
    Object.entries(overrides).forEach(([key, value]) => {
        params.set(key, value);
    });
    return `/analytics/project-cockpit?${Array.from(params.entries())
        .map(([key, value]) => `${key}=${value}`)
        .join('&')}`;
}

function withActions(component: ScreenComponent, ...acts: ScreenComponentAction[]): ScreenComponent {
    return { ...component, actions: acts };
}

function createVariable(
    key: string,
    label: string,
    type: ScreenGlobalVariable['type'],
): ScreenGlobalVariable {
    return { key, label, type, defaultValue: '' };
}

function createTitle(id: string, text: string, x: number, y: number, width: number, fontSize = 38): ScreenComponent {
    return createComponent(id, 'title', text, x, y, width, 48, 60, {
        text,
        fontSize,
        fontWeight: '700',
        color: '#ffffff',
        textAlign: 'center',
    });
}

function createSubtitle(id: string, text: string, x: number, y: number, width: number): ScreenComponent {
    return createComponent(id, 'title', text, x, y, width, 24, 60, {
        text,
        fontSize: 15,
        fontWeight: '500',
        color: 'rgba(255,255,255,0.8)',
        textAlign: 'center',
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

function createFilterInput(id: string, label: string, variableKey: string, x: number, y: number, width: number): ScreenComponent {
    return createComponent(id, 'filter-input', label, x, y, width, 58, 65, {
        label,
        variableKey,
        placeholder: '全部',
        labelColor: 'rgba(255,255,255,0.85)',
        inputBackground: INPUT_BG,
        inputBorderColor: INPUT_BORDER,
        inputTextColor: '#ffffff',
    });
}

function buildFilterOptionsDataSource(responsePath: string): DataSourceConfig {
    return buildScreenApiDataSource('/analytics/api/project-cockpit/screen/overview', responsePath);
}

function createFilterSelect(
    id: string,
    label: string,
    variableKey: string,
    x: number,
    y: number,
    width: number,
    filterResponsePath: string,
): ScreenComponent {
    return createComponent(id, 'filter-select', label, x, y, width, 58, 65, {
        label,
        variableKey,
        placeholder: '全部',
        optionSourceMode: 'data',
        dataOptionValueField: 'value',
        dataOptionLabelField: 'label',
        labelColor: 'rgba(255,255,255,0.85)',
        inputBackground: INPUT_BG,
        inputBorderColor: INPUT_BORDER,
        inputTextColor: '#ffffff',
    }, buildFilterOptionsDataSource(filterResponsePath));
}

function createRiskSelect(id: string, x: number, y: number, width: number): ScreenComponent {
    return createComponent(id, 'filter-select', '风险等级', x, y, width, 58, 65, {
        label: '风险等级',
        variableKey: 'riskLevel',
        placeholder: '全部',
        options: [
            { label: '高', value: '高' },
            { label: '中', value: '中' },
            { label: '低', value: '低' },
        ],
        labelColor: 'rgba(255,255,255,0.85)',
        inputBackground: INPUT_BG,
        inputBorderColor: INPUT_BORDER,
        inputTextColor: '#ffffff',
    });
}

function createDateRange(id: string, x: number, y: number, width: number): ScreenComponent {
    return createComponent(id, 'filter-date-range', '统计周期', x, y, width, 58, 65, {
        label: '统计周期',
        startKey: 'dateFrom',
        endKey: 'dateTo',
        labelColor: 'rgba(255,255,255,0.85)',
        inputBackground: INPUT_BG,
        inputBorderColor: INPUT_BORDER,
        inputTextColor: '#ffffff',
    });
}

function createDatetime(id: string): ScreenComponent {
    return createComponent(id, 'datetime', '系统时间', 1670, 30, 210, 36, 65, {
        format: 'YYYY-MM-DD HH:mm:ss',
        fontSize: 18,
        color: '#ffffff',
    });
}

function buildScreenApiDataSource(url: string, responsePath?: string): DataSourceConfig {
    return {
        type: 'api',
        sourceType: 'api',
        refreshInterval: 60,
        apiConfig: {
            url,
            method: 'GET',
            params: {
                majorProjectId: '{{majorProjectId}}',
                dateFrom: '{{dateFrom}}',
                dateTo: '{{dateTo}}',
                deptId: '{{deptId}}',
                riskLevel: '{{riskLevel}}',
            },
            ...(responsePath ? { responsePath } : {}),
        },
    };
}

function createNumberCard(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    responsePath: string,
    suffix = '',
): ScreenComponent {
    return createComponent(id, 'number-card', title, x, y, width, 118, 10, {
        title,
        value: 0,
        suffix,
        precision: suffix === '%' ? 2 : 0,
        valueField: 'value',
        titleColor: SUBTITLE_COLOR,
        valueColor: TITLE_COLOR,
        backgroundColor: KPI_BG,
    }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/overview', responsePath));
}

function createExecutionNumberCard(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    responsePath: string,
    suffix = '',
): ScreenComponent {
    return createComponent(id, 'number-card', title, x, y, width, 106, 10, {
        title,
        value: 0,
        suffix,
        precision: suffix === '%' ? 2 : 0,
        valueField: 'value',
        titleColor: SUBTITLE_COLOR,
        valueColor: TITLE_COLOR,
        backgroundColor: KPI_BG,
    }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/execution', responsePath));
}

function createRiskNumberCard(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    responsePath: string,
    suffix = '',
): ScreenComponent {
    return createComponent(id, 'number-card', title, x, y, width, 118, 10, {
        title,
        value: 0,
        suffix,
        precision: suffix === '%' ? 2 : 0,
        valueField: 'value',
        titleColor: SUBTITLE_COLOR,
        valueColor: TITLE_COLOR,
        backgroundColor: KPI_BG,
    }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/risk', responsePath));
}

function createApiSummaryCard(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    height: number,
    url: string,
    responsePath: string,
    valueField: string,
    suffix = '',
): ScreenComponent {
    return createComponent(id, 'number-card', title, x, y, width, height, 10, {
        title,
        value: 0,
        suffix,
        precision: suffix === '%' ? 2 : 0,
        valueField,
        titleColor: SUBTITLE_COLOR,
        valueColor: TITLE_COLOR,
        backgroundColor: KPI_BG,
    }, buildScreenApiDataSource(url, responsePath));
}

function createLineChart(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    height: number,
    url: string,
    responsePath: string,
    xAxisField: string,
    series: Array<{ field: string; name: string }>,
): ScreenComponent {
    return createComponent(id, 'line-chart', title, x, y, width, height, 10, {
        title,
        xAxisField,
        series,
        seriesColors: LINE_SERIES_COLORS,
    }, buildScreenApiDataSource(url, responsePath));
}

function createBarChart(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    height: number,
    url: string,
    responsePath: string,
    xAxisField: string,
    series: Array<{ field: string; name: string }>,
): ScreenComponent {
    return createComponent(id, 'bar-chart', title, x, y, width, height, 10, {
        title,
        xAxisField,
        series,
        seriesColors: BAR_SERIES_COLORS,
    }, buildScreenApiDataSource(url, responsePath));
}

function createPieChart(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    height: number,
    url: string,
    responsePath: string,
): ScreenComponent {
    return createComponent(id, 'pie-chart', title, x, y, width, height, 10, {
        title,
        nameField: 'name',
        valueField: 'value',
        seriesColors: PIE_SERIES_COLORS,
    }, buildScreenApiDataSource(url, responsePath));
}

function createRadarChart(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    height: number,
    url: string,
    responsePath: string,
): ScreenComponent {
    return createComponent(id, 'radar-chart', title, x, y, width, height, 10, {
        title,
        indicator: [
            { name: '完成率', max: 100 },
            { name: '按时完成率', max: 100 },
            { name: '里程碑完成率', max: 100 },
            { name: '风险控制', max: 100 },
            { name: '执行力', max: 100 },
        ],
        data: [0, 0, 0, 0, 0],
        seriesColors: ['#3b82f6', '#0ea5e9', '#f59e0b'],
        _fieldMapping: {
            dimension: 'name',
            measures: ['score'],
        },
    }, buildScreenApiDataSource(url, responsePath));
}

function createApiTable(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    height: number,
    url: string,
    responsePath: string,
    columns: Array<{ source: string; alias: string; align?: 'left' | 'center' | 'right'; wrap?: boolean }>,
    extraConfig: Record<string, unknown> = {},
): ScreenComponent {
    return createComponent(id, 'table', title, x, y, width, height, 10, {
        title,
        fields: columns.map((item) => item.source),
        columns: columns.map((item) => ({
            source: item.source,
            alias: item.alias,
            align: item.align ?? (item.source === 'name' || item.source.includes('Name') || item.source === 'status' || item.source === 'reason'
                ? 'left'
                : 'center'),
            wrap: item.wrap ?? (item.source === 'name' || item.source.includes('Name') || item.source === 'status' || item.source === 'reason'),
        })),
        header: [],
        data: [],
        fontSize: 11,
        headerColor: TITLE_COLOR,
        headerBackground: TABLE_HEADER_BG,
        bodyColor: BODY_COLOR,
        bodyBackground: TABLE_BODY_BG,
        oddRowBackground: TABLE_BODY_BG,
        evenRowBackground: TABLE_EVEN_ROW_BG,
        borderColor: PANEL_BORDER,
        enableSort: false,
        enablePagination: false,
        freezeHeader: true,
        ...extraConfig,
    }, buildScreenApiDataSource(url, responsePath));
}

function createTable(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    height: number,
    url: string,
    responsePath: string,
    fields: string[],
): ScreenComponent {
    return createApiTable(
        id,
        title,
        x,
        y,
        width,
        height,
        url,
        responsePath,
        fields.map((field) => ({ source: field, alias: field })),
        { enableSort: true, fontSize: 12 },
    );
}

type KpiDef = {
    id: string;
    title: string;
    url: string;
    responsePath: string;
    suffix?: string;
    jumpUrlTemplate?: string;
};

/**
 * Generate a row of evenly-spaced KPI cards filling the full panel width.
 * x: 56..1864 (inside 32px panel padding) = 1808px usable
 */
function createKpiRow(defs: KpiDef[], y: number, height: number): ScreenComponent[] {
    const startX = 56;
    const endX = 1864;
    const totalWidth = endX - startX;
    const gap = 16;
    const cardWidth = Math.floor((totalWidth - (defs.length - 1) * gap) / defs.length);
    return defs.map((def, i) => {
        const x = startX + i * (cardWidth + gap);
        const card = createComponent(def.id, 'number-card', def.title, x, y, cardWidth, height, 10, {
            title: def.title,
            value: 0,
            suffix: def.suffix || '',
            precision: def.suffix === '%' ? 2 : 0,
            valueField: 'value',
            titleColor: SUBTITLE_COLOR,
            valueColor: TITLE_COLOR,
            backgroundColor: KPI_BG,
        }, buildScreenApiDataSource(def.url, def.responsePath));
        return def.jumpUrlTemplate ? withActions(card, jumpAction(def.jumpUrlTemplate)) : card;
    });
}

function createCommonHeader(pageTitle: string, pageIndex: number): ScreenComponent[] {
    // Layout: header bar background + left=filters | center=title | right=date+time
    return [
        // Header bar background
        createComponent(`pmcc-header-bar-${pageIndex}`, 'border-box', '标题栏背景', 0, 0, SCREEN_WIDTH, 100, 50, {
            backgroundColor: HEADER_BAR_BG,
            borderWidth: 0,
            borderRadius: 0,
        }),
        // Left: filters (dropdown selects with dynamic options from API)
        createFilterSelect(`pmcc-major-${pageIndex}`, '项目', 'majorProjectId', 32, 24, 180, 'filters.majorProjects'),
        createFilterSelect(`pmcc-dept-${pageIndex}`, '责任科室', 'deptId', 228, 24, 180, 'filters.depts'),
        createRiskSelect(`pmcc-risk-${pageIndex}`, 424, 24, 150),
        // Center: title
        createTitle(`pmcc-title-${pageIndex}`, '项目运营管理大屏', 680, 20, 560),
        createSubtitle(`pmcc-subtitle-${pageIndex}`, `第 ${pageIndex + 1} 屏 · ${pageTitle}`, 680, 66, 560),
        // Right: date range + datetime
        createDateRange(`pmcc-date-${pageIndex}`, 1310, 24, 310),
        createDatetime(`pmcc-datetime-${pageIndex}`),
    ];
}

function buildOverviewPage(): ScreenPage {
    return {
        id: 'pmcc-page-overview',
        name: '总体态势',
        backgroundColor: BG,
        components: [
            createPanel('pmcc-overview-bg-top', 32, 110, 1856, 148),
            createPanel('pmcc-overview-bg-left', 32, 280, 930, 760),
            createPanel('pmcc-overview-bg-right', 988, 280, 900, 760),
            ...createCommonHeader('总体态势', 0),
            ...createKpiRow([
                { id: 'pmcc-ov-kpi-total', title: '本周期节点总数', url: '/analytics/api/project-cockpit/screen/overview', responsePath: 'kpis.0', jumpUrlTemplate: buildCockpitJumpUrl('overview', { drillTarget: 'completion' }) },
                { id: 'pmcc-ov-kpi-due', title: '已到期节点', url: '/analytics/api/project-cockpit/screen/overview', responsePath: 'kpis.2', jumpUrlTemplate: buildCockpitJumpUrl('overview', { drillTarget: 'overdue' }) },
                { id: 'pmcc-ov-kpi-completed', title: '节点完成总数', url: '/analytics/api/project-cockpit/screen/overview', responsePath: 'kpis.7', jumpUrlTemplate: buildCockpitJumpUrl('overview', { drillTarget: 'completion' }) },
                { id: 'pmcc-ov-kpi-rate', title: '节点完成率', url: '/analytics/api/project-cockpit/screen/overview', responsePath: 'kpis.8', suffix: '%', jumpUrlTemplate: buildCockpitJumpUrl('overview', { drillTarget: 'completion' }) },
                { id: 'pmcc-ov-kpi-ontime', title: '按时完成率', url: '/analytics/api/project-cockpit/screen/overview', responsePath: 'kpis.9', suffix: '%', jumpUrlTemplate: buildCockpitJumpUrl('overview', { drillTarget: 'completion' }) },
                { id: 'pmcc-ov-kpi-overdue', title: '超期完成率', url: '/analytics/api/project-cockpit/screen/overview', responsePath: 'kpis.10', suffix: '%', jumpUrlTemplate: buildCockpitJumpUrl('overview', { drillTarget: 'completion' }) },
                { id: 'pmcc-ov-kpi-milestone-rate', title: '里程碑完成率', url: '/analytics/api/project-cockpit/screen/execution', responsePath: 'milestoneKpis.3', suffix: '%', jumpUrlTemplate: buildCockpitJumpUrl('execution', { drillTarget: 'milestone' }) },
            ], 128, 118),
            withActions(createLineChart(
                'pmcc-overview-weekly',
                '周度推进态势',
                56,
                304,
                878,
                320,
                '/analytics/api/project-cockpit/screen/overview',
                'weekly',
                'weekLabel',
                [
                    { field: 'completionRate', name: '完成率' },
                    { field: 'delayedNodes', name: '延期节点' },
                    { field: 'highRiskNodes', name: '高风险节点' },
                ],
            ), jumpAction(buildCockpitJumpUrl('overview', { drillTarget: 'completion' }))),
            withActions(createBarChart(
                'pmcc-overview-ranking',
                '重点项目排名',
                56,
                648,
                878,
                360,
                '/analytics/api/project-cockpit/screen/overview',
                'ranking',
                'majorProjectName',
                [
                    { field: 'highRiskCount', name: '高风险' },
                    { field: 'overdueCount', name: '延期' },
                ],
            ), jumpAction(buildCockpitJumpUrl('tree', { majorProjectId: '{{data.majorProjectId}}' }))),
            withActions(createPieChart(
                'pmcc-overview-completion-ring',
                '完成结构',
                1012,
                304,
                380,
                230,
                '/analytics/api/project-cockpit/screen/overview',
                'completionBreakdown',
            ), jumpAction(buildCockpitJumpUrl('overview', { drillTarget: 'completion' }))),
            withActions(createRadarChart(
                'pmcc-overview-health-radar',
                '健康度雷达',
                1012,
                552,
                380,
                232,
                '/analytics/api/project-cockpit/screen/overview',
                'healthRadar',
            ), jumpAction(buildCockpitJumpUrl('overview', { drillTarget: 'completion' }))),
            withActions(createApiTable(
                'pmcc-overview-spotlight',
                '重点盯防项目',
                1012,
                802,
                380,
                206,
                '/analytics/api/project-cockpit/screen/overview',
                'spotlight',
                [
                    { source: 'majorProjectName', alias: '项目' },
                    { source: 'highRiskCount', alias: '高风险' },
                    { source: 'delayCount', alias: '延期' },
                    { source: 'nextMilestone', alias: '下一里程碑', align: 'left', wrap: true },
                ],
            ), jumpAction(buildCockpitJumpUrl('tree'))),
            withActions(createTable(
                'pmcc-overview-alerts',
                '重点预警清单',
                1410,
                304,
                454,
                704,
                '/analytics/api/project-cockpit/screen/overview',
                'alerts',
                ['title', 'majorProjectName', 'riskLevel', 'delayDays', 'reason'],
            ), jumpAction(buildCockpitJumpUrl('overview', { drillTarget: 'overdue' }))),
        ],
    };
}

function buildExecutionPage(): ScreenPage {
    return {
        id: 'pmcc-page-execution',
        name: '执行与里程碑',
        backgroundColor: BG,
        components: [
            createPanel('pmcc-execution-bg-top', 32, 110, 1856, 136),
            createPanel('pmcc-execution-bg-left', 32, 262, 930, 778),
            createPanel('pmcc-execution-bg-right-top', 988, 262, 900, 370),
            createPanel('pmcc-execution-bg-right-bottom', 988, 650, 900, 390),
            ...createCommonHeader('执行与里程碑', 1),
            ...createKpiRow([
                { id: 'pmcc-ex-kpi-high', title: '未完成高风险', url: '/analytics/api/project-cockpit/screen/execution', responsePath: 'incompleteKpis.0', jumpUrlTemplate: buildCockpitJumpUrl('risk', { drillTarget: 'high-risk' }) },
                { id: 'pmcc-ex-kpi-mid', title: '未完成中风险', url: '/analytics/api/project-cockpit/screen/execution', responsePath: 'incompleteKpis.1', jumpUrlTemplate: buildCockpitJumpUrl('risk', { drillTarget: 'high-risk' }) },
                { id: 'pmcc-ex-kpi-ms-open', title: '未完成里程碑', url: '/analytics/api/project-cockpit/screen/execution', responsePath: 'incompleteKpis.2', jumpUrlTemplate: buildCockpitJumpUrl('execution', { drillTarget: 'milestone' }) },
                { id: 'pmcc-ex-kpi-major', title: '未完成重大节点', url: '/analytics/api/project-cockpit/screen/execution', responsePath: 'incompleteKpis.3', jumpUrlTemplate: buildCockpitJumpUrl('execution', { drillTarget: 'overdue' }) },
                { id: 'pmcc-ex-kpi-important', title: '未完成重要节点', url: '/analytics/api/project-cockpit/screen/execution', responsePath: 'incompleteKpis.4', jumpUrlTemplate: buildCockpitJumpUrl('execution', { drillTarget: 'overdue' }) },
                { id: 'pmcc-ex-kpi-ms-ontime', title: '里程碑按时完成', url: '/analytics/api/project-cockpit/screen/execution', responsePath: 'milestoneKpis.0', jumpUrlTemplate: buildCockpitJumpUrl('execution', { drillTarget: 'milestone' }) },
                { id: 'pmcc-ex-kpi-ms-overdue', title: '里程碑超期完成', url: '/analytics/api/project-cockpit/screen/execution', responsePath: 'milestoneKpis.1', jumpUrlTemplate: buildCockpitJumpUrl('execution', { drillTarget: 'milestone' }) },
                { id: 'pmcc-ex-kpi-ms-pending', title: '里程碑待完成', url: '/analytics/api/project-cockpit/screen/execution', responsePath: 'milestoneKpis.2', jumpUrlTemplate: buildCockpitJumpUrl('execution', { drillTarget: 'milestone' }) },
                { id: 'pmcc-ex-kpi-ms-rate', title: '里程碑完成率', url: '/analytics/api/project-cockpit/screen/execution', responsePath: 'milestoneKpis.3', suffix: '%', jumpUrlTemplate: buildCockpitJumpUrl('execution', { drillTarget: 'milestone' }) },
            ], 126, 106),
            withActions(createComponent('pmcc-execution-gantt', 'gantt-chart', '任务甘特图', 56, 286, 878, 520, 10, {
                title: '任务甘特图',
                renderMode: 'board',
                nameField: 'name',
                startField: 'planDate',
                endField: 'actualDate',
                categoryField: 'majorProjectName',
                statusField: 'riskLevel',
            }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/execution', 'ganttTasks')), jumpAction(buildCockpitJumpUrl('execution', {
                majorProjectId: '{{data.majorProjectId}}',
                deptId: '{{data.dept}}',
                drillTarget: 'overdue',
            }))),
            withActions(createBarChart(
                'pmcc-execution-workload',
                '科室负载',
                56,
                824,
                420,
                184,
                '/analytics/api/project-cockpit/screen/execution',
                'workload',
                'dept',
                [
                    { field: 'activeCount', name: '在办任务' },
                    { field: 'overdueCount', name: '延期任务' },
                ],
            ), jumpAction(buildCockpitJumpUrl('execution', { deptId: '{{name}}', drillTarget: 'overdue' }))),
            withActions(createTable(
                'pmcc-execution-due-list',
                '到期与延期任务',
                496,
                824,
                438,
                184,
                '/analytics/api/project-cockpit/screen/execution',
                'dueList',
                ['name', 'majorProjectName', 'dept', 'planDate', 'status', 'delayDays'],
            ), jumpAction(buildCockpitJumpUrl('execution', { deptId: '{{dept}}', drillTarget: 'overdue' }))),
            withActions(createPieChart(
                'pmcc-execution-milestone-ring',
                '里程碑完成结构',
                1012,
                286,
                264,
                320,
                '/analytics/api/project-cockpit/screen/execution',
                'milestoneBreakdown',
            ), jumpAction(buildCockpitJumpUrl('execution', { drillTarget: 'milestone' }))),
            withActions(createPieChart(
                'pmcc-execution-stage',
                '节点类型分布',
                1292,
                286,
                252,
                320,
                '/analytics/api/project-cockpit/screen/execution',
                'stageBuckets',
            ), jumpAction(buildCockpitJumpUrl('execution'))),
            withActions(createApiSummaryCard(
                'pmcc-execution-summary-overdue',
                '延期任务',
                1560,
                304,
                140,
                92,
                '/analytics/api/project-cockpit/screen/execution',
                'executionSummary',
                'overdueCount',
            ), jumpAction(buildCockpitJumpUrl('execution', { drillTarget: 'overdue' }))),
            withActions(createApiSummaryCard(
                'pmcc-execution-summary-max-delay',
                '最大拖期',
                1716,
                304,
                140,
                92,
                '/analytics/api/project-cockpit/screen/execution',
                'executionSummary',
                'maxDelayDays',
                '天',
            ), jumpAction(buildCockpitJumpUrl('execution', { drillTarget: 'overdue' }))),
            withActions(createApiSummaryCard(
                'pmcc-execution-summary-total',
                '节点总数',
                1560,
                414,
                140,
                92,
                '/analytics/api/project-cockpit/screen/execution',
                'executionSummary',
                'nodeTotal',
            ), jumpAction(buildCockpitJumpUrl('execution'))),
            withActions(createApiSummaryCard(
                'pmcc-execution-summary-due-soon',
                '两周内到期',
                1716,
                414,
                140,
                92,
                '/analytics/api/project-cockpit/screen/execution',
                'executionSummary',
                'dueSoonCount',
            ), jumpAction(buildCockpitJumpUrl('execution', { drillTarget: 'overdue' }))),
            withActions(createApiTable(
                'pmcc-execution-milestones',
                '里程碑清单',
                1012,
                650,
                852,
                358,
                '/analytics/api/project-cockpit/screen/execution',
                'milestones',
                [
                    { source: 'name', alias: '里程碑', align: 'left', wrap: true },
                    { source: 'majorProjectName', alias: '项目', align: 'left', wrap: true },
                    { source: 'subprojectName', alias: '子项目', align: 'left', wrap: true },
                    { source: 'planDate', alias: '计划日期' },
                    { source: 'status', alias: '状态', align: 'left', wrap: true },
                ],
            ), jumpAction(buildCockpitJumpUrl('execution', { drillTarget: 'milestone' }))),
        ],
    };
}

function buildRiskPage(): ScreenPage {
    return {
        id: 'pmcc-page-risk',
        name: '风险与变更',
        backgroundColor: BG,
        components: [
            createPanel('pmcc-risk-bg-top', 32, 110, 1856, 148),
            createPanel('pmcc-risk-bg-left', 32, 280, 930, 760),
            createPanel('pmcc-risk-bg-right-top', 988, 280, 900, 360),
            createPanel('pmcc-risk-bg-right-bottom', 988, 658, 900, 382),
            ...createCommonHeader('风险与变更', 2),
            ...createKpiRow([
                { id: 'pmcc-rk-kpi-abnormal', title: '不正常待变更', url: '/analytics/api/project-cockpit/screen/risk', responsePath: 'changeKpis.0', jumpUrlTemplate: buildCockpitJumpUrl('risk', { drillTarget: 'overdue' }) },
                { id: 'pmcc-rk-kpi-unc', title: '超期未完未变更', url: '/analytics/api/project-cockpit/screen/risk', responsePath: 'changeKpis.1', jumpUrlTemplate: buildCockpitJumpUrl('risk', { drillTarget: 'overdue' }) },
                { id: 'pmcc-rk-kpi-chg', title: '超期未完已变更', url: '/analytics/api/project-cockpit/screen/risk', responsePath: 'changeKpis.2', jumpUrlTemplate: buildCockpitJumpUrl('risk') },
                { id: 'pmcc-rk-kpi-done', title: '超期已完未变更', url: '/analytics/api/project-cockpit/screen/risk', responsePath: 'changeKpis.3', jumpUrlTemplate: buildCockpitJumpUrl('risk') },
                { id: 'pmcc-rk-kpi-abnrate', title: '异常率', url: '/analytics/api/project-cockpit/screen/risk', responsePath: 'changeKpis.4', suffix: '%', jumpUrlTemplate: buildCockpitJumpUrl('risk', { drillTarget: 'overdue' }) },
                { id: 'pmcc-rk-kpi-ovrate', title: '超期率', url: '/analytics/api/project-cockpit/screen/risk', responsePath: 'changeKpis.5', suffix: '%', jumpUrlTemplate: buildCockpitJumpUrl('risk', { drillTarget: 'overdue' }) },
            ], 128, 118),
            withActions(createPieChart(
                'pmcc-risk-breakdown',
                '风险等级分布',
                56,
                304,
                420,
                320,
                '/analytics/api/project-cockpit/screen/risk',
                'riskBreakdown',
            ), jumpAction(buildCockpitJumpUrl('risk', { riskLevel: '{{name}}' }))),
            withActions(createBarChart(
                'pmcc-risk-reasons',
                '延期原因分布',
                496,
                304,
                438,
                320,
                '/analytics/api/project-cockpit/screen/risk',
                'delayReasonBreakdown',
                'label',
                [{ field: 'value', name: '数量' }],
            ), jumpAction(buildCockpitJumpUrl('risk', { drillTarget: 'delay-reason', drillReason: '{{name}}' }))),
            withActions(createLineChart(
                'pmcc-risk-weekly',
                '周度拖期趋势',
                56,
                648,
                878,
                360,
                '/analytics/api/project-cockpit/screen/risk',
                'weeklyDelayTrend',
                'weekLabel',
                [
                    { field: 'delayedNodes', name: '延期节点' },
                    { field: 'highRiskNodes', name: '高风险节点' },
                ],
            ), jumpAction(buildCockpitJumpUrl('risk', { drillTarget: 'overdue' }))),
            withActions(createApiSummaryCard(
                'pmcc-risk-govern-delayed',
                '延期节点',
                1012,
                304,
                198,
                102,
                '/analytics/api/project-cockpit/screen/risk',
                'governanceSummary',
                'delayedNodeCount',
            ), jumpAction(buildCockpitJumpUrl('risk', { drillTarget: 'overdue' }))),
            withActions(createApiSummaryCard(
                'pmcc-risk-govern-highrisk',
                '高风险节点',
                1230,
                304,
                198,
                102,
                '/analytics/api/project-cockpit/screen/risk',
                'governanceSummary',
                'highRiskNodeCount',
            ), jumpAction(buildCockpitJumpUrl('risk', { drillTarget: 'high-risk' }))),
            withActions(createApiSummaryCard(
                'pmcc-risk-govern-openrisk',
                '待处理风险',
                1448,
                304,
                198,
                102,
                '/analytics/api/project-cockpit/screen/risk',
                'governanceSummary',
                'openRiskNodeCount',
            ), jumpAction(buildCockpitJumpUrl('risk', { drillTarget: 'high-risk' }))),
            withActions(createApiSummaryCard(
                'pmcc-risk-govern-changed',
                '已变更节点',
                1666,
                304,
                198,
                102,
                '/analytics/api/project-cockpit/screen/risk',
                'governanceSummary',
                'changedNodeCount',
            ), jumpAction(buildCockpitJumpUrl('risk'))),
            createMarkdownNote(
                'pmcc-risk-govern-note',
                '**治理提示**  \n优先盯防高风险延期节点，按责任科室检查技术、质量与协同类原因；矩阵区域用于快速定位责任面。',
                1012,
                424,
                852,
                76,
            ),
            withActions(createApiTable(
                'pmcc-risk-matrix',
                '归因矩阵',
                1012,
                520,
                852,
                276,
                '/analytics/api/project-cockpit/screen/risk',
                'delayReasonMatrix',
                [
                    { source: 'dept', alias: '责任科室', align: 'left', wrap: true },
                    { source: 'technical', alias: '技术' },
                    { source: 'quality', alias: '质量' },
                    { source: 'change', alias: '变更' },
                    { source: 'coordination', alias: '协同' },
                    { source: 'supplier', alias: '供应' },
                    { source: 'test', alias: '测试' },
                    { source: 'archive', alias: '归档' },
                    { source: 'normal', alias: '正常' },
                    { source: 'total', alias: '总数' },
                ],
                {
                    renderMode: 'delay-reason-matrix',
                    fontSize: 10,
                    conditionalRules: [
                        { columnKey: 'technical', operator: '>=', value: 2, color: '#991b1b', background: 'rgba(254, 226, 226, 0.92)' },
                        { columnKey: 'quality', operator: '>=', value: 2, color: '#991b1b', background: 'rgba(254, 226, 226, 0.92)' },
                        { columnKey: 'change', operator: '>=', value: 2, color: '#991b1b', background: 'rgba(254, 226, 226, 0.92)' },
                        { columnKey: 'coordination', operator: '>=', value: 2, color: '#991b1b', background: 'rgba(254, 226, 226, 0.92)' },
                        { columnKey: 'supplier', operator: '>=', value: 2, color: '#991b1b', background: 'rgba(254, 226, 226, 0.92)' },
                        { columnKey: 'test', operator: '>=', value: 2, color: '#991b1b', background: 'rgba(254, 226, 226, 0.92)' },
                        { columnKey: 'archive', operator: '>=', value: 2, color: '#991b1b', background: 'rgba(254, 226, 226, 0.92)' },
                        { columnKey: 'technical', operator: '==', value: 1, color: '#92400e', background: 'rgba(254, 243, 199, 0.92)' },
                        { columnKey: 'quality', operator: '==', value: 1, color: '#92400e', background: 'rgba(254, 243, 199, 0.92)' },
                        { columnKey: 'change', operator: '==', value: 1, color: '#92400e', background: 'rgba(254, 243, 199, 0.92)' },
                        { columnKey: 'coordination', operator: '==', value: 1, color: '#92400e', background: 'rgba(254, 243, 199, 0.92)' },
                        { columnKey: 'supplier', operator: '==', value: 1, color: '#92400e', background: 'rgba(254, 243, 199, 0.92)' },
                        { columnKey: 'test', operator: '==', value: 1, color: '#92400e', background: 'rgba(254, 243, 199, 0.92)' },
                        { columnKey: 'archive', operator: '==', value: 1, color: '#92400e', background: 'rgba(254, 243, 199, 0.92)' },
                        { columnKey: 'total', operator: '>=', value: 3, color: '#0f172a', background: 'rgba(219, 234, 254, 0.96)' },
                    ],
                },
            ), jumpAction(buildCockpitJumpUrl('risk', { deptId: '{{dept}}', drillTarget: 'delay-reason', drillDept: '{{dept}}', drillReason: '{{reason}}' }))),
            withActions(createApiTable(
                'pmcc-risk-delayed-projects',
                '重点延期项目',
                1012,
                814,
                852,
                194,
                '/analytics/api/project-cockpit/screen/risk',
                'delayedProjects',
                [
                    { source: 'majorProjectName', alias: '项目', align: 'left', wrap: true },
                    { source: 'subprojectName', alias: '子项目', align: 'left', wrap: true },
                    { source: 'nodeTask', alias: '节点', align: 'left', wrap: true },
                    { source: 'riskLevel', alias: '风险' },
                    { source: 'delayDays', alias: '延期' },
                    { source: 'reason', alias: '原因', align: 'left', wrap: true },
                    { source: 'dept', alias: '责任科室', align: 'left', wrap: true },
                ],
                { fontSize: 10 },
            ), jumpAction(buildCockpitJumpUrl('risk', { deptId: '{{dept}}', drillTarget: 'delay-reason', drillReason: '{{reason}}' }))),
        ],
    };
}

function createCompareSummaryCard(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    valueField: string,
    suffix = '',
): ScreenComponent {
    return createComponent(id, 'number-card', title, x, y, width, 106, 10, {
        title,
        value: 0,
        suffix,
        precision: suffix === '%' ? 2 : 0,
        valueField,
        titleColor: SUBTITLE_COLOR,
        valueColor: TITLE_COLOR,
        backgroundColor: KPI_BG,
    }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/metrics-compare', 'summary'));
}

function createCompareTable(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    height: number,
    responsePath: string,
    columns: Array<{ source: string; alias: string }>,
): ScreenComponent {
    return createComponent(id, 'table', title, x, y, width, height, 10, {
        title,
        fields: columns.map((item) => item.source),
        columns: columns.map((item) => ({
            source: item.source,
            alias: item.alias,
            align: item.source === 'label' || item.source === 'dimension' ? 'left' : 'center',
            wrap: item.source === 'label' || item.source === 'dimension',
        })),
        header: [],
        data: [],
        fontSize: 11,
        headerColor: TITLE_COLOR,
        headerBackground: TABLE_HEADER_BG,
        bodyColor: BODY_COLOR,
        bodyBackground: TABLE_BODY_BG,
        oddRowBackground: TABLE_BODY_BG,
        evenRowBackground: TABLE_EVEN_ROW_BG,
        borderColor: PANEL_BORDER,
        enableSort: false,
        enablePagination: false,
        freezeHeader: true,
        conditionalRules: [
            { columnKey: 'status', operator: '=', value: '差异', color: '#b91c1c', background: 'rgba(254, 226, 226, 0.96)' },
            { columnKey: 'status', operator: '=', value: '一致', color: '#166534', background: 'rgba(220, 252, 231, 0.96)' },
        ],
    }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/metrics-compare', responsePath));
}

function createTreeSummaryCard(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    valueField: string,
    suffix = '',
): ScreenComponent {
    return createComponent(id, 'number-card', title, x, y, width, 106, 10, {
        title,
        value: 0,
        suffix,
        precision: suffix === '%' ? 2 : 0,
        valueField,
        titleColor: SUBTITLE_COLOR,
        valueColor: TITLE_COLOR,
        backgroundColor: KPI_BG,
    }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/tree', 'summary'));
}

function createTreeTable(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    height: number,
    responsePath: string,
    columns: Array<{ source: string; alias: string }>,
): ScreenComponent {
    return createComponent(id, 'table', title, x, y, width, height, 10, {
        title,
        fields: columns.map((item) => item.source),
        columns: columns.map((item) => ({
            source: item.source,
            alias: item.alias,
            align: item.source === 'name' || item.source === 'subprojectName' || item.source === 'status' ? 'left' : 'center',
            wrap: item.source === 'name' || item.source === 'subprojectName' || item.source === 'status',
        })),
        header: [],
        data: [],
        fontSize: 11,
        headerColor: TITLE_COLOR,
        headerBackground: TABLE_HEADER_BG,
        bodyColor: BODY_COLOR,
        bodyBackground: TABLE_BODY_BG,
        oddRowBackground: TABLE_BODY_BG,
        evenRowBackground: TABLE_EVEN_ROW_BG,
        borderColor: PANEL_BORDER,
        enableSort: false,
        enablePagination: false,
        freezeHeader: true,
    }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/tree', responsePath));
}

function createMarkdownNote(id: string, markdown: string, x: number, y: number, width: number, height: number): ScreenComponent {
    return createComponent(id, 'markdown-text', id, x, y, width, height, 12, {
        markdown,
        color: BODY_COLOR,
        fontSize: 13,
        lineHeight: 1.7,
    });
}

function createDimensionTitle(id: string, text: string, x: number, y: number, width: number): ScreenComponent {
    return createComponent(id, 'title', text, x, y, width, 34, 15, {
        text,
        fontSize: 17,
        fontWeight: '700',
        color: '#2563eb',
        textAlign: 'flex-start',
    });
}

function buildTreePage(): ScreenPage {
    const pageIndex = 3;
    return {
        id: 'pmcc-page-tree',
        name: '项目树与重点项目',
        backgroundColor: BG,
        components: [
            createPanel('pmcc-tree-bg-top', 32, 110, 1856, 148),
            createPanel('pmcc-tree-bg-left', 32, 280, 900, 760),
            createPanel('pmcc-tree-bg-right-top', 952, 280, 936, 360),
            createPanel('pmcc-tree-bg-right-bottom', 952, 658, 936, 382),
            ...createCommonHeader('项目树与重点项目', pageIndex),

            withActions(createTreeSummaryCard('pmcc-tree-summary-total', '当前项目节点数', 56, 128, 220, 'totalNodes'), jumpAction(buildCockpitJumpUrl('tree'))),
            withActions(createTreeSummaryCard('pmcc-tree-summary-completed', '已完成节点', 292, 128, 220, 'completedNodes'), jumpAction(buildCockpitJumpUrl('tree'))),
            withActions(createTreeSummaryCard('pmcc-tree-summary-risk', '高风险节点', 528, 128, 220, 'highRiskNodes'), jumpAction(buildCockpitJumpUrl('tree'))),
            withActions(createTreeSummaryCard('pmcc-tree-summary-health', '平均健康度', 764, 128, 220, 'avgHealthScore'), jumpAction(buildCockpitJumpUrl('tree'))),
            createMarkdownNote(
                'pmcc-tree-summary-note',
                '**项目树说明**  \n- 左侧聚焦当前筛选范围内的重点项目与树摘要。  \n- 右侧先看子项目进度，再看关键节点链。  \n- 适合领导投屏和项目办逐项盯防。',
                1020,
                132,
                844,
                96,
            ),

            createDimensionTitle('pmcc-tree-projects-title', '重点项目总览', 56, 292, 280),
            withActions(createTreeTable(
                'pmcc-tree-projects',
                '重点项目总览',
                56,
                326,
                852,
                690,
                'focusProjects',
                [
                    { source: 'name', alias: '项目' },
                    { source: 'progressRate', alias: '进度%' },
                    { source: 'highRiskCount', alias: '高风险' },
                    { source: 'delayDays', alias: '最大拖期' },
                    { source: 'status', alias: '状态' },
                ],
            ), jumpAction(buildCockpitJumpUrl('tree', { majorProjectId: '{{id}}' }))),

            createDimensionTitle('pmcc-tree-subprojects-title', '子项目进度', 976, 292, 220),
            withActions(createTreeTable(
                'pmcc-tree-subprojects',
                '子项目进度',
                976,
                326,
                888,
                288,
                'subprojectRows',
                [
                    { source: 'name', alias: '子项目' },
                    { source: 'progressRate', alias: '进度%' },
                    { source: 'incompleteCount', alias: '未完成' },
                    { source: 'highRiskCount', alias: '高风险' },
                    { source: 'status', alias: '状态' },
                ],
            ), jumpAction(buildCockpitJumpUrl('tree', { majorProjectId: '{{majorProjectId}}' }))),

            createDimensionTitle('pmcc-tree-focus-nodes-title', '关键节点链', 976, 670, 220),
            withActions(createTreeTable(
                'pmcc-tree-focus-nodes',
                '关键节点链',
                976,
                704,
                888,
                288,
                'focusNodes',
                [
                    { source: 'name', alias: '节点' },
                    { source: 'subprojectName', alias: '子项目' },
                    { source: 'riskLevel', alias: '风险' },
                    { source: 'delayDays', alias: '拖期' },
                    { source: 'status', alias: '状态' },
                    { source: 'planDate', alias: '计划日期' },
                ],
            ), jumpAction(buildCockpitJumpUrl('risk', {
                majorProjectId: '{{majorProjectId}}',
                deptId: '{{ownerDept}}',
                drillTarget: 'delay-reason',
                drillReason: '{{reason}}',
            }))),
        ],
    };
}

function buildMetricsComparePage(): ScreenPage {
    const pageIndex = 4;
    return {
        id: 'pmcc-page-metrics-compare',
        name: '指标对账',
        backgroundColor: BG,
        components: [
            createPanel('pmcc-compare-bg-top', 32, 110, 1856, 148),
            createPanel('pmcc-compare-bg-left-top', 32, 280, 1220, 350),
            createPanel('pmcc-compare-bg-left-bottom', 32, 648, 1220, 392),
            createPanel('pmcc-compare-bg-right', 1272, 280, 616, 760),
            ...createCommonHeader('指标对账', pageIndex),
            createCompareSummaryCard('pmcc-cmp-summary-total', '指标总数', 56, 128, 220, 'metricTotal'),
            createCompareSummaryCard('pmcc-cmp-summary-matched', '一致指标', 292, 128, 220, 'matchedCount'),
            createCompareSummaryCard('pmcc-cmp-summary-mismatch', '差异指标', 528, 128, 220, 'mismatchCount'),
            createMarkdownNote(
                'pmcc-cmp-summary-note',
                '**对账说明**  \n- 系统值来自项目看板口径聚合。  \n- Excel 值基于 ODS 明细按原始字段重算。  \n- 差异值用于和客户 Excel 手工结果逐项核对。',
                780,
                132,
                1084,
                96,
            ),

            createDimensionTitle('pmcc-cmp-dim1-title', '项目（含一般节点）', 56, 292, 320),
            createCompareTable(
                'pmcc-cmp-dim1-table',
                '项目（含一般节点）对账',
                56,
                326,
                580,
                280,
                'groups.0.items',
                [
                    { source: 'label', alias: '指标' },
                    { source: 'systemValue', alias: '系统值' },
                    { source: 'excelValue', alias: 'Excel值' },
                    { source: 'diffValue', alias: '差值' },
                    { source: 'diffRate', alias: '偏差率' },
                    { source: 'status', alias: '状态' },
                ],
            ),

            createDimensionTitle('pmcc-cmp-dim2-title', '项目（除一般节点）', 646, 292, 320),
            createCompareTable(
                'pmcc-cmp-dim2-table',
                '项目（除一般节点）对账',
                646,
                326,
                580,
                280,
                'groups.1.items',
                [
                    { source: 'label', alias: '指标' },
                    { source: 'systemValue', alias: '系统值' },
                    { source: 'excelValue', alias: 'Excel值' },
                    { source: 'diffValue', alias: '差值' },
                    { source: 'diffRate', alias: '偏差率' },
                    { source: 'status', alias: '状态' },
                ],
            ),

            createDimensionTitle('pmcc-cmp-dim3-title', '截止目前未完成节点', 56, 666, 320),
            createCompareTable(
                'pmcc-cmp-dim3-table',
                '截止目前未完成节点对账',
                56,
                700,
                580,
                300,
                'groups.2.items',
                [
                    { source: 'label', alias: '指标' },
                    { source: 'systemValue', alias: '系统值' },
                    { source: 'excelValue', alias: 'Excel值' },
                    { source: 'diffValue', alias: '差值' },
                    { source: 'diffRate', alias: '偏差率' },
                    { source: 'status', alias: '状态' },
                ],
            ),

            createDimensionTitle('pmcc-cmp-dim4-title', '本周期内节点', 646, 666, 320),
            createCompareTable(
                'pmcc-cmp-dim4-table',
                '本周期内节点对账',
                646,
                700,
                580,
                300,
                'groups.3.items',
                [
                    { source: 'label', alias: '指标' },
                    { source: 'systemValue', alias: '系统值' },
                    { source: 'excelValue', alias: 'Excel值' },
                    { source: 'diffValue', alias: '差值' },
                    { source: 'diffRate', alias: '偏差率' },
                    { source: 'status', alias: '状态' },
                ],
            ),

            createDimensionTitle('pmcc-cmp-mismatch-title', '差异指标榜', 1296, 292, 220),
            createCompareTable(
                'pmcc-cmp-mismatch-top',
                '差异指标榜',
                1296,
                326,
                568,
                500,
                'mismatchTop',
                [
                    { source: 'label', alias: '指标' },
                    { source: 'dimension', alias: '维度' },
                    { source: 'diffValue', alias: '差值' },
                    { source: 'diffRate', alias: '偏差率' },
                    { source: 'status', alias: '状态' },
                ],
            ),
            createMarkdownNote(
                'pmcc-cmp-footnote',
                '**使用建议**  \n- 先按客户关心的项目和统计周期筛选。  \n- 优先核对差异榜前几项，再回看 4 个维度表。  \n- 若 ODS 缺明细，系统会回退为系统值并提示。',
                1296,
                848,
                568,
                148,
            ),
        ],
    };
}

const projectManagementVariables: ScreenGlobalVariable[] = [
    createVariable('majorProjectId', '项目', 'string'),
    createVariable('dateFrom', '开始日期', 'date'),
    createVariable('dateTo', '结束日期', 'date'),
    createVariable('deptId', '责任科室', 'string'),
    createVariable('riskLevel', '风险等级', 'string'),
];

export const projectManagementCommandCenterTemplate: ScreenTemplate = {
    id: 'project-management-command-center',
    name: '项目运营管理大屏',
    description: '项目运营管理五屏轮播大屏，覆盖总体态势、执行与里程碑、风险与变更、项目树与重点项目、指标对账。',
    thumbnail: '🛰️',
    category: 'project-management',
    tags: ['科研项目', '项目管理', '指挥大屏', '轮播'],
    recommendedVariables: projectManagementVariables.map((item) => item.key),
    config: {
        schemaVersion: SCREEN_SCHEMA_VERSION,
        name: '项目运营管理大屏',
        description: '项目运营管理五屏轮播大屏',
        width: SCREEN_WIDTH,
        height: SCREEN_HEIGHT,
        backgroundColor: BG,
        theme: 'glacier',
        globalVariables: projectManagementVariables,
        pages: [
            buildOverviewPage(),
            buildExecutionPage(),
            buildRiskPage(),
            buildTreePage(),
            buildMetricsComparePage(),
        ],
        components: [],
        carouselConfig: {
            enabled: true,
            autoPlay: false,
            intervalSeconds: 15,
            transition: 'fade',
            transitionDuration: 800,
            loop: true,
        },
    },
};
