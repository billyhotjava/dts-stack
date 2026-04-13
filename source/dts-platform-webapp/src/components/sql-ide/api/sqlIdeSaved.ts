import apiClient from "@/api/apiClient";

export interface SavedQueryItem {
  id: string;
  name: string;
  description: string | null;
  sqlText: string;
  datasourceId: string | null;
  datasourceName: string | null;
  createdBy: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  folder: string | null;
}

export interface SaveQueryPayload {
  name: string;
  sqlText: string;
  description?: string | null;
  datasourceId?: string | null;
  datasourceName?: string | null;
  folder?: string | null;
}

export async function listSavedQueries(): Promise<SavedQueryItem[]> {
  return apiClient.get<SavedQueryItem[]>({ url: "/api/sql/saved-queries" });
}

export async function createSavedQuery(payload: SaveQueryPayload): Promise<SavedQueryItem> {
  return apiClient.post<SavedQueryItem>({ url: "/api/sql/saved-queries", data: payload });
}

export async function deleteSavedQuery(id: string): Promise<void> {
  return apiClient.delete<void>({ url: `/api/sql/saved-queries/${id}` });
}
