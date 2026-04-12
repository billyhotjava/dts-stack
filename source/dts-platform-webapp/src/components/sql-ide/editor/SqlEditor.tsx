import Editor, { useMonaco, type OnMount } from "@monaco-editor/react";
import { type FC, useCallback, useEffect, useRef } from "react";
import { NOOP_CATALOG, registerSqlCatalogCompletion, type CatalogSource } from "./completion/catalogProvider";
import { SQLIDE_DARK, SQLIDE_LIGHT, registerSqlIdeThemes } from "./themes";

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

  // Keep a ref to the latest callback so the listener never captures a stale closure
  const onCursorPositionChangeRef = useRef(onCursorPositionChange);
  useEffect(() => {
    onCursorPositionChangeRef.current = onCursorPositionChange;
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
      // Ctrl+Enter placeholder — full keymap in T05
      editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyCode.Enter, () => {
        onExecute?.(editor.getValue());
      });
    },
    [onExecute],
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
