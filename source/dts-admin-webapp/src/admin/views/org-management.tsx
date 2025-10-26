import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { type Resolver, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { adminApi } from "@/admin/api/adminApi";
import { KeycloakGroupService, KeycloakUserService } from "@/api/services/keycloakService";
import type { KeycloakUser } from "#/keycloak";
import type { OrganizationNode, OrganizationCreatePayload, OrganizationUpdatePayload } from "@/admin/types";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from "@/ui/form";
import { Input } from "@/ui/input";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Text } from "@/ui/typography";
import { Textarea } from "@/ui/textarea";
import { Switch } from "@/ui/switch";

const orgFormSchema = z
	.object({
		name: z.string().trim().min(1, "请输入部门名称"),
		description: z.preprocess((value) => {
			if (typeof value === "string") {
				const trimmed = value.trim();
				return trimmed.length === 0 ? undefined : trimmed;
			}
			return value ?? undefined;
		}, z.string().max(2000, "部门说明过长").optional()),
		parentId: z.number().int().positive().nullable().optional(),
		isRoot: z.boolean().optional().default(false),
	})
	.superRefine((values, ctx) => {
		const parentProvided = values.parentId != null;
		const rootFlag = Boolean(values.isRoot);
		if (rootFlag && parentProvided) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: "ROOT 节点不能指定上级部门",
				path: ["parentId"],
			});
		}
		if (!rootFlag && !parentProvided) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: "请选择上级部门",
				path: ["parentId"],
			});
		}
	});

type OrgFormValues = z.infer<typeof orgFormSchema>;
type FlattenedOrganization = OrganizationNode & { level: number; path: string[] };

interface FormState {
	open: boolean;
	mode: "create" | "edit";
	parentId: number | null;
	target: FlattenedOrganization | null;
}

interface ParentOption {
	value: string;
	label: string;
	disabled?: boolean;
}

