import type { DataSourceConfig } from './types';

type QueryParameter = { name: string; value: string };

type BuildApiRuntimeRequestOptions = {
    queryParameters?: QueryParameter[];
    queryContext?: Record<string, unknown>;
};

export type ApiRuntimeRequest = {
    url: string;
    method: 'GET' | 'POST';
    headers: Record<string, string>;
    params: Record<string, string>;
    body?: string;
};

const TEMPLATE_PATTERN = /\{\{\s*([^{}\s]+)\s*\}\}/g;

export function buildApiRuntimeRequest(
    apiConfig: DataSourceConfig['apiConfig'],
    options: BuildApiRuntimeRequestOptions = {},
): ApiRuntimeRequest {
    const config = apiConfig ?? { url: '', method: 'GET' as const };
    const method = config.method ?? 'GET';
    const templateValues = collectTemplateValues(options);
    const params = expandRecord(config.params, templateValues, true);
    const headers = expandRecord(config.headers, templateValues, false);
    const queryContext = isPlainObject(options.queryContext) ? options.queryContext : undefined;
    const url = rewritePublicScreenProjectCockpitUrl(config.url ?? '', queryContext);

    return {
        url,
        method,
        headers,
        params,
        body: resolveBody(config.body, method, templateValues, queryContext),
    };
}

export function resolveApiRuntimePayload(payload: unknown, responsePath?: string): unknown {
    const extracted = extractByPath(payload, responsePath);
    if (extracted == null) {
        if (responsePath && responsePath.trim()) {
            console.warn(`[DataSource] responsePath "${responsePath}" 未能从响应中提取数据。请检查路径是否正确。`, { availableKeys: payload && typeof payload === 'object' ? Object.keys(payload) : '(非对象)' });
        }
        return [];
    }
    if (Array.isArray(extracted)) {
        return extracted;
    }
    if (isPlainObject(extracted)) {
        const rowLike = extracted as Record<string, unknown>;
        if (Array.isArray(rowLike.rows) || Array.isArray(rowLike.cols) || isPlainObject(rowLike.data)) {
            return extracted;
        }
        return [rowLike];
    }
    return [[extracted]];
}

function collectTemplateValues(options: BuildApiRuntimeRequestOptions): Map<string, string> {
    const out = new Map<string, string>();
    for (const item of options.queryParameters ?? []) {
        const key = String(item?.name ?? '').trim();
        if (!key) continue;
        out.set(key, String(item?.value ?? ''));
    }
    const queryContext = isPlainObject(options.queryContext) ? options.queryContext : undefined;
    if (!queryContext) {
        return out;
    }
    for (const [key, value] of Object.entries(queryContext)) {
        if (isScalarValue(value)) {
            out.set(key, String(value ?? ''));
        }
    }
    const globalVariables = isPlainObject(queryContext.globalVariables)
        ? queryContext.globalVariables as Record<string, unknown>
        : {};
    for (const [key, value] of Object.entries(globalVariables)) {
        const normalized = String(value ?? '');
        out.set(key, normalized);
        out.set(`globalVariables.${key}`, normalized);
    }
    return out;
}

function expandRecord(
    source: Record<string, string> | undefined,
    values: Map<string, string>,
    omitBlank: boolean,
): Record<string, string> {
    const out: Record<string, string> = {};
    for (const [key, rawValue] of Object.entries(source ?? {})) {
        const nextValue = interpolateTemplate(String(rawValue ?? ''), values);
        if (omitBlank && nextValue.trim().length === 0) {
            continue;
        }
        out[key] = nextValue;
    }
    return out;
}

function resolveBody(
    bodyTemplate: string | undefined,
    method: 'GET' | 'POST',
    values: Map<string, string>,
    queryContext?: Record<string, unknown>,
): string | undefined {
    if (method !== 'POST') {
        return undefined;
    }
    const interpolated = interpolateTemplate(String(bodyTemplate ?? ''), values).trim();
    if (!interpolated) {
        return queryContext ? JSON.stringify({ queryContext }) : undefined;
    }
    try {
        const parsed = JSON.parse(interpolated);
        if (isPlainObject(parsed)) {
            return JSON.stringify(queryContext ? { ...parsed, queryContext } : parsed);
        }
    } catch {
        // Fall back to raw string body.
    }
    return interpolated;
}

function interpolateTemplate(template: string, values: Map<string, string>): string {
    return template.replace(TEMPLATE_PATTERN, (_, rawKey: string) => {
        const key = String(rawKey ?? '').trim();
        return values.get(key) ?? '';
    });
}

function rewritePublicScreenProjectCockpitUrl(url: string, queryContext?: Record<string, unknown>): string {
    const runtimeMeta = isPlainObject(queryContext?.runtimeMeta)
        ? queryContext.runtimeMeta as Record<string, unknown>
        : undefined;
    const accessMode = String(runtimeMeta?.accessMode ?? '').trim().toLowerCase();
    const publicScreenUuid = String(runtimeMeta?.publicScreenUuid ?? '').trim();
    if (accessMode !== 'public' || !publicScreenUuid) {
        return url;
    }
    const match = url.match(/^\/(?:analytics|bi)\/api\/project-cockpit\/screen\/([^/?#]+)$/);
    if (!match) {
        return url;
    }
    return `/bi/api/public/screen/${encodeURIComponent(publicScreenUuid)}/project-cockpit/${match[1]}`;
}

function extractByPath(payload: unknown, responsePath?: string): unknown {
    if (!responsePath || !responsePath.trim()) {
        return payload;
    }
    const segments = responsePath
        .split('.')
        .map((item) => item.trim())
        .filter(Boolean);
    let current: unknown = payload;
    for (const segment of segments) {
        if (Array.isArray(current)) {
            const index = Number.parseInt(segment, 10);
            if (!Number.isInteger(index) || index < 0 || index >= current.length) {
                return undefined;
            }
            current = current[index];
            continue;
        }
        if (isPlainObject(current)) {
            current = (current as Record<string, unknown>)[segment];
            continue;
        }
        return undefined;
    }
    return current;
}

function isScalarValue(value: unknown): value is string | number | boolean {
    return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean';
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
    return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}
