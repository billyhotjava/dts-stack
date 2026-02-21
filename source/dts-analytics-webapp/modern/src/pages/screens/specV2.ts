import type { ScreenComponent, ScreenConfig, ScreenGlobalVariable, ScreenTheme } from './types';

export const SCREEN_SCHEMA_VERSION = 2;

const COMPONENT_TYPES = new Set<ScreenComponent['type']>([
    'line-chart',
    'bar-chart',
    'pie-chart',
    'gauge-chart',
    'scatter-chart',
    'radar-chart',
    'funnel-chart',
    'map-chart',
    'border-box',
    'decoration',
    'scroll-board',
    'scroll-ranking',
    'water-level',
    'digital-flop',
    'flyline-chart',
    'percent-pond',
    'title',
    'markdown-text',
    'number-card',
    'progress-bar',
    'tab-switcher',
    'carousel',
    'countdown',
    'marquee',
    'shape',
    'container',
    'datetime',
    'image',
    'video',
    'iframe',
    'table',
    'filter-input',
    'filter-select',
    'filter-date-range',
]);

const THEMES = new Set<ScreenTheme>(['legacy-dark', 'titanium', 'glacier']);
const DATA_SOURCE_TYPES = new Set(['static', 'api', 'card', 'sql', 'dataset', 'metric', 'database']);
const VARIABLE_TYPES = new Set(['string', 'number', 'date']);
const VARIABLE_KEY_PATTERN = /^[A-Za-z_][A-Za-z0-9_:\.-]{0,63}$/;
const VISIBILITY_MATCH_MODES = new Set([
    'equals',
    'not-equals',
    'not-in',
    'contains',
    'not-contains',
    'starts-with',
    'ends-with',
    'empty',
    'not-empty',
]);
const INTERACTION_TRANSFORMS = new Set(['raw', 'string', 'number', 'lowercase', 'uppercase']);

function asNumber(value: unknown, fallback: number, min?: number): number {
    const n = Number(value);
    if (!Number.isFinite(n)) return fallback;
    if (typeof min === 'number' && n < min) return fallback;
    return n;
}

function asString(value: unknown): string | undefined {
    return typeof value === 'string' ? value : undefined;
}

function asTrimmedString(value: unknown): string | undefined {
    if (typeof value !== 'string') return undefined;
    const out = value.trim();
    return out.length > 0 ? out : undefined;
}

function validateVisibilityRuleConfig(
    config: Record<string, unknown> | undefined,
    path: string,
    errors: string[],
) {
    if (!config || typeof config !== 'object') {
        return;
    }
    if (config.visibilityRuleEnabled !== true) {
        return;
    }
    const variableKey = asTrimmedString(config.visibilityVariableKey);
    if (!variableKey) {
        errors.push(`${path}.config.visibilityVariableKey 不能为空`);
    }
    const mode = String(config.visibilityMatchMode ?? 'equals').trim().toLowerCase();
    if (!VISIBILITY_MATCH_MODES.has(mode)) {
        errors.push(`${path}.config.visibilityMatchMode 非法: ${mode}`);
        return;
    }
    if (mode === 'empty' || mode === 'not-empty') {
        return;
    }
    const values = config.visibilityMatchValues;
    if (values === undefined || values === null) {
        return;
    }
    if (Array.isArray(values) && values.length > 200) {
        errors.push(`${path}.config.visibilityMatchValues 数量不能超过 200`);
    }
}

function normalizeGlobalVariables(input: unknown): ScreenGlobalVariable[] {
    if (!Array.isArray(input)) return [];
    return input.reduce<ScreenGlobalVariable[]>((acc, item) => {
        if (!item || typeof item !== 'object') return acc;
        const row = item as Record<string, unknown>;
        const key = asTrimmedString(row.key);
        if (!key) return acc;
        acc.push({
            key,
            label: asString(row.label) || key,
            type: row.type === 'number' || row.type === 'date' ? row.type : 'string',
            defaultValue: asString(row.defaultValue),
            description: asString(row.description),
        });
        return acc;
    }, []);
}

function normalizeComponent(
    input: unknown,
    index: number,
    warnings: string[],
): ScreenComponent | null {
    if (!input || typeof input !== 'object') {
        warnings.push(`components[${index}] is not an object`);
        return null;
    }

    const row = input as Record<string, unknown>;
    const rawType = asString(row.type);
    const type = rawType && COMPONENT_TYPES.has(rawType as ScreenComponent['type'])
        ? (rawType as ScreenComponent['type'])
        : null;
    if (!type) {
        warnings.push(`components[${index}].type is invalid: ${String(rawType)}`);
        return null;
    }

    const id = asTrimmedString(row.id) || `comp_autogen_${Date.now()}_${index}`;
    const config = row.config && typeof row.config === 'object'
        ? (row.config as Record<string, unknown>)
        : {};
    const dataSource = normalizeDataSource(row.dataSource);

    return {
        id,
        groupId: asTrimmedString(row.groupId),
        parentContainerId: asTrimmedString(row.parentContainerId),
        type,
        name: asString(row.name) || `${type}-${index + 1}`,
        x: asNumber(row.x, 0),
        y: asNumber(row.y, 0),
        width: asNumber(row.width, 240, 20),
        height: asNumber(row.height, 120, 20),
        zIndex: asNumber(row.zIndex, index),
        locked: Boolean(row.locked),
        visible: row.visible === undefined ? true : Boolean(row.visible),
        config,
        dataSource,
        drillDown: row.drillDown as ScreenComponent['drillDown'],
        interaction: row.interaction as ScreenComponent['interaction'],
    };
}

