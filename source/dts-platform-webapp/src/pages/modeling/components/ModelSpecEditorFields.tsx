import type { FormInstance } from "antd";
import { Alert, Button, Col, Collapse, Form, Input, Radio, Row, Select, Space, Typography } from "antd";
import { Plus, Trash2 } from "lucide-react";
import type { ModelSpecLayer, ModelSpecType } from "../modelSpecV2Contract";
import type { ModelSpecDraft } from "../modelSpecWorkbench";
import { MODEL_TYPE_DESCRIPTIONS, MODEL_TYPE_LABELS, modelSpecEditorCopy } from "../modelSpecWorkbench";

const { Text } = Typography;

export type ModelSpecSelectOption = { value: string; label: string; disabled?: boolean };

type Props = {
	form: FormInstance<ModelSpecDraft>;
	planOptions: ModelSpecSelectOption[];
	domainOptions: ModelSpecSelectOption[];
	upstreamOptions: ModelSpecSelectOption[];
	dimensionOptions: ModelSpecSelectOption[];
	planLoading?: boolean;
	domainLoading?: boolean;
	lockPlan?: boolean;
	lockDomain?: boolean;
	lockModelType?: boolean;
	readOnly?: boolean;
	onPlanChange?: (planId: string) => void;
	onModelTypeChange?: (modelType: ModelSpecType) => void;
};

const layerOptions: ModelSpecSelectOption[] = ["ODS", "STG", "DWD", "DWS", "ADS"].map((value) => ({
	value,
	label: value,
}));

const modelTypeOptions = (Object.keys(MODEL_TYPE_LABELS) as ModelSpecType[]).map((value) => ({
	value,
	label: MODEL_TYPE_LABELS[value],
}));

const grainLabel = (modelType: ModelSpecType) => {
	if (modelType === "FACT") return "一行代表什么";
	if (modelType === "DIMENSION") return "每行代表什么";
	return "输出粒度";
};

