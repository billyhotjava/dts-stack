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
