export type SqlContext =
  | { kind: "afterDot"; alias: string }
  | { kind: "afterFrom" }
  | { kind: "default" };

/** Strip content inside single-quoted strings (naive). */
function stripStrings(text: string): string {
  return text.replace(/'([^'\\]|\\.)*'/g, "''");
}

/**
 * Inspect the text before the cursor and classify the completion context.
 * Implementation is intentionally regex-based — a full SQL parser is overkill.
 */
export function parseContext(textBeforeCursor: string): SqlContext {
  const cleaned = stripStrings(textBeforeCursor);
  const trimmed = cleaned.replace(/\s+$/u, "");

  // afterDot: identifier followed by "." with no identifier chars immediately after
  // (covers "u. FROM ..." where no column name has been typed yet, or "public." at end)
  const dotMatch = /(\b[\p{L}_][\p{L}\p{N}_]*)\.(?![\p{L}\p{N}_])/u.exec(cleaned);
  if (dotMatch) return { kind: "afterDot", alias: dotMatch[1] };

  // afterFrom: FROM or JOIN followed by optional whitespace (cursor is where table name goes)
  if (/\b(?:FROM|JOIN)\s*$/iu.test(trimmed)) return { kind: "afterFrom" };

  return { kind: "default" };
}
