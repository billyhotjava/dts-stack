import type { DataSourceConfig, ScreenComponent, ScreenGlobalVariable, ScreenPage } from './types';
import type { ScreenTemplate } from './screenTemplates';
import { SCREEN_SCHEMA_VERSION } from './specV2';

const SCREEN_WIDTH = 1920;
const SCREEN_HEIGHT = 1080;
const BG = '#eef5fb';
const PANEL_BG = 'rgba(255, 255, 255, 0.94)';
const PANEL_BORDER = 'rgba(148, 163, 184, 0.24)';
const TITLE_COLOR = '#16324f';
const SUBTITLE_COLOR = '#5b7088';
const BODY_COLOR = '#35526b';
const ACCENT = '#3b82f6';
const INPUT_BG = 'rgba(255, 255, 255, 0.96)';
const INPUT_BORDER = 'rgba(148, 163, 184, 0.42)';
const KPI_BG = '#ffffff';
const LINE_SERIES_COLORS = ['#3b82f6', '#38bdf8', '#f4b740'];
const BAR_SERIES_COLORS = ['#2563eb', '#60a5fa', '#f4b740'];
const PIE_SERIES_COLORS = ['#60a5fa', '#38bdf8', '#f4b740', '#fb7185', '#818cf8'];
const TABLE_HEADER_BG = 'rgba(219, 234, 254, 0.96)';
const TABLE_BODY_BG = 'rgba(255, 255, 255, 0.96)';
const TABLE_EVEN_ROW_BG = 'rgba(241, 245, 249, 0.96)';

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
    };
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
        color: TITLE_COLOR,
        textAlign: 'flex-start',
    });
}

