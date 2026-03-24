import test from 'node:test';
import assert from 'node:assert/strict';
import { mapCardDataToConfig } from './cardDataMapper';

test('gantt-chart: maps rows to tasks array', () => {
    const cols = [
        { name: 'node_task', display_name: '任务', base_type: 'type/Text' },
        { name: 'node_type', display_name: '类型', base_type: 'type/Text' },
        { name: 'plan_date', display_name: '计划日期', base_type: 'type/Date' },
        { name: 'actual_date', display_name: '实际日期', base_type: 'type/Date' },
        { name: 'is_completed', display_name: '已完成', base_type: 'type/Boolean' },
        { name: 'is_overdue_completed', display_name: '超期完成', base_type: 'type/Boolean' },
        { name: 'is_incomplete', display_name: '未完成', base_type: 'type/Boolean' },
        { name: 'delay_days', display_name: '超期天数', base_type: 'type/Integer' },
        { name: 'risk_level', display_name: '风险等级', base_type: 'type/Text' },
        { name: 'owner', display_name: '责任人', base_type: 'type/Text' },
    ];
    const rows = [
        ['关键算法验证', '重大节点', '2026-02-01', '2026-02-10', true, true, false, 9, '高', '张三'],
        ['需求评审', '里程碑节点', '2026-03-01', null, false, false, true, null, '中', '李四'],
    ];

    const result = mapCardDataToConfig('gantt-chart', { rows, cols });

    assert.ok(Array.isArray(result.tasks));
    const tasks = result.tasks as Array<Record<string, unknown>>;
    assert.equal(tasks.length, 2);

    assert.equal(tasks[0].name, '关键算法验证');
    assert.equal(tasks[0].type, '重大节点');
    assert.equal(tasks[0].planDate, '2026-02-01');
    assert.equal(tasks[0].actualDate, '2026-02-10');
    assert.equal(tasks[0].isCompleted, true);
    assert.equal(tasks[0].isOverdue, true);
    assert.equal(tasks[0].isIncomplete, false);
    assert.equal(tasks[0].delayDays, 9);
    assert.equal(tasks[0].riskLevel, '高');
    assert.equal(tasks[0].owner, '张三');

    assert.equal(tasks[1].name, '需求评审');
    assert.equal(tasks[1].actualDate, '');
    assert.equal(tasks[1].isIncomplete, true);
    assert.equal(tasks[1].delayDays, 0);
});

test('gantt-chart: returns empty on no rows', () => {
    const result = mapCardDataToConfig('gantt-chart', { rows: [], cols: [] });
    assert.deepEqual(result, {});
});

test('gantt-chart: maps project-management alias fields for cockpit reuse', () => {
    const cols = [
        { name: 'task_name', display_name: '任务', base_type: 'type/Text' },
        { name: 'task_type', display_name: '类型', base_type: 'type/Text' },
        { name: 'plan_start', display_name: '计划开始', base_type: 'type/Date' },
        { name: 'actual_end', display_name: '实际结束', base_type: 'type/Date' },
        { name: 'completion_status', display_name: '状态', base_type: 'type/Text' },
        { name: 'delay_days', display_name: '延期天数', base_type: 'type/Integer' },
        { name: 'risk_level', display_name: '风险等级', base_type: 'type/Text' },
        { name: 'owner_dept', display_name: '责任科室', base_type: 'type/Text' },
        { name: 'major_project_name', display_name: '重大项目', base_type: 'type/Text' },
        { name: 'subproject_name', display_name: '子项目', base_type: 'type/Text' },
    ];
    const rows = [
        ['关键算法验证', '里程碑节点', '2026-02-01', '2026-02-10', '已完成但有延期', 9, '高', '导航室', '苍穹导航综合工程', '导航处理机'],
    ];

    const result = mapCardDataToConfig('gantt-chart', { rows, cols });
    const tasks = result.tasks as Array<Record<string, unknown>>;

    assert.equal(tasks[0].name, '关键算法验证');
    assert.equal(tasks[0].planDate, '2026-02-01');
    assert.equal(tasks[0].actualDate, '2026-02-10');
    assert.equal(tasks[0].status, '已完成但有延期');
    assert.equal(tasks[0].owner, '导航室');
    assert.equal(tasks[0].majorProjectName, '苍穹导航综合工程');
    assert.equal(tasks[0].subprojectName, '导航处理机');
});

