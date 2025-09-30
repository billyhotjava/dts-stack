import { useMemo, useState } from "react";
import { toast } from "sonner";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Checkbox } from "@/ui/checkbox";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/ui/dialog";
import {
	Drawer,
	DrawerClose,
	DrawerContent,
	DrawerDescription,
	DrawerFooter,
	DrawerHeader,
	DrawerTitle,
} from "@/ui/drawer";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Switch } from "@/ui/switch";
import { Textarea } from "@/ui/textarea";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import {
    createRule as apiCreateRule,
    listRules as apiListRules,
    updateRule as apiUpdateRule,
    toggleRule as apiToggleRule,
    runCompliance as apiRunCompliance,
    triggerQuality,
    latestQuality,
} from "@/api/platformApi";
import { useEffect } from "react";

const SAMPLE_SQL_PREVIEW = `[
  { "score_bucket": "A", "cnt": 123 },
  { "score_bucket": "B", "cnt": 87 }
]`;

type RuleType = "空值" | "唯一性" | "参考值" | "阈值" | "自定义SQL";

type QualityRule = {
	id: string;
	name: string;
	type: RuleType;
	object: string;
	recentResult: "通过" | "告警" | "失败";
	owner: string;
	status: "启用" | "停用";
	referenceCount: number;
	frequency: string;
	config: Record<string, unknown>;
};

const INITIAL_RULES: QualityRule[] = [
	{
		id: "rule-null-001",
		name: "客户手机号不可为空",
		type: "空值",
		object: "dwd_customer_profile.mobile",
		recentResult: "通过",
		owner: "刘敏",
		status: "启用",
		referenceCount: 12,
		frequency: "日批",
		config: { threshold: 0.01, ignore: ["未知", "null"] },
	},
	{
		id: "rule-unique-001",
		name: "订单号唯一",
		type: "唯一性",
		object: "dws_sales_order.order_id",
		recentResult: "告警",
		owner: "陈伟",
		status: "启用",
		referenceCount: 9,
		frequency: "小时级",
		config: { keys: ["order_id", "channel"], conflict: "写入隔离表" },
	},
	{
		id: "rule-ref-001",
		name: "区域编码参考字典",
		type: "参考值",
		object: "dwd_store.region_code",
		recentResult: "通过",
		owner: "张霞",
		status: "启用",
		referenceCount: 7,
		frequency: "分区到达",
		config: { referenceTable: "dim_region", join: "dwd_store.region_code = dim_region.code", unmatched: 0.02 },
	},
	{
		id: "rule-thr-001",
		name: "GMV 与目标偏差",
		type: "阈值",
		object: "dm_sales_metrics.gmv",
		recentResult: "通过",
		owner: "周丽",
		status: "启用",
		referenceCount: 6,
		frequency: "日批",
		config: { metric: "sum(gmv) - sum(target)", bound: "上下界±10%" },
	},
	{
		id: "rule-sql-001",
		name: "信用评分波动检测",
		type: "自定义SQL",
		object: "dm_risk_score",
		recentResult: "失败",
		owner: "李云",
		status: "停用",
		referenceCount: 3,
		frequency: "小时级",
		config: {
			sql: "select score_bucket, count(*) as cnt from dm_risk_score where dt = :date group by 1",
			params: [{ name: ":date", type: "DATE", required: true }],
		},
	},
];

const DATASETS = [
	{
		id: "dwd_customer_profile",
		name: "dwd_customer_profile",
		fields: ["customer_id", "mobile", "email", "created_at"],
	},
	{
		id: "dws_sales_order",
		name: "dws_sales_order",
		fields: ["order_id", "channel", "target", "dt"],
	},
	{
		id: "dm_sales_metrics",
		name: "dm_sales_metrics",
		fields: ["date", "gmv", "target", "channel"],
	},
];