function normalizeDataSource(input: unknown): ScreenComponent['dataSource'] {
    if (!input || typeof input !== 'object') {
        return undefined;
    }
    const dataSource = { ...(input as Record<string, unknown>) };
    const sourceTypeRaw = asTrimmedString(dataSource.sourceType);
    const typeRaw = asTrimmedString(dataSource.type);
    const normalized = sourceTypeRaw ?? typeRaw;
    if (!normalized) {
        return dataSource as unknown as ScreenComponent['dataSource'];
    }
    const canonical = normalized.toLowerCase() === 'database' ? 'sql' : normalized;
    dataSource.sourceType = canonical;
    dataSource.type = canonical;

    // Legacy compatibility: migrate databaseConfig => sqlConfig when needed.
    if (canonical === 'sql' && !dataSource.sqlConfig && dataSource.databaseConfig) {
        dataSource.sqlConfig = dataSource.databaseConfig;
    }
    return dataSource as unknown as ScreenComponent['dataSource'];
}

export function normalizeScreenConfig(
    input: unknown,
    options?: { id?: string | number },
): { config: ScreenConfig; warnings: string[] } {
    const warnings: string[] = [];
    const row = input && typeof input === 'object' ? (input as Record<string, unknown>) : {};

    const schemaVersion = asNumber(row.schemaVersion, SCREEN_SCHEMA_VERSION, 1);
    if (schemaVersion < SCREEN_SCHEMA_VERSION) {
        warnings.push(`schemaVersion upgraded from ${schemaVersion} to ${SCREEN_SCHEMA_VERSION}`);
    }

    const id = options?.id ?? row.id ?? '';
    const width = asNumber(row.width, 1920, 200);
    const height = asNumber(row.height, 1080, 120);
    const backgroundColor = asString(row.backgroundColor) || '#0d1b2a';
    const themeRaw = asString(row.theme);
    const theme = themeRaw && THEMES.has(themeRaw as ScreenTheme) ? (themeRaw as ScreenTheme) : undefined;

    const rawComponents = Array.isArray(row.components) ? row.components : [];
    const components = rawComponents
        .map((item, idx) => normalizeComponent(item, idx, warnings))
        .filter((item): item is ScreenComponent => item !== null);

    if (!Array.isArray(row.components) && row.components !== undefined) {
        warnings.push('components is not an array, fallback to []');
    }

    const config: ScreenConfig = {
        schemaVersion: SCREEN_SCHEMA_VERSION,
        id: String(id),
        name: asString(row.name) || '未命名大屏',
        description: asString(row.description) || '',
        updatedAt: asString(row.updatedAt),
        width,
        height,
        backgroundColor,
        backgroundImage: asTrimmedString(row.backgroundImage),
        theme,
        components,
        globalVariables: normalizeGlobalVariables(row.globalVariables),
    };

    return { config, warnings };
}

export function buildScreenPayload(config: ScreenConfig): Record<string, unknown> {
    return {
        schemaVersion: SCREEN_SCHEMA_VERSION,
        name: config.name,
        description: config.description,
        width: config.width,
        height: config.height,
        backgroundColor: config.backgroundColor,
        backgroundImage: config.backgroundImage,
        theme: config.theme,
        components: config.components,
        globalVariables: config.globalVariables ?? [],
    };
}

