/**
 * Sprint-12 F4/T02 — 容器宽度驱动的字号自适应 hook。
 *
 * 为什么不用 CSS `clamp(… 2cqw …)`：
 * `cqw` 是 container query unit，Chrome 95 不支持（需要 Chrome 105+）。
 * 客户浏览器是 Chrome 95，因此只能走 ResizeObserver + React state 路径。
 * Chrome 95 原生支持 ResizeObserver，不需要 polyfill。
 *
 * 使用：
 * ```tsx
 * const ref = useRef<HTMLDivElement>(null);
 * const fontSize = useContainerFontSize(ref, { min: 16, max: 72, ratio: 0.15 });
 * return <div ref={ref}><span style={{ fontSize }}>{value}</span></div>;
 * ```
 *
 * 计算规则：`fontSize = clamp(min, containerWidth * ratio, max)`。
 */

import { useEffect, useRef, useState, type RefObject } from "react";

export interface UseContainerFontSizeOptions {
    /** 最小字号（px） */
    min: number;
    /** 最大字号（px） */
    max: number;
    /** 相对容器宽度的比例：fontSize = clamp(min, width * ratio, max) */
    ratio: number;
}

/**
 * 监听 containerRef 宽度变化，返回按比例计算的字号（px）。
 *
 * - 使用 ResizeObserver，高频回调用 rAF 合并，避免每帧 setState。
 * - 初始值取 opts.min；容器挂载后会立即触发一次 observe。
 */
export function useContainerFontSize(
    containerRef: RefObject<HTMLElement | null>,
    opts: UseContainerFontSizeOptions,
): number {
    const [fontSize, setFontSize] = useState<number>(opts.min);
    // 用 ref 持有最新 opts，避免 opts 每帧新引用触发重订阅
    const optsRef = useRef(opts);
    optsRef.current = opts;

    useEffect(() => {
        const el = containerRef.current;
        if (!el || typeof ResizeObserver === "undefined") return;

        let rafId = 0;
        const compute = (width: number) => {
            const { min, max, ratio } = optsRef.current;
            const next = Math.max(min, Math.min(max, width * ratio));
            setFontSize((prev) => (Math.abs(prev - next) < 0.5 ? prev : next));
        };

        const observer = new ResizeObserver((entries) => {
            if (rafId) cancelAnimationFrame(rafId);
            const width = entries[0]?.contentRect.width ?? el.clientWidth;
            rafId = requestAnimationFrame(() => compute(width));
        });
        observer.observe(el);

        // 首帧立即计算一次，避免闪烁
        compute(el.clientWidth);

        return () => {
            if (rafId) cancelAnimationFrame(rafId);
            observer.disconnect();
        };
    }, [containerRef]);

    return fontSize;
}
