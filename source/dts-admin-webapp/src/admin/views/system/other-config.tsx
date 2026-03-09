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
			toast.success("\u5df2\u63d0\u4ea4\u7cfb\u7edf\u914d\u7f6e\u53d8\u66f4\u7533\u8bf7");
			setDraftOpen(false);
		} catch (error: any) {
			toast.error(error?.message || "\u63d0\u4ea4\u5931\u8d25");
		} finally {
			setDraftLoading(false);
		}
	};

	return (
		<div className="space-y-4">
			<Card
				title={"\u7cfb\u7edf\u7b56\u7565\u4e0e\u95e8\u6237\u6cbb\u7406"}
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
							{"\u5237\u65b0\u603b\u89c8"}
						</Button>
						<Button variant="outline" onClick={() => push("/admin/portal-menus")}>
							<LayoutGrid className="h-4 w-4" />
							{"\u83dc\u5355\u7ba1\u7406"}
						</Button>
						<Button onClick={() => push("/admin/ops")}>
							<Workflow className="h-4 w-4" />
							{"\u8fd0\u7ef4\u914d\u7f6e"}
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
							placeholder={"\u641c\u7d22 key\u3001\u503c\u6216\u8bf4\u660e"}
							className="pl-9"
						/>
					</div>
					<Button variant="outline" onClick={() => push("/admin/infra-settings")}>
						{"\u7cfb\u7edf\u96c6\u6210"}
					</Button>
					<Button variant="outline" onClick={() => push("/admin/workflows")}>
						{"\u5de5\u4f5c\u6d41\u914d\u7f6e"}
					</Button>
				</div>
			</Card>

			<div className="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
				<Card
					title={"\u7cfb\u7edf\u9644\u52a0\u914d\u7f6e"}
					extra={<Badge variant="outline" className="rounded-full px-2.5 py-1">{filteredConfigs.length} {"\u9879"}</Badge>}
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
							title={"\u6ca1\u6709\u5339\u914d\u7684\u7cfb\u7edf\u914d\u7f6e"}
							description={keyword ? "\u8bd5\u8bd5\u66f4\u77ed\u7684\u5173\u952e\u8bcd\uff0c\u6216\u6e05\u7a7a\u7b5b\u9009\u6761\u4ef6\u3002" : "\u5f53\u524d\u8fd8\u6ca1\u6709\u7cfb\u7edf\u9644\u52a0\u914d\u7f6e\u9879\u3002"}
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
											{"\u53d1\u8d77\u53d8\u66f4"}
										</Button>
									</div>
									<div className="mt-4 rounded-2xl border border-border/70 bg-background/90 px-3 py-3 text-sm font-mono leading-6 text-foreground">
										{item.value || <span className="text-muted-foreground">{"\u672a\u8bbe\u7f6e"}</span>}
									</div>
								</div>
							))}
						</div>
					)}
				</Card>

				<Card title={"\u6cbb\u7406\u5bfc\u822a"}>
					<div className="grid gap-3">
						{[
							{
								title: "\u95e8\u6237\u83dc\u5355",
								path: "/admin/portal-menus",
								badge: `${activeMenus.length} \u6761\u5165\u53e3`,
							},
							{
								title: "\u8fd0\u7ef4\u914d\u7f6e",
								path: "/admin/ops",
								badge: `${opsQuery.data?.total ?? 0} \u4e2a\u53c2\u6570`,
							},
							{
								title: "\u96c6\u6210\u8bbe\u7f6e",
								path: "/admin/infra-settings",
								badge: "5 \u6761\u94fe\u8def",
							},
							{
								title: "\u5de5\u4f5c\u6d41\u914d\u7f6e",
								path: "/admin/workflows",
								badge: "\u5ba1\u6279\u6a21\u677f",
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

			<Card title={"\u95e8\u6237\u4e0e\u8fd0\u884c\u7b56\u7565\u6458\u8981"}>
				<div className="grid gap-4 xl:grid-cols-2">
					<div className="rounded-[24px] border border-border/70 bg-muted/35 p-4">
						<div className="flex items-center gap-2 text-sm font-semibold text-foreground">
							<LayoutGrid className="h-4 w-4" />
							{"\u95e8\u6237\u83dc\u5355\u6458\u8981"}
						</div>
						<div className="mt-3 grid gap-3 sm:grid-cols-3">
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"\u9876\u5c42\u5206\u7ec4"}</div>
								<div className="mt-1 text-2xl font-semibold">{rootMenus.length}</div>
							</div>
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"\u6709\u6548\u5165\u53e3"}</div>
								<div className="mt-1 text-2xl font-semibold">{activeMenus.length}</div>
							</div>
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"\u5df2\u5220\u9664"}</div>
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
							{"\u8fd0\u884c\u7b56\u7565\u6458\u8981"}
						</div>
						<div className="mt-3 grid gap-3 sm:grid-cols-3">
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"\u7b56\u7565\u5206\u7c7b"}</div>
								<div className="mt-1 text-2xl font-semibold">{opsCategories.length}</div>
							</div>
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"\u9700\u91cd\u542f"}</div>
								<div className="mt-1 text-2xl font-semibold">{restartRequiredCount}</div>
							</div>
							<div className="rounded-2xl border border-border/70 bg-background/90 px-3 py-3">
								<div className="text-xs text-muted-foreground">{"\u654f\u611f\u9879"}</div>
								<div className="mt-1 text-2xl font-semibold">{sensitiveCount}</div>
							</div>
						</div>
						<div className="mt-4 flex flex-wrap gap-2">
							{opsCategories.map((category) => (
								<Badge key={category.key} variant="outline" className="rounded-full px-2.5 py-1">
									{category.label} \u00b7 {category.items.length}
								</Badge>
							))}
						</div>
					</div>
				</div>
			</Card>

			<Modal
				open={draftOpen}
				title={"\u53d1\u8d77\u7cfb\u7edf\u914d\u7f6e\u53d8\u66f4"}
				onCancel={() => setDraftOpen(false)}
				onOk={() => void handleSubmitDraft()}
				confirmLoading={draftLoading}
				okText={"\u63d0\u4ea4\u7533\u8bf7"}
				cancelText={"\u53d6\u6d88"}
			>
				<Form form={form} layout="vertical">
					<Form.Item name="key" label={"\u914d\u7f6e\u952e"} rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u914d\u7f6e\u952e" }]}>
						<AntInput disabled={Boolean(editingConfig?.key)} />
					</Form.Item>
					<Form.Item name="value" label={"\u914d\u7f6e\u503c"}>
						<AntInput.TextArea rows={4} placeholder={"\u8bf7\u8f93\u5165\u65b0\u7684\u914d\u7f6e\u503c"} />
					</Form.Item>
					<Form.Item name="description" label={"\u8bf4\u660e"}>
						<AntInput.TextArea rows={3} placeholder={"\u8865\u5145\u53d8\u66f4\u76ee\u7684\u4e0e\u5f71\u54cd\u8303\u56f4"} />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
