import { Button, message } from "antd";
import { type FC, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useShallow } from "zustand/react/shallow";
import { useQuery } from "@tanstack/react-query";
import { ShortcutsHelp } from "./ShortcutsHelp";
import { CopilotSlot } from "./copilot/CopilotSlot";
import { SqlEditor, type SqlEditorHandle } from "./editor/SqlEditor";
import { formatSql } from "./editor/formatter";
import { ActivityBar } from "./layout/ActivityBar";
import { BottomPanel } from "./layout/BottomPanel";
import { SidePanel } from "./layout/SidePanel";
import { useLayoutStore } from "./layout/useLayoutStore";
import { HistoryPanel } from "./history/HistoryPanel";
import { SchemaTree } from "./schema/SchemaTree";
import { TabBar } from "./tabs/TabBar";
import { useTabStore } from "./tabs/useTabStore";
import { ResultGrid } from "./result/ResultGrid";
import { SavedPanel } from "./saved/SavedPanel";
import { SaveQueryDialog } from "./saved/SaveQueryDialog";
import { listSavedQueries } from "./api/sqlIdeSaved";
import { useSqlExecution } from "./hooks/useSqlExecution";

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

  const sqlExec = useSqlExecution();

  const handleSqlChange = useCallback(
    (next: string) => {
      if (activeTab) updateTab(activeTab.id, { sqlText: next });
    },
    [activeTab, updateTab],
  );

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

  return (
    <div
      data-testid="sqlide-root"
      style={{ display: "flex", width: "100%", height: "100%", minHeight: 0 }}
    >
      <ActivityBar />
      <SidePanel>
        {activeActivity === "schema" && (
          <SchemaTree
            onInsertIdentifier={(name) => {
              const handle = editorHandleRef.current;
              if (!handle) return;
              const ed = handle.getEditor();
              if (!ed) return;
              const pos = ed.getPosition();
              const op = pos
                ? {
                    range: {
                      startLineNumber: pos.lineNumber,
                      startColumn: pos.column,
                      endLineNumber: pos.lineNumber,
                      endColumn: pos.column,
                    },
                    text: name,
                  }
                : null;
              if (op) ed.executeEdits("sqlide.schema-insert", [op]);
            }}
            onInsertSqlAtCursor={(sql) => {
              const handle = editorHandleRef.current;
              if (!handle) return;
              const ed = handle.getEditor();
              if (!ed) return;
              const pos = ed.getPosition();
              const op = pos
                ? {
                    range: {
                      startLineNumber: pos.lineNumber,
                      startColumn: pos.column,
                      endLineNumber: pos.lineNumber,
                      endColumn: pos.column,
                    },
                    text: sql,
                  }
                : null;
              if (op) ed.executeEdits("sqlide.schema-sql", [op]);
            }}
          />
        )}
        {activeActivity === "history" && <HistoryPanel />}
        {activeActivity === "saved" && <SavedPanel />}
        {activeActivity === "search" && (
          <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>Search · 暂未实现</div>
        )}
        {activeActivity === "copilot" && <CopilotSlot />}
      </SidePanel>
      <div
        style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}
      >
        <TabBar />
        <div style={{ flex: 1, minHeight: 0 }}>
          {activeTab ? (
            <SqlEditor
              ref={editorHandleRef}
              value={activeTab.sqlText}
              onChange={handleSqlChange}
              engine={activeTab.engine}
              mode="simple"
              isDark={true}
              onExecute={(s) => {
                if (!s.trim() || !activeTab) return;
                // Fix #5 (Important): hammer guard — no re-submit while running
                if (sqlExec.state === "running") return;
                // Fix #2 (Critical): capture tabId at submit time so a mid-flight
                // tab switch cannot write lastExecutionId to the wrong tab
                const targetTabId = activeTab.id;
                void sqlExec
                  .submit({
                    sqlText: s,
                    datasource: activeTab.datasourceId,
                    catalog: activeTab.schemaContext,
                    schema: null,
                  })
                  .then((id) => {
                    if (id) updateTab(targetTabId, { lastExecutionId: id });
                  });
              }}
              onExecuteInNewTab={(s) =>
                console.info("[SqlIde] executeInNewTab:", s)
              }
              onFormat={handleFormat}
              onSaveAsQuery={(s) => { setPendingSql(s); setSaveDialogOpen(true); }}
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
                    取消
                  </Button>
                )}
                <Button size="small" onClick={() => setHelpOpen(true)}>
                  ⌨ Shortcuts
                </Button>
              </div>
            </div>
            <div style={{ flex: 1, minHeight: 0 }}>
              {activeTab?.lastExecutionId ? (
                <ResultGrid
                  executionId={activeTab.lastExecutionId}
                  gridState={activeTab.gridState}
                  onGridStateChange={(next) => updateGridState(activeTab.id, next)}
                />
              ) : (
                <div style={{ padding: 12, color: "var(--ant-color-text-tertiary)" }}>
                  运行 SQL 后结果出现在这里
                </div>
              )}
            </div>
          </div>
        </BottomPanel>
      </div>
      <ShortcutsHelp open={helpOpen} onClose={() => setHelpOpen(false)} />
      <SaveQueryDialog
        open={saveDialogOpen}
        initialSql={pendingSql}
        existingFolders={existingFolders}
        onClose={() => setSaveDialogOpen(false)}
      />
    </div>
  );
};
