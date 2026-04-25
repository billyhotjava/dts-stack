/**
 * Sprint-12 F1/T02 — v2 大屏只读渲染组件。
 *
 * 用 react-grid-layout 做网格布局（isDraggable/isResizable 关闭）。
 * 组件在容器原生布局流里，viewport 变化时格子位置自动重算；组件内部响应式
 * 由 F4 阶段的 autoResize / container-sized fontSize 等提供（本 task 只负责外层布局）。
 *
 * Chrome 95 兼容：react-grid-layout@^1.5.3 静态检查通过（F1/T01 证据）。
 */

import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import GridLayout, { type Layout as RGLLayout } from 'react-grid-layout';

import type { ComponentV2, ScreenConfigV2, GridLayoutCell } from './types';
import type { ScreenComponent, ScreenTheme } from '../types';
import { ComponentRenderer } from '../components/ComponentRenderer';
import './responsiveLayout.css';

const DEFAULT_ROW_HEIGHT_PX = 40;
const MIN_ROW_HEIGHT_PX = 24;

export interface ResponsiveScreenLayoutProps {
    screen: ScreenConfigV2;
    theme?: ScreenTheme;
    className?: string;
    /** 额外包裹 className / style，用于外部 fullscreen 容器定制背景 */
    rootStyle?: React.CSSProperties;
    components?: ComponentV2[];
    backgroundColor?: string;
    backgroundImage?: string;
}

function toRGLLayout(c: ComponentV2): RGLLayout {
    return {
        i: c.id,
        x: c.layout.x,
        y: c.layout.y,
        w: c.layout.w,
        h: c.layout.h,
        minW: c.layout.minW,
        minH: c.layout.minH,
        maxW: c.layout.maxW,
        maxH: c.layout.maxH,
        static: true, // 只读：锁死位置与尺寸
    };
}

/**
 * 把 v2 ComponentV2 适配为 v1 ScreenComponent 形状，复用现有 ComponentRenderer。
 * 外层 grid 负责放位；x/y/width/height 这里填 0 不影响渲染。
 */
function v2ToV1Component(c: ComponentV2): ScreenComponent {
    return {
        id: c.id,
        groupId: c.groupId,
        parentContainerId: c.parentContainerId,
        type: c.type as ScreenComponent['type'],
        name: c.name ?? c.id,
        x: 0,
        y: 0,
        width: 0,
        height: 0,
        zIndex: c.zIndex ?? 0,
        locked: c.static === true,
        visible: c.visible !== false,
        config: c.config,
        dataSource: c.dataSource,
        drillDown: c.drillDown,
        actions: c.actions,
        interaction: c.interaction,
    };
}

/** Compute the max row (y + h) across visible components — used for auto rowHeight. */
function computeMaxRow(components: ComponentV2[]): number {
    let maxRow = 1;
    for (const c of components) {
        if (c.visible === false) continue;
        const bottom = (c.layout?.y ?? 0) + (c.layout?.h ?? 0);
        if (bottom > maxRow) maxRow = bottom;
    }
    return maxRow;
}

export const ResponsiveScreenLayout = memo(function ResponsiveScreenLayout({
    screen,
    theme,
    className,
    rootStyle,
    components,
    backgroundColor,
    backgroundImage,
}: ResponsiveScreenLayoutProps) {
    const containerRef = useRef<HTMLDivElement | null>(null);
    const [containerWidth, setContainerWidth] = useState(() => window.innerWidth);
    const [containerHeight, setContainerHeight] = useState(() => window.innerHeight);

    const updateSize = useCallback(() => {
        const el = containerRef.current;
        if (!el) return;
        const rect = el.getBoundingClientRect();
        setContainerWidth(rect.width);
        setContainerHeight(rect.height);
    }, []);

    // Chrome 95 原生支持 ResizeObserver，无需 polyfill。
    useEffect(() => {
        updateSize();
        const el = containerRef.current;
        if (!el) return;
        const observer = new ResizeObserver(updateSize);
        observer.observe(el);
        return () => observer.disconnect();
    }, [updateSize]);

    const visibleComponents = useMemo(
        () => (components ?? screen.components).filter((c) => c.visible !== false),
        [components, screen.components],
    );

    const layout = useMemo<RGLLayout[]>(
        () => visibleComponents.map(toRGLLayout),
        [visibleComponents],
    );

    const cols = screen.layout?.cols ?? 12;
    const gap = screen.layout?.gap ?? 12;

    // rowHeight = 'auto' → 填满 viewport；number → 固定。
    const rowHeight = useMemo(() => {
        const cfg = screen.layout?.rowHeight ?? 'auto';
        if (cfg === 'auto') {
            const maxRow = computeMaxRow(visibleComponents);
            if (maxRow <= 0 || containerHeight <= 0) return DEFAULT_ROW_HEIGHT_PX;
            // 把总间距减掉后均匀分给所有 row
            const totalGap = gap * Math.max(0, maxRow - 1);
            const available = Math.max(containerHeight - totalGap, MIN_ROW_HEIGHT_PX * maxRow);
            return Math.max(MIN_ROW_HEIGHT_PX, Math.floor(available / maxRow));
        }
        return Math.max(MIN_ROW_HEIGHT_PX, Math.floor(cfg));
    }, [screen.layout?.rowHeight, visibleComponents, containerHeight, gap]);

    const rootBackground = backgroundColor ?? screen.backgroundColor ?? '#1e1f26';
    const rootBackgroundImage = backgroundImage ?? screen.backgroundImage;

    // 空配置兜底：给一个提示，避免白屏。
    const hasContent = visibleComponents.length > 0;

    return (
        <div
            ref={containerRef}
            className={['v2-screen-root', className].filter(Boolean).join(' ')}
            style={{
                background: rootBackground,
                backgroundImage: rootBackgroundImage ? `url(${CSS.escape(rootBackgroundImage)})` : undefined,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
                backgroundRepeat: 'no-repeat',
                ...rootStyle,
            }}
            data-testid="v2-screen-root"
        >
            {hasContent ? (
                <GridLayout
                    className="v2-screen-grid"
                    layout={layout}
                    cols={cols}
                    rowHeight={rowHeight}
                    width={containerWidth}
                    margin={[gap, gap]}
                    containerPadding={[0, 0]}
                    isDraggable={false}
                    isResizable={false}
                    compactType={null}
                    preventCollision={false}
                    useCSSTransforms
                >
                    {visibleComponents.map((c) => {
                        const adapted = v2ToV1Component(c);
                        return (
                            <div
                                key={c.id}
                                data-component-id={c.id}
                                data-component-type={c.type}
                                style={{ overflow: 'hidden' }}
                            >
                                <ComponentRenderer component={adapted} mode="preview" theme={theme} />
                            </div>
                        );
                    })}
                </GridLayout>
            ) : (
                <div
                    style={{
                        position: 'absolute',
                        inset: 0,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'rgba(255,255,255,0.5)',
                        fontSize: 16,
                    }}
                    data-testid="v2-screen-empty"
                >
                    大屏还没有组件
                </div>
            )}
        </div>
    );
});

// 导出类型帮助测试
export type { GridLayoutCell };
