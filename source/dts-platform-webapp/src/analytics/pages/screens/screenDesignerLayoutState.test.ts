import assert from 'node:assert/strict';
import test from 'node:test';
import {
    clampSidePanelWidth,
    getSidePanelVisibilityStorageKey,
    resolveInitialSidePanelVisibility,
    resolveInitialSidePanelWidths,
} from './screenDesignerLayoutState';

test('designer side panels default to visible even if old localStorage says false', () => {
    const storage = {
        getItem(key: string) {
            if (key === 'dts.analytics.screenDesigner.showLibraryPanel') return 'false';
            if (key === 'dts.analytics.screenDesigner.showInspectorPanel') return 'false';
            return null;
        },
    };

    assert.deepEqual(resolveInitialSidePanelVisibility(storage), {
        showLibraryPanel: true,
        showInspectorPanel: true,
    });
});

test('designer side panels honor v2 visibility storage keys', () => {
    const storage = {
        getItem(key: string) {
            if (key === getSidePanelVisibilityStorageKey('library')) return 'false';
            if (key === getSidePanelVisibilityStorageKey('inspector')) return 'true';
            return null;
        },
    };

    assert.deepEqual(resolveInitialSidePanelVisibility(storage), {
        showLibraryPanel: false,
        showInspectorPanel: true,
    });
});

test('designer side panel widths are clamped and persisted independently', () => {
    assert.equal(clampSidePanelWidth('library', 100), 260);
    assert.equal(clampSidePanelWidth('library', 999), 420);
    assert.equal(clampSidePanelWidth('inspector', 480), 480);

    const storage = {
        getItem(key: string) {
            if (key.endsWith('libraryPanelWidth')) return '312';
            if (key.endsWith('inspectorPanelWidth')) return '999';
            return null;
        },
    };

    assert.deepEqual(resolveInitialSidePanelWidths(storage), {
        libraryWidth: 312,
        inspectorWidth: 520,
    });
});
