import { useScreen } from '../ScreenContext';

export function LayerPanel() {
    const { state, dispatch, selectComponents } = useScreen();
    const { config, selectedIds } = state;

    // Sort components by zIndex descending (top layers first)
    const sortedComponents = [...config.components].sort((a, b) => b.zIndex - a.zIndex);

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
            'number-card': '🔢',
            'title': '🔤',
            'datetime': '🕐',
            'progress-bar': '📏',
            'image': '🖼️',
            'video': '🎬',
            'iframe': '🌐',
            'table': '🗂️',
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

            {sortedComponents.length === 0 ? (
                <div className="empty-state" style={{ padding: '20px 10px' }}>
                    <div className="empty-state-text" style={{ fontSize: 12 }}>
                        暂无组件
                    </div>
                </div>
            ) : (
                <div className="layer-list">
                    {sortedComponents.map((component) => (
                        <div
                            key={component.id}
                            className={`layer-item ${selectedIds.includes(component.id) ? 'selected' : ''}`}
                            onClick={(e) => handleLayerClick(component.id, e)}
                            style={{ opacity: component.visible ? 1 : 0.5 }}
                        >
                            <span className="layer-item-icon">
                                {getComponentIcon(component.type)}
                            </span>
                            <span className="layer-item-name">
                                {component.name}
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
