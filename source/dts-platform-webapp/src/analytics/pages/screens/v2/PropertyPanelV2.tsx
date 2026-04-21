/**
 * Sprint-12 F3/T03 — v2 属性面板（最小可用版）。
 *
 * 与 v1 PropertyPanel 不同点：
 * - Size/Position 改为 grid units（cols × rows），不是 px
 * - 大屏级配置里新增 `layout.cols` / `layout.rowHeight` / `layout.gap`
 * - 组件级只改 layout 与少量常用 config（title/text/color/fontSize 通过现有渲染器层
 *   的 responsive defaults 自动生效）—— 复杂 schema 编辑留到 T03 精修
 *
 * 不引入 antd 表单控件，避免和 DesignerV2Page 的 ConfigProvider 绕开产生冲突；
 * 用原生 input/select 做最小交互。
 */

import type { CSSProperties } from "react";
import type { ComponentV2, ScreenConfigV2 } from "./types";

interface PropertyPanelV2Props {
    screen: ScreenConfigV2;
    selected: ComponentV2 | null;
    onUpdateSelected: (patch: Partial<ComponentV2>) => void;
    onScreenChange: (next: ScreenConfigV2) => void;
    onDeleteSelected?: () => void;
}

const sectionTitle: CSSProperties = {
    fontSize: 12,
    fontWeight: 600,
    color: "rgba(226,232,240,0.6)",
    letterSpacing: 0.5,
    textTransform: "uppercase",
    margin: "16px 0 8px",
};

const row: CSSProperties = {
    display: "grid",
    gridTemplateColumns: "90px 1fr",
    gap: 8,
    alignItems: "center",
    marginBottom: 8,
};

const label: CSSProperties = {
    fontSize: 12,
    color: "rgba(226,232,240,0.75)",
};

const inputStyle: CSSProperties = {
    width: "100%",
    padding: "4px 8px",
    background: "rgba(255,255,255,0.06)",
    border: "1px solid rgba(255,255,255,0.12)",
    borderRadius: 4,
    color: "#e2e8f0",
    fontSize: 13,
    boxSizing: "border-box",
};

