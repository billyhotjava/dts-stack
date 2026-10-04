import type { ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { Status } from "./PrototypePrimitives";

type DeliveryKey = "materialization" | "catalog" | "analysis";

export type DeliveryCellPresentation = {
	label: string;
	tone: "default" | "info" | "success" | "warning" | "danger";
	assetId: string | null;
};

export function resolveModelDeliveryCell(
	model: Pick<ModelSpecView, "revision">,
	status: ModelDeliveryStatus | null | undefined,
	key: DeliveryKey,
): DeliveryCellPresentation {
	if (
		!status ||
		!status.candidate ||
		status.modelRevision !== model.revision ||
		!status.candidate.matchesCurrentModel
	) {
		return { label: "暂无当前记录", tone: "default", assetId: null };
	}
	const step = status.steps.find((item) => item.key === key);
	if (!step || !step.matchesCurrentTarget) return { label: "暂无当前记录", tone: "default", assetId: null };
	const assetId = key === "catalog" ? step.resourceId : null;
	const labels: Record<DeliveryKey, Record<string, string>> = {
		materialization: {
			NOT_STARTED: "尚未构建",
			WAITING_INPUT: "等待构建",
			RUNNING: "构建中",
			SUCCEEDED: "已构建",
			FAILED: "构建失败",
			UNKNOWN: "暂无当前记录",
		},
		catalog: {
			NOT_STARTED: "尚未登记",
			WAITING_INPUT: "等待登记",
			RUNNING: "登记中",
			SUCCEEDED: "资产已登记",
			FAILED: "目录登记失败",
			UNKNOWN: "暂无当前记录",
		},
		analysis: {
			NOT_STARTED: "尚未开始",
			WAITING_INPUT: "等待分析准备",
			RUNNING: "分析准备中",
			SUCCEEDED: "分析准备完成",
			FAILED: "分析准备失败",
			UNKNOWN: "暂无当前记录",
		},
	};
	const tones: Record<string, DeliveryCellPresentation["tone"]> = {
		SUCCEEDED: "success",
		FAILED: "danger",
		RUNNING: "info",
		WAITING_INPUT: "warning",
		NOT_STARTED: "default",
		UNKNOWN: "default",
	};
	return { label: labels[key][step.state] || "暂无当前记录", tone: tones[step.state] || "default", assetId };
}

export function ModelDeliveryStatusCell({
	model,
	status,
	kind,
	loading,
	failed,
}: {
	model: Pick<ModelSpecView, "revision">;
	status: ModelDeliveryStatus | null | undefined;
	kind: DeliveryKey;
	loading?: boolean;
	failed?: boolean;
}) {
	const presentation = resolveModelDeliveryCell(model, status, kind);
	if (loading && !status) return <Status tone="info">读取交付状态中</Status>;
	if (failed && !status) return <Status tone="danger">交付状态读取失败</Status>;
	return <Status tone={presentation.tone}>{presentation.label}</Status>;
}
