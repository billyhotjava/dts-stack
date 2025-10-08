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
import { Separator } from "@/ui/separator";
import { Switch } from "@/ui/switch";

const FIELD_TYPES = ["STRING", "INTEGER", "DECIMAL", "DATE", "BOOLEAN"] as const;
const MASK_STATUSES = [
	{ value: "ALL", label: "全部" },
	{ value: "MASKED", label: "已脱敏" },
	{ value: "PLAIN", label: "未脱敏" },
] as const;

type FieldType = (typeof FIELD_TYPES)[number];
type MaskValue = (typeof MASK_STATUSES)[number]["value"];

type FieldDefinition = {
	name: string;
	type: FieldType;
	term?: string;
	masked: boolean;
	description?: string;
};

const FIELD_DEFINITIONS: FieldDefinition[] = [
	{ name: "customer_id", type: "STRING", term: "客户唯一标识", masked: false, description: "全局唯一客户编号" },
	{ name: "customer_name", type: "STRING", term: "客户姓名", masked: true, description: "脱敏展示客户姓名" },
	{ name: "credential_type", type: "STRING", term: "证件类型", masked: true },
	{ name: "credential_no", type: "STRING", term: "证件号码", masked: true },
	{ name: "mobile", type: "STRING", term: "联系方式", masked: true },
	{ name: "gender", type: "STRING", term: "性别", masked: false },
	{ name: "birth_date", type: "DATE", term: "出生日期", masked: true },
	{ name: "register_channel", type: "STRING", term: "注册渠道", masked: false },
	{ name: "vip_level", type: "STRING", term: "会员等级", masked: false },
	{ name: "is_blacklisted", type: "BOOLEAN", term: "黑名单标记", masked: false },
	{ name: "lifecycle_status", type: "STRING", term: "生命周期状态", masked: false },
	{ name: "updated_at", type: "DATE", term: "最近更新时间", masked: false },
];

const PREVIEW_ROWS = Array.from({ length: 100 }).map((_, index) => ({
	customer_id: `CUS-${1000 + index}`,
	customer_name: "王**",
	credential_type: "身份证",
	credential_no: "51************1234",
	mobile: "139****0012",
	gender: index % 2 === 0 ? "男" : "女",
	birth_date: "1988-07-16",
	register_channel: index % 3 === 0 ? "线上" : "线下",
	vip_level: ["普通", "黄金", "白金"][index % 3],
	is_blacklisted: index % 11 === 0,
	lifecycle_status: ["激活", "沉默", "流失"][index % 3],
	updated_at: `2024-12-${(index % 28) + 1}`,
}));

const OPERATORS = ["=", "!=", ">", "<", "between", "in", "like"];

type FilterCondition = {
	field: string;
	operator: string;
	value: string;
};

const DEFAULT_CONDITIONS: FilterCondition[] = [
	{ field: "vip_level", operator: "=", value: "黄金" },
	{ field: "is_blacklisted", operator: "=", value: "false" },
];

