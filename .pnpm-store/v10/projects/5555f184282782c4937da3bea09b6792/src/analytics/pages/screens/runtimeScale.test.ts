import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveRuntimeScale } from './runtimeScale';

test('resolveRuntimeScale keeps 1080p screens at 1x without safe-area shrinking in fullscreen mode', () => {
    const result = resolveRuntimeScale({
        viewportWidth: 1920,
        viewportHeight: 1080,
        screenWidth: 1920,
        screenHeight: 1080,
        fullscreen: true,
        allowUpscale: true,
    });

    assert.equal(result.scale, 1);
    assert.equal(result.stageWidth, 1920);
    assert.equal(result.stageHeight, 1080);
});

test('resolveRuntimeScale allows 2k 16:9 screens to scale up without clamping at 1x', () => {
    const result = resolveRuntimeScale({
        viewportWidth: 2560,
        viewportHeight: 1440,
        screenWidth: 1920,
        screenHeight: 1080,
        fullscreen: true,
        allowUpscale: true,
    });

    assert.equal(result.scale, 2560 / 1920);
    assert.equal(result.stageWidth, 2560);
    assert.equal(result.stageHeight, 1440);
});

test('resolveRuntimeScale keeps legacy preview safe-area subtraction when fullscreen mode is disabled', () => {
    const result = resolveRuntimeScale({
        viewportWidth: 1920,
        viewportHeight: 1080,
        screenWidth: 1920,
        screenHeight: 1080,
        fullscreen: false,
        allowUpscale: false,
    });

    assert.equal(result.safeWidth, 1896);
    assert.equal(result.safeHeight, 1016);
    assert.equal(result.scale, 1016 / 1080);
});
