import { useState, useCallback, useMemo, useEffect, useRef, type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useScreen } from '../ScreenContext';
import { detectInteractionCycles } from '../interactionGraph';
import {
    analyticsApi,
    HttpError,
    type ScreenDetail,
    type ScreenEditLock,
    type ScreenVersion,
    type ScreenVersionDiff,
} from '../../../api/analyticsApi';
import { GlobalVariableManager } from './GlobalVariableManager';
import { CacheObservabilityPanel } from './CacheObservabilityPanel';
import { ScreenCompliancePanel } from './ScreenCompliancePanel';
import { ScreenAclPanel } from './ScreenAclPanel';
import { ScreenAuditPanel } from './ScreenAuditPanel';
import { ScreenSharePolicyPanel } from './ScreenSharePolicyPanel';
import { ScreenHealthPanel } from './ScreenHealthPanel';
import { InteractionDebugPanel } from './InteractionDebugPanel';
import { ScreenCollaborationPanel } from './ScreenCollaborationPanel';
import { ScreenEditLockPanel } from './ScreenEditLockPanel';
import { ScreenConflictPanel, type ScreenUpdateConflict } from './ScreenConflictPanel';
import { ScreenVersionComparePanel } from './ScreenVersionComparePanel';
import { ScreenVersionComparePickerPanel } from './ScreenVersionComparePickerPanel';
import { ScreenVersionRollbackPanel } from './ScreenVersionRollbackPanel';
import { VersionHistoryPanel } from './VersionHistoryPanel';
import { ScreenSnapshotPanel } from './ScreenSnapshotPanel';
import { buildScreenPayload, normalizeScreenConfig, validateScreenPayload } from '../specV2';
import { resolveScreenTheme } from '../screenThemes';
import type { ScreenConfig } from '../types';
import { writeTextToClipboard } from '../../../hooks/clipboard';

type PublishNotice = {
    screenId: string | number;
    versionNo: number | string;
    previewUrl: string;
    publicUrl: string | null;
    warmupText?: string;
};

type QuickActionItem = {
    id: string;
    label: string;
    keywords: string;
    disabled: boolean;
    hotkey?: string;
    run: () => void | Promise<void>;
};

const DESIGN_ACTION_STORAGE_KEY = 'dts.analytics.screen.header.designAction';
const GOVERNANCE_ACTION_STORAGE_KEY = 'dts.analytics.screen.header.governanceAction';
const VERSION_ACTION_STORAGE_KEY = 'dts.analytics.screen.header.versionAction';
const EXPORT_ACTION_STORAGE_KEY = 'dts.analytics.screen.header.exportAction';
const QUICK_ACTION_RECENT_STORAGE_KEY = 'dts.analytics.screen.header.quickRecentActions';
const PRIMARY_ACTION_STORAGE_KEY = 'dts.analytics.screen.header.primaryAction';
const TOOLS_SECTION_STORAGE_KEY = 'dts.analytics.screen.header.toolsSection';

