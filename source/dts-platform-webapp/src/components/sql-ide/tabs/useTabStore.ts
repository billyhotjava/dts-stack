import { create } from "zustand";
import { batchUpsertTabs, deleteTab, listTabs, type UpsertTabPayload } from "../api/sqlIdeTabs";
import { emptyGridColumnState, type GridColumnState } from "../result/columnState";
import type { TabDto, TabState } from "./types";

const MAX_TABS = 30;
const LOCAL_STORAGE_KEY = "sqlide.tabs.v1";
export const DEBOUNCE_MS = 2000;

// -------- nanoid shim --------

function nanoid(): string {
  // Prefer native randomUUID when available (browser + Node 19+)
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  // Fallback for older environments
  return "t-" + Math.random().toString(36).slice(2) + Date.now().toString(36);
}

// -------- pure helpers (exported for tests) --------

export interface HydrationResult {
  tabs: TabState[];
  toPush: UpsertTabPayload[];
}

export function mergeHydration(local: TabState[], remote: TabDto[]): HydrationResult {
  const byId: Map<string, { local?: TabState; remote?: TabDto }> = new Map();
  for (const t of local) {
    byId.set(t.id, { ...(byId.get(t.id) ?? {}), local: t });
  }
  for (const r of remote) {
    byId.set(r.id, { ...(byId.get(r.id) ?? {}), remote: r });
  }

  const tabs: TabState[] = [];
  const toPush: UpsertTabPayload[] = [];

  for (const [, { local: l, remote: r }] of byId) {
    if (!l && r) {
      tabs.push(fromDto(r));
      continue;
    }
    if (l && !r) {
      if (l.createdLocally) {
        // push as create (drop id from payload)
        toPush.push(stateToPayload(l, /* includeId */ false));
      }
      tabs.push(l);
      continue;
    }
    if (l && r) {
      const localTs = l.updatedAt ? Date.parse(l.updatedAt) : 0;
      const remoteTs = Date.parse(r.updatedAt);
      if (remoteTs > localTs) {
        tabs.push(fromDto(r));
      } else if (localTs > remoteTs) {
        tabs.push(l);
        toPush.push(stateToPayload(l, /* includeId */ true));
      } else {
        tabs.push(l);  // equal → no-op
      }
    }
  }

  tabs.sort((a, b) => a.sortOrder - b.sortOrder);
  return { tabs, toPush };
}

function fromDto(r: TabDto): TabState {
  return {
    id: r.id,
    title: r.title ?? "Untitled",
    sqlText: r.sqlText ?? "",
    engine: (r.engine as TabState["engine"]) ?? "generic",
    datasourceId: r.datasourceId,
    schemaContext: r.schemaCtx,
    cursor: { line: r.cursorLine ?? 1, column: r.cursorCol ?? 1 },
    selection: r.selectionJson ? (JSON.parse(r.selectionJson) as TabState["selection"]) : null,
    lastExecutionId: r.lastExecutionId,
    resultSnapshot: null,
    dirty: false,
    sortOrder: r.sortOrder,
    updatedAt: r.updatedAt,
    createdLocally: false,
    gridState: emptyGridColumnState(),
    subqueryViewName: null,
  };
}

function stateToPayload(t: TabState, includeId: boolean): UpsertTabPayload {
  return {
    id: includeId ? t.id : undefined,
    title: t.title,
    sqlText: t.sqlText,
    engine: t.engine,
    datasourceId: t.datasourceId,
    schemaCtx: t.schemaContext,
    cursorLine: t.cursor.line,
    cursorCol: t.cursor.column,
    selectionJson: t.selection ? JSON.stringify(t.selection) : null,
    lastExecutionId: t.lastExecutionId,
    sortOrder: t.sortOrder,
    active: false,
    updatedAt: t.updatedAt ?? undefined,
  };
}

// -------- store --------

interface TabStore {
  tabs: TabState[];
  activeTabId: string | null;
  hydrated: boolean;

  openTab(initial?: Partial<TabState>): string;
  closeTab(id: string): Promise<void>;
  updateTab(id: string, patch: Partial<TabState>): void;
  updateGridState(tabId: string, partial: Partial<GridColumnState>): void;
  setActive(id: string): void;
  reorder(from: number, to: number): void;

  hydrate(): Promise<void>;
  syncDirty(): Promise<void>;

  // test-only
  __resetForTest(): void;
}

