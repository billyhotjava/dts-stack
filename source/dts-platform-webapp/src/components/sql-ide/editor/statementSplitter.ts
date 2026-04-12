export interface Statement {
  start: number;
  end: number;
  text: string;
}

/**
 * Split SQL by `;` while ignoring semicolons inside single-quoted strings,
 * block comments (/* ... *\/), and line comments (-- ...\n).
 * A full SQL parser is out of scope; this covers the common cases.
 */
export function splitStatements(sql: string): Statement[] {
  const out: Statement[] = [];
  let inString = false;
  let inBlockComment = false;
  let inLineComment = false;
  let stmtStart = 0;
  let i = 0;
  while (i < sql.length) {
    const ch = sql[i];
    const next = sql[i + 1];

    if (inLineComment) {
      if (ch === "\n") {
        inLineComment = false;
        // If the pending statement so far is only whitespace + this line comment,
        // advance stmtStart past the newline so it doesn't pollute the next statement.
        if (sql.slice(stmtStart, i + 1).trim().length === 0 ||
            /^\s*--[^\n]*\n$/.test(sql.slice(stmtStart, i + 1))) {
          stmtStart = i + 1;
        }
      }
      i++;
      continue;
    }
    if (inBlockComment) {
      if (ch === "*" && next === "/") {
        inBlockComment = false;
        i += 2;
        continue;
      }
      i++;
      continue;
    }
    if (inString) {
      if (ch === "'" && sql[i - 1] !== "\\") inString = false;
      i++;
      continue;
    }
    // outside all of the above
    if (ch === "'") {
      inString = true;
      i++;
      continue;
    }
    if (ch === "-" && next === "-") {
      inLineComment = true;
      i += 2;
      continue;
    }
    if (ch === "/" && next === "*") {
      inBlockComment = true;
      i += 2;
      continue;
    }
    if (ch === ";") {
      const end = i + 1;
      out.push({ start: stmtStart, end, text: sql.slice(stmtStart, end) });
      stmtStart = end;
    }
    i++;
  }
  const tail = sql.slice(stmtStart).trim();
  if (tail.length > 0) {
    out.push({ start: stmtStart, end: sql.length, text: sql.slice(stmtStart) });
  }
  // Trim leading whitespace and advance start to match trimmed text.
  return out
    .map((s) => {
      const leading = s.text.match(/^\s*/)?.[0].length ?? 0;
      return { start: s.start + leading, end: s.end, text: s.text.replace(/^\s+/, "") };
    })
    .filter((s) => s.text.length > 0);
}

export function findStatementAt(sql: string, offset: number): Statement | undefined {
  for (const s of splitStatements(sql)) {
    if (offset >= s.start && offset <= s.end) return s;
  }
  return undefined;
}
