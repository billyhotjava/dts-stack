import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Button } from "@/ui/button";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { toast } from "sonner";
import { approveRequest, createRequest, listRequests, rejectRequest } from "@/api/platformApi";

type RequestItem = {
	id: string;
	requester: string;
	resource: string;
	action: string;
	reason?: string;
	status: string;
};

export default function RequestsPage() {
	const [items, setItems] = useState<RequestItem[]>([]);
	const [loading, setLoading] = useState(false);
	const [open, setOpen] = useState(false);
	const [form, setForm] = useState<Partial<RequestItem>>({ resource: "dataset:demo", action: "read", reason: "" });

	const load = async () => {
		setLoading(true);
		try {
			const list = (await listRequests()) as any[];
			setItems(
				(list || []).map((it: any) => ({
					id: String(it.id),
					requester: it.requester || "-",
					resource: it.resource,
					action: it.action,
					reason: it.reason,
					status: it.status,
				})),
			);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void load();
	}, []);

	const onCreate = async () => {
		try {
			await createRequest(form);
			toast.success("已提交申请");
			setOpen(false);
			await load();
		} catch (e) {
			console.error(e);
			toast.error("提交失败");
		}
	};

	const onApprove = async (id: string) => {
		await approveRequest(id);
		await load();
	};
	const onReject = async (id: string) => {
		await rejectRequest(id);
		await load();
	};

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex items-center justify-between">
					<CardTitle className="text-base">权限申请</CardTitle>
					<div className="flex items-center gap-2">
						<Button onClick={() => setOpen(true)}>新建申请</Button>
						<Button variant="outline" onClick={load} disabled={loading}>
							刷新
						</Button>
					</div>
				</CardHeader>
				<CardContent>
					<div className="overflow-hidden rounded-md border">
						<table className="w-full border-collapse text-sm">
							<thead className="bg-muted/50">
								<tr className="text-left">
									<th className="px-3 py-2">资源</th>
									<th className="px-3 py-2">动作</th>
									<th className="px-3 py-2">申请人</th>
									<th className="px-3 py-2">状态</th>
									<th className="px-3 py-2">操作</th>
								</tr>
							</thead>
							<tbody>
								{items.map((it) => (
									<tr key={it.id} className="border-b last:border-b-0">
										<td className="px-3 py-2">{it.resource}</td>
										<td className="px-3 py-2">{it.action}</td>
										<td className="px-3 py-2 text-xs text-muted-foreground">{it.requester}</td>
										<td className="px-3 py-2">{it.status}</td>
										<td className="px-3 py-2">
											<Button variant="ghost" size="sm" onClick={() => onApprove(it.id)}>
												通过
											</Button>
											<Button variant="ghost" size="sm" onClick={() => onReject(it.id)}>
												拒绝
											</Button>
										</td>
									</tr>
								))}
								{!items.length && (
									<tr>
										<td colSpan={5} className="px-3 py-6 text-center text-xs text-muted-foreground">
											暂无申请
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</CardContent>
			</Card>

			<Dialog open={open} onOpenChange={setOpen}>
				<DialogContent className="max-w-lg">
					<DialogHeader>
						<DialogTitle>新建权限申请</DialogTitle>
					</DialogHeader>
					<div className="space-y-3">
						<div className="grid gap-2">
							<Label>资源</Label>
							<Input
								value={form.resource ?? ""}
								onChange={(e) => setForm((f) => ({ ...f, resource: e.target.value }))}
							/>
						</div>
						<div className="grid gap-2">
							<Label>动作</Label>
							<Input value={form.action ?? ""} onChange={(e) => setForm((f) => ({ ...f, action: e.target.value }))} />
						</div>
						<div className="grid gap-2">
							<Label>申请理由</Label>
							<Input value={form.reason ?? ""} onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))} />
						</div>
					</div>
					<DialogFooter>
						<Button variant="ghost" onClick={() => setOpen(false)}>
							取消
						</Button>
						<Button onClick={onCreate}>提交</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}