export default function OrgManagementView() {
	const queryClient = useQueryClient();
	const { data: tree = [], isLoading } = useQuery({
		queryKey: ["admin", "organizations"],
		queryFn: adminApi.getOrganizations,
		refetchOnWindowFocus: true,
		refetchInterval: 60000,
	});
	const [search, setSearch] = useState("");
	const [selectedId, setSelectedId] = useState<number | null>(null);
	const [formState, setFormState] = useState<FormState>({
		open: false,
		mode: "create",
		parentId: null,
		target: null,
	});
	const [deleteState, setDeleteState] = useState<{ open: boolean; target: FlattenedOrganization | null }>({
		open: false,
		target: null,
	});
	const flattened = useMemo(() => flattenTree(tree), [tree]);
	const filteredTree = useMemo(() => {
		if (!search.trim()) return tree;
		const keyword = search.trim().toLowerCase();
		return filterTree(tree, keyword);
	}, [search, tree]);

	const rootNode = useMemo(() => flattened.find((node) => node.isRoot) ?? null, [flattened]);
	const rootNodeName = rootNode?.name ?? "";
	const rootExists = useMemo(() => rootNode != null, [rootNode]);

	const totalOrg = useMemo(() => flattened.length, [flattened]);

	const selected = useMemo(() => {
		if (!selectedId) return null;
		return flattened.find((item) => item.id === selectedId) ?? null;
	}, [flattened, selectedId]);

	// Group members for the selected organization (by Keycloak group id)
	const [members, setMembers] = useState<Array<{ username: string; fullName?: string }>>([]);
	const [membersLoading, setMembersLoading] = useState(false);
	useEffect(() => {
		let cancelled = false;
		async function loadMembers() {
			if (!selected?.keycloakGroupId) {
				if (!cancelled) setMembers([]);
				return;
			}
			setMembersLoading(true);
			try {
				const list = await KeycloakGroupService.getGroupMembers(selected.keycloakGroupId);
				// Load all users once to enrich with full names
				let allUsers: KeycloakUser[] = [];
				try {
					allUsers = await KeycloakUserService.getAllUsers({ first: 0, max: 1000 });
				} catch (e) {
					console.warn("load all users failed", e);
				}
				const byUsername = new Map<string, KeycloakUser>();
				allUsers.forEach((u) => {
					const key = (u.username || u.id || "").toString();
					if (key) byUsername.set(key, u);
				});

				let result: Array<{ username: string; fullName?: string }> = [];
				if (Array.isArray(list) && list.length > 0) {
					result = list
						.map((uname) => {
							const u = byUsername.get(uname);
							return {
								username: uname,
								fullName: (u?.fullName || u?.firstName || u?.lastName || u?.attributes?.fullName?.[0]) as
									| string
									| undefined,
							};
						})
						.filter((x) => x.username);
				} else {
					// Fallback: dev store may return empty; derive by scanning users
					try {
						const normalizedPath = (selected.groupPath || "").startsWith("/")
							? selected.groupPath!
							: `/${selected.groupPath || ""}`;
						const orgIdText = String(selected.id);
						const matched = allUsers.filter((u) => {
							const groups = Array.isArray(u.groups)
								? u.groups.map((p: string) => (p && p.startsWith("/") ? p : `/${p}`))
								: [];
							const inGroup = groups.includes(normalizedPath);
							if (inGroup) return true;
							const deptCode = (u.attributes?.dept_code?.[0] || "").toString().trim();
							return deptCode && deptCode === orgIdText;
						});
						result = matched.map((u) => ({
							username: u.username || (u.id as string),
							fullName: (u.fullName || u.firstName || u.lastName || u.attributes?.fullName?.[0]) as string | undefined,
						}));
					} catch (scanErr) {
						console.warn("fallback scan users failed", scanErr);
					}
				}
				if (!cancelled) setMembers(result || []);
			} catch (e) {
				console.warn("Failed to load group members", e);
				if (!cancelled) setMembers([]);
			} finally {
				if (!cancelled) setMembersLoading(false);
			}
		}
		loadMembers();
		return () => {
			cancelled = true;
		};
	}, [selected?.keycloakGroupId]);

	const createMutation = useMutation({
		mutationFn: (payload: OrganizationCreatePayload) => adminApi.createOrganization(payload),
		onSuccess: (tree) => {
			queryClient.setQueryData(["admin", "organizations"], tree);
		},
		onError: (error: unknown) => {
			console.error(error);
			toast.error(error instanceof Error ? error.message : "创建部门失败");
		},
	});
	const updateMutation = useMutation({
		mutationFn: ({ id, payload }: { id: number; payload: OrganizationUpdatePayload }) =>
			adminApi.updateOrganization(id, payload),
		onSuccess: (tree) => {
			queryClient.setQueryData(["admin", "organizations"], tree);
		},
		onError: (error: unknown) => {
			console.error(error);
			toast.error(error instanceof Error ? error.message : "更新部门失败");
		},
	});
	const deleteMutation = useMutation({
		mutationFn: (id: number) => adminApi.deleteOrganization(id),
		onSuccess: (tree) => {
			queryClient.setQueryData(["admin", "organizations"], tree);
		},
		onError: (error: unknown) => {
			console.error(error);
			toast.error(error instanceof Error ? error.message : "删除部门失败");
		},
	});

	const refreshOrganizations = async () => {
		await queryClient.invalidateQueries({ queryKey: ["admin", "organizations"] });
	};

	const formLoading = createMutation.isPending || updateMutation.isPending;

	const openCreateRoot = () => {
		if (rootExists) {
			toast.error("仅允许存在一个根部门");
			return;
		}
		setFormState({ open: true, mode: "create", parentId: null, target: null });
	};

	const openCreateChild = () => {
		if (!selected) return;
		setFormState({ open: true, mode: "create", parentId: selected.id, target: selected });
	};

	const openEdit = () => {
		if (!selected) return;
		setFormState({ open: true, mode: "edit", parentId: selected.parentId ?? null, target: selected });
	};

	const openDelete = () => {
		if (!selected) return;
		setDeleteState({ open: true, target: selected });
	};

	const closeForm = () => setFormState({ open: false, mode: "create", parentId: null, target: null });
	const closeDelete = () => setDeleteState({ open: false, target: null });

	const handleSubmitForm = async (values: OrgFormValues) => {
		const rootFlag = Boolean(values.isRoot);
		const parentId = rootFlag ? null : values.parentId ?? null;
		if (formState.mode === "create") {
			if (rootFlag && rootExists) {
				toast.error("仅允许存在一个 ROOT 节点");
				return;
			}
			const payload: OrganizationCreatePayload = {
				name: values.name,
				description: values.description,
				parentId,
				isRoot: rootFlag,
			};
			try {
				await createMutation.mutateAsync(payload);
				closeForm();
				setSelectedId(parentId);
				await refreshOrganizations();
				toast.success("部门已创建并同步 服务端");
			} catch (error) {
				console.error(error);
			}
			return;
		}

		if (formState.mode === "edit" && formState.target) {
			const payload: OrganizationUpdatePayload = {
				name: values.name,
				description: values.description,
				parentId,
				isRoot: values.isRoot,
			};
			try {
				await updateMutation.mutateAsync({ id: formState.target.id, payload });
				closeForm();
				await refreshOrganizations();
				toast.success("部门信息已更新并同步 服务端");
			} catch (error) {
				console.error(error);
			}
		}
	};

	const handleConfirmDelete = async () => {
		const target = deleteState.target;
		if (!target) return;
		try {
			await deleteMutation.mutateAsync(target.id);
			closeDelete();
			setSelectedId((current) => {
				if (current === target.id) {
					return target.parentId ?? null;
				}
				return current;
			});
			await refreshOrganizations();
			toast.success("部门已删除并同步 服务端");
		} catch (error) {
			console.error(error);
		}
	};

	const rootSelectionAllowed = useMemo(() => {
		if (!rootExists) {
			return true;
		}
		if (formState.mode === "edit" && formState.target?.isRoot) {
			return true;
		}
		return false;
	}, [formState.mode, formState.target?.isRoot, rootExists]);

	const rootToggleMessage = useMemo(() => {
		if (rootSelectionAllowed) {
			return undefined;
		}
		if (rootNodeName) {
			return `ROOT 节点已由 ${rootNodeName} 占用`;
		}
		return "仅允许存在一个 ROOT 节点";
	}, [rootSelectionAllowed, rootNodeName]);

	const editingNode = formState.mode === "edit" ? formState.target : null;
	const initialValues: OrgFormValues = editingNode
		? {
				name: editingNode.name,
				description: editingNode.description ?? "",
				parentId: editingNode.parentId ?? null,
				isRoot: Boolean(editingNode.isRoot),
			}
		: {
				name: "",
				description: "",
				parentId: formState.parentId ?? null,
				isRoot: formState.parentId == null ? !rootExists : false,
			};

	const disabledParentIds = useMemo(() => {
		if (formState.mode !== "edit" || !formState.target) {
			return new Set<number>();
		}
		const ids = new Set<number>([formState.target.id]);
		for (const id of collectDescendantIds(formState.target)) {
			ids.add(id);
		}
		return ids;
	}, [formState]);

	const parentOptions = useMemo(() => {
		return flattened.map((node) => {
			const indentPrefix = node.level > 1 ? `${"--".repeat(node.level - 1)} ` : "";
			const label = `${indentPrefix}${node.name}`;
			return {
				value: node.id.toString(),
				label,
				disabled: disabledParentIds.has(node.id),
			};
		});
	}, [disabledParentIds, flattened]);

	return (
		<div className="grid gap-6 xl:grid-cols-[minmax(0,0.6fr)_minmax(0,1fr)]">
			<Card>
				<CardHeader className="space-y-3">
					<div className="flex items-center justify-between gap-3">
						<CardTitle>组织结构</CardTitle>
						<div className="flex items-center gap-2">
							<Button
								size="sm"
								onClick={openCreateRoot}
								disabled={rootExists}
								title={rootExists ? "仅允许存在一个根部门" : undefined}
							>
								创建部门
							</Button>
						</div>
					</div>
					<Input placeholder="搜索部门" value={search} onChange={(event) => setSearch(event.target.value)} />
					<div className="text-sm text-muted-foreground">组织数：{totalOrg}</div>
				</CardHeader>
				<CardContent className="h-[560px] p-0">
					{isLoading ? (
						<Text variant="body3" className="p-4">
							加载中...
						</Text>
					) : (
						<ScrollArea className="h-full">
							<div className="p-4">
								{filteredTree.length === 0 ? (
									<Text variant="body3">未找到匹配的组织。</Text>
								) : (
									<OrganizationTree tree={filteredTree} onSelect={setSelectedId} selectedId={selectedId} />
								)}
							</div>
						</ScrollArea>
					)}
				</CardContent>
			</Card>

			<div className="space-y-6">
				<Card>
					<CardHeader className="flex flex-wrap items-center justify-between gap-3">
						<CardTitle>组织详情</CardTitle>
						<div className="flex flex-wrap gap-2">
							<Button variant="outline" size="sm" onClick={openCreateChild} disabled={!selected}>
								创建下级
							</Button>
							<Button variant="outline" size="sm" onClick={openEdit} disabled={!selected}>
								编辑
							</Button>
							<Button
								variant="ghost"
								size="sm"
								className="text-destructive hover:text-destructive"
								onClick={openDelete}
								disabled={!selected || deleteMutation.isPending}
							>
								删除
							</Button>
						</div>
					</CardHeader>
					<CardContent className="space-y-4 text-sm">
						{selected ? (
							<>
								<Text variant="body2" className="font-semibold">
									{selected.name}
								</Text>
								<p className="text-muted-foreground">部门编号：{selected.id}</p>
								<p className="text-muted-foreground">
									上级部门：{selected.path.slice(0, -1).join(" / ") || "无（一级部门）"}
								</p>
								<p className="text-muted-foreground">
									节点类型：{selected.isRoot ? "ROOT（全局公开）" : "普通部门"}
								</p>
								{selected.description ? (
									<div className="rounded-md border border-dashed border-muted/60 bg-muted/30 p-3 text-sm text-muted-foreground">
										{selected.description}
									</div>
								) : null}
								<div className="grid gap-3 sm:grid-cols-2">
									<div>
										<p className="text-xs text-muted-foreground">服务端 组 ID</p>
										<p className="text-sm font-medium text-foreground">{selected.keycloakGroupId ?? "--"}</p>
									</div>
									<div>
										<p className="text-xs text-muted-foreground">组路径</p>
										<p className="text-sm font-medium text-foreground">{selected.groupPath ?? "--"}</p>
									</div>
								</div>
							</>
						) : (
							<Text variant="body3" className="text-muted-foreground">
								请选择左侧组织查看详情。
							</Text>
						)}
					</CardContent>
				</Card>

				<Card>
					<CardHeader className="flex items-center justify-between gap-3">
						<CardTitle>员工列表</CardTitle>
						{selected && <Badge variant="outline">{members.length}</Badge>}
					</CardHeader>
					<CardContent className="min-h-[120px]">
						{!selected ? (
							<Text variant="body3" className="text-muted-foreground">
								请选择左侧组织查看成员。
							</Text>
						) : membersLoading ? (
							<Text variant="body3" className="text-muted-foreground">
								加载成员中…
							</Text>
						) : members.length === 0 ? (
							<Text variant="body3" className="text-muted-foreground">
								暂无成员
							</Text>
						) : (
							<ScrollArea className="max-h-64">
								<table className="w-full min-w-[560px] table-fixed text-sm">
									<thead className="sticky top-0 bg-muted/40 text-left text-xs uppercase text-muted-foreground">
										<tr>
											<th className="px-3 py-2 w-[48px]">#</th>
											<th className="px-3 py-2">用户名</th>
											<th className="px-3 py-2">姓名</th>
										</tr>
									</thead>
									<tbody>
										{members.map((m, idx) => (
											<tr key={m.username} className="border-b last:border-b-0">
												<td className="px-3 py-2 text-xs text-muted-foreground">{idx + 1}</td>
												<td className="px-3 py-2 truncate" title={m.username}>
													{m.username}
												</td>
												<td className="px-3 py-2 truncate" title={m.fullName || "-"}>
													{m.fullName || "-"}
												</td>
											</tr>
										))}
									</tbody>
								</table>
							</ScrollArea>
						)}
					</CardContent>
				</Card>
			</div>

			<OrganizationFormDialog
				open={formState.open}
				mode={formState.mode}
				loading={formLoading}
				initialValues={initialValues}
				parentOptions={parentOptions}
				rootSelectionAllowed={rootSelectionAllowed}
				rootToggleMessage={rootToggleMessage}
				onSubmit={handleSubmitForm}
				onClose={closeForm}
			/>

			<ConfirmDeleteDialog
				open={deleteState.open}
				name={deleteState.target?.name}
				childCount={deleteState.target?.children?.length ?? 0}
				loading={deleteMutation.isPending}
				onCancel={closeDelete}
				onConfirm={handleConfirmDelete}
			/>
		</div>
	);
}
function flattenTree(
	tree: OrganizationNode[],
	level = 1,
	parentPath: string[] = [],
): (OrganizationNode & { level: number; path: string[] })[] {
	const result: (OrganizationNode & { level: number; path: string[] })[] = [];
	for (const node of tree) {
		const path = [...parentPath, node.name];
		result.push({ ...node, level, path });
		if (node.children?.length) {
			result.push(...flattenTree(node.children, level + 1, path));
		}
	}
	return result;
}

