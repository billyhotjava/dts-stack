import { useEffect, useMemo, useState } from 'react';
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
    const [plugins, setPlugins] = useState<ScreenPluginManifest[]>([]);
    const [pluginError, setPluginError] = useState<string | null>(null);
    const [query, setQuery] = useState('');
    const [favorites, setFavorites] = useState<string[]>([]);
    const [recent, setRecent] = useState<string[]>([]);

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
        if (!queryText) return mergedCategories;
        return mergedCategories
            .map((category) => ({
                ...category,
                items: category.items.filter((item) => {
                    const name = item.name.toLowerCase();
                    const type = String(item.type).toLowerCase();
                    return name.includes(queryText) || type.includes(queryText);
                }),
            }))
            .filter((category) => category.items.length > 0);
    }, [mergedCategories, queryText]);

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

    return (
        <div className="component-library">
            <div className="component-library-header">
                <h3>组件库</h3>
                <div style={{ fontSize: 11, opacity: 0.7 }}>插件: {plugins.length}</div>
                <input
                    type="text"
                    className="property-input"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="搜索组件"
                    style={{ marginTop: 8, width: '100%' }}
                />
            </div>
            <div className="component-library-content">
                {pluginError && (
                    <div style={{ color: '#fbbf24', fontSize: 12, marginBottom: 8 }}>{pluginError}</div>
                )}
                {favoriteCategory && (
                    <div className="component-category">
                        <div className="component-category-title">
                            {favoriteCategory.icon} {favoriteCategory.name}
                        </div>
                        <div className="component-grid">
                            {favoriteCategory.items.map((item: ComponentItem, idx: number) => (
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
                {recentCategory && (
                    <div className="component-category">
                        <div className="component-category-title">
                            {recentCategory.icon} {recentCategory.name}
                        </div>
                        <div className="component-grid">
                            {recentCategory.items.map((item: ComponentItem, idx: number) => (
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
                {filteredCategories.map((category: ComponentCategory) => (
                    <div key={category.name} className="component-category">
                        <div className="component-category-title">
                            {category.icon} {category.name}
                        </div>
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
                    </div>
                ))}
            </div>
        </div>
    );
}
