import {
	Alert,
	Badge,
	Button,
	Card,
	Checkbox,
	Divider,
	Dropdown,
	Form,
	Input,
	Layout,
	Modal,
	Select,
	Space,
	Tag,
	Tree,
	Typography,
} from "antd";
import type { DataNode } from "antd/es/tree";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import { toast } from "sonner";
import {
	createDomain,
	deleteDomain,
	getDomainAssetStats,
	getDomainIndicatorStats,
	getDomainTree,
	updateDomain,
} from "@/api/platformApi";
import {
	confirmModelingCandidatesApi,
	createBusinessProcessApi,
	deleteBusinessProcessApi,
	listBusinessProcessesApi,
	listConformedDimensionsApi,
	type Sprint64BusinessProcess,
	type Sprint64ConformedDimension,
} from "@/api/sprint64GovernanceApi";
import { EmptyState } from "@/components/empty-state";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { useRouter } from "@/routes/hooks";
import { normalizeText } from "@/utils/textUtils";
import { buildBusinessModelingRoute } from "../modeling/businessModelingContext";
import { buildModelingJourneyRoute, modelingStagePath } from "../modeling/modelingJourneyContext";
import { ConformedDimensionCatalogCard } from "./ConformedDimensionCatalogCard";
import { DimensionalModelingAssist } from "./DimensionalModelingAssist";
import { pendingCandidateCount } from "./modelingCandidates";
import { type SubjectWorkspaceTab, SubjectWorkspaceTabs } from "./SubjectWorkspaceTabs";
import { DEFAULT_WAREHOUSE_LAYER_SCHEME, resolveLayer } from "./warehouseLayerRegistry";
import {
	buildPlanningRoute,
	createWarehousePlanningContext,
	resolveWarehousePlanningContext,
	resolveWarehousePlanningStatus,
	saveWarehousePlanningContext,
	type WarehousePlanningContext,
} from "./warehousePlanningContext";

const { Sider, Content } = Layout;
const { Title, Text } = Typography;

const ROOT_KEY = "root";

const workspaceTabFrom = (value?: string | null): SubjectWorkspaceTab => {
	if (value === "details" || value === "governance") return value;
	return "scope";
};

type DomainNode = {
	id?: string;
	name?: string;
	code?: string;
	owner?: string;
	description?: string;
	parentId?: string | null;
	children?: DomainNode[];
};

const buildDomainIndex = (nodes: DomainNode[], map: Map<string, DomainNode>) => {
	nodes.forEach((node) => {
		if (node.id) {
			map.set(String(node.id), node);
		}
		if (node.children?.length) {
			buildDomainIndex(node.children, map);
		}
	});
};

const flattenDomains = (nodes: DomainNode[], acc: DomainNode[] = []) => {
	nodes.forEach((node) => {
		acc.push(node);
		if (node.children?.length) {
			flattenDomains(node.children, acc);
		}
	});
	return acc;
};

const isSystemDomain = (node: DomainNode): boolean => {
	const code = String(node.code || "");
	const desc = String(node.description || "");
	if (code.toUpperCase().startsWith("DS:")) {
		return true;
	}
	if (desc.startsWith("Schema from ") || desc.startsWith("Auto-created domain for schema")) {
		return true;
	}
	return false;
};

const filterSystemDomains = (nodes: DomainNode[]): DomainNode[] =>
	nodes
		.filter((node) => !isSystemDomain(node))
		.map((node) => ({
			...node,
			children: node.children ? filterSystemDomains(node.children) : [],
		}));

const filterDomains = (nodes: DomainNode[], keyword: string): DomainNode[] => {
	if (!keyword) return nodes;
	const needle = keyword.toLowerCase();
	return nodes
		.map((node) => {
			const childMatches = node.children ? filterDomains(node.children, keyword) : [];
			const matches =
				String(node.name || "")
					.toLowerCase()
					.includes(needle) ||
				String(node.code || "")
					.toLowerCase()
					.includes(needle) ||
				String(node.owner || "")
					.toLowerCase()
					.includes(needle);
			if (matches || childMatches.length) {
				return { ...node, children: childMatches };
			}
			return null;
		})
		.filter(Boolean) as DomainNode[];
};

const toTreeNodes = (nodes: DomainNode[]): DataNode[] =>
	nodes.map((node) => ({
		key: node.id || Math.random().toString(36),
		title: (
			<Space size={6}>
				<span>{node.name || "未命名主题域"}</span>
				{node.code ? <Tag color="blue">{node.code}</Tag> : null}
			</Space>
		),
		children: node.children ? toTreeNodes(node.children) : undefined,
	}));

