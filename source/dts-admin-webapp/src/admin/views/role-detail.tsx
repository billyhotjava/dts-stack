import { useCallback, useEffect, useMemo, useState } from "react";
import type { Dispatch, ReactNode, SetStateAction } from "react";
import { useLocation, useNavigate, useParams } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Modal, Table, TreeSelect } from "antd";
import type { TreeSelectProps } from "antd";
import type { ColumnsType } from "antd/es/table";
import { adminApi } from "@/admin/api/adminApi";
import type { AdminRoleDetail, ChangeRequest, OrganizationNode, RoleAssignmentUser } from "@/admin/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Textarea } from "@/ui/textarea";
import { Text } from "@/ui/typography";
import { Badge } from "@/ui/badge";
import { Alert, AlertDescription, AlertTitle } from "@/ui/alert";
import { toast } from "sonner";

type OrgTreeOption = {
	value: string;
	label: string;
	children?: OrgTreeOption[];
};

type PendingMember = { username: string; displayName: string; keycloakId?: string };
type AssignmentFilters = { deptPath: string; fullName: string; username: string };
type AssignmentPagination = { current: number; pageSize: number };
type RoleEditSubmissionSummary = {
	roleLabel: string;
	roleName: string;
	displayChanged: boolean;
	scopeChanged: boolean;
	descriptionChanged: boolean;
	memberAddsCount: number;
	memberRemovesCount: number;
};

function pendingMemberFromAssignmentUser(user: RoleAssignmentUser): PendingMember | null {
	const username = user.username?.trim();
	if (!username) return null;
	return {
		username,
		displayName: user.fullName?.trim() || username,
		keycloakId: user.keycloakId,
	};
}

function confirmRoleEditSubmission(summary: RoleEditSubmissionSummary): Promise<boolean> {
	const changedItems = [
		summary.displayChanged ? "角色名称" : null,
		summary.scopeChanged ? "所属域" : null,
		summary.descriptionChanged ? "角色描述" : null,
		summary.memberAddsCount > 0 ? `新增成员 ${summary.memberAddsCount} 人` : null,
		summary.memberRemovesCount > 0 ? `移除成员 ${summary.memberRemovesCount} 人` : null,
	].filter(Boolean);
	return new Promise((resolve) => {
		Modal.confirm({
			title: "确认提交角色编辑？",
			content: (
				<div className="space-y-2 text-sm">
					<p>角色：{summary.roleLabel || summary.roleName}</p>
					<p>变更内容：{changedItems.join("、")}</p>
					<p className="text-muted-foreground">提交后将进入审批流程，审批完成前不能再次编辑该角色。</p>
				</div>
			),
			okText: "确认提交",
			cancelText: "取消",
			onOk: () => resolve(true),
			onCancel: () => resolve(false),
		});
	});
}

