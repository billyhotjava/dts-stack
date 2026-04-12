import type { Engine } from "./SqlEditor";

/** Map SQL-IDE engine names to sql-formatter dialect names */
export const DIALECT_MAP: Record<Engine, string> = {
  trino: "trino",
  hive: "hive",
  postgresql: "postgresql",
  generic: "sql",
};

/**
 * Formats the given SQL string using sql-formatter (dynamically imported
 * so it is never included in the main entry-point bundle).
 */
export async function formatSql(sql: string, engine: Engine = "generic"): Promise<string> {
  // Dynamic import keeps sql-formatter out of the initial bundle chunk
  const { format } = await import("sql-formatter");
  const dialect = DIALECT_MAP[engine] ?? "sql";
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return format(sql, {
    language: dialect as any,
    keywordCase: "upper",
    indentStyle: "standard",
    tabWidth: 2,
  });
}
