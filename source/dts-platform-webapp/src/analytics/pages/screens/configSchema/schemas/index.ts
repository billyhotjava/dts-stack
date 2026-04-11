import type { ComponentType } from '../../types';
import type { ComponentConfigSchema } from '../types';
import { BASIC_SCHEMAS } from './basic';
import { ENTERPRISE_SCHEMAS } from './enterprise';
import { TABLE_SCHEMAS } from './table';
import { CHART_SCHEMAS } from './charts';
import { DATAV_SCHEMAS } from './datav';
import { FILTER_SCHEMAS } from './filters';
import { THREE_D_SCHEMAS } from './three-d';

export { ECHARTS_COMMON_FIELDS, AXIS_CHART_FIELDS } from './common';

// ---------------------------------------------------------------------------
// ALL_SCHEMAS — flat array of every component config schema
// ---------------------------------------------------------------------------

export const ALL_SCHEMAS: ComponentConfigSchema[] = [
    ...BASIC_SCHEMAS,
    ...ENTERPRISE_SCHEMAS,
    ...TABLE_SCHEMAS,
    ...CHART_SCHEMAS,
    ...DATAV_SCHEMAS,
    ...FILTER_SCHEMAS,
    ...THREE_D_SCHEMAS,
];

// ---------------------------------------------------------------------------
// COMPONENT_CONFIG_SCHEMAS — lookup by component type
// ---------------------------------------------------------------------------

export const COMPONENT_CONFIG_SCHEMAS: Partial<Record<ComponentType, ComponentConfigSchema>> =
    Object.fromEntries(ALL_SCHEMAS.map((schema) => [schema.type, schema])) as Partial<Record<ComponentType, ComponentConfigSchema>>;
