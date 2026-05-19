import assert from 'node:assert/strict';
import test from 'node:test';
import { COMPONENT_CONFIG_SCHEMAS } from './index';

function fieldKeys(type: string): string[] {
    return (COMPONENT_CONFIG_SCHEMAS[type]?.fields || []).map((field) => field.key);
}

test('filter date range schema exposes renderer-compatible variable keys', () => {
    const keys = fieldKeys('filter-date-range');

    assert.ok(keys.includes('startKey'));
    assert.ok(keys.includes('endKey'));
    assert.equal(keys.includes('startVariableKey'), false);
    assert.equal(keys.includes('endVariableKey'), false);
});

test('filter select schema exposes dynamic option source fields', () => {
    const keys = fieldKeys('filter-select');

    assert.ok(keys.includes('optionSourceMode'));
    assert.ok(keys.includes('dataOptionLabelField'));
    assert.ok(keys.includes('dataOptionValueField'));
    assert.ok(keys.includes('dataOptionMax'));
    assert.ok(keys.includes('optionBackground'));
    assert.ok(keys.includes('optionTextColor'));
});
