import { useEffect, useMemo } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { Input } from "@/ui/input";
import { Textarea } from "@/ui/textarea";
import { Label } from "@/ui/label";
import { Button } from "@/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { adminApi } from "@/admin/api/adminApi";
import type { ChangeRequest } from "@/admin/types";
import { useAdminLocale } from "@/admin/lib/locale";
import { PERSON_SECURITY_LEVELS } from "@/constants/governance";

const ACTIONS: Record<string, string[]> = {
	// 用户删除功能禁用：移除 DELETE 选项，仅保留停用/启用类与绑定操作
	user: ["CREATE", "UPDATE", "BIND_ROLE", "UNBIND_ROLE"],
	role: ["CREATE", "UPDATE", "DELETE"],
	org: ["CREATE", "UPDATE", "DELETE"],
	config: ["CONFIG_SET"],
	menu: ["CREATE", "UPDATE", "DISABLE"],
};

const TABS = [
	{ key: "user", label: "用户变更" },
	{ key: "role", label: "角色变更" },
	{ key: "org", label: "组织机构" },
	{ key: "config", label: "系统配置" },
	{ key: "menu", label: "菜单管理" },
];

type FormValues = {
	action: string;
	resourceId?: string;
	username?: string;
	email?: string;
	roles?: string;
	name?: string;
	code?: string;
	parentId?: string;
	key?: string;
	value?: string;
	description?: string;
	component?: string;
	sortOrder?: string;
	metadata?: string;
	securityLevel?: string;
	__tab?: string;
};

interface Props {
	onCreated?: (request: ChangeRequest) => void;
	initialTab?: string;
}

