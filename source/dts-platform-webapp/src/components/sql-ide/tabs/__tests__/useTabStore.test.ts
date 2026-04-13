import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";

// Mock the API module so apiClient (and its transitive localStorage deps) never loads
vi.mock("../../api/sqlIdeTabs", () => ({
  listTabs: vi.fn().mockResolvedValue([]),
  createTab: vi.fn().mockResolvedValue({}),
  patchTab: vi.fn().mockResolvedValue({}),
  deleteTab: vi.fn().mockResolvedValue(undefined),
  batchUpsertTabs: vi.fn().mockResolvedValue([]),
}));

import * as api from "../../api/sqlIdeTabs";
import { useTabStore } from "../useTabStore";

describe("useTabStore syncDirty", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useTabStore.getState().__resetForTest();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("sends dirty tabs to server after debounce", async () => {
    const mockBatch = vi.mocked(api.batchUpsertTabs);
    mockBatch.mockResolvedValue([
      {
        id: "server-id-1",
        title: "Query 1",
        sqlText: "SELECT 1",
        engine: "generic",
        datasourceId: null,
        schemaCtx: null,
        cursorLine: null,
        cursorCol: null,
        selectionJson: null,
        lastExecutionId: null,
        sortOrder: 0,
        active: false,
        updatedAt: "2026-04-13T10:00:00.000Z",
      },
    ]);

    useTabStore.getState().openTab();
    const localId = useTabStore.getState().tabs[0].id;
    useTabStore.getState().updateTab(localId, { sqlText: "SELECT 1" });

    await vi.advanceTimersByTimeAsync(2100);
    await vi.waitFor(() => expect(mockBatch).toHaveBeenCalled());

    // Tab should now have server-assigned id and dirty=false
    const tabs = useTabStore.getState().tabs;
    expect(tabs[0].id).toBe("server-id-1");
    expect(tabs[0].dirty).toBe(false);
    expect(tabs[0].createdLocally).toBe(false);
  });

  it("keeps dirty=true if user edits during in-flight sync", async () => {
    const mockBatch = vi.mocked(api.batchUpsertTabs);
    let resolve: (v: any) => void = () => {};
    mockBatch.mockImplementation(() => new Promise((r) => { resolve = r; }));

    useTabStore.getState().openTab();
    const localId = useTabStore.getState().tabs[0].id;
    useTabStore.getState().updateTab(localId, { sqlText: "SELECT 1" });

    // Fire debounce, sync in-flight
    await vi.advanceTimersByTimeAsync(2100);

    // User types while sync is pending
    useTabStore.getState().updateTab(localId, { sqlText: "SELECT 2" });

    // Now resolve the sync with a stale snapshot's server response
    resolve([
      {
        id: "server-id-1",
        title: "Query 1",
        sqlText: "SELECT 1", // old value
        engine: "generic",
        datasourceId: null,
        schemaCtx: null,
        cursorLine: null,
        cursorCol: null,
        selectionJson: null,
        lastExecutionId: null,
        sortOrder: 0,
        active: false,
        updatedAt: "2026-04-13T10:00:00.000Z",
      },
    ]);

    await vi.waitFor(() => {
      const tab = useTabStore.getState().tabs[0];
      // Tab's updatedAt was changed by the second updateTab → dirty must remain true
      expect(tab.dirty).toBe(true);
      expect(tab.sqlText).toBe("SELECT 2");
    });
  });

  it("schedules retry on syncDirty failure", async () => {
    const mockBatch = vi.mocked(api.batchUpsertTabs);
    mockBatch.mockRejectedValue(new Error("network error"));

    useTabStore.getState().openTab();
    useTabStore.getState().updateTab(useTabStore.getState().tabs[0].id, { sqlText: "SELECT 1" });

    await vi.advanceTimersByTimeAsync(2100);
    await vi.waitFor(() => expect(mockBatch).toHaveBeenCalledTimes(1));

    // Now a retry should be scheduled with backoff (2^1 * 2000 = 4s)
    await vi.advanceTimersByTimeAsync(5000);
    await vi.waitFor(() => expect(mockBatch).toHaveBeenCalledTimes(2));
  });
});

describe("useTabStore basic operations", () => {
  beforeEach(() => {
    useTabStore.getState().__resetForTest();
  });

  it("starts empty", () => {
    const s = useTabStore.getState();
    expect(s.tabs).toHaveLength(0);
    expect(s.activeTabId).toBeNull();
  });

  it("openTab creates a tab with sensible defaults", () => {
    const id = useTabStore.getState().openTab();
    const s = useTabStore.getState();
    expect(s.tabs).toHaveLength(1);
    expect(s.tabs[0].id).toBe(id);
    expect(s.tabs[0].title).toBe("Query 1");
    expect(s.tabs[0].sqlText).toBe("");
    expect(s.tabs[0].engine).toBe("generic");
    expect(s.tabs[0].dirty).toBe(true);
    expect(s.tabs[0].createdLocally).toBe(true);
    expect(s.activeTabId).toBe(id);
  });

  it("openTab increments title numbering", () => {
    useTabStore.getState().openTab();
    useTabStore.getState().openTab();
    useTabStore.getState().openTab();
    const titles = useTabStore.getState().tabs.map((t) => t.title);
    expect(titles).toEqual(["Query 1", "Query 2", "Query 3"]);
  });

  it("updateTab marks dirty and updates fields", () => {
    const id = useTabStore.getState().openTab();
    useTabStore.getState().updateTab(id, { sqlText: "SELECT 1" });
    const tab = useTabStore.getState().tabs.find((t) => t.id === id);
    expect(tab?.sqlText).toBe("SELECT 1");
    expect(tab?.dirty).toBe(true);
  });

  it("closeTab removes the tab and re-selects another active", () => {
    const a = useTabStore.getState().openTab();
    const b = useTabStore.getState().openTab();
    useTabStore.getState().setActive(a);
    useTabStore.getState().closeTab(a);
    const s = useTabStore.getState();
    expect(s.tabs).toHaveLength(1);
    expect(s.activeTabId).toBe(b);
  });

  it("closeTab with last tab results in null activeTabId", () => {
    const id = useTabStore.getState().openTab();
    useTabStore.getState().closeTab(id);
    expect(useTabStore.getState().activeTabId).toBeNull();
  });

  it("openTab beyond limit throws a descriptive error", () => {
    const { openTab } = useTabStore.getState();
    for (let i = 0; i < 30; i++) openTab();
    expect(() => openTab()).toThrowError(/TOO_MANY_TABS/i);
  });

  it("reorder swaps positions and updates sortOrder", () => {
    const a = useTabStore.getState().openTab();
    const b = useTabStore.getState().openTab();
    const c = useTabStore.getState().openTab();
    useTabStore.getState().reorder(0, 2);  // move a to end
    const ids = useTabStore.getState().tabs.map((t) => t.id);
    expect(ids).toEqual([b, c, a]);
    const sortOrders = useTabStore.getState().tabs.map((t) => t.sortOrder);
    expect(sortOrders).toEqual([0, 1, 2]);
  });
});
