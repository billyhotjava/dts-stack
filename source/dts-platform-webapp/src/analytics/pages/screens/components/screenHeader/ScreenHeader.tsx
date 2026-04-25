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
import { GlobalVariableManager } from '../GlobalVariableManager';
import { CacheObservabilityPanel } from '../CacheObservabilityPanel';
import { ScreenCompliancePanel } from '../ScreenCompliancePanel';
import { ScreenAclPanel } from '../ScreenAclPanel';
import { PublishResultModal } from '../PublishResultModal';
import { ScreenAuditPanel } from '../ScreenAuditPanel';
import { ScreenSharePolicyPanel } from '../ScreenSharePolicyPanel';
import { ScreenSharePanel } from '../ScreenSharePanel';
import { ScreenHealthPanel } from '../ScreenHealthPanel';
import { InteractionDebugPanel } from '../InteractionDebugPanel';
import { ScreenCollaborationPanel } from '../ScreenCollaborationPanel';
import { ScreenEditLockPanel } from '../ScreenEditLockPanel';
import { ScreenConflictPanel, type ScreenUpdateConflict } from '../ScreenConflictPanel';
import { ScreenVersionComparePanel } from '../ScreenVersionComparePanel';
import { ScreenVersionComparePickerPanel } from '../ScreenVersionComparePickerPanel';
import { ScreenVersionRollbackPanel } from '../ScreenVersionRollbackPanel';
import { VersionHistoryPanel } from '../VersionHistoryPanel';
import { ScreenSnapshotPanel } from '../ScreenSnapshotPanel';
import { Modal, message } from 'antd';
import { toast } from 'sonner';
import { buildExploreSessionSteps } from '../ScreenHeader.helpers';
import { buildScreenPayload, normalizeScreenConfig, validateScreenPayload } from '../../specV2';
import { commitScreenPageDraft, materializeScreenPage } from '../../screenPageState';
import { resolveScreenTheme, applyThemeToComponents, getThemeTokens, type ThemeComponentApplyMode } from '../../screenThemes';
import type { ScreenTheme } from '../../types';
import { LinkageGraphPanel } from '../LinkageGraphPanel';
import { writeTextToClipboard } from '../../../../hooks/clipboard';
import { resolveRouteForOpen, resolveRouteHref } from '../../../../helpers/resolveAnalyticsUrl';
import { HeaderMenu } from './HeaderMenu';
import { ThemeSelector } from './ThemeSelector';
import {
    VERSION_ACTION_STORAGE_KEY,
    QUICK_ACTION_RECENT_STORAGE_KEY,
    PRIMARY_ACTION_STORAGE_KEY,
    BATCH_ACTION_OPTIONS,
    findNextEnabledQuickActionIndex,
    buildPublishNoticeStorageKey,
    buildComponentConflictMeta,
    type PublishInfo,
    type HeaderActionNotice,
    type QuickActionItem,
    type BatchAction,
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
        copyComponents,
        pasteComponents,
        clipboard,
        deleteComponents,
        updateSelectedComponents,
    } = useScreen();
    const { config } = state;
    const persistedConfig = useMemo(
        () => commitScreenPageDraft(config, currentPageIndex),
        [config, currentPageIndex],
    );
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
    const [showSharePanel, setShowSharePanel] = useState(false);
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
    // designAction state removed — "编辑" menu uses direct buttons now
    // governanceAction state removed — "安全" menu uses direct buttons now
    const [previewDeviceMode, setPreviewDeviceMode] = useState<'auto' | 'pc' | 'tablet' | 'mobile'>('auto');
    const [versionAction, setVersionAction] = useState<'history' | 'compare'>(() => {
        if (typeof window === 'undefined') return 'history';
        const raw = window.localStorage.getItem(VERSION_ACTION_STORAGE_KEY);
        return raw === 'compare' ? 'compare' : 'history';
    });
    const [primaryAction, setPrimaryAction] = useState<'preview' | 'publish' | 'save'>(() => {
        if (typeof window === 'undefined') return 'save';
        const raw = window.localStorage.getItem(PRIMARY_ACTION_STORAGE_KEY);
        if (raw === 'preview' || raw === 'publish' || raw === 'save') {
            return raw;
        }
        return 'save';
    });
    // toolsSection removed — toolbox split into 3 independent header menus
    const [isSavingTemplate, setIsSavingTemplate] = useState(false);
    const [headerActionNotice, setHeaderActionNotice] = useState<HeaderActionNotice | null>(null);
    const [showSaveTemplateDialog, setShowSaveTemplateDialog] = useState(false);
    const [templateForm, setTemplateForm] = useState({
        name: `${config.name || '未命名大屏'}-模板`,
        description: config.description || '',
        visibilityScope: 'team' as 'personal' | 'team' | 'global',
    });
    const [showExploreSessionDialog, setShowExploreSessionDialog] = useState(false);
    const [exploreSessionForm, setExploreSessionForm] = useState({
        title: `${config.name || '未命名大屏'} 分析会话`,
        question: `围绕大屏「${config.name || '未命名大屏'}」展开分析`,
        conclusion: '',
        tagsInput: '大屏,复盘',
    });
    // --- Merged toolbar state (from CanvasToolbar "更多工具") ---
    const [batchAction, setBatchAction] = useState<BatchAction>('duplicate');
    const [themeApplyMode, setThemeApplyMode] = useState<ThemeComponentApplyMode>('force');
    const [showLinkageGraph, setShowLinkageGraph] = useState(false);
    const themeInputRef = useRef<HTMLInputElement | null>(null);
    const { selectedIds, showGrid, zoom } = state;

    const canExecuteBatch = (() => {
        if (batchAction === 'paste') return clipboard.length > 0;
        if (batchAction === 'copy' || batchAction === 'duplicate') return selectedIds.length > 0;
        return selectedIds.length > 0;
    })();

    const executeBatchAction = useCallback(() => {
        if (batchAction === 'copy') { if (selectedIds.length > 0) copyComponents(); return; }
        if (batchAction === 'paste') { if (clipboard.length > 0) pasteComponents(); return; }
        if (batchAction === 'duplicate') {
            if (selectedIds.length === 0) return;
            copyComponents();
            setTimeout(() => pasteComponents(), 0);
            return;
        }
        if (batchAction === 'bring-top') {
            if (selectedIds.length === 0) return;
            const selected = config.components.filter(c => selectedIds.includes(c.id)).sort((a, b) => a.zIndex - b.zIndex);
            for (const item of selected) dispatch({ type: 'REORDER_LAYER', payload: { id: item.id, direction: 'top' } });
            return;
        }
        if (batchAction === 'send-bottom') {
            if (selectedIds.length === 0) return;
            const selected = config.components.filter(c => selectedIds.includes(c.id)).sort((a, b) => b.zIndex - a.zIndex);
            for (const item of selected) dispatch({ type: 'REORDER_LAYER', payload: { id: item.id, direction: 'bottom' } });
            return;
        }
        if (batchAction === 'show') { if (selectedIds.length > 0) updateSelectedComponents({ visible: true }); return; }
        if (batchAction === 'hide') { if (selectedIds.length > 0) updateSelectedComponents({ visible: false }); return; }
        if (batchAction === 'unlock') { if (selectedIds.length > 0) updateSelectedComponents({ locked: false }); return; }
        if (batchAction === 'lock') { if (selectedIds.length > 0) updateSelectedComponents({ locked: true }); return; }
        if (batchAction === 'delete') { if (selectedIds.length > 0) deleteComponents(selectedIds); }
    }, [batchAction, selectedIds, clipboard, copyComponents, pasteComponents, deleteComponents, updateSelectedComponents, dispatch, config.components]);

    const handleToolbarThemeChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
        const value = e.target.value as ScreenTheme | '';
        const theme = value || undefined;
        const tokens = getThemeTokens(theme);
        const updatedComponents = applyThemeToComponents(config.components || [], theme, 'force');
        updateConfig({ theme, backgroundColor: tokens.canvasBackground, components: updatedComponents });
    }, [config.components, updateConfig]);

    const applyThemeToAllComponents = useCallback((mode: ThemeComponentApplyMode) => {
        const nextComponents = applyThemeToComponents(config.components, config.theme, mode);
        updateConfig({ components: nextComponents });
    }, [config.components, config.theme, updateConfig]);

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
    }, [config.name, config.theme, config.backgroundColor, config.backgroundImage, themeApplyMode]);

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
                if (t === 'legacy-dark' || t === 'titanium' || t === 'glacier') return t as ScreenTheme;
                return undefined;
            };
            const nextTheme = normalizeTheme(raw.theme) || config.theme;
            const fallbackBg = getThemeTokens(nextTheme).canvasBackground;
            const nextBg = typeof raw.backgroundColor === 'string' && raw.backgroundColor.trim().length > 0 ? raw.backgroundColor.trim() : fallbackBg;
            updateConfig({
                theme: nextTheme,
                backgroundColor: nextBg,
                backgroundImage: typeof raw.backgroundImage === 'string' && raw.backgroundImage.trim().length > 0 ? raw.backgroundImage.trim() : undefined,
            });
            const importMode = raw.componentStyleMode === 'safe' ? 'safe' : 'force';
            if (raw.applyToComponents !== false) {
                const confirmed = window.confirm(`主题包已导入，是否批量应用组件样式？\n策略：${importMode === 'force' ? '强制覆盖' : '仅补缺省'}`);
                if (confirmed) {
                    const nextComps = applyThemeToComponents(config.components, nextTheme, importMode as ThemeComponentApplyMode);
                    updateConfig({ components: nextComps });
                }
            }
        } catch { toast.error('主题包解析失败'); }
    }, [config.theme, config.components, updateConfig]);

    const handleShortcutHelp = useCallback(() => {
        toast.error([
            '快捷键说明', '',
            'Ctrl/Cmd + Z：撤销', 'Ctrl/Cmd + Y / Shift+Z：重做',
            'Ctrl/Cmd + C / V：复制 / 粘贴', 'Ctrl/Cmd + D：复制一份',
            'Ctrl/Cmd + A：全选', 'Ctrl/Cmd + \\：聚焦模式',
            'Ctrl/Cmd + Alt + 1/2：左栏/右栏', 'Ctrl/Cmd + 1/2：属性/图层',
            'Ctrl/Cmd + K：命令面板', 'Delete / Backspace：删除',
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
    const quickInputRef = useRef<HTMLInputElement | null>(null);
    const quickActionRefs = useRef<Array<HTMLButtonElement | null>>([]);
    const menuContainerRef = useRef<HTMLDivElement | null>(null);
    const [activeMenu, setActiveMenu] = useState<'primary' | 'tools-view' | 'tools-edit' | 'tools-theme' | 'tools-io' | 'tools-release' | 'tools-security' | null>(null);
    const [permissions, setPermissions] = useState({
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

    useEffect(() => {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(PRIMARY_ACTION_STORAGE_KEY, primaryAction);
    }, [primaryAction]);

    // toolsSection localStorage persistence removed — no longer needed

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
                console.warn('[screen-spec-v2] save payload warnings:', validation.warnings);
            }

            if (id) {
                const updated = await analyticsApi.updateScreen(id, payload);
                const normalized = normalizeScreenConfig(updated, { id: updated.id });
                const resolvedTheme = resolveScreenTheme(normalized.config.theme, normalized.config.backgroundColor);
                const synced = materializeScreenPage({ ...normalized.config, theme: resolvedTheme }, currentPageIndex);
                updateConfig(synced);
                markBaseline(synced);
                return id;
            }

            const result = await analyticsApi.createScreen(payload);
            if (result.id) {
                navigate(`/bi/screens/${result.id}/edit`, { replace: true });
            }
            return result.id;
        } finally {
            setIsSaving(false);
        }
    }, [currentPageIndex, id, isSaving, markBaseline, navigate, persistedConfig, setIsSaving, state.baselineConfig, updateConfig]);

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
            toast.error(message);
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
            toast.error(message);
        }
    }, [handleLockHttpError, handleUpdateConflictError, saveScreen]);

    useEffect(() => {
        setTemplateForm((current) => ({
            ...current,
            name: current.name === `${config.name || '未命名大屏'}-模板` || !current.name
                ? `${config.name || '未命名大屏'}-模板`
                : current.name,
            description: showSaveTemplateDialog ? current.description : (config.description || ''),
        }));
        setExploreSessionForm((current) => ({
            ...current,
            title: showExploreSessionDialog ? current.title : `${config.name || '未命名大屏'} 分析会话`,
            question: showExploreSessionDialog ? current.question : `围绕大屏「${config.name || '未命名大屏'}」展开分析`,
        }));
    }, [config.description, config.name, showExploreSessionDialog, showSaveTemplateDialog]);

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

    const handleSaveAsTemplate = useCallback(() => {
        setTemplateForm({
            name: `${config.name || '未命名大屏'}-模板`,
            description: config.description || '',
            visibilityScope: 'team',
        });
        setShowSaveTemplateDialog(true);
    }, [config.description, config.name]);

    const handleSubmitSaveAsTemplate = useCallback(async () => {
        if (isSavingTemplate) return;
        setIsSavingTemplate(true);
        try {
            const screenId = await saveScreen();
            if (!screenId) {
                setHeaderActionNotice({
                    tone: 'error',
                    title: '模板保存失败',
                    message: '请先保存大屏后再存为模板。',
                });
                return;
            }
            const templateName = templateForm.name.trim();
            if (!templateName) {
                return;
            }
            const visibilityScope = templateForm.visibilityScope;
            await analyticsApi.createScreenTemplateFromScreen(screenId, {
                name: templateName,
                description: templateForm.description,
                category: 'custom',
                thumbnail: '🧩',
                visibilityScope,
                listed: true,
            });
            const scopeText = visibilityScope === 'personal' ? '个人' : visibilityScope === 'global' ? '全局' : '团队';
            setShowSaveTemplateDialog(false);
            setHeaderActionNotice({
                tone: 'success',
                title: '模板已保存',
                message: `已保存到${scopeText}模板，可在模板资产中心继续上架、恢复版本和导出。`,
            });
        } catch (error) {
            console.error('Failed to save screen as template:', error);
            const message = error instanceof HttpError && error.code === 'SCREEN_UPDATE_CONFLICT'
                ? handleUpdateConflictError(error, '存模板失败，存在并发冲突')
                : handleLockHttpError(error, '存模板失败');
            setHeaderActionNotice({
                tone: 'error',
                title: '模板保存失败',
                message,
            });
        } finally {
            setIsSavingTemplate(false);
        }
    }, [handleLockHttpError, handleUpdateConflictError, isSavingTemplate, saveScreen, templateForm]);

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

    const canExecutePrimaryAction = useMemo(() => {
        if (primaryAction === 'preview') {
            return Boolean(id);
        }
        if (primaryAction === 'publish') {
            return Boolean(id) && !isPublishing && permissions.canPublish && !lockedByOther;
        }
        return !isSaving && permissions.canEdit && !lockedByOther;
    }, [id, isPublishing, isSaving, lockedByOther, permissions.canEdit, permissions.canPublish, primaryAction]);

    const pageCount = Math.max(config.pages?.length ?? 0, 1);
    const themeLabel = config.theme === 'glacier'
        ? '冰川白'
        : (config.theme === 'titanium' ? '钛合金灰' : '经典深蓝');

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
        const url = resolveRouteForOpen(`/bi/screens/${id}/export?${params.toString()}`);
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
            screenSpec: buildScreenPayload(persistedConfig),
        });
        const fallbackName = `${config.name || 'screen'}.${format}`;
        downloadBlob(rendered.blob, rendered.fileName || fallbackName);
        return rendered;
    }, [config.name, downloadBlob, id, persistedConfig, previewDeviceMode, resolveServerRenderPixelRatio]);

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
            toast.error(error instanceof Error ? error.message : 'PNG 导出失败');
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
                toast.error(fallbackError instanceof Error ? fallbackError.message : 'PNG 导出失败');
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
            toast.error(error instanceof Error ? error.message : 'PDF 导出失败');
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
                toast.error(fallbackError instanceof Error ? fallbackError.message : 'PDF 导出失败');
            }
        }
    };

    const handleShare = async () => {
        if (!id || isSharing) return;
        setIsSharing(true);
        try {
            const { uuid } = await analyticsApi.createScreenPublicLink(id, {});
            if (!uuid) {
                setHeaderActionNotice({
                    tone: 'error',
                    title: '分享链接生成失败',
                    message: '未获取到分享链接，请稍后重试。',
                });
                return;
            }
            const baseUrl = resolveRouteHref(`/bi/public/screen/${uuid}`);
            const embedUrl = `${baseUrl}?embed=1&hideControls=1`;
            const iframeCode = `<iframe src="${embedUrl}" width="100%" height="600" frameborder="0" allowfullscreen style="border: none;"></iframe>`;
            const globalVars = config.globalVariables ?? [];
            const paramHint = globalVars.length > 0
                ? `\n\n可透传参数：\n${globalVars.map(v => `  ?var_${v.key}=值`).join('\n')}`
                : '';
            const shareInfo = `链接分享：\n${baseUrl}\n\n嵌入代码（iframe）：\n${iframeCode}${paramHint}`;
            const copied = await writeTextToClipboard(baseUrl);
            setHeaderActionNotice({
                tone: 'success',
                title: copied ? '分享链接已复制' : '分享信息已生成',
                message: shareInfo,
            });
        } catch (err) {
            console.error('Failed to create public link:', err);
            setHeaderActionNotice({
                tone: 'error',
                title: '分享链接生成失败',
                message: '创建分享链接失败，请先发布版本。',
            });
        } finally {
            setIsSharing(false);
        }
    };

    const handleCreateExploreSession = useCallback(() => {
        if (!permissions.canRead) {
            setHeaderActionNotice({
                tone: 'error',
                title: '无法创建分析会话',
                message: '当前账号没有读权限，无法沉淀分析会话。',
            });
            return;
        }
        setExploreSessionForm({
            title: `${config.name || '未命名大屏'} 分析会话`,
            question: `围绕大屏「${config.name || '未命名大屏'}」展开分析`,
            conclusion: '',
            tagsInput: '大屏,复盘',
        });
        setShowExploreSessionDialog(true);
    }, [config.name, permissions.canRead]);

    const handleSubmitCreateExploreSession = useCallback(async () => {
        try {
            const defaultTitle = `${config.name || '未命名大屏'} 分析会话`;
            const tags = exploreSessionForm.tagsInput
                .split(',')
                .map((item) => item.trim())
                .filter((item) => item.length > 0)
                .slice(0, 20);
            const created = await analyticsApi.createExploreSession({
                title: exploreSessionForm.title.trim() || defaultTitle,
                question: exploreSessionForm.question.trim() || null,
                conclusion: exploreSessionForm.conclusion.trim() || null,
                tags,
                steps: buildExploreSessionSteps(config),
            });
            const createdId = created?.id != null ? `#${created.id}` : '';
            setShowExploreSessionDialog(false);
            setHeaderActionNotice({
                tone: 'success',
                title: '分析会话已创建',
                message: `已创建分析会话 ${createdId || ''}。如需继续编排步骤或分享，请前往分析会话中心。`.trim(),
            });
        } catch (error) {
            console.error('Failed to create explore session:', error);
            setHeaderActionNotice({
                tone: 'error',
                title: '分析会话创建失败',
                message: error instanceof Error ? error.message : '创建分析会话失败，请稍后重试。',
            });
        }
    }, [config, exploreSessionForm]);

    const handleBack = () => {
        navigate('/bi/screens');
    };

    const handleCopyUrl = useCallback(async (url: string) => {
        const copied = await writeTextToClipboard(url);
        setHeaderActionNotice({
            tone: copied ? 'success' : 'error',
            title: copied ? '链接已复制' : '复制失败',
            message: copied ? url : `复制失败，请手工复制：\n${url}`,
        });
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

    // canExecuteDesignAction / executeDesignAction / canExecuteGovernanceAction / executeGovernanceAction
    // removed — menus now use direct onClick buttons instead of select+execute pattern

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
                id: 'command-help',
                label: '命令面板帮助',
                keywords: '命令 面板 help 快捷键',
                disabled: false,
                hotkey: 'Ctrl/Cmd + K',
                run: () => message.info('可输入关键词，使用 ↑/↓ 选择，Enter 执行。'),
            },
        ];
    }, [
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
                        </div>
                    </div>
                </div>

                <div className="flex items-center gap-2 min-w-0 shrink">
                    <div className="flex items-center gap-2 shrink-0" ref={menuContainerRef}>
                        <div className="header-mobile-primary-menu hidden">
                            <HeaderMenu
                                label="操作"
                                open={activeMenu === 'primary'}
                                onToggle={() => setActiveMenu((prev) => (prev === 'primary' ? null : 'primary'))}
                            >
                                <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                    <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">快捷操作</div>
                                    <button
                                        type="button"
                                        className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed"
                                        onClick={() => executeMenuAction(handlePreview)}
                                        title={`预览大屏（${previewDeviceMode === 'auto' ? '自动' : previewDeviceMode}）`}
                                    >
                                        预览
                                    </button>
                                    {id && (
                                        <button
                                            type="button"
                                            className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed"
                                            onClick={() => executeMenuAction(handlePublish)}
                                            disabled={isPublishing || !permissions.canPublish || lockedByOther}
                                            title={lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '发布当前草稿'}
                                        >
                                            {isPublishing ? '发布中...' : '发布'}
                                        </button>
                                    )}
                                    <button
                                        type="button"
                                        className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed"
                                        onClick={() => executeMenuAction(handleSave)}
                                        disabled={isSaving || !permissions.canEdit || lockedByOther}
                                        title={lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '保存草稿'}
                                    >
                                        {isSaving ? '保存中...' : '保存'}
                                    </button>
                                </div>
                            </HeaderMenu>
                        </div>
                        {/* --- 1. 视图 --- */}
                        <HeaderMenu
                            label="视图"
                            open={activeMenu === 'tools-view'}
                            onToggle={() => setActiveMenu((prev) => (prev === 'tools-view' ? null : 'tools-view'))}
                        >
                            {onToggleFocusMode && (
                                <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                    <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">面板</div>
                                    <button type="button" className={`flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed ${focusMode ? 'border-[var(--color-primary)] bg-[var(--color-primary-light)]' : ''}`} onClick={() => { onToggleFocusMode(); setActiveMenu(null); }} title="Ctrl/Cmd + \\">
                                        {focusMode ? '退出聚焦' : '聚焦模式'}
                                    </button>
                                    {!focusMode && onToggleLibraryPanel && (
                                        <button type="button" className={`flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed ${showLibraryPanel ? 'border-[var(--color-primary)] bg-[var(--color-primary-light)]' : ''}`} onClick={onToggleLibraryPanel} title="Ctrl/Cmd+Alt+1">
                                            {showLibraryPanel ? '隐藏左栏' : '显示左栏'}
                                        </button>
                                    )}
                                    {!focusMode && onToggleInspectorPanel && (
                                        <button type="button" className={`flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed ${showInspectorPanel ? 'border-[var(--color-primary)] bg-[var(--color-primary-light)]' : ''}`} onClick={onToggleInspectorPanel} title="Ctrl/Cmd+Alt+2">
                                            {showInspectorPanel ? '隐藏右栏' : '显示右栏'}
                                        </button>
                                    )}
                                </div>
                            )}
                            <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">视图</div>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={handleZoomReset} title="缩放重置为 100%">缩放100%</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={handleZoomFit} title="按当前窗口自动适配缩放">缩放适配</button>
                                <button type="button" className={`flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed ${showGrid ? 'border-[var(--color-primary)] bg-[var(--color-primary-light)]' : ''}`} onClick={() => dispatch({ type: 'TOGGLE_GRID' })} title="显示/隐藏网格">
                                    {showGrid ? '隐藏网格' : '显示网格'}
                                </button>
                            </div>
                            <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">帮助</div>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={handleShortcutHelp} title="查看快捷键">快捷键</button>
                            </div>
                        </HeaderMenu>
                        {/* --- 2. 编辑 --- */}
                        <HeaderMenu
                            label={`编辑${cycleWarnings.length > 0 ? `(${cycleWarnings.length})` : ''}`}
                            open={activeMenu === 'tools-edit'}
                            onToggle={() => setActiveMenu((prev) => (prev === 'tools-edit' ? null : 'tools-edit'))}
                        >
                            <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">批量动作</div>
                                <select className="px-2.5 py-[7px] border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium min-w-[110px] w-full focus:outline-none focus:border-[var(--color-primary)]" value={batchAction} onChange={(e) => setBatchAction(e.target.value as BatchAction)} title="批量动作">
                                    {BATCH_ACTION_OPTIONS.map((item) => (<option key={item.value} value={item.value}>{item.label}</option>))}
                                </select>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={executeBatchAction} disabled={!canExecuteBatch} title={canExecuteBatch ? '执行批量动作' : '请先选择组件'}>执行动作</button>
                            </div>
                            <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">联动与设计</div>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => { setActiveMenu(null); setShowLinkageGraph(prev => !prev); }} title="查看组件联动关系图">联动关系图</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => setShowVariableManager(true))} title="管理全局变量">变量管理</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => setShowInteractionDebugPanel(true))} title="联动调试面板">联动调试</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => { setQuickKeyword(''); setShowQuickActions(true); })} title="命令面板 Ctrl/Cmd+K">命令面板</button>
                            </div>
                            <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">更多</div>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(handleCreateExploreSession)} disabled={!permissions.canRead} title="沉淀分析会话">沉淀会话</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => setShowCollaborationPanel(true))} disabled={!id || !permissions.canRead} title="协作批注">协作批注</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(handleSaveAsTemplate)} disabled={!permissions.canEdit || isSavingTemplate} title="保存为模板">{isSavingTemplate ? '模板保存中...' : '保存模板'}</button>
                            </div>
                        </HeaderMenu>
                        {/* --- 3. 主题 --- */}
                        <HeaderMenu
                            label="主题"
                            open={activeMenu === 'tools-theme'}
                            onToggle={() => setActiveMenu((prev) => (prev === 'tools-theme' ? null : 'tools-theme'))}
                        >
                            <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">主题选择</div>
                                <ThemeSelector value={config.theme || ''} onChange={handleToolbarThemeChange} />
                            </div>
                            <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">应用与主题包</div>
                                <select className="px-2.5 py-[7px] border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium min-w-[110px] w-full focus:outline-none focus:border-[var(--color-primary)]" value={themeApplyMode} onChange={(e) => setThemeApplyMode(e.target.value === 'safe' ? 'safe' : 'force')} title="组件样式应用策略">
                                    <option value="force">强制覆盖</option>
                                    <option value="safe">仅补缺省</option>
                                </select>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => applyThemeToAllComponents(themeApplyMode)} title="按当前主题批量刷新组件样式">应用样式</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={handleExportThemePack} title="导出主题包">导出主题</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={handleImportThemePackClick} title="导入主题包">导入主题</button>
                            </div>
                        </HeaderMenu>
                        {/* --- 导出快照（JSON 导入导出已迁移至大屏列表页） --- */}
                        <HeaderMenu
                            label="导出"
                            open={activeMenu === 'tools-io'}
                            onToggle={() => setActiveMenu((prev) => (prev === 'tools-io' ? null : 'tools-io'))}
                        >
                            <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">导出快照</div>
                                <label className="text-xs text-[var(--color-text-secondary)] px-1" htmlFor="screen-io-device-mode">预览设备</label>
                                <select id="screen-io-device-mode" className="px-2.5 py-[7px] border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium min-w-[110px] w-full focus:outline-none focus:border-[var(--color-primary)]" value={previewDeviceMode} onChange={(e) => { const next = e.target.value; if (next === 'pc' || next === 'tablet' || next === 'mobile') { setPreviewDeviceMode(next); return; } setPreviewDeviceMode('auto'); }} title="预览设备模式">
                                    <option value="auto">自动</option>
                                    <option value="pc">PC</option>
                                    <option value="tablet">平板</option>
                                    <option value="mobile">手机</option>
                                </select>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(handleExportPng)} disabled={!id} title="导出 PNG 图片">导出 PNG</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(handleExportPdf)} disabled={!id} title="导出 PDF 文档">导出 PDF</button>
                                <div className="text-[11px] text-[var(--color-text-secondary)] px-1 py-0.5">JSON 导入/导出请前往「大屏列表」。</div>
                            </div>
                        </HeaderMenu>
                        {/* --- 4. 版本导出 (kept as-is) --- */}
                        <HeaderMenu
                            label="版本"
                            open={activeMenu === 'tools-release'}
                            onToggle={() => setActiveMenu((prev) => (prev === 'tools-release' ? null : 'tools-release'))}
                        >
                            <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">版本管理</div>
                                <label className="text-xs text-[var(--color-text-secondary)] px-1" htmlFor="screen-preview-device-mode">预览设备</label>
                                <select id="screen-preview-device-mode" className="px-2.5 py-[7px] border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium min-w-[110px] w-full focus:outline-none focus:border-[var(--color-primary)]" value={previewDeviceMode} onChange={(e) => { const next = e.target.value; if (next === 'pc' || next === 'tablet' || next === 'mobile') { setPreviewDeviceMode(next); return; } setPreviewDeviceMode('auto'); }} title="预览设备模式">
                                    <option value="auto">自动</option>
                                    <option value="pc">PC</option>
                                    <option value="tablet">平板</option>
                                    <option value="mobile">手机</option>
                                </select>
                                {id ? (
                                    <>
                                        <label className="text-xs text-[var(--color-text-secondary)] px-1" htmlFor="screen-version-action">版本动作</label>
                                        <select id="screen-version-action" className="px-2.5 py-[7px] border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium min-w-[110px] w-full focus:outline-none focus:border-[var(--color-primary)]" value={versionAction} onChange={(e) => { setVersionAction(e.target.value === 'compare' ? 'compare' : 'history'); }} title="选择版本动作">
                                            <option value="history">版本历史/回滚</option>
                                            <option value="compare">版本对比</option>
                                        </select>
                                        <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={executeVersionAction} disabled={isLoadingVersions || (versionAction === 'history' ? !permissions.canPublish : !permissions.canRead)} title={versionAction === 'history' ? '查看版本历史并回滚' : '查看版本差异摘要'}>
                                            {isLoadingVersions ? '加载中...' : '执行版本动作'}
                                        </button>
                                    </>
                                ) : null}
                            </div>
                        </HeaderMenu>
                        {/* --- 5. 安全 (replaces 治理) --- */}
                        <HeaderMenu
                            label="安全"
                            open={activeMenu === 'tools-security'}
                            onToggle={() => setActiveMenu((prev) => (prev === 'tools-security' ? null : 'tools-security'))}
                        >
                            <div className="grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1">
                                <div className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5">安全与治理</div>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => setShowEditLockPanel(true))} disabled={!id || !permissions.canRead} title="查看/管理编辑锁">
                                    编辑锁{lockedByOther ? '(占用)' : (editLock?.mine ? '(我)' : '')}
                                </button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => setShowCachePanel(true))} title="缓存观测面板">缓存观测</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => setShowAclPanel(true))} disabled={!id || !permissions.canManage} title="权限矩阵(ACL)">权限(ACL)</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => setShowAuditPanel(true))} disabled={!id || !permissions.canManage} title="审计记录">审计</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => setShowSharePanel(true))} disabled={!id || !permissions.canManage} title="分享大屏给其他用户">分享</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => setShowSharePolicyPanel(true))} disabled={!id || !permissions.canPublish} title="分享策略配置">分享策略</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(handleShare)} disabled={!id || !permissions.canPublish || isSharing} title="生成分享链接">
                                    {isSharing ? '分享中...' : '分享链接'}
                                </button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => setShowCompliancePanel(true))} title="合规检查">合规</button>
                                <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => executeMenuAction(() => setShowHealthPanel(true))} title="体检报告">体检</button>
                            </div>
                        </HeaderMenu>
                        <input ref={themeInputRef} type="file" accept="application/json,.json" style={{ display: 'none' }} onChange={handleThemePackFileChange} />
                    </div>
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
            {lockedByOther && (
                <div className="px-4 py-3 text-xs text-[#f59e0b] border-b border-white/[0.08] shrink-0 bg-[rgba(245,158,11,0.12)]">
                    编辑锁提示：当前由 {lockOwnerText} 编辑中，保存/发布已被保护性禁用。
                    {lockErrorText ? ` (${lockErrorText})` : ''}
                </div>
            )}
            {headerActionNotice && (
                <div
                    data-testid="analytics-screen-header-action-notice"
                    style={{
                        marginTop: 10,
                        padding: '12px 14px',
                        borderRadius: 10,
                        border: headerActionNotice.tone === 'success'
                            ? '1px solid rgba(16,185,129,0.28)'
                            : '1px solid rgba(239,68,68,0.28)',
                        background: headerActionNotice.tone === 'success'
                            ? 'rgba(16,185,129,0.08)'
                            : 'rgba(239,68,68,0.08)',
                        color: headerActionNotice.tone === 'success' ? '#047857' : '#b91c1c',
                        display: 'grid',
                        gap: 4,
                    }}
                >
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
                        <strong>{headerActionNotice.title}</strong>
                        <button
                            type="button"
                            className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed"
                            onClick={() => setHeaderActionNotice(null)}
                        >
                            收起
                        </button>
                    </div>
                    <div style={{ fontSize: 12, whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
                        {headerActionNotice.message}
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
                            background: '#1e2330',
                            border: '1px solid rgba(255,255,255,0.1)',
                            borderRadius: 10,
                            boxShadow: '0 20px 70px rgba(0,0,0,0.6)',
                            display: 'grid',
                            gridTemplateRows: 'auto auto 1fr',
                            color: '#e2e8f0',
                        }}
                        onClick={(event) => event.stopPropagation()}
                    >
                        <div style={{ padding: '10px 12px 0', fontSize: 12, color: '#94a3b8' }}>
                            命令面板（Ctrl/Cmd + K，↑/↓选择，Enter执行，Ctrl/Cmd + Shift + P 预览）
                        </div>
                        <div style={{ padding: '8px 12px 10px' }}>
                            <input
                                ref={quickInputRef}
                                type="text"
                                className="text-base font-semibold text-[var(--color-text-primary)] bg-[var(--color-surface)] border border-[var(--color-primary)] rounded px-2 py-1 outline-none min-w-[200px]"
                                style={{ width: '100%', minWidth: 0, background: 'rgba(255,255,255,0.06)', color: '#e2e8f0', border: '1px solid rgba(255,255,255,0.15)' }}
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
                                <div style={{ padding: '20px 12px', fontSize: 13, color: '#94a3b8' }}>
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
                                        className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed"
                                        style={{
                                            width: '100%',
                                            justifyContent: 'flex-start',
                                            background: index === quickActiveIndex ? 'rgba(99,130,255,0.2)' : undefined,
                                            borderColor: index === quickActiveIndex ? 'rgba(99,130,255,0.5)' : undefined,
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
                                                    background: 'rgba(56,189,248,0.15)',
                                                    color: '#38bdf8',
                                                }}
                                                >
                                                    最近
                                                </span>
                                            ) : null}
                                        </span>
                                        {item.hotkey ? (
                                            <span style={{ marginLeft: 'auto', fontSize: 11, color: '#94a3b8' }}>
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
                isOwner={permissions.isOwner}
            />

            <PublishResultModal
                open={publishModalOpen}
                onClose={() => setPublishModalOpen(false)}
                publishInfo={publishInfo}
                isOwner={permissions.isOwner}
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

            <ScreenSharePanel
                open={showSharePanel}
                screenId={id}
                onClose={() => setShowSharePanel(false)}
                isOwner={permissions.isOwner}
            />

            <Modal
                open={showSaveTemplateDialog}
                onCancel={() => setShowSaveTemplateDialog(false)}
                title="保存为模板"
                width={520}
                footer={(
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                        <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => setShowSaveTemplateDialog(false)}>
                            取消
                        </button>
                        <button
                            type="button"
                            className="header-btn save-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-primary)] rounded-md bg-[var(--color-primary)] text-white text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 hover:bg-[var(--color-primary-dark)] disabled:opacity-50 disabled:cursor-not-allowed"
                            onClick={() => void handleSubmitSaveAsTemplate()}
                            disabled={isSavingTemplate || !templateForm.name.trim()}
                        >
                            {isSavingTemplate ? '保存中...' : '确认保存'}
                        </button>
                    </div>
                )}
            >
                <div style={{ display: 'grid', gap: 12 }}>
                    <label style={{ display: 'grid', gap: 6 }}>
                        <span>模板名称</span>
                        <input
                            className="text-base font-semibold text-[var(--color-text-primary)] bg-[var(--color-surface)] border border-[var(--color-primary)] rounded px-2 py-1 outline-none min-w-[200px]"
                            value={templateForm.name}
                            onChange={(event) => setTemplateForm((current) => ({ ...current, name: event.target.value }))}
                            placeholder="输入模板名称"
                        />
                    </label>
                    <label style={{ display: 'grid', gap: 6 }}>
                        <span>模板描述</span>
                        <textarea
                            className="text-base font-semibold text-[var(--color-text-primary)] bg-[var(--color-surface)] border border-[var(--color-primary)] rounded px-2 py-1 outline-none min-w-[200px]"
                            value={templateForm.description}
                            onChange={(event) => setTemplateForm((current) => ({ ...current, description: event.target.value }))}
                            placeholder="输入模板描述"
                            rows={4}
                            style={{ resize: 'vertical' }}
                        />
                    </label>
                    <label style={{ display: 'grid', gap: 6 }}>
                        <span>可见范围</span>
                        <select
                            className="px-2.5 py-[7px] border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium min-w-[110px] w-full focus:outline-none focus:border-[var(--color-primary)]"
                            value={templateForm.visibilityScope}
                            onChange={(event) => setTemplateForm((current) => ({
                                ...current,
                                visibilityScope: event.target.value as 'personal' | 'team' | 'global',
                            }))}
                        >
                            <option value="personal">个人</option>
                            <option value="team">团队</option>
                            <option value="global">全局</option>
                        </select>
                    </label>
                </div>
            </Modal>

            <Modal
                open={showExploreSessionDialog}
                onCancel={() => setShowExploreSessionDialog(false)}
                title="沉淀分析会话"
                width={720}
                footer={(
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                        <button type="button" className="header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed" onClick={() => setShowExploreSessionDialog(false)}>
                            取消
                        </button>
                        <button
                            type="button"
                            className="header-btn save-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-primary)] rounded-md bg-[var(--color-primary)] text-white text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 hover:bg-[var(--color-primary-dark)] disabled:opacity-50 disabled:cursor-not-allowed"
                            onClick={() => void handleSubmitCreateExploreSession()}
                            disabled={!exploreSessionForm.title.trim()}
                        >
                            创建会话
                        </button>
                    </div>
                )}
            >
                <div style={{ display: 'grid', gap: 12 }}>
                    <label style={{ display: 'grid', gap: 6 }}>
                        <span>会话标题</span>
                        <input
                            className="text-base font-semibold text-[var(--color-text-primary)] bg-[var(--color-surface)] border border-[var(--color-primary)] rounded px-2 py-1 outline-none min-w-[200px]"
                            value={exploreSessionForm.title}
                            onChange={(event) => setExploreSessionForm((current) => ({ ...current, title: event.target.value }))}
                            placeholder="输入会话标题"
                        />
                    </label>
                    <label style={{ display: 'grid', gap: 6 }}>
                        <span>问题描述</span>
                        <textarea
                            className="text-base font-semibold text-[var(--color-text-primary)] bg-[var(--color-surface)] border border-[var(--color-primary)] rounded px-2 py-1 outline-none min-w-[200px]"
                            value={exploreSessionForm.question}
                            onChange={(event) => setExploreSessionForm((current) => ({ ...current, question: event.target.value }))}
                            rows={3}
                            style={{ resize: 'vertical' }}
                        />
                    </label>
                    <label style={{ display: 'grid', gap: 6 }}>
                        <span>阶段结论</span>
                        <textarea
                            className="text-base font-semibold text-[var(--color-text-primary)] bg-[var(--color-surface)] border border-[var(--color-primary)] rounded px-2 py-1 outline-none min-w-[200px]"
                            value={exploreSessionForm.conclusion}
                            onChange={(event) => setExploreSessionForm((current) => ({ ...current, conclusion: event.target.value }))}
                            rows={3}
                            style={{ resize: 'vertical' }}
                        />
                    </label>
                    <label style={{ display: 'grid', gap: 6 }}>
                        <span>标签</span>
                        <input
                            className="text-base font-semibold text-[var(--color-text-primary)] bg-[var(--color-surface)] border border-[var(--color-primary)] rounded px-2 py-1 outline-none min-w-[200px]"
                            value={exploreSessionForm.tagsInput}
                            onChange={(event) => setExploreSessionForm((current) => ({ ...current, tagsInput: event.target.value }))}
                            placeholder="逗号分隔，如：大屏,复盘"
                        />
                    </label>
                </div>
            </Modal>

            {showLinkageGraph && (
                <LinkageGraphPanel
                    config={config}
                    selectedIds={selectedIds}
                    onClose={() => setShowLinkageGraph(false)}
                />
            )}
        </>
    );
}
