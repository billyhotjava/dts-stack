import { useEffect, useMemo, useRef, useState } from 'react';
import { useDrag } from 'react-dnd';
import type { ScreenPluginManifest } from '../../../api/analyticsApi';
import { componentLibrary } from '../componentLibrary';
import type { ComponentCategory, ComponentItem, ComponentType } from '../types';
import { loadScreenPluginManifests } from '../plugins/manifestLoader';

interface DraggableComponentItemProps {
    item: ComponentItem;
    favorite: boolean;
    onToggleFavorite: (item: ComponentItem) => void;
    onUse: (item: ComponentItem) => void;
}

function DraggableComponentItem({ item, favorite, onToggleFavorite, onUse }: DraggableComponentItemProps) {
    const [{ isDragging }, drag] = useDrag(() => ({
        type: 'COMPONENT',
        item: () => {
            onUse(item);
            return item;
        },
        collect: (monitor) => ({
            isDragging: monitor.isDragging(),
        }),
    }), [item, onUse]);

    return (
        <div
            ref={(node) => {
                drag(node);
            }}
            className="component-item"
            style={{ opacity: isDragging ? 0.5 : 1 }}
        >
            <button
                type="button"
                className="component-favorite-btn"
                onClick={(e) => {
                    e.stopPropagation();
                    e.preventDefault();
                    onToggleFavorite(item);
                }}
                title={favorite ? '取消常用' : '加入常用'}
            >
                {favorite ? '★' : '☆'}
            </button>
            <div className="component-item-icon">{item.icon}</div>
            <span className="component-item-name">{item.name}</span>
        </div>
    );
}

const FAVORITE_STORAGE_KEY = 'dts.analytics.screen.component-favorites.v1';
const RECENT_STORAGE_KEY = 'dts.analytics.screen.component-recent.v1';
const COLLAPSED_STORAGE_KEY = 'dts.analytics.screen.component-collapsed-categories.v1';

function toComponentKey(item: ComponentItem): string {
    const cfg = item.defaultConfig as Record<string, unknown> | undefined;
    const plugin = (cfg?.__plugin && typeof cfg.__plugin === 'object')
        ? (cfg.__plugin as Record<string, unknown>)
        : null;
    const pluginId = plugin ? String(plugin.pluginId || '').trim() : '';
    const componentId = plugin ? String(plugin.componentId || '').trim() : '';
    const version = plugin ? String(plugin.version || '').trim() : '';
    if (pluginId && componentId) {
        return `plugin:${pluginId}:${componentId}@${version || 'dev'}`;
    }
    return `builtin:${item.type}::${item.name}`;
}

function mapPluginToCategory(plugin: ScreenPluginManifest): ComponentCategory | null {
    const list = Array.isArray(plugin.components) ? plugin.components : [];
    if (list.length === 0) {
        return null;
    }

    const items: ComponentItem[] = [];
    for (const component of list) {
        const baseTypeRaw = String(component.baseType || '').trim();
        if (!baseTypeRaw) {
            continue;
        }

        items.push({
            type: baseTypeRaw as ComponentType,
            name: component.name || component.id,
            icon: component.icon || '🔌',
            defaultWidth: component.defaultWidth || 360,
            defaultHeight: component.defaultHeight || 240,
            defaultConfig: {
                ...((component.defaultConfig || {}) as Record<string, unknown>),
                __plugin: {
                    pluginId: plugin.id,
                    componentId: component.id,
                    version: plugin.version,
                },
                __pluginPropertySchema: component.propertySchema || null,
                __pluginDataContract: component.dataContract || null,
            },
        });
    }

    if (items.length === 0) {
        return null;
    }

    return {
        name: `${plugin.name || plugin.id} @${plugin.version || 'dev'}`,
        icon: '🔌',
        items,
    };
}

