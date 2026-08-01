import { Alert, Button, Card, Form, Input, Radio, Select, Space, Switch } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import {
	createQualityRule,
	listQualityRules,
	listQualityTemplates,
	previewTemplateSQL,
	updateQualityRule,
} from "@/api/platformApi";
import { ManagePermissionHint, QualityEmpty, QualityPageHeading } from "./QualityShared";
import { buildQualityRulePayload, qualityPath } from "./qualityRoutes";
import { type QualityRule, type QualityTemplate, toList } from "./qualityTypes";
import { bindTemplateTargetTable } from "./templateBindings";
import { useDefaultLakeDatasets } from "./useDefaultLakeDatasets";
import { useQualityMaintainerAccess } from "./useQualityAccess";

type RuleForm = {
	name: string;
	code?: string;
	type: string;
	severity: string;
	datasetId?: string;
	category?: string;
	description?: string;
	owner?: string;
	dataLevel?: string;
	executor?: string;
	frequencyCron?: string;
	enabled: boolean;
	publishNow: boolean;
	sql?: string;
	templateParams?: string;
};

type LoadedRuleIdentity = {
	ruleId: string;
	version: number;
	versionId: string;
};

type LoadedTemplateIdentity = {
	templateId: string;
	sequence: number;
};

const TYPE_OPTIONS = ["COMPLETENESS", "CONSISTENCY", "ACCURACY", "UNIQUENESS", "TIMELINESS", "VALIDITY"].map(
	(value) => ({ value, label: value }),
);

