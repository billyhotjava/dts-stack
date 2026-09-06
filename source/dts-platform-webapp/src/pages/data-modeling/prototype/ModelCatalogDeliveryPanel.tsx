import { useSearchParams } from "react-router";
import type { ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import {
	CatalogDatasetGovernanceSummaryEditor,
	type UnsavedEditorHandle,
} from "@/pages/catalog/CatalogDatasetGovernanceSummaryEditor";
import { Button } from "./PrototypePrimitives";
const labels: Record<string, string> = {
	NOT_STARTED: "未开始",
	WAITING_INPUT: "待完善",
	RUNNING: "处理中",
	SUCCEEDED: "已完成",
	FAILED: "失败",
	NOT_APPLICABLE: "不适用",
	UNKNOWN: "待确认",
};
export function currentCatalogOutputs(delivery: Pick<ModelDeliveryStatus, "steps"> | null) {
	const catalog = delivery?.steps.find((item) => item.key === "catalog");
	if (!catalog?.matchesCurrentTarget) return [];
	const outputs = catalog.outputs.filter((item) => item.matchesCurrentTarget);
	return catalog.outputs.length > 0
		? outputs
		: catalog.resourceId
			? [
					{
						resourceId: catalog.resourceId,
						state: catalog.state,
						reasonCode: catalog.reasonCode,
						message: catalog.message,
						matchesCurrentTarget: true,
						updatedAt: catalog.updatedAt,
					},
				]
			: [];
}
export function ModelCatalogDeliveryPanel({
	delivery,
	modelName,
	canMaintain,
	onSaved,
	onNavigationGuardChange,
}: {
	delivery: Pick<ModelDeliveryStatus, "steps"> | null;
	modelName: string;
	canMaintain: boolean;
	onSaved: () => void;
	onNavigationGuardChange: (handle: UnsavedEditorHandle | null) => void;
}) {
	const [params, setParams] = useSearchParams();
	const outputs = currentCatalogOutputs(delivery);
	const eligible = outputs.filter((item) => item.state === "SUCCEEDED" && item.resourceId);
	const selected = eligible.find((item) => item.resourceId === params.get("catalogDatasetId")) ?? eligible[0];
	return (
		<>
			<div aria-label="资产登记结果">
				{outputs.map((item, index) => (
					<div key={item.resourceId ?? `output-${index}`}>
						<span>
							{modelName || "当前模型"} {outputs.length > 1 ? `输出${index + 1}` : ""} · {labels[item.state]}
						</span>
						{item.state === "SUCCEEDED" && item.resourceId ? (
							<Button
								onClick={() =>
									setParams((current) => {
										const changed = new URLSearchParams(current);
										changed.set("catalogDatasetId", item.resourceId!);
										return changed;
									})
								}
							>
								维护资产
							</Button>
						) : null}
					</div>
				))}
			</div>
			{selected?.resourceId ? (
				<CatalogDatasetGovernanceSummaryEditor
					datasetId={selected.resourceId}
					canMaintain={canMaintain}
					onSaved={onSaved}
					onNavigationGuardChange={onNavigationGuardChange}
				/>
			) : null}
		</>
	);
}