export default function RoleDetailView() {
	const { roleId = "" } = useParams();
	const location = useLocation();
	const navigate = useNavigate();
	const queryClient = useQueryClient();

	const roleKey = decodeURIComponent(roleId);
	const canonical = canonicalRole(roleKey);
	const isEditMode = location.pathname.endsWith("/edit");

	const { data: roleList, isLoading: rolesLoading } = useQuery({
		queryKey: ["admin", "roles"],
		queryFn: () => adminApi.getAdminRoles(),
	});
	const { data: organizations } = useQuery({
		queryKey: ["admin", "orgs"],
		queryFn: () => adminApi.getOrganizations({ auditSilent: true }),
	});
	const { data: pendingChangeList } = useQuery({
		queryKey: ["admin", "role-change-pending", canonical],
		enabled: canonical.length > 0,
		queryFn: async () => {
			const list = await adminApi.getChangeRequests({ status: "PENDING", type: "ROLE" });
			return Array.isArray(list) ? list : [];
		},
		staleTime: 15_000,
		refetchOnWindowFocus: false,
	});
	const pendingChange = useMemo<ChangeRequest | null>(() => {
		if (!pendingChangeList?.length || !canonical) return null;
		for (const item of pendingChangeList) {
			if (!isPendingRoleChange(item)) continue;
			if (resolveChangeRoleId(item) === canonical) {
				return item;
			}
		}
		return null;
	}, [pendingChangeList, canonical]);
	const hasPendingChange = Boolean(pendingChange);

	const targetRole = useMemo<AdminRoleDetail | undefined>(() => {
		if (!roleList) return undefined;
		return roleList.find((role) => canonicalRole(role.roleId || role.code || role.name) === canonical);
	}, [roleList, canonical]);

	const authorityName = useMemo(() => {
		if (!targetRole) return canonical;
		return targetRole.roleId || targetRole.code || targetRole.name || canonical;
	}, [targetRole, canonical]);
	const roleName = useMemo(() => toRoleName(authorityName), [authorityName]);

	const [displayLabel, setDisplayLabel] = useState("");
	const [scope, setScope] = useState<"DEPARTMENT" | "INSTITUTE">("DEPARTMENT");
	const [description, setDescription] = useState("");
	const [updateReason, setUpdateReason] = useState("");
	const [updating, setUpdating] = useState(false);

	useEffect(() => {
		if (!targetRole) {
			return;
		}
		const rawDisplay = targetRole.displayName ?? targetRole.name ?? authorityName;
		setDisplayLabel(rawDisplay ? rawDisplay.trim() : "");
		setScope(targetRole.scope ?? (targetRole.zone === "INST" ? "INSTITUTE" : "DEPARTMENT"));
		setDescription(targetRole.description ?? "");
		setUpdateReason("");
	}, [targetRole, authorityName]);

	const emptyAssignmentFilters = useMemo<AssignmentFilters>(() => ({ deptPath: "", fullName: "", username: "" }), []);
	const [assignmentFiltersDraft, setAssignmentFiltersDraft] = useState<AssignmentFilters>(emptyAssignmentFilters);
	const [assignmentFilters, setAssignmentFilters] = useState<AssignmentFilters>(emptyAssignmentFilters);
	const [assignmentPagination, setAssignmentPagination] = useState<AssignmentPagination>({
		current: 1,
		pageSize: 10,
	});
	const [pendingAdds, setPendingAdds] = useState<Map<string, PendingMember>>(new Map());
	const [pendingRemovals, setPendingRemovals] = useState<Set<string>>(new Set());
	const [knownRoleMembers, setKnownRoleMembers] = useState<Map<string, PendingMember>>(new Map());

	useEffect(() => {
		if (!isEditMode) {
			setPendingAdds(new Map());
			setPendingRemovals(new Set());
			setKnownRoleMembers(new Map());
			setAssignmentFiltersDraft(emptyAssignmentFilters);
			setAssignmentFilters(emptyAssignmentFilters);
			setAssignmentPagination({ current: 1, pageSize: 10 });
		}
	}, [emptyAssignmentFilters, isEditMode]);

	const orgOptionResult = useMemo(() => buildOrgOptions(organizations ?? []), [organizations]);
	const orgOptions = orgOptionResult.options;
	const normalizeOrgPath = orgOptionResult.normalize;

	const hasPendingMemberChange = pendingAdds.size > 0 || pendingRemovals.size > 0;

	const {
		data: assignmentUsersPage,
		isLoading: assignmentUsersLoading,
		isError: assignmentUsersError,
	} = useQuery({
		queryKey: [
			"admin",
			"role-assignment-users",
			roleName,
			assignmentPagination.current,
			assignmentPagination.pageSize,
			assignmentFilters.deptPath,
			assignmentFilters.fullName,
			assignmentFilters.username,
		],
		enabled: isEditMode && roleName.length > 0,
		queryFn: () =>
			adminApi.getRoleAssignmentUsers(roleName, {
				page: Math.max(0, assignmentPagination.current - 1),
				size: assignmentPagination.pageSize,
				deptPath: assignmentFilters.deptPath || undefined,
				fullName: assignmentFilters.fullName || undefined,
				username: assignmentFilters.username || undefined,
			}),
	});
	const assignmentUsers = assignmentUsersPage?.content ?? [];
	const assignmentUsersTotal = assignmentUsersPage?.totalElements ?? assignmentUsers.length;

	const handleApplyAssignmentFilters = useCallback(() => {
		setAssignmentFilters({
			deptPath: assignmentFiltersDraft.deptPath.trim(),
			fullName: assignmentFiltersDraft.fullName.trim(),
			username: assignmentFiltersDraft.username.trim(),
		});
		setAssignmentPagination((prev) => ({ ...prev, current: 1 }));
	}, [assignmentFiltersDraft]);

	const handleResetAssignmentFilters = useCallback(() => {
		setAssignmentFiltersDraft(emptyAssignmentFilters);
		setAssignmentFilters(emptyAssignmentFilters);
		setAssignmentPagination((prev) => ({ ...prev, current: 1 }));
	}, [emptyAssignmentFilters]);

	const handleAssignmentPageChange = useCallback((current: number, pageSize: number) => {
		setAssignmentPagination({ current, pageSize });
	}, []);

	const selectedAssignmentRowKeys = useMemo(
		() =>
			assignmentUsers
				.filter((user) => {
					const key = user.username?.trim().toLowerCase();
					if (!key) return false;
					if (pendingAdds.has(key)) return true;
					if (pendingRemovals.has(key)) return false;
					return Boolean(user.inRole);
				})
				.map((user) => user.username),
		[assignmentUsers, pendingAdds, pendingRemovals],
	);

	const handleAssignmentSelect = useCallback(
		(user: RoleAssignmentUser, selected: boolean) => {
			if (!isEditMode || hasPendingChange) return;
			const draft = pendingMemberFromAssignmentUser(user);
			if (!draft) return;
			const key = draft.username.toLowerCase();
			if (user.inRole) {
				setKnownRoleMembers((prev) => {
					const existing = prev.get(key);
					if (
						existing?.username === draft.username &&
						existing.displayName === draft.displayName &&
						existing.keycloakId === draft.keycloakId
					) {
						return prev;
					}
					const next = new Map(prev);
					next.set(key, draft);
					return next;
				});
			}
			setPendingAdds((prev) => {
				const next = new Map(prev);
				if (selected && !user.inRole) {
					next.set(key, draft);
				} else {
					next.delete(key);
				}
				return next;
			});
			setPendingRemovals((prev) => {
				const next = new Set(prev);
				if (selected) {
					next.delete(key);
				} else if (user.inRole) {
					next.add(key);
				} else {
					next.delete(key);
				}
				return next;
			});
		},
		[hasPendingChange, isEditMode],
	);

	const assignmentColumns = useMemo<ColumnsType<RoleAssignmentUser>>(
		() => [
			{
				title: "用户名",
				dataIndex: "username",
				key: "username",
				width: 180,
				ellipsis: true,
			},
			{
				title: "姓名",
				dataIndex: "fullName",
				key: "fullName",
				width: 160,
				ellipsis: true,
				render: (value?: string) => value?.trim() || <span className="text-muted-foreground">-</span>,
			},
			{
				title: "部门",
				key: "department",
				width: 220,
				ellipsis: true,
				render: (_, record) => {
					if (record.deptName?.trim()) return record.deptName;
					const groupPath = record.groupPaths?.[0];
					return groupPath || <span className="text-muted-foreground">-</span>;
				},
			},
			{
				title: "账号状态",
				key: "enabled",
				width: 120,
				render: (_, record) =>
					record.enabled === false ? (
						<Badge variant="destructive">禁用</Badge>
					) : (
						<Badge variant="secondary">可用</Badge>
					),
			},
			{
				title: "角色状态",
				key: "roleState",
				width: 160,
				render: (_, record) => {
					const key = record.username?.trim().toLowerCase();
					if (key && pendingAdds.has(key))
						return (
							<Badge variant="secondary" className="border-emerald-500 text-emerald-600">
								待新增
							</Badge>
						);
					if (key && pendingRemovals.has(key))
						return (
							<Badge variant="destructive" className="bg-red-50 text-red-600">
								待移除
							</Badge>
						);
					return record.inRole ? (
						<Badge variant="outline">已在角色中</Badge>
					) : (
						<span className="text-muted-foreground">未加入</span>
					);
				},
			},
		],
		[pendingAdds, pendingRemovals],
	);

	const handleSubmitChanges = useCallback(async () => {
		if (!targetRole) return;
		if (pendingChange) {
			const applicant = pendingChange.requestedByDisplayName || pendingChange.requestedBy || "当前用户";
			const submittedAt = formatDateTime(pendingChange.requestedAt);
			const tip = submittedAt
				? `角色已有待审批的变更（#${pendingChange.id}，${applicant} 于 ${submittedAt} 提交），请处理完审批后再发起新的申请。`
				: `角色已有待审批的变更（#${pendingChange.id}，申请人：${applicant}），请处理完审批后再发起新的申请。`;
			toast.error(tip);
			return;
		}
		const trimmedDisplay = displayLabel.trim();
		const scopeChanged = (targetRole.scope ?? (targetRole.zone === "INST" ? "INSTITUTE" : "DEPARTMENT")) !== scope;
		const descriptionChanged = (targetRole.description ?? "") !== description.trim();
		const displayChanged = (targetRole.displayName || targetRole.name || authorityName || "").trim() !== trimmedDisplay;
		const effectiveAdds = new Map(pendingAdds);
		const effectiveRemovals = new Set(pendingRemovals);
		const memberAddsPayload = Array.from(effectiveAdds.values()).map((draft) => ({
			username: draft.username,
			displayName: draft.displayName,
			keycloakId: draft.keycloakId,
		}));
		const memberRemovesPayload = Array.from(effectiveRemovals).map((key) => {
			const base = knownRoleMembers.get(key);
			const username = base?.username ?? key;
			const displayName = base?.displayName?.trim() || username;
			return { username, displayName, keycloakId: base?.keycloakId };
		});
		const membersChanged = memberAddsPayload.length > 0 || memberRemovesPayload.length > 0;

		if (!scopeChanged && !descriptionChanged && !displayChanged && !membersChanged) {
			toast.info("未检测到变更，无需提交审批");
			return;
		}

		if (displayChanged && trimmedDisplay.length === 0) {
			toast.error("角色名称不能为空");
			return;
		}

		const submitRoleName = toRoleName(authorityName);
		const confirmed = await confirmRoleEditSubmission({
			roleLabel: trimmedDisplay || targetRole.displayName || targetRole.name || authorityName || submitRoleName,
			roleName: submitRoleName,
			displayChanged,
			scopeChanged,
			descriptionChanged,
			memberAddsCount: memberAddsPayload.length,
			memberRemovesCount: memberRemovesPayload.length,
		});
		if (!confirmed) {
			return;
		}

		setUpdating(true);
		try {
			const payload: Record<string, unknown> = {
				id: targetRole.id,
				name: submitRoleName,
				scope,
				description: description.trim() || undefined,
			};
			if (displayChanged) {
				payload.displayName = trimmedDisplay;
			}
			if (memberAddsPayload.length) {
				payload.memberAdds = memberAddsPayload;
			}
			if (memberRemovesPayload.length) {
				payload.memberRemoves = memberRemovesPayload;
			}
			const diffBefore = {
				id: targetRole.id ?? null,
				name: submitRoleName,
				displayName: targetRole.displayName || targetRole.name || null,
				scope: targetRole.scope ?? null,
				description: targetRole.description ?? null,
				memberCount: targetRole.memberCount ?? null,
			};
			const diffAfter = {
				id: targetRole.id ?? null,
				name: submitRoleName,
				displayName: trimmedDisplay || null,
				scope,
				description: description.trim() || null,
				memberAdds: memberAddsPayload.map((item) => item.username),
				memberRemoves: memberRemovesPayload.map((item) => item.username),
				memberCount: Math.max(0, (targetRole.memberCount ?? 0) + memberAddsPayload.length - memberRemovesPayload.length),
			};

			const change = await adminApi.createChangeRequest({
				resourceType: "ROLE",
				action: "UPDATE",
				resourceId: submitRoleName || authorityName,
				payloadJson: JSON.stringify(payload),
				diffJson: JSON.stringify({ before: diffBefore, after: diffAfter }),
				reason: updateReason.trim() || undefined,
			});
			await adminApi.submitChangeRequest(change.id);
			toast.success("角色变更申请已提交审批");
			const nextLabel = trimmedDisplay || targetRole.displayName || targetRole.name || authorityName || "";
			setDisplayLabel(nextLabel);
			setPendingAdds(new Map());
			setPendingRemovals(new Set());
			setKnownRoleMembers(new Map());
			await queryClient.invalidateQueries({ queryKey: ["admin", "role-change-pending", canonical] });
			await queryClient.invalidateQueries({ queryKey: ["admin", "roles"] });
			navigate("/admin/roles");
		} catch (error: any) {
			toast.error(error?.message ?? "提交失败，请稍后再试");
		} finally {
			setUpdating(false);
		}
	}, [
		authorityName,
		description,
		pendingAdds,
		displayLabel,
		pendingRemovals,
		knownRoleMembers,
		queryClient,
		scope,
		targetRole,
		updateReason,
		navigate,
		pendingChange,
		canonical,
	]);

	if (rolesLoading) {
		return (
			<div className="space-y-6">
				<Text variant="body2" className="text-muted-foreground">
					数据加载中…
				</Text>
			</div>
		);
	}

	if (!targetRole) {
		return (
			<div className="space-y-6">
				<Button type="default" onClick={() => navigate("/admin/roles")}>
					返回角色列表
				</Button>
				<Card>
					<CardHeader>
						<CardTitle>角色不存在</CardTitle>
					</CardHeader>
					<CardContent>
						<Text variant="body3" className="text-muted-foreground">
							未找到标识为 {roleKey} 的角色，请确认链接是否正确。
						</Text>
					</CardContent>
				</Card>
			</div>
		);
	}

	const resolvedDisplayName = targetRole.displayName || targetRole.name || authorityName;
	const displayLabelText = displayLabel.trim() || resolvedDisplayName;

	return (
		<div className="space-y-6">
			<div className="flex items-center justify-between">
				<Button type="default" onClick={() => navigate("/admin/roles")}>
					返回角色列表
				</Button>
				{!isEditMode ? (
					<Button
						type="primary"
						onClick={() => navigate(`/admin/roles/${encodeURIComponent(roleKey)}/edit`)}
					>
						编辑角色
					</Button>
				) : null}
			</div>

			{hasPendingChange ? (
				<Alert className="border-amber-300 bg-amber-50 text-amber-900">
					<AlertTitle>存在待审批的角色变更</AlertTitle>
					<AlertDescription className="space-y-2">
						<p>
							角色 <strong>{authorityName}</strong> 正在等待审批（单号 #{pendingChange!.id}，申请人{" "}
							{pendingChange!.requestedByDisplayName || pendingChange!.requestedBy || "未知"}
							{pendingChange!.requestedAt ? `，提交时间 ${formatDateTime(pendingChange!.requestedAt)}` : ""}
							）。审批完成前无法提交新的编辑请求。
						</p>
						<div className="flex flex-wrap gap-2">
							<Button type="default" size="small" onClick={() => navigate("/admin/my-changes")}>
								查看我的申请
							</Button>
							<Button type="default" size="small" onClick={() => navigate("/admin/approval")}>
								前往待审批列表
							</Button>
						</div>
					</AlertDescription>
				</Alert>
			) : null}

			<Card>
				<RoleBasicInfoSection
					isEditMode={isEditMode}
					displayLabel={displayLabel}
					onDisplayLabelChange={setDisplayLabel}
					displayLabelText={displayLabelText}
					authorityName={authorityName}
					scope={scope}
					onScopeChange={setScope}
					memberCount={targetRole.memberCount ?? 0}
					hasPendingMemberChange={hasPendingMemberChange}
					pendingAddsCount={pendingAdds.size}
					pendingRemovalsCount={pendingRemovals.size}
					description={description}
					roleDescription={targetRole.description}
					onDescriptionChange={setDescription}
					updateReason={updateReason}
					onUpdateReasonChange={setUpdateReason}
				>
					{isEditMode ? (
						<RoleMemberAssignmentSection
							isEditMode={isEditMode}
							hasPendingMemberChange={hasPendingMemberChange}
							pendingAddsCount={pendingAdds.size}
							pendingRemovalsCount={pendingRemovals.size}
							assignmentFiltersDraft={assignmentFiltersDraft}
							onAssignmentFiltersDraftChange={setAssignmentFiltersDraft}
							normalizeOrgPath={normalizeOrgPath}
							orgOptions={orgOptions}
							onApplyAssignmentFilters={handleApplyAssignmentFilters}
							onResetAssignmentFilters={handleResetAssignmentFilters}
							assignmentUsersError={assignmentUsersError}
							assignmentColumns={assignmentColumns}
							assignmentUsers={assignmentUsers}
							assignmentUsersLoading={assignmentUsersLoading}
							selectedAssignmentRowKeys={selectedAssignmentRowKeys}
							canEditMembers={!hasPendingChange}
							onAssignmentSelect={handleAssignmentSelect}
							assignmentPagination={assignmentPagination}
							assignmentUsersTotal={assignmentUsersTotal}
							onAssignmentPageChange={handleAssignmentPageChange}
						/>
					) : null}
					{isEditMode ? (
						<div className="flex justify-end border-t border-slate-200 pt-4">
							<Button type="primary" onClick={handleSubmitChanges} disabled={updating || hasPendingChange}>
								{updating ? "提交中…" : "提交角色编辑"}
							</Button>
						</div>
					) : null}
				</RoleBasicInfoSection>
			</Card>
		</div>
	);
}

