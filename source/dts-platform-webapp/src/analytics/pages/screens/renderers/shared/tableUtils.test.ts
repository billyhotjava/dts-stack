import test from 'node:test';
import assert from 'node:assert/strict';

import { estimateTablePlaceholderRowCount } from './tableUtils';

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
