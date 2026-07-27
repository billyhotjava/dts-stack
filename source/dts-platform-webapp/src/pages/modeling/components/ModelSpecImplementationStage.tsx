import { Alert, Button, Card, Col, Form, Input, InputNumber, Row, Select, Space, Typography } from "antd";
import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { saveModelImplementation, validateModelImplementation } from "@/api/modelImplementationApi";
import { getModelLifecycle } from "@/api/modelSpecApi";
import {
	isUpstreamModelImplementationPinned,
	type ModelImplementationCasToken,
	type ModelImplementationExecutionPlan,
	type ModelImplementationInputMode,
	type ModelImplementationView,
	type ModelImplementationWriteCommand,
	modelImplementationValidationMessage,
	resolvePhysicalAssetImplementationInputs,
	resolveUpstreamModelImplementationInputs,
} from "../modelImplementationContract";
import type { ModelSpecSourceChoice } from "../modelSpecSourceSelection";
import type { CanonicalModelSpecView, ModelSpecCasToken } from "../modelSpecV2Contract";
import {
	ModelImplementationFieldMappings,
	ModelImplementationGuide,
	ModelImplementationSourceSection,
	ModelImplementationTargetSummary,
	ModelImplementationTechnicalDetails,
} from "./ModelSpecImplementationPresentation";
import { ModelDeliveryIntentActions } from "./ModelDeliveryIntentActions";
import type { ModelSpecSelectOption } from "./ModelSpecLogicalDesignStage";

type ImplementationDraft = {
	inputMode: ModelImplementationInputMode;
	inputIds: string[];
	joins: Array<{
		inputIndex: number;
		type: "INNER" | "LEFT" | "RIGHT" | "FULL";
		leftField: string;
		rightField: string;
	}>;
	projectKey: string;
	dbtUniqueId: string;
	fieldMappings: Array<{ sourceField: string; targetField: string; castType?: string }>;
	deduplicateBy: string[];
	materialization: string;
	targetPhysicalName: string;
	loadStrategy: "FULL" | "INCREMENTAL" | "SNAPSHOT";
	partitionFields: string[];
	retentionDays?: number;
};

const { Text } = Typography;

export type ModelSpecImplementationStageActionRef = {
	save: () => Promise<boolean>;
	validate: () => Promise<boolean>;
};

type Props = {
	model: CanonicalModelSpecView;
	expected: ModelSpecCasToken;
	implementation: ModelImplementationView | null;
	implementationLoading: boolean;
	sourceOptions: ModelSpecSourceChoice[];
	upstreamOptions: ModelSpecSelectOption[];
	sourceLoading: boolean;
	sourceError: string;
	sourcePermissionDenied: boolean;
	readOnly: boolean;
	deliveryReady: boolean;
	onReloadSources: () => void;
	onManageSources?: () => void;
	onStateChange: (state: { configured: boolean; dirty: boolean; validated: boolean }) => void;
	onImplementationSaved: (implementation: ModelImplementationView) => void;
	advancedEntryDisabled: boolean;
	onOpenAdvanced: () => void;
};

type ImplementationErrorLike = {
	response?: {
		status?: number;
		data?: { code?: string };
	};
};

const implementationError = (error: unknown) => {
	const response = (error as ImplementationErrorLike)?.response;
	if (response?.status === 409 && response.data?.code === "MODEL_IMPLEMENTATION_REVISION_CONFLICT") {
		return "实现版本已变化，当前配置仍保留；请先加载最新实现版本后再重试。";
	}
	if (response?.status === 409 && response.data?.code === "MODEL_SPEC_REVISION_CONFLICT") {
		return "ModelSpec 版本已变化，当前配置仍保留；请返回模型详情重新加载。";
	}
	if (response?.status === 409) return "当前实现与服务端约束冲突，配置仍保留；请检查提示后重试。";
	if (response?.status === 403) return "当前账号没有维护数据实现的权限。";
	return "数据实现暂未保存，当前配置仍保留，请检查输入或稍后重试。";
};

const isImplementationVersionConflict = (error: unknown): boolean => {
	const response = (error as ImplementationErrorLike)?.response;
	return response?.status === 409 && response.data?.code === "MODEL_IMPLEMENTATION_REVISION_CONFLICT";
};

const generatedImplementationIdentity = (model: CanonicalModelSpecView) => {
	const projectKey = "dts";
	const nodeName = `model_${model.id.replaceAll("-", "_")}`;
	return {
		projectKey,
		dbtUniqueId: `model.${projectKey}.${nodeName}`,
	};
};

