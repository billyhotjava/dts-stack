/**
 * Sprint-12 F5/T03 — v1 ScreenConfig → v2 ScreenConfigV2 的粗略迁移。
 *
 * 目标：demo 期可运行，不要求精确像素对齐。位置从绝对 px 映射到 grid units：
 *   x_grid = round(x_px / colWidth)
 *   y_grid = round(y_px / rowHeight)
 *
 * 算法有损，对每次 clamp/裁剪/负坐标归零都记录 warning，调用方可展示给用户。
 *
 * 不依赖 v1 的 ScreenContext reducer，也不改动 v1 数据；输入 v1 `ScreenConfig`，
 * 输出新 `ScreenConfigV2`。调用点（F5/T02 转 v2 按钮）负责创建新大屏。
 */

import type { ScreenComponent, ScreenConfig } from "../types";
import type { ComponentV2, ScreenConfigV2 } from "./types";

const DEFAULT_COLS = 12;
const DEFAULT_ROW_HEIGHT_PX = 40;
const DEFAULT_GAP = 8;

export interface MigrateV1ToV2Options {
    cols?: number;
    rowHeightPx?: number;
    gap?: number;
}

export interface MigrateV1ToV2Result {
    config: ScreenConfigV2;
    warnings: string[];
}

function clampGrid(value: number, min: number, max: number): number {
    if (!Number.isFinite(value)) return min;
    return Math.max(min, Math.min(max, value));
}

export function migrateV1ToV2(
    v1: ScreenConfig,
    options: MigrateV1ToV2Options = {},
): MigrateV1ToV2Result {
    const warnings: string[] = [];
    const cols = Math.max(1, Math.floor(options.cols ?? DEFAULT_COLS));
    const rowHeightPx = Math.max(8, Math.floor(options.rowHeightPx ?? DEFAULT_ROW_HEIGHT_PX));
    const gap = Math.max(0, Math.floor(options.gap ?? DEFAULT_GAP));

    const designWidth = Math.max(1, v1.width || 1920);
    const designHeight = Math.max(1, v1.height || 1080);
    const colWidthPx = designWidth / cols;

    const v1Components: ScreenComponent[] = Array.isArray(v1.components) ? v1.components : [];

    const v2Components: ComponentV2[] = v1Components.map((c) => {
        const name = c.name || c.id;

        const rawX = Number.isFinite(c.x) ? c.x : 0;
        const rawY = Number.isFinite(c.y) ? c.y : 0;
        const rawW = Number.isFinite(c.width) && c.width > 0 ? c.width : colWidthPx;
        const rawH = Number.isFinite(c.height) && c.height > 0 ? c.height : rowHeightPx;

        if (rawX < 0) {
            warnings.push(`组件「${name}」负坐标 x=${rawX} 调整为 0`);
        }
        if (rawY < 0) {
            warnings.push(`组件「${name}」负坐标 y=${rawY} 调整为 0`);
        }

        let w = Math.max(1, Math.round(rawW / colWidthPx));
        let h = Math.max(1, Math.round(rawH / rowHeightPx));

        if (w > cols) {
            warnings.push(`组件「${name}」宽度 ${rawW}px 超过画布宽度，裁剪为 ${cols} 列`);
            w = cols;
        }

        const xMax = cols - w;
        const xClean = Math.max(0, Math.round(rawX / colWidthPx));
        const x = clampGrid(xClean, 0, Math.max(0, xMax));
        if (xClean > xMax) {
            warnings.push(`组件「${name}」右侧溢出，x 从 ${xClean} 调整到 ${x}`);
        }

        const y = Math.max(0, Math.round(rawY / rowHeightPx));

        return {
            id: c.id,
            type: c.type,
            name,
            layout: {
                x,
                y,
                w,
                h,
                minW: 1,
                minH: 1,
            },
            config: c.config ?? {},
            visible: c.visible !== false,
            zIndex: typeof c.zIndex === "number" ? c.zIndex : undefined,
        } satisfies ComponentV2;
    });

    const config: ScreenConfigV2 = {
        schemaVersion: 2,
        id: v1.id ? String(v1.id) : undefined,
        name: v1.name,
        description: v1.description,
        theme: v1.theme,
        backgroundColor: v1.backgroundColor,
        backgroundImage: v1.backgroundImage,
        layout: {
            cols,
            rowHeight: rowHeightPx,
            gap,
        },
        components: v2Components,
        globalVariables: v1.globalVariables,
        carouselConfig: v1.carouselConfig,
        referenceViewport: { width: designWidth, height: designHeight },
    };

    return { config, warnings };
}
