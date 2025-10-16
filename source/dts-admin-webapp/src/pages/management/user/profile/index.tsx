import type { CSSProperties } from "react";
import { useEffect, useMemo, useState } from "react";
import type { KeycloakUser } from "#/keycloak";
import { KeycloakUserService } from "@/api/services/keycloakService";
import bannerImage from "@/assets/images/background/banner-1.png";
import { Icon } from "@/components/icon";
import { useUserInfo } from "@/store/userStore";
import { themeVars } from "@/theme/theme.css";
import { Avatar, AvatarImage } from "@/ui/avatar";
import { Text, Title } from "@/ui/typography";
import ProfileTab from "./profile-tab";

const ROLE_LABEL_MAP: Record<string, string> = {
	SYSADMIN: "系统管理员",
	AUTHADMIN: "授权管理员",
	AUDITADMIN: "安全审计员",
	DEPT_DATA_OWNER: "部门数据管理员",
	DEPT_DATA_DEV: "部门数据开发员",
	INST_DATA_OWNER: "研究所数据管理员",
	INST_DATA_DEV: "研究所数据开发员",
	// Legacy aliases retained for兼容
	DEPT_OWNER: "部门数据管理员",
	DEPT_EDITOR: "部门数据开发员",
	INST_OWNER: "研究所数据管理员",
	INST_EDITOR: "研究所数据开发员",
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

const USERNAME_FALLBACK_NAME: Record<string, string> = {
	sysadmin: "系统管理员",
	authadmin: "授权管理员",
	auditadmin: "安全审计员",
	opadmin: "业务运维管理员",
};

const PROTECTED_USERNAMES = new Set(Object.keys(USERNAME_FALLBACK_NAME));

const pickAttributeValue = (attributes: Record<string, string[]> | undefined, keys: string[]) => {
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

function UserProfile() {
	const { avatar, fullName, firstName, username, email, roles, attributes } = useUserInfo();
	const [detail, setDetail] = useState<KeycloakUser | null>(null);

	useEffect(() => {
		let active = true;
		const fetchDetail = async () => {
			if (!username || PROTECTED_USERNAMES.has(username.toLowerCase())) {
				setDetail(null);
				return;
			}
			try {
				const list = await KeycloakUserService.searchUsers(username);
				const remote = list.find((user) => user.username?.toLowerCase() === username.toLowerCase()) || list[0] || null;
				if (active) {
					setDetail(remote);
				}
			} catch (err) {
				console.warn("Failed to load 服务端 user detail:", err);
				if (active) {
					setDetail(null);
				}
			}
		};
		fetchDetail();
		return () => {
			active = false;
		};
	}, [username]);

	const detailAttributes = detail?.attributes as Record<string, string[]> | undefined;
	const storeAttributes = attributes as Record<string, string[]> | undefined;

	const attributefullName =
		pickAttributeValue(detailAttributes, ["fullName", "fullName"]) ||
		pickAttributeValue(storeAttributes, ["fullName", "fullName"]);
	const normalizedAttributefullName =
		attributefullName && attributefullName.toLowerCase() !== username?.toLowerCase() ? attributefullName : "";
	const normalizedDetailfullName =
		detail?.fullName && detail.fullName.toLowerCase() !== username?.toLowerCase() ? detail.fullName : "";
	const normalizedStorefullName = fullName && fullName.toLowerCase() !== username?.toLowerCase() ? fullName : "";
	const normalizedStoreFirstName = firstName && firstName.toLowerCase() !== username?.toLowerCase() ? firstName : "";
	const fallbackName = USERNAME_FALLBACK_NAME[username?.toLowerCase() ?? ""] || "";
	const resolvedName = (
		normalizedAttributefullName ||
		normalizedDetailfullName ||
		normalizedStorefullName ||
		normalizedStoreFirstName ||
		fallbackName ||
		username ||
		""
	).trim();
	const displayEmail = detail?.email || email || "";
	const roleLabels = useMemo(() => {
		const remoteRoles = detail?.realmRoles;
		const labels = resolveRoleLabels(remoteRoles && remoteRoles.length ? remoteRoles : roles);
		if (labels.length > 0) {
			return Array.from(new Set(labels));
		}
		return [];
	}, [detail?.realmRoles, roles]);

	const bgStyle: CSSProperties = {
		position: "absolute",
		inset: 0,
		background: `url(${bannerImage})`,
		backgroundSize: "cover",
		backgroundPosition: "50%",
		backgroundRepeat: "no-repeat",
	};

	return (
		<div className="space-y-6">
			<div className="relative flex flex-col items-center gap-4 p-6 text-center">
				<div style={bgStyle} className="absolute inset-0 rounded-lg" />
				<div className="absolute inset-0 rounded-lg bg-black/40" />
				<div className="relative z-10 flex flex-col items-center gap-3">
					<Avatar className="h-24 w-24 border-4 border-background shadow-lg">
						<AvatarImage src={avatar} className="rounded-full" />
					</Avatar>
					<div className="flex flex-col items-center gap-1">
						<div className="flex items-center gap-2">
							<Title as="h5" className="text-xl text-background">
								{resolvedName}
							</Title>
							<Icon icon="heroicons:check-badge-solid" size={20} color={themeVars.colors.palette.success.default} />
						</div>
						{username ? (
							<Text variant="body3" className="text-background/80">
								登录账号：{username}
							</Text>
						) : null}
						<Text variant="body2" className="text-background">
							{roleLabels.length ? roleLabels.join("、") : "管理员"}
						</Text>
						{displayEmail ? (
							<Text variant="body3" className="text-background/80">
								联系邮箱：{displayEmail}
							</Text>
						) : null}
					</div>
				</div>
			</div>

			<ProfileTab detail={detail} resolveAttributeValue={pickAttributeValue} />
		</div>
	);
}

export default UserProfile;
