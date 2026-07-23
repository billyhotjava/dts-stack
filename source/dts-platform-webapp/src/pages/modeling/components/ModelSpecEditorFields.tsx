import type { FormInstance } from "antd";
import { Alert, Button, Col, Collapse, Form, Input, Radio, Row, Select, Space, Typography } from "antd";
import { Plus, Trash2 } from "lucide-react";
import {
	type ModelSpecSourceChoice,
	modelSpecSourceDraftFromChoice,
	modelSpecSourceMatchesChoice,
} from "../modelSpecSourceSelection";
import { nextDimensionHierarchyCode } from "../modelSpecSystemCode";
import { MODEL_SPEC_TARGET_LAYER_BY_TYPE, type ModelSpecLayer, type ModelSpecType } from "../modelSpecV2Contract";
import type { ModelSpecDraft } from "../modelSpecWorkbench";
import {
	adoptCurrentUpstreamRevisions,
	MODEL_TYPE_DESCRIPTIONS,
	MODEL_TYPE_LABELS,
	modelSpecEditorCopy,
} from "../modelSpecWorkbench";
import { buildWarehousePlanRoute } from "../warehousePlanViewModel";

const { Text } = Typography;

export type ModelSpecSelectOption = {
	value: string;
	label: string;
	disabled?: boolean;
	revision?: number;
};

type Props = {
	form: FormInstance<ModelSpecDraft>;
	planOptions: ModelSpecSelectOption[];
	domainOptions: ModelSpecSelectOption[];
	upstreamOptions: ModelSpecSelectOption[];
	dimensionOptions: ModelSpecSelectOption[];
	sourceOptions: ModelSpecSourceChoice[];
	planLoading?: boolean;
	domainLoading?: boolean;
	sourceLoading?: boolean;
	upstreamValidationAvailable?: boolean;
	sourceError?: string;
	sourcePermissionDenied?: boolean;
	lockPlan?: boolean;
	lockDomain?: boolean;
	lockModelType?: boolean;
	readOnly?: boolean;
	onPlanChange?: (planId: string) => void;
	onModelTypeChange?: (modelType: ModelSpecType) => void;
	onReloadSources?: () => void;
	onManageSources?: () => void;
};

const layerOptions: ModelSpecSelectOption[] = ["ODS", "STG", "DWD"].map((value) => ({
	value,
	label: value,
}));

const modelTypeOptions = (Object.keys(MODEL_TYPE_LABELS) as ModelSpecType[]).map((value) => ({
	value,
	label: MODEL_TYPE_LABELS[value],
}));

const MODEL_TYPE_LIFECYCLE_DEPENDENCIES: Record<ModelSpecType, string> = {
	DIMENSION:
		"草稿：填写维度定义、粒度和稳定键。实现：补齐维度编码、SCD、复用范围，并选择已确认物理来源或生成策略。发布：完成字段标准、质量、权限、构建与测试证据。",
	FACT: "草稿：填写模型名称、粒度和粒度键，上游输入可后补。实现：明确事实形态和业务时间，并至少选择已确认物理来源或锁定版本的上游模型。发布：完成字段标准、质量、权限、构建与测试证据。",
	SUMMARY:
		"草稿：填写输出粒度，并锁定至少一个上游模型版本。实现：声明汇总字段或指标，且上游版本保持有效。发布：完成字段标准、质量、权限、构建与测试证据。",
	APPLICATION:
		"草稿：填写输出粒度和消费场景，并锁定至少一个上游模型版本。实现：声明输出字段契约，且上游版本保持有效。发布：完成字段标准、质量、权限、构建与测试证据。",
};

const sourceKindLabels: Record<ModelSpecSourceChoice["kind"], string> = {
	TABLE: "数据表",
	DBT_MODEL: "dbt 模型",
	DATASET: "数据集",
};

const grainLabel = (modelType: ModelSpecType) => {
	if (modelType === "FACT") return "粒度声明";
	if (modelType === "DIMENSION") return "维度粒度";
	return "输出粒度";
};

