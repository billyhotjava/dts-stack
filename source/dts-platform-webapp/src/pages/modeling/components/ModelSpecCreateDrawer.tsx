import { Alert, Button, Drawer, Form, Input, Select, Space } from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { listDimensionDefinitions } from "@/api/dimensionDefinitionApi";
import {
	getWarehousePlanCategories,
	listWarehousePlans,
	type WarehousePlanCategoryBindingView,
	type WarehousePlanHeader,
} from "@/api/warehousePlanApi";
import type { DimensionDefinitionView } from "../dimensionDefinitionContract";
import type { CreateModelSpecCommand, ModelSpecType } from "../modelSpecV2Contract";
import { validateModelSpecCreate } from "../modelSpecV2Contract";
import {
	buildModelSpecCreateCommand,
	createEmptyModelSpecDraft,
	createModelSpecIdempotencyKey,
	type ModelSpecDraft,
	modelSpecErrorMessage,
	modelSpecIssueMessage,
} from "../modelSpecWorkbench";

type Props = {
	open: boolean;
	initialModelType: ModelSpecType;
	lockModelType?: boolean;
	lockedPlanId?: string;
	initialDomainId?: string;
	initialDimensionDefinitionId?: string;
	initialDimensionDefinitionRevision?: number;
	createCommand: (command: CreateModelSpecCommand) => Promise<{ id: string; name: string }>;
	onClose: () => void;
	onCreated: (model: { id: string; name: string }) => void;
};

type SelectOption = { value: string; label: string };

const modelTypeOptions: SelectOption[] = [
	{ value: "DIMENSION", label: "维度表" },
	{ value: "FACT", label: "明细表" },
	{ value: "SUMMARY", label: "汇总表" },
	{ value: "APPLICATION", label: "应用表" },
];

const confirmedCategoryOptions = (bindings: WarehousePlanCategoryBindingView[]): SelectOption[] =>
	bindings
		.filter((binding) => binding.confirmationStatus === "CONFIRMED" && binding.resolutionStatus === "AVAILABLE")
		.map((binding) => ({
			value: binding.domainId,
			label: binding.name ? (binding.code ? `${binding.name}（${binding.code}）` : binding.name) : binding.code || binding.domainId,
		}));

const issueField = (field: string): keyof ModelSpecDraft =>
	field === "dimensionDefinitionRef" ? "dimensionDefinitionRef" : (field as keyof ModelSpecDraft);

