import { useEffect, useMemo } from "react";
import { useSearchParams } from "@/routes/hooks";
import {
	E2E_DATA_PRODUCT_JOURNEY,
	type DataProductJourneyStageKey,
	parseDataProductJourneyContext,
} from "./journeyContext";
import { persistJourneyContextSnapshot } from "./journeySnapshot";

export function useDataProductJourneyContext(stage: DataProductJourneyStageKey) {
	const searchParams = useSearchParams();
	const journey = searchParams.get("journey");
	const enabled = journey === E2E_DATA_PRODUCT_JOURNEY;

	const context = useMemo(
		() => parseDataProductJourneyContext(searchParams, stage),
		[searchParams, stage, enabled],
	);

	useEffect(() => {
		// 旅程内的上下文变化自动落快照；persist 内部按"已存内容是否变化"去重，
		// 额外的 effect 触发不会产生重复写入。
		persistJourneyContextSnapshot(context);
	}, [context]);

	return context;
}
