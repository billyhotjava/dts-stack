import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	createGlossaryTerm,
	deleteGlossaryTerm,
	listGlossaryTerms,
	updateGlossaryTerm,
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

type TermRow = {
	id: string;
	code?: string | null;
	name: string;
	aliases?: string | null;
	domain?: string | null;
	status?: string | null;
	owner?: string | null;
	ownerDept?: string | null;
	tags?: string | null;
	definition?: string | null;
	lastModifiedDate?: string | null;
};

type TermForm = {
	id?: string;
	code: string;
	name: string;
	aliases: string;
	domain: string;
	status: "DRAFT" | "ACTIVE" | "ARCHIVED";
	owner: string;
	tags: string;
	definition: string;
};

const DEFAULT_FORM: TermForm = {
	code: "",
	name: "",
	aliases: "",
	domain: "",
	status: "ACTIVE",
	owner: "",
	tags: "",
	definition: "",
};

export default function GlossaryPage() {
	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [items, setItems] = useState<TermRow[]>([]);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [form, setForm] = useState<TermForm>(DEFAULT_FORM);

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
			const list: any[] = await listGlossaryTerms({ keyword: keyword.trim() || undefined });
			setItems(
				(Array.isArray(list) ? list : []).map((t: any) => ({
					id: String(t?.id ?? ""),
					code: t?.code ?? null,
					name: String(t?.name ?? ""),
					aliases: t?.aliases ?? null,
					domain: t?.domain ?? null,
					status: t?.status ?? null,
					owner: t?.owner ?? null,
					ownerDept: t?.ownerDept ?? t?.owner_dept ?? null,
					tags: t?.tags ?? null,
					definition: t?.definition ?? null,
					lastModifiedDate: t?.lastModifiedDate ?? t?.last_modified_date ?? null,
				})),
			);
		} catch (e: any) {
			toast.error(e?.message || "加载术语失败");
		} finally {
			setLoading(false);
		}
	}, [keyword]);

	useEffect(() => {
		void fetchList();
	}, [fetchList]);

	const filtered = useMemo(() => {
		const k = keyword.trim().toLowerCase();
		if (!k) return items;
		return items.filter(
			(it) =>
				(it.name || "").toLowerCase().includes(k) ||
				String(it.code || "").toLowerCase().includes(k) ||
				String(it.aliases || "").toLowerCase().includes(k),
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

	const openEdit = (row: TermRow) => {
		setForm({
			id: row.id,
			code: String(row.code ?? ""),
			name: String(row.name ?? ""),
			aliases: String(row.aliases ?? ""),
			domain: String(row.domain ?? ""),
			status:
				String(row.status ?? "ACTIVE").toUpperCase() === "ARCHIVED"
					? "ARCHIVED"
					: String(row.status ?? "ACTIVE").toUpperCase() === "DRAFT"
						? "DRAFT"
						: "ACTIVE",
			owner: String(row.owner ?? ""),
			tags: String(row.tags ?? ""),
			definition: String(row.definition ?? ""),
		});
		setDialogOpen(true);
	};

	const save = useCallback(async () => {
		if (!form.name.trim()) {
			toast.error("请输入名称");
			return;
		}
		setSaving(true);
		try {
			const payload: any = {
				code: form.code.trim() || null,
				name: form.name.trim(),
				aliases: form.aliases.trim() || null,
				domain: form.domain.trim() || null,
				status: form.status,
				owner: form.owner.trim() || null,
				tags: form.tags.trim() || null,
				definition: form.definition.trim() || null,
			};
			if (form.id) {
				await updateGlossaryTerm(form.id, payload);
			} else {
				await createGlossaryTerm(payload);
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

	const remove = useCallback(
		async (row: TermRow) => {
			if (!row?.id) return;
			if (!window.confirm("确认删除该术语？")) return;
			try {
				await deleteGlossaryTerm(row.id);
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
		if (s === "ARCHIVED") return <Badge variant="outline">归档</Badge>;
		if (s === "DRAFT") return <Badge variant="outline">草稿</Badge>;
		return <Badge variant="secondary">启用</Badge>;
	};

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-row items-center justify-between space-y-0">
					<CardTitle>指标口径与术语库</CardTitle>
					<Button size="sm" onClick={openCreate}>
						新增
					</Button>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="flex flex-col gap-2 md:flex-row md:items-end">
						<div className="flex-1 space-y-2">
							<Label>检索</Label>
							<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="按名称/编码/别名搜索" />
						</div>
						<Button variant="secondary" disabled={loading} onClick={() => void fetchList()}>
							刷新
						</Button>
					</div>
					<div className="overflow-auto rounded border">
						<table className="w-full text-sm">
							<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
								<tr>
									<th className="px-3 py-2">编码</th>
									<th className="px-3 py-2">名称</th>
									<th className="px-3 py-2">别名</th>
									<th className="px-3 py-2">主题域</th>
									<th className="px-3 py-2">状态</th>
									<th className="px-3 py-2">负责人</th>
									<th className="px-3 py-2">部门</th>
									<th className="px-3 py-2">更新时间</th>
									<th className="px-3 py-2 w-[180px]">操作</th>
								</tr>
							</thead>
							<tbody>
								{filtered.map((row) => (
									<tr key={row.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs">{row.code || "-"}</td>
										<td className="px-3 py-2 text-xs font-medium">{row.name}</td>
										<td className="px-3 py-2 text-xs">{row.aliases || "-"}</td>
										<td className="px-3 py-2 text-xs">{renderDomainLabel(row.domain)}</td>
										<td className="px-3 py-2 text-xs">{statusBadge(row.status)}</td>
										<td className="px-3 py-2 text-xs">{row.owner || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.ownerDept || "-"}</td>
										<td className="px-3 py-2 text-xs">{formatDateTime(row.lastModifiedDate)}</td>
										<td className="px-3 py-2">
											<div className="flex gap-2">
												<Button size="sm" variant="secondary" onClick={() => openEdit(row)}>
													编辑
												</Button>
												<Button size="sm" variant="destructive" onClick={() => void remove(row)}>
													删除
												</Button>
											</div>
										</td>
									</tr>
								))}
								{filtered.length === 0 && (
									<tr>
										<td colSpan={9} className="px-3 py-10 text-center text-muted-foreground">
											{loading ? "加载中..." : "暂无数据"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</CardContent>
			</Card>

			<Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
				<DialogContent className="sm:max-w-[720px]">
					<DialogHeader>
						<DialogTitle>{form.id ? "编辑术语" : "新增术语"}</DialogTitle>
					</DialogHeader>
					<div className="grid grid-cols-1 gap-3 md:grid-cols-2">
						<div className="space-y-2">
							<Label>编码</Label>
							<Input value={form.code} onChange={(e) => setForm((p) => ({ ...p, code: e.target.value }))} placeholder="可选，如：TERM-001" />
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
						<div className="space-y-2 md:col-span-2">
							<Label>名称 *</Label>
							<Input value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>别名</Label>
							<Input value={form.aliases} onChange={(e) => setForm((p) => ({ ...p, aliases: e.target.value }))} placeholder="逗号分隔" />
						</div>
						<div className="space-y-2">
							<Label>状态</Label>
							<Select value={form.status} onValueChange={(v: any) => setForm((p) => ({ ...p, status: v }))}>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="ACTIVE">启用</SelectItem>
									<SelectItem value="DRAFT">草稿</SelectItem>
									<SelectItem value="ARCHIVED">归档</SelectItem>
								</SelectContent>
							</Select>
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
							<Label>定义/口径说明</Label>
							<Textarea
								value={form.definition}
								onChange={(e) => setForm((p) => ({ ...p, definition: e.target.value }))}
								placeholder="简要说明术语含义、口径边界、示例等"
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