function collectDescendantIds(node?: OrganizationNode | null): number[] {
	if (!node?.children?.length) {
		return [];
	}
	const ids: number[] = [];
	for (const child of node.children) {
		ids.push(child.id);
		ids.push(...collectDescendantIds(child));
	}
	return ids;
}

function filterTree(tree: OrganizationNode[], keyword: string): OrganizationNode[] {
	const includesKeyword = (value?: string | null) => (value ? value.toLowerCase().includes(keyword) : false);
	const matchNode = (node: OrganizationNode): OrganizationNode | null => {
		const hit =
			includesKeyword(node.name) ||
			includesKeyword(node.description) ||
			includesKeyword(node.id ? String(node.id) : undefined) ||
			includesKeyword(node.groupPath) ||
			includesKeyword(node.keycloakGroupId);
		const children = node.children?.map(matchNode).filter((item): item is OrganizationNode => Boolean(item)) ?? [];
		if (hit || children.length > 0) {
			return { ...node, children };
		}
		return null;
	};
	return tree.map(matchNode).filter((item): item is OrganizationNode => Boolean(item));
}

interface TreeProps {
	tree: OrganizationNode[];
	onSelect: (id: number) => void;
	selectedId: number | null;
	depth?: number;
}

function OrganizationTree({ tree, onSelect, selectedId, depth = 0 }: TreeProps) {
	return (
		<ul className="space-y-1">
			{tree.map((node) => {
				const isActive = selectedId === node.id;
				return (
					<li key={node.id}>
						<button
							type="button"
							onClick={() => onSelect(node.id)}
							className={`flex w-full items-center justify-between rounded-md px-2 py-2 text-left text-sm transition ${
								isActive ? "bg-primary text-primary-foreground" : "hover:bg-muted"
							}`}
							style={{ paddingLeft: depth * 16 + 8 }}
						>
							<div className="flex min-w-0 flex-1 flex-col">
								<span className="truncate font-medium">{node.name}</span>
								{node.description ? (
									<span
										className={`truncate text-xs ${isActive ? "text-primary-foreground/80" : "text-muted-foreground"}`}
									>
										{node.description}
									</span>
								) : null}
							</div>
						</button>
						{node.children?.length ? (
							<div className="ml-2 border-l border-border pl-2">
								<OrganizationTree tree={node.children} onSelect={onSelect} selectedId={selectedId} depth={depth + 1} />
							</div>
						) : null}
					</li>
				);
			})}
		</ul>
	);
}

