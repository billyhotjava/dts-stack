import { Alert, Button, Card, Empty, Form, Input, Modal, Select, Space, Spin, Tag } from "antd";
import { Archive, CheckCircle2, FilePlus2, RefreshCw, Save, Send } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import type { DatasetField } from "@/api/platformApi";
import {
	archiveIndicator,
	createIndicator,
	getIndicator,
	getIndicatorPublishPreview,
	listIndicators,
	listIndicatorVersions,
	publishIndicator,
	publishIndicatorRevision,
	rollbackIndicatorVersion,
	updateIndicator,
	validateIndicator,
	validateIndicatorDerivation,
} from "@/api/platformApi";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import {
	buildExistingIndicatorMutationPayload,
	buildIndicatorFormChanges,
	buildIndicatorUpsertPayload,
	type IndicatorDefinition,
	parseIndicatorDependencyCodes,
	validateIndicatorDefinition,
} from "../indicatorDefinitionContract";
import {
	type IndicatorPreflightResult,
	publishIndicatorWithPreview,
	resolveIndicatorDetailRequest,
	rollbackIndicatorAndPublish,
	runIndicatorPreflight,
	shouldApplyIndicatorDetailResponse,
} from "../indicatorDefinitionWorkflow";
import { IndicatorDefinitionForm, type IndicatorFormValues } from "./IndicatorDefinitionForm";
import { type IndicatorVersion, IndicatorVersionHistory } from "./IndicatorVersionHistory";

const STATUS_COLORS: Record<string, string> = {
	DRAFT: "default",
	PUBLISHED: "green",
	ARCHIVED: "red",
	DEPRECATED: "orange",
};

const asPageContent = (value: unknown): IndicatorDefinition[] => {
	if (Array.isArray(value)) return value as IndicatorDefinition[];
	const content = (value as { content?: unknown })?.content;
	return Array.isArray(content) ? (content as IndicatorDefinition[]) : [];
};

const parseDimensionCodes = (value: unknown): string[] => {
	if (typeof value !== "string" || !value.trim()) return [];
	try {
		const parsed = JSON.parse(value);
		if (!Array.isArray(parsed)) return [];
		return parsed
			.map((item) => (typeof item === "string" ? item : item?.field))
			.map((item) => String(item ?? "").trim())
			.filter(Boolean);
	} catch {
		return [];
	}
};

const toFormValues = (indicator: IndicatorDefinition): Partial<IndicatorFormValues> => ({
	...indicator,
	dependencyCodes: parseIndicatorDependencyCodes(indicator.dependencyIndicators),
	dimensionCodes: parseDimensionCodes(indicator.dimensionFields),
});

const safeReturnTo = (value: string | null): string | null => {
	const candidate = String(value ?? "").trim();
	return candidate.startsWith("/") && !candidate.startsWith("//") ? candidate : null;
};

