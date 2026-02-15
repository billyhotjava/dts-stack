import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useScreen } from '../ScreenContext';
import { detectInteractionCycles } from '../interactionGraph';
import { analyticsApi, type ScreenDetail } from '../../../api/analyticsApi';
import { GlobalVariableManager } from './GlobalVariableManager';
import { CacheObservabilityPanel } from './CacheObservabilityPanel';
import { ScreenCompliancePanel } from './ScreenCompliancePanel';
import { ScreenAclPanel } from './ScreenAclPanel';
import { ScreenAuditPanel } from './ScreenAuditPanel';
import { ScreenSharePolicyPanel } from './ScreenSharePolicyPanel';
import { ScreenHealthPanel } from './ScreenHealthPanel';
import { InteractionDebugPanel } from './InteractionDebugPanel';
import { buildScreenPayload, normalizeScreenConfig, validateScreenPayload } from '../specV2';
import { resolveScreenTheme } from '../screenThemes';
import { writeTextToClipboard } from '../../../hooks/clipboard';

export function ScreenHeader() {
    const navigate = useNavigate();
    const { id } = useParams<{ id: string }>();
    const { state, updateConfig, loadConfig, isSaving, setIsSaving } = useScreen();
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
    const [isSavingTemplate, setIsSavingTemplate] = useState(false);
    const importInputRef = useRef<HTMLInputElement | null>(null);
    const [permissions, setPermissions] = useState({
        canRead: true,
        canEdit: true,
        canPublish: true,
        canManage: true,
    });

    const cycleWarnings = useMemo(() => detectInteractionCycles(config), [config]);

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
            const payload = buildScreenPayload(config);
            const validation = validateScreenPayload(payload);
            if (validation.errors.length > 0) {
                throw new Error(`配置校验失败：${validation.errors.join('；')}`);
            }
            if (validation.warnings.length > 0) {
                console.warn('[screen-spec-v2] save payload warnings:', validation.warnings);
            }

            if (id) {
                await analyticsApi.updateScreen(id, payload);
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
    }, [config, id, isSaving, navigate, setIsSaving]);

    const handleSave = useCallback(async () => {
        try {
            await saveScreen();
        } catch (error) {
            console.error('Failed to save screen:', error);
            const message = error instanceof Error && error.message ? error.message : '保存失败';
            alert(message);
        }
    }, [saveScreen]);

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
            alert('存模板失败');
        } finally {
            setIsSavingTemplate(false);
        }
    }, [config.description, config.name, isSavingTemplate, saveScreen]);

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
            alert('发布成功，版本 v' + versionNo + warmupText);
        } catch (error) {
            console.error('Failed to publish screen:', error);
            alert('发布失败');
        } finally {
            setIsPublishing(false);
        }
    }, [isPublishing, saveScreen]);

    const handleVersionHistory = useCallback(async () => {
        if (!id || isLoadingVersions) return;

        setIsLoadingVersions(true);
        try {
            const versions = await analyticsApi.listScreenVersions(id);
            if (!versions.length) {
                alert('当前没有已发布版本');
                return;
            }

            const versionLines = versions
                .map(v => {
                    const tag = v.currentPublished ? ' [当前发布]' : '';
                    const ts = v.publishedAt || v.createdAt || '-';
                    return `ID=${v.id} | v${v.versionNo ?? '-'} | ${ts}${tag}`;
                })
                .join('\n');

            const input = window.prompt(
                `版本列表：\n${versionLines}\n\n输入要回滚的版本ID（留空仅查看）:`,
            );
            const targetId = (input || '').trim();
            if (!targetId) return;

            if (!/^\d+$/.test(targetId)) {
                alert('版本ID格式不正确');
                return;
            }

            if (!window.confirm(`确认回滚到版本 ID=${targetId} 吗？`)) {
                return;
            }

            const result = await analyticsApi.rollbackScreenVersion(id, targetId);
            if (result?.screen) {
                applyScreenDetail(result.screen);
            }
            alert('回滚成功，已切换草稿与发布版本');
        } catch (error) {
            console.error('Failed to rollback version:', error);
            alert('回滚失败');
        } finally {
            setIsLoadingVersions(false);
        }
    }, [applyScreenDetail, id, isLoadingVersions]);

    const handleVersionCompare = useCallback(async () => {
        if (!id || isLoadingVersions) return;
        setIsLoadingVersions(true);
        try {
            const versions = await analyticsApi.listScreenVersions(id);
            if (!versions || versions.length < 2) {
                alert('至少需要两个版本才能对比');
                return;
            }
            const lines = versions
                .map(v => `ID=${v.id} | v${v.versionNo ?? '-'} | ${v.publishedAt || v.createdAt || '-'}`)
                .join('\n');
            const input = (window.prompt(
                `版本列表：\n${lines}\n\n输入对比版本ID（格式：from,to）`,
                `${versions[1]?.id || ''},${versions[0]?.id || ''}`,
            ) || '').trim();
            if (!input) return;
            const pair = input.split(',').map(item => item.trim()).filter(Boolean);
            if (pair.length !== 2) {
                alert('请输入 from,to 两个版本ID');
                return;
            }
            const diff = await analyticsApi.compareScreenVersions(id, pair[0], pair[1]);
            const s = diff.summary || {};
            const summary = [
                `组件数: ${s.componentCountFrom ?? '-'} -> ${s.componentCountTo ?? '-'}`,
                `新增组件: ${s.addedComponents ?? 0}，移除组件: ${s.removedComponents ?? 0}`,
                `新增类型: ${s.addedComponentTypes ?? 0}，移除类型: ${s.removedComponentTypes ?? 0}`,
                `新增变量: ${s.addedVariables ?? 0}，移除变量: ${s.removedVariables ?? 0}`,
            ].join('\n');
            alert(`版本差异摘要：\n${summary}`);
        } catch (error) {
            console.error('Failed to compare versions:', error);
            alert('版本对比失败');
        } finally {
            setIsLoadingVersions(false);
        }
    }, [id, isLoadingVersions]);

    const handlePreview = () => {
        if (id) {
            window.open(`/analytics/screens/${id}/preview`, '_blank');
        } else {
            alert('请先保存大屏后再预览');
        }
    };

    const handleExportJson = () => {
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
    };

    const captureCanvasAsPngDataUrl = useCallback(async (): Promise<string> => {
        const canvasEl = document.querySelector('.canvas') as HTMLElement | null;
        if (!canvasEl) {
            throw new Error('canvas not found');
        }
        const width = Math.max(1, Math.round(config.width || 1920));
        const height = Math.max(1, Math.round(config.height || 1080));
        const serialized = new XMLSerializer().serializeToString(canvasEl);
        const svg = [
            `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">`,
            `<foreignObject width="100%" height="100%">`,
            serialized,
            `</foreignObject>`,
            `</svg>`,
        ].join('');
        const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        try {
            const image = await new Promise<HTMLImageElement>((resolve, reject) => {
                const img = new Image();
                img.onload = () => resolve(img);
                img.onerror = reject;
                img.src = url;
            });
            const out = document.createElement('canvas');
            out.width = width;
            out.height = height;
            const ctx = out.getContext('2d');
            if (!ctx) {
                throw new Error('context unavailable');
            }
            ctx.drawImage(image, 0, 0, width, height);
            return out.toDataURL('image/png');
        } finally {
            URL.revokeObjectURL(url);
        }
    }, [config.height, config.width]);

    const handleExportPng = async () => {
        try {
            const dataUrl = await captureCanvasAsPngDataUrl();
            const link = document.createElement('a');
            link.href = dataUrl;
            link.download = `${config.name || 'screen'}.png`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error('Failed to export png:', error);
            alert('PNG 导出失败，请先预览后截图');
        }
    };

    const handleExportPdf = async () => {
        try {
            const dataUrl = await captureCanvasAsPngDataUrl();
            const popup = window.open('', '_blank');
            if (!popup) {
                alert('请允许弹窗后重试 PDF 导出');
                return;
            }
            popup.document.write(`<html><head><title>${config.name || 'screen'}</title></head><body style="margin:0"><img src="${dataUrl}" style="width:100%;height:auto;display:block"/></body></html>`);
            popup.document.close();
            popup.focus();
            popup.print();
        } catch (error) {
            console.error('Failed to export pdf:', error);
            alert('PDF 导出失败，请使用预览页面浏览器打印');
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

    const handleBack = () => {
        navigate('/screens');
    };

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
                    <button
                        type="button"
                        className="header-btn"
                        onClick={() => setShowVariableManager(true)}
                        disabled={!permissions.canRead}
                        title="全局变量与联动"
                    >
                        变量
                        {cycleWarnings.length > 0 ? `(${cycleWarnings.length})` : ''}
                    </button>
                    <button
                        type="button"
                        className="header-btn"
                        onClick={() => setShowInteractionDebugPanel(true)}
                        disabled={!permissions.canRead}
                        title="联动与变量事件调试"
                    >
                        联动调试
                    </button>
                    <button
                        type="button"
                        className="header-btn"
                        onClick={() => setShowCachePanel(true)}
                        disabled={!permissions.canRead}
                        title="缓存命中率观测"
                    >
                        缓存观测
                    </button>
                    <button
                        type="button"
                        className="header-btn"
                        onClick={() => setShowCompliancePanel(true)}
                        disabled={!permissions.canRead}
                        title="企业级合规策略与审计报表"
                    >
                        合规
                    </button>
                    <button
                        type="button"
                        className="header-btn"
                        onClick={() => setShowHealthPanel(true)}
                        disabled={!permissions.canRead}
                        title="兼容性与性能基线体检"
                    >
                        体检
                    </button>
                    {id && permissions.canManage && (
                        <>
                            <button
                                type="button"
                                className="header-btn"
                                onClick={() => setShowAclPanel(true)}
                                title="大屏 ACL 权限"
                            >
                                权限
                            </button>
                            <button
                                type="button"
                                className="header-btn"
                                onClick={() => setShowAuditPanel(true)}
                                title="审计日志链路"
                            >
                                审计
                            </button>
                        </>
                    )}
                    <button
                        type="button"
                        className="header-btn"
                        onClick={handleSaveAsTemplate}
                        disabled={isSavingTemplate || !permissions.canEdit}
                        title="保存为团队模板"
                    >
                        {isSavingTemplate ? '存模板中...' : '存模板'}
                    </button>
                    {id && (
                        <>
                            <button
                                type="button"
                                className="header-btn"
                                onClick={handleVersionHistory}
                                disabled={isLoadingVersions || !permissions.canPublish}
                                title="版本历史"
                            >
                                {isLoadingVersions ? '加载中...' : '📝 版本'}
                            </button>
                            <button
                                type="button"
                                className="header-btn"
                                onClick={handleVersionCompare}
                                disabled={isLoadingVersions || !permissions.canRead}
                                title="版本差异摘要"
                            >
                                对比
                            </button>
                            <button
                                type="button"
                                className="header-btn"
                                onClick={handlePublish}
                                disabled={isPublishing || !permissions.canPublish}
                                title="发布当前草稿"
                            >
                                {isPublishing ? '发布中...' : '🚀 发布'}
                            </button>
                        </>
                    )}
                    {id && (
                        <>
                            <button
                                type="button"
                                className="header-btn"
                                onClick={() => setShowSharePolicyPanel(true)}
                                disabled={!permissions.canPublish}
                                title="配置过期/口令/IP白名单"
                            >
                                分享策略
                            </button>
                            <button
                                type="button"
                                className="header-btn share-btn"
                                onClick={handleShare}
                                disabled={isSharing || !permissions.canPublish}
                                title="分享大屏"
                            >
                                {isSharing ? '分享中...' : '🔗 分享'}
                            </button>
                        </>
                    )}
                    <button
                        type="button"
                        className="header-btn preview-btn"
                        onClick={handlePreview}
                        title="预览大屏"
                    >
                        👁️ 预览
                    </button>
                    <button
                        type="button"
                        className="header-btn"
                        onClick={handleExportJson}
                        title="导出 JSON"
                    >
                        JSON
                    </button>
                    <button
                        type="button"
                        className="header-btn"
                        onClick={handleExportPng}
                        title="导出 PNG"
                    >
                        PNG
                    </button>
                    <button
                        type="button"
                        className="header-btn"
                        onClick={handleExportPdf}
                        title="导出 PDF"
                    >
                        PDF
                    </button>
                    <button
                        type="button"
                        className="header-btn"
                        onClick={handleOpenImport}
                        title="导入 JSON"
                    >
                        导入
                    </button>
                    <button
                        type="button"
                        className="header-btn save-btn"
                        onClick={handleSave}
                        disabled={isSaving || !permissions.canEdit}
                        title="保存草稿"
                    >
                        {isSaving ? '保存中...' : '💾 保存'}
                    </button>
                    <input
                        ref={importInputRef}
                        type="file"
                        accept="application/json,.json"
                        style={{ display: 'none' }}
                        onChange={handleImportJson}
                    />
                </div>
            </div>

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

            <ScreenSharePolicyPanel
                open={showSharePolicyPanel}
                screenId={id}
                onClose={() => setShowSharePolicyPanel(false)}
            />
        </>
    );
}
