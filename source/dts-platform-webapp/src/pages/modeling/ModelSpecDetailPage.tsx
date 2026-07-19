import { Alert, Button, Card, Descriptions, Form, Modal, Space, Spin, Tabs, Tag, Typography } from "antd";
import { ArrowLeft, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { getModelSpec, listModelSpecs, updateModelSpec } from "@/api/modelSpecApi";
import { useCatalogDomainOptions } from "@/hooks/useCatalogDomainOptions";
import { useParams } from "@/routes/hooks";
import { useUserRoles } from "@/store/userStore";
import { ModelSpecEditorFields, type ModelSpecSelectOption } from "./components/ModelSpecEditorFields";
import { ModelSpecFieldsTab } from "./components/ModelSpecFieldsTab";
import { ModelSpecStandardsTab } from "./components/ModelSpecStandardsTab";
import { modelSpecCatalogPath, modelSpecDetailPath, resolveModelSpecDetailTab } from "./modelSpecDetailNavigation";
import {
	type CanonicalModelSpecView,
	type ModelSpecCasToken,
	type ModelSpecRevisionConflictDetails,
	type ModelSpecView,
	validateModelSpecUpdate,
} from "./modelSpecV2Contract";
import {
	buildModelSpecUpdateCommand,
	isModelSpecStatusReadonly,
	MODEL_STATUS_LABELS,
	MODEL_TYPE_LABELS,
	type ModelSpecDraft,
	modelSpecDraftFromView,
	modelSpecErrorMessage,
	modelSpecIssueMessage,
	modelSpecRevisionConflict,
} from "./modelSpecWorkbench";
import { hasWarehousePlanCreateAccess } from "./warehousePlanCreateFlow";

const { Text, Title } = Typography;

const issueField = (field: string): keyof ModelSpecDraft => {
	if (field === "grain") return "grainStatement";
	if (field === "fields") return "grainKeysText";
	if (field === "sourceRefs") return "sources";
	if (field === "dependsOn") return "upstreamIds";
	if (field === "dimensionRefs") return "dimensionRefIds";
	if (field === "timeSemantics") return "timeSemanticsType";
	return field as keyof ModelSpecDraft;
};

export default function ModelSpecDetailPage() {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const params = useParams();
	const modelSpecId = String(params.modelSpecId || "").trim();
	const userRoles = useUserRoles();
	const roleAllowsEdit = hasWarehousePlanCreateAccess(userRoles);
	const [form] = Form.useForm<ModelSpecDraft>();
	const [model, setModel] = useState<ModelSpecView | null>(null);
	const [availableModels, setAvailableModels] = useState<CanonicalModelSpecView[]>([]);
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [loadError, setLoadError] = useState("");
	const [saveError, setSaveError] = useState("");
	const [writeDenied, setWriteDenied] = useState(false);
	const [statusChanged, setStatusChanged] = useState(false);
	const [conflict, setConflict] = useState<ModelSpecRevisionConflictDetails | null>(null);
	const loadRequestRef = useRef(0);
	const { labelByKey } = useCatalogDomainOptions();
	const canonicalModel = model?.compatibilityMode === "CANONICAL" ? model : null;
	const statusAllowsEdit = canonicalModel?.status === "DRAFT";
	const canEdit = Boolean(canonicalModel && statusAllowsEdit && roleAllowsEdit && !writeDenied && !statusChanged);
	const activeTab = useMemo(() => resolveModelSpecDetailTab(searchParams), [searchParams]);

	const load = useCallback(async () => {
		const requestId = ++loadRequestRef.current;
		setLoading(true);
		setLoadError("");
		setSaveError("");
		setConflict(null);
		setStatusChanged(false);
		try {
			const detail = await getModelSpec(modelSpecId);
			if (requestId !== loadRequestRef.current) return;
			setModel(detail);
			setWriteDenied(false);
			if (detail.compatibilityMode === "CANONICAL") {
				form.setFieldsValue(modelSpecDraftFromView(detail));
			}
			try {
				const list = await listModelSpecs();
				if (requestId !== loadRequestRef.current) return;
				setAvailableModels(
					(Array.isArray(list) ? list : []).filter(
						(candidate): candidate is CanonicalModelSpecView =>
							candidate.compatibilityMode === "CANONICAL" && candidate.id !== modelSpecId,
					),
				);
			} catch {
				if (requestId === loadRequestRef.current) setAvailableModels([]);
			}
		} catch (error) {
			if (requestId !== loadRequestRef.current) return;
			setModel(null);
			setLoadError(modelSpecErrorMessage(error));
		} finally {
			if (requestId === loadRequestRef.current) setLoading(false);
		}
	}, [form, modelSpecId]);

	useEffect(() => {
		void load();
		return () => {
			loadRequestRef.current += 1;
		};
	}, [load]);

	const selectableModels = useMemo(() => {
		if (!canonicalModel) return [];
		return availableModels.filter(
			(candidate) => candidate.planId === canonicalModel.planId || candidate.status === "PUBLISHED",
		);
	}, [availableModels, canonicalModel]);
	const upstreamOptions = useMemo<ModelSpecSelectOption[]>(
		() =>
			selectableModels.map((candidate) => ({
				value: candidate.id,
				label: `${candidate.name} · ${MODEL_TYPE_LABELS[candidate.modelType]} · r${candidate.revision}`,
			})),
		[selectableModels],
	);
	const dimensionOptions = useMemo<ModelSpecSelectOption[]>(
		() =>
			selectableModels
				.filter((candidate) => candidate.modelType === "DIMENSION")
				.map((candidate) => ({ value: candidate.id, label: `${candidate.name} · r${candidate.revision}` })),
		[selectableModels],
	);

	const save = async (override?: ModelSpecCasToken) => {
		if (!canonicalModel || !canEdit) return;
		setSaveError("");
		try {
			await form.validateFields();
			const values = form.getFieldsValue(true);
			const command = buildModelSpecUpdateCommand(values, selectableModels);
			const issues = validateModelSpecUpdate(command);
			if (issues.length > 0) {
				form.setFields(
					issues.map((issue) => ({
						name: issueField(issue.field),
						errors: [modelSpecIssueMessage(issue.code)],
					})),
				);
				setSaveError("请补齐标红字段后再保存");
				return;
			}
			setSaving(true);
			const updated = await updateModelSpec(
				override || { id: canonicalModel.id, revision: canonicalModel.revision, checksum: canonicalModel.checksum },
				command,
			);
			setModel(updated);
			form.setFieldsValue(modelSpecDraftFromView(updated));
			setConflict(null);
			setStatusChanged(false);
			setSaveError("");
		} catch (error) {
			if (error && typeof error === "object" && "errorFields" in error) return;
			const latest = modelSpecRevisionConflict(error);
			if (latest) setConflict(latest);
			if ((error as { response?: { status?: number } })?.response?.status === 403) setWriteDenied(true);
			if (isModelSpecStatusReadonly(error)) {
				setStatusChanged(true);
				setSaveError("");
			} else {
				setSaveError(modelSpecErrorMessage(error));
			}
		} finally {
			setSaving(false);
		}
	};

	const retryConflict = () => {
		if (!canonicalModel || !conflict) return;
		void save({ id: canonicalModel.id, revision: conflict.currentRevision, checksum: conflict.currentChecksum });
	};

	const discardAndReload = () => {
		Modal.confirm({
			title: "加载最新版本？",
			content: "当前未保存输入将被最新版本替换。",
			okText: "加载最新版本",
			cancelText: "继续编辑",
			onOk: () => load(),
		});
	};

	const changeTab = (key: string) => {
		if (!canonicalModel) return;
		const tab = resolveModelSpecDetailTab(new URLSearchParams({ tab: key }));
		navigate(modelSpecDetailPath(canonicalModel.id, tab, canonicalModel.planId), { replace: true });
	};

	if (loading) {
		return (
			<div className="flex min-h-[360px] items-center justify-center">
				<Spin tip="正在加载模型" />
			</div>
		);
	}

	if (!model) {
		return (
			<div className="p-4">
				<Alert
					type="error"
					showIcon
					message={loadError || "模型不可访问"}
					action={
						<Space>
							<Button size="small" onClick={() => navigate("/modeling/models")}>
								返回模型中心
							</Button>
							<Button size="small" onClick={() => void load()}>
								<RefreshCw size={14} />
								重试
							</Button>
						</Space>
					}
				/>
			</div>
		);
	}

	const planOptions: ModelSpecSelectOption[] = model.planId ? [{ value: model.planId, label: "当前建设计划" }] : [];
	const domainOptions: ModelSpecSelectOption[] = model.domainId
		? [{ value: model.domainId, label: labelByKey[model.domainId] || "当前业务分类" }]
		: [];

	return (
		<div className="p-4" data-testid="model-spec-detail-page">
			<div className="mb-4 flex flex-wrap items-start justify-between gap-3">
				<div>
					<Button
						type="link"
						className="!px-0"
						onClick={() => navigate(modelSpecCatalogPath(model.modelType, model.planId, model.domainId))}
					>
						<ArrowLeft size={15} />
						{model.modelType === "DIMENSION" ? "返回维度目录" : "返回模型中心"}
					</Button>
					<Title level={3} className="!mb-1 !mt-1">
						{model.name}
					</Title>
					<Space wrap>
						<Tag color="blue">{MODEL_TYPE_LABELS[model.modelType]}</Tag>
						<Tag>{model.layer}</Tag>
						<Tag>
							{model.compatibilityMode === "LEGACY_READONLY"
								? "兼容只读"
								: MODEL_STATUS_LABELS[model.status] || model.status}
						</Tag>
						<Text type="secondary">版本 r{model.revision}</Text>
					</Space>
				</div>
				{canEdit && activeTab !== "standards" ? (
					<Button type="primary" loading={saving} onClick={() => void save()}>
						保存草稿
					</Button>
				) : null}
			</div>

			{model.compatibilityMode === "LEGACY_READONLY" ? (
				<Alert
					className="mb-3"
					type="warning"
					showIcon
					message="这是迁移期历史模型，可浏览但不能在 canonical 页面修改"
				/>
			) : null}
			{!roleAllowsEdit || writeDenied ? (
				<Alert className="mb-3" type="info" showIcon message="当前账号为只读浏览；编辑需要计划维护权限" />
			) : null}
			{statusChanged ? (
				<Alert
					className="mb-3"
					type="warning"
					showIcon
					message="模型状态已变化，当前输入已保留；请加载最新状态后继续"
					action={
						<Button size="small" onClick={() => void load()}>
							加载最新状态
						</Button>
					}
				/>
			) : null}
			{canonicalModel && !statusAllowsEdit ? (
				<Alert
					className="mb-3"
					type="info"
					showIcon
					message={`当前状态为${MODEL_STATUS_LABELS[canonicalModel.status] || canonicalModel.status}；只有草稿可编辑`}
				/>
			) : null}
			{saveError ? <Alert className="mb-3" type="error" showIcon message={saveError} /> : null}
			{conflict ? (
				<Alert
					className="mb-3"
					type="warning"
					showIcon
					message={`服务器当前为 r${conflict.currentRevision}，你的输入仍保留在页面中`}
					action={
						<Space wrap>
							<Button size="small" onClick={retryConflict}>
								保留当前输入并基于 r{conflict.currentRevision} 重试
							</Button>
							<Button size="small" onClick={discardAndReload}>
								放弃当前输入，加载最新版本
							</Button>
						</Space>
					}
				/>
			) : null}

			{canonicalModel ? (
				<Card>
					<Form form={form} layout="vertical" requiredMark={false} disabled={saving}>
						<Tabs
							activeKey={activeTab}
							onChange={changeTab}
							items={[
								{
									key: "design",
									label: "模型设计",
									forceRender: true,
									children: (
										<ModelSpecEditorFields
											form={form}
											planOptions={planOptions}
											domainOptions={domainOptions}
											upstreamOptions={upstreamOptions}
											dimensionOptions={dimensionOptions}
											lockPlan
											lockDomain
											lockModelType
											readOnly={!canEdit}
										/>
									),
								},
								{
									key: "fields",
									label: "字段设计",
									forceRender: true,
									children: (
										<ModelSpecFieldsTab
											readOnly={!canEdit}
											persistedFieldNames={canonicalModel.fields.map((field) => field.name)}
										/>
									),
								},
								{
									key: "standards",
									label: "字段标准",
									children: <ModelSpecStandardsTab model={canonicalModel} />,
								},
							]}
						/>
					</Form>
				</Card>
			) : (
				<Card>
					<Descriptions column={1} size="small" bordered>
						<Descriptions.Item label="模型名称">{model.name}</Descriptions.Item>
						<Descriptions.Item label="每行含义">{model.grain?.statement || "未记录"}</Descriptions.Item>
						<Descriptions.Item label="粒度键">{model.grain?.keys.join("、") || "未记录"}</Descriptions.Item>
						<Descriptions.Item label="来源">
							{model.sourceRefs.length > 0 ? model.sourceRefs.map((source) => source.ref).join("、") : "未记录"}
						</Descriptions.Item>
					</Descriptions>
				</Card>
			)}
		</div>
	);
}
