import { useEffect, useMemo, useRef, useState } from 'react';
import { analyticsApi, type ScreenTemplateItem } from '../../../api/analyticsApi';
import { screenTemplates, type ScreenTemplate } from '../screenTemplates';
import { SCREEN_SCHEMA_VERSION } from '../specV2';
import '../ScreenDesigner.css';

export type TemplateSelection =
    | { kind: 'builtin'; template: ScreenTemplate }
    | { kind: 'asset'; template: ScreenTemplateItem };

interface TemplateGalleryProps {
    onSelect: (selection: TemplateSelection) => void;
    onClose: () => void;
}

const CATEGORY_LABELS: Record<string, string> = {
    business: '商务',
    tech: '科技',
    dashboard: '仪表盘',
    monitor: '监控',
    custom: '自定义',
    official: '官方',
    industry: '行业',
};

const VISIBILITY_LABELS: Record<string, string> = {
    personal: '个人',
    team: '团队',
    global: '全局',
};

function templateText(template: {
    name?: string;
    description?: string | null;
    category?: string;
    tags?: string[];
}) {
    return [template.name || '', template.description || '', template.category || '', ...(template.tags || [])]
        .join(' ')
        .toLowerCase();
}

function asTemplatePackage(selection: TemplateSelection): Record<string, unknown> {
    if (selection.kind === 'builtin') {
        return {
            packageType: 'screen-template',
            exportedAt: new Date().toISOString(),
            template: {
                name: selection.template.name,
                description: selection.template.description,
                category: selection.template.category,
                thumbnail: selection.template.thumbnail,
                config: selection.template.config,
            },
        };
    }

    const template = selection.template;
    return {
        packageType: 'screen-template',
        exportedAt: new Date().toISOString(),
        template: {
            name: template.name,
            description: template.description,
            category: template.category,
            thumbnail: template.thumbnail,
            tags: template.tags,
            visibilityScope: template.visibilityScope,
            listed: template.listed !== false,
            themePack: template.themePack,
            config: {
                schemaVersion: template.schemaVersion ?? SCREEN_SCHEMA_VERSION,
                width: template.width,
                height: template.height,
                backgroundColor: template.backgroundColor,
                backgroundImage: template.backgroundImage,
                theme: template.theme,
                themePack: template.themePack,
                components: template.components || [],
                globalVariables: template.globalVariables || [],
            },
        },
    };
}

