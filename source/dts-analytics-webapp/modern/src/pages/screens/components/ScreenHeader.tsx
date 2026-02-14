import { useState, useCallback, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useScreen } from '../ScreenContext';
import { detectInteractionCycles } from '../interactionGraph';
import type { ScreenGlobalVariable } from '../types';
import { analyticsApi, type ScreenDetail } from '../../../api/analyticsApi';
import { GlobalVariableManager } from './GlobalVariableManager';

function toGlobalVariables(input: unknown): ScreenGlobalVariable[] {
    if (!Array.isArray(input)) return [];
    return input
        .map((item) => {
            if (!item || typeof item !== 'object') return null;
            const row = item as Record<string, unknown>;
            const key = typeof row.key === 'string' ? row.key.trim() : '';
            if (!key) return null;
            const label = typeof row.label === 'string' ? row.label : key;
            const type = row.type === 'number' || row.type === 'date' ? row.type : 'string';
            const defaultValue = typeof row.defaultValue === 'string' ? row.defaultValue : '';
            const description = typeof row.description === 'string' ? row.description : undefined;
            return { key, label, type, defaultValue, description };
        })
        .filter((x): x is ScreenGlobalVariable => x !== null);
}

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

    const cycleWarnings = useMemo(() => detectInteractionCycles(config), [config]);

    const applyScreenDetail = useCallback((screen: ScreenDetail) => {
        const backgroundColor = screen.backgroundColor || '#0d1b2a';
        loadConfig({
            id: String(screen.id),
            name: screen.name || '未命名大屏',
            description: screen.description || '',
            width: screen.width || 1920,
            height: screen.height || 1080,
            backgroundColor,
            backgroundImage: screen.backgroundImage || undefined,
            theme: screen.theme as import('../types').ScreenTheme | undefined,
            components: (screen.components || []).map(c => ({
                ...c,
                type: c.type as import('../types').ComponentType,
                dataSource: c.dataSource as import('../types').DataSourceConfig | undefined,
                interaction: c.interaction as import('../types').ComponentInteractionConfig | undefined,
            })),
            globalVariables: toGlobalVariables((screen as Record<string, unknown>).globalVariables),
        });
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
            const payload = {
                name: config.name,
                description: config.description,
                width: config.width,
                height: config.height,
                backgroundColor: config.backgroundColor,
                backgroundImage: config.backgroundImage,
                theme: config.theme,
                components: config.components,
                globalVariables: config.globalVariables ?? [],
            };

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
            alert('保存失败');
        }
    }, [saveScreen]);

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
            alert(`发布成功，版本 v${result.version?.versionNo ?? '-'}`);
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

    const handlePreview = () => {
        if (id) {
            window.open(`/analytics/screens/${id}/preview`, '_blank');
        } else {
            alert('请先保存大屏后再预览');
        }
    };

    const handleShare = async () => {
        if (!id || isSharing) return;
        setIsSharing(true);
        try {
            const { uuid } = await analyticsApi.createScreenPublicLink(id);
            const url = `${window.location.origin}/analytics/public/screen/${uuid}`;
            await navigator.clipboard.writeText(url);
            alert('分享链接已复制到剪贴板');
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
                    <button className="header-btn back-btn" onClick={handleBack} title="返回列表">
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
                        className="header-btn"
                        onClick={() => setShowVariableManager(true)}
                        title="全局变量与联动"
                    >
                        变量
                        {cycleWarnings.length > 0 ? `(${cycleWarnings.length})` : ''}
                    </button>
                    {id && (
                        <>
                            <button
                                className="header-btn"
                                onClick={handleVersionHistory}
                                disabled={isLoadingVersions}
                                title="版本历史"
                            >
                                {isLoadingVersions ? '加载中...' : '📝 版本'}
                            </button>
                            <button
                                className="header-btn"
                                onClick={handlePublish}
                                disabled={isPublishing}
                                title="发布当前草稿"
                            >
                                {isPublishing ? '发布中...' : '🚀 发布'}
                            </button>
                        </>
                    )}
                    {id && (
                        <button
                            className="header-btn share-btn"
                            onClick={handleShare}
                            disabled={isSharing}
                            title="分享大屏"
                        >
                            {isSharing ? '分享中...' : '🔗 分享'}
                        </button>
                    )}
                    <button
                        className="header-btn preview-btn"
                        onClick={handlePreview}
                        title="预览大屏"
                    >
                        👁️ 预览
                    </button>
                    <button
                        className="header-btn save-btn"
                        onClick={handleSave}
                        disabled={isSaving}
                        title="保存草稿"
                    >
                        {isSaving ? '保存中...' : '💾 保存'}
                    </button>
                </div>
            </div>

            <GlobalVariableManager
                open={showVariableManager}
                variables={config.globalVariables ?? []}
                cycleWarnings={cycleWarnings}
                onClose={() => setShowVariableManager(false)}
                onChange={(next) => updateConfig({ globalVariables: next })}
            />
        </>
    );
}
