import { useState } from "react";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { OpsConfigItem } from "@/admin/types";
import { Card, CardContent } from "@/ui/card";
import { Button } from "antd";
import { Input } from "@/ui/input";
import { Switch } from "@/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { Badge } from "@/ui/badge";
import { Label } from "@/ui/label";
import { toast } from "sonner";
import { Settings, ToggleLeft, Shield, Database, Plug, RefreshCw, Lock, Pencil, Check, X, RotateCcw } from "lucide-react";

interface OpsConfigCategory {
	key: string;
	label: string;
	items: OpsConfigItem[];
}

interface OpsConfigGroup {
	groupKey: string;
	groupLabel: string;
	groupOrder: number;
	items: OpsConfigItem[];
}

const CATEGORY_ICONS: Record<string, React.ReactNode> = {
	FEATURE_TOGGLE: <ToggleLeft className="h-4 w-4" />,
	SECURITY: <Shield className="h-4 w-4" />,
	DATABASE: <Database className="h-4 w-4" />,
	INTEGRATION: <Plug className="h-4 w-4" />,
	SYSTEM: <Settings className="h-4 w-4" />,
};

const SCOPE_LABELS: Record<string, string> = {
	RUNTIME: "运行期",
	RUNTIME_RESTART: "重启生效",
	BOOTSTRAP: "启动期",
};

function extractErrorMessage(error: unknown, fallback: string): string {
	const anyError = error as any;
	return anyError?.response?.data?.message || anyError?.message || fallback;
}

function buildGroups(items: OpsConfigItem[]): OpsConfigGroup[] {
	const groups = new Map<string, OpsConfigGroup>();
	for (const item of items) {
		const groupKey = item.groupKey || "ungrouped";
		const groupLabel = item.groupLabel || "未分组";
		const groupOrder = item.groupOrder ?? 999;
		if (!groups.has(groupKey)) {
			groups.set(groupKey, { groupKey, groupLabel, groupOrder, items: [] });
		}
		groups.get(groupKey)?.items.push(item);
	}
	return Array.from(groups.values())
		.map((group) => ({
			...group,
			items: group.items.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)),
		}))
		.sort((a, b) => a.groupOrder - b.groupOrder);
}

