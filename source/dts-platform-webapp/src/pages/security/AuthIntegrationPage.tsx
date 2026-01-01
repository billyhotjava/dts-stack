import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";

export default function AuthIntegrationPage() {
	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<CardTitle>身份认证集成（PKI/USBKey）</CardTitle>
				</CardHeader>
				<CardContent className="space-y-2 text-sm">
					<div className="text-muted-foreground">
						本版本按“轻量可用”策略：认证/SSO 在统一登录（Keycloak/SSO 网关）侧完成，平台侧只消费登录态与用户属性。
					</div>
					<ul className="list-disc space-y-1 pl-5 text-sm">
						<li>人员密级：来自令牌中的 `person_security_level`（数字）。</li>
						<li>部门：来自令牌中的 `dept_code`。</li>
						<li>数据访问：按“部门 + 人员密级”进行过滤（不在本页面配置）。</li>
						<li>如需接入 PKI/USBKey：建议在 SSO 网关侧完成证书校验与账号绑定，然后下发标准 claims。</li>
					</ul>
				</CardContent>
			</Card>
		</div>
	);
}

