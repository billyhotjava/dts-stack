// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { expect, it, vi } from "vitest";
import { ThemeLayout } from "#/enum";
import DashboardLayout from "./index";
const state = vi.hoisted(() => ({ mobile: false, layout: "" }));
vi.mock("@/hooks", () => ({ down: () => "md", useMediaQuery: () => state.mobile }));
vi.mock("@/store/settingStore", () => ({ useSettings: () => ({ themeLayout: state.layout }) }));
vi.mock("@/components/brand", () => ({ default: () => <span>brand</span> }));
vi.mock("./header", () => ({ default: ({ leftSlot }: { leftSlot?: React.ReactNode }) => <header>{leftSlot}</header> }));
vi.mock("./main", () => ({ default: () => <main><input aria-label="模型草稿" defaultValue="" /></main> }));
vi.mock("./nav", () => ({ useFilteredNavData: () => [], NavHorizontalLayout: () => <nav>horizontal</nav>, NavMobileLayout: () => <nav>mobile</nav>, NavVerticalLayout: () => <nav>vertical</nav> }));
it("preserves the route and unsaved input across mobile and desktop layouts", () => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    state.mobile = false; state.layout = ThemeLayout.Vertical;
    const container = document.createElement("div"); document.body.append(container);
    const root = createRoot(container);
    try {
        act(() => root.render(<DashboardLayout />));
        const input = container.querySelector("input")!;
        input.value = "未保存的贴源字段";
        for (const mobile of [true, false]) {
            state.mobile = mobile;
            act(() => root.render(<DashboardLayout />));
            expect(container.querySelector("input")).toBe(input);
            expect(input.value).toBe("未保存的贴源字段");
            expect(container.querySelectorAll("main")).toHaveLength(1);
            expect(container.querySelector("nav")?.textContent).toBe(mobile ? "mobile" : "vertical");
        }
        state.layout = ThemeLayout.Horizontal;
        act(() => root.render(<DashboardLayout />));
        expect(container.querySelector("input")).toBe(input);
        expect(container.querySelector("nav")?.textContent).toBe("horizontal");
    } finally { act(() => root.unmount()); container.remove(); }
});
