import Editor, { useMonaco, type OnMount } from "@monaco-editor/react";
import { type FC, useCallback, useEffect, useRef } from "react";
import { NOOP_CATALOG, registerSqlCatalogCompletion, type CatalogSource } from "./completion/catalogProvider";
import { SQLIDE_DARK, SQLIDE_LIGHT, registerSqlIdeThemes } from "./themes";
import { findStatementAt } from "./statementSplitter";

export type Engine = "trino" | "hive" | "postgresql" | "generic";
export type EditorMode = "simple" | "advanced";

export interface SqlEditorProps {
  value: string;
  onChange: (sql: string) => void;
  engine?: Engine;
  mode?: EditorMode;
  readOnly?: boolean;
  isDark?: boolean;
  onExecute?: (sql: string) => void;
  onExecuteInNewTab?: (sql: string) => void;
  onFormat?: () => void;
  onSaveAsQuery?: (sql: string) => void;
  onToggleBottomPanel?: () => void;
  onCursorPositionChange?: (pos: { line: number; column: number }) => void;
  catalog?: CatalogSource;
}

export const SqlEditor: FC<SqlEditorProps> = ({
  value,
  onChange,
  engine = "generic",
  mode = "simple",
  readOnly = false,
  isDark = true,
  onExecute,
  onExecuteInNewTab,
  onFormat,
  onSaveAsQuery,
  onToggleBottomPanel,
  onCursorPositionChange,
  catalog,
}) => {
  const monaco = useMonaco();

  // Register SQL catalog completion provider; re-register when catalog source changes
  useEffect(() => {
    if (!monaco) return;
    const disposable = registerSqlCatalogCompletion(monaco, catalog ?? NOOP_CATALOG);
    return () => disposable.dispose();
  }, [monaco, catalog]);

  // Keep refs to the latest callbacks so listeners never capture stale closures
  const onCursorPositionChangeRef = useRef(onCursorPositionChange);
  useEffect(() => {
    onCursorPositionChangeRef.current = onCursorPositionChange;
  });

  const onExecuteRef = useRef(onExecute);
  useEffect(() => {
    onExecuteRef.current = onExecute;
  });

  const onExecuteInNewTabRef = useRef(onExecuteInNewTab);
  useEffect(() => {
    onExecuteInNewTabRef.current = onExecuteInNewTab;
  });

  const onFormatRef = useRef(onFormat);
  useEffect(() => {
    onFormatRef.current = onFormat;
  });

  const onSaveAsQueryRef = useRef(onSaveAsQuery);
  useEffect(() => {
    onSaveAsQueryRef.current = onSaveAsQuery;
  });

  const onToggleBottomPanelRef = useRef(onToggleBottomPanel);
  useEffect(() => {
    onToggleBottomPanelRef.current = onToggleBottomPanel;
  });

  // Disposables registered during mount — cleaned up when the component unmounts
  const disposablesRef = useRef<{ dispose(): void }[]>([]);
  useEffect(() => {
    return () => {
      disposablesRef.current.forEach((d) => d.dispose());
    };
  }, []);

  const handleMount: OnMount = useCallback(
    (editor, mo) => {
      registerSqlIdeThemes(mo);

      const cursorDisposable = editor.onDidChangeCursorPosition((e) => {
        onCursorPositionChangeRef.current?.({
          line: e.position.lineNumber,
          column: e.position.column,
        });
      });
      disposablesRef.current.push(cursorDisposable);

      const getCurrentSql = (): string => {
        const sel = editor.getSelection();
        const model = editor.getModel();
        if (!model) return "";
        if (sel && !sel.isEmpty()) return model.getValueInRange(sel);
        const offset = model.getOffsetAt(editor.getPosition() ?? { lineNumber: 1, column: 1 });
        return findStatementAt(model.getValue(), offset)?.text ?? model.getValue();
      };

      // Ctrl+Enter — execute current SQL (selection or statement at cursor)
      editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyCode.Enter, () => {
        onExecuteRef.current?.(getCurrentSql());
      });

      // Ctrl+Shift+Enter — execute in new tab
      editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyMod.Shift | mo.KeyCode.Enter, () => {
        onExecuteInNewTabRef.current?.(getCurrentSql());
      });

      // Ctrl+Alt+F — format
      editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyMod.Alt | mo.KeyCode.KeyF, () => {
        onFormatRef.current?.();
      });

      // Ctrl+S — save as query (Monaco addCommand prevents browser default)
      editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyCode.KeyS, () => {
        onSaveAsQueryRef.current?.(editor.getValue());
      });

      // Ctrl+` — toggle bottom panel
      editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyCode.Backquote, () => {
        onToggleBottomPanelRef.current?.();
      });

      // Ctrl+/ — toggle line comment (built-in)
      editor.addAction({
        id: "sqlide.toggleLineComment",
        label: "Toggle Line Comment",
        keybindings: [mo.KeyMod.CtrlCmd | mo.KeyCode.Slash],
        run: (ed) => ed.trigger("sqlide", "editor.action.commentLine", {}),
      });

      // Ctrl+D — add selection to next match (built-in)
      editor.addAction({
        id: "sqlide.addNextMatch",
        label: "Add Selection To Next Match",
        keybindings: [mo.KeyMod.CtrlCmd | mo.KeyCode.KeyD],
        run: (ed) => ed.trigger("sqlide", "editor.action.addSelectionToNextFindMatch", {}),
      });

      // Alt+↑ — move line up (built-in)
      editor.addAction({
        id: "sqlide.moveLineUp",
        label: "Move Line Up",
        keybindings: [mo.KeyMod.Alt | mo.KeyCode.UpArrow],
        run: (ed) => ed.trigger("sqlide", "editor.action.moveLinesUpAction", {}),
      });

      // Alt+↓ — move line down (built-in)
      editor.addAction({
        id: "sqlide.moveLineDown",
        label: "Move Line Down",
        keybindings: [mo.KeyMod.Alt | mo.KeyCode.DownArrow],
        run: (ed) => ed.trigger("sqlide", "editor.action.moveLinesDownAction", {}),
      });

      // F8 — go to next error marker (built-in)
      editor.addAction({
        id: "sqlide.gotoNextMarker",
        label: "Go to Next Marker",
        keybindings: [mo.KeyCode.F8],
        run: (ed) => ed.trigger("sqlide", "editor.action.marker.next", {}),
      });
    },
    [],
  );

  return (
    <div
      data-testid="sqlide-editor"
      data-engine={engine}
      data-mode={mode}
      style={{ height: "100%", width: "100%", minHeight: 0 }}
    >
      <Editor
        height="100%"
        language="sql"
        value={value}
        onChange={(v) => onChange(v ?? "")}
        theme={isDark ? SQLIDE_DARK : SQLIDE_LIGHT}
        onMount={handleMount}
        options={{
          automaticLayout: true,
          minimap: { enabled: mode === "advanced" },
          fontSize: 14,
          lineNumbers: "on",
          wordWrap: "on",
          suggestOnTriggerCharacters: true,
          quickSuggestions: { other: true, comments: false, strings: true },
          tabSize: 2,
          bracketPairColorization: { enabled: true },
          scrollBeyondLastLine: false,
          renderLineHighlight: "line",
          readOnly,
        }}
      />
    </div>
  );
};
