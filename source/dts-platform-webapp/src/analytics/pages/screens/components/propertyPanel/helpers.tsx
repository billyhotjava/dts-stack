import type {
    DataSourceConfig,
    QuerySourceType,
    ScreenComponent,
    ComponentType,
} from '../../types';
import { DRILLABLE_TYPES } from '../../types';
import { analyticsApi, type ScreenListItem } from '../../../../api/analyticsApi';
import type { StyleClipboardPayload, LegendHeuristicLayout } from './types';

export const STYLE_CLIPBOARD_KEY = 'dts.analytics.screen.styleClipboard.v1';
export const LAYOUT_CLIPBOARD_KEY = 'dts.analytics.screen.layoutClipboard.v1';
export const PROPERTY_SECTION_COLLAPSE_KEY = 'dts.analytics.screen.propertySectionCollapse.v1';
export const PROPERTY_SECTION_KEYS = [
    'position-size',
    'plugin-config',
    'component-config',
    'data-source',
    'explain',
    'drill-down',
    'interaction',
    'actions',
    'other',
] as const;
export const STYLE_CONFIG_EXCLUDE_KEYS = new Set<string>([
    '_sourceColumns',
    '_fieldMapping',
    '_useFieldMapping',
    '_colorScheme',
    'markLines',
    'markAreas',
    'conditionalColors',
    'data',
    'xAxisData',
    'series',
    'indicator',
    'options',
    'value',
    'min',
    'max',
    'targetDate',
    'items',
    'content',
    'html',
    'text',
    'imageUrl',
    'videoUrl',
    'src',
    'url',
    'cardId',
    'metricId',
    'metricVersion',
    'query',
    'queryBody',
    'databaseId',
    'connectionId',
    'variableKey',
    'globalVariables',
    'interaction',
    'drillDown',
]);

/**
 * 把 "a.b.c" 路径的值写入对象，返回新对象，保留其他嵌套属性不变。
 * 与 SchemaConfigRenderer 的 resolveNestedValue 读路径对称。
 */
export function setByPath(
    obj: Record<string, unknown> | undefined | null,
    path: string,
    value: unknown,
): Record<string, unknown> {
    const base = (obj && typeof obj === 'object') ? obj : {};
    if (!path) return { ...base };
    const parts = path.split('.');
    if (parts.length === 1) return { ...base, [path]: value };
    const [head, ...rest] = parts;
    const prev = base[head];
    const prevObj = (prev && typeof prev === 'object' && !Array.isArray(prev))
        ? (prev as Record<string, unknown>)
        : {};
    return { ...base, [head]: setByPath(prevObj, rest.join('.'), value) };
}

/**
 * 深合并两个对象，source 的值覆盖 target 的值；对嵌套对象递归合并。
 * 数组、null、primitive 直接替换，不做合并。
 */
export function deepMergeConfig(
    target: Record<string, unknown> | undefined | null,
    source: Record<string, unknown> | undefined | null,
): Record<string, unknown> {
    const a = (target && typeof target === 'object') ? target : {};
    const b = (source && typeof source === 'object') ? source : {};
    const out: Record<string, unknown> = { ...a };
    for (const key of Object.keys(b)) {
        const sv = b[key];
        const tv = a[key];
        if (
            sv && typeof sv === 'object' && !Array.isArray(sv)
            && tv && typeof tv === 'object' && !Array.isArray(tv)
        ) {
            out[key] = deepMergeConfig(tv as Record<string, unknown>, sv as Record<string, unknown>);
        } else {
            out[key] = sv;
        }
    }
    return out;
}

export function isVisualConfigKey(key: string): boolean {
    const normalized = String(key || '').trim();
    if (!normalized) return false;
    if (normalized.startsWith('_')) return false;
    if (STYLE_CONFIG_EXCLUDE_KEYS.has(normalized)) return false;
    const lowered = normalized.toLowerCase();
    if (lowered.includes('data') || lowered.includes('dataset')) return false;
    if (lowered.includes('query') || lowered.includes('metric') || lowered.includes('card')) return false;
    if (lowered.includes('source')) return false;
    return (
        lowered.includes('color')
        || lowered.includes('font')
        || lowered.includes('size')
        || lowered.includes('background')
        || lowered.includes('border')
        || lowered.includes('radius')
        || lowered.includes('padding')
        || lowered.includes('margin')
        || lowered.includes('legend')
        || lowered.includes('axis')
        || lowered.includes('label')
        || lowered.includes('title')
        || lowered.includes('theme')
        || lowered.includes('opacity')
        || lowered.includes('align')
        || lowered.includes('position')
        || lowered.includes('offset')
        || lowered.includes('scale')
        || lowered.includes('rotate')
        || lowered.includes('line')
        || lowered.includes('wrap')
        || lowered.includes('display')
        || lowered.includes('show')
    );
}