export function ChangeRequestForm({ onCreated, initialTab = "user" }: Props) {
	const form = useForm<FormValues>({
		defaultValues: {
			action: ACTIONS[initialTab]?.[0] ?? "CREATE",
		},
	});
	const { translateAction, translateTab } = useAdminLocale();

	useEffect(() => {
		form.register("__tab");
		form.register("securityLevel");
	}, [form]);
	const { handleSubmit, register, setValue, reset, watch } = form;
	const currentTab = watch("__tab") || initialTab;

	useEffect(() => {
		setValue("__tab", initialTab, { shouldDirty: false, shouldTouch: false });
		setValue("action", ACTIONS[initialTab]?.[0] ?? "CREATE", { shouldDirty: false, shouldTouch: false });
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [initialTab]);

	const fields = useMemo(
		() => ({
			action: watch("action"),
			resourceId: watch("resourceId"),
			username: watch("username"),
			email: watch("email"),
			roles: watch("roles"),
			name: watch("name"),
			code: watch("code"),
			parentId: watch("parentId"),
			key: watch("key"),
			value: watch("value"),
			description: watch("description"),
			component: watch("component"),
			sortOrder: watch("sortOrder"),
			metadata: watch("metadata"),
			securityLevel: watch("securityLevel"),
		}),
		[watch],
	);

	const onSubmit = handleSubmit(async (values) => {
		const tab = currentTab || initialTab;
		const payload = buildPayload(tab, values);
		if (!payload) {
			toast.error("请完整填写表单");
			return;
		}
		try {
			const created = await adminApi.createChangeRequest(payload);
			await adminApi.submitChangeRequest(created.id);
			toast.success("变更已提交审批");
			onCreated?.(created);
			reset({ action: ACTIONS[tab]?.[0] ?? "CREATE", __tab: tab });
		} catch (error) {
			toast.error("提交失败，请稍后重试");
		}
	});

	return (
		<Tabs
			defaultValue={initialTab}
			onValueChange={(value) => {
				setValue("__tab", value, { shouldDirty: false, shouldTouch: false });
				setValue("action", ACTIONS[value]?.[0] ?? "CREATE", { shouldDirty: false, shouldTouch: false });
				reset({ action: ACTIONS[value]?.[0] ?? "CREATE", __tab: value });
			}}
			value={currentTab}
		>
			<TabsList className="flex flex-wrap justify-start gap-2">
				{TABS.map((tab) => (
					<TabsTrigger key={tab.key} value={tab.key} className="capitalize">
						{translateTab(tab.key, tab.label)}
					</TabsTrigger>
				))}
			</TabsList>
			<form className="mt-4 space-y-6" onSubmit={onSubmit}>
				<div className="grid gap-6 md:grid-cols-2">
					<div className="space-y-2">
						<Label htmlFor="action">操作类型</Label>
						<select
							id="action"
							className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm"
							value={fields.action}
							{...register("action")}
						>
							{(ACTIONS[currentTab] ?? []).map((action) => (
								<option key={action} value={action}>
									{translateAction(action, action)}
								</option>
							))}
						</select>
					</div>
					<div className="space-y-2">
						<Label htmlFor="resourceId">资源标识（更新/删除时必填）</Label>
						<Input id="resourceId" placeholder="资源唯一编号" {...register("resourceId")} />
					</div>
				</div>

				{currentTab === "user" && (
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label htmlFor="username">用户名</Label>
							<Input id="username" placeholder="sysadmin" {...register("username")} />
						</div>
						<div className="space-y-2">
							<Label htmlFor="email">邮箱</Label>
							<Input id="email" type="email" placeholder="sysadmin@example.com" {...register("email")} />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label htmlFor="roles">角色列表（逗号分隔）</Label>
							<Input id="roles" placeholder="SYSADMIN" {...register("roles")} />
						</div>
					</div>
				)}

				{currentTab === "role" && (
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label htmlFor="name">角色名称</Label>
							<Input id="name" placeholder="authadmin" {...register("name")} />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label htmlFor="description">说明</Label>
							<Textarea id="description" rows={3} {...register("description")} />
						</div>
					</div>
				)}

				{currentTab === "org" && (
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label htmlFor="name">机构名称</Label>
							<Input id="name" placeholder="大数据部" {...register("name")} />
						</div>
						<div className="space-y-2">
							<Label htmlFor="code">机构编码</Label>
							<Input id="code" placeholder="ORG-001" {...register("code")} />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label htmlFor="parentId">父级编号（可选）</Label>
							<Input id="parentId" placeholder="12" {...register("parentId")} />
						</div>
					</div>
				)}

				{currentTab === "config" && (
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label htmlFor="key">配置键</Label>
							<Input id="key" placeholder="cluster.mode" {...register("key")} />
						</div>
						<div className="space-y-2">
							<Label htmlFor="value">配置值</Label>
							<Input id="value" placeholder="active" {...register("value")} />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label htmlFor="description">说明</Label>
							<Textarea id="description" rows={3} {...register("description")} />
						</div>
					</div>
				)}

				{currentTab === "menu" && (
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label htmlFor="name">菜单名称</Label>
							<Input id="name" placeholder="业务审批" {...register("name")} />
						</div>
						<div className="space-y-2">
							<Label htmlFor="path">访问路径</Label>
							<Input id="path" placeholder="/portal/approval" {...register("code")} />
						</div>
						<div className="space-y-2">
							<Label htmlFor="securityLevel">菜单密级</Label>
							<Select
								value={fields.securityLevel || "GENERAL"}
								onValueChange={(value) => setValue("securityLevel", value, { shouldDirty: true })}
							>
								<SelectTrigger id="securityLevel">
									<SelectValue placeholder="请选择菜单密级" />
								</SelectTrigger>
								<SelectContent>
									{PERSON_SECURITY_LEVELS.map((option) => (
										<SelectItem key={option.value} value={option.value}>
											{option.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label htmlFor="component">前端组件</Label>
							<Input id="component" placeholder="/pages/portal/approval" {...register("component")} />
						</div>
						<div className="space-y-2">
							<Label htmlFor="sortOrder">排序值</Label>
							<Input id="sortOrder" type="number" placeholder="1" {...register("sortOrder")} />
						</div>
						<div className="space-y-2">
							<Label htmlFor="parentId">父级编号</Label>
							<Input id="parentId" placeholder="0" {...register("parentId")} />
						</div>
						<div className="space-y-2">
							<Label htmlFor="metadata">附加元数据(JSON)</Label>
							<Textarea id="metadata" rows={3} {...register("metadata")} />
						</div>
					</div>
				)}

				<Button type="submit" className="w-full md:w-auto">
					提交审批
				</Button>
			</form>
			{TABS.map((tab) => (
				<TabsContent key={tab.key} value={tab.key} />
			))}
		</Tabs>
	);
}

function buildPayload(tab: string, values: FormValues): Partial<ChangeRequest> | null {
	const base = {
		action: values.action,
		resourceType: tab.toUpperCase(),
		resourceId: values.resourceId,
	};

	switch (tab) {
		case "user": {
			const payload = {
				username: values.username,
				email: values.email,
				roles: (values.roles || "")
					.split(",")
					.map((item) => item.trim())
					.filter(Boolean),
			};
			return { ...base, payloadJson: JSON.stringify(payload) };
		}
		case "role": {
			const payload = { name: values.name, description: values.description };
			return { ...base, payloadJson: JSON.stringify(payload) };
		}
		case "org": {
			const payload = { name: values.name, code: values.code, parentId: values.parentId };
			return { ...base, payloadJson: JSON.stringify(payload) };
		}
		case "config": {
			if (!values.key) return null;
			const payload = { key: values.key, value: values.value, description: values.description };
			return { ...base, payloadJson: JSON.stringify(payload) };
		}
		case "menu": {
			const payload = {
				name: values.name,
				path: values.code,
				component: values.component,
				sortOrder: values.sortOrder ? Number(values.sortOrder) : undefined,
				parentId: values.parentId,
				metadata: values.metadata,
				securityLevel: values.securityLevel,
			};
			return { ...base, payloadJson: JSON.stringify(payload) };
		}
		default:
			return null;
	}
}
