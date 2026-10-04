/**
 * ScaleAdapter — runtime component that scales a fixed-dimension canvas
 * to fit/fill the actual viewport. Used in preview and public pages.
 *
 * Does NOT change the editor's absolute positioning model.
 * Works by wrapping the canvas in a container that applies transform: scale().
 *
 * Chrome 95 compatible (transform: scale supported since Chrome 4).
 */
import { useState, useEffect, useRef, useCallback, type ReactNode } from 'react';

export type ScaleMode = 'fit' | 'fill' | 'stretch';

interface ScaleAdapterProps {
    /** Design canvas width (default 1920) */
    designWidth: number;
    /** Design canvas height (default 1080) */
    designHeight: number;
    /** Scaling mode */
    mode?: ScaleMode;
    /** Children — the canvas content */
    children: ReactNode;
    /** Additional className for the wrapper */
    className?: string;
}

function computeScale(
    containerW: number,
    containerH: number,
    designW: number,
    designH: number,
    mode: ScaleMode,
): { scaleX: number; scaleY: number } {
    if (containerW <= 0 || containerH <= 0) return { scaleX: 1, scaleY: 1 };

    const ratioX = containerW / designW;
    const ratioY = containerH / designH;

    switch (mode) {
        case 'fit': {
            // Show full content, may have letterbox
            const s = Math.min(ratioX, ratioY);
            return { scaleX: s, scaleY: s };
        }
        case 'fill': {
            // Fill container, may crop
            const s = Math.max(ratioX, ratioY);
            return { scaleX: s, scaleY: s };
        }
        case 'stretch':
            // Stretch to fill exactly (aspect ratio changes)
            return { scaleX: ratioX, scaleY: ratioY };
        default:
            return { scaleX: 1, scaleY: 1 };
    }
}

export function ScaleAdapter({
    designWidth,
    designHeight,
    mode = 'fit',
    children,
    className,
}: ScaleAdapterProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const [scale, setScale] = useState({ scaleX: 1, scaleY: 1 });

    const updateScale = useCallback(() => {
        const el = containerRef.current;
        if (!el) return;
        const rect = el.getBoundingClientRect();
        setScale(computeScale(rect.width, rect.height, designWidth, designHeight, mode));
    }, [designWidth, designHeight, mode]);

    useEffect(() => {
        updateScale();
        const observer = new ResizeObserver(updateScale);
        if (containerRef.current) observer.observe(containerRef.current);
        return () => observer.disconnect();
    }, [updateScale]);

    return (
        <div
            ref={containerRef}
            className={className}
            style={{
                width: '100%',
                height: '100%',
                overflow: 'hidden',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
            }}
        >
            <div
                style={{
                    width: designWidth,
                    height: designHeight,
                    transform: `scale(${scale.scaleX}, ${scale.scaleY})`,
                    transformOrigin: 'center center',
                    flexShrink: 0,
                }}
            >
                {children}
            </div>
        </div>
    );
}
