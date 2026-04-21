/**
 * Sprint-12 F3/T01-T02 — v2 大屏编辑画布。
 *
 * 与 ResponsiveScreenLayout 共用同一个 react-grid-layout 引擎（WYSIWYG）：
 * - `isDraggable` / `isResizable` 打开
 * - `onLayoutChange` 把 grid 坐标写回 ScreenConfigV2
 * - 支持从 ComponentLibraryPanel 拖拽新增组件（react-dnd）
 * - 点击组件选中（Ctrl/Shift 多选）
 *
 * 设计态 rowHeight 固定（默认 40px），避免编辑时高度抖动；运行时按 `screen.layout.rowHeight`
 * 行为（'auto' / number）正常生效。
 *
 * Chrome 95 兼容：沿用 F1/T01 已验证的 react-grid-layout@^1.5.3 + ResizeObserver 栈。
 */

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import GridLayout, { type Layout as RGLLayout } from "react-grid-layout";
import { useDrop } from "react-dnd";

import type { ComponentV2, ScreenConfigV2, GridLayoutCell } from "./types";
import type { ComponentItem, ComponentType, ScreenComponent } from "../types";
import { ComponentRenderer } from "../components/ComponentRenderer";
import { applyChartPresetDefaults, isChartComponentType } from "../chartPresets";
import "./responsiveLayout.css";
import "./designerCanvasV2.css";

const DESIGN_ROW_HEIGHT_PX = 40;

export interface DesignerCanvasV2Props {
    /** 当前 v2 大屏配置（受控） */
    screen: ScreenConfigV2;
    /** 任一改动（layout / 新增组件）的回调 */
    onChange: (next: ScreenConfigV2) => void;
    /** 选中的组件 id 集合（受控） */
    selectedIds: string[];
    /** 选择回调（点击空白清空选择） */
    onSelect: (ids: string[]) => void;
    /** 是否显示网格背景（T04） */
    showGrid?: boolean;
}

function toRGLLayout(c: ComponentV2): RGLLayout {
    return {
        i: c.id,
        x: c.layout.x,
        y: c.layout.y,
        w: c.layout.w,
        h: c.layout.h,
        minW: c.layout.minW ?? 1,
        minH: c.layout.minH ?? 1,
        maxW: c.layout.maxW,
        maxH: c.layout.maxH,
        static: c.static === true,
    };
}

function v2ToV1Component(c: ComponentV2): ScreenComponent {
    return {
        id: c.id,
        type: c.type as ScreenComponent["type"],
        name: c.name ?? c.id,
        x: 0,
        y: 0,
        width: 0,
        height: 0,
        zIndex: c.zIndex ?? 0,
        locked: c.static === true,
        visible: c.visible !== false,
        config: c.config,
    };
}

function computeMaxRow(components: ComponentV2[]): number {
    let maxRow = 1;
    for (const c of components) {
        const bottom = (c.layout?.y ?? 0) + (c.layout?.h ?? 0);
        if (bottom > maxRow) maxRow = bottom;
    }
    return maxRow;
}

/** 新组件默认 grid 尺寸：按类型粗分图表 vs 文字 */
function defaultGridForType(type: string): GridLayoutCell {
    if (isChartComponentType(type as ComponentType)) {
        return { x: 0, y: Infinity, w: 6, h: 6, minW: 3, minH: 3 };
    }
    if (type === "title" || type === "text" || type === "markdown-text") {
        return { x: 0, y: Infinity, w: 6, h: 2, minW: 2, minH: 1 };
    }
    if (type === "number-card" || type === "stat-card") {
        return { x: 0, y: Infinity, w: 3, h: 3, minW: 2, minH: 2 };
    }
    if (type === "table") {
        return { x: 0, y: Infinity, w: 8, h: 6, minW: 4, minH: 3 };
    }
    return { x: 0, y: Infinity, w: 4, h: 4, minW: 2, minH: 2 };
}