export function RuleEditorPage() {
	const { ruleId } = useParams();
	const [searchParams] = useSearchParams();
	const linkedDatasetId = searchParams.get("datasetId") || undefined;
	const linkedTemplateId = searchParams.get("templateId") || undefined;
	const navigate = useNavigate();
	const canManage = useQualityMaintainerAccess();
	const { datasets, loading: datasetsLoading, message, lakeName } = useDefaultLakeDatasets();
	const [form] = Form.useForm<RuleForm>();
	const [saving, setSaving] = useState(false);
	const [loading, setLoading] = useState(Boolean(ruleId));
	const [loadError, setLoadError] = useState("");
	const [selectedTemplate, setSelectedTemplate] = useState<QualityTemplate>();
	const [originalRule, setOriginalRule] = useState<QualityRule>();
	const [previewSql, setPreviewSql] = useState("");
	const [previewing, setPreviewing] = useState(false);
	const loadSequence = useRef(0);
	const activeRuleId = useRef(ruleId);
	const loadedRuleIdentity = useRef<LoadedRuleIdentity>();
	const templateLoadSequence = useRef(0);
	const activeTemplateId = useRef(linkedTemplateId);
	const loadedTemplateIdentity = useRef<LoadedTemplateIdentity>();
	activeRuleId.current = ruleId;
	activeTemplateId.current = linkedTemplateId;
	const isEditing = Boolean(ruleId);
	const isLoadedRuleCurrent = (operationRuleId: string, operationRule: QualityRule | undefined) => {
		const loadedIdentity = loadedRuleIdentity.current;
		return Boolean(
			operationRule &&
				activeRuleId.current === operationRuleId &&
				String(operationRule.id) === operationRuleId &&
				(!operationRule.latestVersion?.ruleId || String(operationRule.latestVersion.ruleId) === operationRuleId) &&
				loadedIdentity?.ruleId === operationRuleId &&
				loadedIdentity.version === Number(operationRule.latestVersion?.version) &&
				loadedIdentity.versionId === String(operationRule.latestVersion?.id || ""),
		);
	};
	const isTemplateSelectionCurrent = (
		operationTemplateId: string | undefined,
		operationTemplate: QualityTemplate | undefined,
	) => {
		const loadedIdentity = loadedTemplateIdentity.current;
		if (activeTemplateId.current !== operationTemplateId) return false;
		if (!operationTemplateId) return !operationTemplate && !loadedIdentity;
		return Boolean(
			!activeRuleId.current &&
				operationTemplate &&
				String(operationTemplate.id) === operationTemplateId &&
				loadedIdentity?.templateId === operationTemplateId &&
				loadedIdentity.sequence === templateLoadSequence.current,
		);
	};
	const currentTemplate = isTemplateSelectionCurrent(linkedTemplateId, selectedTemplate) ? selectedTemplate : undefined;

	useEffect(() => {
		const requestedRuleId = ruleId;
		const sequence = ++loadSequence.current;
		loadedRuleIdentity.current = undefined;
		setOriginalRule(undefined);
		setLoadError("");
		setPreviewSql("");
		form.resetFields();
		if (!requestedRuleId) {
			setLoading(false);
			form.setFieldsValue({
				type: "COMPLETENESS",
				severity: "MEDIUM",
				enabled: true,
				publishNow: true,
				datasetId: linkedDatasetId,
				templateParams: "{}",
			});
			return;
		}
		setLoading(true);
		setLoadError("");
		void listQualityRules()
			.then((response) => {
				if (sequence !== loadSequence.current || activeRuleId.current !== requestedRuleId) return;
				const rule = toList<QualityRule>(response).find((item) => String(item.id) === requestedRuleId);
				if (!rule) throw new Error("未找到需要编辑的规则");
				const version = Number(rule.latestVersion?.version);
				if (
					!Number.isSafeInteger(version) ||
					version <= 0 ||
					(rule.latestVersion?.ruleId && String(rule.latestVersion.ruleId) !== requestedRuleId)
				) {
					throw new Error("规则当前版本信息不完整，无法安全编辑");
				}
				loadedRuleIdentity.current = {
					ruleId: requestedRuleId,
					version,
					versionId: String(rule.latestVersion?.id || ""),
				};
				setOriginalRule(rule);
				let sql = "";
				try {
					const definition =
						typeof rule.latestVersion?.definition === "string"
							? JSON.parse(rule.latestVersion.definition)
							: rule.definition;
					sql = String((definition as { sql?: string })?.sql || "");
				} catch {
					sql = String(rule.latestVersion?.definition || "");
				}
				form.setFieldsValue({
					name: rule.name || "",
					code: rule.code,
					type: rule.type || "COMPLETENESS",
					severity: rule.severity || "MEDIUM",
					datasetId: rule.datasetId,
					category: rule.category,
					description: rule.description,
					owner: rule.owner,
					dataLevel: rule.dataLevel,
					executor: rule.executor,
					frequencyCron: rule.frequencyCron,
					enabled: rule.enabled !== false,
					publishNow: true,
					sql,
				});
			})
			.catch((error) => {
				if (sequence !== loadSequence.current || activeRuleId.current !== requestedRuleId) return;
				const message = error instanceof Error ? error.message : "规则加载失败";
				loadedRuleIdentity.current = undefined;
				setOriginalRule(undefined);
				setLoadError(message);
				toast.error(message);
			})
			.finally(() => {
				if (sequence === loadSequence.current && activeRuleId.current === requestedRuleId) setLoading(false);
			});
	}, [form, linkedDatasetId, ruleId]);

	useEffect(() => {
		const requestedTemplateId = linkedTemplateId;
		const requestedRuleId = ruleId;
		const sequence = ++templateLoadSequence.current;
		loadedTemplateIdentity.current = undefined;
		setSelectedTemplate(undefined);
		setPreviewSql("");
		setPreviewing(false);
		setLoadError("");
		if (!requestedTemplateId || requestedRuleId) return;
		void listQualityTemplates()
			.then((response) => {
				if (
					sequence !== templateLoadSequence.current ||
					activeTemplateId.current !== requestedTemplateId ||
					activeRuleId.current !== requestedRuleId
				) {
					return;
				}
				const template = toList<QualityTemplate>(response).find((item) => String(item.id) === requestedTemplateId);
				if (!template) throw new Error("未找到所选质量规则模板");
				loadedTemplateIdentity.current = { templateId: requestedTemplateId, sequence };
				setSelectedTemplate(template);
				form.setFieldsValue({
					name: template.name ? `${template.name}规则` : "",
					severity: template.severityDefault || "MEDIUM",
					category: template.category,
					description: template.description,
				});
			})
			.catch((error) => {
				if (
					sequence !== templateLoadSequence.current ||
					activeTemplateId.current !== requestedTemplateId ||
					activeRuleId.current !== requestedRuleId
				) {
					return;
				}
				loadedTemplateIdentity.current = undefined;
				const message = error instanceof Error ? error.message : "规则模板加载失败";
				setLoadError(message);
				toast.error(message);
			});
	}, [form, linkedTemplateId, ruleId]);

	const datasetOptions = useMemo(
		() =>
			datasets.map((item) => ({
				value: item.id,
				label: `${item.name}${item.schemaName ? ` · ${item.schemaName}` : ""} · ${item.domainName || "未归属业务域"}`,
			})),
		[datasets],
	);
	const selectedDatasetId = Form.useWatch("datasetId", form);
	const selectedDataset = datasets.find((item) => item.id === selectedDatasetId);

	const renderTemplate = async (template: QualityTemplate, paramsText: string, datasetId?: string) => {
		const dataset = datasets.find((item) => item.id === datasetId);
		if (!dataset) throw new Error("请先选择默认数据湖资产");
		const params = bindTemplateTargetTable(JSON.parse(paramsText || "{}"), dataset, template.paramSchema);
		const result = await previewTemplateSQL(template.id, params);
		const sql = typeof result === "string" ? result : String((result as { sql?: string })?.sql || "");
		if (!sql.trim()) throw new Error("模板未生成可执行检测 SQL");
		return sql.trim();
	};

	const preview = async () => {
		const operationTemplateId = linkedTemplateId;
		const operationTemplate = selectedTemplate;
		if (
			!operationTemplateId ||
			!operationTemplate ||
			!isTemplateSelectionCurrent(operationTemplateId, operationTemplate)
		) {
			toast.error("规则模板尚未安全加载，请刷新后重试");
			return;
		}
		try {
			setPreviewing(true);
			const values = await form.validateFields(["datasetId", "templateParams"]);
			if (!isTemplateSelectionCurrent(operationTemplateId, operationTemplate)) return;
			const sql = await renderTemplate(operationTemplate, values.templateParams || "{}", values.datasetId);
			if (!isTemplateSelectionCurrent(operationTemplateId, operationTemplate)) return;
			setPreviewSql(sql);
		} catch (error) {
			if (!isTemplateSelectionCurrent(operationTemplateId, operationTemplate)) return;
			toast.error(
				error instanceof SyntaxError
					? "模板参数必须是有效 JSON"
					: error instanceof Error
						? error.message
						: "模板预览失败",
			);
		} finally {
			if (isTemplateSelectionCurrent(operationTemplateId, operationTemplate)) setPreviewing(false);
		}
	};

	const save = async () => {
		const operationRuleId = ruleId;
		const operationRule = originalRule;
		const operationTemplateId = linkedTemplateId;
		const operationTemplate = selectedTemplate;
		if (!canManage || loadError || loading) return;
		if (operationRuleId && !isLoadedRuleCurrent(operationRuleId, operationRule)) {
			toast.error("规则或版本尚未安全加载，请刷新后重试");
			return;
		}
		if (!isTemplateSelectionCurrent(operationTemplateId, operationTemplate)) {
			toast.error("规则模板尚未安全加载，请刷新后重试");
			return;
		}
		try {
			const values = await form.validateFields();
			if (
				activeRuleId.current !== operationRuleId ||
				(operationRuleId && !isLoadedRuleCurrent(operationRuleId, operationRule)) ||
				!isTemplateSelectionCurrent(operationTemplateId, operationTemplate)
			) {
				throw new Error("规则、版本或模板已切换，请重新确认后保存");
			}
			setSaving(true);
			let renderedSql = String(values.sql || "").trim();
			if (operationTemplateId) {
				if (!operationTemplate) throw new Error("规则模板尚未安全加载，请刷新后重试");
				renderedSql = await renderTemplate(operationTemplate, values.templateParams || "{}", values.datasetId);
			}
			if (
				activeRuleId.current !== operationRuleId ||
				(operationRuleId && !isLoadedRuleCurrent(operationRuleId, operationRule)) ||
				!isTemplateSelectionCurrent(operationTemplateId, operationTemplate)
			) {
				throw new Error("规则、版本或模板已切换，请重新确认后保存");
			}
			const payload = buildQualityRulePayload(
				{
					name: values.name.trim(),
					code: values.code?.trim() || undefined,
					type: values.type,
					severity: values.severity,
					datasetId: values.datasetId,
					category: values.category?.trim() || undefined,
					description: values.description?.trim() || undefined,
					owner: values.owner?.trim() || undefined,
					dataLevel: values.dataLevel?.trim() || undefined,
					executor: values.executor?.trim() || undefined,
					frequencyCron: values.frequencyCron?.trim() || undefined,
					template: operationRule?.template ?? false,
					enabled: values.enabled,
					publishNow: values.publishNow,
					definition: { sql: renderedSql },
				},
				operationRule,
			);
			if (operationRuleId) await updateQualityRule(operationRuleId, payload);
			else await createQualityRule(payload);
			if (
				activeRuleId.current !== operationRuleId ||
				!isTemplateSelectionCurrent(operationTemplateId, operationTemplate)
			)
				return;
			toast.success(operationRuleId ? "规则已更新并保存新版本" : "规则已创建");
			navigate(operationRuleId ? qualityPath("rule-detail", { ruleId: operationRuleId }) : qualityPath("rule-list"));
		} catch (error) {
			if ((error as { errorFields?: unknown })?.errorFields) return;
			if (
				activeRuleId.current !== operationRuleId ||
				!isTemplateSelectionCurrent(operationTemplateId, operationTemplate)
			)
				return;
			toast.error(error instanceof Error ? error.message : "规则保存失败");
		} finally {
			setSaving(false);
		}
	};

	if (!loading && (loadError || (isEditing && !originalRule))) {
		return <QualityEmpty description={loadError || "未找到需要编辑的质量规则"} />;
	}

	return (
		<div className="dq-page">
			<QualityPageHeading
				title={isEditing ? "编辑质量规则" : "新建质量规则"}
				description={
					currentTemplate
						? "从模板渲染单资产规则；保存时同步写入 definition.sql、datasetId 与版本 bindings。"
						: "规则只允许绑定默认数据湖资产；保存时同步写入版本 bindings，发布后才可执行。"
				}
				actions={<ManagePermissionHint canManage={canManage} />}
			/>
			{message ? <Alert showIcon type="warning" message="默认数据湖资产不可用" description={message} /> : null}
			<Card loading={loading} title="规则定义">
				<Form form={form} layout="vertical" style={{ maxWidth: 860 }}>
					<Space align="start" size={16} wrap style={{ width: "100%" }}>
						<Form.Item
							name="name"
							label="规则名称"
							rules={[{ required: true, message: "请输入规则名称" }]}
							style={{ width: 360 }}
						>
							<Input placeholder="例如：订单主键完整性" />
						</Form.Item>
						<Form.Item name="code" label="规则编码" style={{ width: 260 }}>
							<Input placeholder="可选，例如 order_pk_not_null" />
						</Form.Item>
					</Space>
					<Space align="start" size={16} wrap style={{ width: "100%" }}>
						<Form.Item name="type" label="质量维度" rules={[{ required: true }]} style={{ width: 220 }}>
							<Select options={TYPE_OPTIONS} />
						</Form.Item>
						<Form.Item name="severity" label="严重性" rules={[{ required: true }]} style={{ width: 220 }}>
							<Select options={["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((value) => ({ value, label: value }))} />
						</Form.Item>
						<Form.Item name="dataLevel" label="数据分层" style={{ width: 220 }}>
							<Input placeholder="可选，例如 D2" />
						</Form.Item>
					</Space>
					<Form.Item name="description" label="规则说明">
						<Input.TextArea rows={2} />
					</Form.Item>
					<Space align="start" size={16} wrap style={{ width: "100%" }}>
						<Form.Item name="category" label="规则分类" style={{ width: 200 }}>
							<Input />
						</Form.Item>
						<Form.Item name="owner" label="负责人" style={{ width: 200 }}>
							<Input />
						</Form.Item>
						<Form.Item name="executor" label="执行器" style={{ width: 200 }}>
							<Input placeholder="留空使用系统默认" />
						</Form.Item>
						<Form.Item name="frequencyCron" label="调度表达式" style={{ width: 200 }}>
							<Input placeholder="可选 Cron" />
						</Form.Item>
					</Space>
					<Form.Item
						name="datasetId"
						label={`检测对象（${lakeName}）`}
						rules={[{ required: true, message: "请选择默认数据湖资产" }]}
					>
						<Select
							showSearch
							optionFilterProp="label"
							loading={datasetsLoading}
							disabled={Boolean(message)}
							options={datasetOptions}
							placeholder="选择数据资产"
							onChange={() => setPreviewSql("")}
						/>
					</Form.Item>
					{currentTemplate ? (
						<Card
							size="small"
							title={`模板：${currentTemplate.name || currentTemplate.code || currentTemplate.id}`}
							style={{ marginBottom: 16 }}
						>
							<Space direction="vertical" size={12} style={{ width: "100%" }}>
								<div className="dq-muted">{currentTemplate.description || "请按参数定义填写模板参数。"}</div>
								<Alert
									showIcon
									type="info"
									message={`目标表自动绑定：${selectedDataset?.hiveTable || selectedDataset?.tableName || "请先选择数据资产"}`}
									description="模板参数中的目标表 table 会由所选数据资产强制覆盖，不能指向其他资产。"
								/>
								<pre className="dq-code-block">
									{JSON.stringify(currentTemplate.paramSchema || { params: [] }, null, 2)}
								</pre>
								<Form.Item
									name="templateParams"
									label="模板参数（JSON）"
									rules={[{ required: true, message: "请输入模板参数" }]}
									style={{ marginBottom: 0 }}
								>
									<Input.TextArea rows={6} className="dq-code-block" onChange={() => setPreviewSql("")} />
								</Form.Item>
								<Button loading={previewing} disabled={!canManage} onClick={() => void preview()}>
									预览渲染 SQL
								</Button>
								{previewSql ? <pre className="dq-code-block">{previewSql}</pre> : null}
							</Space>
						</Card>
					) : (
						<Form.Item
							name="sql"
							label="检测 SQL"
							rules={[{ required: true, message: "请输入检测 SQL" }]}
							extra="SQL 应返回不符合规则的记录；物理表必须使用 schema.table 完整限定，只允许只读检查语句和内置模板所需函数。"
						>
							<Input.TextArea
								rows={10}
								placeholder="SELECT * FROM table_name WHERE column_name IS NULL"
								className="dq-code-block"
							/>
						</Form.Item>
					)}
					<Space size={24}>
						<Form.Item name="enabled" label="启用规则" valuePropName="checked">
							<Switch />
						</Form.Item>
						<Form.Item name="publishNow" label="保存后发布">
							<Radio.Group
								options={[
									{ label: "发布", value: true },
									{ label: "仅草稿", value: false },
								]}
							/>
						</Form.Item>
					</Space>
					<Space>
						<Button
							onClick={() => navigate(ruleId ? qualityPath("rule-detail", { ruleId }) : qualityPath("rule-list"))}
						>
							取消
						</Button>
						<Button
							type="primary"
							loading={saving}
							disabled={
								!canManage ||
								Boolean(message) ||
								loading ||
								Boolean(loadError) ||
								(isEditing && !originalRule) ||
								!isTemplateSelectionCurrent(linkedTemplateId, selectedTemplate)
							}
							onClick={() => void save()}
						>
							保存规则
						</Button>
					</Space>
				</Form>
			</Card>
		</div>
	);
}
