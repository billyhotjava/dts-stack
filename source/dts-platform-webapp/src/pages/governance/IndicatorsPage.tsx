import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Breadcrumb,
	Button,
	Card,
	Descriptions,
	Divider,
	Empty,
	Form,
	Input,
	InputNumber,
	Modal,
	Select,
	Space,
	Steps,
	Table,
	Tag,
	Tabs,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined } from "@ant-design/icons";
import { useSearchParams } from "react-router";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { PageHeader } from "@/components/page-header";
import { DatasetPicker } from "@/components/catalog/DatasetPicker";
import type { DatasetField } from "@/api/platformApi";
import {
	listIndicators,
	createIndicator,
	updateIndicator,
	deleteIndicator,
	publishIndicator,
	archiveIndicator,
	validateIndicator,
	listIndicatorVersions,
	getIndicatorVersion,
	diffIndicatorVersions,
	rollbackIndicatorVersion,
	listIndicatorReferences,
	createIndicatorReference,
	updateIndicatorReference,
	deleteIndicatorReference,
	getIndicatorPublishPreview,
	getIndicatorOpsOverview,
	getIndicatorOpsTrend,
	previewIndicator,
	listDimensions,
	createDimension,
	updateDimension,
	deleteDimension,
	publishDimension,
	archiveDimension,
	listDatasets,
} from "@/api/platformApi";
import { formatDateTime } from "@/utils/textUtils";

const STATUS_OPTIONS = [
	{ label: "草稿", value: "DRAFT" },
	{ label: "发布", value: "PUBLISHED" },
	{ label: "废止", value: "ARCHIVED" },
];
const REFERENCE_TYPE_OPTIONS = [
	{ label: "指标", value: "INDICATOR" },
	{ label: "维度", value: "DIMENSION" },
	{ label: "数据集", value: "DATASET" },
	{ label: "SQL 模型", value: "SQL_MODEL" },
	{ label: "看板", value: "DASHBOARD" },
];

type Indicator = any;
type Dimension = any;
type IndicatorReference = {
	id?: string;
	refType?: string;
	refTarget?: string;
	refName?: string;
	notes?: string;
};
type IndicatorVersion = {
	id?: string;
	version?: string;
	status?: string;
	changeSummary?: string;
	releasedAt?: string;
	createdDate?: string;
	createdBy?: string;
	snapshotJson?: string;
};
type PublishIssue = {
	code?: string;
	severity?: string;
	message?: string;
	suggestion?: string;
};
type VersionDiffRow = {
	field?: string;
	label?: string;
	before?: string | null;
	after?: string | null;
	severity?: string;
};
type VersionDiffPayload = {
	leftVersion?: string;
	rightVersion?: string;
	diffCount?: number;
	diffs?: VersionDiffRow[];
};
type WorkflowStage = {
	status: "wait" | "process" | "finish" | "error";
	message?: string;
	at?: string;
};
type PublishWorkflowState = {
	validate: WorkflowStage;
	precheck: WorkflowStage;
	publish: WorkflowStage;
};
type IndicatorOverview = {
	hours: number;
	total: number;
	published: number;
	draft: number;
	archived: number;
	validationSuccess: number;
	validationFailed: number;
	validationNever: number;
	successRate: number;
	failedRate: number;
	failureTop: Array<{ category: string; count: number }>;
};

const initialWorkflowState = (): PublishWorkflowState => ({
	validate: { status: "wait" },
	precheck: { status: "wait" },
	publish: { status: "wait" },
});

const normalizeIndicatorError = (error: any, fallback: string) => {
	const raw = String(error?.message || "").trim();
	if (!raw) return fallback;
	if (raw.includes("当前部门上下文不可访问该数据集")) return "无权访问该数据集（部门上下文不匹配）";
	if (raw.includes("无权限访问该数据集")) return "无权访问该数据集（请申请数据权限）";
	if (raw.includes("绑定的数据集不存在")) return "绑定的数据集不存在或已被删除";
	return raw;
};

