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
      // If local has unpushed edits, always prefer local + re-push.
      // Otherwise compare ts (local.updatedAt now always equals last-seen-server ts since
      // updateTab stopped bumping it; dirty flag is the authoritative "has local changes" signal).
      if (l.dirty) {
        tabs.push(l);
        toPush.push(stateToPayload(l, /* includeId */ true));
      } else {
        const localTs = l.updatedAt ? Date.parse(l.updatedAt) : 0;
        const remoteTs = Date.parse(r.updatedAt);
        if (remoteTs > localTs) {
          tabs.push(fromDto(r));
        } else {
          tabs.push(l);  // same or local ahead (shouldn't happen post-fix) → keep local
        }
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
    localEditVersion: 0,
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

// oldId → newId chain retained after syncDirty remaps local ids to server ids.
// External callers (e.g. async onExecute callbacks) may still hold a stale id;
// resolveId() walks the chain so their mutations land on the correct tab.
// Entries are pruned via pruneIdRemap() once the terminal live id is gone, so
// long-lived sessions don't accumulate a forever-growing map.
const idRemap = new Map<string, string>();

export function resolveId(id: string | null | undefined): string | null {
  if (!id) return null;
  let cur = id;
  const seen = new Set<string>();
  while (idRemap.has(cur)) {
    if (seen.has(cur)) break; // cycle guard (shouldn't happen)
    seen.add(cur);
    cur = idRemap.get(cur)!;
  }
  return cur;
}

/** Remove chains whose terminal server id is no longer present in the live tab list. */
function pruneIdRemap(liveIds: Set<string>): void {
  if (idRemap.size === 0) return;
  for (const oldId of Array.from(idRemap.keys())) {
    const terminal = resolveId(oldId);
    if (!terminal || !liveIds.has(terminal)) {
      idRemap.delete(oldId);
    }
  }
}

let quotaWarned = false;
function warnQuotaOnce(err: unknown) {
  if (quotaWarned) return;
  quotaWarned = true;
  if (import.meta.env?.DEV) {
    // eslint-disable-next-line no-console
    console.warn("[SqlIde] localStorage persist failed", err);
  }
  if (typeof window !== "undefined") {
    // Lazy-import antd message to avoid a hard dep cycle in SSR/tests
    import("antd")
      .then(({ message }) => {
        message.warning("本地存储已满，Tab 将在当前会话内保留但不会持久化");
      })
      .catch(() => {
        /* ignore */
      });
  }
}

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
      localEditVersion: 0,
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
    const resolved = resolveId(id) ?? id;
    const tabs = get().tabs;
    const idx = tabs.findIndex((t) => t.id === resolved);
    if (idx < 0) return;
    const removed = tabs[idx];
    const next = tabs.filter((t) => t.id !== resolved);
    const newActive =
      get().activeTabId === resolved
        ? (next[Math.min(idx, next.length - 1)]?.id ?? null)
        : get().activeTabId;
    set({ tabs: next, activeTabId: newActive });
    persistLocal(next);
    // Prune any oldId→newId mappings whose terminal target was this tab — keeps
    // the idRemap from growing unbounded across long sessions with lots of churn.
    pruneIdRemap(new Set(next.map((t) => t.id)));
    // only call server if tab was previously pushed
    if (!removed.createdLocally) {
      try {
        await deleteTab(resolved);
      } catch {
        /* ignore */
      }
    }
  },

  updateTab(id, patch) {
    // Do NOT overwrite updatedAt here — it is the server-assigned optimistic lock token
    // and must match the server's last response on the next PATCH, or the server rejects
    // with 409 "stale updatedAt". Use localEditVersion for mid-flight edit detection.
    const resolved = resolveId(id) ?? id;
    const cur = get().tabs;
    if (!cur.some((t) => t.id === resolved)) {
      if (import.meta.env?.DEV) {
        // eslint-disable-next-line no-console
        console.warn("[SqlIde] updateTab: tab not found", { id, resolved });
      }
      return;
    }
    const tabs = cur.map((t) =>
      t.id === resolved
        ? { ...t, ...patch, dirty: true, localEditVersion: t.localEditVersion + 1 }
        : t,
    );
    set({ tabs });
    persistLocal(tabs);
    scheduleDebouncedSync(get);
  },

  updateGridState(tabId, partial) {
    const resolved = resolveId(tabId) ?? tabId;
    const tabs = get().tabs.map((t) =>
      t.id === resolved
        ? { ...t, gridState: { ...t.gridState, ...partial } }
        : t,
    );
    set({ tabs });
    persistLocal(tabs);
    // intentionally NO dirty=true, NO updatedAt bump, NO scheduleDebouncedSync
    // gridState is a per-device client preference; it is not in stateToPayload
  },

  setActive(id) {
    const resolved = resolveId(id) ?? id;
    const tabs = get().tabs;
    if (tabs.some((t) => t.id === resolved)) {
      set({ activeTabId: resolved });
    } else if (tabs.length > 0) {
      // Defensive fallback: requested tab no longer exists (e.g. closed mid-await).
      // Surface this loudly in dev so the offending call site can be traced, but in
      // prod pick the first tab instead of leaving activeTabId as an orphan string.
      if (import.meta.env?.DEV) {
        // eslint-disable-next-line no-console
        console.warn("[SqlIde] setActive: unknown tab id, falling back to first", { id, resolved });
      }
      set({ activeTabId: tabs[0].id });
    } else {
      set({ activeTabId: null });
    }
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

    // Map of id → localEditVersion at the time of the snapshot.
    // Used AFTER the server response to detect "user edited during await" — any
    // edit during the await would bump localEditVersion and we must keep dirty=true.
    // NOTE: do NOT use updatedAt for this — updatedAt is the server's optimistic lock
    // token and stays constant across local edits.
    const pushedVersionMap = new Map(snapshot.map((t) => [t.id, t.localEditVersion]));

    const payload: UpsertTabPayload[] = snapshot.map((t) =>
      stateToPayload(t, !t.createdLocally),
    );

    try {
      const resp = await batchUpsertTabs(payload);
      // Build mapping from local id → server-assigned id + updatedAt (response is in request order).
      // Named serverIdMap to avoid shadowing the module-level idRemap (oldId → newId chain).
      const serverIdMap = new Map<string, { newId: string; newUpdatedAt: string }>();
      snapshot.forEach((t, i) => {
        const server = resp[i];
        if (server) {
          serverIdMap.set(t.id, { newId: server.id, newUpdatedAt: server.updatedAt });
        }
      });

      // Only clear dirty on tabs whose localEditVersion hasn't incremented since the snapshot.
      // If it has, the user edited during the await and we must keep dirty=true so the next
      // scheduled sync picks up the latest changes.
      const appliedRemaps = new Map<string, string>(); // oldId → newId
      const tabs = get().tabs.map((t) => {
        const remap = serverIdMap.get(t.id);
        const pushedVersion = pushedVersionMap.get(t.id);
        if (!remap || pushedVersion === undefined) return t; // tab wasn't in the snapshot
        if (t.localEditVersion !== pushedVersion) return t; // user edited during await → keep dirty
        if (remap.newId !== t.id) {
          appliedRemaps.set(t.id, remap.newId);
        }
        return {
          ...t,
          id: remap.newId,               // adopt server-assigned id for creates
          updatedAt: remap.newUpdatedAt,  // adopt server's authoritative updatedAt (optimistic-lock token)
          dirty: false,
          createdLocally: false,
        };
      });

      // Persist the oldId → newId mapping in the module-level idRemap so external
      // callers (async callbacks that captured the pre-remap id) can still resolve
      // to the live tab via resolveId(). Without this, a SQL submit kicked off
      // before the sync completes will try to writeback to an id that no longer
      // exists in the tabs array — producing the "tab not found" symptom.
      for (const [oldId, newId] of appliedRemaps) {
        idRemap.set(oldId, newId);
      }

      // Atomically resolve activeTabId through the (now-updated) remap chain so
      // it never lingers as an orphan after sync. Falls back to the first tab if
      // the current active id was closed mid-flight.
      const currentActive = get().activeTabId;
      const resolvedActive = resolveId(currentActive);
      const nextActive =
        resolvedActive && tabs.some((t) => t.id === resolvedActive)
          ? resolvedActive
          : (tabs[0]?.id ?? null);

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
    idRemap.clear();
    quotaWarned = false;
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
  } catch (err) {
    // Quota exceeded or serialization failure. Surface it to the user once so
    // they know the tab state won't survive a reload — silently swallowing this
    // is what let "tabs mysteriously disappeared after refresh" reports through.
    warnQuotaOnce(err);
  }
}

