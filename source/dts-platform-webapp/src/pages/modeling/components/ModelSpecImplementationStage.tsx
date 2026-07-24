import { Alert, Button, Card, Col, Form, Input, Radio, Row, Select, Space, Tag, Typography } from "antd";
import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import {
	saveModelImplementation,
	validateModelImplementation,
} from "@/api/modelImplementationApi";
import { getModelLifecycle } from "@/api/modelSpecApi";
import {
	type ModelImplementationCasToken,
	type ModelImplementationInputMode,
	type ModelImplementationView,
	type ModelImplementationWriteCommand,
	isUpstreamModelImplementationPinned,
	resolvePhysicalAssetImplementationInputs,
	resolveUpstreamModelImplementationInputs,
} from "../modelImplementationContract";
import type {
	CanonicalModelSpecView,
	ModelSpecCasToken,
} from "../modelSpecV2Contract";
import type { ModelSpecSelectOption } from "./ModelSpecLogicalDesignStage";
import type { ModelSpecSourceChoice } from "../modelSpecSourceSelection";

const { Text } = Typography;

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
};

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
	onReloadSources: () => void;
	onManageSources?: () => void;
	onStateChange: (state: { configured: boolean; dirty: boolean; validated: boolean }) => void;
	onImplementationSaved: (implementation: ModelImplementationView) => void;
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
	const projectKey = `plan_${model.planId.replaceAll("-", "_")}`;
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
					: model.modelType === "DIMENSION"
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

