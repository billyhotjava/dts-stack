import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { type Resolver, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { adminApi } from "@/admin/api/adminApi";
import { ChangeRequestForm } from "@/admin/components/change-request-form";
import { useAdminLocale } from "@/admin/lib/locale";
import type { OrganizationNode, OrganizationPayload, OrgDataLevel } from "@/admin/types";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/ui/form";
import { Input } from "@/ui/input";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Text } from "@/ui/typography";

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
	dataLevel: z.enum(DATA_LEVEL_VALUES, { required_error: "请选择数据密级" }),
	contact: z.preprocess((value) => {
		if (typeof value === "string") {
			const trimmed = value.trim();
			return trimmed.length === 0 ? undefined : trimmed;
		}
		return value;
	}, z.string().max(32, "联系人姓名过长").optional()),
	phone: z.preprocess(
		(value) => {
			if (typeof value === "string") {
				const trimmed = value.trim();
				return trimmed.length === 0 ? undefined : trimmed;
			}
			return value;
		},
		z
			.string()
			.regex(/^[0-9+\-()\s]{5,20}$/, "请输入有效的联系电话")
			.optional(),
	),
});

type OrgFormValues = z.infer<typeof orgFormSchema>;
type FlattenedOrganization = OrganizationNode & { level: number; path: string[] };

interface FormState {
	open: boolean;
	mode: "create" | "edit";
	parentId: number | null;
	target: FlattenedOrganization | null;
}

export default function OrgManagementView() {
	const queryClient = useQueryClient();
	const { data: tree = [], isLoading } = useQuery({
		queryKey: ["admin", "organizations"],
		queryFn: adminApi.getOrganizations,
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
		mutationFn: (payload: OrganizationPayload) => adminApi.createOrganization(payload),
	});
	const updateMutation = useMutation({
		mutationFn: ({ id, payload }: { id: number; payload: OrganizationPayload }) =>
			adminApi.updateOrganization(id, payload),
	});
	const deleteMutation = useMutation({
		mutationFn: (id: number) => adminApi.deleteOrganization(id),
	});

	const formLoading = createMutation.isPending || updateMutation.isPending;

	const openCreateRoot = () => {
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
		if (formState.mode === "create") {
			const payload: OrganizationPayload = {
				...values,
				parentId: formState.parentId ?? null,
			};
			try {
				const created = await createMutation.mutateAsync(payload);
				toast.success("部门新增成功");
				closeForm();
				setSelectedId(created.id);
				await queryClient.invalidateQueries({ queryKey: ["admin", "organizations"] });
			} catch (error) {
				console.error(error);
			}
			return;
		}

		if (formState.mode === "edit" && formState.target) {
			const payload: OrganizationPayload = { ...values };
			try {
				await updateMutation.mutateAsync({ id: formState.target.id, payload });
				toast.success("部门信息已更新");
				closeForm();
				await queryClient.invalidateQueries({ queryKey: ["admin", "organizations"] });
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
			toast.success("部门已删除");
			closeDelete();
			await queryClient.invalidateQueries({ queryKey: ["admin", "organizations"] });
			setSelectedId((current) => {
				if (current === target.id) {
					return target.parentId ?? null;
				}
				return current;
			});
		} catch (error) {
			console.error(error);
		}
	};

	const editingNode = formState.mode === "edit" ? formState.target : null;
	const parentLabel =
		formState.mode === "create"
			? formState.parentId
				? (formState.target?.path.join(" / ") ?? "")
				: "无（一级部门）"
			: editingNode
				? editingNode.path.slice(0, -1).join(" / ") || "无（一级部门）"
				: undefined;
	const initialValues: OrgFormValues = editingNode
		? {
				name: editingNode.name,
				dataLevel: normalizeDataLevel(editingNode.dataLevel ?? editingNode.sensitivity),
				contact: editingNode.contact ?? "",
				phone: editingNode.phone ?? "",
			}
		: {
				name: "",
				dataLevel: normalizeDataLevel(formState.target?.dataLevel ?? formState.target?.sensitivity),
				contact: "",
				phone: "",
			};

	return (
		<div className="grid gap-6 xl:grid-cols-[minmax(0,0.6fr)_minmax(0,1fr)]">
			<Card>
				<CardHeader className="space-y-3">
					<div className="flex items-center justify-between gap-3">
						<CardTitle>组织结构</CardTitle>
						<Button size="sm" onClick={openCreateRoot}>
							新增部门
						</Button>
					</div>
					<Input
						placeholder="搜索部门 / 联系人 / 数据密级"
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
								新增下级
							</Button>
							<Button variant="outline" size="sm" onClick={openEdit} disabled={!selected}>
								编辑
							</Button>
							<Button
								variant="ghost"
								size="sm"
								className="text-destructive hover:text-destructive"
								onClick={openDelete}
								disabled={!selected}
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
								<p className="text-muted-foreground">部门ID：{selected.id}</p>
								<p className="text-muted-foreground">
									上级部门：{selected.path.slice(0, -1).join(" / ") || "无（一级部门）"}
								</p>
								<div className="grid gap-3 sm:grid-cols-2">
									<div>
										<p className="text-xs text-muted-foreground">联系人</p>
										<p className="text-sm font-medium text-foreground">{selected.contact ?? "--"}</p>
									</div>
									<div>
										<p className="text-xs text-muted-foreground">联系电话</p>
										<p className="text-sm font-medium text-foreground">{selected.phone ?? "--"}</p>
									</div>
								</div>
								{selected.description ? (
									<div className="rounded-md border border-dashed border-muted/60 bg-muted/30 p-3 text-sm text-muted-foreground">
										{selected.description}
									</div>
								) : null}
							</>
						) : (
							<Text variant="body3" className="text-muted-foreground">
								请选择左侧组织查看详情。
							</Text>
						)}
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle>发起组织变更</CardTitle>
					</CardHeader>
					<CardContent>
						<ChangeRequestForm initialTab="org" />
					</CardContent>
				</Card>
			</div>

			<OrganizationFormDialog
				open={formState.open}
				mode={formState.mode}
				loading={formLoading}
				initialValues={initialValues}
				parentLabel={parentLabel}
				onSubmit={handleSubmitForm}
				onClose={closeForm}
			/>

			<ConfirmDeleteDialog
				open={deleteState.open}
				name={deleteState.target?.name}
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
			includesKeyword(node.contact) ||
			includesKeyword(node.phone) ||
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
								{node.contact ? (
									<span
										className={`truncate text-xs ${isActive ? "text-primary-foreground/80" : "text-muted-foreground"}`}
									>
										{node.contact}
									</span>
								) : null}
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
	parentLabel?: string;
	onSubmit: (values: OrgFormValues) => Promise<void>;
	onClose: () => void;
}

