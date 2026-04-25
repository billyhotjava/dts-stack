/**
 * Sprint-12 F1/T03 — v2 加载适配器。
 *
 * 后端 `GET /api/screens/:id` 在 v2 大屏的响应里会多一个 `v2Spec` 对象
 * （由 F2/T03 透传），其它字段仍是扁平的（components/globalVariables 等）。
 * 本文件把响应合并成 `ScreenConfigV2` 并 normalize，供 ResponsiveScreenLayout 消费。
 *
 * 不满足 v2 条件（无 v2Spec 或 schemaVersion 不是 2）→ 返回 null，
 * 调用方 fall back 到 v1 渲染路径。
 */

import { normalizeScreenConfigV2 } from './schema';
import type { ScreenConfigV2 } from './types';

interface LooseScreenResponse {
    id?: string | number;
    name?: string;
    description?: string;
    updatedAt?: string;
    theme?: unknown;
    backgroundColor?: unknown;
    backgroundImage?: unknown;
    components?: unknown;
    globalVariables?: unknown;
    pages?: unknown;
    carouselConfig?: unknown;
    v2Spec?: {
        schemaVersion?: unknown;
        layout?: unknown;
        referenceViewport?: unknown;
    } | null;
}

/**
 * 若 `raw` 是 v2 大屏（后端已透传 v2Spec 且 schemaVersion===2）→ 返回 normalized 配置；
 * 否则返回 null（调用方走 v1 路径）。
 */
export function tryLoadV2(raw: unknown): ScreenConfigV2 | null {
    if (raw === null || typeof raw !== 'object') return null;
    const resp = raw as LooseScreenResponse;
    const v2Spec = resp.v2Spec;
    if (!v2Spec || typeof v2Spec !== 'object') return null;
    if (v2Spec.schemaVersion !== 2) return null;

    const payload = {
        schemaVersion: 2,
        id: resp.id !== undefined && resp.id !== null ? String(resp.id) : undefined,
        name: resp.name,
        description: resp.description,
        updatedAt: resp.updatedAt,
        theme: resp.theme,
        backgroundColor: resp.backgroundColor,
        backgroundImage: resp.backgroundImage,
        layout: v2Spec.layout,
        referenceViewport: v2Spec.referenceViewport,
        components: resp.components,
        globalVariables: resp.globalVariables,
        pages: resp.pages,
        carouselConfig: resp.carouselConfig,
    };

    const { config, warnings } = normalizeScreenConfigV2(payload, {
        id: payload.id,
    });
    if (warnings.length > 0) {
        // eslint-disable-next-line no-console
        console.warn('[screen-v2] loaded with warnings:', warnings);
    }
    return config;
}
