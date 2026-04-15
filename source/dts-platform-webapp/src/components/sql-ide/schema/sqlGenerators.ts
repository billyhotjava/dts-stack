function quoteIfNeeded(id: string): string {
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(id) ? id : `"${id.replace(/"/g, '""')}"`;
}

export function generateSelect(
  schema: string,
  table: string,
  columns: string[] | null,
): string {
  const cols =
    columns && columns.length > 0
      ? columns.map(quoteIfNeeded).join(", ")
      : "*";
  return `SELECT ${cols} FROM ${quoteIfNeeded(schema)}.${quoteIfNeeded(table)} LIMIT 100`;
}

export function generateInsert(
  schema: string,
  table: string,
  columns: string[],
): string {
  if (columns.length === 0) return "";
  const quotedCols = columns.map(quoteIfNeeded).join(", ");
  const placeholders = columns.map(() => "?").join(", ");
  return `INSERT INTO ${quoteIfNeeded(schema)}.${quoteIfNeeded(table)} (${quotedCols}) VALUES (${placeholders})`;
}

export function copyName(schema: string, table: string): string {
  return `${schema}.${table}`;
}
