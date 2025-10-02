import { useMemo, useState } from "react";
import clsx from "clsx";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Checkbox } from "@/ui/checkbox";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Switch } from "@/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { Textarea } from "@/ui/textarea";
import SensitiveNotice from "@/components/security/SensitiveNotice";

const MASKING_OPERATORS = [
	{ value: "PASS", label: "展示" },
	{ value: "MASK", label: "掩码 (****)" },
	{ value: "HASH", label: "Hash" },
	{ value: "NULL", label: "置空" },
	{ value: "REGEX", label: "正则替换" },
	{ value: "CUSTOM", label: "自定义函数" },
] as const;

const CLASSIFICATION_LEVELS = [
	{ value: "PUBLIC", label: "公开" },
	{ value: "INTERNAL", label: "内部" },
	{ value: "SECRET", label: "秘密" },
	{ value: "TOP_SECRET", label: "机密" },
] as const;

type MaskingOperator = (typeof MASKING_OPERATORS)[number]["value"];

type DatasetField = {
	id: string;
	name: string;
	type: string;
	classification: string;
	masking: MaskingOperator;
	regex?: string;
	customFn?: string;
};

type DatasetDefinition = {
	id: string;
	name: string;
	description?: string;
	fields: DatasetField[];
};

const DATASETS: DatasetDefinition[] = [
	{
		id: "dwd_customer_profile",
		name: "dwd_customer_profile",
		description: "客户主档宽表，含个人信息与等级标签",
		fields: [
			{ id: "customer_id", name: "customer_id", type: "STRING", classification: "内部", masking: "PASS" },
			{ id: "customer_name", name: "customer_name", type: "STRING", classification: "秘密", masking: "MASK" },
			{
				id: "credential_no",
				name: "credential_no",
				type: "STRING",
				classification: "机密",
				masking: "REGEX",
				regex: "^(\\w{3})\\w+(\\w{4})$",
			},
			{
				id: "mobile",
				name: "mobile",
				type: "STRING",
				classification: "秘密",
				masking: "REGEX",
				regex: "^(\\d{3})\\d+(\\d{2})$",
			},
			{ id: "birthday", name: "birthday", type: "DATE", classification: "内部", masking: "PASS" },
			{
				id: "total_asset",
				name: "total_asset",
				type: "DECIMAL",
				classification: "机密",
				masking: "CUSTOM",
				customFn: "mask_asset_range",
			},
		],
	},
	{
		id: "dws_sales_order",
		name: "dws_sales_order",
		description: "订单明细，包含渠道与目标对照",
		fields: [
			{ id: "order_id", name: "order_id", type: "STRING", classification: "内部", masking: "PASS" },
			{ id: "channel", name: "channel", type: "STRING", classification: "公开", masking: "PASS" },
			{ id: "buyer_id", name: "buyer_id", type: "STRING", classification: "机密", masking: "HASH" },
			{ id: "gmv", name: "gmv", type: "DECIMAL", classification: "内部", masking: "PASS" },
			{ id: "coupon_amount", name: "coupon_amount", type: "DECIMAL", classification: "秘密", masking: "NULL" },
		],
	},
];

type RowPolicy = {
	id: string;
	scope: "角色" | "组织" | "用户";
	target: string;
	dsl: string;
	active: boolean;
};

const INITIAL_ROW_POLICIES: RowPolicy[] = [
	{
		id: "role-analyst",
		scope: "角色",
		target: "DATA_ANALYST",
		dsl: "region != 'RISK_CENTER'",
		active: true,
	},
	{
		id: "org-risk",
		scope: "组织",
		target: "风险管理部",
		dsl: "risk_level in ('HIGH','CRITICAL')",
		active: true,
	},
	{
		id: "user-ceo",
		scope: "用户",
		target: "ceo",
		dsl: "1 = 1",
		active: true,
	},
];

type ScopeConfig = {
	priority: number;
	classifications: string[];
	roles: string[];
	organizations: string[];
};

const INITIAL_SCOPE: ScopeConfig = {
	priority: 10,
	classifications: ["秘密", "机密"],
	roles: ["DATA_STEWARD"],
	organizations: ["数据资产中心"],
};

type SimulationInput = {
	user: string;
	role: string;
	classification: string;
	datasetId: string;
};