export default function OpsConfigView() {
	const queryClient = useQueryClient();
	const [activeTab, setActiveTab] = useState("FEATURE_TOGGLE");

	const { data, isLoading, refetch } = useQuery({
		queryKey: ["admin", "ops-configs"],
		queryFn: adminApi.getOpsConfigs,
	});

	const categories: OpsConfigCategory[] = data?.categories || [];
	const total = data?.total || 0;

	const toggleMutation = useMutation({
		mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
			adminApi.toggleFeature(key, enabled),
		onSuccess: (_, variables) => {
			toast.success(variables.enabled ? "功能已启用" : "功能已禁用");
			queryClient.invalidateQueries({ queryKey: ["admin", "ops-configs"] });
		},
		onError: (error) => {
			toast.error(extractErrorMessage(error, "操作失败，请稍后再试"));
		},
	});

	const updateMutation = useMutation({
		mutationFn: ({ key, value }: { key: string; value: string; restartRequired?: boolean }) =>
			adminApi.updateOpsConfig(key, value),
		onSuccess: (_, variables) => {
			if (variables?.restartRequired) {
				toast.success("配置已保存，需重启相关服务后生效");
			} else {
				toast.success("配置已保存");
			}
			queryClient.invalidateQueries({ queryKey: ["admin", "ops-configs"] });
		},
		onError: (error) => {
			toast.error(extractErrorMessage(error, "保存失败，请稍后再试"));
		},
	});

	const handleToggle = (item: OpsConfigItem) => {
		if (!item.editable) {
			toast.error("该配置项不可编辑");
			return;
		}
		if (item.scope === "BOOTSTRAP") {
			toast.error("该配置项属于启动期参数，请在 .env/compose 中维护");
			return;
		}
		if (item.restartRequired) {
			const ok = window.confirm("该配置切换后需重启相关服务才能生效，确认继续？");
			if (!ok) return;
		}
		const currentValue = item.value === "true";
		toggleMutation.mutate({ key: item.key, enabled: !currentValue });
	};

	const handleUpdate = (item: OpsConfigItem, newValue: string) => {
		if (!item.editable) {
			toast.error("该配置项不可编辑");
			return;
		}
		if (item.scope === "BOOTSTRAP") {
			toast.error("该配置项属于启动期参数，请在 .env/compose 中维护");
			return;
		}
		if (item.restartRequired) {
			const ok = window.confirm("该配置修改后需重启相关服务才能生效，确认继续？");
			if (!ok) return;
		}
		updateMutation.mutate({ key: item.key, value: newValue, restartRequired: item.restartRequired === true });
	};

	const formatValue = (item: OpsConfigItem): string => {
		if (item.sensitive) {
			return "••••••••";
		}
		if (item.dataType === "BOOLEAN") {
			return item.value === "true" ? "启用" : "禁用";
		}
		if (item.dataType === "JSON") {
			try {
				return JSON.stringify(JSON.parse(item.value), null, 2);
			} catch {
				return item.value || "--";
			}
		}
		return item.value || "--";
	};

	if (isLoading) {
		return (
			<div className="flex items-center justify-center h-64">
				<RefreshCw className="h-6 w-6 animate-spin text-muted-foreground" />
			</div>
		);
	}

	return (
		<div className="space-y-6">
			{/* Header */}
			<div className="flex items-center justify-between">
				<div>
					<h1 className="text-2xl font-semibold">运维配置</h1>
					<p className="text-muted-foreground mt-1">
						共 {total} 项配置，敏感信息已脱敏显示
					</p>
				</div>
				<Button type="default" size="small" onClick={() => refetch()}>
					<RefreshCw className="h-4 w-4 mr-2" />
					刷新
				</Button>
			</div>

			{/* Category Tabs */}
			<Tabs value={activeTab} onValueChange={setActiveTab}>
				<TabsList className="grid w-full grid-cols-5">
					{categories.map((cat) => (
						<TabsTrigger key={cat.key} value={cat.key} className="flex items-center gap-2">
							{CATEGORY_ICONS[cat.key]}
							<span className="hidden sm:inline">{cat.label}</span>
							<Badge variant="secondary" className="ml-1">
								{cat.items.length}
							</Badge>
						</TabsTrigger>
					))}
				</TabsList>

				{categories.map((cat) => (
					<TabsContent key={cat.key} value={cat.key} className="mt-6">
						<div className="grid gap-4">
							{buildGroups(cat.items).map((group) => {
								const restartCount = group.items.filter((item) => item.restartRequired).length;
								const sensitiveCount = group.items.filter((item) => item.sensitive).length;
								return (
									<Card key={group.groupKey}>
										<CardContent className="py-4">
											<div className="flex items-center justify-between mb-3">
												<div className="flex items-center gap-2">
													<h3 className="font-semibold">{group.groupLabel}</h3>
													<Badge variant="secondary">{group.items.length}</Badge>
												</div>
												<div className="flex items-center gap-2">
													{restartCount > 0 && <Badge variant="outline">需重启 {restartCount}</Badge>}
													{sensitiveCount > 0 && <Badge variant="outline">敏感 {sensitiveCount}</Badge>}
												</div>
											</div>
											<div className="grid gap-3">
												{group.items.map((item) => (
													<ConfigItemCard
														key={item.id}
														item={item}
														onToggle={handleToggle}
														onUpdate={handleUpdate}
														formatValue={formatValue}
														isSaving={updateMutation.isPending || toggleMutation.isPending}
													/>
												))}
											</div>
										</CardContent>
									</Card>
								);
							})}
							{cat.items.length === 0 && (
								<Card>
									<CardContent className="py-8 text-center text-muted-foreground">
										暂无 {cat.label} 配置
									</CardContent>
								</Card>
							)}
						</div>
					</TabsContent>
				))}
			</Tabs>
		</div>
	);
}

