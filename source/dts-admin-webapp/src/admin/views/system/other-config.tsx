import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Card, Form, Input as AntInput, Modal } from "antd";
import { LayoutGrid, RefreshCw, Search, Workflow } from "lucide-react";
import { adminApi } from "@/admin/api/adminApi";
import type { PortalMenuItem, SystemConfigItem } from "@/admin/types";
import { EmptyState } from "@/components/empty-state";
import { useRouter } from "@/routes/hooks";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Input } from "@/ui/input";
import { toast } from "sonner";

function flattenPortalMenus(items: PortalMenuItem[] | undefined): PortalMenuItem[] {
	const result: PortalMenuItem[] = [];
	const visit = (nodes: PortalMenuItem[] | undefined) => {
		(nodes || []).forEach((node) => {
			result.push(node);
			if (Array.isArray(node.children) && node.children.length > 0) {
				visit(node.children);
			}
		});
	};
	visit(items);
	return result;
}

export default function AdminOtherConfigView() {
	const { push } = useRouter();
	const [keyword, setKeyword] = useState("");
	const [draftOpen, setDraftOpen] = useState(false);
	const [draftLoading, setDraftLoading] = useState(false);
	const [editingConfig, setEditingConfig] = useState<SystemConfigItem | null>(null);
	const [form] = Form.useForm<SystemConfigItem>();

	const systemQuery = useQuery({
		queryKey: ["admin", "system-config"],
		queryFn: () => adminApi.getSystemConfig(),
	});

	const portalQuery = useQuery({
		queryKey: ["admin", "portal-menus"],
		queryFn: () => adminApi.getPortalMenus(),
	});

	const opsQuery = useQuery({
		queryKey: ["admin", "ops-configs"],
		queryFn: () => adminApi.getOpsConfigs(),
	});

	const systemConfigs: SystemConfigItem[] = systemQuery.data ?? [];
	const rootMenus: PortalMenuItem[] = portalQuery.data?.menus ?? [];
	const flatMenus = useMemo(() => {
		const allMenus = portalQuery.data?.allMenus;
		return Array.isArray(allMenus) && allMenus.length > 0 ? allMenus : flattenPortalMenus(rootMenus);
	}, [portalQuery.data?.allMenus, rootMenus]);
	const activeMenus = flatMenus.filter((item) => !item.deleted);
	const opsCategories = opsQuery.data?.categories ?? [];
	const restartRequiredCount = opsCategories.reduce(
		(sum, category) => sum + category.items.filter((item) => item.restartRequired).length,
		0,
	);
	const sensitiveCount = opsCategories.reduce(
		(sum, category) => sum + category.items.filter((item) => item.sensitive).length,
		0,
	);

	const filteredConfigs = useMemo(() => {
		const normalized = keyword.trim().toLowerCase();
		if (!normalized) {
			return systemConfigs;
		}
		return systemConfigs.filter((item) => {
			const haystacks = [item.key, item.value, item.description].map((value) => String(value || "").toLowerCase());
			return haystacks.some((value) => value.includes(normalized));
		});
	}, [keyword, systemConfigs]);

	const handleOpenDraft = (config: SystemConfigItem) => {
		setEditingConfig(config);
		form.setFieldsValue({
			key: config.key,
			value: config.value,
			description: config.description,
		});
		setDraftOpen(true);
	};

	const handleSubmitDraft = async () => {
		const values = await form.validateFields();
		setDraftLoading(true);
		try {
			await adminApi.draftSystemConfig({
				key: String(values.key || "").trim(),
				value: values.value,
				description: values.description,
			});
			toast.success("已提交系统配置变更申请");
			setDraftOpen(false);
		} catch (error: any) {
			toast.error(error?.message || "提交失败");
		} finally {
			setDraftLoading(false);
		}
	};

	return (
		<div className="space-y-4">
			<Card
				title={"系统策略与门户治理"}
				extra={
					<>
						<Button
							variant="outline"
							onClick={() => {
								void systemQuery.refetch();
								void portalQuery.refetch();
								void opsQuery.refetch();
							}}
						>
							<RefreshCw className="h-4 w-4" />
							{"刷新总览"}
						</Button>
						<Button variant="outline" onClick={() => push("/admin/portal-menus")}>
							<LayoutGrid className="h-4 w-4" />
							{"菜单管理"}
						</Button>
						<Button onClick={() => push("/admin/ops")}>
							<Workflow className="h-4 w-4" />
							{"运维配置"}
						</Button>
					</>
				}
			>
				<div className="flex w-full flex-col gap-2 md:w-auto md:flex-row md:items-center mb-4">
					<div className="relative min-w-[260px]">
						<Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
						<Input
							value={keyword}
							onChange={(event) => setKeyword(event.target.value)}
							placeholder={"搜索 key、值或说明"}
							className="pl-9"
						/>
					</div>
					<Button variant="outline" onClick={() => push("/admin/infra-settings")}>
						{"系统集成"}
					</Button>
					<Button variant="outline" onClick={() => push("/admin/workflows")}>
						{"工作流配置"}
					</Button>
				</div>
			</Card>

			<div className="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
				<Card
					title={"系统附加配置"}
					extra={<Badge variant="outline" className="rounded-full px-2.5 py-1">{filteredConfigs.length} {"项"}</Badge>}
				>
					{systemQuery.isLoading ? (
						<div className="grid gap-3">
							{Array.from({ length: 3 }).map((_, index) => (
								<div key={index} className="h-28 rounded-[22px] border border-border/70 bg-muted/35" />
							))}
						</div>
					) : filteredConfigs.length === 0 ? (
						<EmptyState
							compact
							title={"没有匹配的系统配置"}
							description={keyword ? "试试更短的关键词，或清空筛选条件。" : "当前还没有系统附加配置项。"}
						/>
					) : (
						<div className="grid gap-3">
							{filteredConfigs.map((item) => (
								<div key={item.key} className="rounded-[24px] border border-border/70 bg-muted/35 p-4">
									<div className="flex flex-wrap items-start justify-between gap-3">
										<div className="space-y-2">
											<div className="flex flex-wrap items-center gap-2">
												<div className="text-sm font-semibold text-foreground">{item.key}</div>
												<Badge variant="info" className="rounded-full px-2.5 py-1">
													System Config
												</Badge>
											</div>
											{item.description ? <div className="text-sm text-muted-foreground">{item.description}</div> : null}
										</div>
										<Button size="sm" variant="outline" onClick={() => handleOpenDraft(item)}>
											{"发起变更"}
										</Button>
									</div>
									<div className="mt-4 rounded-2xl border border-border/70 bg-background/90 px-3 py-3 text-sm font-mono leading-6 text-foreground">
										{item.value || <span className="text-muted-foreground">{"未设置"}</span>}
									</div>
								</div>
							))}
						</div>
					)}
				</Card>

				<Card title={"治理导航"}>
					<div className="grid gap-3">
						{[
							{
								title: "门户菜单",
								path: "/admin/portal-menus",
								badge: `${activeMenus.length} 条入口`,
							},
							{
								title: "运维配置",
								path: "/admin/ops",
								badge: `${opsQuery.data?.total ?? 0} 个参数`,
							},
							{
								title: "集成设置",
								path: "/admin/infra-settings",
								badge: "5 条链路",
							},
							{
								title: "工作流配置",
								path: "/admin/workflows",
								badge: "审批模板",
							},
						].map((item) => (
							<button
								key={item.path}
								type="button"
								onClick={() => push(item.path)}
								className="flex items-center justify-between rounded-[22px] border border-border/70 bg-muted/35 px-4 py-4 text-left transition hover:bg-muted/55"
							>
								<div className="space-y-1">
									<div className="text-sm font-semibold text-foreground">{item.title}</div>
									<Badge variant="outline" className="rounded-full px-2.5 py-1">
										{item.badge}
									</Badge>
								</div>
							</button>
						))}
					</div>
				</Card>
			</div>

			<Card title={"门户与运行策略摘要"}>
				<div className="grid gap-4 xl:grid-cols-2">
					<div className="rounded-[24px] border border-border/70 bg-muted/35 p-4">
						<div className="flex items-center gap-2 text-sm font-semibold text-foreground">
							<LayoutGrid className="h-4 w-4" />
							{"门户菜单摘要"}
						</div>
						<div className="mt-3 grid gap-3 sm:grid-cols-3">
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"顶层分组"}</div>
								<div className="mt-1 text-2xl font-semibold">{rootMenus.length}</div>
							</div>
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"有效入口"}</div>
								<div className="mt-1 text-2xl font-semibold">{activeMenus.length}</div>
							</div>
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"已删除"}</div>
								<div className="mt-1 text-2xl font-semibold">{flatMenus.length - activeMenus.length}</div>
							</div>
						</div>
						<div className="mt-4 flex flex-wrap gap-2">
							{rootMenus.slice(0, 6).map((item) => (
								<Badge key={item.id ?? item.path} variant="info" className="rounded-full px-2.5 py-1">
									{item.displayName || item.name}
								</Badge>
							))}
						</div>
					</div>

					<div className="rounded-[24px] border border-border/70 bg-muted/35 p-4">
						<div className="flex items-center gap-2 text-sm font-semibold text-foreground">
							<Workflow className="h-4 w-4" />
							{"运行策略摘要"}
						</div>
						<div className="mt-3 grid gap-3 sm:grid-cols-3">
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"策略分类"}</div>
								<div className="mt-1 text-2xl font-semibold">{opsCategories.length}</div>
							</div>
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"需重启"}</div>
								<div className="mt-1 text-2xl font-semibold">{restartRequiredCount}</div>
							</div>
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"敏感项"}</div>
								<div className="mt-1 text-2xl font-semibold">{sensitiveCount}</div>
							</div>
						</div>
						<div className="mt-4 flex flex-wrap gap-2">
							{opsCategories.map((category) => (
								<Badge key={category.key} variant="outline" className="rounded-full px-2.5 py-1">
									{category.label} · {category.items.length}
								</Badge>
							))}
						</div>
					</div>
				</div>
			</Card>

			<Modal
				open={draftOpen}
				title={"发起系统配置变更"}
				onCancel={() => setDraftOpen(false)}
				onOk={() => void handleSubmitDraft()}
				confirmLoading={draftLoading}
				okText={"提交申请"}
				cancelText={"取消"}
			>
				<Form form={form} layout="vertical">
					<Form.Item name="key" label={"配置键"} rules={[{ required: true, message: "请输入配置键" }]}>
						<AntInput disabled={Boolean(editingConfig?.key)} />
					</Form.Item>
					<Form.Item name="value" label={"配置值"}>
						<AntInput.TextArea rows={4} placeholder={"请输入新的配置值"} />
					</Form.Item>
					<Form.Item name="description" label={"说明"}>
						<AntInput.TextArea rows={3} placeholder={"补充变更目的与影响范围"} />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
