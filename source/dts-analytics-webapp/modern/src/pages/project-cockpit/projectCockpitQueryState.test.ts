import assert from 'node:assert/strict';
import test from 'node:test';
import {
    DEFAULT_PROJECT_COCKPIT_THEME,
    createProjectCockpitScopeResetPatch,
    parseProjectCockpitQueryState,
    resolveProjectCockpitEffectiveQueryState,
    serializeProjectCockpitQueryState,
} from './projectCockpitQueryState';

test('parseProjectCockpitQueryState reads supported filters from URLSearchParams', () => {
    const params = new URLSearchParams(
        'theme=tree&programId=program-a&majorProjectId=major-aurora&dateFrom=2026-01-01&dateTo=2026-03-31&deptId=dept-pmo&riskLevel=%E9%AB%98',
    );

    assert.deepEqual(parseProjectCockpitQueryState(params), {
        theme: 'tree',
        programId: 'program-a',
        majorProjectId: 'major-aurora',
        dateFrom: '2026-01-01',
        dateTo: '2026-03-31',
        deptId: 'dept-pmo',
        riskLevel: '高',
    });
});

test('parseProjectCockpitQueryState falls back to overview theme when unsupported', () => {
    const params = new URLSearchParams('theme=unknown');
    const state = parseProjectCockpitQueryState(params);

    assert.equal(state.theme, DEFAULT_PROJECT_COCKPIT_THEME);
    assert.equal(state.majorProjectId, '');
    assert.equal(state.dateFrom, '');
    assert.equal(state.dateTo, '');
});

test('resolveProjectCockpitEffectiveQueryState falls back to published period when local dates are blank', () => {
    const effective = resolveProjectCockpitEffectiveQueryState(
        {
            theme: 'overview',
            programId: 'program-a',
            majorProjectId: '',
            dateFrom: '',
            dateTo: '',
            deptId: '',
            riskLevel: '',
        },
        {
            periodStart: '2026-03-01',
            periodEnd: '2026-03-31',
        },
    );

    assert.equal(effective.dateFrom, '2026-03-01');
    assert.equal(effective.dateTo, '2026-03-31');
    assert.equal(effective.programId, 'program-a');
});

test('createProjectCockpitScopeResetPatch clears scope filters but keeps current dates', () => {
    assert.deepEqual(
        createProjectCockpitScopeResetPatch({
            theme: 'support',
            programId: 'program-a',
            majorProjectId: 'major-aurora',
            dateFrom: '2026-03-01',
            dateTo: '2026-03-31',
            deptId: 'dept-pmo',
            riskLevel: '高',
        }),
        {
            programId: '',
            majorProjectId: '',
            deptId: '',
            riskLevel: '',
        },
    );
});

test('serializeProjectCockpitQueryState omits empty values and keeps theme stable', () => {
    const params = serializeProjectCockpitQueryState({
        theme: 'overview',
        programId: '',
        majorProjectId: 'major-aurora',
        dateFrom: '',
        dateTo: '2026-03-31',
        deptId: '',
        riskLevel: '高',
    });

    assert.equal(params.get('theme'), 'overview');
    assert.equal(params.get('majorProjectId'), 'major-aurora');
    assert.equal(params.get('dateTo'), '2026-03-31');
    assert.equal(params.get('riskLevel'), '高');
    assert.equal(params.has('programId'), false);
    assert.equal(params.has('dateFrom'), false);
});