export function buildStyleClipboardPayload(component: ScreenComponent): StyleClipboardPayload {
    const pickedEntries = Object.entries(component.config || {}).filter(([key]) => isVisualConfigKey(key));
    const visualConfig = Object.fromEntries(pickedEntries);
    return {
        type: component.type,
        width: component.width,
        height: component.height,
        config: visualConfig,
        copiedAt: new Date().toISOString(),
    };
}

export const DEFAULT_SERIES_COLORS = [
    '#3b82f6',
    '#22c55e',
    '#f59e0b',
    '#ef4444',
    '#a855f7',
    '#06b6d4',
];

export function resolveLegendHeuristicLayout(component: ScreenComponent): LegendHeuristicLayout {
    const width = Math.max(1, Number(component.width) || 1);
    const height = Math.max(1, Number(component.height) || 1);
    const ratio = width / height;

    if (width < 440 || ratio <= 1.05) {
        return {
            position: 'bottom',
            orient: 'horizontal',
            align: 'start',
            reserveSize: Math.max(48, Math.min(92, Math.round(height * 0.22))),
            nameMaxWidth: 96,
            hint: '当前组件偏窄，建议底部横向图例',
        };
    }
    if (ratio >= 1.75 && width >= 520) {
        return {
            position: 'right',
            orient: 'vertical',
            align: 'center',
            reserveSize: Math.max(96, Math.min(180, Math.round(width * 0.2))),
            nameMaxWidth: 128,
            hint: '当前组件偏宽，建议右侧纵向图例',
        };
    }
    if (height <= 260) {
        return {
            position: 'bottom',
            orient: 'horizontal',
            align: 'center',
            reserveSize: 56,
            nameMaxWidth: 0,
            hint: '当前组件高度有限，建议底部图例降低遮挡',
        };
    }
    return {
        position: 'top',
        orient: 'horizontal',
        align: 'center',
        reserveSize: 52,
        nameMaxWidth: 0,
        hint: '建议顶部横向图例，保持图形区域均衡',
    };
}

export function applyLegendHeuristicLayout(
    component: ScreenComponent,
    onChange: (key: string, value: unknown) => void,
) {
    const next = resolveLegendHeuristicLayout(component);
    onChange('autoLegendAvoid', true);
    onChange('legendDisplay', 'auto');
    onChange('legendPosition', next.position);
    onChange('legendOrient', next.orient);
    onChange('legendAlign', next.align);
    onChange('legendReserveSize', next.reserveSize);
    onChange('legendNameMaxWidth', next.nameMaxWidth);
    onChange('legendOffsetX', 0);
    onChange('legendOffsetY', 0);
}

export function renderChartTitleLayoutRows(
    config: Record<string, unknown>,
    onChange: (key: string, value: unknown) => void,
) {
    return (
        <>
            <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 8, marginBottom: 4 }}>
                标题位置微调
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">标题位置</label>
                <select
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    value={(config.titlePosition as string) || 'auto'}
                    onChange={(e) => onChange('titlePosition', e.target.value)}
                >
                    <option value="auto">自动</option>
                    <option value="left">左侧</option>
                    <option value="center">居中</option>
                    <option value="right">右侧</option>
                </select>
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">启用拖拽微调</label>
                <input
                    type="checkbox"
                    checked={config.titleDragEnabled === true}
                    onChange={(e) => onChange('titleDragEnabled', e.target.checked)}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">水平偏移</label>
                <input
                    type="number"
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    min={-400}
                    max={400}
                    value={(config.titleOffsetX as number) || 0}
                    onChange={(e) => onChange('titleOffsetX', Number(e.target.value))}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">垂直偏移</label>
                <input
                    type="number"
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    min={-200}
                    max={200}
                    value={(config.titleOffsetY as number) || 0}
                    onChange={(e) => onChange('titleOffsetY', Number(e.target.value))}
                />
            </div>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">标题布局</label>
                <button
                    type="button"
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    onClick={() => {
                        onChange('titlePosition', 'auto');
                        onChange('titleDragEnabled', false);
                        onChange('titleOffsetX', 0);
                        onChange('titleOffsetY', 0);
                    }}
                >
                    一键重置为自动
                </button>
            </div>
        </>
    );
}

