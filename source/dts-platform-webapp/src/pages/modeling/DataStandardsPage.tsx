import { useMemo, useState } from "react";
import clsx from "clsx";
import { toast } from "sonner";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Checkbox } from "@/ui/checkbox";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Drawer, DrawerContent, DrawerHeader, DrawerTitle } from "@/ui/drawer";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from "@/ui/select";
import { Separator } from "@/ui/separator";
import { Switch } from "@/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { Textarea } from "@/ui/textarea";

const STATUS_OPTIONS = [
	{ value: "DRAFT", label: "草稿", badge: "outline" as const },
	{ value: "IN_REVIEW", label: "待审批", badge: "secondary" as const },
	{ value: "ACTIVE", label: "启用", badge: "default" as const },
	{ value: "DEPRECATED", label: "已归档", badge: "secondary" as const },
	{ value: "RETIRED", label: "已销毁", badge: "destructive" as const },
];

type StandardStatus = (typeof STATUS_OPTIONS)[number]["value"];

type DataStandard = {
	id: string;
	name: string;
	code: string;
	domain: string;
	scope: string;
	version: string;
	status: StandardStatus;
	owner: string;
	tags: string[];
	securityLevel: string;
	updatedAt: string;
	description?: string;
};

const DOMAIN_OPTIONS = [
	{ value: "核心域", label: "企业核心域" },
	{ value: "共享域", label: "共享域" },
	{ value: "主数据域", label: "主数据域" },
	{ value: "分析域", label: "分析应用域" },
];

const TAG_OPTIONS = ["指标", "主数据", "参考标准", "敏感", "共享", "内控"];

const RESPONSIBLES = ["陈伟", "刘敏", "张霞", "周丽", "李云"];

const INITIAL_STANDARDS: DataStandard[] = [
	{
		id: "std-1001",
		name: "客户主数据标准",
		code: "STD-CUST-001",
		domain: "主数据域",
		scope: "集团内客户主数据",
		version: "v3.0",
		status: "ACTIVE",
		owner: "刘敏",
		tags: ["主数据", "共享"],
		securityLevel: "内部",
		updatedAt: "2024-11-15",
		description: "定义客户唯一标识、主键、证件规范等字段标准",
	},
	{
		id: "std-1002",
		name: "销售订单指标标准",
		code: "STD-SALES-ORD-002",
		domain: "核心域",
		scope: "线上/线下销售全量订单",
		version: "v2.2",
		status: "IN_REVIEW",
		owner: "陈伟",
		tags: ["指标", "敏感"],
		securityLevel: "秘密",
		updatedAt: "2024-12-02",
		description: "统一订单口径、状态机、业务时间字段标准",
	},
	{
		id: "std-1003",
		name: "供应商共享标准",
		code: "STD-SUP-003",
		domain: "共享域",
		scope: "采购供应商档案",
		version: "v1.5",
		status: "ACTIVE",
		owner: "张霞",
		tags: ["共享", "参考标准"],
		securityLevel: "内部",
		updatedAt: "2024-10-21",
		description: "覆盖供应商基本信息、分级、黑名单规则",
	},
	{
		id: "std-1004",
		name: "风险事件编码标准",
		code: "STD-RISK-004",
		domain: "分析域",
		scope: "集团风险事件库",
		version: "v1.0",
		status: "DRAFT",
		owner: "李云",
		tags: ["内控"],
		securityLevel: "秘密",
		updatedAt: "2024-12-06",
		description: "统一风险事件分级、处置状态与责任人字段",
	},
	{
		id: "std-1005",
		name: "产品生命周期标准",
		code: "STD-PROD-005",
		domain: "核心域",
		scope: "集团产品全生命周期",
		version: "v1.9",
		status: "DEPRECATED",
		owner: "周丽",
		tags: ["指标", "内控"],
		securityLevel: "内部",
		updatedAt: "2024-08-30",
		description: "定义产品立项、上线、退市关键节点与字段",
	},
];

type StandardVersionDetail = {
	version: string;
	releaseDate: string;
	author: string;
	status: "PUBLISHED" | "IN_REVIEW" | "ARCHIVED";
	notes: string;
	definition: {
		scope: string;
		owner: string;
		securityLevel: string;
		columns: string[];
		tags: string[];
	};
};

type StandardVersionMap = Record<string, StandardVersionDetail[]>;

