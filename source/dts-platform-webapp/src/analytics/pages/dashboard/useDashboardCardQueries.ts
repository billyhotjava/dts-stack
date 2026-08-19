import { useEffect, useRef, useState } from "react";
import { analyticsApi, type DashboardCard, type DashboardQueryResponse } from "../../api/analyticsApi";
import { mapWithConcurrency } from "./dashboardInteractionModel";

export type DashboardCardLoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

type Props = {
	dashcards: DashboardCard[];
	dashboardId: string | null;
	queryParameters: unknown[];
	buildCrossFilterParams: (dashcardId: number, baseParams: unknown[]) => unknown[];
};

export function useDashboardCardQueries({
	dashcards,
	dashboardId,
	queryParameters,
	buildCrossFilterParams,
}: Props): Record<number, DashboardCardLoadState<DashboardQueryResponse>> {
	const [results, setResults] = useState<Record<number, DashboardCardLoadState<DashboardQueryResponse>>>({});
	const generation = useRef(0);

	useEffect(() => {
		const currentGeneration = ++generation.current;
		if (dashcards.length === 0) {
			setResults({});
			return;
		}
		const next: Record<number, DashboardCardLoadState<DashboardQueryResponse>> = {};
		for (const dashcard of dashcards) next[dashcard.id] = { state: "loading" };
		setResults({ ...next });

		void mapWithConcurrency(dashcards, 4, async (dashcard) => {
			const embedded = dashcard.card as { id?: number } | null | undefined;
			const cardId = dashcard.card_id ?? embedded?.id;
			if (!cardId) return [dashcard.id, { state: "error", error: new Error("Missing card_id") }] as const;
			const parameters = buildCrossFilterParams(dashcard.id, queryParameters);
			try {
				const value = dashboardId && dashcard.id > 0
					? await analyticsApi.queryDashcard(dashboardId, dashcard.id, cardId, { parameters })
					: await analyticsApi.queryCard(cardId, { parameters });
				return [dashcard.id, { state: "loaded", value }] as const;
			} catch (error) {
				return [dashcard.id, { state: "error", error }] as const;
			}
		}).then((queried) => {
			if (generation.current !== currentGeneration) return;
			for (const [dashcardId, state] of queried) next[dashcardId] = state;
			setResults({ ...next });
		});
	}, [buildCrossFilterParams, dashcards, dashboardId, queryParameters]);

	return results;
}
