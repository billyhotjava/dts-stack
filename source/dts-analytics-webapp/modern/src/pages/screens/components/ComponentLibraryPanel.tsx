import { useEffect, useMemo, useState } from 'react';
import { useDrag } from 'react-dnd';
import { analyticsApi, type ScreenPluginManifest } from '../../../api/analyticsApi';
import { componentLibrary } from '../componentLibrary';
import type { ComponentCategory, ComponentItem, ComponentType } from '../types';

const ALLOWED_BASE_TYPES: Set<ComponentType> = new Set([
    'line-chart', 'bar-chart', 'pie-chart', 'gauge-chart', 'scatter-chart', 'radar-chart', 'funnel-chart', 'map-chart',
    'border-box', 'decoration', 'scroll-board', 'scroll-ranking', 'water-level', 'digital-flop', 'flyline-chart', 'percent-pond',
    'title', 'number-card', 'progress-bar', 'datetime', 'image', 'video', 'iframe', 'table',
    'filter-input', 'filter-select', 'filter-date-range',
]);

interface DraggableComponentItemProps {
    item: ComponentItem;
}

function DraggableComponentItem({ item }: DraggableComponentItemProps) {
    const [{ isDragging }, drag] = useDrag(() => ({
        type: 'COMPONENT',
        item: item,
        collect: (monitor) => ({
            isDragging: monitor.isDragging(),
        }),
    }));

    return (
        <div
            ref={(node) => {
                drag(node);
            }}
            className="component-item"
            style={{ opacity: isDragging ? 0.5 : 1 }}
        >
            <div className="component-item-icon">{item.icon}</div>
            <span className="component-item-name">{item.name}</span>
        </div>
    );
}

function mapPluginToCategory(plugin: ScreenPluginManifest): ComponentCategory | null {
    const list = Array.isArray(plugin.components) ? plugin.components : [];
    if (list.length === 0) {
        return null;
    }

    const items: ComponentItem[] = [];
    for (const component of list) {
        const baseType = component.baseType as ComponentType | undefined;
        if (!baseType || !ALLOWED_BASE_TYPES.has(baseType)) {
            continue;
        }

        items.push({
            type: baseType,
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

    useEffect(() => {
        analyticsApi.listScreenPlugins()
            .then((data) => {
                setPlugins(Array.isArray(data) ? data : []);
                setPluginError(null);
            })
            .catch((error) => {
                console.error('Failed to load screen plugins:', error);
                setPluginError('插件清单加载失败');
                setPlugins([]);
            });
    }, []);

    const mergedCategories = useMemo(() => {
        const pluginCategories = plugins
            .map(mapPluginToCategory)
            .filter((item): item is ComponentCategory => item !== null);
        return [...componentLibrary, ...pluginCategories];
    }, [plugins]);

    return (
        <div className="component-library">
            <div className="component-library-header">
                <h3>组件库</h3>
                <div style={{ fontSize: 11, opacity: 0.7 }}>插件: {plugins.length}</div>
            </div>
            <div className="component-library-content">
                {pluginError && (
                    <div style={{ color: '#fbbf24', fontSize: 12, marginBottom: 8 }}>{pluginError}</div>
                )}
                {mergedCategories.map((category: ComponentCategory) => (
                    <div key={category.name} className="component-category">
                        <div className="component-category-title">
                            {category.icon} {category.name}
                        </div>
                        <div className="component-grid">
                            {category.items.map((item: ComponentItem, idx: number) => (
                                <DraggableComponentItem key={`${category.name}-${item.name}-${idx}`} item={item} />
                            ))}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
