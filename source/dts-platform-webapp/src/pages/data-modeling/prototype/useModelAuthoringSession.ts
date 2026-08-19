import { type Dispatch, type SetStateAction, useCallback, useEffect, useState } from "react";
import type { DbtDraftFile, DbtImplementationDraft } from "@/api/dbtImplementationDraftApi";
import {
	commitModelAuthoringDraft,
	createModelAuthoringDraft,
	getModelAuthoringContext,
	type ModelAuthoringCommit,
	type ModelAuthoringContext,
	type ModelAuthoringProjectionNode,
	type ModelAuthoringValidation,
	saveModelAuthoringDraft,
	validateModelAuthoringDraft,
} from "@/api/modelAuthoringApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { newModelingIdempotencyKey } from "./modelingIdempotency";
import {
	isModelSpecDraft,
	type ModelDraft,
	type ModelDraftValidationErrors,
	type ModelWorkbenchContext,
	modelDraftFromAuthoringSnapshot,
	modelDraftToAuthoringSnapshot,
	modelDraftToUpdateCommand,
	prepareModelDraftForSave,
	validateModelDraftInput,
} from "./services/modelWorkbenchService";
import { type ModelingRequestFailure, normalizeModelingRequestFailure } from "./services/planningProjectionService";

type AuthoringBusy = "load" | "create" | "save" | "validate" | "commit" | "";
type AuthoringView = "VISUAL" | "CODE";

type AuthoringSession = {
	context: ModelAuthoringContext;
	draft: DbtImplementationDraft;
	files: DbtDraftFile[];
};

type UseModelAuthoringSessionOptions = {
	canMaintain: boolean;
	context: ModelWorkbenchContext | null;
	dimensionDefinitions: DimensionDefinitionView[];
	dirty: boolean;
	draft: ModelDraft | null;
	loadWorkbench: (preferredModelId?: string) => Promise<void>;
	ownerId: string;
	replaceDraft: (draft: ModelDraft | null) => void;
	selectedModel: ModelSpecView | null;
	selectedModelId: string;
	setContext: Dispatch<SetStateAction<ModelWorkbenchContext | null>>;
	setFailure: Dispatch<SetStateAction<ModelingRequestFailure | null>>;
	setValidationErrors: Dispatch<SetStateAction<ModelDraftValidationErrors>>;
	show: (message: string) => void;
};

const authoringFilesOf = (draft?: DbtImplementationDraft | null): DbtDraftFile[] =>
	draft?.sourceBundle?.files.map(({ path, content }) => ({ path, content })) || [];

const hasStructuredVisualSnapshot = (draft: DbtImplementationDraft): boolean => {
	const snapshot = draft.modelSpecSnapshot;
	return Boolean(snapshot && "modelSpec" in snapshot && snapshot.schemaVersion === 1 && snapshot.visualImplementation);
};

