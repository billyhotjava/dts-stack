import { useEffect, useState } from "react";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Badge } from "@/ui/badge";
import { toast } from "sonner";
import { createToken, deleteToken, listMyTokens } from "@/api/platformApi";

type Token = {
	id: string;
	token: string;
	revoked: boolean;
	expiresAt?: string;
	createdBy?: string;
	createdDate?: string;
};

export default function TokensPage() {
	const [tokens, setTokens] = useState<Token[]>([]);
	const [loading, setLoading] = useState(false);
	const [creating, setCreating] = useState(false);

	const load = async () => {
		setLoading(true);
		try {
			const data = await listMyTokens();
			setTokens(data as unknown as Token[]);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void load();
	}, []);

	const onCreate = async () => {
		setCreating(true);
		try {
			await createToken();
			toast.success("已生成新的访问令牌");
			await load();
		} catch (e) {
			console.error(e);
			toast.error("生成失败");
		} finally {
			setCreating(false);
		}
	};

	const onDelete = async (id: string) => {
		try {
			await deleteToken(id);
			toast.success("已吊销令牌");
			await load();
		} catch (e) {
			console.error(e);
			toast.error("吊销失败");
		}
	};

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex items-center justify-between">
					<CardTitle className="text-base font-semibold">个人访问令牌</CardTitle>
					<div className="flex items-center gap-2">
						<Button onClick={load} variant="outline" disabled={loading}>
							刷新
						</Button>
						<Button onClick={onCreate} disabled={creating}>
							生成令牌
						</Button>
					</div>
				</CardHeader>
				<CardContent>
					<div className="overflow-hidden rounded-md border">
						<table className="w-full border-collapse text-sm">
							<thead className="bg-muted/50">
								<tr className="text-left">
									<th className="px-3 py-2">令牌</th>
									<th className="px-3 py-2">状态</th>
									<th className="px-3 py-2">过期时间</th>
									<th className="px-3 py-2">创建时间</th>
									<th className="px-3 py-2">操作</th>
								</tr>
							</thead>
							<tbody>
								{tokens.map((t) => (
									<tr key={t.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 font-mono text-xs break-all">{t.token}</td>
										<td className="px-3 py-2">
											<Badge variant={t.revoked ? "secondary" : "default"}>{t.revoked ? "已吊销" : "有效"}</Badge>
										</td>
										<td className="px-3 py-2 text-xs text-muted-foreground">
											{t.expiresAt ? new Date(t.expiresAt).toLocaleString() : "-"}
										</td>
										<td className="px-3 py-2 text-xs text-muted-foreground">
											{t.createdDate ? new Date(t.createdDate).toLocaleString() : "-"}
										</td>
										<td className="px-3 py-2">
											<Button variant="ghost" size="sm" onClick={() => onDelete(t.id)}>
												吊销
											</Button>
										</td>
									</tr>
								))}
								{!tokens.length && (
									<tr>
										<td colSpan={5} className="px-3 py-6 text-center text-xs text-muted-foreground">
											{loading ? "加载中…" : "暂无令牌"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</CardContent>
			</Card>
		</div>
	);
}
