import { useEffect, useMemo, useState } from "react";
import { Card, Descriptions, Tag } from "antd";
import { UserOutlined, MailOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import { listDepartments, type DeptDto } from "@/api/services/deptService";
import { listRealmRoles } from "@/api/services/roleService";
import { searchUsers, type UserDirectoryEntry } from "@/api/services/userDirectoryService";
import { PERSON_SECURITY_LEVELS } from "@/constants/governance";
import { useUserInfo } from "@/store/userStore";
import type { KeycloakRole } from "#/keycloak";
import { resolveProfileName } from "./profile-utils";

const DEFAULT_AVATAR = "/assets/icons/ic-user.svg";

function pickAttributeValue(
	attributes: Record<string, string[]> | undefined,
	keys: string[],
): string {
	if (!attributes) return "";
	for (const key of keys) {
		const values = attributes[key];
		if (Array.isArray(values) && values.length > 0 && values[0]) {
			return String(values[0]).trim();
		}
	}
	return "";
}

const PERSON_LEVEL_LABEL_MAP = PERSON_SECURITY_LEVELS.reduce<Record<string, string>>((acc, item) => {
	acc[item.value] = item.label;
	return acc;
}, {});

const KEYCLOAK_DEFAULT_ROLE_PATTERN =
	/^(ROLE_)?(offline_access|uma_authorization|default_roles_.*|default-roles-.*)$/i;

function formatPersonLevel(value?: string): string {
	const raw = String(value || "").trim();
	if (!raw) return "";
	const normalized = raw.toUpperCase().replace(/[\s-]+/g, "_");
	return PERSON_LEVEL_LABEL_MAP[normalized] || raw;
}

function isHiddenDefaultRole(roleName?: string): boolean {
	const raw = String(roleName || "").trim();
	if (!raw) return true;
	return KEYCLOAK_DEFAULT_ROLE_PATTERN.test(raw);
}

const HIDDEN_SYNTHETIC_PERMISSIONS = new Set(["portal.view"]);
const PERMISSION_LABELS: Record<string, string> = {
	"portal.manage": "平台管理",
	"catalog.manage": "目录管理",
	"governance.manage": "治理管理",
	"iam.manage": "权限管理",
};

function normalizeText(value: unknown): string {
	if (Array.isArray(value)) return normalizeText(value[0]);
	return typeof value === "string" ? value.trim() : "";
}

function readAttribute(attributes: Record<string, string[]> | undefined, keys: string[]): string {
	return pickAttributeValue(attributes, keys);
}

function deptMatches(dept: DeptDto, code: string): boolean {
	const normalized = code.trim();
	if (!normalized) return false;
	const candidates = [dept.code, String(dept.id ?? ""), ...(dept.aliases || [])]
		.map((item) => String(item || "").trim())
		.filter(Boolean);
	return candidates.some((item) => item === normalized);
}

export default function ProfilePage() {
	const userInfo = useUserInfo();
	const [departments, setDepartments] = useState<DeptDto[]>([]);
	const [roleCatalog, setRoleCatalog] = useState<KeycloakRole[]>([]);
	const [directoryUser, setDirectoryUser] = useState<UserDirectoryEntry | null>(null);

	const username = userInfo.username || "";
	const email = userInfo.email || "";
	const avatar = userInfo.avatar || DEFAULT_AVATAR;
	const roles: string[] = Array.isArray(userInfo.roles) ? (userInfo.roles as string[]) : [];
	const permissions: string[] = Array.isArray(userInfo.permissions) ? (userInfo.permissions as string[]) : [];
	const attributes = (userInfo as any)?.attributes as Record<string, string[]> | undefined;

	const displayName = resolveProfileName({
		storeFullName:
			directoryUser?.displayName ||
			directoryUser?.fullName ||
			(userInfo as any)?.fullName ||
			(userInfo as any)?.displayName ||
			(userInfo as any)?.nameZh ||
			(userInfo as any)?.name,
		storeFirstName: (userInfo as any)?.firstName,
		username,
		fallbackName: null,
		attributeSources: [attributes],
		pickAttributeValue,
	});

	const deptCode =
		readAttribute(attributes, ["dept_code", "deptCode", "department"]) ||
		normalizeText((userInfo as any)?.deptCode) ||
		normalizeText((userInfo as any)?.dept_code) ||
		normalizeText((userInfo as any)?.department);
	const personnelLevel =
		readAttribute(attributes, [
			"personnel_security_level",
			"personnel_level",
			"person_security_level",
			"person_level",
		]) ||
		normalizeText((userInfo as any)?.personnel_level) ||
		normalizeText((userInfo as any)?.person_security_level);

	useEffect(() => {
		let alive = true;
		void Promise.all([
			listDepartments(),
			listRealmRoles(),
			username ? searchUsers(username) : Promise.resolve([]),
		])
			.then(([deptList, rolesList, users]) => {
				if (!alive) return;
				setDepartments(Array.isArray(deptList) ? deptList : []);
				setRoleCatalog(Array.isArray(rolesList) ? rolesList : []);
				const matched =
					Array.isArray(users)
						? users.find((item) => String(item.username || "").trim().toLowerCase() === username.toLowerCase()) || null
						: null;
				setDirectoryUser(matched);
			})
			.catch((error) => {
				console.warn("[ProfilePage] failed to load profile display dictionaries:", error);
			});
		return () => {
			alive = false;
		};
	}, [username]);

	const deptName = useMemo(() => {
		if (directoryUser?.deptName) return directoryUser.deptName;
		const effectiveDeptCode = directoryUser?.deptCode || deptCode;
		if (!effectiveDeptCode) return "";
		const matched = departments.find((item) => deptMatches(item, effectiveDeptCode));
		return matched?.nameZh || matched?.nameEn || "";
	}, [departments, deptCode, directoryUser]);
	const effectiveDeptCode = directoryUser?.deptCode || deptCode;

	const visibleRoles = useMemo(() => {
		const roleMap = new Map<string, KeycloakRole>();
		for (const role of roleCatalog) {
			const key = String(role?.name || "").trim().toLowerCase();
			if (key) roleMap.set(key, role);
		}

		return roles
			.map((roleName) => {
				const rawName = String(roleName || "").trim();
				if (isHiddenDefaultRole(rawName)) return null;
				const matched = roleMap.get(rawName.toLowerCase());
				const description = String(matched?.description || "").trim();
				return {
					key: rawName,
					label: description || rawName,
				};
			})
			.filter((item): item is { key: string; label: string } => Boolean(item));
	}, [roleCatalog, roles]);
	const visiblePermissions = useMemo(
		() =>
			permissions
				.map((permission) => String(permission || "").trim())
				.filter((permission) => permission && !HIDDEN_SYNTHETIC_PERMISSIONS.has(permission))
				.map((permission) => ({
					key: permission,
					label: PERMISSION_LABELS[permission] || permission,
				})),
		[permissions],
	);

	return (
		<div className="mx-auto max-w-3xl space-y-6 p-6">
			<Card>
				<div className="flex items-center gap-6">
					<img
						className="h-20 w-20 rounded-2xl border border-border/50 object-cover"
						src={avatar}
						alt={username}
					/>
					<div>
						<h2 className="text-xl font-semibold">{displayName}</h2>
						<p className="text-sm text-muted-foreground">@{username}</p>
					</div>
				</div>
			</Card>

			<Card title="基本信息">
				<Descriptions column={1} colon={false} labelStyle={{ width: 120 }}>
					<Descriptions.Item label={<><UserOutlined className="mr-1" />用户名</>}>
						{username || "-"}
					</Descriptions.Item>
					<Descriptions.Item label={<><UserOutlined className="mr-1" />姓名</>}>
						{displayName || "-"}
					</Descriptions.Item>
					<Descriptions.Item label={<><MailOutlined className="mr-1" />邮箱</>}>
						{email || "-"}
					</Descriptions.Item>
					{effectiveDeptCode && (
						<Descriptions.Item label="部门">
							{deptName || "-"}
						</Descriptions.Item>
					)}
					{personnelLevel && (
						<Descriptions.Item label="密级">
							{formatPersonLevel(personnelLevel)}
						</Descriptions.Item>
					)}
				</Descriptions>
			</Card>

			<Card title={<><SafetyCertificateOutlined className="mr-1" />角色与权限</>}>
				<div className="space-y-4">
					<div>
						<div className="mb-2 text-sm font-medium text-foreground">角色</div>
						<div className="flex flex-wrap gap-2">
							{visibleRoles.length > 0
								? visibleRoles.map((role) => (
										<Tag key={role.key} color="blue">
											{role.label}
										</Tag>
									))
								: <span className="text-sm text-muted-foreground">暂无角色</span>
							}
						</div>
					</div>
					{visiblePermissions.length > 0 && (
						<div>
							<div className="mb-2 text-sm font-medium text-foreground">权限</div>
							<div className="flex flex-wrap gap-2">
								{visiblePermissions.map((perm) => (
									<Tag key={perm.key} color="green">
										{perm.label}
									</Tag>
								))}
							</div>
						</div>
					)}
				</div>
			</Card>
		</div>
	);
}
