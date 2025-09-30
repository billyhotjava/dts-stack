import { useMemo } from "react";
import { useUserInfo } from "@/store/userStore";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";

const ROLE_LABEL_MAP: Record<string, string> = {
	SYSADMIN: "系统管理员",
	AUTHADMIN: "授权管理员",
	AUDITADMIN: "安全审计员",
	DEPT_OWNER: "部门主管",
	DEPT_EDITOR: "部门数据专员",
	DEPT_VIEWER: "数据查阅员",
	INST_OWNER: "研究所领导",
	INST_EDITOR: "研究所数据专员",
	INST_VIEWER: "研究所数据查阅员",
};

function resolveRoleLabels(roles: unknown): string[] {
	if (!Array.isArray(roles) || roles.length === 0) {
		return [];
	}

	return roles
		.map((role) => {
			if (typeof role === "string") {
				return ROLE_LABEL_MAP[role] ?? role;
			}

			if (role && typeof role === "object") {
				const maybeRole = role as { code?: string; name?: string };
				const key = maybeRole.code || maybeRole.name;
				if (key) {
					return ROLE_LABEL_MAP[key] ?? key;
				}
			}

			return undefined;
		})
		.filter((item): item is string => Boolean(item));
}

export default function ProfileTab() {
	const { fullName, firstName, username, email, roles, enabled, id } = useUserInfo();

	const resolvedName = fullName || firstName || username || "-";
	const roleLabels = useMemo(() => {
		const labels = resolveRoleLabels(roles);
		if (labels.length > 0) {
			return Array.from(new Set(labels));
		}
		return [];
	}, [roles]);

	const basicInfo = [
		{ label: "姓名", value: resolvedName },
		{ label: "用户名", value: username || "-" },
		{ label: "邮箱", value: email || "-" },
		{ label: "角色", value: roleLabels.length ? roleLabels.join("、") : "-" },
		{ label: "账号状态", value: enabled === false ? "已停用" : "正常" },
		{ label: "账号标识", value: id || "-" },
	];

	return (
		<Card>
			<CardHeader className="space-y-1">
				<CardTitle>基本信息</CardTitle>
				<Text variant="body3" className="text-muted-foreground">
					查看当前登录管理员的基础资料和账号状态。
				</Text>
			</CardHeader>
			<CardContent>
				<dl className="grid gap-4 sm:grid-cols-2">
					{basicInfo.map((item) => (
						<div key={item.label} className="space-y-1">
							<Text variant="body3" className="text-muted-foreground">
								{item.label}
							</Text>
							<Text variant="body2" className="font-medium text-foreground">
								{item.value}
							</Text>
						</div>
					))}
				</dl>
			</CardContent>
		</Card>
	);
}
