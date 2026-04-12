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

  // afterDot: identifier immediately before end of string (optionally trailed by whitespace)
  // End-anchored so we match the dot closest to the cursor, not the first one in the string.
  // Unicode-aware negative lookbehind replaces \b so CJK identifiers (e.g. 表名) are handled.
  const dotMatch = /(?<![\p{L}\p{N}_])([\p{L}_][\p{L}\p{N}_]*)\.\s*$/u.exec(cleaned);
  if (dotMatch) return { kind: "afterDot", alias: dotMatch[1] };

  // afterFrom: FROM or JOIN followed by optional whitespace (cursor is where table name goes)
  if (/\b(?:FROM|JOIN)\s*$/iu.test(trimmed)) return { kind: "afterFrom" };

  return { kind: "default" };
}
