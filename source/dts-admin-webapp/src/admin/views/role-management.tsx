import type { FormEvent } from "react";
import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type {
	AdminCustomRole,
	AdminDataset,
	AdminRoleAssignment,
	AdminRoleDetail,
	DataOperation,
	OrgDataLevel,
	PermissionCatalogSection,
	SecurityLevel,
} from "@/admin/types";
import { ChangeRequestForm } from "@/admin/components/change-request-form";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Checkbox } from "@/ui/checkbox";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Textarea } from "@/ui/textarea";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

const BUILT_IN_DATA_ROLES: {
	name: string;
	label: string;
	scope: "DEPARTMENT" | "INSTITUTE";
	operations: DataOperation[];
	description: string;
}[] = [
	{
		name: "DEPT_OWNER",
		label: "部门主管",
		scope: "DEPARTMENT",
		operations: ["read", "write", "export"],
		description: "拥有与编辑相同的数据权限，并可额外发起治理与授权",
	},
	{
		name: "DEPT_EDITOR",
		label: "部门数据专员",
		scope: "DEPARTMENT",
		operations: ["read", "write", "export"],
		description: "维护本部门的数据资源，可提交加工与导出申请",
	},
	{
		name: "DEPT_VIEWER",
		label: "数据查阅员",
		scope: "DEPARTMENT",
		operations: ["read"],
		description: "仅在本部门范围内浏览数据",
	},
	{
		name: "INST_OWNER",
		label: "研究所领导",
		scope: "INSTITUTE",
		operations: ["read", "write", "export"],
		description: "治理全院共享区数据并执行跨部门授权",
	},
	{
		name: "INST_EDITOR",
		label: "研究所数据专员",
		scope: "INSTITUTE",
		operations: ["read", "write", "export"],
		description: "维护全院共享区数据，负责日常加工",
	},
	{
		name: "INST_VIEWER",
		label: "研究所数据查阅员",
		scope: "INSTITUTE",
		operations: ["read"],
		description: "查阅全院共享区数据",
	},
];

const BASE_ROLE_LABELS: Record<string, string> = BUILT_IN_DATA_ROLES.reduce(
	(acc, role) => {
		acc[role.name] = role.label;
		return acc;
	},
	{} as Record<string, string>,
);

const OPERATION_LABELS: Record<DataOperation, string> = {
	read: "读取",
	write: "编辑",
	export: "导出",
};

const DATA_LEVEL_LABELS: Record<OrgDataLevel, string> = {
	DATA_PUBLIC: "公开",
	DATA_INTERNAL: "内部",
	DATA_SECRET: "秘密",
	DATA_TOP_SECRET: "机密",
};

const SECURITY_LEVEL_LABELS: Record<SecurityLevel, string> = {
	NON_SECRET: "非密",
	GENERAL: "普通",
	IMPORTANT: "重要",
	CORE: "核心",
};

const SECURITY_ORDER: Record<SecurityLevel, number> = {
	NON_SECRET: 0,
	GENERAL: 1,
	IMPORTANT: 2,
	CORE: 3,
};

const DATA_ORDER: Record<OrgDataLevel, number> = {
	DATA_PUBLIC: 0,
	DATA_INTERNAL: 1,
	DATA_SECRET: 2,
	DATA_TOP_SECRET: 3,
};

const LOCKED_ADMIN_ROLES = new Set(["SYSADMIN", "OPADMIN", "AUTHADMIN", "AUDITADMIN"]);

function getRoleDisplayName(role: Pick<AdminRoleDetail, "name" | "description">) {
	const description = role.description?.trim();
	if (!description) {
		return role.name;
	}
	const [firstClause] = description.split(/[，,。.;；]/);
	return firstClause?.trim() ? firstClause.trim() : description;
}

interface CustomRoleFormState {
	name: string;
	scope: "DEPARTMENT" | "INSTITUTE";
	operations: Set<DataOperation>;
	maxRows: string;
	allowDesensitizeJson: boolean;
	maxDataLevel: OrgDataLevel;
	description: string;
}

interface AssignmentFormState {
	role: string;
	username: string;
	displayName: string;
	userSecurityLevel: SecurityLevel;
	scopeOrgId: string;
	datasetIds: Set<number>;
	operations: Set<DataOperation>;
}

