// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { beforeEach, afterEach, it, expect, vi } from "vitest";
const api = vi.hoisted(() => ({ list: vi.fn(), retry: vi.fn() }));
vi.mock("@/api/modelIngestionTargetApi", () => ({ listModelRegistrationTasks: api.list, retryModelRegistrationTask: api.retry }));
vi.mock("@/components/table", () => ({ CompactTable: ({ columns, dataSource }: any) => <div>{dataSource.map((row: any) => <div key={row.id}>{columns.map((c: any) => <span key={c.key || c.dataIndex}>{c.render ? c.render(row[c.dataIndex], row) : row[c.dataIndex]}</span>)}</div>)}</div> }));
import { ModelRegistrationInbox } from "./ModelRegistrationInbox";
let root: Root, container: HTMLDivElement;
beforeEach(() => { (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true; container=document.createElement("div"); document.body.append(container); root=createRoot(container); });
afterEach(() => { act(() => root.unmount()); container.remove(); vi.resetAllMocks(); });
it("discovers and retries a failed output without an asset ID or model URL", async () => {
    const task={id:"task", candidateId:"candidate", buildVersion:6, environment:"prod", models:["预算 · r3"], state:"FAILED", attempts:5, errorMessage:"目录不可用", canRetry:true};
    api.list.mockResolvedValue({ items:[task], nextOffset:null }); api.retry.mockResolvedValue(undefined);
    await act(async () => root.render(<ModelRegistrationInbox />));
    expect(api.list).toHaveBeenCalledWith(0);
    expect(container.textContent).toContain("预算 · r3");
    const button=Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "重试登记");
    await act(async () => button?.click());
    expect(api.retry).toHaveBeenCalledWith(task);
});
it("keeps a read failure explicit", async () => {
    api.list.mockRejectedValue(new Error("登记服务暂不可用"));
    await act(async () => root.render(<ModelRegistrationInbox />));
    expect(container.textContent).toContain("登记服务暂不可用");
});