export function serializeVisibilityMatchValues(raw: unknown): string {
    if (Array.isArray(raw)) {
        return raw.map((item) => String(item ?? '').trim()).filter((item) => item.length > 0).join('\n');
    }
    const text = String(raw ?? '').trim();
    if (!text) return '';
    return text
        .split(/[\n,，]/g)
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
        .join('\n');
}

export function parseVisibilityMatchValues(text: string): string[] {
    return text
        .split(/[\n,，]/g)
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
        .slice(0, 200);
}

export function extractSqlTemplateParameterNames(sql: string): string[] {
    const text = String(sql ?? '');
    if (!text.trim()) {
        return [];
    }
    const names: string[] = [];
    const seen = new Set<string>();
    const patterns = [
        /\{\{\s*([a-zA-Z][a-zA-Z0-9_-]{0,63})\s*\}\}/g,
        /\$\{\s*([a-zA-Z][a-zA-Z0-9_-]{0,63})\s*\}/g,
    ];
    for (const pattern of patterns) {
        let match: RegExpExecArray | null = null;
        // eslint-disable-next-line no-cond-assign
        while ((match = pattern.exec(text)) !== null) {
            const name = String(match[1] ?? '').trim();
            if (!name || seen.has(name)) {
                continue;
            }
            seen.add(name);
            names.push(name);
        }
    }
    return names.slice(0, 200);
}

export function resolveTabSwitcherOptionValues(raw: unknown): string[] {
    if (!Array.isArray(raw)) {
        return [];
    }
    const out: string[] = [];
    for (const item of raw) {
        if (typeof item === 'string') {
            const text = item.trim();
            if (text) out.push(text);
            continue;
        }
        if (!item || typeof item !== 'object') {
            continue;
        }
        const row = item as Record<string, unknown>;
        const value = String(row.value ?? '').trim();
        if (value) {
            out.push(value);
        }
    }
    return out;
}

// ── Screen Jump Picker helpers ─────────────────────────────────────────

export const SCREEN_REF_PREFIX = 'screen-ref:';
export const SCREEN_PREVIEW_URL_RE = /^\/bi\/screens\/([^/]+)\/preview$/;

/**
 * Object references should be by stable id, not by name. Dropdown writes a
 * direct preview URL `/bi/screens/{id}/preview` so the link survives renames
 * and re-imports. The `screen-ref:` form is kept only as a read-only legacy
 * compatibility layer for v2 instance JSON and old user data.
 */
export function buildScreenJumpUrl(screen: ScreenListItem): string {
    return `/bi/screens/${encodeURIComponent(String(screen.id))}/preview`;
}

/** Extract the screen id this jumpUrlTemplate points at, in either format. */
export function extractScreenIdFromJumpUrl(url: string): string | null {
    if (!url) return null;
    // New canonical format: /bi/screens/{id}/preview
    const direct = url.match(SCREEN_PREVIEW_URL_RE);
    if (direct) return decodeURIComponent(direct[1]);
    // Legacy: screen-ref:{name}|{fallback}|id={id}  — read id segment if present
    if (url.startsWith(SCREEN_REF_PREFIX)) {
        const segments = url.slice(SCREEN_REF_PREFIX.length).split('|');
        for (let i = 2; i < segments.length; i += 1) {
            const seg = decodeURIComponent(segments[i] || '').trim();
            if (seg.startsWith('id=')) return seg.slice(3).trim() || null;
        }
        // Legacy fallback URL embedded in 2nd segment may also be a /preview path
        const fallback = decodeURIComponent(segments[1] || '').trim();
        const fbMatch = fallback.match(SCREEN_PREVIEW_URL_RE);
        if (fbMatch) return decodeURIComponent(fbMatch[1]);
    }
    return null;
}