export function ModelSpecEditorFields({
	form,
	planOptions,
	domainOptions,
	upstreamOptions,
	dimensionOptions,
	sourceOptions,
	planLoading = false,
	domainLoading = false,
	sourceLoading = false,
	upstreamValidationAvailable = true,
	sourceError = "",
	sourcePermissionDenied = false,
	lockPlan = false,
	lockDomain = false,
	lockModelType = false,
	readOnly = false,
	onPlanChange,
	onModelTypeChange,
	onReloadSources,
	onManageSources,
}: Props) {
	const modelType = (Form.useWatch("modelType", form) || "FACT") as ModelSpecType;
	const selectedPlanId = Form.useWatch("planId", form);
	const dimensionScdType = Form.useWatch("dimensionScdType", form);
	const editorCopy = modelSpecEditorCopy(modelType);
	const targetLayer = MODEL_SPEC_TARGET_LAYER_BY_TYPE[modelType];
	const validateUpstreamSelection = async (_: unknown, selectedIds?: string[]) => {
		if (!upstreamValidationAvailable) return;
		if (!Array.isArray(selectedIds) || selectedIds.length === 0) return;
		const optionById = new Map(upstreamOptions.map((option) => [option.value, option]));
		if (selectedIds.some((id) => optionById.get(id)?.disabled !== false)) {
			throw new Error("存在不符合当前模型类别依赖规则的上游模型，请移除后重新选择");
		}
	};
	const adoptCurrentUpstreamRevision = (modelSpecId: string) => {
		const existingPins = (form.getFieldValue("existingUpstreamPins") || []) as ModelSpecDraft["existingUpstreamPins"];
		form.setFieldValue("existingUpstreamPins", adoptCurrentUpstreamRevisions(existingPins, [modelSpecId]));
	};
	const sourceInventoryPath = selectedPlanId
		? buildWarehousePlanRoute(selectedPlanId, "baseline", { tab: "sources" })
		: "";

	return (
		<Space direction="vertical" size={16} className="w-full">
			<Alert
				type="info"
				showIcon
				message={`${MODEL_TYPE_LABELS[modelType]} · 本模型产物 ${targetLayer}`}
				description={
					<Space direction="vertical" size={4}>
						<Text>{MODEL_TYPE_DESCRIPTIONS[modelType]}</Text>
						<Text>{MODEL_TYPE_LIFECYCLE_DEPENDENCIES[modelType]}</Text>
						<Text type="secondary">
							ODS/STG 属于数据接入层，请在数据源连接、元数据同步和来源确认中管理，不是四类表之外的第五类模型。
						</Text>
					</Space>
				}
			/>

			<Row gutter={12}>
				<Col xs={24} md={12}>
					<Form.Item name="planId" label="建设计划" rules={[{ required: true, message: "请选择建设计划" }]}>
						<Select
							showSearch
							optionFilterProp="label"
							options={planOptions}
							loading={planLoading}
							disabled={readOnly || lockPlan}
							placeholder="选择计划"
							onChange={onPlanChange}
						/>
					</Form.Item>
				</Col>
				<Col xs={24} md={12}>
					<Form.Item name="domainId" label="业务分类" rules={[{ required: true, message: "请选择业务分类" }]}>
						<Select
							showSearch
							optionFilterProp="label"
							options={domainOptions}
							loading={domainLoading}
							disabled={readOnly || lockDomain || !selectedPlanId}
							placeholder="选择计划内已确认的分类"
						/>
					</Form.Item>
				</Col>
			</Row>

			<Row gutter={12}>
				<Col xs={24} md={12}>
					<Form.Item
						name="modelType"
						label="模型类别（四类表）"
						rules={[{ required: true, message: "请选择模型类别" }]}
					>
						<Select options={modelTypeOptions} disabled={readOnly || lockModelType} onChange={onModelTypeChange} />
					</Form.Item>
				</Col>
				<Col xs={24} md={12}>
					<Form.Item
						name="layer"
						label="本模型产物分层"
						rules={[{ required: true, message: "目标分层生成失败，请重新选择模型类别" }]}
					>
						<Select options={[{ value: targetLayer, label: `${targetLayer}（系统固定）` }]} disabled />
					</Form.Item>
				</Col>
			</Row>

			<Form.Item
				name="name"
				label={editorCopy.nameLabel}
				rules={[
					{ required: true, whitespace: true, message: editorCopy.nameRequiredMessage },
					{ max: 256, message: "模型名称不能超过 256 个字符" },
				]}
			>
				<Input disabled={readOnly} placeholder="填写便于业务人员识别的模型名称，例如：客户事件明细" />
			</Form.Item>
			<Form.Item
				name="description"
				label={editorCopy.descriptionLabel}
				rules={
					editorCopy.descriptionRequiredMessage
						? [{ required: true, whitespace: true, message: editorCopy.descriptionRequiredMessage }]
						: undefined
				}
			>
				<Input.TextArea disabled={readOnly} rows={2} placeholder="说明模型服务的分析主题、报表或业务问题" />
			</Form.Item>

			<Row gutter={12}>
				<Col xs={24} md={16}>
					<Form.Item
						name="grainStatement"
						label={grainLabel(modelType)}
						rules={[{ required: true, whitespace: true, message: `请填写${grainLabel(modelType)}` }]}
					>
						<Input
							disabled={readOnly}
							placeholder={
								modelType === "FACT"
									? "说明每条记录对应的业务事实，例如：每行记录一次客户事件"
									: "说明每条记录对应的数据范围，例如：每行记录一个组织机构"
							}
						/>
					</Form.Item>
				</Col>
				<Col xs={24} md={8}>
					<Form.Item
						name="grainKeysText"
						label={modelType === "DIMENSION" ? "维度键" : "粒度键"}
						rules={[{ required: true, whitespace: true, message: "请填写至少一个键" }]}
					>
						<Input disabled={readOnly} placeholder="唯一标识记录的字段，多个用逗号分隔" />
					</Form.Item>
				</Col>
			</Row>

			{modelType === "DIMENSION" ? (
				<Space direction="vertical" size={12} className="w-full">
					<Row gutter={12}>
						<Col xs={24} md={8}>
							<Form.Item
								name="dimensionCode"
								label="维度系统编码"
								help="系统生成，保存后不可修改"
								rules={[
									{ required: true, whitespace: true, message: "维度系统编码生成失败，请重新打开表单" },
									{ pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: "使用 1-64 位大写字母、数字或下划线" },
								]}
							>
								<Input disabled />
							</Form.Item>
						</Col>
						<Col xs={24} md={8}>
							<Form.Item name="dimensionScdType" label="历史保留策略" rules={[{ required: true }]}>
								<Select
									disabled={readOnly}
									options={[
										{ value: "NONE", label: "不保留历史" },
										{ value: "TYPE1", label: "覆盖更新（TYPE1）" },
										{ value: "TYPE2", label: "保留历史（TYPE2）" },
									]}
								/>
							</Form.Item>
						</Col>
						<Col xs={24} md={8}>
							<Form.Item name="dimensionReuseScope" label="复用范围" rules={[{ required: true }]}>
								<Select
									disabled={readOnly}
									options={[
										{ value: "PLAN", label: "当前建设计划" },
										{ value: "DOMAIN", label: "当前业务分类" },
										{ value: "TENANT", label: "全租户" },
									]}
								/>
							</Form.Item>
						</Col>
					</Row>

					{dimensionScdType === "TYPE2" ? (
						<Row gutter={12}>
							<Col xs={24} md={8}>
								<Form.Item name="dimensionEffectiveFromField" label="生效时间字段" rules={[{ required: true }]}>
									<Input disabled={readOnly} placeholder="effective_from" />
								</Form.Item>
							</Col>
							<Col xs={24} md={8}>
								<Form.Item name="dimensionEffectiveToField" label="失效时间字段" rules={[{ required: true }]}>
									<Input disabled={readOnly} placeholder="effective_to" />
								</Form.Item>
							</Col>
							<Col xs={24} md={8}>
								<Form.Item name="dimensionCurrentFlagField" label="当前记录标志" rules={[{ required: true }]}>
									<Input disabled={readOnly} placeholder="is_current" />
								</Form.Item>
							</Col>
						</Row>
					) : null}

					<div className="rounded-lg border border-gray-200 p-3">
						<div className="mb-3 flex items-center justify-between gap-3">
							<div>
								<Text strong>分析层级</Text>
								<Text type="secondary" className="ml-2 text-xs">
									可选；字段顺序即层级顺序
								</Text>
							</div>
						</div>
						<Form.List name="dimensionHierarchies">
							{(fields, { add, remove }) => (
								<Space direction="vertical" className="w-full">
									{fields.map((field, index) => (
										<Row key={field.key} gutter={10} align="middle">
											<Col xs={24} md={6}>
												<Form.Item
													name={[field.name, "code"]}
													label={`层级 ${index + 1} 系统编码`}
													rules={[{ required: true }]}
												>
													<Input disabled />
												</Form.Item>
											</Col>
											<Col xs={24} md={6}>
												<Form.Item name={[field.name, "name"]} label="层级名称" rules={[{ required: true }]}>
													<Input disabled={readOnly} placeholder="组织层级" />
												</Form.Item>
											</Col>
											<Col xs={24} md={10}>
												<Form.Item name={[field.name, "levelFieldsText"]} label="层级字段" rules={[{ required: true }]}>
													<Input disabled={readOnly} placeholder="group_id, department_id, team_id" />
												</Form.Item>
											</Col>
											<Col xs={24} md={2}>
												<Button
													type="text"
													danger
													disabled={readOnly}
													aria-label={`移除层级 ${index + 1}`}
													onClick={() => remove(field.name)}
												>
													<Trash2 size={15} />
												</Button>
											</Col>
										</Row>
									))}
									{!readOnly ? (
										<Button
											type="dashed"
											block
											onClick={() =>
												add({
													code: nextDimensionHierarchyCode(form.getFieldValue("dimensionHierarchies") || []),
													name: "",
													levelFieldsText: "",
												})
											}
										>
											<Plus size={15} />
											添加分析层级
										</Button>
									) : null}
								</Space>
							)}
						</Form.List>
					</div>

					<Row gutter={12}>
						<Col xs={24} md={8}>
							<Form.Item name="keyDataType" label="键字段类型">
								<Input disabled={readOnly} placeholder="string" />
							</Form.Item>
						</Col>
						<Col xs={24} md={8}>
							<Form.Item name="generationStrategyType" label="生成策略">
								<Select
									allowClear
									disabled={readOnly}
									options={[
										{ value: "REFERENCE", label: "复用主数据或参考数据" },
										{ value: "DERIVED", label: "由模型生成" },
										{ value: "MANUAL", label: "人工维护" },
									]}
								/>
							</Form.Item>
						</Col>
						<Col xs={24} md={8}>
							<Form.Item name="generationStrategyReference" label="策略来源">
								<Input disabled={readOnly} placeholder="例如：组织主数据" />
							</Form.Item>
						</Col>
					</Row>
				</Space>
			) : null}

			{modelType === "FACT" ? (
				<>
					<Row gutter={12}>
						<Col xs={24} md={8}>
							<Form.Item name="factShape" label="事实形态">
								<Select
									allowClear
									disabled={readOnly}
									placeholder="选择数据随业务变化的记录方式"
									options={[
										{ value: "TRANSACTION", label: "事务记录" },
										{ value: "PERIODIC_SNAPSHOT", label: "周期快照" },
										{ value: "ACCUMULATING_SNAPSHOT", label: "生命周期快照" },
									]}
								/>
							</Form.Item>
						</Col>
						<Col xs={24} md={8}>
							<Form.Item name="timeSemanticsType" label="业务时间">
								<Select
									allowClear
									disabled={readOnly}
									placeholder="选择记录对应的业务时间含义"
									options={[
										{ value: "EVENT_TIME", label: "事件时间" },
										{ value: "SNAPSHOT_DATE", label: "快照日期" },
										{ value: "PERIOD", label: "统计周期" },
										{ value: "MILESTONE_DATES", label: "里程碑日期" },
									]}
								/>
							</Form.Item>
						</Col>
						<Col xs={24} md={8}>
							<Form.Item name="timeFieldsText" label="时间字段">
								<Input disabled={readOnly} placeholder="承载业务时间的字段，多个用逗号分隔" />
							</Form.Item>
						</Col>
					</Row>

					<Form.Item name="dimensionRefIds" label="分析维度">
						<Select
							mode="multiple"
							showSearch
							optionFilterProp="label"
							options={dimensionOptions}
							disabled={readOnly}
							placeholder="选择用于分类、筛选和汇总分析的维度（可选）"
						/>
					</Form.Item>
					<Collapse
						size="small"
						items={[
							{
								key: "business-description",
								label: "业务说明（可选）",
								children: (
									<Form.Item name="businessActivityRef" label="相关业务活动" className="mb-0">
										<Input disabled={readOnly} placeholder="例如：客户事件处理；不填写也可保存" />
									</Form.Item>
								),
							},
						]}
					/>
				</>
			) : null}

			{modelType === "FACT" || modelType === "SUMMARY" || modelType === "APPLICATION" ? (
				<>
					<Form.Item
						name="upstreamIds"
						label={modelType === "FACT" ? "上游模型（可补充或替代物理来源）" : "上游模型"}
						rules={
							modelType === "FACT"
								? [{ validator: validateUpstreamSelection }]
								: [
										{ required: true, type: "array", min: 1, message: "请至少选择一个上游模型" },
										{ validator: validateUpstreamSelection },
									]
						}
						extra={
							modelType === "FACT"
								? "可选。适用于先设计明细模型、后实现加工链路的场景；保存时锁定所选模型当前版本。"
								: undefined
						}
					>
						<Select
							mode="multiple"
							showSearch
							optionFilterProp="label"
							options={upstreamOptions}
							disabled={readOnly}
							placeholder={modelType === "FACT" ? "可选择一个或多个已登记的上游模型" : "保存时锁定所选模型的当前版本"}
						/>
					</Form.Item>
					<Form.Item noStyle shouldUpdate>
						{() => {
							const selectedIds = (form.getFieldValue("upstreamIds") || []) as string[];
							const existingPins = (form.getFieldValue("existingUpstreamPins") ||
								[]) as ModelSpecDraft["existingUpstreamPins"];
							const optionById = new Map(upstreamOptions.map((option) => [option.value, option]));
							const driftedUpstreams = selectedIds.flatMap((modelSpecId) => {
								const pinned = existingPins.find((reference) => reference.modelSpecId === modelSpecId);
								const option = optionById.get(modelSpecId);
								if (
									!pinned ||
									option?.disabled !== false ||
									typeof option.revision !== "number" ||
									option.revision === pinned.revision
								) {
									return [];
								}
								return [{ modelSpecId, option, pinnedRevision: pinned.revision }];
							});
							if (driftedUpstreams.length === 0) return null;
							return (
								<Alert
									type="warning"
									showIcon
									message="所选上游模型已有新版本"
									description={
										<Space direction="vertical" size={4} className="w-full">
											<Text type="secondary">普通保存仍保留已锁定版本；只有点击对应模型的操作才会升级。</Text>
											{driftedUpstreams.map(({ modelSpecId, option, pinnedRevision }) => (
												<div key={modelSpecId} className="flex items-center justify-between gap-3">
													<Text>
														{option.label}：已锁定 r{pinnedRevision}，当前 r{option.revision}
													</Text>
													<Button
														type="link"
														size="small"
														disabled={readOnly}
														onClick={() => adoptCurrentUpstreamRevision(modelSpecId)}
													>
														采用所选上游当前版本
													</Button>
												</div>
											))}
										</Space>
									}
								/>
							);
						}}
					</Form.Item>
				</>
			) : null}

			{modelType === "APPLICATION" ? (
				<Form.Item
					name="consumptionScenario"
					label="消费场景"
					rules={[{ required: true, whitespace: true, message: "请说明服务的报表、接口或业务场景" }]}
				>
					<Input.TextArea disabled={readOnly} rows={2} placeholder="例如：客户运营日报与查询接口" />
				</Form.Item>
			) : null}

			{modelType === "DIMENSION" || modelType === "FACT" ? (
				<div className="rounded-lg border border-gray-200 p-3">
					<div className="mb-3 flex items-center justify-between gap-3">
						<div>
							<Text strong>上游输入来源</Text>
							<Text type="secondary" className="ml-2 text-xs">
								{modelType === "FACT" ? "可选：实现前至少一种，也可组合使用" : "概念维度可稍后补来源"}
							</Text>
						</div>
						{onManageSources && !readOnly && !sourcePermissionDenied ? (
							<Button size="small" type="link" onClick={onManageSources}>
								管理规划来源
							</Button>
						) : null}
					</div>
					<Text type="secondary" className="mb-3 block text-xs">
						{modelType === "FACT"
							? "这里选择的是上游输入，不是正在创建的目标表。草稿阶段可暂不选择；进入实现前，需选择当前计划已确认的物理来源或锁定版本的上游模型。连接 → 元数据同步 → 来源确认 → 建模 → 实现/测试 → 发布运行，不需要先完成 ETL/ELT。"
							: "这里选择的是维度的上游输入。连接 → 元数据同步 → 来源确认 → 建模 → 实现/测试 → 发布运行，不需要先完成 ETL/ELT。"}
					</Text>
					{!selectedPlanId ? (
						<Alert className="mb-3" type="info" showIcon message="请先选择建设计划，再选择计划内来源" />
					) : sourceError ? (
						<Alert
							className="mb-3"
							type="warning"
							showIcon
							message={sourceError}
							action={
								<Space wrap>
									{onReloadSources ? (
										<Button size="small" onClick={onReloadSources} loading={sourceLoading}>
											重试
										</Button>
									) : null}
									{!sourcePermissionDenied && onManageSources ? (
										<Button size="small" type="primary" onClick={onManageSources}>
											在当前表单登记来源
										</Button>
									) : !sourcePermissionDenied ? (
										<Button size="small" href={sourceInventoryPath} target="_blank" rel="noreferrer">
											完善来源盘点
										</Button>
									) : null}
								</Space>
							}
						/>
					) : null}
					<Form.List
						name="sources"
						rules={[
							{
								validator: async (_, sources) => {
									if (!Array.isArray(sources) || sources.length === 0) return;
									const selectableByBindingId = new Map(
										sourceOptions.filter((option) => !option.disabled).map((option) => [option.value, option]),
									);
									const selectedIds = sources.map((source) => source?.sourceBindingId).filter(Boolean);
									if (selectedIds.some((bindingId) => !selectableByBindingId.has(bindingId))) {
										throw new Error("存在已失效或尚未完成确认的来源，请替换或移除后再保存");
									}
									if (
										sources.some(
											(source) =>
												!modelSpecSourceMatchesChoice(source, selectableByBindingId.get(source?.sourceBindingId)),
										)
									) {
										throw new Error("来源定义或版本已变化，请明确采用当前版本后再保存");
									}
									if (new Set(selectedIds).size !== selectedIds.length) {
										throw new Error("同一个规划来源不能重复添加");
									}
								},
							},
						]}
					>
						{(fields, { add, remove }, { errors }) => (
							<Space direction="vertical" className="w-full">
								{fields.map((field, index) => (
									<div key={field.key} className="rounded border border-gray-100 bg-gray-50 p-3">
										<div className="mb-2 flex items-center justify-between">
											<Text type="secondary">来源 {index + 1}</Text>
											<Button
												type="text"
												danger
												disabled={readOnly}
												aria-label={`移除来源 ${index + 1}`}
												onClick={() => remove(field.name)}
											>
												<Trash2 size={15} />
											</Button>
										</div>
										<Form.Item name={[field.name, "kind"]} hidden rules={[{ required: true }]}>
											<Input />
										</Form.Item>
										<Form.Item name={[field.name, "ref"]} hidden rules={[{ required: true, whitespace: true }]}>
											<Input />
										</Form.Item>
										<Form.Item
											name={[field.name, "resolvedVersion"]}
											hidden
											rules={[{ required: true, whitespace: true }]}
										>
											<Input />
										</Form.Item>
										<Form.Item
											name={[field.name, "sourceBindingId"]}
											label="规划来源"
											rules={[{ required: true, whitespace: true, message: "请选择计划内已确认的来源" }]}
										>
											<Select
												showSearch
												optionFilterProp="label"
												options={sourceOptions}
												loading={sourceLoading}
												disabled={readOnly || !selectedPlanId || sourceLoading}
												placeholder="按名称选择当前计划已确认的表、文件或 dbt 节点"
												onChange={(bindingId) => {
													const choice = sourceOptions.find((option) => option.value === bindingId);
													if (!choice) return;
													const currentSources = [...(form.getFieldValue("sources") || [])];
													currentSources[field.name] = modelSpecSourceDraftFromChoice(
														choice,
														currentSources[field.name],
													);
													form.setFieldValue("sources", currentSources);
												}}
											/>
										</Form.Item>
										<Form.Item noStyle shouldUpdate>
											{() => {
												const sources = form.getFieldValue("sources") as ModelSpecDraft["sources"] | undefined;
												const source = sources?.[field.name];
												if (!source?.sourceBindingId) return null;
												const currentChoice = sourceOptions.find(
													(option) => !option.disabled && option.value === source.sourceBindingId,
												);
												const identityCurrent = modelSpecSourceMatchesChoice(source, currentChoice);
												return (
													<Space direction="vertical" size={8} className="mb-3 w-full">
														<div className="rounded border border-blue-100 bg-blue-50 px-3 py-2 text-xs text-slate-600">
															<div>来源标识：{source.ref}</div>
															<div>
																来源类型：{sourceKindLabels[source.kind]} · 已确认版本：{source.resolvedVersion}
															</div>
														</div>
														{!sourceLoading && currentChoice && !identityCurrent ? (
															<Alert
																type="warning"
																showIcon
																message="来源定义或版本已变化"
																description={`已保存 ${source.resolvedVersion}，当前 ${currentChoice.resolvedVersion}；请明确确认后再保存。`}
																action={
																	!readOnly ? (
																		<Button
																			size="small"
																			onClick={() => {
																				const currentSources = [
																					...((form.getFieldValue("sources") as
																						| ModelSpecDraft["sources"]
																						| undefined) || []),
																				];
																				currentSources[field.name] = modelSpecSourceDraftFromChoice(
																					currentChoice,
																					source,
																				);
																				form.setFieldValue("sources", currentSources);
																			}}
																		>
																			采用当前版本
																		</Button>
																	) : null
																}
															/>
														) : null}
													</Space>
												);
											}}
										</Form.Item>
										<Row gutter={10}>
											<Col xs={24} md={12}>
												<Form.Item name={[field.name, "layer"]} label="上游来源分层" rules={[{ required: true }]}>
													<Select disabled={readOnly} options={layerOptions} />
												</Form.Item>
											</Col>
											<Col xs={24} md={12}>
												<Form.Item name={[field.name, "role"]} label="来源作用" rules={[{ required: true }]}>
													<Select
														disabled={readOnly}
														options={[
															{ value: "PRIMARY", label: "主要来源" },
															{ value: "JOINED", label: "关联来源" },
														]}
													/>
												</Form.Item>
											</Col>
										</Row>
									</div>
								))}
								{!readOnly ? (
									<Button
										type="dashed"
										block
										disabled={sourceLoading || sourceOptions.every((option) => option.disabled)}
										onClick={() =>
											add({
												kind: "TABLE",
												ref: "",
												layer: "ODS",
												role: fields.length === 0 ? "PRIMARY" : "JOINED",
												sourceBindingId: "",
												resolvedVersion: "",
											})
										}
									>
										<Plus size={15} />
										{fields.length === 0 ? "选择规划来源" : "添加关联来源"}
									</Button>
								) : null}
								<Form.ErrorList errors={errors} />
							</Space>
						)}
					</Form.List>
				</div>
			) : null}

			<Collapse
				size="small"
				items={[
					{
						key: "implementation",
						label: "实现偏好（可选）",
						children: (
							<Row gutter={12}>
								<Col xs={24} md={12}>
									<Form.Item name="implementationMode" label="实现方式" className="mb-0">
										<Radio.Group
											disabled={readOnly}
											options={[
												{ value: "DESIGNER_GENERATED", label: "设计器生成" },
												{ value: "DBT_MANAGED", label: "dbt 工程维护" },
											]}
										/>
									</Form.Item>
								</Col>
								<Col xs={24} md={12}>
									<Form.Item name="materialization" label="生成方式" className="mb-0">
										<Select
											allowClear
											disabled={readOnly}
											options={[
												{ value: "table", label: "数据表" },
												{ value: "view", label: "视图" },
												{ value: "incremental", label: "增量表" },
											]}
										/>
									</Form.Item>
								</Col>
							</Row>
						),
					},
				]}
			/>
		</Space>
	);
}

export function modelTypeDefaultLayer(modelType: ModelSpecType): ModelSpecLayer {
	return MODEL_SPEC_TARGET_LAYER_BY_TYPE[modelType];
}
