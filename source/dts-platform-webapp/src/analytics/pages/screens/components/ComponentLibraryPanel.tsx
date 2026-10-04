import { useEffect, useMemo, useRef, useState } from 'react';
import { useDrag } from 'react-dnd';
import {
    Box,
    ChartColumn,
    ChartLine,
    ChartPie,
    ChevronDown,
    CircleDot,
    Clock3,
    Container,
    Gauge,
    Image,
    Layers,
    ListFilter,
    Map as MapIcon,
    Maximize2,
    Minimize2,
    Plug,
    Star,
    Table2,
    Type,
    Video,
} from 'lucide-react';
import { componentLibrary } from '../componentLibrary';
import { useScreen } from '../ScreenContext';
import type { ScreenPluginManifest } from '../../../api/analyticsApi';
import { mapPluginManifestToCategory } from '../componentLibraryPlugins';
import type { ComponentCategory, ComponentItem } from '../types';
import { loadScreenPluginManifests, SCREEN_PLUGIN_MANIFESTS_UPDATED_EVENT } from '../plugins/manifestLoader';

const LIBRARY_ICON_SIZE = 14;
const ITEM_ICON_SIZE = 18;

interface DraggableComponentItemProps {
    item: ComponentItem;
    favorite: boolean;
    disabled?: boolean;
    onToggleFavorite: (item: ComponentItem) => void;
    onUse: (item: ComponentItem) => void;
}

function renderLibraryIcon(item: Pick<ComponentItem, 'type' | 'name'> | ComponentCategory, size = LIBRARY_ICON_SIZE) {
    const type = 'type' in item ? String(item.type || '') : '';
    const name = String(item.name || '');
    const props = { size, strokeWidth: 1.8 };
    if (type.includes('line')) return <ChartLine {...props} />;
    if (type.includes('bar') || type.includes('gantt') || type.includes('ranking')) return <ChartColumn {...props} />;
    if (type.includes('pie') || type.includes('funnel')) return <ChartPie {...props} />;
    if (type.includes('gauge') || type.includes('progress') || type.includes('water') || type.includes('digital')) return <Gauge {...props} />;
    if (type.includes('map') || type.includes('flyline') || name.includes('地图') || name.includes('3D')) return <MapIcon {...props} />;
    if (type.includes('table') || type.includes('board') || name.includes('数据展示')) return <Table2 {...props} />;
    if (type.includes('filter') || name.includes('筛选')) return <ListFilter {...props} />;
    if (type.includes('text') || type.includes('title') || type.includes('datetime') || name.includes('文本')) return <Type {...props} />;
    if (type.includes('image') || name.includes('媒体')) return <Image {...props} />;
    if (type.includes('video')) return <Video {...props} />;
    if (type.includes('container') || name.includes('边框')) return <Container {...props} />;
    if (type.includes('shape') || type.includes('decoration') || name.includes('装饰')) return <Layers {...props} />;
    if (name.includes('图表')) return <ChartColumn {...props} />;
    return type ? <CircleDot {...props} /> : <Box {...props} />;
}

