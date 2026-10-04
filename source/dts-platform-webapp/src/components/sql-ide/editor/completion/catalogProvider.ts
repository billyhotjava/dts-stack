import type * as MonacoNs from "monaco-editor";
import { parseContext } from "./contextParser";
import { buildKeywordSuggestions } from "./keywordProvider";

export interface CatalogSource {
  listTables(): Promise<Array<{ schema: string; name: string; comment?: string }>>;
  listColumns(
    tableOrAlias: string,
  ): Promise<Array<{ name: string; dataType: string; nullable: boolean; comment?: string }>>;
}

/** In-memory stub; replaced by React Query-backed impl in F3/T11. */
export const NOOP_CATALOG: CatalogSource = {
  async listTables() {
    return [];
  },
  async listColumns() {
    return [];
  },
};

// FIXME(Sprint-11 F3/T11): monaco.languages.registerCompletionItemProvider
// registers globally per `language`. If multiple <SqlEditor/> instances mount
// simultaneously, each registers its own provider and suggestions will be
// duplicated. Safe today because only one SqlEditor exists at a time, but
// must be addressed when Tab-scoped catalogs land (F2/T11) — likely via
// a module-level singleton registry with reference counting.
export function registerSqlCatalogCompletion(
  monaco: typeof MonacoNs,
  source: CatalogSource,
): MonacoNs.IDisposable {
  return monaco.languages.registerCompletionItemProvider("sql", {
    triggerCharacters: [".", " "],
    async provideCompletionItems(model, position) {
      const textBefore = model.getValueInRange({
        startLineNumber: 1,
        startColumn: 1,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      });
      const word = model.getWordUntilPosition(position);
      const range: MonacoNs.IRange = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };

      const ctx = parseContext(textBefore);
      if (ctx.kind === "afterDot") {
        const cols = await source.listColumns(ctx.alias);
        if (cols.length > 0) {
          return {
            suggestions: cols.map((c) => ({
              label: c.name,
              kind: monaco.languages.CompletionItemKind.Field,
              detail: c.dataType,
              documentation: c.comment ?? undefined,
              insertText: c.name,
              range,
            })),
          };
        }
        // Empty column list — showing keywords here is misleading; show an info item instead.
        return {
          suggestions: [{
            label: "（未加载到表）",
            kind: monaco.languages.CompletionItemKind.Text,
            detail: "catalog is empty or not configured",
            insertText: "",
            range,
            filterText: "",
            sortText: "0_",
          }],
          incomplete: true,
        };
      }
      if (ctx.kind === "afterFrom") {
        const tables = await source.listTables();
        if (tables.length > 0) {
          return {
            suggestions: tables.map((t) => ({
              label: `${t.schema}.${t.name}`,
              kind: monaco.languages.CompletionItemKind.Struct,
              detail: t.comment ?? "table",
              insertText: `${t.schema}.${t.name}`,
              range,
            })),
          };
        }
        // Empty catalog — showing keywords while the user types a table name is misleading.
        return {
          suggestions: [{
            label: "（未加载到表）",
            kind: monaco.languages.CompletionItemKind.Text,
            detail: "catalog is empty or not configured",
            insertText: "",
            range,
            filterText: "",
            sortText: "0_",
          }],
          incomplete: true,
        };
      }
      return { suggestions: buildKeywordSuggestions(monaco, range) };
    },
  });
}
