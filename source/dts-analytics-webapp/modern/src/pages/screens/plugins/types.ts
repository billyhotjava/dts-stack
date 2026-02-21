import type { ReactNode } from 'react';
import type { CardData, ComponentType, ScreenComponent, ScreenTheme } from '../types';

export type PluginPropertyType = 'string' | 'number' | 'boolean' | 'color' | 'json' | 'array' | 'select';

export interface PropertySchemaOption {
    label: string;
    value: string | number | boolean;
}

export interface PropertySchemaField {
    key: string;
    label: string;
    type: PluginPropertyType;
    required?: boolean;
    defaultValue?: unknown;
    description?: string;
    placeholder?: string;
    min?: number;
    max?: number;
    step?: number;
    options?: PropertySchemaOption[];
}

export interface PropertySchema {
    version: string;
    fields: PropertySchemaField[];
}

export type PluginDataContractKind = 'table' | 'series' | 'kv' | 'raw';

export interface DataContract {
    version: string;
    kind: PluginDataContractKind;
    description?: string;
}

export interface RendererPluginRenderContext {
    component: ScreenComponent;
    mode: 'designer' | 'preview';
    theme?: ScreenTheme;
    width: number;
    height: number;
    config: Record<string, unknown>;
    data?: CardData | null;
    runtimeValues: Record<string, string>;
    setVariable: (key: string, value: string) => void;
}

export interface RendererPlugin {
    id: string;
    name: string;
    version: string;
    baseType: ComponentType;
    propertySchema?: PropertySchema;
    dataContract?: DataContract;
    render: (context: RendererPluginRenderContext) => ReactNode;
}
