import { Table as AntTable } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	archiveModelingPlan,
	createModelingPlan,
	getModelingPlan,
	deleteModelingPlan,
	listModelingPlans,
	publishModelingPlan,
	restoreModelingPlan,
	updateModelingPlan,
} from "@/api/platformApi";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { formatDateTime } from "@/utils/format";
import { useCatalogDomainOptions } from "@/hooks/useCatalogDomainOptions";

const DOMAIN_SELECT_UNSET = "__UNSET__";
const DOMAIN_SELECT_CUSTOM = "__CUSTOM__";

type PlanRow = {
	id: string;
	name: string;
	domain?: string | null;
	status?: string | null;
	version?: string | null;
	tags?: string | null;
	content?: string | null;
	owner?: string | null;
	ownerDept?: string | null;
	lastModifiedDate?: string | null;
};

type PlanForm = {
	id?: string;
	name: string;
	domain: string;
	version: string;
	owner: string;
	tags: string;
	content: string;
};

const DEFAULT_FORM: PlanForm = {
	name: "",
	domain: "",
	version: "",
	owner: "",
	tags: "",
	content: "",
};

export default function DataPlanningPage() {
	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [statusFilter, setStatusFilter] = useState<"ALL" | "DRAFT" | "PUBLISHED" | "ARCHIVED">("ALL");
	const [items, setItems] = useState<PlanRow[]>([]);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [form, setForm] = useState<PlanForm>(DEFAULT_FORM);

	const {
		options: domainTreeOptions,
		keyByName: domainKeyByName,
		nameByKey: domainNameByKey,
		labelByKey: domainLabelByKey,
	} = useCatalogDomainOptions();

	const domainOptions = useMemo(() => {
		const list = [...domainTreeOptions];
		list.sort((a, b) => a.label.localeCompare(b.label, "zh-Hans-CN"));
		return list;
	}, [domainTreeOptions]);

	const renderDomainLabel = useCallback(
		(domain?: string | null) => {
			const raw = String(domain ?? "").trim();
			if (!raw) return "-";
			const key = domainKeyByName[raw];
			return key ? domainLabelByKey[key] ?? raw : raw;
		},
		[domainKeyByName, domainLabelByKey],
	);

	const fetchList = useCallback(async () => {
		setLoading(true);
		try {
			const list: any[] = await listModelingPlans({
				keyword: keyword.trim() || undefined,
				status: statusFilter === "ALL" ? undefined : statusFilter,
			});
			setItems(
				(Array.isArray(list) ? list : []).map((p: any) => ({
					id: String(p?.id ?? ""),
					name: String(p?.name ?? ""),
					domain: p?.domain ?? null,
					status: p?.status ?? null,
					version: p?.version ?? null,
					tags: p?.tags ?? null,
					content: p?.content ?? null,
					owner: p?.owner ?? null,
					ownerDept: p?.ownerDept ?? p?.owner_dept ?? null,
					lastModifiedDate: p?.lastModifiedDate ?? p?.last_modified_date ?? null,
				})),
			);
		} catch (e: any) {
			toast.error(e?.message || "加载数据规划失败");
		} finally {
			setLoading(false);
		}
	}, [keyword, statusFilter]);

	useEffect(() => {
		void fetchList();
	}, [fetchList]);

	const filtered = useMemo(() => {
		const k = keyword.trim().toLowerCase();
		if (!k) return items;
		return items.filter(
			(it) =>
				(it.name || "").toLowerCase().includes(k) ||
				String(it.domain || "").toLowerCase().includes(k) ||
				String(it.owner || "").toLowerCase().includes(k),
		);
	}, [items, keyword]);

	const domainFormSelectValue = useMemo(() => {
		const raw = String(form.domain ?? "").trim();
		if (!raw) return DOMAIN_SELECT_UNSET;
		const key = domainKeyByName[raw];
		return key ?? DOMAIN_SELECT_CUSTOM;
	}, [domainKeyByName, form.domain]);

	const openCreate = () => {
		setForm(DEFAULT_FORM);
		setDialogOpen(true);
	};

	const openEdit = useCallback(async (row: PlanRow) => {
		try {
			const full: any = await getModelingPlan(row.id);
			setForm({
				id: String(full?.id ?? row.id),
				name: String(full?.name ?? row.name ?? ""),
				domain: String(full?.domain ?? row.domain ?? ""),
				version: String(full?.version ?? row.version ?? ""),
				owner: String(full?.owner ?? row.owner ?? ""),
				tags: String(full?.tags ?? row.tags ?? ""),
				content: String(full?.content ?? row.content ?? ""),
			});
			setDialogOpen(true);
		} catch (e: any) {
			toast.error(e?.message || "加载详情失败");
		}
	}, []);

	const save = useCallback(async () => {
		if (!form.name.trim()) {
			toast.error("请输入名称");
			return;
		}
		setSaving(true);
		try {
			const payload: any = {
				name: form.name.trim(),
				domain: form.domain.trim() || null,
				version: form.version.trim() || null,
				owner: form.owner.trim() || null,
				tags: form.tags.trim() || null,
				content: form.content.trim() || null,
			};
			if (form.id) {
				await updateModelingPlan(form.id, payload);
			} else {
				await createModelingPlan(payload);
			}
			toast.success("保存成功");
			setDialogOpen(false);
			await fetchList();
		} catch (e: any) {
			toast.error(e?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	}, [form, fetchList]);

	const publish = useCallback(
		async (row: PlanRow) => {
			if (!row?.id) return;
			if (!window.confirm("确认发布该数据规划？")) return;
			try {
				await publishModelingPlan(row.id, { version: row.version ?? undefined });
				toast.success("已发布");
				await fetchList();
			} catch (e: any) {
				toast.error(e?.message || "发布失败");
			}
		},
		[fetchList],
	);

	const archive = useCallback(
		async (row: PlanRow) => {
			if (!row?.id) return;
			if (!window.confirm("确认归档该数据规划？")) return;
			try {
				await archiveModelingPlan(row.id);
				toast.success("已归档");
				await fetchList();
			} catch (e: any) {
				toast.error(e?.message || "归档失败");
			}
		},
		[fetchList],
	);

	const restore = useCallback(
		async (row: PlanRow) => {
			if (!row?.id) return;
			if (!window.confirm("确认恢复为草稿？")) return;
			try {
				await restoreModelingPlan(row.id);
				toast.success("已恢复");
				await fetchList();
			} catch (e: any) {
				toast.error(e?.message || "恢复失败");
			}
		},
		[fetchList],
	);

	const remove = useCallback(
		async (row: PlanRow) => {
			if (!row?.id) return;
			if (!window.confirm("确认删除该规划？")) return;
			try {
				await deleteModelingPlan(row.id);
				toast.success("已删除");
				await fetchList();
			} catch (e: any) {
				toast.error(e?.message || "删除失败");
			}
		},
		[fetchList],
	);

	const statusBadge = (status?: string | null) => {
		const s = String(status || "").toUpperCase();
		if (s === "PUBLISHED") return <Badge variant="secondary">已发布</Badge>;
		if (s === "ARCHIVED") return <Badge variant="outline">已归档</Badge>;
		return <Badge variant="outline">草稿</Badge>;
	};

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-row items-center justify-between space-y-0">
					<CardTitle>数据规划管理</CardTitle>
					<Button size="sm" onClick={openCreate}>
						新增
					</Button>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="flex flex-col gap-2 md:flex-row md:items-end">
						<div className="flex-1 space-y-2">
							<Label>检索</Label>
							<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="按名称/主题域/负责人搜索" />
						</div>
						<div className="space-y-2">
							<Label>状态</Label>
							<Select value={statusFilter} onValueChange={(v: any) => setStatusFilter(v)}>
								<SelectTrigger className="w-[160px]">
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="ALL">全部</SelectItem>
									<SelectItem value="DRAFT">草稿</SelectItem>
									<SelectItem value="PUBLISHED">已发布</SelectItem>
									<SelectItem value="ARCHIVED">已归档</SelectItem>
								</SelectContent>
							</Select>
						</div>
						<Button variant="secondary" disabled={loading} onClick={() => void fetchList()}>
							刷新
						</Button>
					</div>
					<AntTable
						rowKey={(r: any) => String(r.id)}
						size="middle"
						loading={loading}
						pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (total) => `总计 ${total} 条` }}
						dataSource={filtered}
						columns={[
							{
								title: "名称",
								dataIndex: "name",
								key: "name",
								ellipsis: true,
								render: (v: any, r: any) => (
									<button type="button" className="text-left font-medium hover:underline" onClick={() => void openEdit(r)}>
										{String(v || "-")}
									</button>
								),
							},
							{
								title: "主题域",
								dataIndex: "domain",
								key: "domain",
								width: 160,
								ellipsis: true,
								render: (_: any, r: any) => renderDomainLabel(r.domain),
							},
							{
								title: "状态",
								dataIndex: "status",
								key: "status",
								width: 120,
								render: (v: any) => statusBadge(v),
							},
							{ title: "版本", dataIndex: "version", key: "version", width: 100, ellipsis: true },
							{ title: "负责人", dataIndex: "owner", key: "owner", width: 140, ellipsis: true },
							{ title: "部门", dataIndex: "ownerDept", key: "ownerDept", width: 140, ellipsis: true },
							{
								title: "更新时间",
								dataIndex: "lastModifiedDate",
								key: "lastModifiedDate",
								width: 180,
								render: (v: any) => <span className="text-xs text-muted-foreground">{formatDateTime(v)}</span>,
							},
							{
								title: "操作",
								key: "action",
								width: 340,
								render: (_: any, r: any) => {
									const s = String(r?.status || "").toUpperCase();
									return (
										<div className="flex flex-wrap gap-2">
											<Button size="sm" variant="secondary" onClick={() => void openEdit(r)}>
												编辑
											</Button>
											{s === "DRAFT" ? (
												<Button size="sm" variant="outline" onClick={() => void publish(r)}>
													发布
												</Button>
											) : null}
											{s === "PUBLISHED" ? (
												<Button size="sm" variant="outline" onClick={() => void archive(r)}>
													归档
												</Button>
											) : null}
											{s === "ARCHIVED" ? (
												<Button size="sm" variant="outline" onClick={() => void restore(r)}>
													恢复草稿
												</Button>
											) : null}
											<Button size="sm" variant="destructive" onClick={() => void remove(r)}>
												删除
											</Button>
										</div>
									);
								},
							},
						]}
					/>
				</CardContent>
			</Card>

			<Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
				<DialogContent className="sm:max-w-[720px]">
					<DialogHeader>
						<DialogTitle>{form.id ? "编辑数据规划" : "新增数据规划"}</DialogTitle>
					</DialogHeader>
					<div className="grid grid-cols-1 gap-3 md:grid-cols-2">
						<div className="space-y-2 md:col-span-2">
							<Label>名称 *</Label>
							<Input value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} />
						</div>
						<div className="space-y-2">
							<Label>主题域</Label>
							<Select
								value={domainFormSelectValue}
								onValueChange={(value) => {
									if (value === DOMAIN_SELECT_UNSET) {
										setForm((p) => ({ ...p, domain: "" }));
										return;
									}
									if (value === DOMAIN_SELECT_CUSTOM) {
										return;
									}
									const domainName = domainNameByKey[value] ?? "";
									setForm((p) => ({ ...p, domain: domainName }));
								}}
							>
								<SelectTrigger>
									<SelectValue placeholder="选择主题域" />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value={DOMAIN_SELECT_UNSET}>（未选择）</SelectItem>
									{domainFormSelectValue === DOMAIN_SELECT_CUSTOM && !!form.domain.trim() && (
										<SelectItem value={DOMAIN_SELECT_CUSTOM}>当前值：{form.domain}（已不存在）</SelectItem>
									)}
									{domainOptions.map((opt) => (
										<SelectItem key={opt.key} value={opt.key}>
											{opt.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>版本</Label>
							<Input value={form.version} onChange={(e) => setForm((p) => ({ ...p, version: e.target.value }))} placeholder="如：v1" />
						</div>
						<div className="space-y-2">
							<Label>负责人</Label>
							<Input value={form.owner} onChange={(e) => setForm((p) => ({ ...p, owner: e.target.value }))} />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>标签（逗号分隔）</Label>
							<Input value={form.tags} onChange={(e) => setForm((p) => ({ ...p, tags: e.target.value }))} />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>内容</Label>
							<Textarea
								value={form.content}
								onChange={(e) => setForm((p) => ({ ...p, content: e.target.value }))}
								placeholder="可填写规划背景、业务域拆分、产出物模板链接等"
								className="min-h-[160px]"
							/>
						</div>
					</div>
					<DialogFooter>
						<Button variant="secondary" onClick={() => setDialogOpen(false)}>
							取消
						</Button>
						<Button onClick={() => void save()} disabled={saving}>
							保存
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}