export default function RoleManagementView() {
	const queryClient = useQueryClient();
	const { data: roles = [] } = useQuery<AdminRoleDetail[]>({
		queryKey: ["admin", "roles"],
		queryFn: adminApi.getAdminRoles,
	});
	const { data: catalog = [] } = useQuery<PermissionCatalogSection[]>({
		queryKey: ["admin", "permission-catalog"],
		queryFn: adminApi.getPermissionCatalog,
	});
	const { data: datasets = [] } = useQuery<AdminDataset[]>({
		queryKey: ["admin", "datasets"],
		queryFn: adminApi.getDatasets,
	});
	const { data: customRoles = [] } = useQuery<AdminCustomRole[]>({
		queryKey: ["admin", "custom-roles"],
		queryFn: adminApi.getCustomRoles,
	});
	const { data: assignments = [] } = useQuery<AdminRoleAssignment[]>({
		queryKey: ["admin", "role-assignments"],
		queryFn: adminApi.getRoleAssignments,
	});

	const [selectedId, setSelectedId] = useState<number | null>(null);
	const [customRoleForm, setCustomRoleForm] = useState<CustomRoleFormState>({
		name: "",
		scope: "DEPARTMENT",
		operations: new Set<DataOperation>(["read"]),
		maxRows: "",
		allowDesensitizeJson: true,
		maxDataLevel: "DATA_INTERNAL",
		description: "",
	});
	const [assignmentForm, setAssignmentForm] = useState<AssignmentFormState>({
		role: "",
		username: "",
		displayName: "",
		userSecurityLevel: "GENERAL",
		scopeOrgId: "",
		datasetIds: new Set<number>(),
		operations: new Set<DataOperation>(["read"]),
	});

	useEffect(() => {
		if (roles.length > 0 && !selectedId) {
			setSelectedId(roles[0].id);
		}
	}, [roles, selectedId]);

	const selected = useMemo(() => roles.find((role) => role.id === selectedId) ?? null, [roles, selectedId]);
	const selectedDisplayName = selected ? getRoleDisplayName(selected) : "";
	const isLockedAdmin = selected ? LOCKED_ADMIN_ROLES.has(selected.name.trim().toUpperCase()) : false;

	const datasetMap = useMemo(() => new Map(datasets.map((item) => [item.id, item])), [datasets]);

	const roleOperationMatrix = useMemo(() => {
		const matrix = new Map<string, DataOperation[]>();
		BUILT_IN_DATA_ROLES.forEach((item) => {
			matrix.set(item.name, item.operations);
		});
		customRoles.forEach((role) => {
			matrix.set(role.name, role.operations);
		});
		return matrix;
	}, [customRoles]);

	const roleScopeMatrix = useMemo(() => {
		const matrix = new Map<string, "DEPARTMENT" | "INSTITUTE">();
		BUILT_IN_DATA_ROLES.forEach((item) => {
			matrix.set(item.name, item.scope);
		});
		customRoles.forEach((role) => {
			matrix.set(role.name, role.scope);
		});
		return matrix;
	}, [customRoles]);

	const roleLabelMap = useMemo(() => {
		const map = { ...BASE_ROLE_LABELS } as Record<string, string>;
		customRoles.forEach((role) => {
			map[role.name] = role.name;
		});
		return map;
	}, [customRoles]);

	const orgOptions = useMemo(() => {
		const map = new Map<number, string>();
		datasets.forEach((dataset) => {
			map.set(dataset.ownerOrgId, dataset.ownerOrgName);
		});
		return Array.from(map.entries()).map(([value, label]) => ({ value: String(value), label }));
	}, [datasets]);

	const assignmentRoleScope = roleScopeMatrix.get(assignmentForm.role) ?? null;
	const assignmentScopeOrgId = assignmentForm.scopeOrgId ? Number(assignmentForm.scopeOrgId) : null;
	const availableOperations = roleOperationMatrix.get(assignmentForm.role) ?? ["read", "write", "export"];

	useEffect(() => {
		setAssignmentForm((prev) => {
			const nextOperations = new Set<DataOperation>();
			for (const op of availableOperations) {
				if (prev.operations.has(op)) {
					nextOperations.add(op);
				}
			}
			if (nextOperations.size === prev.operations.size && availableOperations.every((op) => prev.operations.has(op))) {
				return prev;
			}
			if (nextOperations.size === 0) {
				nextOperations.add(availableOperations[0] ?? "read");
			}
			return { ...prev, operations: nextOperations };
		});
	}, [availableOperations]);

	useEffect(() => {
		if (assignmentRoleScope === "INSTITUTE" && assignmentForm.scopeOrgId !== "") {
			setAssignmentForm((prev) => ({ ...prev, scopeOrgId: "" }));
		}
	}, [assignmentRoleScope, assignmentForm.scopeOrgId]);

	const filteredDatasets = useMemo(() => {
		return datasets.filter((dataset) => {
			if (assignmentRoleScope === "INSTITUTE") {
				return dataset.isInstituteShared;
			}
			if (assignmentRoleScope === "DEPARTMENT" && assignmentScopeOrgId != null) {
				return dataset.ownerOrgId === assignmentScopeOrgId;
			}
			return true;
		});
	}, [datasets, assignmentRoleScope, assignmentScopeOrgId]);

	const assignmentRoleOptions = useMemo(() => {
		const builtIn = BUILT_IN_DATA_ROLES.filter((role) => !LOCKED_ADMIN_ROLES.has(role.name.trim().toUpperCase())).map(
			(role) => ({
				value: role.name,
				label: role.label,
			}),
		);
		const custom = customRoles
			.filter((role) => !LOCKED_ADMIN_ROLES.has(role.name.trim().toUpperCase()))
			.map((role) => ({
				value: role.name,
				label: role.name,
			}));
		return [...builtIn, ...custom];
	}, [customRoles]);

	const aclFindings = useMemo(() => {
		return assignments
			.flatMap((assignment) => {
				return assignment.datasetIds.map((datasetId) => {
					const dataset = datasetMap.get(datasetId);
					if (!dataset) return null;
					const allowedOps = roleOperationMatrix.get(assignment.role) ?? ["read"];
					const unauthorizedOps = assignment.operations.filter((op) => !allowedOps.includes(op));
					const securityOk = SECURITY_ORDER[assignment.userSecurityLevel] >= DATA_ORDER[dataset.dataLevel];
					const scopeType = roleScopeMatrix.get(assignment.role) ?? null;
					const reasons: string[] = [];
					if (unauthorizedOps.length) {
						reasons.push(`超出角色可执行操作：${unauthorizedOps.map((op) => OPERATION_LABELS[op]).join("、")}`);
					}
					if (!securityOk) {
						reasons.push(
							`用户密级 ${SECURITY_LEVEL_LABELS[assignment.userSecurityLevel]} 低于数据密级 ${DATA_LEVEL_LABELS[dataset.dataLevel]}`,
						);
					}
					if (scopeType === "DEPARTMENT") {
						if (assignment.scopeOrgId == null) {
							reasons.push("部门类角色必须指定机构范围");
						} else if (dataset.ownerOrgId !== assignment.scopeOrgId) {
							reasons.push("数据集不属于授权机构");
						}
					} else if (scopeType === "INSTITUTE") {
						if (assignment.scopeOrgId != null) {
							reasons.push("全院类角色应以全院共享区为作用域");
						}
						if (!dataset.isInstituteShared) {
							reasons.push("数据集未进入全院共享区");
						}
					} else if (assignment.scopeOrgId == null && !dataset.isInstituteShared) {
						reasons.push("仅共享区数据支持全院范围授权");
					}

					return {
						id: `${assignment.id}-${datasetId}`,
						assignment,
						dataset,
						reasons,
					};
				});
			})
			.filter(
				(item): item is { id: string; assignment: AdminRoleAssignment; dataset: AdminDataset; reasons: string[] } =>
					Boolean(item),
			);
	}, [assignments, datasetMap, roleOperationMatrix, roleScopeMatrix]);

	const handleCreateCustomRole = async (event: FormEvent<HTMLFormElement>) => {
		event.preventDefault();
		if (!customRoleForm.name.trim()) {
			toast.error("请输入角色名称");
			return;
		}
		if (customRoleForm.operations.size === 0) {
			toast.error("至少选择一个操作权限");
			return;
		}
		try {
			await adminApi.createCustomRole({
				name: customRoleForm.name.trim(),
				scope: customRoleForm.scope,
				operations: Array.from(customRoleForm.operations),
				maxRows: customRoleForm.maxRows ? Number(customRoleForm.maxRows) : null,
				allowDesensitizeJson: customRoleForm.allowDesensitizeJson,
				maxDataLevel: customRoleForm.maxDataLevel,
				description: customRoleForm.description.trim() || undefined,
			});
			toast.success("自定义角色创建成功");
			setCustomRoleForm({
				name: "",
				scope: "DEPARTMENT",
				operations: new Set<DataOperation>(["read"]),
				maxRows: "",
				allowDesensitizeJson: true,
				maxDataLevel: "DATA_INTERNAL",
				description: "",
			});
			queryClient.invalidateQueries({ queryKey: ["admin", "custom-roles"] });
			queryClient.invalidateQueries({ queryKey: ["admin", "role-assignments"] });
		} catch (error: any) {
			toast.error(error?.message || "创建失败，请稍后再试");
		}
	};

	const handleCreateAssignment = async (event: FormEvent<HTMLFormElement>) => {
		event.preventDefault();
		if (!assignmentForm.role) {
			toast.error("请选择角色");
			return;
		}
		const normalizedAssignmentRole = assignmentForm.role.trim().toUpperCase();
		if (LOCKED_ADMIN_ROLES.has(normalizedAssignmentRole)) {
			toast.error("该管理员角色由系统维护，无法在此分配成员");
			return;
		}
		if (!assignmentForm.username.trim() || !assignmentForm.displayName.trim()) {
			toast.error("请填写用户与显示名称");
			return;
		}
		if (assignmentForm.datasetIds.size === 0) {
			toast.error("请勾选需要授权的数据集");
			return;
		}
		if (assignmentForm.operations.size === 0) {
			toast.error("请选择授权操作");
			return;
		}
		try {
			await adminApi.createRoleAssignment({
				role: assignmentForm.role,
				username: assignmentForm.username.trim(),
				displayName: assignmentForm.displayName.trim(),
				userSecurityLevel: assignmentForm.userSecurityLevel,
				scopeOrgId: assignmentForm.scopeOrgId ? Number(assignmentForm.scopeOrgId) : null,
				datasetIds: Array.from(assignmentForm.datasetIds),
				operations: Array.from(assignmentForm.operations),
			});
			toast.success("角色授权成功");
			setAssignmentForm({
				role: assignmentForm.role,
				username: "",
				displayName: "",
				userSecurityLevel: assignmentForm.userSecurityLevel,
				scopeOrgId: assignmentRoleScope === "INSTITUTE" ? "" : assignmentForm.scopeOrgId,
				datasetIds: new Set<number>(),
				operations: new Set<DataOperation>(assignmentForm.operations),
			});
			queryClient.invalidateQueries({ queryKey: ["admin", "role-assignments"] });
		} catch (error: any) {
			toast.error(error?.message || "授权失败，请稍后重试");
		}
	};

	return (
		<div className="grid gap-6 xl:grid-cols-[minmax(0,0.55fr)_minmax(0,1fr)]">
			<Card>
				<CardHeader>
					<CardTitle>角色列表</CardTitle>
				</CardHeader>
				<CardContent className="space-y-3">
					{roles.length === 0 ? <Text variant="body3">暂无角色数据。</Text> : null}
					<ul className="space-y-2">
						{roles.map((role) => {
							const isActive = role.id === selectedId;
							const normalizedRoleName = role.name.trim().toUpperCase();
							const displayName = getRoleDisplayName(role);
							const locked = LOCKED_ADMIN_ROLES.has(normalizedRoleName);
							return (
								<li key={role.id}>
									<button
										type="button"
										onClick={() => setSelectedId(role.id)}
										className={`w-full rounded-md border px-4 py-3 text-left transition ${
											isActive ? "border-primary bg-primary/10" : "hover:border-primary/40"
										}`}
									>
										<div className="flex items-center justify-between gap-2">
											<div className="flex items-center gap-2">
												<span className="font-mono text-xs text-muted-foreground">#{role.id}</span>
												<span className="font-medium">{displayName}</span>
												{locked ? <Badge variant="secondary">系统预置</Badge> : null}
											</div>
											<Badge variant="outline">{role.securityLevel}</Badge>
										</div>
										<Text variant="body3" className="text-muted-foreground">
											{role.description || "--"}
										</Text>
										<Text variant="body3" className="text-muted-foreground">
											标识：{role.name}
										</Text>
										<div className="mt-1 flex flex-wrap gap-1">
											<Badge variant="secondary">成员：{role.memberCount}</Badge>
											<Badge variant="secondary">审批链：{role.approvalFlow}</Badge>
										</div>
									</button>
								</li>
							);
						})}
					</ul>
				</CardContent>
			</Card>

			<div className="space-y-6">
				<Card>
					<CardHeader>
						<CardTitle>角色详情</CardTitle>
					</CardHeader>
					<CardContent className="space-y-4 text-sm">
						{selected ? (
							<>
								<div className="space-y-1">
									<div className="flex items-center gap-2">
										<span className="font-mono text-xs text-muted-foreground">#{selected.id}</span>
										<span className="text-sm font-semibold">{selectedDisplayName || selected.name}</span>
										{isLockedAdmin ? <Badge variant="secondary">系统预置</Badge> : null}
									</div>
									<Text variant="body3" className="text-muted-foreground">
										标识：{selected.name}
									</Text>
									<Text variant="body3" className="text-muted-foreground">
										安全级别：{selected.securityLevel}
									</Text>
									{isLockedAdmin ? (
										<Text variant="body3" className="text-amber-500">
											内置管理员角色仅由平台维护，不支持在线修改或成员管理。
										</Text>
									) : null}
								</div>
								{selected.description ? <p className="text-muted-foreground">{selected.description}</p> : null}
								<div className="space-y-2">
									<Text variant="body3" className="font-semibold">
										拥有权限
									</Text>
									<div className="flex flex-wrap gap-2">
										{selected.permissions.map((code) => (
											<Badge key={code} variant="outline">
												{code}
											</Badge>
										))}
									</div>
								</div>
								<div className="space-y-2">
									<Text variant="body3" className="font-semibold">
										最新变更
									</Text>
									<p className="text-muted-foreground">{selected.updatedAt}</p>
								</div>
							</>
						) : (
							<Text variant="body3" className="text-muted-foreground">
								请选择左侧角色查看详情。
							</Text>
						)}
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle>内置角色矩阵</CardTitle>
						<Text variant="body3" className="text-muted-foreground">
							OWNER 权限包含 EDITOR 的全部数据操作，额外具备治理与授权能力。
						</Text>
					</CardHeader>
					<CardContent className="space-y-3 text-sm">
						<div className="overflow-x-auto">
							<table className="min-w-full text-left">
								<thead className="text-muted-foreground">
									<tr>
										<th className="px-3 py-2 font-medium">角色</th>
										<th className="px-3 py-2 font-medium">作用域</th>
										<th className="px-3 py-2 font-medium">允许操作</th>
										<th className="px-3 py-2 font-medium">说明</th>
									</tr>
								</thead>
								<tbody>
									{BUILT_IN_DATA_ROLES.map((role) => (
										<tr key={role.name} className="border-b last:border-b-0">
											<td className="px-3 py-2 font-medium">{role.label}</td>
											<td className="px-3 py-2">
												<Badge variant="outline">{role.scope === "DEPARTMENT" ? "部门" : "全院共享区"}</Badge>
											</td>
											<td className="px-3 py-2">
												<div className="flex flex-wrap gap-1">
													{role.operations.map((operation) => (
														<Badge key={operation} variant="secondary">
															{OPERATION_LABELS[operation]}
														</Badge>
													))}
												</div>
											</td>
											<td className="px-3 py-2 text-muted-foreground">{role.description}</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle>权限矩阵</CardTitle>
					</CardHeader>
					<CardContent className="space-y-4 text-sm">
						{catalog.length === 0 ? <Text variant="body3">暂无权限定义。</Text> : null}
						{catalog.map((section) => (
							<div key={section.category} className="rounded-lg border p-3">
								<div className="mb-2 flex items-center justify-between">
									<Text variant="body3" className="font-semibold">
										{section.category}
									</Text>
									<Text variant="body3" className="text-muted-foreground">
										{section.description || ""}
									</Text>
								</div>
								<ul className="space-y-1">
									{section.permissions.map((permission) => {
										const attached = selected?.permissions.includes(permission.code);
										return (
											<li
												key={permission.code}
												className="flex items-center justify-between gap-3 rounded-md px-2 py-1"
											>
												<span>
													{permission.name}
													<Text variant="body3" className="text-muted-foreground">
														{permission.code}
													</Text>
												</span>
												<div className="flex items-center gap-2">
													<Badge variant={permission.securityLevel ? "outline" : "secondary"}>
														{permission.securityLevel || "公共"}
													</Badge>
													{attached ? <Badge variant="secondary">已赋予</Badge> : null}
												</div>
											</li>
										);
									})}
								</ul>
							</div>
						))}
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle>自定义角色组合</CardTitle>
						<Text variant="body3" className="text-muted-foreground">
							仅允许组合读取/编辑/导出操作，并通过脱敏、行数限制与最大数据密级进行收紧。
						</Text>
					</CardHeader>
					<CardContent className="space-y-6 text-sm">
						<form className="space-y-4" onSubmit={handleCreateCustomRole}>
							<div className="grid gap-4 md:grid-cols-2">
								<div className="space-y-2">
									<Label htmlFor="custom-role-name">角色名称</Label>
									<Input
										id="custom-role-name"
										placeholder="如：DEPT_EXPORT_LIMITED"
										value={customRoleForm.name}
										onChange={(event) =>
											setCustomRoleForm((prev) => ({
												...prev,
												name: event.target.value,
											}))
										}
									/>
								</div>
								<div className="space-y-2">
									<Label htmlFor="custom-role-scope">作用域</Label>
									<select
										id="custom-role-scope"
										className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm"
										value={customRoleForm.scope}
										onChange={(event) =>
											setCustomRoleForm((prev) => ({
												...prev,
												scope: event.target.value as "DEPARTMENT" | "INSTITUTE",
											}))
										}
									>
										<option value="DEPARTMENT">部门</option>
										<option value="INSTITUTE">全院共享区</option>
									</select>
								</div>
							</div>
							<div className="space-y-2">
								<Label>操作权限</Label>
								<div className="flex flex-wrap gap-4">
									{(Object.keys(OPERATION_LABELS) as DataOperation[]).map((operation) => {
										const checked = customRoleForm.operations.has(operation);
										return (
											<label key={operation} className="flex items-center gap-2 text-sm">
												<Checkbox
													checked={checked}
													onCheckedChange={(value) =>
														setCustomRoleForm((prev) => {
															const next = new Set(prev.operations);
															if (value) {
																next.add(operation);
															} else {
																next.delete(operation);
															}
															return { ...prev, operations: next };
														})
													}
												/>
												{OPERATION_LABELS[operation]}
											</label>
										);
									})}
								</div>
							</div>
							<div className="grid gap-4 md:grid-cols-3">
								<div className="space-y-2">
									<Label htmlFor="custom-role-max-rows">行数上限</Label>
									<Input
										id="custom-role-max-rows"
										type="number"
										min="0"
										placeholder="不限制则留空"
										value={customRoleForm.maxRows}
										onChange={(event) =>
											setCustomRoleForm((prev) => ({
												...prev,
												maxRows: event.target.value,
											}))
										}
									/>
								</div>
								<div className="space-y-2">
									<Label htmlFor="custom-role-desensitize">是否需要脱敏 JSON</Label>
									<label className="flex items-center gap-2 text-sm">
										<Checkbox
											id="custom-role-desensitize"
											checked={customRoleForm.allowDesensitizeJson}
											onCheckedChange={(value) =>
												setCustomRoleForm((prev) => ({
													...prev,
													allowDesensitizeJson: Boolean(value),
												}))
											}
										/>
										需脱敏 JSON
									</label>
								</div>
								<div className="space-y-2">
									<Label htmlFor="custom-role-max-level">最大数据密级</Label>
									<select
										id="custom-role-max-level"
										className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm"
										value={customRoleForm.maxDataLevel}
										onChange={(event) =>
											setCustomRoleForm((prev) => ({
												...prev,
												maxDataLevel: event.target.value as OrgDataLevel,
											}))
										}
									>
										{(Object.keys(DATA_LEVEL_LABELS) as OrgDataLevel[]).map((level) => (
											<option key={level} value={level}>
												{DATA_LEVEL_LABELS[level]}
											</option>
										))}
									</select>
								</div>
							</div>
							<div className="space-y-2">
								<Label htmlFor="custom-role-description">说明</Label>
								<Textarea
									id="custom-role-description"
									rows={3}
									placeholder="补充约束描述，便于审批记录"
									value={customRoleForm.description}
									onChange={(event) =>
										setCustomRoleForm((prev) => ({
											...prev,
											description: event.target.value,
										}))
									}
								/>
							</div>
							<Button type="submit">创建自定义角色</Button>
						</form>

						<div className="space-y-3">
							<Text variant="body3" className="font-semibold">
								当前自定义角色
							</Text>
							{customRoles.length === 0 ? (
								<Text variant="body3" className="text-muted-foreground">
									还没有创建自定义角色。
								</Text>
							) : (
								<div className="space-y-3">
									{customRoles.map((role) => (
										<div key={role.id} className="rounded-md border p-3">
											<div className="flex flex-wrap items-center gap-2">
												<Text variant="body2" className="font-semibold">
													{role.name}
												</Text>
												<Badge variant="outline">{role.scope === "DEPARTMENT" ? "部门" : "全院共享区"}</Badge>
												<Badge variant="secondary">最大数据密级：{DATA_LEVEL_LABELS[role.maxDataLevel]}</Badge>
												<Badge variant="secondary">创建人：{role.createdBy}</Badge>
												<Badge variant="secondary">创建时间：{role.createdAt}</Badge>
											</div>
											{role.description ? (
												<p className="mt-2 text-sm text-muted-foreground">{role.description}</p>
											) : null}
											<div className="mt-2 flex flex-wrap gap-2 text-xs">
												{role.operations.map((operation) => (
													<Badge key={operation} variant="outline">
														{OPERATION_LABELS[operation]}
													</Badge>
												))}
												<Badge variant="outline">
													{role.maxRows ? `行数上限 ${role.maxRows.toLocaleString()} 行` : "行数无限制"}
												</Badge>
												<Badge variant="outline">{role.allowDesensitizeJson ? "需脱敏 JSON" : "无需脱敏"}</Badge>
											</div>
										</div>
									))}
								</div>
							)}
						</div>
					</CardContent>
				</Card>

				{isLockedAdmin ? (
					<Card>
						<CardHeader>
							<CardTitle>角色分配</CardTitle>
						</CardHeader>
						<CardContent className="space-y-4 text-sm">
							<Text variant="body3" className="text-muted-foreground">
								内置管理员角色的成员与权限由平台策略统一维护，如需调整请通过工单或变更流程处理。
							</Text>
						</CardContent>
					</Card>
				) : (
					<Card>
						<CardHeader>
							<CardTitle>角色分配</CardTitle>
							<Text variant="body3" className="text-muted-foreground">
								授权需绑定用户与作用域（Org ID 或留空表示全院共享区）。
							</Text>
						</CardHeader>
						<CardContent className="space-y-6 text-sm">
							<form className="space-y-4" onSubmit={handleCreateAssignment}>
								<div className="grid gap-4 md:grid-cols-2">
									<div className="space-y-2">
										<Label htmlFor="assignment-role">角色</Label>
										<select
											id="assignment-role"
											className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm"
											value={assignmentForm.role}
											onChange={(event) =>
												setAssignmentForm((prev) => ({
													...prev,
													role: event.target.value,
													operations: new Set<DataOperation>(["read"]),
													datasetIds: new Set<number>(),
												}))
											}
										>
											<option value="">请选择角色</option>
											{assignmentRoleOptions.map((option) => (
												<option key={option.value} value={option.value}>
													{option.label}
												</option>
											))}
										</select>
									</div>
									<div className="space-y-2">
										<Label htmlFor="assignment-security">用户密级</Label>
										<select
											id="assignment-security"
											className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm"
											value={assignmentForm.userSecurityLevel}
											onChange={(event) =>
												setAssignmentForm((prev) => ({
													...prev,
													userSecurityLevel: event.target.value as SecurityLevel,
												}))
											}
										>
											{(Object.keys(SECURITY_LEVEL_LABELS) as SecurityLevel[]).map((level) => (
												<option key={level} value={level}>
													{SECURITY_LEVEL_LABELS[level]}
												</option>
											))}
										</select>
									</div>
								</div>
								<div className="grid gap-4 md:grid-cols-2">
									<div className="space-y-2">
										<Label htmlFor="assignment-username">用户名</Label>
										<Input
											id="assignment-username"
											placeholder="zhangwei"
											value={assignmentForm.username}
											onChange={(event) =>
												setAssignmentForm((prev) => ({
													...prev,
													username: event.target.value,
												}))
											}
										/>
									</div>
									<div className="space-y-2">
										<Label htmlFor="assignment-display">显示名称</Label>
										<Input
											id="assignment-display"
											placeholder="张伟"
											value={assignmentForm.displayName}
											onChange={(event) =>
												setAssignmentForm((prev) => ({
													...prev,
													displayName: event.target.value,
												}))
											}
										/>
									</div>
								</div>
								<div className="space-y-2">
									<Label htmlFor="assignment-scope">授权作用域</Label>
									<select
										id="assignment-scope"
										className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm"
										value={assignmentForm.scopeOrgId}
										disabled={assignmentRoleScope === "INSTITUTE"}
										onChange={(event) =>
											setAssignmentForm((prev) => ({
												...prev,
												scopeOrgId: event.target.value,
											}))
										}
									>
										<option value="">全院共享区</option>
										{orgOptions.map((option) => (
											<option key={option.value} value={option.value}>
												{option.label}
											</option>
										))}
									</select>
									{assignmentRoleScope === "INSTITUTE" ? (
										<Text variant="body3" className="text-muted-foreground">
											全院类角色默认作用于共享区数据。
										</Text>
									) : null}
								</div>
								<div className="space-y-2">
									<Label>授权数据集</Label>
									{filteredDatasets.length === 0 ? (
										<Text variant="body3" className="text-muted-foreground">
											当前筛选条件下暂无可授权数据集。
										</Text>
									) : (
										<div className="grid gap-2 md:grid-cols-2">
											{filteredDatasets.map((dataset) => {
												const checked = assignmentForm.datasetIds.has(dataset.id);
												return (
													<label key={dataset.id} className="flex items-start gap-2 rounded-md border p-2 text-sm">
														<Checkbox
															checked={checked}
															onCheckedChange={(value) =>
																setAssignmentForm((prev) => {
																	const next = new Set(prev.datasetIds);
																	if (value) {
																		next.add(dataset.id);
																	} else {
																		next.delete(dataset.id);
																	}
																	return { ...prev, datasetIds: next };
																})
															}
														/>
														<span>
															<span className="font-medium">{dataset.businessCode}</span>
															<Text variant="body3" className="text-muted-foreground">
																{dataset.ownerOrgName} · {DATA_LEVEL_LABELS[dataset.dataLevel]}
																{dataset.isInstituteShared ? " · 全院共享" : ""}
															</Text>
														</span>
													</label>
												);
											})}
										</div>
									)}
								</div>
								<div className="space-y-2">
									<Label>授权操作</Label>
									<div className="flex flex-wrap gap-4">
										{availableOperations.map((operation) => {
											const checked = assignmentForm.operations.has(operation);
											return (
												<label key={operation} className="flex items-center gap-2 text-sm">
													<Checkbox
														checked={checked}
														onCheckedChange={(value) =>
															setAssignmentForm((prev) => {
																const next = new Set(prev.operations);
																if (value) {
																	next.add(operation);
																} else {
																	next.delete(operation);
																}
																if (next.size === 0) {
																	next.add(operation);
																}
																return { ...prev, operations: next };
															})
														}
													/>
													{OPERATION_LABELS[operation]}
												</label>
											);
										})}
									</div>
								</div>
								<Button type="submit">提交授权</Button>
							</form>

							<div className="space-y-3">
								<Text variant="body3" className="font-semibold">
									授权记录
								</Text>
								{assignments.length === 0 ? (
									<Text variant="body3" className="text-muted-foreground">
										暂无授权记录。
									</Text>
								) : (
									<div className="overflow-x-auto">
										<table className="min-w-full text-left text-sm">
											<thead className="text-muted-foreground">
												<tr>
													<th className="px-3 py-2 font-medium">用户</th>
													<th className="px-3 py-2 font-medium">角色</th>
													<th className="px-3 py-2 font-medium">作用域</th>
													<th className="px-3 py-2 font-medium">数据集</th>
													<th className="px-3 py-2 font-medium">操作</th>
													<th className="px-3 py-2 font-medium">授权时间</th>
												</tr>
											</thead>
											<tbody>
												{assignments.map((assignment) => (
													<tr key={assignment.id} className="border-b last:border-b-0">
														<td className="px-3 py-2">
															<div className="flex flex-col">
																<span className="font-medium">{assignment.displayName}</span>
																<Text variant="body3" className="text-muted-foreground">
																	{assignment.username} · {SECURITY_LEVEL_LABELS[assignment.userSecurityLevel]}
																</Text>
															</div>
														</td>
														<td className="px-3 py-2">{roleLabelMap[assignment.role] ?? assignment.role}</td>
														<td className="px-3 py-2">{assignment.scopeOrgName}</td>
														<td className="px-3 py-2">
															<div className="flex flex-wrap gap-1">
																{assignment.datasetIds.map((id) => {
																	const dataset = datasetMap.get(id);
																	return (
																		<Badge key={id} variant="outline">
																			{dataset?.businessCode ?? id}
																		</Badge>
																	);
																})}
															</div>
														</td>
														<td className="px-3 py-2">
															<div className="flex flex-wrap gap-1">
																{assignment.operations.map((operation) => (
																	<Badge key={operation} variant="secondary">
																		{OPERATION_LABELS[operation]}
																	</Badge>
																))}
															</div>
														</td>
														<td className="px-3 py-2 text-muted-foreground">{assignment.grantedAt}</td>
													</tr>
												))}
											</tbody>
										</table>
									</div>
								)}
							</div>
						</CardContent>
					</Card>
				)}

				<Card>
					<CardHeader>
						<CardTitle>数据集清单</CardTitle>
					</CardHeader>
					<CardContent className="overflow-x-auto text-sm">
						{datasets.length === 0 ? (
							<Text variant="body3" className="text-muted-foreground">
								暂无数据资产。
							</Text>
						) : (
							<table className="min-w-full text-left">
								<thead className="text-muted-foreground">
									<tr>
										<th className="px-3 py-2 font-medium">数据集</th>
										<th className="px-3 py-2 font-medium">所属机构</th>
										<th className="px-3 py-2 font-medium">数据密级</th>
										<th className="px-3 py-2 font-medium">共享范围</th>
										<th className="px-3 py-2 font-medium text-right">记录数</th>
										<th className="px-3 py-2 font-medium">最近同步</th>
									</tr>
								</thead>
								<tbody>
									{datasets.map((dataset) => (
										<tr key={dataset.id} className="border-b last:border-b-0">
											<td className="px-3 py-2">
												<div className="flex flex-col">
													<span className="font-medium">{dataset.businessCode}</span>
													<Text variant="body3" className="text-muted-foreground">
														{dataset.description || "--"}
													</Text>
												</div>
											</td>
											<td className="px-3 py-2">{dataset.ownerOrgName}</td>
											<td className="px-3 py-2">
												<Badge variant="outline">{DATA_LEVEL_LABELS[dataset.dataLevel]}</Badge>
											</td>
											<td className="px-3 py-2">
												<Badge variant={dataset.isInstituteShared ? "secondary" : "outline"}>
													{dataset.isInstituteShared ? "全院共享" : "部门内"}
												</Badge>
											</td>
											<td className="px-3 py-2 text-right">{dataset.rowCount.toLocaleString()}</td>
											<td className="px-3 py-2 text-muted-foreground">{dataset.updatedAt}</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle>ACL 检查结果</CardTitle>
						<Text variant="body3" className="text-muted-foreground">
							任何访问必须同时满足用户密级 ≥ 数据密级、作用域合法以及操作权限受限。
						</Text>
					</CardHeader>
					<CardContent className="space-y-3 text-sm">
						{aclFindings.length === 0 ? (
							<Text variant="body3" className="text-muted-foreground">
								暂无可校验的授权。
							</Text>
						) : (
							<div className="overflow-x-auto">
								<table className="min-w-full text-left">
									<thead className="text-muted-foreground">
										<tr>
											<th className="px-3 py-2 font-medium">用户</th>
											<th className="px-3 py-2 font-medium">角色</th>
											<th className="px-3 py-2 font-medium">数据集</th>
											<th className="px-3 py-2 font-medium">结果</th>
											<th className="px-3 py-2 font-medium">说明</th>
										</tr>
									</thead>
									<tbody>
										{aclFindings.map((finding) => {
											const ok = finding.reasons.length === 0;
											return (
												<tr key={finding.id} className="border-b last:border-b-0">
													<td className="px-3 py-2">
														<div className="flex flex-col">
															<span className="font-medium">{finding.assignment.displayName}</span>
															<Text variant="body3" className="text-muted-foreground">
																{finding.assignment.username}
															</Text>
														</div>
													</td>
													<td className="px-3 py-2">
														{roleLabelMap[finding.assignment.role] ?? finding.assignment.role}
													</td>
													<td className="px-3 py-2">{finding.dataset.businessCode}</td>
													<td className="px-3 py-2">
														<Badge variant={ok ? "secondary" : "destructive"}>{ok ? "通过" : "阻断"}</Badge>
													</td>
													<td className="px-3 py-2 text-muted-foreground">
														{ok ? "已满足安全策略" : finding.reasons.join("；")}
													</td>
												</tr>
											);
										})}
									</tbody>
								</table>
							</div>
						)}
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle>发起角色变更</CardTitle>
					</CardHeader>
					<CardContent>
						{isLockedAdmin ? (
							<Text variant="body3" className="text-muted-foreground">
								内置管理员角色变更需通过线下审批或专用流程提交，请联系系统管理员。
							</Text>
						) : (
							<ChangeRequestForm initialTab="role" />
						)}
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
