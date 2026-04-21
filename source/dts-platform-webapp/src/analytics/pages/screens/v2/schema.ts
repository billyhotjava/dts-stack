/**
 * Sprint-12 — v2 ScreenConfig 的 normalize + validate。
 *
 * 读取时统一走 normalize（宽容）：补默认、修类型、裁剪越界；把有问题的地方
 * 写入 `warnings` 但不抛。
 *
 * 保存/发布前走 validate（严格）：返回错误清单，空数组表示通过。
 */

import {
    DEFAULT_SCREEN_LAYOUT_V2,
    type ScreenConfigV2,
    type ComponentV2,
    type GridLayoutCell,
    type ScreenLayoutV2,
} from './types';

export interface NormalizeResult {
    config: ScreenConfigV2;
    warnings: string[];
}

/** 安全生成组件 id（与项目既有模式一致，用 crypto.randomUUID — Chrome 92+ 支持） */
function newComponentId(): string {
    const uuid = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
    return `comp_${uuid.replace(/-/g, '').slice(0, 12)}`;
}

function toFiniteInt(value: unknown, fallback: number): number {
    if (typeof value === 'number' && Number.isFinite(value)) {
        return Math.floor(value);
    }
    if (typeof value === 'string') {
        const parsed = Number(value);
        if (Number.isFinite(parsed)) return Math.floor(parsed);
    }
    return fallback;
}

function normalizeLayoutParams(raw: unknown, warnings: string[]): ScreenLayoutV2 {
    if (raw === null || typeof raw !== 'object') {
        warnings.push('layout 字段缺失或非对象，使用默认 { cols: 12, rowHeight: "auto", gap: 12 }');
        return { ...DEFAULT_SCREEN_LAYOUT_V2 };
    }
    const src = raw as Partial<ScreenLayoutV2>;
    let cols = toFiniteInt(src.cols, DEFAULT_SCREEN_LAYOUT_V2.cols);
    if (cols <= 0) {
        warnings.push(`layout.cols 必须 > 0，收到 ${src.cols}，回落到 12`);
        cols = DEFAULT_SCREEN_LAYOUT_V2.cols;
    }

    let rowHeight: ScreenLayoutV2['rowHeight'];
    if (src.rowHeight === 'auto') {
        rowHeight = 'auto';
    } else if (typeof src.rowHeight === 'number' && Number.isFinite(src.rowHeight) && src.rowHeight > 0) {
        rowHeight = Math.floor(src.rowHeight);
    } else {
        if (src.rowHeight !== undefined) {
            warnings.push(`layout.rowHeight 非法（${String(src.rowHeight)}），回落到 "auto"`);
        }
        rowHeight = DEFAULT_SCREEN_LAYOUT_V2.rowHeight;
    }

    const gap = toFiniteInt(src.gap, DEFAULT_SCREEN_LAYOUT_V2.gap);

    return { cols, rowHeight, gap };
}

function normalizeCell(raw: unknown, cols: number, warnings: string[], ctx: string): GridLayoutCell {
    const src = (raw ?? {}) as Partial<GridLayoutCell>;
    let x = Math.max(0, toFiniteInt(src.x, 0));
    let y = Math.max(0, toFiniteInt(src.y, 0));
    let w = Math.max(1, toFiniteInt(src.w, 2));
    let h = Math.max(1, toFiniteInt(src.h, 2));

    if (x >= cols) {
        warnings.push(`${ctx}: layout.x (${x}) 超过 cols (${cols})，裁剪到 ${cols - 1}`);
        x = Math.max(0, cols - 1);
    }
    if (x + w > cols) {
        const trimmed = cols - x;
        warnings.push(`${ctx}: layout.w 从 ${w} 裁剪到 ${trimmed}（超过 cols - x）`);
        w = Math.max(1, trimmed);
    }

    const cell: GridLayoutCell = { x, y, w, h };
    if (typeof src.minW === 'number') cell.minW = Math.max(1, Math.floor(src.minW));
    if (typeof src.minH === 'number') cell.minH = Math.max(1, Math.floor(src.minH));
    if (typeof src.maxW === 'number') cell.maxW = Math.max(1, Math.floor(src.maxW));
    if (typeof src.maxH === 'number') cell.maxH = Math.max(1, Math.floor(src.maxH));
    return cell;
}

function normalizeComponent(
    raw: unknown,
    cols: number,
    index: number,
    seenIds: Set<string>,
    warnings: string[],
): ComponentV2 | null {
    if (raw === null || typeof raw !== 'object') {
        warnings.push(`components[${index}]: 非对象，跳过`);
        return null;
    }
    const src = raw as Partial<ComponentV2>;
    if (typeof src.type !== 'string' || src.type.length === 0) {
        warnings.push(`components[${index}]: 缺少 type，跳过`);
        return null;
    }

    let id = typeof src.id === 'string' && src.id.length > 0 ? src.id : newComponentId();
    if (seenIds.has(id)) {
        const fresh = newComponentId();
        warnings.push(`components[${index}]: id "${id}" 重复，改为 "${fresh}"`);
        id = fresh;
    }
    seenIds.add(id);

    const ctx = `components[${index}](${id})`;
    const layout = normalizeCell(src.layout, cols, warnings, ctx);

    const comp: ComponentV2 = {
        id,
        type: src.type,
        layout,
        config: (src.config && typeof src.config === 'object') ? { ...src.config } : {},
    };
    if (src.name !== undefined) comp.name = String(src.name);
    comp.visible = src.visible !== false;
    if (src.static === true) comp.static = true;
    if (src.visibleByDevice && typeof src.visibleByDevice === 'object') {
        comp.visibleByDevice = { ...src.visibleByDevice };
    }
    if (typeof src.zIndex === 'number' && Number.isFinite(src.zIndex)) {
        comp.zIndex = Math.floor(src.zIndex);
    }
    return comp;
}

