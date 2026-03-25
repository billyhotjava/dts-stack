type StorageLike = Pick<Storage, 'getItem'>;

export function resolveInitialRightPanelTab(storage?: StorageLike): 'property' | 'layer' {
    const raw = storage?.getItem('dts.analytics.screenDesigner.rightPanelTab');
    return raw === 'layer' ? 'layer' : 'property';
}

export function resolveInitialFocusMode(storage?: StorageLike): boolean {
    return storage?.getItem('dts.analytics.screenDesigner.focusMode') === 'true';
}

export function resolveInitialSidePanelVisibility(_storage?: StorageLike): {
    showLibraryPanel: boolean;
    showInspectorPanel: boolean;
} {
    return {
        showLibraryPanel: true,
        showInspectorPanel: true,
    };
}
