import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { TreeSelect } from "antd";
import type { CreateUserRequest, KeycloakRole, KeycloakUser, UpdateUserRequest, UserProfileConfig } from "#/keycloak";
import { KeycloakRoleService, KeycloakUserProfileService, KeycloakUserService } from "@/api/services/keycloakService";
import { adminApi } from "@/admin/api/adminApi";
import { POSITION_SUGGESTIONS } from "@/constants/user";
import type { OrganizationNode } from "@/admin/types";
import { Icon } from "@/components/icon";
import { Alert, AlertDescription } from "@/ui/alert";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Switch } from "@/ui/switch";
import { UserProfileField } from "./user-profile-field";
import { t } from "@/locales/i18n";
import {
	DATA_SECURITY_LEVEL_LABELS,
	PERSON_SECURITY_LEVELS,
	deriveDataLevels,
	isApplicationAdminRole,
	isDataRole,
	isGovernanceRole,
} from "@/constants/governance";

const RESERVED_PROFILE_ATTRIBUTE_NAMES = [
	"username",
	"email",
	"firstName",
	"lastName",
	"fullName",
	"locale",
	"department",
	"position",
	"person_security_level",
	"personnel_security_level",
	"person_level",
	"data_levels",
];

const DATA_LEVEL_LABEL_MAP = DATA_SECURITY_LEVEL_LABELS as Record<string, string>;

type OrgTreeOption = {
	value: string;
	title: string;
	key: string;
	selectable: boolean;
	children?: OrgTreeOption[];
};

const normalizeGroupPath = (path: string | undefined): string => {
	if (!path) return '';
	return path.startsWith('/') ? path : `/${path}`;
};

const areGroupPathsEqual = (current: string[], original: string[]): boolean => {
	if (current.length !== original.length) {
		return false;
	}
	return current.every((value, index) => value === original[index]);
};

const buildOrgOptions = (
	nodes: OrganizationNode[] = [],
	ancestors: string[] = [],
	index: Record<string, OrganizationNode>,
): OrgTreeOption[] => {
	return nodes.map((node) => {
		const segment = node.name ?? '';
		const nextPath = segment ? [...ancestors, segment] : [...ancestors];
		const groupPath = normalizeGroupPath(node.groupPath ?? `/${nextPath.join('/')}`);
		index[groupPath] = node;
		return {
			value: groupPath,
			title: segment || groupPath,
			key: groupPath,
			selectable: Boolean(groupPath),
			children:
				node.children && node.children.length > 0
					? buildOrgOptions(node.children, nextPath, index)
					: undefined,
		};
	});
};

interface UserModalProps {
	open: boolean;
	mode: "create" | "edit";
	user?: KeycloakUser;
	onCancel: () => void;
	onSuccess: () => void;
}

interface FormData {
	username: string;
	fullName: string;
	email: string;
	enabled: boolean;
	emailVerified: boolean;
	attributes: Record<string, string[]>;
}

interface RoleChange {
	role: KeycloakRole;
	action: "add" | "remove";
}

interface FormState {
	originalData: FormData;
	originalRoles: KeycloakRole[];
	roleChanges: RoleChange[];
	groupPaths: string[];
}

const createEmptyFormData = (): FormData => ({
	username: "",
	fullName: "",
	email: "",
	enabled: true,
	emailVerified: false,
	attributes: {},
});

const createEmptyFormState = (): FormState => ({
	originalData: createEmptyFormData(),
	originalRoles: [],
	roleChanges: [],
	groupPaths: [],
});