interface OrganizationFormDialogProps {
	open: boolean;
	mode: "create" | "edit";
	loading?: boolean;
	initialValues: OrgFormValues;
	parentOptions: ParentOption[];
	rootSelectionAllowed: boolean;
	rootToggleMessage?: string;
	onSubmit: (values: OrgFormValues) => Promise<void>;
	onClose: () => void;
}

function OrganizationFormDialog({
	open,
	mode,
	loading,
	initialValues,
	parentOptions,
	rootSelectionAllowed,
	rootToggleMessage,
	onSubmit,
	onClose,
}: OrganizationFormDialogProps) {
	const form = useForm<OrgFormValues, any, OrgFormValues>({
		resolver: zodResolver(orgFormSchema) as Resolver<OrgFormValues>,
		defaultValues: initialValues,
	});

	useEffect(() => {
		if (open) {
			form.reset(initialValues);
		}
	}, [form, initialValues, open]);

	const handleOpenChange = (value: boolean) => {
		if (!value && !loading) {
			onClose();
		}
	};

	const handleSubmit = form.handleSubmit(async (values: OrgFormValues) => {
		await onSubmit(values);
	});

	const title = mode === "create" ? "创建部门" : "编辑部门";
	const submitText = mode === "create" ? "创建" : "保存";
	const isRootValue = form.watch("isRoot");

	useEffect(() => {
		if (open && !rootSelectionAllowed && form.getValues("isRoot")) {
			form.setValue("isRoot", false, { shouldValidate: false, shouldDirty: false });
		}
	}, [form, open, rootSelectionAllowed]);

	return (
		<Dialog open={open} onOpenChange={handleOpenChange}>
			<DialogContent className="min-w-[420px] max-w-[520px]">
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
					<DialogDescription>提交后会立即保存并同步至 Keycloak。</DialogDescription>
				</DialogHeader>
				<Form {...form}>
					<form onSubmit={handleSubmit} className="space-y-4">
						<FormField
							control={form.control}
							name="name"
							render={({ field }) => (
								<FormItem>
									<FormLabel>部门名称</FormLabel>
									<FormControl>
										<Input placeholder="请输入部门名称" {...field} />
									</FormControl>
									<FormMessage />
								</FormItem>
							)}
						/>
						<FormField
							control={form.control}
							name="isRoot"
							render={({ field }) => (
								<FormItem className="flex items-start justify-between space-x-4 rounded-md border border-border/60 p-3">
									<div className="space-y-1">
										<FormLabel className="text-sm font-medium">标记为 ROOT 节点</FormLabel>
										<FormDescription>
											{rootSelectionAllowed
												? "ROOT 节点代表全局公开的组织，数据集归属此节点时所有用户均可访问。"
												: rootToggleMessage ?? "已有 ROOT 节点，其他部门不可标记为 ROOT。"}
										</FormDescription>
									</div>
									<FormControl>
										<Switch
											checked={Boolean(field.value)}
											disabled={!rootSelectionAllowed}
											title={
												rootSelectionAllowed
													? undefined
													: rootToggleMessage ?? "已有 ROOT 节点，其他部门不可标记为 ROOT。"
											}
											onCheckedChange={(checked) => {
												if (!rootSelectionAllowed) {
													return;
												}
												field.onChange(checked);
												if (checked) {
													form.setValue("parentId", null, { shouldValidate: true });
												}
											}}
										/>
									</FormControl>
								</FormItem>
							)}
						/>
						<FormField
							control={form.control}
							name="parentId"
							render={({ field }) => {
								const value = field.value == null ? "root" : String(field.value);
								return (
									<FormItem>
										<FormLabel>上级部门</FormLabel>
										<FormControl>
											<Select
												disabled={Boolean(isRootValue)}
												onValueChange={(next) => field.onChange(next === "root" ? null : Number(next))}
												value={value}
											>
												<SelectTrigger className="w-full justify-between" disabled={Boolean(isRootValue)}>
													<SelectValue placeholder="请选择上级部门" />
												</SelectTrigger>
												<SelectContent>
													<SelectItem value="root">无（一级部门）</SelectItem>
													{parentOptions.map((option) => (
														<SelectItem key={option.value} value={option.value} disabled={option.disabled}>
															{option.label}
														</SelectItem>
													))}
												</SelectContent>
											</Select>
										</FormControl>
										<FormDescription>{Boolean(isRootValue) ? "ROOT 节点不能指定上级部门" : "请选择该部门的父级组织"}</FormDescription>
										<FormMessage />
									</FormItem>
								);
							}}
						/>
						<FormField
							control={form.control}
							name="description"
							render={({ field }) => (
								<FormItem>
									<FormLabel>部门说明</FormLabel>
									<FormControl>
										<Textarea placeholder="可选，描述部门职责、范围等信息" rows={3} {...field} />
									</FormControl>
									<FormMessage />
								</FormItem>
							)}
						/>
						<DialogFooter>
							<Button type="button" variant="outline" onClick={onClose} disabled={loading}>
								取消
							</Button>
							<Button type="submit" disabled={loading}>
								{loading ? `${submitText}中...` : submitText}
							</Button>
						</DialogFooter>
					</form>
				</Form>
			</DialogContent>
		</Dialog>
	);
}

