import { useMemo } from "react";
import type { KeycloakUser } from "#/keycloak";
import { useUserInfo } from "@/store/userStore";

const USERNAME_FALLBACK_NAME: Record<string, string> = {
	sysadmin: "系统管理员",
	authadmin: "授权管理员",
	auditadmin: "安全审计员",
	opadmin: "业务运维管理员",
};

import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";

const ROLE_LABEL_MAP: Record<string, string> = {
	SYSADMIN: "系统管理员",
	SYS_ADMIN: "系统管理员",
	AUTHADMIN: "授权管理员",
	AUTH_ADMIN: "授权管理员",
	AUDITADMIN: "安全审计员",
	SECURITY_AUDITOR: "安全审计员",
	OP_ADMIN: "业务运维管理员",
	DEPT_DATA_OWNER: "部门数据管理员",
	DEPT_DATA_DEV: "部门数据开发员",
	INST_DATA_OWNER: "研究所数据管理员",
	INST_DATA_DEV: "研究所数据开发员",
	DEPT_LEADER: "部门领导",
	INST_LEADER: "研究所领导",
	EMPLOYEE: "普通员工",
	DEPT_OWNER: "部门数据管理员",
	DEPT_EDITOR: "部门数据开发员",
	INST_OWNER: "研究所数据管理员",
	INST_EDITOR: "研究所数据开发员",
};

const HIDDEN_ROLE_EXACT = new Set(["offline_access", "uma_authorization"]);

function shouldHideRoleName(role: string): boolean {
	const trimmed = role.trim();
	if (!trimmed) return true;
	const lower = trimmed.toLowerCase();
	const upper = trimmed.toUpperCase();
	if (HIDDEN_ROLE_EXACT.has(lower)) return true;
	if (lower.startsWith("default-roles-")) return true;
	// Keycloak / realm roles in token may come with ROLE_ prefix
	if (upper.startsWith("ROLE_DEFAULT")) return true;
	if (upper.startsWith("DEFAULT-ROLES")) return true;
	if (upper === "ROLE_OFFLINE_ACCESS" || upper === "ROLE_UMA_AUTHORIZATION") return true;

	return false;
}

function resolveRoleLabels(roles: unknown): string[] {
	if (!Array.isArray(roles) || roles.length === 0) {
		return [];
	}

	return roles
		.map((role) => {
			if (typeof role === "string") {
				const raw = role.trim();
				if (!raw || shouldHideRoleName(raw)) return undefined;
				const upper = raw.toUpperCase();
				const normalized = upper.startsWith("ROLE_") ? upper.substring(5) : upper;
				return ROLE_LABEL_MAP[upper] ?? ROLE_LABEL_MAP[normalized] ?? raw;
			}

			if (role && typeof role === "object") {
				const maybeRole = role as { code?: string; name?: string };
				const key = (maybeRole.code || maybeRole.name || "").trim();
				if (key) {
					if (shouldHideRoleName(key)) return undefined;
					const upper = key.toUpperCase();
					const normalized = upper.startsWith("ROLE_") ? upper.substring(5) : upper;
					return ROLE_LABEL_MAP[upper] ?? ROLE_LABEL_MAP[normalized] ?? key;
				}
			}

			return undefined;
		})
		.filter((item): item is string => Boolean(item));
}

interface ProfileTabProps {
	detail?: KeycloakUser | null;
	resolveAttributeValue: (attributes: Record<string, string[]> | undefined, keys: string[]) => string;
}

export default function ProfileTab({ detail, resolveAttributeValue }: ProfileTabProps) {
	const { fullName, firstName, username, email, roles, enabled, id, attributes } = useUserInfo();
	const detailAttributes = detail?.attributes as Record<string, string[]> | undefined;
	const storeAttributes = attributes as Record<string, string[]> | undefined;

	const attributefullName =
		resolveAttributeValue(detailAttributes, ["fullName", "fullName"]) ||
		resolveAttributeValue(storeAttributes, ["fullName", "fullName"]);
	const resolvedUsername = detail?.username || username || "-";
	const resolvedEmail = detail?.email || email || "-";
	const roleLabels = useMemo(() => {
		const source = detail?.realmRoles && detail.realmRoles.length ? detail.realmRoles : roles;
		const labels = resolveRoleLabels(source);
		if (labels.length > 0) {
			return Array.from(new Set(labels));
		}
		const fallback = USERNAME_FALLBACK_NAME[resolvedUsername?.toLowerCase() ?? ""];
		if (fallback) {
			return [fallback];
		}
		return [];
	}, [detail?.realmRoles, roles, resolvedUsername]);
	const accountStatus = detail?.enabled ?? enabled;
	const accountId = detail?.id || id || "-";
	const fallbackName = USERNAME_FALLBACK_NAME[resolvedUsername?.toLowerCase() ?? ""] || "";
	const normalizedAttributefullName =
		attributefullName && attributefullName.toLowerCase() !== resolvedUsername.toLowerCase() ? attributefullName : "";
	const normalizedDetailfullName =
		detail?.fullName && detail.fullName.toLowerCase() !== resolvedUsername.toLowerCase() ? detail.fullName : "";
	const normalizedStorefullName = fullName && fullName.toLowerCase() !== resolvedUsername.toLowerCase() ? fullName : "";
	const normalizedStoreFirstName =
		firstName && firstName.toLowerCase() !== resolvedUsername.toLowerCase() ? firstName : "";
	const resolvedName =
		normalizedAttributefullName ||
		normalizedDetailfullName?.trim() ||
		normalizedStorefullName?.trim() ||
		normalizedStoreFirstName?.trim() ||
		fallbackName ||
		resolvedUsername;

	const basicInfo = [
		{ label: "姓名", value: resolvedName },
		{ label: "用户名", value: resolvedUsername },
		{ label: "邮箱", value: resolvedEmail },
		{ label: "角色", value: roleLabels.length ? roleLabels.join("、") : "-" },
		{ label: "账号状态", value: accountStatus === false ? "禁用" : "可用" },
		{ label: "账号标识", value: accountId },
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
								{item.label}：
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
