import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Icon } from "@/components/icon";
import {
	listInfraDataStorages,
	type InfraDataStorage,
} from "@/api/services/infraService";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";

export default function DataStoragePage() {
	const [storages, setStorages] = useState<InfraDataStorage[]>([]);
	const [loading, setLoading] = useState(false);

	const loadStorages = async () => {
		setLoading(true);
		try {
			const data = await listInfraDataStorages();
			setStorages(data);
		} catch (error) {
			console.error(error);
			toast.error("数据存储列表加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadStorages();
	}, []);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-row items-center justify-between">
					<div>
						<CardTitle className="text-base">已登记数据存储</CardTitle>
						<CardDescription>平台侧注册的对象存储/HDFS 等信息（不含敏感配置）。</CardDescription>
					</div>
					<Button type="button" variant="ghost" size="sm" onClick={loadStorages} disabled={loading}>
						<Icon icon="solar:refresh-bold" className="mr-1 h-4 w-4" /> 刷新
					</Button>
				</CardHeader>
				<CardContent>
					<div className="overflow-x-auto rounded-md border">
						<table className="w-full min-w-[720px] table-fixed border-collapse text-sm">
							<thead className="bg-muted/40 text-left text-xs text-muted-foreground">
								<tr>
									<th className="px-3 py-2 font-medium">名称</th>
									<th className="px-3 py-2 font-medium">类型</th>
									<th className="px-3 py-2 font-medium">位置</th>
									<th className="px-3 py-2 font-medium">说明</th>
									<th className="px-3 py-2 font-medium">密钥</th>
								</tr>
							</thead>
							<tbody>
								{storages.map((item) => (
									<tr key={item.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 font-medium">{item.name}</td>
										<td className="px-3 py-2 text-xs text-muted-foreground">{item.type}</td>
										<td className="px-3 py-2 text-xs font-mono text-muted-foreground break-all">{item.location}</td>
										<td className="px-3 py-2 text-xs text-muted-foreground">{item.description || "-"}</td>
										<td className="px-3 py-2">
											<Badge variant="outline">{item.hasSecrets ? "已加密" : "未配置"}</Badge>
										</td>
									</tr>
								))}
								{!storages.length && (
									<tr>
										<td colSpan={5} className="px-3 py-6 text-center text-xs text-muted-foreground">
											{loading ? "加载中…" : "暂无数据存储配置"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
					<p className="mt-3 text-xs text-muted-foreground">
						如需新增或修改存储配置，请通过后端 API 或配置文件进行管理，页面暂提供只读预览。
					</p>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle className="text-base">最佳实践</CardTitle>
					<CardDescription>对象存储/HDFS 集群配置建议</CardDescription>
				</CardHeader>
				<CardContent>
					<ul className="list-disc space-y-2 pl-5 text-sm text-muted-foreground">
						<li>启用 HA：确保 nameservices、namenode RPC 地址在 core-site/hdfs-site 配置一致。</li>
						<li>安全：结合 Kerberos + Ranger/Sentry 控制访问，敏感路径使用独立技术账户。</li>
						<li>告警：定期巡检 NameNode 状态，配置联机/离线告警通道，避免单点风险。</li>
					</ul>
				</CardContent>
			</Card>
		</div>
	);
}