function findNextEnabledQuickActionIndex(
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

function buildPublishNoticeStorageKey(screenId: string | number): string {
    return `dts.analytics.screen.publishNotice.${screenId}`;
}

function buildExploreSessionSteps(config: ScreenConfig): Array<Record<string, unknown>> {
    const now = new Date().toISOString();
    const componentOutline = [...(config.components ?? [])]
        .sort((a, b) => (a.zIndex || 0) - (b.zIndex || 0))
        .slice(0, 20)
        .map((item) => ({
            id: item.id,
            type: item.type,
            name: item.name,
            visible: item.visible !== false,
            dataSourceType: item.dataSource?.sourceType ?? item.dataSource?.type ?? 'static',
        }));
    return [
        {
            at: now,
            title: '大屏快照',
            type: 'screen_snapshot',
            params: {
                screenId: config.id || null,
                screenName: config.name || null,
                width: config.width,
                height: config.height,
                theme: config.theme || null,
                componentCount: config.components?.length ?? 0,
                globalVariableCount: config.globalVariables?.length ?? 0,
            },
        },
        {
            at: now,
            title: '关键组件概览',
            type: 'component_outline',
            params: {
                components: componentOutline,
            },
        },
    ];
}

function buildComponentConflictMeta(baseline: ScreenConfig): Record<string, unknown> {
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

function HeaderMenu({
    label,
    open,
    onToggle,
    children,
}: {
    label: string;
    open: boolean;
    onToggle: () => void;
    children: ReactNode;
}) {
    return (
        <div className={`header-menu ${open ? 'is-open' : ''}`}>
            <button
                type="button"
                className="header-btn header-menu-trigger"
                aria-expanded={open}
                onClick={onToggle}
            >
                {label}
            </button>
            {open ? (
                <div className="header-menu-panel">
                    {children}
                </div>
            ) : null}
        </div>
    );
}

export function ScreenHeader() {
    const navigate = useNavigate();
    const { id } = useParams<{ id: string }>();
    const { state, updateConfig, loadConfig, markBaseline, selectComponents, isSaving, setIsSaving } = useScreen();
    const { config } = state;
    const [isEditingName, setIsEditingName] = useState(false);
    const [nameValue, setNameValue] = useState(config.name);
    const [isSharing, setIsSharing] = useState(false);
    const [isPublishing, setIsPublishing] = useState(false);
    const [isLoadingVersions, setIsLoadingVersions] = useState(false);
    const [showVariableManager, setShowVariableManager] = useState(false);
    const [showCachePanel, setShowCachePanel] = useState(false);
    const [showCompliancePanel, setShowCompliancePanel] = useState(false);
    const [showHealthPanel, setShowHealthPanel] = useState(false);
    const [showAclPanel, setShowAclPanel] = useState(false);
    const [showAuditPanel, setShowAuditPanel] = useState(false);
    const [showSharePolicyPanel, setShowSharePolicyPanel] = useState(false);
    const [showInteractionDebugPanel, setShowInteractionDebugPanel] = useState(false);
    const [showCollaborationPanel, setShowCollaborationPanel] = useState(false);
    const [showEditLockPanel, setShowEditLockPanel] = useState(false);
    const [showConflictPanel, setShowConflictPanel] = useState(false);
    const [showVersionComparePanel, setShowVersionComparePanel] = useState(false);
    const [showVersionComparePicker, setShowVersionComparePicker] = useState(false);
    const [showVersionRollbackPanel, setShowVersionRollbackPanel] = useState(false);
    const [showVersionHistoryPanel, setShowVersionHistoryPanel] = useState(false);
    const [showSnapshotPanel, setShowSnapshotPanel] = useState(false);
    const [showQuickActions, setShowQuickActions] = useState(false);
    const [quickKeyword, setQuickKeyword] = useState('');
    const [quickActiveIndex, setQuickActiveIndex] = useState(-1);
    const [quickRecentIds, setQuickRecentIds] = useState<string[]>(() => {
        if (typeof window === 'undefined') return [];
        try {
            const raw = window.localStorage.getItem(QUICK_ACTION_RECENT_STORAGE_KEY);
            if (!raw) return [];
            const parsed = JSON.parse(raw);
            if (!Array.isArray(parsed)) return [];
            return parsed
                .map((item) => String(item || '').trim())
                .filter((item) => item.length > 0)
                .slice(0, 8);
        } catch {
            return [];
        }
    });
    const [designAction, setDesignAction] = useState<
        'session' | 'variables' | 'interaction' | 'collaboration' | 'template' | 'import' | 'command'
    >(() => {
        if (typeof window === 'undefined') return 'variables';
        const raw = window.localStorage.getItem(DESIGN_ACTION_STORAGE_KEY);
        if (
            raw === 'session'
            || raw === 'variables'
            || raw === 'interaction'
            || raw === 'collaboration'
            || raw === 'template'
            || raw === 'import'
            || raw === 'command'
        ) {
            return raw;
        }
        return 'variables';
    });
    const [governanceAction, setGovernanceAction] = useState<
        'edit-lock' | 'cache' | 'compliance' | 'health' | 'acl' | 'audit' | 'share-policy' | 'share-link'
    >(() => {
        if (typeof window === 'undefined') return 'cache';
        const raw = window.localStorage.getItem(GOVERNANCE_ACTION_STORAGE_KEY);
        if (
            raw === 'edit-lock'
            || raw === 'cache'
            || raw === 'compliance'
            || raw === 'health'
            || raw === 'acl'
            || raw === 'audit'
            || raw === 'share-policy'
            || raw === 'share-link'
        ) {
            return raw;
        }
        return 'cache';
    });
    const [previewDeviceMode, setPreviewDeviceMode] = useState<'auto' | 'pc' | 'tablet' | 'mobile'>('auto');
    const [versionAction, setVersionAction] = useState<'history' | 'compare'>(() => {
        if (typeof window === 'undefined') return 'history';
        const raw = window.localStorage.getItem(VERSION_ACTION_STORAGE_KEY);
        return raw === 'compare' ? 'compare' : 'history';
    });
    const [exportAction, setExportAction] = useState<'json' | 'png' | 'pdf'>(() => {
        if (typeof window === 'undefined') return 'png';
        const raw = window.localStorage.getItem(EXPORT_ACTION_STORAGE_KEY);
        if (raw === 'json' || raw === 'pdf' || raw === 'png') {
            return raw;
        }
        return 'png';
    });
    const [primaryAction, setPrimaryAction] = useState<'preview' | 'publish' | 'save'>(() => {
        if (typeof window === 'undefined') return 'save';
        const raw = window.localStorage.getItem(PRIMARY_ACTION_STORAGE_KEY);
        if (raw === 'preview' || raw === 'publish' || raw === 'save') {
            return raw;
        }
        return 'save';
    });
    const [toolsSection, setToolsSection] = useState<'design' | 'release' | 'governance'>(() => {
        if (typeof window === 'undefined') return 'design';
        const raw = window.localStorage.getItem(TOOLS_SECTION_STORAGE_KEY);
        if (raw === 'design' || raw === 'release' || raw === 'governance') {
            return raw;
        }
        return 'design';
    });
    const [isSavingTemplate, setIsSavingTemplate] = useState(false);
    const [conflictLoading, setConflictLoading] = useState(false);
    const [lastConflict, setLastConflict] = useState<ScreenUpdateConflict | null>(null);
    const [versionDiff, setVersionDiff] = useState<ScreenVersionDiff | null>(null);
    const [versionCandidates, setVersionCandidates] = useState<ScreenVersion[]>([]);
    const [editLock, setEditLock] = useState<ScreenEditLock | null>(null);
    const [lockErrorText, setLockErrorText] = useState<string | null>(null);
    const [publishNotice, setPublishNotice] = useState<PublishNotice | null>(null);
    const importInputRef = useRef<HTMLInputElement | null>(null);
    const quickInputRef = useRef<HTMLInputElement | null>(null);
    const quickActionRefs = useRef<Array<HTMLButtonElement | null>>([]);
    const menuContainerRef = useRef<HTMLDivElement | null>(null);
    const [activeMenu, setActiveMenu] = useState<'primary' | 'tools' | null>(null);
    const [permissions, setPermissions] = useState({
        canRead: true,
        canEdit: true,
        canPublish: true,
        canManage: true,
    });

    const cycleWarnings = useMemo(() => detectInteractionCycles(config), [config]);
    const lockedByOther = !!(editLock?.active && !editLock?.mine);
    const lockOwnerText = String(editLock?.ownerName || editLock?.ownerId || '其他用户');

    useEffect(() => {
        if (!id || typeof window === 'undefined') {
            setPublishNotice(null);
            return;
        }
        try {
            const raw = window.localStorage.getItem(buildPublishNoticeStorageKey(id));
            if (!raw) {
                setPublishNotice(null);
                return;
            }
            const parsed = JSON.parse(raw) as PublishNotice;
            if (!parsed || String(parsed.screenId || '') !== String(id)) {
                setPublishNotice(null);
                return;
            }
            setPublishNotice(parsed);
        } catch {
            setPublishNotice(null);
        }
    }, [id]);

    useEffect(() => {
        if (!id || typeof window === 'undefined') {
            return;
        }
        const key = buildPublishNoticeStorageKey(id);
        if (!publishNotice || String(publishNotice.screenId || '') !== String(id)) {
            window.localStorage.removeItem(key);
            return;
        }
        window.localStorage.setItem(key, JSON.stringify(publishNotice));
    }, [id, publishNotice]);

    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(DESIGN_ACTION_STORAGE_KEY, designAction);
    }, [designAction]);

    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(GOVERNANCE_ACTION_STORAGE_KEY, governanceAction);
    }, [governanceAction]);

    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(VERSION_ACTION_STORAGE_KEY, versionAction);
    }, [versionAction]);

    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(EXPORT_ACTION_STORAGE_KEY, exportAction);
    }, [exportAction]);

    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(PRIMARY_ACTION_STORAGE_KEY, primaryAction);
    }, [primaryAction]);

    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(TOOLS_SECTION_STORAGE_KEY, toolsSection);
    }, [toolsSection]);

    useEffect(() => {
        if (!id && primaryAction === 'publish') {
            setPrimaryAction('save');
        }
    }, [id, primaryAction]);

    useEffect(() => {
        if (typeof window === 'undefined') return;
        if (!quickRecentIds.length) {
            window.localStorage.removeItem(QUICK_ACTION_RECENT_STORAGE_KEY);
            return;
        }
        window.localStorage.setItem(QUICK_ACTION_RECENT_STORAGE_KEY, JSON.stringify(quickRecentIds.slice(0, 8)));
    }, [quickRecentIds]);

    useEffect(() => {
        if (!activeMenu) {
            return;
        }
        const handlePointerDown = (event: MouseEvent) => {
            const node = menuContainerRef.current;
            if (!node) {
                return;
            }
            if (!node.contains(event.target as Node)) {
                setActiveMenu(null);
            }
        };
        const handleEscape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                setActiveMenu(null);
            }
        };
        window.addEventListener('mousedown', handlePointerDown);
        window.addEventListener('keydown', handleEscape);
        return () => {
            window.removeEventListener('mousedown', handlePointerDown);
            window.removeEventListener('keydown', handleEscape);
        };
    }, [activeMenu]);

    useEffect(() => {
        if (!id) {
            setPermissions({ canRead: true, canEdit: true, canPublish: true, canManage: true });
            return;
        }
        let cancelled = false;
        analyticsApi.getScreen(id, { mode: 'draft', fallbackDraft: true })
            .then((screen) => {
                if (cancelled) return;
                setPermissions({
                    canRead: screen.canRead !== false,
                    canEdit: screen.canEdit !== false,
                    canPublish: screen.canPublish !== false,
                    canManage: screen.canManage !== false,
                });
            })
            .catch(() => {
                if (!cancelled) {
                    setPermissions({ canRead: true, canEdit: true, canPublish: true, canManage: false });
                }
            });
        return () => {
            cancelled = true;
        };
    }, [id]);

    const refreshLockState = useCallback(async () => {
        if (!id || !permissions.canRead) {
            setEditLock(null);
            return;
        }
        try {
            const lock = await analyticsApi.getScreenEditLock(id);
            setEditLock(lock);
            if (!lock?.active || lock.mine) {
                setLockErrorText(null);
            }
        } catch {
            // keep lock workflow non-blocking
        }
    }, [id, permissions.canRead]);

    useEffect(() => {
        if (!id || !permissions.canRead) {
            setEditLock(null);
            return;
        }
        void refreshLockState();
    }, [id, permissions.canRead, refreshLockState]);

    useEffect(() => {
        if (!id || !permissions.canEdit) {
            return;
        }
        let cancelled = false;
        const bootstrap = async () => {
            try {
                const lock = await analyticsApi.acquireScreenEditLock(id, { ttlSeconds: 120 });
                if (!cancelled) {
                    setEditLock(lock);
                    setLockErrorText(null);
                }
            } catch (error) {
                if (cancelled) return;
                if (error instanceof HttpError) {
                    try {
                        const payload = JSON.parse(error.bodyText) as { lock?: ScreenEditLock; message?: string };
                        if (payload?.lock) {
                            setEditLock(payload.lock);
                        } else {
                            void refreshLockState();
                        }
                        setLockErrorText(payload?.message || error.message);
                    } catch {
                        setLockErrorText(error.message);
                        void refreshLockState();
                    }
                } else {
                    setLockErrorText(error instanceof Error ? error.message : '编辑锁申请失败');
                    void refreshLockState();
                }
            }
        };
        bootstrap();
        return () => {
            cancelled = true;
        };
    }, [id, permissions.canEdit, refreshLockState]);

    useEffect(() => {
        if (!id || !permissions.canEdit || !editLock?.mine) {
            return;
        }
        const timer = window.setInterval(async () => {
            try {
                const lock = await analyticsApi.heartbeatScreenEditLock(id, { ttlSeconds: 120 });
                setEditLock(lock);
            } catch {
                await refreshLockState();
            }
        }, 45000);
        return () => window.clearInterval(timer);
    }, [id, permissions.canEdit, editLock?.mine, refreshLockState]);

    useEffect(() => {
        if (!id || !permissions.canRead || editLock?.mine) {
            return;
        }
        const timer = window.setInterval(() => {
            void refreshLockState();
        }, 30000);
        return () => window.clearInterval(timer);
    }, [id, permissions.canRead, editLock?.mine, refreshLockState]);

    useEffect(() => {
        if (!id) return;
        const release = () => {
            if (editLock?.mine) {
                void analyticsApi.releaseScreenEditLock(id).catch(() => undefined);
            }
        };
        window.addEventListener('beforeunload', release);
        return () => {
            window.removeEventListener('beforeunload', release);
            release();
        };
    }, [id, editLock?.mine]);

    const applyScreenDetail = useCallback((screen: ScreenDetail) => {
        const normalized = normalizeScreenConfig(screen, { id: screen.id });
        if (normalized.warnings.length > 0) {
            console.warn('[screen-spec-v2] normalized with warnings:', normalized.warnings);
        }
        const resolvedTheme = resolveScreenTheme(normalized.config.theme, normalized.config.backgroundColor);
        loadConfig({ ...normalized.config, theme: resolvedTheme });
    }, [loadConfig]);

    const handleNameClick = () => {
        setNameValue(config.name);
        setIsEditingName(true);
    };

    const handleNameBlur = () => {
        setIsEditingName(false);
        if (nameValue.trim() && nameValue !== config.name) {
            updateConfig({ name: nameValue.trim() });
        }
    };

    const handleNameKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter') {
            handleNameBlur();
        } else if (e.key === 'Escape') {
            setIsEditingName(false);
            setNameValue(config.name);
        }
    };

    const saveScreen = useCallback(async (): Promise<string | number | undefined> => {
        if (isSaving) return id;

        setIsSaving(true);
        try {
            const payload = buildScreenPayload(config) as Record<string, unknown>;
            const baseline = state.baselineConfig;
            if (id && baseline?.updatedAt) {
                payload._conflict = buildComponentConflictMeta(baseline);
            }
            const validation = validateScreenPayload(payload);
            if (validation.errors.length > 0) {
                throw new Error(`配置校验失败：${validation.errors.join('；')}`);
            }
            if (validation.warnings.length > 0) {
                console.warn('[screen-spec-v2] save payload warnings:', validation.warnings);
            }

            if (id) {
                const updated = await analyticsApi.updateScreen(id, payload);
                const normalized = normalizeScreenConfig(updated, { id: updated.id });
                const resolvedTheme = resolveScreenTheme(normalized.config.theme, normalized.config.backgroundColor);
                const synced = { ...normalized.config, theme: resolvedTheme };
                updateConfig(synced);
                markBaseline(synced);
                return id;
            }

            const result = await analyticsApi.createScreen(payload);
            if (result.id) {
                navigate(`/screens/${result.id}/edit`, { replace: true });
            }
            return result.id;
        } finally {
            setIsSaving(false);
        }
    }, [config, id, isSaving, markBaseline, navigate, setIsSaving, state.baselineConfig, updateConfig]);

    const handleLockHttpError = useCallback((error: unknown, fallbackMessage: string): string => {
        if (error instanceof HttpError && error.code === 'SCREEN_EDIT_LOCKED') {
            let detail = fallbackMessage;
            try {
                const payload = JSON.parse(error.bodyText) as { message?: string; lock?: ScreenEditLock };
                if (payload?.lock) {
                    setEditLock(payload.lock);
                    const owner = String(payload.lock.ownerName || payload.lock.ownerId || '其他用户');
                    detail = `当前由 ${owner} 持有编辑锁，请稍后重试`;
                } else {
                    void refreshLockState();
                }
                setLockErrorText(payload?.message || detail);
            } catch {
                setLockErrorText(error.message);
                void refreshLockState();
                detail = error.message || fallbackMessage;
            }
            setShowEditLockPanel(true);
            return detail;
        }
        if (error instanceof Error && error.message) {
            return error.message;
        }
        return fallbackMessage;
    }, [refreshLockState]);

    const handleUpdateConflictError = useCallback((error: unknown, fallbackMessage: string): string => {
        if (!(error instanceof HttpError) || error.code !== 'SCREEN_UPDATE_CONFLICT') {
            return fallbackMessage;
        }
        let detail = fallbackMessage;
        try {
            const payload = JSON.parse(error.bodyText) as {
                code?: string;
                message?: string;
                componentIds?: unknown;
                fields?: unknown;
            };
            const next: ScreenUpdateConflict = {
                code: String(payload?.code || 'SCREEN_UPDATE_CONFLICT'),
                message: String(payload?.message || '检测到并发编辑冲突'),
                componentIds: Array.isArray(payload?.componentIds)
                    ? payload.componentIds.map((item) => String(item || '').trim()).filter(Boolean)
                    : [],
                fields: Array.isArray(payload?.fields)
                    ? payload.fields.map((item) => String(item || '').trim()).filter(Boolean)
                    : [],
            };
            setLastConflict(next);
            if (next.componentIds.length > 0) {
                selectComponents(next.componentIds);
            }
            setShowConflictPanel(true);
            detail = next.message || fallbackMessage;
        } catch {
            setLastConflict({
                code: 'SCREEN_UPDATE_CONFLICT',
                message: error.message || fallbackMessage,
                componentIds: [],
                fields: [],
            });
            setShowConflictPanel(true);
            detail = error.message || fallbackMessage;
        }
        return detail;
    }, [selectComponents]);

    const handleReloadLatestDraft = useCallback(async () => {
        if (!id) return;
        setConflictLoading(true);
        try {
            const latest = await analyticsApi.getScreen(id, { mode: 'draft', fallbackDraft: true });
            applyScreenDetail(latest);
            setShowConflictPanel(false);
        } catch (error) {
            const message = error instanceof Error ? error.message : '重载最新草稿失败';
            alert(message);
        } finally {
            setConflictLoading(false);
        }
    }, [applyScreenDetail, id]);

    const handleSave = useCallback(async () => {
        try {
            await saveScreen();
        } catch (error) {
            console.error('Failed to save screen:', error);
            const message = error instanceof HttpError && error.code === 'SCREEN_UPDATE_CONFLICT'
                ? handleUpdateConflictError(error, '保存失败，存在并发冲突')
                : handleLockHttpError(error, '保存失败');
            alert(message);
        }
    }, [handleLockHttpError, handleUpdateConflictError, saveScreen]);

    useEffect(() => {
        const isTypingTarget = (target: EventTarget | null): boolean => {
            const node = target as HTMLElement | null;
            if (!node) return false;
            const tag = node.tagName;
            if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
            return node.isContentEditable;
        };
        const handleKeyDown = (event: KeyboardEvent) => {
            const hotkey = event.ctrlKey || event.metaKey;
            if (!hotkey || event.key.toLowerCase() !== 's') {
                return;
            }
            if (isTypingTarget(event.target)) {
                return;
            }
            event.preventDefault();
            if (isSaving || !permissions.canEdit || lockedByOther) {
                return;
            }
            void handleSave();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleSave, isSaving, lockedByOther, permissions.canEdit]);

    const handleSaveAsTemplate = useCallback(async () => {
        if (isSavingTemplate) return;
        setIsSavingTemplate(true);
        try {
            const screenId = await saveScreen();
            if (!screenId) {
                alert('请先保存大屏后再存为模板');
                return;
            }
            const defaultName = (config.name || '未命名大屏') + '-模板';
            const templateName = (window.prompt('模板名称', defaultName) || '').trim();
            if (!templateName) {
                return;
            }
            const templateDesc = window.prompt('模板描述（可选）', config.description || '') || '';
            const visibilityInput = (window.prompt('模板可见范围（personal/team/global）', 'team') || 'team').trim().toLowerCase();
            const visibilityScope = visibilityInput === 'personal' || visibilityInput === 'global' ? visibilityInput : 'team';
            await analyticsApi.createScreenTemplateFromScreen(screenId, {
                name: templateName,
                description: templateDesc,
                category: 'custom',
                thumbnail: '🧩',
                visibilityScope,
                listed: true,
            });
            const scopeText = visibilityScope === 'personal' ? '个人' : visibilityScope === 'global' ? '全局' : '团队';
            alert(`已保存到${scopeText}模板`);
        } catch (error) {
            console.error('Failed to save screen as template:', error);
            const message = error instanceof HttpError && error.code === 'SCREEN_UPDATE_CONFLICT'
                ? handleUpdateConflictError(error, '存模板失败，存在并发冲突')
                : handleLockHttpError(error, '存模板失败');
            alert(message);
        } finally {
            setIsSavingTemplate(false);
        }
    }, [config.description, config.name, handleLockHttpError, handleUpdateConflictError, isSavingTemplate, saveScreen]);

    const handlePublish = useCallback(async () => {
        if (isPublishing) return;
        setIsPublishing(true);
        try {
            const screenId = await saveScreen();
            if (!screenId) {
                alert('请先保存大屏');
                return;
            }
            const result = await analyticsApi.publishScreen(screenId);
            const warmup = result.warmup;
            const versionNo = result.version && result.version.versionNo != null ? result.version.versionNo : '-';
            let warmupText = '';
            if (warmup) {
                warmupText = '\nWarmup: 总计 '
                    + (warmup.totalDatabaseSources || 0)
                    + '，成功 ' + (warmup.warmed || 0)
                    + '，跳过 ' + (warmup.skipped || 0)
                    + '，失败 ' + (warmup.failed || 0);
            }
            const previewUrl = `${window.location.origin}/analytics/screens/${encodeURIComponent(String(screenId))}/preview`;
            let publicUrl: string | null = null;
            try {
                const policy = await analyticsApi.createScreenPublicLink(screenId, {});
                if (policy?.uuid) {
                    publicUrl = `${window.location.origin}/analytics/public/screen/${policy.uuid}`;
                }
            } catch (linkError) {
                console.warn('Publish succeeded but creating public link failed:', linkError);
            }
            setPublishNotice({
                screenId,
                versionNo,
                previewUrl,
                publicUrl,
                warmupText,
            });
            alert('发布成功，版本 v' + versionNo + warmupText);
        } catch (error) {
            console.error('Failed to publish screen:', error);
            const message = error instanceof HttpError && error.code === 'SCREEN_UPDATE_CONFLICT'
                ? handleUpdateConflictError(error, '发布失败，存在并发冲突')
                : handleLockHttpError(error, '发布失败');
            alert(message);
        } finally {
            setIsPublishing(false);
        }
    }, [handleLockHttpError, handleUpdateConflictError, isPublishing, saveScreen]);

    useEffect(() => {
        if (!id || !permissions.canRead) {
            return;
        }
        if (publishNotice) {
            return;
        }
        let cancelled = false;
        const hydratePublishedNotice = async () => {
            try {
                const published = await analyticsApi.getScreen(id, { mode: 'published' });
                if (cancelled) {
                    return;
                }
                const versionNo = Number(published?.publishedVersionNo || 0);
                if (versionNo <= 0) {
                    return;
                }
                setPublishNotice({
                    screenId: id,
                    versionNo,
                    previewUrl: `${window.location.origin}/analytics/screens/${encodeURIComponent(String(id))}/preview`,
                    publicUrl: null,
                    warmupText: '',
                });
            } catch {
                // no published version or no permission, keep silent
            }
        };
        void hydratePublishedNotice();
        return () => {
            cancelled = true;
        };
    }, [id, permissions.canRead, publishNotice]);

    const handleVersionHistory = useCallback(async () => {
        if (!id || isLoadingVersions) return;

        setIsLoadingVersions(true);
        try {
            const versions = await analyticsApi.listScreenVersions(id);
            if (!versions.length) {
                alert('当前没有已发布版本');
                return;
            }
            setVersionCandidates(versions);
            setShowVersionHistoryPanel(true);
        } catch (error) {
            console.error('Failed to load version history:', error);
            const message = handleLockHttpError(error, '加载版本历史失败');
            alert(message);
        } finally {
            setIsLoadingVersions(false);
        }
    }, [handleLockHttpError, id, isLoadingVersions]);

    const handleConfirmVersionRollback = useCallback(async (versionId: string) => {
        if (!id) return;
        if (!window.confirm(`确认回滚到版本 ID=${versionId} 吗？`)) {
            return;
        }
        setIsLoadingVersions(true);
        try {
            const result = await analyticsApi.rollbackScreenVersion(id, versionId);
            if (result?.screen) {
                applyScreenDetail(result.screen);
            }
            setShowVersionRollbackPanel(false);
            alert('回滚成功，已切换草稿与发布版本');
        } catch (error) {
            console.error('Failed to rollback version:', error);
            const message = handleLockHttpError(error, '回滚失败');
            alert(message);
        } finally {
            setIsLoadingVersions(false);
        }
    }, [applyScreenDetail, handleLockHttpError, id]);

    const handleVersionCompare = useCallback(async () => {
        if (!id || isLoadingVersions) return;
        setIsLoadingVersions(true);
        try {
            const versions = await analyticsApi.listScreenVersions(id);
            if (!versions || versions.length < 2) {
                alert('至少需要两个版本才能对比');
                return;
            }
            setVersionCandidates(versions);
            setShowVersionComparePicker(true);
        } catch (error) {
            console.error('Failed to compare versions:', error);
            alert('版本对比失败');
        } finally {
            setIsLoadingVersions(false);
        }
    }, [id, isLoadingVersions]);

    const handleConfirmVersionCompare = useCallback(async (fromVersionId: string, toVersionId: string) => {
        if (!id) return;
        setIsLoadingVersions(true);
        try {
            const diff = await analyticsApi.compareScreenVersions(id, fromVersionId, toVersionId);
            setVersionDiff(diff);
            setShowVersionComparePicker(false);
            setShowVersionComparePanel(true);
        } catch (error) {
            console.error('Failed to compare versions:', error);
            alert('版本对比失败');
        } finally {
            setIsLoadingVersions(false);
        }
    }, [id]);

    const handlePreview = () => {
        if (id) {
            const suffix = previewDeviceMode === 'auto'
                ? ''
                : `?device=${encodeURIComponent(previewDeviceMode)}`;
            window.open(`/analytics/screens/${id}/preview${suffix}`, '_blank', 'noopener,noreferrer');
        } else {
            alert('请先保存大屏后再预览');
        }
    };

    const canExecutePrimaryAction = useMemo(() => {
        if (primaryAction === 'preview') {
            return Boolean(id);
        }
        if (primaryAction === 'publish') {
            return Boolean(id) && !isPublishing && permissions.canPublish && !lockedByOther;
        }
        return !isSaving && permissions.canEdit && !lockedByOther;
    }, [id, isPublishing, isSaving, lockedByOther, permissions.canEdit, permissions.canPublish, primaryAction]);

    const executePrimaryAction = useCallback(() => {
        if (primaryAction === 'preview') {
            handlePreview();
            return;
        }
        if (primaryAction === 'publish') {
            if (!id || isPublishing || !permissions.canPublish || lockedByOther) {
                return;
            }
            void handlePublish();
            return;
        }
        if (isSaving || !permissions.canEdit || lockedByOther) {
            return;
        }
        void handleSave();
    }, [
        handlePreview,
        handlePublish,
        handleSave,
        id,
        isPublishing,
        isSaving,
        lockedByOther,
        permissions.canEdit,
        permissions.canPublish,
        primaryAction,
    ]);

    useEffect(() => {
        const isTypingTarget = (target: EventTarget | null): boolean => {
            const node = target as HTMLElement | null;
            if (!node) return false;
            const tag = node.tagName;
            if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
            return node.isContentEditable;
        };
        const handleKeyDown = (event: KeyboardEvent) => {
            const hotkey = event.ctrlKey || event.metaKey;
            if (!hotkey || !event.shiftKey || event.key.toLowerCase() !== 'p') {
                return;
            }
            if (isTypingTarget(event.target)) {
                return;
            }
            event.preventDefault();
            handlePreview();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handlePreview]);

    const ensureExportAllowed = useCallback(async (format: 'json' | 'png' | 'pdf') => {
        if (!id) {
            return null;
        }
        try {
            return await analyticsApi.prepareScreenExport(id, {
                format,
                mode: 'draft',
                ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
            });
        } catch (error) {
            if (error instanceof HttpError) {
                let detail: string | null = null;
                try {
                    const payload = JSON.parse(error.bodyText) as { message?: string };
                    if (payload?.message) {
                        detail = payload.message;
                    }
                } catch {
                    // no-op
                }
                throw new Error(detail || error.message || '导出失败');
            }
            throw new Error(error instanceof Error ? error.message : '导出失败');
        }
    }, [id, previewDeviceMode]);

    const handleExportJson = async () => {
        let preparedRequestId: string | undefined;
        try {
            const prepared = await ensureExportAllowed('json');
            preparedRequestId = prepared?.requestId || undefined;
        } catch (error) {
            alert(error instanceof Error ? error.message : '导出失败');
            if (id) {
                void analyticsApi.reportScreenExport(id, {
                    status: 'failed',
                    format: 'json',
                    mode: 'draft',
                    ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
                    requestId: preparedRequestId,
                    message: error instanceof Error ? error.message : 'prepare_failed',
                });
            }
            return;
        }
        try {
            const payload = {
                schema: 'dts.screen.spec',
                exportedAt: new Date().toISOString(),
                screenSpec: buildScreenPayload(config),
            };
            const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `${config.name || 'screen'}-spec.json`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);
            if (id) {
                void analyticsApi.reportScreenExport(id, {
                    status: 'success',
                    format: 'json',
                    mode: 'draft',
                    ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
                    requestId: preparedRequestId,
                });
            }
        } catch (error) {
            if (id) {
                void analyticsApi.reportScreenExport(id, {
                    status: 'failed',
                    format: 'json',
                    mode: 'draft',
                    ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
                    requestId: preparedRequestId,
                    message: error instanceof Error ? error.message : 'export_failed',
                });
            }
            alert(error instanceof Error ? error.message : 'JSON 导出失败');
        }
    };

    const openExportWindow = useCallback((format: 'png' | 'pdf') => {
        if (!id) {
            throw new Error('请先保存大屏后再导出');
        }
        const browserRatio = Number.isFinite(window.devicePixelRatio)
            ? Math.max(1, Math.min(window.devicePixelRatio, 3))
            : 1;
        const baseRatio = format === 'pdf'
            ? Math.max(1.5, Math.min(browserRatio, 2))
            : Math.max(2, Math.min(browserRatio, 3));
        const pixelRatio = previewDeviceMode === 'mobile'
            ? Math.min(3, baseRatio + 0.5)
            : (previewDeviceMode === 'tablet' ? Math.min(3, baseRatio + 0.25) : baseRatio);
        const params = new URLSearchParams();
        params.set('format', format);
        params.set('mode', 'draft');
        params.set('pixelRatio', String(Number(pixelRatio.toFixed(2))));
        if (previewDeviceMode !== 'auto') {
            params.set('device', previewDeviceMode);
        }
        const url = `/analytics/screens/${id}/export?${params.toString()}`;
        const popup = window.open(url, '_blank', 'noopener,noreferrer');
        if (!popup) {
            throw new Error('请允许弹窗后重试导出');
        }
    }, [id, previewDeviceMode]);

    const downloadBlob = useCallback((blob: Blob, fileName: string) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }, []);

    const resolveServerRenderPixelRatio = useCallback((format: 'png' | 'pdf') => {
        const browserRatio = typeof window !== 'undefined' && Number.isFinite(window.devicePixelRatio)
            ? Math.max(1, Math.min(window.devicePixelRatio, 3))
            : 1;
        const baseRatio = format === 'pdf'
            ? Math.max(1.5, Math.min(browserRatio, 2))
            : Math.max(2, Math.min(browserRatio, 3));
        let tunedRatio = baseRatio;
        if (previewDeviceMode === 'mobile') {
            tunedRatio = Math.min(3, baseRatio + 0.5);
        } else if (previewDeviceMode === 'tablet') {
            tunedRatio = Math.min(3, baseRatio + 0.25);
        }
        return Number(tunedRatio.toFixed(2));
    }, [previewDeviceMode]);

    const renderExportByServer = useCallback(async (format: 'png' | 'pdf') => {
        if (!id) {
            throw new Error('请先保存大屏后再导出');
        }
        const pixelRatio = resolveServerRenderPixelRatio(format);
        const rendered = await analyticsApi.renderScreenExport(id, {
            format,
            mode: 'draft',
            ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
            pixelRatio,
            screenSpec: buildScreenPayload(config),
        });
        const fallbackName = `${config.name || 'screen'}.${format}`;
        downloadBlob(rendered.blob, rendered.fileName || fallbackName);
        return rendered;
    }, [config, downloadBlob, id, previewDeviceMode, resolveServerRenderPixelRatio]);

    const handleExportPng = async () => {
        let preparedRequestId: string | undefined;
        let preparedSpecDigest: string | undefined;
        try {
            const prepared = await ensureExportAllowed('png');
            preparedRequestId = prepared?.requestId || undefined;
            preparedSpecDigest = prepared?.specDigest || undefined;
        } catch (error) {
            if (id) {
                void analyticsApi.reportScreenExport(id, {
                    status: 'failed',
                    format: 'png',
                    mode: 'draft',
                    resolvedMode: 'draft',
                    ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
                    requestId: preparedRequestId,
                    specDigest: preparedSpecDigest,
                    message: error instanceof Error ? error.message : 'prepare_failed',
                });
            }
            alert(error instanceof Error ? error.message : 'PNG 导出失败');
            return;
        }
        try {
            const rendered = await renderExportByServer('png');
            if (id) {
                void analyticsApi.reportScreenExport(id, {
                    status: 'success',
                    format: 'png',
                    mode: 'draft',
                    resolvedMode: rendered.resolvedMode || 'draft',
                    ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
                    requestId: rendered.requestId || preparedRequestId,
                    specDigest: rendered.specDigest || preparedSpecDigest,
                });
            }
        } catch (error) {
            console.warn('Failed to export png by server render, fallback to export page:', error);
            try {
                openExportWindow('png');
                if (id) {
                    void analyticsApi.reportScreenExport(id, {
                        status: 'fallback',
                        format: 'png',
                        mode: 'draft',
                        resolvedMode: 'draft',
                        ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
                        requestId: preparedRequestId,
                        specDigest: preparedSpecDigest,
                        message: error instanceof Error ? error.message : 'server_render_failed',
                    });
                }
            } catch (fallbackError) {
                console.error('Failed to export png:', fallbackError);
                if (id) {
                    void analyticsApi.reportScreenExport(id, {
                        status: 'failed',
                        format: 'png',
                        mode: 'draft',
                        resolvedMode: 'draft',
                        ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
                        requestId: preparedRequestId,
                        specDigest: preparedSpecDigest,
                        message: fallbackError instanceof Error ? fallbackError.message : 'export_failed',
                    });
                }
                alert(fallbackError instanceof Error ? fallbackError.message : 'PNG 导出失败');
            }
        }
    };

    const handleExportPdf = async () => {
        let preparedRequestId: string | undefined;
        let preparedSpecDigest: string | undefined;
        try {
            const prepared = await ensureExportAllowed('pdf');
            preparedRequestId = prepared?.requestId || undefined;
            preparedSpecDigest = prepared?.specDigest || undefined;
        } catch (error) {
            if (id) {
                void analyticsApi.reportScreenExport(id, {
                    status: 'failed',
                    format: 'pdf',
                    mode: 'draft',
                    resolvedMode: 'draft',
                    ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
                    requestId: preparedRequestId,
                    specDigest: preparedSpecDigest,
                    message: error instanceof Error ? error.message : 'prepare_failed',
                });
            }
            alert(error instanceof Error ? error.message : 'PDF 导出失败');
            return;
        }
        try {
            const rendered = await renderExportByServer('pdf');
            if (id) {
                void analyticsApi.reportScreenExport(id, {
                    status: 'success',
                    format: 'pdf',
                    mode: 'draft',
                    resolvedMode: rendered.resolvedMode || 'draft',
                    ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
                    requestId: rendered.requestId || preparedRequestId,
                    specDigest: rendered.specDigest || preparedSpecDigest,
                });
            }
        } catch (error) {
            console.warn('Failed to export pdf by server render, fallback to export page:', error);
            try {
                openExportWindow('pdf');
                if (id) {
                    void analyticsApi.reportScreenExport(id, {
                        status: 'fallback',
                        format: 'pdf',
                        mode: 'draft',
                        resolvedMode: 'draft',
                        ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
                        requestId: preparedRequestId,
                        specDigest: preparedSpecDigest,
                        message: error instanceof Error ? error.message : 'server_render_failed',
                    });
                }
            } catch (fallbackError) {
                console.error('Failed to export pdf:', fallbackError);
                if (id) {
                    void analyticsApi.reportScreenExport(id, {
                        status: 'failed',
                        format: 'pdf',
                        mode: 'draft',
                        resolvedMode: 'draft',
                        ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
                        requestId: preparedRequestId,
                        specDigest: preparedSpecDigest,
                        message: fallbackError instanceof Error ? fallbackError.message : 'export_failed',
                    });
                }
                alert(fallbackError instanceof Error ? fallbackError.message : 'PDF 导出失败');
            }
        }
    };

    const handleOpenImport = () => {
        importInputRef.current?.click();
    };

    const handleImportJson = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        event.target.value = '';
        if (!file) {
            return;
        }
        try {
            const content = await file.text();
            const parsed = JSON.parse(content) as Record<string, unknown>;
            const source = (parsed.screenSpec || parsed) as Record<string, unknown>;
            const normalized = normalizeScreenConfig(source, { id: id || '' });
            if (normalized.warnings.length > 0) {
                console.warn('[screen-import] normalized warnings:', normalized.warnings);
            }
            const validation = validateScreenPayload(buildScreenPayload(normalized.config));
            if (validation.errors.length > 0) {
                alert(`JSON 导入失败，配置不合法：${validation.errors.join('；')}`);
                return;
            }
            if (validation.warnings.length > 0) {
                console.warn('[screen-import] validate warnings:', validation.warnings);
            }
            loadConfig(normalized.config);
            alert('JSON 导入完成');
        } catch (error) {
            console.error('Failed to import screen json:', error);
            alert('JSON 导入失败，请检查文件格式');
        }
    };

    const handleShare = async () => {
        if (!id || isSharing) return;
        setIsSharing(true);
        try {
            const { uuid } = await analyticsApi.createScreenPublicLink(id, {});
            if (!uuid) {
                alert('未获取到分享链接，请先发布后重试');
                return;
            }
            const baseUrl = `${window.location.origin}/analytics/public/screen/${uuid}`;
            const embedUrl = `${baseUrl}?embed=1&hideControls=1`;
            const iframeCode = `<iframe src="${embedUrl}" width="100%" height="600" frameborder="0" allowfullscreen style="border: none;"></iframe>`;
            const globalVars = config.globalVariables ?? [];
            const paramHint = globalVars.length > 0
                ? `\n\n可透传参数：\n${globalVars.map(v => `  ?var_${v.key}=值`).join('\n')}`
                : '';
            const shareInfo = `链接分享：\n${baseUrl}\n\n嵌入代码（iframe）：\n${iframeCode}${paramHint}`;
            const copied = await writeTextToClipboard(baseUrl);
            alert(copied ? `分享链接已复制到剪贴板\n\n${shareInfo}` : shareInfo);
        } catch (err) {
            console.error('Failed to create public link:', err);
            alert('创建分享链接失败，请先发布版本');
        } finally {
            setIsSharing(false);
        }
    };

    const handleCreateExploreSession = useCallback(async () => {
        if (!permissions.canRead) {
            alert('当前无读权限，无法沉淀分析会话');
            return;
        }
        const defaultTitle = `${config.name || '未命名大屏'} 分析会话`;
        const titleInput = window.prompt('会话标题', defaultTitle);
        if (titleInput === null) {
            return;
        }
        const questionInput = window.prompt('问题描述（可选）', `围绕大屏「${config.name || '未命名大屏'}」展开分析`) ?? '';
        const conclusionInput = window.prompt('阶段结论（可选）', '') ?? '';
        const tagsInput = window.prompt('标签（逗号分隔，可选）', '大屏,复盘') ?? '';
        const tags = tagsInput
            .split(',')
            .map((item) => item.trim())
            .filter((item) => item.length > 0)
            .slice(0, 20);
        const created = await analyticsApi.createExploreSession({
            title: titleInput.trim() || defaultTitle,
            question: questionInput.trim() || null,
            conclusion: conclusionInput.trim() || null,
            tags,
            steps: buildExploreSessionSteps(config),
        });
        const createdId = created?.id != null ? `#${created.id}` : '';
        if (createdId && window.confirm(`已创建分析会话 ${createdId}，是否打开会话中心？`)) {
            navigate('/explore-sessions');
            return;
        }
        alert(`已创建分析会话 ${createdId}`.trim());
    }, [config, navigate, permissions.canRead]);

    const handleBack = () => {
        navigate('/screens');
    };

    const handleCopyUrl = useCallback(async (url: string) => {
        const copied = await writeTextToClipboard(url);
        alert(copied ? '链接已复制到剪贴板' : `复制失败，请手工复制：\n${url}`);
    }, []);

    const executeMenuAction = useCallback((action: () => void | Promise<void>) => {
        setActiveMenu(null);
        window.setTimeout(() => {
            void Promise.resolve(action()).catch((error) => {
                console.error('Failed to execute header menu action:', error);
            });
        }, 0);
    }, []);

    const executeVersionAction = useCallback(() => {
        if (versionAction === 'history') {
            if (!permissions.canPublish || isLoadingVersions) {
                return;
            }
            void executeMenuAction(handleVersionHistory);
            return;
        }
        if (!permissions.canRead || isLoadingVersions) {
            return;
        }
        void executeMenuAction(handleVersionCompare);
    }, [
        executeMenuAction,
        handleVersionCompare,
        handleVersionHistory,
        isLoadingVersions,
        permissions.canPublish,
        permissions.canRead,
        versionAction,
    ]);

    const executeExportAction = useCallback(() => {
        if (exportAction === 'json') {
            void executeMenuAction(handleExportJson);
            return;
        }
        if (exportAction === 'pdf') {
            void executeMenuAction(handleExportPdf);
            return;
        }
        void executeMenuAction(handleExportPng);
    }, [executeMenuAction, exportAction, handleExportJson, handleExportPdf, handleExportPng]);

    const canExecuteDesignAction = useMemo(() => {
        if (designAction === 'session') return permissions.canRead;
        if (designAction === 'collaboration') return !!id && permissions.canRead;
        if (designAction === 'template') return permissions.canEdit && !isSavingTemplate;
        return true;
    }, [designAction, id, isSavingTemplate, permissions.canEdit, permissions.canRead]);

    const executeDesignAction = useCallback(() => {
        if (designAction === 'session') {
            if (!permissions.canRead) return;
            void executeMenuAction(handleCreateExploreSession);
            return;
        }
        if (designAction === 'variables') {
            void executeMenuAction(() => setShowVariableManager(true));
            return;
        }
        if (designAction === 'interaction') {
            void executeMenuAction(() => setShowInteractionDebugPanel(true));
            return;
        }
        if (designAction === 'collaboration') {
            if (!id || !permissions.canRead) return;
            void executeMenuAction(() => setShowCollaborationPanel(true));
            return;
        }
        if (designAction === 'template') {
            if (!permissions.canEdit || isSavingTemplate) return;
            void executeMenuAction(handleSaveAsTemplate);
            return;
        }
        if (designAction === 'import') {
            void executeMenuAction(handleOpenImport);
            return;
        }
        void executeMenuAction(() => {
            setQuickKeyword('');
            setShowQuickActions(true);
        });
    }, [
        designAction,
        executeMenuAction,
        handleCreateExploreSession,
        handleOpenImport,
        handleSaveAsTemplate,
        id,
        isSavingTemplate,
        permissions.canEdit,
        permissions.canRead,
    ]);

    const canExecuteGovernanceAction = useMemo(() => {
        if (governanceAction === 'edit-lock') return !!id && permissions.canRead;
        if (governanceAction === 'acl' || governanceAction === 'audit') return !!id && permissions.canManage;
        if (governanceAction === 'share-policy' || governanceAction === 'share-link') return !!id && permissions.canPublish;
        return true;
    }, [governanceAction, id, permissions.canManage, permissions.canPublish, permissions.canRead]);

    const executeGovernanceAction = useCallback(() => {
        if (governanceAction === 'edit-lock') {
            if (!id || !permissions.canRead) return;
            void executeMenuAction(() => setShowEditLockPanel(true));
            return;
        }
        if (governanceAction === 'cache') {
            void executeMenuAction(() => setShowCachePanel(true));
            return;
        }
        if (governanceAction === 'compliance') {
            void executeMenuAction(() => setShowCompliancePanel(true));
            return;
        }
        if (governanceAction === 'health') {
            void executeMenuAction(() => setShowHealthPanel(true));
            return;
        }
        if (governanceAction === 'acl') {
            if (!id || !permissions.canManage) return;
            void executeMenuAction(() => setShowAclPanel(true));
            return;
        }
        if (governanceAction === 'audit') {
            if (!id || !permissions.canManage) return;
            void executeMenuAction(() => setShowAuditPanel(true));
            return;
        }
        if (governanceAction === 'share-policy') {
            if (!id || !permissions.canPublish) return;
            void executeMenuAction(() => setShowSharePolicyPanel(true));
            return;
        }
        if (!id || !permissions.canPublish || isSharing) return;
        void executeMenuAction(handleShare);
    }, [
        executeMenuAction,
        governanceAction,
        handleShare,
        id,
        isSharing,
        permissions.canManage,
        permissions.canPublish,
        permissions.canRead,
    ]);

    const quickActions: QuickActionItem[] = useMemo(() => {
        return [
            {
                id: 'save',
                label: isSaving ? '保存中...' : '保存草稿',
                keywords: 'save 保存 草稿',
                disabled: isSaving || !permissions.canEdit || lockedByOther,
                hotkey: 'Ctrl/Cmd + S',
                run: handleSave,
            },
            {
                id: 'preview',
                label: '预览大屏',
                keywords: 'preview 预览',
                disabled: !id,
                hotkey: 'Ctrl/Cmd + Shift + P',
                run: handlePreview,
            },
            {
                id: 'publish',
                label: isPublishing ? '发布中...' : '发布版本',
                keywords: 'publish 发布 版本',
                disabled: isPublishing || !permissions.canPublish || lockedByOther || !id,
                run: handlePublish,
            },
            {
                id: 'save-template',
                label: isSavingTemplate ? '模板保存中...' : '保存为模板',
                keywords: '模板 template 保存',
                disabled: isSavingTemplate || !permissions.canEdit || lockedByOther,
                run: handleSaveAsTemplate,
            },
            {
                id: 'version-history',
                label: isLoadingVersions ? '版本处理中...' : '版本历史/回滚',
                keywords: '版本 回滚 history rollback',
                disabled: isLoadingVersions || !permissions.canPublish || !id,
                run: handleVersionHistory,
            },
            {
                id: 'version-compare',
                label: isLoadingVersions ? '版本处理中...' : '版本对比',
                keywords: '版本 对比 compare diff',
                disabled: isLoadingVersions || !permissions.canRead || !id,
                run: handleVersionCompare,
            },
            {
                id: 'snapshot',
                label: '快照与报告',
                keywords: '快照 截图 定时 报告 snapshot report',
                disabled: !id,
                run: () => setShowSnapshotPanel(true),
            },
            {
                id: 'variables',
                label: '变量管理',
                keywords: '变量 variable',
                disabled: false,
                run: () => setShowVariableManager(true),
            },
            {
                id: 'cache',
                label: '缓存观测',
                keywords: '缓存 cache',
                disabled: false,
                run: () => setShowCachePanel(true),
            },
            {
                id: 'compliance',
                label: '合规检查',
                keywords: '合规 compliance',
                disabled: false,
                run: () => setShowCompliancePanel(true),
            },
            {
                id: 'health',
                label: '体检报告',
                keywords: '体检 健康 health',
                disabled: false,
                run: () => setShowHealthPanel(true),
            },
            {
                id: 'governance-lock',
                label: '编辑锁状态',
                keywords: '编辑锁 lock',
                disabled: !id || !permissions.canRead,
                run: () => setShowEditLockPanel(true),
            },
            {
                id: 'governance-acl',
                label: '权限矩阵',
                keywords: '权限 acl',
                disabled: !id || !permissions.canManage,
                run: () => setShowAclPanel(true),
            },
            {
                id: 'governance-audit',
                label: '审计记录',
                keywords: '审计 audit',
                disabled: !id || !permissions.canManage,
                run: () => setShowAuditPanel(true),
            },
            {
                id: 'share',
                label: isSharing ? '分享中...' : '分享链接',
                keywords: '分享 share 链接',
                disabled: isSharing || !permissions.canPublish || !id,
                run: handleShare,
            },
            {
                id: 'export-png',
                label: '导出 PNG',
                keywords: '导出 export png',
                disabled: false,
                run: handleExportPng,
            },
            {
                id: 'export-pdf',
                label: '导出 PDF',
                keywords: '导出 export pdf',
                disabled: false,
                run: handleExportPdf,
            },
            {
                id: 'export-json',
                label: '导出 JSON',
                keywords: '导出 export json',
                disabled: false,
                run: handleExportJson,
            },
            {
                id: 'command-help',
                label: '命令面板帮助',
                keywords: '命令 面板 help 快捷键',
                disabled: false,
                hotkey: 'Ctrl/Cmd + K',
                run: () => alert('可输入关键词，使用 ↑/↓ 选择，Enter 执行。'),
            },
        ];
    }, [
        handleExportJson,
        handleExportPdf,
        handleExportPng,
        handlePreview,
        handlePublish,
        handleSave,
        handleSaveAsTemplate,
        handleShare,
        handleVersionCompare,
        handleVersionHistory,
        id,
        isPublishing,
        isSaving,
        isSavingTemplate,
        isSharing,
        isLoadingVersions,
        lockedByOther,
        permissions.canEdit,
        permissions.canManage,
        permissions.canPublish,
        permissions.canRead,
    ]);

    const quickRecentOrder = useMemo(() => {
        const mapping = new Map<string, number>();
        quickRecentIds.forEach((id, index) => {
            mapping.set(id, index);
        });
        return mapping;
    }, [quickRecentIds]);

    const filteredQuickActions = useMemo(() => {
        const keyword = quickKeyword.trim().toLowerCase();
        const matched = keyword
            ? quickActions.filter((item) => {
                const label = item.label.toLowerCase();
                const extra = String(item.keywords || '').toLowerCase();
                const hotkey = String(item.hotkey || '').toLowerCase();
                return label.includes(keyword) || extra.includes(keyword) || hotkey.includes(keyword);
            })
            : quickActions.slice();
        return matched.sort((a, b) => {
            const aRecent = quickRecentOrder.has(a.id) ? quickRecentOrder.get(a.id)! : Number.MAX_SAFE_INTEGER;
            const bRecent = quickRecentOrder.has(b.id) ? quickRecentOrder.get(b.id)! : Number.MAX_SAFE_INTEGER;
            if (aRecent !== bRecent) {
                return aRecent - bRecent;
            }
            const label = a.label.toLowerCase();
            const labelB = b.label.toLowerCase();
            return label.localeCompare(labelB, 'zh-CN');
        });
    }, [quickActions, quickKeyword, quickRecentOrder]);

    const runQuickAction = useCallback((actionId: string, action: () => void | Promise<void>) => {
        setShowQuickActions(false);
        setQuickKeyword('');
        setQuickActiveIndex(-1);
        setActiveMenu(null);
        setQuickRecentIds((prev) => [actionId, ...prev.filter((item) => item !== actionId)].slice(0, 8));
        window.setTimeout(() => {
            void Promise.resolve(action()).catch((error) => {
                console.error('Failed to run quick action:', error);
            });
        }, 0);
    }, []);

    useEffect(() => {
        if (!showQuickActions) {
            return;
        }
        setQuickActiveIndex((prev) => {
            const firstEnabled = filteredQuickActions.findIndex((item) => !item.disabled);
            if (firstEnabled < 0) {
                return -1;
            }
            if (
                prev >= 0
                && prev < filteredQuickActions.length
                && !filteredQuickActions[prev]?.disabled
            ) {
                return prev;
            }
            return firstEnabled;
        });
    }, [filteredQuickActions, showQuickActions]);

    useEffect(() => {
        if (!showQuickActions || quickActiveIndex < 0) {
            return;
        }
        quickActionRefs.current[quickActiveIndex]?.scrollIntoView({ block: 'nearest' });
    }, [quickActiveIndex, showQuickActions]);

    useEffect(() => {
        if (!showQuickActions) {
            return;
        }
        const timer = window.setTimeout(() => {
            quickInputRef.current?.focus();
            quickInputRef.current?.select();
        }, 0);
        return () => window.clearTimeout(timer);
    }, [showQuickActions]);

    useEffect(() => {
        const isTypingTarget = (target: EventTarget | null): boolean => {
            const node = target as HTMLElement | null;
            if (!node) return false;
            const tag = node.tagName;
            if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
            return node.isContentEditable;
        };
        const handleKeyDown = (event: KeyboardEvent) => {
            const hotkey = event.ctrlKey || event.metaKey;
            if (hotkey && event.key.toLowerCase() === 'k') {
                if (isTypingTarget(event.target)) {
                    return;
                }
                event.preventDefault();
                setShowQuickActions((prev) => {
                    const next = !prev;
                    if (next) {
                        setQuickKeyword('');
                        setQuickActiveIndex(-1);
                    }
                    return next;
                });
                return;
            }
            if (event.key === 'Escape' && showQuickActions) {
                event.preventDefault();
                setShowQuickActions(false);
                setQuickActiveIndex(-1);
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [showQuickActions]);

    return (
        <>
            <div className="screen-header">
                <div className="screen-header-left">
                    <button type="button" className="header-btn back-btn" onClick={handleBack} title="返回列表">
                        ← 返回
                    </button>
                    <div className="screen-name-container">
                        {isEditingName ? (
                            <input
                                type="text"
                                className="screen-name-input"
                                value={nameValue}
                                onChange={(e) => setNameValue(e.target.value)}
                                onBlur={handleNameBlur}
                                onKeyDown={handleNameKeyDown}
                                autoFocus
                            />
                        ) : (
                            <span className="screen-name" onClick={handleNameClick} title="点击编辑名称">
                                {config.name}
                            </span>
                        )}
                    </div>
                </div>

                <div className="screen-header-right">
                    <div className="header-menu-group" ref={menuContainerRef}>
                        <div className="header-mobile-primary-menu">
                            <HeaderMenu
                                label="操作"
                                open={activeMenu === 'primary'}
                                onToggle={() => setActiveMenu((prev) => (prev === 'primary' ? null : 'primary'))}
                            >
                                <div className="header-menu-section">
                                    <div className="header-menu-section-title">快捷操作</div>
                                    <button
                                        type="button"
                                        className="header-btn"
                                        onClick={() => executeMenuAction(handlePreview)}
                                        title={`预览大屏（${previewDeviceMode === 'auto' ? '自动' : previewDeviceMode}）`}
                                    >
                                        预览
                                    </button>
                                    {id && (
                                        <button
                                            type="button"
                                            className="header-btn"
                                            onClick={() => executeMenuAction(handlePublish)}
                                            disabled={isPublishing || !permissions.canPublish || lockedByOther}
                                            title={lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '发布当前草稿'}
                                        >
                                            {isPublishing ? '发布中...' : '发布'}
                                        </button>
                                    )}
                                    <button
                                        type="button"
                                        className="header-btn"
                                        onClick={() => executeMenuAction(handleSave)}
                                        disabled={isSaving || !permissions.canEdit || lockedByOther}
                                        title={lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '保存草稿'}
                                    >
                                        {isSaving ? '保存中...' : '保存'}
                                    </button>
                                </div>
                            </HeaderMenu>
                        </div>
                        <HeaderMenu
                            label={`工具${cycleWarnings.length > 0 ? `(${cycleWarnings.length})` : ''}`}
                            open={activeMenu === 'tools'}
                            onToggle={() => setActiveMenu((prev) => (prev === 'tools' ? null : 'tools'))}
                        >
                            <div className="header-menu-tabs" role="tablist" aria-label="工具菜单分区">
                                <button
                                    type="button"
                                    className={`header-menu-tab ${toolsSection === 'design' ? 'is-active' : ''}`}
                                    onClick={() => setToolsSection('design')}
                                >
                                    设计
                                </button>
                                <button
                                    type="button"
                                    className={`header-menu-tab ${toolsSection === 'release' ? 'is-active' : ''}`}
                                    onClick={() => setToolsSection('release')}
                                >
                                    版本导出
                                </button>
                                <button
                                    type="button"
                                    className={`header-menu-tab ${toolsSection === 'governance' ? 'is-active' : ''}`}
                                    onClick={() => setToolsSection('governance')}
                                >
                                    治理
                                </button>
                            </div>
                            {toolsSection === 'design' ? (
                                <div className="header-menu-section">
                                    <div className="header-menu-section-title">设计</div>
                                    <label className="header-menu-inline-label" htmlFor="screen-design-action">设计动作</label>
                                    <select
                                        id="screen-design-action"
                                        className="header-device-select"
                                        value={designAction}
                                        onChange={(e) => {
                                            const next = e.target.value;
                                            if (
                                                next === 'session'
                                                || next === 'variables'
                                                || next === 'interaction'
                                                || next === 'collaboration'
                                                || next === 'template'
                                                || next === 'import'
                                                || next === 'command'
                                            ) {
                                                setDesignAction(next);
                                                return;
                                            }
                                            setDesignAction('variables');
                                        }}
                                        title="选择设计动作"
                                    >
                                        <option value="variables">变量管理</option>
                                        <option value="interaction">联动调试</option>
                                        <option value="session">沉淀会话</option>
                                        <option value="collaboration">协作批注</option>
                                        <option value="template">保存模板</option>
                                        <option value="import">导入JSON</option>
                                        <option value="command">命令面板</option>
                                    </select>
                                    <button
                                        type="button"
                                        className="header-btn"
                                        onClick={executeDesignAction}
                                        disabled={!canExecuteDesignAction}
                                        title="执行设计动作"
                                    >
                                        执行设计动作
                                    </button>
                                </div>
                            ) : null}
                            {toolsSection === 'release' ? (
                                <div className="header-menu-section">
                                    <div className="header-menu-section-title">版本与导出</div>
                                    <label className="header-menu-inline-label" htmlFor="screen-preview-device-mode">预览设备</label>
                                    <select
                                        id="screen-preview-device-mode"
                                        className="header-device-select"
                                        value={previewDeviceMode}
                                        onChange={(e) => {
                                            const next = e.target.value;
                                            if (next === 'pc' || next === 'tablet' || next === 'mobile') {
                                                setPreviewDeviceMode(next);
                                                return;
                                            }
                                            setPreviewDeviceMode('auto');
                                        }}
                                        title="预览设备模式"
                                    >
                                        <option value="auto">自动</option>
                                        <option value="pc">PC</option>
                                        <option value="tablet">平板</option>
                                        <option value="mobile">手机</option>
                                    </select>
                                    {id ? (
                                        <>
                                            <label className="header-menu-inline-label" htmlFor="screen-version-action">版本动作</label>
                                            <select
                                                id="screen-version-action"
                                                className="header-device-select"
                                                value={versionAction}
                                                onChange={(e) => {
                                                    const next = e.target.value;
                                                    setVersionAction(next === 'compare' ? 'compare' : 'history');
                                                }}
                                                title="选择版本动作"
                                            >
                                                <option value="history">版本历史/回滚</option>
                                                <option value="compare">版本对比</option>
                                            </select>
                                            <button
                                                type="button"
                                                className="header-btn"
                                                onClick={executeVersionAction}
                                                disabled={
                                                    isLoadingVersions
                                                    || (versionAction === 'history' ? !permissions.canPublish : !permissions.canRead)
                                                }
                                                title={versionAction === 'history' ? '查看版本历史并回滚' : '查看版本差异摘要'}
                                            >
                                                {isLoadingVersions ? '加载中...' : '执行版本动作'}
                                            </button>
                                        </>
                                    ) : null}
                                    <label className="header-menu-inline-label" htmlFor="screen-export-action">导出动作</label>
                                    <select
                                        id="screen-export-action"
                                        className="header-device-select"
                                        value={exportAction}
                                        onChange={(e) => {
                                            const next = e.target.value;
                                            if (next === 'json' || next === 'pdf' || next === 'png') {
                                                setExportAction(next);
                                                return;
                                            }
                                            setExportAction('png');
                                        }}
                                        title="选择导出格式"
                                    >
                                        <option value="png">导出PNG</option>
                                        <option value="pdf">导出PDF</option>
                                        <option value="json">导出JSON</option>
                                    </select>
                                    <button
                                        type="button"
                                        className="header-btn"
                                        onClick={executeExportAction}
                                        title="执行导出"
                                    >
                                        执行导出
                                    </button>
                                </div>
                            ) : null}
                            {toolsSection === 'governance' ? (
                                <div className="header-menu-section">
                                    <div className="header-menu-section-title">治理与安全</div>
                                    <label className="header-menu-inline-label" htmlFor="screen-governance-action">治理动作</label>
                                    <select
                                        id="screen-governance-action"
                                        className="header-device-select"
                                        value={governanceAction}
                                        onChange={(e) => {
                                            const next = e.target.value;
                                            if (
                                                next === 'edit-lock'
                                                || next === 'cache'
                                                || next === 'compliance'
                                                || next === 'health'
                                                || next === 'acl'
                                                || next === 'audit'
                                                || next === 'share-policy'
                                                || next === 'share-link'
                                            ) {
                                                setGovernanceAction(next);
                                                return;
                                            }
                                            setGovernanceAction('cache');
                                        }}
                                        title="选择治理动作"
                                    >
                                        <option value="edit-lock">编辑锁{lockedByOther ? '(占用)' : (editLock?.mine ? '(我)' : '')}</option>
                                        <option value="cache">缓存观测</option>
                                        <option value="compliance">合规</option>
                                        <option value="health">体检</option>
                                        <option value="acl">权限</option>
                                        <option value="audit">审计</option>
                                        <option value="share-policy">分享策略</option>
                                        <option value="share-link">分享链接</option>
                                    </select>
                                    <button
                                        type="button"
                                        className="header-btn"
                                        onClick={executeGovernanceAction}
                                        disabled={!canExecuteGovernanceAction || (governanceAction === 'share-link' && isSharing)}
                                        title="执行治理动作"
                                    >
                                        {governanceAction === 'share-link' && isSharing ? '分享中...' : '执行治理动作'}
                                    </button>
                                </div>
                            ) : null}
                        </HeaderMenu>
                    </div>
                    <div className="screen-header-primary-actions">
                        <select
                            className="header-device-select header-primary-desktop"
                            value={primaryAction}
                            onChange={(event) => {
                                const next = event.target.value;
                                if (next === 'preview' || next === 'publish' || next === 'save') {
                                    setPrimaryAction(next);
                                    return;
                                }
                                setPrimaryAction('save');
                            }}
                            title="主操作"
                        >
                            <option value="save">保存草稿</option>
                            <option value="preview">预览大屏</option>
                            {id ? <option value="publish">发布版本</option> : null}
                        </select>
                        <button
                            type="button"
                            className="header-btn save-btn header-primary-desktop"
                            onClick={executePrimaryAction}
                            disabled={!canExecutePrimaryAction}
                            title={
                                primaryAction === 'publish'
                                    ? (lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '发布当前草稿')
                                    : (primaryAction === 'save'
                                        ? (lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '保存草稿')
                                        : `预览大屏（${previewDeviceMode === 'auto' ? '自动' : previewDeviceMode}）`)
                            }
                        >
                            {primaryAction === 'publish'
                                ? (isPublishing ? '发布中...' : '执行发布')
                                : (primaryAction === 'save'
                                    ? (isSaving ? '保存中...' : '执行保存')
                                    : '执行预览')}
                        </button>
                    </div>
                    <input
                        ref={importInputRef}
                        type="file"
                        accept="application/json,.json"
                        style={{ display: 'none' }}
                        onChange={handleImportJson}
                    />
                </div>
            </div>
            {lockedByOther && (
                <div style={{
                    padding: '6px 12px',
                    fontSize: 12,
                    color: '#f59e0b',
                    borderTop: '1px solid rgba(245,158,11,0.3)',
                    background: 'rgba(245,158,11,0.08)',
                }}>
                    编辑锁提示：当前由 {lockOwnerText} 编辑中，保存/发布已被保护性禁用。
                    {lockErrorText ? ` (${lockErrorText})` : ''}
                </div>
            )}
            {publishNotice && (
                <div className="screen-publish-notice">
                    <div className="screen-publish-notice-main">
                        <div className="screen-publish-notice-title">
                            已发布 v{publishNotice.versionNo}（大屏 #{publishNotice.screenId}）
                        </div>
                        <div className="screen-publish-notice-link-row">
                            <span className="screen-publish-notice-label">预览链接</span>
                            <a href={publishNotice.previewUrl} target="_blank" rel="noreferrer">{publishNotice.previewUrl}</a>
                            <button
                                type="button"
                                className="header-btn"
                                onClick={() => void handleCopyUrl(publishNotice.previewUrl)}
                            >
                                复制
                            </button>
                        </div>
                        <div className="screen-publish-notice-link-row">
                            <span className="screen-publish-notice-label">公开链接</span>
                            {publishNotice.publicUrl ? (
                                <>
                                    <a href={publishNotice.publicUrl} target="_blank" rel="noreferrer">{publishNotice.publicUrl}</a>
                                    <button
                                        type="button"
                                        className="header-btn"
                                        onClick={() => void handleCopyUrl(publishNotice.publicUrl!)}
                                    >
                                        复制
                                    </button>
                                </>
                            ) : (
                                <span className="screen-publish-notice-muted">未生成（可在“更多/治理/分享链接”中重试）</span>
                            )}
                        </div>
                        {publishNotice.warmupText ? (
                            <div className="screen-publish-notice-muted">{publishNotice.warmupText.trim()}</div>
                        ) : null}
                    </div>
                    <div className="screen-publish-notice-actions">
                        <button
                            type="button"
                            className="header-btn"
                            onClick={() => navigate('/')}
                            title="返回 Analytics 首页"
                        >
                            Analytics首页
                        </button>
                        <button
                            type="button"
                            className="header-btn"
                            onClick={() => navigate('/screens')}
                            title="进入大屏管理列表"
                        >
                            大屏中心
                        </button>
                        <button
                            type="button"
                            className="header-btn"
                            onClick={() => setPublishNotice(null)}
                            title="收起发布信息"
                        >
                            收起
                        </button>
                    </div>
                </div>
            )}

            {showQuickActions ? (
                <div
                    style={{
                        position: 'fixed',
                        inset: 0,
                        zIndex: 21000,
                        background: 'rgba(10,18,32,0.55)',
                        display: 'flex',
                        alignItems: 'flex-start',
                        justifyContent: 'center',
                        paddingTop: 'min(12vh, 92px)',
                        paddingLeft: 12,
                        paddingRight: 12,
                    }}
                    onClick={() => {
                        setShowQuickActions(false);
                        setQuickActiveIndex(-1);
                    }}
                >
                    <div
                        style={{
                            width: 'min(680px, 96vw)',
                            maxHeight: '70vh',
                            overflow: 'hidden',
                            background: '#ffffff',
                            border: '1px solid var(--color-border)',
                            borderRadius: 10,
                            boxShadow: '0 20px 70px rgba(2,6,23,0.35)',
                            display: 'grid',
                            gridTemplateRows: 'auto auto 1fr',
                        }}
                        onClick={(event) => event.stopPropagation()}
                    >
                        <div style={{ padding: '10px 12px 0', fontSize: 12, color: '#64748b' }}>
                            命令面板（Ctrl/Cmd + K，↑/↓选择，Enter执行，Ctrl/Cmd + Shift + P 预览）
                        </div>
                        <div style={{ padding: '8px 12px 10px' }}>
                            <input
                                ref={quickInputRef}
                                type="text"
                                className="screen-name-input"
                                style={{ width: '100%', minWidth: 0 }}
                                value={quickKeyword}
                                onChange={(event) => {
                                    setQuickKeyword(event.target.value);
                                    setQuickActiveIndex(-1);
                                }}
                                onKeyDown={(event) => {
                                    if (event.key === 'ArrowDown') {
                                        event.preventDefault();
                                        setQuickActiveIndex((prev) => (
                                            findNextEnabledQuickActionIndex(filteredQuickActions, prev, 1)
                                        ));
                                        return;
                                    }
                                    if (event.key === 'ArrowUp') {
                                        event.preventDefault();
                                        setQuickActiveIndex((prev) => {
                                            const seed = prev < 0 ? 0 : prev;
                                            return findNextEnabledQuickActionIndex(filteredQuickActions, seed, -1);
                                        });
                                        return;
                                    }
                                    if (event.key !== 'Enter') return;
                                    const selected = quickActiveIndex >= 0
                                        ? filteredQuickActions[quickActiveIndex]
                                        : null;
                                    const fallback = filteredQuickActions.find((item) => !item.disabled);
                                    const target = selected && !selected.disabled ? selected : fallback;
                                    if (!target) return;
                                    event.preventDefault();
                                    runQuickAction(target.id, target.run);
                                }}
                                placeholder="输入关键词：保存 / 预览 / 发布 / 变量 / 缓存 / 合规 / 导出 ..."
                            />
                        </div>
                        <div style={{ overflowY: 'auto', padding: '0 12px 12px', display: 'grid', gap: 6 }}>
                            {filteredQuickActions.length === 0 ? (
                                <div style={{ padding: '20px 12px', fontSize: 13, color: '#64748b' }}>
                                    未匹配到动作，请换个关键词。
                                </div>
                            ) : (
                                filteredQuickActions.map((item, index) => {
                                    const isRecent = quickRecentOrder.has(item.id);
                                    return (
                                    <button
                                        key={item.id}
                                        ref={(node) => {
                                            quickActionRefs.current[index] = node;
                                        }}
                                        type="button"
                                        className="header-btn"
                                        style={{
                                            width: '100%',
                                            justifyContent: 'flex-start',
                                            background: index === quickActiveIndex ? 'rgba(148,163,184,0.2)' : undefined,
                                            borderColor: index === quickActiveIndex ? '#94a3b8' : undefined,
                                        }}
                                        disabled={item.disabled}
                                        onMouseEnter={() => setQuickActiveIndex(index)}
                                        onClick={() => runQuickAction(item.id, item.run)}
                                        title={item.label}
                                    >
                                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                                            <span>{item.label}</span>
                                            {isRecent ? (
                                                <span style={{
                                                    fontSize: 11,
                                                    padding: '1px 6px',
                                                    borderRadius: 999,
                                                    background: 'rgba(14,116,144,0.15)',
                                                    color: '#0e7490',
                                                }}
                                                >
                                                    最近
                                                </span>
                                            ) : null}
                                        </span>
                                        {item.hotkey ? (
                                            <span style={{ marginLeft: 'auto', fontSize: 11, color: '#64748b' }}>
                                                {item.hotkey}
                                            </span>
                                        ) : null}
                                    </button>
                                    );
                                })
                            )}
                        </div>
                    </div>
                </div>
            ) : null}

            <GlobalVariableManager
                open={showVariableManager}
                variables={config.globalVariables ?? []}
                cycleWarnings={cycleWarnings}
                onClose={() => setShowVariableManager(false)}
                onChange={(next) => updateConfig({ globalVariables: next })}
            />

            <InteractionDebugPanel
                open={showInteractionDebugPanel}
                cycleWarnings={cycleWarnings}
                onClose={() => setShowInteractionDebugPanel(false)}
            />

            <CacheObservabilityPanel
                open={showCachePanel}
                onClose={() => setShowCachePanel(false)}
            />

            <ScreenCompliancePanel
                open={showCompliancePanel}
                screenId={id}
                onClose={() => setShowCompliancePanel(false)}
            />

            <ScreenHealthPanel
                open={showHealthPanel}
                screenId={id}
                onClose={() => setShowHealthPanel(false)}
            />

            <ScreenAclPanel
                open={showAclPanel}
                screenId={id}
                onClose={() => setShowAclPanel(false)}
            />

            <ScreenAuditPanel
                open={showAuditPanel}
                screenId={id}
                onClose={() => setShowAuditPanel(false)}
            />

            <ScreenCollaborationPanel
                open={showCollaborationPanel}
                screenId={id}
                components={config.components ?? []}
                selectedIds={state.selectedIds ?? []}
                onLocateComponent={(componentId) => {
                    const target = String(componentId || '').trim();
                    if (!target) return;
                    const exists = (config.components ?? []).some((item) => item.id === target);
                    if (exists) {
                        selectComponents([target]);
                    }
                }}
                onClose={() => setShowCollaborationPanel(false)}
            />

            <ScreenEditLockPanel
                open={showEditLockPanel}
                screenId={id}
                lock={editLock}
                onChange={(next) => {
                    setEditLock(next);
                    if (!next?.active || next.mine) {
                        setLockErrorText(null);
                    }
                }}
                onClose={() => setShowEditLockPanel(false)}
            />

            <ScreenConflictPanel
                open={showConflictPanel}
                conflict={lastConflict}
                loading={conflictLoading}
                onClose={() => setShowConflictPanel(false)}
                onReloadLatest={handleReloadLatestDraft}
                onSelectConflictComponents={(ids) => {
                    const idSet = new Set((config.components ?? []).map((item) => item.id));
                    const filtered = ids.filter((item) => idSet.has(item));
                    selectComponents(filtered);
                }}
            />

            <ScreenVersionComparePanel
                open={showVersionComparePanel}
                diff={versionDiff}
                onClose={() => setShowVersionComparePanel(false)}
            />

            <ScreenVersionComparePickerPanel
                open={showVersionComparePicker}
                versions={versionCandidates}
                loading={isLoadingVersions}
                onClose={() => setShowVersionComparePicker(false)}
                onCompare={handleConfirmVersionCompare}
            />

            <ScreenVersionRollbackPanel
                open={showVersionRollbackPanel}
                versions={versionCandidates}
                loading={isLoadingVersions}
                onClose={() => setShowVersionRollbackPanel(false)}
                onRollback={handleConfirmVersionRollback}
            />

            <VersionHistoryPanel
                open={showVersionHistoryPanel}
                versions={versionCandidates}
                currentConfig={config}
                loading={isLoadingVersions}
                onClose={() => setShowVersionHistoryPanel(false)}
                onRollback={handleConfirmVersionRollback}
                onCompare={handleConfirmVersionCompare}
            />

            <ScreenSnapshotPanel
                open={showSnapshotPanel}
                screenId={id}
                onClose={() => setShowSnapshotPanel(false)}
            />

            <ScreenSharePolicyPanel
                open={showSharePolicyPanel}
                screenId={id}
                onClose={() => setShowSharePolicyPanel(false)}
            />
        </>
    );
}
