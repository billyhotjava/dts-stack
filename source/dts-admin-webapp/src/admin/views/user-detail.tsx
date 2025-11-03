import { CUSTOM_USER_ATTRIBUTE_KEYS } from "@/constants/user";
import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import type { KeycloakGroup, KeycloakRole, KeycloakUser, UserProfileConfig } from "#/keycloak";
import type { OrganizationNode } from "@/admin/types";
import { adminApi } from "@/admin/api/adminApi";
import { KeycloakGroupService, KeycloakUserProfileService, KeycloakUserService } from "@/api/services/keycloakService";
import { isKeycloakBuiltInRole } from "@/constants/keycloak-roles";
import { GLOBAL_CONFIG } from "@/global-config";
import { Icon } from "@/components/icon";
import { useParams, useRouter } from "@/routes/hooks";
import { Alert, AlertDescription } from "@/ui/alert";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { getAttributeDisplayName } from "@/utils/translation";
// import ResetPasswordModal from "./reset-password-modal";
import UserModal from "./user-management.modal";
import { ApprovalStatus } from "./user-approval-status";

const RESERVED_PROFILE_ATTRIBUTES = new Set<string>([
	"username",
	"email",
	"firstName",
	"lastName",
	"locale",
	"fullName",
	...CUSTOM_USER_ATTRIBUTE_KEYS,
	// 合并人员密级显示：排除 person_security_level，避免动态区域重复
	"person_security_level",
]);

const getSingleAttributeValue = (attributes: Record<string, string[]> | undefined, key: string) => {
	const values = attributes?.[key];
	if (!values || values.length === 0) {
		return "";
	}
	const nonEmpty = values.find((item) => item && item.trim());
	return nonEmpty ?? values[0] ?? "";
};

const leafOfPath = (path?: string) => {
	if (!path) return "";
	const idx = path.lastIndexOf("/");
	return idx >= 0 && idx + 1 < path.length ? path.substring(idx + 1) : path;
};

