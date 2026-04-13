import apiClient from "@/api/apiClient";

export interface ColumnMeta {
  name: string;
  dataType: string;
  nullable: boolean;
}

export interface ResultMeta {
  executionId: string;
  status: string | null;
  columns: ColumnMeta[];
  totalRows: number;
  truncated: boolean;
  elapsedMs: number | null;
  bytesProcessed: number | null;
}

export interface ResultPage {
  rows: Array<Record<string, unknown>>;
  columns: ColumnMeta[];
  page: number;
  pageSize: number;
  total: number;
  truncated: boolean;
}

export async function getExecutionMeta(executionId: string): Promise<ResultMeta> {
  return apiClient.get<ResultMeta>({ url: `/api/sql/v2/executions/${executionId}/meta` });
}

export async function getExecutionPage(
  executionId: string,
  page = 1,
  size = 200,
): Promise<ResultPage> {
  return apiClient.get<ResultPage>({
    url: `/api/sql/v2/executions/${executionId}/page?page=${page}&size=${size}`,
  });
}
