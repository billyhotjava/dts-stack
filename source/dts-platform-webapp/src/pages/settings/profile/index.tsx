import { useMemo } from "react";
import { useUserInfo } from "@/store/userStore";
import { Avatar, AvatarImage } from "@/ui/avatar";
import { Badge } from "@/ui/badge";
import { Card, CardContent } from "@/ui/card";
import { Text, Title } from "@/ui/typography";
import ProfileTab, { USERNAME_FALLBACK_NAME, resolveRoleLabels } from "./profile-tab";

type AttributeMap = Record<string, string[]> | undefined;

// Note: reserved username map retained in profile-tab; no local usage here.

const pickAttributeValue = (attributes: AttributeMap, keys: string[]) => {
	if (!attributes) return "";
	for (const key of keys) {
		const values = attributes[key];
		if (Array.isArray(values) && values.length > 0) {
			const found = values.find((item) => item && item.trim());
			if (found) return found.trim();
			return values[0] ? values[0].trim() : "";
		}
	}
	return "";
};

function PersonalProfilePage() {
    const { avatar, fullName, firstName, username, email, roles, attributes } = useUserInfo();
	// 远端 Keycloak 查询已移除；仅使用本地登录态信息
	const detailAttributes = undefined as AttributeMap;
	const storeAttributes = attributes as AttributeMap;

	const attributefullName =
		pickAttributeValue(detailAttributes, ["fullName", "fullName"]) || pickAttributeValue(storeAttributes, ["fullName", "fullName"]);
	const normalizedAttributefullName =
		attributefullName && attributefullName.toLowerCase() !== username?.toLowerCase() ? attributefullName : "";
	const normalizedDetailfullName = "";
	const normalizedStorefullName = fullName && fullName.toLowerCase() !== username?.toLowerCase() ? fullName : "";
	const normalizedStoreFirstName = firstName && firstName.toLowerCase() !== username?.toLowerCase() ? firstName : "";
	const fallbackName = username ? USERNAME_FALLBACK_NAME[username.toLowerCase()] : "";
	const resolvedName = (
		normalizedAttributefullName ||
		normalizedDetailfullName ||
		normalizedStorefullName ||
		normalizedStoreFirstName ||
		fallbackName ||
		username ||
		""
	).trim();

	const displayEmail = email || "";
	const roleLabels = useMemo(() => {
		const labels = resolveRoleLabels(roles);
		if (labels.length > 0) {
			return Array.from(new Set(labels));
		}
		return [];
	}, [roles]);

	const summaryItems = [
		{ key: "username", label: "登录账号", value: username || "-" },
		{ key: "email", label: "联系邮箱", value: displayEmail || "-" },
	];

	return (
		<div className="space-y-6">
			<Card>
				<CardContent className="flex flex-col items-center gap-5 p-6 text-center md:flex-row md:items-center md:gap-8 md:text-left">
					<Avatar className="h-24 w-24 border border-border shadow-sm">
						<AvatarImage src={avatar} className="rounded-full" />
					</Avatar>
					<div className="flex-1 space-y-3">
						<div className="space-y-2">
							<Title as="h4" className="text-2xl font-semibold">
								{resolvedName || username || "-"}
							</Title>
							{roleLabels.length ? (
								<div className="flex flex-wrap justify-center gap-2 md:justify-start">
									{roleLabels.map((label) => (
										<Badge key={label} variant="info">
											{label}
										</Badge>
									))}
								</div>
							) : null}
						</div>
						<div className="grid w-full gap-2 text-sm text-muted-foreground md:grid-cols-2 md:gap-x-6">
							{summaryItems.map((item) => (
								<Text key={item.key} variant="body3" className="text-muted-foreground">
								{`${item.label}：${item.value}`}
								</Text>
							))}
						</div>
					</div>
				</CardContent>
			</Card>

			<ProfileTab detail={null} pickAttributeValue={pickAttributeValue} />
		</div>
	);
}

export default PersonalProfilePage;
