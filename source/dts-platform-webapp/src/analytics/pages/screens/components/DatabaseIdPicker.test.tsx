// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { expect, it, vi } from "vitest";
import { analyticsApi } from "../../../api/analyticsApi";
import { DatabaseIdPicker, invalidateDatabaseCache } from "./DatabaseIdPicker";

vi.mock("../../../api/analyticsApi", () => ({ analyticsApi: {
    listPlatformSources: vi.fn(), ensureFromPlatform: vi.fn(),
} }));
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

it("distinguishes request failure from empty sources and retries without reloading the draft", async () => {
    invalidateDatabaseCache();
    vi.mocked(analyticsApi.listPlatformSources)
        .mockRejectedValueOnce(new Error("HTTP 500"))
        .mockResolvedValueOnce([])
        .mockResolvedValueOnce([{ platformId: "source-1", name: "业务库", analyticsDbId: 7 }]);
    const container = document.createElement("div");
    const root = createRoot(container);
    const onChange = vi.fn();
    try {
        await act(async () => root.render(<DatabaseIdPicker value={0} onChange={onChange} />));
        expect(container.textContent).toContain("数据源加载失败，请重试");
        expect(container.textContent).not.toContain("无可用数据源");
        await act(async () => container.querySelector("button")!.click());
        expect(container.textContent).toContain("无可用数据源");
        await act(async () => container.querySelector("button")!.click());
        expect(container.textContent).toContain("业务库");
        expect(container.querySelector("button")).toBeNull();
        await act(async () => {
            const select = container.querySelector("select")!;
            select.value = "7";
            select.dispatchEvent(new Event("change", { bubbles: true }));
        });
        expect(onChange).toHaveBeenCalledWith(7);
    } finally {
        await act(async () => root.unmount());
        invalidateDatabaseCache();
    }
});