test('gantt-chart: maps screen execution camelCase fields for command center board mode', () => {
    const cols = [
        { name: 'id', display_name: 'ID', base_type: 'type/Text' },
        { name: 'name', display_name: '任务', base_type: 'type/Text' },
        { name: 'type', display_name: '类型', base_type: 'type/Text' },
        { name: 'planDate', display_name: '计划日期', base_type: 'type/Date' },
        { name: 'baselineStartDate', display_name: '基线开始', base_type: 'type/Date' },
        { name: 'baselineEndDate', display_name: '基线结束', base_type: 'type/Date' },
        { name: 'actualDate', display_name: '实际日期', base_type: 'type/Date' },
        { name: 'delayDays', display_name: '延期天数', base_type: 'type/Integer' },
        { name: 'riskLevel', display_name: '风险等级', base_type: 'type/Text' },
        { name: 'owner', display_name: '责任人', base_type: 'type/Text' },
        { name: 'dept', display_name: '责任科室', base_type: 'type/Text' },
        { name: 'majorProjectId', display_name: '项目ID', base_type: 'type/Text' },
        { name: 'majorProjectName', display_name: '项目', base_type: 'type/Text' },
        { name: 'subprojectId', display_name: '子项目ID', base_type: 'type/Text' },
        { name: 'subprojectName', display_name: '子项目', base_type: 'type/Text' },
        { name: 'status', display_name: '状态', base_type: 'type/Text' },
    ];
    const rows = [
        ['node-01', '详细设计评审', '里程碑节点', '2026-01-07', '2026-01-05', '2026-01-09', '', 4, '高', '张工', '质量室', 'PRJ-2026-003', 'PRJ-2026-003', 'sub-03', '海天感知平台', '延期中'],
    ];

    const result = mapCardDataToConfig('gantt-chart', { rows, cols });
    const tasks = result.tasks as Array<Record<string, unknown>>;

    assert.equal(tasks[0].id, 'node-01');
    assert.equal(tasks[0].planDate, '2026-01-07');
    assert.equal(tasks[0].baselineStartDate, '2026-01-05');
    assert.equal(tasks[0].baselineEndDate, '2026-01-09');
    assert.equal(tasks[0].actualDate, '');
    assert.equal(tasks[0].delayDays, 4);
    assert.equal(tasks[0].riskLevel, '高');
    assert.equal(tasks[0].dept, '质量室');
    assert.equal(tasks[0].majorProjectId, 'PRJ-2026-003');
    assert.equal(tasks[0].majorProjectName, 'PRJ-2026-003');
    assert.equal(tasks[0].subprojectId, 'sub-03');
    assert.equal(tasks[0].subprojectName, '海天感知平台');
    assert.equal(tasks[0].status, '延期中');
});

test('funnel-chart: maps rows to name-value data', () => {
    const cardData = {
        cols: [
            { name: 'name', display_name: '状态', base_type: 'type/Text' },
            { name: 'value', display_name: '数量', base_type: 'type/Integer' },
        ],
        rows: [
            ['总计', 12],
            ['已完成', 8],
            ['进行中', 2],
            ['超期', 1],
            ['未启动', 1],
        ],
    };
    const result = mapCardDataToConfig('funnel-chart', cardData, { nameField: 'name', valueField: 'value' });
    assert.deepStrictEqual(result.data, [
        { name: '总计', value: 12 },
        { name: '已完成', value: 8 },
        { name: '进行中', value: 2 },
        { name: '超期', value: 1 },
        { name: '未启动', value: 1 },
    ]);
});

test('combo-chart: maps rows using same axis chart logic as bar/line', () => {
    const cardData = {
        cols: [
            { name: 'month', display_name: '月份', base_type: 'type/Text' },
            { name: 'value', display_name: '值', base_type: 'type/Float' },
            { name: 'delta', display_name: '环比', base_type: 'type/Float' },
        ],
        rows: [
            ['2026-01', 65, 0],
            ['2026-02', 72, 7],
        ],
    };
    const result = mapCardDataToConfig('combo-chart', cardData, {
        xAxisField: 'month',
        series: [{ field: 'value', name: '完成率' }],
    });
    assert.deepStrictEqual(result.xAxisData, ['2026-01', '2026-02']);
    const series = result.series as Array<{ name: string; data: number[] }>;
    assert.strictEqual(series[0].name, '完成率');
    assert.deepStrictEqual(series[0].data, [65, 72]);
});

test('scatter-chart: maps rows to multi-series scatter data by category', () => {
    const cardData = {
        cols: [
            { name: 'x', display_name: '超期天数', base_type: 'type/Float' },
            { name: 'y', display_name: '风险值', base_type: 'type/Integer' },
            { name: 'size', display_name: '节点数', base_type: 'type/Integer' },
            { name: 'category', display_name: '风险等级', base_type: 'type/Text' },
        ],
        rows: [
            [3, 3, 2, '高'],
            [1, 2, 3, '中'],
            [0, 1, 5, '低'],
            [8, 3, 1, '高'],
        ],
    };
    const result = mapCardDataToConfig('scatter-chart', cardData, {
        xField: 'x', yField: 'y', sizeField: 'size', categoryField: 'category',
    });
    assert.ok(Array.isArray(result.series));
    const series = result.series as Array<{ name: string; data: number[][] }>;
    assert.strictEqual(series.length, 3);
    const highSeries = series.find((s) => s.name === '高');
    assert.strictEqual(highSeries!.data.length, 2);
    assert.deepStrictEqual(highSeries!.data[0], [3, 3, 2]);
});

test('scatter-chart: falls back to single series without categoryField', () => {
    const cardData = {
        cols: [
            { name: 'x', display_name: 'X', base_type: 'type/Float' },
            { name: 'y', display_name: 'Y', base_type: 'type/Float' },
        ],
        rows: [[1, 2], [3, 4]],
    };
    const result = mapCardDataToConfig('scatter-chart', cardData, {});
    assert.ok(Array.isArray(result.data));
    assert.deepStrictEqual(result.data, [[1, 2], [3, 4]]);
});