export function PropertyPanelV2({
    screen,
    selected,
    onUpdateSelected,
    onScreenChange,
    onDeleteSelected,
}: PropertyPanelV2Props) {
    return (
        <div
            style={{
                flex: 1,
                minHeight: 0,
                padding: "12px 16px",
                color: "#e2e8f0",
                background: "#161922",
                overflow: "auto",
            }}
        >
            {/* 大屏级配置 */}
            <div style={sectionTitle}>大屏</div>
            <div style={row}>
                <span style={label}>列数 (cols)</span>
                <input
                    type="number"
                    min={1}
                    max={48}
                    step={1}
                    value={screen.layout?.cols ?? 12}
                    onChange={(e) => {
                        const cols = Math.max(1, Math.min(48, Number(e.target.value) || 12));
                        onScreenChange({ ...screen, layout: { ...screen.layout, cols } });
                    }}
                    style={inputStyle}
                />
            </div>
            <div style={row}>
                <span style={label}>行高</span>
                <select
                    value={screen.layout?.rowHeight === "auto" ? "auto" : "fixed"}
                    onChange={(e) => {
                        const mode = e.target.value;
                        onScreenChange({
                            ...screen,
                            layout: {
                                ...screen.layout,
                                rowHeight: mode === "auto" ? "auto" : 40,
                            },
                        });
                    }}
                    style={inputStyle}
                >
                    <option value="auto">auto（按 viewport 铺满）</option>
                    <option value="fixed">固定像素</option>
                </select>
            </div>
            {screen.layout?.rowHeight !== "auto" && (
                <div style={row}>
                    <span style={label}>行高 (px)</span>
                    <input
                        type="number"
                        min={12}
                        max={240}
                        step={4}
                        value={Number(screen.layout?.rowHeight ?? 40)}
                        onChange={(e) => {
                            const v = Math.max(12, Math.min(240, Number(e.target.value) || 40));
                            onScreenChange({ ...screen, layout: { ...screen.layout, rowHeight: v } });
                        }}
                        style={inputStyle}
                    />
                </div>
            )}
            <div style={row}>
                <span style={label}>间距 (px)</span>
                <input
                    type="number"
                    min={0}
                    max={48}
                    value={screen.layout?.gap ?? 12}
                    onChange={(e) => {
                        const gap = Math.max(0, Math.min(48, Number(e.target.value) || 0));
                        onScreenChange({ ...screen, layout: { ...screen.layout, gap } });
                    }}
                    style={inputStyle}
                />
            </div>
            <div style={row}>
                <span style={label}>背景色</span>
                <input
                    type="text"
                    value={screen.backgroundColor ?? ""}
                    placeholder="#1e1f26"
                    onChange={(e) => onScreenChange({ ...screen, backgroundColor: e.target.value })}
                    style={inputStyle}
                />
            </div>

            {/* 组件级 */}
            {selected ? (
                <>
                    <div style={sectionTitle}>组件 · {selected.name || selected.type}</div>
                    <div style={row}>
                        <span style={label}>名称</span>
                        <input
                            type="text"
                            value={selected.name ?? ""}
                            onChange={(e) => onUpdateSelected({ name: e.target.value })}
                            style={inputStyle}
                        />
                    </div>
                    <div style={row}>
                        <span style={label}>可见</span>
                        <select
                            value={selected.visible === false ? "false" : "true"}
                            onChange={(e) => onUpdateSelected({ visible: e.target.value === "true" })}
                            style={inputStyle}
                        >
                            <option value="true">显示</option>
                            <option value="false">隐藏</option>
                        </select>
                    </div>

                    <div style={sectionTitle}>位置（grid units）</div>
                    <div style={row}>
                        <span style={label}>X（列起点）</span>
                        <input
                            type="number"
                            min={0}
                            max={(screen.layout?.cols ?? 12) - 1}
                            value={selected.layout.x}
                            onChange={(e) =>
                                onUpdateSelected({
                                    layout: { ...selected.layout, x: Math.max(0, Number(e.target.value) || 0) },
                                })
                            }
                            style={inputStyle}
                        />
                    </div>
                    <div style={row}>
                        <span style={label}>Y（行起点）</span>
                        <input
                            type="number"
                            min={0}
                            value={selected.layout.y}
                            onChange={(e) =>
                                onUpdateSelected({
                                    layout: { ...selected.layout, y: Math.max(0, Number(e.target.value) || 0) },
                                })
                            }
                            style={inputStyle}
                        />
                    </div>
                    <div style={row}>
                        <span style={label}>W（列跨度）</span>
                        <input
                            type="number"
                            min={1}
                            max={screen.layout?.cols ?? 12}
                            value={selected.layout.w}
                            onChange={(e) =>
                                onUpdateSelected({
                                    layout: { ...selected.layout, w: Math.max(1, Number(e.target.value) || 1) },
                                })
                            }
                            style={inputStyle}
                        />
                    </div>
                    <div style={row}>
                        <span style={label}>H（行跨度）</span>
                        <input
                            type="number"
                            min={1}
                            value={selected.layout.h}
                            onChange={(e) =>
                                onUpdateSelected({
                                    layout: { ...selected.layout, h: Math.max(1, Number(e.target.value) || 1) },
                                })
                            }
                            style={inputStyle}
                        />
                    </div>

                    <div style={sectionTitle}>配置（JSON）</div>
                    <textarea
                        value={JSON.stringify(selected.config, null, 2)}
                        onChange={(e) => {
                            try {
                                const parsed = JSON.parse(e.target.value);
                                if (parsed && typeof parsed === "object") {
                                    onUpdateSelected({ config: parsed });
                                }
                            } catch {
                                // ignore JSON 编辑态中的中间无效状态
                            }
                        }}
                        rows={10}
                        style={{
                            ...inputStyle,
                            fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
                            fontSize: 12,
                            minHeight: 180,
                        }}
                    />

                    {onDeleteSelected && (
                        <button
                            type="button"
                            onClick={onDeleteSelected}
                            style={{
                                marginTop: 12,
                                padding: "6px 12px",
                                background: "rgba(248, 113, 113, 0.15)",
                                color: "#fecaca",
                                border: "1px solid rgba(248, 113, 113, 0.4)",
                                borderRadius: 4,
                                cursor: "pointer",
                            }}
                        >
                            删除组件
                        </button>
                    )}
                </>
            ) : (
                <div style={{ marginTop: 20, color: "rgba(226,232,240,0.5)", fontSize: 13 }}>
                    点击画布上的组件查看属性；从左侧组件库拖入新组件。
                </div>
            )}
        </div>
    );
}
