import { Button, message } from "antd";
import { type FC, useCallback, useEffect, useRef, useState } from "react";
import { ActivityBar } from "./layout/ActivityBar";
import { SidePanel } from "./layout/SidePanel";
import { BottomPanel } from "./layout/BottomPanel";
import { SqlEditor, type SqlEditorHandle } from "./editor/SqlEditor";
import { ShortcutsHelp } from "./ShortcutsHelp";
import { formatSql } from "./editor/formatter";

export const SqlIde: FC = () => {
  const [sql, setSql] = useState<string>("-- SQL IDE v2\nSELECT 1;");
  const [helpOpen, setHelpOpen] = useState(false);

  const sqlRef = useRef(sql);
  useEffect(() => { sqlRef.current = sql; });

  const editorHandleRef = useRef<SqlEditorHandle>(null);

  const handleFormat = useCallback(async () => {
    const input = sql;
    try {
      const next = await formatSql(input, "generic");
      if (sqlRef.current !== input) {
        // User kept typing while format chunk was loading; discard stale result
        return;
      }
      const handle = editorHandleRef.current;
      if (handle) {
        // Cursor-preserving replace: saveViewState → pushEditOperations → restoreViewState.
        // Monaco fires onDidChangeModelContent → onChange → setSql, so no explicit setSql needed.
        handle.replaceContent(next);
      } else {
        // Fallback: editor not mounted yet — update React state directly.
        setSql(next);
      }
    } catch (err) {
      message.error("格式化失败，请检查 SQL 语法");
      if (import.meta.env.DEV) {
        // eslint-disable-next-line no-console
        console.error("[SqlIde] format failed", err);
      }
    }
  }, [sql]);

  return (
    <div
      data-testid="sqlide-root"
      style={{ display: "flex", width: "100%", height: "100%", minHeight: 0 }}
    >
      <ActivityBar />
      <SidePanel>
        <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>Schema · 骨架</div>
      </SidePanel>
      <div style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        <div style={{ flex: 1, minHeight: 0 }}>
          <SqlEditor
            ref={editorHandleRef}
            value={sql}
            onChange={setSql}
            engine="generic"
            mode="simple"
            isDark={true}
            onExecute={(s) => console.info("[SqlIde] execute:", s)}
            onExecuteInNewTab={(s) => console.info("[SqlIde] executeInNewTab:", s)}
            onFormat={handleFormat}
            onSaveAsQuery={(s) => console.info("[SqlIde] saveAsQuery:", s)}
            onToggleBottomPanel={() => console.info("[SqlIde] toggleBottomPanel")}
          />
        </div>
        <BottomPanel>
          <div style={{ padding: "8px 12px", display: "flex", alignItems: "center", gap: 8 }}>
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
