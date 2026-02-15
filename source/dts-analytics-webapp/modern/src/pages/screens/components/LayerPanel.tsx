import { useScreen } from '../ScreenContext';

export function LayerPanel() {
    const { state, dispatch, selectComponents } = useScreen();
    const { config, selectedIds } = state;

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

    return (
        <div className="layer-panel">
            <div className="layer-panel-header">
                <h4>图层</h4>
                <span style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>
                    {config.components.length} 个组件
                </span>
            </div>

            {layered.length === 0 ? (
                <div className="empty-state" style={{ padding: '20px 10px' }}>
                    <div className="empty-state-text" style={{ fontSize: 12 }}>
                        暂无组件
                    </div>
                </div>
            ) : (
                <div className="layer-list">
                    {layered.map(({ component, depth }) => (
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
                                    }}
                                    title={component.locked ? '解锁' : '锁定'}
                                >
                                    {component.locked ? '🔒' : '🔓'}
                                </button>
                                <button
                                    className="layer-action-btn"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleReorder(component.id, 'up');
                                    }}
                                    title="上移一层"
                                >
                                    ⬆️
                                </button>
                                <button
                                    className="layer-action-btn"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleReorder(component.id, 'down');
                                    }}
                                    title="下移一层"
                                >
                                    ⬇️
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