function RoleBasicInfoSection({
	isEditMode,
	displayLabel,
	onDisplayLabelChange,
	displayLabelText,
	authorityName,
	scope,
	onScopeChange,
	memberCount,
	hasPendingMemberChange,
	pendingAddsCount,
	pendingRemovalsCount,
	description,
	roleDescription,
	onDescriptionChange,
	updateReason,
	onUpdateReasonChange,
	children,
}: {
	isEditMode: boolean;
	displayLabel: string;
	onDisplayLabelChange: (value: string) => void;
	displayLabelText: string;
	authorityName: string;
	scope: "DEPARTMENT" | "INSTITUTE";
	onScopeChange: (value: "DEPARTMENT" | "INSTITUTE") => void;
	memberCount: number;
	hasPendingMemberChange: boolean;
	pendingAddsCount: number;
	pendingRemovalsCount: number;
	description: string;
	roleDescription?: string | null;
	onDescriptionChange: (value: string) => void;
	updateReason: string;
	onUpdateReasonChange: (value: string) => void;
	children: ReactNode;
}) {
	// 标准企业表单: 左侧 label 列定宽 96px,右侧控件占满,所有 row 竖向严格对齐。
	// header 显示当前名称 + 一行 inline 元数据(标识/成员/域),与正文表单清晰分层。
	const scopeText = scope === "INSTITUTE" ? "全所共享域" : "部门域";
	const memberSummary = `${memberCount} 人${
		hasPendingMemberChange ? `(待新增 ${pendingAddsCount} · 移除 ${pendingRemovalsCount})` : ""
	}`;
	return (
		<>
			<CardHeader className="px-6 pt-5 pb-4 border-b border-slate-200/70">
				<div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-2">
					<div className="min-w-0 flex-1">
						<CardTitle className="text-lg font-semibold leading-tight truncate">{displayLabelText}</CardTitle>
						<div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
							<span>标识：<span className="font-mono text-foreground/80">{authorityName}</span></span>
							<span className="text-slate-300">·</span>
							<span>所属域：<span className="text-foreground/80">{scopeText}</span></span>
							<span className="text-slate-300">·</span>
							<span>角色成员：<span className="text-foreground/80">{memberSummary}</span></span>
						</div>
					</div>
				</div>
			</CardHeader>
			<CardContent className="px-6 py-5 text-sm">
				<div className="divide-y divide-slate-100">
					<FormRow label="角色名称" required>
						{isEditMode ? (
							<Input
								value={displayLabel}
								onChange={(event) => onDisplayLabelChange(event.target.value)}
								placeholder="请输入角色名称"
							/>
						) : (
							<Text variant="body3" className="text-sm">{displayLabelText}</Text>
						)}
					</FormRow>
					<FormRow label="所属域" required>
						{isEditMode ? (
							<SelectScope value={scope} onChange={onScopeChange} />
						) : (
							<Text variant="body3" className="text-sm">{scopeText}</Text>
						)}
					</FormRow>
					<FormRow label="角色描述">
						{isEditMode ? (
							<Textarea
								rows={2}
								placeholder="更新角色说明"
								value={description}
								onChange={(event) => onDescriptionChange(event.target.value)}
							/>
						) : (
							<Text variant="body3" className="text-sm whitespace-pre-line">
								{roleDescription?.trim() || "未填写"}
							</Text>
						)}
					</FormRow>
					{isEditMode ? (
						<FormRow label="审批备注" hint="可选">
							<Textarea
								rows={2}
								placeholder="补充审批说明"
								value={updateReason}
								onChange={(event) => onUpdateReasonChange(event.target.value)}
							/>
						</FormRow>
					) : null}
				</div>

				{children}
			</CardContent>
		</>
	);
}

