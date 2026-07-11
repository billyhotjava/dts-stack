import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Badge,
	Button,
	Card,
	Checkbox,
	Divider,
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
import { } from "@ant-design/icons";
import type { DataNode } from "antd/es/tree";
import { useSearchParams } from "react-router";
import { EmptyState } from "@/components/empty-state";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { createDomain, deleteDomain, getDomainAssetStats, getDomainIndicatorStats, getDomainTree, updateDomain } from "@/api/platformApi";
import {
	createBusinessProcessApi,
	deleteBusinessProcessApi,
	listBusMatrixApi,
	listConformedDimensionsApi,
	listBusinessProcessesApi,
	saveBusMatrixLinkApi,
	type Sprint64BusinessProcess,
	type Sprint64ConformedDimension,
} from "@/api/sprint64GovernanceApi";
import { useRouter } from "@/routes/hooks";
import { normalizeText } from "@/utils/textUtils";
import {
	buildPlanningRoute,
	createWarehousePlanningContext,
	resolveWarehousePlanningContext,
	resolveWarehousePlanningStatus,
	saveWarehousePlanningContext,
} from "./warehousePlanningContext";
import {
	BUSINESS_PROCESS_SEEDS,
	adoptBusinessProcessSeeds,
	createBusinessProcess,
	loadBusinessProcesses,
	removeBusinessProcess,
	saveBusinessProcesses,
	type BusinessProcess,
} from "./businessProcess";
import { dimensionsForDomain, loadBusMatrix, toggleBusMatrixLink, type BusMatrix, type ConformedDimension } from "./conformedDimensions";

const { Sider, Content } = Layout;
const { Title, Text } = Typography;

const ROOT_KEY = "root";

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
				String(node.name || "").toLowerCase().includes(needle) ||
				String(node.code || "").toLowerCase().includes(needle) ||
				String(node.owner || "").toLowerCase().includes(needle);
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

const toBusinessProcess = (item: Sprint64BusinessProcess, domainId: string): BusinessProcess => {
	const timestamp = item.updatedAt || item.createdAt || new Date().toISOString();
	return {
		version: 1,
		processId: item.processId,
		domainId: item.domainId || domainId,
		name: item.name,
		description: item.description || undefined,
		createdAt: item.createdAt || timestamp,
		updatedAt: timestamp,
	};
};

const toConformedDimension = (item: Sprint64ConformedDimension): ConformedDimension => ({
	version: 1,
	dimensionId: item.dimensionId,
	name: item.name,
	sourceModel: item.sourceModel,
	domainIds: item.domainIds,
});

