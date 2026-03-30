import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';

test('builtinPluginAdapters keeps a static import.meta.glob call for custom plugin adapters', () => {
    const source = readFileSync(new URL('./builtinPluginAdapters.tsx', import.meta.url), 'utf-8');
    assert.ok(
        source.includes("import.meta.glob('./custom/*.tsx', { eager: true })"),
        'expected builtinPluginAdapters to use a static Vite glob for custom plugin adapters',
    );
});
