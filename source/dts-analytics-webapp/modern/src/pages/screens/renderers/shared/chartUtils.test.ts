import test from 'node:test';
import assert from 'node:assert/strict';

import { resolveDateRangeDefaultValues, resolveFilterDefaultValue } from './chartUtils';

test('resolveFilterDefaultValue prefers current runtime value when present', () => {
    assert.equal(resolveFilterDefaultValue('QMS二期', 'PLM整合', [
        { label: 'QMS二期', value: 'QMS二期' },
        { label: 'PLM整合', value: 'PLM整合' },
    ]), 'QMS二期');
});

test('resolveFilterDefaultValue falls back to configured default or first option', () => {
    assert.equal(resolveFilterDefaultValue('', 'PLM整合', [
        { label: 'QMS二期', value: 'QMS二期' },
        { label: 'PLM整合', value: 'PLM整合' },
    ]), 'PLM整合');

    assert.equal(resolveFilterDefaultValue('', '', [
        { label: 'QMS二期', value: 'QMS二期' },
        { label: 'PLM整合', value: 'PLM整合' },
    ]), 'QMS二期');
});

test('resolveDateRangeDefaultValues fills missing values only', () => {
    assert.deepEqual(
        resolveDateRangeDefaultValues('', '2026-03-31', '2026-03-01', '2026-03-31'),
        { startValue: '2026-03-01', endValue: '2026-03-31' },
    );
});
