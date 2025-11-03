import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { TreeSelect, Select as AntSelect } from "antd";
import type { TreeSelectProps } from "antd";
import { adminApi } from "@/admin/api/adminApi";
import type { AdminRoleDetail, AdminUser, ChangeRequest, OrganizationNode } from "@/admin/types";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Textarea } from "@/ui/textarea";
import { Text } from "@/ui/typography";
import { Badge } from "@/ui/badge";
import { Alert, AlertDescription, AlertTitle } from "@/ui/alert";
import { toast } from "sonner";
import { Icon } from "@/components/icon";

type OrgTreeOption = {
	value: string;
	label: string;
	children?: OrgTreeOption[];
};

type RealmMember = { username: string; displayName?: string };
type PendingMember = { username: string; displayName: string; keycloakId?: string };
type MemberView = {
	username: string;
	displayName: string;
	status: "existing" | "add" | "remove";
	origin: "existing" | "new";
	keycloakId?: string;
};

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
	const { data: adminUsers } = useQuery({
		queryKey: ["admin", "users"],
		queryFn: () => adminApi.getAdminUsers({ size: 500 }),
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

	const [realmMembers, setRealmMembers] = useState<RealmMember[]>([]);
	const [membersLoading, setMembersLoading] = useState(false);

	useEffect(() => {
		let cancelled = false;
		const roleName = toRoleName(authorityName);
		if (!roleName) {
			setRealmMembers([]);
			return;
		}
		setMembersLoading(true);
		(async () => {
			try {
				const list = await adminApi.getRoleMembers(roleName);
				if (!cancelled) {
					setRealmMembers(
						(list ?? []).map((item) => ({
							username: item.username,
							displayName: item.displayName,
						}))
					);
				}
			} catch (error) {
				if (!cancelled) {
					console.warn("Failed to load realm members:", error);
					setRealmMembers([]);
				}
			} finally {
				if (!cancelled) setMembersLoading(false);
			}
		})();
		return () => {
			cancelled = true;
		};
	}, [authorityName]);

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

	const adminUsersList = useMemo<AdminUser[]>(() => {
		if (!adminUsers) return [];
		if (Array.isArray(adminUsers)) return adminUsers;
		const content = (adminUsers as any)?.content;
		if (Array.isArray(content)) return content;
		const records = (adminUsers as any)?.records;
		if (Array.isArray(records)) return records;
		return [];
	}, [adminUsers]);

	const adminUsersIndex = useMemo(() => {
		const map = new Map<string, AdminUser>();
		adminUsersList.forEach((user) => {
			if (user?.username) {
				map.set(user.username.toLowerCase(), user);
			}
		});
		return map;
	}, [adminUsersList]);

	const [selectedOrgPath, setSelectedOrgPath] = useState<string>("");
	const [selectedUsername, setSelectedUsername] = useState<string>("");
	const [pendingAdds, setPendingAdds] = useState<Map<string, PendingMember>>(new Map());
	const [pendingRemovals, setPendingRemovals] = useState<Set<string>>(new Set());
	const [memberError, setMemberError] = useState<string>("");

	useEffect(() => {
		if (!isEditMode) {
			setPendingAdds(new Map());
			setPendingRemovals(new Set());
			setSelectedOrgPath("");
			setSelectedUsername("");
			setMemberError("");
		}
	}, [isEditMode]);

	const orgOptionResult = useMemo(() => buildOrgOptions(organizations ?? []), [organizations]);
	const orgOptions = orgOptionResult.options;
	const normalizeOrgPath = orgOptionResult.normalize;

	const baseMembersMap = useMemo(() => {
		const map = new Map<string, RealmMember>();
		realmMembers.forEach((member) => {
			if (member?.username) {
				map.set(member.username.toLowerCase(), member);
			}
		});
		return map;
	}, [realmMembers]);

	const memberStatusMap = useMemo(() => {
		const map = new Map<string, MemberView>();
		baseMembersMap.forEach((member, key) => {
			const displayName = member.displayName?.trim() || member.username;
			const userRecord = adminUsersIndex.get(key);
			map.set(key, {
				username: member.username,
				displayName,
				status: pendingRemovals.has(key) ? "remove" : "existing",
				origin: "existing",
				keycloakId: userRecord?.keycloakId ?? undefined,
			});
		});
		pendingAdds.forEach((draft, key) => {
			map.set(key, {
				username: draft.username,
				displayName: draft.displayName,
				status: "add",
				origin: "new",
				keycloakId: draft.keycloakId,
			});
		});
		return map;
	}, [adminUsersIndex, baseMembersMap, pendingAdds, pendingRemovals]);

	const memberViews = useMemo(
		() => Array.from(memberStatusMap.values()).sort((a, b) => a.displayName.localeCompare(b.displayName, "zh-CN")),
		[memberStatusMap],
	);
	const visibleMemberCount = useMemo(
		() => memberViews.filter((member) => member.status !== "remove").length,
		[memberViews],
	);
	const hasPendingMemberChange = pendingAdds.size > 0 || pendingRemovals.size > 0;

	const filteredUsers = useMemo(() => {
		if (!selectedOrgPath) return [];
		const normalized = normalizeGroupPath(selectedOrgPath);
		if (!normalized) return [];
		return adminUsersList.filter((user) => {
			const username = user?.username?.trim();
			if (!username) return false;
			const key = username.toLowerCase();
			const status = memberStatusMap.get(key)?.status;
			if (status && status !== "remove") {
				return false;
			}
			const rawGroupPaths: unknown = (user as any)?.groupPaths ?? (user as any)?.orgPath;
			const candidatePaths = Array.isArray(rawGroupPaths) ? (rawGroupPaths as string[]) : [];
			if (!candidatePaths.length) return false;
			return candidatePaths.some((path) => {
				const normalizedUserPath = normalizeGroupPath(path);
				return normalizedUserPath === normalized || normalizedUserPath.startsWith(`${normalized}/`);
			});
		});
	}, [adminUsersList, memberStatusMap, selectedOrgPath]);

	const handleQueueAdd = useCallback(() => {
		setMemberError("");
		if (!isEditMode) {
			setMemberError("当前为只读模式，无法添加成员");
			return;
		}
		if (!selectedOrgPath) {
			setMemberError("请先选择所属部门");
			return;
		}
		if (!selectedUsername) {
			setMemberError("请选择成员");
			return;
		}
		const key = selectedUsername.toLowerCase();
		const existingStatus = memberStatusMap.get(key);
		if (existingStatus && existingStatus.status !== "remove") {
			setMemberError("该成员已在角色中");
			return;
		}
		if (pendingAdds.has(key)) {
			setMemberError("该成员已添加到待审批列表");
			return;
		}
		const userInfo = adminUsersIndex.get(key);
		const displayName =
			(userInfo?.fullName && userInfo.fullName.trim()) || userInfo?.username || selectedUsername;
		const nextAdds = new Map(pendingAdds);
		nextAdds.set(key, {
			username: userInfo?.username ?? selectedUsername,
			displayName,
			keycloakId: userInfo?.keycloakId ?? undefined,
		});
		setPendingAdds(nextAdds);

		if (pendingRemovals.has(key)) {
			const nextRemovals = new Set(pendingRemovals);
			nextRemovals.delete(key);
			setPendingRemovals(nextRemovals);
		}
		setSelectedUsername("");
	}, [adminUsersIndex, isEditMode, memberStatusMap, pendingAdds, pendingRemovals, selectedOrgPath, selectedUsername]);

	const handleToggleMember = useCallback(
		(member: MemberView) => {
			if (!isEditMode) return;
			const key = member.username.toLowerCase();
			if (member.origin === "new") {
				const next = new Map(pendingAdds);
				next.delete(key);
				setPendingAdds(next);
				return;
			}
			const nextRemovals = new Set(pendingRemovals);
			if (nextRemovals.has(key)) {
				nextRemovals.delete(key);
			} else {
				nextRemovals.add(key);
			}
			setPendingRemovals(nextRemovals);
		},
		[isEditMode, pendingAdds, pendingRemovals],
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
			const base = baseMembersMap.get(key);
			const userRecord = adminUsersIndex.get(key);
			const username = base?.username ?? userRecord?.username ?? key;
			const displayName =
				base?.displayName?.trim() ||
				userRecord?.fullName?.trim() ||
				userRecord?.username ||
				base?.username ||
				username;
			const keycloakId = userRecord?.keycloakId ?? undefined;
			return { username, displayName, keycloakId };
		});

		const finalMembers = new Map(baseMembersMap);
		effectiveRemovals.forEach((key) => {
			finalMembers.delete(key);
		});
		effectiveAdds.forEach((draft, key) => {
			finalMembers.set(key, { username: draft.username, displayName: draft.displayName });
		});
		const memberAdded = Array.from(finalMembers.keys()).some((key) => !baseMembersMap.has(key));
		const memberRemoved = Array.from(baseMembersMap.keys()).some((key) => !finalMembers.has(key));
		const membersChanged = memberAdded || memberRemoved;

		if (!scopeChanged && !descriptionChanged && !displayChanged && !membersChanged) {
			toast.info("未检测到变更，无需提交审批");
			return;
		}

		if (displayChanged && trimmedDisplay.length === 0) {
			toast.error("角色名称不能为空");
			return;
		}

		setUpdating(true);
		try {
			const roleName = toRoleName(authorityName);
			const payload: Record<string, unknown> = {
				id: targetRole.id,
				name: roleName,
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
				name: roleName,
				displayName: targetRole.displayName || targetRole.name || null,
				scope: targetRole.scope ?? null,
				description: targetRole.description ?? null,
				members: Array.from(baseMembersMap.values()).map((member) => member.username),
			};
			const diffAfter = {
				id: targetRole.id ?? null,
				name: roleName,
				displayName: trimmedDisplay || null,
				scope,
				description: description.trim() || null,
				memberAdds: memberAddsPayload.map((item) => item.username),
				memberRemoves: memberRemovesPayload.map((item) => item.username),
				members: Array.from(finalMembers.values()).map((member) => member.username),
			};

			const change = await adminApi.createChangeRequest({
				resourceType: "ROLE",
				action: "UPDATE",
				resourceId: roleName || authorityName,
				payloadJson: JSON.stringify(payload),
				diffJson: JSON.stringify({ before: diffBefore, after: diffAfter }),
				reason: updateReason.trim() || undefined,
			});
			await adminApi.submitChangeRequest(change.id);
			toast.success("角色变更申请已提交审批");
			const nextLabel = trimmedDisplay || (targetRole.displayName || targetRole.name || authorityName || "");
			setDisplayLabel(nextLabel);
			setPendingAdds(new Map());
			setPendingRemovals(new Set());
			setSelectedUsername("");
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
		baseMembersMap,
		description,
		pendingAdds,
		displayLabel,
		pendingRemovals,
		queryClient,
		realmMembers,
		scope,
		targetRole,
		updateReason,
		adminUsersIndex,
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
				<Button variant="outline" onClick={() => navigate("/admin/roles")}>
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
				<Button variant="outline" onClick={() => navigate("/admin/roles")}>
					返回角色列表
				</Button>
				{!isEditMode ? (
					<Button onClick={() => navigate(`/admin/roles/${encodeURIComponent(roleKey)}/edit`)}>
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
							{pendingChange!.requestedAt ? `，提交时间 ${formatDateTime(pendingChange!.requestedAt)}` : ""}）。审批完成前无法提交新的编辑请求。
						</p>
						<div className="flex flex-wrap gap-2">
							<Button variant="outline" size="sm" onClick={() => navigate("/admin/my-changes")}>
								查看我的申请
							</Button>
							<Button variant="outline" size="sm" onClick={() => navigate("/admin/approval")}>
								前往待审批列表
							</Button>
						</div>
					</AlertDescription>
				</Alert>
			) : null}

			<Card>
				<CardHeader>
					<div className="space-y-2">
						{isEditMode ? (
							<div className="space-y-2">
								<Text variant="body3" className="font-medium">
									角色名称
								</Text>
								<Input
									value={displayLabel}
									onChange={(event) => setDisplayLabel(event.target.value)}
									placeholder="请输入角色名称"
								/>
							</div>
						) : (
							<CardTitle>{displayLabelText}</CardTitle>
						)}
						<Text variant="body3" className="text-muted-foreground">
							角色标识：{authorityName}
						</Text>
					</div>
				</CardHeader>
				<CardContent className="space-y-8 text-sm">
					<section className="grid gap-4 md:grid-cols-2">
						<div className="space-y-1">
							<Text variant="body3" className="font-medium">
								所属域
							</Text>
							{isEditMode ? (
								<SelectScope value={scope} onChange={setScope} />
							) : (
								<Text variant="body3" className="text-muted-foreground">
									{scope === "INSTITUTE" ? "全所共享域" : "部门域"}
								</Text>
							)}
						</div>
						<div className="space-y-1">
							<Text variant="body3" className="font-medium">
								角色成员数
							</Text>
							<Text variant="body3" className="text-muted-foreground">
								{visibleMemberCount} 人
								{hasPendingMemberChange ? "（含待审批变更）" : ""}
							</Text>
						</div>
					</section>

					<section className="space-y-2">
						<Text variant="body3" className="font-medium">
							角色描述
						</Text>
						{isEditMode ? (
							<Textarea
								rows={3}
								placeholder="更新角色说明"
								value={description}
								onChange={(event) => setDescription(event.target.value)}
							/>
						) : (
							<Text variant="body3" className="text-muted-foreground">
								{targetRole.description?.trim() || "未填写"}
							</Text>
						)}
					</section>

					{isEditMode ? (
						<section className="space-y-2">
							<Text variant="body3" className="font-medium">
								审批备注（可选）
							</Text>
							<Textarea
								rows={2}
								placeholder="补充审批说明"
								value={updateReason}
								onChange={(event) => setUpdateReason(event.target.value)}
							/>
						</section>
					) : null}

					<section className="space-y-4 border-t border-slate-200 pt-4">
						<div className="space-y-1">
							<Text variant="body3" className="font-medium">
								角色成员
							</Text>
							<Text variant="body3" className="text-muted-foreground">
								Keycloak 成员 {realmMembers.length} 人
								{hasPendingMemberChange ? "，当前包含待审批的新增/移除" : ""}
							</Text>
						</div>

						{isEditMode ? (
							<div className="space-y-4 rounded-lg border border-dashed border-slate-200 p-4">
								<div className="flex items-center gap-2">
									<Icon icon="mdi:account-multiple-plus-outline" className="h-5 w-5 text-primary" />
									<Text variant="body3">选择新增成员</Text>
								</div>
								<div className="flex flex-col gap-4 lg:flex-row lg:items-start">
									<div className="space-y-2 lg:w-64">
										<Text variant="body3" className="font-medium">
											所在部门
										</Text>
										<TreeSelect
											className="w-full"
											placeholder="选择部门"
											treeDefaultExpandAll
											allowClear
											value={selectedOrgPath || undefined}
											onChange={(value) => {
												setSelectedOrgPath(normalizeOrgPath(value || ""));
												setSelectedUsername("");
												setMemberError("");
											}}
											treeData={orgOptions}
											style={{ width: "100%" }}
										/>
									</div>
									<div className="space-y-2 lg:w-80">
										<Text variant="body3" className="font-medium">
											候选成员
										</Text>
										<AntSelect
											className="w-full"
											style={{ width: "100%", maxWidth: 320 }}
											showSearch
											allowClear
											placeholder={selectedOrgPath ? "选择成员" : "请先选择部门"}
											value={selectedUsername || undefined}
											disabled={!selectedOrgPath}
											onChange={(value) => {
												setSelectedUsername(value);
												setMemberError("");
											}}
											filterOption={(input, option) =>
												(option?.label as string).toLowerCase().includes(input.toLowerCase())
											}
											options={filteredUsers.map((user) => ({
												value: user.username,
												label: user.fullName?.trim()
													? `${user.fullName}（${user.username}）`
													: user.username,
											}))}
										/>
									</div>
								</div>
								<div className="flex flex-wrap items-center justify-between gap-3">
									<Text variant="body3" className="text-muted-foreground">
										已选待新增成员 {pendingAdds.size} 人
									</Text>
									<Button type="button" onClick={handleQueueAdd}>
										加入待审批列表
									</Button>
								</div>
								{memberError ? (
									<Text variant="body3" className="text-destructive">
										{memberError}
									</Text>
								) : null}
							</div>
						) : null}

						<section className="space-y-3">
							<Text variant="body3" className="font-medium">
								当前成员
							</Text>
							{membersLoading ? (
								<Text variant="body3" className="text-muted-foreground">
									加载中…
								</Text>
							) : memberViews.length === 0 ? (
								<Text variant="body3" className="text-muted-foreground">
									暂无成员。
								</Text>
							) : (
								memberViews.map((member) => (
									<div key={member.username} className="rounded-lg border px-4 py-3">
										<div className="flex flex-wrap items-start justify-between gap-2">
											<div>
												<Text variant="body2" className="font-medium">
													{member.displayName}
												</Text>
												<Text variant="body3" className="text-muted-foreground">
													{member.username}
												</Text>
											</div>
											<div className="flex items-center gap-2">
												{member.status === "add" ? (
													<Badge variant="secondary" className="border-emerald-500 text-emerald-600">
														新增
													</Badge>
												) : null}
												{member.status === "remove" ? (
													<Badge variant="destructive" className="bg-red-50 text-red-600">
														待移除
													</Badge>
												) : null}
												{isEditMode ? (
													<Button size="sm" variant="outline" onClick={() => handleToggleMember(member)}>
														{member.origin === "new"
															? "撤销新增"
															: member.status === "remove"
																? "恢复"
																: "移除"}
													</Button>
												) : null}
											</div>
										</div>
									</div>
								))
							)}
						</section>
					</section>

					{isEditMode ? (
						<div className="flex justify-end border-t border-slate-200 pt-4">
				<Button onClick={handleSubmitChanges} disabled={updating || hasPendingChange}>
					{updating ? "提交中…" : "提交角色编辑"}
				</Button>
						</div>
					) : null}
				</CardContent>
			</Card>
		</div>
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
	return trimmed.toUpperCase().replace(/^ROLE[_-]?/, "").replace(/_/g, "");
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
	const payloadName = payload && typeof payload === "object" ? canonicalRole((payload as any).name || (payload as any).role) : "";
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
	return path.startsWith("/") ? path.replace(/\/{2,}/g, "/").replace(/\/$/, "") : "/" + path.replace(/\/{2,}/g, "/").replace(/\/$/, "");
}

function buildOrgOptions(nodes: OrganizationNode[]): {
	options: TreeSelectProps["treeData"];
	normalize: (value: string) => string;
} {
	const result: OrgTreeOption[] = [];

	const build = (tree: OrganizationNode[], prefix: string[]) => {
		return tree.map((node) => {
			const segment = node.name ?? "";
			const nextPath = [...prefix, segment].filter(Boolean);
			const groupPath = node.groupPath ? normalizeGroupPath(node.groupPath) : normalizeGroupPath("/" + nextPath.join("/"));
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
				type="button"
				variant={value === "DEPARTMENT" ? "default" : "outline"}
				onClick={() => onChange("DEPARTMENT")}
			>
				部门域
			</Button>
			<Button type="button" variant={value === "INSTITUTE" ? "default" : "outline"} onClick={() => onChange("INSTITUTE")}>
				全所共享域
			</Button>
		</div>
	);
}
