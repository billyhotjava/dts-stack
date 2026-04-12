import Editor, { type OnMount, useMonaco } from "@monaco-editor/react";
import { type FC, useCallback, useEffect } from "react";
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
}) => {
  const monaco = useMonaco();

  useEffect(() => {
    if (monaco) registerSqlIdeThemes(monaco);
  }, [monaco]);

  const handleMount: OnMount = useCallback(
    (editor, mo) => {
      registerSqlIdeThemes(mo);
      editor.onDidChangeCursorPosition((e) => {
        onCursorPositionChange?.({ line: e.position.lineNumber, column: e.position.column });
      });
      // Ctrl+Enter placeholder — full keymap in T05
      editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyCode.Enter, () => {
        onExecute?.(editor.getValue());
      });
    },
    [onCursorPositionChange, onExecute],
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
