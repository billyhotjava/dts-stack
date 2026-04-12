import apiClient from "@/api/apiClient";

export type PingResponse = { status: "ok"; version: string };

export async function pingSqlIde(): Promise<PingResponse> {
  return apiClient.get<PingResponse>({ url: "/api/sql/v2/ping" });
}
