// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useScreen } from '../../ScreenContext';
import { detectInteractionCycles } from '../../interactionGraph';
import {
    analyticsApi,
    HttpError,
    type ScreenDetail,
    type ScreenEditLock,
    type ScreenVersion,
    type ScreenVersionDiff,
} from '../../../../api/analyticsApi';
import type { ScreenUpdateConflict } from '../ScreenConflictPanel';
import { Modal, message } from 'antd';
import { toast } from 'sonner';
import { buildScreenPayload, normalizeScreenConfig, validateScreenPayload } from '../../screenSpec';
import { commitScreenPageDraft, materializeScreenPage } from '../../screenPageState';
import {
    buildScreenDraftRecoveryKey,
    clearScreenDraftRecovery,
    hasScreenDraftChanges,
    readScreenDraftRecovery,
    saveScreenDraftRecovery,
} from '../../screenDraftRecovery';
import { resolveScreenTheme, applyThemeToComponents, getThemeTokens, type ThemeComponentApplyMode } from '../../screenThemes';
import type { ScreenTheme } from '../../types';
import { resolveRouteForOpen, resolveRouteHref } from '../../../../helpers/resolveAnalyticsUrl';
import {
    ScreenHeaderMenus,
    type HeaderActiveMenu,
    type PreviewDeviceMode,
    type ScreenHeaderPermissions,
} from './ScreenHeaderMenus';
import { ScreenHeaderNotices } from './ScreenHeaderNotices';
import { ScreenHeaderPanels } from './ScreenHeaderPanels';
import { useScreenExportActions } from './useScreenExportActions';
import {
    VERSION_ACTION_STORAGE_KEY,
    THEME_OPTIONS,
    buildPublishNoticeStorageKey,
    buildComponentConflictMeta,
    type PublishInfo,
} from './helpers';

interface ScreenHeaderProps {
    currentPageIndex?: number;
    onResetPageIndex?: () => void;
    focusMode?: boolean;
    onToggleFocusMode?: () => void;
    showLibraryPanel?: boolean;
    onToggleLibraryPanel?: () => void;
    showInspectorPanel?: boolean;
    onToggleInspectorPanel?: () => void;
}

