import { useState } from "react";
import type { ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import { getModelServingSyncStatus, retryModelServingSync } from "@/api/modelSpecApi";
import { Button } from "./PrototypePrimitives";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

export function ModelAnalysisPreparationAction({
	delivery,
	canMaintain,
	onChanged,
}: {
	delivery: ModelDeliveryStatus;
	canMaintain: boolean;
	onChanged: () => void;
}) {
	const [busy, setBusy] = useState(false);
	const [failure, setFailure] = useState("");
	const analysis = delivery.steps.find((item) => item.key === "analysis");
	const retry = async () => {
		if (busy || !canMaintain) return;
		setBusy(true);
		setFailure("");
		try {
			const current = await getModelServingSyncStatus(delivery.modelSpecId);
			const published = current.latestPublishedRef;
			if (
				current.modelSpecId !== delivery.modelSpecId ||
				published?.modelSpecId !== delivery.modelSpecId ||
				published?.modelRevision !== delivery.modelRevision ||
				published?.modelChecksum !== delivery.modelChecksum ||
				(delivery.candidate && published?.candidateId !== delivery.candidate.id)
			) {
				throw new Error("发布版本已变化，请刷新后重试");
			}
			await retryModelServingSync(current);
			onChanged();
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "分析准备重试失败").message);
		} finally {
			setBusy(false);
		}
	};
	if (analysis?.state !== "FAILED" || !analysis.matchesCurrentTarget) return null;
	return (
		<div>
			{failure ? <div role="alert">{failure}</div> : null}
			<Button disabled={!canMaintain || busy} onClick={() => void retry()}>
				{busy ? "处理中…" : "重试分析准备"}
			</Button>
		</div>
	);
}
