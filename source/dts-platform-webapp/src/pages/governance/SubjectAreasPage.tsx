import {
	Alert,
	Badge,
	Button,
	Card,
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
import { buildCatalogDomainAssetKey } from "@/api/catalogTagsApi";
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
import { GovernedAssetTagPanel } from "@/components/catalog/tags/GovernedAssetTagPanel";
import { EmptyState } from "@/components/empty-state";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { useRouter } from "@/routes/hooks";
import { normalizeText } from "@/utils/textUtils";
import { buildBusinessModelingRoute } from "@/features/modeling/navigation/businessModelingContext";
import { buildModelingJourneyRoute, modelingStagePath } from "@/features/modeling/navigation/modelingJourneyContext";
import { buildWarehousePlanRoute, resolveWarehousePlanPageContext } from "@/features/modeling/navigation/warehousePlanViewModel";
import { ConformedDimensionCatalogCard } from "./ConformedDimensionCatalogCard";
import { DataMartWorkspace } from "./DataMartWorkspace";
import { DimensionalModelingAssist } from "./DimensionalModelingAssist";
import { pendingCandidateCount } from "./modelingCandidates";
import { type SubjectWorkspaceTab, SubjectWorkspaceTabs } from "./SubjectWorkspaceTabs";

const { Sider, Content } = Layout;
const { Title, Text } = Typography;

const ROOT_KEY = "root";

const workspaceTabFrom = (value?: string | null): SubjectWorkspaceTab => {
	if (value === "data-marts" || value === "details" || value === "governance") return value;
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
				<span>{node.name || "未命名业务分类"}</span>
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
	const planPageContext = useMemo(() => resolveWarehousePlanPageContext(searchParams), [searchParams]);
	const returnPlanId = planPageContext?.planId || "";
	const safeReturnTo = planPageContext?.returnTo || null;
	const planBaselineRoute = useMemo(
		() => (returnPlanId ? buildWarehousePlanRoute(returnPlanId, "baseline", { tab: "categories" }) : null),
		[returnPlanId],
	);
	const planningReturnRoute = safeReturnTo || planBaselineRoute;
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
			toast.error(err?.message || "加载业务分类失败");
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
				title: "全部业务分类",
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
	const startProcessPlanning = (_process: Sprint64BusinessProcess) => {
		if (!activeDomain?.id) return;
		if (!returnPlanId) {
			toast.info("请先创建或选择建设规划，再进入业务建模");
			router.push("/data-modeling/planning/spaces");
			return;
		}
		router.push(
			buildBusinessModelingRoute("/data-modeling/dimensions/workbench", {
				planId: returnPlanId,
				domainId: activeDomain.id,
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
		if (!returnPlanId) {
			toast.info("请先创建或选择建设规划，再继续逻辑模型");
			router.push("/data-modeling/planning/spaces");
			return;
		}
		router.push(
			buildModelingJourneyRoute(modelingStagePath("LOGICAL"), {
				planId: returnPlanId,
				domainId: activeDomain.id,
				domainName: activeDomain.name,
				stage: "LOGICAL",
			}),
		);
	};
	const returnToPlanning = () => router.push(planningReturnRoute || "/data-modeling/planning/spaces");
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
					throw new Error("上级业务分类不能选择自身");
				}
				await updateDomain(editing.id, payload);
				toast.success("业务分类已更新");
			} else {
				await createDomain(payload);
				toast.success("业务分类已创建");
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
			title: "删除业务分类？",
			content: "删除后无法恢复，请确认该分类下没有关联资产。",
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteDomain(domain.id as string);
					toast.success("业务分类已删除");
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
				title="业务分类"
				extra={
					<Space wrap>
						{planningReturnRoute ? <Button onClick={returnToPlanning}>返回建设计划</Button> : null}
						<Dropdown
							trigger={["click"]}
							menu={{
								items: [
									{ key: "root", label: "新增一级分类" },
									...(activeDomain?.id
										? [{ key: "child", label: `在 ${activeDomain.name || "当前分类"} 下新增子分类` }]
										: []),
								],
								onClick: ({ key }) => openModal(null, key === "child" ? activeDomain?.id : null),
							}}
						>
							<Button type="primary" disabled={!canManage}>
								新增业务分类
							</Button>
						</Dropdown>
					</Space>
				}
			>
				<div className="mb-3 flex flex-wrap items-center gap-2">
					<Input.Search
						placeholder="搜索业务分类..."
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
							<Text strong>业务分类目录</Text>
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
								<EmptyState title="暂无业务分类" description="请先创建业务分类。" />
							) : null}
						</Space>
					</Sider>
					<Content className="min-w-0 p-3 sm:p-6">
						{loading ? (
							<div className="rounded-[24px] border border-dashed border-slate-200 bg-slate-50 px-6 py-10 text-center text-sm text-slate-500">
								业务分类加载中...
							</div>
						) : !activeDomain ? (
							<div className="rounded-[24px] border border-slate-200 bg-slate-50 px-6 py-6">
								<Title level={4}>全部业务分类</Title>
								<Text type="secondary">请选择左侧业务分类查看详情与治理指标。</Text>
								<Divider />
								{domainTree.length === 0 ? (
									<EmptyState title="暂无业务分类结构" description="请先创建业务分类后再进入详情视图。" />
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
												{activeDomain.name || "未命名业务分类"}
											</Title>
											{activeDomain.code ? <Tag color="blue">{activeDomain.code}</Tag> : null}
										</Space>
										<div className="mt-2 text-sm text-slate-500">
											负责人：{activeDomain.owner || "未指定"} ｜ 子分类数：{activeChildren.length} ｜ 资产数：
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
												{ key: "edit", label: "编辑分类信息" },
												{ key: "delete", label: "删除分类", danger: true },
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
									dataMarts={
										<DataMartWorkspace
											domainId={activeDomain.id as string}
											domainOptions={domainOptions
												.filter((domain): domain is DomainNode & { id: string } => Boolean(domain.id))
												.map((domain) => ({
													value: domain.id,
													label: `${domain.name || "未命名分类"}${domain.code ? `（${domain.code}）` : ""}`,
												}))}
											canManage={canManage}
										/>
									}
									details={
										<Card data-testid="canonical-plan-handoff" title="建设规划归属">
										</Card>
									}
									governance={
										<div className="space-y-4">
											{activeDomain.code?.trim() ? (
												<GovernedAssetTagPanel
													assetType="CATALOG_DOMAIN"
													assetKey={buildCatalogDomainAssetKey(activeDomain.code)}
												/>
											) : (
												<Alert
													showIcon
													type="warning"
													message="当前业务分类暂不能维护数据标签"
													description="请先补充业务分类编码，以建立稳定的资产标识。"
												/>
											)}
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
																查看该分类全部指标 →
															</Button>
														</div>
													) : (
														<div className="text-xs text-slate-400">暂无指标</div>
													)}
												</div>
											</div>
											<div className="rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-4">
												<div className="mb-3 text-sm font-semibold text-slate-900">子分类列表</div>
												{activeChildren.length ? (
													<Space wrap>
														{activeChildren.map((child) => (
															<Tag key={child.id || child.name}>{child.name}</Tag>
														))}
													</Space>
												) : (
													<Space align="center">
														<Badge status="default" />
														<Text type="secondary">暂无子分类</Text>
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
				title={editing ? "编辑业务分类" : "新增业务分类"}
				onCancel={() => setModalOpen(false)}
				onOk={submit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				okButtonProps={{ disabled: !canManage }}
			>
				<Form layout="vertical" form={form}>
					<Form.Item name="name" label="业务分类名称" rules={[{ required: true, message: "请输入业务分类名称" }]}>
						<Input placeholder="例如：财务管理" />
					</Form.Item>
					<Form.Item name="code" label="业务分类编码">
						<Input placeholder="FINANCE" />
					</Form.Item>
					<Form.Item name="owner" label="负责人">
						<Input placeholder="负责人姓名" />
					</Form.Item>
					<Form.Item name="parentId" label="上级业务分类">
						<Select
							allowClear
							placeholder="无上级业务分类"
							options={parentOptions.map((item) => ({
								label: item.name || item.code || "未命名业务分类",
								value: item.id,
							}))}
						/>
					</Form.Item>
					<Form.Item name="description" label="说明">
						<Input.TextArea rows={3} placeholder="业务分类说明" />
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
