import assert from 'node:assert/strict';
import test from 'node:test';
import { applyFieldMapping } from './fieldMappingTransform';

test('applyFieldMapping maps radar-chart rows into indicator max values and series data', () => {
    const mapped = applyFieldMapping(
        'radar-chart',
        {
            dimension: 'name',
            measures: ['score'],
        },
        {
            cols: [
                { name: 'name', display_name: 'name', base_type: 'type/Text' },
                { name: 'score', display_name: 'score', base_type: 'type/Float' },
                { name: 'max', display_name: 'max', base_type: 'type/Float' },
            ],
            rows: [
                ['完成率', 82.5, 100],
                ['按时完成率', 71.25, 100],
                ['里程碑完成率', 66.67, 100],
            ],
        },
    );

    assert.deepEqual(mapped.indicator, [
        { name: '完成率', max: 100 },
        { name: '按时完成率', max: 100 },
        { name: '里程碑完成率', max: 100 },
    ]);
    assert.deepEqual(mapped.series, [
        {
            name: 'score',
            data: [82.5, 71.25, 66.67],
        },
    ]);
});