export default function DataPreviewPage() {
	const [keyword, setKeyword] = useState("");
	const [typeFilters, setTypeFilters] = useState<FieldType[]>([]);
	const [maskFilter, setMaskFilter] = useState<MaskValue>("ALL");
	const [topN, setTopN] = useState("100");
	const [conditions, setConditions] = useState<FilterCondition[]>(DEFAULT_CONDITIONS);
	const [previewMaskedOnly, setPreviewMaskedOnly] = useState(false);

	const filteredFields = useMemo(() => {
		return FIELD_DEFINITIONS.filter((field) => {
			const keywordMatch = keyword.trim()
				? field.name.toLowerCase().includes(keyword.trim().toLowerCase()) ||
					field.term?.includes(keyword.trim()) ||
					field.description?.includes(keyword.trim())
				: true;
			const typeMatch = typeFilters.length ? typeFilters.includes(field.type) : true;
			const maskMatch = maskFilter === "ALL" ? true : maskFilter === "MASKED" ? field.masked : !field.masked;
			return keywordMatch && typeMatch && maskMatch;
		});
	}, [keyword, typeFilters, maskFilter]);

	const displayedRows = useMemo(() => {
		const limit = Number.parseInt(topN, 10);
		const sourceRows = previewMaskedOnly
			? PREVIEW_ROWS.filter((row) =>
					FIELD_DEFINITIONS.some((field) => field.masked && row[field.name as keyof typeof row]),
				)
			: PREVIEW_ROWS;
		return sourceRows.slice(0, Number.isNaN(limit) ? 100 : limit);
	}, [topN, previewMaskedOnly]);

	const generatedSql = useMemo(() => {
		const whereClause = conditions
			.filter((condition) => condition.field && condition.operator && condition.value)
			.map((condition) => {
				const field = condition.field;
				const operator = condition.operator;
				const value = condition.value;
				if (operator === "in") {
					return `${field} IN (${value})`;
				}
				if (operator === "between") {
					const [start, end] = value.split(",");
					return `${field} BETWEEN '${start?.trim()}' AND '${end?.trim()}'`;
				}
				if (operator === "like") {
					return `${field} LIKE '%${value}%'`;
				}
				if (operator === "=" && (value === "true" || value === "false")) {
					return `${field} = ${value}`;
				}
				return `${field} ${operator} '${value}'`;
			})
			.join("\n  AND ");
		return `SELECT *\nFROM customer_master\nWHERE ${whereClause || "1 = 1"}\nLIMIT ${topN};`;
	}, [conditions, topN]);

	const toggleTypeFilter = (type: FieldType) => {
		setTypeFilters((prev) => (prev.includes(type) ? prev.filter((item) => item !== type) : [...prev, type]));
	};

	const handleMaskChange = (value: string) => {
		setMaskFilter(value as MaskValue);
	};

	const updateCondition = (index: number, updates: Partial<FilterCondition>) => {
		setConditions((prev) => {
			const next = [...prev];
			next[index] = { ...next[index], ...updates };
			return next;
		});
	};

	const addCondition = () => {
		setConditions((prev) => [...prev, { field: "", operator: "=", value: "" }]);
	};

	const removeCondition = (index: number) => {
		setConditions((prev) => prev.filter((_, conditionIndex) => conditionIndex !== index));
	};

	return (
		<div className="space-y-4">
			<div className="flex flex-wrap items-center gap-3 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">
				<span aria-hidden className="text-red-500 text-lg">
					★
				</span>
            <span className="font-semibold">此功能涉及数据密级（DATA_*）数据，请注意保密！</span>
				<Badge variant="secondary" className="bg-red-100 text-red-700">
                    默认数据密级：DATA_SECRET（示例）
				</Badge>
				<Badge variant="outline" className="border-red-300 text-red-700">
					导出需审批，禁自建渠道
				</Badge>
			</div>

			<div className="grid gap-4 xl:grid-cols-[320px,1fr]">
				<Card className="h-[calc(100vh-240px)]">
					<CardHeader className="space-y-4">
						<CardTitle className="text-base">字段列表</CardTitle>
						<div className="space-y-2">
							<Label className="text-xs text-muted-foreground">字段筛选</Label>
							<Input
								placeholder="搜索字段 / 术语"
								value={keyword}
								onChange={(event) => setKeyword(event.target.value)}
							/>
						</div>
						<div className="space-y-2">
							<Label className="text-xs text-muted-foreground">字段类型</Label>
							<div className="flex flex-wrap gap-2">
								{FIELD_TYPES.map((type) => (
									<label key={type} className="flex items-center gap-2 rounded-md border px-3 py-1 text-xs font-medium">
										<Checkbox checked={typeFilters.includes(type)} onCheckedChange={() => toggleTypeFilter(type)} />
										{type}
									</label>
								))}
							</div>
						</div>
						<div className="space-y-2">
							<Label className="text-xs text-muted-foreground">脱敏筛选</Label>
							<Select value={maskFilter} onValueChange={handleMaskChange}>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									{MASK_STATUSES.map((status) => (
										<SelectItem key={status.value} value={status.value}>
											{status.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
					</CardHeader>
					<CardContent className="h-full p-0">
						<ScrollArea className="h-full">
							<table className="w-full border-separate border-spacing-y-1 text-sm">
								<thead className="sticky top-0 bg-background">
									<tr className="text-left text-xs uppercase text-muted-foreground">
										<th className="px-4 py-2">字段名</th>
										<th className="px-4 py-2">类型</th>
										<th className="px-4 py-2">业务术语</th>
										<th className="px-4 py-2">脱敏</th>
									</tr>
								</thead>
								<tbody>
									{filteredFields.map((field) => (
										<tr key={field.name} className="rounded-md border border-border/60 bg-muted/30">
											<td className="px-4 py-3">
												<div className="font-medium">{field.name}</div>
												{field.description ? (
													<div className="text-xs text-muted-foreground">{field.description}</div>
												) : null}
											</td>
											<td className="px-4 py-3 text-xs font-semibold text-muted-foreground">{field.type}</td>
											<td className="px-4 py-3 text-xs">{field.term || "--"}</td>
											<td className="px-4 py-3">
												<Badge
													variant={field.masked ? "secondary" : "outline"}
													className={clsx({ "bg-amber-100 text-amber-700": field.masked })}
												>
													{field.masked ? "已脱敏" : "原始"}
												</Badge>
											</td>
										</tr>
									))}
								</tbody>
							</table>
						</ScrollArea>
					</CardContent>
				</Card>

				<div className="space-y-4">
					<Card>
						<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
							<div>
								<CardTitle className="text-base">数据预览 Top N</CardTitle>
								<p className="text-xs text-muted-foreground">默认展示 Top 100，支持切换 N；脱敏字段以图标提示</p>
							</div>
							<div className="flex items-center gap-3">
								<Select value={topN} onValueChange={setTopN}>
									<SelectTrigger className="w-[120px]">
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										{["50", "100", "500"].map((value) => (
											<SelectItem key={value} value={value}>
												Top {value}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
								<label className="flex items-center gap-2 text-xs text-muted-foreground">
									<Switch
										checked={previewMaskedOnly}
										onCheckedChange={(checked) => setPreviewMaskedOnly(Boolean(checked))}
									/>
									仅查看含脱敏字段
								</label>
							</div>
						</CardHeader>
						<CardContent className="overflow-x-auto">
							<table className="w-full min-w-[960px] table-fixed border-collapse text-sm">
								<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
									<tr>
										{FIELD_DEFINITIONS.map((field) => (
											<th key={field.name} className="px-3 py-2">
												<div className="flex items-center gap-1">
													{field.name}
													{field.masked ? (
														<Icon icon="solar:shield-keyhole-bold" className="text-amber-500" size={14} />
													) : null}
												</div>
											</th>
										))}
									</tr>
								</thead>
								<tbody>
									{displayedRows.map((row, rowIndex) => (
										<tr
											key={row.customer_id}
											className={clsx("border-b border-border/40", { "bg-muted/20": rowIndex % 2 === 0 })}
										>
											{FIELD_DEFINITIONS.map((field) => (
												<td
													key={field.name}
													className="truncate px-3 py-2"
													title={String(row[field.name as keyof typeof row])}
												>
													{String(row[field.name as keyof typeof row])}
												</td>
											))}
										</tr>
									))}
								</tbody>
							</table>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<CardTitle className="text-base">快捷筛选构造器</CardTitle>
						</CardHeader>
						<CardContent className="space-y-4">
							<div className="space-y-3">
								{conditions.map((condition, index) => (
									<div
										key={`${condition.field}-${index}`}
										className="flex flex-wrap gap-2 rounded-md border border-dashed border-border/60 p-3"
									>
										<Select value={condition.field} onValueChange={(value) => updateCondition(index, { field: value })}>
											<SelectTrigger className="w-[180px]">
												<SelectValue placeholder="选择字段" />
											</SelectTrigger>
											<SelectContent>
												{FIELD_DEFINITIONS.map((field) => (
													<SelectItem key={field.name} value={field.name}>
														{field.name}
														<span className="text-xs text-muted-foreground"> · {field.type}</span>
													</SelectItem>
												))}
											</SelectContent>
										</Select>
										<Select
											value={condition.operator}
											onValueChange={(value) => updateCondition(index, { operator: value })}
										>
											<SelectTrigger className="w-[120px]">
												<SelectValue />
											</SelectTrigger>
											<SelectContent>
												{OPERATORS.map((operator) => (
													<SelectItem key={operator} value={operator}>
														{operator}
													</SelectItem>
												))}
											</SelectContent>
										</Select>
										<Input
											className="min-w-[200px]"
											placeholder="输入值，between 使用 start,end"
											value={condition.value}
											onChange={(event) => updateCondition(index, { value: event.target.value })}
										/>
										<Button variant="ghost" size="icon" onClick={() => removeCondition(index)}>
											<Icon icon="mdi:trash-can-outline" size={18} />
										</Button>
									</div>
								))}
							</div>
							<Button variant="outline" size="sm" onClick={addCondition}>
								<Icon icon="mdi:plus" className="mr-1" size={16} /> 添加条件
							</Button>

							<Separator />

							<div className="space-y-2">
								<Label className="text-xs text-muted-foreground">生成 SQL</Label>
								<pre className="max-h-48 overflow-auto rounded-md border bg-muted/40 p-3 text-xs">{generatedSql}</pre>
							</div>
							<div className="flex justify-end gap-2">
								<Button variant="secondary">
									<Icon icon="mdi:content-copy" className="mr-1" size={16} />
									复制 SQL
								</Button>
								<Button>
									<Icon icon="mdi:database-eye-outline" className="mr-1" size={16} />
									生成 SQL
								</Button>
							</div>
						</CardContent>
					</Card>
				</div>
			</div>
		</div>
	);
}