/** Extract the legacy `screen-ref:{name}|...` name segment for display only. */
export function extractLegacyScreenRefName(url: string): string | null {
    if (!url.startsWith(SCREEN_REF_PREFIX)) return null;
    const namePart = url.slice(SCREEN_REF_PREFIX.length).split('|')[0] ?? '';
    return decodeURIComponent(namePart).trim() || null;
}

let _screenListCache: { items: ScreenListItem[]; ts: number } | null = null;
const SCREEN_CACHE_TTL = 30_000;

export async function fetchScreenList(): Promise<ScreenListItem[]> {
    const now = Date.now();
    if (_screenListCache && _screenListCache.ts + SCREEN_CACHE_TTL > now) {
        return _screenListCache.items;
    }
    const items = await analyticsApi.listScreens();
    items.sort((a, b) => new Date(String(b.updatedAt || 0)).getTime() - new Date(String(a.updatedAt || 0)).getTime());
    _screenListCache = { items, ts: now };
    return items;
}

// ── DataSource helpers ─────────────────────────────────────────────────

export function resolveDataSourceType(ds?: DataSourceConfig): 'static' | QuerySourceType {
    const type = ((ds?.sourceType ?? ds?.type) || 'static').toLowerCase();
    if (type === 'database' || type === 'sql') return 'sql';
    if (type === 'card' || type === 'api' || type === 'dataset' || type === 'metric') {
        return type;
    }
    return 'static';
}

export function resolveExplainCardId(component: ScreenComponent): number | undefined {
    const ds = component.dataSource as DataSourceConfig | undefined;
    const dsType = resolveDataSourceType(ds);
    if (dsType === 'card') {
        const id = Number(ds?.cardConfig?.cardId ?? 0);
        return Number.isFinite(id) && id > 0 ? id : undefined;
    }
    if (dsType === 'metric') {
        const id = Number(ds?.metricConfig?.cardId ?? 0);
        return Number.isFinite(id) && id > 0 ? id : undefined;
    }
    return undefined;
}

export function resolveSqlConfig(ds?: DataSourceConfig): DataSourceConfig['sqlConfig'] | DataSourceConfig['databaseConfig'] | undefined {
    if (!ds) return undefined;
    return ds.sqlConfig ?? ds.databaseConfig;
}

export function safeJsonParse(text: string): Record<string, unknown> | null {
    const raw = (text || '').trim();
    if (!raw) return {};
    try {
        const parsed = JSON.parse(raw);
        return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
            ? (parsed as Record<string, unknown>)
            : null;
    } catch {
        return null;
    }
}

export function safeJsonStringify(value: unknown): string {
    if (!value || typeof value !== 'object') return '{}';
    try {
        return JSON.stringify(value, null, 2);
    } catch {
        return '{}';
    }
}

export const INTERACTION_COMPONENT_TYPES = new Set<ComponentType>([
    'line-chart',
    'bar-chart',
    'pie-chart',
    'scatter-chart',
    'radar-chart',
    'funnel-chart',
]);

export const ACTION_COMPONENT_TYPES = new Set<ComponentType>([
    ...INTERACTION_COMPONENT_TYPES,
    'table',
    'scroll-board',
    'scroll-ranking',
    // Runtime click wrapper (ComponentRenderer.supportsRuntimeActionWrapper)
    // already supports these basic types — keep editor whitelist in sync.
    'shape',
    'title',
    'number-card',
    'markdown-text',
]);

export const DRILL_CONFIGURABLE_TYPES = new Set<ComponentType>([
    ...Array.from(DRILLABLE_TYPES),
    'table',
]);

export function getActionSourcePathCandidates(type: ComponentType): string[] {
    if (type === 'pie-chart' || type === 'funnel-chart') return ['name', 'value', 'percent', 'data.name'];
    if (type === 'map-chart') return ['name', 'data.name', 'data.value', 'value'];
    if (type === 'table' || type === 'scroll-board') return ['row[0]', 'row[1]', 'row[2]', 'name', 'value'];
    if (type === 'scatter-chart') return ['name', 'value', 'data[0]', 'data[1]', 'seriesName'];
    if (type === 'treemap-chart' || type === 'sunburst-chart') return ['name', 'value', 'data.name', 'treePathInfo'];
    if (type === 'radar-chart') return ['name', 'seriesName', 'value', 'data.name'];
    return ['name', 'seriesName', 'value', 'data.name', 'data.value', 'data.code'];
}
