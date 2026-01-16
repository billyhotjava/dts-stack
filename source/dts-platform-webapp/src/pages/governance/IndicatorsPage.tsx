import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	archiveIndicator,
	createIndicator,
	deleteIndicator,
	listIndicators,
	publishIndicator,
	updateIndicator,
} from "@/api/platformApi";
import deptService, { type DeptDto } from "@/api/services/deptService";
import userDirectoryService, { type UserDirectoryEntry } from "@/api/services/userDirectoryService";
import { useCatalogDomainOptions } from "@/hooks/useCatalogDomainOptions";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { useUserInfo } from "@/store/userStore";

type DataLevel = "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_CONFIDENTIAL" | "DATA_SECRET";
type Status = "DRAFT" | "PUBLISHED" | "DEPRECATED";

function formatOwnerValue(entry: UserDirectoryEntry): string {
	const display = String(entry.displayName || entry.fullName || "").trim();
	const username = String(entry.username || "").trim();
	if (!display && !username) return "";
	if (!display) return username;
	if (!username) return display;
	if (display.toLowerCase() === username.toLowerCase()) return display;
	return `${display} (${username})`;
}

type IndicatorRow = {
	id: string;
	code: string;
	name: string;
	category?: string | null;
	definition?: string | null;
	expressionSql?: string | null;
	datasetId?: string | null;
	owner?: string | null;
	ownerDept?: string | null;
	dataLevel?: DataLevel | string | null;
	status?: Status | string | null;
	version?: string | null;
	versionNotes?: string | null;
	tags?: string | null;
	lastModifiedDate?: string | null;
};

type FormState = {
	id?: string;
	code: string;
	name: string;
	category: string;
	owner: string;
	ownerDept: string;
	dataLevel: DataLevel;
	status: Status;
	version: string;
	versionNotes: string;
	tags: string;
	definition: string;
	expressionSql: string;
	datasetId: string;
};

const PAGE_SIZE = 10;

const DATA_LEVEL_OPTIONS: Array<{ value: DataLevel; label: string }> = [
	{ value: "DATA_PUBLIC", label: "公开" },
	{ value: "DATA_INTERNAL", label: "内部" },
	{ value: "DATA_CONFIDENTIAL", label: "秘密" },
	{ value: "DATA_SECRET", label: "机密" },
];

const STATUS_OPTIONS: Array<{ value: Status; label: string }> = [
	{ value: "DRAFT", label: "草稿" },
	{ value: "PUBLISHED", label: "已发布" },
	{ value: "DEPRECATED", label: "已废止" },
];

const FALLBACK_DEPT_OPTIONS: DeptDto[] = [
	{ code: "INFO", nameZh: "信息管理部" },
	{ code: "PROJECT", nameZh: "科研项目部" },
	{ code: "PLANNING", nameZh: "规划发展部" },
	{ code: "FINANCE", nameZh: "财务管理部" },
	{ code: "SECURITY", nameZh: "安全保密部" },
	{ code: "GENERAL", nameZh: "综合管理部" },
];

const DEFAULT_FORM: FormState = {
	code: "",
	name: "",
	category: "",
	owner: "",
	ownerDept: "",
	dataLevel: "DATA_INTERNAL",
	status: "DRAFT",
	version: "v1",
	versionNotes: "",
	tags: "",
	definition: "",
	expressionSql: "",
	datasetId: "",
};

