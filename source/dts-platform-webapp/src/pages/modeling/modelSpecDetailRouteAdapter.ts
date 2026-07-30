import { useCallback, useEffect, useMemo, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { useParams } from "@/routes/hooks";
import {
	type ModelSpecDetailStage,
	modelSpecCatalogPath,
	modelSpecDetailPath,
	resolveModelSpecDetailStage,
} from "./modelSpecDetailNavigation";
import type { ModelSpecView } from "./modelSpecV2Contract";

export type ModelSpecDetailPageProps = {
	embedded?: boolean;
	modelSpecIdOverride?: string;
	onBack?: () => void;
	onStageChange?: (stage: ModelSpecDetailStage) => void;
	onResolvedContext?: (context: { modelSpecId: string; planId: string }) => void;
};

export const useModelSpecDetailRouteAdapter = ({
	modelSpecIdOverride,
	onBack,
	onStageChange,
	onResolvedContext,
}: ModelSpecDetailPageProps) => {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const params = useParams();
	const routeModelSpecId = String(params.modelSpecId || "").trim();
	const modelSpecId = String(modelSpecIdOverride || routeModelSpecId).trim();
	const activeStage = useMemo(() => resolveModelSpecDetailStage(searchParams), [searchParams]);
	const onResolvedContextRef = useRef(onResolvedContext);
	useEffect(() => {
		onResolvedContextRef.current = onResolvedContext;
	}, [onResolvedContext]);
	const notifyResolvedContext = useCallback(
		(context: { modelSpecId: string; planId: string }) => onResolvedContextRef.current?.(context),
		[],
	);
	const returnToCatalog = useCallback(
		(model?: ModelSpecView | null) => {
			if (onBack) {
				onBack();
				return;
			}
			navigate(model ? modelSpecCatalogPath(model.modelType, model.planId, model.domainId) : "/modeling/models");
		},
		[navigate, onBack],
	);
	const changeStage = useCallback(
		(model: ModelSpecView | null, stage: ModelSpecDetailStage) => {
			if (!model) return;
			if (onStageChange) {
				onStageChange(stage);
				return;
			}
			navigate(modelSpecDetailPath(model.id, stage, model.planId), { replace: true });
		},
		[navigate, onStageChange],
	);
	return { activeStage, changeStage, modelSpecId, navigate, notifyResolvedContext, returnToCatalog };
};
