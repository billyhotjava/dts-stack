import type { ScreenPluginManifest } from '../../api/analyticsApi';
import type { ComponentCategory, ComponentItem, ComponentType } from './types';

function isPluginComponentVisible(component: NonNullable<ScreenPluginManifest['components']>[number]): boolean {
    return component.installed !== false;
}

export function mapPluginManifestToCategory(plugin: ScreenPluginManifest): ComponentCategory | null {
    const list = Array.isArray(plugin.components) ? plugin.components : [];
    if (list.length === 0) {
        return null;
    }

    const items: ComponentItem[] = [];
    for (const component of list) {
        if (!isPluginComponentVisible(component)) {
            continue;
        }
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
                    installed: component.installed !== false,
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