let debounceTimer: ReturnType<typeof setTimeout> | null = null;
let nextTitleCounter = 1;
let retryCount = 0;
let retryTimer: ReturnType<typeof setTimeout> | null = null;
const MAX_RETRIES = 3;

function scheduleRetry(get: () => TabStore) {
  if (retryTimer) clearTimeout(retryTimer);
  if (retryCount >= MAX_RETRIES) {
    retryCount = 0; // reset for next round
    return;
  }
  retryCount++;
  const delay = Math.min(30_000, DEBOUNCE_MS * Math.pow(2, retryCount));
  retryTimer = setTimeout(() => {
    retryTimer = null;
    void get().syncDirty();
  }, delay);
}

export const useTabStore = create<TabStore>((set, get) => ({
  tabs: [],
  activeTabId: null,
  hydrated: false,

  openTab(initial) {
    const cur = get().tabs;
    if (cur.length >= MAX_TABS) {
      throw new Error("TOO_MANY_TABS: maximum 30 tabs per user");
    }
    const id = initial?.id ?? nanoid();
    const title = initial?.title ?? `Query ${nextTitleCounter++}`;
    const tab: TabState = {
      id,
      title,
      sqlText: "",
      engine: "generic",
      datasourceId: null,
      schemaContext: null,
      cursor: { line: 1, column: 1 },
      selection: null,
      lastExecutionId: null,
      resultSnapshot: null,
      dirty: true,
      sortOrder: cur.length,
      updatedAt: null,
      createdLocally: true,
      gridState: emptyGridColumnState(),
      subqueryViewName: null,
      ...initial,
    };
    set({ tabs: [...cur, tab], activeTabId: id });
    persistLocal(get().tabs);
    scheduleDebouncedSync(get);
    return id;
  },

  async closeTab(id) {
    const tabs = get().tabs;
    const idx = tabs.findIndex((t) => t.id === id);
    if (idx < 0) return;
    const removed = tabs[idx];
    const next = tabs.filter((t) => t.id !== id);
    const newActive =
      get().activeTabId === id
        ? (next[Math.min(idx, next.length - 1)]?.id ?? null)
        : get().activeTabId;
    set({ tabs: next, activeTabId: newActive });
    persistLocal(next);
    // only call server if tab was previously pushed
    if (!removed.createdLocally) {
      try {
        await deleteTab(id);
      } catch {
        /* ignore */
      }
    }
  },

  updateTab(id, patch) {
    const now = new Date().toISOString();
    const tabs = get().tabs.map((t) =>
      t.id === id ? { ...t, ...patch, dirty: true, updatedAt: now } : t,
    );
    set({ tabs });
    persistLocal(tabs);
    scheduleDebouncedSync(get);
  },

  updateGridState(tabId, partial) {
    const tabs = get().tabs.map((t) =>
      t.id === tabId
        ? { ...t, gridState: { ...t.gridState, ...partial } }
        : t,
    );
    set({ tabs });
    persistLocal(tabs);
    // intentionally NO dirty=true, NO updatedAt bump, NO scheduleDebouncedSync
    // gridState is a per-device client preference; it is not in stateToPayload
  },

  setActive(id) {
    if (get().tabs.some((t) => t.id === id)) set({ activeTabId: id });
  },

  reorder(from, to) {
    if (from === to) return;
    const tabs = [...get().tabs];
    const [moved] = tabs.splice(from, 1);
    tabs.splice(to, 0, moved);
    const renumbered = tabs.map((t, i) => ({ ...t, sortOrder: i, dirty: true }));
    set({ tabs: renumbered });
    persistLocal(renumbered);
    scheduleDebouncedSync(get);
  },

  async hydrate() {
    const local = loadLocal();
    let remote: TabDto[] = [];
    try {
      remote = await listTabs();
    } catch {
      /* offline — carry on with local */
    }
    const merge = mergeHydration(local, remote);

    if (merge.toPush.length > 0) {
      try {
        await batchUpsertTabs(merge.toPush);
        // Re-fetch to get server-assigned ids for locally-created tabs
        const fresh = await listTabs();
        // Re-merge: clear createdLocally/dirty on the local snapshot so server state wins
        const reconciled = mergeHydration(
          merge.tabs.map((t) => ({ ...t, createdLocally: false, dirty: false })),
          fresh,
        );
        set({ tabs: reconciled.tabs, hydrated: true, activeTabId: reconciled.tabs[0]?.id ?? null });
        nextTitleCounter =
          Math.max(
            1,
            ...reconciled.tabs.map((t) => parseInt(t.title.match(/^Query (\d+)$/)?.[1] ?? "0", 10)),
          ) + 1;
        persistLocal(reconciled.tabs);
        return;
      } catch {
        /* fall through — use pre-push merge state */
      }
    }

    set({ tabs: merge.tabs, hydrated: true, activeTabId: merge.tabs[0]?.id ?? null });
    nextTitleCounter =
      Math.max(
        1,
        ...merge.tabs.map((t) => parseInt(t.title.match(/^Query (\d+)$/)?.[1] ?? "0", 10)),
      ) + 1;
    persistLocal(merge.tabs);
  },

  async syncDirty() {
    const snapshot = get().tabs.filter((t) => t.dirty);
    if (snapshot.length === 0) return;

    // Map of id → updatedAt at the time of the snapshot
    const pushedAtMap = new Map(snapshot.map((t) => [t.id, t.updatedAt]));

    const payload: UpsertTabPayload[] = snapshot.map((t) =>
      stateToPayload(t, !t.createdLocally),
    );

    try {
      const resp = await batchUpsertTabs(payload);
      // Build mapping from local id → server-assigned id + updatedAt (response is in request order)
      const idRemap = new Map<string, { newId: string; newUpdatedAt: string }>();
      snapshot.forEach((t, i) => {
        const server = resp[i];
        if (server) {
          idRemap.set(t.id, { newId: server.id, newUpdatedAt: server.updatedAt });
        }
      });

      // Only clear dirty on tabs whose updatedAt hasn't changed since the snapshot
      // AND track id remaps actually applied (skipped if user edited mid-flight)
      const appliedRemaps = new Map<string, string>(); // oldId → newId
      const tabs = get().tabs.map((t) => {
        const remap = idRemap.get(t.id);
        const pushedAt = pushedAtMap.get(t.id);
        if (!remap || !pushedAt) return t; // tab wasn't in the snapshot
        if (t.updatedAt !== pushedAt) return t; // user edited during await → keep dirty
        if (remap.newId !== t.id) {
          appliedRemaps.set(t.id, remap.newId);
        }
        return {
          ...t,
          id: remap.newId,               // adopt server-assigned id for creates
          updatedAt: remap.newUpdatedAt,  // adopt server's authoritative updatedAt
          dirty: false,
          createdLocally: false,
        };
      });

      // Remap activeTabId if it pointed at a tab whose id just changed.
      // Without this the UI renders "正在恢复 Tab" because activeTabId is stale.
      const currentActive = get().activeTabId;
      const nextActive = currentActive && appliedRemaps.has(currentActive)
        ? appliedRemaps.get(currentActive)!
        : currentActive;

      // Reset retry state on success
      retryCount = 0;
      if (retryTimer) {
        clearTimeout(retryTimer);
        retryTimer = null;
      }

      set({ tabs, activeTabId: nextActive });
      persistLocal(tabs);
    } catch (err) {
      // Retry with exponential backoff (up to MAX_RETRIES, then give up until next updateTab)
      scheduleRetry(get);
    }
  },

  __resetForTest() {
    nextTitleCounter = 1;
    if (debounceTimer) {
      clearTimeout(debounceTimer);
      debounceTimer = null;
    }
    retryCount = 0;
    if (retryTimer) {
      clearTimeout(retryTimer);
      retryTimer = null;
    }
    set({ tabs: [], activeTabId: null, hydrated: false });
    if (typeof window !== "undefined") {
      window.localStorage?.removeItem(LOCAL_STORAGE_KEY);
    }
  },
}));

// -------- local storage persistence --------

function persistLocal(tabs: TabState[]) {
  if (typeof window === "undefined") return;
  try {
    const slim = tabs.map((t) => ({ ...t, resultSnapshot: null }));
    window.localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(slim));
  } catch {
    /* quota or serialization — ignore */
  }
}

function loadLocal(): TabState[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(LOCAL_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as TabState[];
    // Migration: pre-T17 tabs lack gridState; pre-T24 tabs lack subqueryViewName
    return parsed.map((t) => ({
      ...t,
      gridState: t.gridState ?? emptyGridColumnState(),
      subqueryViewName: t.subqueryViewName ?? null,
    }));
  } catch {
    return [];
  }
}

// -------- debounced sync --------

function scheduleDebouncedSync(get: () => TabStore) {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    debounceTimer = null;
    void get().syncDirty();
  }, DEBOUNCE_MS);
}
