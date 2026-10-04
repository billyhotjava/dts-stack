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
import { buildModelDataManagementUrl, buildModelWorkbenchReturnUrl } from "./modelDataManagementLink";
import { Button, RequestState, Status } from "./PrototypePrimitives";
import "./model-wizard.css";

const STEP_LABELS: Record<ModelWizardStep, string> = {
	definition: "模型设计",
	implementation: "加工配置",
	verification: "构建",
	delivery: "完成",
};
// F15: the wizard reports only the build; registration, quality, publication and runs are handled in their own pages.
const RESULT_LABELS: Record<string, string> = { materialization: "构建" };
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
					description={model ? "请先完成加工配置" : "请先保存模型设计"}
					onRetry={() => navigateStep(model ? "implementation" : "definition")}
				/>
			) : step === "definition" || step === "implementation" ? (
				children
			) : !model ? null : (
				<>
					<section className="dmx-wizard-results" aria-label="当前版本构建结果">
						{delivery?.steps.filter(item => item.key === "materialization").map((item) => (
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
							<p>{completed ? `当前版本已构建到 ${delivery?.modelingResult?.targetRelation || "目标表"}。资产登记、质量与版本发布按需在各自功能中办理。` : "当前版本尚未完成构建。"}</p>
							<div className="dmx-dialog-actions">
								<Button primary onClick={onBack}>返回模型列表</Button>
								{completed ? (
									<Link className="dmx-wizard-completion__link" to={buildModelDataManagementUrl({ modelSpecId: model.id, environment, candidateId: delivery?.candidate?.id, returnTo: buildModelWorkbenchReturnUrl(model.id, environment, step) })}>查看数据资产</Link>
								) : null}
							</div>
						</section>
					) : loading && !delivery ? (
						<RequestState kind="loading" title="正在读取构建结果" description="" />
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
