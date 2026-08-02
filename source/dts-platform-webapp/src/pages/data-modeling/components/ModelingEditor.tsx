import { Code2, ListChecks, Settings2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { listDimensionDefinitions } from "@/api/dimensionDefinitionApi";
import { listModelFieldStandardOptions, type ModelFieldStandardOption } from "@/api/modelingStandardsApi";
import {
	type CreateDimensionModelCommand,
	createDimensionModel,
	createModelSpec,
	getModelSpecStageGates,
	type ModelSpecStageGate,
	updateModelSpec,
} from "@/api/modelSpecApi";
import {
	getWarehousePlanCategories,
	listWarehousePlans,
	type WarehousePlanCategoryBindingView,
	type WarehousePlanHeader,
} from "@/api/warehousePlanApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import {
	type CanonicalModelSpecView,
	type ModelSpecField,
	type ModelSpecLayer,
	type ModelSpecStandardBinding,
	type ModelSpecType,
	type ModelSpecView,
	type UpdateModelSpecCommand,
	validateModelSpecUpdate,
} from "@/features/modeling/contracts/modelSpecV2Contract";
import { persistDimensionModelDraft } from "@/features/modeling/operations/dimensionModelDraftSession";
import { useCatalogMaintainerAccess } from "@/hooks/useModuleManageAccess";
import { useUserInfo } from "@/store/userStore";
import { MODEL_FIELD_DISPLAY_COLUMNS, type ModelingDialogKind, ModelingDialogs } from "./ModelingDialogs";
import { ModelingBasicInfoSection, ModelingEditorHeader } from "./ModelingEditorSections";
import { type ModelingFieldRow, ModelingFieldTable } from "./ModelingFieldTable";
import { ActionButton, StatusTag } from "./WorkspacePage";

export type ModelObjectType = "dimension" | "dimension-table" | "fact" | "aggregate" | "application";

export type ModelSelection = {
	type: ModelObjectType;
	code: string;
	name: string;
	layer: string;
	domain: string;
	isNew?: boolean;
};

type FieldRow = ModelingFieldRow;

type EditorProps = {
	selection: ModelSelection;
	model?: ModelSpecView | null;
	dimensionOperationId?: string | null;
	dimensionActorScope?: string;
	initialDimensionCommand?: CreateDimensionModelCommand | null;
	onBusyChange?: (busy: boolean) => void;
	onSaved: (model: CanonicalModelSpecView, completedOperationId?: string) => void | Promise<void>;
	onCancel?: () => void;
};

const MODEL_CONFIG: Record<ModelObjectType, { modelType: ModelSpecType; layer: ModelSpecLayer; label: string }> = {
	dimension: { modelType: "DIMENSION", layer: "DWD", label: "维度" },
	"dimension-table": { modelType: "DIMENSION", layer: "DWD", label: "维度表" },
	fact: { modelType: "FACT", layer: "DWD", label: "明细模型" },
	aggregate: { modelType: "SUMMARY", layer: "DWS", label: "汇总模型" },
	application: { modelType: "APPLICATION", layer: "ADS", label: "应用模型" },
};

const newField = (id: string): FieldRow => ({
	id,
	code: "",
	dataType: "STRING",
	displayName: "",
	role: "ATTRIBUTE",
	notNull: false,
	attributeCode: "",
	standardElementId: "",
	standardElementVersion: null,
	originalCode: "",
	sourceFieldRef: null,
	securityLevel: null,
	redundant: false,
	redundancySourceRef: null,
});

const isCanonical = (model?: ModelSpecView | null): model is CanonicalModelSpecView =>
	Boolean(model && model.compatibilityMode === "CANONICAL" && model.contractVersion === 2);

