import test from 'node:test';
import assert from 'node:assert/strict';

import { normalizeTabularData } from './tabularDataAdapter';

test('normalizeTabularData maps rows with semantic metadata columns', () => {
    const result = normalizeTabularData({
        data: {
            rows: [
                ['项目A', 12],
                ['项目B', 8],
            ],
            results_metadata: {
                columns: [
                    { field_ref: 'project_name', display_name: '项目名称', semantic_type: 'type/Name' },
                    { name: 'issue_count', displayName: '问题数', baseType: 'type/Integer' },
                ],
            },
        },
    });

    assert.deepEqual(result.cols, [
        { name: 'project_name', display_name: '项目名称', base_type: 'type/Name' },
        { name: 'issue_count', display_name: '问题数', base_type: 'type/Integer' },
    ]);
    assert.deepEqual(result.rows, [
        ['项目A', 12],
        ['项目B', 8],
    ]);
});

test('normalizeTabularData maps object-array API payloads into a stable column union', () => {
    const result = normalizeTabularData([
        { project: 'P1', amount: 10 },
        { project: 'P2', status: '延期' },
    ]);

    assert.deepEqual(result.cols, [
        { name: 'project', display_name: 'project', base_type: 'type/Text' },
        { name: 'amount', display_name: 'amount', base_type: 'type/Text' },
        { name: 'status', display_name: 'status', base_type: 'type/Text' },
    ]);
    assert.deepEqual(result.rows, [
        ['P1', 10, null],
        ['P2', null, '延期'],
    ]);
});

test('normalizeTabularData rejects scalar metric payloads for table rendering', () => {
    assert.throws(
        () => normalizeTabularData(42),
        /返回格式不支持行列化/,
    );
});
