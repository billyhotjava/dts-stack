import test from 'node:test';
import assert from 'node:assert/strict';

import { resolveChartTitleLayout } from './chartTitleLayout';

test('resolveChartTitleLayout centers title by default and exposes draggable box', () => {
    const layout = resolveChartTitleLayout({
        text: '技术状态变更',
        width: 300,
        height: 156,
        fontSize: 15,
        color: '#ffffff',
        defaultPosition: 'center',
    });

    assert.equal(layout.position, 'center');
    assert.equal(layout.titleOption.textAlign, 'center');
    assert.equal(layout.titleOption.top, 6);
    assert.ok(typeof layout.titleOption.left === 'number');
    assert.ok(layout.handleRect.width > 80);
    assert.ok(layout.handleRect.left >= 0);
});

test('resolveChartTitleLayout respects explicit left position and drag offsets', () => {
    const layout = resolveChartTitleLayout({
        text: '风险分类排名',
        width: 300,
        height: 212,
        fontSize: 15,
        color: '#ffffff',
        positionRaw: 'left',
        offsetX: 24,
        offsetY: 10,
        defaultPosition: 'center',
    });

    assert.equal(layout.position, 'left');
    assert.equal(layout.titleOption.textAlign, 'left');
    assert.equal(layout.titleOption.left, 36);
    assert.equal(layout.titleOption.top, 16);
});
