export function valueToCellString(v: unknown): string {
  if (v === null || v === undefined) return "";
  if (typeof v === "number" || typeof v === "boolean") return String(v);
  if (typeof v === "string") return v.replace(/\t/g, " ").replace(/\n/g, " ");
  try {
    return JSON.stringify(v);
  } catch {
    return String(v);
  }
}

export function rowsToTSV(rows: Array<Record<string, unknown>>, columnNames: string[]): string {
  const header = columnNames.join("\t");
  const body = rows
    .map((r) => columnNames.map((c) => valueToCellString(r[c])).join("\t"))
    .join("\n");
  return body ? `${header}\n${body}` : header;
}
