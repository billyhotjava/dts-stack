import { describe, expect, it } from "vitest";
import {
  applyColumnAction,
  type ColumnAction,
  type GridColumnState,
  emptyGridColumnState,
} from "../columnState";

describe("applyColumnAction", () => {
  it("setColumnWidth stores width by column name", () => {
    const next = applyColumnAction(emptyGridColumnState(), { type: "setWidth", name: "id", width: 80 });
    expect(next.columnWidths.id).toBe(80);
  });

  it("toggleHidden flips hidden state", () => {
    const s1 = applyColumnAction(emptyGridColumnState(), { type: "toggleHidden", name: "name" });
    expect(s1.hiddenColumns).toContain("name");
    const s2 = applyColumnAction(s1, { type: "toggleHidden", name: "name" });
    expect(s2.hiddenColumns).not.toContain("name");
  });

  it("setPin adds to pinned, setUnpin removes", () => {
    const s1 = applyColumnAction(emptyGridColumnState(), { type: "setPin", name: "id", side: "left" });
    expect(s1.pinnedColumns.left).toContain("id");
    const s2 = applyColumnAction(s1, { type: "setUnpin", name: "id" });
    expect(s2.pinnedColumns.left).not.toContain("id");
  });

  it("setSort replaces existing sort", () => {
    const s1 = applyColumnAction(emptyGridColumnState(), { type: "setSort", name: "amount", direction: "desc" });
    expect(s1.sort).toEqual({ name: "amount", direction: "desc" });
    const s2 = applyColumnAction(s1, { type: "setSort", name: "id", direction: "asc" });
    expect(s2.sort).toEqual({ name: "id", direction: "asc" });
  });

  it("clearSort removes sort", () => {
    const s1 = applyColumnAction(emptyGridColumnState(), { type: "setSort", name: "id", direction: "asc" });
    const s2 = applyColumnAction(s1, { type: "clearSort" });
    expect(s2.sort).toBeNull();
  });
});
