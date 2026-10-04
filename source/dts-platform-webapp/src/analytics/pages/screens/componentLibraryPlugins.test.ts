import assert from 'node:assert/strict';
import test from 'node:test';
import { mapPluginManifestToCategory } from './componentLibraryPlugins';

test('mapPluginManifestToCategory hides remote plugin components that are not installed', () => {
    const category = mapPluginManifestToCategory({
        id: 'demo-stat-pack',
        name: 'Demo 统计组件包',
        version: '1.0.0',
        components: [
            {
                id: 'kpi-card-pro',
                name: 'KPI 卡片',
                baseType: 'number-card',
                installed: false,
            },
        ],
    });

    assert.equal(category, null);
});

test('mapPluginManifestToCategory removes the finance template family from the editor library', () => {
    const category = mapPluginManifestToCategory({
        id: 'finance-kit',
        name: '财务模板族',
        version: '1.0.0',
        components: [
            {
                id: 'kpi-card',
                name: '财务KPI卡',
                baseType: 'number-card',
                installed: true,
            },
        ],
    });

    assert.equal(category, null);
});

test('mapPluginManifestToCategory keeps installed and local plugin components visible', () => {
    const installedCategory = mapPluginManifestToCategory({
        id: 'demo-stat-pack',
        name: 'Demo 统计组件包',
        version: '1.0.0',
        components: [
            {
                id: 'kpi-card-pro',
                name: 'KPI 卡片',
                baseType: 'number-card',
                installed: true,
            },
        ],
    });
    assert.ok(installedCategory);
    assert.equal(installedCategory.items.length, 1);
    assert.equal(installedCategory.items[0].name, 'KPI 卡片');

    const localCategory = mapPluginManifestToCategory({
        id: 'custom-pack',
        name: 'Custom Pack',
        version: '0.1.0',
        components: [
            {
                id: 'trend-pro',
                name: 'Trend Pro',
                baseType: 'line-chart',
            },
        ],
    });
    assert.ok(localCategory);
    assert.equal(localCategory.items.length, 1);
    assert.equal(localCategory.items[0].type, 'line-chart');
});
