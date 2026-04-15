import apiClient from "@/api/apiClient";

export interface TempView {
  viewName: string;
  executionId: string;
  rowCount: number;
  expiresAt: string;
}

export async function createTempView(executionId: string): Promise<TempView> {
  return apiClient.post<TempView>({
    url: `/sql/v2/temp-views?executionId=${encodeURIComponent(executionId)}`,
  });
}

export async function deleteTempView(viewName: string): Promise<void> {
  await apiClient.delete<void>({ url: `/sql/v2/temp-views/${encodeURIComponent(viewName)}` });
}
