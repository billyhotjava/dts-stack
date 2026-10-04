import { test } from 'vitest';
import assert from 'node:assert/strict';

import {
    buildTableRowActionParams,
    buildActionRuntimeParams,
    normalizeScreenActionType,
    normalizeRuntimeJumpUrl,
    resolvePreferredDrillValue,
    resolveActionMappingValues,
    resolveActionTemplateText,
    shouldRunDefaultDrill,
} from './actionUtils';

test('normalizeScreenActionType keeps known action values', () => {
    assert.equal(normalizeScreenActionType('open-panel'), 'open-panel');
    assert.equal(normalizeScreenActionType('drill-view'), 'drill-view');
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

test('buildActionRuntimeParams merges runtime filters and click params for jump-url templates', () => {
    const params = buildActionRuntimeParams(
        {
            majorProjectId: 'major-aurora',
            dateFrom: '2026-03-01',
            dateTo: '2026-03-31',
            deptId: '总体组',
            riskLevel: '高',
        },
        {
            dept: '质量科',
            data: { majorProjectId: 'major-dragon' },
        },
    );

    assert.equal(params.majorProjectId, 'major-aurora');
    assert.equal(params.dateFrom, '2026-03-01');
    assert.equal(params.riskLevel, '高');
    assert.equal(params.dept, '质量科');
    assert.deepEqual(params.runtime, {
        majorProjectId: 'major-aurora',
        dateFrom: '2026-03-01',
        dateTo: '2026-03-31',
        deptId: '总体组',
        riskLevel: '高',
    });
    assert.equal(resolveActionTemplateText(
        '/bi/project-cockpit?theme=risk&majorProjectId={{majorProjectId}}&deptId={{dept}}&riskLevel={{runtime.riskLevel}}',
        params,
    ), '/bi/project-cockpit?theme=risk&majorProjectId=major-aurora&deptId=质量科&riskLevel=高');
});

test('resolvePreferredDrillValue picks chart or table drill labels in priority order', () => {
    assert.equal(resolvePreferredDrillValue({ name: '验证' }), '验证');
    assert.equal(resolvePreferredDrillValue({ data: { name: '实施' } }), '实施');
    assert.equal(resolvePreferredDrillValue({ row: ['PLM整合', '李工'] }), 'PLM整合');
    assert.equal(resolvePreferredDrillValue({ 项目: '不应读取' }), undefined);
});

test('resolveActionMappingValues uses the same neutral source-path mapping as drilldown', () => {
    assert.deepEqual(
        resolveActionMappingValues(
            { data: { key: 'A-01' } },
            [{ sourcePath: 'data.key', variableKey: 'selectedKey', transform: 'string' }],
        ),
        { selectedKey: 'A-01' },
    );
});

test('explicit actions suppress the implicit default drill attempt', () => {
    assert.equal(shouldRunDefaultDrill({
        drillActive: true,
        canDrillDown: true,
        loading: false,
        actionCount: 0,
    }), true);
    assert.equal(shouldRunDefaultDrill({
        drillActive: true,
        canDrillDown: true,
        loading: false,
        actionCount: 1,
    }), false);
});

test('normalizeRuntimeJumpUrl rewrites duplicate /bi prefixes into hash-friendly app routes', () => {
    const resolveAppRoute = (route: string) => `/#${route}`;

    assert.equal(
        normalizeRuntimeJumpUrl('/bi/bi/gpmc/execution', {
            currentOrigin: 'https://bi.example.com',
            resolveAppRoute,
        }),
        '/#/bi/gpmc/execution',
    );

    assert.equal(
        normalizeRuntimeJumpUrl('https://bi.example.com/bi/bi/gpmc/drill/execution?screen=1', {
            currentOrigin: 'https://bi.example.com',
            resolveAppRoute,
        }),
        '/#/bi/gpmc/drill/execution?screen=1',
    );

    assert.equal(
        normalizeRuntimeJumpUrl('https://external.example.com/bi/gpmc/execution', {
            currentOrigin: 'https://bi.example.com',
            resolveAppRoute,
        }),
        'https://external.example.com/bi/gpmc/execution',
    );
});
