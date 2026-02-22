import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { analyticsApi, ScreenListItem, type ScreenAiGenerationResponse } from '../../api/analyticsApi';
import { writeTextToClipboard } from '../../hooks/clipboard';
import { TemplateGallery, type TemplateSelection } from './components';
import { createConfigFromTemplate } from './screenTemplates';
import { buildScreenPayload, normalizeScreenConfig } from './specV2';
import '../page.css';

export default function ScreensPage() {
    const navigate = useNavigate();
    const [screens, setScreens] = useState<ScreenListItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [showTemplateGallery, setShowTemplateGallery] = useState(false);
    const [sharingId, setSharingId] = useState<string | number | null>(null);
    const [savingTemplateId, setSavingTemplateId] = useState<string | number | null>(null);

    const [showAiGenerator, setShowAiGenerator] = useState(false);
    const [aiPrompt, setAiPrompt] = useState('');
    const [aiLoading, setAiLoading] = useState(false);
    const [aiRefining, setAiRefining] = useState(false);
    const [aiCreating, setAiCreating] = useState(false);
    const [aiRefinePrompt, setAiRefinePrompt] = useState('');
    const [aiRefineMode, setAiRefineMode] = useState<'apply' | 'suggest'>('apply');
    const [aiResult, setAiResult] = useState<ScreenAiGenerationResponse | null>(null);
    const [aiContextHistory, setAiContextHistory] = useState<string[]>([]);

    const loadScreens = useCallback(() => {
        setLoading(true);
        analyticsApi.listScreens()
            .then((data) => {
                setScreens(data);
                setLoading(false);
            })
            .catch((err) => {
                console.error('Failed to load screens:', err);
                setError('加载大屏列表失败');
                setLoading(false);
            });
    }, []);

    useEffect(() => {
        loadScreens();
    }, [loadScreens]);

    const handleCreate = () => {
        setShowTemplateGallery(true);
    };

    const handleOpenAiGenerator = () => {
        setAiPrompt('生成一个面向运营的周报大屏，包含趋势、结构占比、区域排名和明细表');
        setAiRefinePrompt('改成三列布局，增加区域筛选，切换为浅色商务风格，刷新30秒并放大字体');
        setAiRefineMode('apply');
        setAiResult(null);
        setAiContextHistory([]);
        setShowAiGenerator(true);
    };

    const handleTemplateSelect = async (selection: TemplateSelection) => {
        setShowTemplateGallery(false);

        try {
            if (selection.kind === 'asset') {
                const remoteTemplate = selection.template;
                const response = await analyticsApi.createScreenFromTemplate(remoteTemplate.id as string | number, {
                    name: (remoteTemplate.name || '未命名模板') + ' 副本',
                });
                navigate(`/screens/${response.id}/edit`);
                return;
            }

            const config = createConfigFromTemplate(selection.template);
            const response = await analyticsApi.createScreen(
                buildScreenPayload({
                    id: '',
                    ...config,
                }),
            );
            navigate(`/screens/${response.id}/edit`);
        } catch (err) {
            console.error('Failed to create screen from template:', err);
            navigate('/screens/new');
        }
    };

    const handleGenerateAi = async () => {
        if (aiLoading) return;
        const prompt = aiPrompt.trim();
        if (!prompt) {
            alert('请输入业务需求描述');
            return;
        }

        setAiLoading(true);
        try {
            const result = await analyticsApi.generateScreenSpec({
                prompt,
                width: 1920,
                height: 1080,
            });
            setAiResult(result);
            setAiContextHistory((prev) => ([...prev, `初始需求: ${prompt}`]).slice(-12));
        } catch (err) {
            console.error('Failed to generate ai screen spec:', err);
            alert('AI 生成失败');
        } finally {
            setAiLoading(false);
        }
    };

    const handleCreateFromAi = async () => {
        if (aiCreating) return;
        const spec = aiResult?.screenSpec;
        if (!spec) {
            alert('请先生成方案');
            return;
        }
        const pendingVariables = aiResult?.nl2sqlDiagnostics?.pendingVariables ?? [];
        if (pendingVariables.length > 0) {
            const preview = pendingVariables.slice(0, 6).join('、');
            const confirmed = window.confirm(
                `当前仍有 ${pendingVariables.length} 个待补参数（${preview}${pendingVariables.length > 6 ? '...' : ''}），继续创建草稿吗？`,
            );
            if (!confirmed) {
                return;
            }
        }

        setAiCreating(true);
        try {
            const normalized = normalizeScreenConfig(spec, { id: '' });
            const created = await analyticsApi.createScreen(buildScreenPayload({
                ...normalized.config,
                name: spec.name || normalized.config.name || 'AI生成大屏草稿',
                description: spec.description || normalized.config.description || 'AI自动生成',
            }));
            setShowAiGenerator(false);
            navigate(`/screens/${created.id}/edit`);
        } catch (err) {
            console.error('Failed to create screen from ai spec:', err);
            alert('创建 AI 草稿失败');
        } finally {
            setAiCreating(false);
        }
    };

    const handleRefineAi = async () => {
        if (aiRefining) return;
        const prompt = aiRefinePrompt.trim();
        const screenSpec = aiResult?.screenSpec;
        if (!prompt) {
            alert('请输入优化指令');
            return;
        }
        if (!screenSpec) {
            alert('请先生成初始方案');
            return;
        }
        setAiRefining(true);
        try {
            const result = await analyticsApi.reviseScreenSpec({
                prompt,
                screenSpec: screenSpec as Record<string, unknown>,
                context: aiContextHistory.slice(-8),
                mode: aiRefineMode,
            });
            setAiResult(result);
            setAiContextHistory((prev) => {
                const modeLabel = aiRefineMode === 'suggest' ? '建议模式' : '应用模式';
                const next = [...prev, `优化指令(${modeLabel}): ${prompt}`];
                if (Array.isArray(result.actions) && result.actions.length > 0) {
                    next.push(`执行结果: ${result.actions.join('；')}`);
                }
                return next.slice(-12);
            });
        } catch (err) {
            console.error('Failed to refine ai screen spec:', err);
            alert('AI 优化失败');
        } finally {
            setAiRefining(false);
        }
    };

    const handleCopyAiRecommendations = async () => {
        if (!aiResult) {
            alert('请先生成 AI 方案');
            return;
        }
        const payload = {
            engine: aiResult.engine || 'heuristic-v1',
            prompt: aiResult.prompt || aiPrompt.trim(),
            intent: aiResult.intent || {},
            semanticModelHints: aiResult.semanticModelHints || {},
            queryRecommendations: aiResult.queryRecommendations || [],
            sqlBlueprints: aiResult.sqlBlueprints || [],
            vizRecommendations: aiResult.vizRecommendations || [],
            semanticRecall: aiResult.semanticRecall || {},
            metricLensReferences: aiResult.metricLensReferences || [],
            nl2sqlDiagnostics: aiResult.nl2sqlDiagnostics || {},
            quality: aiResult.quality || {},
            actions: aiResult.actions || [],
        };
        const copied = await writeTextToClipboard(JSON.stringify(payload, null, 2));
        if (!copied) {
            alert('复制失败，请稍后重试');
            return;
        }
        alert('AI建议已复制到剪贴板');
    };

    const handleEdit = (id: string | number) => {
        navigate(`/screens/${id}/edit`);
    };

    const handlePreview = (id: string | number) => {
        window.open(`/analytics/screens/${id}/preview`, '_blank');
    };

    const handleShare = async (id: string | number) => {
        if (sharingId !== null) return;
        setSharingId(id);
        try {
            const { uuid } = await analyticsApi.createScreenPublicLink(id);
            const url = `${window.location.origin}/analytics/public/screen/${uuid}`;
            const copied = await writeTextToClipboard(url);
            alert(copied ? '分享链接已复制到剪贴板' : `复制失败，请手工复制：\n${url}`);
        } catch (err) {
            console.error('Failed to create public link:', err);
            alert('创建分享链接失败');
        } finally {
            setSharingId(null);
        }
    };

    const handleSaveAsTemplate = async (id: string | number, screenName?: string) => {
        if (savingTemplateId !== null) return;

        const suggestedName = `${(screenName || '未命名大屏').trim() || '未命名大屏'} 模板`;
        const name = (window.prompt('请输入模板名称', suggestedName) || '').trim();
        if (!name) {
            return;
        }

        const categoryInput = (window.prompt('模板分类（business/tech/dashboard/monitor/custom）', 'custom') || 'custom').trim();
        const category = categoryInput || 'custom';

        setSavingTemplateId(id);
        try {
            await analyticsApi.createScreenTemplateFromScreen(id, {
                name,
                category,
                tags: ['saved-from-screen'],
            });
            alert('已保存到模板资产中心');
        } catch (err) {
            console.error('Failed to create template from screen:', err);
            alert('保存模板失败');
        } finally {
            setSavingTemplateId(null);
        }
    };

    const handleDelete = async (id: string | number) => {
        if (!confirm('确定要删除这个大屏吗？')) return;

        try {
            await analyticsApi.deleteScreen(id);
            loadScreens();
        } catch (err) {
            console.error('Failed to delete screen:', err);
            alert('删除失败');
        }
    };

    const formatDate = (dateStr?: string) => {
        if (!dateStr) return '-';
        const date = new Date(dateStr);
        return date.toLocaleDateString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
        });
    };

    return (
        <div className="page-container">
            <div className="page-header">
                <h1 className="page-title">🖥️ 大屏管理</h1>
                <div style={{ display: 'flex', gap: 10 }}>
                    <button className="primary-btn" onClick={handleOpenAiGenerator}>
                        🤖 AI生成
                    </button>
                    <button className="primary-btn" onClick={handleCreate}>
                        ➕ 新建大屏
                    </button>
                </div>
            </div>

            <div className="page-content">
                {loading ? (
                    <div className="loading-state">
                        <div className="loading-spinner" />
                        <span>加载中...</span>
                    </div>
                ) : error ? (
                    <div className="error-state">
                        <span>❌ {error}</span>
                        <button onClick={loadScreens}>重试</button>
                    </div>
                ) : screens.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-state-icon">🖥️</div>
                        <div className="empty-state-text">暂无大屏</div>
                        <div className="empty-state-hint">点击"新建大屏"创建您的第一个数据大屏</div>
                        <button className="primary-btn" onClick={handleCreate}>
                            ➕ 新建大屏
                        </button>
                    </div>
                ) : (
                    <div className="screens-grid">
                        {screens.map((screen) => (
                            <div key={screen.id} className="screen-card">
                                <div
                                    className="screen-card-preview"
                                    onClick={() => handleEdit(screen.id)}
                                >
                                    <div className="screen-card-placeholder">
                                        🖥️
                                    </div>
                                    <div className="screen-card-size">
                                        {screen.width || 1920} × {screen.height || 1080}
                                    </div>
                                </div>
                                <div className="screen-card-info">
                                    <h3 className="screen-card-name">{screen.name || '未命名大屏'}</h3>
                                    <p className="screen-card-desc">
                                        {screen.description || '无描述'}
                                    </p>
                                    <div className="screen-card-meta">
                                        <span>更新: {formatDate(screen.updatedAt)}</span>
                                    </div>
                                </div>
                                <div className="screen-card-actions">
                                    <button
                                        className="action-btn edit"
                                        onClick={() => handleEdit(screen.id)}
                                        title="编辑"
                                    >
                                        ✏️
                                    </button>
                                    <button
                                        className="action-btn preview"
                                        onClick={() => handlePreview(screen.id)}
                                        title="预览"
                                    >
                                        👁️
                                    </button>
                                    <button
                                        className="action-btn share"
                                        onClick={() => handleShare(screen.id)}
                                        disabled={sharingId === screen.id}
                                        title="分享"
                                    >
                                        🔗
                                    </button>
                                    <button
                                        className="action-btn"
                                        onClick={() => handleSaveAsTemplate(screen.id, screen.name)}
                                        disabled={savingTemplateId === screen.id}
                                        title="保存为模板"
                                    >
                                        {savingTemplateId === screen.id ? '…' : '📦'}
                                    </button>
                                    <button
                                        className="action-btn delete"
                                        onClick={() => handleDelete(screen.id)}
                                        title="删除"
                                    >
                                        🗑️
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <style>{`
                .screens-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
                    gap: 20px;
                    padding: 20px;
                }
                
                .screen-card {
                    background: var(--color-surface-secondary);
                    border: 1px solid var(--color-border);
                    border-radius: 8px;
                    overflow: hidden;
                    transition: all 0.2s ease;
                }
                
                .screen-card:hover {
                    border-color: var(--color-primary);
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
                }
                
                .screen-card-preview {
                    position: relative;
                    height: 160px;
                    background: #0d1b2a;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    cursor: pointer;
                }
                
                .screen-card-placeholder {
                    font-size: 48px;
                    opacity: 0.5;
                }
                
                .screen-card-size {
                    position: absolute;
                    bottom: 8px;
                    right: 8px;
                    padding: 4px 8px;
                    background: rgba(0, 0, 0, 0.6);
                    color: white;
                    border-radius: 4px;
                    font-size: 11px;
                }
                
                .screen-card-info {
                    padding: 16px;
                }
                
                .screen-card-name {
                    margin: 0 0 8px 0;
                    font-size: 16px;
                    font-weight: 600;
                    color: var(--color-text-primary);
                }
                
                .screen-card-desc {
                    margin: 0 0 8px 0;
                    font-size: 12px;
                    color: var(--color-text-secondary);
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }
                
                .screen-card-meta {
                    font-size: 11px;
                    color: var(--color-text-tertiary);
                }
                
                .screen-card-actions {
                    display: flex;
                    gap: 8px;
                    padding: 12px 16px;
                    border-top: 1px solid var(--color-border);
                }
                
                .action-btn {
                    flex: 1;
                    padding: 8px;
                    border: 1px solid var(--color-border);
                    border-radius: 6px;
                    background: var(--color-surface);
                    cursor: pointer;
                    font-size: 14px;
                    transition: all 0.2s ease;
                }
                
                .action-btn:hover {
                    border-color: var(--color-primary);
                    background: var(--color-primary-light);
                }
                
                .action-btn.delete:hover {
                    border-color: #ef4444;
                    background: rgba(239, 68, 68, 0.1);
                }
                
                .loading-state, .error-state {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    padding: 60px;
                    gap: 16px;
                }
                
                .loading-spinner {
                    width: 32px;
                    height: 32px;
                    border: 3px solid var(--color-border);
                    border-top-color: var(--color-primary);
                    border-radius: 50%;
                    animation: spin 1s linear infinite;
                }

                .ai-modal-overlay {
                    position: fixed;
                    inset: 0;
                    background: rgba(10, 18, 32, 0.6);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    z-index: 1400;
                }

                .ai-modal {
                    width: min(920px, 92vw);
                    max-height: 86vh;
                    overflow: auto;
                    background: #0f172a;
                    border: 1px solid rgba(148, 163, 184, 0.25);
                    border-radius: 12px;
                    box-shadow: 0 24px 80px rgba(2, 6, 23, 0.45);
                    color: #e2e8f0;
                }

                .ai-modal-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    padding: 16px 20px;
                    border-bottom: 1px solid rgba(148, 163, 184, 0.2);
                }

                .ai-modal-body {
                    padding: 18px 20px;
                    display: grid;
                    gap: 12px;
                }

                .ai-textarea {
                    width: 100%;
                    min-height: 120px;
                    border: 1px solid rgba(148, 163, 184, 0.25);
                    border-radius: 8px;
                    padding: 10px 12px;
                    font-size: 14px;
                    line-height: 1.5;
                    resize: vertical;
                    background: #0b1222;
                    color: #e2e8f0;
                }

                .ai-result-card {
                    border: 1px solid rgba(148, 163, 184, 0.2);
                    border-radius: 8px;
                    padding: 12px;
                    background: rgba(15, 23, 42, 0.7);
                }

                .ai-context-card {
                    border: 1px solid rgba(148, 163, 184, 0.2);
                    border-radius: 8px;
                    padding: 10px 12px;
                    background: rgba(15, 23, 42, 0.45);
                }

                .ai-context-list {
                    margin: 8px 0 0;
                    padding-left: 18px;
                    display: grid;
                    gap: 4px;
                    max-height: 140px;
                    overflow: auto;
                    font-size: 12px;
                    color: #cbd5e1;
                }

                .ai-result-grid {
                    display: grid;
                    grid-template-columns: repeat(4, minmax(0, 1fr));
                    gap: 8px;
                    margin-top: 8px;
                }

                .ai-result-item {
                    border: 1px solid rgba(148, 163, 184, 0.18);
                    border-radius: 6px;
                    padding: 8px;
                    font-size: 12px;
                    color: #cbd5e1;
                }

                .ai-modal-footer {
                    display: flex;
                    justify-content: flex-end;
                    gap: 10px;
                    padding: 14px 20px;
                    border-top: 1px solid rgba(148, 163, 184, 0.2);
                }
                
                @keyframes spin {
                    to { transform: rotate(360deg); }
                }
            `}</style>

            {showTemplateGallery && (
                <TemplateGallery
                    onSelect={handleTemplateSelect}
                    onClose={() => setShowTemplateGallery(false)}
                />
            )}

            {showAiGenerator && (
                <div className="ai-modal-overlay" onClick={() => setShowAiGenerator(false)}>
                    <div className="ai-modal" onClick={(e) => e.stopPropagation()}>
                        <div className="ai-modal-header">
                            <h3 style={{ margin: 0 }}>🤖 AI 生成大屏草稿</h3>
                            <button className="action-btn" style={{ maxWidth: 80 }} onClick={() => setShowAiGenerator(false)}>关闭</button>
                        </div>
                        <div className="ai-modal-body">
                            <div style={{ fontSize: 13, color: '#94a3b8' }}>
                                描述业务场景、核心指标、时间粒度，系统将生成可编辑大屏草稿（可再绑定真实数据源）。
                            </div>
                            <textarea
                                className="ai-textarea"
                                value={aiPrompt}
                                onChange={(e) => setAiPrompt(e.target.value)}
                                placeholder="示例：生成一个制造车间运营大屏，包含产量趋势、良率、设备告警、班组排名和明细表"
                            />
                            <textarea
                                className="ai-textarea"
                                style={{ minHeight: 78 }}
                                value={aiRefinePrompt}
                                onChange={(e) => setAiRefinePrompt(e.target.value)}
                                placeholder="优化指令示例：改成三列布局，首图改成柱状图，切换为浅色主题，增加筛选器，加tab切换场景，移除tab切换，刷新30秒，放大字体"
                            />
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                <label style={{ fontSize: 12, color: '#94a3b8', minWidth: 88 }}>优化模式</label>
                                <select
                                    className="property-input"
                                    value={aiRefineMode}
                                    onChange={(e) => setAiRefineMode(e.target.value === 'suggest' ? 'suggest' : 'apply')}
                                    style={{ maxWidth: 180 }}
                                >
                                    <option value="apply">应用模式（默认）</option>
                                    <option value="suggest">建议模式（不自动发布）</option>
                                </select>
                            </div>
                            {aiContextHistory.length > 0 && (
                                <div className="ai-context-card">
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                        <div style={{ fontWeight: 600, fontSize: 12 }}>多轮上下文（最近 12 条）</div>
                                        <button
                                            className="action-btn"
                                            style={{ maxWidth: 100 }}
                                            onClick={() => setAiContextHistory([])}
                                        >
                                            清空上下文
                                        </button>
                                    </div>
                                    <ol className="ai-context-list">
                                        {aiContextHistory.map((item, index) => (
                                            <li key={`${item}-${index}`}>{item}</li>
                                        ))}
                                    </ol>
                                </div>
                            )}
                            {aiResult?.screenSpec && (
                                <div className="ai-result-card">
                                    <div style={{ fontWeight: 600 }}>生成预览</div>
                                    <div className="ai-result-grid">
                                        <div className="ai-result-item">名称: {aiResult.screenSpec.name || '-'}</div>
                                        <div className="ai-result-item">主题: {aiResult.screenSpec.theme || '-'}</div>
                                        <div className="ai-result-item">组件数: {(aiResult.screenSpec.components || []).length}</div>
                                        <div className="ai-result-item">质量分: {aiResult.quality?.score ?? '-'}</div>
                                        <div className="ai-result-item">上下文条数: {aiResult.contextCount ?? 0}</div>
                                        <div className="ai-result-item">有效上下文: {aiResult.usedContextCount ?? aiResult.contextCount ?? 0}</div>
                                        <div className="ai-result-item">优化模式: {aiResult.applyMode || 'apply'}</div>
                                        <div className="ai-result-item">领域: {aiResult.intent?.domain || '-'}</div>
                                        <div className="ai-result-item">时间范围: {aiResult.intent?.timeRange || '-'}</div>
                                        <div className="ai-result-item">粒度: {aiResult.intent?.granularity || '-'}</div>
                                    </div>
                                    {aiResult.intent && (
                                        <div style={{ marginTop: 10, fontSize: 12, color: '#cbd5e1' }}>
                                            识别指标：{(aiResult.intent.metrics || []).join('、') || '-'}；维度：{(aiResult.intent.dimensions || []).join('、') || '-'}；筛选：{(aiResult.intent.filters || []).join('、') || '-'}
                                        </div>
                                    )}
                                    {Array.isArray(aiResult.quality?.warnings) && aiResult.quality?.warnings.length > 0 && (
                                        <div style={{ marginTop: 10, fontSize: 12, color: '#fbbf24' }}>
                                            {aiResult.quality?.warnings.join('；')}
                                        </div>
                                    )}
                                    {Array.isArray(aiResult.actions) && aiResult.actions.length > 0 && (
                                        <div style={{ marginTop: 10, fontSize: 12, color: '#38bdf8' }}>
                                            {aiResult.applyMode === 'suggest' ? '建议动作：' : '已执行：'}{aiResult.actions.join('；')}
                                        </div>
                                    )}
                                    {Array.isArray(aiResult.queryRecommendations) && aiResult.queryRecommendations.length > 0 && (
                                        <div style={{ marginTop: 10, fontSize: 12, color: '#93c5fd' }}>
                                            查询建议：{aiResult.queryRecommendations.map((q) => `${q.id || '-'}(${q.purpose || '-'})`).join('；')}
                                        </div>
                                    )}
                                    {aiResult.semanticModelHints && (
                                        <div style={{ marginTop: 10, fontSize: 12, color: '#60a5fa' }}>
                                            语义映射：事实表 {aiResult.semanticModelHints.factTable || '-'}，时间字段 {aiResult.semanticModelHints.timeField || '-'}
                                        </div>
                                    )}
                                    {Array.isArray(aiResult.sqlBlueprints) && aiResult.sqlBlueprints.length > 0 && (
                                        <div style={{ marginTop: 10, fontSize: 12, color: '#bfdbfe' }}>
                                            SQL蓝图：{aiResult.sqlBlueprints.map((row) => `${row.queryId || '-'}(${row.purpose || '-'})`).join('；')}
                                        </div>
                                    )}
                                    {aiResult.nl2sqlDiagnostics && (
                                        <div style={{ marginTop: 10, fontSize: 12, color: '#fca5a5' }}>
                                            NL2SQL诊断：状态 {aiResult.nl2sqlDiagnostics.status || '-'}，就绪度 {aiResult.nl2sqlDiagnostics.executionReadiness || '-'}，可执行 {aiResult.nl2sqlDiagnostics.executableBlueprintCount ?? aiResult.nl2sqlDiagnostics.safeCount ?? 0}，需补参 {aiResult.nl2sqlDiagnostics.needsParamsCount ?? 0}，阻断 {aiResult.nl2sqlDiagnostics.blockedCount ?? 0}
                                        </div>
                                    )}
                                    {Array.isArray(aiResult.nl2sqlDiagnostics?.requiredVariables) && aiResult.nl2sqlDiagnostics.requiredVariables.length > 0 && (
                                        <div style={{ marginTop: 8, fontSize: 12, color: '#fda4af' }}>
                                            识别参数：{aiResult.nl2sqlDiagnostics.requiredVariables.slice(0, 8).join('、')}
                                        </div>
                                    )}
                                    {Array.isArray(aiResult.nl2sqlDiagnostics?.pendingVariables) && aiResult.nl2sqlDiagnostics.pendingVariables.length > 0 && (
                                        <div style={{ marginTop: 8, fontSize: 12, color: '#fecdd3' }}>
                                            待补参数：{aiResult.nl2sqlDiagnostics.pendingVariables.slice(0, 8).join('、')}
                                        </div>
                                    )}
                                    {Array.isArray(aiResult.nl2sqlDiagnostics?.autoInjectedVariables) && aiResult.nl2sqlDiagnostics.autoInjectedVariables.length > 0 && (
                                        <div style={{ marginTop: 8, fontSize: 12, color: '#fdba74' }}>
                                            自动补齐变量：{aiResult.nl2sqlDiagnostics.autoInjectedVariables.slice(0, 8).join('、')}
                                        </div>
                                    )}
                                    {Array.isArray(aiResult.nl2sqlDiagnostics?.blueprintChecks) && aiResult.nl2sqlDiagnostics!.blueprintChecks!.length > 0 && (
                                        <div style={{ marginTop: 8, fontSize: 12, color: '#fecaca' }}>
                                            蓝图检查：{aiResult.nl2sqlDiagnostics!.blueprintChecks!.slice(0, 6).map((row) => `${String(row.queryId || '-')}:${String(row.status || '-')}`).join('；')}
                                        </div>
                                    )}
                                    {aiResult.semanticRecall && (
                                        <div style={{ marginTop: 10, fontSize: 12, color: '#86efac' }}>
                                            语义召回：候选表/字段 {aiResult.semanticRecall.schemaCandidates?.length ?? 0}，同义词命中 {aiResult.semanticRecall.synonymHits?.length ?? 0}，few-shot {aiResult.semanticRecall.fewShotExamples?.length ?? 0}
                                        </div>
                                    )}
                                    {Array.isArray(aiResult.vizRecommendations) && aiResult.vizRecommendations.length > 0 && (
                                        <div style={{ marginTop: 10, fontSize: 12, color: '#a7f3d0' }}>
                                            图表建议：{aiResult.vizRecommendations.map((v) => `${v.componentType || '-'}←${v.queryId || '-'}`).join('；')}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                        <div className="ai-modal-footer">
                            <button className="action-btn" style={{ maxWidth: 100 }} onClick={() => setShowAiGenerator(false)}>取消</button>
                            <button className="action-btn" style={{ maxWidth: 120 }} onClick={handleGenerateAi} disabled={aiLoading}>
                                {aiLoading ? '生成中...' : '生成方案'}
                            </button>
                            <button className="action-btn" style={{ maxWidth: 130 }} onClick={handleRefineAi} disabled={aiRefining || !aiResult?.screenSpec}>
                                {aiRefining ? '优化中...' : '按指令优化'}
                            </button>
                            <button
                                className="action-btn"
                                style={{ maxWidth: 130 }}
                                onClick={handleCopyAiRecommendations}
                                disabled={!aiResult}
                            >
                                复制建议
                            </button>
                            <button className="primary-btn" onClick={handleCreateFromAi} disabled={aiCreating || !aiResult?.screenSpec}>
                                {aiCreating ? '创建中...' : '创建草稿'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