export const rowsFromModel = (model?: Pick<ModelSpecView, "id" | "fields" | "standardBindings"> | null): FieldRow[] =>
	model?.fields.length
		? model.fields.map((field, index) => {
				const binding = model.standardBindings.find((item) => item.fieldName === field.name);
				return {
					id: `${model.id}-${index}`,
					code: field.name,
					dataType: field.dataType,
					displayName: field.displayName || "",
					role: field.role,
					notNull: !field.nullable,
					attributeCode: field.dimensionAttributeCode || "",
					standardElementId: binding?.standardElementId || "",
					standardElementVersion: binding?.standardElementVersion || null,
					originalCode: field.name,
					sourceFieldRef: field.sourceFieldRef || null,
					securityLevel: field.securityLevel || null,
					redundant: field.redundant || false,
					redundancySourceRef: field.redundancySourceRef || null,
				};
			})
		: [newField("new-field-1")];

export const rowsFromCommand = (command?: CreateDimensionModelCommand | null): FieldRow[] => {
	if (!command?.modelSpec.fields.length) return [newField("new-field-1")];
	return command.modelSpec.fields.map((field, index) => {
		const binding = command.modelSpec.standardBindings.find((item) => item.fieldName === field.name);
		return {
			id: `pending-${index}`,
			code: field.name,
			dataType: field.dataType,
			displayName: field.displayName || "",
			role: field.role,
			notNull: !field.nullable,
			attributeCode: field.dimensionAttributeCode || "",
			standardElementId: binding?.standardElementId || "",
			standardElementVersion: binding?.standardElementVersion || null,
			originalCode: field.name,
			sourceFieldRef: field.sourceFieldRef || null,
			securityLevel: field.securityLevel || null,
			redundant: field.redundant || false,
			redundancySourceRef: field.redundancySourceRef || null,
		};
	});
};

export const modelSpecFieldFromRow = (row: FieldRow): ModelSpecField => ({
	name: row.code.trim(),
	displayName: row.displayName.trim(),
	dataType: row.dataType.trim(),
	nullable: !row.notNull,
	sourceFieldRef: row.sourceFieldRef,
	role: row.role,
	securityLevel: row.securityLevel,
	dimensionAttributeCode: row.attributeCode.trim() || null,
	redundant: row.redundant,
	redundancySourceRef: row.redundancySourceRef,
});

const safeErrorCode = (error: unknown, fallback: string) => {
	if (!error || typeof error !== "object") return fallback;
	const candidate = error as { response?: { status?: number; data?: { code?: string; message?: string } } };
	if (candidate.response?.status === 401 || candidate.response?.status === 403) return "MODEL_SPEC_WRITE_FORBIDDEN";
	return candidate.response?.data?.code || candidate.response?.data?.message || fallback;
};

