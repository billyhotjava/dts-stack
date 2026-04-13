import { Button, message } from "antd";
import { type FC, useCallback, useEffect, useRef, useState } from "react";
import { useShallow } from "zustand/react/shallow";
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

export const SqlIde: FC = () => {
  const activeActivity = useLayoutStore((s) => s.activeActivity);
  const { tabs, activeTabId, hydrated } = useTabStore(
    useShallow((s) => ({ tabs: s.tabs, activeTabId: s.activeTabId, hydrated: s.hydrated })),
  );
  const hydrate = useTabStore((s) => s.hydrate);
  const openTab = useTabStore((s) => s.openTab);
  const updateTab = useTabStore((s) => s.updateTab);
  const editorHandleRef = useRef<SqlEditorHandle>(null);
  const [helpOpen, setHelpOpen] = useState(false);

  // Hydrate on mount
  useEffect(() => {
    if (!hydrated) void hydrate();
  }, [hydrated, hydrate]);

  // Ensure at least one tab exists after hydrate
  useEffect(() => {
    if (hydrated && tabs.length === 0) openTab();
  }, [hydrated, tabs.length, openTab]);

  const activeTab = tabs.find((t) => t.id === activeTabId) ?? null;

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
        {activeActivity === "saved" && (
          <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>Saved · T15 待完成</div>
        )}
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
              onExecute={(s) => console.info("[SqlIde] execute:", s)}
              onExecuteInNewTab={(s) =>
                console.info("[SqlIde] executeInNewTab:", s)
              }
              onFormat={handleFormat}
              onSaveAsQuery={(s) => console.info("[SqlIde] saveAsQuery:", s)}
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
          <div
            style={{
              padding: "8px 12px",
              display: "flex",
              alignItems: "center",
              gap: 8,
            }}
          >
            <span style={{ flex: 1 }}>Bottom · 骨架</span>
            <Button size="small" onClick={() => setHelpOpen(true)}>
              ⌨ Shortcuts
            </Button>
          </div>
        </BottomPanel>
      </div>
      <ShortcutsHelp open={helpOpen} onClose={() => setHelpOpen(false)} />
    </div>
  );
};
