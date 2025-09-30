import { useMemo, useState } from "react";
import { Tree, TreeSelect } from "antd";
import type { DataNode, TreeProps } from "antd/es/tree";
import clsx from "clsx";
import { toast } from "sonner";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Checkbox } from "@/ui/checkbox";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription } from "@/ui/dialog";
import { Drawer, DrawerContent, DrawerDescription, DrawerHeader, DrawerTitle } from "@/ui/drawer";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Textarea } from "@/ui/textarea";
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from "@/ui/select";
import { useEffect } from "react";
import {
	listDatasets as apiListDatasets,
	getDomainTree,
	moveDomain,
	getAccessPolicy,
	upsertAccessPolicy,
	previewSecurityViews,
	createDomain as apiCreateDomain,
	updateDomain as apiUpdateDomain,
	deleteDomain as apiDeleteDomain,
	updateDataset as apiUpdateDataset,
} from "@/api/platformApi";

const SECURITY_LEVELS = [
	{ value: "PUBLIC", label: "公开" },
	{ value: "INTERNAL", label: "内部" },
	{ value: "SECRET", label: "秘密" },
	{ value: "TOP_SECRET", label: "核心" },
] as const;

type SecurityLevel = (typeof SECURITY_LEVELS)[number]["value"];

type DomainNode = {
	key: string;
	name: string;
	owner: string;
	classification: SecurityLevel;
	sourceSystem: string;
	description?: string;
	children?: DomainNode[];
};

type Dataset = {
	id: string;
	name: string;
	domainKey: string;
	owner: string;
	classification: SecurityLevel;
	sourceSystem: string;
	lastUpdated: string;
	tags: string[];
};

type DomainMeta = {
	pathKeys: string[];
	pathNames: string[];
	node: DomainNode;
};

const INITIAL_DOMAINS: DomainNode[] = [
	{
		key: "domain-core",
		name: "企业核心域",
		owner: "陈伟",
		classification: "TOP_SECRET",
		sourceSystem: "CDP",
		description: "覆盖企业战略与核心经营指标",
		children: [
			{
				key: "domain-core-ops",
				name: "经营分析主题",
				owner: "陈伟",
				classification: "SECRET",
				sourceSystem: "CDP",
				children: [
					{
						key: "domain-core-ops-sales",
						name: "销售运营",
						owner: "周丽",
						classification: "SECRET",
						sourceSystem: "Salesforce",
					},
					{
						key: "domain-core-ops-supply",
						name: "供应链管理",
						owner: "张强",
						classification: "INTERNAL",
						sourceSystem: "SAP",
					},
				],
			},
			{
				key: "domain-core-risk",
				name: "风控主题",
				owner: "李云",
				classification: "SECRET",
				sourceSystem: "RiskHub",
			},
		],
	},
	{
		key: "domain-shared",
		name: "共享域",
		owner: "刘敏",
		classification: "INTERNAL",
		sourceSystem: "ODS",
		children: [
			{
				key: "domain-shared-master",
				name: "主数据主题",
				owner: "刘敏",
				classification: "INTERNAL",
				sourceSystem: "MDM",
			},
			{
				key: "domain-shared-ref",
				name: "参考数据主题",
				owner: "赵倩",
				classification: "PUBLIC",
				sourceSystem: "ODS",
			},
		],
	},
];

const INITIAL_DATASETS: Dataset[] = [
	{
		id: "ds-1001",
		name: "销售收入日报",
		domainKey: "domain-core-ops-sales",
		owner: "周丽",
		classification: "SECRET",
		sourceSystem: "Salesforce",
		lastUpdated: "2024-12-08",
		tags: ["销售", "日报"],
	},
	{
		id: "ds-1002",
		name: "渠道目标完成情况",
		domainKey: "domain-core-ops-sales",
		owner: "周丽",
		classification: "SECRET",
		sourceSystem: "Salesforce",
		lastUpdated: "2024-12-06",
		tags: ["渠道", "KPI"],
	},
	{
		id: "ds-1100",
		name: "供应商准入记录",
		domainKey: "domain-core-ops-supply",
		owner: "张强",
		classification: "INTERNAL",
		sourceSystem: "SAP",
		lastUpdated: "2024-12-07",
		tags: ["供应链"],
	},
	{
		id: "ds-1200",
		name: "风控案件清单",
		domainKey: "domain-core-risk",
		owner: "李云",
		classification: "SECRET",
		sourceSystem: "RiskHub",
		lastUpdated: "2024-12-05",
		tags: ["风控"],
	},
	{
		id: "ds-2000",
		name: "客户主数据",
		domainKey: "domain-shared-master",
		owner: "刘敏",
		classification: "INTERNAL",
		sourceSystem: "MDM",
		lastUpdated: "2024-12-02",
		tags: ["主数据"],
	},
];

