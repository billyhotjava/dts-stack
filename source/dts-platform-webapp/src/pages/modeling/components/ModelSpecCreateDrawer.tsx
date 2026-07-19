import { Alert, Button, Drawer, Form, Space } from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import {
	getWarehousePlanCategories,
	listWarehousePlans,
	type WarehousePlanCategoryBindingView,
	type WarehousePlanHeader,
} from "@/api/warehousePlanApi";
import type { CanonicalModelSpecView, CreateModelSpecCommand, ModelSpecType } from "../modelSpecV2Contract";
import { validateModelSpecCreate } from "../modelSpecV2Contract";
import {
	buildModelSpecCreateCommand,
	createEmptyModelSpecDraft,
	createModelSpecIdempotencyKey,
	type ModelSpecDraft,
	modelSpecErrorMessage,
	modelSpecIssueMessage,
} from "../modelSpecWorkbench";
import { ModelSpecEditorFields, type ModelSpecSelectOption, modelTypeDefaultLayer } from "./ModelSpecEditorFields";

type Props = {
	open: boolean;
	initialModelType: ModelSpecType;
	lockModelType?: boolean;
	lockedPlanId?: string;
	initialDomainId?: string;
	availableModels: CanonicalModelSpecView[];
	createCommand: (command: CreateModelSpecCommand) => Promise<CanonicalModelSpecView>;
	onClose: () => void;
	onCreated: (model: CanonicalModelSpecView) => void;
};

const emptySource = () => ({
	kind: "TABLE" as const,
	ref: "",
	layer: "ODS" as const,
	role: "PRIMARY" as const,
	sourceBindingId: "",
	resolvedVersion: "",
});

const confirmedCategoryOptions = (bindings: WarehousePlanCategoryBindingView[]): ModelSpecSelectOption[] =>
	bindings
		.filter((binding) => binding.confirmationStatus === "CONFIRMED" && binding.resolutionStatus === "AVAILABLE")
		.map((binding) => ({
			value: binding.domainId,
			label: binding.name
				? binding.code
					? `${binding.name}（${binding.code}）`
					: binding.name
				: binding.code || binding.domainId,
		}));

const issueField = (field: string): keyof ModelSpecDraft => {
	if (field === "grain") return "grainStatement";
	if (field === "fields") return "grainKeysText";
	if (field === "sourceRefs") return "sources";
	if (field === "dependsOn") return "upstreamIds";
	if (field === "dimensionRefs") return "dimensionRefIds";
	if (field === "timeSemantics") return "timeSemanticsType";
	return field as keyof ModelSpecDraft;
};

