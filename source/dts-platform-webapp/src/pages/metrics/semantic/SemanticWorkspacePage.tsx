import {
	BranchesOutlined,
	CodeOutlined,
	DashboardOutlined,
	DatabaseOutlined,
	FunctionOutlined,
	PartitionOutlined,
	PlayCircleOutlined,
	ProjectOutlined,
	RocketOutlined,
	TableOutlined,
} from "@ant-design/icons";
import { type Edge, MarkerType, type Node } from "@xyflow/react";
import {
	Alert,
	Button,
	Card,
	Col,
	Empty,
	Form,
	Input,
	Modal,
	message,
	Row,
	Segmented,
	Select,
	Space,
	Statistic,
	Steps,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import type React from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { type DatasetField, getDatasetFields, getDomainTree, listDatasets } from "@/api/platformApi";
import {
	approveSemanticModelReview,
	createSemanticBusinessObject,
	createSemanticDimension,
	createSemanticMetric,
	createSemanticModel,
	createSemanticSubjectDomain,
	generateSemanticModelArtifacts,
	getSemanticModelBindings,
	listSemanticBusinessObjects,
	listSemanticDimensions,
	listSemanticGeneratedArtifacts,
	listSemanticMetrics,
	listSemanticModelReviewLogs,
	listSemanticModelRuns,
	listSemanticModels,
	listSemanticObjectTableMappings,
	listSemanticSubjectDomains,
	previewSemanticModelData,
	publishSemanticModelToDbt,
	registerSemanticBiDataset,
	registerSemanticLineage,
	rejectSemanticModelReview,
	type SemanticBusinessObject,
	type SemanticDimension,
	type SemanticGeneratedArtifact,
	type SemanticMetric,
	type SemanticModel,
	type SemanticModelPreview,
	type SemanticModelReviewLog,
	type SemanticModelRun,
	type SemanticSubjectDomain,
	saveSemanticModelBindings,
	saveSemanticObjectTableMappings,
	submitSemanticModelReview,
	triggerSemanticModelRun,
} from "@/api/semanticModelingApi";
import { PageHeader } from "@/components/page-header";
import { CompactTable } from "@/components/table";
import { VisualFlowCanvas, type VisualFlowDropEvent } from "@/components/visual-canvas/VisualFlowCanvas";
import { useRouter } from "@/routes/hooks";

const { Text, Paragraph } = Typography;

type DatasetOption = {
	id: string;
	name: string;
	table?: string;
	layer?: string;
	database?: string;
	schema?: string;
};

type GovernanceDomainOption = {
	id: string;
	code?: string;
	name: string;
	label: string;
};

type JoinTable = {
	id: string;
	name: string;
};

type JoinRule = {
	leftTable?: string;
	leftField?: string;
	rightTable?: string;
	rightField?: string;
	joinType?: string;
};

type DragFieldPayload = {
	id?: string;
	name: string;
	dataType?: string;
	tableName?: string;
	kind?: "field" | "dimension" | "metric";
};

const backendGaps = ["Superset 远端 Dataset 同步"];

export type SemanticModelingSection = "overview" | "subjects" | "objects" | "metrics" | "models" | "publish" | "runs";

const sectionMeta: Record<SemanticModelingSection, { title: string; path: string }> = {
	overview: { title: "语义建模流程", path: "/metrics/semantic" },
	subjects: { title: "主题域映射", path: "/metrics/semantic/subjects" },
	objects: { title: "业务对象 Join", path: "/metrics/semantic/objects" },
	metrics: { title: "指标可视化配置", path: "/metrics/semantic/metrics" },
	models: { title: "DWS/ADS 数据集", path: "/metrics/semantic/models" },
	publish: { title: "审核发布与血缘", path: "/metrics/semantic/publish" },
	runs: { title: "模型运行监控", path: "/metrics/semantic/runs" },
};

const asArray = <T,>(payload: any): T[] => {
	if (Array.isArray(payload)) return payload;
	if (Array.isArray(payload?.content)) return payload.content;
	if (Array.isArray(payload?.data)) return payload.data;
	if (Array.isArray(payload?.data?.content)) return payload.data.content;
	return [];
};

const flattenGovernanceDomains = (nodes: any[], path: string[] = [], out: GovernanceDomainOption[] = []) => {
	nodes.forEach((node) => {
		if (!node || typeof node !== "object") return;
		const id = String(node.id || node.key || "");
		const name = String(node.name || "");
		if (!id || !name) return;
		const code = node.code ? String(node.code) : undefined;
		const nextPath = [...path, name];
		out.push({
			id,
			code,
			name,
			label: nextPath.join(" / "),
		});
		if (Array.isArray(node.children) && node.children.length) {
			flattenGovernanceDomains(node.children, nextPath, out);
		}
	});
	return out;
};

export default function SemanticModelingCenterPage({ section = "overview" }: { section?: SemanticModelingSection }) {
	const router = useRouter();
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [datasetsLoading, setDatasetsLoading] = useState(false);
	const [datasetFields, setDatasetFields] = useState<DatasetField[]>([]);
	const [datasetFieldsLoading, setDatasetFieldsLoading] = useState(false);
	const [governanceDomains, setGovernanceDomains] = useState<GovernanceDomainOption[]>([]);
	const [governanceLoading, setGovernanceLoading] = useState(false);
	const [selectedSource, setSelectedSource] = useState<string>();
	const [modelType, setModelType] = useState<"DWS" | "ADS">("DWS");
	const [semanticLoading, setSemanticLoading] = useState(false);
	const [semanticApiReady, setSemanticApiReady] = useState(false);
	const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
	const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
	const [dimensions, setDimensions] = useState<SemanticDimension[]>([]);
	const [metrics, setMetrics] = useState<SemanticMetric[]>([]);
	const [models, setModels] = useState<SemanticModel[]>([]);
	const [artifacts, setArtifacts] = useState<SemanticGeneratedArtifact[]>([]);
	const [selectedObjectId, setSelectedObjectId] = useState<string>();
	const [selectedModelId, setSelectedModelId] = useState<string>();
	const [modalType, setModalType] = useState<"domain" | "object" | "dimension" | "metric" | "model" | null>(null);
	const [saving, setSaving] = useState(false);
	const [artifactActionLoading, setArtifactActionLoading] = useState(false);
	const [joinTables, setJoinTables] = useState<JoinTable[]>([]);
	const [joinRules, setJoinRules] = useState<JoinRule[]>([]);
	const [draftDimensions, setDraftDimensions] = useState<DragFieldPayload[]>([]);
	const [draftMetrics, setDraftMetrics] = useState<DragFieldPayload[]>([]);
	const [modelDimensions, setModelDimensions] = useState<string[]>([]);
	const [modelMetrics, setModelMetrics] = useState<string[]>([]);
	const [semanticDraftSaving, setSemanticDraftSaving] = useState(false);
	const [previewLoading, setPreviewLoading] = useState(false);
	const [previewOpen, setPreviewOpen] = useState(false);
	const [previewResult, setPreviewResult] = useState<SemanticModelPreview | null>(null);
	const [reviewAction, setReviewAction] = useState<"submit" | "approve" | "reject" | null>(null);
	const [reviewComment, setReviewComment] = useState("");
	const [reviewLoading, setReviewLoading] = useState(false);
	const [reviewLogs, setReviewLogs] = useState<SemanticModelReviewLog[]>([]);
	const [modelRuns, setModelRuns] = useState<SemanticModelRun[]>([]);
	const [runLoading, setRunLoading] = useState(false);
	const [form] = Form.useForm();

	useEffect(() => {
		let ignore = false;
		setDatasetsLoading(true);
		listDatasets({ page: 0, size: 120 })
			.then((resp: any) => {
				if (ignore) return;
				const content = Array.isArray(resp?.content)
					? resp.content
					: Array.isArray(resp?.data?.content)
						? resp.data.content
						: [];
				setDatasets(
					content.map((item: any) => ({
						id: String(item.id || item.key || item.name || item.tableName || item.hiveTable),
						name: String(item.name || item.displayName || item.hiveTable || item.tableName || item.id || ""),
						table: String(item.hiveTable || item.tableName || item.name || ""),
						layer: String(item.warehouseLayer || item.layer || ""),
						database: String(item.databaseName || item.database || ""),
						schema: String(item.schemaName || item.schema || ""),
					})),
				);
			})
			.catch(() => {
				if (!ignore) setDatasets([]);
			})
			.finally(() => {
				if (!ignore) setDatasetsLoading(false);
			});
		return () => {
			ignore = true;
		};
	}, []);

	useEffect(() => {
		let ignore = false;
		setGovernanceLoading(true);
		getDomainTree()
			.then((resp: any) => {
				if (ignore) return;
				setGovernanceDomains(flattenGovernanceDomains(Array.isArray(resp) ? resp : []));
			})
			.catch(() => {
				if (!ignore) setGovernanceDomains([]);
			})
			.finally(() => {
				if (!ignore) setGovernanceLoading(false);
			});
		return () => {
			ignore = true;
		};
	}, []);

	const loadSemanticData = useCallback(() => {
		let ignore = false;
		setSemanticLoading(true);
		Promise.allSettled([
			listSemanticSubjectDomains(),
			listSemanticBusinessObjects(),
			listSemanticDimensions(),
			listSemanticMetrics(),
			listSemanticModels(),
			listSemanticGeneratedArtifacts(),
		])
			.then((results) => {
				if (ignore) return;
				const valueAt = (index: number) =>
					results[index]?.status === "fulfilled" ? (results[index] as PromiseFulfilledResult<any>).value : undefined;
				setSemanticApiReady(results.some((item) => item.status === "fulfilled"));
				setDomains(asArray<SemanticSubjectDomain>(valueAt(0)));
				setObjects(asArray<SemanticBusinessObject>(valueAt(1)));
				setDimensions(asArray<SemanticDimension>(valueAt(2)));
				setMetrics(asArray<SemanticMetric>(valueAt(3)));
				setModels(asArray<SemanticModel>(valueAt(4)));
				setArtifacts(asArray<SemanticGeneratedArtifact>(valueAt(5)));
			})
			.finally(() => {
				if (!ignore) setSemanticLoading(false);
			});
		return () => {
			ignore = true;
		};
	}, []);

	useEffect(() => loadSemanticData(), [loadSemanticData]);

	useEffect(() => {
		if (!selectedModelId) return;
		let ignore = false;
		Promise.allSettled([
			listSemanticGeneratedArtifacts({ modelId: selectedModelId }),
			getSemanticModelBindings(selectedModelId),
			listSemanticModelReviewLogs(selectedModelId),
			listSemanticModelRuns(selectedModelId),
		])
			.then((results) => {
				if (ignore) return;
				const artifactsResp = results[0].status === "fulfilled" ? results[0].value : [];
				const bindingsResp = results[1].status === "fulfilled" ? results[1].value : undefined;
				const reviewLogsResp = results[2].status === "fulfilled" ? results[2].value : [];
				const runsResp = results[3].status === "fulfilled" ? results[3].value : [];
				setArtifacts(asArray<SemanticGeneratedArtifact>(artifactsResp));
				setModelDimensions(
					Array.isArray((bindingsResp as any)?.dimensionIds) ? (bindingsResp as any).dimensionIds : [],
				);
				setModelMetrics(Array.isArray((bindingsResp as any)?.metricIds) ? (bindingsResp as any).metricIds : []);
				setReviewLogs(asArray<SemanticModelReviewLog>(reviewLogsResp));
				setModelRuns(asArray<SemanticModelRun>(runsResp));
			})
			.catch(() => {
				if (!ignore) setArtifacts([]);
			});
		return () => {
			ignore = true;
		};
	}, [selectedModelId]);

	useEffect(() => {
		if (!selectedObjectId) return;
		let ignore = false;
		listSemanticObjectTableMappings(selectedObjectId)
			.then((resp) => {
				if (ignore) return;
				const rows = asArray<any>(resp);
				setJoinTables(
					rows
						.map((item) => ({ id: String(item.id || item.tableName), name: String(item.tableName || "") }))
						.filter((item) => item.name),
				);
				setJoinRules(
					rows
						.filter((item) => String(item.tableRole || "").toUpperCase() !== "PRIMARY")
						.map((item) => ({
							joinType: String(item.tableRole || "left").toLowerCase(),
							rightTable: item.tableName,
							rightField: item.joinExpression,
						})),
				);
			})
			.catch(() => {
				if (!ignore) {
					setJoinTables([]);
					setJoinRules([]);
				}
			});
		return () => {
			ignore = true;
		};
	}, [selectedObjectId]);

	const dwdDatasets = useMemo(
		() =>
			datasets.filter(
				(item) => (item.layer || "").toUpperCase() === "DWD" || item.table?.toLowerCase().startsWith("dwd_"),
			),
		[datasets],
	);

	const selectedDataset = useMemo(
		() => dwdDatasets.find((item) => (item.table || item.name) === selectedSource),
		[dwdDatasets, selectedSource],
	);

	useEffect(() => {
		if (!selectedDataset?.id) {
			setDatasetFields([]);
			return;
		}
		let ignore = false;
		setDatasetFieldsLoading(true);
		getDatasetFields(selectedDataset.id)
			.then((fields) => {
				if (!ignore) setDatasetFields(Array.isArray(fields) ? fields : []);
			})
			.catch(() => {
				if (!ignore) setDatasetFields([]);
			})
			.finally(() => {
				if (!ignore) setDatasetFieldsLoading(false);
			});
		return () => {
			ignore = true;
		};
	}, [selectedDataset?.id]);

	const selectedModel = useMemo(() => models.find((item) => item.id === selectedModelId), [models, selectedModelId]);

	const selectedModelReviewStatus = (selectedModel?.reviewStatus || selectedModel?.status || "DRAFT").toUpperCase();
	const canSubmitReview = Boolean(
		selectedModelId && !["IN_REVIEW", "APPROVED", "PUBLISHED"].includes(selectedModelReviewStatus),
	);
	const canApproveReview = Boolean(selectedModelId && selectedModelReviewStatus === "IN_REVIEW");
	const canRejectReview = canApproveReview;
	const canPublishModel = Boolean(selectedModelId && ["APPROVED", "PUBLISHED"].includes(selectedModelReviewStatus));

	const selectedObject = useMemo(
		() => objects.find((item) => item.id === selectedObjectId),
		[objects, selectedObjectId],
	);

	const activeSemanticObjectId = selectedModel?.objectId || selectedObjectId;

	const visibleDimensions = useMemo(
		() => (activeSemanticObjectId ? dimensions.filter((item) => item.objectId === activeSemanticObjectId) : dimensions),
		[activeSemanticObjectId, dimensions],
	);

	const visibleMetrics = useMemo(
		() => (activeSemanticObjectId ? metrics.filter((item) => item.objectId === activeSemanticObjectId) : metrics),
		[activeSemanticObjectId, metrics],
	);

	const dimensionNameById = useMemo(
		() => new Map(dimensions.map((item) => [item.id, item.name || item.code])),
		[dimensions],
	);

	const metricNameById = useMemo(() => new Map(metrics.map((item) => [item.id, item.name || item.code])), [metrics]);

	const previewColumns = useMemo<ColumnsType<Record<string, any>>>(
		() =>
			(previewResult?.headers || []).map((name) => ({
				title: name,
				dataIndex: name,
				key: name,
				ellipsis: true,
				render: (value) => (value == null ? <Text type="secondary">NULL</Text> : String(value)),
			})),
		[previewResult?.headers],
	);

	const reviewStatusColor = (status?: string) => {
		const value = (status || "DRAFT").toUpperCase();
		if (value === "APPROVED" || value === "PUBLISHED") return "green";
		if (value === "IN_REVIEW") return "blue";
		if (value === "REJECTED") return "red";
		return "default";
	};

	const openModal = (type: typeof modalType) => {
		setModalType(type);
		form.resetFields();
		if (type === "object") {
			form.setFieldsValue({ mainTable: selectedDataset?.table || selectedDataset?.name });
		}
		if (type === "dimension" || type === "metric") {
			form.setFieldsValue({ objectId: selectedObjectId });
		}
		if (type === "model") {
			form.setFieldsValue({ type: modelType, objectId: selectedObjectId });
		}
	};

	const closeModal = () => {
		setModalType(null);
		form.resetFields();
	};

	const submitModal = async () => {
		const formValues = await form.validateFields();
		const values = { ...formValues };
		if (modalType === "domain" && values.governanceDomainId) {
			const governanceDomain = governanceDomains.find((item) => item.id === values.governanceDomainId);
			values.governanceDomainCode = governanceDomain?.code;
			values.governanceDomainName = governanceDomain?.name;
		}
		setSaving(true);
		try {
			if (modalType === "domain") await createSemanticSubjectDomain(values);
			if (modalType === "object") await createSemanticBusinessObject(values);
			if (modalType === "dimension") await createSemanticDimension(values);
			if (modalType === "metric") await createSemanticMetric(values);
			if (modalType === "model") {
				const created = await createSemanticModel(values);
				if ((created as any)?.id) setSelectedModelId((created as any).id);
			}
			message.success("已保存");
			closeModal();
			loadSemanticData();
		} finally {
			setSaving(false);
		}
	};

	const generateArtifacts = async () => {
		if (!selectedModelId) {
			message.warning("请选择一个语义模型");
			return;
		}
		setArtifactActionLoading(true);
		try {
			const result = await generateSemanticModelArtifacts(selectedModelId);
			setArtifacts((result as any)?.artifacts || []);
			message.success("已生成 SQL/dbt 产物");
		} finally {
			setArtifactActionLoading(false);
		}
	};

	const previewModelData = async () => {
		if (!selectedModelId) {
			message.warning("请选择一个语义模型");
			return;
		}
		setPreviewLoading(true);
		try {
			const result = await previewSemanticModelData(selectedModelId, 100);
			setPreviewResult(result);
			setPreviewOpen(true);
			if ((result as any)?.success === false) {
				message.warning((result as any)?.errorMessage || "预览执行失败，请检查模型 SQL");
			}
		} finally {
			setPreviewLoading(false);
		}
	};

	const publishArtifacts = async () => {
		if (!selectedModelId) {
			message.warning("请选择一个语义模型");
			return;
		}
		setArtifactActionLoading(true);
		try {
			const result = await publishSemanticModelToDbt(selectedModelId);
			message.success(`已发布 ${((result as any)?.publishedPaths || []).length} 个 dbt 文件`);
			const refreshed = await listSemanticGeneratedArtifacts({ modelId: selectedModelId });
			setArtifacts(asArray<SemanticGeneratedArtifact>(refreshed));
			loadSemanticData();
		} finally {
			setArtifactActionLoading(false);
		}
	};

	const openReviewDialog = (action: "submit" | "approve" | "reject") => {
		setReviewAction(action);
		setReviewComment("");
	};

	const closeReviewDialog = () => {
		setReviewAction(null);
		setReviewComment("");
	};

	const submitReviewAction = async () => {
		if (!selectedModelId || !reviewAction) return;
		if (reviewAction === "reject" && !reviewComment.trim()) {
			message.warning("请输入驳回原因");
			return;
		}
		setReviewLoading(true);
		try {
			if (reviewAction === "submit") await submitSemanticModelReview(selectedModelId, reviewComment);
			if (reviewAction === "approve") await approveSemanticModelReview(selectedModelId, reviewComment);
			if (reviewAction === "reject") await rejectSemanticModelReview(selectedModelId, reviewComment);
			message.success({ submit: "已提交审核", approve: "已审核通过", reject: "已驳回" }[reviewAction]);
			closeReviewDialog();
			loadSemanticData();
			const logs = await listSemanticModelReviewLogs(selectedModelId);
			setReviewLogs(asArray<SemanticModelReviewLog>(logs));
		} finally {
			setReviewLoading(false);
		}
	};

	const registerBiDataset = async () => {
		if (!selectedModelId) {
			message.warning("请选择一个语义模型");
			return;
		}
		setArtifactActionLoading(true);
		try {
			const result = await registerSemanticBiDataset(selectedModelId);
			message.success(`已注册 BI 数据集：${(result as any)?.datasetName || ""}`);
			const refreshed = await listSemanticGeneratedArtifacts({ modelId: selectedModelId });
			setArtifacts(asArray<SemanticGeneratedArtifact>(refreshed));
		} finally {
			setArtifactActionLoading(false);
		}
	};

	const registerLineage = async () => {
		if (!selectedModelId) {
			message.warning("请选择一个语义模型");
			return;
		}
		setArtifactActionLoading(true);
		try {
			await registerSemanticLineage(selectedModelId);
			message.success("已写入目录血缘");
			const refreshed = await listSemanticGeneratedArtifacts({ modelId: selectedModelId });
			setArtifacts(asArray<SemanticGeneratedArtifact>(refreshed));
		} finally {
			setArtifactActionLoading(false);
		}
	};

	const triggerModelRun = async () => {
		if (!selectedModelId) {
			message.warning("请选择一个语义模型");
			return;
		}
		setRunLoading(true);
		try {
			await triggerSemanticModelRun(selectedModelId);
			message.success("已触发模型运行");
			const runs = await listSemanticModelRuns(selectedModelId);
			setModelRuns(asArray<SemanticModelRun>(runs));
			loadSemanticData();
		} finally {
			setRunLoading(false);
		}
	};

	const dragField = (event: React.DragEvent<HTMLElement>, field: DragFieldPayload) => {
		event.dataTransfer.setData("application/json", JSON.stringify(field));
		event.dataTransfer.effectAllowed = "copy";
	};

	const dragDataset = (event: React.DragEvent<HTMLElement>, dataset: DatasetOption) => {
		event.dataTransfer.setData("application/json", JSON.stringify({ kind: "dataset", value: dataset }));
		event.dataTransfer.effectAllowed = "copy";
	};

	const addUniqueField = (list: DragFieldPayload[], field: DragFieldPayload) =>
		list.some((item) => item.name === field.name && item.tableName === field.tableName) ? list : [...list, field];

	const safeCode = (value: string) =>
		value
			.trim()
			.replace(/([a-z0-9])([A-Z])/g, "$1_$2")
			.replace(/[^a-zA-Z0-9_]+/g, "_")
			.replace(/^_+|_+$/g, "")
			.toLowerCase();

	const isNumericField = (field: DragFieldPayload) => {
		const dataType = (field.dataType || "").toLowerCase();
		return ["int", "integer", "bigint", "smallint", "decimal", "numeric", "number", "double", "float", "real"].some(
			(item) => dataType.includes(item),
		);
	};

	const saveDraftDimensions = async () => {
		if (!selectedObjectId) {
			message.warning("请选择业务对象");
			return;
		}
		if (!draftDimensions.length) {
			message.warning("请先拖入维度字段");
			return;
		}
		setSemanticDraftSaving(true);
		try {
			const createdIds: string[] = [];
			for (const field of draftDimensions) {
				const code = safeCode(field.name);
				const duplicated = dimensions.some(
					(item) => item.objectId === selectedObjectId && (item.fieldName === field.name || item.code === code),
				);
				if (duplicated) continue;
				const created = await createSemanticDimension({
					objectId: selectedObjectId,
					code,
					name: field.name,
					fieldName: field.name,
					dataType: field.dataType,
					semanticType: field.name.toLowerCase().includes("date") || field.name.includes("时间") ? "time" : "dimension",
				});
				if ((created as any)?.id) createdIds.push(String((created as any).id));
			}
			if (selectedModel?.objectId === selectedObjectId && createdIds.length) {
				setModelDimensions((current) => [...current, ...createdIds.filter((id) => !current.includes(id))]);
			}
			setDraftDimensions([]);
			loadSemanticData();
			message.success("维度已保存");
		} finally {
			setSemanticDraftSaving(false);
		}
	};

	const saveDraftMetrics = async () => {
		if (!selectedObjectId) {
			message.warning("请选择业务对象");
			return;
		}
		if (!draftMetrics.length) {
			message.warning("请先拖入指标字段");
			return;
		}
		setSemanticDraftSaving(true);
		try {
			const createdIds: string[] = [];
			for (const field of draftMetrics) {
				const code = safeCode(field.name);
				const duplicated = metrics.some(
					(item) => item.objectId === selectedObjectId && (item.code === code || item.name === field.name),
				);
				if (duplicated) continue;
				const formulaType = isNumericField(field) ? "sum" : "count_distinct";
				const created = await createSemanticMetric({
					objectId: selectedObjectId,
					code,
					name: field.name,
					formulaType,
					formulaJson: JSON.stringify({ type: formulaType, field: field.name }),
					format: isNumericField(field) ? "number" : "integer",
				});
				if ((created as any)?.id) createdIds.push(String((created as any).id));
			}
			if (selectedModel?.objectId === selectedObjectId && createdIds.length) {
				setModelMetrics((current) => [...current, ...createdIds.filter((id) => !current.includes(id))]);
			}
			setDraftMetrics([]);
			loadSemanticData();
			message.success("指标已保存");
		} finally {
			setSemanticDraftSaving(false);
		}
	};

	const addDraftDimension = (field?: DragFieldPayload | null) => {
		if (field) setDraftDimensions((current) => addUniqueField(current, field));
	};

	const addDraftMetric = (field?: DragFieldPayload | null) => {
		if (field) setDraftMetrics((current) => addUniqueField(current, field));
	};

	const addModelDimension = (field?: DragFieldPayload | null) => {
		const value = field?.id || field?.name;
		if (value) setModelDimensions((current) => (current.includes(value) ? current : [...current, value]));
	};

	const addModelMetric = (field?: DragFieldPayload | null) => {
		const value = field?.id || field?.name;
		if (value) setModelMetrics((current) => (current.includes(value) ? current : [...current, value]));
	};

	const fieldFromDropPayload = ({ payload }: VisualFlowDropEvent): DragFieldPayload | null => {
		if (payload.kind === "dataset") return null;
		if (payload.value && typeof payload.value === "object") return payload.value as DragFieldPayload;
		return payload as DragFieldPayload;
	};

	const dropDimensionToCanvas = (event: VisualFlowDropEvent) => addDraftDimension(fieldFromDropPayload(event));

	const dropMetricToCanvas = (event: VisualFlowDropEvent) => addDraftMetric(fieldFromDropPayload(event));

	const dropModelDimensionToCanvas = (event: VisualFlowDropEvent) => addModelDimension(fieldFromDropPayload(event));

	const dropModelMetricToCanvas = (event: VisualFlowDropEvent) => addModelMetric(fieldFromDropPayload(event));

	const addSelectedTableToCanvas = () => {
		if (!selectedDataset) {
			message.warning("请选择 DWD 明细模型");
			return;
		}
		const name = selectedDataset.table || selectedDataset.name;
		setJoinTables((current) =>
			current.some((item) => item.id === selectedDataset.id) ? current : [...current, { id: selectedDataset.id, name }],
		);
	};

	const addJoinRule = () => {
		setJoinRules((current) => [...current, { joinType: "left" }]);
	};

	const addDatasetToJoinCanvas = (dataset: DatasetOption) => {
		const name = dataset.table || dataset.name;
		setSelectedSource(name);
		setJoinTables((current) =>
			current.some((item) => item.id === dataset.id) ? current : [...current, { id: dataset.id, name }],
		);
	};

	const dropDatasetToJoinCanvas = ({ payload }: VisualFlowDropEvent) => {
		if (payload.kind !== "dataset" || !payload.value || typeof payload.value !== "object") return;
		addDatasetToJoinCanvas(payload.value as DatasetOption);
	};

	const saveJoinCanvas = async () => {
		if (!selectedObjectId) {
			message.warning("请选择业务对象");
			return;
		}
		const mappings = joinTables.map((table, index) => {
			const rule = joinRules[index - 1];
			return {
				tableName: table.name,
				tableRole: index === 0 ? "PRIMARY" : (rule?.joinType || "left").toUpperCase(),
				joinExpression: index === 0 ? undefined : [rule?.leftField, rule?.rightField].filter(Boolean).join(" = "),
				sortOrder: index,
			};
		});
		await saveSemanticObjectTableMappings(selectedObjectId, mappings);
		message.success("Join 画布已保存");
	};

	const saveModelCanvas = async () => {
		if (!selectedModelId) {
			message.warning("请选择语义模型");
			return;
		}
		const dimensionIds = modelDimensions.filter((id) => dimensions.some((item) => item.id === id));
		const metricIds = modelMetrics.filter((id) => metrics.some((item) => item.id === id));
		await saveSemanticModelBindings(selectedModelId, { dimensionIds, metricIds });
		message.success("模型画布已保存");
	};

	const findJoinTableId = useCallback(
		(tableName?: string) => {
			if (!tableName) return undefined;
			const normalized = tableName.trim();
			return joinTables.find((item) => item.name === normalized || item.name.endsWith(`.${normalized}`))?.id;
		},
		[joinTables],
	);

	const getJoinRuleKey = (rule: JoinRule, index: number) =>
		[
			rule.joinType || "join",
			rule.leftTable || rule.leftField || "left",
			rule.rightTable || rule.rightField || "right",
			index,
		].join("-");

	const joinCanvasNodes: Node[] = useMemo(
		() =>
			joinTables.map((table, index) => ({
				id: table.id,
				position: {
					x: (index % 2) * 260,
					y: Math.floor(index / 2) * 130,
				},
				data: {
					label: (
						<Space direction="vertical" size={2}>
							<Tag color={index === 0 ? "blue" : "default"}>{index === 0 ? "主表" : "关联表"}</Tag>
							<Text strong>{table.name}</Text>
							<Text type="secondary" className="text-xs">
								拖入 DWD 模型或在右侧配置 Join
							</Text>
						</Space>
					),
				},
				style: {
					width: 210,
					minHeight: 86,
					background: "#fff",
					border: `1px solid ${index === 0 ? "#1677ff" : "#d9d9d9"}`,
					borderRadius: 6,
					padding: "10px 12px",
					boxShadow: index === 0 ? "0 0 0 2px rgba(22, 119, 255, 0.08)" : "0 1px 3px rgba(15, 23, 42, 0.08)",
				},
			})),
		[joinTables],
	);

	const joinCanvasEdges: Edge[] = useMemo(
		() =>
			joinRules
				.map((rule, index): Edge | null => {
					const sourceName = rule.leftTable || String(rule.leftField || "").split(".")[0];
					const targetName = rule.rightTable || String(rule.rightField || "").split(".")[0];
					const source = findJoinTableId(sourceName) || joinTables[0]?.id;
					const target = findJoinTableId(targetName) || joinTables[index + 1]?.id;
					if (!source || !target || source === target) return null;
					return {
						id: `join-${index}`,
						source,
						target,
						type: "smoothstep",
						label: `${String(rule.joinType || "left").toUpperCase()} JOIN`,
						labelStyle: { fontSize: 10, fill: "#475569", fontWeight: 600 },
						labelBgPadding: [6, 3] as [number, number],
						labelBgBorderRadius: 4,
						style: { stroke: "#1677ff", strokeWidth: 1.6 },
						markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16 },
					};
				})
				.filter((edge): edge is Edge => Boolean(edge)),
		[findJoinTableId, joinRules, joinTables],
	);

	const draftDimensionNodes: Node[] = useMemo(
		() =>
			draftDimensions.map((field, index) => ({
				id: `draft-dimension-${field.tableName || "field"}-${field.name}`,
				position: { x: (index % 2) * 170, y: Math.floor(index / 2) * 82 },
				data: {
					label: (
						<Tag
							closable
							className="nodrag"
							onClose={() =>
								setDraftDimensions((current) =>
									current.filter((item) => item.name !== field.name || item.tableName !== field.tableName),
								)
							}
						>
							{field.name}
						</Tag>
					),
				},
				style: {
					width: 150,
					minHeight: 52,
					background: "#eff6ff",
					border: "1px solid #91caff",
					borderRadius: 6,
					padding: "10px",
				},
			})),
		[draftDimensions],
	);

	const draftMetricNodes: Node[] = useMemo(
		() =>
			draftMetrics.map((field, index) => ({
				id: `draft-metric-${field.tableName || "field"}-${field.name}`,
				position: { x: (index % 2) * 170, y: Math.floor(index / 2) * 82 },
				data: {
					label: (
						<Tag
							color="green"
							closable
							className="nodrag"
							onClose={() =>
								setDraftMetrics((current) =>
									current.filter((item) => item.name !== field.name || item.tableName !== field.tableName),
								)
							}
						>
							{field.name}
						</Tag>
					),
				},
				style: {
					width: 150,
					minHeight: 52,
					background: "#f6ffed",
					border: "1px solid #95de64",
					borderRadius: 6,
					padding: "10px",
				},
			})),
		[draftMetrics],
	);

	const modelDimensionNodes: Node[] = useMemo(
		() =>
			modelDimensions.map((id, index) => ({
				id: `model-dimension-${id}`,
				position: { x: (index % 3) * 170, y: Math.floor(index / 3) * 78 },
				data: {
					label: (
						<Tag
							closable
							className="nodrag"
							onClose={() => setModelDimensions((current) => current.filter((value) => value !== id))}
						>
							{dimensionNameById.get(id) || id}
						</Tag>
					),
				},
				style: {
					width: 150,
					minHeight: 50,
					background: "#fff",
					border: "1px solid #91caff",
					borderRadius: 6,
					padding: "9px",
				},
			})),
		[dimensionNameById, modelDimensions],
	);

	const modelMetricNodes: Node[] = useMemo(
		() =>
			modelMetrics.map((id, index) => ({
				id: `model-metric-${id}`,
				position: { x: (index % 3) * 170, y: Math.floor(index / 3) * 78 },
				data: {
					label: (
						<Tag
							color="green"
							closable
							className="nodrag"
							onClose={() => setModelMetrics((current) => current.filter((value) => value !== id))}
						>
							{metricNameById.get(id) || id}
						</Tag>
					),
				},
				style: {
					width: 150,
					minHeight: 50,
					background: "#fff",
					border: "1px solid #95de64",
					borderRadius: 6,
					padding: "9px",
				},
			})),
		[metricNameById, modelMetrics],
	);

	const datasetColumns: ColumnsType<DatasetOption> = [
		{
			title: "资产名称",
			dataIndex: "name",
			sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
			render: (value, row) => (
				<Space direction="vertical" size={0}>
					<Text strong>{value || row.table || row.id}</Text>
					<Text type="secondary" className="text-xs">
						{row.table || "-"}
					</Text>
				</Space>
			),
		},
		{
			title: "分层",
			dataIndex: "layer",
			width: 90,
			render: (value) => (value ? <Tag>{value}</Tag> : <Text type="secondary">-</Text>),
		},
		{ title: "库", dataIndex: "database", width: 140, render: (value) => value || "-" },
		{ title: "Schema", dataIndex: "schema", width: 140, render: (value) => value || "-" },
	];

	const activeSection = sectionMeta[section] ? section : "overview";
	const activeMeta = sectionMeta[activeSection];
	const showOverview = activeSection === "overview";
	const showSubjects = activeSection === "subjects";
	const showObjects = activeSection === "objects";
	const showMetrics = activeSection === "metrics";
	const showModels = activeSection === "models";
	const showPublish = activeSection === "publish";
	const showRuns = activeSection === "runs";
	const headerActions = (
		<Space wrap>
			{showOverview ? (
				<>
					<Button icon={<DashboardOutlined />} onClick={() => router.push("/metrics/center")}>
						指标工作台
					</Button>
					<Button icon={<FunctionOutlined />} onClick={() => router.push("/metrics/dictionary")}>
						指标字典
					</Button>
				</>
			) : null}
			{showSubjects ? (
				<Button type="primary" icon={<ProjectOutlined />} onClick={() => openModal("domain")}>
					新建主题域
				</Button>
			) : null}
			{showObjects ? (
				<Button type="primary" icon={<DatabaseOutlined />} onClick={() => openModal("object")}>
					新建业务对象
				</Button>
			) : null}
			{showMetrics ? (
				<>
					<Button icon={<DatabaseOutlined />} onClick={() => openModal("object")}>
						业务对象
					</Button>
					<Button icon={<TableOutlined />} onClick={() => openModal("dimension")}>
						新增维度
					</Button>
					<Button type="primary" icon={<FunctionOutlined />} onClick={() => openModal("metric")}>
						新增指标
					</Button>
				</>
			) : null}
			{showModels ? (
				<Button type="primary" icon={<RocketOutlined />} onClick={() => openModal("model")}>
					定义模型
				</Button>
			) : null}
			{showRuns ? (
				<Button icon={<DashboardOutlined />} onClick={() => router.push("/metrics/center")}>
					指标工作台
				</Button>
			) : null}
		</Space>
	);

	return (
		<div className="space-y-5 p-5" data-testid="semantic-modeling-center-page">
			<PageHeader title={activeMeta.title} actions={headerActions} />

			<Card>
				<Segmented
					value={activeSection}
					onChange={(value) => router.push(sectionMeta[value as SemanticModelingSection].path)}
					options={(Object.keys(sectionMeta) as SemanticModelingSection[]).map((key) => ({
						label: sectionMeta[key].title,
						value: key,
					}))}
				/>
			</Card>

			{showOverview ? (
				<Space direction="vertical" className="w-full" size={16}>
					<Alert
						type="info"
						showIcon
						message="设计口径"
						description="指标可视化设计从 DWD 明细模型开始做业务对象、Join、维度和指标定义；面向 BI、大屏和 API 消费时，只发布 DWS 公共汇总模型或 ADS 应用数据集。DWD 是建模输入，不直接作为可视化消费层。"
					/>
					<Row gutter={[16, 16]}>
						<Col xs={24} lg={16}>
							<Card title="端到端流程">
								<Steps
									current={0}
									items={[
										{ title: "治理主题域", description: "优先复用治理中心主题域", icon: <ProjectOutlined /> },
										{ title: "DWD 业务对象", description: "选择明细模型并配置 Join", icon: <DatabaseOutlined /> },
										{ title: "指标定义", description: "字段拖拽生成维度和指标", icon: <FunctionOutlined /> },
										{ title: "DWS/ADS", description: "生成可消费数据集", icon: <TableOutlined /> },
										{ title: "发布消费", description: "dbt、BI、API、血缘", icon: <BranchesOutlined /> },
									]}
								/>
							</Card>
						</Col>
						<Col xs={24} lg={8}>
							<Card title="当前资产">
								<Row gutter={12}>
									<Col span={8}>
										<Statistic title="主题域" value={domains.length} />
									</Col>
									<Col span={8}>
										<Statistic title="业务对象" value={objects.length} />
									</Col>
									<Col span={8}>
										<Statistic title="指标" value={metrics.length} />
									</Col>
								</Row>
							</Card>
						</Col>
					</Row>
					<Row gutter={[16, 16]}>
						{(["subjects", "objects", "metrics", "models", "publish", "runs"] as SemanticModelingSection[]).map(
							(key, index) => (
								<Col xs={24} md={12} xl={8} key={key}>
									<Card
										title={`${index + 1}. ${sectionMeta[key].title}`}
										extra={
											<Button size="small" onClick={() => router.push(sectionMeta[key].path)}>
												进入
											</Button>
										}
									>
										<Paragraph type="secondary" className="mb-0">
											{key === "subjects"
												? "把治理中心主题域作为优先依据，但允许开发工程师自建主题域。"
												: key === "objects"
													? "从 DWD 明细模型配置业务对象和多表 Join。"
													: key === "metrics"
														? "用拖拽方式定义维度、指标和业务口径。"
														: key === "models"
															? "把指标组合成 DWS 公共汇总模型或 ADS 应用数据集。"
															: key === "publish"
																? "审核后发布到 dbt、BI 数据集、API，并写入血缘。"
																: "查看 dbt/调度运行状态，形成可运维闭环。"}
										</Paragraph>
									</Card>
								</Col>
							),
						)}
					</Row>
				</Space>
			) : null}

			{showSubjects ? (
				<Row gutter={[16, 16]}>
					<Col xs={24} xl={7}>
						<Card
							title="1. 业务主题域"
							extra={
								<Space>
									<Tag color={semanticApiReady ? "green" : "default"}>{semanticApiReady ? "已接 API" : "等待 API"}</Tag>
									<Button size="small" type="primary" onClick={() => openModal("domain")}>
										新建
									</Button>
								</Space>
							}
						>
							{domains.length ? (
								<CompactTable<SemanticSubjectDomain>
									rowKey="id"
									size="small"
									loading={semanticLoading}
									pagination={false}
									columns={[
										{ title: "名称", dataIndex: "name" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
										{
											title: "来源",
											width: 130,
											render: (_, row) =>
												row.governanceDomainId ? (
													<Tag color="blue">{row.governanceDomainName || row.governanceDomainCode || "治理主题域"}</Tag>
												) : (
													<Tag>开发自建</Tag>
												),
										},
										{ title: "状态", dataIndex: "status", width: 90, render: (value) => value || "-" },
									]}
									dataSource={domains}
								/>
							) : (
								<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="等待主题域 API" />
							)}
						</Card>
					</Col>

					<Col xs={24} xl={17}>
						<Card title="2. DWD 明细模型">
							{dwdDatasets.length ? (
								<CompactTable<DatasetOption>
									rowKey="id"
									size="small"
									loading={datasetsLoading}
									pagination={{ pageSize: 6 }}
									columns={datasetColumns}
									dataSource={dwdDatasets}
									rowSelection={{
										type: "radio",
										selectedRowKeys: selectedDataset ? [selectedDataset.id] : [],
										onChange: (_, rows) => setSelectedSource(rows[0] ? rows[0].table || rows[0].name : undefined),
									}}
								/>
							) : (
								<Empty
									image={Empty.PRESENTED_IMAGE_SIMPLE}
									description={datasetsLoading ? "正在读取资产目录" : "资产目录暂无 DWD 明细模型"}
								/>
							)}
						</Card>
					</Col>
				</Row>
			) : null}

			{showObjects ? (
				<Card
					title="2.1 业务对象 Join 画布"
					extra={
						<Space>
							<Select
								size="small"
								allowClear
								placeholder="选择业务对象"
								style={{ minWidth: 180 }}
								value={selectedObjectId}
								options={objects.map((item) => ({ label: item.name, value: item.id }))}
								onChange={setSelectedObjectId}
							/>
							<Button size="small" onClick={addSelectedTableToCanvas}>
								加入画布
							</Button>
							<Button size="small" type="primary" onClick={addJoinRule}>
								新增 Join
							</Button>
							<Button size="small" disabled={!selectedObjectId} onClick={saveJoinCanvas}>
								保存画布
							</Button>
						</Space>
					}
				>
					<Space direction="vertical" size={16} className="w-full">
						<div>
							<Text type="secondary">从真实 DWD 明细模型加入业务对象画布。</Text>
							{dwdDatasets.length ? (
								<div className="mt-3 flex gap-2 overflow-x-auto rounded border border-dashed border-slate-200 p-3">
									{dwdDatasets.map((item) => (
										<button
											key={item.id}
											type="button"
											draggable
											className="min-w-56 cursor-pointer rounded border border-slate-200 bg-white p-2 text-left hover:border-blue-400"
											onDragStart={(event) => dragDataset(event, item)}
											onClick={() => setSelectedSource(item.table || item.name)}
											onKeyDown={(event) => {
												if (event.key === "Enter" || event.key === " ") {
													event.preventDefault();
													addDatasetToJoinCanvas(item);
												}
											}}
											onDoubleClick={() => {
												addDatasetToJoinCanvas(item);
											}}
										>
											<Text strong>{item.table || item.name}</Text>
											<div>
												<Text type="secondary" className="text-xs">
													{item.database || "-"} / {item.schema || "-"}
												</Text>
											</div>
										</button>
									))}
								</div>
							) : (
								<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 DWD 明细模型" />
							)}
						</div>
						<Row gutter={[16, 16]} align="stretch">
							<Col xs={24} xl={18} xxl={19}>
								<VisualFlowCanvas
									nodes={joinCanvasNodes}
									edges={joinCanvasEdges}
									height={560}
									emptyText="从上方拖入 DWD 明细模型"
									onDropItem={dropDatasetToJoinCanvas}
								/>
							</Col>
							<Col xs={24} xl={6} xxl={5}>
								<div className="h-full min-h-96 overflow-auto rounded border border-slate-200 p-3">
									<Space direction="vertical" className="w-full">
										<Text strong>Join 条件</Text>
										{joinRules.length ? (
											joinRules.map((rule, index) => (
												<Card key={getJoinRuleKey(rule, index)} size="small">
													<Space direction="vertical" className="w-full">
														<Select
															size="small"
															value={rule.joinType}
															options={[
																{ label: "Left Join", value: "left" },
																{ label: "Inner Join", value: "inner" },
																{ label: "Full Join", value: "full" },
															]}
															onChange={(value) =>
																setJoinRules((current) =>
																	current.map((item, i) => (i === index ? { ...item, joinType: value } : item)),
																)
															}
														/>
														<Input
															size="small"
															placeholder="左表.字段"
															value={[rule.leftTable, rule.leftField].filter(Boolean).join(".")}
															onChange={(event) =>
																setJoinRules((current) =>
																	current.map((item, i) =>
																		i === index ? { ...item, leftField: event.target.value } : item,
																	),
																)
															}
														/>
														<Input
															size="small"
															placeholder="右表.字段"
															value={[rule.rightTable, rule.rightField].filter(Boolean).join(".")}
															onChange={(event) =>
																setJoinRules((current) =>
																	current.map((item, i) =>
																		i === index ? { ...item, rightField: event.target.value } : item,
																	),
																)
															}
														/>
													</Space>
												</Card>
											))
										) : (
											<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 Join 条件" />
										)}
									</Space>
								</div>
							</Col>
						</Row>
					</Space>
				</Card>
			) : null}

			{showMetrics ? (
				<Row gutter={[16, 16]}>
					<Col xs={24} xl={10}>
						<Card
							title="3. 业务对象与字段语义"
							extra={
								<Space>
									<Button size="small" onClick={() => openModal("object")}>
										业务对象
									</Button>
									<Button size="small" onClick={() => openModal("dimension")}>
										维度
									</Button>
								</Space>
							}
						>
							<Form layout="vertical">
								<Form.Item label="来源明细模型">
									<Select
										allowClear
										loading={datasetsLoading}
										value={selectedSource}
										placeholder="请选择真实 DWD 明细模型"
										onChange={setSelectedSource}
										options={dwdDatasets.map((item) => ({
											label: item.table || item.name,
											value: item.table || item.name,
										}))}
									/>
								</Form.Item>
								<Form.Item label="业务对象">
									<Select
										allowClear
										value={selectedObjectId}
										placeholder="请选择业务对象"
										options={objects.map((item) => ({ label: item.name, value: item.id }))}
										onChange={setSelectedObjectId}
									/>
								</Form.Item>
								<Form.Item label="主键">
									<Input readOnly value={selectedObject?.primaryKey || ""} placeholder="业务对象未配置主键" />
								</Form.Item>
							</Form>
							<Row gutter={[12, 12]} className="mb-4">
								<Col xs={24} md={8}>
									<div className="rounded border border-slate-200 p-3">
										<Text strong>字段池</Text>
										<div className="mt-2 max-h-64 space-y-2 overflow-auto">
											{datasetFields.length ? (
												datasetFields.map((field) => (
													<button
														key={`${field.tableName || selectedDataset?.table || ""}-${field.name}`}
														type="button"
														draggable
														onDragStart={(event) =>
															dragField(event, {
																name: field.name,
																dataType: field.dataType,
																tableName: field.tableName || selectedDataset?.table,
															})
														}
														onKeyDown={(event) => {
															if (event.key === "Enter" || event.key === " ") {
																event.preventDefault();
																addDraftDimension({
																	name: field.name,
																	dataType: field.dataType,
																	tableName: field.tableName || selectedDataset?.table,
																});
															}
														}}
														className="w-full cursor-grab rounded border border-slate-200 bg-white p-2 text-left active:cursor-grabbing"
													>
														<Text>{field.name}</Text>
														<div>
															<Text type="secondary" className="text-xs">
																{field.dataType || "-"}
																{field.comment ? ` / ${field.comment}` : ""}
															</Text>
														</div>
													</button>
												))
											) : (
												<Empty
													image={Empty.PRESENTED_IMAGE_SIMPLE}
													description={datasetFieldsLoading ? "正在读取字段" : "暂无字段元数据"}
												/>
											)}
										</div>
									</div>
								</Col>
								<Col xs={24} md={8}>
									<Space direction="vertical" className="w-full">
										<div className="flex items-center justify-between gap-2">
											<Text strong>拖拽到维度区</Text>
											<Button
												size="small"
												type="primary"
												loading={semanticDraftSaving}
												disabled={!selectedObjectId || !draftDimensions.length}
												onClick={saveDraftDimensions}
											>
												保存维度
											</Button>
										</div>
										<VisualFlowCanvas
											nodes={draftDimensionNodes}
											height={230}
											emptyText="放入时间、组织、状态等维度字段"
											onDropItem={dropDimensionToCanvas}
										/>
									</Space>
								</Col>
								<Col xs={24} md={8}>
									<Space direction="vertical" className="w-full">
										<div className="flex items-center justify-between gap-2">
											<Text strong>拖拽到指标区</Text>
											<Button
												size="small"
												type="primary"
												loading={semanticDraftSaving}
												disabled={!selectedObjectId || !draftMetrics.length}
												onClick={saveDraftMetrics}
											>
												保存指标
											</Button>
										</div>
										<VisualFlowCanvas
											nodes={draftMetricNodes}
											height={230}
											emptyText="放入金额、数量、状态判断等指标字段"
											onDropItem={dropMetricToCanvas}
										/>
									</Space>
								</Col>
							</Row>
							{objects.length || dimensions.length ? (
								<Space direction="vertical" className="w-full">
									<CompactTable<SemanticBusinessObject>
										rowKey="id"
										size="small"
										pagination={false}
										loading={semanticLoading}
										rowSelection={{
											type: "radio",
											selectedRowKeys: selectedObjectId ? [selectedObjectId] : [],
											onChange: (keys) => setSelectedObjectId(String(keys[0] || "")),
										}}
										columns={[
											{ title: "业务对象", dataIndex: "name" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
											{ title: "主表", dataIndex: "mainTable", render: (value) => value || "-" },
										]}
										dataSource={objects}
									/>
									<CompactTable<SemanticDimension>
										rowKey="id"
										size="small"
										pagination={false}
										loading={semanticLoading}
										columns={[
											{ title: "维度", dataIndex: "name" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
											{ title: "字段", dataIndex: "fieldName", render: (value) => value || "-" },
										]}
										dataSource={visibleDimensions}
									/>
								</Space>
							) : (
								<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="等待字段语义映射 API" />
							)}
						</Card>
					</Col>

					<Col xs={24} xl={14}>
						<Card
							title="4. 指标定义"
							extra={
								<Button size="small" type="primary" onClick={() => openModal("metric")}>
									新增指标
								</Button>
							}
						>
							{visibleMetrics.length ? (
								<CompactTable<SemanticMetric>
									rowKey="id"
									size="small"
									pagination={false}
									loading={semanticLoading}
									columns={[
										{ title: "指标", dataIndex: "name" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
										{ title: "公式类型", dataIndex: "formulaType", render: (value) => value || "-" },
										{ title: "格式", dataIndex: "format", render: (value) => value || "-" },
										{ title: "状态", dataIndex: "status", render: (value) => value || "-" },
									]}
									dataSource={visibleMetrics}
								/>
							) : (
								<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="等待指标定义 API" />
							)}
						</Card>
					</Col>
				</Row>
			) : null}

			{showModels ? (
				<Card
					title="5. 生成公共汇总模型 / 应用数据集"
					extra={
						<Segmented
							value={modelType}
							onChange={(value) => setModelType(value as "DWS" | "ADS")}
							options={[
								{ label: "公共汇总模型", value: "DWS", icon: <PartitionOutlined /> },
								{ label: "应用数据集", value: "ADS", icon: <DashboardOutlined /> },
							]}
						/>
					}
				>
					<Row gutter={[16, 16]} className="mb-4">
						<Col xs={24} lg={8}>
							<div className="rounded border border-slate-200 p-3">
								<Text strong>可用维度 / 指标</Text>
								<div className="mt-3 space-y-3">
									<div>
										<Text type="secondary">维度</Text>
										<div className="mt-2 flex flex-wrap gap-2">
											{visibleDimensions.length ? (
												visibleDimensions.map((item) => (
													<Tag
														key={item.id}
														draggable
														onDragStart={(event) =>
															dragField(event, {
																id: item.id,
																name: item.code || item.name,
																dataType: item.dataType,
																tableName: item.fieldName,
																kind: "dimension",
															})
														}
														className="cursor-grab"
													>
														{item.name}
													</Tag>
												))
											) : (
												<Text type="secondary">暂无已保存维度</Text>
											)}
										</div>
									</div>
									<div>
										<Text type="secondary">指标</Text>
										<div className="mt-2 flex flex-wrap gap-2">
											{visibleMetrics.length ? (
												visibleMetrics.map((item) => (
													<Tag
														key={item.id}
														color="green"
														draggable
														onDragStart={(event) =>
															dragField(event, {
																id: item.id,
																name: item.code || item.name,
																dataType: item.formulaType,
																kind: "metric",
															})
														}
														className="cursor-grab"
													>
														{item.name}
													</Tag>
												))
											) : (
												<Text type="secondary">暂无已保存指标</Text>
											)}
										</div>
									</div>
								</div>
							</div>
						</Col>
						<Col xs={24} lg={16}>
							<div className="rounded border border-slate-200 bg-slate-50 p-4">
								<Row gutter={[12, 12]}>
									<Col xs={24} md={12}>
										<Space direction="vertical" className="w-full">
											<Text strong>{modelType} 统计粒度</Text>
											<VisualFlowCanvas
												nodes={modelDimensionNodes}
												height={180}
												emptyText="拖入维度字段"
												onDropItem={dropModelDimensionToCanvas}
											/>
										</Space>
									</Col>
									<Col xs={24} md={12}>
										<Space direction="vertical" className="w-full">
											<Text strong>{modelType} 输出指标</Text>
											<VisualFlowCanvas
												nodes={modelMetricNodes}
												height={180}
												emptyText="拖入指标字段"
												onDropItem={dropModelMetricToCanvas}
											/>
										</Space>
									</Col>
								</Row>
							</div>
						</Col>
					</Row>
					<Row gutter={[16, 16]}>
						<Col xs={24} xl={8}>
							<Form layout="vertical">
								<Form.Item label="目标模型名">
									<Input readOnly value={selectedModel?.name || ""} placeholder="请在下方选择或定义模型" />
								</Form.Item>
								<Form.Item label="来源 DWD 明细模型">
									<Input readOnly value={selectedDataset?.table || selectedDataset?.name || ""} placeholder="未选择" />
								</Form.Item>
								<Form.Item label="发布动作">
									<Space wrap>
										<Button
											icon={<PlayCircleOutlined />}
											loading={previewLoading}
											disabled={!selectedModelId}
											onClick={previewModelData}
										>
											预览数据
										</Button>
										<Button
											icon={<CodeOutlined />}
											loading={artifactActionLoading}
											disabled={!selectedModelId}
											onClick={generateArtifacts}
										>
											生成 dbt
										</Button>
									</Space>
								</Form.Item>
							</Form>
						</Col>
						<Col xs={24} xl={16}>
							{artifacts.length ? (
								<CompactTable<SemanticGeneratedArtifact>
									rowKey="id"
									size="small"
									pagination={{ pageSize: 3 }}
									loading={semanticLoading || artifactActionLoading}
									columns={[
										{ title: "类型", dataIndex: "artifactType", width: 130, render: (value) => value || "-" },
										{ title: "路径", dataIndex: "path", render: (value) => value || "-" },
										{ title: "状态", dataIndex: "status", width: 110, render: (value) => value || "-" },
									]}
									expandable={{
										expandedRowRender: (row) => <Input.TextArea readOnly rows={10} value={row.content || ""} />,
									}}
									dataSource={artifacts}
								/>
							) : (
								<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择模型后生成 SQL / dbt 产物" />
							)}
						</Col>
					</Row>
				</Card>
			) : null}

			{showPublish ? (
				<Row gutter={[16, 16]}>
					<Col xs={24} xl={14}>
						<Card
							title="生成物清单"
							extra={
								<Space>
									<Button size="small" onClick={saveModelCanvas} disabled={!selectedModelId}>
										保存模型画布
									</Button>
									<Button size="small" onClick={previewModelData} loading={previewLoading} disabled={!selectedModelId}>
										预览数据
									</Button>
									<Button
										size="small"
										onClick={generateArtifacts}
										loading={artifactActionLoading}
										disabled={!selectedModelId}
									>
										生成
									</Button>
									<Button
										size="small"
										onClick={() => openReviewDialog("submit")}
										loading={reviewLoading}
										disabled={!canSubmitReview}
									>
										提交审核
									</Button>
									<Button
										size="small"
										type="primary"
										onClick={() => openReviewDialog("approve")}
										loading={reviewLoading}
										disabled={!canApproveReview}
									>
										审核通过
									</Button>
									<Button
										size="small"
										danger
										onClick={() => openReviewDialog("reject")}
										loading={reviewLoading}
										disabled={!canRejectReview}
									>
										驳回
									</Button>
									<Button
										size="small"
										type="primary"
										onClick={publishArtifacts}
										loading={artifactActionLoading}
										disabled={!canPublishModel}
									>
										发布 dbt
									</Button>
									<Button
										size="small"
										onClick={registerBiDataset}
										loading={artifactActionLoading}
										disabled={!selectedModelId}
									>
										注册 BI
									</Button>
									<Button
										size="small"
										onClick={registerLineage}
										loading={artifactActionLoading}
										disabled={!selectedModelId}
									>
										写血缘
									</Button>
									<Button size="small" onClick={() => openModal("model")}>
										定义模型
									</Button>
								</Space>
							}
						>
							{models.length || artifacts.length ? (
								<Space direction="vertical" className="w-full">
									<CompactTable<SemanticModel>
										rowKey="id"
										size="small"
										pagination={false}
										loading={semanticLoading}
										rowSelection={{
											type: "radio",
											selectedRowKeys: selectedModelId ? [selectedModelId] : [],
											onChange: (keys) => setSelectedModelId(String(keys[0] || "")),
										}}
										columns={[
											{ title: "模型", dataIndex: "name" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
											{ title: "类型", dataIndex: "type", width: 90, render: (value) => value || "-" },
											{ title: "表名", dataIndex: "tableName", render: (value) => value || "-" , sorter: (a, b) => (a.tableName || "").localeCompare(b.tableName || "") },
											{
												title: "审核",
												dataIndex: "reviewStatus",
												width: 120,
												render: (value, row) => (
													<Tag color={reviewStatusColor(value || row.status)}>{value || row.status || "DRAFT"}</Tag>
												),
											},
											{ title: "发布", dataIndex: "status", width: 110, render: (value) => value || "-" },
										]}
										dataSource={models}
									/>
									{selectedModel ? (
										<Card size="small" title="审核记录">
											<Space direction="vertical" className="w-full">
												<Space wrap>
													<Tag color={reviewStatusColor(selectedModel.reviewStatus || selectedModel.status)}>
														{selectedModel.reviewStatus || selectedModel.status || "DRAFT"}
													</Tag>
													<Text type="secondary">
														提交：{selectedModel.submittedBy || "-"} {selectedModel.submittedAt || ""}
													</Text>
													<Text type="secondary">
														审核：{selectedModel.reviewedBy || "-"} {selectedModel.reviewedAt || ""}
													</Text>
												</Space>
												{selectedModel.reviewComment ? <Text>意见：{selectedModel.reviewComment}</Text> : null}
												<CompactTable<SemanticModelReviewLog>
													rowKey="id"
													size="small"
													pagination={false}
													columns={[
														{ title: "动作", dataIndex: "action", width: 100 },
														{ title: "人员", dataIndex: "actor", width: 140, render: (value) => value || "-" },
														{ title: "意见", dataIndex: "comment", render: (value) => value || "-" },
														{ title: "时间", dataIndex: "createdDate", width: 190, render: (value) => value || "-" , sorter: (a, b) => { const ta = a.createdDate ? new Date(a.createdDate as any).getTime() : 0; const tb = b.createdDate ? new Date(b.createdDate as any).getTime() : 0; return ta - tb; } },
													]}
													dataSource={reviewLogs}
												/>
											</Space>
										</Card>
									) : null}
									<CompactTable<SemanticGeneratedArtifact>
										rowKey="id"
										size="small"
										pagination={false}
										loading={semanticLoading}
										columns={[
											{ title: "生成物", dataIndex: "artifactType", width: 120, render: (value) => value || "-" },
											{ title: "路径", dataIndex: "path", render: (value) => value || "-" },
											{ title: "状态", dataIndex: "status", width: 110, render: (value) => value || "-" },
										]}
										dataSource={artifacts}
									/>
								</Space>
							) : (
								<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="等待生成物 API" />
							)}
						</Card>
					</Col>
					<Col xs={24} xl={10}>
						<Card title="后续 API 缺口">
							<Paragraph type="secondary">
								基础配置接口已接入真实数据；下面这些能力补齐后，才能从配置继续串到生成、发布、血缘和 BI 注册。
							</Paragraph>
							<Space wrap>
								{backendGaps.map((item) => (
									<Tag key={item}>{item}</Tag>
								))}
							</Space>
						</Card>
					</Col>
				</Row>
			) : null}

			{showRuns ? (
				<Card
					title="模型运行监控"
					extra={
						<Space wrap>
							<Select
								size="small"
								placeholder="选择模型"
								style={{ minWidth: 220 }}
								value={selectedModelId}
								options={models.map((item) => ({
									label: `${item.name}${item.tableName ? ` / ${item.tableName}` : ""}`,
									value: item.id,
								}))}
								onChange={setSelectedModelId}
							/>
							<Button size="small" onClick={triggerModelRun} loading={runLoading} disabled={!selectedModelId}>
								触发运行
							</Button>
							<Button
								size="small"
								onClick={async () => {
									if (!selectedModelId) return;
									setRunLoading(true);
									try {
										const runs = await listSemanticModelRuns(selectedModelId);
										setModelRuns(asArray<SemanticModelRun>(runs));
									} finally {
										setRunLoading(false);
									}
								}}
								loading={runLoading}
								disabled={!selectedModelId}
							>
								刷新
							</Button>
						</Space>
					}
				>
					{selectedModelId ? (
						<CompactTable<SemanticModelRun>
							rowKey="id"
							size="small"
							loading={runLoading}
							pagination={{ pageSize: 8 }}
							columns={[
								{
									title: "状态",
									dataIndex: "status",
									width: 120,
									render: (value) => <Tag color={reviewStatusColor(value)}>{value || "-"}</Tag>,
								},
								{ title: "Selector", dataIndex: "selector", render: (value) => value || "-" },
								{ title: "DAG", dataIndex: "dagId", render: (value) => value || "-" },
								{ title: "外部运行 ID", dataIndex: "externalRunId", render: (value) => value || "-" },
								{ title: "触发人", dataIndex: "triggeredBy", width: 120, render: (value) => value || "-" },
								{ title: "开始时间", dataIndex: "startedAt", width: 190, render: (value) => value || "-" , sorter: (a, b) => { const ta = a.startedAt ? new Date(a.startedAt as any).getTime() : 0; const tb = b.startedAt ? new Date(b.startedAt as any).getTime() : 0; return ta - tb; } },
								{ title: "耗时(ms)", dataIndex: "durationMs", width: 110, render: (value) => value ?? "-" },
								{ title: "消息", dataIndex: "message", render: (value) => value || "-" },
							]}
							dataSource={modelRuns}
						/>
					) : (
						<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择模型查看运行记录" />
					)}
				</Card>
			) : null}

			<Modal
				open={Boolean(modalType)}
				title={
					{
						domain: "新建主题域",
						object: "新建业务对象",
						dimension: "新增维度",
						metric: "新增指标",
						model: "定义模型",
					}[modalType || "domain"]
				}
				onCancel={closeModal}
				onOk={submitModal}
				confirmLoading={saving}
				destroyOnClose
			>
				<Form form={form} layout="vertical">
					{modalType === "domain" ? (
						<>
							<Alert
								type="info"
								showIcon
								className="mb-4"
								message="治理主题域为可选引用"
								description="有治理主题域时建议选择，便于后续口径、资产和血缘对齐；没有治理前置工作时可以留空，直接在开发中心建模。"
							/>
							<Form.Item name="governanceDomainId" label="治理主题域（可选）">
								<Select
									allowClear
									showSearch
									loading={governanceLoading}
									placeholder="可从治理中心主题域带入，也可以留空自建"
									optionFilterProp="label"
									options={governanceDomains.map((item) => ({
										label: item.code ? `${item.label}（${item.code}）` : item.label,
										value: item.id,
									}))}
									onChange={(value) => {
										const selected = governanceDomains.find((item) => item.id === value);
										if (!selected) return;
										const current = form.getFieldsValue(["code", "name"]);
										form.setFieldsValue({
											code: current.code || selected.code,
											name: current.name || selected.name,
										});
									}}
								/>
							</Form.Item>
							<Form.Item name="code" label="编码" rules={[{ required: true, message: "请输入编码" }]}>
								<Input placeholder="唯一编码" />
							</Form.Item>
							<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
								<Input placeholder="业务名称" />
							</Form.Item>
							<Form.Item name="description" label="说明">
								<Input.TextArea rows={3} />
							</Form.Item>
						</>
					) : null}

					{modalType === "object" ? (
						<>
							<Form.Item name="domainId" label="主题域">
								<Select allowClear options={domains.map((item) => ({ label: item.name, value: item.id }))} />
							</Form.Item>
							<Form.Item name="code" label="编码" rules={[{ required: true, message: "请输入编码" }]}>
								<Input />
							</Form.Item>
							<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
								<Input />
							</Form.Item>
							<Form.Item name="primaryKey" label="主键">
								<Input />
							</Form.Item>
							<Form.Item name="mainTable" label="主表">
								<Input />
							</Form.Item>
							<Form.Item name="description" label="说明">
								<Input.TextArea rows={3} />
							</Form.Item>
						</>
					) : null}

					{modalType === "dimension" ? (
						<>
							<Form.Item name="objectId" label="业务对象">
								<Select allowClear options={objects.map((item) => ({ label: item.name, value: item.id }))} />
							</Form.Item>
							<Form.Item name="code" label="编码" rules={[{ required: true, message: "请输入编码" }]}>
								<Input />
							</Form.Item>
							<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
								<Input />
							</Form.Item>
							<Form.Item name="fieldName" label="来源字段">
								<Input />
							</Form.Item>
							<Form.Item name="dataType" label="数据类型">
								<Input />
							</Form.Item>
						</>
					) : null}

					{modalType === "metric" ? (
						<>
							<Form.Item name="objectId" label="业务对象">
								<Select allowClear options={objects.map((item) => ({ label: item.name, value: item.id }))} />
							</Form.Item>
							<Form.Item name="code" label="编码" rules={[{ required: true, message: "请输入编码" }]}>
								<Input />
							</Form.Item>
							<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
								<Input />
							</Form.Item>
							<Form.Item name="formulaType" label="公式类型">
								<Select
									allowClear
									options={["sum", "count", "count_distinct", "avg", "count_if", "sum_if", "ratio"].map((item) => ({
										label: item,
										value: item,
									}))}
								/>
							</Form.Item>
							<Form.Item name="formulaJson" label="公式 JSON">
								<Input.TextArea rows={4} />
							</Form.Item>
							<Form.Item name="format" label="展示格式">
								<Input />
							</Form.Item>
						</>
					) : null}

					{modalType === "model" ? (
						<>
							<Form.Item name="objectId" label="业务对象">
								<Select allowClear options={objects.map((item) => ({ label: item.name, value: item.id }))} />
							</Form.Item>
							<Form.Item name="type" label="类型" rules={[{ required: true, message: "请选择类型" }]}>
								<Select
									options={[
										{ label: "DWS 公共汇总模型", value: "DWS" },
										{ label: "ADS 应用数据集", value: "ADS" },
									]}
								/>
							</Form.Item>
							<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
								<Input />
							</Form.Item>
							<Form.Item name="tableName" label="目标表名">
								<Input />
							</Form.Item>
							<Form.Item name="grain" label="统计粒度">
								<Input />
							</Form.Item>
							<Form.Item name="materialization" label="物化方式">
								<Select
									allowClear
									options={["view", "table", "incremental"].map((item) => ({ label: item, value: item }))}
								/>
							</Form.Item>
							<Form.Item name="description" label="说明">
								<Input.TextArea rows={3} />
							</Form.Item>
						</>
					) : null}
				</Form>
			</Modal>

			<Modal
				open={previewOpen}
				title="模型数据预览"
				onCancel={() => setPreviewOpen(false)}
				footer={<Button onClick={() => setPreviewOpen(false)}>关闭</Button>}
				width={960}
			>
				<Space direction="vertical" className="w-full">
					{previewResult?.success === false ? (
						<Alert
							type="warning"
							showIcon
							message="预览执行失败"
							description={previewResult.errorMessage || "请检查来源表、Join 条件和字段口径。"}
						/>
					) : null}
					<Input.TextArea readOnly rows={8} value={previewResult?.sql || ""} />
					<CompactTable<Record<string, any>>
						size="small"
						rowKey={(_, index) => String(index)}
						columns={previewColumns}
						dataSource={(previewResult?.rows || []).map((row, index) => ({ ...row, __rowIndex: index }))}
						pagination={{ pageSize: 10 }}
						scroll={{ x: true }}
					/>
					<Text type="secondary">
						行数：{previewResult?.rowCount ?? 0}，耗时：{previewResult?.durationMs ?? 0} ms
					</Text>
				</Space>
			</Modal>

			<Modal
				open={Boolean(reviewAction)}
				title={
					{
						submit: "提交模型审核",
						approve: "审核通过",
						reject: "驳回模型",
					}[reviewAction || "submit"]
				}
				onCancel={closeReviewDialog}
				onOk={submitReviewAction}
				confirmLoading={reviewLoading}
				okText={{ submit: "提交", approve: "通过", reject: "驳回" }[reviewAction || "submit"]}
				okButtonProps={{ danger: reviewAction === "reject" }}
				destroyOnClose
			>
				<Space direction="vertical" className="w-full">
					<Alert
						type={reviewAction === "reject" ? "warning" : "info"}
						showIcon
						message={
							reviewAction === "submit"
								? "提交后模型进入待审核状态，审核通过前不能发布 dbt。"
								: reviewAction === "approve"
									? "审核通过后可以发布 dbt、注册 BI 和写入血缘。"
									: "驳回后模型需要修改并重新提交审核。"
						}
					/>
					<Input.TextArea
						rows={4}
						value={reviewComment}
						onChange={(event) => setReviewComment(event.target.value)}
						placeholder={reviewAction === "reject" ? "请输入驳回原因" : "审核意见，可选"}
					/>
				</Space>
			</Modal>
		</div>
	);
}
