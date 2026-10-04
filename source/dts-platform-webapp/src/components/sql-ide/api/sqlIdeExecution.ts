import apiClient from "@/api/apiClient";

// ---- Submit / Status / Cancel ----

export interface SubmitPayload {
  sqlText: string;
  /** Maps to `datasource` field on server (SqlSubmitRequest.datasource) */
  datasource: string | null;
  catalog: string | null;
  schema: string | null;
}

export interface SubmitResponse {
  executionId: string;
}

export type ExecutionStatusCode = "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "CANCELED";

export interface ExecutionStatus {
  executionId: string;
  status: ExecutionStatusCode;
  elapsedMs: number | null;
  rows: number | null;
  errorMessage: string | null;
}

export async function submitSql(payload: SubmitPayload): Promise<SubmitResponse> {
  return apiClient.post<SubmitResponse>({
    url: "/sql/submit",
    data: {
      sqlText: payload.sqlText,
      datasource: payload.datasource,
      catalog: payload.catalog,
      schema: payload.schema,
    },
  });
}

export async function getExecutionStatus(executionId: string): Promise<ExecutionStatus> {
  return apiClient.get<ExecutionStatus>({ url: `/sql/status/${executionId}` });
}

export async function cancelExecution(executionId: string): Promise<void> {
  await apiClient.post<unknown>({ url: `/sql/cancel/${executionId}`, data: {} });
}

// ---- Result pages (v2) ----

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
  return apiClient.get<ResultMeta>({ url: `/sql/v2/executions/${executionId}/meta` });
}

export async function getExecutionPage(
  executionId: string,
  page = 1,
  size = 200,
): Promise<ResultPage> {
  return apiClient.get<ResultPage>({
    url: `/sql/v2/executions/${executionId}/page?page=${page}&size=${size}`,
  });
}

// ---- Audit ----

/**
 * Reports a clipboard-copy event to the server audit log (Sprint-11 F4 T20).
 *
 * @param executionId - the execution whose result grid was copied
 * @param cellCount   - total number of cells copied (rows × visible columns)
 */
export async function postCopyAudit(executionId: string, cellCount: number): Promise<void> {
  await apiClient.post<void>({ url: "/sql/v2/audit/copy", data: { executionId, cellCount } });
}