export function ModelSpecCreateDrawer({
	open,
	initialModelType,
	lockModelType = false,
	lockedPlanId,
	initialDomainId,
	availableModels,
	createCommand,
	onClose,
	onCreated,
}: Props) {
	const [form] = Form.useForm<ModelSpecDraft>();
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [domainOptions, setDomainOptions] = useState<ModelSpecSelectOption[]>([]);
	const [loadingPlans, setLoadingPlans] = useState(false);
	const [loadingDomains, setLoadingDomains] = useState(false);
	const [saving, setSaving] = useState(false);
	const [contextError, setContextError] = useState("");
	const [submitError, setSubmitError] = useState("");
	const idempotencyKeyRef = useRef("");
	const domainRequestRef = useRef(0);
	const selectedPlanId = Form.useWatch("planId", form) || "";

	const loadDomains = useCallback(
		async (planId: string, keepDomainId?: string) => {
			const requestId = ++domainRequestRef.current;
			if (!planId) {
				setDomainOptions([]);
				return;
			}
			setLoadingDomains(true);
			setContextError("");
			try {
				const result = await getWarehousePlanCategories(planId);
				if (requestId !== domainRequestRef.current) return;
				const options = confirmedCategoryOptions(result.value.domainBindings);
				setDomainOptions(options);
				const currentDomainId = keepDomainId || String(form.getFieldValue("domainId") || "");
				if (currentDomainId && !options.some((option) => option.value === currentDomainId)) {
					form.setFieldValue("domainId", "");
				}
				if (!form.getFieldValue("domainId") && options.length === 1) {
					form.setFieldValue("domainId", options[0].value);
				}
				if (options.length === 0) {
					setContextError("当前计划还没有可用于建模的已确认业务分类，请先完善规划基线");
				}
			} catch {
				if (requestId !== domainRequestRef.current) return;
				setDomainOptions([]);
				form.setFieldValue("domainId", "");
				setContextError("业务分类加载失败，当前表单已保留，请稍后重试");
			} finally {
				if (requestId === domainRequestRef.current) setLoadingDomains(false);
			}
		},
		[form],
	);

	useEffect(() => {
		if (!open) return;
		const draft = createEmptyModelSpecDraft(initialModelType, {
			planId: lockedPlanId,
			domainId: initialDomainId,
		});
		if (initialModelType === "FACT") draft.sources = [emptySource()];
		form.resetFields();
		form.setFieldsValue(draft);
		idempotencyKeyRef.current = createModelSpecIdempotencyKey();
		setSubmitError("");
		setContextError("");
		setLoadingPlans(true);
		void listWarehousePlans()
			.then((result) => setPlans(Array.isArray(result) ? result : []))
			.catch(() => {
				setPlans([]);
				setContextError("建设计划加载失败，当前表单已保留，请稍后重试");
			})
			.finally(() => setLoadingPlans(false));
		const planId = lockedPlanId?.trim() || "";
		if (planId) void loadDomains(planId, initialDomainId);
		else setDomainOptions([]);
		return () => {
			domainRequestRef.current += 1;
		};
	}, [form, initialDomainId, initialModelType, loadDomains, lockedPlanId, open]);

	const planOptions = useMemo<ModelSpecSelectOption[]>(() => {
		const options = plans.map((plan) => ({ value: plan.id, label: `${plan.name}（${plan.code}）` }));
		if (lockedPlanId && !options.some((option) => option.value === lockedPlanId)) {
			return [{ value: lockedPlanId, label: `当前计划（${lockedPlanId}）` }, ...options];
		}
		return options;
	}, [lockedPlanId, plans]);

	const selectableModels = useMemo(
		() => availableModels.filter((model) => model.planId === selectedPlanId || model.status === "PUBLISHED"),
		[availableModels, selectedPlanId],
	);
	const upstreamOptions = useMemo<ModelSpecSelectOption[]>(
		() =>
			selectableModels.map((model) => ({
				value: model.id,
				label: `${model.name} · ${model.modelType} · r${model.revision}`,
			})),
		[selectableModels],
	);
	const dimensionOptions = useMemo<ModelSpecSelectOption[]>(
		() =>
			selectableModels
				.filter((model) => model.modelType === "DIMENSION")
				.map((model) => ({ value: model.id, label: `${model.name} · r${model.revision}` })),
		[selectableModels],
	);

	const changePlan = (planId: string) => {
		form.setFieldValue("domainId", "");
		void loadDomains(planId);
	};

	const changeModelType = (modelType: ModelSpecType) => {
		form.setFieldsValue({
			modelType,
			layer: modelTypeDefaultLayer(modelType),
			factShape: undefined,
			timeSemanticsType: undefined,
			timeFieldsText: "",
			businessActivityRef: "",
			upstreamIds: [],
			dimensionRefIds: [],
			consumptionScenario: "",
			sources: modelType === "FACT" ? [emptySource()] : [],
		});
	};

	const submit = async () => {
		setSubmitError("");
		try {
			await form.validateFields();
			const values = form.getFieldsValue(true);
			const command = buildModelSpecCreateCommand(values, selectableModels, idempotencyKeyRef.current);
			const issues = validateModelSpecCreate(command);
			if (issues.length > 0) {
				form.setFields(
					issues.map((issue) => ({
						name: issueField(issue.field),
						errors: [modelSpecIssueMessage(issue.code)],
					})),
				);
				setSubmitError("请补齐标红字段后再保存");
				return;
			}
			setSaving(true);
			const created = await createCommand(command);
			toast.success(`${created.name} 已保存为草稿`);
			onCreated(created);
			onClose();
		} catch (error) {
			if (error && typeof error === "object" && "errorFields" in error) return;
			setSubmitError(modelSpecErrorMessage(error));
		} finally {
			setSaving(false);
		}
	};

	return (
		<Drawer
			title={initialModelType === "DIMENSION" && lockModelType ? "登记维度" : "新建模型"}
			aria-label={initialModelType === "DIMENSION" && lockModelType ? "登记维度" : "新建模型"}
			open={open}
			onClose={onClose}
			width={"min(760px, 100vw)"}
			maskClosable={!saving}
			footer={
				<div className="flex justify-end">
					<Space>
						<Button onClick={onClose} disabled={saving}>
							取消
						</Button>
						<Button
							type="primary"
							loading={saving}
							disabled={loadingPlans || loadingDomains}
							onClick={() => void submit()}
						>
							保存草稿
						</Button>
					</Space>
				</div>
			}
		>
			<Space direction="vertical" size={12} className="w-full">
				{contextError ? <Alert type="warning" showIcon message={contextError} /> : null}
				{submitError ? <Alert type="error" showIcon message={submitError} /> : null}
				<Form form={form} layout="vertical" requiredMark={false} disabled={saving}>
					<ModelSpecEditorFields
						form={form}
						planOptions={planOptions}
						domainOptions={domainOptions}
						upstreamOptions={upstreamOptions}
						dimensionOptions={dimensionOptions}
						planLoading={loadingPlans}
						domainLoading={loadingDomains}
						lockPlan={Boolean(lockedPlanId)}
						lockModelType={lockModelType}
						onPlanChange={changePlan}
						onModelTypeChange={changeModelType}
					/>
				</Form>
			</Space>
		</Drawer>
	);
}
