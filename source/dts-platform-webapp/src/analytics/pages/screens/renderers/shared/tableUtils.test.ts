import test from 'node:test';
import assert from 'node:assert/strict';

import {
    estimateTablePlaceholderRowCount,
    resolveBoundTableData,
    resolveFrozenColumnOffsets,
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

test('resolveBoundTableData supports schema column key and custom label for dynamic data', () => {
    const resolved = resolveBoundTableData({
        _sourceColumns: [
            { name: 'project_no', displayName: '项目编号' },
            { name: 'issue_name', displayName: '问题名称' },
            { name: 'owner_name', displayName: '责任人' },
        ],
        columns: [
            { key: 'issue_name', label: '自定义问题表头', width: 180, align: 'center', sortable: false },
            { key: 'owner_name', label: '处理人', width: 120, align: 'right', frozen: true },
        ],
        data: [
            ['PJ-001', '管线焊缝缺陷', '王工'],
            ['PJ-002', '材料复检超期', '李工'],
        ],
    });

    assert.deepEqual(resolved.header, ['自定义问题表头', '处理人']);
    assert.deepEqual(resolved.data, [
        ['管线焊缝缺陷', '王工'],
        ['材料复检超期', '李工'],
    ]);
    assert.equal(resolved.columnMeta[0].key, 'issue_name');
    assert.equal(resolved.columnMeta[0].title, '自定义问题表头');
    assert.equal(resolved.columnMeta[0].width, 180);
    assert.equal(resolved.columnMeta[0].widthCss, '180px');
    assert.equal(resolved.columnMeta[0].sortable, false);
    assert.equal(resolved.columnMeta[1].frozen, true);
});

test('resolveBoundTableData keeps legacy source alias columns working', () => {
    const resolved = resolveBoundTableData({
        _sourceColumns: [
            { name: 'project_no', displayName: '项目编号' },
            { name: 'issue_name', displayName: '问题名称' },
        ],
        columns: [
            { source: 'issue_name', alias: '问题标题', width: 45 },
            { source: 'project_no', alias: '项目编码', width: 30 },
        ],
        data: [['PJ-001', '管线焊缝缺陷']],
    });

    assert.deepEqual(resolved.header, ['问题标题', '项目编码']);
    assert.deepEqual(resolved.data, [['管线焊缝缺陷', 'PJ-001']]);
    assert.equal(resolved.columnMeta[0].widthCss, '45%');
    assert.equal(resolved.columnMeta[1].widthCss, '30%');
});

test('resolveBoundTableData applies column config to static header/data tables', () => {
    const resolved = resolveBoundTableData({
        header: ['项目编号', '问题名称', '负责人'],
        columns: [
            { key: '2', label: '处理人', width: 120 },
            { key: '问题名称', label: '问题标题', align: 'center' },
        ],
        data: [['PJ-001', '管线焊缝缺陷', '王工']],
    });

    assert.deepEqual(resolved.header, ['处理人', '问题标题']);
    assert.deepEqual(resolved.data, [['王工', '管线焊缝缺陷']]);
    assert.equal(resolved.columnMeta[0].widthCss, '120px');
    assert.equal(resolved.columnMeta[1].align, 'center');
});

test('resolveFrozenColumnOffsets computes sticky offsets for configured frozen columns', () => {
    const offsets = resolveFrozenColumnOffsets([
        { key: 'project_no', title: '项目编号', align: 'left', wrap: false, formatter: 'auto', frozen: true, width: 120, widthCss: '120px', widthUnit: 'px' },
        { key: 'issue_name', title: '问题标题', align: 'left', wrap: false, formatter: 'auto', frozen: true, width: 180, widthCss: '180px', widthUnit: 'px' },
        { key: 'owner_name', title: '处理人', align: 'left', wrap: false, formatter: 'auto', width: 80, widthCss: '80px', widthUnit: 'px' },
    ], 100);

    assert.deepEqual(offsets, [0, 120, undefined]);
});