/**
 * 企业表单行 — label 在左定宽 96px,控件占满右侧,所有 row 竖向严格对齐。
 * 比"label 一行 + 控件一行"上下堆叠更紧凑,也比 inline label + input 更专业。
 */
function FormRow({
	label,
	required,
	hint,
	children,
}: {
	label: string;
	required?: boolean;
	hint?: string;
	children: ReactNode;
}) {
	return (
		<div className="flex items-start gap-4 py-3 first:pt-0 last:pb-0">
			<div className="w-24 shrink-0 pt-2 text-sm text-muted-foreground">
				<span>{label}</span>
				{required ? <span className="ml-1 text-rose-500">*</span> : null}
				{hint ? <span className="ml-1 text-xs text-muted-foreground/70">({hint})</span> : null}
			</div>
			<div className="flex-1 min-w-0">{children}</div>
		</div>
	);
}

function RoleMemberAssignmentSection({
	isEditMode,
	hasPendingMemberChange,
	pendingAddsCount,
	pendingRemovalsCount,
	assignmentFiltersDraft,
	onAssignmentFiltersDraftChange,
	normalizeOrgPath,
	orgOptions,
	onApplyAssignmentFilters,
	onResetAssignmentFilters,
	assignmentUsersError,
	assignmentColumns,
	assignmentUsers,
	assignmentUsersLoading,
	selectedAssignmentRowKeys,
	canEditMembers,
	onAssignmentSelect,
	assignmentPagination,
	assignmentUsersTotal,
	onAssignmentPageChange,
}: {
	isEditMode: boolean;
	hasPendingMemberChange: boolean;
	pendingAddsCount: number;
	pendingRemovalsCount: number;
	assignmentFiltersDraft: AssignmentFilters;
	onAssignmentFiltersDraftChange: Dispatch<SetStateAction<AssignmentFilters>>;
	normalizeOrgPath: (value: string) => string;
	orgOptions: TreeSelectProps["treeData"];
	onApplyAssignmentFilters: () => void;
	onResetAssignmentFilters: () => void;
	assignmentUsersError: boolean;
	assignmentColumns: ColumnsType<RoleAssignmentUser>;
	assignmentUsers: RoleAssignmentUser[];
	assignmentUsersLoading: boolean;
	selectedAssignmentRowKeys: string[];
	canEditMembers: boolean;
	onAssignmentSelect: (user: RoleAssignmentUser, selected: boolean) => void;
	assignmentPagination: AssignmentPagination;
	assignmentUsersTotal: number;
	onAssignmentPageChange: (current: number, pageSize: number) => void;
}) {
	return (
		<section className="space-y-4 border-t border-slate-200 pt-4">
			<div className="space-y-1">
				<Text variant="body3" className="font-medium">
					角色成员
				</Text>
				<Text variant="body3" className="text-muted-foreground">
					待新增 {pendingAddsCount} 人，待移除 {pendingRemovalsCount} 人
					{hasPendingMemberChange ? "，提交后进入审批" : ""}
				</Text>
			</div>

			{isEditMode ? (
				<div className="space-y-4 rounded-lg border border-dashed border-slate-200 p-4">
					<div className="flex flex-wrap items-center justify-between gap-3">
						<Text variant="body3" className="font-medium">
							成员分配
						</Text>
						<Text variant="body3" className="text-muted-foreground">
							待新增 {pendingAddsCount} 人，待移除 {pendingRemovalsCount} 人
						</Text>
					</div>
					<div className="grid gap-3 lg:grid-cols-[minmax(220px,1.1fr)_minmax(180px,0.8fr)_minmax(180px,0.8fr)_auto_auto]">
						<div className="space-y-2">
							<Text variant="body3" className="font-medium">
								部门
							</Text>
							<TreeSelect
								className="w-full"
								placeholder="选择部门"
								treeDefaultExpandAll
								allowClear
								value={assignmentFiltersDraft.deptPath || undefined}
								onChange={(value) => {
									onAssignmentFiltersDraftChange((prev) => ({
										...prev,
										deptPath: normalizeOrgPath(String(value || "")),
									}));
								}}
								treeData={orgOptions}
								style={{ width: "100%" }}
							/>
						</div>
						<div className="space-y-2">
							<Text variant="body3" className="font-medium">
								姓名
							</Text>
							<Input
								value={assignmentFiltersDraft.fullName}
								onChange={(event) =>
									onAssignmentFiltersDraftChange((prev) => ({
										...prev,
										fullName: event.target.value,
									}))
								}
								placeholder="按姓名查询"
							/>
						</div>
						<div className="space-y-2">
							<Text variant="body3" className="font-medium">
								用户名
							</Text>
							<Input
								value={assignmentFiltersDraft.username}
								onChange={(event) =>
									onAssignmentFiltersDraftChange((prev) => ({
										...prev,
										username: event.target.value,
									}))
								}
								placeholder="按用户名查询"
							/>
						</div>
						<div className="flex items-end">
							<Button type="primary" htmlType="button" onClick={onApplyAssignmentFilters}>
								查询
							</Button>
						</div>
						<div className="flex items-end">
							<Button type="default" htmlType="button" onClick={onResetAssignmentFilters}>
								重置
							</Button>
						</div>
					</div>
					{assignmentUsersError ? (
						<Text variant="body3" className="text-destructive">
							加载用户列表失败，请稍后重试。
						</Text>
					) : null}
					<Table<RoleAssignmentUser>
						rowKey={(record) => record.username}
						columns={assignmentColumns}
						dataSource={assignmentUsers}
						loading={assignmentUsersLoading}
						rowSelection={{
							selectedRowKeys: selectedAssignmentRowKeys,
							preserveSelectedRowKeys: true,
							getCheckboxProps: () => ({ disabled: !canEditMembers }),
							onSelect: (record, selected) => onAssignmentSelect(record, selected),
							onSelectAll: (selected, _selectedRows, changedRows) => {
								changedRows.forEach((record) => onAssignmentSelect(record, selected));
							},
						}}
						locale={{ emptyText: "未找到匹配用户" }}
						pagination={{
							current: assignmentPagination.current,
							pageSize: assignmentPagination.pageSize,
							total: assignmentUsersTotal,
							showSizeChanger: true,
							pageSizeOptions: ["10", "20", "50", "100", "200"],
							showTotal: (total) => `共 ${total} 人`,
							onChange: onAssignmentPageChange,
						}}
						size="small"
						tableLayout="fixed"
						scroll={{ x: 900 }}
					/>
				</div>
			) : null}
		</section>
	);
}

