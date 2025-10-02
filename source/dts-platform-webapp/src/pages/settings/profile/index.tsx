import type { CSSProperties } from "react";
import { useEffect, useMemo, useState } from "react";
import bannerImage from "@/assets/images/background/banner-1.png";
import { Icon } from "@/components/icon";
import { KeycloakUserService } from "@/api/services/keycloakService";
import type { KeycloakUser } from "#/keycloak";
import { useUserInfo } from "@/store/userStore";
import { themeVars } from "@/theme/theme.css";
import { Avatar, AvatarImage } from "@/ui/avatar";
import { Text, Title } from "@/ui/typography";
import ProfileTab, { USERNAME_FALLBACK_NAME, resolveRoleLabels } from "./profile-tab";

type AttributeMap = Record<string, string[]> | undefined;

const PROTECTED_USERNAMES = new Set(Object.keys(USERNAME_FALLBACK_NAME));

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
				console.warn("Failed to load Keycloak user detail:", err);
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

	const detailAttributes = detail?.attributes as AttributeMap;
	const storeAttributes = attributes as AttributeMap;

	const attributeFullName =
		pickAttributeValue(detailAttributes, ["fullName", "fullname"]) || pickAttributeValue(storeAttributes, ["fullName", "fullname"]);
	const normalizedAttributeFullName = attributeFullName && attributeFullName.toLowerCase() !== username?.toLowerCase() ? attributeFullName : "";
	const normalizedDetailFullName = detail?.fullName && detail.fullName.toLowerCase() !== username?.toLowerCase() ? detail.fullName : "";
	const normalizedStoreFullName = fullName && fullName.toLowerCase() !== username?.toLowerCase() ? fullName : "";
	const normalizedStoreFirstName = firstName && firstName.toLowerCase() !== username?.toLowerCase() ? firstName : "";
	const fallbackName = username ? USERNAME_FALLBACK_NAME[username.toLowerCase()] : "";
	const resolvedName = (
		normalizedAttributeFullName ||
		normalizedDetailFullName ||
		normalizedStoreFullName ||
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
								{resolvedName || username || "-"}
							</Title>
							<Icon icon="heroicons:check-badge-solid" size={20} color={themeVars.colors.palette.success.default} />
						</div>
						{username ? (
							<Text variant="body3" className="text-background/80">{`\u767b\u5f55\u8d26\u53f7\uff1a${username}`}
							</Text>
						) : null}
						{roleLabels.length ? (
							<Text variant="body2" className="text-background">
								{roleLabels.join("\u3001")}
							</Text>
						) : null}
						{displayEmail ? (
							<Text variant="body3" className="text-background/80">{`\u8054\u7cfb\u90ae\u7bb1\uff1a${displayEmail}`}
							</Text>
						) : null}
					</div>
				</div>
			</div>

			<ProfileTab detail={detail} pickAttributeValue={pickAttributeValue} />
		</div>
	);
}

export default PersonalProfilePage;





