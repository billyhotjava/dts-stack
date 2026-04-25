// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import type { ScreenUpdateConflictMeta } from '../../contracts';
import type { ScreenConfig, ScreenTheme } from '../../types';

export type PublishInfo = {
    screenId: string | number;
    versionNo: number | string;
    previewUrl: string;
    publicUrl?: string;
    warmupText?: string;
};

export type HeaderActionNotice = {
    tone: 'success' | 'error';
    title: string;
    message: string;
};

export type QuickActionItem = {
    id: string;
    label: string;
    keywords: string;
    disabled: boolean;
    hotkey?: string;
    run: () => void | Promise<void>;
};

// DESIGN_ACTION_STORAGE_KEY / GOVERNANCE_ACTION_STORAGE_KEY / EXPORT_ACTION_STORAGE_KEY removed — menus use direct buttons now
export const VERSION_ACTION_STORAGE_KEY = 'dts.analytics.screen.header.versionAction';
export const QUICK_ACTION_RECENT_STORAGE_KEY = 'dts.analytics.screen.header.quickRecentActions';
export const PRIMARY_ACTION_STORAGE_KEY = 'dts.analytics.screen.header.primaryAction';

export const THEME_OPTIONS: { value: ScreenTheme | ''; label: string }[] = [
    { value: '', label: '经典深蓝' },
    { value: 'titanium', label: '钛合金灰' },
    { value: 'glacier', label: '冰川白' },
    { value: 'light-business', label: '商务浅色' },
    { value: 'dark-command', label: '指挥深色' },
    { value: 'brand-custom', label: '自定义' },
];

export const BATCH_ACTION_OPTIONS = [
    { value: 'duplicate', label: '复制一份' },
    { value: 'copy', label: '复制' },
    { value: 'paste', label: '粘贴' },
    { value: 'delete', label: '删除' },
    { value: 'bring-top', label: '置于顶层' },
    { value: 'send-bottom', label: '置于底层' },
    { value: 'show', label: '显示' },
    { value: 'hide', label: '隐藏' },
    { value: 'lock', label: '锁定' },
    { value: 'unlock', label: '解锁' },
] as const;

export type BatchAction = typeof BATCH_ACTION_OPTIONS[number]['value'];

export function findNextEnabledQuickActionIndex(
    actions: QuickActionItem[],
    startIndex: number,
    direction: 1 | -1,
): number {
    if (actions.length === 0) {
        return -1;
    }
    let cursor = startIndex;
    for (let step = 0; step < actions.length; step += 1) {
        cursor = (cursor + direction + actions.length) % actions.length;
        if (!actions[cursor]?.disabled) {
            return cursor;
        }
    }
    return -1;
}

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