export default function UserDetailView() {
	const { id } = useParams();
	const { back } = useRouter();

	const [user, setUser] = useState<KeycloakUser | null>(null);
	const [userRoles, setUserRoles] = useState<KeycloakRole[]>([]);
	const [userGroups, setUserGroups] = useState<KeycloakGroup[]>([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<string>("");
	// Role catalogs
	// 1) description catalog: ROLE_NAME -> description
	const [roleCatalog, setRoleCatalog] = useState<Record<string, string>>({});
	// 2) display name catalog: ROLE_NAME -> 中文名称/展示名
	const [roleDisplayNameCatalog, setRoleDisplayNameCatalog] = useState<Record<string, string>>({});
	const [departmentName, setDepartmentName] = useState<string>("");
	const [orgIndexById, setOrgIndexById] = useState<Record<string, OrganizationNode>>({});

	// UserProfile相关状态
	const [, setUserProfileConfig] = useState<UserProfileConfig | null>(null);
	const [_profileLoading, setProfileLoading] = useState(false);
	const [_profileError, setProfileError] = useState<string>("");

	// Modal状态
	const [editModal, setEditModal] = useState(false);
	// const [resetPasswordModal, setResetPasswordModal] = useState(false);

	// 加载UserProfile配置
	const loadUserProfileConfig = useCallback(async () => {
		setProfileLoading(true);
		setProfileError("");
		try {
			const config = await KeycloakUserProfileService.getUserProfileConfig();
			setUserProfileConfig(config);
		} catch (err) {
			console.error("Error loading user profile config:", err);
			setProfileError("加载用户配置文件失败");
		} finally {
			setProfileLoading(false);
		}
	}, []);

	// 加载用户详情
	const loadUserDetail = useCallback(async () => {
		if (!id) return;
		setLoading(true);
		setError("");
		try {
			const [userData, rolesData, groupsData] = await Promise.all([
				KeycloakUserService.getUserById(id),
				KeycloakUserService.getUserRoles(id),
				KeycloakGroupService.getUserGroups(id),
			]);
			setUser(userData);
			// Hide Keycloak 内置/默认角色（如 default-roles-*、offline_access、uma_authorization、realm-management 等）
			const filtered = (rolesData || []).filter((r) => {
				const name = (r?.name || "").toString().trim();
				if (!name) return false;
				const lower = name.toLowerCase();
				if (GLOBAL_CONFIG.hideDefaultRoles && lower.startsWith("default-roles-")) return false;
				if (GLOBAL_CONFIG.hideBuiltinRoles && isKeycloakBuiltInRole(r as any)) return false;
				if (name.startsWith("ROLE_")) {
					const withoutPrefix = name.slice(5);
					if (withoutPrefix === withoutPrefix.toUpperCase()) return false;
				}
				if (lower.startsWith("offline_access") || lower.startsWith("uma_authorization")) return false;
				return true;
			});
			setUserRoles(filtered);
			setUserGroups(groupsData);
		} catch (err: any) {
			console.error("Error loading user detail:", err);
			setError(err.message || "加载用户信息失败");
		} finally {
			setLoading(false);
		}
	}, [id]);

	useEffect(() => {
		loadUserDetail();
		loadUserProfileConfig();
	}, [loadUserDetail, loadUserProfileConfig]);

	// Load admin role catalog once to enrich role descriptions & display names
	useEffect(() => {
		(async () => {
			try {
				const roles = await adminApi.getAdminRoles();
				const descMap: Record<string, string> = {};
				const displayMap: Record<string, string> = {};
				const put = (key: string, cn: string | undefined) => {
					const k = (key || "").toString().trim().toUpperCase();
					if (!k) return;
					const v = (cn || "").toString().trim();
					if (v) displayMap[k] = v;
				};
				(roles || []).forEach((r: any) => {
					const name = (r?.name || "").toString().trim().toUpperCase();
					const code = (r?.code || r?.roleId || r?.legacyName || "").toString().trim().toUpperCase();
					const desc = (r?.description || "").toString();
					const display = (r?.displayName || r?.name || "").toString();
					if (name) descMap[name] = desc;
					if (name) put(name, display);
					if (code) put(code, display);
				});
				setRoleCatalog(descMap);
				setRoleDisplayNameCatalog(displayMap);
			} catch (e) {
				// best-effort only
			}
		})();
	}, []);

	// Build organization index when needed for mapping dept_code -> name
	const ensureOrgIndex = useCallback(async () => {
		if (Object.keys(orgIndexById).length > 0) return;
		try {
			const tree = await adminApi.getOrganizations({ auditSilent: true });
			const index: Record<string, OrganizationNode> = {};
			const visit = (nodes?: OrganizationNode[]) => {
				if (!nodes) return;
				for (const n of nodes) {
					index[String(n.id)] = n;
					if (n.children && n.children.length) visit(n.children);
				}
			};
			visit(tree);
			setOrgIndexById(index);
		} catch (e) {
			// best-effort; ignore failures so page still renders
		}
	}, [orgIndexById]);

	// Derive department display name
	useEffect(() => {
		const compute = async () => {
			// 1) Prefer group path leaf from detailed groups
			const pathFromUserGroups = userGroups && userGroups.length > 0 ? userGroups[0]?.path : undefined;
			const pathFromUser = user?.groups && user.groups.length > 0 ? user.groups[0] : undefined;
			const chosenPath = pathFromUserGroups || pathFromUser || "";
			const leaf = leafOfPath(chosenPath);
			if (leaf) {
				setDepartmentName(leaf);
				return;
			}
			// 2) Fallback to dept_code attribute: try map id -> org name; else show raw value
			const dc = getSingleAttributeValue(user?.attributes, "dept_code");
			if (dc) {
				// Try to map to organization name if looks like id
				if (!orgIndexById[dc]) {
					await ensureOrgIndex();
				}
				const node = orgIndexById[dc];
				setDepartmentName(node?.name || dc);
				return;
			}
			setDepartmentName("");
		};
		compute();
	}, [user, userGroups, orgIndexById, ensureOrgIndex]);

	// 角色表格列定义
	const roleColumns: ColumnsType<KeycloakRole> = [
		{
			title: "角色名称",
			dataIndex: "name",
			key: "name",
			render: (_: any, record) => {
				const key = (record?.name || "").toString().trim().toUpperCase();
				const display = roleDisplayNameCatalog[key];
				return (display || record?.name || "-").toString();
			},
		},
		{
			title: "描述",
			dataIndex: "description",
			key: "description",
			render: (desc: string, record) => {
				const fallback = roleCatalog[(record?.name || "").toString().trim().toUpperCase()] || "";
				const text = (desc || fallback || "").toString().trim();
				return text ? text : "-";
			},
		},
		{
			title: "类型",
			dataIndex: "clientRole",
			key: "clientRole",
			align: "center",
			render: (clientRole: boolean) => (
				<Badge variant={clientRole ? "secondary" : "default"}>{clientRole ? "客户端角色" : "系统角色"}</Badge>
			),
		},
	];

	// 组表格列定义
	const groupColumns: ColumnsType<KeycloakGroup> = [
		{ title: "组名称", dataIndex: "name", key: "name" },
		{ title: "路径", dataIndex: "path", key: "path" },
	];

	// 合并人员密级：优先 personnel_security_level，其次 person_security_level，再次 person_level
	const personnelSecurityLevelRaw =
		getSingleAttributeValue(user?.attributes, "personnel_security_level") ||
		getSingleAttributeValue(user?.attributes, "person_security_level") ||
		getSingleAttributeValue(user?.attributes, "person_level");
	const personnelSecurityLevel = (() => {
		const v = personnelSecurityLevelRaw || "";
		const upper = v.toUpperCase();
		if (upper === "CORE") return "核心";
		if (upper === "IMPORTANT") return "重要";
		if (upper === "GENERAL") return "一般";
		if (upper === "NON_SECRET") return "非密";
		return v;
	})();
	// departmentName is derived from groups or dept_code
	const phone =
		getSingleAttributeValue(user?.attributes, "phone") ||
		getSingleAttributeValue(user?.attributes, "phone_number") ||
		getSingleAttributeValue(user?.attributes, "mobile") ||
		getSingleAttributeValue(user?.attributes, "mobile_number");
	const fullName = (() => {
		const attrs = user?.attributes || {};
		const first = (user?.firstName || attrs?.firstName?.[0] || "").toString().trim();
		const last = (user?.lastName || attrs?.lastName?.[0] || "").toString().trim();
		const combined = `${first}${last}`.trim();
		if (combined) return combined;
		const candidates: Array<string | undefined> = [
			user?.fullName as string | undefined,
			attrs?.fullName?.[0],
			attrs?.full_name?.[0],
			attrs?.display_name?.[0],
		];
		for (const value of candidates) {
			const text = (value || "").toString().trim();
			if (text) return text;
		}
		return "";
	})();
	const email = user?.email || "";

	const getFilteredUserAttributes = () => {
		const attrs = user?.attributes || {};
		const entries = Object.entries(attrs).filter(([k]) => !RESERVED_PROFILE_ATTRIBUTES.has(k));
		return Object.fromEntries(entries) as Record<string, string[]>;
	};

	const getTranslatedAttributeDisplayName = (name: string) => getAttributeDisplayName(name) || name;

	if (loading) {
		return (
			<div className="p-4">
				<Alert variant="default">
					<AlertDescription>加载中...</AlertDescription>
				</Alert>
			</div>
		);
	}

	if (error) {
		return (
			<div className="p-4">
				<Alert variant="destructive">
					<AlertDescription>{error}</AlertDescription>
				</Alert>
			</div>
		);
	}

	if (!user) return null;

	return (
		<div className="mx-auto w-full max-w-[1200px] px-6 py-6 space-y-6">
			<div className="flex items-center gap-3">
				<Button variant="outline" onClick={back}>
					<Icon icon="mdi:arrow-left" className="mr-1" /> 返回
				</Button>
				<h1 className="text-xl font-semibold">用户详情</h1>
				<div className="ml-auto flex items-center gap-2">
					{/* <Button variant="outline" onClick={() => setResetPasswordModal(true)}>
            <Icon icon="solar:lock-password-unlocked-broken" className="mr-1" /> 重置密码
          </Button> */}
					<Button onClick={() => setEditModal(true)}>
						<Icon icon="solar:pen-new-square-broken" className="mr-1" /> 编辑
					</Button>
				</div>
			</div>

			{/* 基本信息 */}
			<Card>
				<CardHeader>
					<CardTitle>基本信息</CardTitle>
				</CardHeader>
				<CardContent>
					<div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
						<div>
							<span className="text-sm font-medium text-muted-foreground">用户名</span>
							<p className="mt-1 text-sm">{user.username || "-"}</p>
						</div>
						<div>
							<span className="text-sm font-medium text-muted-foreground">姓名</span>
							<p className={`mt-1 text-sm ${fullName ? "" : "text-muted-foreground"}`}>{fullName || "-"}</p>
						</div>
						<div>
							<span className="text-sm font-medium text-muted-foreground">邮箱</span>
							<p className={`mt-1 text-sm ${email ? "" : "text-muted-foreground"}`}>{email || "-"}</p>
						</div>
						<div>
							<span className="text-sm font-medium text-muted-foreground">状态</span>
							<div className="mt-1">
								<Badge variant={user.enabled ? "success" : "destructive"}>{user.enabled ? "启用" : "禁用"}</Badge>
							</div>
						</div>
						<div>
							<span className="text-sm font-medium text-muted-foreground">审批状态</span>
							<div className="mt-1">
								<ApprovalStatus userId={user.id || ""} />
							</div>
						</div>
						<div>
							<span className="text-sm font-medium text-muted-foreground">人员密级</span>
							<div className="mt-1">
								{personnelSecurityLevel ? (
									<Badge variant="outline">{personnelSecurityLevel}</Badge>
								) : (
									<span className="text-muted-foreground text-sm">-</span>
								)}
							</div>
						</div>
						<div>
							<span className="text-sm font-medium text-muted-foreground">部门</span>
							<p className={`mt-1 text-sm ${departmentName ? "" : "text-muted-foreground"}`}>{departmentName || "-"}</p>
						</div>
						<div>
							<span className="text-sm font-medium text-muted-foreground">联系方式</span>
							<p className={`mt-1 text-sm ${phone ? "" : "text-muted-foreground"}`}>{phone || "-"}</p>
						</div>

						{Object.entries(getFilteredUserAttributes()).map(([name, values]) => (
							<div key={name}>
								<span className="text-sm font-medium text-muted-foreground">
									{getTranslatedAttributeDisplayName(name)}
								</span>
								<div className="mt-1">
									{values.length > 0 ? (
										values.map((value) => (
											<Badge key={value} variant="outline" className="mr-1 mb-1">
												{value}
											</Badge>
										))
									) : (
										<span className="text-muted-foreground text-sm">-</span>
									)}
								</div>
							</div>
						))}

						{user.createdTimestamp && (
							<div>
								<span className="text-sm font-medium text-muted-foreground">创建时间</span>
								<p className="mt-1 text-sm">{new Date(user.createdTimestamp).toLocaleString("zh-CN")}</p>
							</div>
						)}
					</div>
				</CardContent>
			</Card>

			{/* 用户角色 */}
			<Card>
				<CardHeader>
					<CardTitle className="flex items-center justify-between">
						<span>用户角色 ({userRoles.length})</span>
					</CardTitle>
				</CardHeader>
				<CardContent>
					{userRoles.length > 0 ? (
						<Table
							rowKey={(record, index) => record.id ?? record.name ?? (index != null ? `role-${index}` : "role-unknown")}
							columns={roleColumns}
							dataSource={userRoles}
							pagination={false}
							size="small"
						/>
					) : (
						<div className="text-center py-8 text-muted-foreground">
							<Icon icon="mdi:account-group-outline" size={48} className="mx-auto mb-2 opacity-50" />
							<p>暂无分配角色</p>
						</div>
					)}
				</CardContent>
			</Card>

			{/* 用户组（可隐藏） */}
			<Card className="hidden">
				<CardHeader>
					<CardTitle>所属组 ({userGroups.length})</CardTitle>
				</CardHeader>
				<CardContent>
					{userGroups.length > 0 ? (
						<Table
							rowKey={(record, index) =>
								record.id ?? record.name ?? record.path ?? (index != null ? `group-${index}` : "group-unknown")
							}
							columns={groupColumns}
							dataSource={userGroups}
							pagination={false}
							size="small"
						/>
					) : (
						<div className="text-center py-8 text-muted-foreground">
							<Icon icon="mdi:account-group-outline" size={48} className="mx-auto mb-2 opacity-50" />
							<p>暂无所属组</p>
						</div>
					)}
				</CardContent>
			</Card>

			{/* 编辑用户Modal */}
			<UserModal
				open={editModal}
				mode="edit"
				user={user}
				onCancel={() => setEditModal(false)}
				onSuccess={() => {
					setEditModal(false);
					loadUserDetail();
				}}
			/>

			{/* 重置密码Modal */}
			{/* <ResetPasswordModal
        open={resetPasswordModal}
        userId={user?.id || ""}
        username={user?.username || ""}
        onCancel={() => setResetPasswordModal(false)}
        onSuccess={() => {}}
      /> */}
		</div>
	);
}