const defaultDraft = (model: CanonicalModelSpecView): ImplementationDraft => {
	const sourceIds = model.sourceRefs.map((source) => source.sourceBindingId).filter(Boolean);
	const upstreamIds = model.dependsOn.map((reference) => reference.modelSpecId).filter(Boolean);
	const inputMode: ModelImplementationInputMode =
		model.modelType === "SUMMARY" || model.modelType === "APPLICATION"
			? "UPSTREAM_MODEL"
			: sourceIds.length > 0
				? "PHYSICAL_ASSET"
				: upstreamIds.length > 0
					? "UPSTREAM_MODEL"
					: model.modelType === "DIMENSION" && model.generationStrategy?.type === "DATE_DIMENSION"
						? "GENERATED"
						: "PHYSICAL_ASSET";
	return {
		inputMode,
		inputIds: inputMode === "PHYSICAL_ASSET" ? sourceIds : inputMode === "UPSTREAM_MODEL" ? upstreamIds : [],
		joins: [],
		...generatedImplementationIdentity(model),
		fieldMappings: [],
		deduplicateBy: [],
		materialization: model.materialization || "table",
		targetPhysicalName: model.implementationPolicy?.physicalName || "",
		loadStrategy: model.implementationPolicy?.loadStrategy || "FULL",
		partitionFields: model.implementationPolicy?.partitionFields || [],
		retentionDays: model.implementationPolicy?.retentionDays ?? undefined,
	};
};

const persistedDraft = (implementation: ModelImplementationView): ImplementationDraft => {
	const casts =
		implementation.settings?.casts &&
		!Array.isArray(implementation.settings.casts) &&
		typeof implementation.settings.casts === "object"
			? (implementation.settings.casts as Record<string, unknown>)
			: {};
	const deduplicateBy = Array.isArray(implementation.settings?.deduplicateBy)
		? implementation.settings.deduplicateBy.filter((value): value is string => typeof value === "string")
		: [];
	const joins = Array.isArray(implementation.settings?.joins)
		? implementation.settings.joins.flatMap((value) => {
				if (!value || typeof value !== "object" || Array.isArray(value)) return [];
				const join = value as Record<string, unknown>;
				if (
					typeof join.inputIndex !== "number" ||
					!["INNER", "LEFT", "RIGHT", "FULL"].includes(String(join.type)) ||
					typeof join.leftField !== "string" ||
					typeof join.rightField !== "string"
				) {
					return [];
				}
				return [
					{
						inputIndex: join.inputIndex,
						type: join.type as "INNER" | "LEFT" | "RIGHT" | "FULL",
						leftField: join.leftField,
						rightField: join.rightField,
					},
				];
			})
		: [];
	return {
		inputMode: implementation.inputMode,
		inputIds:
			implementation.inputMode === "PHYSICAL_ASSET"
				? implementation.inputs.flatMap((input) => ("sourceBindingId" in input ? [input.sourceBindingId] : []))
				: implementation.inputMode === "UPSTREAM_MODEL"
					? implementation.inputs.flatMap((input) => ("modelSpecId" in input ? [input.modelSpecId] : []))
					: implementation.inputs.flatMap((input) => ("generatorType" in input ? [input.generatorType] : [])),
		joins,
		projectKey: implementation.projectKey,
		dbtUniqueId: implementation.dbtUniqueId,
		fieldMappings: implementation.fieldMappings.map((mapping) => ({
			...mapping,
			castType: typeof casts[mapping.targetField] === "string" ? String(casts[mapping.targetField]) : undefined,
		})),
		deduplicateBy,
		materialization: implementation.materialization,
		targetPhysicalName:
			typeof implementation.settings?.targetPhysicalName === "string" ? implementation.settings.targetPhysicalName : "",
		loadStrategy: ["FULL", "INCREMENTAL", "SNAPSHOT"].includes(String(implementation.settings?.loadStrategy))
			? (implementation.settings.loadStrategy as ImplementationDraft["loadStrategy"])
			: "FULL",
		partitionFields: Array.isArray(implementation.settings?.partitionFields)
			? implementation.settings.partitionFields.filter((value): value is string => typeof value === "string")
			: [],
		retentionDays:
			typeof implementation.settings?.retentionDays === "number" ? implementation.settings.retentionDays : undefined,
	};
};

const castOptions = [
	{ value: "string", label: "字符串" },
	{ value: "integer", label: "整数" },
	{ value: "bigint", label: "长整数" },
	{ value: "decimal", label: "小数" },
	{ value: "date", label: "日期" },
	{ value: "timestamp", label: "时间戳" },
	{ value: "boolean", label: "布尔值" },
];

