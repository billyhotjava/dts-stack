import { describe, expect, it, vi } from "vitest";

// Mock the API module so apiClient (and its transitive localStorage deps) never loads
vi.mock("../../api/sqlIdeTabs", () => ({
  listTabs: vi.fn().mockResolvedValue([]),
  createTab: vi.fn().mockResolvedValue({}),
  patchTab: vi.fn().mockResolvedValue({}),
  deleteTab: vi.fn().mockResolvedValue(undefined),
  batchUpsertTabs: vi.fn().mockResolvedValue([]),
}));

import { mergeHydration } from "../useTabStore";
import type { TabDto } from "../types";

function makeLocal(overrides: Partial<import("../types").TabState>): import("../types").TabState {
  return {
    id: overrides.id ?? "l1",
    title: overrides.title ?? "L",
    sqlText: overrides.sqlText ?? "",
    engine: overrides.engine ?? "generic",
    datasourceId: null,
    schemaContext: null,
    cursor: { line: 1, column: 1 },
    selection: null,
    lastExecutionId: null,
    resultSnapshot: null,
    dirty: overrides.dirty ?? false,
    sortOrder: overrides.sortOrder ?? 0,
    updatedAt: overrides.updatedAt ?? null,
    createdLocally: overrides.createdLocally ?? false,
  };
}

function makeRemote(overrides: Partial<TabDto>): TabDto {
  return {
    id: overrides.id ?? "r1",
    title: overrides.title ?? "R",
    sqlText: overrides.sqlText ?? "",
    engine: overrides.engine ?? "generic",
    datasourceId: null,
    schemaCtx: null,
    cursorLine: null,
    cursorCol: null,
    selectionJson: null,
    lastExecutionId: null,
    sortOrder: overrides.sortOrder ?? 0,
    active: overrides.active ?? false,
    updatedAt: overrides.updatedAt ?? "2026-04-13T00:00:00Z",
  };
}

describe("mergeHydration", () => {
  it("returns remote when no local data", () => {
    const { tabs, toPush } = mergeHydration([], [makeRemote({ id: "r1" })]);
    expect(tabs).toHaveLength(1);
    expect(tabs[0].id).toBe("r1");
    expect(toPush).toHaveLength(0);
  });

  it("pushes locally-created tabs not on server", () => {
    const local = [makeLocal({ id: "l1", createdLocally: true })];
    const { toPush } = mergeHydration(local, []);
    expect(toPush).toHaveLength(1);
    expect(toPush[0].id).toBeUndefined();  // create = no id in payload
  });

  it("server wins when remote updatedAt is newer", () => {
    const local = [makeLocal({
      id: "x", sqlText: "old-local",
      updatedAt: "2026-04-13T00:00:00Z",
    })];
    const remote = [makeRemote({
      id: "x", sqlText: "new-remote",
      updatedAt: "2026-04-13T01:00:00Z",
    })];
    const { tabs } = mergeHydration(local, remote);
    expect(tabs[0].sqlText).toBe("new-remote");
  });

  it("local wins and marks dirty when local updatedAt is newer", () => {
    const local = [makeLocal({
      id: "x", sqlText: "new-local",
      updatedAt: "2026-04-13T01:00:00Z",
      dirty: true,
    })];
    const remote = [makeRemote({
      id: "x", sqlText: "old-remote",
      updatedAt: "2026-04-13T00:00:00Z",
    })];
    const { tabs, toPush } = mergeHydration(local, remote);
    expect(tabs[0].sqlText).toBe("new-local");
    expect(toPush).toHaveLength(1);
    expect(toPush[0].id).toBe("x");
  });

  it("equal updatedAt keeps local (no-op)", () => {
    const ts = "2026-04-13T00:00:00Z";
    const local = [makeLocal({ id: "x", sqlText: "same", updatedAt: ts })];
    const remote = [makeRemote({ id: "x", sqlText: "same", updatedAt: ts })];
    const { toPush } = mergeHydration(local, remote);
    expect(toPush).toHaveLength(0);
  });
});