const prettyJson = (value: any) => {
	if (value == null) return "";
	if (typeof value === "string") {
		try {
			return JSON.stringify(JSON.parse(value), null, 2);
		} catch {
			return value;
		}
	}
	try {
		return JSON.stringify(value, null, 2);
	} catch {
		return String(value);
	}
};
export default function Page() {
	const [searchParams, setSearchParams] = useSearchParams();
	const initialTabKey = searchParams.get("tab") === "dimensions" ? "dimensions" : "indicators";
	const initialIndicatorKeyword = searchParams.get("i_kw") || "";
	const initialIndicatorStatus = searchParams.get("i_st") || undefined;
	const initialDimensionKeyword = searchParams.get("d_kw") || "";
	const initialDimensionStatus = searchParams.get("d_st") || undefined;

	const [tabKey, setTabKey] = useState(initialTabKey);
	const [indicators, setIndicators] = useState<Indicator[]>([]);
	const [indicatorPage, setIndicatorPage] = useState({ page: 1, size: 10, total: 0 });
	const [dimensions, setDimensions] = useState<Dimension[]>([]);
	const [dimensionPage, setDimensionPage] = useState({ page: 1, size: 10, total: 0 });
	const [loading, setLoading] = useState(false);
	const [indicatorModal, setIndicatorModal] = useState(false);
	const [dimensionModal, setDimensionModal] = useState(false);
	const [editingIndicator, setEditingIndicator] = useState<Indicator | null>(null);
	const [editingDimension, setEditingDimension] = useState<Dimension | null>(null);
	const [indicatorForm] = Form.useForm();
	const [dimensionForm] = Form.useForm();
	const [indicatorFilters, setIndicatorFilters] = useState<{ keyword?: string; status?: string }>({
		keyword: initialIndicatorKeyword.trim() || undefined,
		status: initialIndicatorStatus || undefined,
	});
	const [dimensionFilters, setDimensionFilters] = useState<{ keyword?: string; status?: string }>({
		keyword: initialDimensionKeyword.trim() || undefined,
		status: initialDimensionStatus || undefined,
	});
	const [indicatorKeyword, setIndicatorKeyword] = useState(initialIndicatorKeyword);
	const [indicatorStatus, setIndicatorStatus] = useState<string | undefined>(initialIndicatorStatus);
	const [dimensionKeyword, setDimensionKeyword] = useState(initialDimensionKeyword);
	const [dimensionStatus, setDimensionStatus] = useState<string | undefined>(initialDimensionStatus);
	const [previewLimit, setPreviewLimit] = useState(20);
	const [previewModal, setPreviewModal] = useState(false);
	const [previewPayload, setPreviewPayload] = useState<any>(null);
	const [publishPreviewModal, setPublishPreviewModal] = useState(false);
	const [publishPreviewPayload, setPublishPreviewPayload] = useState<any>(null);
	const [versionsModal, setVersionsModal] = useState(false);
	const [versions, setVersions] = useState<IndicatorVersion[]>([]);
	const [leftVersion, setLeftVersion] = useState<string>("CURRENT");
	const [rightVersion, setRightVersion] = useState<string>("");
	const [versionDiffLoading, setVersionDiffLoading] = useState(false);
	const [versionDiffPayload, setVersionDiffPayload] = useState<VersionDiffPayload | null>(null);
	const [versionDetailModal, setVersionDetailModal] = useState(false);
	const [versionDetail, setVersionDetail] = useState<IndicatorVersion | null>(null);
	const [referencesModal, setReferencesModal] = useState(false);
	const [references, setReferences] = useState<IndicatorReference[]>([]);
	const [editingReferenceId, setEditingReferenceId] = useState<string | undefined>(undefined);
	const [referenceForm] = Form.useForm();
	const [currentIndicator, setCurrentIndicator] = useState<Indicator | null>(null);
	const [publishWorkflowModal, setPublishWorkflowModal] = useState(false);
	const [publishWorkflow, setPublishWorkflow] = useState<PublishWorkflowState>(initialWorkflowState());
	const [publishWorkflowRunning, setPublishWorkflowRunning] = useState(false);
	const [overview, setOverview] = useState<IndicatorOverview>({
		hours: 168,
		total: 0,
		published: 0,
		draft: 0,
		archived: 0,
		validationSuccess: 0,
		validationFailed: 0,
		validationNever: 0,
		successRate: 0,
		failedRate: 0,
		failureTop: [],
	});
	const [trendRows, setTrendRows] = useState<any[]>([]);
	const [datasets, setDatasets] = useState<{ id: string; name: string }[]>([]);
	const [datasetFields, setDatasetFields] = useState<DatasetField[]>([]);
	const canManage = useGovernanceManageAccess();

	const syncQueryState = (patch?: {
		tab?: string;
		indicatorKeyword?: string;
		indicatorStatus?: string;
		dimensionKeyword?: string;
		dimensionStatus?: string;
	}) => {
		const params = new URLSearchParams(searchParams);
		const tab = patch?.tab ?? tabKey;
		const iKw = patch?.indicatorKeyword ?? indicatorKeyword;
		const iSt = patch?.indicatorStatus ?? indicatorStatus;
		const dKw = patch?.dimensionKeyword ?? dimensionKeyword;
		const dSt = patch?.dimensionStatus ?? dimensionStatus;
		const setOrDelete = (key: string, value?: string) => {
			if (!value || !value.trim()) {
				params.delete(key);
				return;
			}
			params.set(key, value.trim());
		};
		if (tab === "dimensions") {
			params.set("tab", "dimensions");
		} else {
			params.delete("tab");
		}
		setOrDelete("i_kw", iKw);
		setOrDelete("i_st", iSt);
		setOrDelete("d_kw", dKw);
		setOrDelete("d_st", dSt);
		setSearchParams(params, { replace: true });
	};

	const loadIndicators = async (
		page = indicatorPage.page,
		size = indicatorPage.size,
		filters: { keyword?: string; status?: string } = indicatorFilters,
	) => {
		setLoading(true);
		try {
			const resp: any = await listIndicators({
				page: page - 1,
				size,
				keyword: filters.keyword || undefined,
				status: filters.status || undefined,
			});
			setIndicators(Array.isArray(resp?.content) ? resp.content : []);
			setIndicatorPage({
				page: Number(resp?.page ?? page - 1) + 1,
				size: Number(resp?.size ?? size),
				total: Number(resp?.total ?? 0),
			});
			void loadIndicatorOverview();
		} catch (error: any) {
			toast.error(error?.message || "指标加载失败");
		} finally {
			setLoading(false);
		}
	};

	const loadIndicatorOverview = async () => {
		try {
			const [overviewResp, trendResp] = await Promise.all([
				getIndicatorOpsOverview({ hours: 168 }),
				getIndicatorOpsTrend({ hours: 168, bucketHours: 24 }),
			]);
			const overviewData: any = overviewResp || {};
			const statusDist = overviewData?.statusDistribution || {};
			const validation = overviewData?.validation || {};
			setOverview({
				hours: Number(overviewData?.hours ?? 168),
				total: Number(overviewData?.totalIndicators ?? 0),
				published: Number(statusDist?.published ?? 0),
				draft: Number(statusDist?.draft ?? 0),
				archived: Number(statusDist?.archived ?? 0),
				validationSuccess: Number(validation?.success ?? 0),
				validationFailed: Number(validation?.failed ?? 0),
				validationNever: Number(validation?.never ?? 0),
				successRate: Number(validation?.successRate ?? 0),
				failedRate: Number(validation?.failedRate ?? 0),
				failureTop: Array.isArray(overviewData?.failureTop) ? overviewData.failureTop : [],
			});
			setTrendRows(Array.isArray(trendResp as any) ? (trendResp as any[]) : []);
		} catch {
			// keep page usable if observability summary fetch fails
		}
	};

	const loadDimensions = async (
		page = dimensionPage.page,
		size = dimensionPage.size,
		filters: { keyword?: string; status?: string } = dimensionFilters,
	) => {
		setLoading(true);
		try {
			const resp: any = await listDimensions({
				page: page - 1,
				size,
				keyword: filters.keyword || undefined,
				status: filters.status || undefined,
			});
			setDimensions(Array.isArray(resp?.content) ? resp.content : []);
			setDimensionPage({
				page: Number(resp?.page ?? page - 1) + 1,
				size: Number(resp?.size ?? size),
				total: Number(resp?.total ?? 0),
			});
		} catch (error: any) {
			toast.error(error?.message || "维度加载失败");
		} finally {
			setLoading(false);
		}
	};

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200 });
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(list.map((item: any) => ({ id: String(item.id), name: item.name || item.id })));
		} catch (error: any) {
			toast.error(error?.message || "数据集加载失败");
		}
	};

	useEffect(() => {
		void loadIndicators(1, indicatorPage.size, indicatorFilters);
		void loadDimensions(1, dimensionPage.size, dimensionFilters);
		void loadDatasets();
		void loadIndicatorOverview();
	}, []);

	const openIndicatorModal = (row?: Indicator) => {
		setEditingIndicator(row || null);
		setDatasetFields([]);
		indicatorForm.setFieldsValue({
			code: row?.code || "",
			name: row?.name || "",
			category: row?.category || "",
			definition: row?.definition || "",
			expressionSql: row?.expressionSql || "",
			datasetId: row?.datasetId || undefined,
			dimensionFields: row?.dimensionFields || undefined,
			status: row?.status || "DRAFT",
			versionNotes: row?.versionNotes || "",
			tags: row?.tags || "",
		});
		setIndicatorModal(true);
	};

	const saveIndicator = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		try {
			const values = await indicatorForm.validateFields();
			if (editingIndicator?.id) {
				await updateIndicator(editingIndicator.id, values);
				toast.success("指标已更新");
			} else {
				await createIndicator(values);
				toast.success("指标已新增");
			}
			setIndicatorModal(false);
			await loadIndicators();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const openDimensionModal = (row?: Dimension) => {
		setEditingDimension(row || null);
		dimensionForm.setFieldsValue({
			code: row?.code || "",
			name: row?.name || "",
			description: row?.description || "",
			status: row?.status || "DRAFT",
			tags: row?.tags || "",
		});
		setDimensionModal(true);
	};

	const saveDimension = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		try {
			const values = await dimensionForm.validateFields();
			if (editingDimension?.id) {
				await updateDimension(editingDimension.id, values);
				toast.success("维度已更新");
			} else {
				await createDimension(values);
				toast.success("维度已新增");
			}
			setDimensionModal(false);
			await loadDimensions();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const runPublishPreview = async (row: Indicator) => {
		try {
			const payload = await getIndicatorPublishPreview(row.id);
			setCurrentIndicator(row);
			setPublishPreviewPayload(payload || {});
			setPublishPreviewModal(true);
		} catch (error: any) {
			toast.error(normalizeIndicatorError(error, "发布预检失败"));
		}
	};

	const runComputePreview = async (row: Indicator) => {
		try {
			const payload = await previewIndicator(row.id, { limit: previewLimit });
			setCurrentIndicator(row);
			setPreviewPayload(payload || {});
			setPreviewModal(true);
		} catch (error: any) {
			toast.error(normalizeIndicatorError(error, "计算预览失败"));
		}
	};

	const openVersions = async (row: Indicator) => {
		try {
			const list = await listIndicatorVersions(row.id);
			const rows = Array.isArray(list) ? (list as IndicatorVersion[]) : [];
			setVersions(rows);
			setCurrentIndicator(row);
			const targetVersion = String(rows?.[0]?.version || row?.version || "");
			setLeftVersion("CURRENT");
			setRightVersion(targetVersion);
			if (targetVersion) {
				setVersionDiffLoading(true);
				try {
					const payload = (await diffIndicatorVersions(row.id, {
						left: "CURRENT",
						right: targetVersion,
					})) as VersionDiffPayload;
					setVersionDiffPayload(payload || null);
				} finally {
					setVersionDiffLoading(false);
				}
			} else {
				setVersionDiffPayload(null);
			}
			setVersionsModal(true);
		} catch (error: any) {
			toast.error(error?.message || "版本历史加载失败");
		}
	};

	const refreshVersionDiff = async (left: string, right: string) => {
		if (!currentIndicator?.id || !left || !right) return;
		setVersionDiffLoading(true);
		try {
			const payload = (await diffIndicatorVersions(currentIndicator.id, { left, right })) as VersionDiffPayload;
			setVersionDiffPayload(payload || null);
		} catch (error: any) {
			toast.error(error?.message || "版本差异加载失败");
		} finally {
			setVersionDiffLoading(false);
		}
	};

	const openVersionDetail = async (version: string) => {
		if (!currentIndicator?.id || !version) return;
		try {
			const detail = await getIndicatorVersion(currentIndicator.id, version);
			setVersionDetail((detail || null) as IndicatorVersion | null);
			setVersionDetailModal(true);
		} catch (error: any) {
			toast.error(error?.message || "版本快照加载失败");
		}
	};

	const runRollback = async (version: string) => {
		if (!canManage || !currentIndicator?.id || !version) return;
		Modal.confirm({
			title: "回滚到该版本？",
			content: `将创建新版本并回滚到历史版本 ${version} 的快照内容。`,
			okText: "确认回滚",
			cancelText: "取消",
			onOk: async () => {
				try {
					await rollbackIndicatorVersion(currentIndicator.id, version, {
						reason: `前端回滚操作 from ${version}`,
						publishAfterRollback: false,
					});
					toast.success("回滚成功，已生成新草稿版本");
					await loadIndicators();
					const list = await listIndicatorVersions(currentIndicator.id);
					const rows = Array.isArray(list) ? (list as IndicatorVersion[]) : [];
					setVersions(rows);
					const latest = String(rows?.[0]?.version || "");
					if (latest) {
						setLeftVersion("CURRENT");
						setRightVersion(latest);
						await refreshVersionDiff("CURRENT", latest);
					}
				} catch (error: any) {
					toast.error(error?.message || "版本回滚失败");
				}
			},
		});
	};

	const loadReferences = async (indicatorId: string) => {
		const list = await listIndicatorReferences(indicatorId);
		setReferences(Array.isArray(list) ? (list as IndicatorReference[]) : []);
	};

	const openReferences = async (row: Indicator) => {
		try {
			await loadReferences(row.id);
			setCurrentIndicator(row);
			setEditingReferenceId(undefined);
			referenceForm.resetFields();
			setReferencesModal(true);
		} catch (error: any) {
			toast.error(error?.message || "引用关系加载失败");
		}
	};

	const saveReference = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!currentIndicator?.id) return;
		try {
			const values = await referenceForm.validateFields();
			if (editingReferenceId) {
				await updateIndicatorReference(currentIndicator.id, editingReferenceId, values);
				toast.success("引用关系已更新");
			} else {
				await createIndicatorReference(currentIndicator.id, values);
				toast.success("引用关系已新增");
			}
			referenceForm.resetFields();
			setEditingReferenceId(undefined);
			await loadReferences(currentIndicator.id);
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "引用关系保存失败");
		}
	};

	const editReference = (ref: IndicatorReference) => {
		setEditingReferenceId(ref.id);
		referenceForm.setFieldsValue({
			refType: ref.refType || undefined,
			refTarget: ref.refTarget || "",
			refName: ref.refName || "",
			notes: ref.notes || "",
		});
	};

	const removeReference = async (refId?: string) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!currentIndicator?.id || !refId) return;
		try {
			await deleteIndicatorReference(currentIndicator.id, refId);
			toast.success("引用关系已删除");
			await loadReferences(currentIndicator.id);
		} catch (error: any) {
			toast.error(error?.message || "引用关系删除失败");
		}
	};

	const openPublishWorkflow = (row: Indicator) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		setCurrentIndicator(row);
		setPublishWorkflow(initialWorkflowState());
		setPublishWorkflowModal(true);
	};

	const runWorkflowValidate = async (indicatorId: string) => {
		if (!canManage) return false;
		setPublishWorkflow((prev) => ({
			...prev,
			validate: { status: "process", message: "执行中..." },
		}));
		try {
			const result: any = await validateIndicator(indicatorId);
			const success = String(result?.status || "").toUpperCase() === "SUCCESS";
			setPublishWorkflow((prev) => ({
				...prev,
				validate: {
					status: success ? "finish" : "error",
					message: result?.message || (success ? "校验通过" : "校验失败"),
					at: formatDateTime(result?.validatedAt),
				},
			}));
			await loadIndicators();
			return success;
		} catch (error: any) {
			setPublishWorkflow((prev) => ({
				...prev,
				validate: {
					status: "error",
					message: normalizeIndicatorError(error, "校验失败"),
					at: formatDateTime(new Date().toISOString()),
				},
			}));
			return false;
		}
	};

	const runWorkflowPrecheck = async (indicatorId: string) => {
		if (!canManage) return false;
		setPublishWorkflow((prev) => ({
			...prev,
			precheck: { status: "process", message: "执行中..." },
		}));
		try {
			const result: any = await getIndicatorPublishPreview(indicatorId);
			const blockers = Array.isArray(result?.blockingIssues) ? (result.blockingIssues as PublishIssue[]) : [];
			const warnings = Array.isArray(result?.warningIssues) ? (result.warningIssues as PublishIssue[]) : [];
			const success = Boolean(result?.readyToPublish);
			const blockerMsg = blockers.map((item) => String(item?.message || "")).filter(Boolean).join("；");
			const warningMsg = warnings.map((item) => String(item?.message || "")).filter(Boolean).join("；");
			setPublishWorkflow((prev) => ({
				...prev,
				precheck: {
					status: success ? "finish" : "error",
					message: success ? (warningMsg ? `预检通过（告警：${warningMsg}）` : "预检通过") : blockerMsg || "预检未通过",
					at: formatDateTime(new Date().toISOString()),
				},
			}));
			setPublishPreviewPayload(result || {});
			return success;
		} catch (error: any) {
			setPublishWorkflow((prev) => ({
				...prev,
				precheck: {
					status: "error",
					message: normalizeIndicatorError(error, "预检失败"),
					at: formatDateTime(new Date().toISOString()),
				},
			}));
			return false;
		}
	};

	const runWorkflowPublish = async (indicatorId: string) => {
		if (!canManage) return false;
		setPublishWorkflow((prev) => ({
			...prev,
			publish: { status: "process", message: "执行中..." },
		}));
		try {
			await publishIndicator(indicatorId);
			setPublishWorkflow((prev) => ({
				...prev,
				publish: {
					status: "finish",
					message: "发布完成",
					at: formatDateTime(new Date().toISOString()),
				},
			}));
			await loadIndicators();
			return true;
		} catch (error: any) {
			setPublishWorkflow((prev) => ({
				...prev,
				publish: {
					status: "error",
					message: normalizeIndicatorError(error, "发布失败"),
					at: formatDateTime(new Date().toISOString()),
				},
			}));
			return false;
		}
	};

	const runPublishWorkflowAll = async () => {
		if (!currentIndicator?.id) return;
		setPublishWorkflowRunning(true);
		try {
			const validateOk = await runWorkflowValidate(currentIndicator.id);
			if (!validateOk) return;
			const precheckOk = await runWorkflowPrecheck(currentIndicator.id);
			if (!precheckOk) return;
			await runWorkflowPublish(currentIndicator.id);
		} finally {
			setPublishWorkflowRunning(false);
		}
	};

	const indicatorColumns: ColumnsType<Indicator> = [
		{ title: "名称", dataIndex: "name", render: (v) => v || "-" },
		{ title: "编码", dataIndex: "code", width: 140, render: (v) => v || "-" },
		{ title: "状态", dataIndex: "status", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "数据集", dataIndex: "datasetId", render: (v) => datasets.find((d) => d.id === v)?.name || v || "-" },
		{
			title: "最近校验",
			dataIndex: "lastValidationStatus",
			width: 120,
			render: (v) => {
				if (!v) return "-";
				const status = String(v).toUpperCase();
				const color = status === "SUCCESS" ? "green" : status === "FAILED" ? "red" : "default";
				return <Tag color={color}>{status}</Tag>;
			},
		},
		{
			title: "操作",
			width: 620,
			render: (_, record) => (
				<Space wrap>
					<Button size="small" icon={<EditOutlined />} onClick={() => openIndicatorModal(record)} disabled={!canManage}>
						编辑
					</Button>
					<Button size="small" onClick={() => void runComputePreview(record)}>
						预览
					</Button>
					<Button size="small" onClick={() => void runPublishPreview(record)}>
						发布预检
					</Button>
					<Button size="small" onClick={() => void openVersions(record)}>
						版本
					</Button>
					<Button size="small" onClick={() => void openReferences(record)}>
						引用
					</Button>
					<Button size="small" onClick={() => openPublishWorkflow(record)} disabled={!canManage}>
						流程发布
					</Button>
					<Button
						size="small"
						icon={<UploadOutlined />}
						onClick={async () => {
							if (!canManage) return;
							try {
								const precheck: any = await getIndicatorPublishPreview(record.id);
								if (!precheck?.readyToPublish) {
									setCurrentIndicator(record);
									setPublishPreviewPayload(precheck || {});
									setPublishPreviewModal(true);
									toast.error("发布前检查未通过，请先修复问题");
									return;
								}
								await publishIndicator(record.id);
								toast.success("指标已发布");
								await loadIndicators();
							} catch (error: any) {
								toast.error(normalizeIndicatorError(error, "发布失败"));
							}
						}}
						disabled={!canManage}
					>
						发布
					</Button>
					<Button
						size="small"
						onClick={async () => {
							if (!canManage) return;
							try {
								await archiveIndicator(record.id);
								toast.success("指标已废止");
								await loadIndicators();
							} catch (error: any) {
								toast.error(error?.message || "废止失败");
							}
						}}
						disabled={!canManage}
					>
						废止
					</Button>
					<Button
						size="small"
						onClick={async () => {
							if (!canManage) return;
							try {
								await validateIndicator(record.id);
								toast.success("已触发校验");
								await loadIndicators();
							} catch (error: any) {
								toast.error(normalizeIndicatorError(error, "校验失败"));
							}
						}}
						disabled={!canManage}
					>
						校验
					</Button>
					<Button
						size="small"
						danger
						icon={<DeleteOutlined />}
						onClick={async () => {
							if (!canManage) return;
							try {
								await deleteIndicator(record.id);
								toast.success("指标已删除");
								await loadIndicators();
							} catch (error: any) {
								toast.error(error?.message || "删除失败");
							}
						}}
						disabled={!canManage}
					>
						删除
					</Button>
				</Space>
			),
		},
	];

	const dimensionColumns: ColumnsType<Dimension> = [
		{ title: "名称", dataIndex: "name", render: (v) => v || "-" },
		{ title: "编码", dataIndex: "code", width: 140, render: (v) => v || "-" },
		{ title: "状态", dataIndex: "status", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "描述", dataIndex: "description", render: (v) => v || "-" },
		{
			title: "操作",
			width: 220,
			render: (_, record) => (
				<Space>
					<Button size="small" icon={<EditOutlined />} onClick={() => openDimensionModal(record)} disabled={!canManage}>
						编辑
					</Button>
					<Button
						size="small"
						onClick={async () => {
							if (!canManage) return;
							await publishDimension(record.id);
							toast.success("维度已发布");
							await loadDimensions();
						}}
						disabled={!canManage}
					>
						发布
					</Button>
					<Button
						size="small"
						onClick={async () => {
							if (!canManage) return;
							await archiveDimension(record.id);
							toast.success("维度已废止");
							await loadDimensions();
						}}
						disabled={!canManage}
					>
						废止
					</Button>
					<Button
						size="small"
						danger
						icon={<DeleteOutlined />}
						onClick={async () => {
							if (!canManage) return;
							await deleteDimension(record.id);
							toast.success("维度已删除");
							await loadDimensions();
						}}
						disabled={!canManage}
					>
						删除
					</Button>
				</Space>
			),
		},
	];

	const previewHeaders = useMemo(
		() =>
			Array.isArray(previewPayload?.headers)
				? previewPayload.headers.map((item: any) => String(item))
				: [],
		[previewPayload],
	);

	const previewRows = useMemo(() => {
		const rawRows = Array.isArray(previewPayload?.rows) ? previewPayload.rows : [];
		return rawRows.map((row: any, index: number) => {
			if (Array.isArray(row)) {
				const mapped: Record<string, any> = { key: index };
				row.forEach((value, colIndex) => {
					const col = previewHeaders[colIndex] || `col_${colIndex + 1}`;
					mapped[col] = value;
				});
				return mapped;
			}
			if (row && typeof row === "object") {
				return { key: index, ...row };
			}
			return { key: index, value: row };
		});
	}, [previewHeaders, previewPayload]);

	const previewColumns = useMemo<ColumnsType<any>>(() => {
		const renderValue = (value: any) => {
			if (value == null || value === "") return "-";
			if (typeof value === "object") {
				return (
					<Typography.Text ellipsis style={{ maxWidth: 280, display: "inline-block" }}>
						{prettyJson(value)}
					</Typography.Text>
				);
			}
			return String(value);
		};
		if (previewHeaders.length > 0) {
			return previewHeaders.map((header: string) => ({
				title: header,
				dataIndex: header,
				key: header,
				ellipsis: true,
				render: renderValue,
			}));
		}
		const first = previewRows[0] || {};
		return Object.keys(first)
			.filter((key) => key !== "key")
			.map((key) => ({
				title: key,
				dataIndex: key,
				key,
				ellipsis: true,
				render: renderValue,
			}));
	}, [previewHeaders, previewRows]);

	const runIndicatorSearch = () => {
		const filters = {
			keyword: indicatorKeyword.trim() || undefined,
			status: indicatorStatus || undefined,
		};
		setIndicatorFilters(filters);
		syncQueryState({
			indicatorKeyword,
			indicatorStatus,
		});
		void loadIndicators(1, indicatorPage.size, filters);
	};

	const resetIndicatorSearch = () => {
		setIndicatorKeyword("");
		setIndicatorStatus(undefined);
		setIndicatorFilters({});
		syncQueryState({
			indicatorKeyword: "",
			indicatorStatus: undefined,
		});
		void loadIndicators(1, indicatorPage.size, {});
	};

	const runDimensionSearch = () => {
		const filters = {
			keyword: dimensionKeyword.trim() || undefined,
			status: dimensionStatus || undefined,
		};
		setDimensionFilters(filters);
		syncQueryState({
			dimensionKeyword,
			dimensionStatus,
		});
		void loadDimensions(1, dimensionPage.size, filters);
	};

	const resetDimensionSearch = () => {
		setDimensionKeyword("");
		setDimensionStatus(undefined);
		setDimensionFilters({});
		syncQueryState({
			dimensionKeyword: "",
			dimensionStatus: undefined,
		});
		void loadDimensions(1, dimensionPage.size, {});
	};

	return (
		<div className="space-y-6">
			<Breadcrumb items={[{ title: "数据治理中心" }, { title: "指标中心" }, { title: "指标字典" }]} />
			<PageHeader title="数据治理中心 / 指标中心" />
			<Card size="small">
				<Space wrap size={16}>
					<Card size="small" title="指标总数" style={{ minWidth: 160 }}>
						<Typography.Title level={4} style={{ margin: 0 }}>
							{overview.total}
						</Typography.Title>
					</Card>
					<Card size="small" title="已发布" style={{ minWidth: 160 }}>
						<Typography.Title level={4} style={{ margin: 0, color: "#166534" }}>
							{overview.published}
						</Typography.Title>
					</Card>
					<Card size="small" title="草稿" style={{ minWidth: 160 }}>
						<Typography.Title level={4} style={{ margin: 0, color: "#1d4ed8" }}>
							{overview.draft}
						</Typography.Title>
					</Card>
					<Card size="small" title="废止" style={{ minWidth: 160 }}>
						<Typography.Title level={4} style={{ margin: 0, color: "#7c2d12" }}>
							{overview.archived}
						</Typography.Title>
					</Card>
					<Card size="small" title="校验成功" style={{ minWidth: 160 }}>
						<Typography.Title level={4} style={{ margin: 0, color: "#15803d" }}>
							{overview.validationSuccess}
						</Typography.Title>
					</Card>
					<Card size="small" title="校验失败" style={{ minWidth: 160 }}>
						<Typography.Title level={4} style={{ margin: 0, color: "#b91c1c" }}>
							{overview.validationFailed}
						</Typography.Title>
					</Card>
					<Card size="small" title="未校验/窗口外" style={{ minWidth: 160 }}>
						<Typography.Title level={4} style={{ margin: 0, color: "#64748b" }}>
							{overview.validationNever}
						</Typography.Title>
					</Card>
					<Card size="small" title="成功率(%)" style={{ minWidth: 160 }}>
						<Typography.Title level={4} style={{ margin: 0, color: "#166534" }}>
							{overview.successRate}
						</Typography.Title>
					</Card>
					<Card size="small" title="失败率(%)" style={{ minWidth: 160 }}>
						<Typography.Title level={4} style={{ margin: 0, color: "#b91c1c" }}>
							{overview.failedRate}
						</Typography.Title>
					</Card>
				</Space>
				<Divider />
				<Typography.Text strong>最近 {overview.hours} 小时失败分类 TopN</Typography.Text>
				{overview.failureTop.length > 0 ? (
					<Table
						size="small"
						pagination={false}
						rowKey={(row) => `${row.category}-${row.count}`}
						style={{ marginTop: 8 }}
						columns={[
							{ title: "分类", dataIndex: "category", width: 220 },
							{ title: "失败数", dataIndex: "count", width: 120 },
						]}
						dataSource={overview.failureTop}
					/>
				) : (
					<Empty style={{ marginTop: 8 }} description="暂无失败分类数据" />
				)}
				<Divider />
				<Typography.Text strong>最近 7 天校验趋势（按天）</Typography.Text>
				<Table
					size="small"
					pagination={{ pageSize: 7 }}
					rowKey={(row) => String(row.bucketStart)}
					style={{ marginTop: 8 }}
					columns={[
						{
							title: "时间段",
							dataIndex: "bucketStart",
							render: (_, row) => `${formatDateTime(row.bucketStart)} ~ ${formatDateTime(row.bucketEnd)}`,
						},
						{ title: "总数", dataIndex: "total", width: 100 },
						{ title: "成功", dataIndex: "success", width: 100 },
						{ title: "失败", dataIndex: "failed", width: 100 },
						{ title: "失败率(%)", dataIndex: "failedRate", width: 120 },
					]}
					dataSource={trendRows}
				/>
			</Card>
			<Card>
				<Tabs
					activeKey={tabKey}
					onChange={(value) => {
						setTabKey(value);
						syncQueryState({ tab: value });
					}}
					items={[
						{
							key: "indicators",
							label: "指标字典",
							children: (
								<>
									<Space className="mb-3" wrap>
										<Input
											allowClear
											placeholder="关键字（名称/编码）"
											value={indicatorKeyword}
											onChange={(event) => setIndicatorKeyword(event.target.value)}
											onPressEnter={runIndicatorSearch}
											style={{ width: 260 }}
										/>
										<Select
											allowClear
											placeholder="状态"
											value={indicatorStatus}
											options={STATUS_OPTIONS}
											onChange={(value) => setIndicatorStatus(value)}
											style={{ width: 140 }}
										/>
										<Button onClick={runIndicatorSearch}>查询</Button>
										<Button onClick={resetIndicatorSearch}>重置</Button>
										<Button type="primary" icon={<PlusOutlined />} onClick={() => openIndicatorModal()} disabled={!canManage}>
											新增指标
										</Button>
									</Space>
									<Table
										rowKey={(record) => record.id}
										columns={indicatorColumns}
										dataSource={indicators}
										loading={loading}
										pagination={{
											current: indicatorPage.page,
											pageSize: indicatorPage.size,
											total: indicatorPage.total,
											onChange: (page, size) => loadIndicators(page, size),
										}}
									/>
								</>
							),
						},
						{
							key: "dimensions",
							label: "维度字典",
							children: (
								<>
									<Space className="mb-3" wrap>
										<Input
											allowClear
											placeholder="关键字（名称/编码）"
											value={dimensionKeyword}
											onChange={(event) => setDimensionKeyword(event.target.value)}
											onPressEnter={runDimensionSearch}
											style={{ width: 260 }}
										/>
										<Select
											allowClear
											placeholder="状态"
											value={dimensionStatus}
											options={STATUS_OPTIONS}
											onChange={(value) => setDimensionStatus(value)}
											style={{ width: 140 }}
										/>
										<Button onClick={runDimensionSearch}>查询</Button>
										<Button onClick={resetDimensionSearch}>重置</Button>
										<Button type="primary" icon={<PlusOutlined />} onClick={() => openDimensionModal()} disabled={!canManage}>
											新增维度
										</Button>
									</Space>
									<Table
										rowKey={(record) => record.id}
										columns={dimensionColumns}
										dataSource={dimensions}
										loading={loading}
										pagination={{
											current: dimensionPage.page,
											pageSize: dimensionPage.size,
											total: dimensionPage.total,
											onChange: (page, size) => loadDimensions(page, size),
										}}
									/>
								</>
							),
						},
					]}
				/>
			</Card>

			<Modal
				open={indicatorModal}
				title={editingIndicator ? "编辑指标" : "新增指标"}
				onCancel={() => setIndicatorModal(false)}
				onOk={saveIndicator}
				okText="保存"
				okButtonProps={{ disabled: !canManage }}
				width={720}
				destroyOnClose
			>
				<Form form={indicatorForm} layout="vertical">
					<Form.Item label="指标名称" name="name" rules={[{ required: true, message: "请输入名称" }]}>
						<Input />
					</Form.Item>
					<Form.Item label="指标编码" name="code">
						<Input />
					</Form.Item>
					<Form.Item label="分类" name="category">
						<Input />
					</Form.Item>
					<Form.Item label="定义" name="definition">
						<Input.TextArea rows={3} />
					</Form.Item>
					<Form.Item label="计算SQL" name="expressionSql">
						<Input.TextArea rows={4} />
					</Form.Item>
					<Form.Item label="来源数据集" name="datasetId">
						<DatasetPicker
							onFieldsLoaded={(fields) => setDatasetFields(fields)}
							placeholder="选择来源数据集"
						/>
					</Form.Item>
					<Form.Item label="维度字段" name="dimensionFields">
						<Select
							mode="multiple"
							placeholder="选择或输入维度字段"
							options={datasetFields.map((f) => ({ label: `${f.name} (${f.dataType})`, value: f.name }))}
							allowClear
							style={{ width: "100%" }}
						/>
					</Form.Item>
					<Form.Item label="状态" name="status">
						<Select options={STATUS_OPTIONS} />
					</Form.Item>
					<Form.Item label="版本说明" name="versionNotes">
						<Input.TextArea rows={2} />
					</Form.Item>
					<Form.Item label="标签" name="tags">
						<Input placeholder="逗号分隔" />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={dimensionModal}
				title={editingDimension ? "编辑维度" : "新增维度"}
				onCancel={() => setDimensionModal(false)}
				onOk={saveDimension}
				okText="保存"
				okButtonProps={{ disabled: !canManage }}
				destroyOnClose
			>
				<Form form={dimensionForm} layout="vertical">
					<Form.Item label="维度名称" name="name" rules={[{ required: true, message: "请输入名称" }]}>
						<Input />
					</Form.Item>
					<Form.Item label="维度编码" name="code">
						<Input />
					</Form.Item>
					<Form.Item label="描述" name="description">
						<Input.TextArea rows={3} />
					</Form.Item>
					<Form.Item label="状态" name="status">
						<Select options={STATUS_OPTIONS} />
					</Form.Item>
					<Form.Item label="标签" name="tags">
						<Input placeholder="逗号分隔" />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={publishWorkflowModal}
				title={`发布流程${currentIndicator?.name ? ` - ${currentIndicator.name}` : ""}`}
				onCancel={() => {
					setPublishWorkflowModal(false);
					setPublishWorkflowRunning(false);
				}}
				footer={null}
				width={980}
			>
				<Space direction="vertical" style={{ width: "100%" }} size={12}>
					<Descriptions size="small" bordered column={2}>
						<Descriptions.Item label="指标编码">{currentIndicator?.code || "-"}</Descriptions.Item>
						<Descriptions.Item label="状态">{currentIndicator?.status || "-"}</Descriptions.Item>
						<Descriptions.Item label="最近校验">{currentIndicator?.lastValidationStatus || "-"}</Descriptions.Item>
						<Descriptions.Item label="最近校验时间">
							{formatDateTime(currentIndicator?.lastValidatedAt)}
						</Descriptions.Item>
						<Descriptions.Item label="最近修改人">{currentIndicator?.lastModifiedBy || "-"}</Descriptions.Item>
						<Descriptions.Item label="最近修改时间">
							{formatDateTime(currentIndicator?.lastModifiedDate)}
						</Descriptions.Item>
					</Descriptions>
					<Steps
						items={[
							{
								title: "校验",
								description: publishWorkflow.validate.message || "执行 SQL 安全与权限校验",
								status: publishWorkflow.validate.status,
							},
							{
								title: "预检",
								description: publishWorkflow.precheck.message || "检查数据集、引用与签名一致性",
								status: publishWorkflow.precheck.status,
							},
							{
								title: "发布",
								description: publishWorkflow.publish.message || "发布当前指标版本",
								status: publishWorkflow.publish.status,
							},
						]}
					/>
					<Space wrap>
						<Button
							loading={publishWorkflowRunning}
							onClick={() => {
								if (currentIndicator?.id) void runWorkflowValidate(currentIndicator.id);
							}}
							disabled={!canManage}
						>
							1. 执行校验
						</Button>
						<Button
							loading={publishWorkflowRunning}
							onClick={() => {
								if (currentIndicator?.id) void runWorkflowPrecheck(currentIndicator.id);
							}}
							disabled={!canManage}
						>
							2. 执行预检
						</Button>
						<Button
							type="primary"
							loading={publishWorkflowRunning}
							onClick={() => {
								if (currentIndicator?.id) void runWorkflowPublish(currentIndicator.id);
							}}
							disabled={!canManage}
						>
							3. 直接发布
						</Button>
						<Button
							type="primary"
							ghost
							loading={publishWorkflowRunning}
							onClick={() => void runPublishWorkflowAll()}
							disabled={!canManage}
						>
							一键执行全流程
						</Button>
					</Space>
					{publishWorkflow.validate.status === "error" ? (
						<Alert
							type="error"
							showIcon
							message="校验失败"
							description={`${publishWorkflow.validate.message || "未知错误"}。建议：检查数据集权限、部门上下文和 SQL 语法。`}
						/>
					) : null}
					{publishWorkflow.precheck.status === "error" ? (
						<Alert
							type="warning"
							showIcon
							message="预检未通过"
							description={`${publishWorkflow.precheck.message || "存在阻断项"}。建议：先修复引用缺失和签名差异，再发布。`}
						/>
					) : null}
					{publishWorkflow.publish.status === "error" ? (
						<Alert
							type="error"
							showIcon
							message="发布失败"
							description={`${publishWorkflow.publish.message || "未知错误"}。建议：查看上游校验结果并重试。`}
						/>
					) : null}
					{publishWorkflow.publish.status === "finish" ? (
						<Alert type="success" showIcon message="发布成功" description="指标已发布，可在版本历史中查看最新版本。" />
					) : null}
				</Space>
			</Modal>

			<Modal
				open={previewModal}
				title={`计算预览${currentIndicator?.name ? ` - ${currentIndicator.name}` : ""}`}
				onCancel={() => setPreviewModal(false)}
				footer={null}
				width={980}
			>
				<Space direction="vertical" style={{ width: "100%" }} size={12}>
					<Space wrap>
						<Tag color={String(previewPayload?.status || "").toUpperCase() === "SUCCESS" ? "green" : "red"}>
							{previewPayload?.status || "-"}
						</Tag>
						<Typography.Text type="secondary">
							{previewPayload?.message || "无结果"}
						</Typography.Text>
					</Space>
					<Space wrap>
						<Typography.Text type="secondary">预览行数：</Typography.Text>
						<InputNumber
							min={1}
							max={200}
							value={previewLimit}
							onChange={(value) => setPreviewLimit(Number(value || 20))}
						/>
						<Button
							onClick={() => {
								if (currentIndicator) void runComputePreview(currentIndicator);
							}}
						>
							重新预览
						</Button>
					</Space>
					<Space split={<Divider type="vertical" />} wrap>
						<Typography.Text>总行数：{previewPayload?.rowCount ?? "-"}</Typography.Text>
						<Typography.Text>耗时(ms)：{previewPayload?.durationMs ?? "-"}</Typography.Text>
					</Space>
					{previewPayload?.effectiveSql ? (
						<>
							<Typography.Text strong>生效 SQL</Typography.Text>
							<pre className="max-h-48 overflow-auto rounded bg-slate-900 p-3 text-xs text-slate-100">
								{previewPayload.effectiveSql}
							</pre>
						</>
					) : null}
					{previewRows.length > 0 ? (
						<Table
							size="small"
							rowKey={(row) => row.key}
							columns={previewColumns}
							dataSource={previewRows}
							pagination={{ pageSize: 10 }}
							scroll={{ x: "max-content" }}
						/>
					) : (
						<Empty description="暂无预览数据" />
					)}
				</Space>
			</Modal>

			<Modal
				open={publishPreviewModal}
				title={`发布预检${currentIndicator?.name ? ` - ${currentIndicator.name}` : ""}`}
				onCancel={() => setPublishPreviewModal(false)}
				footer={null}
				width={980}
			>
				<Space direction="vertical" style={{ width: "100%" }} size={12}>
					<Space wrap>
						<Typography.Text strong>结果：</Typography.Text>
						<Tag color={publishPreviewPayload?.readyToPublish ? "green" : "red"}>
							{publishPreviewPayload?.readyToPublish ? "可发布" : "不可发布"}
						</Tag>
					</Space>
					{Array.isArray(publishPreviewPayload?.blockingIssues) && publishPreviewPayload.blockingIssues.length > 0 ? (
						<>
							<Typography.Text strong>阻断项</Typography.Text>
							<Table
								size="small"
								pagination={false}
								rowKey={(_, index) => String(index)}
								columns={[
									{ title: "编码", dataIndex: "code", width: 180 },
									{ title: "说明", dataIndex: "message" },
									{ title: "建议", dataIndex: "suggestion" },
								]}
								dataSource={publishPreviewPayload.blockingIssues}
							/>
						</>
					) : (
						<Typography.Text type="secondary">未发现阻断项。</Typography.Text>
					)}
					{Array.isArray(publishPreviewPayload?.warningIssues) && publishPreviewPayload.warningIssues.length > 0 ? (
						<>
							<Typography.Text strong>告警项</Typography.Text>
							<Table
								size="small"
								pagination={false}
								rowKey={(_, index) => String(index)}
								columns={[
									{ title: "编码", dataIndex: "code", width: 180 },
									{ title: "说明", dataIndex: "message" },
									{ title: "建议", dataIndex: "suggestion" },
								]}
								dataSource={publishPreviewPayload.warningIssues}
							/>
						</>
					) : null}
					<Divider />
					<Typography.Text strong>校验摘要</Typography.Text>
					<Space split={<Divider type="vertical" />} wrap>
						<Typography.Text>状态：{publishPreviewPayload?.validation?.status || "-"}</Typography.Text>
						<Typography.Text>消息：{publishPreviewPayload?.validation?.message || "-"}</Typography.Text>
						<Typography.Text>签名：{publishPreviewPayload?.currentSignature || "-"}</Typography.Text>
						<Typography.Text>失败编码：{publishPreviewPayload?.failureReasonCode || "-"}</Typography.Text>
					</Space>
					<Divider />
					<Typography.Text strong>引用检查</Typography.Text>
					{Array.isArray(publishPreviewPayload?.referenceCheck) &&
					publishPreviewPayload.referenceCheck.length > 0 ? (
						<Table
							size="small"
							pagination={false}
							rowKey={(_, index) => String(index)}
							columns={[
								{ title: "类型", dataIndex: "refType", width: 120 },
								{ title: "目标", dataIndex: "refTarget" },
								{
									title: "状态",
									dataIndex: "status",
									width: 120,
									render: (value) => {
										const text = String(value || "-").toUpperCase();
										const color = text === "OK" ? "green" : text === "MISSING" ? "red" : "default";
										return <Tag color={color}>{text}</Tag>;
									},
								},
								{ title: "说明", dataIndex: "message" },
							]}
							dataSource={publishPreviewPayload.referenceCheck}
						/>
					) : (
						<Empty description="无引用检查记录" />
					)}
					<Divider />
					<Typography.Text strong>与上次发布差异</Typography.Text>
					{Array.isArray(publishPreviewPayload?.changesSinceLastPublish) &&
					publishPreviewPayload.changesSinceLastPublish.length > 0 ? (
						<Table
							size="small"
							pagination={false}
							rowKey={(_, index) => String(index)}
							columns={[
								{ title: "字段", dataIndex: "field", width: 160 },
								{ title: "当前值", dataIndex: "after", render: (value) => prettyJson(value) || "-" },
								{ title: "上次发布值", dataIndex: "before", render: (value) => prettyJson(value) || "-" },
							]}
							dataSource={publishPreviewPayload.changesSinceLastPublish}
						/>
					) : (
						<Typography.Text type="secondary">无差异或暂无历史发布版本。</Typography.Text>
					)}
				</Space>
			</Modal>

			<Modal
				open={versionsModal}
				title={`版本历史${currentIndicator?.name ? ` - ${currentIndicator.name}` : ""}`}
				onCancel={() => setVersionsModal(false)}
				footer={null}
				width={920}
			>
				<Space direction="vertical" style={{ width: "100%" }} size={12}>
					<Space wrap>
						<Typography.Text strong>版本对比</Typography.Text>
						<Select
							style={{ width: 180 }}
							value={leftVersion}
							options={[
								{ label: "CURRENT(当前草稿)", value: "CURRENT" },
								...versions.map((item) => ({
									label: String(item.version || "-"),
									value: String(item.version || ""),
								})),
							]}
							onChange={(value) => setLeftVersion(String(value || "CURRENT"))}
						/>
						<Select
							style={{ width: 180 }}
							value={rightVersion || undefined}
							options={versions.map((item) => ({
								label: String(item.version || "-"),
								value: String(item.version || ""),
							}))}
							onChange={(value) => setRightVersion(String(value || ""))}
						/>
						<Button
							loading={versionDiffLoading}
							onClick={() => {
								if (leftVersion && rightVersion) void refreshVersionDiff(leftVersion, rightVersion);
							}}
							disabled={!leftVersion || !rightVersion}
						>
							比较
						</Button>
					</Space>
					<Table
						size="small"
						rowKey={(row, idx) => `${row.field || "f"}-${idx}`}
						loading={versionDiffLoading}
						dataSource={(versionDiffPayload?.diffs || []) as VersionDiffRow[]}
						columns={[
							{ title: "字段", dataIndex: "label", width: 160, render: (_, row) => row.label || row.field || "-" },
							{ title: "左版本", dataIndex: "before", render: (value) => prettyJson(value) || "-" },
							{ title: "右版本", dataIndex: "after", render: (value) => prettyJson(value) || "-" },
							{
								title: "级别",
								dataIndex: "severity",
								width: 100,
								render: (value) => (
									<Tag color={String(value || "").toUpperCase() === "BLOCKER" ? "red" : "default"}>
										{String(value || "INFO").toUpperCase()}
									</Tag>
								),
							},
						]}
						pagination={{ pageSize: 6 }}
						locale={{ emptyText: "无差异" }}
					/>
					<Divider />
					<Typography.Text strong>版本时间线</Typography.Text>
					<Table
						size="small"
						rowKey={(row) =>
							String(row.id || `${row.version || "unknown"}-${row.createdDate || row.releasedAt || "0"}`)
						}
						dataSource={versions}
						columns={[
							{ title: "版本", dataIndex: "version", width: 100 },
							{ title: "状态", dataIndex: "status", width: 120, render: (value) => <Tag>{value || "-"}</Tag> },
							{ title: "变更说明", dataIndex: "changeSummary", render: (value) => value || "-" },
							{ title: "创建时间", dataIndex: "createdDate", width: 180, render: (value) => formatDateTime(value) },
							{ title: "发布时间", dataIndex: "releasedAt", width: 180, render: (value) => formatDateTime(value) },
							{
								title: "操作",
								width: 220,
								render: (_, record) => (
									<Space>
										<Button size="small" onClick={() => void openVersionDetail(String(record.version || ""))}>
											查看快照
										</Button>
										<Button
											size="small"
											onClick={() => void runRollback(String(record.version || ""))}
											disabled={!canManage}
										>
											回滚到此
										</Button>
									</Space>
								),
							},
						]}
						pagination={{ pageSize: 8 }}
					/>
				</Space>
			</Modal>

			<Modal
				open={versionDetailModal}
				title={`版本快照${versionDetail?.version ? ` - ${versionDetail.version}` : ""}`}
				onCancel={() => setVersionDetailModal(false)}
				footer={null}
				width={860}
			>
				<Space direction="vertical" style={{ width: "100%" }}>
					<Space split={<Divider type="vertical" />} wrap>
						<Typography.Text>版本：{versionDetail?.version || "-"}</Typography.Text>
						<Typography.Text>状态：{versionDetail?.status || "-"}</Typography.Text>
						<Typography.Text>变更说明：{versionDetail?.changeSummary || "-"}</Typography.Text>
					</Space>
					<Typography.Text strong>快照</Typography.Text>
					<pre className="max-h-[520px] overflow-auto rounded bg-slate-900 p-3 text-xs text-slate-100">
						{prettyJson(versionDetail?.snapshotJson)}
					</pre>
				</Space>
			</Modal>

			<Modal
				open={referencesModal}
				title={`引用关系${currentIndicator?.name ? ` - ${currentIndicator.name}` : ""}`}
				onCancel={() => {
					setReferencesModal(false);
					setEditingReferenceId(undefined);
				}}
				footer={null}
				width={960}
			>
				<Space direction="vertical" style={{ width: "100%" }} size={12}>
					<Form form={referenceForm} layout="vertical">
						<Space align="end" wrap style={{ width: "100%" }}>
							<Form.Item
								label="引用类型"
								name="refType"
								rules={[{ required: true, message: "请选择引用类型" }]}
								style={{ minWidth: 180 }}
							>
								<Select options={REFERENCE_TYPE_OPTIONS} placeholder="选择类型" />
							</Form.Item>
							<Form.Item
								label="引用目标"
								name="refTarget"
								rules={[{ required: true, message: "请输入引用目标" }]}
								style={{ minWidth: 260 }}
							>
								<Input placeholder="目标编码或ID" />
							</Form.Item>
							<Form.Item label="展示名称" name="refName" style={{ minWidth: 200 }}>
								<Input placeholder="可选" />
							</Form.Item>
							<Form.Item label="备注" name="notes" style={{ minWidth: 200 }}>
								<Input placeholder="可选" />
							</Form.Item>
							<Button type="primary" onClick={saveReference} disabled={!canManage}>
								{editingReferenceId ? "更新引用" : "新增引用"}
							</Button>
							<Button
								onClick={() => {
									setEditingReferenceId(undefined);
									referenceForm.resetFields();
								}}
							>
								清空
							</Button>
						</Space>
					</Form>
					<Table
						size="small"
						rowKey={(row) => String(row.id || `${row.refType || "ref"}-${row.refTarget || "unknown"}`)}
						dataSource={references}
						columns={[
							{ title: "类型", dataIndex: "refType", width: 120 },
							{ title: "目标", dataIndex: "refTarget", render: (value) => value || "-" },
							{ title: "名称", dataIndex: "refName", render: (value) => value || "-" },
							{ title: "备注", dataIndex: "notes", render: (value) => value || "-" },
							{
								title: "操作",
								width: 150,
								render: (_, record) => (
									<Space>
										<Button size="small" icon={<EditOutlined />} onClick={() => editReference(record)} disabled={!canManage}>
											编辑
										</Button>
										<Button size="small" danger icon={<DeleteOutlined />} onClick={() => void removeReference(record.id)} disabled={!canManage}>
											删除
										</Button>
									</Space>
								),
							},
						]}
						pagination={{ pageSize: 8 }}
					/>
				</Space>
			</Modal>
		</div>
	);
}