const SIM_SAMPLE_ROWS = [
	{
		customer_id: "CUS-2001",
		customer_name: "王**",
		credential_no: "110*********4321",
		mobile: "138****0021",
		birthday: "1989-03-21",
		total_asset: "***",
	},
	{
		customer_id: "CUS-2002",
		customer_name: "李**",
		credential_no: "310*********8765",
		mobile: "139****3344",
		birthday: "1992-11-30",
		total_asset: "***",
	},
];

const DIFF_DATA = {
	before: {
		columnPolicies: [
			{ field: "customer_name", strategy: "掩码" },
			{ field: "credential_no", strategy: "Hash" },
		],
		rowPolicies: ["DATA_ANALYST: region != 'CORE'"],
	},
	after: {
		columnPolicies: [
			{ field: "customer_name", strategy: "掩码" },
			{ field: "credential_no", strategy: "正则替换" },
			{ field: "total_asset", strategy: "自定义函数" },
		],
		rowPolicies: ["数据资产中心: risk_level in ('HIGH','CRITICAL')"],
	},
};

export default function MaskingPoliciesPage() {
	const [datasetId, setDatasetId] = useState(DATASETS[0]?.id ?? "");
	const [searchField, setSearchField] = useState("");
	const [columnPolicies, setColumnPolicies] = useState(() => new Map<string, DatasetField>());
	const [rowPolicies, setRowPolicies] = useState<RowPolicy[]>(INITIAL_ROW_POLICIES);
	const [scopeConfig, setScopeConfig] = useState<ScopeConfig>(INITIAL_SCOPE);
	const [simulation, setSimulation] = useState<SimulationInput>({
		user: "analyst.chen",
		role: "DATA_ANALYST",
		classification: "内部",
		datasetId: DATASETS[0]?.id ?? "",
	});

	const activeDataset = useMemo(() => DATASETS.find((dataset) => dataset.id === datasetId), [datasetId]);

	const fieldRows = useMemo(() => {
		const fields = activeDataset?.fields ?? [];
		if (!searchField.trim()) return fields;
		const lower = searchField.toLowerCase();
		return fields.filter((field) => field.name.toLowerCase().includes(lower));
	}, [activeDataset, searchField]);

	const columnPolicyState = useMemo(() => {
		const map = new Map<string, DatasetField>();
		for (const field of activeDataset?.fields ?? []) {
			const override = columnPolicies.get(`${datasetId}:${field.id}`);
			map.set(field.id, override ?? field);
		}
		return map;
	}, [activeDataset, columnPolicies, datasetId]);

	const updateColumnPolicy = (fieldId: string, updates: Partial<DatasetField>) => {
		setColumnPolicies((prev) => {
			const next = new Map(prev);
			const base = (activeDataset?.fields ?? []).find((item) => item.id === fieldId);
			if (!base) return prev;
			next.set(`${datasetId}:${fieldId}`, { ...base, ...next.get(`${datasetId}:${fieldId}`), ...updates });
			return next;
		});
	};

	const toggleRowPolicy = (policyId: string) => {
		setRowPolicies((prev) =>
			prev.map((policy) => (policy.id === policyId ? { ...policy, active: !policy.active } : policy)),
		);
	};

	const addRowPolicy = () => {
		const id = `policy-${Date.now()}`;
		setRowPolicies((prev) => [{ id, scope: "角色", target: "DATA_ANALYST", dsl: "", active: true }, ...prev]);
	};

	const updateRowPolicy = (policyId: string, updates: Partial<RowPolicy>) => {
		setRowPolicies((prev) => prev.map((policy) => (policy.id === policyId ? { ...policy, ...updates } : policy)));
	};

	const updateScopeConfig = (updates: Partial<ScopeConfig>) => {
		setScopeConfig((prev) => ({ ...prev, ...updates }));
	};

	return (
		<div className="space-y-4">
			<SensitiveNotice />
			<div className="flex flex-wrap items-center gap-2 rounded-md border border-purple-200 bg-purple-50 px-4 py-3 text-sm text-purple-700">
				<Icon icon="solar:shield-bold" size={18} />
				治理策略落地列级/行级访问控制，可按密级、角色、组织叠加覆盖并提供模拟验证。
			</div>

			<div className="grid gap-4 2xl:grid-cols-[400px,1fr]">
				<Card className="h-[calc(100vh-260px)]">
					<CardHeader className="space-y-3">
						<CardTitle className="text-base">数据集与字段</CardTitle>
						<Select value={datasetId} onValueChange={setDatasetId}>
							<SelectTrigger>
								<SelectValue placeholder="选择数据集" />
							</SelectTrigger>
							<SelectContent>
								{DATASETS.map((dataset) => (
									<SelectItem key={dataset.id} value={dataset.id}>
										{dataset.name}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						<Input
							value={searchField}
							onChange={(event) => setSearchField(event.target.value)}
							placeholder="按字段名筛选"
						/>
					</CardHeader>
					<CardContent className="p-0">
						<ScrollArea className="h-[calc(100vh-360px)]">
							<table className="w-full text-sm">
								<thead className="sticky top-0 bg-muted/30 text-left text-xs uppercase text-muted-foreground">
									<tr>
										<th className="px-4 py-2">字段名</th>
										<th className="px-4 py-2">类型</th>
										<th className="px-4 py-2">密级</th>
										<th className="px-4 py-2">当前策略</th>
									</tr>
								</thead>
								<tbody>
									{fieldRows.map((field) => {
										const policy = columnPolicyState.get(field.id) ?? field;
										return (
											<tr key={field.id} className="border-b border-border/40 last:border-none">
												<td className="px-4 py-3">
													<div className="font-medium">{field.name}</div>
													<p className="text-xs text-muted-foreground">
														默认：{field.masking} · 当前：{policy.masking}
													</p>
												</td>
												<td className="px-4 py-3 text-xs text-muted-foreground">{field.type}</td>
												<td className="px-4 py-3 text-xs">
													<Badge variant="outline">{field.classification}</Badge>
												</td>
												<td className="px-4 py-3">
													<Select
														value={policy.masking}
														onValueChange={(value) =>
															updateColumnPolicy(field.id, { masking: value as MaskingOperator })
														}
													>
														<SelectTrigger>
															<SelectValue />
														</SelectTrigger>
														<SelectContent>
															{MASKING_OPERATORS.map((option) => (
																<SelectItem key={option.value} value={option.value}>
																	{option.label}
																</SelectItem>
															))}
														</SelectContent>
													</Select>
													{policy.masking === "REGEX" ? (
														<Input
															className="mt-2"
															placeholder="正则表达式"
															value={policy.regex ?? ""}
															onChange={(event) => updateColumnPolicy(field.id, { regex: event.target.value })}
														/>
													) : null}
													{policy.masking === "CUSTOM" ? (
														<Input
															className="mt-2"
															placeholder="函数名称"
															value={policy.customFn ?? ""}
															onChange={(event) => updateColumnPolicy(field.id, { customFn: event.target.value })}
														/>
													) : null}
												</td>
											</tr>
										);
									})}
								</tbody>
							</table>
						</ScrollArea>
					</CardContent>
				</Card>

				<div className="space-y-4">
					<Card>
						<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
							<div>
								<CardTitle className="text-base">行级策略</CardTitle>
								<p className="text-xs text-muted-foreground">按角色/组织/用户构造过滤条件</p>
							</div>
							<Button size="sm" variant="outline" onClick={addRowPolicy}>
								<Icon icon="mdi:plus" className="mr-1" size={15} /> 新增策略
							</Button>
						</CardHeader>
						<CardContent className="space-y-3">
							{rowPolicies.map((policy) => (
								<Card key={policy.id} className={clsx("border-dashed", { "opacity-60": !policy.active })}>
									<CardContent className="space-y-3 p-4">
										<div className="flex flex-wrap items-center gap-3">
											<Select
												value={policy.scope}
												onValueChange={(value) => updateRowPolicy(policy.id, { scope: value as RowPolicy["scope"] })}
											>
												<SelectTrigger className="w-[120px]">
													<SelectValue />
												</SelectTrigger>
												<SelectContent>
													<SelectItem value="角色">角色</SelectItem>
													<SelectItem value="组织">组织</SelectItem>
													<SelectItem value="用户">用户</SelectItem>
												</SelectContent>
											</Select>
											<Input
												className="w-[180px]"
												value={policy.target}
												onChange={(event) => updateRowPolicy(policy.id, { target: event.target.value })}
												placeholder="例如 DATA_ANALYST"
											/>
											<label className="flex items-center gap-1 text-xs text-muted-foreground">
												<Switch checked={policy.active} onCheckedChange={() => toggleRowPolicy(policy.id)} />
												启用
											</label>
										</div>
										<Textarea
											className="font-mono"
											rows={3}
											value={policy.dsl}
											onChange={(event) => updateRowPolicy(policy.id, { dsl: event.target.value })}
											placeholder="region != 'RISK_CENTER'"
										/>
									</CardContent>
								</Card>
							))}
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<CardTitle className="text-base">适用范围与优先级</CardTitle>
						</CardHeader>
						<CardContent className="space-y-3">
							<div className="flex items-center gap-3">
								<Label className="text-xs text-muted-foreground">优先级 (数值越小优先)</Label>
								<Input
									type="number"
									className="w-[120px]"
									value={scopeConfig.priority}
									onChange={(event) => updateScopeConfig({ priority: Number(event.target.value) })}
								/>
							</div>
							<div className="space-y-2">
								<Label className="text-xs text-muted-foreground">密级</Label>
								<Select value="multi" onValueChange={() => undefined} open={false}>
									<SelectTrigger className="justify-start">
										<SelectValue placeholder="选择密级" />
									</SelectTrigger>
								</Select>
								<div className="flex flex-wrap gap-2">
									{CLASSIFICATION_LEVELS.map((level) => (
										<label key={level.value} className="flex items-center gap-1 rounded-md border px-2 py-1 text-xs">
											<Checkbox
												checked={scopeConfig.classifications.includes(level.label)}
												onCheckedChange={(checked) => {
													updateScopeConfig({
														classifications: checked
															? [...scopeConfig.classifications, level.label]
															: scopeConfig.classifications.filter((item) => item !== level.label),
													});
												}}
											/>
											<span>{level.label}</span>
										</label>
									))}
								</div>
							</div>
							<div className="space-y-2">
								<Label className="text-xs text-muted-foreground">角色</Label>
								<Textarea
									rows={2}
									placeholder="以逗号分隔"
									value={scopeConfig.roles.join(",")}
									onChange={(event) =>
										updateScopeConfig({
											roles: event.target.value
												.split(",")
												.map((item) => item.trim())
												.filter(Boolean),
										})
									}
								/>
							</div>
							<div className="space-y-2">
								<Label className="text-xs text-muted-foreground">组织</Label>
								<Textarea
									rows={2}
									placeholder="以逗号分隔"
									value={scopeConfig.organizations.join(",")}
									onChange={(event) =>
										updateScopeConfig({
											organizations: event.target.value
												.split(",")
												.map((item) => item.trim())
												.filter(Boolean),
										})
									}
								/>
							</div>
							<p className="text-xs text-muted-foreground">
								策略叠加遵循：优先级 &lt; 密级 &lt; 角色/组织，覆盖冲突时按照优先级较小者生效。
							</p>
						</CardContent>
					</Card>

					<Tabs defaultValue="simulate">
						<TabsList className="grid w-full grid-cols-2">
							<TabsTrigger value="simulate">策略模拟</TabsTrigger>
							<TabsTrigger value="diff">变更对比</TabsTrigger>
						</TabsList>
						<TabsContent value="simulate" className="space-y-3 pt-4">
							<div className="grid gap-3 md:grid-cols-4">
								<Select
									value={simulation.user}
									onValueChange={(value) => setSimulation((prev) => ({ ...prev, user: value }))}
								>
									<SelectTrigger>
										<SelectValue placeholder="用户" />
									</SelectTrigger>
									<SelectContent>
										<SelectItem value="analyst.chen">analyst.chen</SelectItem>
										<SelectItem value="risk.li">risk.li</SelectItem>
										<SelectItem value="ceo">ceo</SelectItem>
									</SelectContent>
								</Select>
								<Select
									value={simulation.role}
									onValueChange={(value) => setSimulation((prev) => ({ ...prev, role: value }))}
								>
									<SelectTrigger>
										<SelectValue placeholder="角色" />
									</SelectTrigger>
									<SelectContent>
										<SelectItem value="DATA_ANALYST">DATA_ANALYST</SelectItem>
										<SelectItem value="DATA_STEWARD">DATA_STEWARD</SelectItem>
										<SelectItem value="RISK_MANAGER">RISK_MANAGER</SelectItem>
									</SelectContent>
								</Select>
								<Select
									value={simulation.classification}
									onValueChange={(value) => setSimulation((prev) => ({ ...prev, classification: value }))}
								>
									<SelectTrigger>
										<SelectValue placeholder="密级" />
									</SelectTrigger>
									<SelectContent>
										{CLASSIFICATION_LEVELS.map((level) => (
											<SelectItem key={level.value} value={level.label}>
												{level.label}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
								<Select
									value={simulation.datasetId}
									onValueChange={(value) => setSimulation((prev) => ({ ...prev, datasetId: value }))}
								>
									<SelectTrigger>
										<SelectValue placeholder="数据集" />
									</SelectTrigger>
									<SelectContent>
										{DATASETS.map((dataset) => (
											<SelectItem key={dataset.id} value={dataset.id}>
												{dataset.name}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</div>
							<Card className="overflow-x-auto">
								<CardHeader>
									<CardTitle className="text-sm">模拟结果</CardTitle>
								</CardHeader>
								<CardContent>
									<table className="w-full min-w-[640px] table-fixed text-sm">
										<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
											<tr>
												{Object.keys(SIM_SAMPLE_ROWS[0]).map((column) => (
													<th key={column} className="px-3 py-2">
														<div className="flex items-center gap-1">
															{column}
															<Icon icon="solar:shield-check-bold" className="text-purple-500" size={14} />
														</div>
													</th>
												))}
											</tr>
										</thead>
										<tbody>
											{SIM_SAMPLE_ROWS.map((row, index) => (
												<tr
													key={index}
													className={clsx("border-b border-border/40", { "bg-muted/20": index % 2 === 0 })}
												>
													{Object.entries(row).map(([column, value]) => (
														<td key={column} className="px-3 py-2">
															<span className={clsx({ "font-semibold text-purple-600": String(value).includes("*") })}>
																{value}
															</span>
														</td>
													))}
												</tr>
											))}
										</tbody>
									</table>
								</CardContent>
							</Card>
						</TabsContent>

						<TabsContent value="diff" className="space-y-4 pt-4">
							<div className="grid gap-3 md:grid-cols-2">
								<Card>
									<CardHeader>
										<CardTitle className="text-sm">旧策略</CardTitle>
									</CardHeader>
									<CardContent className="space-y-3 text-sm text-muted-foreground">
										<div>
											<p className="font-semibold">列级</p>
											<ul className="ml-3 list-disc">
												{DIFF_DATA.before.columnPolicies.map((policy) => (
													<li key={`${policy.field}-before`}>
														{policy.field} → {policy.strategy}
													</li>
												))}
											</ul>
										</div>
										<div>
											<p className="font-semibold">行级</p>
											<ul className="ml-3 list-disc">
												{DIFF_DATA.before.rowPolicies.map((policy) => (
													<li key={`${policy}-before`}>{policy}</li>
												))}
											</ul>
										</div>
									</CardContent>
								</Card>
								<Card>
									<CardHeader>
										<CardTitle className="text-sm">新策略</CardTitle>
									</CardHeader>
									<CardContent className="space-y-3 text-sm text-muted-foreground">
										<div>
											<p className="font-semibold">列级</p>
											<ul className="ml-3 list-disc">
												{DIFF_DATA.after.columnPolicies.map((policy) => (
													<li key={`${policy.field}-after`}>
														{policy.field} → {policy.strategy}
													</li>
												))}
											</ul>
										</div>
										<div>
											<p className="font-semibold">行级</p>
											<ul className="ml-3 list-disc">
												{DIFF_DATA.after.rowPolicies.map((policy) => (
													<li key={`${policy}-after`}>{policy}</li>
												))}
											</ul>
										</div>
									</CardContent>
								</Card>
							</div>
						</TabsContent>
					</Tabs>
				</div>
			</div>
		</div>
	);
}
