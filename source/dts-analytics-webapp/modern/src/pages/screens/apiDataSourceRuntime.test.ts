import assert from 'node:assert/strict';
import test from 'node:test';
import {
    buildApiRuntimeRequest,
    resolveApiRuntimePayload,
} from './apiDataSourceRuntime';

test('buildApiRuntimeRequest expands params from runtime variables and omits blank values', () => {
    const result = buildApiRuntimeRequest(
        {
            url: '/analytics/api/project-cockpit/screen/overview',
            method: 'GET',
            params: {
                programId: '{{programId}}',
                majorProjectId: '{{majorProjectId}}',
                dateFrom: '{{dateFrom}}',
                dateTo: '{{dateTo}}',
                deptId: '{{deptId}}',
                riskLevel: '{{riskLevel}}',
            },
        },
        {
            queryContext: {
                globalVariables: {
                    programId: 'program-a',
                    majorProjectId: '',
                    dateFrom: '2026-03-01',
                    dateTo: '2026-03-31',
                    deptId: '',
                    riskLevel: '高',
                },
            },
        },
    );

    assert.equal(result.params.programId, 'program-a');
    assert.equal(result.params.dateFrom, '2026-03-01');
    assert.equal(result.params.dateTo, '2026-03-31');
    assert.equal(result.params.riskLevel, '高');
    assert.equal(Object.hasOwn(result.params, 'majorProjectId'), false);
    assert.equal(Object.hasOwn(result.params, 'deptId'), false);
});

test('buildApiRuntimeRequest expands post body templates and keeps queryContext payload', () => {
    const result = buildApiRuntimeRequest(
        {
            url: '/analytics/api/project-cockpit/screen/filters',
            method: 'POST',
            body: '{"programId":"{{programId}}","dateFrom":"{{dateFrom}}","riskLevel":"{{riskLevel}}"}',
        },
        {
            queryContext: {
                componentId: 'screen-overview',
                globalVariables: {
                    programId: 'program-a',
                    dateFrom: '2026-03-01',
                    riskLevel: '',
                },
            },
        },
    );

    assert.equal(result.method, 'POST');
    assert.equal(result.body, JSON.stringify({
        programId: 'program-a',
        dateFrom: '2026-03-01',
        riskLevel: '',
        queryContext: {
            componentId: 'screen-overview',
            globalVariables: {
                programId: 'program-a',
                dateFrom: '2026-03-01',
                riskLevel: '',
            },
        },
    }));
});

test('resolveApiRuntimePayload extracts nested arrays and wraps a nested object as rows', () => {
    const payload = {
        kpis: [
            { key: 'periodNodeTotalCount', label: '项目本周期节点总数', value: '8', unit: '个' },
            { key: 'completionRate', label: '节点完成百分比', value: '37.5', unit: '%' },
        ],
        spotlight: {
            majorProjectName: '苍穹导航综合工程',
            highRiskCount: 3,
        },
    };

    assert.deepEqual(resolveApiRuntimePayload(payload, 'kpis'), payload.kpis);
    assert.deepEqual(resolveApiRuntimePayload(payload, 'spotlight'), [payload.spotlight]);
});

test('resolveApiRuntimePayload wraps primitive leaves for number-card friendly parsing', () => {
    const payload = {
        governanceSummary: {
            delayedNodeCount: 6,
        },
    };

    assert.deepEqual(resolveApiRuntimePayload(payload, 'governanceSummary.delayedNodeCount'), [[6]]);
});