function ConfigItemCard({
	item,
	onToggle,
	onUpdate,
	formatValue,
	isSaving,
}: {
	item: OpsConfigItem;
	onToggle: (item: OpsConfigItem) => void;
	onUpdate: (item: OpsConfigItem, newValue: string) => void;
	formatValue: (item: OpsConfigItem) => string;
	isSaving: boolean;
}) {
	const isBoolean = item.dataType === "BOOLEAN";
	const isInteger = item.dataType === "INTEGER";
	const isEditable = item.editable && item.scope !== "BOOTSTRAP";
	const boolValue = item.value === "true";

	const [isEditing, setIsEditing] = useState(false);
	const [editValue, setEditValue] = useState(item.value);

	const handleStartEdit = () => {
		if (!isEditable) return;
		setEditValue(item.sensitive ? "******" : item.value);
		setIsEditing(true);
	};

	const handleSave = () => {
		// Validate integer values
		if (isInteger) {
			const numValue = parseInt(editValue, 10);
			if (isNaN(numValue) || numValue < 0) {
				toast.error("请输入有效的正整数");
				return;
			}
		}
		onUpdate(item, editValue);
		setIsEditing(false);
	};

	const handleCancel = () => {
		setEditValue(item.sensitive ? "******" : item.value);
		setIsEditing(false);
	};

	return (
		<Card className={!item.editable ? "opacity-75" : undefined}>
			<CardContent className="py-4">
				<div className="flex items-start justify-between">
					<div className="flex-1 min-w-0">
						{/* Title Row */}
						<div className="flex items-center gap-2 mb-2">
							<h3 className="font-medium text-base truncate">
								{item.displayName || item.key}
							</h3>
							{item.sensitive && (
								<Badge variant="outline" className="text-orange-600 border-orange-300">
									<Lock className="h-3 w-3 mr-1" />
									敏感
								</Badge>
							)}
							{item.scope && (
								<Badge variant={item.scope === "BOOTSTRAP" ? "destructive" : "secondary"}>
									{SCOPE_LABELS[item.scope] ?? item.scope}
								</Badge>
							)}
							{item.restartRequired && (
								<Badge variant="outline" className="text-amber-700 border-amber-300">
									<RotateCcw className="h-3 w-3 mr-1" />
									需重启
								</Badge>
							)}
							{!item.editable && (
								<Badge variant="secondary">只读</Badge>
							)}
						</div>

						{/* Description */}
						{item.description && (
							<p className="text-sm text-muted-foreground mb-3">
								{item.description}
							</p>
						)}
						{item.validationRule && (
							<p className="text-xs text-muted-foreground mb-3">
								校验规则: <code className="font-mono">{item.validationRule}</code>
							</p>
						)}

						{/* Value Display */}
						<div className="flex items-center gap-4">
							<div className="flex items-center gap-2">
								<Label className="text-xs text-muted-foreground">配置键:</Label>
								<code className="text-xs bg-muted px-2 py-0.5 rounded font-mono">
									{item.key}
								</code>
							</div>

							{!isBoolean && !isEditing && (
								<div className="flex items-center gap-2">
									<Label className="text-xs text-muted-foreground">当前值:</Label>
									<span className={`text-sm ${item.sensitive ? "font-mono text-muted-foreground" : ""}`}>
										{formatValue(item)}
									</span>
									{isEditable && (
										<Button
											type="text"
											size="small"
											className="h-6 w-6 p-0"
											onClick={handleStartEdit}
										>
											<Pencil className="h-3 w-3" />
										</Button>
									)}
								</div>
							)}

							{/* Inline Edit Mode */}
							{!isBoolean && isEditing && (
								<div className="flex items-center gap-2">
									<Label className="text-xs text-muted-foreground">新值:</Label>
									<Input
										type={item.sensitive ? "password" : isInteger ? "number" : "text"}
										value={editValue}
										onChange={(e) => setEditValue(e.target.value)}
										className="h-7 w-32 text-sm"
										min={isInteger ? 0 : undefined}
										placeholder={item.sensitive ? "******" : undefined}
										autoFocus
									/>
									<Button
										type="text"
										size="small"
										className="h-6 w-6 p-0 text-green-600 hover:text-green-700"
										onClick={handleSave}
										disabled={isSaving}
									>
										<Check className="h-4 w-4" />
									</Button>
									<Button
										type="text"
										size="small"
										className="h-6 w-6 p-0 text-red-600 hover:text-red-700"
										onClick={handleCancel}
										disabled={isSaving}
									>
										<X className="h-4 w-4" />
									</Button>
								</div>
							)}
						</div>

						{/* Last Modified */}
						{item.lastModifiedBy && (
							<p className="text-xs text-muted-foreground mt-2">
								最后修改: {item.lastModifiedBy}
								{item.lastModified && ` · ${new Date(item.lastModified).toLocaleString("zh-CN")}`}
							</p>
						)}
					</div>

					{/* Toggle Switch for boolean types */}
					{isBoolean && (
						<div className="flex items-center gap-3 ml-4">
							<span className={`text-sm ${boolValue ? "text-green-600" : "text-muted-foreground"}`}>
								{boolValue ? "启用" : "禁用"}
							</span>
							<Switch
								checked={boolValue}
								onCheckedChange={() => onToggle(item)}
								disabled={!isEditable || isSaving}
							/>
						</div>
					)}
				</div>
			</CardContent>
		</Card>
	);
}