function createSubtitle(id: string, text: string, x: number, y: number, width: number): ScreenComponent {
    return createComponent(id, 'title', text, x, y, width, 24, 60, {
        text,
        fontSize: 15,
        fontWeight: '500',
        color: SUBTITLE_COLOR,
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

function createFilterInput(id: string, label: string, variableKey: string, x: number, y: number, width: number): ScreenComponent {
    return createComponent(id, 'filter-input', label, x, y, width, 58, 65, {
        label,
        variableKey,
        placeholder: '全部',
        labelColor: SUBTITLE_COLOR,
        inputBackground: INPUT_BG,
        inputBorderColor: INPUT_BORDER,
        inputTextColor: TITLE_COLOR,
    });
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
        labelColor: SUBTITLE_COLOR,
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
        labelColor: SUBTITLE_COLOR,
        inputBackground: INPUT_BG,
        inputBorderColor: INPUT_BORDER,
        inputTextColor: TITLE_COLOR,
    });
}

function createDatetime(id: string): ScreenComponent {
    return createComponent(id, 'datetime', '系统时间', 1670, 30, 210, 36, 65, {
        format: 'YYYY-MM-DD HH:mm:ss',
        fontSize: 18,
        color: ACCENT,
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
                programId: '{{programId}}',
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
    return createComponent(id, 'table', title, x, y, width, height, 10, {
        title,
        fields,
        header: [],
        data: [],
        fontSize: 12,
        headerColor: TITLE_COLOR,
        headerBackground: TABLE_HEADER_BG,
        bodyColor: BODY_COLOR,
        bodyBackground: TABLE_BODY_BG,
        oddRowBackground: TABLE_BODY_BG,
        evenRowBackground: TABLE_EVEN_ROW_BG,
        borderColor: PANEL_BORDER,
        enableSort: true,
        enablePagination: false,
        freezeHeader: true,
    }, buildScreenApiDataSource(url, responsePath));
}

function createCommonHeader(pageTitle: string, pageIndex: number): ScreenComponent[] {
    return [
        createTitle(`pmcc-title-${pageIndex}`, '科研项目管理指挥大屏', 44, 28, 560),
        createSubtitle(`pmcc-subtitle-${pageIndex}`, `演示定制版 · 第 ${pageIndex + 1} 屏 · ${pageTitle}`, 44, 74, 520),
        createSubtitle(`pmcc-page-${pageIndex}`, pageTitle, 1520, 34, 120),
        createDatetime(`pmcc-datetime-${pageIndex}`),
        createFilterInput(`pmcc-program-${pageIndex}`, '项目群', 'programId', 640, 24, 170),
        createFilterInput(`pmcc-major-${pageIndex}`, '重大项目', 'majorProjectId', 824, 24, 170),
        createFilterInput(`pmcc-dept-${pageIndex}`, '责任科室', 'deptId', 1008, 24, 170),
        createRiskSelect(`pmcc-risk-${pageIndex}`, 1192, 24, 140),
        createDateRange(`pmcc-date-${pageIndex}`, 1346, 24, 300),
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
            createNumberCard('pmcc-overview-kpi-total', '本周期节点总数', 56, 128, 210, 'kpis.0'),
            createNumberCard('pmcc-overview-kpi-due', '已到期节点', 286, 128, 210, 'kpis.2'),
            createNumberCard('pmcc-overview-kpi-completed', '节点完成总数', 516, 128, 210, 'kpis.7'),
            createNumberCard('pmcc-overview-kpi-rate', '节点完成百分比', 746, 128, 210, 'kpis.8', '%'),
            createNumberCard('pmcc-overview-kpi-ontime', '按时完成率', 976, 128, 210, 'kpis.9', '%'),
            createNumberCard('pmcc-overview-kpi-overdue', '超期完成率', 1206, 128, 210, 'kpis.10', '%'),
            createLineChart(
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
                    { field: 'delayedNodes', name: '延期节点' },
                    { field: 'highRiskNodes', name: '高风险节点' },
                ],
            ),
            createBarChart(
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
            ),
            createTable(
                'pmcc-overview-alerts',
                '重点预警清单',
                1012,
                304,
                852,
                704,
                '/analytics/api/project-cockpit/screen/overview',
                'alerts',
                ['title', 'majorProjectName', 'riskLevel', 'delayDays', 'reason'],
            ),
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
            createExecutionNumberCard('pmcc-execution-kpi-high-risk', '未完成高风险', 56, 126, 168, 'incompleteKpis.0'),
            createExecutionNumberCard('pmcc-execution-kpi-mid-risk', '未完成中风险', 240, 126, 168, 'incompleteKpis.1'),
            createExecutionNumberCard('pmcc-execution-kpi-milestone-open', '未完成里程碑', 424, 126, 168, 'incompleteKpis.2'),
            createExecutionNumberCard('pmcc-execution-kpi-milestone-ontime', '里程碑按时完成', 608, 126, 168, 'milestoneKpis.0'),
            createExecutionNumberCard('pmcc-execution-kpi-milestone-overdue', '里程碑超期完成', 792, 126, 168, 'milestoneKpis.1'),
            createExecutionNumberCard('pmcc-execution-kpi-milestone-rate', '里程碑完成率', 976, 126, 200, 'milestoneKpis.3', '%'),
            createBarChart(
                'pmcc-execution-workload',
                '科室负载',
                56,
                286,
                878,
                330,
                '/analytics/api/project-cockpit/screen/execution',
                'workload',
                'dept',
                [
                    { field: 'activeCount', name: '在办任务' },
                    { field: 'overdueCount', name: '延期任务' },
                ],
            ),
            createTable(
                'pmcc-execution-due-list',
                '到期与延期任务',
                56,
                640,
                878,
                368,
                '/analytics/api/project-cockpit/screen/execution',
                'dueList',
                ['name', 'majorProjectName', 'dept', 'planDate', 'status', 'delayDays'],
            ),
            createPieChart(
                'pmcc-execution-stage',
                '节点类型分布',
                1012,
                286,
                412,
                320,
                '/analytics/api/project-cockpit/screen/execution',
                'stageBuckets',
            ),
            createTable(
                'pmcc-execution-milestones',
                '里程碑清单',
                1444,
                286,
                420,
                320,
                '/analytics/api/project-cockpit/screen/execution',
                'milestones',
                ['name', 'majorProjectName', 'subprojectName', 'planDate', 'status'],
            ),
            createTable(
                'pmcc-execution-govern',
                '执行盯防清单',
                1012,
                674,
                852,
                334,
                '/analytics/api/project-cockpit/screen/execution',
                'ganttTasks',
                ['name', 'type', 'majorProjectName', 'subprojectName', 'planDate', 'riskLevel'],
            ),
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
            createRiskNumberCard('pmcc-risk-kpi-abnormal', '不正常待变更', 56, 128, 210, 'changeKpis.0'),
            createRiskNumberCard('pmcc-risk-kpi-overdue-unchanged', '超期未完未变更', 286, 128, 210, 'changeKpis.1'),
            createRiskNumberCard('pmcc-risk-kpi-overdue-changed', '超期未完已变更', 516, 128, 210, 'changeKpis.2'),
            createRiskNumberCard('pmcc-risk-kpi-overdue-done', '超期已完成未变更', 746, 128, 210, 'changeKpis.3'),
            createRiskNumberCard('pmcc-risk-kpi-abnormal-rate', '异常率', 976, 128, 210, 'changeKpis.4', '%'),
            createRiskNumberCard('pmcc-risk-kpi-overdue-rate', '超期率', 1206, 128, 210, 'changeKpis.5', '%'),
            createPieChart(
                'pmcc-risk-breakdown',
                '风险等级分布',
                56,
                304,
                420,
                320,
                '/analytics/api/project-cockpit/screen/risk',
                'riskBreakdown',
            ),
            createBarChart(
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
            ),
            createLineChart(
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
            ),
            createTable(
                'pmcc-risk-projects',
                '拖期项目清单',
                1012,
                304,
                852,
                704,
                '/analytics/api/project-cockpit/screen/risk',
                'delayedProjects',
                ['nodeTask', 'majorProjectName', 'subprojectName', 'riskLevel', 'delayDays', 'dept'],
            ),
        ],
    };
}

const projectManagementVariables: ScreenGlobalVariable[] = [
    createVariable('programId', '项目群', 'string'),
    createVariable('majorProjectId', '重大项目', 'string'),
    createVariable('dateFrom', '开始日期', 'date'),
    createVariable('dateTo', '结束日期', 'date'),
    createVariable('deptId', '责任科室', 'string'),
    createVariable('riskLevel', '风险等级', 'string'),
];

export const projectManagementCommandCenterTemplate: ScreenTemplate = {
    id: 'project-management-command-center',
    name: '项目管理指挥大屏',
    description: 'Java 聚合接口驱动的三屏轮播演示版，面向现场汇报与客户演示。',
    thumbnail: '🛰️',
    category: 'project-management',
    tags: ['项目管理', '指挥大屏', '演示版', '轮播'],
    recommendedVariables: projectManagementVariables.map((item) => item.key),
    config: {
        schemaVersion: SCREEN_SCHEMA_VERSION,
        name: '项目管理指挥大屏',
        description: '科研项目管理三屏轮播演示版',
        width: SCREEN_WIDTH,
        height: SCREEN_HEIGHT,
        backgroundColor: BG,
        theme: 'glacier',
        globalVariables: projectManagementVariables,
        pages: [
            buildOverviewPage(),
            buildExecutionPage(),
            buildRiskPage(),
        ],
        components: [],
        carouselConfig: {
            enabled: true,
            intervalSeconds: 15,
            transition: 'fade',
            transitionDuration: 800,
            loop: true,
        },
    },
};
