import apiClient from "@/api/apiClient";

export interface CatalogDatasource {
  id: string;
  name: string;
  engine: string;
  label: string;
}

export interface CatalogSchema {
  name: string;
  catalog: string | null;
}

export interface CatalogTable {
  name: string;
  type: string;
  comment: string | null;
  rowCountEstimate: number | null;
}

export interface CatalogColumn {
  name: string;
  dataType: string;
  nullable: boolean;
  comment: string | null;
  ordinalPosition: number;
}

export interface CatalogSearchHit {
  schema: string;
  table: string;
  column: string | null;
  type: string;
}

export async function listDatasources(): Promise<CatalogDatasource[]> {
  return apiClient.get<CatalogDatasource[]>({ url: "/sql/v2/catalog/datasources" });
}

export async function listSchemas(dsId: string): Promise<CatalogSchema[]> {
  return apiClient.get<CatalogSchema[]>({ url: `/sql/v2/catalog/${encodeURIComponent(dsId)}/schemas` });
}

export async function listTables(dsId: string, schema: string): Promise<CatalogTable[]> {
  return apiClient.get<CatalogTable[]>({
    url: `/sql/v2/catalog/${encodeURIComponent(dsId)}/schemas/${encodeURIComponent(schema)}/tables`,
  });
}

export async function listColumns(dsId: string, schemaTable: string): Promise<CatalogColumn[]> {
  return apiClient.get<CatalogColumn[]>({
    url: `/sql/v2/catalog/${encodeURIComponent(dsId)}/tables/${encodeURIComponent(schemaTable)}/columns`,
  });
}

export async function searchCatalog(
  dsId: string,
  q: string,
  limit = 50,
): Promise<CatalogSearchHit[]> {
  return apiClient.get<CatalogSearchHit[]>({
    url: `/sql/v2/catalog/${encodeURIComponent(dsId)}/search?q=${encodeURIComponent(q)}&limit=${limit}`,
  });
}
