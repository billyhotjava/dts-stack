import { useEffect, useRef, useState, type RefObject } from "react";

export interface UseContainerFontSizeOptions {
    min: number;
    max: number;
    ratio: number;
}

export function useContainerFontSize(
    containerRef: RefObject<HTMLElement | null>,
    opts: UseContainerFontSizeOptions,
): number {
    const [fontSize, setFontSize] = useState<number>(opts.min);
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
        compute(el.clientWidth);

        return () => {
            if (rafId) cancelAnimationFrame(rafId);
            observer.disconnect();
        };
    }, [containerRef]);

    return fontSize;
}
