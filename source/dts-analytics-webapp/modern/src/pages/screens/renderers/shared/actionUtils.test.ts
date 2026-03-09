import test from 'node:test';
import assert from 'node:assert/strict';

import {
    buildTableRowActionParams,
    normalizeScreenActionType,
    resolvePreferredDrillValue,
    resolveActionMappingValues,
    resolveActionTemplateText,
} from './actionUtils';

test('normalizeScreenActionType keeps known action values', () => {
    assert.equal(normalizeScreenActionType('open-panel'), 'open-panel');
    assert.equal(normalizeScreenActionType(' emit-intent '), 'emit-intent');
    assert.equal(normalizeScreenActionType('unknown-action'), null);
});

test('resolveActionMappingValues maps click params into runtime variable payload', () => {
    const result = resolveActionMappingValues(
        {
            name: 'QMS二期',
            value: 12,
            data: { owner: '周工' },
        },
        [
            { variableKey: 'projectId', sourcePath: 'name', transform: 'raw' },
            { variableKey: 'issueCount', sourcePath: 'value', transform: 'number' },
            { variableKey: 'ownerUserId', sourcePath: 'data.owner', transform: 'raw' },
            { variableKey: 'fallbackStage', sourcePath: 'data.stage', transform: 'raw', fallbackValue: '验证' },
        ],
    );

    assert.deepEqual(result, {
        projectId: 'QMS二期',
        issueCount: '12',
        ownerUserId: '周工',
        fallbackStage: '验证',
    });
});

test('resolveActionTemplateText interpolates placeholders using action params', () => {
    const result = resolveActionTemplateText(
        '项目 {{name}} 由 {{data.owner}} 负责，当前问题数 {{value}}',
        {
            name: '主数据治理',
            value: 6,
            data: { owner: '王工' },
        },
    );

    assert.equal(result, '项目 主数据治理 由 王工 负责，当前问题数 6');
});

test('buildTableRowActionParams exposes row fields by header name and index', () => {
    const params = buildTableRowActionParams(
        ['项目', '责任人', '状态'],
        ['QMS二期', '周工', '推进中'],
    );

    assert.equal(params['项目'], 'QMS二期');
    assert.equal(params['责任人'], '周工');
    assert.equal(params['状态'], '推进中');
    assert.deepEqual(params.row, ['QMS二期', '周工', '推进中']);
    assert.equal(params['row[1]'], '周工');
});

test('resolvePreferredDrillValue picks chart or table drill labels in priority order', () => {
    assert.equal(resolvePreferredDrillValue({ name: '验证' }), '验证');
    assert.equal(resolvePreferredDrillValue({ data: { name: '实施' } }), '实施');
    assert.equal(resolvePreferredDrillValue({ 项目: 'QMS二期', row: ['QMS二期', '周工'] }), 'QMS二期');
    assert.equal(resolvePreferredDrillValue({ row: ['PLM整合', '李工'] }), 'PLM整合');
});
