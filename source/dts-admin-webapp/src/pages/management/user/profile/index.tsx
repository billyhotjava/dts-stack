import type { CSSProperties } from "react";
import { useMemo } from "react";
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

function UserProfile() {
	const { avatar, fullName, firstName, username, email, roles } = useUserInfo();

	const resolvedName = fullName || firstName || username || "";
	const roleLabels = useMemo(() => {
		const labels = resolveRoleLabels(roles);
		if (labels.length > 0) {
			return Array.from(new Set(labels));
		}
		return [];
	}, [roles]);

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
						{email ? (
							<Text variant="body3" className="text-background/80">
								联系邮箱：{email}
							</Text>
						) : null}
					</div>
				</div>
			</div>

			<ProfileTab />
		</div>
	);
}

export default UserProfile;
