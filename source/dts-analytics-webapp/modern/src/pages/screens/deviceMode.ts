export type DeviceMode = 'pc' | 'tablet' | 'mobile';

export function parseForcedDeviceModeFromSearch(search: string | undefined): DeviceMode | null {
    const raw = new URLSearchParams(search || '').get('device');
    const normalized = String(raw || '').trim().toLowerCase();
    if (normalized === 'pc' || normalized === 'tablet' || normalized === 'mobile') {
        return normalized;
    }
    return null;
}

export function parseForcedDeviceModeFromWindow(): DeviceMode | null {
    if (typeof window === 'undefined') return null;
    return parseForcedDeviceModeFromSearch(window.location.search);
}

export function resolveDeviceModeByViewport(width: number): DeviceMode {
    return width <= 768 ? 'mobile' : (width <= 1200 ? 'tablet' : 'pc');
}

export function isVisibleForDevice(component: { config?: Record<string, unknown> }, device: DeviceMode): boolean {
    const raw = component?.config?.visibleOn;
    if (!Array.isArray(raw) || raw.length === 0) {
        return true;
    }
    return raw.includes(device);
}

export function syncDeviceModeToWindowUrl(mode: DeviceMode | null): void {
    if (typeof window === 'undefined') return;
    const url = new URL(window.location.href);
    if (!mode) {
        url.searchParams.delete('device');
    } else {
        url.searchParams.set('device', mode);
    }
    window.history.replaceState(null, '', url.toString());
}

