import assert from 'node:assert/strict';
import test from 'node:test';
import { hasInteractiveRuntimeFilters, resolveRuntimeCanvasScaleStyle } from './runtimeCanvasStyle';
import type { ScreenComponent } from './types';

function component(type: ScreenComponent['type']): ScreenComponent {
    return {
        id: `${type}-1`,
        type,
        name: type,
        x: 0,
        y: 0,
        width: 100,
        height: 40,
        zIndex: 1,
        locked: false,
        visible: true,
        config: {},
    };
}

test('hasInteractiveRuntimeFilters detects runtime filter controls', () => {
    assert.equal(hasInteractiveRuntimeFilters([component('title')]), false);
    assert.equal(hasInteractiveRuntimeFilters([component('filter-select')]), true);
    assert.equal(hasInteractiveRuntimeFilters([component('filter-input')]), true);
    assert.equal(hasInteractiveRuntimeFilters([component('filter-date-range')]), true);
});

test('resolveRuntimeCanvasScaleStyle prefers zoom for interactive filter controls', () => {
    const style = resolveRuntimeCanvasScaleStyle(0.75, [component('filter-select')]);
    assert.equal(style.transform, undefined);
    assert.equal(style.zoom, 0.75);
  });

test('resolveRuntimeCanvasScaleStyle keeps transform scaling for non-filter pages', () => {
    const style = resolveRuntimeCanvasScaleStyle(0.75, [component('title')]);
    assert.equal(style.zoom, undefined);
    assert.equal(style.transform, 'scale(0.75)');
    assert.equal(style.transformOrigin, 'top left');
});
