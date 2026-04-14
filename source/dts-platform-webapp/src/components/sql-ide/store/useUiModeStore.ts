import { create } from "zustand";

export type UiMode = "simple" | "advanced";

const STORAGE_KEY = "sqlide.ui-mode.v1";
const DEFAULT_MODE: UiMode = "simple";

function loadMode(): UiMode {
  if (typeof window === "undefined") return DEFAULT_MODE;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_MODE;
    const parsed = JSON.parse(raw) as { mode?: UiMode };
    return parsed.mode === "advanced" ? "advanced" : DEFAULT_MODE;
  } catch {
    return DEFAULT_MODE;
  }
}

function persistMode(mode: UiMode): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ mode }));
  } catch {
    /* ignore quota */
  }
}

interface UiModeStore {
  mode: UiMode;
  setMode(mode: UiMode): void;
  __resetForTest(): void;
}

export const useUiModeStore = create<UiModeStore>((set) => ({
  mode: loadMode(),
  setMode(mode) {
    set({ mode });
    persistMode(mode);
  },
  __resetForTest() {
    if (typeof window !== "undefined") {
      try {
        window.localStorage.removeItem(STORAGE_KEY);
      } catch {
        /* ignore */
      }
    }
    set({ mode: DEFAULT_MODE });
  },
}));