function cloneDomains(domains: DomainNode[]): DomainNode[] {
	return domains.map((node) => ({
		...node,
		children: node.children ? cloneDomains(node.children) : undefined,
	}));
}

function loop(
	nodes: DomainNode[],
	key: string,
	callback: (node: DomainNode, index: number, array: DomainNode[]) => void,
) {
	for (let i = 0; i < nodes.length; i += 1) {
		const node = nodes[i];
		if (node.key === key) {
			callback(node, i, nodes);
			return;
		}
		if (node.children) {
			loop(node.children, key, callback);
		}
	}
}

function buildDomainMeta(domains: DomainNode[]): Record<string, DomainMeta> {
	const meta: Record<string, DomainMeta> = {};

	const traverse = (nodes: DomainNode[], ancestorKeys: string[], ancestorNames: string[]) => {
		nodes.forEach((node) => {
			const currentKeys = [...ancestorKeys, node.key];
			const currentNames = [...ancestorNames, node.name];
			meta[node.key] = {
				pathKeys: currentKeys,
				pathNames: currentNames,
				node,
			};
			if (node.children) {
				traverse(node.children, currentKeys, currentNames);
			}
		});
	};

	traverse(domains, [], []);
	return meta;
}

function collectExpandedKeys(domains: DomainNode[]): string[] {
	const keys: string[] = [];
	const walk = (nodes: DomainNode[]) => {
		nodes.forEach((node) => {
			keys.push(node.key);
			if (node.children) {
				walk(node.children);
			}
		});
	};
	walk(domains);
	return keys;
}

function formatPath(pathNames: string[]): string {
	return pathNames.join(" / ");
}

function generateKey(prefix: string) {
	if (typeof crypto !== "undefined" && crypto.randomUUID) {
		return `${prefix}-${crypto.randomUUID()}`;
	}
	return `${prefix}-${Math.random().toString(36).slice(2, 8)}`;
}

function highlight(text: string, keyword: string) {
	if (!keyword.trim()) return text;
	const regex = new RegExp(`(${keyword.replace(/[.*+?^${}()|[\\]\\]/g, "\\$&")})`, "ig");
	return text.split(regex).map((segment, index) =>
		regex.test(segment) ? (
			<mark key={`${segment}-${index}`} className="rounded bg-amber-200/70 px-0.5">
				{segment}
			</mark>
		) : (
			<span key={`${segment}-${index}`}>{segment}</span>
		),
	);
}

function filterTreeByKeyword(nodes: DomainNode[], keyword: string): string[] {
	if (!keyword.trim()) return [];
	const matchedKeys: string[] = [];
	const lower = keyword.toLowerCase();
	const traverse = (items: DomainNode[], ancestors: string[]) => {
		items.forEach((node) => {
			const includes = node.name.toLowerCase().includes(lower) || node.owner.toLowerCase().includes(lower);
			if (includes) {
				matchedKeys.push(...ancestors, node.key);
			}
			if (node.children) {
				traverse(node.children, includes ? [...ancestors, node.key] : ancestors);
			}
		});
	};
	traverse(nodes, []);
	return Array.from(new Set(matchedKeys));
}

function countDatasetsByDomain(domains: DomainNode[], datasets: Dataset[]): Record<string, number> {
	const meta = buildDomainMeta(domains);
	const counts: Record<string, number> = {};
	datasets.forEach((dataset) => {
		const domain = meta[dataset.domainKey];
		if (!domain) return;
		domain.pathKeys.forEach((key) => {
			counts[key] = (counts[key] ?? 0) + 1;
		});
	});
	return counts;
}

function DomainBadge({ level }: { level: SecurityLevel }) {
	const meta = SECURITY_LEVELS.find((item) => item.value === level);
	if (!meta) return null;
	const tone =
		level === "TOP_SECRET"
			? "bg-rose-500/10 text-rose-500"
			: level === "SECRET"
				? "bg-amber-500/10 text-amber-500"
				: level === "INTERNAL"
					? "bg-sky-500/10 text-sky-500"
					: "bg-emerald-500/10 text-emerald-600";
	return (
		<span className={clsx("inline-flex items-center rounded px-1.5 py-0.5 text-xs font-semibold", tone)}>
			{meta.label}
		</span>
	);
}

