import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import userStore from "./userStore";

type Scope = "DEPT" | "INST";

type ContextState = {
  activeScope: Scope;
  activeDept?: string;
  actions: {
    setActiveScope: (s: Scope) => void;
    setActiveDept: (d?: string) => void;
    initDefaults: () => void;
  };
};

const useContextStore = create<ContextState>()(
  persist(
    (set, get) => ({
      activeScope: "DEPT",
      activeDept: undefined,
      actions: {
        setActiveScope: (s) => {
          set({ activeScope: s });
          // Policy: switch to INST clears activeDept; switch back to DEPT restores from token
          try {
            if (s === "INST") {
              set({ activeDept: undefined });
            } else if (s === "DEPT") {
              const attrs = userStore.getState().userInfo.attributes || {};
              const dept = Array.isArray((attrs as any).dept_code)
                ? String(((attrs as any).dept_code[0] || "").toString().trim())
                : String(((attrs as any).dept_code || "")).trim();
              if (dept) set({ activeDept: dept });
            }
          } catch {
            // ignore
          }
        },
        setActiveDept: (d) => set({ activeDept: d?.trim() || undefined }),
        initDefaults: () => {
          const st = get();
          if (!st.activeDept) {
            try {
              const attrs = userStore.getState().userInfo.attributes || {};
              const dept = Array.isArray((attrs as any).dept_code)
                ? String(((attrs as any).dept_code[0] || "").toString().trim())
                : String(((attrs as any).dept_code || "")).trim();
              if (dept) set({ activeDept: dept });
            } catch {
              // ignore
            }
          }
        },
      },
    }),
    {
      name: "contextStore",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ activeScope: state.activeScope, activeDept: state.activeDept }),
    },
  ),
);

export default useContextStore;
export const useActiveScope = () => useContextStore((s) => s.activeScope);
export const useActiveDept = () => useContextStore((s) => s.activeDept);
export const useContextActions = () => useContextStore((s) => s.actions);
