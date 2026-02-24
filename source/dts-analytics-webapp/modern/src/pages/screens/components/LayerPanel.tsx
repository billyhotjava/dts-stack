import { useEffect, useMemo, useState } from 'react';
import { useScreen } from '../ScreenContext';

export function LayerPanel() {
    const { state, dispatch, selectComponents } = useScreen();
    const { config, selectedIds } = state;
    const [keyword, setKeyword] = useState('');
    const [bulkAction, setBulkAction] = useState<'show' | 'hide' | 'lock' | 'unlock' | 'top' | 'bottom'>('show');
    const [openMenuId, setOpenMenuId] = useState<string | null>(null);

    useEffect(() => {
        const handlePointerDown = (event: MouseEvent) => {
            const target = event.target as Element | null;
            if (!target) {
                setOpenMenuId(null);
                return;
            }
            if (target.closest('.layer-item-actions')) {
                return;
            }
            setOpenMenuId(null);
        };
        const handleEscape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                setOpenMenuId(null);
            }
        };
        window.addEventListener('mousedown', handlePointerDown);
        window.addEventListener('keydown', handleEscape);
        return () => {
            window.removeEventListener('mousedown', handlePointerDown);
            window.removeEventListener('keydown', handleEscape);
        };
    }, []);

    const componentMap = new Map(config.components.map((item) => [item.id, item]));
    const visited = new Set<string>();

    const topLevelComponents = config.components
        .filter((item) => !item.parentContainerId || !componentMap.has(item.parentContainerId))
        .sort((a, b) => b.zIndex - a.zIndex);

    const layered: Array<{ component: typeof config.components[number]; depth: number }> = [];
    const walk = (component: typeof config.components[number], depth: number) => {
        if (visited.has(component.id)) return;
        visited.add(component.id);
        layered.push({ component, depth });
        if (component.type !== 'container') {
            return;
        }
        const children = config.components
            .filter((item) => item.parentContainerId === component.id)
            .sort((a, b) => b.zIndex - a.zIndex);
        for (const child of children) {
            walk(child, depth + 1);
        }
    };
    for (const component of topLevelComponents) {
        walk(component, 0);
    }
    for (const component of config.components.sort((a, b) => b.zIndex - a.zIndex)) {
        walk(component, 0);
    }

    const handleLayerClick = (id: string, e: React.MouseEvent) => {
        if (e.ctrlKey || e.metaKey) {
            // Multi-select with Ctrl/Cmd
            if (selectedIds.includes(id)) {
                selectComponents(selectedIds.filter((i) => i !== id));
            } else {
                selectComponents([...selectedIds, id]);
            }
        } else {
            selectComponents([id]);
        }
    };

    const handleReorder = (id: string, direction: 'up' | 'down' | 'top' | 'bottom') => {
        dispatch({ type: 'REORDER_LAYER', payload: { id, direction } });
    };

    const handleVisibilityToggle = (id: string, visible: boolean) => {
        dispatch({ type: 'UPDATE_COMPONENT', payload: { id, updates: { visible: !visible } } });
    };

    const handleLockToggle = (id: string, locked: boolean) => {
        dispatch({ type: 'UPDATE_COMPONENT', payload: { id, updates: { locked: !locked } } });
    };

    const getComponentIcon = (type: string): string => {
        const iconMap: Record<string, string> = {
            'line-chart': '📈',
            'bar-chart': '📊',
            'pie-chart': '🥧',
            'gauge-chart': '🎯',
            'radar-chart': '🕸️',
            'funnel-chart': '🔽',
            'map-chart': '🗺️',
            'number-card': '🔢',
            'title': '🔤',
            'markdown-text': '📄',
            'countdown': '⏳',
            'marquee': '📢',
            'shape': '🔷',
            'container': '🗂️',
            'datetime': '🕐',
            'progress-bar': '📏',
            'image': '🖼️',
            'video': '🎬',
            'iframe': '🌐',
            'table': '🗂️',
            'filter-input': '⌨️',
            'filter-select': '🔽',
            'filter-date-range': '📅',
            'border-box': '🔲',
            'decoration': '💠',
            'scroll-board': '📜',
            'scroll-ranking': '🏆',
            'water-level': '💧',
            'digital-flop': '🔄',
        };
        return iconMap[type] || '📦';
    };

    const normalizedKeyword = keyword.trim().toLowerCase();
    const filteredLayered = useMemo(() => {
        if (!normalizedKeyword) return layered;
        return layered.filter(({ component }) => {
            const name = String(component.name || '').toLowerCase();
            const type = String(component.type || '').toLowerCase();
            const id = String(component.id || '').toLowerCase();
            return name.includes(normalizedKeyword) || type.includes(normalizedKeyword) || id.includes(normalizedKeyword);
        });
    }, [layered, normalizedKeyword]);

    const applySelectedUpdates = (updates: Partial<typeof config.components[number]>) => {
        if (selectedIds.length === 0) return;
        for (const id of selectedIds) {
            dispatch({ type: 'UPDATE_COMPONENT', payload: { id, updates } });
        }
    };

    const reorderSelected = (direction: 'top' | 'bottom') => {
        if (selectedIds.length === 0) return;
        const selected = config.components
            .filter((item) => selectedIds.includes(item.id))
            .sort((a, b) => (direction === 'top' ? a.zIndex - b.zIndex : b.zIndex - a.zIndex));
        for (const item of selected) {
            dispatch({ type: 'REORDER_LAYER', payload: { id: item.id, direction } });
        }
    };

    const selectFiltered = () => {
        if (filteredLayered.length === 0) return;
        selectComponents(filteredLayered.map((item) => item.component.id));
    };
    const hasSelected = selectedIds.length > 0;
    const executeBulkAction = () => {
        if (!hasSelected) return;
        if (bulkAction === 'show') {
            applySelectedUpdates({ visible: true });
            return;
        }
        if (bulkAction === 'hide') {
            applySelectedUpdates({ visible: false });
            return;
        }
        if (bulkAction === 'lock') {
            applySelectedUpdates({ locked: true });
            return;
        }
        if (bulkAction === 'unlock') {
            applySelectedUpdates({ locked: false });
            return;
        }
        if (bulkAction === 'top') {
            reorderSelected('top');
            return;
        }
        reorderSelected('bottom');
    };

    return (
        <div className="layer-panel">
            <div className="layer-panel-header">
                <h4>图层</h4>
                <span style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>
                    {selectedIds.length}/{config.components.length}
                </span>
            </div>
            <div style={{ display: 'grid', gap: 6, marginBottom: 10 }}>
                <input
                    type="text"
                    className="property-input"
                    placeholder="搜索组件名/类型/ID"
                    value={keyword}
                    onChange={(e) => setKeyword(e.target.value)}
                />
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 4 }}>
                    <button className="layer-action-btn" style={{ width: '100%', height: 26 }} onClick={selectFiltered} title="选择当前筛选结果">全选</button>
                    <button className="layer-action-btn" style={{ width: '100%', height: 26 }} onClick={() => selectComponents([])} title="清空选择">清空</button>
                    <button className="layer-action-btn" style={{ width: '100%', height: 26 }} onClick={() => setKeyword('')} title="清空搜索">重置</button>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) auto', gap: 4 }}>
                    <select
                        className="property-input"
                        style={{ height: 26, padding: '2px 8px' }}
                        value={bulkAction}
                        onChange={(e) => {
                            const next = e.target.value;
                            if (
                                next === 'show'
                                || next === 'hide'
                                || next === 'lock'
                                || next === 'unlock'
                                || next === 'top'
                                || next === 'bottom'
                            ) {
                                setBulkAction(next);
                            }
                        }}
                        title="选择批量动作"
                    >
                        <option value="show">显示选中</option>
                        <option value="hide">隐藏选中</option>
                        <option value="lock">锁定选中</option>
                        <option value="unlock">解锁选中</option>
                        <option value="top">置顶选中</option>
                        <option value="bottom">置底选中</option>
                    </select>
                    <button
                        className="layer-action-btn"
                        style={{ height: 26, opacity: hasSelected ? 1 : 0.45, minWidth: 56 }}
                        onClick={executeBulkAction}
                        title="执行批量动作"
                        disabled={!hasSelected}
                    >
                        执行
                    </button>
                </div>
            </div>

            {filteredLayered.length === 0 ? (
                <div className="empty-state" style={{ padding: '20px 10px' }}>
                    <div className="empty-state-text" style={{ fontSize: 12 }}>
                        {config.components.length === 0 ? '暂无组件' : '没有匹配结果'}
                    </div>
                </div>
            ) : (
                <div className="layer-list">
                    {filteredLayered.map(({ component, depth }) => (
                        <div
                            key={component.id}
                            className={`layer-item ${selectedIds.includes(component.id) ? 'selected' : ''}`}
                            onClick={(e) => handleLayerClick(component.id, e)}
                            style={{
                                opacity: component.visible ? 1 : 0.5,
                                paddingLeft: 8 + depth * 14,
                            }}
                        >
                            <span className="layer-item-icon">
                                {getComponentIcon(component.type)}
                            </span>
                            <span className="layer-item-name">
                                {component.parentContainerId ? '↳ ' : ''}
                                {component.name}
                                {component.groupId ? ' [组]' : ''}
                                {component.parentContainerId ? ' [容器]' : ''}
                            </span>
                            <div className="layer-item-actions">
                                <button
                                    className="layer-action-btn"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleVisibilityToggle(component.id, component.visible);
                                        setOpenMenuId(null);
                                    }}
                                    title={component.visible ? '隐藏' : '显示'}
                                >
                                    {component.visible ? '👁️' : '👁️‍🗨️'}
                                </button>
                                <button
                                    className="layer-action-btn"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleLockToggle(component.id, component.locked);
                                        setOpenMenuId(null);
                                    }}
                                    title={component.locked ? '解锁' : '锁定'}
                                >
                                    {component.locked ? '🔒' : '🔓'}
                                </button>
                                <button
                                    className="layer-action-btn"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        setOpenMenuId((prev) => (prev === component.id ? null : component.id));
                                    }}
                                    title="更多动作"
                                >
                                    ⋯
                                </button>
                                {openMenuId === component.id ? (
                                    <div
                                        className="layer-item-menu"
                                        onClick={(e) => e.stopPropagation()}
                                    >
                                        <button
                                            type="button"
                                            className="layer-item-menu-btn"
                                            onClick={() => {
                                                handleReorder(component.id, 'up');
                                                setOpenMenuId(null);
                                            }}
                                        >
                                            上移一层
                                        </button>
                                        <button
                                            type="button"
                                            className="layer-item-menu-btn"
                                            onClick={() => {
                                                handleReorder(component.id, 'down');
                                                setOpenMenuId(null);
                                            }}
                                        >
                                            下移一层
                                        </button>
                                        <button
                                            type="button"
                                            className="layer-item-menu-btn"
                                            onClick={() => {
                                                handleReorder(component.id, 'top');
                                                setOpenMenuId(null);
                                            }}
                                        >
                                            置顶图层
                                        </button>
                                        <button
                                            type="button"
                                            className="layer-item-menu-btn"
                                            onClick={() => {
                                                handleReorder(component.id, 'bottom');
                                                setOpenMenuId(null);
                                            }}
                                        >
                                            置底图层
                                        </button>
                                    </div>
                                ) : null}
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