function canonicalRole(value: string | null | undefined): string {
	if (!value) {
		return "";
	}
	const trimmed = value.trim();
	if (!trimmed || trimmed.toLowerCase() === "null" || trimmed.toLowerCase() === "undefined") {
		return "";
	}
	return trimmed
		.toUpperCase()
		.replace(/^ROLE[_-]?/, "")
		.replace(/_/g, "");
}

function isPendingRoleChange(change: ChangeRequest | undefined | null): boolean {
	if (!change) return false;
	const status = String(change.status || "")
		.trim()
		.toUpperCase();
	const resourceType = String(change.resourceType || "")
		.trim()
		.toUpperCase();
	return status === "PENDING" && (resourceType === "ROLE" || resourceType === "CUSTOM_ROLE");
}

function resolveChangeRoleId(change: ChangeRequest | undefined | null): string {
	if (!change) return "";
	const directSource =
		typeof change.resourceId === "string"
			? change.resourceId
			: typeof change.resourceId === "number"
				? String(change.resourceId)
				: "";
	const direct = canonicalRole(directSource);
	if (direct && /[A-Z]/.test(direct)) {
		return direct;
	}
	const payload = safeParseJson(change.payloadJson);
	const payloadName =
		payload && typeof payload === "object" ? canonicalRole((payload as any).name || (payload as any).role) : "";
	if (payloadName) return payloadName;
	const updated = change.updatedValue;
	if (updated && typeof updated === "object") {
		const candidate = canonicalRole((updated as any).name || (updated as any).role);
		if (candidate) return candidate;
	}
	const original = change.originalValue;
	if (original && typeof original === "object") {
		const candidate = canonicalRole((original as any).name || (original as any).role);
		if (candidate) return candidate;
	}
	return "";
}