export function ModelSpecEditorFields({
	form,
	planOptions,
	domainOptions,
	upstreamOptions,
	dimensionOptions,
	planLoading = false,
	domainLoading = false,
	lockPlan = false,
	lockDomain = false,
	lockModelType = false,
	readOnly = false,
	onPlanChange,
	onModelTypeChange,
}: Props) {
	const modelType = (Form.useWatch("modelType", form) || "FACT") as ModelSpecType;
	const selectedPlanId = Form.useWatch("planId", form);
	const editorCopy = modelSpecEditorCopy(modelType);

	return (
		<Space direction="vertical" size={16} className="w-full">
			<Alert type="info" showIcon message={MODEL_TYPE_DESCRIPTIONS[modelType]} />

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
					<Form.Item name="modelType" label="表类型" rules={[{ required: true, message: "请选择表类型" }]}>
						<Select options={modelTypeOptions} disabled={readOnly || lockModelType} onChange={onModelTypeChange} />
					</Form.Item>
				</Col>
				<Col xs={24} md={12}>
					<Form.Item name="layer" label="数仓分层" rules={[{ required: true, message: "请选择数仓分层" }]}>
						<Select options={layerOptions} disabled={readOnly} />
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
				<Input disabled={readOnly} placeholder="例如：客户事件明细" />
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
				<Input.TextArea disabled={readOnly} rows={2} placeholder="说明这张表解决什么分析或使用问题" />
			</Form.Item>

			<Row gutter={12}>
				<Col xs={24} md={16}>
					<Form.Item
						name="grainStatement"
						label={grainLabel(modelType)}
						rules={[{ required: true, whitespace: true, message: `请说明${grainLabel(modelType)}` }]}
					>
						<Input
							disabled={readOnly}
							placeholder={modelType === "FACT" ? "例如：一行代表一次客户事件" : "例如：一行代表一个组织机构"}
						/>
					</Form.Item>
				</Col>
				<Col xs={24} md={8}>
					<Form.Item
						name="grainKeysText"
						label={modelType === "DIMENSION" ? "维度键" : "粒度键"}
						rules={[{ required: true, whitespace: true, message: "请填写至少一个键" }]}
					>
						<Input disabled={readOnly} placeholder="多个字段用逗号分隔" />
					</Form.Item>
				</Col>
			</Row>

			{modelType === "DIMENSION" ? (
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
			) : null}

			{modelType === "FACT" ? (
				<>
					<Row gutter={12}>
						<Col xs={24} md={8}>
							<Form.Item name="factShape" label="事实形态">
								<Select
									allowClear
									disabled={readOnly}
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
								<Input disabled={readOnly} placeholder="多个字段用逗号分隔" />
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
							placeholder="可选，引用维度目录中的维度"
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

			{modelType === "SUMMARY" || modelType === "APPLICATION" ? (
				<Form.Item
					name="upstreamIds"
					label="上游模型"
					rules={[{ required: true, type: "array", min: 1, message: "请至少选择一个上游模型" }]}
				>
					<Select
						mode="multiple"
						showSearch
						optionFilterProp="label"
						options={upstreamOptions}
						disabled={readOnly}
						placeholder="保存时锁定所选模型的当前版本"
					/>
				</Form.Item>
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
							<Text strong>数据来源</Text>
							<Text type="secondary" className="ml-2 text-xs">
								{modelType === "FACT" ? "明细表至少需要一个已确认来源" : "概念维度可稍后补来源"}
							</Text>
						</div>
					</div>
					<Form.List
						name="sources"
						rules={[
							{
								validator: async (_, sources) => {
									if (modelType === "FACT" && (!Array.isArray(sources) || sources.length === 0)) {
										throw new Error("请至少添加一个来源");
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
										<Row gutter={10}>
											<Col xs={24} md={8}>
												<Form.Item name={[field.name, "kind"]} label="来源类型" rules={[{ required: true }]}>
													<Select
														disabled={readOnly}
														options={[
															{ value: "TABLE", label: "数据表" },
															{ value: "DBT_MODEL", label: "dbt 模型" },
															{ value: "DATASET", label: "数据集" },
														]}
													/>
												</Form.Item>
											</Col>
											<Col xs={24} md={8}>
												<Form.Item
													name={[field.name, "ref"]}
													label="来源标识"
													rules={[{ required: true, whitespace: true }]}
												>
													<Input disabled={readOnly} placeholder="例如：ods.customer_event" />
												</Form.Item>
											</Col>
											<Col xs={24} md={8}>
												<Form.Item name={[field.name, "layer"]} label="来源分层" rules={[{ required: true }]}>
													<Select disabled={readOnly} options={layerOptions} />
												</Form.Item>
											</Col>
										</Row>
										<Row gutter={10}>
											<Col xs={24} md={8}>
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
											<Col xs={24} md={8}>
												<Form.Item
													name={[field.name, "sourceBindingId"]}
													label="来源登记 ID"
													rules={[{ required: true, whitespace: true, message: "请填写来源盘点中的登记 ID" }]}
												>
													<Input disabled={readOnly} placeholder="来源盘点中的 UUID" />
												</Form.Item>
											</Col>
											<Col xs={24} md={8}>
												<Form.Item
													name={[field.name, "resolvedVersion"]}
													label="已确认版本"
													rules={[{ required: true, whitespace: true, message: "请填写来源版本" }]}
												>
													<Input disabled={readOnly} placeholder="例如：v1" />
												</Form.Item>
											</Col>
										</Row>
									</div>
								))}
								{!readOnly ? (
									<Button type="dashed" block onClick={() => add({ kind: "TABLE", layer: "ODS", role: "PRIMARY" })}>
										<Plus size={15} />
										添加来源
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
	if (modelType === "SUMMARY") return "DWS";
	if (modelType === "APPLICATION") return "ADS";
	return "DWD";
}