function generateV2Id(): string {
    try {
        return `c_${crypto.randomUUID().replace(/-/g, "").slice(0, 12)}`;
    } catch {
        return `c_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
    }
}

export function DesignerCanvasV2(props: DesignerCanvasV2Props) {
    const { screen, onChange, selectedIds, onSelect, showGrid = true } = props;
    const containerRef = useRef<HTMLDivElement | null>(null);
    const [containerWidth, setContainerWidth] = useState(() => 1200);

    useEffect(() => {
        const el = containerRef.current;
        if (!el) return;
        const update = () => {
            const rect = el.getBoundingClientRect();
            setContainerWidth(Math.max(320, Math.floor(rect.width)));
        };
        update();
        const observer = new ResizeObserver(update);
        observer.observe(el);
        return () => observer.disconnect();
    }, []);

    const cols = screen.layout?.cols ?? 12;
    const gap = screen.layout?.gap ?? 12;

    const visibleComponents = useMemo(
        () => screen.components.filter((c) => c.visible !== false),
        [screen.components],
    );

    const rglLayout = useMemo<RGLLayout[]>(
        () => visibleComponents.map(toRGLLayout),
        [visibleComponents],
    );

    /**
     * Drop new component from ComponentLibraryPanel.
     * 库里拖的是 `ComponentItem`（accept=`COMPONENT`），我们只取 type/name/defaultConfig，
     * 尺寸改由 grid 默认值决定。
     */
    const [{ isOver }, dropRef] = useDrop(
        () => ({
            accept: "COMPONENT",
            drop: (item: ComponentItem) => {
                const newGrid = defaultGridForType(item.type);
                const rawConfig = isChartComponentType(item.type)
                    ? applyChartPresetDefaults({ ...item.defaultConfig }, "business")
                    : { ...item.defaultConfig };

                // y: Infinity 交给 react-grid-layout 自动找底部空位
                const nextMaxRow = computeMaxRow(screen.components);
                const resolvedY = Number.isFinite(newGrid.y) ? (newGrid.y as number) : nextMaxRow;

                const newComp: ComponentV2 = {
                    id: generateV2Id(),
                    type: item.type,
                    name: item.name,
                    layout: {
                        x: newGrid.x,
                        y: resolvedY,
                        w: newGrid.w,
                        h: newGrid.h,
                        minW: newGrid.minW,
                        minH: newGrid.minH,
                    },
                    config: rawConfig,
                    visible: true,
                };
                onChange({
                    ...screen,
                    components: [...screen.components, newComp],
                });
                onSelect([newComp.id]);
            },
            collect: (monitor) => ({ isOver: monitor.isOver() }),
        }),
        [screen, onChange, onSelect],
    );

    /** layout 变更（拖动 / resize / 新增占位）统一走这里回写 */
    const handleLayoutChange = useCallback(
        (layout: RGLLayout[]) => {
            // 仅当至少一个组件的 x/y/w/h 与 state 不一致时才 setState，避免 render 风暴
            const byId = new Map<string, RGLLayout>();
            for (const l of layout) byId.set(l.i, l);

            let changed = false;
            const nextComponents = screen.components.map((c) => {
                const l = byId.get(c.id);
                if (!l) return c;
                if (
                    l.x === c.layout.x &&
                    l.y === c.layout.y &&
                    l.w === c.layout.w &&
                    l.h === c.layout.h
                ) {
                    return c;
                }
                changed = true;
                return {
                    ...c,
                    layout: {
                        ...c.layout,
                        x: l.x,
                        y: l.y,
                        w: l.w,
                        h: l.h,
                    },
                };
            });

            if (!changed) return;
            onChange({ ...screen, components: nextComponents });
        },
        [screen, onChange],
    );

    const handleComponentMouseDown = useCallback(
        (e: React.MouseEvent, id: string) => {
            // Shift/Ctrl 多选
            if (e.shiftKey || e.ctrlKey || e.metaKey) {
                if (selectedIds.includes(id)) {
                    onSelect(selectedIds.filter((x) => x !== id));
                } else {
                    onSelect([...selectedIds, id]);
                }
            } else if (!selectedIds.includes(id)) {
                onSelect([id]);
            }
            // 不 stopPropagation，让 react-grid-layout 的拖拽逻辑继续接收
        },
        [selectedIds, onSelect],
    );

    const handleCanvasBackgroundClick = useCallback(
        (e: React.MouseEvent) => {
            if (e.target === e.currentTarget) {
                onSelect([]);
            }
        },
        [onSelect],
    );

    const rootBackground = screen.backgroundColor || "#1e1f26";

    return (
        <div
            ref={(node) => {
                containerRef.current = node;
                dropRef(node);
            }}
            className="v2-designer-canvas"
            style={{
                position: "relative",
                width: "100%",
                height: "100%",
                overflow: "auto",
                background: rootBackground,
                backgroundImage: screen.backgroundImage
                    ? `url(${CSS.escape(screen.backgroundImage)})`
                    : undefined,
                backgroundSize: "cover",
                backgroundPosition: "center",
                backgroundRepeat: "no-repeat",
            }}
            onClick={handleCanvasBackgroundClick}
            data-testid="v2-designer-canvas"
        >
            {showGrid && (
                <div
                    className="v2-designer-grid-bg"
                    style={{
                        position: "absolute",
                        inset: 0,
                        pointerEvents: "none",
                        // 12 列网格线 + 40px 基线行
                        backgroundImage: `
                            linear-gradient(to right, rgba(148,163,184,0.08) 1px, transparent 1px),
                            linear-gradient(to bottom, rgba(148,163,184,0.06) 1px, transparent 1px)
                        `,
                        backgroundSize: `${(containerWidth - gap * (cols - 1)) / cols + gap}px 100%, 100% ${DESIGN_ROW_HEIGHT_PX + gap}px`,
                    }}
                />
            )}
            <GridLayout
                className="v2-screen-grid v2-designer-grid"
                layout={rglLayout}
                cols={cols}
                rowHeight={DESIGN_ROW_HEIGHT_PX}
                width={containerWidth}
                margin={[gap, gap]}
                containerPadding={[gap, gap]}
                isDraggable
                isResizable
                compactType={null}
                preventCollision={false}
                onLayoutChange={handleLayoutChange}
                useCSSTransforms
                draggableHandle=".v2-component-drag-handle"
            >
                {visibleComponents.map((c) => {
                    const adapted = v2ToV1Component(c);
                    const selected = selectedIds.includes(c.id);
                    return (
                        <div
                            key={c.id}
                            data-component-id={c.id}
                            data-component-type={c.type}
                            className={`v2-designer-cell ${selected ? "is-selected" : ""}`}
                            onMouseDown={(e) => handleComponentMouseDown(e, c.id)}
                            onClick={(e) => e.stopPropagation()}
                        >
                            <div className="v2-component-drag-handle" title="拖动">
                                <span className="v2-component-drag-handle__grip">⋮⋮</span>
                                <span className="v2-component-drag-handle__label">{c.name || c.type}</span>
                            </div>
                            <div className="v2-designer-cell__body">
                                <ComponentRenderer component={adapted} mode="designer" />
                            </div>
                        </div>
                    );
                })}
            </GridLayout>

            {isOver && (
                <div
                    style={{
                        position: "absolute",
                        inset: 0,
                        pointerEvents: "none",
                        background: "rgba(99, 102, 241, 0.08)",
                        border: "2px dashed rgba(99, 102, 241, 0.6)",
                        borderRadius: 6,
                    }}
                />
            )}
        </div>
    );
}