export function validateScreenPayload(input: unknown): { errors: string[]; warnings: string[] } {
    const errors: string[] = [];
    const warnings: string[] = [];
    if (!input || typeof input !== 'object') {
        return { errors: ['payload must be object'], warnings };
    }
    const row = input as Record<string, unknown>;

    const schemaVersionRaw = row.schemaVersion;
    if (schemaVersionRaw !== undefined && schemaVersionRaw !== null) {
        const schemaVersion = Number(schemaVersionRaw);
        if (!Number.isInteger(schemaVersion)) {
            errors.push('schemaVersion 必须是整数');
        } else if (schemaVersion > SCREEN_SCHEMA_VERSION) {
            errors.push(`schemaVersion=${schemaVersion} 高于当前支持版本 v${SCREEN_SCHEMA_VERSION}`);
        } else if (schemaVersion < SCREEN_SCHEMA_VERSION) {
            warnings.push(`schemaVersion=${schemaVersion} 将按 v${SCREEN_SCHEMA_VERSION} 处理`);
        }
    } else {
        warnings.push(`schemaVersion 缺失，将按 v${SCREEN_SCHEMA_VERSION} 处理`);
    }

    const width = Number(row.width);
    if (!Number.isFinite(width) || width < 200 || width > 7680) {
        errors.push('width 超出范围 [200,7680]');
    }
    const height = Number(row.height);
    if (!Number.isFinite(height) || height < 120 || height > 4320) {
        errors.push('height 超出范围 [120,4320]');
    }

    const rawComponents = row.components;
    if (!Array.isArray(rawComponents)) {
        errors.push('components 必须是数组');
    } else {
        rawComponents.forEach((item, idx) => {
            const path = `components[${idx}]`;
            if (!item || typeof item !== 'object') {
                errors.push(`${path} 必须是对象`);
                return;
            }
            const component = item as Record<string, unknown>;
            const id = typeof component.id === 'string' ? component.id.trim() : '';
            if (!id) {
                errors.push(`${path}.id 不能为空`);
            }
            const type = typeof component.type === 'string' ? component.type.trim() : '';
            const hasPlugin = Boolean(
                component.config
                && typeof component.config === 'object'
                && (component.config as Record<string, unknown>).__plugin
                && typeof (component.config as Record<string, unknown>).__plugin === 'object',
            );
            if (!type) {
                errors.push(`${path}.type 不能为空`);
            } else if (!COMPONENT_TYPES.has(type as ScreenComponent['type']) && !hasPlugin) {
                errors.push(`${path}.type 不支持: ${type}`);
            } else if (!COMPONENT_TYPES.has(type as ScreenComponent['type']) && hasPlugin) {
                warnings.push(`${path}.type=${type} 通过插件模式放行`);
            }
            const cWidth = Number(component.width);
            const cHeight = Number(component.height);
            if (!Number.isFinite(cWidth) || cWidth <= 0) {
                errors.push(`${path}.width 必须大于0`);
            }
            if (!Number.isFinite(cHeight) || cHeight <= 0) {
                errors.push(`${path}.height 必须大于0`);
            }
            const dataSource = component.dataSource as Record<string, unknown> | undefined;
            if (dataSource && typeof dataSource === 'object') {
                const sourceType = String(dataSource.sourceType ?? dataSource.type ?? '').trim().toLowerCase();
                if (sourceType && !DATA_SOURCE_TYPES.has(sourceType)) {
                    errors.push(`${path}.dataSource.sourceType 非法: ${sourceType}`);
                }
            }
            const config = component.config && typeof component.config === 'object'
                ? component.config as Record<string, unknown>
                : undefined;
            validateVisibilityRuleConfig(config, path, errors);

            const interaction = component.interaction;
            if (interaction !== undefined && interaction !== null) {
                if (typeof interaction !== 'object') {
                    errors.push(`${path}.interaction 必须是对象`);
                } else {
                    const interactionRow = interaction as Record<string, unknown>;
                    const mappings = interactionRow.mappings;
                    if (mappings !== undefined && mappings !== null) {
                        if (!Array.isArray(mappings)) {
                            errors.push(`${path}.interaction.mappings 必须是数组`);
                        } else {
                            mappings.forEach((mapping, mappingIdx) => {
                                const mappingPath = `${path}.interaction.mappings[${mappingIdx}]`;
                                if (!mapping || typeof mapping !== 'object') {
                                    errors.push(`${mappingPath} 必须是对象`);
                                    return;
                                }
                                const mappingRow = mapping as Record<string, unknown>;
                                const variableKey = asTrimmedString(mappingRow.variableKey);
                                const sourcePath = asTrimmedString(mappingRow.sourcePath);
                                if (!variableKey) {
                                    errors.push(`${mappingPath}.variableKey 不能为空`);
                                }
                                if (!sourcePath) {
                                    errors.push(`${mappingPath}.sourcePath 不能为空`);
                                }
                                const transform = String(mappingRow.transform ?? 'raw').trim().toLowerCase();
                                if (!INTERACTION_TRANSFORMS.has(transform)) {
                                    errors.push(`${mappingPath}.transform 非法: ${transform}`);
                                }
                            });
                        }
                    }
                }
            }
        });
    }

    const rawVariables = row.globalVariables;
    if (rawVariables !== undefined && rawVariables !== null) {
        if (!Array.isArray(rawVariables)) {
            errors.push('globalVariables 必须是数组');
        } else {
            const keySet = new Set<string>();
            rawVariables.forEach((item, idx) => {
                const path = `globalVariables[${idx}]`;
                if (!item || typeof item !== 'object') {
                    errors.push(`${path} 必须是对象`);
                    return;
                }
                const variable = item as Record<string, unknown>;
                const key = typeof variable.key === 'string' ? variable.key.trim() : '';
                if (!key) {
                    errors.push(`${path}.key 不能为空`);
                } else {
                    if (!VARIABLE_KEY_PATTERN.test(key)) {
                        errors.push(`${path}.key 格式非法: ${key}`);
                    }
                    if (keySet.has(key)) {
                        errors.push(`${path}.key 重复: ${key}`);
                    }
                    keySet.add(key);
                }
                if (variable.type !== undefined && variable.type !== null) {
                    const varType = String(variable.type);
                    if (!VARIABLE_TYPES.has(varType)) {
                        errors.push(`${path}.type 非法: ${varType}`);
                    }
                }
            });
        }
    }

    return { errors, warnings };
}
