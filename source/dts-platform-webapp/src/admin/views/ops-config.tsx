import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { SystemConfigItem } from "@/admin/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Button } from "@/ui/button";
import { Input } from "@/ui/input";
import { Textarea } from "@/ui/textarea";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

export default function OpsConfigView() {
	const queryClient = useQueryClient();
	const { data = [] } = useQuery({
		queryKey: ["admin", "system-config"],
		queryFn: adminApi.getSystemConfig,
	});

	const [draft, setDraft] = useState<SystemConfigItem>({ key: "", value: "" });

	const handleSubmit = async () => {
		if (!draft.key) {
			toast.error("请填写配置键");
			return;
		}
		try {
			await adminApi.draftSystemConfig(draft);
			toast.success("已生成变更并提交审批");
			setDraft({ key: "", value: "" });
			queryClient.invalidateQueries({ queryKey: ["admin", "system-config"] });
		} catch (error) {
			toast.error("提交失败，请稍后再试");
		}
	};

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader>
					<CardTitle>当前系统配置</CardTitle>
				</CardHeader>
				<CardContent className="space-y-3">
					{data.length === 0 ? <Text variant="body3">暂无记录。</Text> : null}
					{data.map((item) => (
						<div key={item.key} className="rounded-lg border bg-muted/40 p-3">
							<Text variant="body2" className="font-semibold">
								{item.key}
							</Text>
							<Text variant="body3" className="text-muted-foreground">
								{item.value || "--"}
							</Text>
							{item.description ? (
								<Text variant="body3" className="text-muted-foreground">
									{item.description}
								</Text>
							) : null}
						</div>
					))}
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>提交新配置</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<Input
						placeholder="配置键，如 cluster.mode"
						value={draft.key}
						onChange={(event) => setDraft((prev) => ({ ...prev, key: event.target.value }))}
					/>
					<Input
						placeholder="配置值"
						value={draft.value || ""}
						onChange={(event) => setDraft((prev) => ({ ...prev, value: event.target.value }))}
					/>
					<Textarea
						placeholder="描述（可选）"
						value={draft.description || ""}
						onChange={(event) => setDraft((prev) => ({ ...prev, description: event.target.value }))}
						rows={3}
					/>
					<Button onClick={handleSubmit} className="w-full md:w-auto">
						提交审批
					</Button>
				</CardContent>
			</Card>
		</div>
	);
}