function DraggableComponentItem({ item, favorite, disabled, onToggleFavorite, onUse }: DraggableComponentItemProps) {
    const [{ isDragging }, drag] = useDrag(() => ({
        type: 'COMPONENT',
        canDrag: !disabled,
        item: () => {
            if (disabled) return null;
            onUse(item);
            return item;
        },
        collect: (monitor) => ({
            isDragging: monitor.isDragging(),
        }),
    }), [disabled, item, onUse]);

    return (
        <div
            ref={(node) => {
                drag(node);
            }}
            data-testid={`analytics-screen-library-item-${String(item.type)}`}
            aria-disabled={disabled}
            className={`relative flex flex-col items-center pt-2.5 px-1 pb-1.5 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-lg overflow-hidden transition-all duration-150 ${disabled ? 'cursor-not-allowed' : 'cursor-grab hover:border-[var(--color-primary)] hover:shadow-[0_2px_8px_rgba(80,158,227,0.12)] hover:-translate-y-px active:cursor-grabbing active:translate-y-0'}`}
            style={{ opacity: disabled ? 0.45 : (isDragging ? 0.5 : 1) }}
        >
            <button
                type="button"
                className="absolute top-0.5 right-0.5 border-none bg-transparent text-amber-400 cursor-pointer leading-none p-0.5 z-[1]"
                onClick={(e) => {
                    e.stopPropagation();
                    e.preventDefault();
                    onToggleFavorite(item);
                }}
                title={favorite ? '取消常用' : '加入常用'}
            >
                <Star size={12} strokeWidth={1.8} fill={favorite ? 'currentColor' : 'none'} />
            </button>
            <div className="w-9 h-9 flex items-center justify-center text-[var(--color-primary)] mb-0.5 bg-[var(--color-primary-light,rgba(80,158,227,0.08))] rounded-lg">
                {renderLibraryIcon(item, ITEM_ICON_SIZE)}
            </div>
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
    const { editorReadonly } = useScreen();
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
            const isPlugin = category.name.includes('@');
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
        return { name: '常用组件', icon: '', items };
    }, [componentIndex, favorites]);

    const recentCategory = useMemo<ComponentCategory | null>(() => {
        const items = recent
            .slice(0, 5)
            .map((key) => componentIndex.get(key))
            .filter((item): item is ComponentItem => Boolean(item));
        if (items.length === 0) {
            return null;
        }
        return { name: '最近使用', icon: '', items };
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
                        <button type="button" className="component-library-scope-btn inline-flex items-center justify-center" onClick={expandVisibleCategories} title="展开分类">
                            <Maximize2 size={LIBRARY_ICON_SIZE} strokeWidth={1.8} />
                        </button>
                        <button type="button" className="component-library-scope-btn inline-flex items-center justify-center" onClick={collapseVisibleCategories} title="收起分类">
                            <Minimize2 size={LIBRARY_ICON_SIZE} strokeWidth={1.8} />
                        </button>
                    </div>
                </div>
                {editorReadonly && (
                    <div
                        data-testid="analytics-screen-library-readonly-note"
                        className="mt-1.5 rounded border px-2 py-1 text-[11px]"
                        style={{
                            color: '#fbbf24',
                            borderColor: 'rgba(251,191,36,0.28)',
                            background: 'rgba(251,191,36,0.08)',
                        }}
                    >
                        只读模式下不可拖入新组件。
                    </div>
                )}
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
                    <button type="button" className={`component-library-scope-btn inline-flex items-center gap-1 ${activeScope === 'favorites' ? 'active' : ''}`} onClick={() => setActiveScope('favorites')} title="常用组件">
                        <Star size={LIBRARY_ICON_SIZE} strokeWidth={1.8} />
                        {favorites.length}
                    </button>
                    <button type="button" className={`component-library-scope-btn inline-flex items-center gap-1 ${activeScope === 'recent' ? 'active' : ''}`} onClick={() => setActiveScope('recent')} title="最近使用">
                        <Clock3 size={LIBRARY_ICON_SIZE} strokeWidth={1.8} />
                        {recent.length}
                    </button>
                    <button type="button" className={`component-library-scope-btn inline-flex items-center justify-center ${activeScope === 'plugin' ? 'active' : ''}`} onClick={() => setActiveScope('plugin')} title="插件组件">
                        <Plug size={LIBRARY_ICON_SIZE} strokeWidth={1.8} />
                    </button>
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
                            <span className="inline-flex items-center gap-1.5">
                                <Star size={LIBRARY_ICON_SIZE} strokeWidth={1.8} />
                                {filteredFavoriteCategory.name}
                            </span>
                        </div>
                        <div className="grid grid-cols-3 gap-1.5">
                            {filteredFavoriteCategory.items.map((item: ComponentItem, idx: number) => (
                                <DraggableComponentItem
                                    key={`favorite-${item.name}-${idx}`}
                                    item={item}
                                    favorite={favoriteSet.has(toComponentKey(item))}
                                    disabled={editorReadonly}
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
                            <span className="inline-flex items-center gap-1.5">
                                <Clock3 size={LIBRARY_ICON_SIZE} strokeWidth={1.8} />
                                {filteredRecentCategory.name}
                            </span>
                        </div>
                        <div className="grid grid-cols-3 gap-1.5">
                            {filteredRecentCategory.items.map((item: ComponentItem, idx: number) => (
                                <DraggableComponentItem
                                    key={`recent-${item.name}-${idx}`}
                                    item={item}
                                    favorite={favoriteSet.has(toComponentKey(item))}
                                    disabled={editorReadonly}
                                    onToggleFavorite={handleToggleFavorite}
                                    onUse={handleUse}
                                />
                            ))}
                        </div>
                    </div>
                )}
                {tagFilteredCategories.map((category: ComponentCategory) => (
                    <div key={category.name} className="mb-4">
                        <button
                            type="button"
                            className="w-full text-xs font-semibold text-[var(--color-text-secondary)] mb-2 uppercase tracking-wide cursor-pointer bg-transparent border-0 p-0 flex items-center gap-1.5 text-left"
                            onClick={() => toggleCategory(category.name)}
                        >
                            {collapsedCategories.includes(category.name)
                                ? <ChevronDown size={LIBRARY_ICON_SIZE} strokeWidth={1.8} style={{ transform: 'rotate(-90deg)' }} />
                                : <ChevronDown size={LIBRARY_ICON_SIZE} strokeWidth={1.8} />}
                            {renderLibraryIcon(category)}
                            <span className="truncate">{category.name}</span>
                        </button>
                        {!collapsedCategories.includes(category.name) ? (
                            <div className="grid grid-cols-3 gap-1.5">
                                {category.items.map((item: ComponentItem, idx: number) => (
                                    <DraggableComponentItem
                                        key={`${category.name}-${item.name}-${idx}`}
                                        item={item}
                                        favorite={favoriteSet.has(toComponentKey(item))}
                                        disabled={editorReadonly}
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