export function ScreenHeader({
    currentPageIndex = 0,
    onResetPageIndex,
    focusMode,
    onToggleFocusMode,
    showLibraryPanel,
    onToggleLibraryPanel,
    showInspectorPanel,
    onToggleInspectorPanel,
}: ScreenHeaderProps = {}) {
    const navigate = useNavigate();
    const { id } = useParams<{ id: string }>();
    const {
        state,
        dispatch,
        updateConfig,
        loadConfig,
        markBaseline,
        selectComponents,
        isSaving,
        setIsSaving,
        setEditorReadonly,
    } = useScreen();
    const { config } = state;
    const persistedConfig = useMemo(
        () => commitScreenPageDraft(config, currentPageIndex),
        [config, currentPageIndex],
    );
    const baselineConfigForCurrentPage = useMemo(
        () => materializeScreenPage(state.baselineConfig, currentPageIndex),
        [currentPageIndex, state.baselineConfig],
    );
    const hasUnsavedChanges = useMemo(
        () => hasScreenDraftChanges(baselineConfigForCurrentPage, persistedConfig),
        [baselineConfigForCurrentPage, persistedConfig],
    );
    const draftRecoveryKey = useMemo(
        () => buildScreenDraftRecoveryKey(id || config.id || 'new'),
        [config.id, id],
    );
    const recoveryPromptedKeyRef = useRef<string | null>(null);
    // DEBUG: expose live config on window so we can inspect in Console.
    useEffect(() => {
        (window as any)._dtsScreenState = {
            name: config.name,
            id: config.id,
            components: config.components?.length ?? 0,
            pages: config.pages?.length ?? 0,
            currentPageIndex,
            ts: Date.now(),
        };
    }, [config, currentPageIndex]);
    const [isEditingName, setIsEditingName] = useState(false);
    const [nameValue, setNameValue] = useState(config.name);
    const [isPublishing, setIsPublishing] = useState(false);
    const [isLoadingVersions, setIsLoadingVersions] = useState(false);
    const [saveFailure, setSaveFailure] = useState<{ message: string; failedAt: number } | null>(null);
    const [lastRecoverySavedAt, setLastRecoverySavedAt] = useState<number | null>(null);
    const [showVariableManager, setShowVariableManager] = useState(false);
    const [showConflictPanel, setShowConflictPanel] = useState(false);
    const [showVersionComparePanel, setShowVersionComparePanel] = useState(false);
    const [showVersionComparePicker, setShowVersionComparePicker] = useState(false);
    const [showVersionRollbackPanel, setShowVersionRollbackPanel] = useState(false);
    const [showVersionHistoryPanel, setShowVersionHistoryPanel] = useState(false);
    // designAction state removed — "编辑" menu uses direct buttons now
    // governanceAction state removed — "安全" menu uses direct buttons now
    const [previewDeviceMode, setPreviewDeviceMode] = useState<PreviewDeviceMode>('auto');
    const [versionAction, setVersionAction] = useState<'history' | 'compare'>(() => {
        if (typeof window === 'undefined') return 'history';
        const raw = window.localStorage.getItem(VERSION_ACTION_STORAGE_KEY);
        return raw === 'compare' ? 'compare' : 'history';
    });
    // toolsSection removed — toolbox split into 3 independent header menus
    // --- Merged toolbar state (from CanvasToolbar "更多工具") ---
    const [themeApplyMode, setThemeApplyMode] = useState<ThemeComponentApplyMode>('force');
    const [showLinkageGraph, setShowLinkageGraph] = useState(false);
    const themeInputRef = useRef<HTMLInputElement | null>(null);
    const { selectedIds, showGrid } = state;

    const handleToolbarThemeChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
        const theme = e.target.value as ScreenTheme;
        const customTheme = theme === 'brand-custom' ? config.customTheme : undefined;
        const tokens = getThemeTokens(theme, customTheme);
        const updatedComponents = applyThemeToComponents(config.components || [], theme, 'force', customTheme);
        updateConfig({ theme, backgroundColor: tokens.canvasBackground, components: updatedComponents });
    }, [config.components, config.customTheme, updateConfig]);

    const applyThemeToAllComponents = useCallback((mode: ThemeComponentApplyMode) => {
        const nextComponents = applyThemeToComponents(config.components, config.theme, mode, config.customTheme);
        updateConfig({ components: nextComponents });
    }, [config.components, config.customTheme, config.theme, updateConfig]);

    const handleZoomReset = useCallback(() => dispatch({ type: 'SET_ZOOM', payload: 100 }), [dispatch]);

    const handleZoomFit = useCallback(() => {
        const el = document.querySelector('.canvas-container') as HTMLElement | null;
        const aw = el ? Math.max(320, el.clientWidth - 24) : Math.max(480, (window.visualViewport?.width ?? window.innerWidth) - 420);
        const ah = el ? Math.max(240, el.clientHeight - 24) : Math.max(260, (window.visualViewport?.height ?? window.innerHeight) - 160);
        const cw = Number(config.width) || 1920;
        const ch = Number(config.height) || 1080;
        const fitPercent = Math.min(300, Math.max(25, Math.floor(Math.min(aw / cw, ah / ch) * 100)));
        dispatch({ type: 'SET_ZOOM', payload: fitPercent });
    }, [dispatch, config.width, config.height]);

    const handleExportThemePack = useCallback(() => {
        const payload = {
            schema: 'dts.screen-theme-pack',
            version: 1,
            name: config.name,
            theme: config.theme || 'legacy-dark',
            customTheme: config.customTheme || null,
            backgroundColor: config.backgroundColor,
            backgroundImage: config.backgroundImage || null,
            applyToComponents: true,
            componentStyleMode: themeApplyMode,
            exportedAt: new Date().toISOString(),
        };
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `theme-pack-${new Date().toISOString().slice(0, 10)}.json`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    }, [config.name, config.theme, config.customTheme, config.backgroundColor, config.backgroundImage, themeApplyMode]);

    const handleImportThemePackClick = useCallback(() => themeInputRef.current?.click(), []);

    const handleThemePackFileChange = useCallback(async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        event.target.value = '';
        if (!file) return;
        try {
            const content = await file.text();
            const raw = JSON.parse(content);
            if (!raw || typeof raw !== 'object') { toast.error('主题包格式不正确'); return; }
            if (raw.schema && raw.schema !== 'dts.screen-theme-pack') { toast.error('主题包 schema 不匹配'); return; }
            const normalizeTheme = (t: unknown) => {
                if (THEME_OPTIONS.some((option) => option.value === t)) return t as ScreenTheme;
                return undefined;
            };
            const nextTheme = normalizeTheme(raw.theme) || config.theme;
            const nextCustomTheme = raw.customTheme && typeof raw.customTheme === 'object' && !Array.isArray(raw.customTheme)
                ? raw.customTheme as Record<string, string>
                : config.customTheme;
            const fallbackBg = getThemeTokens(nextTheme, nextCustomTheme).canvasBackground;
            const nextBg = typeof raw.backgroundColor === 'string' && raw.backgroundColor.trim().length > 0 ? raw.backgroundColor.trim() : fallbackBg;
            updateConfig({
                theme: nextTheme,
                customTheme: nextCustomTheme,
                backgroundColor: nextBg,
                backgroundImage: typeof raw.backgroundImage === 'string' && raw.backgroundImage.trim().length > 0 ? raw.backgroundImage.trim() : undefined,
            });
            const importMode = raw.componentStyleMode === 'safe' ? 'safe' : 'force';
            if (raw.applyToComponents !== false) {
                const confirmed = window.confirm(`主题包已导入，是否批量应用组件样式？\n策略：${importMode === 'force' ? '强制覆盖' : '仅补缺省'}`);
                if (confirmed) {
                    const nextComps = applyThemeToComponents(config.components, nextTheme, importMode as ThemeComponentApplyMode, nextCustomTheme);
                    updateConfig({ components: nextComps });
                }
            }
        } catch { toast.error('主题包解析失败'); }
    }, [config.theme, config.customTheme, config.components, updateConfig]);

    const handleShortcutHelp = useCallback(() => {
        toast.error([
            '快捷键说明', '',
            'Ctrl/Cmd + Z：撤销', 'Ctrl/Cmd + Y / Shift+Z：重做',
            'Ctrl/Cmd + C / V：复制 / 粘贴', 'Ctrl/Cmd + D：复制一份',
            'Ctrl/Cmd + A：全选', 'Ctrl/Cmd + \\：聚焦模式',
            'Ctrl/Cmd + Alt + 1/2：左栏/右栏', 'Ctrl/Cmd + 1/2：属性/图层',
            'Delete / Backspace：删除',
            '方向键：移动 1px', 'Shift+方向键：移动 10px',
            'Ctrl/Cmd + =/- ：缩放', 'Ctrl/Cmd + 0：100%',
        ].join('\n'));
    }, []);
    // --- End merged toolbar state ---

    const [conflictLoading, setConflictLoading] = useState(false);
    const [lastConflict, setLastConflict] = useState<ScreenUpdateConflict | null>(null);
    const [versionDiff, setVersionDiff] = useState<ScreenVersionDiff | null>(null);
    const [versionCandidates, setVersionCandidates] = useState<ScreenVersion[]>([]);
    const [editLock, setEditLock] = useState<ScreenEditLock | null>(null);
    const [lockErrorText, setLockErrorText] = useState<string | null>(null);
    const [publishModalOpen, setPublishModalOpen] = useState(false);
    const [publishInfo, setPublishInfo] = useState<PublishInfo | null>(null);
    const menuContainerRef = useRef<HTMLDivElement | null>(null);
    const [activeMenu, setActiveMenu] = useState<HeaderActiveMenu>(null);
    const [permissions, setPermissions] = useState<ScreenHeaderPermissions>({
        canRead: true,
        canEdit: true,
        canPublish: true,
        canManage: true,
        canDelete: true,
        isOwner: true,
    });

    const cycleWarnings = useMemo(() => detectInteractionCycles(config), [config]);
    const lockedByOther = !!(editLock?.active && !editLock?.mine);
    const lockOwnerText = String(editLock?.ownerName || editLock?.ownerId || '其他用户');

    useEffect(() => {
        setEditorReadonly(lockedByOther);
        return () => setEditorReadonly(false);
    }, [lockedByOther, setEditorReadonly]);

    useEffect(() => {
        if (!hasUnsavedChanges || lockedByOther) {
            return;
        }
        const handleBeforeUnload = (event: BeforeUnloadEvent) => {
            event.preventDefault();
            event.returnValue = '';
        };
        window.addEventListener('beforeunload', handleBeforeUnload);
        return () => window.removeEventListener('beforeunload', handleBeforeUnload);
    }, [hasUnsavedChanges, lockedByOther]);

    useEffect(() => {
        if (lockedByOther) {
            return;
        }
        if (!hasUnsavedChanges) {
            try {
                clearScreenDraftRecovery(draftRecoveryKey);
                setLastRecoverySavedAt(null);
            } catch {
                // ignore local cache failures
            }
            return;
        }
        const timer = window.setTimeout(() => {
            try {
                const savedAt = Date.now();
                saveScreenDraftRecovery(draftRecoveryKey, {
                    screenId: String(id || config.id || 'new'),
                    savedAt,
                    config: persistedConfig,
                });
                setLastRecoverySavedAt(savedAt);
            } catch {
                // ignore quota/private-mode failures
            }
        }, 800);
        return () => window.clearTimeout(timer);
    }, [config.id, draftRecoveryKey, hasUnsavedChanges, id, lockedByOther, persistedConfig]);

    useEffect(() => {
        const isExistingScreenReady = !id || String(config.id || '') === String(id);
        if (!isExistingScreenReady || lockedByOther || hasUnsavedChanges) {
            return;
        }
        if (recoveryPromptedKeyRef.current === draftRecoveryKey) {
            return;
        }
        recoveryPromptedKeyRef.current = draftRecoveryKey;
        let recovered;
        try {
            recovered = readScreenDraftRecovery(draftRecoveryKey);
        } catch {
            clearScreenDraftRecovery(draftRecoveryKey);
            return;
        }
        if (!recovered || !hasScreenDraftChanges(persistedConfig, recovered.config)) {
            clearScreenDraftRecovery(draftRecoveryKey);
            return;
        }
        const savedAt = new Date(recovered.savedAt).toLocaleString();
        Modal.confirm({
            title: '发现未保存的本地草稿',
            content: `检测到 ${savedAt} 自动保存的编辑内容，是否恢复到编辑器？`,
            okText: '恢复草稿',
            cancelText: '丢弃草稿',
            onOk: () => {
                updateConfig(recovered.config);
                toast.success('已恢复本地草稿');
            },
            onCancel: () => {
                clearScreenDraftRecovery(draftRecoveryKey);
            },
        });
    }, [config.id, draftRecoveryKey, hasUnsavedChanges, id, lockedByOther, persistedConfig, updateConfig]);

    useEffect(() => {
        if (!id || typeof window === 'undefined') {
            setPublishInfo(null);
            return;
        }
        try {
            const raw = window.localStorage.getItem(buildPublishNoticeStorageKey(id));
            if (!raw) {
                setPublishInfo(null);
                return;
            }
            const parsed = JSON.parse(raw) as PublishInfo;
            if (!parsed || String(parsed.screenId || '') !== String(id)) {
                setPublishInfo(null);
                return;
            }
            setPublishInfo(parsed);
        } catch {
            setPublishInfo(null);
        }
    }, [id]);

    useEffect(() => {
        if (!id || typeof window === 'undefined') {
            return;
        }
        const key = buildPublishNoticeStorageKey(id);
        if (!publishInfo || String(publishInfo.screenId || '') !== String(id)) {
            window.localStorage.removeItem(key);
            return;
        }
        window.localStorage.setItem(key, JSON.stringify(publishInfo));
    }, [id, publishInfo]);

    // designAction / governanceAction localStorage persistence removed — menus use direct buttons now

    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(VERSION_ACTION_STORAGE_KEY, versionAction);
    }, [versionAction]);

    // toolsSection localStorage persistence removed — no longer needed

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
            setPermissions({ canRead: true, canEdit: true, canPublish: true, canManage: true, canDelete: true, isOwner: true });
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
                    canDelete: (screen as Record<string, unknown>).canDelete !== false,
                    isOwner: (screen as Record<string, unknown>).isOwner === true,
                });
            })
            .catch(() => {
                if (!cancelled) {
                    setPermissions({ canRead: true, canEdit: true, canPublish: true, canManage: false, canDelete: false, isOwner: false });
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
            console.warn('[screen-spec] normalized with warnings:', normalized.warnings);
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
            const payload = buildScreenPayload(persistedConfig);
            const baseline = state.baselineConfig;
            if (id && baseline?.updatedAt) {
                payload._conflict = buildComponentConflictMeta(baseline);
            }
            const validation = validateScreenPayload(payload);
            if (validation.errors.length > 0) {
                throw new Error(`配置校验失败：${validation.errors.join('；')}`);
            }
            if (validation.warnings.length > 0) {
                console.warn('[screen-spec] save payload warnings:', validation.warnings);
            }

            if (id) {
                // Sprint-24 F3：classification 必须走专属 PATCH 端点（owner-only / 降级 reason / 独立审计），
                // PUT /{id} 故意不接受 classification 字段。检测到 classification 相对 baseline 有变化时，
                // 先调 PATCH /classification 完成密级落库，再调 PUT 同步其它结构变更。
                const upper = (v: unknown): string | null => {
                    if (typeof v !== 'string') return null;
                    const t = v.trim().toUpperCase();
                    return t === 'PUBLIC' || t === 'INTERNAL' || t === 'SECRET' || t === 'CONFIDENTIAL' ? t : null;
                };
                const baseClassification = upper((baseline as { classification?: string | null } | undefined)?.classification);
                const nextClassification = upper(persistedConfig.classification);
                if (nextClassification && nextClassification !== baseClassification) {
                    try {
                        await analyticsApi.updateScreenClassification(id, nextClassification);
                    } catch (err) {
                        if (err instanceof HttpError) {
                            if (err.status === 403) {
                                throw new Error('密级修改失败：仅大屏 owner 可设置密级，请联系 owner 协助补登。');
                            }
                            if (err.status === 400) {
                                // 后端在「降级路径未带 reason」「不是合法枚举」等场景返回 400
                                throw new Error(`密级修改失败：${err.bodyText || '请检查输入'}`);
                            }
                        }
                        throw err;
                    }
                }

                const updated = await analyticsApi.updateScreen(id, payload);
                const normalized = normalizeScreenConfig(updated, { id: updated.id });
                const resolvedTheme = resolveScreenTheme(normalized.config.theme, normalized.config.backgroundColor);
                const synced = materializeScreenPage({ ...normalized.config, theme: resolvedTheme }, currentPageIndex);
                updateConfig(synced);
                markBaseline(synced);
                clearScreenDraftRecovery(draftRecoveryKey);
                return id;
            }

            const result = await analyticsApi.createScreen(payload);
            clearScreenDraftRecovery(draftRecoveryKey);
            if (result.id) {
                navigate(`/bi/screens/${result.id}/edit`, { replace: true });
            }
            return result.id;
        } finally {
            setIsSaving(false);
        }
    }, [currentPageIndex, draftRecoveryKey, id, isSaving, markBaseline, navigate, persistedConfig, setIsSaving, state.baselineConfig, updateConfig]);

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
            toast.error(message);
        } finally {
            setConflictLoading(false);
        }
    }, [applyScreenDetail, id]);

    const handleSave = useCallback(async () => {
        try {
            await saveScreen();
            setSaveFailure(null);
        } catch (error) {
            console.error('Failed to save screen:', error);
            const message = error instanceof HttpError && error.code === 'SCREEN_UPDATE_CONFLICT'
                ? handleUpdateConflictError(error, '保存失败，存在并发冲突')
                : handleLockHttpError(error, '保存失败');
            setSaveFailure({ message, failedAt: Date.now() });
            toast.error(message);
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

    const handlePublish = useCallback(async () => {
        if (isPublishing) return;
        setIsPublishing(true);
        try {
            const screenId = await saveScreen();
            if (!screenId) {
                toast.error('请先保存大屏');
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
            const previewUrl = resolveRouteHref(`/bi/screens/${encodeURIComponent(String(screenId))}/preview`);
            let publicUrl: string | null = null;
            try {
                const policy = await analyticsApi.createScreenPublicLink(screenId, {});
                if (policy?.uuid) {
                    publicUrl = resolveRouteHref(`/bi/public/screen/${policy.uuid}`);
                }
            } catch (linkError) {
                console.warn('Publish succeeded but creating public link failed:', linkError);
            }
            setPublishInfo({
                screenId,
                versionNo,
                previewUrl,
                publicUrl: publicUrl ?? undefined,
                warmupText: warmupText || undefined,
            });
            setPublishModalOpen(true);
            toast.success('发布成功，版本 v' + versionNo + warmupText);
        } catch (error) {
            console.error('Failed to publish screen:', error);
            const message = error instanceof HttpError && error.code === 'SCREEN_UPDATE_CONFLICT'
                ? handleUpdateConflictError(error, '发布失败，存在并发冲突')
                : handleLockHttpError(error, '发布失败');
            toast.error(message);
        } finally {
            setIsPublishing(false);
        }
    }, [handleLockHttpError, handleUpdateConflictError, isPublishing, saveScreen]);

    useEffect(() => {
        if (!id || !permissions.canRead || publishInfo) {
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
                setPublishInfo({
                    screenId: id,
                    versionNo,
                    previewUrl: resolveRouteHref(`/bi/screens/${encodeURIComponent(String(id))}/preview`),
                    publicUrl: undefined,
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
    }, [id, permissions.canRead, publishInfo]);

    const handleVersionHistory = useCallback(async () => {
        if (!id || isLoadingVersions) return;

        setIsLoadingVersions(true);
        try {
            const versions = await analyticsApi.listScreenVersions(id);
            if (!versions.length) {
                toast.error('当前没有已发布版本');
                return;
            }
            setVersionCandidates(versions);
            setShowVersionHistoryPanel(true);
        } catch (error) {
            console.error('Failed to load version history:', error);
            const message = handleLockHttpError(error, '加载版本历史失败');
            toast.error(message);
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
            toast.success('回滚成功，已切换草稿与发布版本');
        } catch (error) {
            console.error('Failed to rollback version:', error);
            const message = handleLockHttpError(error, '回滚失败');
            toast.error(message);
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
                message.warning('至少需要两个版本才能对比');
                return;
            }
            setVersionCandidates(versions);
            setShowVersionComparePicker(true);
        } catch (error) {
            console.error('Failed to compare versions:', error);
            toast.error('版本对比失败');
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
            toast.error('版本对比失败');
        } finally {
            setIsLoadingVersions(false);
        }
    }, [id]);

    const handlePreview = () => {
        if (id) {
            const suffix = previewDeviceMode === 'auto'
                ? ''
                : `?device=${encodeURIComponent(previewDeviceMode)}`;
            window.open(resolveRouteForOpen(`/bi/screens/${id}/preview${suffix}`), '_blank', 'noopener,noreferrer');
        } else {
            toast.error('请先保存大屏后再预览');
        }
    };

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

    const { handleExportPng, handleExportPdf } = useScreenExportActions({
        id,
        persistedConfig,
        previewDeviceMode,
        screenName: config.name,
    });

    const handleBack = () => {
        if (hasUnsavedChanges && !window.confirm('当前大屏有未保存改动，确认返回列表并放弃这些改动？')) {
            return;
        }
        navigate('/bi/screens');
    };

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

    // canExecuteDesignAction / executeDesignAction / canExecuteGovernanceAction / executeGovernanceAction
    // removed — menus now use direct onClick buttons instead of select+execute pattern

    return (
        <>
            <div className="flex items-center justify-between gap-2.5 px-3 py-2 min-h-[52px] bg-[var(--color-surface-secondary)] border-b border-[var(--color-border)] shrink-0 relative z-[1200]">
                <div className="flex items-center gap-2.5 min-w-0 flex-auto">
                    <button type="button" className="header-btn back-btn flex items-center gap-1.5 px-4 py-2 border-0 bg-transparent text-[var(--color-text-secondary)] text-[13px] font-medium cursor-pointer whitespace-nowrap shrink-0 rounded-md transition-all duration-200 hover:text-[var(--color-text-primary)] hover:bg-[var(--color-surface-hover)]" onClick={handleBack} title="返回列表">
                        ← 返回
                    </button>
                    <div className="flex items-center min-w-0">
                        <div className="flex items-center min-w-0">
                            {isEditingName ? (
                                <input
                                    type="text"
                                    className="text-base font-semibold text-[var(--color-text-primary)] bg-[var(--color-surface)] border border-[var(--color-primary)] rounded px-2 py-1 outline-none min-w-[200px]"
                                    value={nameValue}
                                    onChange={(e) => setNameValue(e.target.value)}
                                    onBlur={handleNameBlur}
                                    onKeyDown={handleNameKeyDown}
                                    autoFocus
                                />
                            ) : (
                                <span className="text-base font-semibold text-[var(--color-text-primary)] cursor-pointer px-2 py-1 rounded transition-[background] duration-200 max-w-[min(42vw,420px)] overflow-hidden text-ellipsis whitespace-nowrap hover:bg-[var(--color-surface-hover)]" onClick={handleNameClick} title="点击编辑名称">
                                    {config.name}
                                </span>
                            )}
                            {hasUnsavedChanges && (
                                <span
                                    className="ml-1 shrink-0 rounded border px-1.5 py-0.5 text-[11px]"
                                    style={{
                                        color: '#fbbf24',
                                        borderColor: 'rgba(251,191,36,0.45)',
                                        background: 'rgba(251,191,36,0.10)',
                                    }}
                                    title="当前大屏存在未保存改动，本地恢复点会自动更新"
                                >
                                    未保存
                                </span>
                            )}
                        </div>
                    </div>
                </div>

                <div className="flex items-center gap-2 min-w-0 shrink">
                    <ScreenHeaderMenus
                        menuContainerRef={menuContainerRef}
                        activeMenu={activeMenu}
                        setActiveMenu={setActiveMenu}
                        id={id}
                        focusMode={focusMode}
                        showLibraryPanel={showLibraryPanel}
                        showInspectorPanel={showInspectorPanel}
                        onToggleFocusMode={onToggleFocusMode}
                        onToggleLibraryPanel={onToggleLibraryPanel}
                        onToggleInspectorPanel={onToggleInspectorPanel}
                        previewDeviceMode={previewDeviceMode}
                        setPreviewDeviceMode={setPreviewDeviceMode}
                        versionAction={versionAction}
                        setVersionAction={setVersionAction}
                        themeApplyMode={themeApplyMode}
                        setThemeApplyMode={setThemeApplyMode}
                        theme={config.theme || 'legacy-dark'}
                        showGrid={showGrid}
                        cycleWarningCount={cycleWarnings.length}
                        permissions={permissions}
                        lockedByOther={lockedByOther}
                        lockOwnerText={lockOwnerText}
                        isPublishing={isPublishing}
                        isSaving={isSaving}
                        isLoadingVersions={isLoadingVersions}
                        themeInputRef={themeInputRef}
                        executeMenuAction={executeMenuAction}
                        executeVersionAction={executeVersionAction}
                        onPreview={handlePreview}
                        onPublish={handlePublish}
                        onSave={handleSave}
                        onThemeChange={handleToolbarThemeChange}
                        onApplyThemeToAllComponents={applyThemeToAllComponents}
                        onExportThemePack={handleExportThemePack}
                        onImportThemePackClick={handleImportThemePackClick}
                        onThemePackFileChange={handleThemePackFileChange}
                        onZoomReset={handleZoomReset}
                        onZoomFit={handleZoomFit}
                        onToggleGrid={() => dispatch({ type: 'TOGGLE_GRID' })}
                        onShortcutHelp={handleShortcutHelp}
                        onToggleLinkageGraph={() => setShowLinkageGraph((prev) => !prev)}
                        onOpenVariableManager={() => setShowVariableManager(true)}
                        onExportPng={handleExportPng}
                        onExportPdf={handleExportPdf}
                    />
                    <div className="flex items-center gap-2 min-w-0 overflow-visible whitespace-nowrap">
                        <button
                            type="button"
                            className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed"
                            onClick={handlePreview}
                            disabled={!id}
                            title={`预览大屏（${previewDeviceMode === 'auto' ? '自动' : previewDeviceMode}）`}
                        >
                            预览
                        </button>
                        {id ? (
                            <button
                                type="button"
                                className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed"
                                onClick={() => void handlePublish()}
                                disabled={isPublishing || !permissions.canPublish || lockedByOther}
                                title={lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '发布当前草稿'}
                            >
                                {isPublishing ? '发布中...' : '发布'}
                            </button>
                        ) : null}
                        <button
                            type="button"
                            data-testid="analytics-screen-primary-action-button"
                            className="header-btn save-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-primary)] rounded-md bg-[var(--color-primary)] text-white text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 hover:bg-[var(--color-primary-dark)] disabled:opacity-50 disabled:cursor-not-allowed"
                            onClick={() => void handleSave()}
                            disabled={isSaving || !permissions.canEdit || lockedByOther}
                            title={lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '保存草稿'}
                        >
                            {isSaving ? '保存中...' : '保存'}
                        </button>
                    </div>
                </div>
            </div>
            <ScreenHeaderNotices
                lockedByOther={lockedByOther}
                lockOwnerText={lockOwnerText}
                lockErrorText={lockErrorText}
                saveFailure={saveFailure}
                hasUnsavedChanges={hasUnsavedChanges}
                lastRecoverySavedAt={lastRecoverySavedAt}
                hasClassification={!!config.classification}
                canEdit={permissions.canEdit}
                isSaving={isSaving}
                onRetrySave={handleSave}
            />

            <ScreenHeaderPanels
                config={config}
                selectedIds={selectedIds}
                cycleWarnings={cycleWarnings}
                showVariableManager={showVariableManager}
                onCloseVariableManager={() => setShowVariableManager(false)}
                onChangeVariables={(next) => updateConfig({ globalVariables: next })}
                publishModalOpen={publishModalOpen}
                onClosePublishModal={() => setPublishModalOpen(false)}
                publishInfo={publishInfo}
                isOwner={permissions.isOwner}
                showConflictPanel={showConflictPanel}
                lastConflict={lastConflict}
                conflictLoading={conflictLoading}
                onCloseConflictPanel={() => setShowConflictPanel(false)}
                onReloadLatestDraft={handleReloadLatestDraft}
                onSelectConflictComponents={(ids) => {
                    const idSet = new Set((config.components ?? []).map((item) => item.id));
                    const filtered = ids.filter((item) => idSet.has(item));
                    selectComponents(filtered);
                }}
                showVersionComparePanel={showVersionComparePanel}
                versionDiff={versionDiff}
                onCloseVersionComparePanel={() => setShowVersionComparePanel(false)}
                showVersionComparePicker={showVersionComparePicker}
                versionCandidates={versionCandidates}
                isLoadingVersions={isLoadingVersions}
                onCloseVersionComparePicker={() => setShowVersionComparePicker(false)}
                onConfirmVersionCompare={handleConfirmVersionCompare}
                showVersionRollbackPanel={showVersionRollbackPanel}
                onCloseVersionRollbackPanel={() => setShowVersionRollbackPanel(false)}
                onConfirmVersionRollback={handleConfirmVersionRollback}
                showVersionHistoryPanel={showVersionHistoryPanel}
                onCloseVersionHistoryPanel={() => setShowVersionHistoryPanel(false)}
                showLinkageGraph={showLinkageGraph}
                onCloseLinkageGraph={() => setShowLinkageGraph(false)}
            />
        </>
    );
}
