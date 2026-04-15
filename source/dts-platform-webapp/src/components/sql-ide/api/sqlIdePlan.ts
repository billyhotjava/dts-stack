import apiClient from "@/api/apiClient";
import type { PlanNode } from "../result/planLayout";

export interface PlanResult {
  root: PlanNode;
  rawText: string;
  engine: string;
  explainTimeMs: number;
}

export interface ExplainPayload {
  sql: string;
  engine: string;
  datasourceId: string | null;
  catalog: string | null;
}

export async function postExplain(payload: ExplainPayload): Promise<PlanResult> {
  return apiClient.post<PlanResult>({ url: "/sql/v2/explain", data: payload });
}
