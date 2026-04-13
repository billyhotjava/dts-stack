import Editor, { useMonaco, type OnMount } from "@monaco-editor/react";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef } from "react";
import type { editor as MonacoEditor } from "monaco-editor";
import { NOOP_CATALOG, registerSqlCatalogCompletion, type CatalogSource } from "./completion/catalogProvider";
import { SQLIDE_DARK, SQLIDE_LIGHT, registerSqlIdeThemes } from "./themes";
import { findStatementAt } from "./statementSplitter";

export type Engine = "trino" | "hive" | "postgresql" | "generic";
export type EditorMode = "simple" | "advanced";

export interface SqlEditorHandle {
  /** Replace editor content while preserving cursor/selection/scroll via model.pushEditOperations. */
  replaceContent(newValue: string): void;
  /** Focus the editor. */
  focus(): void;
  /** Get the underlying monaco editor (for advanced use cases — prefer named methods above). */
  getEditor(): MonacoEditor.IStandaloneCodeEditor | null;
}

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

export const SqlEditor = forwardRef<SqlEditorHandle, SqlEditorProps>(function SqlEditor({
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
}, ref) {
  const monaco = useMonaco();
  const editorInstanceRef = useRef<MonacoEditor.IStandaloneCodeEditor | null>(null);

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

  // Clear editor ref on unmount
  useEffect(() => () => { editorInstanceRef.current = null; }, []);

  useImperativeHandle(ref, () => ({
    replaceContent(newValue: string) {
      const editor = editorInstanceRef.current;
      if (!editor) return;
      const model = editor.getModel();
      if (!model) return;
      const viewState = editor.saveViewState();
      const fullRange = model.getFullModelRange();
      model.pushEditOperations([], [{ range: fullRange, text: newValue }], () => null);
      if (viewState) editor.restoreViewState(viewState);
      editor.focus();
    },
    focus() {
      editorInstanceRef.current?.focus();
    },
    getEditor() {
      return editorInstanceRef.current;
    },
  }), []);

  const handleMount: OnMount = useCallback(
    (editor, mo) => {
      editorInstanceRef.current = editor;
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
        return findStatementAt(model.getValue(), offset)?.text ?? "";
      };

      // Ctrl+Enter — execute current SQL (selection or statement at cursor)
      editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyCode.Enter, () => {
        const sql = getCurrentSql();
        if (!sql.trim()) return;
        onExecuteRef.current?.(sql);
      });

      // Ctrl+Shift+Enter — execute in new tab
      editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyMod.Shift | mo.KeyCode.Enter, () => {
        const sql = getCurrentSql();
        if (!sql.trim()) return;
        onExecuteInNewTabRef.current?.(sql);
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

      // F8 — go to next error marker (built-in)
      disposablesRef.current.push(editor.addAction({
        id: "sqlide.gotoNextMarker",
        label: "Go to Next Marker",
        keybindings: [mo.KeyCode.F8],
        run: (ed) => ed.trigger("sqlide", "editor.action.marker.next", {}),
      }));
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
});
