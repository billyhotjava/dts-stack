import { create } from "zustand";

export type ActivityId = "schema" | "history" | "saved" | "search" | "copilot";

const STORAGE_KEY = "sqlide.layout.v1";
const DEFAULT_SIDE_PANEL_WIDTH = 240;
const DEFAULT_BOTTOM_PANEL_HEIGHT = 260;

interface LayoutSnapshot {
  activeActivity: ActivityId | null;
  sidePanelWidth: number;
  bottomPanelHeight: number;
}

function loadSnapshot(): LayoutSnapshot {
  if (typeof window === "undefined") {
    return {
      activeActivity: "schema",
      sidePanelWidth: DEFAULT_SIDE_PANEL_WIDTH,
      bottomPanelHeight: DEFAULT_BOTTOM_PANEL_HEIGHT,
    };
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return {
        activeActivity: "schema",
        sidePanelWidth: DEFAULT_SIDE_PANEL_WIDTH,
        bottomPanelHeight: DEFAULT_BOTTOM_PANEL_HEIGHT,
      };
    }
    const parsed = JSON.parse(raw) as Partial<LayoutSnapshot>;
    return {
      activeActivity: parsed.activeActivity ?? "schema",
      sidePanelWidth: clampWidth(parsed.sidePanelWidth ?? DEFAULT_SIDE_PANEL_WIDTH),
      bottomPanelHeight: parsed.bottomPanelHeight ?? DEFAULT_BOTTOM_PANEL_HEIGHT,
    };
  } catch {
    return {
      activeActivity: "schema",
      sidePanelWidth: DEFAULT_SIDE_PANEL_WIDTH,
      bottomPanelHeight: DEFAULT_BOTTOM_PANEL_HEIGHT,
    };
  }
}

function clampWidth(w: number): number {
  return Math.min(500, Math.max(200, w));
}

function persist(snapshot: LayoutSnapshot): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
  } catch {
    /* ignore quota */
  }
}

interface LayoutStore extends LayoutSnapshot {
  setActivity(id: ActivityId | null): void;
  setSidePanelWidth(w: number): void;
  setBottomPanelHeight(h: number): void;
  __resetForTest(): void;
}

const initial = loadSnapshot();

export const useLayoutStore = create<LayoutStore>((set, get) => ({
  ...initial,
  setActivity(id) {
    const cur = get().activeActivity;
    const next = cur === id ? null : id; // toggle behavior
    set({ activeActivity: next });
    persist({ ...get(), activeActivity: next });
  },
  setSidePanelWidth(w) {
    const clamped = clampWidth(w);
    set({ sidePanelWidth: clamped });
    persist({ ...get(), sidePanelWidth: clamped });
  },
  setBottomPanelHeight(h) {
    set({ bottomPanelHeight: h });
    persist({ ...get(), bottomPanelHeight: h });
  },
  __resetForTest() {
    if (typeof window !== "undefined") {
      try { window.localStorage.removeItem(STORAGE_KEY); } catch { /* ignore */ }
    }
    set({
      activeActivity: "schema",
      sidePanelWidth: DEFAULT_SIDE_PANEL_WIDTH,
      bottomPanelHeight: DEFAULT_BOTTOM_PANEL_HEIGHT,
    });
  },
}));