const STANDARD_VERSIONS: StandardVersionMap = {
	"std-1001": [
		{
			version: "v3.0",
			releaseDate: "2024-11-15",
			author: "刘敏",
			status: "PUBLISHED",
			notes: "引入客户敏感字段密级标识，调整证件字段长度",
			definition: {
				scope: "集团内客户主数据",
				owner: "刘敏",
				securityLevel: "内部",
				columns: ["customer_id", "customer_name", "credential_type", "credential_no", "security_level"],
				tags: ["主数据", "共享"],
			},
		},
		{
			version: "v2.4",
			releaseDate: "2024-07-02",
			author: "刘敏",
			status: "ARCHIVED",
			notes: "增加客户标签数组字段",
			definition: {
				scope: "集团内客户主数据",
				owner: "刘敏",
				securityLevel: "内部",
				columns: ["customer_id", "customer_name", "credential_type", "credential_no", "customer_tags"],
				tags: ["主数据"],
			},
		},
		{
			version: "v2.0",
			releaseDate: "2024-03-10",
			author: "刘敏",
			status: "ARCHIVED",
			notes: "首个覆盖集团的统一标准",
			definition: {
				scope: "集团内客户主数据",
				owner: "刘敏",
				securityLevel: "内部",
				columns: ["customer_id", "customer_name", "credential_type", "credential_no"],
				tags: ["主数据"],
			},
		},
	],
	"std-1002": [
		{
			version: "v2.2",
			releaseDate: "2024-12-02",
			author: "陈伟",
			status: "IN_REVIEW",
			notes: "订单取消口径调整，新增渠道字段",
			definition: {
				scope: "线上/线下销售全量订单",
				owner: "陈伟",
				securityLevel: "秘密",
				columns: ["order_id", "order_type", "order_channel", "cancel_reason", "revenue_amount"],
				tags: ["指标", "敏感"],
			},
		},
		{
			version: "v2.1",
			releaseDate: "2024-09-12",
			author: "陈伟",
			status: "PUBLISHED",
			notes: "引入口径稽核字段",
			definition: {
				scope: "线上/线下销售全量订单",
				owner: "陈伟",
				securityLevel: "秘密",
				columns: ["order_id", "order_type", "cancel_reason", "revenue_amount", "audit_flag"],
				tags: ["指标"],
			},
		},
	],
};

type ImportErrorDetail = {
	row: number;
	field: string;
	message: string;
};

type ImportResult = {
	fileName: string;
	success: number;
	failed: number;
	total: number;
	errors: ImportErrorDetail[];
	status: "PARTIAL" | "SUCCESS" | "FAILED";
};

type LifecycleStep = {
	key: string;
	label: string;
	requiredPermission: string;
	description: string;
};

const LIFECYCLE_STEPS: LifecycleStep[] = [
	{ key: "create", label: "创建", requiredPermission: "CREATE", description: "草拟标准并提交审批" },
	{ key: "use", label: "使用", requiredPermission: "USE", description: "绑定数据集并发布使用" },
	{ key: "share", label: "共享", requiredPermission: "SHARE", description: "共享至空间/项目" },
	{ key: "archive", label: "归档", requiredPermission: "ARCHIVE", description: "冻结标准并转入归档库" },
	{ key: "retire", label: "销毁", requiredPermission: "RETIRE", description: "销毁标准并撤回引用" },
];

type LifecycleState = Record<string, number>;

type ApprovalNode = {
	node: string;
	assignee: string;
	status: "PENDING" | "APPROVED" | "REJECTED";
	comment?: string;
	finishedAt?: string;
};

type ApprovalRecord = {
	id: string;
	category: "STARTED" | "TODO" | "CC";
	standardId: string;
	standardName: string;
	action: string;
	status: "PENDING" | "APPROVED" | "REJECTED";
	updatedAt: string;
	currentNode: string;
	nodes: ApprovalNode[];
};

const APPROVAL_RECORDS: ApprovalRecord[] = [
	{
		id: "ap-01",
		category: "STARTED",
		standardId: "std-1002",
		standardName: "销售订单指标标准",
		action: "提交审批",
		status: "PENDING",
		updatedAt: "2024-12-02 14:20",
		currentNode: "数据治理经理",
		nodes: [
			{
				node: "数据治理专员",
				assignee: "孙燕",
				status: "APPROVED",
				comment: "指标拆分清晰",
				finishedAt: "2024-12-02 11:05",
			},
			{ node: "数据治理经理", assignee: "曹亮", status: "PENDING" },
			{ node: "安全审批", assignee: "安全审批机器人", status: "PENDING" },
		],
	},
	{
		id: "ap-02",
		category: "TODO",
		standardId: "std-1001",
		standardName: "客户主数据标准",
		action: "版本回滚",
		status: "PENDING",
		updatedAt: "2024-12-05 09:12",
		currentNode: "安全审批",
		nodes: [
			{ node: "数据治理专员", assignee: "孙燕", status: "APPROVED", finishedAt: "2024-12-04 18:30" },
			{ node: "安全审批", assignee: "安全审批机器人", status: "PENDING" },
		],
	},
	{
		id: "ap-03",
		category: "CC",
		standardId: "std-1003",
		standardName: "供应商共享标准",
		action: "共享通知",
		status: "APPROVED",
		updatedAt: "2024-11-21 16:10",
		currentNode: "归档",
		nodes: [
			{
				node: "共享审批",
				assignee: "杨倩",
				status: "APPROVED",
				comment: "同步供应商门户",
				finishedAt: "2024-11-21 15:42",
			},
			{ node: "归档", assignee: "系统", status: "APPROVED", finishedAt: "2024-11-21 16:05" },
		],
	},
];

type StandardFormDraft = {
	name: string;
	code: string;
	domain: string;
	scope: string;
	version: string;
	status: StandardStatus;
	owner: string;
	tags: string;
	securityLevel: string;
	description: string;
};

const EMPTY_FORM: StandardFormDraft = {
	name: "",
	code: "",
	domain: "",
	scope: "",
	version: "v1.0",
	status: "DRAFT",
	owner: "",
	tags: "",
	securityLevel: "内部",
	description: "",
};

function formatValue(value: string | string[]): string {
	if (Array.isArray(value)) {
		return value.join(", ");
	}
	return value;
}