export function TemplateGallery({ onSelect, onClose }: TemplateGalleryProps) {
    const [selectedKey, setSelectedKey] = useState<string>('builtin:blank');
    const [keyword, setKeyword] = useState('');
    const [category, setCategory] = useState('all');
    const [visibility, setVisibility] = useState('all');
    const [listing, setListing] = useState<'all' | 'listed' | 'unlisted'>('all');
    const [assetTemplates, setAssetTemplates] = useState<ScreenTemplateItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [isUpdatingAsset, setIsUpdatingAsset] = useState(false);
    const [importMode, setImportMode] = useState<'template' | 'industry'>('template');
    const fileInputRef = useRef<HTMLInputElement | null>(null);

    const loadAssetTemplates = () => {
        setLoading(true);
        setError(null);
        analyticsApi.listScreenTemplates()
            .then((data) => {
                setAssetTemplates(Array.isArray(data) ? data : []);
            })
            .catch((err) => {
                console.error('Failed to load screen templates:', err);
                setError('模板资产加载失败');
            })
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        loadAssetTemplates();
    }, []);

    const normalizedKeyword = keyword.trim().toLowerCase();

    const filteredBuiltins = useMemo(() => {
        return screenTemplates.filter((template) => {
            if (category !== 'all' && template.category !== category) {
                return false;
            }
            if (!normalizedKeyword) {
                return true;
            }
            return templateText({
                name: template.name,
                description: template.description,
                category: template.category,
            }).includes(normalizedKeyword);
        });
    }, [category, normalizedKeyword]);

    const filteredAssets = useMemo(() => {
        return assetTemplates.filter((template) => {
            const templateCategory = (template.category || 'custom').toLowerCase();
            if (category !== 'all' && templateCategory !== category) {
                return false;
            }
            const templateVisibility = String(template.visibilityScope || 'team').toLowerCase();
            if (visibility !== 'all' && templateVisibility !== visibility) {
                return false;
            }
            const isListed = template.listed !== false;
            if (listing === 'listed' && !isListed) {
                return false;
            }
            if (listing === 'unlisted' && isListed) {
                return false;
            }
            if (!normalizedKeyword) {
                return true;
            }
            return templateText({
                name: template.name,
                description: template.description,
                category: template.category || 'custom',
                tags: template.tags,
            }).includes(normalizedKeyword);
        });
    }, [assetTemplates, category, listing, normalizedKeyword, visibility]);

    const allCategories = useMemo(() => {
        const values = new Set<string>(['all']);
        for (const t of screenTemplates) {
            values.add(t.category);
        }
        for (const t of assetTemplates) {
            values.add((t.category || 'custom').toLowerCase());
        }
        return Array.from(values);
    }, [assetTemplates]);

    const selectedBuiltin = selectedKey.startsWith('builtin:')
        ? screenTemplates.find((template) => template.id === selectedKey.slice('builtin:'.length))
        : undefined;
    const selectedAsset = selectedKey.startsWith('asset:')
        ? assetTemplates.find((template) => String(template.id) === selectedKey.slice('asset:'.length))
        : undefined;

    const selectedSelection: TemplateSelection | null = selectedBuiltin
        ? { kind: 'builtin', template: selectedBuiltin }
        : selectedAsset
            ? { kind: 'asset', template: selectedAsset }
            : null;

    const handleConfirm = () => {
        if (!selectedSelection) {
            return;
        }
        onSelect(selectedSelection);
    };

    const handleImportClick = (mode: 'template' | 'industry') => {
        setImportMode(mode);
        fileInputRef.current?.click();
    };

    const handleImport = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        event.target.value = '';
        if (!file) return;

        try {
            const text = await file.text();
            const payload = JSON.parse(text) as Record<string, unknown>;

            if (importMode === 'industry') {
                const validation = await analyticsApi.validateScreenIndustryPack(payload);
                if (Array.isArray(validation.errors) && validation.errors.length > 0) {
                    alert(`行业包校验失败：${validation.errors.join('；')}`);
                    return;
                }
                if (Array.isArray(validation.warnings) && validation.warnings.length > 0) {
                    const confirmed = window.confirm(`行业包存在告警：\n${validation.warnings.join('\n')}\n\n是否继续导入？`);
                    if (!confirmed) {
                        return;
                    }
                }
                const result = await analyticsApi.importScreenIndustryPack(payload);
                await loadAssetTemplates();
                alert('行业包导入完成：成功 ' + (result.imported || 0) + '，失败 ' + (result.failed || 0));
                return;
            }

            const rawTemplate = (payload.template || payload) as Record<string, unknown>;
            const rawConfig = (rawTemplate.config || payload.config || payload) as Record<string, unknown>;

            const body = {
                name: typeof rawTemplate.name === 'string' ? rawTemplate.name : file.name.replace(/\.[^.]+$/, ''),
                description: typeof rawTemplate.description === 'string' ? rawTemplate.description : '',
                category: typeof rawTemplate.category === 'string' ? rawTemplate.category : 'custom',
                thumbnail: typeof rawTemplate.thumbnail === 'string' ? rawTemplate.thumbnail : '🧩',
                tags: Array.isArray(rawTemplate.tags) ? rawTemplate.tags : [],
                visibilityScope: typeof rawTemplate.visibilityScope === 'string' ? rawTemplate.visibilityScope : 'team',
                listed: typeof rawTemplate.listed === 'boolean' ? rawTemplate.listed : true,
                themePack: rawTemplate.themePack && typeof rawTemplate.themePack === 'object' ? rawTemplate.themePack : undefined,
                config: {
                    schemaVersion: typeof rawConfig.schemaVersion === 'number' ? rawConfig.schemaVersion : SCREEN_SCHEMA_VERSION,
                    width: typeof rawConfig.width === 'number' ? rawConfig.width : 1920,
                    height: typeof rawConfig.height === 'number' ? rawConfig.height : 1080,
                    backgroundColor: typeof rawConfig.backgroundColor === 'string' ? rawConfig.backgroundColor : '#0d1b2a',
                    backgroundImage: typeof rawConfig.backgroundImage === 'string' ? rawConfig.backgroundImage : null,
                    theme: typeof rawConfig.theme === 'string' ? rawConfig.theme : null,
                    themePack: rawConfig.themePack && typeof rawConfig.themePack === 'object' ? rawConfig.themePack : undefined,
                    components: Array.isArray(rawConfig.components) ? rawConfig.components : [],
                    globalVariables: Array.isArray(rawConfig.globalVariables) ? rawConfig.globalVariables : [],
                },
            };

            const created = await analyticsApi.createScreenTemplate(body);
            await loadAssetTemplates();
            setSelectedKey(`asset:${String(created.id)}`);
            alert('模板包导入成功');
        } catch (err) {
            console.error('Failed to import package:', err);
            alert(importMode === 'industry' ? '行业包导入失败，请检查 JSON 结构' : '模板包导入失败，请检查 JSON 结构');
        }
    };

    const handleExportTemplate = () => {
        if (!selectedSelection) {
            alert('请先选择模板');
            return;
        }
        const pack = asTemplatePackage(selectedSelection);
        const blob = new Blob([JSON.stringify(pack, null, 2)], { type: 'application/json;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        const name = selectedSelection.kind === 'builtin'
            ? selectedSelection.template.name
            : (selectedSelection.template.name || `template-${String(selectedSelection.template.id)}`);
        link.download = `${name || 'screen-template'}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    };

    const handleExportIndustryPack = async () => {
        try {
            const presets = await analyticsApi.getScreenIndustryPackPresets().catch(() => null);
            const defaultIndustry = String((presets?.industries?.[0] as Record<string, unknown> | undefined)?.id || 'discrete-manufacturing');
            const defaultHardware = String((presets?.hardwareProfiles?.[0] as Record<string, unknown> | undefined)?.id || 'edge-box-standard');
            const industry = (window.prompt('行业标识（例如 discrete-manufacturing / energy-carbon）', defaultIndustry) || defaultIndustry).trim();
            const hardwareProfile = (window.prompt('硬件预置（例如 edge-box-standard / ipc-dual-4k）', defaultHardware) || defaultHardware).trim();
            const deploymentMode = (window.prompt('部署模式（online/offline/isolated）', 'online') || 'online').trim();
            const connectors = (window.prompt('连接器类型（逗号分隔，如 plc,mqtt,opcua,postgresql）', 'plc,mqtt,opcua') || '').trim();
            const body = selectedSelection && selectedSelection.kind === 'asset'
                ? { templateIds: [selectedSelection.template.id], industry, hardwareProfile, deploymentMode, connectorTypes: connectors ? connectors.split(',').map(s => s.trim()).filter(Boolean) : undefined }
                : { industry, hardwareProfile, deploymentMode, connectorTypes: connectors ? connectors.split(',').map(s => s.trim()).filter(Boolean) : undefined };
            const pack = await analyticsApi.exportScreenIndustryPack(body);
            const blob = new Blob([JSON.stringify(pack, null, 2)], { type: 'application/json;charset=utf-8' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = 'dts-industry-pack.json';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);
        } catch (err) {
            console.error('Failed to export industry pack:', err);
            alert('行业包导出失败');
        }
    };

    const handleToggleListing = async () => {
        if (!selectedAsset || isUpdatingAsset) {
            return;
        }
        const nextListed = selectedAsset.listed === false;
        setIsUpdatingAsset(true);
        try {
            const updated = await analyticsApi.updateScreenTemplateListing(selectedAsset.id, nextListed);
            setAssetTemplates((prev) => prev.map((item) => String(item.id) === String(updated.id) ? updated : item));
            setSelectedKey(`asset:${String(updated.id)}`);
            alert(nextListed ? '模板已上架' : '模板已下架');
        } catch (err) {
            console.error('Failed to update template listing:', err);
            alert('模板上下架失败');
        } finally {
            setIsUpdatingAsset(false);
        }
    };

    const handleRestoreTemplateVersion = async () => {
        if (!selectedAsset || isUpdatingAsset) {
            return;
        }
        setIsUpdatingAsset(true);
        try {
            const versions = await analyticsApi.listScreenTemplateVersions(selectedAsset.id, 30);
            if (!Array.isArray(versions) || versions.length === 0) {
                alert('暂无可恢复版本');
                return;
            }
            const lines = versions
                .map((v) => `v${v.versionNo ?? '-'} | ${v.action || '-'} | ${v.createdAt || '-'}`)
                .join('\n');
            const input = (window.prompt(`版本列表：\n${lines}\n\n输入要恢复的版本号（versionNo）`, '') || '').trim();
            if (!input) {
                return;
            }
            const versionNo = Number(input);
            if (!Number.isFinite(versionNo) || versionNo <= 0) {
                alert('版本号格式不正确');
                return;
            }
            const updated = await analyticsApi.restoreScreenTemplateVersion(selectedAsset.id, versionNo);
            setAssetTemplates((prev) => prev.map((item) => String(item.id) === String(updated.id) ? updated : item));
            setSelectedKey(`asset:${String(updated.id)}`);
            alert('模板恢复成功');
        } catch (err) {
            console.error('Failed to restore template version:', err);
            alert('模板版本恢复失败');
        } finally {
            setIsUpdatingAsset(false);
        }
    };

    return (
        <div className="template-gallery-overlay" onClick={onClose}>
            <div className="template-gallery-modal" onClick={(e) => e.stopPropagation()}>
                <div className="template-gallery-header">
                    <h2>📋 模板市场</h2>
                    <button className="template-gallery-close" onClick={onClose}>✕</button>
                </div>

                <div className="template-gallery-filters">
                    <input
                        type="text"
                        className="template-search-input"
                        value={keyword}
                        onChange={(e) => setKeyword(e.target.value)}
                        placeholder="搜索模板名称/标签"
                    />
                    <select
                        className="template-category-select"
                        value={category}
                        onChange={(e) => setCategory(e.target.value)}
                    >
                        {allCategories.map((value) => (
                            <option key={value} value={value}>
                                {value === 'all' ? '全部分类' : (CATEGORY_LABELS[value] || value)}
                            </option>
                        ))}
                    </select>
                    <select
                        className="template-category-select"
                        value={visibility}
                        onChange={(e) => setVisibility(e.target.value)}
                    >
                        <option value="all">全部可见范围</option>
                        <option value="personal">个人</option>
                        <option value="team">团队</option>
                        <option value="global">全局</option>
                    </select>
                    <select
                        className="template-category-select"
                        value={listing}
                        onChange={(e) => setListing(e.target.value as 'all' | 'listed' | 'unlisted')}
                    >
                        <option value="all">全部状态</option>
                        <option value="listed">仅上架</option>
                        <option value="unlisted">仅下架</option>
                    </select>
                    <button className="template-btn secondary" onClick={loadAssetTemplates}>刷新资产</button>
                    <button className="template-btn secondary" onClick={() => handleImportClick('template')}>导入模板包</button>
                    <button className="template-btn secondary" onClick={handleExportTemplate}>导出模板包</button>
                    <button className="template-btn secondary" onClick={() => handleImportClick('industry')}>导入行业包</button>
                    <button className="template-btn secondary" onClick={handleExportIndustryPack}>导出行业包</button>
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept="application/json"
                        style={{ display: 'none' }}
                        onChange={handleImport}
                    />
                </div>

                <div className="template-gallery-content">
                    <div className="template-section-title">内置模板</div>
                    <div className="template-gallery-grid">
                        {filteredBuiltins.map((template) => {
                            const key = `builtin:${template.id}`;
                            return (
                                <div
                                    key={key}
                                    className={`template-card ${selectedKey === key ? 'selected' : ''}`}
                                    onClick={() => setSelectedKey(key)}
                                >
                                    <div
                                        className="template-card-preview"
                                        style={{
                                            background: template.config.backgroundColor
                                                ? `linear-gradient(135deg, ${template.config.backgroundColor} 0%, ${template.config.backgroundColor} 100%)`
                                                : undefined,
                                        }}
                                    >
                                        <span className="template-card-icon">{template.thumbnail}</span>
                                        <div className="template-card-badge">内置</div>
                                    </div>
                                    <div className="template-card-info">
                                        <h3>{template.name}</h3>
                                        <p>{template.description}</p>
                                        <div className="template-card-meta">
                                            {(CATEGORY_LABELS[template.category] || template.category)} · {template.config.components.length} 个组件
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    <div className="template-section-title" style={{ marginTop: 20 }}>资产模板</div>
                    {loading ? (
                        <div className="template-empty-hint">加载中...</div>
                    ) : error ? (
                        <div className="template-empty-hint">{error}</div>
                    ) : filteredAssets.length === 0 ? (
                        <div className="template-empty-hint">暂无资产模板</div>
                    ) : (
                        <div className="template-gallery-grid">
                            {filteredAssets.map((template) => {
                                const key = `asset:${template.id}`;
                                return (
                                    <div
                                        key={key}
                                        className={`template-card ${selectedKey === key ? 'selected' : ''}`}
                                        onClick={() => setSelectedKey(key)}
                                    >
                                        <div
                                            className="template-card-preview"
                                            style={{
                                                background: template.backgroundColor
                                                    ? `linear-gradient(135deg, ${template.backgroundColor} 0%, ${template.backgroundColor} 100%)`
                                                    : undefined,
                                            }}
                                        >
                                            <span className="template-card-icon">{template.thumbnail || '🧩'}</span>
                                            <div className="template-card-badge">资产</div>
                                        </div>
                                        <div className="template-card-info">
                                            <h3>{template.name || `模板 #${template.id}`}</h3>
                                            <p>{template.description || '无描述'}</p>
                                            <div className="template-card-meta">
                                                {(CATEGORY_LABELS[(template.category || 'custom').toLowerCase()] || template.category || 'custom')}
                                                {` · ${template.listed === false ? '下架' : '上架'}`}
                                                {` · ${VISIBILITY_LABELS[String(template.visibilityScope || 'team').toLowerCase()] || String(template.visibilityScope || 'team')}`}
                                                {template.templateVersion ? ` · v${template.templateVersion}` : ''}
                                                {Array.isArray(template.tags) && template.tags.length > 0 ? ` · ${template.tags.join(' / ')}` : ''}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                <div className="template-gallery-footer">
                    {selectedAsset ? (
                        <>
                            <button
                                className="template-btn secondary"
                                onClick={handleToggleListing}
                                disabled={isUpdatingAsset}
                            >
                                {selectedAsset.listed === false ? '上架模板' : '下架模板'}
                            </button>
                            <button
                                className="template-btn secondary"
                                onClick={handleRestoreTemplateVersion}
                                disabled={isUpdatingAsset}
                            >
                                版本恢复
                            </button>
                        </>
                    ) : null}
                    <button className="template-btn secondary" onClick={onClose}>取消</button>
                    <button className="template-btn primary" onClick={handleConfirm}>使用此模板</button>
                </div>
            </div>

            <style>{`
                .template-gallery-overlay {
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    background: rgba(0, 0, 0, 0.7);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    z-index: 1000;
                    backdrop-filter: blur(4px);
                }

                .template-gallery-modal {
                    background: #1a1f36;
                    border-radius: 12px;
                    width: 92%;
                    max-width: 1080px;
                    max-height: 84vh;
                    display: flex;
                    flex-direction: column;
                    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
                    border: 1px solid rgba(255, 255, 255, 0.1);
                }

                .template-gallery-header {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    padding: 20px 24px;
                    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
                }

                .template-gallery-header h2 {
                    margin: 0;
                    font-size: 20px;
                    color: #fff;
                }

                .template-gallery-close {
                    background: none;
                    border: none;
                    color: #888;
                    font-size: 24px;
                    cursor: pointer;
                    padding: 4px 8px;
                    border-radius: 4px;
                    transition: all 0.2s;
                }

                .template-gallery-close:hover {
                    background: rgba(255, 255, 255, 0.1);
                    color: #fff;
                }

                .template-gallery-filters {
                    display: grid;
                    grid-template-columns: 1fr repeat(8, auto);
                    gap: 8px;
                    padding: 12px 24px;
                    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                }

                .template-search-input {
                    background: rgba(15, 23, 42, 0.8);
                    border: 1px solid rgba(255, 255, 255, 0.2);
                    border-radius: 6px;
                    padding: 8px 10px;
                    color: #fff;
                    font-size: 13px;
                }

                .template-category-select {
                    background: rgba(15, 23, 42, 0.8);
                    border: 1px solid rgba(255, 255, 255, 0.2);
                    border-radius: 6px;
                    padding: 8px 10px;
                    color: #fff;
                    font-size: 13px;
                }

                .template-gallery-content {
                    flex: 1;
                    overflow-y: auto;
                    padding: 24px;
                }

                .template-section-title {
                    font-size: 14px;
                    font-weight: 700;
                    color: #e2e8f0;
                    margin-bottom: 12px;
                }

                .template-gallery-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
                    gap: 20px;
                }

                .template-card {
                    position: relative;
                    background: #0d1226;
                    border: 2px solid transparent;
                    border-radius: 10px;
                    overflow: hidden;
                    cursor: pointer;
                    transition: all 0.2s ease;
                }

                .template-card:hover {
                    border-color: rgba(0, 212, 255, 0.3);
                    transform: translateY(-2px);
                }

                .template-card.selected {
                    border-color: #00d4ff;
                    box-shadow: 0 0 20px rgba(0, 212, 255, 0.3);
                }

                .template-card-preview {
                    height: 140px;
                    background: linear-gradient(135deg, #0a0e27 0%, #1a1f46 100%);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    position: relative;
                }

                .template-card-icon {
                    font-size: 48px;
                }

                .template-card-badge {
                    position: absolute;
                    top: 10px;
                    right: 10px;
                    padding: 4px 10px;
                    background: rgba(0, 212, 255, 0.2);
                    color: #00d4ff;
                    border-radius: 20px;
                    font-size: 11px;
                    font-weight: 500;
                }

                .template-card-info {
                    padding: 16px;
                }

                .template-card-info h3 {
                    margin: 0 0 8px 0;
                    font-size: 16px;
                    font-weight: 600;
                    color: #fff;
                }

                .template-card-info p {
                    margin: 0 0 10px 0;
                    font-size: 13px;
                    color: #888;
                    line-height: 1.4;
                }

                .template-card-meta {
                    font-size: 12px;
                    color: #666;
                }

                .template-empty-hint {
                    color: #94a3b8;
                    font-size: 13px;
                    padding: 14px 4px;
                }

                .template-gallery-footer {
                    display: flex;
                    gap: 12px;
                    justify-content: flex-end;
                    padding: 16px 24px;
                    border-top: 1px solid rgba(255, 255, 255, 0.1);
                }

                .template-btn {
                    padding: 10px 14px;
                    border-radius: 6px;
                    font-size: 13px;
                    font-weight: 500;
                    cursor: pointer;
                    transition: all 0.2s;
                }

                .template-btn.secondary {
                    background: transparent;
                    border: 1px solid rgba(255, 255, 255, 0.2);
                    color: #888;
                }

                .template-btn.secondary:hover {
                    border-color: rgba(255, 255, 255, 0.4);
                    color: #fff;
                }

                .template-btn.primary {
                    background: linear-gradient(135deg, #00d4ff 0%, #0066ff 100%);
                    border: none;
                    color: #fff;
                }

                .template-btn.primary:hover {
                    transform: translateY(-1px);
                    box-shadow: 0 4px 12px rgba(0, 212, 255, 0.4);
                }
            `}</style>
        </div>
    );
}
