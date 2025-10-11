import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { type Resolver, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { adminApi } from "@/admin/api/adminApi";
import { KeycloakGroupService, KeycloakUserService } from "@/api/services/keycloakService";
import type { KeycloakUser } from "#/keycloak";
import { useAdminLocale } from "@/admin/lib/locale";
import type {
    OrganizationNode,
    OrganizationCreatePayload,
    OrganizationUpdatePayload,
    OrgDataLevel,
} from "@/admin/types";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/ui/form";
import { Input } from "@/ui/input";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Text } from "@/ui/typography";
import { Textarea } from "@/ui/textarea";

const DATA_LEVEL_VALUES = ["DATA_PUBLIC", "DATA_INTERNAL", "DATA_SECRET", "DATA_TOP_SECRET"] as const;
const DATA_LEVEL_OPTIONS: { value: OrgDataLevel; label: string }[] = [
	{ value: "DATA_PUBLIC", label: "公开" },
	{ value: "DATA_INTERNAL", label: "内部" },
	{ value: "DATA_SECRET", label: "秘密" },
	{ value: "DATA_TOP_SECRET", label: "机密" },
];

const LEGACY_LEVEL_MAP: Record<string, OrgDataLevel> = {
	TOP_SECRET: "DATA_TOP_SECRET",
	SECRET: "DATA_SECRET",
	NORMAL: "DATA_INTERNAL",
	UNKNOWN: "DATA_INTERNAL",
};

