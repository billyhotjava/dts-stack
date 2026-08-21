import {
  listColumns,
  listSchemas,
  listTables,
  type CatalogColumn,
  type CatalogSchema,
  type CatalogTable,
} from "../../api/sqlIdeCatalog";
import type { Engine } from "../SqlEditor";
import type { CatalogSource } from "./catalogProvider";

interface CatalogApi {
  listSchemas(dsId: string): Promise<CatalogSchema[]>;
  listTables(dsId: string, schema: string): Promise<CatalogTable[]>;
  listColumns(dsId: string, schemaTable: string): Promise<CatalogColumn[]>;
}

const DEFAULT_CATALOG_API: CatalogApi = { listSchemas, listTables, listColumns };

interface QualifiedTable extends CatalogTable {
  schema: string;
}

export function normalizeCatalogEngine(engine?: string | null): Engine {
  const normalized = engine?.toLowerCase();
  if (normalized === "trino" || normalized === "hive" || normalized === "postgresql") {
    return normalized;
  }
  return "generic";
}

export function createSqlIdeCatalogSource(
  datasourceId: string,
  api: CatalogApi = DEFAULT_CATALOG_API,
): CatalogSource {
  let tablePromise: Promise<QualifiedTable[]> | null = null;

  const loadTables = () => {
    if (!tablePromise) {
      tablePromise = api.listSchemas(datasourceId).then(async (schemas) => {
        const groups = await Promise.all(
          schemas.map(async (schema) => {
            try {
              const tables = await api.listTables(datasourceId, schema.name);
              return tables.map((table) => ({ ...table, schema: schema.name }));
            } catch {
              return [];
            }
          }),
        );
        return groups.flat();
      });
    }
    return tablePromise;
  };

  return {
    async listTables() {
      const tables = await loadTables();
      return tables.map((table) => ({
        schema: table.schema,
        name: table.name,
        comment: table.comment ?? undefined,
      }));
    },
    async listColumns(tableOrSchema) {
      const tables = await loadTables();
      const normalized = tableOrSchema.toLowerCase();

      // Monaco treats the text before a trailing dot as a column owner. In a
      // FROM clause that owner can also be a schema, so return its tables and
      // let users continue typing `schema.table` naturally.
      const schemaTables = tables.filter((table) => table.schema.toLowerCase() === normalized);
      if (schemaTables.length > 0) {
        return schemaTables.map((table) => ({
          name: table.name,
          dataType: "表",
          nullable: false,
          comment: table.comment ?? undefined,
        }));
      }

      const table = tables.find(
        (candidate) =>
          candidate.name.toLowerCase() === normalized ||
          `${candidate.schema}.${candidate.name}`.toLowerCase() === normalized,
      );
      if (!table) return [];

      const columns = await api.listColumns(datasourceId, `${table.schema}.${table.name}`);
      return columns.map((column) => ({
        name: column.name,
        dataType: column.dataType,
        nullable: column.nullable,
        comment: column.comment ?? undefined,
      }));
    },
  };
}
