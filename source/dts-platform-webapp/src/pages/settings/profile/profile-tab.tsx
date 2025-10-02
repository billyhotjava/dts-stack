import { useMemo } from "react";
import type { KeycloakUser } from "#/keycloak";
import { useUserInfo } from "@/store/userStore";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";

type AttributeMap = Record<string, string[]> | undefined;

type ProfileTabProps = {
	detail?: KeycloakUser | null;
	pickAttributeValue: (attributes: AttributeMap, keys: string[]) => string;
};

export const ROLE_LABEL_MAP: Record<string, string> = {
	SYSADMIN: "\u7cfb\u7edf\u7ba1\u7406\u5458",
	AUTHADMIN: "\u6388\u6743\u7ba1\u7406\u5458",
	AUDITADMIN: "\u5b89\u5168\u5ba1\u8ba1\u5458",
	DEPT_OWNER: "\u90e8\u95e8\u4e3b\u7ba1",
	DEPT_EDITOR: "\u90e8\u95e8\u6570\u636e\u4e13\u5458",
	DEPT_VIEWER: "\u6570\u636e\u67e5\u9605\u5458",
	INST_OWNER: "\u7814\u7a76\u6240\u9886\u5bfc",
	INST_EDITOR: "\u7814\u7a76\u6240\u6570\u636e\u4e13\u5458",
	INST_VIEWER: "\u7814\u7a76\u6240\u6570\u636e\u67e5\u9605\u5458",
	DATA_STEWARD: "\u6570\u636e\u7ba1\u5bb6",
	DATA_ANALYST: "\u6570\u636e\u5206\u6790\u5458",
};

export const USERNAME_FALLBACK_NAME: Record<string, string> = {
	sysadmin: "\u7cfb\u7edf\u7ba1\u7406\u5458",
	syadmin: "\u7cfb\u7edf\u7ba1\u7406\u5458",
	authadmin: "\u6388\u6743\u7ba1\u7406\u5458",
	auditadmin: "\u5b89\u5168\u5ba1\u8ba1\u5458",
	opadmin: "\u8fd0\u7ef4\u7ba1\u7406\u5458",
};

export function resolveRoleLabels(roles: unknown): string[] {
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

export default function ProfileTab({ detail, pickAttributeValue }: ProfileTabProps) {
	const { fullName, firstName, username, email, roles, enabled, id, attributes } = useUserInfo();
	const detailAttributes = detail?.attributes as AttributeMap;
	const storeAttributes = attributes as AttributeMap;

	const attributeFullName =
		pickAttributeValue(detailAttributes, ["fullName", "fullname"]) || pickAttributeValue(storeAttributes, ["fullName", "fullname"]);
	const resolvedUsername = detail?.username || username || "-";
	const resolvedEmail = detail?.email || email || "-";
	const roleLabels = useMemo(() => {
		const source = detail?.realmRoles && detail.realmRoles.length ? detail.realmRoles : roles;
		const labels = resolveRoleLabels(source);
		if (labels.length > 0) {
			return Array.from(new Set(labels));
		}
		return [];
	}, [detail?.realmRoles, roles]);
	const accountStatus = detail?.enabled ?? enabled;
	const accountId = detail?.id || id || "-";
	const fallbackName = USERNAME_FALLBACK_NAME[resolvedUsername?.toLowerCase() ?? ""] || "";
	const normalizedAttributeFullName = attributeFullName && attributeFullName.toLowerCase() !== resolvedUsername.toLowerCase() ? attributeFullName : "";
	const normalizedDetailFullName = detail?.fullName && detail.fullName.toLowerCase() !== resolvedUsername.toLowerCase() ? detail.fullName : "";
	const normalizedStoreFullName = fullName && fullName.toLowerCase() !== resolvedUsername.toLowerCase() ? fullName : "";
	const normalizedStoreFirstName = firstName && firstName.toLowerCase() !== resolvedUsername.toLowerCase() ? firstName : "";
	const resolvedName = (
		normalizedAttributeFullName ||
		normalizedDetailFullName?.trim() ||
		normalizedStoreFullName?.trim() ||
		normalizedStoreFirstName?.trim() ||
		fallbackName ||
		resolvedUsername
	);

	const basicInfo = [
		{ label: "\u59d3\u540d", value: resolvedName, key: "name" },
		{ label: "\u7528\u6237\u540d", value: resolvedUsername, key: "username" },
		{ label: "\u90ae\u7bb1", value: resolvedEmail, key: "email" },
		{ label: "\u89d2\u8272", value: roleLabels.length ? roleLabels.join("\u3001") : "-", key: "roles" },
		{ label: "\u8d26\u53f7\u72b6\u6001", value: accountStatus === false ? "\u5df2\u505c\u7528" : "\u6b63\u5e38", key: "status" },
		{ label: "\u8d26\u53f7\u6807\u8bc6", value: accountId, key: "id" },
	];

	return (
		<Card>
			<CardHeader className="space-y-1">
				<CardTitle>\u57fa\u672c\u4fe1\u606f</CardTitle>
				<Text variant="body3" className="text-muted-foreground">
					\u5f53\u524d\u767b\u5f55\u8d26\u6237\u7684\u57fa\u7840\u8d44\u6599\u548c\u72b6\u6001
				</Text>
			</CardHeader>
			<CardContent>
				<dl className="grid gap-4 sm:grid-cols-2">
					{basicInfo.map((item) => (
						<div key={item.key} className="space-y-1">
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