const orgFormSchema = z.object({
    name: z.string().trim().min(1, "请输入部门名称"),
    description: z.preprocess((value) => {
        if (typeof value === "string") {
            const trimmed = value.trim();
            return trimmed.length === 0 ? undefined : trimmed;
        }
        return value ?? undefined;
    }, z.string().max(2000, "部门说明过长").optional()),
    parentId: z.number().int().positive().nullable().optional(),
    dataLevel: z.enum(DATA_LEVEL_VALUES, { required_error: "请选择部门最大数据密级" }),
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
	const { translateSensitivity } = useAdminLocale();

	const flattened = useMemo(() => flattenTree(tree), [tree]);
	const filteredTree = useMemo(() => {
		if (!search.trim()) return tree;
		const keyword = search.trim().toLowerCase();
		return filterTree(tree, keyword, translateSensitivity);
	}, [search, tree, translateSensitivity]);

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
                                fullName: (u?.fullName || u?.firstName || u?.lastName || u?.attributes?.fullname?.[0]) as string | undefined,
                            };
                        })
                        .filter((x) => x.username);
                } else {
                    // Fallback: dev store may return empty; derive by scanning users
                    try {
                        const normalizedPath = (selected.groupPath || "").startsWith("/") ? selected.groupPath! : `/${selected.groupPath || ""}`;
                        const orgIdText = String(selected.id);
                        const matched = allUsers.filter((u) => {
                            const groups = Array.isArray(u.groups) ? u.groups.map((p: string) => (p && p.startsWith("/") ? p : `/${p}`)) : [];
                            const inGroup = groups.includes(normalizedPath);
                            if (inGroup) return true;
                            const deptCode = (u.attributes?.dept_code?.[0] || '').toString().trim();
                            return deptCode && deptCode === orgIdText;
                        });
                        result = matched.map((u) => ({
                            username: u.username || (u.id as string),
                            fullName: (u.fullName || u.firstName || u.lastName || u.attributes?.fullname?.[0]) as string | undefined,
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
        return () => { cancelled = true; };
    }, [selected?.keycloakGroupId]);

	const stats = useMemo(() => {
		const totalOrg = flattened.length;
		const sensitiveOrgs = flattened.filter((item) => {
			const level = normalizeDataLevel(item.dataLevel ?? item.sensitivity);
			return level === "DATA_SECRET" || level === "DATA_TOP_SECRET";
		}).length;
		const topSecret = flattened.filter(
			(item) => normalizeDataLevel(item.dataLevel ?? item.sensitivity) === "DATA_TOP_SECRET",
		).length;
		return { totalOrg, sensitiveOrgs, topSecret };
	}, [flattened]);

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

    const rootExists = useMemo(() => (Array.isArray(tree) ? tree.length > 0 : false), [tree]);
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
        const parentId = values.parentId ?? null;
        // 友好校验：若选择了上级，子部门最大密级不得高于上级
        if (parentId != null) {
            const parent = flattened.find((n) => n.id === parentId);
            if (parent) {
                const levelRank = (v: OrgDataLevel) => ({
                    DATA_PUBLIC: 1,
                    DATA_INTERNAL: 2,
                    DATA_SECRET: 3,
                    DATA_TOP_SECRET: 4,
                }[v] || 0);
                const parentLevel = normalizeDataLevel((parent.dataLevel as string) ?? (parent.sensitivity as string));
                if (levelRank(values.dataLevel) > levelRank(parentLevel)) {
                    const parentText = getDataLevelFallback(parent.dataLevel ?? parent.sensitivity);
                    const childText = getDataLevelFallback(values.dataLevel);
                    toast.error(`所选最大数据密级（${childText}）不能高于上级部门（${parent.name} / ${parentText}）`);
                    return;
                }
            }
        }
        if (formState.mode === "create") {
            const payload: OrganizationCreatePayload = {
                name: values.name,
                description: values.description,
                parentId,
                dataLevel: values.dataLevel,
            };
            if (parentId == null && rootExists) {
                toast.error("仅允许存在一个根部门");
                return;
            }
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
                dataLevel: values.dataLevel,
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

    const editingNode = formState.mode === "edit" ? formState.target : null;
    const defaultParentLevel = (() => {
        const pid = formState.parentId ?? null;
        if (pid == null) return "DATA_INTERNAL" as OrgDataLevel;
        const parent = flattened.find((n) => n.id === pid);
        return (parent?.dataLevel as OrgDataLevel) || (parent?.sensitivity as OrgDataLevel) || ("DATA_INTERNAL" as OrgDataLevel);
    })();
    const initialValues: OrgFormValues = editingNode
        ? {
                name: editingNode.name,
                description: editingNode.description ?? "",
                parentId: editingNode.parentId ?? null,
                dataLevel: (editingNode.dataLevel as OrgDataLevel) || (editingNode.sensitivity as OrgDataLevel) || "DATA_INTERNAL",
            }
        : {
                name: "",
                description: "",
                parentId: formState.parentId ?? null,
                dataLevel: defaultParentLevel,
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
            <Button size="sm" onClick={openCreateRoot} disabled={rootExists} title={rootExists ? "仅允许存在一个根部门" : undefined}>
                创建部门
            </Button>
					</div>
					</div>
					<Input
						placeholder="搜索部门 / 数据密级"
						value={search}
						onChange={(event) => setSearch(event.target.value)}
					/>
					<div className="flex flex-wrap gap-3 text-sm text-muted-foreground">
						<span>组织数：{stats.totalOrg}</span>
						<span>涉敏部门：{stats.sensitiveOrgs}</span>
						<span>机密部门：{stats.topSecret}</span>
					</div>
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
									<OrganizationTree
										tree={filteredTree}
										onSelect={setSelectedId}
										selectedId={selectedId}
										translateLevel={translateSensitivity}
									/>
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
								<div className="flex flex-wrap items-center gap-3">
									<Text variant="body2" className="font-semibold">
										{selected.name}
									</Text>
									<Badge variant={getDataLevelBadgeVariant(selected.dataLevel ?? selected.sensitivity)}>
										{translateSensitivity(
											selected.dataLevel ?? selected.sensitivity,
											getDataLevelFallback(selected.dataLevel ?? selected.sensitivity),
										)}
									</Badge>
								</div>
								<p className="text-muted-foreground">部门编号：{selected.id}</p>
								<p className="text-muted-foreground">
									上级部门：{selected.path.slice(0, -1).join(" / ") || "无（一级部门）"}
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
                        {selected && (
                            <Badge variant="outline">{members.length}</Badge>
                        )}
                    </CardHeader>
                    <CardContent className="min-h-[120px]">
                        {!selected ? (
                            <Text variant="body3" className="text-muted-foreground">请选择左侧组织查看成员。</Text>
                        ) : membersLoading ? (
                            <Text variant="body3" className="text-muted-foreground">加载成员中…</Text>
                        ) : members.length === 0 ? (
                            <Text variant="body3" className="text-muted-foreground">暂无成员</Text>
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
                                                <td className="px-3 py-2 truncate" title={m.username}>{m.username}</td>
                                                <td className="px-3 py-2 truncate" title={m.fullName || "-"}>{m.fullName || "-"}</td>
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

function filterTree(
	tree: OrganizationNode[],
	keyword: string,
	translateLevel: (value?: string | null, fallback?: string) => string,
): OrganizationNode[] {
	const includesKeyword = (value?: string | null) => (value ? value.toLowerCase().includes(keyword) : false);
	const matchNode = (node: OrganizationNode): OrganizationNode | null => {
		const fallback = getDataLevelFallback(node.dataLevel ?? node.sensitivity);
		const translated = translateLevel(node.dataLevel ?? node.sensitivity, fallback);
		const hit =
			includesKeyword(node.name) ||
			includesKeyword(node.dataLevel ?? node.sensitivity) ||
			includesKeyword(fallback) ||
			includesKeyword(translated);
		const children = node.children?.map(matchNode).filter((item): item is OrganizationNode => Boolean(item)) ?? [];
		if (hit || children.length > 0) {
			return { ...node, children };
		}
		return null;
	};
	return tree.map(matchNode).filter((item): item is OrganizationNode => Boolean(item));
}

function getDataLevelFallback(level?: string | null) {
	const normalized = normalizeDataLevel(level);
	const option = DATA_LEVEL_OPTIONS.find((item) => item.value === normalized);
	return option?.label ?? normalized;
}

function normalizeDataLevel(level?: string | null): OrgDataLevel {
	if (!level) return "DATA_PUBLIC";
	if (DATA_LEVEL_VALUES.includes(level as OrgDataLevel)) {
		return level as OrgDataLevel;
	}
	return LEGACY_LEVEL_MAP[level] ?? "DATA_PUBLIC";
}

function getDataLevelBadgeVariant(level?: string | null) {
	const normalized = normalizeDataLevel(level);
	switch (normalized) {
		case "DATA_TOP_SECRET":
			return "destructive" as const;
		case "DATA_SECRET":
			return "secondary" as const;
		case "DATA_INTERNAL":
			return "default" as const;
		default:
			return "outline" as const;
	}
}

interface TreeProps {
	tree: OrganizationNode[];
	onSelect: (id: number) => void;
	selectedId: number | null;
	depth?: number;
	translateLevel: (value?: string | null, fallback?: string) => string;
}

function OrganizationTree({ tree, onSelect, selectedId, depth = 0, translateLevel }: TreeProps) {
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
							</div>
							<Badge variant={getDataLevelBadgeVariant(node.dataLevel ?? node.sensitivity)} className="ml-2">
								{translateLevel(
									node.dataLevel ?? node.sensitivity,
									getDataLevelFallback(node.dataLevel ?? node.sensitivity),
								)}
							</Badge>
						</button>
						{node.children?.length ? (
							<div className="ml-2 border-l border-border pl-2">
								<OrganizationTree
									tree={node.children}
									onSelect={onSelect}
									selectedId={selectedId}
									depth={depth + 1}
									translateLevel={translateLevel}
								/>
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
	onSubmit: (values: OrgFormValues) => Promise<void>;
	onClose: () => void;
}

function OrganizationFormDialog({
	open,
	mode,
	loading,
	initialValues,
	parentOptions,
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
                    name="dataLevel"
                    render={({ field }) => (
                        <FormItem>
                            <FormLabel>最大数据密级</FormLabel>
                            <FormControl>
                                <Select value={field.value} onValueChange={field.onChange}>
                                    <SelectTrigger className="w-full justify-between">
                                        <SelectValue placeholder="请选择部门可承载的最高数据密级" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {DATA_LEVEL_OPTIONS.map((opt) => (
                                            <SelectItem key={opt.value} value={opt.value}>
                                                {opt.label}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </FormControl>
                            <FormMessage />
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
												onValueChange={(next) => field.onChange(next === "root" ? null : Number(next))}
												value={value}
											>
												<SelectTrigger className="w-full justify-between">
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
										<Textarea
											placeholder="可选，描述部门职责、范围等信息"
											rows={3}
											{...field}
										/>
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
