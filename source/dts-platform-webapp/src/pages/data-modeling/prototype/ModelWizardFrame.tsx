import { type ReactNode, useEffect } from "react";
import { Link, useSearchParams } from "react-router";
import {
	MODEL_WIZARD_STEPS,
	type ModelDeliveryStatus,
	type ModelWizardStep,
	normalizeModelWizardStep,
} from "@/api/modelDeliveryStatusApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { UnsavedEditorHandle } from "@/pages/catalog/CatalogDatasetGovernanceSummaryEditor";
import { ModelPublishDialog } from "./ModelPublishDialog";
import { Button, RequestState, Status } from "./PrototypePrimitives";
import "./model-wizard.css";

const STEP_LABELS: Record<ModelWizardStep, string> = {
	definition: "模型设计",
	implementation: "实现配置",
	verification: "物化",
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
	const requested = normalizeModelWizardStep(params.get("step"));
	const step = requested || delivery?.recommendedStep || "definition";
	const completed = delivery?.modelingResult?.state === "SUCCEEDED" && delivery.modelingResult.matchesCurrentTarget;
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
						{delivery?.steps.filter(item => step === "delivery" || item.key === "materialization").map((item) => (
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
					{step === "delivery" || completed ? (
						<section className="dmx-wizard-completion" aria-label={completed ? "建模完成" : "历史交付结果"}>
							<h3>{completed ? "建模已完成" : "历史交付结果"}</h3>
							<p>{completed ? `当前版本已物化到 ${delivery?.modelingResult?.targetRelation || "目标表"}。` : "此页保留交付结果回看，资产治理和发布请进入数据管理。"}</p>
							<div className="dmx-dialog-actions">
								<Button primary onClick={onBack}>返回模型列表</Button>
								<Link className="dmx-wizard-completion__link" to={`/catalog/search?view=table&modelSpecId=${encodeURIComponent(model.id)}&environment=${encodeURIComponent(environment)}${delivery?.candidate?.id ? `&candidateId=${encodeURIComponent(delivery.candidate.id)}` : ""}`}>去数据管理</Link>
							</div>
						</section>
					) : loading && !delivery ? (
						<RequestState kind="loading" title="正在读取物化结果" description="" />
					) : (
						<ModelPublishDialog
							key={`${model.id}:${model.revision}:${environment}:${step}`}
							models={[model]} step="verification" deliveryStatus={delivery} initialEnvironment={environment}
							canMaintain={canMaintain && !commandsBlocked && Boolean(delivery)} canConfigureQuality={false}
							onChanged={onRefresh}
							onEnvironmentChange={(value) => setParams((current) => {
								const changed = new URLSearchParams(current);
								changed.set("environment", value); changed.delete("candidateId"); return changed;
							})}
							onNext={onBack} onClose={() => navigateStep("implementation")}
						/>
					)}
				</>
			)}
		</section>
	);
}
