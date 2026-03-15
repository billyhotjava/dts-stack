import assert from 'node:assert/strict';
import test from 'node:test';
import { buildExploreSessionSteps } from './ScreenHeader.helpers';

test('buildExploreSessionSteps sorts visible component outline by zIndex and caps length', () => {
    const steps = buildExploreSessionSteps({
        id: 'screen-1',
        name: '运营驾驶舱',
        width: 1920,
        height: 1080,
        theme: 'legacy-dark',
        components: Array.from({ length: 22 }, (_, index) => ({
            id: `cmp-${index}`,
            type: 'number-card',
            name: `组件 ${index}`,
            x: 0,
            y: 0,
            width: 200,
            height: 120,
            zIndex: 22 - index,
            locked: false,
            visible: index % 2 === 0,
            config: {},
            dataSource: { type: 'static' },
        })),
        globalVariables: [{ key: 'dept', label: '部门', type: 'string' }],
    });

    assert.equal(steps.length, 2);
    const snapshot = steps[0];
    const outline = steps[1];
    assert.equal(snapshot.title, '大屏快照');
    assert.equal((snapshot.params as Record<string, unknown>).componentCount, 22);
    assert.equal((snapshot.params as Record<string, unknown>).globalVariableCount, 1);
    const components = (outline.params as Record<string, unknown>).components as Array<Record<string, unknown>>;
    assert.equal(components.length, 20);
    assert.equal(components[0].id, 'cmp-21');
    assert.equal(components[0].visible, false);
    assert.equal(components[0].dataSourceType, 'static');
});
