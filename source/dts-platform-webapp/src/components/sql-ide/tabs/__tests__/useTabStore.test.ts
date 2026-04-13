import { beforeEach, describe, expect, it, vi } from "vitest";

// Mock the API module so apiClient (and its transitive localStorage deps) never loads
vi.mock("../../api/sqlIdeTabs", () => ({
  listTabs: vi.fn().mockResolvedValue([]),
  createTab: vi.fn().mockResolvedValue({}),
  patchTab: vi.fn().mockResolvedValue({}),
  deleteTab: vi.fn().mockResolvedValue(undefined),
  batchUpsertTabs: vi.fn().mockResolvedValue([]),
}));

import { useTabStore } from "../useTabStore";

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