export default function SubjectAreasPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const [keyword, setKeyword] = useState(searchParams.get("keyword") || "");
	const [domainTree, setDomainTree] = useState<DomainNode[]>([]);
	const [domainIndex, setDomainIndex] = useState<Map<string, DomainNode>>(new Map());
	const [domainOptions, setDomainOptions] = useState<DomainNode[]>([]);
	const [selectedKey, setSelectedKey] = useState<string>(searchParams.get("active") || ROOT_KEY);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editing, setEditing] = useState<DomainNode | null>(null);
	const [form] = Form.useForm();
	const canManage = useGovernanceManageAccess();
	const router = useRouter();
	const [indicatorStats, setIndicatorStats] = useState<{ total: number; published: number; draft: number } | null>(null);
	const [indicatorStatsLoading, setIndicatorStatsLoading] = useState(false);
	const [assetStats, setAssetStats] = useState<{
		datasetCount: number;
		indicatorCount: number | null;
		qualityRuleCount: number | null;
	} | null>(null);
	const [statsLoading, setStatsLoading] = useState(false);
	const [businessProcesses, setBusinessProcesses] = useState<BusinessProcess[]>([]);
	const [processModalOpen, setProcessModalOpen] = useState(false);
	const [processSaving, setProcessSaving] = useState(false);
	const [processForm] = Form.useForm();
	const [busMatrix, setBusMatrix] = useState<BusMatrix | null>(null);
	const [conformedDimensionCatalog, setConformedDimensionCatalog] = useState<ConformedDimension[]>([]);

	const syncQuery = (patch?: { keyword?: string; active?: string }) => {
		const params = new URLSearchParams(searchParams);
		const nextKeyword = patch?.keyword ?? keyword;
		const nextActive = patch?.active ?? selectedKey;
		if (nextKeyword?.trim()) {
			params.set("keyword", nextKeyword.trim());
		} else {
			params.delete("keyword");
		}
		if (nextActive && nextActive !== ROOT_KEY) {
			params.set("active", nextActive);
		} else {
			params.delete("active");
		}
		setSearchParams(params, { replace: true });
	};

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
			if (selectedKey !== ROOT_KEY && !index.has(selectedKey)) {
				setSelectedKey(ROOT_KEY);
			}
		} catch (err: any) {
			toast.error(err?.message || "加载主题域失败");
		} finally {
			setLoading(false);
		}
	}, [selectedKey]);

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
	const activePlanningContext =
		planningResolution.context?.domainId === activeDomain?.id ? planningResolution.context : null;
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
			return { status: "draft" as const, reason: "尚未创建 DWD 维度建模规划" };
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

	useEffect(() => {
		if (!activeDomain?.id) {
			setBusinessProcesses([]);
			setBusMatrix(null);
			setConformedDimensionCatalog([]);
			return;
		}
		let cancelled = false;
		const domainId = activeDomain.id;
		const sessionProcesses = loadBusinessProcesses(domainId);
		setBusinessProcesses(sessionProcesses);
		setBusMatrix(loadBusMatrix(domainId));
		setConformedDimensionCatalog(dimensionsForDomain(domainId));
		void Promise.all([listBusinessProcessesApi(domainId), listConformedDimensionsApi(domainId), listBusMatrixApi(domainId)])
			.then(([apiProcesses, apiDimensions, apiLinks]) => {
				if (cancelled) return;
				const remoteProcesses = Array.isArray(apiProcesses) ? apiProcesses : [];
				const remoteDimensions = Array.isArray(apiDimensions) ? apiDimensions : [];
				const remoteLinks = Array.isArray(apiLinks) ? apiLinks : [];
				if (remoteProcesses.length) {
					const processes = remoteProcesses.map((item) => toBusinessProcess(item, domainId));
					setBusinessProcesses(processes);
					saveBusinessProcesses(domainId, processes);
				}
				if (remoteDimensions.length) setConformedDimensionCatalog(remoteDimensions.map((item) => toConformedDimension(item)));
				if (remoteLinks.length) {
					const links = remoteLinks.reduce<Record<string, string[]>>((acc, item) => {
						if (item.enabled) acc[item.processId] = [...(acc[item.processId] || []), item.dimensionId];
						return acc;
					}, {});
					setBusMatrix({ version: 1, domainId, links, updatedAt: new Date().toISOString() });
				}
			})
			.catch(() => {
				// Session state remains the offline fallback when the platform API is unavailable.
			});
		return () => {
				cancelled = true;
			};
	}, [activeDomain?.id]);

	useEffect(() => {
		syncQuery();
	}, [keyword, selectedKey]);

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
	const conformedDimensions = conformedDimensionCatalog;
	const processDimensionIds = (processId: string) => new Set(busMatrix?.links[processId] || []);
	const openProcessModal = (preset?: Partial<BusinessProcess>) => {
		processForm.resetFields();
		processForm.setFieldsValue({
			processId: preset?.processId || "",
			name: preset?.name || "",
			description: preset?.description || "",
		});
		setProcessModalOpen(true);
	};
	const adoptSeeds = async () => {
		if (!activeDomain?.id) return;
		const domainId = activeDomain.id;
		const localNext = adoptBusinessProcessSeeds(domainId, businessProcesses);
		try {
			const known = new Set(businessProcesses.map((item) => item.processId));
			await Promise.all(
				BUSINESS_PROCESS_SEEDS.filter((seed) => !known.has(seed.processId)).map((seed) =>
					createBusinessProcessApi(domainId, seed),
				),
			);
			const remote = await listBusinessProcessesApi(domainId);
			const next = remote.length ? remote.map((item) => toBusinessProcess(item, domainId)) : localNext;
			setBusinessProcesses(next);
			saveBusinessProcesses(domainId, next);
		} catch {
			setBusinessProcesses(localNext);
		}
		toast.success(`已采用 ${BUSINESS_PROCESS_SEEDS.length} 条业务过程示例`);
	};
	const submitProcess = async () => {
		if (!canManage || !activeDomain?.id) return;
		setProcessSaving(true);
		try {
			const values = await processForm.validateFields();
			const processId = normalizeText(values.processId).toLowerCase().replace(/[^a-z0-9_-]+/g, "-");
			if (businessProcesses.some((item) => item.processId === processId)) throw new Error("业务过程编码已存在");
			const localProcess = createBusinessProcess({
				processId,
				domainId: activeDomain.id,
				name: normalizeText(values.name),
				description: normalizeText(values.description) || undefined,
			});
			let next = [...businessProcesses, localProcess];
			try {
				const saved = await createBusinessProcessApi(activeDomain.id, {
					processId,
					name: localProcess.name,
					description: localProcess.description,
				});
				next = [...businessProcesses, toBusinessProcess(saved, activeDomain.id)];
			} catch {
				// Keep the session draft usable when the backend is not reachable yet.
			}
			if (!saveBusinessProcesses(activeDomain.id, next)) throw new Error("业务过程草稿保存失败");
			setBusinessProcesses(next);
			setProcessModalOpen(false);
			toast.success("业务过程已创建");
		} catch (err: any) {
			if (!err?.errorFields) toast.error(err?.message || "业务过程保存失败");
		} finally {
			setProcessSaving(false);
		}
	};
	const startProcessPlanning = (process: BusinessProcess) => {
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
		router.push(buildPlanningRoute("/governance/standards/elements?bindingDraft=1", context));
	};
	const deleteProcess = (process: BusinessProcess) => {
		if (!activeDomain?.id || !canManage) return;
		const domainId = activeDomain.id;
		Modal.confirm({
			title: "删除业务过程？",
			content: `删除“${process.name}”后，当前 session 草稿中的矩阵勾选也会失去业务锚点。`,
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				const next = businessProcesses.filter((item) => item.processId !== process.processId);
				try {
					await deleteBusinessProcessApi(domainId, process.processId);
				} catch {
					// Deleting a local draft is still safe if the remote service is unavailable.
				}
				removeBusinessProcess(domainId, process.processId);
				setBusinessProcesses(next);
			},
		});
	};
	const toggleMatrix = (processId: string, dimensionId: string) => {
		if (!activeDomain?.id) return;
		const next = toggleBusMatrixLink(activeDomain.id, processId, dimensionId);
		setBusMatrix(next);
		void saveBusMatrixLinkApi(activeDomain.id, {
			processId,
			dimensionId,
			enabled: next.links[processId]?.includes(dimensionId) || false,
		}).catch(() => {
			// Session state remains the immediate UI source of truth during API outages.
		});
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
					<Space>
						<Button onClick={() => openModal(null, null)} disabled={!canManage}>
							新增根域
						</Button>
						<Button
							type="primary"
							onClick={() => openModal(null, activeDomain?.id || undefined)}
							disabled={!canManage || !activeDomain?.id}
						>
							+ 新增子域
						</Button>
					</Space>
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
				<Layout className="overflow-hidden rounded-[24px] border border-border/70 bg-background">
					<Sider width={320} theme="light" className="border-r border-slate-200 p-4">
					<Space direction="vertical" className="w-full" size="middle">
						<div className="flex items-center justify-between">
							<Text strong>域目录结构</Text>
							<Button type="link" size="small" onClick={() => openModal(null, null)} disabled={!canManage}>
								+ 新增域
							</Button>
						</div>
						<Tree
							showLine
							defaultExpandAll
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
					<Content className="p-6">
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
							<div className="flex items-start justify-between gap-4">
								<div>
									<Space size={8} wrap>
										<Title level={4} style={{ margin: 0 }}>
											{activeDomain.name || "未命名主题域"}
										</Title>
										{activeDomain.code ? <Tag color="blue">{activeDomain.code}</Tag> : null}
									</Space>
									<div className="mt-2 text-sm text-slate-500">
										负责人：{activeDomain.owner || "未指定"} ｜ 子域数：{activeChildren.length} ｜ 资产数：{statsLoading ? "..." : (assetStats?.datasetCount ?? "-")}
									</div>
									{activeDomain.description ? (
										<Text type="secondary" className="block mt-2">
											{activeDomain.description}
										</Text>
									) : null}
								</div>
								<Space>
									<Button onClick={() => openModal(activeDomain, activeDomain.parentId)} disabled={!canManage}>
										编辑域属性
									</Button>
									<Button danger onClick={() => confirmDelete(activeDomain)} disabled={!canManage}>
										删除域
									</Button>
								</Space>
							</div>

							<Divider />

							<Alert
								showIcon
								data-testid="warehouse-planning-card"
								type={planningStatus.status === "blocked" ? "error" : planningStatus.status === "ready" ? "success" : "info"}
								message="数仓规划 · DWD 维度建模"
								description={
									<Space direction="vertical" size={4}>
										<Text>
											主题域：{activeDomain.name || activeDomain.id} · 数仓层：DWD · 建模模式：维度建模
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

							<Card
								className="border-slate-200"
								title="业务过程"
								extra={
									<Space>
										<Button size="small" onClick={adoptSeeds} disabled={!canManage}>
											{businessProcesses.length ? "补充示例" : "从示例创建"}
										</Button>
										<Button size="small" type="primary" onClick={() => openProcessModal()} disabled={!canManage}>
											新增业务过程
										</Button>
									</Space>
								}
							>
								{businessProcesses.length ? (
									<Space direction="vertical" className="w-full" size="middle">
										{businessProcesses.map((process) => (
											<div key={process.processId} className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
												<div className="flex flex-wrap items-start justify-between gap-3">
													<div>
														<div className="font-semibold text-slate-900">{process.name}</div>
														<div className="mt-1 text-xs text-slate-500">{process.processId}</div>
														{process.description ? <div className="mt-1 text-sm text-slate-600">{process.description}</div> : null}
													</div>
													<Space size={4}>
														<Button size="small" type="link" onClick={() => startProcessPlanning(process)}>
															发起规划
														</Button>
														<Button size="small" danger type="link" onClick={() => deleteProcess(process)} disabled={!canManage}>
															删除
														</Button>
													</Space>
												</div>
											</div>
										))}
									</Space>
								) : (
										<EmptyState title="暂无业务过程" description="业务过程是事实建模的锚点，可从 PJM 示例开始。" actions={<Button onClick={adoptSeeds} disabled={!canManage}>从示例创建</Button>} />
								)}
							</Card>

							<Card
								className="border-slate-200"
								title="总线矩阵"
								extra={<Text type="secondary">业务过程 × 一致性维度</Text>}
							>
								{businessProcesses.length && conformedDimensions.length ? (
									<div className="overflow-x-auto">
										<table className="min-w-full text-sm">
											<thead>
												<tr className="border-b border-slate-200 text-left text-xs text-slate-500">
													<th className="px-3 py-2">业务过程</th>
													{conformedDimensions.map((dimension) => <th key={dimension.dimensionId} className="px-3 py-2 whitespace-nowrap">{dimension.name}</th>)}
												</tr>
											</thead>
											<tbody>
												{businessProcesses.map((process) => {
													const selectedDimensions = processDimensionIds(process.processId);
													return <tr key={process.processId} className="border-b border-slate-100">
														<td className="px-3 py-2 font-medium text-slate-800">{process.name}</td>
														{conformedDimensions.map((dimension) => <td key={dimension.dimensionId} className="px-3 py-2"><Checkbox checked={selectedDimensions.has(dimension.dimensionId)} onChange={() => toggleMatrix(process.processId, dimension.dimensionId)} /></td>)}
													</tr>;
												})}
											</tbody>
										</table>
									</div>
								) : (
									<EmptyState title="矩阵尚未形成" description="先创建业务过程，登记后即可勾选可复用维度。" />
								)}
							</Card>

							<div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
								<div className="rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-4">
									<div className="mb-3 text-sm font-semibold text-slate-900">关联术语</div>
									<Space wrap>
										<Tag>暂无挂载</Tag>
									</Space>
									<Button className="mt-3" type="dashed" block disabled>
										+ 挂载术语
									</Button>
								</div>
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
										<Text strong>{assetStats?.qualityRuleCount != null ? assetStats.qualityRuleCount : "-"}</Text>
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
											<a
												onClick={() => router.push(`/governance/indicator-center?domain=${activeDomain?.code ?? ""}`)}
												className="cursor-pointer text-xs text-blue-500 hover:underline"
											>
												查看该域全部指标 →
											</a>
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
											<Tag key={child.id || child.name}>
												{child.name}
											</Tag>
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
						<Input placeholder="例如：节点计划闭环" />
					</Form.Item>
					<Form.Item
							name="processId"
							label="业务过程编码"
							rules={[{ required: true, message: "请输入业务过程编码" }, { min: 2, max: 64, message: "编码长度为 2-64 位" }, { pattern: /^[a-z0-9][a-z0-9_-]*$/, message: "仅支持小写字母、数字、下划线和连字符，且必须以字母或数字开头" }]}
					>
						<Input placeholder="node-plan-loop" />
					</Form.Item>
					<Form.Item name="description" label="过程说明">
						<Input.TextArea rows={3} placeholder="说明过程边界、开始和结束条件" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
