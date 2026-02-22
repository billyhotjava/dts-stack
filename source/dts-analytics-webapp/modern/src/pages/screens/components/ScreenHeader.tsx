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
    const [previewDeviceMode, setPreviewDeviceMode] = useState<'auto' | 'pc' | 'tablet' | 'mobile'>('auto');
    const [isSavingTemplate, setIsSavingTemplate] = useState(false);
    const [conflictLoading, setConflictLoading] = useState(false);
    const [lastConflict, setLastConflict] = useState<ScreenUpdateConflict | null>(null);
    const [versionDiff, setVersionDiff] = useState<ScreenVersionDiff | null>(null);
    const [versionCandidates, setVersionCandidates] = useState<ScreenVersion[]>([]);
    const [editLock, setEditLock] = useState<ScreenEditLock | null>(null);
    const [lockErrorText, setLockErrorText] = useState<string | null>(null);
    const [publishNotice, setPublishNotice] = useState<PublishNotice | null>(null);
    const importInputRef = useRef<HTMLInputElement | null>(null);
    const menuContainerRef = useRef<HTMLDivElement | null>(null);
    const [activeMenu, setActiveMenu] = useState<'more' | null>(null);
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
            setShowVersionRollbackPanel(true);
        } catch (error) {
            console.error('Failed to rollback version:', error);
            const message = handleLockHttpError(error, '回滚失败');
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
            window.open(`/analytics/screens/${id}/preview${suffix}`, '_blank');
        } else {
            alert('请先保存大屏后再预览');
        }
    };

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
        const popup = window.open(url, '_blank');
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
            const url = `${window.location.origin}/analytics/public/screen/${uuid}`;
            const copied = await writeTextToClipboard(url);
            alert(copied ? '分享链接已复制到剪贴板' : `复制失败，请手工复制：\n${url}`);
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
        void Promise.resolve(action()).catch((error) => {
            console.error('Failed to execute header menu action:', error);
        });
    }, []);

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
                        <HeaderMenu
                            label={`更多${cycleWarnings.length > 0 ? `(${cycleWarnings.length})` : ''}`}
                            open={activeMenu === 'more'}
                            onToggle={() => setActiveMenu((prev) => (prev === 'more' ? null : 'more'))}
                        >
                            <div className="header-menu-section">
                                <div className="header-menu-section-title">设计</div>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => executeMenuAction(handleCreateExploreSession)}
                                    disabled={!permissions.canRead}
                                    title="将当前大屏沉淀为可复盘分析会话"
                                >
                                    沉淀会话
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => executeMenuAction(() => setShowVariableManager(true))}
                                    title="全局变量与联动"
                                >
                                    变量管理
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => executeMenuAction(() => setShowInteractionDebugPanel(true))}
                                    title="联动与变量事件调试"
                                >
                                    联动调试
                                </button>
                                {id && permissions.canRead && (
                                    <button
                                        type="button"
                                        className="header-btn"
                                        onClick={() => executeMenuAction(() => setShowCollaborationPanel(true))}
                                        title="评论/批注轻协作"
                                    >
                                        协作
                                    </button>
                                )}
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => executeMenuAction(handleSaveAsTemplate)}
                                    disabled={isSavingTemplate || !permissions.canEdit}
                                    title="保存为团队模板"
                                >
                                    {isSavingTemplate ? '存模板中...' : '保存模板'}
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => executeMenuAction(handleOpenImport)}
                                    title="导入 JSON"
                                >
                                    导入JSON
                                </button>
                            </div>

                            <div className="header-menu-section">
                                <div className="header-menu-section-title">治理</div>
                                {id && permissions.canRead && (
                                    <button
                                        type="button"
                                        className="header-btn"
                                        onClick={() => executeMenuAction(() => setShowEditLockPanel(true))}
                                        title="编辑锁状态与手工接管"
                                    >
                                        编辑锁{lockedByOther ? '(占用)' : (editLock?.mine ? '(我)' : '')}
                                    </button>
                                )}
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => executeMenuAction(() => setShowCachePanel(true))}
                                    title="缓存命中率观测"
                                >
                                    缓存观测
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => executeMenuAction(() => setShowCompliancePanel(true))}
                                    title="企业级合规策略与审计报表"
                                >
                                    合规
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => executeMenuAction(() => setShowHealthPanel(true))}
                                    title="兼容性与性能基线体检"
                                >
                                    体检
                                </button>
                                {id && permissions.canManage && (
                                    <>
                                        <button
                                            type="button"
                                            className="header-btn"
                                            onClick={() => executeMenuAction(() => setShowAclPanel(true))}
                                            title="大屏 ACL 权限"
                                        >
                                            权限
                                        </button>
                                        <button
                                            type="button"
                                            className="header-btn"
                                            onClick={() => executeMenuAction(() => setShowAuditPanel(true))}
                                            title="审计日志链路"
                                        >
                                            审计
                                        </button>
                                    </>
                                )}
                                {id && (
                                    <button
                                        type="button"
                                        className="header-btn"
                                        onClick={() => executeMenuAction(() => setShowSharePolicyPanel(true))}
                                        disabled={!permissions.canPublish}
                                        title="配置过期/口令/IP白名单"
                                    >
                                        分享策略
                                    </button>
                                )}
                                {id && (
                                    <button
                                        type="button"
                                        className="header-btn"
                                        onClick={() => executeMenuAction(handleShare)}
                                        disabled={isSharing || !permissions.canPublish}
                                        title="生成公开链接并复制"
                                    >
                                        {isSharing ? '分享中...' : '分享链接'}
                                    </button>
                                )}
                            </div>

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
                                {id && (
                                    <>
                                        <button
                                            type="button"
                                            className="header-btn"
                                            onClick={() => executeMenuAction(handleVersionHistory)}
                                            disabled={isLoadingVersions || !permissions.canPublish}
                                            title="版本历史"
                                        >
                                            {isLoadingVersions ? '加载中...' : '版本历史'}
                                        </button>
                                        <button
                                            type="button"
                                            className="header-btn"
                                            onClick={() => executeMenuAction(handleVersionCompare)}
                                            disabled={isLoadingVersions || !permissions.canRead}
                                            title="版本差异摘要"
                                        >
                                            版本对比
                                        </button>
                                    </>
                                )}
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => executeMenuAction(handleExportJson)}
                                    title="导出 JSON"
                                >
                                    导出JSON
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => executeMenuAction(handleExportPng)}
                                    title="导出 PNG"
                                >
                                    导出PNG
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => executeMenuAction(handleExportPdf)}
                                    title="导出 PDF"
                                >
                                    导出PDF
                                </button>
                            </div>
                        </HeaderMenu>
                    </div>
                    <div className="screen-header-primary-actions">
                        <button
                            type="button"
                            className="header-btn preview-btn"
                            onClick={handlePreview}
                            title={`预览大屏（${previewDeviceMode === 'auto' ? '自动' : previewDeviceMode}）`}
                        >
                            预览
                        </button>
                        {id && (
                            <button
                                type="button"
                                className="header-btn"
                                onClick={handlePublish}
                                disabled={isPublishing || !permissions.canPublish || lockedByOther}
                                title={lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '发布当前草稿'}
                            >
                                {isPublishing ? '发布中...' : '发布'}
                            </button>
                        )}
                        <button
                            type="button"
                            className="header-btn save-btn"
                            onClick={handleSave}
                            disabled={isSaving || !permissions.canEdit || lockedByOther}
                            title={lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '保存草稿'}
                        >
                            {isSaving ? '保存中...' : '保存'}
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

            <ScreenSharePolicyPanel
                open={showSharePolicyPanel}
                screenId={id}
                onClose={() => setShowSharePolicyPanel(false)}
            />
        </>
    );
}
