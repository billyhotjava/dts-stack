import type * as MonacoNs from "monaco-editor";

const ANSI_KEYWORDS = [
  "SELECT", "FROM", "WHERE", "GROUP BY", "ORDER BY", "HAVING", "LIMIT",
  "OFFSET", "JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "FULL OUTER JOIN",
  "ON", "AS", "AND", "OR", "NOT", "IN", "BETWEEN", "LIKE", "IS NULL",
  "IS NOT NULL", "CASE", "WHEN", "THEN", "ELSE", "END", "UNION", "UNION ALL",
  "WITH", "DISTINCT", "COUNT", "SUM", "AVG", "MIN", "MAX",
];

export function buildKeywordSuggestions(
  monaco: typeof MonacoNs,
  range: MonacoNs.IRange,
): MonacoNs.languages.CompletionItem[] {
  return ANSI_KEYWORDS.map((kw) => ({
    label: kw,
    kind: monaco.languages.CompletionItemKind.Keyword,
    insertText: kw,
    range,
    sortText: `z_${kw}`, // keywords sort lower than catalog items
  }));
}