export default function DataDomainManagementPage() {
	const [domains, setDomains] = useState<DomainNode[]>(INITIAL_DOMAINS);
	const [datasets, setDatasets] = useState<Dataset[]>(INITIAL_DATASETS);
	const [selectedDomainKey, setSelectedDomainKey] = useState<string>(INITIAL_DOMAINS[0]?.key ?? "");
	const [searchKeyword, setSearchKeyword] = useState("");
	const [datasetKeyword, setDatasetKeyword] = useState("");
	const [ownerFilter, setOwnerFilter] = useState("all");
	const [levelFilter, setLevelFilter] = useState("all");
	const [sourceFilter, setSourceFilter] = useState("all");
	const [expandedKeys, setExpandedKeys] = useState<string[]>(() => collectExpandedKeys(INITIAL_DOMAINS));
	const [autoExpandParent, setAutoExpandParent] = useState(true);
	const [selectedDatasetIds, setSelectedDatasetIds] = useState<string[]>([]);
	const [formOpen, setFormOpen] = useState(false);
	const [policyOpen, setPolicyOpen] = useState(false);
	const [policyDatasetId, setPolicyDatasetId] = useState<string>("");
	const [policyRoles, setPolicyRoles] = useState<string>("ROLE_PUBLIC,ROLE_INTERNAL");
	const [policyRowFilter, setPolicyRowFilter] = useState<string>("");
	const [policyPreview, setPolicyPreview] = useState<Record<string, string> | null>(null);
	const [formMode, setFormMode] = useState<"create" | "edit">("create");
	const [editingNode, setEditingNode] = useState<DomainNode | null>(null);
	const [formState, setFormState] = useState({
		name: "",
		owner: "",
		classification: "INTERNAL" as SecurityLevel,
		sourceSystem: "",
		description: "",
	});
	const [impactInfo, setImpactInfo] = useState<{ open: boolean; nodeName: string; affectedDatasets: Dataset[] }>({
		open: false,
		nodeName: "",
		affectedDatasets: [],
	});
	const [migrationTarget, setMigrationTarget] = useState<string>("");
	const [migrationOpen, setMigrationOpen] = useState(false);

	const domainMeta = useMemo(() => buildDomainMeta(domains), [domains]);

	// Load domain tree from backend if available
	useEffect(() => {
		(async () => {
			try {
				const tree = (await getDomainTree()) as any[];
				if (Array.isArray(tree) && tree.length) {
					const mapNode = (n: any): DomainNode => ({
						key: String(n.id),
						name: n.name,
						owner: n.owner || "",
						classification: "INTERNAL",
						sourceSystem: "",
						description: n.description || "",
						children: Array.isArray(n.children) ? n.children.map(mapNode) : undefined,
					});
					const nodes = tree.map(mapNode);
					setDomains(nodes);
					setSelectedDomainKey(nodes[0]?.key || "");
					setExpandedKeys(collectExpandedKeys(nodes));
				}
			} catch (e) {
				console.warn("load domain tree failed", e);
			}
		})();
	}, []);

	useEffect(() => {
		// Try loading datasets from backend; fallback to demo data when empty
		(async () => {
			try {
				const resp = (await apiListDatasets({ page: 0, size: 50 })) as any;
				const content = (resp && (resp as any).content) || [];
				if (Array.isArray(content) && content.length) {
					const mapped: Dataset[] = content.map((it: any) => ({
						id: String(it.id),
						name: it.name,
						domainKey: String(it.domainId || ""),
						owner: it.owner || "",
						classification: (it.classification || "INTERNAL") as SecurityLevel,
						sourceSystem: it.type || "",
						lastUpdated: new Date().toISOString().slice(0, 10),
						tags: [],
					}));
					setDatasets(mapped);
				}
			} catch (e) {
				console.warn("load datasets failed", e);
			}
		})();
	}, []);
	const datasetCounts = useMemo(() => countDatasetsByDomain(domains, datasets), [domains, datasets]);

	const treeData = useMemo<DataNode[]>(() => {
		const build = (nodes: DomainNode[]): DataNode[] =>
			nodes.map((node) => {
				const count = datasetCounts[node.key] ?? 0;
				return {
					key: node.key,
					title: (
						<div className="flex items-center justify-between gap-2 pr-2">
							<div className="flex flex-1 items-center gap-2 overflow-hidden">
								<DomainBadge level={node.classification} />
								<span className="truncate text-sm font-medium text-text-primary">
									{highlight(node.name, searchKeyword)}
								</span>
								<span className="truncate text-xs text-text-tertiary">{node.owner}</span>
							</div>
							<span className="text-xs font-medium text-text-tertiary">{count}</span>
						</div>
					),
					children: node.children ? build(node.children) : undefined,
					isLeaf: !node.children?.length,
				};
			});
		return build(domains);
	}, [domains, datasetCounts, searchKeyword]);

	const owners = useMemo(() => Array.from(new Set(datasets.map((item) => item.owner))), [datasets]);
	const levels = SECURITY_LEVELS.map((item) => item);
	const sources = useMemo(() => Array.from(new Set(datasets.map((item) => item.sourceSystem))), [datasets]);

	const selectedDomainMeta = selectedDomainKey ? domainMeta[selectedDomainKey] : undefined;

	const filteredDatasets = useMemo(() => {
		return datasets.filter((dataset) => {
			const meta = domainMeta[dataset.domainKey];
			if (!meta) return false;
			const belongsToDomain = selectedDomainKey ? meta.pathKeys.includes(selectedDomainKey) : true;
			if (!belongsToDomain) return false;
			if (datasetKeyword && !dataset.name.toLowerCase().includes(datasetKeyword.toLowerCase())) return false;
			if (ownerFilter !== "all" && dataset.owner !== ownerFilter) return false;
			if (levelFilter !== "all" && dataset.classification !== levelFilter) return false;
			if (sourceFilter !== "all" && dataset.sourceSystem !== sourceFilter) return false;
			return true;
		});
	}, [datasets, domainMeta, selectedDomainKey, datasetKeyword, ownerFilter, levelFilter, sourceFilter]);

	const handleExpand: TreeProps["onExpand"] = (keys) => {
		setExpandedKeys(keys as string[]);
		setAutoExpandParent(false);
	};

	const handleSelect: TreeProps["onSelect"] = (_keys, info) => {
		setSelectedDomainKey(info.node.key as string);
	};

	const handleDrop: TreeProps["onDrop"] = async (info) => {
		const dropKey = info.node.key as string;
		const dragKey = info.dragNode.key as string;
		const dropPos = info.node.pos.split("-");
		const dropPosition = info.dropPosition - Number.parseInt(dropPos[dropPos.length - 1] ?? "0", 10);
		const data = cloneDomains(domains);
		let dragObj: DomainNode | null = null;

		loop(data, dragKey, (_node, index, array) => {
			dragObj = array.splice(index, 1)[0];
		});

		if (!dragObj) return;
		const currentDrag = dragObj as DomainNode;

		if (!info.dropToGap) {
			loop(data, dropKey, (node) => {
				node.children = node.children ? [...node.children, currentDrag as DomainNode] : [currentDrag as DomainNode];
			});
		} else if ((info.node.children || []).length > 0 && info.node.expanded && dropPosition === 1) {
			loop(data, dropKey, (node) => {
				node.children = node.children ? [currentDrag as DomainNode, ...node.children] : [currentDrag as DomainNode];
			});
		} else {
			let targetArray: DomainNode[] = [];
			let targetIndex = 0;
			loop(data, dropKey, (_node, index, array) => {
				targetArray = array;
				targetIndex = index;
			});

			if (dropPosition < 0) {
				targetArray.splice(targetIndex, 0, currentDrag);
			} else {
				targetArray.splice(targetIndex + 1, 0, currentDrag);
			}
		}

		setDomains(data);
		const impactedMeta = buildDomainMeta(data);
		const impacted = datasets.filter((dataset) => impactedMeta[dataset.domainKey]?.pathKeys.includes(currentDrag.key));
		setImpactInfo({ open: true, nodeName: currentDrag.name, affectedDatasets: impacted });
		try {
			await moveDomain(dragKey, { newParentId: dropKey });
			toast.success("已更新层级");
		} catch (e) {
			console.error(e);
			toast.error("更新层级失败");
		}
	};

	const openCreateForm = () => {
		setFormMode("create");
		setEditingNode(null);
		setFormState({
			name: "",
			owner: selectedDomainMeta?.node.owner ?? "",
			classification: selectedDomainMeta?.node.classification ?? "INTERNAL",
			sourceSystem: selectedDomainMeta?.node.sourceSystem ?? "",
			description: "",
		});
		setFormOpen(true);
	};

	const openEditForm = () => {
		if (!selectedDomainMeta) {
			toast.warning("请选择需要编辑的节点");
			return;
		}
		setFormMode("edit");
		setEditingNode(selectedDomainMeta.node);
		setFormState({
			name: selectedDomainMeta.node.name,
			owner: selectedDomainMeta.node.owner,
			classification: selectedDomainMeta.node.classification,
			sourceSystem: selectedDomainMeta.node.sourceSystem,
			description: selectedDomainMeta.node.description ?? "",
		});
		setFormOpen(true);
	};

	const closeForm = () => setFormOpen(false);

	const handleFormSubmit = async () => {
		if (!formState.name.trim()) {
			toast.error("请填写节点名称");
			return;
		}

		try {
			if (formMode === "create") {
				const payload: any = { name: formState.name.trim(), owner: formState.owner.trim() };
				if (selectedDomainMeta) payload.parent = { id: selectedDomainMeta.node.key };
				const saved = (await apiCreateDomain(payload)) as any;
				// reflect locally
				const newNode: DomainNode = {
					key: String(saved.id || generateKey("domain")),
					name: saved.name || formState.name.trim(),
					owner: saved.owner || formState.owner.trim() || "暂未指定",
					classification: formState.classification,
					sourceSystem: formState.sourceSystem.trim() || "未配置",
					description: saved.description || formState.description.trim() || undefined,
				};
				if (selectedDomainMeta) {
					const data = cloneDomains(domains);
					loop(data, selectedDomainMeta.node.key, (node) => {
						node.children = node.children ? [...node.children, newNode] : [newNode];
					});
					setDomains(data);
				} else {
					setDomains((prev) => [...prev, newNode]);
				}
				setSelectedDomainKey(newNode.key);
				toast.success("节点已新增");
			} else if (editingNode) {
				const payload: any = { name: formState.name.trim(), owner: formState.owner.trim() };
				await apiUpdateDomain(editingNode.key, payload);
				const data = cloneDomains(domains);
				loop(data, editingNode.key, (node, index, array) => {
					array[index] = {
						...node,
						name: formState.name.trim(),
						owner: formState.owner.trim() || "暂未指定",
						classification: formState.classification,
						sourceSystem: formState.sourceSystem.trim() || "未配置",
						description: formState.description.trim() || undefined,
					} as DomainNode;
				});
				setDomains(data);
				toast.success("节点信息已更新");
			}
		} catch (e) {
			console.error(e);
			toast.error("保存失败");
		}

		setFormOpen(false);
	};

	const toggleDatasetSelection = (id: string) => {
		setSelectedDatasetIds((prev) => (prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]));
	};

	const toggleSelectAllDatasets = (checked: boolean) => {
		if (checked) {
			setSelectedDatasetIds(filteredDatasets.map((item) => item.id));
		} else {
			setSelectedDatasetIds([]);
		}
	};

	const openMigration = () => {
		if (!selectedDatasetIds.length) {
			toast.warning("请选择需要迁移的数据集");
			return;
		}
		setMigrationTarget("");
		setMigrationOpen(true);
	};

	const handleMigration = async () => {
		if (!migrationTarget) {
			toast.error("请选择目标域或主题");
			return;
		}
		// persist to backend
		try {
			for (const id of selectedDatasetIds) {
				await apiUpdateDataset(id, { domain: { id: migrationTarget } });
			}
		} catch (e) {
			console.error(e);
			toast.error("迁移失败（部分或全部）");
		}
		setDatasets((prev) =>
			prev.map((dataset) =>
				selectedDatasetIds.includes(dataset.id) ? { ...dataset, domainKey: migrationTarget } : dataset,
			),
		);
		setSelectedDatasetIds([]);
		setMigrationOpen(false);
		toast.success("数据集已迁移");
	};

	const handleTemplateDownload = () => {
		const headers = ["名称", "负责人", "默认密级", "来源系统", "父节点路径"];
		const csv = `${headers.join(",")}`;
		const blob = new Blob([`${csv}\n`], { type: "text/csv;charset=utf-8;" });
		const url = URL.createObjectURL(blob);
		const link = document.createElement("a");
		link.href = url;
		link.download = "domain-import-template.csv";
		link.click();
		URL.revokeObjectURL(url);
		toast.info("已下载导入模板");
	};

	const handleFileUpload: React.ChangeEventHandler<HTMLInputElement> = (event) => {
		const file = event.target.files?.[0];
		if (!file) return;
		toast.success(`已上传 ${file.name}，正在后台校验`);
		event.target.value = "";
	};

	return (
		<div className="space-y-4">
			<div className="flex items-center gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-600">
				<span aria-hidden className="text-red-500">
					★
				</span>
				此功能涉及密级数据，请注意保密！
			</div>
			<div className="grid gap-4 xl:grid-cols-[320px,1fr]">
				<Card className="h-[calc(100vh-220px)]">
					<CardHeader className="space-y-4">
						<CardTitle className="flex items-center justify-between text-base font-semibold">
							<span>数据域/主题域</span>
							<div className="flex items-center gap-2">
								<Button size="sm" variant="outline" onClick={openEditForm}>
									编辑
								</Button>
								<Button size="sm" onClick={openCreateForm}>
									新增节点
								</Button>
								<Button
									size="sm"
									variant="destructive"
									onClick={async () => {
										if (!selectedDomainMeta) {
											toast.warning("请选择需要删除的节点");
											return;
										}
										const key = selectedDomainMeta.node.key;
										const hasChildren = !!selectedDomainMeta.node.children?.length;
										const hasDatasets = (datasetCounts[key] ?? 0) > 0;
										if (hasChildren || hasDatasets) {
											toast.error("请先清空子节点和数据集后再删除");
											return;
										}
										try {
											await apiDeleteDomain(key);
											setDomains((prev) => prev.filter((n) => n.key !== key));
											setSelectedDomainKey("");
											toast.success("已删除");
										} catch (e) {
											console.error(e);
											toast.error("删除失败");
										}
									}}
								>
									删除
								</Button>
							</div>
						</CardTitle>
						<div className="space-y-2">
							<Label className="text-xs text-muted-foreground">关键词搜索</Label>
							<Input
								value={searchKeyword}
								onChange={(event) => {
									setSearchKeyword(event.target.value);
									if (event.target.value) {
										const keys = filterTreeByKeyword(domains, event.target.value);
										setExpandedKeys((prev) => Array.from(new Set([...prev, ...keys])));
										setAutoExpandParent(true);
									}
								}}
								placeholder="搜索域 / 主题 / 负责人"
							/>
						</div>
					</CardHeader>
					<CardContent className="h-full overflow-hidden">
						<ScrollArea className="h-full">
							<Tree
								showLine
								draggable
								onDrop={handleDrop}
								treeData={treeData}
								onSelect={handleSelect}
								selectedKeys={selectedDomainKey ? [selectedDomainKey] : []}
								expandedKeys={expandedKeys}
								onExpand={handleExpand}
								autoExpandParent={autoExpandParent}
								blockNode
							/>
						</ScrollArea>
					</CardContent>
				</Card>

				<Card className="h-full">
					<CardHeader className="space-y-3">
						<CardTitle className="flex items-center justify-between text-base font-semibold">
							<span>数据集列表</span>
							<div className="flex items-center gap-2">
								<Button size="sm" variant="outline" onClick={handleTemplateDownload}>
									模板下载
								</Button>
								<Button size="sm" variant="outline" onClick={openMigration}>
									批量迁移
								</Button>
								<label className="inline-flex cursor-pointer items-center gap-2">
									<input type="file" accept=".csv,.xlsx" className="hidden" onChange={handleFileUpload} />
									<Button size="sm" variant="ghost">
										批量导入
									</Button>
								</label>
							</div>
						</CardTitle>
						<div className="grid gap-3 lg:grid-cols-4">
							<Input
								value={datasetKeyword}
								onChange={(event) => setDatasetKeyword(event.target.value)}
								placeholder="搜索数据集名称"
								className="lg:col-span-2"
							/>
							<Select value={ownerFilter} onValueChange={setOwnerFilter}>
								<SelectTrigger>
									<SelectValue placeholder="负责人" />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="all">负责人筛选</SelectItem>
									{owners.map((owner) => (
										<SelectItem key={owner} value={owner}>
											{owner}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
							<Select value={levelFilter} onValueChange={setLevelFilter}>
								<SelectTrigger>
									<SelectValue placeholder="密级" />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="all">密级筛选</SelectItem>
									{levels.map((item) => (
										<SelectItem key={item.value} value={item.value}>
											{item.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
							<Select value={sourceFilter} onValueChange={setSourceFilter}>
								<SelectTrigger>
									<SelectValue placeholder="来源系统" />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="all">来源系统</SelectItem>
									{sources.map((source) => (
										<SelectItem key={source} value={source}>
											{source}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
					</CardHeader>
					<CardContent className="space-y-4">
						<div className="overflow-hidden rounded-md border">
							<table className="w-full border-collapse text-sm">
								<thead className="bg-muted/50">
									<tr className="text-left">
										<th className="w-12 border-b px-3 py-2">
											<Checkbox
												checked={selectedDatasetIds.length > 0 && selectedDatasetIds.length === filteredDatasets.length}
												onCheckedChange={(value) => toggleSelectAllDatasets(Boolean(value))}
											/>
										</th>
										<th className="border-b px-3 py-2">数据集</th>
										<th className="border-b px-3 py-2">所属域/主题</th>
										<th className="border-b px-3 py-2">负责人</th>
										<th className="border-b px-3 py-2">密级</th>
										<th className="border-b px-3 py-2">来源系统</th>
										<th className="border-b px-3 py-2">最近更新</th>
										<th className="border-b px-3 py-2">策略</th>
									</tr>
								</thead>
								<tbody>
									{filteredDatasets.map((dataset) => {
										const meta = domainMeta[dataset.domainKey];
										return (
											<tr key={dataset.id} className="border-b last:border-b-0">
												<td className="px-3 py-2">
													<Checkbox
														checked={selectedDatasetIds.includes(dataset.id)}
														onCheckedChange={() => toggleDatasetSelection(dataset.id)}
													/>
												</td>
												<td className="px-3 py-2">
													<div className="flex flex-col">
														<span className="font-medium text-text-primary">{dataset.name}</span>
														<span className="text-xs text-text-tertiary">{dataset.tags.join(" · ")}</span>
													</div>
												</td>
												<td className="px-3 py-2 text-sm text-text-secondary">
													{meta ? formatPath(meta.pathNames) : "已失联"}
												</td>
												<td className="px-3 py-2 text-sm text-text-secondary">{dataset.owner}</td>
												<td className="px-3 py-2">
													<DomainBadge level={dataset.classification} />
												</td>
												<td className="px-3 py-2 text-sm text-text-secondary">{dataset.sourceSystem}</td>
												<td className="px-3 py-2 text-sm text-text-secondary">{dataset.lastUpdated}</td>
												<td className="px-3 py-2 text-sm">
													<Button
														size="sm"
														variant="outline"
														onClick={async () => {
															setPolicyDatasetId(dataset.id);
															setPolicyPreview(null);
															try {
																const p = (await getAccessPolicy(dataset.id)) as any;
																if (p && p.allowRoles !== undefined) {
																	setPolicyRoles(p.allowRoles || "");
																	setPolicyRowFilter(p.rowFilter || "");
																} else {
																	setPolicyRoles("ROLE_PUBLIC,ROLE_INTERNAL");
																	setPolicyRowFilter("");
																}
																setPolicyOpen(true);
															} catch (e) {
																setPolicyOpen(true);
															}
														}}
													>
														访问策略
													</Button>
												</td>
											</tr>
										);
									})}
									{filteredDatasets.length === 0 ? (
										<tr>
											<td colSpan={8} className="px-3 py-6 text-center text-sm text-text-tertiary">
												暂无数据集
											</td>
										</tr>
									) : null}
								</tbody>
							</table>
						</div>
						<div className="flex items-center justify-between text-xs text-text-tertiary">
							<span>已选 {selectedDatasetIds.length} 个数据集</span>
							<span>共 {filteredDatasets.length} 个结果</span>
						</div>
					</CardContent>
				</Card>
			</div>

			<Dialog open={formOpen} onOpenChange={setFormOpen}>
				<DialogContent className="max-w-lg">
					<DialogHeader>
						<DialogTitle>{formMode === "create" ? "新增域/主题节点" : "编辑域/主题节点"}</DialogTitle>
					</DialogHeader>
					<div className="space-y-4">
						<div className="grid gap-2">
							<Label>名称 *</Label>
							<Input
								value={formState.name}
								onChange={(event) => setFormState((prev) => ({ ...prev, name: event.target.value }))}
							/>
						</div>
						<div className="grid gap-2">
							<Label>负责人</Label>
							<Input
								value={formState.owner}
								onChange={(event) => setFormState((prev) => ({ ...prev, owner: event.target.value }))}
							/>
						</div>
						<div className="grid gap-2">
							<Label>默认密级</Label>
							<Select
								value={formState.classification}
								onValueChange={(value: SecurityLevel) => setFormState((prev) => ({ ...prev, classification: value }))}
							>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectGroup>
										<SelectLabel>密级</SelectLabel>
										{SECURITY_LEVELS.map((item) => (
											<SelectItem key={item.value} value={item.value}>
												{item.label}
											</SelectItem>
										))}
									</SelectGroup>
								</SelectContent>
							</Select>
						</div>
						<div className="grid gap-2">
							<Label>来源系统</Label>
							<Input
								value={formState.sourceSystem}
								onChange={(event) => setFormState((prev) => ({ ...prev, sourceSystem: event.target.value }))}
								placeholder="如 CDP、SAP、ODS"
							/>
						</div>
						<div className="grid gap-2">
							<Label>描述</Label>
							<Textarea
								value={formState.description}
								onChange={(event) => setFormState((prev) => ({ ...prev, description: event.target.value }))}
								placeholder="简要说明域/主题的职责范围"
							/>
						</div>
					</div>
					<DialogFooter className="mt-6">
						<Button variant="ghost" onClick={closeForm}>
							取消
						</Button>
						<Button onClick={handleFormSubmit}>{formMode === "create" ? "新增" : "保存"}</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>

			<Dialog open={policyOpen} onOpenChange={setPolicyOpen}>
				<DialogContent className="max-w-2xl">
					<DialogHeader>
						<DialogTitle>访问策略</DialogTitle>
						<DialogDescription>为该数据集配置允许访问的角色集合与可选的行级过滤条件（视图路线）。</DialogDescription>
					</DialogHeader>
					<div className="grid gap-3">
						<div className="grid gap-2">
							<Label>允许角色（逗号分隔）</Label>
							<Input value={policyRoles} onChange={(e) => setPolicyRoles(e.target.value)} />
						</div>
						<div className="grid gap-2">
							<Label>行级过滤（可选）</Label>
							<Textarea value={policyRowFilter} onChange={(e) => setPolicyRowFilter(e.target.value)} rows={3} />
						</div>
						<div className="flex items-center gap-2">
							<Button
								variant="outline"
								onClick={async () => {
									try {
										const data = (await previewSecurityViews(policyDatasetId)) as any;
										setPolicyPreview(data || {});
									} catch (e) {
										console.error(e);
										toast.error("获取预览失败");
									}
								}}
							>
								预览安全视图SQL
							</Button>
						</div>
						{policyPreview && (
							<div className="max-h-60 overflow-auto rounded border bg-muted/30 p-2 font-mono text-xs">
								{Object.entries(policyPreview).map(([k, v]) => (
									<div key={k} className="mb-3">
										<div className="mb-1 font-semibold">{k}</div>
										<pre className="whitespace-pre-wrap">{String(v)}</pre>
									</div>
								))}
							</div>
						)}
					</div>
					<DialogFooter>
						<Button variant="ghost" onClick={() => setPolicyOpen(false)}>
							取消
						</Button>
						<Button
							onClick={async () => {
								try {
									await upsertAccessPolicy(policyDatasetId, { allowRoles: policyRoles, rowFilter: policyRowFilter });
									toast.success("已保存访问策略");
									setPolicyOpen(false);
								} catch (e) {
									console.error(e);
									toast.error("保存失败");
								}
							}}
						>
							保存
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>

			<Dialog open={migrationOpen} onOpenChange={setMigrationOpen}>
				<DialogContent className="max-w-md">
					<DialogHeader>
						<DialogTitle>批量迁移数据集</DialogTitle>
					</DialogHeader>
					<div className="space-y-4">
						<p className="text-sm text-text-secondary">
							已选择 {selectedDatasetIds.length} 个数据集，请选择目标域/主题并确认迁移。
						</p>
						<TreeSelect
							value={migrationTarget}
							onChange={(value) => setMigrationTarget(value)}
							treeData={treeData}
							placeholder="选择目标节点"
							className="w-full"
							treeDefaultExpandAll
						/>
					</div>
					<DialogFooter>
						<Button variant="ghost" onClick={() => setMigrationOpen(false)}>
							取消
						</Button>
						<Button onClick={handleMigration}>迁移</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>

			<Drawer open={impactInfo.open} onOpenChange={(open) => setImpactInfo((prev) => ({ ...prev, open }))}>
				<DrawerContent>
					<DrawerHeader>
						<DrawerTitle>变更影响评估</DrawerTitle>
						<DrawerDescription>
							{impactInfo.nodeName} 结构调整会影响 {impactInfo.affectedDatasets.length} 个数据集，请确认相关负责人知悉。
						</DrawerDescription>
					</DrawerHeader>
					<div className="max-h-[320px] overflow-y-auto px-6 pb-6">
						{impactInfo.affectedDatasets.length === 0 ? (
							<p className="text-sm text-text-tertiary">暂无受影响的数据集。</p>
						) : (
							<ul className="space-y-2 text-sm">
								{impactInfo.affectedDatasets.map((dataset) => (
									<li key={dataset.id} className="flex flex-col rounded-md border px-3 py-2">
										<span className="font-medium text-text-primary">{dataset.name}</span>
										<span className="text-xs text-text-tertiary">
											{dataset.owner} · {dataset.sourceSystem}
										</span>
									</li>
								))}
							</ul>
						)}
					</div>
				</DrawerContent>
			</Drawer>
		</div>
	);
}
