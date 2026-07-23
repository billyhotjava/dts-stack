import { Alert, Button, Drawer, Form, Space } from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import {
	getWarehousePlanCategories,
	getWarehousePlanSources,
	listWarehousePlans,
	type WarehousePlanCategoryBindingView,
	type WarehousePlanHeader,
	type WarehousePlanSourceInventoryView,
} from "@/api/warehousePlanApi";
import { useUserRoles } from "@/store/userStore";
import {
	type ModelSpecSourceChoice,
	modelSpecSourceInventoryState,
	modelSpecSourcePermissionDenied,
	selectableModelSpecSources,
	withPinnedExistingSources,
} from "../modelSpecSourceSelection";
import { createDimensionSystemCode } from "../modelSpecSystemCode";
import type { CanonicalModelSpecView, CreateModelSpecCommand, ModelSpecType } from "../modelSpecV2Contract";
import { isModelSpecReferenceTargetAllowed, validateModelSpecCreate } from "../modelSpecV2Contract";
import {
	buildModelSpecCreateCommand,
	createEmptyModelSpecDraft,
	createModelSpecIdempotencyKey,
	type ModelSpecDraft,
	modelSpecErrorMessage,
	modelSpecIssueMessage,
} from "../modelSpecWorkbench";
import { hasWarehousePlanCreateAccess } from "../warehousePlanCreateFlow";
import { ModelSpecEditorFields, type ModelSpecSelectOption, modelTypeDefaultLayer } from "./ModelSpecEditorFields";
import { ModelSpecSourceInventoryModal } from "./ModelSpecSourceInventoryModal";

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
	if (field === "generationStrategy") return "generationStrategyType";
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
	const [sourceOptions, setSourceOptions] = useState<ModelSpecSourceChoice[]>([]);
	const [loadingPlans, setLoadingPlans] = useState(false);
	const [loadingDomains, setLoadingDomains] = useState(false);
	const [loadingSources, setLoadingSources] = useState(false);
	const [saving, setSaving] = useState(false);
	const [contextError, setContextError] = useState("");
	const [sourceError, setSourceError] = useState("");
	const [sourcePermissionDenied, setSourcePermissionDenied] = useState(false);
	const [sourceInventoryOpen, setSourceInventoryOpen] = useState(false);
	const [submitError, setSubmitError] = useState("");
	const idempotencyKeyRef = useRef("");
	const planRequestRef = useRef(0);
	const domainRequestRef = useRef(0);
	const sourceRequestRef = useRef(0);
	const selectedPlanId = Form.useWatch("planId", form) || "";
	const selectedModelType = Form.useWatch("modelType", form) || initialModelType;
	const selectedSources = Form.useWatch("sources", form) || [];
	const userRoles = useUserRoles();
	const roleAllowsPlanMaintenance = hasWarehousePlanCreateAccess(userRoles);

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

	const loadSources = useCallback(
		async (planId: string): Promise<WarehousePlanSourceInventoryView | null> => {
			const requestId = ++sourceRequestRef.current;
			if (!planId) {
				setSourceOptions([]);
				setSourceError("");
				setSourcePermissionDenied(false);
				setLoadingSources(false);
				return null;
			}
			setLoadingSources(true);
			setSourceError("");
			setSourcePermissionDenied(false);
			try {
				const inventory = await getWarehousePlanSources(planId);
				if (requestId !== sourceRequestRef.current) return null;
				const choices = selectableModelSpecSources(inventory.bindings);
				const currentSources = (form.getFieldValue("sources") as ModelSpecDraft["sources"] | undefined) || [];
				setSourceOptions(withPinnedExistingSources(choices, currentSources));
				const state = modelSpecSourceInventoryState(inventory.bindings);
				if (state === "EMPTY") {
					setSourceError(
						"当前计划尚未登记具体来源，不影响保存草稿。可锁定上游模型；或点击“在当前表单登记来源”，按已验证连接 → Schema → 具体表确认纳入；若没有可选表再执行元数据同步",
					);
				} else if (state === "FORBIDDEN") {
					setSourcePermissionDenied(true);
					setSourceError("当前账号无权读取计划来源，请联系计划负责人或管理员授权");
				} else if (state === "UNAVAILABLE") {
					setSourceError("当前计划已有来源，但尚未确认、已失效或版本需要刷新，请先完善来源盘点");
				}
				return inventory;
			} catch (error) {
				if (requestId !== sourceRequestRef.current) return null;
				const currentSources = (form.getFieldValue("sources") as ModelSpecDraft["sources"] | undefined) || [];
				setSourceOptions(withPinnedExistingSources([], currentSources));
				const denied = modelSpecSourcePermissionDenied(error);
				setSourcePermissionDenied(denied);
				setSourceError(
					denied
						? "当前账号无权读取计划来源，请联系计划负责人或管理员授权"
						: "规划来源加载失败，当前表单已保留；请稍后重试",
				);
				return null;
			} finally {
				if (requestId === sourceRequestRef.current) setLoadingSources(false);
			}
		},
		[form],
	);

	useEffect(() => {
		if (!open) {
			planRequestRef.current += 1;
			domainRequestRef.current += 1;
			sourceRequestRef.current += 1;
			setSourceInventoryOpen(false);
			return;
		}
		const requestId = ++planRequestRef.current;
		const draft = createEmptyModelSpecDraft(initialModelType, {
			planId: lockedPlanId,
			domainId: initialDomainId,
		});
		if (initialModelType === "DIMENSION") draft.dimensionCode = createDimensionSystemCode();
		form.resetFields();
		form.setFieldsValue(draft);
		idempotencyKeyRef.current = createModelSpecIdempotencyKey();
		setSubmitError("");
		setContextError("");
		setSourceError("");
		setSourcePermissionDenied(false);
		setSourceOptions([]);
		setLoadingSources(false);
		setLoadingPlans(true);
		void listWarehousePlans()
			.then((result) => {
				if (requestId !== planRequestRef.current) return;
				setPlans(Array.isArray(result) ? result : []);
			})
			.catch(() => {
				if (requestId !== planRequestRef.current) return;
				setPlans([]);
				setContextError("建设计划加载失败，当前表单已保留，请稍后重试");
			})
			.finally(() => {
				if (requestId === planRequestRef.current) setLoadingPlans(false);
			});
		const planId = lockedPlanId?.trim() || "";
		if (planId) {
			void loadDomains(planId, initialDomainId);
			void loadSources(planId);
		} else {
			setDomainOptions([]);
			setSourceOptions([]);
		}
		return () => {
			planRequestRef.current += 1;
			domainRequestRef.current += 1;
			sourceRequestRef.current += 1;
		};
	}, [form, initialDomainId, initialModelType, loadDomains, loadSources, lockedPlanId, open]);

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
			selectableModels.map((model) => {
				const allowed = isModelSpecReferenceTargetAllowed(
					{ modelType: selectedModelType, planId: selectedPlanId },
					model,
					"DEPENDENCY",
				);
				return {
					value: model.id,
					label: `${model.name} · ${model.modelType} · ${model.layer} · r${model.revision}${
						allowed ? "" : "（不符合当前模型依赖）"
					}`,
					disabled: !allowed,
					revision: model.revision,
				};
			}),
		[selectableModels, selectedModelType, selectedPlanId],
	);
	const dimensionOptions = useMemo<ModelSpecSelectOption[]>(
		() =>
			selectableModels
				.filter((model) =>
					isModelSpecReferenceTargetAllowed(
						{ modelType: selectedModelType, planId: selectedPlanId },
						model,
						"DIMENSION",
					),
				)
				.map((model) => ({
					value: model.id,
					label: `${model.name} · DIMENSION · DWD · r${model.revision}`,
					revision: model.revision,
				})),
		[selectableModels, selectedModelType, selectedPlanId],
	);

	const changePlan = (planId: string) => {
		domainRequestRef.current += 1;
		sourceRequestRef.current += 1;
		form.setFieldValue("domainId", "");
		form.setFieldValue("sources", []);
		setDomainOptions([]);
		setSourceOptions([]);
		setSourceError("");
		setSourcePermissionDenied(false);
		setSourceInventoryOpen(false);
		void loadDomains(planId);
		void loadSources(planId);
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
			sources: [],
			generationStrategyType: "",
			generationStrategyReference: "",
			dimensionCode: modelType === "DIMENSION" ? createDimensionSystemCode() : "",
			dimensionHierarchies: [],
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
		<>
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
								disabled={loadingPlans || loadingDomains || (loadingSources && selectedSources.length > 0)}
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
							sourceOptions={sourceOptions}
							planLoading={loadingPlans}
							domainLoading={loadingDomains}
							sourceLoading={loadingSources}
							sourceError={sourceError}
							sourcePermissionDenied={sourcePermissionDenied}
							lockPlan={Boolean(lockedPlanId)}
							lockModelType={lockModelType}
							onPlanChange={changePlan}
							onModelTypeChange={changeModelType}
							onReloadSources={() => void loadSources(selectedPlanId)}
							onManageSources={
								roleAllowsPlanMaintenance && selectedPlanId ? () => setSourceInventoryOpen(true) : undefined
							}
						/>
					</Form>
				</Space>
			</Drawer>
			<ModelSpecSourceInventoryModal
				open={sourceInventoryOpen}
				planId={selectedPlanId}
				roleAllowsPlanMaintenance={roleAllowsPlanMaintenance}
				onClose={() => setSourceInventoryOpen(false)}
				onSaved={async () => {
					return loadSources(selectedPlanId);
				}}
			/>
		</>
	);
}