function loadLocal(): TabState[] {
  if (typeof window === "undefined") return [];
  let raw: string | null;
  try {
    raw = window.localStorage.getItem(LOCAL_STORAGE_KEY);
  } catch {
    return [];
  }
  if (!raw) return [];
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch (err) {
    // Top-level JSON corruption — dump the payload and return empty rather than
    // blocking IDE boot. Keep the raw string in a side-channel key so support can
    // inspect it if a user reports "all my tabs disappeared".
    if (import.meta.env?.DEV) {
      // eslint-disable-next-line no-console
      console.warn("[SqlIde] localStorage root JSON corrupt, dropping", err);
    }
    try {
      window.localStorage.setItem(`${LOCAL_STORAGE_KEY}.corrupt`, raw ?? "");
    } catch {
      /* ignore */
    }
    // Surface to the user so "my tabs vanished" isn't silent. Lazy-import
    // antd to avoid a hard SSR/test dep.
    import("antd")
      .then(({ message }) => {
        message.warning("本地 Tab 存储损坏，已保留副本 (sqlide.tabs.v1.corrupt) 以便排查。");
      })
      .catch(() => {
        /* ignore */
      });
    return [];
  }
  if (!Array.isArray(parsed)) return [];

  // Migrations: pre-T17 lacks gridState; pre-T24 lacks subqueryViewName;
  // pre-staleUpdatedAt-fix lacks localEditVersion.
  // CRITICAL: pre-staleUpdatedAt-fix entries have a client-generated updatedAt
  // (updateTab used to overwrite it with Date.now()); that token will never match
  // the server's lastModifiedDate → 409 on next PATCH. Strip updatedAt so the
  // server-side comparison short-circuits via the null guard and the next sync
  // adopts the server's authoritative token.
  const out: TabState[] = [];
  for (const entry of parsed as unknown[]) {
    try {
      const t = entry as Partial<TabState> | null;
      if (!t || typeof t !== "object") continue;
      if (typeof t.id !== "string" || !t.id) continue;
      const isLegacy = (t as { localEditVersion?: unknown }).localEditVersion === undefined;
      out.push({
        id: t.id,
        title: t.title ?? "Untitled",
        sqlText: t.sqlText ?? "",
        engine: (t.engine as TabState["engine"]) ?? "generic",
        datasourceId: t.datasourceId ?? null,
        schemaContext: t.schemaContext ?? null,
        cursor: t.cursor ?? { line: 1, column: 1 },
        selection: t.selection ?? null,
        lastExecutionId: t.lastExecutionId ?? null,
        resultSnapshot: null,
        dirty: t.dirty ?? false,
        sortOrder: t.sortOrder ?? out.length,
        updatedAt: isLegacy ? null : (t.updatedAt ?? null),
        localEditVersion: t.localEditVersion ?? 0,
        createdLocally: t.createdLocally ?? false,
        gridState: t.gridState ?? emptyGridColumnState(),
        subqueryViewName: t.subqueryViewName ?? null,
      });
    } catch (err) {
      // Single-tab corruption: skip it, don't blow away the whole list.
      if (import.meta.env?.DEV) {
        // eslint-disable-next-line no-console
        console.warn("[SqlIde] skipping corrupt tab entry", err);
      }
    }
  }
  return out;
}

// -------- debounced sync --------

function scheduleDebouncedSync(get: () => TabStore) {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    debounceTimer = null;
    void get().syncDirty();
  }, DEBOUNCE_MS);
}