type RuleForm = {
	id?: string;
	name: string;
	type: RuleType;
	datasetId: string;
	fieldIds: string[];
	scope: "全表" | "分区" | "过滤条件";
	filter?: string;
	frequency: "小时级" | "日批" | "分区到达";
	config: Record<string, unknown>;
	owner: string;
	enabled: boolean;
};

const EMPTY_FORM: RuleForm = {
	name: "",
	type: "空值",
	datasetId: DATASETS[0]?.id ?? "",
	fieldIds: [],
	scope: "全表",
	frequency: "日批",
	config: {},
	owner: "",
	enabled: true,
};

export default function QualityRulesPage() {
	const [rules, setRules] = useState(INITIAL_RULES);
  const [search, setSearch] = useState("");
  const [selectedDatasetId, setSelectedDatasetId] = useState<string>(DATASETS[0]?.id ?? "");
  const [latest, setLatest] = useState<any | null>(null);
	const [drawerOpen, setDrawerOpen] = useState(false);
	const [form, setForm] = useState<RuleForm>(EMPTY_FORM);
	const [sqlPreview, setSqlPreview] = useState("");
	const [sqlDialogOpen, setSqlDialogOpen] = useState(false);

	const filteredRules = useMemo(() => {
		if (!search.trim()) return rules;
		const lower = search.trim().toLowerCase();
		return rules.filter((rule) => rule.name.toLowerCase().includes(lower) || rule.object.toLowerCase().includes(lower));
	}, [rules, search]);

	const currentDataset = useMemo(
		() => DATASETS.find((dataset) => dataset.id === form.datasetId) ?? DATASETS[0],
		[form.datasetId],
	);

	useEffect(() => {
		// Load from backend and map to local view model; fallback to demo data when empty
		(async () => {
			try {
				const list = (await apiListRules()) as any[];
				const mapped: QualityRule[] = (list || []).map((it: any) => ({
					id: String(it.id),
					name: it.name || "",
					type: it.type || "自定义SQL",
					object: it.datasetId || "-",
					recentResult: "通过",
					owner: "-",
					status: it.enabled ? "启用" : "停用",
					referenceCount: 0,
					frequency: "日批",
					config: { expression: it.expression },
				}));
				if (mapped.length) setRules(mapped);
			} catch (e) {
				console.warn("Load rules failed", e);
			}
		})();
	}, []);

	const openCreate = () => {
		setForm(EMPTY_FORM);
		setDrawerOpen(true);
	};

	const openEdit = (rule: QualityRule) => {
		setForm({
			id: rule.id,
			name: rule.name,
			type: rule.type,
			datasetId: rule.object.split(".")[0] ?? DATASETS[0]?.id ?? "",
			fieldIds: [rule.object],
			scope: "全表",
			frequency: (rule.frequency as RuleForm["frequency"]) || "日批",
			config: rule.config,
			owner: rule.owner,
			enabled: rule.status === "启用",
		});
		setDrawerOpen(true);
	};

	const toggleField = (field: string) => {
		setForm((prev) => {
			const qualified = `${prev.datasetId}.${field}`;
			return {
				...prev,
				fieldIds: prev.fieldIds.includes(qualified)
					? prev.fieldIds.filter((item) => item !== qualified)
					: [...prev.fieldIds, qualified],
			};
		});
	};

	const updateConfig = (updates: Record<string, unknown>) => {
		setForm((prev) => ({ ...prev, config: { ...prev.config, ...updates } }));
	};

	const handleSubmit = async () => {
		const expression = (form.config as any)?.sql || form.filter || "";
		const enabled = form.enabled;
		const name = form.name;
		const type = form.type;
		try {
			if (form.id) {
				await apiUpdateRule(form.id, { name, type, expression, enabled });
			} else {
				await apiCreateRule({ name, type, expression, enabled });
			}
			toast.success("已保存质量规则");
			const list = (await apiListRules()) as any[];
			const mapped: QualityRule[] = (list || []).map((it: any) => ({
				id: String(it.id),
				name: it.name || "",
				type: it.type || "自定义SQL",
				object: it.datasetId || "-",
				recentResult: "通过",
				owner: "-",
				status: it.enabled ? "启用" : "停用",
				referenceCount: 0,
				frequency: "日批",
				config: { expression: it.expression },
			}));
			setRules(mapped.length ? mapped : INITIAL_RULES);
			setDrawerOpen(false);
		} catch (e) {
			console.error(e);
			toast.error("保存失败");
		}
	};

	return (
		<div className="space-y-4">
			<div className="flex flex-wrap items-center gap-2 rounded-md border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-700">
				<Icon icon="solar:checklist-minimalistic-bold" size={18} />
				质量规则用于度量数据健康，启停和更新均会被审计留痕。
			</div>

			<Card>
				<CardHeader className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
					<div>
						<CardTitle>质量规则</CardTitle>
						<p className="text-sm text-muted-foreground">支持按对象与类型管理规则，抽屉式新建/编辑</p>
					</div>
          <div className="flex flex-wrap gap-2">
            <Input
              className="w-[220px]"
              placeholder="搜索规则 / 对象"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            <Button onClick={openCreate}>
              <Icon icon="mdi:plus" className="mr-1" size={16} /> 新建规则
            </Button>
            <Button
              variant="outline"
              onClick={async () => {
                await apiRunCompliance();
                toast.success("已触发合规检查");
              }}
            >
              执行合规模拟
            </Button>
            <Select value={selectedDatasetId} onValueChange={setSelectedDatasetId}>
              <SelectTrigger className="w-[200px]">
                <SelectValue placeholder="选择数据集" />
              </SelectTrigger>
              <SelectContent>
                {DATASETS.map((d) => (
                  <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              onClick={async () => {
                if (!selectedDatasetId) return;
                await triggerQuality(selectedDatasetId);
                toast.success("已触发质量检查");
              }}
              disabled={!selectedDatasetId}
            >
              触发数据质量
            </Button>
            <Button
              variant="secondary"
              onClick={async () => {
                if (!selectedDatasetId) return;
                const r = (await latestQuality(selectedDatasetId)) as any;
                setLatest(r);
              }}
              disabled={!selectedDatasetId}
            >
              查看最新结果
            </Button>
          </div>
        </CardHeader>
        <CardContent className="overflow-x-auto">
					<table className="w-full min-w-[960px] table-fixed border-collapse text-sm">
						<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
							<tr>
								<th className="px-3 py-2">规则名</th>
								<th className="px-3 py-2">类型</th>
								<th className="px-3 py-2">作用对象</th>
								<th className="px-3 py-2">最近结果</th>
								<th className="px-3 py-2">负责人</th>
								<th className="px-3 py-2">状态</th>
								<th className="px-3 py-2">引用次数</th>
								<th className="px-3 py-2 text-right">操作</th>
							</tr>
						</thead>
						<tbody>
							{filteredRules.map((rule) => (
								<tr key={rule.id} className="border-b border-border/40 last:border-none">
									<td className="px-3 py-3 font-medium">{rule.name}</td>
									<td className="px-3 py-3 text-xs">
										<Badge variant="secondary">{rule.type}</Badge>
									</td>
									<td className="px-3 py-3 text-xs text-muted-foreground">{rule.object}</td>
									<td className="px-3 py-3">
										<Badge
											variant={
												rule.recentResult === "通过"
													? "outline"
													: rule.recentResult === "告警"
														? "secondary"
														: "destructive"
											}
										>
											{rule.recentResult}
										</Badge>
									</td>
									<td className="px-3 py-3 text-xs">{rule.owner}</td>
									<td className="px-3 py-3">
										<Badge variant={rule.status === "启用" ? "default" : "secondary"}>{rule.status}</Badge>
									</td>
									<td className="px-3 py-3 text-xs">{rule.referenceCount}</td>
									<td className="px-3 py-3 text-right">
										<Button variant="ghost" size="sm" onClick={() => openEdit(rule)}>
											编辑
										</Button>
										<Button
											variant="ghost"
											size="sm"
											onClick={async () => {
												try {
													await apiToggleRule(rule.id);
													toast.success("已切换状态");
													const list = (await apiListRules()) as any[];
													setRules(
														list.map((it: any) => ({
															id: String(it.id),
															name: it.name,
															type: it.type,
															object: it.datasetId || "-",
															recentResult: "通过",
															owner: "-",
															status: it.enabled ? "启用" : "停用",
															referenceCount: 0,
															frequency: "日批",
															config: { expression: it.expression },
														})),
													);
												} catch (e) {
													console.error(e);
												}
											}}
										>
											{rule.status === "启用" ? "停用" : "启用"}
										</Button>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</CardContent>
      </Card>

      {latest && (
        <Card>
          <CardHeader>
            <CardTitle>最新质量结果</CardTitle>
          </CardHeader>
          <CardContent>
            <pre className="text-xs whitespace-pre-wrap">{JSON.stringify(latest, null, 2)}</pre>
          </CardContent>
        </Card>
      )}

			<Drawer open={drawerOpen} onOpenChange={setDrawerOpen}>
				<DrawerContent className="max-h-[90vh]">
					<DrawerHeader>
						<DrawerTitle>{form.id ? "编辑质量规则" : "新建质量规则"}</DrawerTitle>
						<DrawerDescription>配置作用对象、执行频率以及不同类型的规则逻辑</DrawerDescription>
					</DrawerHeader>
					<ScrollArea className="px-6 pb-6">
						<div className="grid gap-4 md:grid-cols-2">
							<div className="space-y-2">
								<Label>规则名称</Label>
								<Input
									value={form.name}
									onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
								/>
							</div>
							<div className="space-y-2">
								<Label>负责人</Label>
								<Input
									value={form.owner}
									onChange={(event) => setForm((prev) => ({ ...prev, owner: event.target.value }))}
									placeholder="请输入负责人"
								/>
							</div>
							<div className="space-y-2">
								<Label>规则类型</Label>
								<Select
									value={form.type}
									onValueChange={(value) => setForm((prev) => ({ ...prev, type: value as RuleType, config: {} }))}
								>
									<SelectTrigger>
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										{["空值", "唯一性", "参考值", "阈值", "自定义SQL"].map((option) => (
											<SelectItem key={option} value={option}>
												{option}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</div>
							<div className="space-y-2">
								<Label>执行频率</Label>
								<Select
									value={form.frequency}
									onValueChange={(value) => setForm((prev) => ({ ...prev, frequency: value as RuleForm["frequency"] }))}
								>
									<SelectTrigger>
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										<SelectItem value="小时级">按小时</SelectItem>
										<SelectItem value="日批">按日</SelectItem>
										<SelectItem value="分区到达">分区到达事件</SelectItem>
									</SelectContent>
								</Select>
							</div>
						</div>

						<Tabs defaultValue="target" className="mt-6">
							<TabsList>
								<TabsTrigger value="target">作用对象</TabsTrigger>
								<TabsTrigger value="scope">适用范围</TabsTrigger>
								<TabsTrigger value="config">规则配置</TabsTrigger>
							</TabsList>
							<TabsContent value="target" className="space-y-3 pt-4">
								<div className="space-y-2">
									<Label>数据集</Label>
									<Select
										value={form.datasetId}
										onValueChange={(value) => setForm((prev) => ({ ...prev, datasetId: value, fieldIds: [] }))}
									>
										<SelectTrigger>
											<SelectValue />
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
								<div className="rounded-md border">
									<div className="border-b px-4 py-2 text-xs font-semibold uppercase text-muted-foreground">
										字段选择
									</div>
									<div className="max-h-48 space-y-1 overflow-auto px-4 py-3 text-sm">
										{currentDataset?.fields.map((field) => (
											<label key={field} className="flex items-center gap-2">
												<Checkbox
													checked={form.fieldIds.includes(`${form.datasetId}.${field}`)}
													onCheckedChange={() => toggleField(field)}
												/>
												<span>{field}</span>
											</label>
										))}
									</div>
								</div>
							</TabsContent>
							<TabsContent value="scope" className="space-y-4 pt-4">
								<div className="space-y-2">
									<Label>适用范围</Label>
									<Select
										value={form.scope}
										onValueChange={(value) => setForm((prev) => ({ ...prev, scope: value as RuleForm["scope"] }))}
									>
										<SelectTrigger>
											<SelectValue />
										</SelectTrigger>
										<SelectContent>
											<SelectItem value="全表">全表</SelectItem>
											<SelectItem value="分区">分区</SelectItem>
											<SelectItem value="过滤条件">过滤条件</SelectItem>
										</SelectContent>
									</Select>
								</div>
								{form.scope === "过滤条件" ? (
									<div className="space-y-2">
										<Label>过滤 SQL</Label>
										<Textarea
											value={form.filter ?? ""}
											onChange={(event) => setForm((prev) => ({ ...prev, filter: event.target.value }))}
											rows={3}
											placeholder="dt = current_date"
										/>
									</div>
								) : null}
							</TabsContent>
							<TabsContent value="config" className="space-y-4 pt-4">
								{form.type === "空值" ? (
									<div className="grid gap-4 md:grid-cols-2">
										<div className="space-y-2">
											<Label>允许空占比上限</Label>
											<Input
												placeholder="0.01"
												value={String((form.config.threshold as number | undefined) ?? "")}
												onChange={(event) => updateConfig({ threshold: Number(event.target.value) })}
											/>
										</div>
										<div className="space-y-2">
											<Label>忽略值列表</Label>
											<Textarea
												placeholder="用逗号分隔"
												value={(form.config.ignore as string[] | undefined)?.join(",") ?? ""}
												onChange={(event) =>
													updateConfig({
														ignore: event.target.value
															.split(",")
															.map((item) => item.trim())
															.filter(Boolean),
													})
												}
												rows={3}
											/>
										</div>
									</div>
								) : null}

								{form.type === "唯一性" ? (
									<div className="space-y-3">
										<div className="space-y-2">
											<Label>唯一键字段集合</Label>
											<Textarea
												value={(form.config.keys as string[] | undefined)?.join(",") ?? ""}
												onChange={(event) =>
													updateConfig({
														keys: event.target.value
															.split(",")
															.map((item) => item.trim())
															.filter(Boolean),
													})
												}
												rows={2}
											/>
										</div>
										<div className="space-y-2">
											<Label>冲突处理策略</Label>
											<Select
												value={(form.config.conflict as string | undefined) ?? "告警"}
												onValueChange={(value) => updateConfig({ conflict: value })}
											>
												<SelectTrigger>
													<SelectValue />
												</SelectTrigger>
												<SelectContent>
													<SelectItem value="告警">仅告警</SelectItem>
													<SelectItem value="写入隔离表">写入隔离表</SelectItem>
													<SelectItem value="覆盖旧值">覆盖旧值</SelectItem>
												</SelectContent>
											</Select>
										</div>
									</div>
								) : null}

								{form.type === "参考值" ? (
									<div className="space-y-3">
										<div className="space-y-2">
											<Label>参照表 / 字段</Label>
											<Input
												placeholder="dim_region.code"
												value={(form.config.referenceTable as string | undefined) ?? ""}
												onChange={(event) => updateConfig({ referenceTable: event.target.value })}
											/>
										</div>
										<div className="space-y-2">
											<Label>连接条件</Label>
											<Textarea
												value={(form.config.join as string | undefined) ?? ""}
												onChange={(event) => updateConfig({ join: event.target.value })}
												rows={2}
												placeholder="dwd.region_code = dim_region.code"
											/>
										</div>
										<div className="space-y-2">
											<Label>允许未匹配比例</Label>
											<Input
												value={String((form.config.unmatched as number | undefined) ?? "")}
												onChange={(event) => updateConfig({ unmatched: Number(event.target.value) })}
												placeholder="0.02"
											/>
										</div>
									</div>
								) : null}

								{form.type === "阈值" ? (
									<div className="grid gap-4 md:grid-cols-2">
										<div className="space-y-2">
											<Label>度量表达式</Label>
											<Textarea
												value={(form.config.metric as string | undefined) ?? ""}
												onChange={(event) => updateConfig({ metric: event.target.value })}
												rows={2}
												placeholder="sum(gmv) - sum(target)"
											/>
										</div>
										<div className="space-y-2">
											<Label>阈值类型</Label>
											<Select
												value={(form.config.bound as string | undefined) ?? "上下界"}
												onValueChange={(value) => updateConfig({ bound: value })}
											>
												<SelectTrigger>
													<SelectValue />
												</SelectTrigger>
												<SelectContent>
													<SelectItem value="上下界">上下界</SelectItem>
													<SelectItem value="同比">同比</SelectItem>
													<SelectItem value="环比">环比</SelectItem>
												</SelectContent>
											</Select>
										</div>
									</div>
								) : null}

								{form.type === "自定义SQL" ? (
									<div className="space-y-3">
										<div className="space-y-2">
											<Label>SQL 脚本</Label>
											<Textarea
												className="font-mono"
												rows={8}
												value={(form.config.sql as string | undefined) ?? ""}
												onChange={(event) => updateConfig({ sql: event.target.value })}
											/>
										</div>
										<div className="space-y-2">
											<Label>参数 Schema (JSON)</Label>
											<Textarea
												className="font-mono"
												rows={4}
												value={JSON.stringify(form.config.params ?? [], null, 2)}
												onChange={(event) => {
													try {
														updateConfig({ params: JSON.parse(event.target.value) });
													} catch (error) {
														// ignore parse errors while typing
													}
												}}
											/>
										</div>
										<Button
											variant="outline"
											onClick={() => {
												setSqlPreview((form.config.sql as string | undefined) ?? "");
												setSqlDialogOpen(true);
											}}
										>
											<Icon icon="mdi:database-search" className="mr-1" size={16} /> 示例数据
										</Button>
									</div>
								) : null}
							</TabsContent>
						</Tabs>

						<div className="mt-6 flex items-center justify-between">
							<label className="flex items-center gap-2 text-sm">
								<Switch
									checked={form.enabled}
									onCheckedChange={(checked) => setForm((prev) => ({ ...prev, enabled: Boolean(checked) }))}
								/>
								启用规则
							</label>
						</div>
					</ScrollArea>
					<DrawerFooter>
						<Button onClick={handleSubmit}>保存</Button>
						<DrawerClose asChild>
							<Button variant="outline">取消</Button>
						</DrawerClose>
					</DrawerFooter>
				</DrawerContent>
			</Drawer>

			<Dialog open={sqlDialogOpen} onOpenChange={setSqlDialogOpen}>
				<DialogContent className="max-w-3xl">
					<DialogHeader>
						<DialogTitle>示例 SQL 与结果</DialogTitle>
					</DialogHeader>
					<div className="space-y-3">
						<Label>SQL</Label>
						<pre className="max-h-48 overflow-auto rounded-md border bg-muted/40 p-3 text-xs font-mono">
							{sqlPreview || "暂无 SQL"}
						</pre>
						<Label>示例数据</Label>
						<pre className="max-h-48 overflow-auto rounded-md border bg-muted/40 p-3 text-xs font-mono">
							{SAMPLE_SQL_PREVIEW}
						</pre>
					</div>
				</DialogContent>
			</Dialog>
		</div>
	);
}
