import { useMemo } from "react";
import { useSearchParams } from "@/routes/hooks";
import {
	E2E_DATA_PRODUCT_JOURNEY,
	type DataProductJourneyStageKey,
	parseDataProductJourneyContext,
} from "./journeyContext";

export function useDataProductJourneyContext(stage: DataProductJourneyStageKey) {
	const searchParams = useSearchParams();
	const journey = searchParams.get("journey");
	const enabled = journey === E2E_DATA_PRODUCT_JOURNEY;

	return useMemo(
		() => parseDataProductJourneyContext(searchParams, stage),
		[searchParams, stage, enabled],
	);
}
