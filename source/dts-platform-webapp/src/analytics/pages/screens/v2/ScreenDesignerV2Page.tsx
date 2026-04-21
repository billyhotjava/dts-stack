/**
 * Sprint-12 F3/T01 — v2 大屏编辑器页面壳。
 *
 * 为什么独立一页而不是在 ScreenDesignerPage 里分支：
 * - v1 编辑器已经很大（ScreenDesignerPage 510 行 + DesignerCanvas 432 行 + 众多
 *   子组件与 ScreenContext reducer），其数据模型是 v1 扁平 px。
 * - v2 用 grid units，编辑器 UX 与 ResponsiveScreenLayout 一致（同一 react-grid-layout
 *   引擎），逻辑截然不同。
 * - 并行维护比强行把两套模型塞进一个 context 更稳，同时 v1 不受影响。
 *
 * 路由：/bi/screens/:id/designer-v2
 *
 * 数据流（最小闭环）：
 *   GET /bi/api/screens/:id → tryLoadV2 → setScreenV2
 *   用户拖放/resize/删/改 → setScreenV2
 *   保存 → PUT /bi/api/screens/:id { name, components, v2Spec: { layout, schemaVersion, referenceViewport } }
 *
 * 后端在 F2/T03 已经透传 v2Spec，不需要改动。
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { DndProvider } from "react-dnd";
import { HTML5Backend } from "react-dnd-html5-backend";
import { useNavigate, useParams } from "react-router";
import { ConfigProvider, theme as antdTheme } from "antd";
import { toast } from "sonner";

import { analyticsApi } from "../../../api/analyticsApi";
import { ComponentLibraryPanel } from "../components";
import { DesignerCanvasV2 } from "./DesignerCanvasV2";
import { PropertyPanelV2 } from "./PropertyPanelV2";
import { tryLoadV2 } from "./loader";
import { createEmptyScreenV2, type ComponentV2, type ScreenConfigV2 } from "./types";
import "../ScreenDesigner.css";

interface V2DesignerShellProps {
    screen: ScreenConfigV2;
    onChange: (next: ScreenConfigV2) => void;
    selectedIds: string[];
    onSelect: (ids: string[]) => void;
    onSave: () => void | Promise<void>;
    onPreview: () => void;
    isSaving: boolean;
    dirty: boolean;
}

function V2DesignerShell({
    screen,
    onChange,
    selectedIds,
    onSelect,
    onSave,
    onPreview,
    isSaving,
    dirty,
}: V2DesignerShellProps) {
    const handleDelete = useCallback(() => {
        if (selectedIds.length === 0) return;
        const keep = new Set(screen.components.map((c) => c.id));
        for (const id of selectedIds) keep.delete(id);
        onChange({
            ...screen,
            components: screen.components.filter((c) => keep.has(c.id)),
        });
        onSelect([]);
    }, [screen, selectedIds, onChange, onSelect]);

    const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        onChange({ ...screen, name: e.target.value });
    };

    // Delete 快捷键
    useEffect(() => {
        const handler = (e: KeyboardEvent) => {
            const target = e.target as HTMLElement | null;
            if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable)) {
                return;
            }
            if ((e.key === "Delete" || e.key === "Backspace") && selectedIds.length > 0) {
                e.preventDefault();
                handleDelete();
            }
        };
        window.addEventListener("keydown", handler);
        return () => window.removeEventListener("keydown", handler);
    }, [handleDelete, selectedIds]);

    const selectedComponent: ComponentV2 | null = useMemo(() => {
        if (selectedIds.length !== 1) return null;
        return screen.components.find((c) => c.id === selectedIds[0]) ?? null;
    }, [selectedIds, screen.components]);

    const handleUpdateSelected = useCallback(
        (patch: Partial<ComponentV2>) => {
            if (selectedIds.length !== 1) return;
            const id = selectedIds[0];
            onChange({
                ...screen,
                components: screen.components.map((c) => (c.id === id ? { ...c, ...patch } : c)),
            });
        },
        [selectedIds, screen, onChange],
    );

    return (
        <div className="screen-designer flex flex-col fixed inset-0 overflow-hidden isolate z-[9999]">
            {/* Header */}
            <div
                style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 12,
                    padding: "8px 16px",
                    background: "#1a1f2e",
                    borderBottom: "1px solid rgba(255,255,255,0.08)",
                    color: "#e2e8f0",
                }}
            >
                <span style={{ fontSize: 12, color: "#94a3b8", letterSpacing: 0.4 }}>v2 自适应大屏</span>
                <input
                    value={screen.name ?? ""}
                    onChange={handleNameChange}
                    placeholder="大屏名称"
                    style={{
                        flex: "0 1 320px",
                        padding: "4px 10px",
                        background: "rgba(255,255,255,0.06)",
                        border: "1px solid rgba(255,255,255,0.1)",
                        borderRadius: 4,
                        color: "#e2e8f0",
                        fontSize: 14,
                    }}
                />
                <div style={{ flex: 1 }} />
                {dirty && <span style={{ fontSize: 12, color: "#fbbf24" }}>● 未保存</span>}
                <button
                    type="button"
                    onClick={onPreview}
                    disabled={isSaving}
                    style={{
                        padding: "6px 14px",
                        background: "rgba(255,255,255,0.08)",
                        border: "1px solid rgba(255,255,255,0.15)",
                        borderRadius: 4,
                        color: "#e2e8f0",
                        cursor: "pointer",
                    }}
                >
                    预览
                </button>
                <button
                    type="button"
                    onClick={() => void onSave()}
                    disabled={isSaving}
                    style={{
                        padding: "6px 14px",
                        background: "#6366f1",
                        border: "1px solid #6366f1",
                        borderRadius: 4,
                        color: "#fff",
                        cursor: isSaving ? "not-allowed" : "pointer",
                        opacity: isSaving ? 0.6 : 1,
                    }}
                >
                    {isSaving ? "保存中…" : "保存"}
                </button>
            </div>

            {/* Body */}
            <div className="flex flex-1 min-h-0 overflow-hidden">
                <div
                    className="designer-side-rail designer-side-rail--library flex min-h-0 overflow-hidden shrink-0 border-r border-[var(--color-border)]"
                    style={{ width: "clamp(280px, 18vw, 320px)", flex: "0 0 clamp(280px, 18vw, 320px)" }}
                >
                    <ComponentLibraryPanel />
                </div>

                <div className="flex-1 flex flex-col min-w-0 min-h-0 relative">
                    <DesignerCanvasV2
                        screen={screen}
                        onChange={onChange}
                        selectedIds={selectedIds}
                        onSelect={onSelect}
                    />
                </div>

                <div
                    className="designer-side-rail designer-side-rail--inspector flex min-h-0 overflow-hidden shrink-0 border-l border-[var(--color-border)]"
                    style={{ width: "clamp(320px, 22vw, 360px)", flex: "0 0 clamp(320px, 22vw, 360px)" }}
                >
                    <PropertyPanelV2
                        screen={screen}
                        selected={selectedComponent}
                        onUpdateSelected={handleUpdateSelected}
                        onScreenChange={onChange}
                        onDeleteSelected={selectedIds.length > 0 ? handleDelete : undefined}
                    />
                </div>
            </div>
        </div>
    );
}

