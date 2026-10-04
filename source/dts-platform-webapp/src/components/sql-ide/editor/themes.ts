import type * as MonacoNs from "monaco-editor";

export const SQLIDE_DARK = "sqlide-dark";
export const SQLIDE_LIGHT = "sqlide-light";

let registered = false;

export function registerSqlIdeThemes(monaco: typeof MonacoNs): void {
  if (registered) return;
  registered = true;
  monaco.editor.defineTheme(SQLIDE_DARK, {
    base: "vs-dark",
    inherit: true,
    rules: [
      { token: "keyword.sql", foreground: "c678dd", fontStyle: "bold" },
      { token: "string.sql", foreground: "98c379" },
      { token: "number.sql", foreground: "d19a66" },
      { token: "comment.sql", foreground: "7f848e", fontStyle: "italic" },
    ],
    colors: {
      "editor.background": "#1e1e2e",
      "editor.foreground": "#d4d4d8",
      "editorLineNumber.foreground": "#5c6370",
      "editor.selectionBackground": "#3a3a5e",
    },
  });
  monaco.editor.defineTheme(SQLIDE_LIGHT, {
    base: "vs",
    inherit: true,
    rules: [
      { token: "keyword.sql", foreground: "7c3aed", fontStyle: "bold" },
      { token: "string.sql", foreground: "059669" },
      { token: "number.sql", foreground: "b45309" },
      { token: "comment.sql", foreground: "6b7280", fontStyle: "italic" },
    ],
    colors: {
      "editor.background": "#ffffff",
      "editor.foreground": "#18181b",
    },
  });
}
