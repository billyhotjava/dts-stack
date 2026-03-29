import { useCallback, useMemo, useState } from "react";

export type TimeGrain = "day" | "week" | "month" | "quarter" | "year";

export const TIME_GRAINS: TimeGrain[] = ["day", "week", "month", "quarter", "year"];

export interface TimeGranularityState {
	hasTimeGranularity: boolean;
	currentGrain: TimeGrain;
	setGrain: (grain: TimeGrain) => void;
	applyGrainToQuery: (datasetQuery: Record<string, unknown>) => Record<string, unknown>;
}

/**
 * Detects whether a card's dataset_query contains a temporal-unit breakout
 * and allows switching between day/week/month/quarter/year.
 */
export function useTimeGranularity(datasetQuery: unknown): TimeGranularityState {
	const [overrideGrain, setOverrideGrain] = useState<TimeGrain | null>(null);

	const detectedGrain = useMemo<TimeGrain | null>(() => {
		if (!datasetQuery || typeof datasetQuery !== "object") return null;
		const dq = datasetQuery as Record<string, unknown>;
		const query = dq.query as Record<string, unknown> | undefined;
		if (!query) return null;

		const breakout = query.breakout as unknown[] | undefined;
		if (!Array.isArray(breakout)) return null;

		for (const b of breakout) {
			if (!Array.isArray(b)) continue;
			// MBQL temporal-unit breakout: ["field", fieldRef, {"temporal-unit": "month"}]
			if (b.length >= 3 && typeof b[2] === "object" && b[2] !== null) {
				const opts = b[2] as Record<string, unknown>;
				const unit = opts["temporal-unit"];
				if (typeof unit === "string" && TIME_GRAINS.includes(unit as TimeGrain)) {
					return unit as TimeGrain;
				}
			}
		}
		return null;
	}, [datasetQuery]);

	const hasTimeGranularity = detectedGrain !== null;
	const currentGrain: TimeGrain = overrideGrain ?? detectedGrain ?? "month";

	const setGrain = useCallback((grain: TimeGrain) => {
		setOverrideGrain(grain);
	}, []);

	const applyGrainToQuery = useCallback(
		(dq: Record<string, unknown>): Record<string, unknown> => {
			if (!hasTimeGranularity || !overrideGrain) return dq;

			const query = dq.query as Record<string, unknown> | undefined;
			if (!query) return dq;

			const breakout = query.breakout as unknown[] | undefined;
			if (!Array.isArray(breakout)) return dq;

			const newBreakout = breakout.map((b) => {
				if (!Array.isArray(b) || b.length < 3) return b;
				if (typeof b[2] === "object" && b[2] !== null) {
					const opts = b[2] as Record<string, unknown>;
					if (typeof opts["temporal-unit"] === "string") {
						return [b[0], b[1], { ...opts, "temporal-unit": overrideGrain }];
					}
				}
				return b;
			});

			return {
				...dq,
				query: { ...query, breakout: newBreakout },
			};
		},
		[hasTimeGranularity, overrideGrain],
	);

	return { hasTimeGranularity, currentGrain, setGrain, applyGrainToQuery };
}
