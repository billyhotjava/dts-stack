import { useQuery } from "@tanstack/react-query";
import { type HistoryParams, type QueryHistoryItem, listHistory } from "../api/sqlIdeHistory";

const STALE_30S = 30 * 1000;

export function useHistoryQuery(params: HistoryParams = {}) {
  return useQuery<QueryHistoryItem[]>({
    queryKey: ["sqlide", "history", params],
    queryFn: () => listHistory(params),
    staleTime: STALE_30S,
  });
}
