import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveInitialSidePanelVisibility } from './screenDesignerLayoutState';

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
