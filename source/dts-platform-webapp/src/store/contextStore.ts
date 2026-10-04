import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import userStore from "./userStore";

type ContextState = {
  activeDept?: string;
  actions: {
    setActiveDept: (dept?: string) => void;
    initDefaults: () => void;
  };
};

const useContextStore = create<ContextState>()(
  persist(
    (set, get) => ({
      activeDept: undefined,
      actions: {
        setActiveDept: (dept) => set({ activeDept: dept?.trim() || undefined }),
        initDefaults: () => {
          try {
            const initialized = sessionStorage.getItem("dts.ctx.init");
            if (initialized !== "1") {
              sessionStorage.setItem("dts.ctx.init", "1");
            }
          } catch {
            // ignore storage errors
          }

          const state = get();
          if (!state.activeDept) {
            try {
              const attrs = userStore.getState().userInfo.attributes || {};
              const dept = Array.isArray((attrs as any).dept_code)
                ? String(((attrs as any).dept_code[0] || "").toString().trim())
                : String(((attrs as any).dept_code || "")).trim();
              if (dept) set({ activeDept: dept });
            } catch {
              // ignore attribute parsing failures
            }
          }
        },
      },
    }),
    {
      name: "contextStore",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ activeDept: state.activeDept }),
    },
  ),
);

export default useContextStore;
export const useActiveDept = () => useContextStore((s) => s.activeDept);
export const useContextActions = () => useContextStore((s) => s.actions);
