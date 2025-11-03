import { useCallback, useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { TreeSelect } from "antd";
import type { CreateUserRequest, KeycloakUser, UpdateUserRequest } from "#/keycloak";
import { KeycloakUserService } from "@/api/services/keycloakService";
import { adminApi } from "@/admin/api/adminApi";
// 职位字段已废弃，改为联系方式（phone）
import type { OrganizationNode } from "@/admin/types";
import { Icon } from "@/components/icon";
import { Alert, AlertDescription } from "@/ui/alert";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Switch } from "@/ui/switch";
import { PERSON_SECURITY_LEVELS } from "@/constants/governance";

type OrgTreeOption = {
	value: string;
	title: string;
	key: string;
	selectable: boolean;
	children?: OrgTreeOption[];
};

const normalizeGroupPath = (path: string | undefined): string => {
	if (!path) return "";
	return path.startsWith("/") ? path : `/${path}`;
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
		const segment = node.name ?? "";
		const nextPath = segment ? [...ancestors, segment] : [...ancestors];
		const groupPath = normalizeGroupPath(node.groupPath ?? `/${nextPath.join("/")}`);
		index[groupPath] = node;
		return {
			value: groupPath,
			title: segment || groupPath,
			key: groupPath,
			selectable: Boolean(groupPath),
			children: node.children && node.children.length > 0 ? buildOrgOptions(node.children, nextPath, index) : undefined,
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

interface FormState {
	originalData: FormData;
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
	groupPaths: [],
});

export default function UserModal({ open, mode, user, onCancel, onSuccess }: UserModalProps) {
	const queryClient = useQueryClient();
	const [formData, setFormData] = useState<FormData>(() => createEmptyFormData());
	const [formState, setFormState] = useState<FormState>(() => createEmptyFormState());
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string>("");
	const [personLevel, setPersonLevel] = useState<string>("GENERAL");
	const [orgOptions, setOrgOptions] = useState<OrgTreeOption[]>([]);
	const [orgIndex, setOrgIndex] = useState<Record<string, OrganizationNode>>({});
	const [orgLoading, setOrgLoading] = useState(false);
	const [selectedGroupPaths, setSelectedGroupPaths] = useState<string[]>([]);

	const loadOrganizations = useCallback(async () => {
		setOrgLoading(true);
		try {
			const index: Record<string, OrganizationNode> = {};
			const tree = await adminApi.getOrganizations({ auditSilent: true });
			const options = buildOrgOptions(tree ?? [], [], index);
			setOrgOptions(options);
			setOrgIndex(index);
		} catch (error: any) {
			console.error("Error loading organizations:", error);
			toast.error(`加载组织结构失败: ${error?.message || "未知错误"}`);
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
			// Clear dept_code when no organization is selected
			updateSingleValueAttribute("dept_code", "");
			return;
		}
		const normalized = normalizeGroupPath(nextValue);
		setSelectedGroupPaths([normalized]);
		const node = orgIndex[normalized];
		// Persist selected organization id as dept_code (aligns with Keycloak group's dts_org_id)
		if (node?.id != null) {
			updateSingleValueAttribute("dept_code", String(node.id));
		}
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
			let existingGroups = Array.isArray(user.groups)
				? user.groups.map((item: string) => normalizeGroupPath(item)).filter((item: string) => item)
				: [];
			// Fallback: if no groups returned but dept_code attribute exists, match by organization id (dts_org_id)
			if (existingGroups.length === 0) {
				const deptCode = (user.attributes?.dept_code?.[0] || "").trim();
				if (deptCode && Object.keys(orgIndex).length > 0) {
					let matchedPath: string | undefined;
					for (const [path, node] of Object.entries(orgIndex)) {
						if (String(node?.id ?? "") === deptCode) {
							matchedPath = path;
							break;
						}
					}
					// Last resort: try matching by leaf segment for legacy data
					if (!matchedPath) {
						matchedPath = Object.keys(orgIndex).find(
							(p) => p.endsWith(`/${deptCode}`) || p.split("/").includes(deptCode),
						);
					}
					if (matchedPath) {
						existingGroups = [normalizeGroupPath(matchedPath)];
					}
				}
			}
			const normalizedAttributes = normalizeAttributesForState(user.attributes || {}, resolvedLevel);
			const initialFormData: FormData = {
				username: user.username || "",
				fullName: (user.fullName || user.firstName || user.lastName || user.attributes?.fullName?.[0] || "").trim(),
				email: user.email || "",
				enabled: user.enabled ?? true,
				emailVerified: user.emailVerified ?? false,
				attributes: normalizedAttributes,
			};

			setPersonLevel(resolvedLevel);
			setFormData(initialFormData);
			setSelectedGroupPaths(existingGroups);
			setFormState({
				originalData: {
					...initialFormData,
					attributes: { ...initialFormData.attributes },
				},
				groupPaths: existingGroups,
			});
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
			setFormData(emptyData);
			setSelectedGroupPaths([]);
			setFormState({
				originalData: {
					...emptyData,
					attributes: { ...emptyData.attributes },
				},
				groupPaths: [],
			});
		}

		setError("");
	}, [open, mode, user, normalizeAttributesForState]);

	// Ensure organization selection is pre-filled after org tree loads
	useEffect(() => {
		if (!open || mode !== "edit" || !user) return;
		if (selectedGroupPaths.length > 0) return;
		// Prefer groups from user payload
		let existingGroups = Array.isArray(user.groups)
			? user.groups.map((item: string) => normalizeGroupPath(item)).filter((item: string) => item)
			: [];
		if (existingGroups.length > 0) {
			setSelectedGroupPaths(existingGroups);
			return;
		}
		// Fallback: map by dept_code -> dts_org_id once orgIndex has data
		if (Object.keys(orgIndex).length > 0) {
			const deptCode = (user.attributes?.dept_code?.[0] || "").trim();
			if (deptCode) {
				let matchedPath: string | undefined;
				for (const [path, node] of Object.entries(orgIndex)) {
					if (String(node?.id ?? "") === deptCode) {
						matchedPath = path;
						break;
					}
				}
				if (!matchedPath) {
					matchedPath = Object.keys(orgIndex).find(
						(p) => p.endsWith(`/${deptCode}`) || p.split("/").includes(deptCode),
					);
				}
				if (matchedPath) {
					setSelectedGroupPaths([normalizeGroupPath(matchedPath)]);
				}
			}
		}
	}, [open, mode, user, orgIndex, selectedGroupPaths.length]);

	useEffect(() => {
		if (!open) {
			return;
		}

		loadOrganizations();
	}, [open, loadOrganizations]);

	const hasUserInfoChanged = (normalizedAttributes?: Record<string, string[]>, groupPaths?: string[]): boolean => {
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
		const groupPathsPayload = selectedGroupPaths.map((item) => normalizeGroupPath(item)).filter((item) => item);

		// 部门必填：创建与编辑均需选择所属组织
		if (groupPathsPayload.length === 0) {
			setError("所属组织为必填项，请先选择所属组织");
			return;
		}

		// 创建用户时人员密级不得为“非密”
		if (personLevel?.toUpperCase?.() === "NON_SECRET") {
			setError("人员密级不允许为‘非密’，请选取更高密级。");
			return;
		}

		const hasUserInfoChanges = hasUserInfoChanged(attributesPayload, groupPathsPayload);

		if (mode === "edit" && !hasUserInfoChanges) {
			toast.info("没有检测到任何变更");
			return;
		}

		setLoading(true);
		setError("");

		try {
			// 恢复使用 服务端 接口，由后端生成审批请求并内嵌 changeRequestId，避免“审批请求不存在”
			if (mode === "create") {
				const createData: CreateUserRequest = {
					username,
					email: email || undefined,
					firstName: fullName,
					fullName: fullName,
					enabled: formData.enabled,
					emailVerified: formData.emailVerified,
					attributes: attributesPayload,
					groups: groupPathsPayload.length > 0 ? groupPathsPayload : undefined,
					// Persist deptCode as organization id (dts_org_id) to align with Keycloak group attribute
					deptCode: (() => {
						const p = groupPathsPayload[0];
						const node = p ? orgIndex[normalizeGroupPath(p)] : undefined;
						return node ? String(node.id) : attributesPayload.dept_code?.[0] || undefined;
					})(),
				};

				const response = await KeycloakUserService.createUser(createData);
				if (response?.message) {
					toast.success(response.message);
				} else {
					toast.success("用户创建请求已提交，等待审批");
				}
			} else if (mode === "edit" && user?.id) {
				const emailPayload = email || (formState.originalData.email ? "" : undefined);

				if (hasUserInfoChanges) {
					const updateData: UpdateUserRequest = {
						id: user.id,
						username,
						email: emailPayload,
						firstName: fullName,
						fullName: fullName,
						enabled: formData.enabled,
						emailVerified: formData.emailVerified,
						attributes: attributesPayload,
						groups: groupPathsPayload.length > 0 ? groupPathsPayload : [],
						deptCode: (() => {
							const p = groupPathsPayload[0];
							const node = p ? orgIndex[normalizeGroupPath(p)] : undefined;
							return node ? String(node.id) : attributesPayload.dept_code?.[0] || undefined;
						})(),
					};

					const response = await KeycloakUserService.updateUser(user.id, updateData);
					if (response?.message) {
						toast.success(`用户信息更新请求提交成功: ${response.message}`);
					} else {
						toast.success("用户信息更新请求提交成功");
					}
				}
			}

			// 刷新“我的申请”和“审批中心”列表，并联动刷新角色/菜单缓存
			try {
				await queryClient.invalidateQueries({ queryKey: ["admin", "change-requests", "mine", "dashboard"] });
			} catch {}
			try {
				await queryClient.invalidateQueries({ queryKey: ["admin", "change-requests"] });
			} catch {}
			try {
				await queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
			} catch {}
			try {
				await queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });
			} catch {}
			onSuccess();
		} catch (err: any) {
			setError(err.message || "操作失败");
			console.error("Error saving user:", err);
		} finally {
			setLoading(false);
		}
	};

	const phoneValue = formData.attributes.phone?.[0] ?? "";
	const title = mode === "create" ? "创建用户" : "编辑用户";
	const canEditUsername = mode === "create";

	return (
		<Dialog open={open} onOpenChange={onCancel}>
			<DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
				</DialogHeader>

				<div className="space-y-6">
					<div className="flex items-center justify-center gap-2 rounded-md border border-dashed border-red-200 bg-red-50 px-4 py-3 text-center text-sm font-medium text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
						<Icon icon="mdi:star" className="h-5 w-5 text-red-500" />
						<span className="text-center">非密模块禁止处理涉密数据</span>
					</div>

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
										onChange={
											canEditUsername
												? (e) =>
														setFormData((prev) => ({
															...prev,
															username: e.target.value,
														}))
												: undefined
										}
										placeholder="请输入用户名"
										disabled={!canEditUsername}
									/>
									<p className="text-xs text-muted-foreground">
										用户名即登录名，需要保持唯一。{!canEditUsername ? " 已创建的用户不支持修改用户名。" : ""}
									</p>
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
									<Label htmlFor="department">组织机构 *</Label>
									<TreeSelect
										id="department"
										allowClear
										value={selectedGroupPaths[0] ?? undefined}
										treeData={orgOptions}
										showSearch
										treeNodeFilterProp="title"
										placeholder="请选择所属组织"
										style={{ width: "100%" }}
										dropdownStyle={{ maxHeight: 320, overflow: "auto" }}
										loading={orgLoading}
										treeDefaultExpandAll
										getPopupContainer={(triggerNode) => triggerNode.parentElement ?? document.body}
										onChange={(value) => handleOrganizationChange(value as string | string[] | null)}
										onSelect={(value) => handleOrganizationChange(value as string)}
									/>
									<p className="text-xs text-muted-foreground">必须选择所属组织，平台将依据组织控制数据访问范围。</p>
								</div>
								<div className="space-y-2">
									<Label htmlFor="phone">联系方式</Label>
									<Input
										id="phone"
										value={phoneValue}
										onChange={(e) => updateSingleValueAttribute("phone", e.target.value)}
										placeholder="请输入联系方式"
									/>
								</div>
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
													{option.label}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
									<p className="text-xs text-muted-foreground">
										人员密级决定自动分配的数据访问范围和可授予的数据密级角色。
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