export default function UserModal({ open, mode, user, onCancel, onSuccess }: UserModalProps) {
	const [formData, setFormData] = useState<FormData>(() => createEmptyFormData());
	const [formState, setFormState] = useState<FormState>(() => createEmptyFormState());
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string>("");
	const [roles, setRoles] = useState<KeycloakRole[]>([]);
	const [userRoles, setUserRoles] = useState<KeycloakRole[]>([]);
	const [roleError, setRoleError] = useState<string>("");
	const [personLevel, setPersonLevel] = useState<string>("GENERAL");
	const [userProfileConfig, setUserProfileConfig] = useState<UserProfileConfig | null>(null);
	const [profileLoading, setProfileLoading] = useState(false);

	const [orgOptions, setOrgOptions] = useState<OrgTreeOption[]>([]);
	const [orgIndex, setOrgIndex] = useState<Record<string, OrganizationNode>>({});
	const [orgLoading, setOrgLoading] = useState(false);
	const [selectedGroupPaths, setSelectedGroupPaths] = useState<string[]>([]);

	const loadOrganizations = useCallback(async () => {
		setOrgLoading(true);
		try {
			const index: Record<string, OrganizationNode> = {};
			const tree = await adminApi.getOrganizations();
			const options = buildOrgOptions(tree ?? [], [], index);
			setOrgOptions(options);
			setOrgIndex(index);
		} catch (error: any) {
			console.error('Error loading organizations:', error);
			toast.error(`加载组织结构失败: ${error?.message || '未知错误'}`);
		} finally {
			setOrgLoading(false);
		}
	}, []);

	const normalizeAttributesForState = useCallback(
		(attributes: Record<string, string[]> = {}, level?: string): Record<string, string[]> => {
			const cloned: Record<string, string[]> = {};
			Object.entries(attributes).forEach(([key, value]) => {
				if (Array.isArray(value)) {
					cloned[key] = [...value];
				}
			});

			const candidateLevel = (
				level ||
				cloned.personnel_security_level?.[0] ||
				cloned.person_security_level?.[0] ||
				cloned.person_level?.[0] ||
				""
			).toUpperCase();
			const resolvedLevel = PERSON_SECURITY_LEVELS.some((option) => option.value === candidateLevel)
				? candidateLevel
				: undefined;

			if (resolvedLevel) {
				cloned.personnel_security_level = [resolvedLevel];
				cloned.person_security_level = [resolvedLevel];
				cloned.person_level = [resolvedLevel];
			} else {
				delete cloned.personnel_security_level;
				delete cloned.person_security_level;
				delete cloned.person_level;
			}
			delete cloned.data_levels;
			delete (cloned as Record<string, unknown>)["dataLevels"];

			Object.keys(cloned).forEach((key) => {
				const value = cloned[key];
				if (Array.isArray(value)) {
					const sanitized = value
						.map((item) => (typeof item === "string" ? item.trim() : item))
						.filter((item): item is string => typeof item === "string" && item.length > 0);

					if (sanitized.length > 0) {
						cloned[key] = sanitized;
					} else {
						delete cloned[key];
					}
				}
			});

			return cloned;
		},
		[],
	);

	const buildAttributesPayload = useCallback((): Record<string, string[]> => {
		return normalizeAttributesForState(formData.attributes, personLevel);
	}, [formData.attributes, normalizeAttributesForState, personLevel]);

	const derivedDataLevels = useMemo(() => deriveDataLevels(personLevel), [personLevel]);
	const resolveDataLevelLabel = useCallback(
		(level: string) => DATA_LEVEL_LABEL_MAP[level] ?? level.replace(/^DATA_/, "").replace(/_/g, " "),
		[],
	);

	const updateSingleValueAttribute = (key: string, value: string) => {
		setFormData((prev) => {
			const nextAttributes = { ...prev.attributes };
			const trimmed = value.trim();
			if (trimmed) {
				nextAttributes[key] = [trimmed];
			} else {
				delete nextAttributes[key];
			}

			return {
				...prev,
				attributes: nextAttributes,
			};
		});
	};

	const handleProfileFieldChange = (attributeName: string, value: string | string[]) => {
		setFormData((prev) => {
			const nextAttributes = { ...prev.attributes };
			const normalizedValues = Array.isArray(value)
				? value.map((item) => item.trim()).filter((item) => item.length > 0)
				: typeof value === "string" && value.trim().length > 0
					? [value.trim()]
					: [];

			if (normalizedValues.length > 0) {
				nextAttributes[attributeName] = normalizedValues;
			} else {
				delete nextAttributes[attributeName];
			}

			return {
				...prev,
				attributes: nextAttributes,
			};
		});
	};

	const loadUserProfileConfig = useCallback(async () => {
		setProfileLoading(true);
		try {
			const config = await KeycloakUserProfileService.getUserProfileConfig();
			setUserProfileConfig(config);
		} catch (err) {
			console.error("Error loading user profile config:", err);
		} finally {
			setProfileLoading(false);
		}
	}, []);

	const loadRoles = useCallback(async () => {
		try {
			const rolesData = await KeycloakRoleService.getAllRealmRoles();
			setRoles(rolesData);
		} catch (err) {
			setRoleError("加载角色列表失败");
			console.error("Error loading roles:", err);
		}
	}, []);

	const loadUserRoles = useCallback(async (userId: string) => {
		try {
			const userRolesData = await KeycloakUserService.getUserRoles(userId);
			setUserRoles(userRolesData);
			return userRolesData;
		} catch (err) {
			setRoleError("加载用户角色失败");
			console.error("Error loading user roles:", err);
			return [];
		}
	}, []);

	const handlePersonLevelChange = useCallback(
		(level: string) => {
			setPersonLevel(level);
			setFormData((prev) => ({
				...prev,
				attributes: normalizeAttributesForState(prev.attributes, level),
			}));
		},
		[normalizeAttributesForState],
	);

	const handleOrganizationChange = (value: string | string[] | null) => {
		const nextValue = Array.isArray(value) ? value[0] : value;
		if (!nextValue) {
			setSelectedGroupPaths([]);
			updateSingleValueAttribute("department", "");
			return;
		}
		const normalized = normalizeGroupPath(nextValue);
		setSelectedGroupPaths([normalized]);
		const node = orgIndex[normalized];
		updateSingleValueAttribute("department", node?.name ?? "");
	};

	useEffect(() => {
		if (!open) {
			setSelectedGroupPaths([]);
			return;
		}

		if (mode === "edit" && user) {
			const candidateLevel = (
				user.attributes?.personnel_security_level?.[0] ||
				user.attributes?.person_security_level?.[0] ||
				user.attributes?.person_level?.[0] ||
				"NON_SECRET"
			).toUpperCase();
			const resolvedLevel = PERSON_SECURITY_LEVELS.some((option) => option.value === candidateLevel)
				? candidateLevel
				: "NON_SECRET";
			const existingGroups = Array.isArray(user.groups)
				? user.groups.map((item) => normalizeGroupPath(item)).filter((item) => item)
				: [];
			const normalizedAttributes = normalizeAttributesForState(user.attributes || {}, resolvedLevel);
			const initialFormData: FormData = {
				username: user.username || "",
				fullName: (user.firstName || user.lastName || user.attributes?.fullname?.[0] || "").trim(),
				email: user.email || "",
				enabled: user.enabled ?? true,
				emailVerified: user.emailVerified ?? false,
				attributes: normalizedAttributes,
			};

			setPersonLevel(resolvedLevel);
			setUserRoles([]);
			setFormData(initialFormData);
			setSelectedGroupPaths(existingGroups);
			setFormState({
				originalData: {
					...initialFormData,
					attributes: { ...initialFormData.attributes },
				},
				originalRoles: [],
				roleChanges: [],
				groupPaths: existingGroups,
			});

			if (user.id) {
				loadUserRoles(user.id).then((fetchedRoles) => {
					setFormState((prev) => ({
						...prev,
						originalRoles: fetchedRoles,
					}));
				});
			}
    } else {
        // 创建用户时默认不允许选择“非密”，默认值调整为“一般”
        const defaultLevel = "GENERAL";
        const baseAttributes = normalizeAttributesForState({}, defaultLevel);
        const emptyData: FormData = {
            username: "",
            fullName: "",
            email: "",
				enabled: true,
				emailVerified: false,
				attributes: baseAttributes,
			};

        setPersonLevel(defaultLevel);
        setUserRoles([]);
        setFormData(emptyData);
        setSelectedGroupPaths([]);
			setFormState({
				originalData: {
					...emptyData,
					attributes: { ...emptyData.attributes },
				},
				originalRoles: [],
				roleChanges: [],
				groupPaths: [],
			});
		}

		setError("");
		setRoleError("");
	}, [open, mode, user, loadUserRoles, normalizeAttributesForState]);

	useEffect(() => {
		if (!open) {
			return;
		}

		loadRoles();
		loadUserProfileConfig();
		loadOrganizations();
	}, [open, loadRoles, loadUserProfileConfig, loadOrganizations]);

	const hasUserInfoChanged = (
		normalizedAttributes?: Record<string, string[]>,
		groupPaths?: string[],
	): boolean => {
		const { originalData, groupPaths: originalGroupPaths } = formState;
		const attributesToCompare = normalizedAttributes ?? buildAttributesPayload();
		const pathsToCompare = groupPaths ?? selectedGroupPaths;
		return (
			formData.username !== originalData.username ||
			formData.email !== originalData.email ||
			formData.fullName !== originalData.fullName ||
			formData.enabled !== originalData.enabled ||
			formData.emailVerified !== originalData.emailVerified ||
			JSON.stringify(attributesToCompare) !== JSON.stringify(originalData.attributes) ||
			!areGroupPathsEqual(pathsToCompare, originalGroupPaths)
		);
	};

    const handleSubmit = async () => {
		const username = formData.username.trim();
		const fullName = formData.fullName.trim();
		const email = formData.email.trim();

		if (!username) {
			setError("用户名不能为空");
			return;
		}

		if (!fullName) {
			setError("姓名不能为空");
			return;
		}

        const attributesPayload = buildAttributesPayload();
		const groupPathsPayload = selectedGroupPaths
			.map((item) => normalizeGroupPath(item))
			.filter((item) => item);

		// 创建用户时人员密级不得为“非密”
		if (personLevel?.toUpperCase?.() === "NON_SECRET") {
			setError("人员密级不允许为‘非密’，请选取更高密级。");
			return;
		}

		if (userProfileConfig?.attributes) {
			for (const attribute of userProfileConfig.attributes) {
				if (RESERVED_PROFILE_ATTRIBUTE_NAMES.includes(attribute.name)) {
					continue;
				}

				if (attribute.required) {
					const value = attributesPayload[attribute.name];
					if (!value || value.length === 0) {
						const displayName = attribute.displayName.replace(/\$\{([^}]*)\}/g, "$1");
						setError(`"${t(displayName) || attribute.name}" 是必填字段`);
						return;
					}
				}
			}
		}

		const hasUserInfoChanges = hasUserInfoChanged(attributesPayload, groupPathsPayload);
		const hasRoleChanges = formState.roleChanges.length > 0;

		if (mode === "edit" && !hasUserInfoChanges && !hasRoleChanges) {
			toast.info("没有检测到任何变更");
			return;
		}

		setLoading(true);
		setError("");

		try {
			if (mode === "create") {
				const createData: CreateUserRequest = {
					username,
					email: email || undefined,
					firstName: fullName,
					enabled: formData.enabled,
					emailVerified: formData.emailVerified,
					attributes: attributesPayload,
					groups: groupPathsPayload.length > 0 ? groupPathsPayload : undefined,
				};

				const response = await KeycloakUserService.createUser(createData);
				if (response.userId) {
					toast.success("用户创建请求已提交，等待审批");
				} else {
					toast.success("用户创建请求提交成功");
				}
			} else if (mode === "edit" && user?.id) {
				const emailPayload = email || (formState.originalData.email ? "" : undefined);

				if (hasUserInfoChanges) {
					const updateData: UpdateUserRequest = {
						id: user.id,
						username,
						email: emailPayload,
						firstName: fullName,
						enabled: formData.enabled,
						emailVerified: formData.emailVerified,
						attributes: attributesPayload,
						groups: groupPathsPayload.length > 0 ? groupPathsPayload : [],
					};

					const response = await KeycloakUserService.updateUser(user.id, updateData);
					if (response.message) {
						toast.success(`用户信息更新请求提交成功: ${response.message}`);
					} else {
						toast.success("用户信息更新请求提交成功");
					}
				}

				if (hasRoleChanges) {
					const rolesToAdd = formState.roleChanges.filter((rc) => rc.action === "add").map((rc) => rc.role);

					if (rolesToAdd.length > 0) {
						const response = await KeycloakUserService.assignRolesToUser(user.id, rolesToAdd);
						if (response.message) {
							toast.success(`角色分配请求提交成功: ${response.message}`);
						} else {
							toast.success("角色分配请求提交成功");
						}
					}

					const rolesToRemove = formState.roleChanges.filter((rc) => rc.action === "remove").map((rc) => rc.role);

					if (rolesToRemove.length > 0) {
						const response = await KeycloakUserService.removeRolesFromUser(user.id, rolesToRemove);
						if (response.message) {
							toast.success(`角色移除请求提交成功: ${response.message}`);
						} else {
							toast.success("角色移除请求提交成功");
						}
					}
				}
			}

			onSuccess();
		} catch (err: any) {
			setError(err.message || "操作失败");
			console.error("Error saving user:", err);
		} finally {
			setLoading(false);
		}
	};

	const resolveRoleBadgeVariant = (
		roleName: string,
	): "default" | "secondary" | "destructive" | "info" | "warning" | "success" | "error" | "outline" => {
		if (isDataRole(roleName)) return "secondary";
		if (isGovernanceRole(roleName)) return "warning";
		if (isApplicationAdminRole(roleName)) return "info";
		return "default";
	};

	const handleRoleToggle = (role: KeycloakRole) => {
		const hasRole = userRoles.some((r) => r.id === role.id);
		const roleName = role.name;

		if (isDataRole(roleName)) {
			toast.warning("数据密级角色由人员密级自动分配，请在上方调整人员密级。");
			return;
		}

		if (!hasRole) {
			if (isApplicationAdminRole(roleName) && userRoles.some((existing) => isGovernanceRole(existing.name))) {
				toast.error("请先移除治理类角色后再分配应用管理员角色。");
				return;
			}
			if (isGovernanceRole(roleName) && userRoles.some((existing) => isApplicationAdminRole(existing.name))) {
				toast.error("应用管理员角色与治理类角色互斥，请先移除应用管理员角色。");
				return;
			}
		}

		if (hasRole) {
			setUserRoles((prev) => prev.filter((r) => r.id !== role.id));
			setFormState((prev) => ({
				...prev,
				roleChanges: [
					...prev.roleChanges.filter((rc) => rc.role.id !== role.id || rc.action !== "add"),
					{ role, action: "remove" },
				],
			}));
		} else {
			setUserRoles((prev) => [...prev, role]);
			setFormState((prev) => ({
				...prev,
				roleChanges: [
					...prev.roleChanges.filter((rc) => rc.role.id !== role.id || rc.action !== "remove"),
					{ role, action: "add" },
				],
			}));
		}
	};

	const positionValue = formData.attributes.position?.[0] ?? "";
	const title = mode === "create" ? "创建用户" : "编辑用户";

	return (
		<Dialog open={open} onOpenChange={onCancel}>
			<DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
				</DialogHeader>

				<div className="space-y-6">
					{error && (
						<Alert variant="destructive">
							<AlertDescription>{error}</AlertDescription>
						</Alert>
					)}

					<Card>
						<CardHeader>
							<CardTitle className="text-lg">基本信息</CardTitle>
						</CardHeader>
						<CardContent className="space-y-4">
							<div className="grid grid-cols-2 gap-4">
								<div className="space-y-2">
									<Label htmlFor="username">用户名 *</Label>
									<Input
										id="username"
										value={formData.username}
										onChange={(e) =>
											setFormData((prev) => ({
												...prev,
												username: e.target.value,
											}))
										}
										placeholder="请输入用户名"
									/>
									<p className="text-xs text-muted-foreground">用户名即登录名，需要保持唯一。</p>
								</div>
								<div className="space-y-2">
									<Label htmlFor="fullName">姓名 *</Label>
									<Input
										id="fullName"
										value={formData.fullName}
										onChange={(e) =>
											setFormData((prev) => ({
												...prev,
												fullName: e.target.value,
											}))
										}
										placeholder="请输入姓名"
									/>
								</div>
								<div className="space-y-2">
									<Label htmlFor="email">邮箱</Label>
									<Input
										id="email"
										type="email"
										value={formData.email}
										onChange={(e) =>
											setFormData((prev) => ({
												...prev,
												email: e.target.value,
											}))
										}
										placeholder="请输入邮箱（可选）"
									/>
									<p className="text-xs text-muted-foreground">邮箱用于接收通知，可选填写。</p>
								</div>
								<div className="space-y-2">
									<Label htmlFor="department">组织机构（可选）</Label>
									<TreeSelect
										id="department"
										allowClear
										value={selectedGroupPaths[0] ?? undefined}
										treeData={orgOptions}
										showSearch
										treeNodeFilterProp="title"
										placeholder="请选择所属组织（可选）"
										style={{ width: "100%" }}
										dropdownStyle={{ maxHeight: 320, overflow: "auto" }}
										loading={orgLoading}
										treeDefaultExpandAll
										getPopupContainer={(triggerNode) => triggerNode.parentElement ?? document.body}
										onChange={(value) => handleOrganizationChange(value as string | string[] | null)}
										onSelect={(value) => handleOrganizationChange(value as string)}
									/>
									<p className="text-xs text-muted-foreground">如不选择，将暂不分配组织，可在审批后再调整。</p>
								</div>
								<div className="space-y-2">
									<Label htmlFor="position">职位</Label>
									<Input
										id="position"
										value={positionValue}
										onChange={(e) => updateSingleValueAttribute("position", e.target.value)}
										placeholder={`例如：${POSITION_SUGGESTIONS.join("、")}`}
									/>
								</div>
							</div>

							<div className="grid grid-cols-2 gap-4">
								<div className="flex items-center space-x-2">
									<Switch
										id="enabled"
										checked={formData.enabled}
										onCheckedChange={(checked) =>
											setFormData((prev) => ({
												...prev,
												enabled: checked,
											}))
										}
									/>
									<Label htmlFor="enabled">启用用户</Label>
								</div>
								<div className="flex items-center space-x-2 hidden">
									<Switch
										id="emailVerified"
										checked={formData.emailVerified}
										onCheckedChange={(checked) =>
											setFormData((prev) => ({
												...prev,
												emailVerified: checked,
											}))
										}
									/>
									<Label htmlFor="emailVerified">邮箱已验证</Label>
								</div>
							</div>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<CardTitle className="text-lg">安全属性</CardTitle>
						</CardHeader>
						<CardContent className="space-y-4">
							<div className="space-y-2">
								<Label>人员密级 *</Label>
								<Select value={personLevel} onValueChange={handlePersonLevelChange}>
									<SelectTrigger className="w-full justify-between">
										<SelectValue placeholder="请选择人员密级" />
									</SelectTrigger>
								<SelectContent>
									{PERSON_SECURITY_LEVELS.map((option) => (
										<SelectItem
											key={option.value}
											value={option.value}
											disabled={option.value === "NON_SECRET"}
											className={option.value === "NON_SECRET" ? "hidden" : undefined}
										>
											{option.label}（{option.value}）
										</SelectItem>
									))}
								</SelectContent>
								</Select>
								<p className="text-xs text-muted-foreground">
									人员密级决定自动分配的数据访问范围和可授予的数据密级角色。
								</p>
							</div>

							<div className="space-y-2">
								<Label>可访问数据密级（自动派生）</Label>
								<div className="flex flex-wrap gap-2">
									{derivedDataLevels.length > 0 ? (
										derivedDataLevels.map((level) => (
											<Badge key={level} variant="secondary">
												{resolveDataLevelLabel(level)}
											</Badge>
										))
									) : (
										<span className="text-muted-foreground text-sm">未配置人员密级</span>
									)}
								</div>
								<p className="text-xs text-muted-foreground">数据密级角色将由系统自动同步，请勿手动调整。</p>
							</div>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<CardTitle className="text-lg">扩展属性</CardTitle>
						</CardHeader>
						<CardContent>
							{profileLoading ? (
								<div className="flex items-center text-sm text-muted-foreground">
									<Icon icon="mdi:loading" className="animate-spin mr-2" />
									<span>加载配置中...</span>
								</div>
							) : userProfileConfig?.attributes ? (
								<div className="grid grid-cols-2 gap-4">
									{userProfileConfig.attributes
										.filter((attr) => !RESERVED_PROFILE_ATTRIBUTE_NAMES.includes(attr.name))
										.map((attribute) => (
											<UserProfileField
												key={attribute.name}
												attribute={attribute}
												value={formData.attributes[attribute.name]}
												onChange={(value) => handleProfileFieldChange(attribute.name, value)}
											/>
										))}
								</div>
							) : (
								<p className="text-sm text-muted-foreground">暂无额外属性配置。</p>
							)}
						</CardContent>
					</Card>

					{mode === "edit" && user?.id && (
						<Card>
							<CardHeader>
								<CardTitle className="text-lg">角色分配</CardTitle>
							</CardHeader>
							<CardContent>
								{roleError && (
									<Alert variant="destructive" className="mb-4">
										<AlertDescription>{roleError}</AlertDescription>
									</Alert>
								)}

								<div className="space-y-2">
									<Label>用户角色</Label>
									<div className="flex flex-wrap gap-2 mb-4">
										{userRoles.map((role) => {
											const allowRemoval = !isDataRole(role.name);
											return (
												<Badge key={role.id ?? role.name} variant={resolveRoleBadgeVariant(role.name)}>
													{role.name}
													{allowRemoval && (
														<Button
															variant="ghost"
															size="sm"
															className="ml-1 h-4 w-4 p-0"
															onClick={() => handleRoleToggle(role)}
														>
															<Icon icon="mdi:close" size={12} />
														</Button>
													)}
												</Badge>
											);
										})}
										{userRoles.length === 0 && <span className="text-muted-foreground">暂无分配角色</span>}
									</div>

									<Label>可用角色</Label>
									<div className="flex flex-wrap gap-2">
										{roles
											.filter((role) => !userRoles.some((ur) => ur.id === role.id))
											.filter((role) => !isDataRole(role.name))
											.map((role) => (
												<Badge
													key={role.id ?? role.name}
													variant={resolveRoleBadgeVariant(role.name)}
													className="cursor-pointer hover:bg-primary hover:text-primary-foreground"
													onClick={() => handleRoleToggle(role)}
												>
													{role.name}
													<Icon icon="mdi:plus" size={12} className="ml-1" />
												</Badge>
											))}
									</div>
									<p className="text-xs text-muted-foreground mt-2">
										治理角色与应用管理员角色互斥；数据密级角色由系统根据人员密级自动管理。
									</p>
								</div>
							</CardContent>
						</Card>
					)}
				</div>

				<DialogFooter>
					<Button variant="outline" onClick={onCancel}>
						取消
					</Button>
					<Button onClick={handleSubmit} disabled={loading}>
						{loading ? "处理中..." : "确定"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