/**
 * 宽容读取：无论输入多脏，都返回一个可渲染的 ScreenConfigV2 + warning list。
 * 不抛错。
 */
export function normalizeScreenConfigV2(raw: unknown, opts?: { id?: string }): NormalizeResult {
    const warnings: string[] = [];
    const src = (raw && typeof raw === 'object') ? (raw as Partial<ScreenConfigV2>) : {};

    if (src.schemaVersion !== 2) {
        warnings.push(`schemaVersion 应为 2，收到 ${String(src.schemaVersion)}，已按 v2 处理`);
    }

    const layout = normalizeLayoutParams(src.layout, warnings);

    const seenIds = new Set<string>();
    const rawComponents = Array.isArray(src.components) ? src.components : [];
    if (!Array.isArray(src.components)) {
        warnings.push('components 缺失或非数组，视作空');
    }
    const components: ComponentV2[] = [];
    rawComponents.forEach((c, i) => {
        const normalized = normalizeComponent(c, layout.cols, i, seenIds, warnings);
        if (normalized) components.push(normalized);
    });

    const config: ScreenConfigV2 = {
        schemaVersion: 2,
        id: opts?.id ?? (typeof src.id === 'string' ? src.id : undefined),
        name: typeof src.name === 'string' ? src.name : '新建大屏',
        description: typeof src.description === 'string' ? src.description : undefined,
        updatedAt: typeof src.updatedAt === 'string' ? src.updatedAt : undefined,
        theme: src.theme,
        backgroundColor: typeof src.backgroundColor === 'string' ? src.backgroundColor : '#1e1f26',
        backgroundImage: typeof src.backgroundImage === 'string' ? src.backgroundImage : undefined,
        layout,
        components,
        globalVariables: Array.isArray(src.globalVariables) ? [...src.globalVariables] : undefined,
        carouselConfig: src.carouselConfig,
        referenceViewport: src.referenceViewport,
    };

    return { config, warnings };
}

/**
 * 严格校验：返回错误字符串列表，空数组表示通过。
 * 用于保存 / 发布前的门禁。
 */
export function validateScreenConfigV2(config: ScreenConfigV2): string[] {
    const errors: string[] = [];

    if (config.schemaVersion !== 2) {
        errors.push(`schemaVersion 必须为 2，收到 ${String(config.schemaVersion)}`);
    }

    if (!config.layout || typeof config.layout !== 'object') {
        errors.push('layout 必须存在');
    } else {
        if (!Number.isInteger(config.layout.cols) || config.layout.cols <= 0) {
            errors.push(`layout.cols 必须为正整数，当前 ${config.layout.cols}`);
        }
        if (config.layout.rowHeight !== 'auto' && (typeof config.layout.rowHeight !== 'number' || config.layout.rowHeight <= 0)) {
            errors.push(`layout.rowHeight 必须为 "auto" 或正整数，当前 ${String(config.layout.rowHeight)}`);
        }
        if (!Number.isInteger(config.layout.gap) || config.layout.gap < 0) {
            errors.push(`layout.gap 必须为非负整数，当前 ${config.layout.gap}`);
        }
    }

    if (!Array.isArray(config.components)) {
        errors.push('components 必须为数组');
        return errors;
    }

    const cols = config.layout?.cols ?? 12;
    const seenIds = new Set<string>();
    config.components.forEach((c, i) => {
        const ctx = `components[${i}]`;
        if (typeof c.id !== 'string' || c.id.length === 0) {
            errors.push(`${ctx}: id 必须为非空字符串`);
        } else if (seenIds.has(c.id)) {
            errors.push(`${ctx}: id 重复 "${c.id}"`);
        } else {
            seenIds.add(c.id);
        }
        if (typeof c.type !== 'string' || c.type.length === 0) {
            errors.push(`${ctx}: type 必须为非空字符串`);
        }
        if (!c.layout) {
            errors.push(`${ctx}: layout 缺失`);
        } else {
            const { x, y, w, h } = c.layout;
            if (!Number.isInteger(x) || x < 0) errors.push(`${ctx}: layout.x 必须为非负整数`);
            if (!Number.isInteger(y) || y < 0) errors.push(`${ctx}: layout.y 必须为非负整数`);
            if (!Number.isInteger(w) || w <= 0) errors.push(`${ctx}: layout.w 必须为正整数`);
            if (!Number.isInteger(h) || h <= 0) errors.push(`${ctx}: layout.h 必须为正整数`);
            if (Number.isInteger(x) && Number.isInteger(w) && x + w > cols) {
                errors.push(`${ctx}: layout.x + w (${x + w}) 超过 cols (${cols})`);
            }
        }
    });

    return errors;
}
