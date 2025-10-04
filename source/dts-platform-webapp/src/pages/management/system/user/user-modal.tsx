import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import type { CreateUserRequest, KeycloakRole, KeycloakUser, UpdateUserRequest } from "#/keycloak";
import { KeycloakRoleService, KeycloakUserService } from "@/api/services/keycloakService";
import { DEPARTMENT_SUGGESTIONS, POSITION_SUGGESTIONS } from "@/constants/user";
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
import {
	PERSON_SECURITY_LEVELS,
	isApplicationAdminRole,
	isDataRole,
	isGovernanceRole,
} from "@/constants/governance";

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
});

export default function UserModal({ open, mode, user, onCancel, onSuccess }: UserModalProps) {
	const [formData, setFormData] = useState<FormData>(() => createEmptyFormData());
	const [formState, setFormState] = useState<FormState>(() => createEmptyFormState());
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string>("");
	const [roles, setRoles] = useState<KeycloakRole[]>([]);
	const [userRoles, setUserRoles] = useState<KeycloakRole[]>([]);
	const [roleError, setRoleError] = useState<string>("");
	const [personLevel, setPersonLevel] = useState<string>("NON_SECRET");

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

	useEffect(() => {
		if (!open) {
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
			setFormState({
				originalData: {
					...initialFormData,
					attributes: { ...initialFormData.attributes },
				},
				originalRoles: [],
				roleChanges: [],
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
			const defaultLevel = "NON_SECRET";
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
			setFormState({
				originalData: {
					...emptyData,
					attributes: { ...emptyData.attributes },
				},
				originalRoles: [],
				roleChanges: [],
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
	}, [open, loadRoles]);

	const hasUserInfoChanged = (normalizedAttributes?: Record<string, string[]>): boolean => {
		const { originalData } = formState;
		const attributesToCompare = normalizedAttributes ?? buildAttributesPayload();
		return (
			formData.username !== originalData.username ||
			formData.email !== originalData.email ||
			formData.fullName !== originalData.fullName ||
			formData.enabled !== originalData.enabled ||
			formData.emailVerified !== originalData.emailVerified ||
			JSON.stringify(attributesToCompare) !== JSON.stringify(originalData.attributes)
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

		// 校验必填的人员密级
		if (!personLevel || personLevel === "") {
			setError("人员密级为必填项，请选择");
			return;
		}

		const attributesPayload = buildAttributesPayload();

		const hasUserInfoChanges = hasUserInfoChanged(attributesPayload);
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

	const resolveRoleBadgeVariant = (roleName: string): "default" | "secondary" | "destructive" | "info" | "warning" | "success" | "error" | "outline" => {
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

	const departmentValue = formData.attributes.department?.[0] ?? "";
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
									<Label htmlFor="department">部门</Label>
									<Input
										id="department"
										value={departmentValue}
										onChange={(e) => updateSingleValueAttribute("department", e.target.value)}
										placeholder={`例如：${DEPARTMENT_SUGGESTIONS.join("、")}`}
									/>
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
								<div className="space-y-2">
									<Label htmlFor="personLevel">人员密级 *</Label>
									<Select value={personLevel} onValueChange={handlePersonLevelChange}>
										<SelectTrigger className="w-full justify-between">
											<SelectValue placeholder="请选择人员密级" />
										</SelectTrigger>
										<SelectContent>
											{PERSON_SECURITY_LEVELS.map((option) => (
												<SelectItem key={option.value} value={option.value}>
													{option.label}（{option.value}）
												</SelectItem>
											))}
										</SelectContent>
									</Select>
									<p className="text-xs text-muted-foreground">
										选择人员密级用于控制数据访问范围和角色分配。
									</p>
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