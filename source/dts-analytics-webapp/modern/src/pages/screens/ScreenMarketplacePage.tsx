/**
 * ScreenMarketplacePage — 组件/模板市场
 *
 * Browse, search, and install community-shared components and templates.
 */
import { useEffect, useState, useCallback, useMemo } from 'react';
import { analyticsApi } from '../../api/analyticsApi';

/* ---------- types ---------- */

interface MarketplaceItem {
    id: string;
    name: string;
    description?: string;
    author?: string;
    version?: string;
    category?: string;
    tags?: string[];
    thumbnailUrl?: string;
    downloads?: number;
    createdAt?: string;
    installed?: boolean;
}

type TabKey = 'components' | 'templates';
type CategoryKey = 'all' | 'chart' | 'decoration' | 'container' | 'metric' | 'other';

const CATEGORIES: Array<{ key: CategoryKey; label: string }> = [
    { key: 'all', label: '全部' },
    { key: 'chart', label: '图表' },
    { key: 'decoration', label: '装饰' },
    { key: 'container', label: '容器' },
    { key: 'metric', label: '指标' },
    { key: 'other', label: '其他' },
];

/* ---------- component ---------- */

export default function ScreenMarketplacePage() {
    const [activeTab, setActiveTab] = useState<TabKey>('components');
    const [search, setSearch] = useState('');
    const [category, setCategory] = useState<CategoryKey>('all');
    const [items, setItems] = useState<MarketplaceItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [installing, setInstalling] = useState<string | null>(null);

    const loadItems = useCallback(async () => {
        setLoading(true);
        try {
            const params = {
                search: search || undefined,
                category: category !== 'all' ? category : undefined,
            };
            const result = activeTab === 'components'
                ? await analyticsApi.listMarketplaceComponents(params)
                : await analyticsApi.listMarketplaceTemplates(params);
            setItems(Array.isArray(result) ? result as MarketplaceItem[] : []);
        } catch {
            setItems([]);
        } finally {
            setLoading(false);
        }
    }, [activeTab, search, category]);

    useEffect(() => {
        loadItems();
    }, [loadItems]);

    const handleInstall = useCallback(async (item: MarketplaceItem) => {
        if (installing) return;
        setInstalling(item.id);
        try {
            if (activeTab === 'components') {
                await analyticsApi.installMarketplaceComponent(item.id);
            } else {
                await analyticsApi.installMarketplaceTemplate(item.id);
            }
            alert(`${item.name} 安装成功`);
            loadItems();
        } catch {
            alert('安装失败');
        } finally {
            setInstalling(null);
        }
    }, [activeTab, installing, loadItems]);

    const filteredItems = useMemo(() => {
        if (!search.trim()) return items;
        const lower = search.trim().toLowerCase();
        return items.filter(item =>
            item.name.toLowerCase().includes(lower)
            || (item.description || '').toLowerCase().includes(lower)
            || (item.tags || []).some(t => t.toLowerCase().includes(lower)),
        );
    }, [items, search]);

    return (
        <div style={{
            padding: 24,
            maxWidth: 1200,
            margin: '0 auto',
            fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        }}>
            {/* Header */}
            <div style={{ marginBottom: 24 }}>
                <h1 style={{ fontSize: 20, fontWeight: 700, margin: '0 0 8px 0' }}>组件与模板市场</h1>
                <p style={{ fontSize: 13, opacity: 0.6, margin: 0 }}>
                    浏览、搜索和安装社区共享的组件和模板
                </p>
            </div>

            {/* Tab bar */}
            <div style={{ display: 'flex', gap: 4, marginBottom: 16, borderBottom: '1px solid rgba(148,163,184,0.15)' }}>
                {(['components', 'templates'] as TabKey[]).map(tab => (
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
                        {tab === 'components' ? '组件' : '模板'}
                    </button>
                ))}
            </div>

            {/* Search + Category filter */}
            <div style={{ display: 'flex', gap: 10, marginBottom: 16, alignItems: 'center' }}>
                <input
                    type="text"
                    placeholder="搜索..."
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                    style={{
                        flex: 1,
                        padding: '8px 12px',
                        border: '1px solid rgba(148,163,184,0.3)',
                        borderRadius: 8,
                        background: 'rgba(148,163,184,0.06)',
                        color: 'inherit',
                        fontSize: 13,
                        outline: 'none',
                    }}
                />
                <div style={{ display: 'flex', gap: 4 }}>
                    {CATEGORIES.map(cat => (
                        <button
                            key={cat.key}
                            type="button"
                            onClick={() => setCategory(cat.key)}
                            style={{
                                padding: '6px 12px',
                                fontSize: 12,
                                borderRadius: 6,
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

            {/* Grid */}
            {loading && (
                <div style={{ textAlign: 'center', padding: 40, opacity: 0.6, fontSize: 13 }}>加载中...</div>
            )}
            {!loading && filteredItems.length === 0 && (
                <div style={{ textAlign: 'center', padding: 40, opacity: 0.5, fontSize: 13 }}>
                    {items.length === 0 ? '市场暂无内容，敬请期待' : '无匹配结果'}
                </div>
            )}
            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
                gap: 16,
            }}>
                {filteredItems.map(item => (
                    <div
                        key={item.id}
                        style={{
                            border: '1px solid rgba(148,163,184,0.2)',
                            borderRadius: 12,
                            overflow: 'hidden',
                            background: 'rgba(148,163,184,0.04)',
                            transition: 'box-shadow 0.2s',
                        }}
                    >
                        {/* Thumbnail */}
                        <div style={{
                            height: 140,
                            background: 'rgba(148,163,184,0.08)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            overflow: 'hidden',
                        }}>
                            {item.thumbnailUrl ? (
                                <img
                                    src={item.thumbnailUrl}
                                    alt={item.name}
                                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                />
                            ) : (
                                <span style={{ fontSize: 32, opacity: 0.3 }}>
                                    {activeTab === 'components' ? '[ ]' : '[ ]'}
                                </span>
                            )}
                        </div>

                        {/* Info */}
                        <div style={{ padding: '12px 14px' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                                <span style={{ fontSize: 14, fontWeight: 600 }}>{item.name}</span>
                                {item.version && (
                                    <span style={{ fontSize: 10, opacity: 0.5 }}>v{item.version}</span>
                                )}
                            </div>
                            {item.description && (
                                <div style={{ fontSize: 12, opacity: 0.6, marginBottom: 8, lineHeight: 1.4 }}>
                                    {item.description.length > 80 ? item.description.slice(0, 80) + '...' : item.description}
                                </div>
                            )}
                            {/* Tags */}
                            {item.tags && item.tags.length > 0 && (
                                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginBottom: 8 }}>
                                    {item.tags.slice(0, 4).map(tag => (
                                        <span
                                            key={tag}
                                            style={{
                                                fontSize: 10,
                                                padding: '1px 6px',
                                                borderRadius: 999,
                                                border: '1px solid rgba(148,163,184,0.2)',
                                                opacity: 0.7,
                                            }}
                                        >
                                            {tag}
                                        </span>
                                    ))}
                                </div>
                            )}
                            {/* Footer */}
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <span style={{ fontSize: 11, opacity: 0.5 }}>
                                    {item.author || '匿名'}
                                    {item.downloads != null ? ` · ${item.downloads} 次安装` : ''}
                                </span>
                                <button
                                    type="button"
                                    onClick={() => handleInstall(item)}
                                    disabled={item.installed || installing === item.id}
                                    style={{
                                        padding: '4px 12px',
                                        fontSize: 12,
                                        borderRadius: 6,
                                        border: 'none',
                                        background: item.installed ? 'rgba(148,163,184,0.15)' : 'var(--color-primary, #3b82f6)',
                                        color: item.installed ? 'inherit' : '#fff',
                                        cursor: item.installed ? 'default' : 'pointer',
                                        opacity: item.installed ? 0.6 : 1,
                                    }}
                                >
                                    {installing === item.id ? '安装中...' : item.installed ? '已安装' : '安装'}
                                </button>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
