import { useEffect, useMemo, useRef, useState } from 'react';
import { useDrag } from 'react-dnd';
import { componentLibrary } from '../componentLibrary';
import type { ScreenPluginManifest } from '../../../api/analyticsApi';
import { mapPluginManifestToCategory } from '../componentLibraryPlugins';
import type { ComponentCategory, ComponentItem } from '../types';
import { loadScreenPluginManifests, SCREEN_PLUGIN_MANIFESTS_UPDATED_EVENT } from '../plugins/manifestLoader';

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
            className="relative flex flex-col items-center pt-2.5 px-1 pb-1.5 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-lg cursor-grab overflow-hidden transition-all duration-150 hover:border-[var(--color-primary)] hover:shadow-[0_2px_8px_rgba(80,158,227,0.12)] hover:-translate-y-px active:cursor-grabbing active:translate-y-0"
            style={{ opacity: isDragging ? 0.5 : 1 }}
        >
            <button
                type="button"
                className="absolute top-0.5 right-0.5 border-none bg-transparent text-amber-400 cursor-pointer text-[10px] leading-none p-0.5 z-[1]"
                onClick={(e) => {
                    e.stopPropagation();
                    e.preventDefault();
                    onToggleFavorite(item);
                }}
                title={favorite ? '取消常用' : '加入常用'}
            >
                {favorite ? '★' : '☆'}
            </button>
            <div className="w-9 h-9 flex items-center justify-center text-[22px] text-[var(--color-primary)] mb-0.5 bg-[var(--color-primary-light,rgba(80,158,227,0.08))] rounded-lg">{item.icon}</div>
            <span className="text-[10px] text-[var(--color-text-secondary)] text-center leading-tight max-w-full overflow-hidden text-ellipsis whitespace-nowrap">{item.name}</span>
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
        const loadPlugins = (force = false) => {
            loadScreenPluginManifests(force)
                .then((data) => {
                    setPlugins(data);
                    setPluginError(null);
                })
                .catch((error) => {
                    console.error('Failed to load screen plugins:', error);
                    setPluginError('插件清单加载失败');
                    setPlugins([]);
                });
        };

        loadPlugins();
        const handlePluginCatalogUpdated = () => {
            loadPlugins(true);
        };
        window.addEventListener(SCREEN_PLUGIN_MANIFESTS_UPDATED_EVENT, handlePluginCatalogUpdated);
        return () => {
            window.removeEventListener(SCREEN_PLUGIN_MANIFESTS_UPDATED_EVENT, handlePluginCatalogUpdated);
        };
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
            .map(mapPluginManifestToCategory)
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
            .slice(0, 5)
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

    const CATEGORY_TAGS = ['图表', '边框', '装饰', '数据展示', '文本', '筛选器', '媒体', '3D'] as const;
    const [activeTag, setActiveTag] = useState<string>('');

    const tagFilteredCategories = useMemo(() => {
        if (!activeTag) return visibleCategories;
        return visibleCategories.filter(c => c.name.includes(activeTag));
    }, [visibleCategories, activeTag]);

    return (
        <div className="w-[280px] bg-[var(--color-surface-secondary)] border-r border-[var(--color-border)] flex flex-col overflow-hidden">
            <div className="px-3 py-2.5 border-b border-[var(--color-border)] shrink-0">
                <div className="flex items-center justify-between">
                    <h3 className="m-0 text-sm font-semibold text-[var(--color-text-primary)]">组件库</h3>
                    <div className="flex gap-1 text-[10px]">
                        <button type="button" className="component-library-scope-btn" onClick={expandVisibleCategories} title="展开分类">▼</button>
                        <button type="button" className="component-library-scope-btn" onClick={collapseVisibleCategories} title="收起分类">▲</button>
                    </div>
                </div>
                <input
                    ref={searchInputRef}
                    type="text"
                    className="property-input mt-1.5 w-full"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="搜索组件（按 / 聚焦）"
                />
                <div className="flex flex-wrap gap-1.5 mt-1.5">
                    <button type="button" className={`component-library-scope-btn ${activeScope === 'all' ? 'active' : ''}`} onClick={() => setActiveScope('all')}>全部</button>
                    <button type="button" className={`component-library-scope-btn ${activeScope === 'favorites' ? 'active' : ''}`} onClick={() => setActiveScope('favorites')}>★{favorites.length}</button>
                    <button type="button" className={`component-library-scope-btn ${activeScope === 'recent' ? 'active' : ''}`} onClick={() => setActiveScope('recent')}>⏱{recent.length}</button>
                    <button type="button" className={`component-library-scope-btn ${activeScope === 'plugin' ? 'active' : ''}`} onClick={() => setActiveScope('plugin')}>🔌</button>
                </div>
                <div className="component-library-tags flex flex-wrap gap-1 mt-1">
                    {CATEGORY_TAGS.map(tag => (
                        <button
                            key={tag}
                            type="button"
                            className={`component-library-tag ${activeTag === tag ? 'active' : ''}`}
                            onClick={() => setActiveTag(activeTag === tag ? '' : tag)}
                        >
                            {tag}
                        </button>
                    ))}
                </div>
            </div>
            <div className="flex-1 overflow-y-auto p-3">
                {pluginError && (
                    <div className="text-amber-400 text-xs mb-2">{pluginError}</div>
                )}
                {activeScope === 'all' && filteredFavoriteCategory && (
                    <div className="mb-4">
                        <div className="text-xs font-semibold text-[var(--color-text-secondary)] mb-2 uppercase tracking-wide">
                            {filteredFavoriteCategory.icon} {filteredFavoriteCategory.name}
                        </div>
                        <div className="grid grid-cols-3 gap-1.5">
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
                    <div className="mb-4">
                        <div className="text-xs font-semibold text-[var(--color-text-secondary)] mb-2 uppercase tracking-wide">
                            {filteredRecentCategory.icon} {filteredRecentCategory.name}
                        </div>
                        <div className="grid grid-cols-3 gap-1.5">
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
                {tagFilteredCategories.map((category: ComponentCategory) => (
                    <div key={category.name} className="mb-4">
                        <div className="text-xs font-semibold text-[var(--color-text-secondary)] mb-2 uppercase tracking-wide cursor-pointer" onClick={() => toggleCategory(category.name)}>
                            {collapsedCategories.includes(category.name) ? '▸' : '▾'} {category.icon} {category.name}
                        </div>
                        {!collapsedCategories.includes(category.name) ? (
                            <div className="grid grid-cols-3 gap-1.5">
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
                    <div className="text-xs text-[var(--color-text-secondary)] py-1.5">
                        未找到匹配组件
                    </div>
                ) : null}
            </div>
        </div>
    );
}