export default function IndicatorsPage() {
	const [loading, setLoading] = useState(false);
	const [items, setItems] = useState<IndicatorRow[]>([]);
	const [total, setTotal] = useState(0);
	const [page, setPage] = useState(0);
	const [keyword, setKeyword] = useState("");
	const [status, setStatus] = useState<Status | "ALL">("ALL");

	const [deptOptions, setDeptOptions] = useState<DeptDto[]>(FALLBACK_DEPT_OPTIONS);
	const [deptLoading, setDeptLoading] = useState(false);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [form, setForm] = useState<FormState>(DEFAULT_FORM);
	const [saving, setSaving] = useState(false);

	const { options: domainOptions, loading: domainLoading } = useCatalogDomainOptions();
	const domainNameSet = useMemo(() => new Set(domainOptions.map((opt) => opt.name)), [domainOptions]);
	const selectedDomainName = useMemo(() => {
		return domainNameSet.has(form.category) ? form.category : "__UNSET__";
	}, [domainNameSet, form.category]);

	const [ownerSearch, setOwnerSearch] = useState("");
	const [ownerLoading, setOwnerLoading] = useState(false);
	const [ownerCandidates, setOwnerCandidates] = useState<UserDirectoryEntry[]>([]);

	const userInfo = useUserInfo() as any;
	const roles = useMemo(() => {
		const raw = userInfo?.roles;
		if (!Array.isArray(raw)) return [];
		return raw.map((role: any) => String(role ?? "").toUpperCase()).filter(Boolean);
	}, [userInfo]);
	const roleSet = useMemo(() => new Set(roles), [roles]);
	const canSelectAnyDept = useMemo(() => {
		return (
			roleSet.has("ROLE_OP_ADMIN") ||
			roleSet.has("ROLE_ADMIN") ||
			roleSet.has("ROLE_INST_DATA_OWNER") ||
			roleSet.has("ROLE_INST_DATA_DEV") ||
			roleSet.has("ROLE_INST_LEADER")
		);
	}, [roleSet]);
	const userDeptCode = useMemo(() => {
		const attrs = ((userInfo as any)?.attributes || {}) as Record<string, unknown>;
		const pick = (value: unknown): string | undefined => {
			if (Array.isArray(value)) {
				const first = String(value[0] ?? "").trim();
				return first || undefined;
			}
			if (typeof value === "string") {
				const trimmed = value.trim();
				return trimmed || undefined;
			}
			return undefined;
		};
		return (
			pick(attrs.dept_code) ||
			pick(attrs.deptCode) ||
			pick(attrs.department) ||
			pick((userInfo as any)?.dept_code) ||
			pick((userInfo as any)?.deptCode) ||
			pick((userInfo as any)?.department)
		);
	}, [userInfo]);
	const enforcedDeptCode = useMemo(() => {
		if (canSelectAnyDept) return undefined;
		return userDeptCode || undefined;
	}, [canSelectAnyDept, userDeptCode]);

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const params: Record<string, any> = { page, size: PAGE_SIZE };
			if (keyword.trim()) params.keyword = keyword.trim();
			if (status !== "ALL") params.status = status;
			const resp: any = await listIndicators(params);
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setItems(
				content.map((it: any) => ({
					id: String(it?.id ?? ""),
					code: String(it?.code ?? ""),
					name: String(it?.name ?? ""),
					category: it?.category ?? "",
					definition: it?.definition ?? "",
					expressionSql: it?.expressionSql ?? "",
					datasetId: it?.datasetId ?? "",
					owner: it?.owner ?? "",
					ownerDept: it?.ownerDept ?? "",
					dataLevel: it?.dataLevel ?? "",
					status: it?.status ?? "",
					version: it?.version ?? "",
					versionNotes: it?.versionNotes ?? "",
					tags: it?.tags ?? "",
					lastModifiedDate: it?.lastModifiedDate ?? it?.last_modified_date ?? "",
				})),
			);
			setTotal(Number(resp?.total ?? 0));
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message ?? "加载指标列表失败");
		} finally {
			setLoading(false);
		}
	}, [keyword, page, status]);

	useEffect(() => {
		void load();
	}, [load]);

	useEffect(() => {
		let mounted = true;
		setDeptLoading(true);
		deptService
			.listDepartments()
			.then((list) => {
				if (!mounted) return;
				if (Array.isArray(list) && list.length) {
					setDeptOptions(
						list.map((d) => ({
							code: String(d.code),
							nameZh: d.nameZh,
							nameEn: d.nameEn,
							parentId: d.parentId ?? null,
						})),
					);
				}
			})
			.catch((e) => console.warn("加载部门失败", e))
			.finally(() => {
				if (mounted) setDeptLoading(false);
			});
		return () => {
			mounted = false;
		};
	}, []);

	useEffect(() => {
		if (!canSelectAnyDept && enforcedDeptCode) {
			setForm((prev) => ({ ...prev, ownerDept: enforcedDeptCode }));
		}
	}, [canSelectAnyDept, enforcedDeptCode]);

	const openCreate = () => {
		setForm({ ...DEFAULT_FORM, ownerDept: enforcedDeptCode || "" });
		setDialogOpen(true);
	};

	const openEdit = (row: IndicatorRow) => {
		setForm({
			id: row.id,
			code: row.code,
			name: row.name,
			category: String(row.category ?? ""),
			owner: String(row.owner ?? ""),
			ownerDept: String(row.ownerDept ?? enforcedDeptCode ?? ""),
			dataLevel: (row.dataLevel as DataLevel) || "DATA_INTERNAL",
			status: (row.status as Status) || "DRAFT",
			version: String(row.version ?? "v1"),
			versionNotes: String(row.versionNotes ?? ""),
			tags: String(row.tags ?? ""),
			definition: String(row.definition ?? ""),
			expressionSql: String(row.expressionSql ?? ""),
			datasetId: String(row.datasetId ?? ""),
		});
		setDialogOpen(true);
	};

	useEffect(() => {
		if (!dialogOpen) return;
		let alive = true;
		const keyword = ownerSearch.trim();
		setOwnerLoading(true);
		const timer = setTimeout(() => {
			userDirectoryService
				.searchUsers(keyword)
				.then((list) => {
					if (!alive) return;
					setOwnerCandidates(Array.isArray(list) ? list : []);
				})
				.catch(() => {
					if (!alive) return;
					setOwnerCandidates([]);
				})
				.finally(() => {
					if (!alive) return;
					setOwnerLoading(false);
				});
		}, 250);
		return () => {
			alive = false;
			clearTimeout(timer);
		};
	}, [dialogOpen, ownerSearch]);

	const submit = async () => {
		if (!form.code.trim() || !form.name.trim()) {
			toast.error("请填写指标编码和名称");
			return;
		}
		setSaving(true);
		try {
			const payload = {
				code: form.code.trim(),
				name: form.name.trim(),
				category: form.category.trim() || null,
				owner: form.owner.trim() || null,
				ownerDept: form.ownerDept.trim() || null,
				dataLevel: form.dataLevel,
				status: form.status,
				version: form.version.trim() || "v1",
				versionNotes: form.versionNotes.trim() || null,
				tags: form.tags.trim() || null,
				definition: form.definition.trim() || null,
				expressionSql: form.expressionSql.trim() || null,
				datasetId: form.datasetId.trim() || null,
			};
			if (form.id) {
				await updateIndicator(form.id, payload);
				toast.success("已更新");
			} else {
				await createIndicator(payload);
				toast.success("已创建");
			}
			setDialogOpen(false);
			await load();
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message ?? "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
	const pageLabel = `${page + 1}/${totalPages}`;

	return (
		<div className="flex flex-col gap-6">
			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
					<CardTitle>指标字典</CardTitle>
					<div className="flex flex-col gap-2 md:flex-row md:items-center">
						<Input
							value={keyword}
							onChange={(e) => {
								setKeyword(e.target.value);
								setPage(0);
							}}
							placeholder="搜索编码/名称/分类"
							className="w-full md:w-64"
						/>
						<Select
							value={status}
							onValueChange={(v) => {
								setStatus(v as any);
								setPage(0);
							}}
						>
							<SelectTrigger className="w-full md:w-40">
								<SelectValue placeholder="状态" />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="ALL">全部状态</SelectItem>
								{STATUS_OPTIONS.map((opt) => (
									<SelectItem key={opt.value} value={opt.value}>
										{opt.label}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						<Button onClick={openCreate}>新建指标</Button>
					</div>
				</CardHeader>
				<CardContent>
					<div className="overflow-x-auto">
						<table className="min-w-full text-sm">
							<thead className="text-left text-muted-foreground border-b">
								<tr>
									<th className="py-2 pr-4 font-medium">编码</th>
									<th className="py-2 pr-4 font-medium">名称</th>
									<th className="py-2 pr-4 font-medium">主题域</th>
									<th className="py-2 pr-4 font-medium">负责人</th>
									<th className="py-2 pr-4 font-medium">部门</th>
									<th className="py-2 pr-4 font-medium">数据集</th>
									<th className="py-2 pr-4 font-medium">密级</th>
									<th className="py-2 pr-4 font-medium">状态</th>
									<th className="py-2 pr-0 font-medium text-right">操作</th>
								</tr>
							</thead>
							<tbody>
								{items.map((row) => (
									<tr key={row.id} className="border-b last:border-none">
										<td className="py-2 pr-4 font-mono">{row.code}</td>
										<td className="py-2 pr-4 font-medium">{row.name}</td>
										<td className="py-2 pr-4 text-muted-foreground">{row.category || "-"}</td>
										<td className="py-2 pr-4 text-muted-foreground">{row.owner || "-"}</td>
										<td className="py-2 pr-4 text-muted-foreground">{row.ownerDept || "-"}</td>
										<td className="py-2 pr-4 text-muted-foreground">
											{row.datasetId ? <Badge variant="outline">已绑定</Badge> : <Badge variant="secondary">未绑定</Badge>}
										</td>
										<td className="py-2 pr-4">
											<Badge variant="outline">{formatDataLevel(row.dataLevel)}</Badge>
										</td>
										<td className="py-2 pr-4">
											<Badge variant={row.status === "PUBLISHED" ? "default" : row.status === "DEPRECATED" ? "secondary" : "outline"}>
												{formatStatus(row.status)}
											</Badge>
										</td>
										<td className="py-2 pr-0">
											<div className="flex justify-end gap-2">
												<Button size="sm" variant="outline" onClick={() => openEdit(row)}>
													编辑
												</Button>
												<Button
													size="sm"
													variant="secondary"
													onClick={async () => {
														try {
															await publishIndicator(row.id);
															toast.success("已发布");
															await load();
														} catch (e: any) {
															toast.error(e?.message ?? "发布失败");
														}
													}}
												>
													发布
												</Button>
												<Button
													size="sm"
													variant="secondary"
													onClick={async () => {
														try {
															await archiveIndicator(row.id);
															toast.success("已废止");
															await load();
														} catch (e: any) {
															toast.error(e?.message ?? "废止失败");
														}
													}}
												>
													废止
												</Button>
												<Button
													size="sm"
													variant="destructive"
													onClick={async () => {
														if (!confirm(`确认删除指标：${row.name}？`)) return;
														try {
															await deleteIndicator(row.id);
															toast.success("已删除");
															await load();
														} catch (e: any) {
															toast.error(e?.message ?? "删除失败");
														}
													}}
												>
													删除
												</Button>
											</div>
										</td>
									</tr>
								))}
								{items.length === 0 ? (
									<tr>
										<td colSpan={9} className="py-10 text-center text-muted-foreground">
											{loading ? "加载中..." : "暂无数据"}
										</td>
									</tr>
								) : null}
							</tbody>
						</table>
					</div>

					<div className="mt-4 flex items-center justify-between text-sm">
						<div className="text-muted-foreground">共 {total} 条</div>
						<div className="flex items-center gap-2">
							<Button size="sm" variant="outline" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
								上一页
							</Button>
							<span className="text-muted-foreground">{pageLabel}</span>
							<Button
								size="sm"
								variant="outline"
								disabled={page + 1 >= totalPages}
								onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
							>
								下一页
							</Button>
						</div>
					</div>
				</CardContent>
			</Card>

			<Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
				<DialogContent className="max-w-3xl">
					<DialogHeader>
						<DialogTitle>{form.id ? "编辑指标" : "新建指标"}</DialogTitle>
					</DialogHeader>
					<ScrollArea className="max-h-[70vh] pr-2">
						<div className="grid gap-4 md:grid-cols-2">
							<div className="space-y-2">
								<Label>指标编码</Label>
								<Input value={form.code} onChange={(e) => setForm((p) => ({ ...p, code: e.target.value }))} />
							</div>
							<div className="space-y-2">
								<Label>指标名称</Label>
								<Input value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} />
							</div>
							<div className="space-y-2">
								<Label>主题域（可手工填）</Label>
								<Input value={form.category} onChange={(e) => setForm((p) => ({ ...p, category: e.target.value }))} />
								<Select
									value={selectedDomainName}
									onValueChange={(v) => {
										if (v === "__UNSET__") return;
										setForm((p) => ({ ...p, category: v }));
									}}
								>
									<SelectTrigger>
										<SelectValue placeholder={domainLoading ? "加载主题域中..." : "从主题域树选择"} />
									</SelectTrigger>
									<SelectContent>
										<SelectItem value="__UNSET__">不选择</SelectItem>
										{domainOptions.map((opt) => (
											<SelectItem key={opt.key} value={opt.name}>
												{opt.label}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</div>
							<div className="space-y-2">
								<Label>负责人（可手工填）</Label>
								<Input value={form.owner} onChange={(e) => setForm((p) => ({ ...p, owner: e.target.value }))} />
								<div className="grid grid-cols-1 gap-2">
									<Input
										value={ownerSearch}
										onChange={(e) => setOwnerSearch(e.target.value)}
										placeholder="从通讯录搜索用户（姓名/用户名）"
									/>
											<Select
												value="__UNSET__"
												onValueChange={(v) => {
													const picked = ownerCandidates.find((u) => u.id === v);
													if (!picked) return;
													setForm((p) => ({ ...p, owner: formatOwnerValue(picked) }));
												}}
											>
										<SelectTrigger>
											<SelectValue placeholder={ownerLoading ? "加载用户中..." : "选择用户写入负责人"} />
										</SelectTrigger>
										<SelectContent>
											<SelectItem value="__UNSET__">不选择</SelectItem>
											{ownerCandidates.slice(0, 50).map((u) => (
												<SelectItem key={u.id} value={u.id}>
													{u.displayName || u.username}（{u.username}）
												</SelectItem>
											))}
										</SelectContent>
									</Select>
								</div>
							</div>
							<div className="space-y-2">
								<Label>所属部门</Label>
								<Select
									value={form.ownerDept || "__UNSET__"}
									onValueChange={(v) => setForm((p) => ({ ...p, ownerDept: v === "__UNSET__" ? "" : v }))}
									disabled={!canSelectAnyDept && !!enforcedDeptCode}
								>
									<SelectTrigger>
										<SelectValue placeholder={deptLoading ? "加载中..." : "选择部门"} />
									</SelectTrigger>
									<SelectContent>
										<SelectItem value="__UNSET__">默认（当前部门）</SelectItem>
										{deptOptions.map((d) => (
											<SelectItem key={d.code} value={String(d.code)}>
												{d.nameZh || d.code}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</div>
							<div className="space-y-2">
								<Label>数据密级</Label>
								<Select value={form.dataLevel} onValueChange={(v) => setForm((p) => ({ ...p, dataLevel: v as DataLevel }))}>
									<SelectTrigger>
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										{DATA_LEVEL_OPTIONS.map((opt) => (
											<SelectItem key={opt.value} value={opt.value}>
												{opt.label}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</div>
							<div className="space-y-2">
								<Label>状态</Label>
								<Select value={form.status} onValueChange={(v) => setForm((p) => ({ ...p, status: v as Status }))}>
									<SelectTrigger>
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										{STATUS_OPTIONS.map((opt) => (
											<SelectItem key={opt.value} value={opt.value}>
												{opt.label}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</div>
							<div className="space-y-2">
								<Label>版本</Label>
								<Input value={form.version} onChange={(e) => setForm((p) => ({ ...p, version: e.target.value }))} />
							</div>
							<div className="space-y-2 md:col-span-2">
								<Label>标签（逗号分隔）</Label>
								<Input value={form.tags} onChange={(e) => setForm((p) => ({ ...p, tags: e.target.value }))} />
							</div>
							<div className="space-y-2 md:col-span-2">
								<Label>口径说明</Label>
								<Textarea
									value={form.definition}
									onChange={(e) => setForm((p) => ({ ...p, definition: e.target.value }))}
									rows={4}
								/>
							</div>
							<div className="space-y-2 md:col-span-2">
								<Label>计算 SQL（可选）</Label>
								<Textarea
									value={form.expressionSql}
									onChange={(e) => setForm((p) => ({ ...p, expressionSql: e.target.value }))}
									placeholder="用于后续对接计算任务/下钻查询"
									rows={6}
									className="font-mono"
								/>
							</div>
							<div className="space-y-2 md:col-span-2">
								<Label>版本说明</Label>
								<Textarea
									value={form.versionNotes}
									onChange={(e) => setForm((p) => ({ ...p, versionNotes: e.target.value }))}
									rows={3}
								/>
							</div>
						</div>
					</ScrollArea>
					<DialogFooter className="gap-2">
						<Button variant="outline" onClick={() => setDialogOpen(false)}>
							取消
						</Button>
						<Button onClick={submit} disabled={saving}>
							{saving ? "保存中..." : "保存"}
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}

function formatStatus(status: unknown) {
	const s = String(status ?? "").trim().toUpperCase();
	if (s === "PUBLISHED") return "已发布";
	if (s === "DEPRECATED") return "已废止";
	if (s === "DRAFT") return "草稿";
	return s || "-";
}

function formatDataLevel(level: unknown) {
	const s = String(level ?? "").trim().toUpperCase();
	if (s === "DATA_PUBLIC") return "公开";
	if (s === "DATA_INTERNAL") return "内部";
	if (s === "DATA_CONFIDENTIAL") return "秘密";
	if (s === "DATA_SECRET") return "机密";
	return s || "-";
}