export const ModelSpecImplementationStage = forwardRef<ModelSpecImplementationStageActionRef, Props>(function ModelSpecImplementationStage(
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
		onReloadSources,
		onManageSources,
		onStateChange,
		onImplementationSaved,
	},
	ref,
) {
	const [form] = Form.useForm<ImplementationDraft>();
	const [saving, setSaving] = useState(false);
	const [rebasing, setRebasing] = useState(false);
	const [error, setError] = useState("");
	const [recoveryNotice, setRecoveryNotice] = useState("");
	const [versionConflict, setVersionConflict] = useState(false);
	const [configured, setConfigured] = useState(false);
	const [implementationCas, setImplementationCas] = useState<ModelImplementationCasToken | null>(null);
	const [adoptedCurrentPinIds, setAdoptedCurrentPinIds] = useState<string[]>([]);
	const operationRequestRef = useRef(0);
	const inputMode = Form.useWatch("inputMode", form) || "PHYSICAL_ASSET";
	const inputIds = Form.useWatch("inputIds", form) || [];
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
			(currentImplementation?.inputMode === "UPSTREAM_MODEL" ? currentImplementation.inputs : []).flatMap(
				(input) => ("modelSpecId" in input ? [input] : []),
			),
		[currentImplementation],
	);
	const persistedUpstreamById = useMemo(
		() => new Map(persistedUpstreamInputs.map((input) => [input.modelSpecId, input])),
		[persistedUpstreamInputs],
	);
	const implementationIdentity = useMemo(
		() =>
			currentImplementation
				? {
						projectKey: currentImplementation.projectKey,
						dbtUniqueId: currentImplementation.dbtUniqueId,
					}
				: generatedImplementationIdentity(model),
		[currentImplementation, model],
	);
	const inputModeOptions = useMemo(() => {
		const options = [
			{ value: "PHYSICAL_ASSET", label: "规划物理资产" },
			{ value: "UPSTREAM_MODEL", label: "上游逻辑模型" },
			{ value: "GENERATED", label: "受控生成器" },
		];
		if (model.modelType === "DIMENSION") return options;
		if (model.modelType === "FACT") return options.filter((option) => option.value !== "GENERATED");
		return options.filter((option) => option.value === "UPSTREAM_MODEL");
	}, [model.modelType]);
	const inputOptions = useMemo(() => {
		const rawAvailable =
			inputMode === "PHYSICAL_ASSET"
				? sourceOptions
				: inputMode === "UPSTREAM_MODEL"
					? upstreamOptions
					: [{ value: "DATE_DIMENSION", label: "日期维度生成器" }];
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
					"modelSpecId" in input
						? [[input.modelSpecId, `${input.revision}:${input.checksum}`] as const]
						: [],
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
					.filter(
						(input) =>
							!input.disabled &&
							(input.revision ?? 0) > 0 &&
							Boolean(input.checksum?.trim()),
					)
					.map((input) => input.value),
			),
		[upstreamOptions],
	);
	const adoptableCurrentInputIds = useMemo(() => {
		if (inputMode === "PHYSICAL_ASSET") return pendingPinDriftIds;
		if (inputMode !== "UPSTREAM_MODEL") return [];
		return inputIds.filter(
			(id) =>
				persistedUpstreamById.has(id) &&
				selectableUpstreamIdSet.has(id) &&
				!adoptedCurrentPinIdSet.has(id),
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
			const persistedPinned =
				persisted !== undefined && isUpstreamModelImplementationPinned(persisted);
			const pinned = !explicitlyAdopted && persistedPinned;
			return {
				id,
				label: labelById.get(id) || id,
				pinned,
				requiresAdoption:
					persisted !== undefined &&
					!explicitlyAdopted &&
					!persistedPinned,
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
			if (!result.valid) setError(result.code || "当前实现未通过验证，配置已保留。");
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
			<Alert className="mb-4" type="info" showIcon message="数据实现" description="选择一种输入模式，并锁定一个或多个同类输入的当前版本；逻辑定义不会因来源服务暂时不可用而变为只读。" />
			{implementationLoading ? <Alert className="mb-4" type="info" showIcon message="正在读取已保存的数据实现" /> : null}
			{error ? <Alert className="mb-4" type="warning" showIcon message={error} action={versionConflict ? <Button size="small" loading={rebasing} onClick={() => void rebaseImplementationCas()}>加载最新实现并保留当前输入</Button> : undefined} /> : null}
			{recoveryNotice ? <Alert className="mb-4" type="info" showIcon message={recoveryNotice} /> : null}
			{model.implementationMode === "DESIGNER_GENERATED" ? <Card size="small" className="mb-4" title="系统预处理摘要" data-testid="model-spec-system-preprocessing"><Space wrap><Tag color="blue">系统管理</Tag><Tag>ephemeral</Tag><Tag>无物理表</Tag><Text type="secondary">普通模式自动生成临时 STG；它只作为编译与血缘技术节点，不进入物理资产台账。</Text></Space></Card> : null}
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
					onStateChange({ configured, dirty: true, validated: false });
				}}
			>
				<Row gutter={12}>
					<Col xs={24} md={12}><Form.Item name="inputMode" label="规范实现输入" rules={[{ required: true }]}><Radio.Group options={inputModeOptions} /></Form.Item></Col>
					<Col xs={24} md={12}><Form.Item name="materialization" label="目标物化方式" rules={[{ required: true }]}><Select options={[{ value: "table", label: "表" }, { value: "view", label: "视图" }, { value: "incremental", label: "增量表" }]} /></Form.Item></Col>
				</Row>
				<Form.Item name="inputIds" label={inputMode === "PHYSICAL_ASSET" ? "已确认 Landing / 物理来源" : inputMode === "UPSTREAM_MODEL" ? "锁定上游模型版本" : "生成器"} rules={[{ required: true, type: "array", min: 1, message: "请至少选择一个实现输入" }]}>
					<Select mode="multiple" allowClear showSearch optionFilterProp="label" options={inputOptions} loading={inputMode === "PHYSICAL_ASSET" && sourceLoading} placeholder="选择一个或多个同类规范输入" />
				</Form.Item>
				{inputMode === "PHYSICAL_ASSET" && pendingPinDriftIds.length > 0 ? <Alert className="mb-4" type="warning" showIcon data-testid="model-implementation-pin-drift" message={`检测到 ${pendingPinDriftIds.length} 个输入已有新版本，当前仍保留已保存的精确版本`} description="版本不会随普通保存静默升级；确认采用当前盘点版本后，下一次保存才会写入新 pin。" action={<Button size="small" onClick={adoptCurrentPins}>采用当前版本</Button>} /> : null}
				{inputMode === "UPSTREAM_MODEL" && upstreamPinStates.length > 0 ? (
					<Alert
						className="mb-4"
						type={
							upstreamPinStates.some((input) => input.requiresAdoption) || pendingPinDriftIds.length > 0
								? "warning"
								: "info"
						}
						showIcon
						data-testid="model-implementation-upstream-pin-state"
						message={
							<Space size={8} wrap>
								<span>上游实现引用</span>
								<Tag color="success">
									已固定 {upstreamPinStates.filter((input) => input.pinned).length}
								</Tag>
								<Tag color="gold">
									待固定 {upstreamPinStates.filter((input) => !input.pinned).length}
								</Tag>
							</Space>
						}
						description={
							<div>
								<Space size={4} wrap>
									{upstreamPinStates.map((input) => (
										<Tag key={input.id} color={input.pinned ? "success" : "gold"}>
											{input.pinned ? "已固定" : "待固定"} · {input.label}
										</Tag>
									))}
								</Space>
								<div className="mt-2">
									{upstreamPinStates.some((input) => input.requiresAdoption)
										? "已有引用缺少完整 implementation pin；必须明确采用当前实现后才能保存。"
										: pendingPinDriftIds.length > 0
											? "检测到上游 ModelSpec 已有新 revision；普通保存仍保留原六元 pin，只有明确升级才会重新固定。"
											: upstreamPinStates.some((input) => !input.pinned)
												? "新选或已明确升级的引用将在保存时由服务端固定当前 implementation。"
												: "普通保存会原样保留 modelSpec 与 implementation 六元 pin，不会静默升级。"}
								</div>
							</div>
						}
						action={
							adoptableCurrentInputIds.length > 0 ? (
								<Button size="small" onClick={adoptCurrentPins}>
									采用当前实现 / 升级引用
								</Button>
							) : undefined
						}
					/>
				) : null}
				{inputIds.length > 1 ? (
					<Form.List name="joins">
						{(fields) => (
							<Card
								size="small"
								className="mb-4"
								title="受控输入关联"
								extra={<Text type="secondary">系统别名按顺序固定为 src_0、src_1…，不接受自由 SQL</Text>}
							>
								{fields.map((field, index) => {
									const inputNumber = index + 2;
									const rightAlias = `src_${index + 1}`;
									return (
										<Row key={field.key} gutter={8}>
											<Col xs={24} md={4}>
												<Form.Item label={`输入 ${inputNumber}`}>
													<Input value={rightAlias} disabled />
												</Form.Item>
											</Col>
											<Col xs={24} md={5}>
												<Form.Item name={[field.name, "type"]} label="关联方式" rules={[{ required: true }]}>
													<Select
														options={[
															{ value: "INNER", label: "INNER" },
															{ value: "LEFT", label: "LEFT" },
															{ value: "RIGHT", label: "RIGHT" },
															{ value: "FULL", label: "FULL" },
														]}
													/>
												</Form.Item>
											</Col>
											<Col xs={24} md={7}>
												<Form.Item
													name={[field.name, "leftField"]}
													label="左侧关联字段"
													rules={[
														{ required: true, whitespace: true },
														{
															validator: async (_rule, value) => {
																const match = /^src_(\d+)\.[A-Za-z_][A-Za-z0-9_]*$/.exec(String(value || ""));
																if (!match || Number(match[1]) >= index + 1) {
																	throw new Error(`仅可引用 src_0 至 src_${index} 的安全字段名`);
																}
															},
														},
													]}
												>
													<Input placeholder={`src_${index}.business_key`} />
												</Form.Item>
											</Col>
											<Col xs={24} md={8}>
												<Form.Item
													name={[field.name, "rightField"]}
													label="当前输入关联字段"
													rules={[
														{ required: true, whitespace: true },
														{
															pattern: new RegExp(`^${rightAlias}\\.[A-Za-z_][A-Za-z0-9_]*$`),
															message: `必须使用 ${rightAlias}.field`,
														},
													]}
												>
													<Input placeholder={`${rightAlias}.business_key`} />
												</Form.Item>
											</Col>
										</Row>
									);
								})}
							</Card>
						)}
					</Form.List>
				) : null}
				{inputMode === "PHYSICAL_ASSET" && sourceError ? <Alert className="mb-4" type="warning" showIcon message={sourceError} action={<Space wrap><Button size="small" onClick={onReloadSources} loading={sourceLoading}>重试</Button>{!sourcePermissionDenied && onManageSources ? <Button size="small" type="primary" onClick={onManageSources}>修复 Landing 来源</Button> : null}</Space>} /> : null}
				<Card size="small" className="mb-4" title="系统技术标识" extra={<Text type="secondary">自动生成并保持稳定，无需人工维护</Text>}>
					<Row gutter={[12, 8]}>
						<Col xs={24} md={12}><Text type="secondary">实现项目标识</Text><br /><Text code copyable>{implementationIdentity.projectKey}</Text></Col>
						<Col xs={24} md={12}><Text type="secondary">目标节点标识</Text><br /><Text code copyable>{implementationIdentity.dbtUniqueId}</Text></Col>
					</Row>
				</Card>
				<Form.List name="fieldMappings">{(fields, { add, remove }) => <Card size="small" title="字段映射" extra={!readOnly ? <Button size="small" onClick={() => add({ sourceField: "", targetField: "" })}>添加映射</Button> : null}>{fields.map((field) => <Row key={field.key} gutter={8}><Col xs={24} md={8}><Form.Item name={[field.name, "sourceField"]} label="输入字段" rules={[{ pattern: /^(?:src_\d+\.)?[A-Za-z_][A-Za-z0-9_]*$/, message: "请输入字段名或安全限定名 src_N.field，不要填写 SQL 表达式" }, { validator: async (_rule, value) => { const match = /^src_(\d+)\./.exec(String(value || "")); if (match && Number(match[1]) >= inputIds.length) throw new Error("字段别名超出当前输入范围"); } }]}><Input placeholder={inputIds.length > 1 ? "src_0.customer_id" : "customer_id"} /></Form.Item></Col><Col xs={24} md={8}><Form.Item name={[field.name, "targetField"]} label="目标字段" rules={[{ pattern: /^[A-Za-z_][A-Za-z0-9_]*$/, message: "请输入字段名，不要填写 SQL 表达式" }]}><Input placeholder="customer_id" /></Form.Item></Col><Col xs={20} md={6}><Form.Item name={[field.name, "castType"]} label="类型转换"><Select allowClear placeholder="保持原类型" options={castOptions} /></Form.Item></Col><Col xs={4} md={2}><Button type="link" danger className="mt-8" onClick={() => remove(field.name)}>移除</Button></Col></Row>)}</Card>}</Form.List>
				<Form.Item name="deduplicateBy" label="去重键（可选）" extra="只可选择模型字段；系统按相同字段稳定排序并保留一条。复杂 SQL 请转到高级 dbt 工作台。">
					<Select mode="multiple" allowClear options={model.fields.map((field) => ({ value: field.name, label: field.name }))} placeholder="选择一个或多个模型字段" />
				</Form.Item>
			</Form>
		</div>
	);
});