function safeParseJson<T = any>(value: unknown): T | null {
	if (!value) return null;
	if (typeof value === "object") {
		return value as T;
	}
	if (typeof value !== "string") {
		return null;
	}
	try {
		return JSON.parse(value) as T;
	} catch {
		return null;
	}
}

function formatDateTime(value: string | undefined | null): string {
	if (!value) return "";
	try {
		const date = new Date(value);
		if (Number.isNaN(date.getTime())) {
			return value;
		}
		return date.toLocaleString();
	} catch {
		return value ?? "";
	}
}

function toRoleName(value: string | null | undefined): string {
	if (!value) return "";
	let upper = value.trim().toUpperCase();
	if (upper.startsWith("ROLE_")) {
		upper = upper.substring(5);
	} else if (upper.startsWith("ROLE-")) {
		upper = upper.substring(5);
	}
	return upper.replace(/[^A-Z0-9_]/g, "_").replace(/_+/g, "_");
}

function normalizeGroupPath(path: string): string {
	if (!path) return "";
	return path.startsWith("/")
		? path.replace(/\/{2,}/g, "/").replace(/\/$/, "")
		: "/" + path.replace(/\/{2,}/g, "/").replace(/\/$/, "");
}

function buildOrgOptions(nodes: OrganizationNode[]): {
	options: TreeSelectProps["treeData"];
	normalize: (value: string) => string;
} {
	const result: OrgTreeOption[] = [];

	const build = (tree: OrganizationNode[], prefix: string[]) => {
		return tree
			.filter((node) => String(node?.status ?? "1") !== "0")
			.map((node) => {
				const segment = node.name ?? "";
				const nextPath = [...prefix, segment].filter(Boolean);
				const groupPath = node.groupPath
					? normalizeGroupPath(node.groupPath)
					: normalizeGroupPath("/" + nextPath.join("/"));
				const option: OrgTreeOption = {
					value: groupPath,
					label: segment || groupPath,
				};
				if (node.children && node.children.length > 0) {
					option.children = build(node.children, nextPath);
				}
				return option;
			});
	};

	result.push(...build(nodes, []));

	return {
		options: result,
		normalize: (value: string) => {
			if (!value) return "";
			return normalizeGroupPath(value);
		},
	};
}

function SelectScope({
	value,
	onChange,
}: {
	value: "DEPARTMENT" | "INSTITUTE";
	onChange: (value: "DEPARTMENT" | "INSTITUTE") => void;
}) {
	return (
		<div className="flex gap-2">
			<Button
				htmlType="button"
				type={value === "DEPARTMENT" ? "primary" : "default"}
				onClick={() => onChange("DEPARTMENT")}
			>
				部门域
			</Button>
			<Button
				htmlType="button"
				type={value === "INSTITUTE" ? "primary" : "default"}
				onClick={() => onChange("INSTITUTE")}
			>
				全所共享域
			</Button>
		</div>
	);
}
