import apiClient from "@/api/apiClient";
import type { TabDto } from "../tabs/types";

export interface CreateTabPayload {
  title?: string;
  sqlText?: string;
  engine?: string;
  datasourceId?: string | null;
  schemaCtx?: string | null;
  cursorLine?: number | null;
  cursorCol?: number | null;
  selectionJson?: string | null;
  lastExecutionId?: string | null;
  sortOrder?: number;
  active?: boolean;
}

export interface PatchTabPayload extends CreateTabPayload {
  updatedAt?: string | null;  // optimistic lock
}

export interface UpsertTabPayload extends PatchTabPayload {
  id?: string;
}

export async function listTabs(): Promise<TabDto[]> {
  return apiClient.get<TabDto[]>({ url: "/sql/v2/tabs" });
}

export async function createTab(payload: CreateTabPayload): Promise<TabDto> {
  return apiClient.post<TabDto>({ url: "/sql/v2/tabs", data: payload });
}

export async function patchTab(id: string, payload: PatchTabPayload): Promise<TabDto> {
  return apiClient.request<TabDto>({ url: `/sql/v2/tabs/${id}`, method: "PATCH", data: payload });
}

export async function deleteTab(id: string): Promise<void> {
  await apiClient.delete<void>({ url: `/sql/v2/tabs/${id}` });
}

export async function batchUpsertTabs(payload: UpsertTabPayload[]): Promise<TabDto[]> {
  return apiClient.post<TabDto[]>({ url: "/sql/v2/tabs/batch", data: payload });
}