export default function SubjectAreasPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const [keyword, setKeyword] = useState(searchParams.get("keyword") || "");
	const [domainTree, setDomainTree] = useState<DomainNode[]>([]);
	const [domainIndex, setDomainIndex] = useState<Map<string, DomainNode>>(new Map());
	const [domainOptions, setDomainOptions] = useState<DomainNode[]>([]);
	const [selectedKey, setSelectedKey] = useState<string>(
		searchParams.get("domainId") || searchParams.get("active") || ROOT_KEY,
	);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editing, setEditing] = useState<DomainNode | null>(null);
	const [form] = Form.useForm();
	const canManage = useGovernanceManageAccess();
	const router = useRouter();
	const [indicatorStats, setIndicatorStats] = useState<{ total: number; published: number; draft: number } | null>(
		null,
	);
	const [indicatorStatsLoading, setIndicatorStatsLoading] = useState(false);
	const [assetStats, setAssetStats] = useState<{
		datasetCount: number;
		indicatorCount: number | null;
		qualityRuleCount: number | null;
	} | null>(null);
	const [statsLoading, setStatsLoading] = useState(false);
	const [businessProcesses, setBusinessProcesses] = useState<Sprint64BusinessProcess[]>([]);
	const [processModalOpen, setProcessModalOpen] = useState(false);
	const [processSaving, setProcessSaving] = useState(false);
	const [processForm] = Form.useForm();
	const [conformedDimensionCatalog, setConformedDimensionCatalog] = useState<Sprint64ConformedDimension[]>([]);
	const [modelingFactsLoading, setModelingFactsLoading] = useState(false);
	const modelingFactsRequest = useRef(0);

	const searchParamsValue = searchParams.toString();
	const syncQuery = useCallback(
		(patch?: { keyword?: string; active?: string; tab?: SubjectWorkspaceTab }) => {
			const params = new URLSearchParams(searchParamsValue);
			const nextKeyword = patch?.keyword ?? keyword;
			const nextActive = patch?.active ?? selectedKey;
			if (nextKeyword?.trim()) {
				params.set("keyword", nextKeyword.trim());
			} else {
				params.delete("keyword");
			}
			if (nextActive && nextActive !== ROOT_KEY) {
				params.set("active", nextActive);
				params.set("domainId", nextActive);
			} else {
				params.delete("active");
				params.delete("domainId");
			}
			if (patch?.tab) params.set("tab", patch.tab);
			if (params.toString() !== searchParamsValue) setSearchParams(params, { replace: true });
		},
		[keyword, searchParamsValue, selectedKey, setSearchParams],
	);

	const loadDomainTree = useCallback(async () => {
		setLoading(true);
		try {
			const resp = (await getDomainTree()) as DomainNode[];
			const list = Array.isArray(resp) ? filterSystemDomains(resp) : [];
			const index = new Map<string, DomainNode>();
			buildDomainIndex(list, index);
			setDomainTree(list);
			setDomainIndex(index);
			setDomainOptions(flattenDomains(list, []));
			if (searchParams.get("focus") === "business-processes" && selectedKey === ROOT_KEY) {
				const firstDomainId = list.find((node) => node.id)?.id;
				if (firstDomainId) setSelectedKey(String(firstDomainId));
			}
			if (selectedKey !== ROOT_KEY && !index.has(selectedKey)) {
				setSelectedKey(ROOT_KEY);
			}
		} catch (err: any) {
			toast.error(err?.message || "加载主题域失败");
		} finally {
			setLoading(false);
		}
	}, [searchParams, selectedKey]);

	const loadAssetStats = useCallback(async (domainId: string) => {
		setStatsLoading(true);
		try {
			const stats = await getDomainAssetStats(domainId);
			setAssetStats(stats);
		} catch {
			setAssetStats(null);
		} finally {
			setStatsLoading(false);
		}
	}, []);

	const activeDomain = selectedKey !== ROOT_KEY ? domainIndex.get(selectedKey) || null : null;
	const planningResolution = useMemo(() => resolveWarehousePlanningContext(searchParams), [searchParams]);
	const [planningContextOverride, setPlanningContextOverride] = useState<WarehousePlanningContext | null>(null);
	const activePlanningContext =
		planningContextOverride?.domainId === activeDomain?.id
			? planningContextOverride
			: planningResolution.context?.domainId === activeDomain?.id
				? planningResolution.context
				: null;
	const planningStatus = useMemo(() => {
		if (!activeDomain?.id) {
			return { status: "blocked" as const, reason: "请先选择主题域" };
		}
		if (planningResolution.status === "blocked" && searchParams.get("planningId")) {
			return {
				status: "blocked" as const,
				reason: planningResolution.reason || "规划上下文不可用，请重新确认规划",
			};
		}
		if (!activePlanningContext) {
			if (searchParams.get("planningId")) {
				return {
					status: "blocked" as const,
					reason: planningResolution.reason || "规划上下文不可用，请重新确认规划",
				};
			}
			return { status: "draft" as const, reason: "尚未创建数仓规划，默认以 DWD 维度建模为首个业务输出" };
		}
		return resolveWarehousePlanningStatus(activePlanningContext, {
			source: planningResolution.source,
			standardFieldCount: activePlanningContext.standardDraftId ? 1 : 0,
		});
	}, [
		activeDomain?.id,
		activePlanningContext,
		planningResolution.reason,
		planningResolution.source,
		planningResolution.status,
		searchParams,
	]);
	const stgLayer = resolveLayer("STG");
	const enabledPlanningLayers = activePlanningContext?.enabledLayers ?? [
		...DEFAULT_WAREHOUSE_LAYER_SCHEME.enabledLayers,
	];
	const outputPlanningLayers = activePlanningContext?.outputLayers ?? [...DEFAULT_WAREHOUSE_LAYER_SCHEME.outputLayers];
	const updateStgPlanning = (enabled: boolean) => {
		if (!activeDomain?.id || !canManage) return;
		const base =
			activePlanningContext ||
			createWarehousePlanningContext({
				planningId: `warehouse-plan-${activeDomain.id}-${Date.now()}`,
				domainId: activeDomain.id,
				domainName: activeDomain.name,
				processId: searchParams.get("processId") || planningResolution.context?.processId,
				warehouseLayer: "DWD",
				modelingMode: "dimension",
			});
		const next: WarehousePlanningContext = {
			...base,
			enabledLayers: enabled
				? ([...new Set([...base.enabledLayers, "STG" as const])] as WarehousePlanningContext["enabledLayers"])
				: base.enabledLayers.filter((layer) => layer !== "STG"),
			updatedAt: new Date().toISOString(),
		};
		if (!saveWarehousePlanningContext(next)) {
			toast.error("分层方案保存失败，请检查浏览器会话存储后重试");
			return;
		}
		setPlanningContextOverride(next);
		toast.success(enabled ? "已启用 STG 技术过渡层" : "已停用 STG 技术过渡层");
	};

	useEffect(() => {
		void loadDomainTree();
	}, [loadDomainTree]);

	useEffect(() => {
		if (activeDomain?.id) {
			void loadAssetStats(activeDomain.id);
		} else {
			setAssetStats(null);
		}
	}, [activeDomain?.id, loadAssetStats]);

	useEffect(() => {
		if (!activeDomain?.id) {
			setIndicatorStats(null);
			return;
		}
		setIndicatorStatsLoading(true);
		getDomainIndicatorStats(activeDomain.id)
			.then((res: any) => setIndicatorStats(res ?? null))
			.catch(() => setIndicatorStats(null))
			.finally(() => setIndicatorStatsLoading(false));
	}, [activeDomain?.id]);

	const loadDomainModelingFacts = useCallback(async (domainId: string) => {
		const requestId = ++modelingFactsRequest.current;
		setModelingFactsLoading(true);
		try {
			const [apiProcesses, apiDimensions] = await Promise.all([
				listBusinessProcessesApi(domainId),
				listConformedDimensionsApi(domainId),
			]);
			if (requestId !== modelingFactsRequest.current) return;
			const processes = Array.isArray(apiProcesses) ? apiProcesses : [];
			const dimensions = Array.isArray(apiDimensions) ? apiDimensions : [];
			setBusinessProcesses(processes);
			setConformedDimensionCatalog(dimensions);
		} catch (error: any) {
			if (requestId !== modelingFactsRequest.current) return;
			setBusinessProcesses([]);
			setConformedDimensionCatalog([]);
			toast.error(error?.message || "领域建模事实加载失败");
		} finally {
			if (requestId === modelingFactsRequest.current) setModelingFactsLoading(false);
		}
	}, []);

	useEffect(() => {
		if (!activeDomain?.id) {
			modelingFactsRequest.current += 1;
			setBusinessProcesses([]);
			setConformedDimensionCatalog([]);
			setModelingFactsLoading(false);
			return;
		}
		void loadDomainModelingFacts(activeDomain.id);
	}, [activeDomain?.id, loadDomainModelingFacts]);

	useEffect(() => {
		syncQuery();
	}, [syncQuery]);

	const filteredTree = useMemo(() => filterDomains(domainTree, keyword), [domainTree, keyword]);
	const treeData = useMemo(() => {
		const children = toTreeNodes(filteredTree);
		return [
			{
				key: ROOT_KEY,
				title: "全域主题 (Root)",
				children,
			},
		];
	}, [filteredTree]);
	const activeChildren = activeDomain?.children || [];
	const candidateCount = pendingCandidateCount(businessProcesses, conformedDimensionCatalog);
	const workspaceTab = workspaceTabFrom(searchParams.get("tab"));
	const focusBusinessProcesses = searchParams.get("focus") === "business-processes";
	const setWorkspaceTabAndQuery = (tab: SubjectWorkspaceTab) => syncQuery({ tab });
	const openProcessModal = (preset?: Partial<Sprint64BusinessProcess>) => {
		processForm.resetFields();
		processForm.setFieldsValue({
			processId: preset?.processId || "",
			name: preset?.name || "",
			description: preset?.description || "",
		});
		setProcessModalOpen(true);
	};
	const submitProcess = async () => {
		if (!canManage || !activeDomain?.id) return;
		setProcessSaving(true);
		try {
			const values = await processForm.validateFields();
			const processId = normalizeText(values.processId)
				.toLowerCase()
				.replace(/[^a-z0-9_-]+/g, "-");
			if (businessProcesses.some((item) => item.processId === processId)) throw new Error("业务过程编码已存在");
			await createBusinessProcessApi(activeDomain.id, {
				processId,
				name: normalizeText(values.name),
				description: normalizeText(values.description) || undefined,
			});
			await loadDomainModelingFacts(activeDomain.id);
			processForm.resetFields();
			setProcessModalOpen(false);
			toast.success("业务过程已创建");
		} catch (err: any) {
			if (!err?.errorFields) toast.error(err?.message || "业务过程保存失败");
		} finally {
			setProcessSaving(false);
		}
	};
	const startProcessPlanning = (process: Sprint64BusinessProcess) => {
		if (!activeDomain?.id) return;
		const context = createWarehousePlanningContext({
			planningId: `warehouse-plan-${activeDomain.id}-${process.processId}-${Date.now()}`,
			domainId: activeDomain.id,
			domainName: activeDomain.name,
			processId: process.processId,
			warehouseLayer: "DWD",
			modelingMode: "dimension",
			sourceId: searchParams.get("sourceId") || planningResolution.context?.sourceId,
		});
		if (!saveWarehousePlanningContext(context)) {
			toast.error("规划草稿保存失败，请检查浏览器会话存储后重试");
			return;
		}
		router.push(
			buildBusinessModelingRoute("/modeling/semantic/objects?from=business-process", {
				...context,
				processName: process.name,
			}),
		);
	};
	const deleteProcess = (process: Sprint64BusinessProcess) => {
		if (!activeDomain?.id || !canManage) return;
		const domainId = activeDomain.id;
		Modal.confirm({
			title: "删除业务过程？",
			content: `删除“${process.name}”后将无法继续用于后续建模。`,
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteBusinessProcessApi(domainId, process.processId);
					await loadDomainModelingFacts(domainId);
					toast.success("业务过程已删除");
				} catch (error: any) {
					toast.error(error?.message || "业务过程删除失败");
					throw error;
				}
			},
		});
	};
	const confirmProcess = async (process: Sprint64BusinessProcess) => {
		if (!activeDomain?.id || !canManage) return;
		try {
			await confirmModelingCandidatesApi(activeDomain.id, {
				processIds: [process.processId],
				dimensionIds: [],
			});
			await loadDomainModelingFacts(activeDomain.id);
			toast.success("业务过程候选已确认");
		} catch (error: any) {
			toast.error(error?.message || "业务过程确认失败");
		}
	};
	const continueLogicalModel = () => {
		if (!activeDomain?.id) return;
		router.push(
			buildModelingJourneyRoute(modelingStagePath("LOGICAL"), {
				domainId: activeDomain.id,
				domainName: activeDomain.name,
				stage: "LOGICAL",
			}),
		);
	};
	const continueDimensionPlanning = () => {
		if (!activeDomain?.id) return;
		const context =
			activePlanningContext ||
			createWarehousePlanningContext({
				planningId: `warehouse-plan-${activeDomain.id}-${Date.now()}`,
				domainId: activeDomain.id,
				domainName: activeDomain.name,
				processId: searchParams.get("processId") || planningResolution.context?.processId,
				warehouseLayer: "DWD",
				modelingMode: "dimension",
				sourceId: searchParams.get("sourceId") || planningResolution.context?.sourceId,
			});
		if (!saveWarehousePlanningContext(context)) {
			toast.error("规划草稿保存失败，请检查浏览器会话存储后重试");
			return;
		}
		router.push(buildPlanningRoute("/governance/standards/elements?bindingDraft=1", context));
	};
	const openModal = (domain?: DomainNode | null, parentId?: string | null) => {
		setEditing(domain || null);
		form.resetFields();
		form.setFieldsValue({
			name: domain?.name,
			code: domain?.code,
			owner: domain?.owner,
			description: domain?.description,
			parentId: parentId ?? domain?.parentId ?? undefined,
		});
		setModalOpen(true);
	};

	const submit = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		setSaving(true);
		try {
			const values = await form.validateFields(["name"]);
			const parentId = normalizeText(form.getFieldValue("parentId")) || undefined;
			const payload: DomainNode = {
				name: normalizeText(values.name),
				code: normalizeText(form.getFieldValue("code")) || undefined,
				owner: normalizeText(form.getFieldValue("owner")) || undefined,
				description: normalizeText(form.getFieldValue("description")) || undefined,
				parent: parentId ? { id: parentId } : undefined,
			} as any;
			if (editing?.id) {
				if (editing.id === parentId) {
					throw new Error("上级主题域不能选择自身");
				}
				await updateDomain(editing.id, payload);
				toast.success("主题域已更新");
			} else {
				await createDomain(payload);
				toast.success("主题域已创建");
			}
			setModalOpen(false);
			setEditing(null);
			await loadDomainTree();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const confirmDelete = (domain?: DomainNode | null) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!domain?.id) return;
		Modal.confirm({
			title: "删除主题域？",
			content: "删除后无法恢复，请确认该域下没有关联资产。",
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteDomain(domain.id as string);
					toast.success("主题域已删除");
					setSelectedKey(ROOT_KEY);
					await loadDomainTree();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
	};

	const parentOptions = useMemo(
		() => domainOptions.filter((item) => item.id && item.id !== editing?.id),
		[domainOptions, editing?.id],
	);

	return (
		<div className="space-y-4">
			<Card
				title="主题域管理"
				extra={
					<Dropdown
						trigger={["click"]}
						menu={{
							items: [
								{ key: "root", label: "新增根域" },
								...(activeDomain?.id
									? [{ key: "child", label: `在 ${activeDomain.name || "当前域"} 下新增子域` }]
									: []),
							],
							onClick: ({ key }) => openModal(null, key === "child" ? activeDomain?.id : null),
						}}
					>
						<Button type="primary" disabled={!canManage}>
							新增主题域
						</Button>
					</Dropdown>
				}
			>
				<div className="mb-3 flex flex-wrap items-center gap-2">
					<Input.Search
						placeholder="搜索主题域..."
						style={{ width: 400 }}
						value={keyword}
						onChange={(e) => setKeyword(e.target.value)}
						allowClear
					/>
				</div>
				<Layout className="min-w-0 overflow-hidden rounded-[24px] border border-border/70 bg-background">
					<Sider
						width={320}
						breakpoint="lg"
						collapsedWidth={0}
						theme="light"
						className="overflow-hidden border-r border-slate-200 p-4"
					>
						<Space direction="vertical" className="w-full" size="middle">
							<Text strong>域目录结构</Text>
							<Tree
								showLine
								defaultExpandedKeys={[ROOT_KEY]}
								selectedKeys={[selectedKey]}
								treeData={treeData}
								onSelect={(keys) => {
									const key = String(keys?.[0] ?? ROOT_KEY);
									setSelectedKey(key || ROOT_KEY);
								}}
							/>
							{!loading && domainTree.length === 0 ? (
								<EmptyState title="暂无主题域" description="请先创建主题域。" />
							) : null}
						</Space>
					</Sider>
					<Content className="min-w-0 p-3 sm:p-6">
						{loading ? (
							<div className="rounded-[24px] border border-dashed border-slate-200 bg-slate-50 px-6 py-10 text-center text-sm text-slate-500">
								主题域结构加载中...
							</div>
						) : !activeDomain ? (
							<div className="rounded-[24px] border border-slate-200 bg-slate-50 px-6 py-6">
								<Title level={4}>全域主题视角</Title>
								<Text type="secondary">请选择左侧主题域查看详情与治理指标。</Text>
								<Divider />
								{domainTree.length === 0 ? (
									<EmptyState title="暂无主题域结构" description="请先创建主题域后再进入详情视图。" />
								) : (
									<Space wrap>
										{domainOptions.slice(0, 12).map((item) => (
											<Tag key={item.id}>{item.name}</Tag>
										))}
										{domainOptions.length > 12 ? <Tag>+{domainOptions.length - 12} 更多</Tag> : null}
									</Space>
								)}
							</div>
						) : (
							<div className="space-y-6">
								<div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
									<div className="min-w-0">
										<Space size={8} wrap>
											<Title level={4} style={{ margin: 0 }}>
												{activeDomain.name || "未命名主题域"}
											</Title>
											{activeDomain.code ? <Tag color="blue">{activeDomain.code}</Tag> : null}
										</Space>
										<div className="mt-2 text-sm text-slate-500">
											负责人：{activeDomain.owner || "未指定"} ｜ 子域数：{activeChildren.length} ｜ 资产数：
											{statsLoading ? "..." : (assetStats?.datasetCount ?? "-")}
										</div>
										{activeDomain.description ? (
											<Text type="secondary" className="block mt-2">
												{activeDomain.description}
											</Text>
										) : null}
									</div>
									<Dropdown
										trigger={["click"]}
										menu={{
											items: [
												{ key: "edit", label: "编辑域属性" },
												{ key: "delete", label: "删除域", danger: true },
											],
											onClick: ({ key }) => {
												if (key === "edit") openModal(activeDomain, activeDomain.parentId);
												if (key === "delete") confirmDelete(activeDomain);
											},
										}}
									>
										<Button disabled={!canManage}>更多</Button>
									</Dropdown>
								</div>

								<Divider />
								<SubjectWorkspaceTabs
									activeKey={workspaceTab}
									onChange={setWorkspaceTabAndQuery}
									scope={
										<div className="space-y-4">
											<div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-4">
												<div>
													<div className="font-medium text-slate-900">当前范围：{activeDomain.name}</div>
													<div className="mt-1 text-sm text-slate-500">
														从业务分类、数据表和已有模型开始设计明细表与维度表。
													</div>
												</div>
												<Button type="primary" onClick={continueLogicalModel}>
													继续逻辑模型
												</Button>
											</div>
											<div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4">
												<Text type="secondary">需要一致性维度时再使用维度建模辅助。</Text>
												<DimensionalModelingAssist candidateCount={candidateCount} openOnMount={focusBusinessProcesses}>
													<div className="space-y-4">
														<Card
															className="border-slate-200"
															title="业务过程"
															extra={
																<Space>
																	{candidateCount ? <Tag color="gold">待确认候选 {candidateCount}</Tag> : null}
																	<Button
																		size="small"
																		type="primary"
																		onClick={() => openProcessModal()}
																		disabled={!canManage}
																	>
																		新增业务过程
																	</Button>
																</Space>
															}
															loading={modelingFactsLoading}
														>
															{businessProcesses.length ? (
																<Space direction="vertical" className="w-full" size="middle">
																	{businessProcesses.map((process) => (
																		<div
																			key={process.processId}
																			className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3"
																		>
																			<div className="flex flex-wrap items-start justify-between gap-3">
																				<div>
																					<Space wrap size={6}>
																						<div className="font-semibold text-slate-900">{process.name}</div>
																						<Tag color={process.confirmed ? "green" : "gold"}>
																							{process.confirmed ? "已确认" : "确认后使用"}
																						</Tag>
																						<Tag>{process.sourceType === "MANUAL" ? "人工登记" : "候选来源"}</Tag>
																					</Space>
																					<div className="mt-1 text-xs text-slate-500">{process.processId}</div>
																					{process.sourceId ? (
																						<div className="mt-1 text-xs text-slate-500">
																							来源：{process.sourceId}
																							{process.sourceVersion ? ` v${process.sourceVersion}` : ""}
																						</div>
																					) : null}
																					{process.description ? (
																						<div className="mt-1 text-sm text-slate-600">{process.description}</div>
																					) : null}
																				</div>
																				<Space size={4}>
																					{process.confirmed ? (
																						<Button
																							size="small"
																							type="link"
																							onClick={() => startProcessPlanning(process)}
																						>
																							进入业务建模
																						</Button>
																					) : (
																						<Button
																							size="small"
																							type="link"
																							onClick={() => void confirmProcess(process)}
																							disabled={!canManage}
																						>
																							确认后使用
																						</Button>
																					)}
																					<Button
																						size="small"
																						danger
																						type="link"
																						onClick={() => deleteProcess(process)}
																						disabled={!canManage}
																					>
																						删除
																					</Button>
																				</Space>
																			</div>
																		</div>
																	))}
																</Space>
															) : (
																<EmptyState
																	title="暂无业务过程"
																	description="业务过程是事实建模的锚点，请先按当前主题域的实际业务边界创建。"
																	actions={
																		<Button onClick={() => openProcessModal()} disabled={!canManage}>
																			新增业务过程
																		</Button>
																	}
																/>
															)}
														</Card>
														<ConformedDimensionCatalogCard
															domainId={activeDomain.id as string}
															canManage={canManage}
															processes={businessProcesses}
															dimensions={conformedDimensionCatalog}
															onChanged={() => loadDomainModelingFacts(activeDomain.id as string)}
														/>
													</div>
												</DimensionalModelingAssist>
											</div>
										</div>
									}
									details={
										<div className="space-y-4">
											<Card
												className="border-blue-100 bg-blue-50/30"
												data-testid="warehouse-layer-plan"
												title="输出分层方案"
												extra={
													<Tag color="blue">
														标准方案 v
														{activePlanningContext?.layerSchemeVersion ?? DEFAULT_WAREHOUSE_LAYER_SCHEME.version}
													</Tag>
												}
											>
												<Space direction="vertical" size={8} className="w-full">
													<div className="flex flex-wrap items-center gap-2">
														{enabledPlanningLayers.map((layer, index) => (
															<span key={layer} className="flex items-center gap-2">
																<Tag
																	color={
																		layer === "STG"
																			? "gold"
																			: layer === "DWD"
																				? "purple"
																				: layer === "DWS"
																					? "blue"
																					: layer === "ADS"
																						? "green"
																						: "default"
																	}
																>
																	{resolveLayer(layer)?.title || layer}
																</Tag>
																{index < enabledPlanningLayers.length - 1 ? <Text type="secondary">→</Text> : null}
															</span>
														))}
													</div>
													<Text type="secondary">
														业务输出层：
														{outputPlanningLayers.map((layer) => resolveLayer(layer)?.key || layer).join("、")}；STG
														是可选技术过渡层，不产出业务指标。
													</Text>
													{stgLayer ? (
														<div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2">
															<div className="flex flex-wrap items-center justify-between gap-2">
																<div>
																	<Text strong>STG · {stgLayer.title}</Text>
																	<div className="mt-1 text-xs text-slate-600">{stgLayer.responsibility}</div>
																	<div className="mt-1 text-xs text-slate-500">{stgLayer.dbtRole}</div>
																</div>
																<Checkbox
																	checked={enabledPlanningLayers.includes("STG")}
																	onChange={(event) => updateStgPlanning(event.target.checked)}
																	disabled={!canManage}
																>
																	启用 STG（dbt 推荐）
																</Checkbox>
															</div>
														</div>
													) : null}
												</Space>
											</Card>
											<Alert
												showIcon
												data-testid="warehouse-planning-card"
												type={
													planningStatus.status === "blocked"
														? "error"
														: planningStatus.status === "ready"
															? "success"
															: "info"
												}
												message="数仓规划 · 分层方案"
												description={
													<Space direction="vertical" size={4}>
														<Text>
															主题域：{activeDomain.name || activeDomain.id} · 输出层：
															{outputPlanningLayers.join(" → ")} · 建模模式：维度建模
														</Text>
														<Text type="secondary">
															{planningStatus.reason || "规划、标准草稿与维度模型候选已具备连续上下文"}
														</Text>
													</Space>
												}
												action={
													<Button type="primary" onClick={continueDimensionPlanning}>
														{planningStatus.status === "blocked"
															? "重新确认规划"
															: activePlanningContext
																? "继续标准落标"
																: "创建规划并落标"}
													</Button>
												}
											/>
										</div>
									}
									governance={
										<div className="space-y-4">
											<div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
												<div className="rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-4">
													<div className="mb-3 text-sm font-semibold text-slate-900">域级治理指标</div>
													<div className="flex items-center justify-between py-1">
														<Text>数据集数</Text>
														<Text strong>{statsLoading ? "..." : (assetStats?.datasetCount ?? "-")}</Text>
													</div>
													<div className="flex items-center justify-between py-1">
														<Text>指标数</Text>
														<Text strong>{assetStats?.indicatorCount != null ? assetStats.indicatorCount : "-"}</Text>
													</div>
													<div className="flex items-center justify-between py-1">
														<Text>质量规则数</Text>
														<Text strong>
															{assetStats?.qualityRuleCount != null ? assetStats.qualityRuleCount : "-"}
														</Text>
													</div>
												</div>
												<div className="rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-4">
													<div className="mb-3 text-sm font-semibold text-slate-900">域级指标统计</div>
													{indicatorStatsLoading ? (
														<div className="text-xs text-slate-400">加载中...</div>
													) : indicatorStats && indicatorStats.total > 0 ? (
														<div className="space-y-2">
															<div className="grid grid-cols-3 gap-3 text-center">
																<div className="rounded border border-slate-100 bg-slate-50 p-2">
																	<div className="text-lg font-bold text-slate-800">{indicatorStats.total}</div>
																	<div className="text-xs text-slate-500">总数</div>
																</div>
																<div className="rounded border border-green-100 bg-green-50 p-2">
																	<div className="text-lg font-bold text-green-600">{indicatorStats.published}</div>
																	<div className="text-xs text-slate-500">已发布</div>
																</div>
																<div className="rounded border border-orange-100 bg-orange-50 p-2">
																	<div className="text-lg font-bold text-orange-500">{indicatorStats.draft}</div>
																	<div className="text-xs text-slate-500">草稿</div>
																</div>
															</div>
															<Button
																type="link"
																size="small"
																onClick={() =>
																	router.push(`/governance/indicator-center?domain=${activeDomain?.code ?? ""}`)
																}
															>
																查看该域全部指标 →
															</Button>
														</div>
													) : (
														<div className="text-xs text-slate-400">暂无指标</div>
													)}
												</div>
											</div>
											<div className="rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-4">
												<div className="mb-3 text-sm font-semibold text-slate-900">子域列表</div>
												{activeChildren.length ? (
													<Space wrap>
														{activeChildren.map((child) => (
															<Tag key={child.id || child.name}>{child.name}</Tag>
														))}
													</Space>
												) : (
													<Space align="center">
														<Badge status="default" />
														<Text type="secondary">暂无子域</Text>
													</Space>
												)}
											</div>
										</div>
									}
								/>
							</div>
						)}
					</Content>
				</Layout>
			</Card>

			<Modal
				open={modalOpen}
				title={editing ? "编辑主题域" : "新增主题域"}
				onCancel={() => setModalOpen(false)}
				onOk={submit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				okButtonProps={{ disabled: !canManage }}
			>
				<Form layout="vertical" form={form}>
					<Form.Item name="name" label="主题域名称" rules={[{ required: true, message: "请输入主题域名称" }]}>
						<Input placeholder="例如：财务域" />
					</Form.Item>
					<Form.Item name="code" label="主题域编码">
						<Input placeholder="FINANCE" />
					</Form.Item>
					<Form.Item name="owner" label="负责人">
						<Input placeholder="负责人姓名" />
					</Form.Item>
					<Form.Item name="parentId" label="上级主题域">
						<Select
							allowClear
							placeholder="无上级主题域"
							options={parentOptions.map((item) => ({
								label: item.name || item.code || "未命名主题域",
								value: item.id,
							}))}
						/>
					</Form.Item>
					<Form.Item name="description" label="说明">
						<Input.TextArea rows={3} placeholder="主题域描述" />
					</Form.Item>
				</Form>
			</Modal>
			<Modal
				open={processModalOpen}
				title="新增业务过程"
				onCancel={() => setProcessModalOpen(false)}
				onOk={submitProcess}
				okText="保存过程"
				cancelText="取消"
				confirmLoading={processSaving}
			>
				<Form layout="vertical" form={processForm}>
					<Form.Item name="name" label="业务过程名称" rules={[{ required: true, message: "请输入业务过程名称" }]}>
						<Input placeholder="例如：业务申请处理" />
					</Form.Item>
					<Form.Item
						name="processId"
						label="业务过程编码"
						rules={[
							{ required: true, message: "请输入业务过程编码" },
							{ min: 2, max: 64, message: "编码长度为 2-64 位" },
							{
								pattern: /^[a-z0-9][a-z0-9_-]*$/,
								message: "仅支持小写字母、数字、下划线和连字符，且必须以字母或数字开头",
							},
						]}
					>
						<Input placeholder="application-review" />
					</Form.Item>
					<Form.Item name="description" label="过程说明">
						<Input.TextArea rows={3} placeholder="说明过程边界、开始和结束条件" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
