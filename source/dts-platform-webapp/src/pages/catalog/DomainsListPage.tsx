import { useEffect, useMemo, useState } from "react";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Textarea } from "@/ui/textarea";
import { toast } from "sonner";
import { createDomain, deleteDomain, listDomains, updateDomain } from "@/api/platformApi";

type Domain = {
	id: string;
	name: string;
	code: string;
	owner: string;
	description?: string;
};

export default function DomainsListPage() {
	const [items, setItems] = useState<Domain[]>([]);
	const [total, setTotal] = useState(0);
	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [page, setPage] = useState(0);
	const [size] = useState(10);
	const [open, setOpen] = useState(false);
	const [mode, setMode] = useState<"create" | "edit">("create");
	const [editing, setEditing] = useState<Domain | null>(null);
	const [form, setForm] = useState<Partial<Domain>>({ name: "", code: "", owner: "", description: "" });

	const fetchList = async () => {
		setLoading(true);
		try {
			const data = (await listDomains(page, size, keyword)) as any;
			const content = (data && data.content) || [];
			setItems(
				content.map((d: any) => ({
					id: String(d.id),
					name: d.name,
					code: d.code,
					owner: d.owner,
					description: d.description,
				})),
			);
			setTotal(Number((data && data.total) || 0));
		} catch (e) {
			console.error(e);
			toast.error("加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void fetchList();
	}, [page, size]);

	const onCreate = () => {
		setMode("create");
		setForm({ name: "", code: "", owner: "", description: "" });
		setOpen(true);
	};

	const onEdit = (d: Domain) => {
		setMode("edit");
		setEditing(d);
		setForm(d);
		setOpen(true);
	};

	const onDelete = async (d: Domain) => {
		try {
			await deleteDomain(d.id);
			toast.success("已删除");
			await fetchList();
		} catch (e) {
			console.error(e);
			toast.error("删除失败");
		}
	};

	const onSubmit = async () => {
		if (!form.name || !form.code) {
			toast.error("请填写名称与编码");
			return;
		}
		try {
			if (mode === "create") {
				await createDomain(form);
			} else if (editing) {
				await updateDomain(editing.id, form);
			}
			toast.success("已保存");
			setOpen(false);
			await fetchList();
		} catch (e) {
			console.error(e);
			toast.error("保存失败");
		}
	};

	const totalPages = useMemo(() => Math.max(1, Math.ceil(total / size)), [total, size]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex items-center justify-between">
					<CardTitle className="text-base">数据域管理</CardTitle>
					<div className="flex items-center gap-2">
						<Input
							value={keyword}
							placeholder="搜索名称/编码/负责人"
							onChange={(e) => setKeyword(e.target.value)}
							onKeyDown={(e) => e.key === "Enter" && fetchList()}
							className="w-[220px]"
						/>
						<Button variant="outline" onClick={fetchList} disabled={loading}>
							刷新
						</Button>
						<Button onClick={onCreate}>新建</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="overflow-hidden rounded-md border">
						<table className="w-full border-collapse text-sm">
							<thead className="bg-muted/50">
								<tr className="text-left">
									<th className="px-3 py-2">名称</th>
									<th className="px-3 py-2">编码</th>
									<th className="px-3 py-2">负责人</th>
									<th className="px-3 py-2">描述</th>
									<th className="px-3 py-2">操作</th>
								</tr>
							</thead>
							<tbody>
								{items.map((d) => (
									<tr key={d.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 font-medium">{d.name}</td>
										<td className="px-3 py-2">{d.code}</td>
										<td className="px-3 py-2">{d.owner}</td>
										<td className="px-3 py-2 text-xs text-muted-foreground">{d.description || "-"}</td>
										<td className="px-3 py-2">
											<Button variant="ghost" size="sm" onClick={() => onEdit(d)}>
												编辑
											</Button>
											<Button variant="ghost" size="sm" onClick={() => onDelete(d)}>
												删除
											</Button>
										</td>
									</tr>
								))}
								{!items.length && (
									<tr>
										<td colSpan={5} className="px-3 py-6 text-center text-xs text-muted-foreground">
											{loading ? "加载中…" : "暂无数据"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
					<div className="flex items-center justify-between text-xs text-muted-foreground">
						<span>
							第 {totalPages ? page + 1 : 0} / {totalPages} 页 · 共 {total} 条
						</span>
						<div className="flex items-center gap-2">
							<Button
								variant="outline"
								size="sm"
								disabled={page === 0}
								onClick={() => setPage((p) => Math.max(0, p - 1))}
							>
								上一页
							</Button>
							<Button
								variant="outline"
								size="sm"
								disabled={page >= totalPages - 1}
								onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
							>
								下一页
							</Button>
						</div>
					</div>
				</CardContent>
			</Card>

			<Dialog open={open} onOpenChange={setOpen}>
				<DialogContent className="max-w-lg">
					<DialogHeader>
						<DialogTitle>{mode === "create" ? "新建数据域" : "编辑数据域"}</DialogTitle>
					</DialogHeader>
					<div className="space-y-3">
						<div className="grid gap-2">
							<Label>名称 *</Label>
							<Input value={form.name ?? ""} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
						</div>
						<div className="grid gap-2">
							<Label>编码 *</Label>
							<Input value={form.code ?? ""} onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))} />
						</div>
						<div className="grid gap-2">
							<Label>负责人</Label>
							<Input value={form.owner ?? ""} onChange={(e) => setForm((f) => ({ ...f, owner: e.target.value }))} />
						</div>
						<div className="grid gap-2">
							<Label>描述</Label>
							<Textarea
								value={form.description ?? ""}
								onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
							/>
						</div>
					</div>
					<DialogFooter>
						<Button variant="ghost" onClick={() => setOpen(false)}>
							取消
						</Button>
						<Button onClick={onSubmit}>保存</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}
