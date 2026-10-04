import type { ScreenUpdateConflictMeta } from '../../contracts';
import type { ScreenConfig, ScreenTheme } from '../../types';

export type PublishInfo = {
    screenId: string | number;
    versionNo: number | string;
    previewUrl: string;
    publicUrl?: string;
    warmupText?: string;
};

// DESIGN_ACTION_STORAGE_KEY / GOVERNANCE_ACTION_STORAGE_KEY / EXPORT_ACTION_STORAGE_KEY removed — menus use direct buttons now
export const VERSION_ACTION_STORAGE_KEY = 'dts.analytics.screen.header.versionAction';

export const THEME_OPTIONS: { value: ScreenTheme; label: string }[] = [
    { value: 'legacy-dark', label: '经典深蓝' },
    { value: 'titanium', label: '钛合金灰' },
    { value: 'glacier', label: '冰川白' },
    { value: 'light-business', label: '商务浅色' },
    { value: 'dark-command', label: '指挥深色' },
    { value: 'enterprise-light', label: '企业浅色' },
    { value: 'enterprise-dark', label: '企业深色' },
    { value: 'brand-custom', label: '自定义' },
];

export function buildPublishNoticeStorageKey(screenId: string | number): string {
    return `dts.analytics.screen.publishNotice.${screenId}`;
}

export function buildComponentConflictMeta(baseline: ScreenConfig): ScreenUpdateConflictMeta {
    const baseComponents = (baseline.components ?? []).map((item) => ({
        id: item.id,
        component: item,
    }));
    return {
        mode: 'component',
        baseUpdatedAt: baseline.updatedAt || null,
        baseScreen: {
            name: baseline.name ?? null,
            description: baseline.description ?? null,
            width: baseline.width,
            height: baseline.height,
            backgroundColor: baseline.backgroundColor ?? null,
            backgroundImage: baseline.backgroundImage ?? null,
            theme: baseline.theme ?? null,
        },
        baseComponents,
        baseVariables: baseline.globalVariables ?? [],
    };
}
