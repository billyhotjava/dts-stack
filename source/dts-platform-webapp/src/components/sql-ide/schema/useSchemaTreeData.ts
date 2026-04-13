import { useQuery } from "@tanstack/react-query";
import {
  type CatalogColumn,
  type CatalogDatasource,
  type CatalogSchema,
  type CatalogTable,
  listColumns,
  listDatasources,
  listSchemas,
  listTables,
} from "../api/sqlIdeCatalog";

const STALE_5_MIN = 5 * 60 * 1000;
const CACHE_30_MIN = 30 * 60 * 1000;

export function useDatasourcesQuery() {
  return useQuery<CatalogDatasource[]>({
    queryKey: ["sqlide", "catalog", "datasources"],
    queryFn: listDatasources,
    staleTime: STALE_5_MIN,
    gcTime: CACHE_30_MIN,
  });
}

export function useSchemasQuery(dsId: string | null) {
  return useQuery<CatalogSchema[]>({
    queryKey: ["sqlide", "catalog", "schemas", dsId],
    queryFn: () => listSchemas(dsId!),
    enabled: !!dsId,
    staleTime: STALE_5_MIN,
    gcTime: CACHE_30_MIN,
  });
}

export function useTablesQuery(dsId: string | null, schema: string | null) {
  return useQuery<CatalogTable[]>({
    queryKey: ["sqlide", "catalog", "tables", dsId, schema],
    queryFn: () => listTables(dsId!, schema!),
    enabled: !!dsId && !!schema,
    staleTime: STALE_5_MIN,
    gcTime: CACHE_30_MIN,
  });
}

export function useColumnsQuery(dsId: string | null, schemaTable: string | null) {
  return useQuery<CatalogColumn[]>({
    queryKey: ["sqlide", "catalog", "columns", dsId, schemaTable],
    queryFn: () => listColumns(dsId!, schemaTable!),
    enabled: !!dsId && !!schemaTable,
    staleTime: STALE_5_MIN,
    gcTime: CACHE_30_MIN,
  });
}