export function ModelingEditor({
	selection,
	model,
	dimensionOperationId = null,
	dimensionActorScope = "",
	initialDimensionCommand = null,
	onBusyChange,
	onSaved,
	onCancel,
}: EditorProps) {
	const canMaintain = useCatalogMaintainerAccess();
	const userInfo = useUserInfo();
	const canonicalModel = isCanonical(model) ? model : null;
	const config = MODEL_CONFIG[selection.type];
	const [persistedModel, setPersistedModel] = useState<CanonicalModelSpecView | null>(canonicalModel);
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [categories, setCategories] = useState<WarehousePlanCategoryBindingView[]>([]);
	const [dimensionDefinitions, setDimensionDefinitions] = useState<DimensionDefinitionView[]>([]);
	const [standardOptions, setStandardOptions] = useState<ModelFieldStandardOption[]>([]);
	const pendingModel = initialDimensionCommand?.modelSpec || null;
	const pendingDefinitionRef =
		initialDimensionCommand?.definitionBinding.mode === "EXISTING"
			? initialDimensionCommand.definitionBinding.dimensionDefinitionRef
			: null;
	const pendingCreatedDefinition =
		initialDimensionCommand?.definitionBinding.mode === "CREATE"
			? initialDimensionCommand.definitionBinding.definition
			: null;
	const [planId, setPlanId] = useState(canonicalModel?.planId || pendingModel?.planId || "");
	const [domainId, setDomainId] = useState(canonicalModel?.domainId || pendingModel?.domainId || "");
	const [dimensionDefinitionId, setDimensionDefinitionId] = useState(
		canonicalModel?.dimensionDefinitionRef?.dimensionDefinitionId || pendingDefinitionRef?.dimensionDefinitionId || "",
	);
	const [dimensionDefinitionRevision, setDimensionDefinitionRevision] = useState(
		canonicalModel?.dimensionDefinitionRef?.revision || pendingDefinitionRef?.revision || 0,
	);
	const [name, setName] = useState(canonicalModel?.name || pendingModel?.name || "");
	const [description, setDescription] = useState(canonicalModel?.description || pendingModel?.description || "");
	const [variantCode, setVariantCode] = useState(canonicalModel?.variantCode || pendingModel?.variantCode || "");
	const [grainStatement, setGrainStatement] = useState(
		canonicalModel?.grain?.statement || pendingModel?.grain?.statement || "",
	);
	const [materialization, setMaterialization] = useState(
		canonicalModel?.materialization || pendingModel?.materialization || "table",
	);
	const [dimensionReuseScope, setDimensionReuseScope] = useState<"PLAN" | "DOMAIN" | "TENANT">(
		canonicalModel?.dimensionProfile?.reuseScope || pendingCreatedDefinition?.reuseScope || "DOMAIN",
	);
	const [dimensionScdType, setDimensionScdType] = useState<"NONE" | "TYPE1" | "TYPE2">(
		canonicalModel?.dimensionProfile?.scdPolicy.type || pendingModel?.dimensionProfile?.scdPolicy.type || "NONE",
	);
	const [rows, setRows] = useState<FieldRow[]>(() =>
		canonicalModel ? rowsFromModel(canonicalModel) : rowsFromCommand(initialDimensionCommand),
	);
	const [mode, setMode] = useState<"quick" | "code">("quick");
	const [insertCount, setInsertCount] = useState(1);
	const [dialog, setDialog] = useState<ModelingDialogKind>(null);
	const [visibleColumns, setVisibleColumns] = useState<Set<string>>(
		() => new Set(MODEL_FIELD_DISPLAY_COLUMNS.map(([key]) => key)),
	);
	const [contextLoading, setContextLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [checking, setChecking] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [notice, setNotice] = useState<string | null>(null);
	const [gates, setGates] = useState<ModelSpecStageGate[]>([]);
	const rowCounter = useRef(100);
	const modelCreateIdempotencyKeyRef = useRef(crypto.randomUUID());
	const mountedRef = useRef(true);
	const requestEpochRef = useRef(0);
	const saveAbortRef = useRef<AbortController | null>(null);

	useEffect(() => {
		const restoredModel = initialDimensionCommand?.modelSpec || null;
		const restoredRef =
			initialDimensionCommand?.definitionBinding.mode === "EXISTING"
				? initialDimensionCommand.definitionBinding.dimensionDefinitionRef
				: null;
		setPersistedModel(canonicalModel);
		setPlanId(canonicalModel?.planId || restoredModel?.planId || "");
		setDomainId(canonicalModel?.domainId || restoredModel?.domainId || "");
		setDimensionDefinitionId(
			canonicalModel?.dimensionDefinitionRef?.dimensionDefinitionId || restoredRef?.dimensionDefinitionId || "",
		);
		setDimensionDefinitionRevision(canonicalModel?.dimensionDefinitionRef?.revision || restoredRef?.revision || 0);
		setName(canonicalModel?.name || restoredModel?.name || "");
		setDescription(canonicalModel?.description || restoredModel?.description || "");
		setVariantCode(canonicalModel?.variantCode || restoredModel?.variantCode || "");
		setGrainStatement(canonicalModel?.grain?.statement || restoredModel?.grain?.statement || "");
		setMaterialization(canonicalModel?.materialization || restoredModel?.materialization || "table");
		setDimensionReuseScope(
			canonicalModel?.dimensionProfile?.reuseScope ||
				(initialDimensionCommand?.definitionBinding.mode === "CREATE"
					? initialDimensionCommand.definitionBinding.definition.reuseScope
					: null) ||
				"DOMAIN",
		);
		setDimensionScdType(
			canonicalModel?.dimensionProfile?.scdPolicy.type || restoredModel?.dimensionProfile?.scdPolicy.type || "NONE",
		);
		setRows(canonicalModel ? rowsFromModel(canonicalModel) : rowsFromCommand(initialDimensionCommand));
		setMode("quick");
		setError(null);
		setNotice(null);
		setGates([]);
		modelCreateIdempotencyKeyRef.current = crypto.randomUUID();
	}, [canonicalModel, initialDimensionCommand]);

	useEffect(() => {
		mountedRef.current = true;
		return () => {
			mountedRef.current = false;
			requestEpochRef.current += 1;
			saveAbortRef.current?.abort();
		};
	}, []);

	useEffect(() => {
		onBusyChange?.(saving);
		return () => onBusyChange?.(false);
	}, [onBusyChange, saving]);

	useEffect(() => {
		let active = true;
		setContextLoading(true);
		void Promise.all([listWarehousePlans(), listModelFieldStandardOptions()])
			.then(([planResult, standardResult]) => {
				if (!active) return;
				setPlans(planResult.filter((plan) => plan.lifecycleStatus !== "ARCHIVED"));
				setStandardOptions(standardResult);
			})
			.catch((cause) => {
				if (active) setError(safeErrorCode(cause, "MODEL_EDITOR_CONTEXT_READ_FAILED"));
			})
			.finally(() => {
				if (active) setContextLoading(false);
			});
		return () => {
			active = false;
		};
	}, []);

	useEffect(() => {
		let active = true;
		if (!planId) {
			setCategories([]);
			return () => {
				active = false;
			};
		}
		setContextLoading(true);
		void getWarehousePlanCategories(planId)
			.then((result) => {
				if (!active) return;
				const available = result.value.domainBindings.filter(
					(binding) => binding.confirmationStatus !== "EXCLUDED" && binding.resolutionStatus === "AVAILABLE",
				);
				setCategories(available);
				if (!domainId && available[0]) setDomainId(available[0].domainId);
			})
			.catch((cause) => {
				if (active) setError(safeErrorCode(cause, "WAREHOUSE_PLAN_CATEGORY_READ_FAILED"));
			})
			.finally(() => {
				if (active) setContextLoading(false);
			});
		return () => {
			active = false;
		};
	}, [domainId, planId]);

	useEffect(() => {
		let active = true;
		if (config.modelType !== "DIMENSION" || !domainId || selection.type === "dimension") {
			setDimensionDefinitions([]);
			return () => {
				active = false;
			};
		}
		void listDimensionDefinitions({ domainId, status: "CURRENT" })
			.then((result) => {
				if (!active) return;
				setDimensionDefinitions(result);
				if (!dimensionDefinitionId && result[0]) {
					setDimensionDefinitionId(result[0].id);
					setDimensionDefinitionRevision(result[0].revision);
				}
			})
			.catch((cause) => {
				if (active) setError(safeErrorCode(cause, "DIMENSION_DEFINITION_LIST_FAILED"));
			});
		return () => {
			active = false;
		};
	}, [config.modelType, dimensionDefinitionId, domainId, selection.type]);

	const codePreview = useMemo(
		() =>
			[
				`MODEL ${variantCode || "new_model"} {`,
				...rows.map(
					(row) => `  ${row.code || "new_field"} ${row.dataType} ${row.role}${row.notNull ? " NOT NULL" : ""};`,
				),
				"}",
			].join("\n"),
		[rows, variantCode],
	);

	const validRows = useMemo(
		() => rows.filter((row) => row.code.trim() && row.displayName.trim() && row.dataType.trim()),
		[rows],
	);
	const keyNames = useMemo(
		() => validRows.filter((row) => row.role === "KEY").map((row) => row.code.trim()),
		[validRows],
	);

	const patchRow = (id: string, patch: Partial<FieldRow>) => {
		setRows((current) => current.map((row) => (row.id === id ? { ...row, ...patch } : row)));
	};

	const addRows = () => {
		const additions = Array.from({ length: Math.max(1, insertCount) }, () =>
			newField(`new-field-${rowCounter.current++}`),
		);
		setRows((current) => [...current, ...additions]);
	};

	const buildStandardBindings = useCallback(
		(base?: Pick<UpdateModelSpecCommand, "standardBindings"> | null): ModelSpecStandardBinding[] =>
			validRows.flatMap<ModelSpecStandardBinding>((row) => {
				const prior = base?.standardBindings?.find(
					(binding) => binding.fieldName === (row.originalCode || row.code.trim()),
				);
				const fieldName = row.code.trim();
				if (row.standardElementId && row.standardElementVersion) {
					return [
						{
							...prior,
							fieldName,
							standardElementId: row.standardElementId,
							standardElementVersion: row.standardElementVersion,
						},
					];
				}
				if (prior?.referenceCode || prior?.measurementUnitId || prior?.securityLevel) {
					return [{ ...prior, fieldName, standardElementId: null, standardElementVersion: null }];
				}
				return [];
			}),
		[validRows],
	);

	const buildUpdate = useCallback(
		(base?: CanonicalModelSpecView | null): UpdateModelSpecCommand => {
			const restored = base || initialDimensionCommand?.modelSpec || null;
			return {
				planId,
				domainId,
				modelType: config.modelType,
				layer: config.layer,
				name: name.trim(),
				description: description.trim() || null,
				implementationMode: restored?.implementationMode || "DESIGNER_GENERATED",
				materialization,
				businessActivityRef: restored?.businessActivityRef || null,
				consumptionScenario: restored?.consumptionScenario || null,
				grain: { statement: grainStatement.trim(), keys: keyNames },
				factShape: config.modelType === "FACT" ? restored?.factShape || "TRANSACTION" : null,
				timeSemantics: config.modelType === "FACT" ? restored?.timeSemantics || null : null,
				generationStrategy: config.modelType === "DIMENSION" ? restored?.generationStrategy || null : null,
				dimensionProfile:
					config.modelType === "DIMENSION"
						? {
								hierarchies: restored?.dimensionProfile?.hierarchies || [],
								scdPolicy:
									dimensionScdType === "TYPE2"
										? { ...restored?.dimensionProfile?.scdPolicy, type: "TYPE2" }
										: { type: dimensionScdType },
							}
						: null,
				dataMartId: restored?.dataMartId || null,
				variantCode: variantCode.trim().toUpperCase() || null,
				fields: validRows.map(modelSpecFieldFromRow),
				sourceRefs: restored?.sourceRefs || [],
				dependsOn: restored?.dependsOn || [],
				dimensionRefs: restored?.dimensionRefs || [],
				metricRefs: restored?.metricRefs || [],
				standardBindings: buildStandardBindings(restored),
			};
		},
		[
			buildStandardBindings,
			config.layer,
			config.modelType,
			description,
			dimensionScdType,
			domainId,
			grainStatement,
			initialDimensionCommand,
			keyNames,
			materialization,
			name,
			planId,
			validRows,
			variantCode,
		],
	);

	const buildDimensionCreateCommand = (): CreateDimensionModelCommand => {
		if (!dimensionOperationId) throw new Error("DIMENSION_MODEL_OPERATION_ID_REQUIRED");
		const modelSpec = buildUpdate(null);
		if (
			modelSpec.modelType !== "DIMENSION" ||
			modelSpec.layer !== "DWD" ||
			modelSpec.implementationMode !== "DESIGNER_GENERATED"
		) {
			throw new Error("DIMENSION_MODEL_COMMAND_BOUNDARY_INVALID");
		}
		const exactModelSpec: CreateDimensionModelCommand["modelSpec"] = {
			...modelSpec,
			modelType: "DIMENSION",
			layer: "DWD",
			implementationMode: "DESIGNER_GENERATED",
			dimensionProfile: {
				hierarchies: modelSpec.dimensionProfile?.hierarchies || [],
				scdPolicy: modelSpec.dimensionProfile?.scdPolicy || { type: "NONE" },
			},
			fields: modelSpec.fields || [],
			sourceRefs: modelSpec.sourceRefs || [],
			dependsOn: modelSpec.dependsOn || [],
			dimensionRefs: modelSpec.dimensionRefs || [],
			metricRefs: modelSpec.metricRefs || [],
			standardBindings: modelSpec.standardBindings || [],
		};
		if (selection.type === "dimension") {
			if (!userInfo.id) throw new Error("DIMENSION_OWNER_REQUIRED");
			const priorDefinition =
				initialDimensionCommand?.definitionBinding.mode === "CREATE"
					? initialDimensionCommand.definitionBinding.definition
					: null;
			return {
				operationId: dimensionOperationId,
				definitionBinding: {
					mode: "CREATE",
					definition: {
						...priorDefinition,
						domainId,
						name: name.trim(),
						definition: description.trim() || name.trim(),
						ownerId: priorDefinition?.ownerId || userInfo.id,
						reuseScope: dimensionReuseScope,
						scopeType: priorDefinition?.scopeType || "DOMAIN",
						attributes: validRows.map((row, index) => ({
							code: (row.attributeCode.trim() || row.code.trim()).toUpperCase(),
							name: row.displayName.trim(),
							definition: row.displayName.trim(),
							primaryKey: row.role === "KEY",
							standardRef: row.standardElementId || null,
							standardVersion: row.standardElementVersion == null ? null : String(row.standardElementVersion),
							order: index + 1,
						})),
					},
				},
				modelSpec: exactModelSpec,
			};
		}
		const definition = dimensionDefinitions.find((item) => item.id === dimensionDefinitionId);
		const revision = dimensionDefinitionRevision || definition?.revision || 0;
		if (!dimensionDefinitionId || revision < 1) throw new Error("DIMENSION_DEFINITION_REQUIRED");
		return {
			operationId: dimensionOperationId,
			definitionBinding: {
				mode: "EXISTING",
				dimensionDefinitionRef: { dimensionDefinitionId, revision },
			},
			modelSpec: exactModelSpec,
		};
	};

	const save = async () => {
		setError(null);
		setNotice(null);
		if (!canMaintain) {
			setError("MODEL_SPEC_WRITE_FORBIDDEN");
			return;
		}
		if (!planId || !domainId || !name.trim() || !grainStatement.trim() || keyNames.length === 0) {
			setError("MODEL_SPEC_REQUIRED_CONTEXT_OR_GRAIN_MISSING");
			return;
		}
		if (validRows.length !== rows.length || validRows.length === 0) {
			setError("MODEL_SPEC_FIELD_INCOMPLETE");
			return;
		}
		setSaving(true);
		saveAbortRef.current?.abort();
		const requestController = new AbortController();
		saveAbortRef.current = requestController;
		const requestEpoch = ++requestEpochRef.current;
		const isCurrentRequest = () => mountedRef.current && requestEpochRef.current === requestEpoch;
		let base = persistedModel;
		try {
			if (!base) {
				if (config.modelType === "DIMENSION") {
					const command = buildDimensionCreateCommand();
					const issues = validateModelSpecUpdate(command.modelSpec);
					if (issues.length) {
						setError(issues.map((issue) => `${issue.field}:${issue.code}`).join("；"));
						return;
					}
					if (!dimensionActorScope) throw new Error("DIMENSION_MODEL_ACTOR_SCOPE_REQUIRED");
					await persistDimensionModelDraft(command, dimensionActorScope);
					const created = await createDimensionModel(command, requestController.signal);
					if (!isCurrentRequest()) return;
					const expectedDefinitionRevision =
						command.definitionBinding.mode === "CREATE" ? 2 : command.definitionBinding.dimensionDefinitionRef.revision;
					if (
						created.operationId !== command.operationId ||
						created.bindingMode !== command.definitionBinding.mode ||
						created.dimensionDefinitionRevision.revision !== expectedDefinitionRevision ||
						created.modelSpecRevision.revision !== 2 ||
						created.modelSpecRevision.id !== created.currentModelSpec.id ||
						created.currentModelSpec.contractVersion !== 2
					) {
						throw new Error("DIMENSION_MODEL_OPERATION_RESPONSE_INVALID");
					}
					base = created.currentModelSpec;
					setPersistedModel(base);
					setNotice(`MODEL_SPEC_SAVED_R${base.revision}`);
					await onSaved(base, command.operationId);
					return;
				} else {
					base = await createModelSpec({
						planId,
						domainId,
						modelType: config.modelType,
						name: name.trim(),
						description: description.trim() || null,
						variantCode: variantCode.trim().toUpperCase() || null,
						idempotencyKey: modelCreateIdempotencyKeyRef.current,
					} as Parameters<typeof createModelSpec>[0]);
				}
				if (!isCurrentRequest()) return;
				setPersistedModel(base);
			}
			const update = buildUpdate(base);
			const issues = validateModelSpecUpdate(update);
			if (issues.length) {
				setError(issues.map((issue) => `${issue.field}:${issue.code}`).join("；"));
				return;
			}
			const saved = await updateModelSpec(base, update);
			if (!isCurrentRequest()) return;
			setPersistedModel(saved);
			setNotice(`MODEL_SPEC_SAVED_R${saved.revision}`);
			await onSaved(saved);
		} catch (cause) {
			if (isCurrentRequest() && !requestController.signal.aborted) {
				setError(safeErrorCode(cause, cause instanceof Error ? cause.message : "MODEL_SPEC_SAVE_FAILED"));
			}
		} finally {
			if (isCurrentRequest()) setSaving(false);
		}
	};

	const cancel = () => {
		if (saving) return;
		onCancel?.();
	};

	const checkGates = async () => {
		if (!persistedModel) return;
		setChecking(true);
		setError(null);
		try {
			const result = await getModelSpecStageGates(persistedModel.id);
			setGates(result);
			const blocked = result.filter((gate) => gate.status === "BLOCKED").length;
			setNotice(blocked ? `MODEL_SPEC_GATES_BLOCKED_${blocked}` : "MODEL_SPEC_GATES_READY");
		} catch (cause) {
			setError(safeErrorCode(cause, "MODEL_SPEC_STAGE_GATE_READ_FAILED"));
		} finally {
			setChecking(false);
		}
	};

	return (
		<section aria-busy={saving} className="dm-model-editor">
			<ModelingEditorHeader
				canMaintain={canMaintain}
				checking={checking}
				contextLoading={contextLoading}
				error={error}
				gates={gates}
				model={persistedModel}
				modelLabel={config.label}
				name={name}
				notice={notice}
				onCancel={onCancel ? cancel : undefined}
				onCheckGates={() => void checkGates()}
				onRefresh={() => {
					setRows(rowsFromModel(persistedModel));
					setError(null);
					setNotice(null);
				}}
				onRelease={() => setDialog("release")}
				onSave={() => void save()}
				saving={saving}
			/>

			<div className="dm-model-editor__scroll">
				<ModelingBasicInfoSection
					categories={categories}
					description={description}
					dimensionDefinitionId={dimensionDefinitionId}
					dimensionDefinitions={dimensionDefinitions}
					dimensionReuseScope={dimensionReuseScope}
					dimensionScdType={dimensionScdType}
					domainId={domainId}
					grainStatement={grainStatement}
					isDimension={config.modelType === "DIMENSION"}
					isDimensionDefinition={selection.type === "dimension"}
					isDimensionTable={selection.type === "dimension-table"}
					materialization={materialization}
					modelLabel={config.label}
					modelLayer={config.layer}
					name={name}
					onDescriptionChange={setDescription}
					onDimensionDefinitionChange={(nextId) => {
						setDimensionDefinitionId(nextId);
						setDimensionDefinitionRevision(dimensionDefinitions.find((item) => item.id === nextId)?.revision || 0);
					}}
					onDimensionReuseScopeChange={setDimensionReuseScope}
					onDimensionScdTypeChange={setDimensionScdType}
					onDomainChange={(nextDomainId) => {
						setDomainId(nextDomainId);
						setDimensionDefinitionId("");
						setDimensionDefinitionRevision(0);
					}}
					onGrainStatementChange={setGrainStatement}
					onMaterializationChange={setMaterialization}
					onNameChange={setName}
					onPlanChange={(nextPlanId) => {
						setPlanId(nextPlanId);
						setDomainId("");
						setDimensionDefinitionId("");
						setDimensionDefinitionRevision(0);
					}}
					onVariantCodeChange={setVariantCode}
					pendingDefinitionRef={pendingDefinitionRef}
					persisted={Boolean(persistedModel)}
					placeholder="UPPER_SNAKE_CASE"
					planId={planId}
					plans={plans}
					variantCode={variantCode}
				/>

				<section className="dm-editor-section">
					<div className="dm-editor-section__title">
						<h2>字段管理</h2>
						<fieldset className="dm-segmented">
							<legend>字段编辑模式</legend>
							<button className={mode === "quick" ? "is-active" : ""} onClick={() => setMode("quick")} type="button">
								<ListChecks aria-hidden="true" size={13} />
								快捷模式
							</button>
							<button className={mode === "code" ? "is-active" : ""} onClick={() => setMode("code")} type="button">
								<Code2 aria-hidden="true" size={13} />
								定义预览
							</button>
						</fieldset>
					</div>
					{mode === "code" ? (
						<div className="dm-code-editor">
							<div>
								<span>业务模型定义预览</span>
								<StatusTag tone="info">只读</StatusTag>
							</div>
							<textarea aria-label="业务模型定义" readOnly spellCheck={false} value={codePreview} />
						</div>
					) : (
						<>
							<div className="dm-field-actions">
								<ActionButton
									onClick={() =>
										setRows((current) => current.filter((row) => row.code || row.displayName || row.attributeCode))
									}
								>
									移除空白行
								</ActionButton>
								<label>
									插入
									<input
										aria-label="插入行数"
										max={20}
										min={1}
										onChange={(event) => setInsertCount(Number(event.target.value) || 1)}
										type="number"
										value={insertCount}
									/>
									行
								</label>
								<ActionButton onClick={addRows}>添加</ActionButton>
								<span className="dm-field-actions__spacer" />
								<ActionButton onClick={() => setDialog("display")}>
									<Settings2 aria-hidden="true" size={14} />
									字段显示设置
								</ActionButton>
							</div>
							<ModelingFieldTable
								onDelete={(id) => setRows((current) => current.filter((item) => item.id !== id))}
								onPatch={patchRow}
								rows={rows}
								standardOptions={standardOptions}
								visibleColumns={visibleColumns}
							/>
						</>
					)}
				</section>
			</div>

			<ModelingDialogs
				dialog={dialog}
				model={persistedModel}
				onClose={() => setDialog(null)}
				onToggleColumn={(key, checked) =>
					setVisibleColumns((current) => {
						const next = new Set(current);
						if (checked) next.add(key);
						else next.delete(key);
						return next;
					})
				}
				rows={rows}
				selectionCode={variantCode || selection.code}
				visibleColumns={visibleColumns}
			/>
		</section>
	);
}
