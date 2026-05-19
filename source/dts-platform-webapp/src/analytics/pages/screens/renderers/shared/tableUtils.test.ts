import test from 'node:test';
import assert from 'node:assert/strict';

import {
    estimateTablePlaceholderRowCount,
    resolveTableConditionalStyle,
    resolveTableRowConditionalStyle,
} from './tableUtils';

test('estimateTablePlaceholderRowCount fills visible table height with placeholder rows', () => {
    assert.equal(
        estimateTablePlaceholderRowCount({
            containerHeight: 222,
            headerHeight: 36,
            rowHeight: 40,
            currentRowCount: 1,
        }),
        3,
    );
});

test('estimateTablePlaceholderRowCount does not add placeholders when data already fills the viewport', () => {
    assert.equal(
        estimateTablePlaceholderRowCount({
            containerHeight: 222,
            headerHeight: 36,
            rowHeight: 40,
            currentRowCount: 5,
        }),
        0,
    );
});

test('estimateTablePlaceholderRowCount accounts for pagination footer height', () => {
    assert.equal(
        estimateTablePlaceholderRowCount({
            containerHeight: 260,
            headerHeight: 36,
            rowHeight: 40,
            footerHeight: 40,
            currentRowCount: 3,
        }),
        1,
    );
});

test('resolveTableRowConditionalStyle colors whole row from a referenced column', () => {
    const style = resolveTableRowConditionalStyle(
        [
            { scope: 'row', columnKey: '超期天数', operator: '>=', value: 90, background: 'red', color: 'white' },
            { scope: 'row', columnKey: '超期天数', operator: '>=', value: 30, background: 'orange', color: 'black' },
        ],
        ['PJ-2025-001', '力学试验', 45],
        [
            { key: '项目编号', title: '项目编号' },
            { key: '节点任务', title: '节点任务' },
            { key: '超期天数', title: '超期天数' },
        ],
    );

    assert.deepEqual(style, { background: 'orange', color: 'black' });
});

test('resolveTableConditionalStyle keeps row scoped rules out of cell-only formatting', () => {
    const style = resolveTableConditionalStyle(
        [
            { scope: 'row', columnKey: '超期天数', operator: '>=', value: 30, background: 'orange' },
            { columnKey: '超期天数', operator: '>=', value: 30, background: 'cell-orange' },
        ],
        0,
        45,
        { key: '超期天数', title: '超期天数' },
    );

    assert.deepEqual(style, { background: 'cell-orange', color: undefined });
});
