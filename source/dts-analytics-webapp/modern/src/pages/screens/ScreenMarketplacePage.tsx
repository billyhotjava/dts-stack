/**
 * ScreenMarketplacePage — 组件/模板市场
 *
 * Browse, search, inspect and install shared components/templates.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { analyticsApi, type MarketplaceCatalogItem } from '../../api/analyticsApi';
import {
    filterMarketplaceItems,
    resolveMarketplaceEmptyState,
    resolveMarketplaceInstallMessage,
} from './ScreenMarketplacePage.helpers';
import { notifyScreenPluginManifestsUpdated } from './plugins/manifestLoader';

type TabKey = 'components' | 'templates';
type CategoryKey = 'all' | 'chart' | 'decoration' | 'container' | 'metric' | 'other' | 'retail' | 'business';

type ActionNotice = {
    tone: 'success' | 'error';
    text: string;
};

const CATEGORIES: Array<{ key: CategoryKey; label: string }> = [
    { key: 'all', label: '全部' },
    { key: 'chart', label: '图表' },
    { key: 'decoration', label: '装饰' },
    { key: 'container', label: '容器' },
    { key: 'metric', label: '指标' },
    { key: 'business', label: '业务' },
    { key: 'retail', label: '零售' },
    { key: 'other', label: '其他' },
];

function cardAccent(activeTab: TabKey): string {
    return activeTab === 'components' ? 'rgba(14, 165, 233, 0.18)' : 'rgba(16, 185, 129, 0.18)';
}

function formatTime(value?: string): string | null {
    if (!value) return null;
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return null;
    return date.toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    });
}

export default function ScreenMarketplacePage() {
    const [activeTab, setActiveTab] = useState<TabKey>('components');
    const [search, setSearch] = useState('');
    const [category, setCategory] = useState<CategoryKey>('all');
    const [items, setItems] = useState<MarketplaceCatalogItem[]>([]);
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const [installing, setInstalling] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<ActionNotice | null>(null);

    const loadItems = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const params = {
                search: search || undefined,
                category: category !== 'all' ? category : undefined,
            };
            const result = activeTab === 'components'
                ? await analyticsApi.listMarketplaceComponents(params)
                : await analyticsApi.listMarketplaceTemplates(params);
            const nextItems = Array.isArray(result) ? result : [];
            setItems(nextItems);
            setSelectedId((current) => {
                if (current && nextItems.some((item) => item.id === current)) {
                    return current;
                }
                return nextItems[0]?.id || null;
            });
        } catch (loadError) {
            console.error('Failed to load marketplace items:', loadError);
            setItems([]);
            setSelectedId(null);
            setError(loadError instanceof Error ? loadError.message : '市场加载失败');
        } finally {
            setLoading(false);
        }
    }, [activeTab, category, search]);

    useEffect(() => {
        void loadItems();
    }, [loadItems]);

    useEffect(() => {
        setNotice(null);
    }, [activeTab, search, category]);

    const filteredItems = useMemo(() => filterMarketplaceItems(items, search), [items, search]);
    const selectedItem = useMemo(
        () => filteredItems.find((item) => item.id === selectedId) || filteredItems[0] || null,
        [filteredItems, selectedId],
    );
    const emptyState = resolveMarketplaceEmptyState({
        hasError: Boolean(error),
        hasRemoteItems: items.length > 0,
        hasFilteredItems: filteredItems.length > 0,
        activeTab,
        search,
    });

    const handleInstall = useCallback(async (item: MarketplaceCatalogItem) => {
        if (installing) return;
        setInstalling(item.id);
        setNotice(null);
        try {
            const installed = activeTab === 'components'
                ? await analyticsApi.installMarketplaceComponent(item.id)
                : await analyticsApi.installMarketplaceTemplate(item.id);
            setItems((current) => current.map((entry) => entry.id === item.id ? { ...entry, ...installed, installed: true } : entry));
            setNotice({
                tone: 'success',
                text: resolveMarketplaceInstallMessage(activeTab, item.name || item.id, true),
            });
            if (activeTab === 'components') {
                notifyScreenPluginManifestsUpdated();
            }
            await loadItems();
        } catch (installError) {
            console.error('Failed to install marketplace item:', installError);
            const detail = installError instanceof Error ? installError.message : '';
            setNotice({
                tone: 'error',
                text: detail
                    ? `${resolveMarketplaceInstallMessage(activeTab, item.name || item.id, false)} ${detail}`
                    : resolveMarketplaceInstallMessage(activeTab, item.name || item.id, false),
            });
        } finally {
            setInstalling(null);
        }
    }, [activeTab, installing, loadItems]);

    return (
        <div style={{ padding: 24, maxWidth: 1320, margin: '0 auto', color: 'inherit' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 24, alignItems: 'flex-start', marginBottom: 20 }}>
                <div>
                    <h1 style={{ fontSize: 24, fontWeight: 700, margin: '0 0 8px 0' }}>组件与模板市场</h1>
                    <p style={{ fontSize: 13, opacity: 0.68, margin: 0, maxWidth: 640 }}>
                        市场页已经接通真实后端，可按分类浏览可安装组件与模板，并将安装结果同步回组件库和模板资产中心。
                    </p>
                </div>
                <div style={{
                    minWidth: 280,
                    padding: 14,
                    borderRadius: 14,
                    background: 'linear-gradient(135deg, rgba(15,23,42,0.88), rgba(30,41,59,0.82))',
                    color: '#e2e8f0',
                    boxShadow: '0 18px 40px rgba(15,23,42,0.18)',
                }}>
                    <div style={{ fontSize: 12, letterSpacing: 0.6, opacity: 0.72, textTransform: 'uppercase' }}>Market Snapshot</div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginTop: 12 }}>
                        <div>
                            <div style={{ fontSize: 22, fontWeight: 700 }}>{items.length}</div>
                            <div style={{ fontSize: 11, opacity: 0.68 }}>远端条目</div>
                        </div>
                        <div>
                            <div style={{ fontSize: 22, fontWeight: 700 }}>{filteredItems.length}</div>
                            <div style={{ fontSize: 11, opacity: 0.68 }}>当前结果</div>
                        </div>
                        <div>
                            <div style={{ fontSize: 22, fontWeight: 700 }}>{items.filter((item) => item.installed).length}</div>
                            <div style={{ fontSize: 11, opacity: 0.68 }}>已安装</div>
                        </div>
                    </div>
                </div>
            </div>

            {notice && (
                <div
                    data-testid="analytics-marketplace-notice"
                    style={{
                        marginBottom: 16,
                        padding: '12px 14px',
                        borderRadius: 12,
                        border: notice.tone === 'success'
                            ? '1px solid rgba(16,185,129,0.24)'
                            : '1px solid rgba(239,68,68,0.24)',
                        background: notice.tone === 'success'
                            ? 'rgba(16,185,129,0.10)'
                            : 'rgba(239,68,68,0.08)',
                        color: notice.tone === 'success' ? '#047857' : '#b91c1c',
                        fontSize: 13,
                    }}
                >
                    {notice.text}
                </div>
            )}

            <div style={{ display: 'flex', gap: 24, alignItems: 'flex-start' }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', gap: 4, marginBottom: 16, borderBottom: '1px solid rgba(148,163,184,0.15)' }}>
                        {(['components', 'templates'] as TabKey[]).map((tab) => (
                            <button
                                key={tab}
                                type="button"
                                onClick={() => setActiveTab(tab)}
                                style={{
                                    background: 'none',
                                    border: 'none',
                                    borderBottom: activeTab === tab ? '2px solid var(--color-primary, #3b82f6)' : '2px solid transparent',
                                    color: activeTab === tab ? 'var(--color-primary, #3b82f6)' : 'inherit',
                                    padding: '8px 16px',
                                    fontSize: 13,
                                    cursor: 'pointer',
                                    fontWeight: activeTab === tab ? 600 : 400,
                                }}
                            >
                                {tab === 'components' ? '组件市场' : '模板市场'}
                            </button>
                        ))}
                    </div>

                    <div style={{ display: 'flex', gap: 10, marginBottom: 16, alignItems: 'center' }}>
                        <input
                            data-testid="analytics-marketplace-search"
                            type="text"
                            placeholder={activeTab === 'components' ? '搜索组件、标签或描述...' : '搜索模板、标签或描述...'}
                            value={search}
                            onChange={(event) => setSearch(event.target.value)}
                            style={{
                                flex: 1,
                                padding: '10px 12px',
                                border: '1px solid rgba(148,163,184,0.3)',
                                borderRadius: 10,
                                background: 'rgba(148,163,184,0.06)',
                                color: 'inherit',
                                fontSize: 13,
                                outline: 'none',
                            }}
                        />
                        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                            {CATEGORIES.map((cat) => (
                                <button
                                    key={cat.key}
                                    type="button"
                                    onClick={() => setCategory(cat.key)}
                                    style={{
                                        padding: '6px 12px',
                                        fontSize: 12,
                                        borderRadius: 999,
                                        border: category === cat.key ? '1px solid var(--color-primary, #3b82f6)' : '1px solid rgba(148,163,184,0.2)',
                                        background: category === cat.key ? 'rgba(59,130,246,0.12)' : 'transparent',
                                        color: category === cat.key ? 'var(--color-primary, #3b82f6)' : 'inherit',
                                        cursor: 'pointer',
                                    }}
                                >
                                    {cat.label}
                                </button>
                            ))}
                        </div>
                    </div>

                    {loading ? (
                        <div style={{
                            textAlign: 'center',
                            padding: 48,
                            borderRadius: 18,
                            background: 'rgba(15,23,42,0.03)',
                            border: '1px solid rgba(148,163,184,0.12)',
                            fontSize: 13,
                            opacity: 0.68,
                        }}
                        >
                            正在读取 {activeTab === 'components' ? '组件' : '模板'} 市场...
                        </div>
                    ) : filteredItems.length === 0 ? (
                        <div style={{
                            padding: 40,
                            borderRadius: 18,
                            background: 'rgba(15,23,42,0.03)',
                            border: '1px solid rgba(148,163,184,0.12)',
                        }}
                        >
                            <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 6 }}>{emptyState.title}</div>
                            <div style={{ fontSize: 13, opacity: 0.68, lineHeight: 1.6 }}>{emptyState.description}</div>
                            {(error || items.length === 0) && (
                                <button
                                    type="button"
                                    onClick={() => void loadItems()}
                                    style={{
                                        marginTop: 16,
                                        padding: '8px 14px',
                                        borderRadius: 10,
                                        border: '1px solid rgba(59,130,246,0.24)',
                                        background: 'rgba(59,130,246,0.10)',
                                        color: 'var(--color-primary, #3b82f6)',
                                        cursor: 'pointer',
                                    }}
                                >
                                    重新加载
                                </button>
                            )}
                        </div>
                    ) : (
                        <div
                            data-testid="analytics-marketplace-grid"
                            style={{
                                display: 'grid',
                                gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
                                gap: 16,
                            }}
                        >
                            {filteredItems.map((item) => {
                                const isSelected = selectedItem?.id === item.id;
                                return (
                                    <button
                                        key={item.id}
                                        type="button"
                                        onClick={() => setSelectedId(item.id)}
                                        data-testid={`analytics-marketplace-card-${item.id}`}
                                        style={{
                                            textAlign: 'left',
                                            border: isSelected ? '1px solid rgba(59,130,246,0.32)' : '1px solid rgba(148,163,184,0.18)',
                                            borderRadius: 16,
                                            overflow: 'hidden',
                                            background: isSelected ? 'rgba(59,130,246,0.06)' : 'rgba(148,163,184,0.04)',
                                            transition: 'box-shadow 0.2s, transform 0.2s',
                                            boxShadow: isSelected ? '0 18px 36px rgba(59,130,246,0.12)' : '0 8px 18px rgba(15,23,42,0.05)',
                                            padding: 0,
                                            cursor: 'pointer',
                                            color: 'inherit',
                                        }}
                                    >
                                        <div style={{
                                            height: 124,
                                            background: `linear-gradient(135deg, ${cardAccent(activeTab)}, rgba(15,23,42,0.06))`,
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'space-between',
                                            padding: 16,
                                        }}>
                                            <div>
                                                <div style={{ fontSize: 12, letterSpacing: 0.6, opacity: 0.68, textTransform: 'uppercase' }}>
                                                    {item.category || (activeTab === 'components' ? 'component' : 'template')}
                                                </div>
                                                <div style={{ marginTop: 10, fontSize: 24, fontWeight: 700 }}>{item.name || item.id}</div>
                                            </div>
                                            <div style={{
                                                padding: '6px 10px',
                                                borderRadius: 999,
                                                background: item.installed ? 'rgba(16,185,129,0.16)' : 'rgba(255,255,255,0.46)',
                                                color: item.installed ? '#047857' : 'rgba(15,23,42,0.65)',
                                                fontSize: 11,
                                                fontWeight: 600,
                                            }}>
                                                {item.installed ? '已安装' : '可安装'}
                                            </div>
                                        </div>
                                        <div style={{ padding: 16 }}>
                                            <div style={{ fontSize: 13, lineHeight: 1.6, minHeight: 42, opacity: 0.74 }}>
                                                {item.description || '暂无详细说明'}
                                            </div>
                                            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 12 }}>
                                                {(item.tags || []).slice(0, 4).map((tag) => (
                                                    <span
                                                        key={tag}
                                                        style={{
                                                            fontSize: 10,
                                                            padding: '2px 8px',
                                                            borderRadius: 999,
                                                            border: '1px solid rgba(148,163,184,0.2)',
                                                            opacity: 0.72,
                                                        }}
                                                    >
                                                        {tag}
                                                    </span>
                                                ))}
                                            </div>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 14 }}>
                                                <span style={{ fontSize: 11, opacity: 0.58 }}>
                                                    {item.author || '系统'}{item.downloads != null ? ` · ${item.downloads} 次安装` : ''}
                                                </span>
                                                <span style={{ fontSize: 11, opacity: 0.48 }}>
                                                    {formatTime(item.updatedAt || item.createdAt) || '刚刚更新'}
                                                </span>
                                            </div>
                                        </div>
                                    </button>
                                );
                            })}
                        </div>
                    )}
                </div>

                <aside
                    data-testid="analytics-marketplace-detail"
                    style={{
                        width: 340,
                        position: 'sticky',
                        top: 24,
                        alignSelf: 'flex-start',
                        borderRadius: 18,
                        border: '1px solid rgba(148,163,184,0.16)',
                        background: 'linear-gradient(180deg, rgba(255,255,255,0.96), rgba(248,250,252,0.96))',
                        padding: 18,
                        boxShadow: '0 18px 48px rgba(15,23,42,0.10)',
                    }}
                >
                    {selectedItem ? (
                        <>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
                                <div>
                                    <div style={{ fontSize: 12, letterSpacing: 0.6, opacity: 0.58, textTransform: 'uppercase' }}>
                                        {activeTab === 'components' ? 'Component Detail' : 'Template Detail'}
                                    </div>
                                    <h2 style={{ margin: '8px 0 0 0', fontSize: 20 }}>{selectedItem.name || selectedItem.id}</h2>
                                </div>
                                <div style={{
                                    padding: '6px 10px',
                                    borderRadius: 999,
                                    background: selectedItem.installed ? 'rgba(16,185,129,0.12)' : 'rgba(59,130,246,0.10)',
                                    color: selectedItem.installed ? '#047857' : '#2563eb',
                                    fontSize: 11,
                                    fontWeight: 700,
                                }}
                                >
                                    {selectedItem.installed ? '已安装' : '待安装'}
                                </div>
                            </div>
                            <div style={{ marginTop: 14, fontSize: 13, lineHeight: 1.72, opacity: 0.74 }}>
                                {selectedItem.description || '当前条目尚未提供更多说明。'}
                            </div>
                            <div style={{ marginTop: 18, display: 'grid', gap: 12 }}>
                                <div style={{ padding: 12, borderRadius: 14, background: 'rgba(15,23,42,0.03)' }}>
                                    <div style={{ fontSize: 11, textTransform: 'uppercase', opacity: 0.52 }}>发布信息</div>
                                    <div style={{ marginTop: 6, fontSize: 13 }}>
                                        作者：{selectedItem.author || '系统'}
                                        <br />
                                        版本：{selectedItem.version || '-'}
                                        <br />
                                        分类：{selectedItem.category || '-'}
                                    </div>
                                </div>
                                <div style={{ padding: 12, borderRadius: 14, background: 'rgba(15,23,42,0.03)' }}>
                                    <div style={{ fontSize: 11, textTransform: 'uppercase', opacity: 0.52 }}>标签与轨迹</div>
                                    <div style={{ marginTop: 8, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                                        {(selectedItem.tags || []).length > 0 ? (selectedItem.tags || []).map((tag) => (
                                            <span
                                                key={tag}
                                                style={{
                                                    fontSize: 10,
                                                    padding: '3px 8px',
                                                    borderRadius: 999,
                                                    background: 'rgba(59,130,246,0.10)',
                                                    color: '#2563eb',
                                                }}
                                            >
                                                {tag}
                                            </span>
                                        )) : <span style={{ fontSize: 12, opacity: 0.58 }}>暂无标签</span>}
                                    </div>
                                    <div style={{ marginTop: 10, fontSize: 12, opacity: 0.62 }}>
                                        最近更新：{formatTime(selectedItem.updatedAt || selectedItem.createdAt) || '未知'}
                                    </div>
                                </div>
                            </div>
                            <button
                                type="button"
                                onClick={() => void handleInstall(selectedItem)}
                                disabled={selectedItem.installed || installing === selectedItem.id}
                                data-testid="analytics-marketplace-install"
                                style={{
                                    marginTop: 18,
                                    width: '100%',
                                    padding: '12px 14px',
                                    borderRadius: 12,
                                    border: 'none',
                                    background: selectedItem.installed
                                        ? 'rgba(148,163,184,0.18)'
                                        : 'linear-gradient(135deg, #2563eb, #0ea5e9)',
                                    color: selectedItem.installed ? 'rgba(15,23,42,0.62)' : '#fff',
                                    fontSize: 13,
                                    fontWeight: 700,
                                    cursor: selectedItem.installed ? 'default' : 'pointer',
                                }}
                            >
                                {installing === selectedItem.id ? '安装中...' : selectedItem.installed ? '已安装，可直接使用' : '安装到当前空间'}
                            </button>
                        </>
                    ) : (
                        <div style={{ fontSize: 13, opacity: 0.64, lineHeight: 1.7 }}>
                            选择左侧卡片后，这里会显示更完整的安装说明、作者、标签与状态信息。
                        </div>
                    )}
                </aside>
            </div>
        </div>
    );
}
