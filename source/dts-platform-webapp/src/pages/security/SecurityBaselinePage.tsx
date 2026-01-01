import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";

export default function SecurityBaselinePage() {
	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<CardTitle>安全基线与系统加固</CardTitle>
				</CardHeader>
				<CardContent className="space-y-2 text-sm">
					<div className="text-muted-foreground">
						本模块当前为轻量落地：平台侧提供基线检查清单与操作指引；实际加固项以生产环境运维基线为准。
					</div>
					<ul className="list-disc space-y-1 pl-5 text-sm">
						<li>账号/口令：强口令策略、禁用默认口令、最小权限。</li>
						<li>会话/令牌：合理过期时间、刷新机制、退出登录失效。</li>
						<li>接口安全：鉴权、限流、审计、异常兜底。</li>
						<li>数据访问：按“部门 + 人员密级”进行行过滤，脱敏规则按字段生效。</li>
						<li>日志与审计：关键操作全部留痕（中文展示），可在“日志审计”查询。</li>
					</ul>
				</CardContent>
			</Card>
		</div>
	);
}