export const ModelSpecImplementationStage = forwardRef<ModelSpecImplementationStageActionRef, Props>(
	function ModelSpecImplementationStage(
		{
			model,
			expected,
			implementation,
			implementationLoading,
			sourceOptions,
			upstreamOptions,
			sourceLoading,
			sourceError,
			sourcePermissionDenied,
			readOnly,
			deliveryReady,
			onReloadSources,
			onManageSources,
			onStateChange,
			onImplementationSaved,
			advancedEntryDisabled,
			onOpenAdvanced,
		},
		ref,
	) {
		const [form] = Form.useForm<ImplementationDraft>();
		const [saving, setSaving] = useState(false);
		const [rebasing, setRebasing] = useState(false);
		const [error, setError] = useState("");
		const [recoveryNotice, setRecoveryNotice] = useState("");
		const [executionPlan, setExecutionPlan] = useState<ModelImplementationExecutionPlan | null>(null);
		const [versionConflict, setVersionConflict] = useState(false);
		const [configured, setConfigured] = useState(false);
		const [implementationCas, setImplementationCas] = useState<ModelImplementationCasToken | null>(null);
		const [adoptedCurrentPinIds, setAdoptedCurrentPinIds] = useState<string[]>([]);
		const operationRequestRef = useRef(0);
		const inputMode = Form.useWatch("inputMode", form) || "PHYSICAL_ASSET";
		const inputIds = Form.useWatch("inputIds", form) || [];
		const targetPhysicalName = Form.useWatch("targetPhysicalName", form) || "";
		const loadStrategy = Form.useWatch("loadStrategy", form) || "FULL";
		const partitionFields = Form.useWatch("partitionFields", form) || [];
		const retentionDays = Form.useWatch("retentionDays", form);
		const currentImplementation = useMemo(
			() =>
				implementation?.modelSpecId === model.id &&
				implementation.revision === model.revision &&
				implementation.modelChecksum === model.checksum
					? implementation
					: null,
			[implementation, model.checksum, model.id, model.revision],
		);
		const persistedUpstreamInputs = useMemo(
			() =>
				(currentImplementation?.inputMode === "UPSTREAM_MODEL" ? currentImplementation.inputs : []).flatMap((input) =>
					"modelSpecId" in input ? [input] : [],
				),
			[currentImplementation],
		);
		const persistedUpstreamById = useMemo(
			() => new Map(persistedUpstreamInputs.map((input) => [input.modelSpecId, input])),
			[persistedUpstreamInputs],
		);
		const implementationIdentity = useMemo(() => generatedImplementationIdentity(model), [model]);
		const inputModeOptions = useMemo(() => {
			const options = [
				{ value: "PHYSICAL_ASSET", label: "已登记的数据表" },
				{ value: "UPSTREAM_MODEL", label: "已有模型输出" },
				{ value: "GENERATED", label: "系统生成（仅日期维度）" },
			];
			if (model.modelType === "DIMENSION") {
				const canUseDateGenerator =
					model.generationStrategy?.type === "DATE_DIMENSION" || currentImplementation?.inputMode === "GENERATED";
				return canUseDateGenerator ? options : options.filter((option) => option.value !== "GENERATED");
			}
			if (model.modelType === "FACT") return options.filter((option) => option.value !== "GENERATED");
			return options.filter((option) => option.value === "UPSTREAM_MODEL");
		}, [currentImplementation?.inputMode, model.generationStrategy?.type, model.modelType]);
		const inputOptions = useMemo(() => {
			const rawAvailable =
				inputMode === "PHYSICAL_ASSET"
					? sourceOptions
					: inputMode === "UPSTREAM_MODEL"
						? upstreamOptions
						: [{ value: "DATE_DIMENSION", label: "日期维度生成规则" }];
			if (!currentImplementation || currentImplementation.inputMode !== inputMode) return rawAvailable;
			const persistedIds = new Set(
				currentImplementation.inputs.flatMap((input) =>
					inputMode === "PHYSICAL_ASSET" && "sourceBindingId" in input
						? [input.sourceBindingId]
						: inputMode === "UPSTREAM_MODEL" && "modelSpecId" in input
							? [input.modelSpecId]
							: inputMode === "GENERATED" && "generatorType" in input
								? [input.generatorType]
								: [],
				),
			);
			const available = rawAvailable.map((option) =>
				persistedIds.has(option.value) && "disabled" in option && option.disabled
					? { ...option, disabled: false }
					: option,
			);
			const availableIds = new Set(available.map((option) => option.value));
			const persisted = currentImplementation.inputs.flatMap((input) => {
				if (inputMode === "PHYSICAL_ASSET" && "sourceBindingId" in input && !availableIds.has(input.sourceBindingId)) {
					return [
						{
							value: input.sourceBindingId,
							label: `${input.sourceBindingId} · 已保存版本 ${input.resolvedVersion}`,
						},
					];
				}
				if (inputMode === "UPSTREAM_MODEL" && "modelSpecId" in input && !availableIds.has(input.modelSpecId)) {
					return [
						{
							value: input.modelSpecId,
							label: isUpstreamModelImplementationPinned(input)
								? `${input.modelSpecId} · 已固定 r${input.revision} / i${input.implementationRevision}`
								: `${input.modelSpecId} · 待固定 r${input.revision}`,
						},
					];
				}
				if (inputMode === "GENERATED" && "generatorType" in input && !availableIds.has(input.generatorType)) {
					return [{ value: input.generatorType, label: `${input.generatorType} · 已保存生成器` }];
				}
				return [];
			});
			return [...available, ...persisted];
		}, [currentImplementation, inputMode, sourceOptions, upstreamOptions]);
		const adoptedCurrentPinIdSet = useMemo(() => new Set(adoptedCurrentPinIds), [adoptedCurrentPinIds]);
		const pinDriftIds = useMemo(() => {
			if (!currentImplementation || currentImplementation.inputMode !== inputMode) return [];
			if (inputMode === "PHYSICAL_ASSET") {
				const persistedById = new Map(
					currentImplementation.inputs.flatMap((input) =>
						"sourceBindingId" in input ? [[input.sourceBindingId, input.resolvedVersion] as const] : [],
					),
				);
				const availableById = new Map(
					sourceOptions.filter((input) => !input.disabled).map((input) => [input.value, input.resolvedVersion]),
				);
				return inputIds.filter((id) => {
					const persisted = persistedById.get(id);
					const available = availableById.get(id);
					return Boolean(persisted && available && persisted !== available);
				});
			}
			if (inputMode === "UPSTREAM_MODEL") {
				const persistedById = new Map(
					currentImplementation.inputs.flatMap((input) =>
						"modelSpecId" in input ? [[input.modelSpecId, `${input.revision}:${input.checksum}`] as const] : [],
					),
				);
				const availableById = new Map(
					upstreamOptions
						.filter((input) => !input.disabled)
						.map((input) => [input.value, `${input.revision || 0}:${input.checksum || ""}`]),
				);
				return inputIds.filter((id) => {
					const persisted = persistedById.get(id);
					const available = availableById.get(id);
					return Boolean(persisted && available && persisted !== available);
				});
			}
			return [];
		}, [currentImplementation, inputIds, inputMode, sourceOptions, upstreamOptions]);
		const pendingPinDriftIds = pinDriftIds.filter((id) => !adoptedCurrentPinIdSet.has(id));
		const selectableUpstreamIdSet = useMemo(
			() =>
				new Set(
					upstreamOptions
						.filter((input) => !input.disabled && (input.revision ?? 0) > 0 && Boolean(input.checksum?.trim()))
						.map((input) => input.value),
				),
			[upstreamOptions],
		);
		const adoptableCurrentInputIds = useMemo(() => {
			if (inputMode === "PHYSICAL_ASSET") return pendingPinDriftIds;
			if (inputMode !== "UPSTREAM_MODEL") return [];
			return inputIds.filter(
				(id) => persistedUpstreamById.has(id) && selectableUpstreamIdSet.has(id) && !adoptedCurrentPinIdSet.has(id),
			);
		}, [
			adoptedCurrentPinIdSet,
			inputIds,
			inputMode,
			pendingPinDriftIds,
			persistedUpstreamById,
			selectableUpstreamIdSet,
		]);
		const upstreamPinStates = useMemo(() => {
			if (inputMode !== "UPSTREAM_MODEL") return [];
			const labelById = new Map(upstreamOptions.map((input) => [input.value, input.label]));
			return inputIds.map((id) => {
				const persisted = persistedUpstreamById.get(id);
				const explicitlyAdopted = adoptedCurrentPinIdSet.has(id);
				const persistedPinned = persisted !== undefined && isUpstreamModelImplementationPinned(persisted);
				const pinned = !explicitlyAdopted && persistedPinned;
				return {
					id,
					label: labelById.get(id) || id,
					pinned,
					requiresAdoption: persisted !== undefined && !explicitlyAdopted && !persistedPinned,
				};
			});
		}, [adoptedCurrentPinIdSet, inputIds, inputMode, persistedUpstreamById, upstreamOptions]);

		useEffect(() => {
			operationRequestRef.current += 1;
			form.resetFields();
			form.setFieldsValue(currentImplementation ? persistedDraft(currentImplementation) : defaultDraft(model));
			setImplementationCas(currentImplementation);
			setConfigured(Boolean(currentImplementation));
			setRebasing(false);
			setError("");
			setRecoveryNotice("");
			setVersionConflict(false);
			setAdoptedCurrentPinIds([]);
			onStateChange({ configured: Boolean(currentImplementation), dirty: false, validated: false });
			return () => {
				operationRequestRef.current += 1;
			};
		}, [currentImplementation, form, model, onStateChange]);

		useEffect(() => {
			const joinCount = Math.max(0, inputIds.length - 1);
			const existing = (form.getFieldValue("joins") as ImplementationDraft["joins"] | undefined) || [];
			const next = Array.from({ length: joinCount }, (_unused, index) => {
				const inputIndex = index + 1;
				const current = existing.find((join) => join.inputIndex === inputIndex) || existing[index];
				return {
					inputIndex,
					type: current?.type || "LEFT",
					leftField: current?.leftField || "",
					rightField: current?.rightField || "",
				};
			});
			if (JSON.stringify(existing) !== JSON.stringify(next)) form.setFieldValue("joins", next);
		}, [form, inputIds]);

		const commandFromForm = async (): Promise<ModelImplementationWriteCommand | null> => {
			const values = await form.validateFields();
			const selectedIds = Array.from(new Set(values.inputIds.map((value) => value.trim()).filter(Boolean)));
			const fieldMappings = values.fieldMappings
				.filter((mapping) => mapping.sourceField.trim() && mapping.targetField.trim())
				.map(({ sourceField, targetField }) => ({ sourceField: sourceField.trim(), targetField: targetField.trim() }));
			const casts = Object.fromEntries(
				values.fieldMappings
					.filter((mapping) => mapping.targetField.trim() && mapping.castType)
					.map((mapping) => [mapping.targetField.trim(), mapping.castType]),
			);
			const settings: Record<string, unknown> = {};
			settings.targetPhysicalName = values.targetPhysicalName.trim();
			settings.loadStrategy = values.loadStrategy;
			settings.partitionFields = values.partitionFields;
			if (values.retentionDays != null) settings.retentionDays = values.retentionDays;
			if (Object.keys(casts).length > 0) settings.casts = casts;
			if (values.deduplicateBy.length > 0) settings.deduplicateBy = values.deduplicateBy;
			if (selectedIds.length > 1) {
				const joins = (values.joins || []).map((join, index) => ({
					inputIndex: index + 1,
					type: join.type,
					leftField: join.leftField.trim(),
					rightField: join.rightField.trim(),
				}));
				const invalidJoin = joins.find((join, index) => {
					const leftAlias = /^src_(\d+)\.[A-Za-z_][A-Za-z0-9_]*$/.exec(join.leftField);
					const rightAlias = new RegExp(`^src_${index + 1}\\.[A-Za-z_][A-Za-z0-9_]*$`).test(join.rightField);
					return !leftAlias || Number(leftAlias[1]) >= index + 1 || !rightAlias;
				});
				if (joins.length !== selectedIds.length - 1 || invalidJoin) {
					form.setFields([
						{
							name: "joins",
							errors: ["每个追加输入都必须使用 src_N.field 配置受控关联，左侧只能引用此前输入"],
						},
					]);
					return null;
				}
				settings.joins = joins;
			}
			const base = {
				projectKey: implementationIdentity.projectKey,
				dbtUniqueId: implementationIdentity.dbtUniqueId,
				fieldMappings,
				settings,
				ownership: model.implementationMode,
				materialization: values.materialization.trim() || "table",
				idempotencyKey: `model-implementation:${model.id}:${expected.revision}:i${implementationCas?.implementationRevision ?? 0}`,
			};
			if (values.inputMode === "PHYSICAL_ASSET") {
				const available = sourceOptions
					.filter((source) => !source.disabled)
					.map((source) => ({
						sourceBindingId: source.value,
						resolvedVersion: source.resolvedVersion,
					}));
				const persisted = (
					currentImplementation?.inputMode === "PHYSICAL_ASSET" ? currentImplementation.inputs : []
				).flatMap((input) => ("sourceBindingId" in input ? [input] : []));
				const inputs = resolvePhysicalAssetImplementationInputs(
					selectedIds,
					available,
					persisted,
					adoptedCurrentPinIdSet,
				);
				if (inputs.some((input) => !input.resolvedVersion)) {
					form.setFields([{ name: "inputIds", errors: ["所选规划来源缺少已确认版本，请刷新来源盘点后重选"] }]);
					return null;
				}
				return { ...base, inputMode: "PHYSICAL_ASSET", inputs };
			}
			if (values.inputMode === "UPSTREAM_MODEL") {
				const available = upstreamOptions
					.filter((source) => !source.disabled)
					.map((source) => ({
						modelSpecId: source.value,
						revision: source.revision || 0,
						checksum: source.checksum || "",
					}));
				const incompletePersistedIds = selectedIds.filter((id) => {
					const persisted = persistedUpstreamById.get(id);
					return (
						persisted !== undefined &&
						!isUpstreamModelImplementationPinned(persisted) &&
						!adoptedCurrentPinIdSet.has(id)
					);
				});
				if (incompletePersistedIds.length > 0) {
					form.setFields([
						{
							name: "inputIds",
							errors: ["已保存的上游引用缺少完整实现 pin，请先明确选择“采用当前实现 / 升级引用”"],
						},
					]);
					return null;
				}
				const inputs = resolveUpstreamModelImplementationInputs(
					selectedIds,
					available,
					persistedUpstreamInputs.filter(isUpstreamModelImplementationPinned),
					adoptedCurrentPinIdSet,
				);
				if (inputs.some((input) => input.revision < 1 || !input.checksum)) {
					form.setFields([{ name: "inputIds", errors: ["所选上游模型缺少锁定版本或校验值，请刷新模型列表后重选"] }]);
					return null;
				}
				return { ...base, inputMode: "UPSTREAM_MODEL", inputs };
			}
			return {
				...base,
				inputMode: "GENERATED",
				inputs: selectedIds.map((generatorType) => ({ generatorType })),
			};
		};

		const initializeFieldMappings = () => {
			const currentMappings =
				(form.getFieldValue("fieldMappings") as ImplementationDraft["fieldMappings"] | undefined) || [];
			const currentByTarget = new Map(
				currentMappings
					.filter((mapping) => mapping.targetField?.trim())
					.map((mapping) => [mapping.targetField.trim(), mapping]),
			);
			const targetNames = new Set(model.fields.map((field) => field.name));
			const initialized = model.fields.map((field) => {
				const current = currentByTarget.get(field.name);
				if (current) return current;
				const sourceReference = field.sourceFieldRef?.trim() || "";
				const sourceFieldCandidate = sourceReference.split(".").at(-1) || field.name;
				const safeQualifiedReference = /^(?:src_\d+\.)[A-Za-z_][A-Za-z0-9_]*$/.test(sourceReference)
					? sourceReference
					: "";
				const sourceField =
					safeQualifiedReference ||
					(/^[A-Za-z_][A-Za-z0-9_]*$/.test(sourceFieldCandidate) ? sourceFieldCandidate : field.name);
				return {
					sourceField: inputIds.length > 1 && !sourceField.startsWith("src_") ? `src_0.${sourceField}` : sourceField,
					targetField: field.name,
				};
			});
			const extraMappings = currentMappings.filter((mapping) => !targetNames.has(mapping.targetField?.trim()));
			form.setFieldValue("fieldMappings", [...initialized, ...extraMappings]);
			setRecoveryNotice("已按逻辑设计字段初始化同名映射；请确认来源字段名称和必要的类型转换。");
			onStateChange({ configured, dirty: true, validated: false });
		};

		const adoptCurrentPins = () => {
			if (adoptableCurrentInputIds.length === 0) return;
			setAdoptedCurrentPinIds((current) => Array.from(new Set([...current, ...adoptableCurrentInputIds])));
			form.setFields([{ name: "inputIds", errors: [] }]);
			setRecoveryNotice(
				inputMode === "UPSTREAM_MODEL"
					? "已明确选择采用当前上游实现；保存时将移除旧实现 pin，由服务端重新固定当前 implementation。"
					: "已选择采用当前版本；保存前仍可调整输入，只有保存成功后版本升级才会生效。",
			);
			onStateChange({ configured, dirty: true, validated: false });
		};

		const rebaseImplementationCas = async () => {
			if (readOnly || saving || rebasing) return;
			const requestId = ++operationRequestRef.current;
			setRebasing(true);
			setError("");
			setRecoveryNotice("");
			try {
				const timeline = await getModelLifecycle(model.id);
				if (requestId !== operationRequestRef.current) return;
				const latest = timeline.implementation;
				if (
					!latest ||
					latest.modelSpecId !== model.id ||
					latest.planId !== model.planId ||
					latest.revision !== model.revision ||
					latest.modelChecksum !== model.checksum ||
					latest.ownership !== model.implementationMode
				) {
					setError("最新实现已不属于当前 ModelSpec 版本；请返回模型详情重新加载。");
					return;
				}
				setImplementationCas(latest);
				setConfigured(true);
				setVersionConflict(false);
				setRecoveryNotice("已加载最新 implementation CAS，当前表单输入仍保留；请确认差异后再次保存。");
				onStateChange({ configured: true, dirty: true, validated: false });
			} catch {
				if (requestId !== operationRequestRef.current) return;
				setError("最新实现版本加载失败，当前输入仍保留，请稍后重试。");
			} finally {
				if (requestId === operationRequestRef.current) setRebasing(false);
			}
		};

		const save = async () => {
			if (readOnly || saving || rebasing) return false;
			const requestId = ++operationRequestRef.current;
			setError("");
			setRecoveryNotice("");
			try {
				const command = await commandFromForm();
				if (!command) return false;
				setSaving(true);
				const saved = await saveModelImplementation(expected, implementationCas, command);
				if (requestId !== operationRequestRef.current) return false;
				setImplementationCas(saved);
				setConfigured(true);
				setVersionConflict(false);
				setAdoptedCurrentPinIds([]);
				onImplementationSaved(saved);
				onStateChange({ configured: true, dirty: false, validated: false });
				return true;
			} catch (saveFailure) {
				if (requestId !== operationRequestRef.current) return false;
				setVersionConflict(isImplementationVersionConflict(saveFailure));
				setError(implementationError(saveFailure));
				return false;
			} finally {
				if (requestId === operationRequestRef.current) setSaving(false);
			}
		};

		const validate = async () => {
			if (readOnly || saving || rebasing) return false;
			const requestId = ++operationRequestRef.current;
			setError("");
			setRecoveryNotice("");
			try {
				const command = await commandFromForm();
				if (!command) return false;
				setSaving(true);
				const result = await validateModelImplementation(expected, command);
				if (requestId !== operationRequestRef.current) return false;
				setExecutionPlan(result.valid ? result.executionPlan || null : null);
				if (!result.valid) setError(modelImplementationValidationMessage(result));
				onStateChange({ configured, dirty: !configured, validated: result.valid });
				return result.valid;
			} catch (validationFailure) {
				if (requestId !== operationRequestRef.current) return false;
				setError(implementationError(validationFailure));
				return false;
			} finally {
				if (requestId === operationRequestRef.current) setSaving(false);
			}
		};

		useImperativeHandle(ref, () => ({ save, validate }), [
			adoptedCurrentPinIds,
			configured,
			expected,
			form,
			implementationCas,
			model,
			persistedUpstreamById,
			persistedUpstreamInputs,
			readOnly,
			rebasing,
			saving,
			sourceOptions,
			upstreamOptions,
		]);

		return (
			<div className="min-w-0" data-testid="model-spec-implementation-stage">
				<ModelImplementationGuide />
				<Card
					size="small"
					className="mb-4"
					title="选择实现方式"
					extra={
						<Button size="small" disabled={advancedEntryDisabled} onClick={onOpenAdvanced}>
							进入高级 dbt 工作台
						</Button>
					}
				>
					<Space wrap>
						<Text>当前页面用于普通配置：选择来源、映射字段并设置目标产出。</Text>
						{advancedEntryDisabled ? (
							<Text type="secondary">首次进入高级 dbt 前，请先保存当前实现以锁定模型与实现版本。</Text>
						) : null}
					</Space>
				</Card>
				<ModelDeliveryIntentActions
					id="model-spec-delivery-intents"
					model={model}
					implementationReady={deliveryReady && Boolean(currentImplementation) && configured && !versionConflict}
					readOnly={readOnly || saving || rebasing || implementationLoading}
				/>
				<ModelImplementationTargetSummary
					model={model}
					settings={{ targetPhysicalName, loadStrategy, partitionFields, retentionDays }}
				/>
				{implementationLoading ? (
					<Alert className="mb-4" type="info" showIcon message="正在读取已保存的数据实现" />
				) : null}
				{error ? (
					<Alert
						className="mb-4"
						type="warning"
						showIcon
						message={error}
						action={
							versionConflict ? (
								<Button size="small" loading={rebasing} onClick={() => void rebaseImplementationCas()}>
									加载最新实现并保留当前输入
								</Button>
							) : undefined
						}
					/>
				) : null}
				{recoveryNotice ? <Alert className="mb-4" type="info" showIcon message={recoveryNotice} /> : null}
				{executionPlan ? (
					<Alert
						className="mb-4"
						type="success"
						showIcon
						message="当前实现可构建"
						description={`${executionPlan.adapter} · ${executionPlan.targetIdentifier} · ${executionPlan.effectiveMaterialization}`}
					/>
				) : null}
				<Form
					form={form}
					layout="vertical"
					requiredMark={false}
					disabled={readOnly || saving || rebasing || implementationLoading}
					onValuesChange={(changed) => {
						if ("inputMode" in changed) {
							form.setFieldValue("inputIds", []);
							setAdoptedCurrentPinIds([]);
						}
						const changedInputIds = Array.isArray(changed.inputIds) ? changed.inputIds : null;
						if (changedInputIds) {
							setAdoptedCurrentPinIds((current) => current.filter((id) => changedInputIds.includes(id)));
						}
						setRecoveryNotice("");
						setExecutionPlan(null);
						onStateChange({ configured, dirty: true, validated: false });
					}}
				>
					<ModelImplementationSourceSection
						inputMode={inputMode}
						inputModeOptions={inputModeOptions}
						inputOptions={inputOptions}
						inputIds={inputIds}
						sourceLoading={sourceLoading}
						sourceError={sourceError}
						sourcePermissionDenied={sourcePermissionDenied}
						pendingPinDriftCount={pendingPinDriftIds.length}
						upstreamPinStates={upstreamPinStates}
						adoptableCurrentInputCount={adoptableCurrentInputIds.length}
						onAdoptCurrentPins={adoptCurrentPins}
						onReloadSources={onReloadSources}
						onManageSources={onManageSources}
					/>
					<ModelImplementationFieldMappings
						inputCount={inputIds.length}
						modelFields={model.fields}
						readOnly={readOnly}
						castOptions={castOptions}
						onInitialize={initializeFieldMappings}
					/>
					<Card size="small" title="3. 确认产出方式">
						<Row gutter={12}>
							<Col xs={24} md={12}>
								<Form.Item
									name="materialization"
									label="数据生成方式"
									extra="表适合稳定复用；视图在查询时计算；增量表只处理新增或变化数据。"
									rules={[{ required: true }]}
								>
									<Select
										options={[
											{ value: "table", label: "表（完整物化）" },
											{ value: "view", label: "视图（查询时计算）" },
											{ value: "incremental", label: "增量表（只处理变化）" },
										]}
									/>
								</Form.Item>
							</Col>
							<Col xs={24} md={12}>
								<Form.Item
									name="deduplicateBy"
									label="重复记录判定字段（可选）"
									extra="选择能够唯一识别同一业务记录的模型字段；复杂处理请使用高级 dbt 工作台。"
								>
									<Select
										mode="multiple"
										allowClear
										options={model.fields.map((field) => ({
											value: field.name,
											label: field.name,
										}))}
										placeholder="例如：项目编码、业务日期"
									/>
								</Form.Item>
							</Col>
						</Row>
						<Row gutter={12}>
							<Col xs={24} md={12}>
								<Form.Item
									name="targetPhysicalName"
									label="目标物理名称"
									rules={[
										{ required: true, whitespace: true, message: "请输入目标物理名称" },
										{
											pattern: /^[a-z][a-z0-9_]{0,62}$/,
											message: "使用小写英文、数字和下划线，最长 63 个字符",
										},
									]}
								>
									<Input placeholder="例如：dwd_finance_project" />
								</Form.Item>
							</Col>
							<Col xs={24} md={12}>
								<Form.Item name="loadStrategy" label="装载策略" rules={[{ required: true }]}>
									<Select
										options={[
											{ value: "FULL", label: "全量覆盖" },
											{ value: "INCREMENTAL", label: "增量装载" },
											{ value: "SNAPSHOT", label: "周期快照" },
										]}
									/>
								</Form.Item>
							</Col>
						</Row>
						<Row gutter={12}>
							<Col xs={24} md={12}>
								<Form.Item name="partitionFields" label="分区字段（可选）" extra="只能选择逻辑设计中已登记的技术字段。">
									<Select
										mode="multiple"
										allowClear
										options={model.fields.map((field) => ({
											value: field.name,
											label: field.displayName ? `${field.displayName}（${field.name}）` : field.name,
										}))}
									/>
								</Form.Item>
							</Col>
							<Col xs={24} md={12}>
								<Form.Item
									name="retentionDays"
									label="数据保留天数（可选）"
									rules={[{ type: "number", min: 0, max: 36000, message: "请输入 0 到 36000 天" }]}
								>
									<InputNumber className="w-full" min={0} max={36000} />
								</Form.Item>
							</Col>
						</Row>
					</Card>
				</Form>
				<ModelImplementationTechnicalDetails
					model={model}
					projectKey={implementationIdentity.projectKey}
					dbtUniqueId={implementationIdentity.dbtUniqueId}
				/>
			</div>
		);
	},
);
