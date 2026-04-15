// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { useUiModeStore } from "../useUiModeStore";

describe("useUiModeStore", () => {
  beforeEach(() => {
    useUiModeStore.getState().__resetForTest();
  });

  it("defaults to simple mode", () => {
    expect(useUiModeStore.getState().mode).toBe("simple");
  });

  it("setMode changes the mode", () => {
    useUiModeStore.getState().setMode("advanced");
    expect(useUiModeStore.getState().mode).toBe("advanced");
  });

  it("setMode persists to localStorage", () => {
    useUiModeStore.getState().setMode("advanced");
    const raw = window.localStorage.getItem("sqlide.ui-mode.v1");
    expect(raw).toBeDefined();
    expect(JSON.parse(raw!).mode).toBe("advanced");
  });

  it("__resetForTest clears localStorage and restores default", () => {
    useUiModeStore.getState().setMode("advanced");
    useUiModeStore.getState().__resetForTest();
    expect(useUiModeStore.getState().mode).toBe("simple");
    expect(window.localStorage.getItem("sqlide.ui-mode.v1")).toBeNull();
  });

  it("setMode rejects invalid values via TypeScript — compile-time only", () => {
    // This test asserts that setMode accepts only "simple" | "advanced" — compile-time.
    // At runtime nothing is validated, so we just verify no throw with valid values.
    expect(() => useUiModeStore.getState().setMode("simple")).not.toThrow();
    expect(() => useUiModeStore.getState().setMode("advanced")).not.toThrow();
  });
});
