import { useEffect, useState } from "react";
import { getModelBuildStatus, type ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

export function useModelDeliveryStatus(
	model: ModelSpecView | null,
	environment: string,
	candidateId: string,
	refreshKey: number,
) {
	const [result, setResult] = useState<{
		identity: string;
		data: ModelDeliveryStatus | null;
		failure: string;
		loading: boolean;
	} | null>(null);
	const identity = `${model?.id}:${model?.revision}:${model?.checksum}:${environment}:${candidateId}:${refreshKey}`;
	const modelId = model?.id,
		revision = model?.revision,
		checksum = model?.checksum,
		canonical = model?.compatibilityMode === "CANONICAL";
	useEffect(() => {
		if (!modelId || !canonical) return;
		let active = true;
		let timer: ReturnType<typeof setTimeout> | undefined;
		const load = async () => {
			setResult((previous) => ({
				identity,
				data: previous?.identity === identity ? previous.data : null,
				failure: "",
				loading: true,
			}));
			try {
				const data = await getModelBuildStatus(modelId, environment, candidateId || undefined);
				if (!active) return;
				if (data.modelSpecId !== modelId || data.modelRevision !== revision || data.modelChecksum !== checksum) {
					throw new Error("模型版本已变化，请刷新后继续");
				}
				setResult({ identity, data, failure: "", loading: false });
				if (data.steps.some((step) => step.state === "RUNNING")) timer = setTimeout(load, 5000);
			} catch (error) {
				if (active)
					setResult({
						identity,
						data: null,
						failure: normalizeModelingRequestFailure(error, "构建状态读取失败").message,
						loading: false,
					});
			}
		};
		void load();
		return () => {
			active = false;
			clearTimeout(timer);
		};
	}, [identity, environment, candidateId, modelId, revision, checksum, canonical]);
	return result?.identity === identity ? result : { data: null, failure: "", loading: Boolean(model) };
}
