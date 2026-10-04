import { PlayCircleOutlined, SaveOutlined, StopOutlined } from "@ant-design/icons";
import { Button, message } from "antd";
import { type FC, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useShallow } from "zustand/react/shallow";
import { useQuery } from "@tanstack/react-query";
import { ShortcutsHelp } from "./ShortcutsHelp";
import { ModeSwitcher } from "./ModeSwitcher";
import { CopilotSlot } from "./copilot/CopilotSlot";
import { useUiModeStore } from "./store/useUiModeStore";
import { SqlEditor, type SqlEditorHandle } from "./editor/SqlEditor";
import { formatSql } from "./editor/formatter";
import { findStatementAt } from "./editor/statementSplitter";
import { createSqlIdeCatalogSource, normalizeCatalogEngine } from "./editor/completion/sqlIdeCatalogSource";
import { NOOP_CATALOG } from "./editor/completion/catalogProvider";
import { ActivityBar } from "./layout/ActivityBar";
import { BottomPanel } from "./layout/BottomPanel";
import { SidePanel } from "./layout/SidePanel";
import { useLayoutStore } from "./layout/useLayoutStore";
import { HistoryPanel } from "./history/HistoryPanel";
import { SchemaTree } from "./schema/SchemaTree";
import { TabBar } from "./tabs/TabBar";
import { resolveId, useTabStore } from "./tabs/useTabStore";
import { BottomTabs } from "./result/BottomTabs";
import { ExportMenu } from "./result/ExportMenu";
import { SubQueryButton } from "./result/SubQueryButton";
import { SavedPanel } from "./saved/SavedPanel";
import { SaveQueryDialog } from "./saved/SaveQueryDialog";
import { listSavedQueries } from "./api/sqlIdeSaved";
import { getExecutionMeta } from "./api/sqlIdeExecution";
import { useSqlExecution } from "./hooks/useSqlExecution";
import { useDatasourcesQuery } from "./schema/useSchemaTreeData";