export function IndicatorDefinitionPanel() {
	const canManage = useGovernanceManageAccess();
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const [form] = Form.useForm<IndicatorFormValues>();
	const [items, setItems] = useState<IndicatorDefinition[]>([]);
	const [current, setCurrent] = useState<IndicatorDefinition | null>(null);
	const [versions, setVersions] = useState<IndicatorVersion[]>([]);
	const [datasetFields, setDatasetFields] = useState<DatasetField[]>([]);
	const [loading, setLoading] = useState(true);
	const [detailLoading, setDetailLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [statusFilter, setStatusFilter] = useState("");
	const [preflight, setPreflight] = useState<IndicatorPreflightResult | null>(null);
	const actionLockRef = useRef(false);
	const detailRequestSequence = useRef(0);
	const formRevision = useRef(0);
	const editedFieldNamesRef = useRef<Set<string>>(new Set());

	const selectedId = current?.id || "";
	const isDerived = Boolean(Form.useWatch("isDerived", form));
	const dependencyCodes = Form.useWatch("dependencyCodes", form) || [];
	const requestedIndicatorId = searchParams.get("indicatorId");
	const returnTo = safeReturnTo(searchParams.get("returnTo"));

	const dependencyOptions = useMemo(
		() =>
			items
				.filter((item) => item.status === "PUBLISHED" && item.id && item.code && item.id !== selectedId)
				.map((item) => ({ id: String(item.id), code: String(item.code), name: item.name })),
		[items, selectedId],
	);
	const filteredItems = useMemo(() => {
		const normalized = keyword.trim().toLowerCase();
		return items.filter((item) => {
			if (statusFilter && item.status !== statusFilter) return false;
			return !normalized || `${item.code || ""} ${item.name || ""}`.toLowerCase().includes(normalized);
		});
	}, [items, keyword, statusFilter]);

	const updateDeepLink = useCallback(
		(id?: string) => {
			const next = new URLSearchParams(searchParams);
			if (id) next.set("indicatorId", id);
			else next.delete("indicatorId");
			next.set("tab", "owner");
			setSearchParams(next, { replace: true });
		},
		[searchParams, setSearchParams],
	);

	const markEditedFields = useCallback((fieldNames: readonly string[]) => {
		for (const fieldName of fieldNames) editedFieldNamesRef.current.add(fieldName);
		formRevision.current += 1;
		setPreflight(null);
	}, []);

	const applyIndicatorDetail = useCallback(
		(detail: IndicatorDefinition, history: IndicatorVersion[]) => {
			setCurrent(detail);
			setVersions(history);
			form.resetFields();
			form.setFieldsValue(toFormValues(detail));
			editedFieldNamesRef.current.clear();
			formRevision.current += 1;
		},
		[form],
	);

	const openIndicator = useCallback(
		async (id: string, updateLocation = true) => {
			if (editedFieldNamesRef.current.size > 0) {
				if (updateLocation) toast.error("当前指标有未保存修改，请先保存后再切换");
				return;
			}
			const requestSequence = ++detailRequestSequence.current;
			const formRevisionAtRequest = formRevision.current;
			setDetailLoading(true);
			setPreflight(null);
			try {
				const [detailResult, versionResult] = await Promise.all([
					getIndicator(id),
					listIndicatorVersions(id).catch(() => []),
				]);
				if (
					!shouldApplyIndicatorDetailResponse({
						requestSequence,
						activeRequestSequence: detailRequestSequence.current,
						formRevisionAtRequest,
						currentFormRevision: formRevision.current,
					})
				) {
					return;
				}
				const detail = detailResult as IndicatorDefinition;
				const history = Array.isArray(versionResult) ? (versionResult as IndicatorVersion[]) : [];
				applyIndicatorDetail(detail, history);
				if (updateLocation) updateDeepLink(id);
			} catch (error: any) {
				if (requestSequence === detailRequestSequence.current) {
					toast.error(error?.message || "指标详情加载失败");
				}
			} finally {
				if (requestSequence === detailRequestSequence.current) setDetailLoading(false);
			}
		},
		[applyIndicatorDetail, updateDeepLink],
	);

	const loadList = useCallback(async () => {
		setLoading(true);
		try {
			const result = await listIndicators({ page: 0, size: 500 });
			const nextItems = asPageContent(result);
			setItems(nextItems);
		} catch (error: any) {
			setItems([]);
			toast.error(error?.message || "指标列表加载失败");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void loadList();
	}, [loadList]);

	useEffect(() => {
		const detailId = resolveIndicatorDetailRequest(requestedIndicatorId, current?.id ?? null);
		if (detailId) void openIndicator(detailId, false);
	}, [current?.id, openIndicator, requestedIndicatorId]);

	const startCreate = () => {
		detailRequestSequence.current += 1;
		formRevision.current += 1;
		setDetailLoading(false);
		setCurrent(null);
		setVersions([]);
		setDatasetFields([]);
		setPreflight(null);
		form.resetFields();
		form.setFieldsValue({
			status: "DRAFT",
			version: "v1",
			aggregationType: "SUM",
			isDerived: false,
			dependencyCodes: [],
			dimensionCodes: [],
			windowFunction: "NONE",
			timeGrain: "DAY",
			dataLevel: "DATA_INTERNAL",
			dataPrivacy: "INTERNAL",
			precisionScale: 2,
			humanVerified: true,
			llmGenerated: false,
		});
		editedFieldNamesRef.current.clear();
		updateDeepLink();
	};

	const runWriteAction = useCallback(async (action: () => Promise<void>) => {
		if (actionLockRef.current) return;
		actionLockRef.current = true;
		setSaving(true);
		try {
			await action();
		} finally {
			actionLockRef.current = false;
			setSaving(false);
		}
	}, []);

	const applyMutationResult = useCallback(
		async (detail: IndicatorDefinition) => {
			if (!detail.id) throw new Error("指标写入成功但未返回指标 ID");
			const acceptedSequence = ++detailRequestSequence.current;
			setDetailLoading(false);
			applyIndicatorDetail(detail, []);
			const [versionResult] = await Promise.all([listIndicatorVersions(detail.id).catch(() => []), loadList()]);
			if (acceptedSequence === detailRequestSequence.current) {
				setVersions(Array.isArray(versionResult) ? (versionResult as IndicatorVersion[]) : []);
			}
		},
		[applyIndicatorDetail, loadList],
	);

	const preparePayload = async () => {
		await form.validateFields();
		const allValues = form.getFieldsValue(true) as IndicatorFormValues;
		const editedFieldNames = current?.id ? new Set(editedFieldNamesRef.current) : new Set(Object.keys(allValues));
		const changes = buildIndicatorFormChanges(allValues, editedFieldNames, datasetFields);
		const [latest, versionResult] = current?.id
			? await Promise.all([
					getIndicator(current.id) as Promise<IndicatorDefinition>,
					listIndicatorVersions(current.id).catch(() => []),
				])
			: [{}, []];
		const knownVersions = (Array.isArray(versionResult) ? versionResult : [])
			.map((item: IndicatorVersion) => item?.version)
			.filter(Boolean);
		const payload = current?.id
			? buildExistingIndicatorMutationPayload(current, latest, changes, knownVersions)
			: buildIndicatorUpsertPayload({ status: "DRAFT", version: "v1", isDerived: false }, changes, knownVersions);
		const localIssues = validateIndicatorDefinition({
			...payload,
			dependencyCodes: parseIndicatorDependencyCodes(payload.dependencyIndicators),
		});
		if (localIssues.length) throw new Error(localIssues[0]);
		return payload;
	};

	const saveDefinition = async (notify = true): Promise<IndicatorDefinition> => {
		if (current?.status === "PUBLISHED") {
			throw new Error("已发布指标不能覆盖保存；请使用“发布新版本”原子更新");
		}
		const wasExisting = Boolean(current?.id);
		const payload = await preparePayload();
		const saved = (
			current?.id ? await updateIndicator(current.id, payload) : await createIndicator(payload)
		) as IndicatorDefinition;
		if (!saved.id) throw new Error("指标保存成功但未返回指标 ID");
		await applyMutationResult(saved);
		updateDeepLink(saved.id);
		if (notify) toast.success(wasExisting ? "指标草稿已保存" : "指标草稿已创建");
		return saved;
	};

	const validateSavedDefinition = async (indicator: IndicatorDefinition) => {
		const result = await runIndicatorPreflight(indicator, {
			validateAtomic: async (id) => (await validateIndicator(id)) as any,
			validateDerivation: async (id) => (await validateIndicatorDerivation(id)) as IndicatorPreflightResult,
		});
		setPreflight(result);
		if (!result.valid) throw new Error(result.issues[0]?.message || result.message || "指标校验未通过");
		return result;
	};

	const handleValidate = async () => {
		await runWriteAction(async () => {
			try {
				const target = current?.status === "PUBLISHED" ? current : await saveDefinition(false);
				if (!target) throw new Error("请先选择或创建指标");
				await validateSavedDefinition(target);
				toast.success(
					current?.status === "PUBLISHED"
						? "当前已发布版本校验通过；表单修改将在发布新版本时原子校验"
						: target.isDerived
							? "派生表达式与依赖校验通过"
							: "原子指标计算 SQL 校验通过",
				);
			} catch (error: any) {
				toast.error(error?.message || "指标校验失败");
			}
		});
	};

	const handlePublish = async () => {
		await runWriteAction(async () => {
			try {
				if (current?.status === "PUBLISHED") {
					if (!current.id) throw new Error("指标缺少 ID，无法发布新版本");
					const payload = await preparePayload();
					const published = (await publishIndicatorRevision(current.id, payload)) as IndicatorDefinition;
					toast.success(`指标 ${published.version || ""} 新版本已原子发布`);
					await applyMutationResult(published);
					return;
				}
				const saved = await saveDefinition(false);
				await validateSavedDefinition(saved);
				const savedId = saved.id;
				if (!savedId) throw new Error("指标保存成功但未返回指标 ID");
				const published = (await publishIndicatorWithPreview(savedId, {
					getPublishPreview: async (id) => (await getIndicatorPublishPreview(id)) as any,
					publish: publishIndicator,
				})) as IndicatorDefinition;
				toast.success(`指标 ${published.version || ""} 已发布`);
				await applyMutationResult(published);
			} catch (error: any) {
				toast.error(error?.message || "指标发布失败");
			}
		});
	};

	const handleArchive = () => {
		const currentId = current?.id;
		if (!currentId) return;
		Modal.confirm({
			title: "确认归档指标",
			content: "归档会生成并保留不可变版本快照，不覆盖已有发布历史。",
			okText: "归档",
			okType: "danger",
			cancelText: "取消",
			onOk: async () => {
				await runWriteAction(async () => {
					try {
						const archived = (await archiveIndicator(currentId)) as IndicatorDefinition;
						toast.success("指标已归档");
						await applyMutationResult(archived);
					} catch (error: any) {
						toast.error(error?.message || "指标归档失败");
					}
				});
			},
		});
	};

	const handleRollback = (version: string) => {
		const currentId = current?.id;
		if (!currentId) return;
		Modal.confirm({
			title: `回滚到 ${version}`,
			content: "系统会基于该快照生成新版本并原子发布；失败时当前发布版本保持不变。",
			okText: "回滚并发布",
			cancelText: "取消",
			onOk: async () => {
				await runWriteAction(async () => {
					try {
						const rolledBack = await rollbackIndicatorAndPublish(currentId, version, `指标工作台回滚至 ${version}`, {
							rollback: async (id, targetVersion, data) =>
								(await rollbackIndicatorVersion(id, targetVersion, data)) as {
									published?: boolean;
									indicator?: IndicatorDefinition;
								},
						});
						toast.success(`已回滚至 ${version} 并发布为 ${rolledBack.version || "新版本"}`);
						await applyMutationResult(rolledBack);
					} catch (error: any) {
						toast.error(error?.message || "指标版本回滚发布失败");
					}
				});
			},
		});
	};

	const handleDerivedChange = (checked: boolean) => {
		markEditedFields(["isDerived", "aggregationType", "dependencyCodes"]);
		if (checked) {
			form.setFieldsValue({ aggregationType: "DERIVED", dependencyCodes: [] });
			return;
		}
		form.setFieldsValue({
			aggregationType:
				form.getFieldValue("aggregationType") === "DERIVED" ? "SUM" : form.getFieldValue("aggregationType"),
			dependencyCodes: [],
		});
	};

	const insertDependencyToken = (code: string) => {
		const currentExpression = String(form.getFieldValue("expressionSql") || "");
		form.setFieldValue("expressionSql", `${currentExpression}${currentExpression ? " " : ""}{{metric:${code}}}`);
		markEditedFields(["expressionSql"]);
	};

	return (
		<section className="mt-5 rounded-lg border border-blue-100 bg-blue-50/30 p-4" data-testid="indicator-owner-panel">
			<div className="mb-4 flex flex-wrap items-start justify-between gap-3">
				<div>
					<div className="text-base font-semibold text-gray-900">指标定义与发布</div>
					<div className="mt-1 text-sm text-gray-500">
						治理指标是唯一真源；在此维护原子/派生口径、依赖引用、校验、发布与不可变版本。
					</div>
				</div>
				<Space wrap>
					{returnTo ? <Button onClick={() => navigate(returnTo)}>返回来源页面</Button> : null}
					<Button icon={<RefreshCw size={15} />} onClick={() => void loadList()} loading={loading}>
						刷新指标
					</Button>
					<Button icon={<FilePlus2 size={15} />} onClick={startCreate} disabled={!canManage || saving}>
						新建指标
					</Button>
				</Space>
			</div>

			<div className="grid gap-4 xl:grid-cols-[300px_minmax(0,1fr)]">
				<Card size="small" title={`指标目录（${filteredItems.length}）`}>
					<Space direction="vertical" className="w-full" size={8}>
						<Input.Search
							value={keyword}
							onChange={(event) => setKeyword(event.target.value)}
							allowClear
							placeholder="搜索编码或名称"
						/>
						<Select
							className="w-full"
							allowClear
							value={statusFilter || undefined}
							onChange={(value) => setStatusFilter(value || "")}
							placeholder="全部状态"
							options={["DRAFT", "PUBLISHED", "ARCHIVED", "DEPRECATED"].map((value) => ({
								value,
								label: value,
							}))}
						/>
					</Space>
					<div className="mt-3 max-h-[760px] space-y-2 overflow-auto">
						{loading ? <Spin className="my-8 flex justify-center" /> : null}
						{!loading && !filteredItems.length ? (
							<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无指标定义" />
						) : null}
						{filteredItems.map((item) => (
							<button
								key={item.id}
								type="button"
								disabled={saving}
								onClick={() => item.id && item.id !== selectedId && void openIndicator(item.id)}
								className={`w-full rounded-md border p-3 text-left ${item.id === selectedId ? "border-blue-500 bg-blue-50" : "border-gray-200 bg-white hover:border-blue-300"}`}
							>
								<div className="flex items-center justify-between gap-2">
									<span className="truncate font-medium text-gray-900">{item.name || item.code}</span>
									<Tag color={STATUS_COLORS[String(item.status || "")]}>{item.status || "UNKNOWN"}</Tag>
								</div>
								<div className="mt-1 truncate text-xs text-gray-500">
									{item.code || "未设置编码"} · {item.version || "未标记版本"} · {item.isDerived ? "派生" : "原子"}
								</div>
							</button>
						))}
					</div>
				</Card>

				<Card
					size="small"
					title={current ? `${current.name || current.code} · ${current.version || "未标记版本"}` : "新建指标草稿"}
					extra={
						current ? (
							<Tag color={STATUS_COLORS[String(current.status || "")]}>{current.status || "UNKNOWN"}</Tag>
						) : null
					}
				>
					<Spin spinning={detailLoading}>
						{current?.status === "PUBLISHED" ? (
							<Alert
								className="mb-4"
								showIcon
								type="info"
								message="当前发布版本持续在线"
								description="普通草稿保存已禁用；表单修改只会通过“发布新版本”在后端完成原子预检与切换，失败时当前版本保持不变。"
							/>
						) : null}
						<Form
							form={form}
							layout="vertical"
							disabled={!canManage || saving}
							onValuesChange={(changedValues) => {
								markEditedFields(Object.keys(changedValues));
							}}
						>
							<IndicatorDefinitionForm
								currentId={current?.id}
								isDerived={isDerived}
								dependencyCodes={dependencyCodes}
								dependencyOptions={dependencyOptions}
								datasetFields={datasetFields}
								onDatasetFieldsLoaded={setDatasetFields}
								onDerivedChange={handleDerivedChange}
								onInsertDependencyToken={insertDependencyToken}
							/>
						</Form>

						{preflight ? (
							<Alert
								className="mt-4"
								showIcon
								type={preflight.valid ? "success" : "error"}
								message={preflight.valid ? "发布前校验通过" : "发布前校验未通过"}
								description={
									preflight.valid
										? preflight.compiledExpression || preflight.message || "计算规则可执行"
										: preflight.issues.map((item) => `${item.code}: ${item.message}`).join("；")
								}
							/>
						) : null}

						<div className="mt-4 flex flex-wrap items-center justify-between gap-3">
							<Space wrap>
								<Button
									type="primary"
									icon={<Save size={15} />}
									loading={saving}
									disabled={!canManage || saving || current?.status === "PUBLISHED"}
									onClick={() =>
										void runWriteAction(async () => {
											try {
												await saveDefinition();
											} catch (error: any) {
												toast.error(error?.message || "保存失败");
											}
										})
									}
								>
									保存草稿
								</Button>
								<Button
									icon={<CheckCircle2 size={15} />}
									loading={saving}
									disabled={!canManage || saving}
									onClick={() => void handleValidate()}
								>
									{current?.status === "PUBLISHED" ? "校验当前版本" : "保存并校验"}
								</Button>
								<Button
									type="primary"
									icon={<Send size={15} />}
									loading={saving}
									disabled={!canManage || saving}
									onClick={() => void handlePublish()}
								>
									{current?.status === "PUBLISHED" ? "发布新版本" : "预检并发布"}
								</Button>
							</Space>
							{current?.id && current.status !== "ARCHIVED" ? (
								<Button danger icon={<Archive size={15} />} disabled={!canManage || saving} onClick={handleArchive}>
									归档
								</Button>
							) : null}
						</div>

						{current?.id ? (
							<IndicatorVersionHistory
								currentVersion={current.version}
								versions={versions}
								canManage={canManage}
								saving={saving}
								onRollback={handleRollback}
							/>
						) : null}
					</Spin>
				</Card>
			</div>
		</section>
	);
}