export function ModelSpecCreateDrawer({
	open,
	initialModelType,
	lockModelType = false,
	lockedPlanId,
	initialDomainId,
	initialDimensionDefinitionId,
	initialDimensionDefinitionRevision,
	createCommand,
	onClose,
	onCreated,
}: Props) {
	const [form] = Form.useForm<ModelSpecDraft>();
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [domainOptions, setDomainOptions] = useState<SelectOption[]>([]);
	const [dimensionDefinitions, setDimensionDefinitions] = useState<DimensionDefinitionView[]>([]);
	const [loadingPlans, setLoadingPlans] = useState(false);
	const [loadingDomains, setLoadingDomains] = useState(false);
	const [loadingDefinitions, setLoadingDefinitions] = useState(false);
	const [contextError, setContextError] = useState("");
	const [definitionError, setDefinitionError] = useState("");
	const [submitError, setSubmitError] = useState("");
	const [saving, setSaving] = useState(false);
	const idempotencyKeyRef = useRef("");
	const planRequestRef = useRef(0);
	const domainRequestRef = useRef(0);
	const definitionRequestRef = useRef(0);
	const selectedPlanId = Form.useWatch("planId", form) || "";
	const selectedDomainId = Form.useWatch("domainId", form) || "";
	const selectedModelType = Form.useWatch("modelType", form) || initialModelType;
	const selectedDimensionDefinitionRef = Form.useWatch("dimensionDefinitionRef", form);

	const loadDomains = useCallback(
		async (planId: string, preferredDomainId?: string) => {
			const requestId = ++domainRequestRef.current;
			if (!planId) {
				setDomainOptions([]);
				setContextError("请先选择建设计划。创建模型需要明确建设计划，系统不会自动猜测。");
				return;
			}
			setLoadingDomains(true);
			setContextError("");
			try {
				const result = await getWarehousePlanCategories(planId);
				if (requestId !== domainRequestRef.current) return;
				const options = confirmedCategoryOptions(result.value.domainBindings);
				setDomainOptions(options);
				const currentDomainId = preferredDomainId || String(form.getFieldValue("domainId") || "");
				if (currentDomainId && !options.some((option) => option.value === currentDomainId)) {
					form.setFieldValue("domainId", "");
					setContextError("当前业务分类不属于所选建设计划。请重新选择已确认的业务分类后再创建。");
				} else if (!currentDomainId) {
					setContextError("请从当前建设计划中选择业务分类。系统不会自动猜测模型归属。");
				}
				if (options.length === 0) {
					setContextError("当前建设计划还没有可用于建模的已确认业务分类。请先在规划基线中确认业务分类后返回。");
				}
			} catch {
				if (requestId !== domainRequestRef.current) return;
				setDomainOptions([]);
				form.setFieldValue("domainId", "");
				setContextError("业务分类加载失败。请稍后重试，或返回建设计划确认分类后再创建。");
			} finally {
				if (requestId === domainRequestRef.current) setLoadingDomains(false);
			}
		},
		[form],
	);

	const loadDimensionDefinitions = useCallback(
		async (domainId: string) => {
			const requestId = ++definitionRequestRef.current;
			if (!domainId || selectedModelType !== "DIMENSION") {
				setDimensionDefinitions([]);
				setDefinitionError("");
				return;
			}
			setLoadingDefinitions(true);
			setDefinitionError("");
			try {
				const result = await listDimensionDefinitions({ domainId, status: "CURRENT" });
				if (requestId !== definitionRequestRef.current) return;
				const definitions = Array.isArray(result) ? result : [];
				setDimensionDefinitions(definitions);
				const requested = initialDimensionDefinitionId
					? definitions.find(
						(definition) =>
							definition.id === initialDimensionDefinitionId &&
							definition.revision === initialDimensionDefinitionRevision,
					)
					: undefined;
				if (requested) {
					form.setFieldValue("dimensionDefinitionRef", {
						dimensionDefinitionId: requested.id,
						revision: requested.revision,
					});
				} else if (initialDimensionDefinitionId) {
					form.setFieldValue("dimensionDefinitionRef", undefined);
					setDefinitionError("请求的业务维度不是当前分类下的现行版本。请返回维度目录选择现行维度后重新创建维度表。");
				} else if (definitions.length === 0) {
					setDefinitionError("当前业务分类没有现行业务维度。请先在维度目录登记并确认维度，再创建维度表。");
				}
			} catch {
				if (requestId !== definitionRequestRef.current) return;
				setDimensionDefinitions([]);
				form.setFieldValue("dimensionDefinitionRef", undefined);
				setDefinitionError("现行业务维度加载失败。请稍后重试，或返回维度目录确认维度状态后再创建。");
			} finally {
				if (requestId === definitionRequestRef.current) setLoadingDefinitions(false);
			}
		},
		[form, initialDimensionDefinitionId, initialDimensionDefinitionRevision, selectedModelType],
	);

	useEffect(() => {
		if (!open) {
			planRequestRef.current += 1;
			domainRequestRef.current += 1;
			definitionRequestRef.current += 1;
			return;
		}
		const requestId = ++planRequestRef.current;
		form.resetFields();
		form.setFieldsValue(createEmptyModelSpecDraft(initialModelType, { planId: lockedPlanId, domainId: initialDomainId }));
		idempotencyKeyRef.current = createModelSpecIdempotencyKey();
		setSubmitError("");
		setContextError("");
		setDefinitionError("");
		setDimensionDefinitions([]);
		setLoadingPlans(true);
		void listWarehousePlans()
			.then((result) => {
				if (requestId !== planRequestRef.current) return;
				setPlans(Array.isArray(result) ? result : []);
			})
			.catch(() => {
				if (requestId !== planRequestRef.current) return;
				setPlans([]);
				setContextError("建设计划加载失败。请稍后重试后选择建设计划。");
			})
			.finally(() => {
				if (requestId === planRequestRef.current) setLoadingPlans(false);
			});
		if (lockedPlanId?.trim()) {
			void loadDomains(lockedPlanId.trim(), initialDomainId);
		} else {
			setDomainOptions([]);
			setContextError("请先选择建设计划。创建模型需要明确建设计划，系统不会自动猜测。");
		}
		return () => {
			planRequestRef.current += 1;
			domainRequestRef.current += 1;
			definitionRequestRef.current += 1;
		};
	}, [form, initialDomainId, initialModelType, loadDomains, lockedPlanId, open]);

	useEffect(() => {
		void loadDimensionDefinitions(selectedDomainId);
	}, [loadDimensionDefinitions, selectedDomainId]);

	const planOptions = useMemo<SelectOption[]>(() => {
		const options = plans.map((plan) => ({ value: plan.id, label: `${plan.name}（${plan.code}）` }));
		if (lockedPlanId && !options.some((option) => option.value === lockedPlanId)) {
			return [{ value: lockedPlanId, label: `当前计划（${lockedPlanId}）` }, ...options];
		}
		return options;
	}, [lockedPlanId, plans]);
	const dimensionOptions = useMemo<SelectOption[]>(
		() => dimensionDefinitions.map((definition) => ({ value: definition.id, label: `${definition.name} · r${definition.revision}` })),
		[dimensionDefinitions],
	);

	const changePlan = (planId: string) => {
		definitionRequestRef.current += 1;
		form.setFieldsValue({ domainId: "", dimensionDefinitionRef: undefined });
		setDimensionDefinitions([]);
		setDefinitionError("");
		void loadDomains(planId);
	};

	const changeDomain = () => {
		form.setFieldValue("dimensionDefinitionRef", undefined);
		setDefinitionError("");
	};

	const changeModelType = (modelType: ModelSpecType) => {
		form.setFieldsValue({ modelType, dimensionDefinitionRef: undefined });
		setDefinitionError("");
	};

	const selectDimensionDefinition = (definitionId: string) => {
		const definition = dimensionDefinitions.find((candidate) => candidate.id === definitionId);
		form.setFieldValue(
			"dimensionDefinitionRef",
			definition ? { dimensionDefinitionId: definition.id, revision: definition.revision } : undefined,
		);
	};

	const submit = async () => {
		setSubmitError("");
		try {
			await form.validateFields();
			const values = form.getFieldsValue(true);
			if (values.modelType === "DIMENSION" && !values.dimensionDefinitionRef) {
				setSubmitError("请选择已确认的现行业务维度后再保存");
				return;
			}
			const command = buildModelSpecCreateCommand(values, [], idempotencyKeyRef.current);
			const issues = validateModelSpecCreate(command);
			if (issues.length > 0) {
				form.setFields(issues.map((issue) => ({ name: issueField(issue.field), errors: [modelSpecIssueMessage(issue.code)] })));
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
			title="新建模型"
			aria-label="新建模型"
			open={open}
			onClose={onClose}
			width={540}
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
							disabled={loadingPlans || loadingDomains || (selectedModelType === "DIMENSION" && loadingDefinitions)}
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
				{definitionError ? <Alert type="warning" showIcon message={definitionError} /> : null}
				{submitError ? <Alert type="error" showIcon message={submitError} /> : null}
				<Form form={form} layout="vertical" requiredMark="optional" disabled={saving}>
					<Form.Item name="planId" label="建设计划" rules={[{ required: true, message: "请选择建设计划" }]}>
						<Select
							showSearch
							optionFilterProp="label"
							placeholder="选择模型所属的建设计划"
							options={planOptions}
							loading={loadingPlans}
							disabled={Boolean(lockedPlanId) || loadingPlans}
							onChange={changePlan}
						/>
					</Form.Item>
					<Form.Item name="domainId" label="业务分类" rules={[{ required: true, message: "请选择业务分类" }]}>
						<Select
							showSearch
							optionFilterProp="label"
							placeholder="从当前建设计划中选择已确认业务分类"
							options={domainOptions}
							loading={loadingDomains}
							disabled={!selectedPlanId || loadingDomains}
							onChange={changeDomain}
						/>
					</Form.Item>
					<Form.Item name="modelType" label="模型类型" rules={[{ required: true, message: "请选择模型类型" }]}>
						<Select options={modelTypeOptions} disabled={lockModelType} onChange={(value) => changeModelType(value as ModelSpecType)} />
					</Form.Item>
					{selectedModelType === "DIMENSION" ? (
						<Form.Item label="业务维度" required>
							<Select
								showSearch
								optionFilterProp="label"
								placeholder="选择当前业务分类下的现行业务维度"
								options={dimensionOptions}
								value={selectedDimensionDefinitionRef?.dimensionDefinitionId}
								loading={loadingDefinitions}
								disabled={!selectedDomainId || loadingDefinitions}
								onChange={selectDimensionDefinition}
							/>
						</Form.Item>
					) : null}
					<Form.Item name="name" label="模型名称" rules={[{ required: true, whitespace: true, message: "请输入模型名称" }]}>
						<Input autoFocus maxLength={128} placeholder="例如：客户订单明细" />
					</Form.Item>
					<Form.Item name="description" label="用途说明（可选）">
						<Input.TextArea rows={3} maxLength={2000} showCount placeholder="说明此模型支持的业务分析或应用场景" />
					</Form.Item>
				</Form>
			</Space>
		</Drawer>
	);
}