function buildV2Payload(screen: ScreenConfigV2): Record<string, unknown> {
    // 后端 ScreenResource.parseV2Spec 只读 v2Spec.{schemaVersion,layout,referenceViewport}
    // components 直接存 `components_json`（后端透明透传）
    return {
        name: screen.name ?? "未命名大屏",
        description: screen.description,
        theme: screen.theme,
        backgroundColor: screen.backgroundColor,
        backgroundImage: screen.backgroundImage,
        // v1 layout 字段（后端可能校验），给合理占位
        width: 1920,
        height: 1080,
        // components 按 v2 形状透传 —— 运行期 tryLoadV2 会正确还原
        components: screen.components,
        globalVariables: screen.globalVariables ?? [],
        pages: [],
        carouselConfig: screen.carouselConfig,
        v2Spec: {
            schemaVersion: 2,
            layout: screen.layout,
            referenceViewport: screen.referenceViewport,
        },
    };
}

function ScreenDesignerV2Content() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [screen, setScreen] = useState<ScreenConfigV2 | null>(null);
    const [selectedIds, setSelectedIds] = useState<string[]>([]);
    const [loading, setLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [dirty, setDirty] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!id) {
            setScreen(createEmptyScreenV2());
            setLoading(false);
            return;
        }
        let cancelled = false;
        analyticsApi
            .getScreen(id, { mode: "draft" })
            .then((raw) => {
                if (cancelled) return;
                if (raw.canEdit === false) {
                    toast.error("当前账号没有该大屏的编辑权限");
                    navigate("/bi/screens", { replace: true });
                    return;
                }
                const v2 = tryLoadV2(raw);
                if (v2) {
                    setScreen(v2);
                } else {
                    // 后端没有 v2Spec → 这条大屏其实是 v1，提示并跳回列表
                    toast.error("此大屏是 v1 固定像素大屏，v2 编辑器不支持。请使用旧编辑器或新建 v2 大屏");
                    navigate("/bi/screens", { replace: true });
                }
            })
            .catch((e) => {
                if (!cancelled) setError(String(e?.message ?? e));
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [id, navigate]);

    const handleScreenChange = useCallback((next: ScreenConfigV2) => {
        setScreen(next);
        setDirty(true);
    }, []);

    const handleSave = useCallback(async () => {
        if (!screen || !id || isSaving) return;
        setIsSaving(true);
        try {
            await analyticsApi.updateScreen(id, buildV2Payload(screen));
            toast.success("保存成功");
            setDirty(false);
        } catch (e) {
            toast.error(`保存失败：${String((e as Error)?.message ?? e)}`);
        } finally {
            setIsSaving(false);
        }
    }, [screen, id, isSaving]);

    const handlePreview = useCallback(() => {
        if (!id) return;
        if (dirty) {
            const confirmLeave = window.confirm("还有未保存的改动，预览前要保存吗？");
            if (confirmLeave) {
                void handleSave().then(() => navigate(`/bi/screens/${id}/preview`));
                return;
            }
        }
        navigate(`/bi/screens/${id}/preview`);
    }, [id, dirty, handleSave, navigate]);

    if (loading) {
        return (
            <div
                style={{
                    position: "fixed",
                    inset: 0,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    background: "#0f172a",
                    color: "#e2e8f0",
                }}
            >
                加载中…
            </div>
        );
    }
    if (error || !screen) {
        return (
            <div
                style={{
                    position: "fixed",
                    inset: 0,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    background: "#0f172a",
                    color: "#f87171",
                }}
            >
                加载失败：{error ?? "unknown"}
            </div>
        );
    }

    return (
        <V2DesignerShell
            screen={screen}
            onChange={handleScreenChange}
            selectedIds={selectedIds}
            onSelect={setSelectedIds}
            onSave={handleSave}
            onPreview={handlePreview}
            isSaving={isSaving}
            dirty={dirty}
        />
    );
}

export default function ScreenDesignerV2Page() {
    return (
        <ConfigProvider
            theme={{
                algorithm: antdTheme.darkAlgorithm,
                token: {
                    zIndexPopupBase: 10050,
                    colorBgContainer: "#2a2b36",
                    colorBgElevated: "#262730",
                    colorBorder: "rgba(255, 255, 255, 0.12)",
                    colorBorderSecondary: "rgba(255, 255, 255, 0.08)",
                    colorText: "rgba(255, 255, 255, 0.92)",
                    colorTextSecondary: "rgba(255, 255, 255, 0.60)",
                    colorTextTertiary: "rgba(255, 255, 255, 0.40)",
                    colorTextPlaceholder: "rgba(255, 255, 255, 0.35)",
                    colorFillAlter: "rgba(255, 255, 255, 0.04)",
                    colorFillSecondary: "rgba(255, 255, 255, 0.08)",
                },
            }}
        >
            <DndProvider backend={HTML5Backend}>
                <ScreenDesignerV2Content />
            </DndProvider>
        </ConfigProvider>
    );
}
