import { useState } from "react";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/ui/card";
import { Button } from "@/ui/button";
import { Input } from "@/ui/input";
import { Switch } from "@/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { Badge } from "@/ui/badge";
import { Label } from "@/ui/label";
import { toast } from "sonner";
import { Settings, ToggleLeft, Shield, Database, Plug, RefreshCw, Eye, EyeOff, Lock } from "lucide-react";

interface OpsConfigItem {
	id: number;
	key: string;
	value: string;
	description?: string;
	category: string;
	sensitive: boolean;
	dataType: "STRING" | "BOOLEAN" | "INTEGER" | "JSON";
	editable: boolean;
	sortOrder: number;
	displayName?: string;
	lastModified?: string;
	lastModifiedBy?: string;
}

interface OpsConfigCategory {
	key: string;
	label: string;
	items: OpsConfigItem[];
}

const CATEGORY_ICONS: Record<string, React.ReactNode> = {
	FEATURE_TOGGLE: <ToggleLeft className="h-4 w-4" />,
	SECURITY: <Shield className="h-4 w-4" />,
	DATABASE: <Database className="h-4 w-4" />,
	INTEGRATION: <Plug className="h-4 w-4" />,
	SYSTEM: <Settings className="h-4 w-4" />,
};

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
		onError: () => {
			toast.error("操作失败，请稍后再试");
		},
	});

	const handleToggle = (item: OpsConfigItem) => {
		if (!item.editable) {
			toast.error("该配置项不可编辑");
			return;
		}
		const currentValue = item.value === "true";
		toggleMutation.mutate({ key: item.key, enabled: !currentValue });
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
				<Button variant="outline" size="sm" onClick={() => refetch()}>
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
							{cat.items.map((item) => (
								<ConfigItemCard
									key={item.id}
									item={item}
									onToggle={handleToggle}
									formatValue={formatValue}
								/>
							))}
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
	formatValue,
}: {
	item: OpsConfigItem;
	onToggle: (item: OpsConfigItem) => void;
	formatValue: (item: OpsConfigItem) => string;
}) {
	const isBoolean = item.dataType === "BOOLEAN";
	const boolValue = item.value === "true";

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

						{/* Value Display */}
						<div className="flex items-center gap-4">
							<div className="flex items-center gap-2">
								<Label className="text-xs text-muted-foreground">配置键:</Label>
								<code className="text-xs bg-muted px-2 py-0.5 rounded font-mono">
									{item.key}
								</code>
							</div>

							{!isBoolean && (
								<div className="flex items-center gap-2">
									<Label className="text-xs text-muted-foreground">当前值:</Label>
									<span className={`text-sm ${item.sensitive ? "font-mono text-muted-foreground" : ""}`}>
										{formatValue(item)}
									</span>
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
								disabled={!item.editable}
							/>
						</div>
					)}
				</div>
			</CardContent>
		</Card>
	);
}
