import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";

export default function BackupRecoveryPage() {
	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<CardTitle>备份恢复与灾备演练</CardTitle>
				</CardHeader>
				<CardContent className="space-y-2 text-sm">
					<div className="text-muted-foreground">
						本模块当前为轻量落地：平台侧提供策略建议与演练留痕入口（审计）；实际备份动作由运维脚本/数据库工具链执行。
					</div>
					<ul className="list-disc space-y-1 pl-5 text-sm">
						<li>备份对象：平台数据库、dts-admin 数据库、关键配置文件与密钥。</li>
						<li>保留策略：按“日/周/月”保留，定期校验可恢复性。</li>
						<li>演练：每季度至少一次恢复演练，并形成演练记录（建议作为审计附件/工单）。</li>
					</ul>
				</CardContent>
			</Card>
		</div>
	);
}

