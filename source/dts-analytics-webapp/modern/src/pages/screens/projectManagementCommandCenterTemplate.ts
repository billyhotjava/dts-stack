import type { DataSourceConfig, ScreenComponent, ScreenComponentAction, ScreenGlobalVariable, ScreenPage } from './types';
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
        color: TITLE_COLOR,
        textAlign: 'center',
    });
}

function createSubtitle(id: string, text: string, x: number, y: number, width: number): ScreenComponent {
    return createComponent(id, 'title', text, x, y, width, 24, 60, {
        text,
        fontSize: 15,
        fontWeight: '500',
        color: SUBTITLE_COLOR,
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
    // Layout: left=filters | center=title | right=date+time
    return [
        // Left: filters
        createFilterInput(`pmcc-program-${pageIndex}`, '项目群', 'programId', 32, 24, 155),
        createFilterInput(`pmcc-major-${pageIndex}`, '重大项目', 'majorProjectId', 196, 24, 155),
        createFilterInput(`pmcc-dept-${pageIndex}`, '责任科室', 'deptId', 360, 24, 155),
        createRiskSelect(`pmcc-risk-${pageIndex}`, 524, 24, 130),
        // Center: title
        createTitle(`pmcc-title-${pageIndex}`, '科研项目管理指挥大屏', 680, 20, 560),
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
            withActions(createNumberCard('pmcc-overview-kpi-total', '本周期节点总数', 56, 128, 210, 'kpis.0'),
                jumpAction('/analytics/project-cockpit?theme=overview&drillTarget=completion')),
            withActions(createNumberCard('pmcc-overview-kpi-due', '已到期节点', 286, 128, 210, 'kpis.2'),
                jumpAction('/analytics/project-cockpit?theme=overview&drillTarget=overdue')),
            withActions(createNumberCard('pmcc-overview-kpi-completed', '节点完成总数', 516, 128, 210, 'kpis.7'),
                jumpAction('/analytics/project-cockpit?theme=overview&drillTarget=completion')),
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
            createPieChart(
                'pmcc-overview-completion-ring',
                '节点完成率',
                1012,
                304,
                380,
                340,
                '/analytics/api/project-cockpit/screen/overview',
                'completionBreakdown',
            ),
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
            ), jumpAction('/analytics/project-cockpit?theme=overview&drillTarget=overdue')),
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
            withActions(createExecutionNumberCard('pmcc-execution-kpi-high-risk', '未完成高风险', 56, 126, 168, 'incompleteKpis.0'),
                jumpAction('/analytics/project-cockpit?theme=risk&drillTarget=high-risk')),
            withActions(createExecutionNumberCard('pmcc-execution-kpi-mid-risk', '未完成中风险', 240, 126, 168, 'incompleteKpis.1'),
                jumpAction('/analytics/project-cockpit?theme=risk&drillTarget=high-risk')),
            withActions(createExecutionNumberCard('pmcc-execution-kpi-milestone-open', '未完成里程碑', 424, 126, 168, 'incompleteKpis.2'),
                jumpAction('/analytics/project-cockpit?theme=execution&drillTarget=milestone')),
            createExecutionNumberCard('pmcc-execution-kpi-milestone-ontime', '里程碑按时完成', 608, 126, 168, 'milestoneKpis.0'),
            createExecutionNumberCard('pmcc-execution-kpi-milestone-overdue', '里程碑超期完成', 792, 126, 168, 'milestoneKpis.1'),
            createExecutionNumberCard('pmcc-execution-kpi-milestone-rate', '里程碑完成率', 976, 126, 200, 'milestoneKpis.3', '%'),
            createBarChart(
                'pmcc-execution-workload',
                '科室负载',
                56,
                286,
                420,
                330,
                '/analytics/api/project-cockpit/screen/execution',
                'workload',
                'dept',
                [
                    { field: 'activeCount', name: '在办任务' },
                    { field: 'overdueCount', name: '延期任务' },
                ],
            ),
            createComponent('pmcc-execution-gantt', 'gantt-chart', '任务甘特图', 496, 286, 462, 330, 10, {
                title: '任务甘特图',
                nameField: 'name',
                startField: 'planDate',
                endField: 'actualDate',
                categoryField: 'majorProjectName',
                statusField: 'riskLevel',
            }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/execution', 'ganttTasks')),
            withActions(createTable(
                'pmcc-execution-due-list',
                '到期与延期任务',
                56,
                640,
                878,
                368,
                '/analytics/api/project-cockpit/screen/execution',
                'dueList',
                ['name', 'majorProjectName', 'dept', 'planDate', 'status', 'delayDays'],
            ), jumpAction('/analytics/project-cockpit?theme=execution&drillTarget=overdue')),
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
            withActions(createRiskNumberCard('pmcc-risk-kpi-abnormal', '不正常待变更', 56, 128, 210, 'changeKpis.0'),
                jumpAction('/analytics/project-cockpit?theme=risk&drillTarget=overdue')),
            withActions(createRiskNumberCard('pmcc-risk-kpi-overdue-unchanged', '超期未完未变更', 286, 128, 210, 'changeKpis.1'),
                jumpAction('/analytics/project-cockpit?theme=risk&drillTarget=overdue')),
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
            withActions(createTable(
                'pmcc-risk-projects',
                '拖期项目清单',
                1012,
                304,
                852,
                540,
                '/analytics/api/project-cockpit/screen/risk',
                'delayedProjects',
                ['nodeTask', 'majorProjectName', 'subprojectName', 'riskLevel', 'delayDays', 'dept'],
            ), jumpAction('/analytics/project-cockpit?theme=risk&drillTarget=delay-reason')),
            // 治理摘要 — from governanceSummary
            createComponent('pmcc-risk-govern-title', 'title', '治理摘要', 1012, 860, 200, 28, 15, {
                content: '治理摘要',
                fontSize: 15,
                fontWeight: 700,
                color: TITLE_COLOR,
                textAlign: 'left',
            }),
            createComponent('pmcc-risk-govern-delayed', 'number-card', '延期节点数', 1012, 896, 200, 100, 10, {
                title: '延期节点数',
                value: 0,
                valueField: 'delayedNodeCount',
                titleColor: SUBTITLE_COLOR,
                valueColor: TITLE_COLOR,
                backgroundColor: KPI_BG,
            }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/risk', 'governanceSummary')),
            createComponent('pmcc-risk-govern-highrisk', 'number-card', '高风险节点', 1232, 896, 200, 100, 10, {
                title: '高风险节点',
                value: 0,
                valueField: 'highRiskNodeCount',
                titleColor: SUBTITLE_COLOR,
                valueColor: TITLE_COLOR,
                backgroundColor: KPI_BG,
            }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/risk', 'governanceSummary')),
            createComponent('pmcc-risk-govern-openrisk', 'number-card', '待处理风险', 1452, 896, 200, 100, 10, {
                title: '待处理风险',
                value: 0,
                valueField: 'openRiskNodeCount',
                titleColor: SUBTITLE_COLOR,
                valueColor: TITLE_COLOR,
                backgroundColor: KPI_BG,
            }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/risk', 'governanceSummary')),
            createComponent('pmcc-risk-govern-changed', 'number-card', '已变更节点', 1672, 896, 192, 100, 10, {
                title: '已变更节点',
                value: 0,
                valueField: 'changedNodeCount',
                titleColor: SUBTITLE_COLOR,
                valueColor: TITLE_COLOR,
                backgroundColor: KPI_BG,
            }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/risk', 'governanceSummary')),
        ],
    };
}

function createMetricsCard(
    id: string,
    title: string,
    x: number,
    y: number,
    width: number,
    responsePath: string,
    suffix = '',
): ScreenComponent {
    return createComponent(id, 'number-card', title, x, y, width, 90, 10, {
        title,
        value: 0,
        suffix,
        precision: suffix === '%' ? 2 : 0,
        valueField: 'value',
        valueFontSize: 28,
        titleFontSize: 12,
        titleColor: SUBTITLE_COLOR,
        valueColor: TITLE_COLOR,
        backgroundColor: KPI_BG,
    }, buildScreenApiDataSource('/analytics/api/project-cockpit/screen/metrics-overview', responsePath));
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

function buildMetricsPage(): ScreenPage {
    const pageIndex = 3;
    // Layout: 4 columns for 4 dimensions
    // Col 1 (x: 32-488): 项目（含一般节点）13 items
    // Col 2 (x: 508-808): 项目（除一般节点）6 items
    // Col 3 (x: 828-1128): 截止目前未完成节点 5 items
    // Col 4 (x: 1148-1888): 本周期内节点 11 items (2 sub-cols)

    const cardW = 210;
    const cardH = 90;
    const gap = 10;
    const col1X = 56;
    const col2X = 508;
    const col3X = 850;
    const col4aX = 1168;
    const col4bX = 1548;
    const headerY = 126;
    const startY = 168;

    function cy(row: number) { return startY + row * (cardH + gap); }

    return {
        id: 'pmcc-page-metrics',
        name: '指标全览',
        backgroundColor: BG,
        components: [
            // Background panels
            createPanel('pmcc-metrics-bg-top', 32, 110, 1856, 930),
            ...createCommonHeader('指标全览', pageIndex),

            // Dimension 1: 项目（含一般节点）
            createDimensionTitle('pmcc-metrics-dim1', '项目（含一般节点）', col1X, headerY, 420),
            createMetricsCard('pmcc-m-d1-0', '项目本周期节点总数', col1X, cy(0), cardW, 'kpis.0'),
            createMetricsCard('pmcc-m-d1-1', '正常待完成', col1X + cardW + gap, cy(0), cardW, 'kpis.1'),
            createMetricsCard('pmcc-m-d1-2', '已到时间节点总数', col1X, cy(1), cardW, 'kpis.2'),
            createMetricsCard('pmcc-m-d1-3', '以外完成节点总数', col1X + cardW + gap, cy(1), cardW, 'kpis.3'),
            createMetricsCard('pmcc-m-d1-4', '未完成总数', col1X, cy(2), cardW, 'kpis.4'),
            createMetricsCard('pmcc-m-d1-5', '按时完成数', col1X + cardW + gap, cy(2), cardW, 'kpis.5'),
            createMetricsCard('pmcc-m-d1-6', '超期完成数', col1X, cy(3), cardW, 'kpis.7'),
            createMetricsCard('pmcc-m-d1-7', '完成总数', col1X + cardW + gap, cy(3), cardW, 'kpis.8'),
            createMetricsCard('pmcc-m-d1-8', '节点完成百分比', col1X, cy(4), cardW, 'kpis.9', '%'),
            createMetricsCard('pmcc-m-d1-9', '按时完成百分比', col1X + cardW + gap, cy(4), cardW, 'kpis.10', '%'),
            createMetricsCard('pmcc-m-d1-10', '超期完成百分比', col1X, cy(5), cardW, 'kpis.11', '%'),

            // Dimension 2: 项目（除一般节点）
            createDimensionTitle('pmcc-metrics-dim2', '项目（除一般节点）', col2X, headerY, 320),
            createMetricsCard('pmcc-m-d2-0', '不正常待变更', col2X, cy(0), cardW + 100, 'kpis.12'),
            createMetricsCard('pmcc-m-d2-1', '超期未完未变更', col2X, cy(1), cardW + 100, 'kpis.13'),
            createMetricsCard('pmcc-m-d2-2', '超期未完已变更', col2X, cy(2), cardW + 100, 'kpis.14'),
            createMetricsCard('pmcc-m-d2-3', '超期已完未变更', col2X, cy(3), cardW + 100, 'kpis.15'),
            createMetricsCard('pmcc-m-d2-4', '异常率', col2X, cy(4), cardW + 100, 'kpis.16', '%'),
            createMetricsCard('pmcc-m-d2-5', '超期率', col2X, cy(5), cardW + 100, 'kpis.17', '%'),

            // Dimension 3: 截止目前未完成节点
            createDimensionTitle('pmcc-metrics-dim3', '截止目前未完成节点', col3X, headerY, 300),
            createMetricsCard('pmcc-m-d3-0', '高风险未完成', col3X, cy(0), cardW + 80, 'kpis.18'),
            createMetricsCard('pmcc-m-d3-1', '中风险未完成', col3X, cy(1), cardW + 80, 'kpis.19'),
            createMetricsCard('pmcc-m-d3-2', '里程碑未完成', col3X, cy(2), cardW + 80, 'kpis.20'),
            createMetricsCard('pmcc-m-d3-3', '重大节点未完成', col3X, cy(3), cardW + 80, 'kpis.21'),
            createMetricsCard('pmcc-m-d3-4', '重要节点未完成', col3X, cy(4), cardW + 80, 'kpis.22'),

            // Dimension 4: 本周期内节点
            createDimensionTitle('pmcc-metrics-dim4', '本周期内节点', col4aX, headerY, 700),
            createMetricsCard('pmcc-m-d4-0', '里程碑按时完成', col4aX, cy(0), cardW, 'kpis.23'),
            createMetricsCard('pmcc-m-d4-1', '里程碑超期完成', col4bX, cy(0), cardW, 'kpis.24'),
            createMetricsCard('pmcc-m-d4-2', '里程碑正常待完成', col4aX, cy(1), cardW, 'kpis.25'),
            createMetricsCard('pmcc-m-d4-3', '里程碑完成率', col4bX, cy(1), cardW, 'kpis.26', '%'),
            createMetricsCard('pmcc-m-d4-4', '高风险节点数', col4aX, cy(2), cardW, 'kpis.27'),
            createMetricsCard('pmcc-m-d4-5', '中风险节点数', col4bX, cy(2), cardW, 'kpis.28'),
            createMetricsCard('pmcc-m-d4-6', '里程碑节点总数', col4aX, cy(3), cardW, 'kpis.29'),
            createMetricsCard('pmcc-m-d4-7', '重大节点总数', col4bX, cy(3), cardW, 'kpis.30'),
            createMetricsCard('pmcc-m-d4-8', '重要节点总数', col4aX, cy(4), cardW, 'kpis.31'),
            createMetricsCard('pmcc-m-d4-9', '里程碑按时完成', col4bX, cy(4), cardW, 'kpis.32'),
            createMetricsCard('pmcc-m-d4-10', '里程碑超期完成', col4aX, cy(5), cardW, 'kpis.33'),
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
    description: 'Java 聚合接口驱动的四屏轮播版，面向现场汇报与客户演示。含总体态势、执行里程碑、风险变更、指标全览。',
    thumbnail: '🛰️',
    category: 'project-management',
    tags: ['项目管理', '指挥大屏', '演示版', '轮播'],
    recommendedVariables: projectManagementVariables.map((item) => item.key),
    config: {
        schemaVersion: SCREEN_SCHEMA_VERSION,
        name: '项目管理指挥大屏',
        description: '科研项目管理四屏轮播版',
        width: SCREEN_WIDTH,
        height: SCREEN_HEIGHT,
        backgroundColor: BG,
        theme: 'glacier',
        globalVariables: projectManagementVariables,
        pages: [
            buildOverviewPage(),
            buildExecutionPage(),
            buildRiskPage(),
            buildMetricsPage(),
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