export const SqlIde: FC = () => {
  const activeActivity = useLayoutStore((s) => s.activeActivity);
  const { tabs, activeTabId, hydrated } = useTabStore(
    useShallow((s) => ({ tabs: s.tabs, activeTabId: s.activeTabId, hydrated: s.hydrated })),
  );
  const hydrate = useTabStore((s) => s.hydrate);
  const openTab = useTabStore((s) => s.openTab);
  const updateTab = useTabStore((s) => s.updateTab);
  const updateGridState = useTabStore((s) => s.updateGridState);
  const editorHandleRef = useRef<SqlEditorHandle>(null);
  const [helpOpen, setHelpOpen] = useState(false);
  const [saveDialogOpen, setSaveDialogOpen] = useState(false);
  const [pendingSql, setPendingSql] = useState("");

  // Resizable editor area. Default = 20 lines @ ~19px Monaco line-height = 380px.
  // Height persists per-browser via localStorage so user drags survive reload.
  const EDITOR_HEIGHT_KEY = "sqlide.editor.height.v1";
  const EDITOR_MIN_HEIGHT = 120;
  const EDITOR_MAX_HEIGHT = 2400;
  const EDITOR_DEFAULT_HEIGHT = 380;
  const [editorHeight, setEditorHeight] = useState<number>(() => {
    if (typeof window === "undefined") return EDITOR_DEFAULT_HEIGHT;
    const saved = Number(window.localStorage.getItem(EDITOR_HEIGHT_KEY));
    return Number.isFinite(saved) && saved >= EDITOR_MIN_HEIGHT ? saved : EDITOR_DEFAULT_HEIGHT;
  });
  const dragStartRef = useRef<{ y: number; h: number } | null>(null);
  const handleResizeStart = useCallback((e: React.MouseEvent) => {
    dragStartRef.current = { y: e.clientY, h: editorHeight };
    document.body.style.cursor = "row-resize";
    document.body.style.userSelect = "none";
  }, [editorHeight]);
  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      const start = dragStartRef.current;
      if (!start) return;
      const next = Math.min(EDITOR_MAX_HEIGHT, Math.max(EDITOR_MIN_HEIGHT, start.h + (e.clientY - start.y)));
      setEditorHeight(next);
    };
    const onUp = () => {
      if (!dragStartRef.current) return;
      dragStartRef.current = null;
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      // persist on release (avoids spamming localStorage during the drag)
      try {
        window.localStorage.setItem(EDITOR_HEIGHT_KEY, String(editorHeight));
      } catch {
        /* quota — ignore */
      }
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
    return () => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
  }, [editorHeight]);

  const { data: savedList } = useQuery({
    queryKey: ["sqlide", "saved"],
    queryFn: listSavedQueries,
    staleTime: 60_000,
  });

  const existingFolders = useMemo(() => {
    if (!savedList) return [];
    const set = new Set<string>();
    for (const item of savedList) {
      if (item.folder?.trim()) set.add(item.folder.trim());
    }
    return [...set].sort();
  }, [savedList]);

  // Hydrate on mount
  useEffect(() => {
    if (!hydrated) void hydrate();
  }, [hydrated, hydrate]);

  // Ensure at least one tab exists after hydrate
  useEffect(() => {
    if (hydrated && tabs.length === 0) openTab();
  }, [hydrated, tabs.length, openTab]);

  const activeTab = tabs.find((t) => t.id === activeTabId) ?? null;

  // Validate the active tab's persisted lastExecutionId once. If the result_set has
  // been cleaned up between sessions, the meta endpoint 404s with "result set not
  // available". Clear it so the bottom panel falls back to the empty placeholder
  // instead of repeatedly hitting a doomed endpoint on every tab switch / re-render.
  const validatedExecutionsRef = useRef<Set<string>>(new Set());
  useEffect(() => {
    const tabId = activeTab?.id;
    const execId = activeTab?.lastExecutionId;
    if (!tabId || !execId) return;
    if (validatedExecutionsRef.current.has(execId)) return;
    validatedExecutionsRef.current.add(execId);
    let aborted = false;
    void getExecutionMeta(execId)
      .catch((err: any) => {
        if (aborted) return;
        if (err?.response?.status === 404) {
          updateTab(tabId, { lastExecutionId: null });
        }
      });
    return () => {
      aborted = true;
    };
  }, [activeTab?.id, activeTab?.lastExecutionId, updateTab]);

  const uiMode = useUiModeStore((s) => s.mode);

  const sqlExec = useSqlExecution();
  const { data: datasources } = useDatasourcesQuery();

  const activeDatasource = useMemo(
    () => datasources?.find((datasource) => datasource.id === activeTab?.datasourceId) ?? null,
    [activeTab?.datasourceId, datasources],
  );

  const catalogSource = useMemo(
    () =>
      activeTab?.datasourceId
        ? createSqlIdeCatalogSource(activeTab.datasourceId)
        : NOOP_CATALOG,
    [activeTab?.datasourceId],
  );

  // Read activeTabId at call time, not from closure — prevents Monaco onChange
  // events triggered mid-switch from writing back into the previously-active tab.
  const handleSqlChange = useCallback(
    (next: string) => {
      const id = useTabStore.getState().activeTabId;
      if (id) updateTab(id, { sqlText: next });
    },
    [updateTab],
  );

  // Insert text at the current cursor position and immediately sync the new editor
  // value into the tab store. Monaco's model-change event will also fire onChange,
  // but calling updateTab explicitly removes any window where the store lags behind
  // the editor and a subsequent Run would submit the pre-insert SQL.
  const insertAtCursor = useCallback((source: string, text: string) => {
    const handle = editorHandleRef.current;
    if (!handle) return;
    const ed = handle.getEditor();
    if (!ed) return;
    const pos = ed.getPosition();
    if (!pos) return;
    ed.executeEdits(source, [
      {
        range: {
          startLineNumber: pos.lineNumber,
          startColumn: pos.column,
          endLineNumber: pos.lineNumber,
          endColumn: pos.column,
        },
        text,
        forceMoveMarkers: true,
      },
    ]);
    const id = useTabStore.getState().activeTabId;
    if (id) updateTab(id, { sqlText: ed.getValue() });
  }, [updateTab]);

  const handleFormat = useCallback(async () => {
    if (!activeTab) return;
    const input = activeTab.sqlText;
    try {
      const next = await formatSql(input, activeTab.engine);
      // race guard: if user typed during await, discard stale result
      const latest = useTabStore
        .getState()
        .tabs.find((t) => t.id === activeTab.id)?.sqlText;
      if (latest !== input) return;
      const handle = editorHandleRef.current;
      if (handle) {
        // Cursor-preserving replace via Monaco pushEditOperations
        handle.replaceContent(next);
      } else {
        // Fallback: editor not mounted yet — update store directly
        updateTab(activeTab.id, { sqlText: next });
      }
    } catch (err) {
      message.error("格式化失败，请检查 SQL 语法");
      if (import.meta.env.DEV) {
        // eslint-disable-next-line no-console
        console.error("[SqlIde] format failed", err);
      }
    }
  }, [activeTab, updateTab]);

  const handleDatasourceChange = useCallback(
    (datasource: NonNullable<typeof activeDatasource>) => {
      const state = useTabStore.getState();
      const tab = state.tabs.find((item) => item.id === state.activeTabId);
      if (!tab || tab.datasourceId === datasource.id) return;
      updateTab(tab.id, {
        datasourceId: datasource.id,
        schemaContext: null,
        engine: normalizeCatalogEngine(datasource.engine),
      });
    },
    [updateTab],
  );

  const handleExecute = useCallback(
    (sql: string) => {
      if (!sql.trim() || !activeTab || sqlExec.state === "running") return;
      const targetTabId = activeTab.id;
      void sqlExec
        .submit({
          sqlText: sql,
          datasource: activeTab.datasourceId,
          catalog: activeTab.schemaContext,
          schema: null,
        })
        .then((id) => {
          if (!id) return;
          const live = resolveId(targetTabId) ?? targetTabId;
          updateTab(live, { lastExecutionId: id });
        });
    },
    [activeTab, sqlExec.state, sqlExec.submit, updateTab],
  );

  const handleRun = useCallback(() => {
    if (!activeTab) return;
    const editor = editorHandleRef.current?.getEditor();
    const model = editor?.getModel();
    if (!editor || !model) {
      handleExecute(activeTab.sqlText);
      return;
    }
    const selection = editor.getSelection();
    if (selection && !selection.isEmpty()) {
      handleExecute(model.getValueInRange(selection));
      return;
    }
    const position = editor.getPosition() ?? { lineNumber: 1, column: 1 };
    const offset = model.getOffsetAt(position);
    handleExecute(findStatementAt(model.getValue(), offset)?.text ?? activeTab.sqlText);
  }, [activeTab, handleExecute]);

  const handleOpenSave = useCallback((sql: string) => {
    if (!sql.trim()) return;
    setPendingSql(sql);
    setSaveDialogOpen(true);
  }, []);

  return (
    <div
      data-testid="sqlide-root"
      style={{ display: "flex", width: "100%", height: "100%", minHeight: 0 }}
    >
      <ActivityBar />
      <SidePanel>
        {activeActivity === "schema" && (
          <SchemaTree
            key={activeTab?.id ?? "no-active-tab"}
            datasourceId={activeTab?.datasourceId}
            onDatasourceChange={handleDatasourceChange}
            onInsertIdentifier={(name) => {
              insertAtCursor("sqlide.schema-insert", name);
            }}
            onInsertSqlAtCursor={(sql) => {
              insertAtCursor("sqlide.schema-sql", sql);
            }}
          />
        )}
        {activeActivity === "history" && <HistoryPanel />}
        {activeActivity === "saved" && <SavedPanel />}
        {activeActivity === "search" && (
          <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>搜索 · 暂未开放</div>
        )}
        {activeActivity === "copilot" && <CopilotSlot />}
      </SidePanel>
      <div
        style={{
          flex: 1,
          display: "flex",
          flexDirection: "column",
          minWidth: 0,
          // Right column owns its own vertical scroll: editor is given a fixed
          // 80-line height and BottomPanel a fixed 486 — together they can
          // overflow the viewport, so the column needs a scrollbar.
          overflowY: "auto",
          minHeight: 0,
        }}
      >
        <div style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          padding: "4px 8px",
          borderBottom: "1px solid var(--ant-color-border-secondary)",
          gap: 8,
          // Keep the mode switcher visible while the column scrolls.
          position: "sticky",
          top: 0,
          zIndex: 5,
          background: "var(--ant-color-bg-container, #fff)",
        }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <Button
              type="primary"
              size="small"
              icon={<PlayCircleOutlined />}
              disabled={!activeTab?.sqlText.trim() || sqlExec.state === "running"}
              onClick={handleRun}
            >
              运行
            </Button>
            <Button
              danger
              size="small"
              icon={<StopOutlined />}
              disabled={sqlExec.state !== "running"}
              onClick={sqlExec.cancel}
            >
              停止
            </Button>
            <Button
              size="small"
              icon={<SaveOutlined />}
              disabled={!activeTab?.sqlText.trim()}
              onClick={() => handleOpenSave(activeTab?.sqlText ?? "")}
            >
              保存
            </Button>
          </div>
          <ModeSwitcher />
        </div>
        <TabBar />
        {/*
          Editor wrapper: user-resizable height, default 20 lines (380px).
          Drag the row-resize handle below to grow/shrink. Persisted in localStorage.
          flexShrink: 0 prevents the column flex layout from squeezing it.
        */}
        <div style={{ height: editorHeight, flexShrink: 0, minHeight: 0 }}>
          {activeTab ? (
            <SqlEditor
              ref={editorHandleRef}
              value={activeTab.sqlText}
              onChange={handleSqlChange}
              engine={activeTab.engine}
              mode={uiMode}
              isDark={true}
              catalog={catalogSource}
              onExecute={handleExecute}
              onExecuteInNewTab={(s) =>
                console.info("[SqlIde] executeInNewTab:", s)
              }
              onFormat={handleFormat}
              onSaveAsQuery={handleOpenSave}
              onToggleBottomPanel={() =>
                console.info("[SqlIde] toggleBottomPanel")
              }
            />
          ) : (
            <div
              style={{ padding: 16, color: "var(--ant-color-text-secondary)" }}
            >
              正在恢复 Tab……
            </div>
          )}
        </div>
        {/* Drag handle to resize the SQL editor area (between editor and result panel) */}
        <div
          role="separator"
          aria-orientation="horizontal"
          aria-label="拖动调整 SQL 编辑器高度"
          onMouseDown={handleResizeStart}
          onDoubleClick={() => setEditorHeight(EDITOR_DEFAULT_HEIGHT)}
          title="拖动调整高度，双击恢复默认"
          style={{
            height: 6,
            flexShrink: 0,
            cursor: "row-resize",
            background: "var(--ant-color-fill-secondary, rgba(0,0,0,0.06))",
            borderTop: "1px solid var(--ant-color-border-secondary, rgba(5,5,5,0.06))",
            borderBottom: "1px solid var(--ant-color-border-secondary, rgba(5,5,5,0.06))",
            transition: "background 120ms",
          }}
          onMouseEnter={(e) => {
            (e.currentTarget as HTMLDivElement).style.background =
              "var(--ant-color-primary, #1677ff)";
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLDivElement).style.background =
              "var(--ant-color-fill-secondary, rgba(0,0,0,0.06))";
          }}
        />
        <BottomPanel>
          <div style={{ height: "100%", display: "flex", flexDirection: "column" }}>
            <div
              style={{
                padding: "4px 8px",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                borderBottom: "1px solid var(--ant-color-border-secondary)",
              }}
            >
              <span style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>
                {sqlExec.state === "running" && `运行中 · ${(sqlExec.elapsedMs / 1000).toFixed(1)}s`}
                {sqlExec.state === "success" && `成功 · ${sqlExec.rowCount ?? 0} 行 · ${(sqlExec.elapsedMs / 1000).toFixed(1)}s`}
                {sqlExec.state === "failed" && `失败 · ${sqlExec.errorMessage ?? ""}`}
                {sqlExec.state === "canceled" && "已取消"}
                {sqlExec.state === "idle" && "未运行"}
              </span>
              <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                {sqlExec.state === "running" && (
                  <Button danger size="small" onClick={sqlExec.cancel}>
                    停止
                  </Button>
                )}
                {activeTab?.lastExecutionId && (
                  <ExportMenu executionId={activeTab.lastExecutionId} />
                )}
                {activeTab?.lastExecutionId && (
                  <SubQueryButton executionId={activeTab.lastExecutionId} />
                )}
                <Button size="small" onClick={() => setHelpOpen(true)}>
                  ⌨ 快捷键
                </Button>
              </div>
            </div>
            <div style={{ flex: 1, minHeight: 0 }}>
              {sqlExec.state === "failed" ? (
                <div style={{ padding: 12, color: "var(--ant-color-error)", whiteSpace: "pre-wrap", fontFamily: "monospace", fontSize: 12 }}>
                  ❌ 执行失败
                  {"\n\n"}
                  {sqlExec.errorMessage ?? "(未返回错误信息)"}
                </div>
              ) : sqlExec.state === "canceled" ? (
                <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>⏹ 已取消</div>
              ) : activeTab?.lastExecutionId && sqlExec.state !== "running" ? (
                <BottomTabs
                  executionId={activeTab.lastExecutionId}
                  sql={activeTab.sqlText}
                  engine={activeTab.engine}
                  datasourceId={activeTab.datasourceId}
                  catalog={activeTab.schemaContext}
                  gridState={activeTab.gridState}
                  onGridStateChange={(next) => updateGridState(activeTab.id, next)}
                />
              ) : sqlExec.state === "running" ? (
                <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>⏳ 运行中……</div>
              ) : (
                <div style={{ padding: 12, color: "var(--ant-color-text-tertiary)" }}>运行 SQL 后结果出现在这里</div>
              )}
            </div>
          </div>
        </BottomPanel>
      </div>
      <ShortcutsHelp open={helpOpen} onClose={() => setHelpOpen(false)} />
      <SaveQueryDialog
        open={saveDialogOpen}
        initialSql={pendingSql}
        datasourceId={activeTab?.datasourceId}
        datasourceName={activeDatasource?.name ?? activeDatasource?.label}
        existingFolders={existingFolders}
        onClose={() => setSaveDialogOpen(false)}
      />
    </div>
  );
};
