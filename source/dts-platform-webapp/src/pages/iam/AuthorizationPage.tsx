import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Checkbox } from "@/ui/checkbox";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Separator } from "@/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import policyApi, {
	type BatchAuthorizationInput,
	type ConflictItem,
	type DatasetPoliciesResponse,
	type DomainNode,
	type SubjectType,
} from "@/api/services/iamPolicyService";
import SensitiveNotice from "@/components/security/SensitiveNotice";

type WizardStep = "subjects" | "objects" | "scope" | "confirm";

function SectionHeader({ icon, title, extra }: { icon: string; title: string; extra?: React.ReactNode }) {
	return (
		<div className="flex items-center justify-between gap-2">
			<div className="flex items-center gap-2 text-base font-medium">
				<Icon icon={icon} className="text-primary" /> {title}
			</div>
			{extra}
		</div>
	);
}

function ObjectPoliciesTable({ data }: { data: DatasetPoliciesResponse["objectPolicies"] }) {
	return (
		<div className="overflow-x-auto">
			<table className="w-full min-w-[760px] table-fixed border-collapse text-sm">
				<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
					<tr>
						<th className="px-3 py-2 font-medium">主体</th>
						<th className="px-3 py-2 font-medium">效果</th>
						<th className="px-3 py-2 font-medium">有效期</th>
						<th className="px-3 py-2 font-medium">来源</th>
					</tr>
				</thead>
				<tbody>
					{data.map((item, idx) => (
						<tr key={idx} className="border-b last:border-b-0">
							<td className="px-3 py-2">
								<div className="flex items-center gap-2">
									<Badge variant="outline">{item.subjectType}</Badge>
									<span className="font-medium">{item.subjectName}</span>
								</div>
							</td>
							<td className="px-3 py-2">
								<Badge
									className={item.effect === "ALLOW" ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"}
								>
									{item.effect}
								</Badge>
							</td>
							<td className="px-3 py-2 text-xs text-muted-foreground">
								{item.validFrom ? new Date(item.validFrom).toLocaleDateString() : "-"} ~{" "}
								{item.validTo ? new Date(item.validTo).toLocaleDateString() : "-"}
							</td>
							<td className="px-3 py-2 text-xs text-muted-foreground">{item.source}</td>
						</tr>
					))}
					{!data.length && (
						<tr>
							<td colSpan={4} className="px-3 py-6 text-center text-xs text-muted-foreground">
								暂无对象级策略
							</td>
						</tr>
					)}
				</tbody>
			</table>
		</div>
	);
}

function FieldPoliciesTable({ data }: { data: DatasetPoliciesResponse["fieldPolicies"] }) {
	return (
		<div className="overflow-x-auto">
			<table className="w-full min-w-[760px] table-fixed border-collapse text-sm">
				<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
					<tr>
						<th className="px-3 py-2 font-medium">字段</th>
						<th className="px-3 py-2 font-medium">主体</th>
						<th className="px-3 py-2 font-medium">效果</th>
					</tr>
				</thead>
				<tbody>
					{data.map((item, idx) => (
						<tr key={`${item.field}-${idx}`} className="border-b last:border-b-0">
							<td className="px-3 py-2 font-medium">{item.field}</td>
							<td className="px-3 py-2">
								<div className="flex items-center gap-2">
									<Badge variant="outline">{item.subjectType}</Badge>
									<span>{item.subjectName}</span>
								</div>
							</td>
							<td className="px-3 py-2">
								<Badge
									className={item.effect === "ALLOW" ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"}
								>
									{item.effect}
								</Badge>
							</td>
						</tr>
					))}
					{!data.length && (
						<tr>
							<td colSpan={3} className="px-3 py-6 text-center text-xs text-muted-foreground">
								暂无字段级策略
							</td>
						</tr>
					)}
				</tbody>
			</table>
		</div>
	);
}

function RowConditionsTable({ data }: { data: DatasetPoliciesResponse["rowConditions"] }) {
	return (
		<div className="overflow-x-auto">
			<table className="w-full min-w-[760px] table-fixed border-collapse text-sm">
				<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
					<tr>
						<th className="px-3 py-2 font-medium">主体</th>
						<th className="px-3 py-2 font-medium">表达式</th>
						<th className="px-3 py-2 font-medium">说明</th>
					</tr>
				</thead>
				<tbody>
					{data.map((item, idx) => (
						<tr key={idx} className="border-b last:border-b-0">
							<td className="px-3 py-2">
								<div className="flex items-center gap-2">
									<Badge variant="outline">{item.subjectType}</Badge>
									<span>{item.subjectName}</span>
								</div>
							</td>
							<td className="px-3 py-2 font-mono text-xs">{item.expression}</td>
							<td className="px-3 py-2 text-xs text-muted-foreground">{item.description || "-"}</td>
						</tr>
					))}
					{!data.length && (
						<tr>
							<td colSpan={3} className="px-3 py-6 text-center text-xs text-muted-foreground">
								暂无行域条件
							</td>
						</tr>
					)}
				</tbody>
			</table>
		</div>
	);
}

export default function AuthorizationPage() {
	const [searchParams] = useSearchParams();
	const datasetIdFromQuery = searchParams.get("datasetId");
	const [tab, setTab] = useState<"object" | "subject">("object");
	const [tree, setTree] = useState<DomainNode[]>([]);
	const [selectedDatasetId, setSelectedDatasetId] = useState<string | null>(null);
	const [datasetPolicies, setDatasetPolicies] = useState<DatasetPoliciesResponse | null>(null);
	const [datasetKeyword, setDatasetKeyword] = useState("");

	// Subject view state
	const [subjectType, setSubjectType] = useState<SubjectType>("user");
	const [subjectKeyword, setSubjectKeyword] = useState("");
	const [subjectResults, setSubjectResults] = useState<{ id: string; name: string }[]>([]);
	const [activeSubject, setActiveSubject] = useState<{ id: string; name: string } | null>(null);
	const [subjectVisible, setSubjectVisible] = useState<{ objects: any[]; fields: any[]; expressions: any[] } | null>(
		null,
	);

	// Batch wizard state
	const [wizardOpen, setWizardOpen] = useState(false);
	const [wizardStep, setWizardStep] = useState<WizardStep>("subjects");
	const [selectedSubjects, setSelectedSubjects] = useState<{ type: SubjectType; id: string; name: string }[]>([]);
	const [selectedObjects, setSelectedObjects] = useState<{ datasetId: string; datasetName: string }[]>([]);
	const [scopeObjectEffect, setScopeObjectEffect] = useState<"ALLOW" | "DENY" | "">("");
	const [scopeFieldsText, setScopeFieldsText] = useState("");
	const [scopeFieldsEffect, setScopeFieldsEffect] = useState<"ALLOW" | "DENY" | "">("");
	const [scopeRowExpr, setScopeRowExpr] = useState("org_id IN (${orgChildren}) AND project_id = :projectId");
	const [validFrom, setValidFrom] = useState("");
	const [validTo, setValidTo] = useState("");
	const [conflicts, setConflicts] = useState<ConflictItem[] | null>(null);
	const [previewing, setPreviewing] = useState(false);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		// init domain tree
		void policyApi.getDomainDatasetTree().then((nodes) => {
			setTree(nodes);
			const first = nodes[0]?.datasets?.[0];
			if (datasetIdFromQuery) {
				const matched = nodes
					.flatMap((node) => node.datasets || [])
					.find((ds) => ds.id === datasetIdFromQuery);
				if (matched) {
					setSelectedDatasetId(datasetIdFromQuery);
					return;
				}
			}
			if (first) setSelectedDatasetId(first.id);
		});
	}, [datasetIdFromQuery]);

	useEffect(() => {
		if (!selectedDatasetId) return;
		void policyApi.getDatasetPolicies(selectedDatasetId).then(setDatasetPolicies);
	}, [selectedDatasetId]);

	const filteredTree = useMemo(() => {
		const k = datasetKeyword.trim().toLowerCase();
		if (!k) return tree;
		return tree.map((dom) => ({
			...dom,
			datasets: dom.datasets.filter((ds) => ds.name.toLowerCase().includes(k)),
		}));
	}, [datasetKeyword, tree]);

	const onSearchSubjects = async () => {
		const list = await policyApi.searchSubjects(subjectType, subjectKeyword);
		setSubjectResults(list);
		setActiveSubject(null);
		setSubjectVisible(null);
	};

	const onPickSubject = async (s: { id: string; name: string }) => {
		setActiveSubject(s);
		const visible = await policyApi.getSubjectVisible(subjectType, s.id);
		setSubjectVisible(visible);
	};

	const toggleSelectSubject = (item: { id: string; name: string }) => {
		setSelectedSubjects((prev) => {
			const exists = prev.find((p) => p.id === item.id && p.type === subjectType);
			if (exists) return prev.filter((p) => !(p.id === item.id && p.type === subjectType));
			return [...prev, { type: subjectType, id: item.id, name: item.name }];
		});
	};

	const toggleSelectObject = (ds: { id: string; name: string }) => {
		setSelectedObjects((prev) => {
			const exists = prev.find((p) => p.datasetId === ds.id);
			if (exists) return prev.filter((p) => p.datasetId !== ds.id);
			return [...prev, { datasetId: ds.id, datasetName: ds.name }];
		});
	};

	const insertToken = (text: string) => {
		setScopeRowExpr((prev) => (prev ? `${prev} AND ${text}` : text));
	};

	const buildBatchInput = (): BatchAuthorizationInput => {
		const fieldNames = scopeFieldsText
			.split(",")
			.map((s) => s.trim())
			.filter(Boolean);
		return {
			subjects: selectedSubjects,
			objects: selectedObjects,
			scope: {
				objectEffect: scopeObjectEffect || undefined,
				fields:
					fieldNames.length && scopeFieldsEffect
						? fieldNames.map((n) => ({ name: n, effect: scopeFieldsEffect! }))
						: undefined,
				rowExpression: scopeRowExpr || undefined,
				validFrom: validFrom || undefined,
				validTo: validTo || undefined,
			},
		};
	};

	const doPreview = async () => {
		setPreviewing(true);
		try {
			const res = await policyApi.previewConflicts(buildBatchInput());
			setConflicts(res.conflicts);
			setWizardStep("confirm");
		} finally {
			setPreviewing(false);
		}
	};

	const doSubmit = async () => {
		setSubmitting(true);
		try {
			await policyApi.applyBatch(buildBatchInput());
			setWizardOpen(false);
			setWizardStep("subjects");
			setSelectedSubjects([]);
			setSelectedObjects([]);
			setScopeObjectEffect("");
			setScopeFieldsText("");
			setScopeFieldsEffect("");
			setScopeRowExpr("org_id IN (${orgChildren}) AND project_id = :projectId");
			setValidFrom("");
			setValidTo("");
			setConflicts(null);
			// refresh current dataset policies when staying in object view
			if (selectedDatasetId) {
				void policyApi.getDatasetPolicies(selectedDatasetId).then(setDatasetPolicies);
			}
		} finally {
			setSubmitting(false);
		}
	};

	return (
		<div className="space-y-4">
			<SensitiveNotice />
			<div className="flex items-center justify-between">
				<div>
					<h2 className="text-lg font-semibold">授权策略</h2>
					<p className="text-sm text-muted-foreground">对象维度与主体维度双视图；支持批量授权与行域 DSL 构造器。</p>
				</div>
				<Button onClick={() => setWizardOpen(true)}>
					<Icon icon="solar:magic-stick-3-bold-duotone" /> 批量授权
				</Button>
			</div>

			<Tabs value={tab} onValueChange={(v) => setTab(v as any)}>
				<TabsList>
					<TabsTrigger value="object">对象维度</TabsTrigger>
					<TabsTrigger value="subject">主体维度</TabsTrigger>
				</TabsList>

				<TabsContent value="object" className="mt-4">
					<div className="grid gap-4 2xl:grid-cols-[360px,1fr]">
						<Card className="h-[calc(100vh-260px)]">
							<CardHeader className="space-y-3">
								<CardTitle className="text-base">对象树（域/数据集）</CardTitle>
								<Input
									placeholder="搜索数据集"
									value={datasetKeyword}
									onChange={(e) => setDatasetKeyword(e.target.value)}
								/>
							</CardHeader>
							<CardContent className="p-0">
								<ScrollArea className="h-[calc(100vh-340px)]">
									<div className="p-3 space-y-3">
										{filteredTree.map((dom) => (
											<div key={dom.id} className="space-y-1">
												<div className="px-2 text-xs font-medium text-muted-foreground">{dom.name}</div>
												<div className="space-y-1">
													{dom.datasets.map((ds) => (
														<button
															key={ds.id}
															className={`w-full rounded-md px-2 py-1.5 text-left text-sm hover:bg-accent ${selectedDatasetId === ds.id ? "bg-accent" : ""}`}
															onClick={() => setSelectedDatasetId(ds.id)}
														>
															<span className="font-medium">{ds.name}</span>
															<span className="ml-2 text-xs text-muted-foreground">({ds.fields.length})</span>
														</button>
													))}
													{!dom.datasets.length && <div className="px-2 text-xs text-muted-foreground">无数据集</div>}
												</div>
											</div>
										))}
									</div>
								</ScrollArea>
							</CardContent>
						</Card>

						<Card>
							<CardHeader className="space-y-2">
								<CardTitle className="text-base flex items-center gap-2">
									<Icon icon="solar:shield-check-bold-duotone" className="text-primary" /> 授权表
								</CardTitle>
							</CardHeader>
							<CardContent className="space-y-4">
								<Tabs defaultValue="obj">
									<TabsList>
										<TabsTrigger value="obj">对象级</TabsTrigger>
										<TabsTrigger value="field">字段级</TabsTrigger>
										<TabsTrigger value="row">行域条件</TabsTrigger>
									</TabsList>
									<TabsContent value="obj" className="mt-4">
										{datasetPolicies ? (
											<ObjectPoliciesTable data={datasetPolicies.objectPolicies} />
										) : (
											<div className="text-sm text-muted-foreground">加载中…</div>
										)}
									</TabsContent>
									<TabsContent value="field" className="mt-4">
										{datasetPolicies ? (
											<FieldPoliciesTable data={datasetPolicies.fieldPolicies} />
										) : (
											<div className="text-sm text-muted-foreground">加载中…</div>
										)}
									</TabsContent>
									<TabsContent value="row" className="mt-4">
										{datasetPolicies ? (
											<RowConditionsTable data={datasetPolicies.rowConditions} />
										) : (
											<div className="text-sm text-muted-foreground">加载中…</div>
										)}
									</TabsContent>
								</Tabs>
							</CardContent>
						</Card>
					</div>
				</TabsContent>

				<TabsContent value="subject" className="mt-4">
					<div className="grid gap-4 2xl:grid-cols-[420px,1fr]">
						<Card>
							<CardHeader className="space-y-3">
								<CardTitle className="text-base">选择主体</CardTitle>
								<div className="flex items-center gap-2">
									<Select value={subjectType} onValueChange={(v) => setSubjectType(v as SubjectType)}>
										<SelectTrigger className="w-[140px]">
											<SelectValue />
										</SelectTrigger>
										<SelectContent>
											<SelectItem value="user">用户</SelectItem>
											<SelectItem value="role">角色</SelectItem>
											<SelectItem value="org">组织</SelectItem>
											<SelectItem value="project">项目</SelectItem>
										</SelectContent>
									</Select>
									<Input
										placeholder="搜索主体名称"
										value={subjectKeyword}
										onChange={(e) => setSubjectKeyword(e.target.value)}
										onKeyDown={(e) => e.key === "Enter" && onSearchSubjects()}
									/>
									<Button onClick={onSearchSubjects}>搜索</Button>
								</div>
							</CardHeader>
							<CardContent className="space-y-3">
								<Label className="text-xs text-muted-foreground">匹配结果</Label>
								<div className="flex flex-wrap gap-2">
									{subjectResults.map((s) => (
										<button
											key={s.id}
											className={`rounded-md border px-3 py-1 text-xs ${activeSubject?.id === s.id ? "border-primary bg-primary/10" : ""}`}
											onClick={() => onPickSubject(s)}
										>
											{s.name}
										</button>
									))}
									{!subjectResults.length && <span className="text-xs text-muted-foreground">暂无结果</span>}
								</div>
							</CardContent>
						</Card>

						<Card>
							<CardHeader>
								<CardTitle className="text-base">可见对象/字段与行域</CardTitle>
							</CardHeader>
							<CardContent className="space-y-4">
								{subjectVisible ? (
									<>
										<SectionHeader icon="solar:database-bold-duotone" title="可见对象" />
										<div className="overflow-x-auto">
											<table className="w-full min-w-[760px] table-fixed border-collapse text-sm">
												<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
													<tr>
														<th className="px-3 py-2 font-medium">数据集</th>
														<th className="px-3 py-2 font-medium">效果</th>
													</tr>
												</thead>
												<tbody>
													{subjectVisible.objects.map((o, idx) => (
														<tr key={idx} className="border-b last:border-b-0">
															<td className="px-3 py-2 font-medium">{o.datasetName}</td>
															<td className="px-3 py-2">
																<Badge className="bg-emerald-100 text-emerald-700">{o.effect}</Badge>
															</td>
														</tr>
													))}
												</tbody>
											</table>
										</div>

										<Separator />

										<SectionHeader icon="solar:list-bold-duotone" title="字段清单" />
										<div className="overflow-x-auto">
											<table className="w-full min-w-[760px] table-fixed border-collapse text-sm">
												<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
													<tr>
														<th className="px-3 py-2 font-medium">数据集</th>
														<th className="px-3 py-2 font-medium">字段</th>
														<th className="px-3 py-2 font-medium">效果</th>
													</tr>
												</thead>
												<tbody>
													{subjectVisible.fields.map((f, idx) => (
														<tr key={idx} className="border-b last:border-b-0">
															<td className="px-3 py-2">{f.datasetName}</td>
															<td className="px-3 py-2 font-medium">{f.field}</td>
															<td className="px-3 py-2">
																<Badge
																	className={
																		f.effect === "ALLOW" ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"
																	}
																>
																	{f.effect}
																</Badge>
															</td>
														</tr>
													))}
												</tbody>
											</table>
										</div>

										<Separator />

										<SectionHeader icon="solar:filter-bold-duotone" title="行域表达式" />
										<ul className="list-disc pl-6 text-sm">
											{subjectVisible.expressions.map((e, idx) => (
												<li key={idx} className="mb-1">
													<span className="font-medium mr-2">{e.datasetName}</span>
													<span className="font-mono text-xs">{e.expression}</span>
												</li>
											))}
										</ul>
									</>
								) : (
									<div className="text-sm text-muted-foreground">请选择主体查看可见范围</div>
								)}
							</CardContent>
						</Card>
					</div>
				</TabsContent>
			</Tabs>

			{/* 批量授权向导 */}
			<Dialog open={wizardOpen} onOpenChange={setWizardOpen}>
				<DialogContent>
					<DialogHeader>
						<DialogTitle>批量授权向导</DialogTitle>
						<DialogDescription>选择主体与对象，设置作用范围与有效期，提交前将进行冲突检测。</DialogDescription>
					</DialogHeader>

					<div className="space-y-4">
						<Tabs value={wizardStep} onValueChange={(v) => setWizardStep(v as WizardStep)}>
							<TabsList>
								<TabsTrigger value="subjects">1 主体</TabsTrigger>
								<TabsTrigger value="objects">2 对象</TabsTrigger>
								<TabsTrigger value="scope">3 范围</TabsTrigger>
								<TabsTrigger value="confirm">4 确认</TabsTrigger>
							</TabsList>

							<TabsContent value="subjects" className="mt-4 space-y-3">
								<div className="flex items-center gap-2">
									<Select value={subjectType} onValueChange={(v) => setSubjectType(v as SubjectType)}>
										<SelectTrigger className="w-[140px]">
											<SelectValue />
										</SelectTrigger>
										<SelectContent>
											<SelectItem value="user">用户</SelectItem>
											<SelectItem value="role">角色</SelectItem>
											<SelectItem value="org">组织</SelectItem>
											<SelectItem value="project">项目</SelectItem>
										</SelectContent>
									</Select>
									<Input
										placeholder="搜索主体"
										value={subjectKeyword}
										onChange={(e) => setSubjectKeyword(e.target.value)}
										onKeyDown={(e) => e.key === "Enter" && onSearchSubjects()}
									/>
									<Button onClick={onSearchSubjects}>搜索</Button>
								</div>
								<div className="rounded-md border p-3 max-h-[220px] overflow-auto space-y-1">
									{subjectResults.map((s) => {
										const checked = selectedSubjects.some((it) => it.id === s.id && it.type === subjectType);
										return (
											<label key={s.id} className="flex items-center gap-2 text-sm">
												<Checkbox checked={checked} onCheckedChange={() => toggleSelectSubject(s)} />
												<span>{s.name}</span>
											</label>
										);
									})}
									{!subjectResults.length && <div className="text-xs text-muted-foreground">搜索后选择主体</div>}
								</div>
								<div className="text-xs text-muted-foreground">已选：{selectedSubjects.length} 项</div>
								<div className="flex justify-end">
									<Button onClick={() => setWizardStep("objects")} disabled={!selectedSubjects.length}>
										下一步
									</Button>
								</div>
							</TabsContent>

							<TabsContent value="objects" className="mt-4 space-y-3">
								<div className="flex items-center justify-between">
									<div className="text-sm text-muted-foreground">选择需要授权的数据集</div>
									<div className="text-xs text-muted-foreground">已选：{selectedObjects.length}</div>
								</div>
								<div className="grid grid-cols-2 gap-3 max-h-[300px] overflow-auto rounded-md border p-3">
									{tree.map((dom) => (
										<div key={dom.id} className="space-y-1">
											<div className="text-xs font-medium text-muted-foreground">{dom.name}</div>
											<div className="space-y-1">
												{dom.datasets.map((ds) => {
													const checked = selectedObjects.some((o) => o.datasetId === ds.id);
													return (
														<label key={ds.id} className="flex items-center gap-2 text-sm">
															<Checkbox checked={checked} onCheckedChange={() => toggleSelectObject(ds)} />
															<span className="font-medium">{ds.name}</span>
															<span className="ml-2 text-xs text-muted-foreground">({ds.fields.length})</span>
														</label>
													);
												})}
											</div>
										</div>
									))}
								</div>
								<div className="flex justify-between">
									<Button variant="outline" onClick={() => setWizardStep("subjects")}>
										上一步
									</Button>
									<Button onClick={() => setWizardStep("scope")} disabled={!selectedObjects.length}>
										下一步
									</Button>
								</div>
							</TabsContent>

							<TabsContent value="scope" className="mt-4 space-y-4">
								<SectionHeader icon="solar:shield-check-bold-duotone" title="对象级" />
								<div className="flex items-center gap-2">
									<Select value={scopeObjectEffect} onValueChange={(v) => setScopeObjectEffect(v as any)}>
										<SelectTrigger className="w-[140px]">
											<SelectValue placeholder="选择效果" />
										</SelectTrigger>
										<SelectContent>
											<SelectItem value="ALLOW">ALLOW</SelectItem>
											<SelectItem value="DENY">DENY</SelectItem>
										</SelectContent>
									</Select>
									<Input
										type="date"
										value={validFrom}
										onChange={(e) => setValidFrom(e.target.value)}
										placeholder="生效日期"
									/>
									<Input
										type="date"
										value={validTo}
										onChange={(e) => setValidTo(e.target.value)}
										placeholder="失效日期"
									/>
								</div>

								<SectionHeader icon="solar:list-bold-duotone" title="字段级" />
								<div className="flex items-center gap-2">
									<Input
										placeholder="字段名，逗号分隔，如: customer_name, mobile"
										value={scopeFieldsText}
										onChange={(e) => setScopeFieldsText(e.target.value)}
									/>
									<Select value={scopeFieldsEffect} onValueChange={(v) => setScopeFieldsEffect(v as any)}>
										<SelectTrigger className="w-[140px]">
											<SelectValue placeholder="选择效果" />
										</SelectTrigger>
										<SelectContent>
											<SelectItem value="ALLOW">ALLOW</SelectItem>
											<SelectItem value="DENY">DENY</SelectItem>
										</SelectContent>
									</Select>
								</div>

								<SectionHeader icon="solar:filter-bold-duotone" title="行域条件（DSL 构造器）" />
								<div className="space-y-2">
									<div className="flex flex-wrap gap-2 text-xs">
										<Button variant="outline" size="sm" onClick={() => insertToken("org_id IN (${orgChildren})")}>
											orgChildren
										</Button>
										<Button variant="outline" size="sm" onClick={() => insertToken("project_id = :projectId")}>
											:projectId
										</Button>
										<Button variant="outline" size="sm" onClick={() => insertToken("region = :region")}>
											:region
										</Button>
										<Button variant="outline" size="sm" onClick={() => insertToken("role IN (:roles)")}>
											:roles
										</Button>
									</div>
									<Input value={scopeRowExpr} onChange={(e) => setScopeRowExpr(e.target.value)} />
									<div className="text-xs text-muted-foreground">
										示例：org_id IN ({"$"}
										{"{"}orgChildren{"}"}) AND project_id = :projectId
									</div>
								</div>

								<div className="flex justify-between">
									<Button variant="outline" onClick={() => setWizardStep("objects")}>
										上一步
									</Button>
									<Button
										onClick={doPreview}
										disabled={previewing || (!scopeObjectEffect && !scopeFieldsText && !scopeRowExpr)}
									>
										{previewing ? "预检中…" : "预检冲突"}
									</Button>
								</div>
							</TabsContent>

							<TabsContent value="confirm" className="mt-4 space-y-3">
								<div className="text-sm">冲突检测结果：</div>
								<div className="rounded-md border max-h-[260px] overflow-auto">
									<table className="w-full table-fixed border-collapse text-sm">
										<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
											<tr>
												<th className="px-3 py-2 font-medium w-[100px]">类型</th>
												<th className="px-3 py-2 font-medium">目标</th>
												<th className="px-3 py-2 font-medium">主体</th>
												<th className="px-3 py-2 font-medium">旧策略</th>
												<th className="px-3 py-2 font-medium">新策略</th>
											</tr>
										</thead>
										<tbody>
											{conflicts?.map((c, idx) => (
												<tr key={idx} className="border-b last:border-b-0">
													<td className="px-3 py-2">
														<Badge
															variant="outline"
															className={
																c.kind === "conflict"
																	? "border-red-300 text-red-700"
																	: "border-amber-300 text-amber-700"
															}
														>
															{c.kind === "conflict" ? "冲突" : "覆盖"}
														</Badge>
													</td>
													<td className="px-3 py-2">{c.target}</td>
													<td className="px-3 py-2">{c.subject}</td>
													<td className="px-3 py-2 text-xs text-muted-foreground">{c.old}</td>
													<td className="px-3 py-2 text-xs">{c.next}</td>
												</tr>
											))}
											{!conflicts?.length && (
												<tr>
													<td colSpan={5} className="px-3 py-6 text-center text-xs text-muted-foreground">
														无冲突，全部为新增或一致策略
													</td>
												</tr>
											)}
										</tbody>
									</table>
								</div>
								<div className="flex justify-between">
									<Button variant="outline" onClick={() => setWizardStep("scope")}>
										上一步
									</Button>
									<Button onClick={doSubmit} disabled={submitting}>
										{submitting ? "提交中…" : "提交"}
									</Button>
								</div>
							</TabsContent>
						</Tabs>
					</div>

					<DialogFooter />
				</DialogContent>
			</Dialog>
		</div>
	);
}