export default function DataStandardsPage() {
	const [standards, setStandards] = useState<DataStandard[]>(INITIAL_STANDARDS);
	const [selectedIds, setSelectedIds] = useState<string[]>([]);
	const [filters, setFilters] = useState({ domain: "ALL", status: "ALL", tag: "ALL", keyword: "" });
	const [formOpen, setFormOpen] = useState(false);
	const [formDraft, setFormDraft] = useState<StandardFormDraft>(EMPTY_FORM);
	const [editingId, setEditingId] = useState<string | null>(null);
	const [importFileName, setImportFileName] = useState("");
	const [importResult, setImportResult] = useState<ImportResult | null>(null);
	const [versionFocusId, setVersionFocusId] = useState<string>(INITIAL_STANDARDS[0]?.id || "std-1001");
	const [targetVersion, setTargetVersion] = useState<string | null>(null);
	const [baseVersion, setBaseVersion] = useState<string | null>(null);
	const [lifecycleState, setLifecycleState] = useState<LifecycleState>(() => {
		return INITIAL_STANDARDS.reduce<LifecycleState>((acc, standard) => {
			const stepIndex =
				standard.status === "ACTIVE"
					? 2
					: standard.status === "DEPRECATED"
						? 3
						: standard.status === "RETIRED"
							? 4
							: standard.status === "IN_REVIEW"
								? 1
								: 0;
			acc[standard.id] = stepIndex;
			return acc;
		}, {});
	});
	const [userPermissions, setUserPermissions] = useState<string[]>(["CREATE", "USE", "SHARE", "ARCHIVE"]);
	const [approvalDetail, setApprovalDetail] = useState<ApprovalRecord | null>(null);

	const versionsForFocus = STANDARD_VERSIONS[versionFocusId] || [];

	const effectiveBaseVersion = baseVersion ?? versionsForFocus[versionsForFocus.length - 1]?.version ?? null;
	const effectiveTargetVersion = targetVersion ?? versionsForFocus[0]?.version ?? null;

	const diffLines = useMemo(() => {
		if (!effectiveBaseVersion || !effectiveTargetVersion) {
			return [] as string[];
		}
		const base = versionsForFocus.find((item) => item.version === effectiveBaseVersion);
		const target = versionsForFocus.find((item) => item.version === effectiveTargetVersion);
		if (!base || !target) {
			return [] as string[];
		}
		const keys = new Set<keyof StandardVersionDetail["definition"]>([
			"scope",
			"owner",
			"securityLevel",
			"columns",
			"tags",
		]);
		const lines: string[] = [`比较 ${base.version} → ${target.version}`];
		for (const key of keys) {
			const baseValue = formatValue(base.definition[key]);
			const targetValue = formatValue(target.definition[key]);
			if (baseValue === targetValue) {
				lines.push(`  ${key}: ${baseValue}`);
			} else {
				lines.push(`- ${key}: ${baseValue}`);
				lines.push(`+ ${key}: ${targetValue}`);
			}
		}
		return lines;
	}, [effectiveBaseVersion, effectiveTargetVersion, versionsForFocus]);

	const filteredStandards = useMemo(() => {
		return standards.filter((standard) => {
			const domainMatch = filters.domain === "ALL" || standard.domain === filters.domain;
			const statusMatch = filters.status === "ALL" || standard.status === filters.status;
			const tagMatch = filters.tag === "ALL" || standard.tags.includes(filters.tag);
			const keyword = filters.keyword.trim().toLowerCase();
			const keywordMatch =
				!keyword ||
				standard.name.toLowerCase().includes(keyword) ||
				standard.code.toLowerCase().includes(keyword) ||
				standard.owner.toLowerCase().includes(keyword);
			return domainMatch && statusMatch && tagMatch && keywordMatch;
		});
	}, [filters, standards]);

	const handleToggleSelect = (id: string) => {
		setSelectedIds((prev) => (prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]));
	};

	const handleSelectAll = (checked: boolean) => {
		setSelectedIds(checked ? filteredStandards.map((item) => item.id) : []);
	};

	const handleOpenCreate = () => {
		setEditingId(null);
		setFormDraft(EMPTY_FORM);
		setFormOpen(true);
	};

	const handleEdit = (standard: DataStandard) => {
		setEditingId(standard.id);
		setFormDraft({
			name: standard.name,
			code: standard.code,
			domain: standard.domain,
			scope: standard.scope,
			version: standard.version,
			status: standard.status,
			owner: standard.owner,
			tags: standard.tags.join(", "),
			securityLevel: standard.securityLevel,
			description: standard.description ?? "",
		});
		setFormOpen(true);
	};

	const handleSubmitForm = () => {
		if (!formDraft.name || !formDraft.code || !formDraft.domain || !formDraft.owner) {
			toast.error("请完整填写标准名称、编号、域和负责人");
			return;
		}
		const now = new Date();
		const formattedDate = now.toISOString().slice(0, 10);
		const normalizedTags = formDraft.tags
			.split(/[，,\s]+/)
			.map((item) => item.trim())
			.filter(Boolean);

		setStandards((prev) => {
			if (editingId) {
				return prev.map((item) =>
					item.id === editingId
						? {
								...item,
								name: formDraft.name,
								code: formDraft.code,
								domain: formDraft.domain,
								scope: formDraft.scope,
								version: formDraft.version,
								status: formDraft.status,
								owner: formDraft.owner,
								tags: normalizedTags,
								securityLevel: formDraft.securityLevel,
								description: formDraft.description,
								updatedAt: formattedDate,
							}
						: item,
				);
			}
			return [
				{
					id: `std-${Date.now()}`,
					name: formDraft.name,
					code: formDraft.code,
					domain: formDraft.domain,
					scope: formDraft.scope,
					version: formDraft.version,
					status: formDraft.status,
					owner: formDraft.owner,
					tags: normalizedTags,
					securityLevel: formDraft.securityLevel,
					updatedAt: formattedDate,
					description: formDraft.description,
				},
				...prev,
			];
		});
		setFormOpen(false);
		toast.success(editingId ? "标准已更新" : "标准已创建");
	};

	const handleSubmitApproval = () => {
		if (!selectedIds.length) {
			toast.error("请先选择需要提交审批的标准");
			return;
		}
		setStandards((prev) =>
			prev.map((item) =>
				selectedIds.includes(item.id)
					? {
							...item,
							status: item.status === "ACTIVE" ? "IN_REVIEW" : "IN_REVIEW",
						}
					: item,
			),
		);
		toast.info(`已提交 ${selectedIds.length} 个标准至审批流程`);
	};

	const handleArchiveSelected = () => {
		if (!selectedIds.length) {
			toast.error("请选择需要归档的标准");
			return;
		}
		setStandards((prev) =>
			prev.map((item) =>
				selectedIds.includes(item.id)
					? {
							...item,
							status: "DEPRECATED",
							updatedAt: new Date().toISOString().slice(0, 10),
						}
					: item,
			),
		);
		toast.success(`已归档 ${selectedIds.length} 个标准`);
	};

	const handleTemplateDownload = () => {
		toast.success("已下载数据标准导入模板 (xlsx)");
	};

	const handleValidateImport = () => {
		if (!importFileName) {
			toast.error("请先选择需要上传校验的 Excel 文件");
			return;
		}
		const mockErrors: ImportErrorDetail[] = [
			{ row: 8, field: "负责人", message: "负责人未配置到组织" },
			{ row: 12, field: "编码", message: "编码重复：STD-CUST-001" },
			{ row: 15, field: "适用范围", message: "缺失适用范围说明" },
		];
		setImportResult({
			fileName: importFileName,
			success: 18,
			failed: mockErrors.length,
			total: 21,
			errors: mockErrors,
			status: "PARTIAL",
		});
		toast.warning("校验完成，存在部分错误需处理");
	};

	const handleRollbackImport = () => {
		if (!importResult) return;
		toast.info("已生成回滚请求，等待审批执行");
	};

	const handleSecondImport = () => {
		if (!importResult) return;
		toast.success("已触发二次导入，成功写入剩余记录");
		setImportResult((prev) => (prev ? { ...prev, status: "SUCCESS", failed: 0, errors: [] } : prev));
	};

	const handleAdvanceLifecycle = (standardId: string) => {
		const currentIndex = lifecycleState[standardId] ?? 0;
		const nextIndex = Math.min(currentIndex + 1, LIFECYCLE_STEPS.length - 1);
		if (nextIndex === currentIndex) {
			toast.info("已处于生命周期终态");
			return;
		}
		const nextStep = LIFECYCLE_STEPS[nextIndex];
		if (!userPermissions.includes(nextStep.requiredPermission)) {
			toast.error(`缺少 ${nextStep.label} 操作所需权限`);
			return;
		}
		setLifecycleState((prev) => ({ ...prev, [standardId]: nextIndex }));
		toast.success(`生命周期已推进至「${nextStep.label}」`);
	};

	const handleRollbackLifecycle = (standardId: string) => {
		const currentIndex = lifecycleState[standardId] ?? 0;
		if (currentIndex === 0) {
			toast.info("当前已是起始状态");
			return;
		}
		const prevIndex = currentIndex - 1;
		setLifecycleState((prev) => ({ ...prev, [standardId]: prevIndex }));
		toast.message("已回退生命周期，触发密级复核");
	};

	const handleTogglePermission = (permission: string, checked: boolean) => {
		setUserPermissions((prev) => {
			if (checked) {
				return prev.includes(permission) ? prev : [...prev, permission];
			}
			return prev.filter((item) => item !== permission);
		});
	};

	const activeLifecycleId = selectedIds[0] || versionFocusId;
	const lifecycleIndex = lifecycleState[activeLifecycleId] ?? 0;
	const lifecycleStandard = standards.find((item) => item.id === activeLifecycleId);

	const approvalsByTab = useMemo(() => {
		return {
			STARTED: APPROVAL_RECORDS.filter((item) => item.category === "STARTED"),
			TODO: APPROVAL_RECORDS.filter((item) => item.category === "TODO"),
			CC: APPROVAL_RECORDS.filter((item) => item.category === "CC"),
		};
	}, []);

	return (
		<div className="space-y-6">
			<p className="flex items-center gap-2 text-sm font-semibold text-red-600">
				<span aria-hidden className="text-red-500">
					★
				</span>
				此功能涉及密级数据，请注意保密！
			</p>

			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
					<div>
						<CardTitle>数据标准列表</CardTitle>
						<p className="text-sm text-muted-foreground">支持筛选、批量操作以及进入编辑全生命周期</p>
					</div>
					<div className="flex flex-wrap gap-2">
						<Button variant="outline" onClick={handleTemplateDownload}>
							下载模板
						</Button>
						<Button onClick={handleOpenCreate}>新建标准</Button>
						<Button variant="secondary" disabled={!selectedIds.length} onClick={handleSubmitApproval}>
							提交审批
						</Button>
						<Button variant="outline" disabled={!selectedIds.length} onClick={handleArchiveSelected}>
							归档
						</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-4">
					<div className="grid gap-4 md:grid-cols-4">
						<Select
							value={filters.domain}
							onValueChange={(value) => setFilters((prev) => ({ ...prev, domain: value }))}
						>
							<SelectTrigger>
								<SelectValue placeholder="所属域" />
							</SelectTrigger>
							<SelectContent>
								<SelectGroup>
									<SelectLabel>所属域</SelectLabel>
									<SelectItem value="ALL">全部</SelectItem>
									{DOMAIN_OPTIONS.map((option) => (
										<SelectItem key={option.value} value={option.value}>
											{option.label}
										</SelectItem>
									))}
								</SelectGroup>
							</SelectContent>
						</Select>
						<Select
							value={filters.status}
							onValueChange={(value) => setFilters((prev) => ({ ...prev, status: value }))}
						>
							<SelectTrigger>
								<SelectValue placeholder="状态" />
							</SelectTrigger>
							<SelectContent>
								<SelectGroup>
									<SelectLabel>状态</SelectLabel>
									<SelectItem value="ALL">全部</SelectItem>
									{STATUS_OPTIONS.map((status) => (
										<SelectItem key={status.value} value={status.value}>
											{status.label}
										</SelectItem>
									))}
								</SelectGroup>
							</SelectContent>
						</Select>
						<Select value={filters.tag} onValueChange={(value) => setFilters((prev) => ({ ...prev, tag: value }))}>
							<SelectTrigger>
								<SelectValue placeholder="标签" />
							</SelectTrigger>
							<SelectContent>
								<SelectGroup>
									<SelectLabel>标签</SelectLabel>
									<SelectItem value="ALL">全部</SelectItem>
									{TAG_OPTIONS.map((tag) => (
										<SelectItem key={tag} value={tag}>
											{tag}
										</SelectItem>
									))}
								</SelectGroup>
							</SelectContent>
						</Select>
						<Input
							placeholder="搜索名称/编码/负责人"
							value={filters.keyword}
							onChange={(event) => setFilters((prev) => ({ ...prev, keyword: event.target.value }))}
						/>
					</div>

					<div className="overflow-x-auto">
						<table className="w-full min-w-[960px] table-fixed border-collapse text-sm">
							<thead className="bg-muted/40">
								<tr>
									<th className="w-12 px-3 py-3 text-left">
										<Checkbox
											checked={selectedIds.length > 0 && selectedIds.length === filteredStandards.length}
											onCheckedChange={(checked) => handleSelectAll(Boolean(checked))}
										/>
									</th>
									<th className="w-48 px-3 py-3 text-left">名称/编码</th>
									<th className="w-32 px-3 py-3 text-left">所属域</th>
									<th className="w-36 px-3 py-3 text-left">适用范围</th>
									<th className="w-20 px-3 py-3 text-left">版本</th>
									<th className="w-24 px-3 py-3 text-left">状态</th>
									<th className="w-28 px-3 py-3 text-left">负责人</th>
									<th className="w-32 px-3 py-3 text-left">标签</th>
									<th className="w-28 px-3 py-3 text-left">安全密级</th>
									<th className="w-24 px-3 py-3 text-left">更新时间</th>
									<th className="px-3 py-3 text-right">操作</th>
								</tr>
							</thead>
							<tbody>
								{filteredStandards.map((standard) => (
									<tr key={standard.id} className="border-b last:border-none">
										<td className="px-3 py-3">
											<Checkbox
												checked={selectedIds.includes(standard.id)}
												onCheckedChange={() => handleToggleSelect(standard.id)}
											/>
										</td>
										<td className="px-3 py-3">
											<div className="font-medium">{standard.name}</div>
											<div className="text-xs text-muted-foreground">{standard.code}</div>
										</td>
										<td className="px-3 py-3">{standard.domain}</td>
										<td className="truncate px-3 py-3" title={standard.scope}>
											{standard.scope}
										</td>
										<td className="px-3 py-3">{standard.version}</td>
										<td className="px-3 py-3">
											{(() => {
												const statusMeta =
													STATUS_OPTIONS.find((item) => item.value === standard.status) ?? STATUS_OPTIONS[0];
												return <Badge variant={statusMeta.badge}>{statusMeta.label}</Badge>;
											})()}
										</td>
										<td className="px-3 py-3">{standard.owner}</td>
										<td className="px-3 py-3">
											<div className="flex flex-wrap gap-1">
												{standard.tags.map((tag) => (
													<Badge key={tag} variant="outline">
														{tag}
													</Badge>
												))}
											</div>
										</td>
										<td className="px-3 py-3">
											<Badge variant="secondary">{standard.securityLevel}</Badge>
										</td>
										<td className="px-3 py-3">{standard.updatedAt}</td>
										<td className="px-3 py-3 text-right">
											<div className="flex justify-end gap-2">
												<Button size="sm" variant="ghost" onClick={() => handleEdit(standard)}>
													编辑
												</Button>
												<Button size="sm" variant="outline" onClick={() => setVersionFocusId(standard.id)}>
													版本
												</Button>
											</div>
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</CardContent>
			</Card>

			<div className="grid gap-6 lg:grid-cols-2">
				<Card>
					<CardHeader>
						<CardTitle>Excel 导入：模板下载 / 上传校验</CardTitle>
					</CardHeader>
					<CardContent className="space-y-4">
						<div className="flex flex-col gap-3 md:flex-row md:items-center">
							<Button variant="outline" onClick={handleTemplateDownload}>
								下载模板
							</Button>
							<div className="flex flex-1 items-center gap-2">
								<Input
									type="file"
									accept=".xlsx"
									onChange={(event) => {
										const file = event.target.files?.[0];
										setImportFileName(file ? file.name : "");
										setImportResult(null);
									}}
								/>
								<Button onClick={handleValidateImport}>上传校验</Button>
							</div>
						</div>

						{importFileName ? (
							<div className="rounded-md border p-3 text-xs text-muted-foreground">文件：{importFileName}</div>
						) : null}

						{importResult ? (
							<div className="space-y-3">
								<div
									className={clsx("rounded-md border p-3", {
										"border-yellow-400 bg-yellow-50 text-yellow-700": importResult.status === "PARTIAL",
										"border-emerald-400 bg-emerald-50 text-emerald-700": importResult.status === "SUCCESS",
									})}
								>
									<div className="font-medium">校验结果</div>
									<p>
										共 {importResult.total} 条 · 成功 {importResult.success} 条 · 失败 {importResult.failed} 条
									</p>
								</div>
								{importResult.errors.length ? (
									<div className="rounded-md border">
										<div className="border-b p-2 text-sm font-semibold">错误明细预览</div>
										<ScrollArea className="max-h-40">
											<table className="w-full text-xs">
												<thead className="bg-muted/60">
													<tr>
														<th className="px-3 py-2 text-left">行号</th>
														<th className="px-3 py-2 text-left">字段</th>
														<th className="px-3 py-2 text-left">错误信息</th>
													</tr>
												</thead>
												<tbody>
													{importResult.errors.map((error) => (
														<tr key={`${error.row}-${error.field}`} className="border-b last:border-none">
															<td className="px-3 py-2">{error.row}</td>
															<td className="px-3 py-2">{error.field}</td>
															<td className="px-3 py-2">{error.message}</td>
														</tr>
													))}
												</tbody>
											</table>
										</ScrollArea>
									</div>
								) : null}

								<div className="flex flex-wrap gap-2">
									<Button variant="outline" onClick={handleRollbackImport}>
										回滚
									</Button>
									<Button disabled={!importResult || importResult.status === "SUCCESS"} onClick={handleSecondImport}>
										二次导入
									</Button>
								</div>
							</div>
						) : null}
					</CardContent>
				</Card>

				<Card>
					<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
						<div>
							<CardTitle>版本管理</CardTitle>
							<p className="text-sm text-muted-foreground">支持 Diff 对比、历史版本列表和一键回滚</p>
						</div>
						<div className="flex gap-2">
							<Select
								value={versionFocusId}
								onValueChange={(value) => {
									setVersionFocusId(value);
									setBaseVersion(null);
									setTargetVersion(null);
								}}
							>
								<SelectTrigger className="w-48">
									<SelectValue placeholder="选择标准" />
								</SelectTrigger>
								<SelectContent>
									{standards.map((standard) => (
										<SelectItem key={standard.id} value={standard.id}>
											{standard.name}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
							<Button
								variant="outline"
								disabled={versionsForFocus.length <= 1}
								onClick={() => toast.info("回滚请求已提交审批")}
							>
								一键回滚
							</Button>
						</div>
					</CardHeader>
					<CardContent>
						<Tabs defaultValue="diff">
							<TabsList>
								<TabsTrigger value="diff">版本对比</TabsTrigger>
								<TabsTrigger value="history">历史版本列表</TabsTrigger>
							</TabsList>
							<TabsContent value="diff" className="space-y-4 pt-4">
								<div className="grid gap-3 md:grid-cols-2">
									<div>
										<Label>基准版本</Label>
										<Select value={effectiveBaseVersion ?? undefined} onValueChange={(value) => setBaseVersion(value)}>
											<SelectTrigger>
												<SelectValue placeholder="选择基准版本" />
											</SelectTrigger>
											<SelectContent>
												{versionsForFocus
													.slice()
													.reverse()
													.map((version) => (
														<SelectItem key={version.version} value={version.version}>
															{version.version} · {version.releaseDate}
														</SelectItem>
													))}
											</SelectContent>
										</Select>
									</div>
									<div>
										<Label>目标版本</Label>
										<Select
											value={effectiveTargetVersion ?? undefined}
											onValueChange={(value) => setTargetVersion(value)}
										>
											<SelectTrigger>
												<SelectValue placeholder="选择目标版本" />
											</SelectTrigger>
											<SelectContent>
												{versionsForFocus.map((version) => (
													<SelectItem key={version.version} value={version.version}>
														{version.version} · {version.releaseDate}
													</SelectItem>
												))}
											</SelectContent>
										</Select>
									</div>
								</div>
								<ScrollArea className="max-h-64 rounded-md border bg-muted/40 p-3 font-mono text-xs">
									<ul className="space-y-1">
										{diffLines.map((line) => (
											<li
												key={line}
												className={clsx({
													"text-emerald-600": line.startsWith("+"),
													"text-red-600": line.startsWith("-"),
												})}
											>
												{line}
											</li>
										))}
									</ul>
								</ScrollArea>
							</TabsContent>
							<TabsContent value="history" className="pt-4">
								<div className="overflow-x-auto">
									<table className="w-full min-w-[520px] text-sm">
										<thead className="bg-muted/40">
											<tr>
												<th className="px-3 py-2 text-left">版本</th>
												<th className="px-3 py-2 text-left">发布时间</th>
												<th className="px-3 py-2 text-left">作者</th>
												<th className="px-3 py-2 text-left">状态</th>
												<th className="px-3 py-2 text-left">更新说明</th>
											</tr>
										</thead>
										<tbody>
											{versionsForFocus.map((version) => (
												<tr key={version.version} className="border-b last:border-none">
													<td className="px-3 py-2 font-medium">{version.version}</td>
													<td className="px-3 py-2">{version.releaseDate}</td>
													<td className="px-3 py-2">{version.author}</td>
													<td className="px-3 py-2">
														<Badge
															variant={
																version.status === "PUBLISHED"
																	? "default"
																	: version.status === "IN_REVIEW"
																		? "secondary"
																		: "outline"
															}
														>
															{version.status === "PUBLISHED"
																? "已发布"
																: version.status === "IN_REVIEW"
																	? "审批中"
																	: "已归档"}
														</Badge>
													</td>
													<td className="px-3 py-2 text-sm text-muted-foreground">{version.notes}</td>
												</tr>
											))}
										</tbody>
									</table>
								</div>
							</TabsContent>
						</Tabs>
					</CardContent>
				</Card>
			</div>

			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
					<div>
						<CardTitle>生命周期状态线</CardTitle>
						<p className="text-sm text-muted-foreground">
							支持创建/使用/共享/归档/销毁全链路操作，并对关键操作做权限校验
						</p>
					</div>
					<div className="flex items-center gap-2 text-xs text-muted-foreground">
						<span>权限模拟：</span>
						{LIFECYCLE_STEPS.map((step) => (
							<label key={step.key} className="flex items-center gap-1">
								<Switch
									checked={userPermissions.includes(step.requiredPermission)}
									onCheckedChange={(checked) => handleTogglePermission(step.requiredPermission, checked)}
								/>
								{step.label}
							</label>
						))}
					</div>
				</CardHeader>
				<CardContent className="space-y-4">
					{lifecycleStandard ? (
						<div className="rounded-md border p-4">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<div>
									<div className="text-sm text-muted-foreground">当前标准</div>
									<div className="font-semibold">{lifecycleStandard.name}</div>
								</div>
								<div className="flex gap-2">
									<Button size="sm" variant="ghost" onClick={() => handleRollbackLifecycle(lifecycleStandard.id)}>
										回退
									</Button>
									<Button size="sm" onClick={() => handleAdvanceLifecycle(lifecycleStandard.id)}>
										推进下一状态
									</Button>
								</div>
							</div>
							<Separator className="my-4" />
							<div className="grid gap-4 md:grid-cols-5">
								{LIFECYCLE_STEPS.map((step, index) => (
									<div key={step.key} className="flex flex-col gap-2">
										<div
											className={clsx(
												"flex h-10 items-center justify-center rounded-md border text-sm font-medium",
												index < lifecycleIndex
													? "border-emerald-500 bg-emerald-50 text-emerald-700"
													: index === lifecycleIndex
														? "border-blue-500 bg-blue-50 text-blue-700"
														: "border-muted bg-muted/30 text-muted-foreground",
											)}
										>
											{step.label}
										</div>
										<p className="text-xs text-muted-foreground">{step.description}</p>
										{index === lifecycleIndex ? (
											<div className="rounded-md border bg-background p-2 text-xs">
												<ul className="space-y-1">
													<li>权限：{userPermissions.includes(step.requiredPermission) ? "已通过" : "缺失"}</li>
													<li>密级校验：{lifecycleStandard.securityLevel}</li>
													<li>最近操作：{lifecycleStandard.updatedAt}</li>
												</ul>
											</div>
										) : null}
									</div>
								))}
							</div>
						</div>
					) : (
						<p className="text-sm text-muted-foreground">请在列表中选择标准查看生命周期详情</p>
					)}
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>审批中心</CardTitle>
					<p className="text-sm text-muted-foreground">我发起 / 我处理 / 抄送的审批单，支持节点历史与审批意见查看</p>
				</CardHeader>
				<CardContent>
					<Tabs defaultValue="STARTED">
						<TabsList>
							<TabsTrigger value="STARTED">我发起</TabsTrigger>
							<TabsTrigger value="TODO">我处理</TabsTrigger>
							<TabsTrigger value="CC">抄送</TabsTrigger>
						</TabsList>
						{(Object.keys(approvalsByTab) as Array<keyof typeof approvalsByTab>).map((tabKey) => (
							<TabsContent key={tabKey} value={tabKey} className="pt-4">
								<div className="overflow-x-auto">
									<table className="w-full min-w-[640px] text-sm">
										<thead className="bg-muted/40">
											<tr>
												<th className="px-3 py-2 text-left">标准</th>
												<th className="px-3 py-2 text-left">动作</th>
												<th className="px-3 py-2 text-left">当前节点</th>
												<th className="px-3 py-2 text-left">最新进展</th>
												<th className="px-3 py-2 text-left">状态</th>
												<th className="px-3 py-2 text-right">操作</th>
											</tr>
										</thead>
										<tbody>
											{approvalsByTab[tabKey].map((record) => (
												<tr key={record.id} className="border-b last:border-none">
													<td className="px-3 py-2 font-medium">{record.standardName}</td>
													<td className="px-3 py-2">{record.action}</td>
													<td className="px-3 py-2">{record.currentNode}</td>
													<td className="px-3 py-2 text-sm text-muted-foreground">{record.updatedAt}</td>
													<td className="px-3 py-2">
														<Badge
															variant={
																record.status === "APPROVED"
																	? "default"
																	: record.status === "PENDING"
																		? "secondary"
																		: "destructive"
															}
														>
															{record.status === "APPROVED"
																? "已通过"
																: record.status === "PENDING"
																	? "待处理"
																	: "已拒绝"}
														</Badge>
													</td>
													<td className="px-3 py-2 text-right">
														<Button size="sm" variant="outline" onClick={() => setApprovalDetail(record)}>
															查看节点历史
														</Button>
													</td>
												</tr>
											))}
										</tbody>
									</table>
								</div>
							</TabsContent>
						))}
					</Tabs>
				</CardContent>
			</Card>

			<Dialog open={formOpen} onOpenChange={setFormOpen}>
				<DialogContent className="max-w-2xl">
					<DialogHeader>
						<DialogTitle>{editingId ? "编辑标准" : "新建标准"}</DialogTitle>
						<DialogDescription>维护数据标准基础信息，提交后进入审批流程</DialogDescription>
					</DialogHeader>
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label>标准名称</Label>
							<Input
								value={formDraft.name}
								onChange={(event) => setFormDraft((prev) => ({ ...prev, name: event.target.value }))}
							/>
						</div>
						<div className="space-y-2">
							<Label>编码</Label>
							<Input
								value={formDraft.code}
								onChange={(event) => setFormDraft((prev) => ({ ...prev, code: event.target.value }))}
							/>
						</div>
						<div className="space-y-2">
							<Label>所属域</Label>
							<Select
								value={formDraft.domain}
								onValueChange={(value) => setFormDraft((prev) => ({ ...prev, domain: value }))}
							>
								<SelectTrigger>
									<SelectValue placeholder="选择域" />
								</SelectTrigger>
								<SelectContent>
									{DOMAIN_OPTIONS.map((option) => (
										<SelectItem key={option.value} value={option.value}>
											{option.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>负责人</Label>
							<Select
								value={formDraft.owner}
								onValueChange={(value) => setFormDraft((prev) => ({ ...prev, owner: value }))}
							>
								<SelectTrigger>
									<SelectValue placeholder="选择负责人" />
								</SelectTrigger>
								<SelectContent>
									{RESPONSIBLES.map((name) => (
										<SelectItem key={name} value={name}>
											{name}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>版本</Label>
							<Input
								value={formDraft.version}
								onChange={(event) => setFormDraft((prev) => ({ ...prev, version: event.target.value }))}
							/>
						</div>
						<div className="space-y-2">
							<Label>状态</Label>
							<Select
								value={formDraft.status}
								onValueChange={(value) => setFormDraft((prev) => ({ ...prev, status: value as StandardStatus }))}
							>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									{STATUS_OPTIONS.map((status) => (
										<SelectItem key={status.value} value={status.value}>
											{status.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="md:col-span-2 space-y-2">
							<Label>适用范围</Label>
							<Textarea
								value={formDraft.scope}
								onChange={(event) => setFormDraft((prev) => ({ ...prev, scope: event.target.value }))}
								rows={3}
							/>
						</div>
						<div className="space-y-2">
							<Label>标签</Label>
							<Input
								placeholder="以逗号分隔，如：指标,共享"
								value={formDraft.tags}
								onChange={(event) => setFormDraft((prev) => ({ ...prev, tags: event.target.value }))}
							/>
						</div>
						<div className="space-y-2">
							<Label>安全密级</Label>
							<Select
								value={formDraft.securityLevel}
								onValueChange={(value) => setFormDraft((prev) => ({ ...prev, securityLevel: value }))}
							>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									{["公开", "内部", "秘密", "机密"].map((level) => (
										<SelectItem key={level} value={level}>
											{level}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="md:col-span-2 space-y-2">
							<Label>备注</Label>
							<Textarea
								value={formDraft.description}
								onChange={(event) => setFormDraft((prev) => ({ ...prev, description: event.target.value }))}
								rows={3}
							/>
						</div>
					</div>
					<DialogFooter>
						<Button variant="outline" onClick={() => setFormOpen(false)}>
							取消
						</Button>
						<Button onClick={handleSubmitForm}>{editingId ? "保存" : "提交审批"}</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>

			<Drawer open={Boolean(approvalDetail)} onOpenChange={(open) => !open && setApprovalDetail(null)}>
				<DrawerContent className="max-h-[80vh]">
					<DrawerHeader>
						<DrawerTitle>节点历史 · {approvalDetail?.standardName}</DrawerTitle>
					</DrawerHeader>
					<ScrollArea className="px-6 pb-6">
						{approvalDetail ? (
							<ul className="space-y-4">
								{approvalDetail.nodes.map((node) => (
									<li key={`${approvalDetail.id}-${node.node}`} className="rounded-md border p-4">
										<div className="flex items-center justify-between">
											<div>
												<div className="font-semibold">{node.node}</div>
												<div className="text-sm text-muted-foreground">处理人：{node.assignee}</div>
											</div>
											<Badge
												variant={
													node.status === "APPROVED"
														? "default"
														: node.status === "PENDING"
															? "secondary"
															: "destructive"
												}
											>
												{node.status === "APPROVED" ? "已通过" : node.status === "PENDING" ? "待处理" : "已拒绝"}
											</Badge>
										</div>
										{node.comment ? <p className="mt-2 text-sm">意见：{node.comment}</p> : null}
										{node.finishedAt ? (
											<p className="mt-1 text-xs text-muted-foreground">完成时间：{node.finishedAt}</p>
										) : null}
									</li>
								))}
							</ul>
						) : null}
					</ScrollArea>
				</DrawerContent>
			</Drawer>
		</div>
	);
}
