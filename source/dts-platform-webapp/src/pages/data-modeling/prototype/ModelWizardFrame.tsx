import { type ReactNode, useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import {
	MODEL_WIZARD_STEPS,
	type ModelDeliveryStatus,
	type ModelWizardStep,
	normalizeModelWizardStep,
} from "@/api/modelDeliveryStatusApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { UnsavedEditorHandle } from "@/pages/catalog/CatalogDatasetGovernanceSummaryEditor";
import { ModelAnalysisPreparationAction } from "./ModelAnalysisPreparationAction";
import { ModelCatalogDeliveryPanel } from "./ModelCatalogDeliveryPanel";
import { ModelPublishDialog } from "./ModelPublishDialog";
import { ModelTargetQualityPanel } from "./ModelTargetQualityPanel";
import { Button, RequestState, Status } from "./PrototypePrimitives";
import "./model-wizard.css";

const STEP_LABELS: Record<ModelWizardStep, string> = {
	definition: "模型设计",
	implementation: "实现配置",
	verification: "构建与检查",
	delivery: "发布与交付",
};
const RESULT_LABELS = {
	materialization: "物化",
	quality: "质量检查",
	publication: "发布",
	catalog: "资产登记",
	analysis: "分析准备",
};
const STATE_LABELS = {
	NOT_STARTED: "未开始",
	WAITING_INPUT: "待完善",
	RUNNING: "处理中",
	SUCCEEDED: "已完成",
	FAILED: "失败",
	NOT_APPLICABLE: "不适用",
	UNKNOWN: "待确认",
};

export function ModelWizardFrame({
	model,
	enabled,
	children,
	delivery,
	loading,
	failure,
	onRefresh,
	canMaintain,
	onBack,
	dirty,
	onAssetGuardChange,
	commandsBlocked,
}: {
	model: ModelSpecView | null;
	enabled: boolean;
	children: ReactNode;
	delivery: ModelDeliveryStatus | null;
	loading: boolean;
	failure: string;
	onRefresh: () => void;
	canMaintain: boolean;
	onBack: () => void;
	dirty: boolean;
	commandsBlocked: boolean;
	onAssetGuardChange: (handle: UnsavedEditorHandle | null) => void;
}) {
	const [params, setParams] = useSearchParams();
	const [qualityOpenRequest, setQualityOpenRequest] = useState(0);
	const requested = normalizeModelWizardStep(params.get("step"));
	const step = requested || delivery?.recommendedStep || "definition";
	const environment = params.get("environment") || delivery?.environment || "dev";
	const navigateStep = (next: ModelWizardStep) =>
		setParams((current) => {
			const changed = new URLSearchParams(current);
			changed.set("step", next);
			return changed;
		});
	useEffect(() => {
		if (!enabled || requested || !delivery) return;
		setParams(
			(current) => {
				const changed = new URLSearchParams(current);
				changed.set("step", delivery.recommendedStep);
				return changed;
			},
			{ replace: true },
		);
	}, [enabled, requested, delivery, setParams]);
	if (!enabled) return <>{children}</>;
	const page = delivery?.wizard.find((item) => item.key === step);
	const blocked = (!model && step !== "definition") || page?.canView === false;
	return (
		<section className="dmx-model-wizard" aria-label="建模向导">
			<nav className="dmx-wizard-navigation" aria-label="建模步骤">
				{MODEL_WIZARD_STEPS.map((key, index) => (
					<Button
						key={key}
						disabled={!model && key !== "definition"}
						className={step === key ? "active" : ""}
						onClick={() => navigateStep(key)}
						aria-current={step === key ? "step" : undefined}
					>
						{index + 1}. {STEP_LABELS[key]}
					</Button>
				))}
			</nav>
			{failure ? (
				<div className="dmx-inline-error" role="alert">
					{failure}
					<Button onClick={onRefresh}>重试</Button>
				</div>
			) : null}
			{blocked ? (
				<RequestState
					title="请先完成前置步骤"
					kind="empty"
					description={model ? "请先完成实现配置" : "请先保存模型设计"}
					onRetry={() => navigateStep(model ? "implementation" : "definition")}
				/>
			) : step === "definition" || step === "implementation" ? (
				children
			) : !model ? null : (
				<>
					<section className="dmx-wizard-results" aria-label="当前版本交付状态">
						{delivery?.steps.map((item) => (
							<div key={item.key}>
								<strong>{RESULT_LABELS[item.key]}</strong>
								<Status tone={item.state === "SUCCEEDED" ? "success" : item.state === "FAILED" ? "danger" : "neutral"}>
									{STATE_LABELS[item.state]}
								</Status>
								{item.message && item.state !== "SUCCEEDED" && item.state !== "NOT_APPLICABLE" ? (
									<small>{item.message}</small>
								) : null}
							</div>
						))}
					</section>
					{loading && !delivery ? (
						<RequestState kind="loading" title="正在读取交付状态" description="" />
					) : (
						<ModelPublishDialog
							key={`${model.id}:${model.revision}:${environment}:${step}`}
							models={[model]}
							step={step}
							deliveryStatus={delivery}
							initialEnvironment={environment}
							canMaintain={canMaintain && !commandsBlocked && Boolean(delivery)}
							canConfigureQuality={canMaintain && !dirty && Boolean(delivery?.candidate?.matchesCurrentModel)}
							onConfigureQuality={() => setQualityOpenRequest((value) => value + 1)}
							onChanged={onRefresh}
							onEnvironmentChange={(value) =>
								setParams((current) => {
									const changed = new URLSearchParams(current);
									changed.set("environment", value);
									changed.delete("candidateId");
									return changed;
								})
							}
							onNext={() => navigateStep("delivery")}
							onClose={step === "verification" ? () => navigateStep("implementation") : onBack}
						/>
					)}
					{step === "verification" && delivery?.candidate?.matchesCurrentModel ? (
						<ModelTargetQualityPanel
							delivery={delivery}
							openRequest={qualityOpenRequest}
							canMaintain={canMaintain && !dirty}
							onChanged={onRefresh}
							onNavigationGuardChange={onAssetGuardChange}
						/>
					) : null}
					{step === "delivery" && delivery ? (
						<ModelAnalysisPreparationAction
							delivery={delivery}
							canMaintain={canMaintain && !commandsBlocked}
							onChanged={onRefresh}
						/>
					) : null}
					{step === "delivery" ? (
						<ModelCatalogDeliveryPanel
							delivery={delivery}
							modelName={model.name}
							canMaintain={canMaintain}
							onSaved={onRefresh}
							onNavigationGuardChange={onAssetGuardChange}
						/>
					) : null}
				</>
			)}
		</section>
	);
}
