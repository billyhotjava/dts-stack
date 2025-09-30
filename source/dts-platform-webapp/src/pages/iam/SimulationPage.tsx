import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Button } from "@/ui/button";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { toast } from "sonner";
import { simulateIam } from "@/api/platformApi";

export default function SimulationPage() {
	const params = new URLSearchParams(location.search);
	const [user, setUser] = useState(params.get("user") || "");
	const [resource, setResource] = useState("dataset:demo");
	const [action, setAction] = useState("read");
	const [result, setResult] = useState<{ allowed: boolean; reason?: string } | null>(null);

	const onRun = async () => {
		try {
			const resp = (await simulateIam({ user, resource, action })) as any;
			setResult(resp as any);
		} catch (e) {
			console.error(e);
			toast.error("模拟失败");
		}
	};

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<CardTitle className="text-base">权限模拟</CardTitle>
				</CardHeader>
				<CardContent className="grid gap-3 md:grid-cols-3">
					<div className="grid gap-2">
						<Label>用户</Label>
						<Input value={user} onChange={(e) => setUser(e.target.value)} placeholder="preferred_username" />
					</div>
					<div className="grid gap-2">
						<Label>资源</Label>
						<Input value={resource} onChange={(e) => setResource(e.target.value)} placeholder="dataset:xxx" />
					</div>
					<div className="grid gap-2">
						<Label>动作</Label>
						<Select value={action} onValueChange={setAction}>
							<SelectTrigger>
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								{["read", "write", "execute"].map((a) => (
									<SelectItem key={a} value={a}>
										{a}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>
					<div className="md:col-span-3">
						<Button onClick={onRun}>运行模拟</Button>
					</div>
					{result && (
						<div className="md:col-span-3 text-sm">
							<div>结果：{result.allowed ? "允许" : "拒绝"}</div>
							<div className="text-muted-foreground">依据：{result.reason || "-"}</div>
						</div>
					)}
				</CardContent>
			</Card>
		</div>
	);
}
