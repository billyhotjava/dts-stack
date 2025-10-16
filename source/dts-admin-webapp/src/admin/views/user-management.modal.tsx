import { useCallback, useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { TreeSelect } from "antd";
import type { CreateUserRequest, KeycloakRole, KeycloakUser, UpdateUserRequest } from "#/keycloak";
import { KeycloakRoleService, KeycloakUserService } from "@/api/services/keycloakService";
import { adminApi } from "@/admin/api/adminApi";
// 职位字段已废弃，改为联系方式（phone）
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
import {
	PERSON_SECURITY_LEVELS,
	isApplicationAdminRole,
	isDataRole,
	isGovernanceRole,
} from "@/constants/governance";
import { isKeycloakBuiltInRole, isReservedBusinessRoleName, shouldHideRole } from "@/constants/keycloak-roles";
import { GLOBAL_CONFIG } from "@/global-config";

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
	const queryClient = useQueryClient();
	const [formData, setFormData] = useState<FormData>(() => createEmptyFormData());
	const [formState, setFormState] = useState<FormState>(() => createEmptyFormState());
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string>("");
    const [roles, setRoles] = useState<KeycloakRole[]>([]);
    const [roleNameMap, setRoleNameMap] = useState<Record<string, string>>({});
	const [userRoles, setUserRoles] = useState<KeycloakRole[]>([]);
	const [roleError, setRoleError] = useState<string>("");
	const [personLevel, setPersonLevel] = useState<string>("GENERAL");
	const [orgOptions, setOrgOptions] = useState<OrgTreeOption[]>([]);
	const [orgIndex, setOrgIndex] = useState<Record<string, OrganizationNode>>({});
	const [orgLoading, setOrgLoading] = useState(false);
	const [selectedGroupPaths, setSelectedGroupPaths] = useState<string[]>([]);

	const roleKey = (r: KeycloakRole | undefined | null): string => {
		if (!r) return "";
		return (r.id ?? r.name ?? "").toString();
	};

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
			// Fetch from both sources: Keycloak realm roles + Admin catalog
			const [kc, admin] = await Promise.allSettled([
				KeycloakRoleService.getAllRealmRoles(),
				adminApi.getAdminRoles(),
			]);
            const list: KeycloakRole[] = [];
            const byName = new Map<string, KeycloakRole>();
            const nameMap: Record<string, string> = {};
			const add = (r?: KeycloakRole) => {
				if (!r || !r.name) return;
				const key = r.name.trim().toUpperCase();
				if (!byName.has(key)) {
					byName.set(key, r);
					list.push(r);
				}
			};
			if (kc.status === "fulfilled" && Array.isArray(kc.value)) {
				kc.value.forEach((r) => add(r));
			}
            if (admin.status === "fulfilled" && Array.isArray(admin.value)) {
                // Map AdminRoleDetail -> KeycloakRole: use canonical code for .name; keep displayName in map
                (admin.value as any[]).forEach((ar: any) => {
                    const code = normalizeRoleCode(ar?.roleId || ar?.code || ar?.name);
                    const display = (ar?.nameZh || ar?.displayName || ar?.name || code) as string;
                    if (!code) return;
                    nameMap[code] = display;
                    add({ name: code, description: ar?.description } as KeycloakRole);
                });
            }
            setRoles(list);
            setRoleNameMap(nameMap);
		} catch (err) {
			setRoleError("加载角色列表失败");
			console.error("Error loading roles:", err);
		}
	}, []);

    function normalizeRoleCode(value?: string): string {
		if (!value) return "";
		let upper = String(value).trim().toUpperCase();
		if (upper.startsWith("ROLE_")) {
			upper = upper.substring(5);
		} else if (upper.startsWith("ROLE-")) {
			upper = upper.substring(5);
		}
		upper = upper.replace(/[^A-Z0-9_]/g, "_").replace(/_+/g, "_");
        return upper;
    }

    const displayRoleName = (name?: string): string => {
        if (!name) return "";
        const key = name.trim().toUpperCase();
        // 优先使用从 admin 目录得到的中文名
        return roleNameMap[key] || name;
    };

    const loadUserRoles = useCallback(async (userId: string) => {
        try {
            const userRolesData = await KeycloakUserService.getUserRoles(userId);
            // 合并 DB-authority 模式下的角色分配（data roles）
            const username = user?.username || "";
            let assignmentRoles: KeycloakRole[] = [];
            if (username) {
                try {
                    const all = await adminApi.getRoleAssignments();
                    const mine = (all || []).filter((it: any) => (it?.username || "").toString().toLowerCase() === username.toLowerCase());
                    assignmentRoles = mine.map((it: any) => ({ name: (it?.role || "").toString().trim().toUpperCase() })) as KeycloakRole[];
                } catch (e) {
                    // ignore
                }
            }
            let merged: KeycloakRole[] = [];
            const seen = new Set<string>();
            const push = (r?: KeycloakRole) => {
                if (!r || !r.name) return;
                const key = r.name.trim().toUpperCase();
                if (seen.has(key)) return;
                seen.add(key);
                merged.push(r);
            };
            (userRolesData || []).forEach(push);
            (assignmentRoles || []).forEach(push);
            // Hide Keycloak 内置/默认角色
            merged = merged.filter((r) => {
                const name = (r?.name || "").toString();
                if (GLOBAL_CONFIG.hideDefaultRoles && name.toLowerCase().startsWith("default-roles-")) return false;
                if (GLOBAL_CONFIG.hideBuiltinRoles && shouldHideRole(r)) return false;
                return true;
            });
            setUserRoles(merged);
            return merged;
        } catch (err) {
            setRoleError("加载用户角色失败");
            console.error("Error loading user roles:", err);
            return [];
        }
    }, [user?.username]);

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
                const deptCode = (user.attributes?.dept_code?.[0] || '').trim();
                if (deptCode && Object.keys(orgIndex).length > 0) {
                    let matchedPath: string | undefined;
                    for (const [path, node] of Object.entries(orgIndex)) {
                        if (String(node?.id ?? '') === deptCode) { matchedPath = path; break; }
                    }
                    // Last resort: try matching by leaf segment for legacy data
                    if (!matchedPath) {
                        matchedPath = Object.keys(orgIndex).find((p) => p.endsWith(`/${deptCode}`) || p.split('/').includes(deptCode));
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
            const deptCode = (user.attributes?.dept_code?.[0] || '').trim();
            if (deptCode) {
                let matchedPath: string | undefined;
                for (const [path, node] of Object.entries(orgIndex)) {
                    if (String(node?.id ?? '') === deptCode) { matchedPath = path; break; }
                }
                if (!matchedPath) {
                    matchedPath = Object.keys(orgIndex).find((p) => p.endsWith(`/${deptCode}`) || p.split('/').includes(deptCode));
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

		loadRoles();
		loadOrganizations();
	}, [open, loadRoles, loadOrganizations]);

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
		const hasRoleChanges = formState.roleChanges.length > 0;

		if (mode === "edit" && !hasUserInfoChanges && !hasRoleChanges) {
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
                    // 在创建时一并提交选择的角色，由后端生成包含角色分配的审批请求
                    realmRoles: userRoles.map((r) => r.name).filter(Boolean),
                    // Persist deptCode as organization id (dts_org_id) to align with Keycloak group attribute
                    deptCode: (() => { const p = groupPathsPayload[0]; const node = p ? orgIndex[normalizeGroupPath(p)] : undefined; return node ? String(node.id) : (attributesPayload.dept_code?.[0] || undefined); })(),
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
                        deptCode: (() => { const p = groupPathsPayload[0]; const node = p ? orgIndex[normalizeGroupPath(p)] : undefined; return node ? String(node.id) : (attributesPayload.dept_code?.[0] || undefined); })(),
                    };

                    const response = await KeycloakUserService.updateUser(user.id, updateData);
                    if (response?.message) {
                        toast.success(`用户信息更新请求提交成功: ${response.message}`);
                    } else {
                        toast.success("用户信息更新请求提交成功");
                    }
                }

                if (hasRoleChanges) {
                    const rolesToAdd = formState.roleChanges.filter((rc) => rc.action === "add").map((rc) => rc.role);

                    if (rolesToAdd.length > 0) {
                        const response = await KeycloakUserService.assignRolesToUser(user.id, rolesToAdd);
                        if (response?.message) {
                            toast.success(`角色分配请求提交成功: ${response.message}`);
                        } else {
                            toast.success("角色分配请求提交成功");
                        }
                    }

                    const rolesToRemove = formState.roleChanges.filter((rc) => rc.action === "remove").map((rc) => rc.role);

                    if (rolesToRemove.length > 0) {
                        const response = await KeycloakUserService.removeRolesFromUser(user.id, rolesToRemove);
                        if (response?.message) {
                            toast.success(`角色移除请求提交成功: ${response.message}`);
                        } else {
                            toast.success("角色移除请求提交成功");
                        }
                    }
                }
            }

            // 刷新“我的申请”和“审批中心”列表，并联动刷新角色/菜单缓存
            try { await queryClient.invalidateQueries({ queryKey: ["admin", "change-requests", "mine", "dashboard"] }); } catch {}
            try { await queryClient.invalidateQueries({ queryKey: ["admin", "change-requests"] }); } catch {}
            try { await queryClient.invalidateQueries({ queryKey: ["admin", "roles"] }); } catch {}
            try { await queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] }); } catch {}
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
		const hasRole = userRoles.some((r) => roleKey(r) === roleKey(role));
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
			setUserRoles((prev) => prev.filter((r) => roleKey(r) !== roleKey(role)));
			setFormState((prev) => ({
				...prev,
				roleChanges: [
					...prev.roleChanges.filter((rc) => roleKey(rc.role) !== roleKey(role) || rc.action !== "add"),
					{ role, action: "remove" },
				],
			}));
		} else {
			setUserRoles((prev) => [...prev, role]);
			setFormState((prev) => ({
				...prev,
				roleChanges: [
					...prev.roleChanges.filter((rc) => roleKey(rc.role) !== roleKey(role) || rc.action !== "remove"),
					{ role, action: "add" },
				],
			}));
		}
	};

    const phoneValue = formData.attributes.phone?.[0] ?? "";
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


					{(
						// 创建和编辑均展示角色分配区块；
						// 编辑模式会预加载现有角色，创建模式从空开始供选择
						mode === "edit" ? true : mode === "create"
					) && (
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
                                const allowRemoval = !isDataRole(role.name) && !isReservedBusinessRoleName(role.name) && !isKeycloakBuiltInRole(role);
                                        return (
                                            <Badge key={roleKey(role)} variant={resolveRoleBadgeVariant(role.name)}>
                                                {displayRoleName(role.name)}
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
                                .filter((role) => !userRoles.some((ur) => roleKey(ur) === roleKey(role)))
                                .filter((role) => !isDataRole(role.name))
                                .filter((role) => {
                                    const name = (role?.name || "").toString();
                                    if (GLOBAL_CONFIG.hideDefaultRoles && name.toLowerCase().startsWith("default-roles-")) return false;
                                    if (GLOBAL_CONFIG.hideBuiltinRoles && shouldHideRole(role)) return false;
                                    return true;
                                })
                                .map((role) => (
                                            <Badge
                                                key={roleKey(role)}
                                                variant={resolveRoleBadgeVariant(role.name)}
                                                className="cursor-pointer hover:bg-primary hover:text-primary-foreground"
                                                onClick={() => handleRoleToggle(role)}
                                            >
                                                {displayRoleName(role.name)}
                                                <Icon icon="mdi:plus" size={12} className="ml-1" />
                                            </Badge>
                                        ))}
									</div>
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