interface ConfirmDeleteDialogProps {
	open: boolean;
	name?: string;
	childCount?: number;
	loading?: boolean;
	onCancel: () => void;
	onConfirm: () => void;
}

function ConfirmDeleteDialog({ open, name, childCount = 0, loading, onCancel, onConfirm }: ConfirmDeleteDialogProps) {
	const handleOpenChange = (value: boolean) => {
		if (!value && !loading) {
			onCancel();
		}
	};

	return (
		<Dialog open={open} onOpenChange={handleOpenChange}>
			<DialogContent className="min-w-[420px] max-w-[480px]">
				<DialogHeader>
					<DialogTitle>确认删除部门</DialogTitle>
					<DialogDescription>删除后会立即同步至 Keycloak，请谨慎操作。</DialogDescription>
				</DialogHeader>
				<div className="space-y-3 py-2 text-sm">
					<p>
						即将删除：<span className="font-medium text-foreground">{name ?? "未命名部门"}</span>
					</p>
					{childCount > 0 ? (
						<p className="text-xs text-destructive/80">
							该部门包含 {childCount} 个下级部门，删除后将同时移除所有下级节点。
						</p>
					) : null}
				</div>
				<DialogFooter>
					<Button variant="outline" onClick={onCancel} disabled={loading}>
						取消
					</Button>
					<Button variant="destructive" onClick={onConfirm} disabled={loading}>
						{loading ? "删除中..." : "确认删除"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
