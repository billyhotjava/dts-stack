import apiClient from "@/api/apiClient";

export type HistoryStatus = "SUCCESS" | "FAILED" | "CANCELED" | "RUNNING";

export interface QueryHistoryItem {
  id: string;
  sqlText: string | null;
  engine: string | null;
  connection: string | null;
  status: HistoryStatus | null;
  startedAt: string | null;
  finishedAt: string | null;
  rowCount: number | null;
  elapsedMs: number | null;
  createdBy: string | null;
}

export interface HistoryParams {
  status?: HistoryStatus | "";
  connection?: string;
  q?: string;
  limit?: number;
}

export async function listHistory(params: HistoryParams = {}): Promise<QueryHistoryItem[]> {
  const searchParams = new URLSearchParams();
  if (params.status) searchParams.set("status", params.status);
  if (params.connection) searchParams.set("connection", params.connection);
  if (params.q) searchParams.set("q", params.q);
  if (params.limit != null) searchParams.set("limit", String(params.limit));

  const qs = searchParams.toString();
  return apiClient.get<QueryHistoryItem[]>({ url: `/sql/v2/history${qs ? `?${qs}` : ""}` });
}