export function ComponentLibraryPanel() {
    const searchInputRef = useRef<HTMLInputElement | null>(null);
    const [plugins, setPlugins] = useState<ScreenPluginManifest[]>([]);
    const [pluginError, setPluginError] = useState<string | null>(null);
    const [query, setQuery] = useState('');
    const [favorites, setFavorites] = useState<string[]>([]);
    const [recent, setRecent] = useState<string[]>([]);
    const [activeScope, setActiveScope] = useState<'all' | 'builtin' | 'plugin' | 'favorites' | 'recent'>('all');
    const [collapsedCategories, setCollapsedCategories] = useState<string[]>([]);

    useEffect(() => {
        loadScreenPluginManifests()
            .then((data) => {
                setPlugins(data);
                setPluginError(null);
            })
            .catch((error) => {
                console.error('Failed to load screen plugins:', error);
                setPluginError('插件清单加载失败');
                setPlugins([]);
            });
    }, []);

    useEffect(() => {
        try {
            const raw = localStorage.getItem(FAVORITE_STORAGE_KEY);
            if (!raw) return;
            const parsed = JSON.parse(raw) as unknown;
            if (Array.isArray(parsed)) {
                setFavorites(parsed.filter((item) => typeof item === 'string'));
            }
        } catch {
            // ignore invalid local cache
        }
    }, []);

    useEffect(() => {
        try {
            const raw = localStorage.getItem(RECENT_STORAGE_KEY);
            if (!raw) return;
            const parsed = JSON.parse(raw) as unknown;
            if (Array.isArray(parsed)) {
                setRecent(parsed.filter((item) => typeof item === 'string'));
            }
        } catch {
            // ignore invalid local cache
        }
    }, []);
    useEffect(() => {
        try {
            const raw = localStorage.getItem(COLLAPSED_STORAGE_KEY);
            if (!raw) return;
            const parsed = JSON.parse(raw) as unknown;
            if (Array.isArray(parsed)) {
                setCollapsedCategories(parsed.filter((item) => typeof item === 'string'));
            }
        } catch {
            // ignore invalid local cache
        }
    }, []);
    useEffect(() => {
        try {
            localStorage.setItem(COLLAPSED_STORAGE_KEY, JSON.stringify(collapsedCategories));
        } catch {
            // ignore localStorage failure
        }
    }, [collapsedCategories]);
    useEffect(() => {
        const isTypingTarget = (target: EventTarget | null): boolean => {
            const node = target as HTMLElement | null;
            if (!node) return false;
            const tag = node.tagName;
            if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
            return node.isContentEditable;
        };
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key !== '/') return;
            if (event.ctrlKey || event.metaKey || event.altKey) return;
            if (isTypingTarget(event.target)) return;
            event.preventDefault();
            searchInputRef.current?.focus();
            searchInputRef.current?.select();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, []);

    const persistFavorites = (next: string[]) => {
        setFavorites(next);
        try {
            localStorage.setItem(FAVORITE_STORAGE_KEY, JSON.stringify(next));
        } catch {
            // ignore localStorage failure
        }
    };

    const persistRecent = (next: string[]) => {
        setRecent(next);
        try {
            localStorage.setItem(RECENT_STORAGE_KEY, JSON.stringify(next));
        } catch {
            // ignore localStorage failure
        }
    };

    const mergedCategories = useMemo(() => {
        const pluginCategories = plugins
            .map(mapPluginToCategory)
            .filter((item): item is ComponentCategory => item !== null);
        return [...componentLibrary, ...pluginCategories];
    }, [plugins]);

    const componentIndex = useMemo(() => {
        const map = new Map<string, ComponentItem>();
        for (const category of mergedCategories) {
            for (const item of category.items) {
                map.set(toComponentKey(item), item);
            }
        }
        return map;
    }, [mergedCategories]);

    const queryText = query.trim().toLowerCase();
    const filteredCategories = useMemo(() => {
        const byQuery = !queryText ? mergedCategories : mergedCategories
            .map((category) => ({
                ...category,
                items: category.items.filter((item) => {
                    const name = item.name.toLowerCase();
                    const type = String(item.type).toLowerCase();
                    return name.includes(queryText) || type.includes(queryText);
                }),
            }))
            .filter((category) => category.items.length > 0);
        if (activeScope === 'all' || activeScope === 'favorites' || activeScope === 'recent') {
            return byQuery;
        }
        return byQuery.filter((category) => {
            const isPlugin = category.icon === '🔌' || category.name.includes('@');
            return activeScope === 'plugin' ? isPlugin : !isPlugin;
        });
    }, [activeScope, mergedCategories, queryText]);

    const favoriteSet = useMemo(() => new Set(favorites), [favorites]);
    const favoriteCategory = useMemo<ComponentCategory | null>(() => {
        const items = favorites
            .map((key) => componentIndex.get(key))
            .filter((item): item is ComponentItem => Boolean(item));
        if (items.length === 0) {
            return null;
        }
        return { name: '常用组件', icon: '⭐', items };
    }, [componentIndex, favorites]);

    const recentCategory = useMemo<ComponentCategory | null>(() => {
        const items = recent
            .map((key) => componentIndex.get(key))
            .filter((item): item is ComponentItem => Boolean(item));
        if (items.length === 0) {
            return null;
        }
        return { name: '最近使用', icon: '🕘', items };
    }, [componentIndex, recent]);

    const handleToggleFavorite = (item: ComponentItem) => {
        const key = toComponentKey(item);
        if (favoriteSet.has(key)) {
            persistFavorites(favorites.filter((entry) => entry !== key));
            return;
        }
        persistFavorites([key, ...favorites].slice(0, 24));
    };

    const handleUse = (item: ComponentItem) => {
        const key = toComponentKey(item);
        const next = [key, ...recent.filter((entry) => entry !== key)].slice(0, 16);
        persistRecent(next);
    };

    const filterCategoryByQuery = (category: ComponentCategory | null): ComponentCategory | null => {
        if (!category) return null;
        if (!queryText) return category;
        const items = category.items.filter((item) => {
            const name = item.name.toLowerCase();
            const type = String(item.type).toLowerCase();
            return name.includes(queryText) || type.includes(queryText);
        });
        if (items.length === 0) return null;
        return { ...category, items };
    };
    const filteredFavoriteCategory = filterCategoryByQuery(favoriteCategory);
    const filteredRecentCategory = filterCategoryByQuery(recentCategory);
    const visibleCategories = (() => {
        if (activeScope === 'favorites') {
            return filteredFavoriteCategory ? [filteredFavoriteCategory] : [];
        }
        if (activeScope === 'recent') {
            return filteredRecentCategory ? [filteredRecentCategory] : [];
        }
        return filteredCategories;
    })();

    const toggleCategory = (name: string) => {
        setCollapsedCategories((prev) => {
            if (prev.includes(name)) {
                return prev.filter((item) => item !== name);
            }
            return [...prev, name];
        });
    };
    const visibleCategoryNames = visibleCategories.map((item) => item.name);
    const collapseVisibleCategories = () => {
        setCollapsedCategories((prev) => Array.from(new Set([...prev, ...visibleCategoryNames])));
    };
    const expandVisibleCategories = () => {
        setCollapsedCategories((prev) => prev.filter((item) => !visibleCategoryNames.includes(item)));
    };

    return (
        <div className="component-library">
            <div className="component-library-header">
                <h3>组件库</h3>
                <div style={{ fontSize: 11, opacity: 0.7 }}>插件: {plugins.length}</div>
                <div className="component-library-scope-row">
                    <button type="button" className={`component-library-scope-btn ${activeScope === 'all' ? 'active' : ''}`} onClick={() => setActiveScope('all')}>全部</button>
                    <button type="button" className={`component-library-scope-btn ${activeScope === 'builtin' ? 'active' : ''}`} onClick={() => setActiveScope('builtin')}>内置</button>
                    <button type="button" className={`component-library-scope-btn ${activeScope === 'plugin' ? 'active' : ''}`} onClick={() => setActiveScope('plugin')}>插件</button>
                    <button type="button" className={`component-library-scope-btn ${activeScope === 'favorites' ? 'active' : ''}`} onClick={() => setActiveScope('favorites')}>常用({favorites.length})</button>
                    <button type="button" className={`component-library-scope-btn ${activeScope === 'recent' ? 'active' : ''}`} onClick={() => setActiveScope('recent')}>最近({recent.length})</button>
                </div>
                <div className="component-library-scope-row">
                    <button type="button" className="component-library-scope-btn" onClick={() => persistFavorites([])} disabled={favorites.length === 0}>清空常用</button>
                    <button type="button" className="component-library-scope-btn" onClick={() => persistRecent([])} disabled={recent.length === 0}>清空最近</button>
                </div>
                <div className="component-library-scope-row">
                    <button
                        type="button"
                        className="component-library-scope-btn"
                        onClick={expandVisibleCategories}
                        disabled={visibleCategoryNames.length === 0}
                    >
                        展开分类
                    </button>
                    <button
                        type="button"
                        className="component-library-scope-btn"
                        onClick={collapseVisibleCategories}
                        disabled={visibleCategoryNames.length === 0}
                    >
                        收起分类
                    </button>
                </div>
                <input
                    ref={searchInputRef}
                    type="text"
                    className="property-input"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="搜索组件（/）"
                    style={{ marginTop: 8, width: '100%' }}
                />
            </div>
            <div className="component-library-content">
                {pluginError && (
                    <div style={{ color: '#fbbf24', fontSize: 12, marginBottom: 8 }}>{pluginError}</div>
                )}
                {activeScope === 'all' && filteredFavoriteCategory && (
                    <div className="component-category">
                        <div className="component-category-title">
                            {filteredFavoriteCategory.icon} {filteredFavoriteCategory.name}
                        </div>
                        <div className="component-grid">
                            {filteredFavoriteCategory.items.map((item: ComponentItem, idx: number) => (
                                <DraggableComponentItem
                                    key={`favorite-${item.name}-${idx}`}
                                    item={item}
                                    favorite={favoriteSet.has(toComponentKey(item))}
                                    onToggleFavorite={handleToggleFavorite}
                                    onUse={handleUse}
                                />
                            ))}
                        </div>
                    </div>
                )}
                {activeScope === 'all' && filteredRecentCategory && (
                    <div className="component-category">
                        <div className="component-category-title">
                            {filteredRecentCategory.icon} {filteredRecentCategory.name}
                        </div>
                        <div className="component-grid">
                            {filteredRecentCategory.items.map((item: ComponentItem, idx: number) => (
                                <DraggableComponentItem
                                    key={`recent-${item.name}-${idx}`}
                                    item={item}
                                    favorite={favoriteSet.has(toComponentKey(item))}
                                    onToggleFavorite={handleToggleFavorite}
                                    onUse={handleUse}
                                />
                            ))}
                        </div>
                    </div>
                )}
                {visibleCategories.map((category: ComponentCategory) => (
                    <div key={category.name} className="component-category">
                        <div className="component-category-title" style={{ cursor: 'pointer' }} onClick={() => toggleCategory(category.name)}>
                            {collapsedCategories.includes(category.name) ? '▸' : '▾'} {category.icon} {category.name}
                        </div>
                        {!collapsedCategories.includes(category.name) ? (
                            <div className="component-grid">
                                {category.items.map((item: ComponentItem, idx: number) => (
                                    <DraggableComponentItem
                                        key={`${category.name}-${item.name}-${idx}`}
                                        item={item}
                                        favorite={favoriteSet.has(toComponentKey(item))}
                                        onToggleFavorite={handleToggleFavorite}
                                        onUse={handleUse}
                                    />
                                ))}
                            </div>
                        ) : null}
                    </div>
                ))}
                {visibleCategories.length === 0 ? (
                    <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', padding: '6px 0' }}>
                        未找到匹配组件
                    </div>
                ) : null}
            </div>
        </div>
    );
}
