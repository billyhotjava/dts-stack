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
      // Run one-time per browser session to avoid overriding explicit user toggles later
      try {
        const inited = sessionStorage.getItem("dts.ctx.init");
        if (inited === "1") {
          // Already initialized this session
        } else {
          sessionStorage.setItem("dts.ctx.init", "1");
        }
      } catch {}

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
      // Aggressive default: if user has INST_* role(s), default scope to INST; otherwise,
      // if a department is known, default to DEPT.
      try {
        const st2 = get();
        const inited = ((): boolean => { try { return sessionStorage.getItem("dts.ctx.initedOnce") === "1"; } catch { return false; } })();
        if (!inited) {
          const roles: string[] = (userStore.getState().userInfo.roles as any) || [];
          const hasInst = Array.isArray(roles) && roles.some((r) => String(r || "").toUpperCase().includes("INST_"));
          if (hasInst) {
            set({ activeScope: "INST" });
          } else if (st2.activeDept) {
            set({ activeScope: "DEPT" });
          }
          try { sessionStorage.setItem("dts.ctx.initedOnce", "1"); } catch {}
        }
      } catch {}
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