export function useModelAuthoringSession({
	canMaintain,
	context,
	dimensionDefinitions,
	dirty,
	draft,
	loadWorkbench,
	ownerId,
	replaceDraft,
	selectedModel,
	selectedModelId,
	setContext,
	setFailure,
	setValidationErrors,
	show,
}: UseModelAuthoringSessionOptions) {
	const [authoringContext, setAuthoringContext] = useState<ModelAuthoringContext | null>(null);
	const [files, setFiles] = useState<DbtDraftFile[]>([]);
	const [codeDirty, setCodeDirty] = useState(false);
	const [validation, setValidation] = useState<ModelAuthoringValidation | null>(null);
	const [commit, setCommit] = useState<ModelAuthoringCommit | null>(null);
	const [busy, setBusy] = useState<AuthoringBusy>("");
	const [conflict, setConflict] = useState(false);
	const [failure, setAuthoringFailure] = useState("");
	const [focusNode, setFocusNode] = useState<ModelAuthoringProjectionNode | null>(null);

	const reset = useCallback(() => {
		setAuthoringContext(null);
		setFiles([]);
		setCodeDirty(false);
		setValidation(null);
		setCommit(null);
		setBusy("");
		setConflict(false);
		setAuthoringFailure("");
		setFocusNode(null);
	}, []);

	const recordFailure = useCallback(
		(error: unknown, fallback: string) => {
			const status = Number((error as { response?: { status?: unknown } } | null)?.response?.status ?? 0);
			setConflict(status === 409 || status === 412);
			const normalized = normalizeModelingRequestFailure(error, fallback);
			setAuthoringFailure(normalized.message);
			setFailure(normalized);
		},
		[setFailure],
	);

	const createSession = async (targetPhysicalName = ""): Promise<AuthoringSession> => {
		if (!draft || !isModelSpecDraft(draft) || !draft.base || !authoringContext) {
			throw new Error("请先打开已保存的模型");
		}
		const normalizedTarget = (targetPhysicalName || draft.physicalName).trim();
		if (!authoringContext.implementation && !/^[a-z][a-z0-9_]{0,62}$/.test(normalizedTarget)) {
			throw new Error("请填写有效的目标物理表名：仅支持小写字母、数字和下划线，且必须以字母开头");
		}
		const created = await createModelAuthoringDraft(draft.base.id, {
			intent: authoringContext.publishedForkRequired ? "FORK_PUBLISHED" : "EDIT_DRAFT",
			baseModelRevision: authoringContext.model.revision,
			baseModelChecksum: authoringContext.model.checksum,
			baseImplementationRevision: authoringContext.implementation?.implementationRevision || null,
			baseImplementationChecksum: authoringContext.implementation?.implementationChecksum || null,
			targetPhysicalName: authoringContext.implementation ? null : normalizedTarget,
			idempotencyKey: newModelingIdempotencyKey(),
		});
		const nextFiles = authoringFilesOf(created.draft);
		const nextContext: ModelAuthoringContext = {
			...authoringContext,
			model: created.model,
			provenance: created.provenance,
			projection: created.projection,
			openDraft: created.draft,
			allowedActions: created.allowedActions,
			publishedForkRequired: false,
		};
		setAuthoringContext(nextContext);
		setFiles(nextFiles);
		setCodeDirty(false);
		setValidation(null);
		setCommit(null);
		setConflict(false);
		setContext((current) =>
			current
				? { ...current, models: [...current.models.filter((item) => item.id !== created.model.id), created.model] }
				: current,
		);
		const hydrated = modelDraftFromAuthoringSnapshot(
			created.model,
			authoringContext.implementation || null,
			created.draft.modelSpecSnapshot ||
				modelDraftToUpdateCommand(prepareModelDraftForSave(draft, dimensionDefinitions)),
		);
		replaceDraft({ ...hydrated, physicalName: normalizedTarget || hydrated.physicalName });
		show(
			authoringContext.publishedForkRequired ? `已创建新草稿版本：r${created.model.revision}` : "模型创作草稿已创建",
		);
		return { context: nextContext, draft: created.draft, files: nextFiles };
	};

	const ensureSession = async (targetPhysicalName = ""): Promise<AuthoringSession> => {
		const open = authoringContext?.openDraft;
		if (authoringContext && open && (open.state === "DRAFT" || open.state === "VALIDATED")) {
			return { context: authoringContext, draft: open, files };
		}
		return createSession(targetPhysicalName);
	};

	const persist = async (
		activeView: AuthoringView,
		preparedInput?: ReturnType<typeof prepareModelDraftForSave>,
	): Promise<DbtImplementationDraft> => {
		if (!draft || !isModelSpecDraft(draft) || !draft.base) throw new Error("请先保存模型定义");
		const prepared = preparedInput || prepareModelDraftForSave(draft, dimensionDefinitions);
		const nextValidationErrors = validateModelDraftInput(prepared);
		setValidationErrors(nextValidationErrors);
		if (Object.keys(nextValidationErrors).length) throw new Error("请先修正模型定义中的必填项");
		const session = await ensureSession(prepared.physicalName);
		const nextFiles = codeDirty ? files : session.files;
		const saved = await saveModelAuthoringDraft(session.context.model.id, session.draft.draftId, {
			expectedEtag: session.draft.etag,
			modelSpecSnapshot: modelDraftToAuthoringSnapshot(
				prepared,
				{ ownerId, dimensionDefinitions, models: context?.models || [] },
				!codeDirty &&
					(hasStructuredVisualSnapshot(session.draft) ||
						!session.context.implementation ||
						Boolean(prepared.implementationInputMode)),
			),
			files: nextFiles,
			activeView: codeDirty ? "CODE" : activeView,
		});
		const nextOpen: DbtImplementationDraft = {
			...session.draft,
			state: "DRAFT",
			etag: saved.etag,
			modelSpecSnapshot: saved.modelSpecSnapshot,
			projectionSummary: saved.projection,
		};
		setAuthoringContext({ ...session.context, openDraft: nextOpen, projection: saved.projection });
		setFiles(saved.files);
		setCodeDirty(false);
		setValidation(null);
		setCommit(null);
		setConflict(false);
		setAuthoringFailure("");
		replaceDraft(
			modelDraftFromAuthoringSnapshot(
				session.context.model,
				session.context.implementation || null,
				saved.modelSpecSnapshot,
			),
		);
		return nextOpen;
	};

	const create = async (targetPhysicalName = "") => {
		if (busy || !canMaintain) return;
		setBusy("create");
		setAuthoringFailure("");
		try {
			await createSession(targetPhysicalName);
		} catch (error) {
			recordFailure(error, authoringContext?.publishedForkRequired ? "新草稿版本创建失败。" : "模型草稿创建失败。");
		} finally {
			setBusy("");
		}
	};

	const save = async (activeView: AuthoringView) => {
		if (busy || !canMaintain) return false;
		setBusy("save");
		setAuthoringFailure("");
		try {
			await persist(activeView);
			show("模型创作草稿已保存");
			return true;
		} catch (error) {
			recordFailure(error, "模型创作草稿保存失败。");
			return false;
		} finally {
			setBusy("");
		}
	};

	const validate = async (activeView: AuthoringView) => {
		if (busy || !canMaintain) return;
		setBusy("validate");
		setAuthoringFailure("");
		try {
			let open = authoringContext?.openDraft || null;
			if (!open || dirty || codeDirty) open = await persist(activeView);
			const checked = await validateModelAuthoringDraft(
				authoringContext?.model.id || selectedModelId,
				open.draftId,
				open.etag,
			);
			setValidation(checked);
			if (checked.implementationValidation) {
				const nextOpen: DbtImplementationDraft = {
					...open,
					state: "VALIDATED",
					etag: checked.implementationValidation.etag,
					expiresAt: checked.implementationValidation.expiresAt,
				};
				setAuthoringContext((current) => (current ? { ...current, openDraft: nextOpen } : current));
			}
			show(checked.modelIssues.length || checked.projectionIssues.length ? "校验完成，请处理诊断" : "模型草稿校验通过");
		} catch (error) {
			recordFailure(error, "模型创作草稿校验失败。");
		} finally {
			setBusy("");
		}
	};

	const commitDraft = async () => {
		const open = authoringContext?.openDraft;
		const checked = validation?.implementationValidation;
		if (busy || !canMaintain || !authoringContext || !open || !checked) return;
		setBusy("commit");
		setAuthoringFailure("");
		try {
			const committed = await commitModelAuthoringDraft(authoringContext.model.id, open.draftId, {
				expectedEtag: checked.etag,
				validatedChecksum: checked.validatedChecksum,
				dependencyChecksum: checked.dependencyValidation?.dependencyChecksum,
				idempotencyKey: newModelingIdempotencyKey(),
			});
			setCommit(committed);
			setCodeDirty(false);
			show(`模型实现已提交：模型 r${committed.receipt.modelRevision}`);
			await loadWorkbench(authoringContext.model.id);
		} catch (error) {
			recordFailure(error, "模型实现提交失败。");
		} finally {
			setBusy("");
		}
	};

	const changeFiles = (nextFiles: DbtDraftFile[]) => {
		setFiles(nextFiles);
		setCodeDirty(true);
		setValidation(null);
		setCommit(null);
	};

	const invalidateValidation = () => {
		setValidation(null);
		setCommit(null);
	};

	const selectedModelIdValue = selectedModel?.id || "";
	const selectedModelRevision = selectedModel?.revision;
	const selectedModelChecksum = selectedModel?.checksum;
	// biome-ignore lint/correctness/useExhaustiveDependencies: the same model id can advance to a new revision or checksum after fork and commit.
	useEffect(() => {
		if (!selectedModelIdValue) {
			reset();
			return;
		}
		let active = true;
		setAuthoringContext(null);
		setFiles([]);
		setValidation(null);
		setCommit(null);
		setCodeDirty(false);
		setConflict(false);
		setAuthoringFailure("");
		setBusy("load");
		void getModelAuthoringContext(selectedModelIdValue)
			.then((value) => {
				if (!active) return;
				setAuthoringContext(value);
				setFiles(authoringFilesOf(value.openDraft));
				if (value.openDraft?.modelSpecSnapshot) {
					replaceDraft(
						modelDraftFromAuthoringSnapshot(
							value.model,
							value.implementation || null,
							value.openDraft.modelSpecSnapshot,
						),
					);
				}
			})
			.catch((error) => {
				if (active) setAuthoringFailure(normalizeModelingRequestFailure(error, "模型创作上下文读取失败。").message);
			})
			.finally(() => {
				if (active) setBusy("");
			});
		return () => {
			active = false;
		};
	}, [replaceDraft, reset, selectedModelChecksum, selectedModelIdValue, selectedModelRevision]);

	return {
		busy,
		changeFiles,
		codeDirty,
		commit,
		commitDraft,
		conflict,
		context: authoringContext,
		create,
		failure,
		files,
		focusNode,
		invalidateValidation,
		save,
		setFocusNode,
		validate,
		validation,
	};
}
