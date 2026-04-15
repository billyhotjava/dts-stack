import apiClient from "@/api/apiClient";

export interface QueryLog {
  executionId: string;
  originalSql: string;
  rewrittenSql: string;
  status: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  rowCount: number | null;
  elapsedMs: number | null;
  errorMessage: string | null;
  bytesProcessed: number | null;
}

export async function getExecutionLog(executionId: string): Promise<QueryLog> {
  return apiClient.get<QueryLog>({ url: `/sql/v2/executions/${executionId}/log` });
}