function OrganizationFormDialog({
	open,
	mode,
	loading,
	initialValues,
	parentLabel,
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

	const title = mode === "create" ? "新增部门" : "编辑部门";
	const submitText = mode === "create" ? "新增" : "保存";

	return (
		<Dialog open={open} onOpenChange={handleOpenChange}>
			<DialogContent className="min-w-[420px] max-w-[520px]">
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
					<DialogDescription>请填写部门基础信息并确认数据密级。</DialogDescription>
				</DialogHeader>
				{parentLabel ? (
					<div className="rounded-md border border-dashed border-muted/60 bg-muted/30 p-3 text-xs text-muted-foreground">
						<span className="font-medium text-foreground">上级部门：</span>
						{parentLabel}
					</div>
				) : null}
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
									<FormLabel>数据密级</FormLabel>
									<FormControl>
										<Select onValueChange={field.onChange} value={field.value}>
											<SelectTrigger className="w-full justify-between">
												<SelectValue placeholder="请选择数据密级" />
											</SelectTrigger>
											<SelectContent>
												{DATA_LEVEL_OPTIONS.map((option) => (
													<SelectItem key={option.value} value={option.value}>
														{option.label}
													</SelectItem>
												))}
											</SelectContent>
										</Select>
									</FormControl>
									<FormMessage />
								</FormItem>
							)}
						/>
						<div className="grid gap-4 sm:grid-cols-2">
							<FormField
								control={form.control}
								name="contact"
								render={({ field }) => (
									<FormItem>
										<FormLabel>联系人</FormLabel>
										<FormControl>
											<Input placeholder="请输入联系人" {...field} />
										</FormControl>
										<FormMessage />
									</FormItem>
								)}
							/>
							<FormField
								control={form.control}
								name="phone"
								render={({ field }) => (
									<FormItem>
										<FormLabel>联系电话</FormLabel>
										<FormControl>
											<Input placeholder="请输入联系电话" {...field} />
										</FormControl>
										<FormMessage />
									</FormItem>
								)}
							/>
						</div>
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
	loading?: boolean;
	onCancel: () => void;
	onConfirm: () => void;
}

function ConfirmDeleteDialog({ open, name, loading, onCancel, onConfirm }: ConfirmDeleteDialogProps) {
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
					<DialogDescription>删除后该部门及其子部门将无法恢复，请谨慎操作。</DialogDescription>
				</DialogHeader>
				<div className="space-y-2 py-2 text-sm">
					<p>
						即将删除：<span className="font-medium text-foreground">{name ?? "未命名部门"}</span>
					</p>
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
