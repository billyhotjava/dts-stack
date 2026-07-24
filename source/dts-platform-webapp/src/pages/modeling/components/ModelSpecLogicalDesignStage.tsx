import type { FormInstance } from "antd";
import { Alert, Button, Col, Form, Input, Row, Select, Space, Tabs, Typography } from "antd";
import { Plus, Trash2 } from "lucide-react";
import { nextDimensionHierarchyCode } from "../modelSpecSystemCode";
import { type CanonicalModelSpecView, type ModelSpecType, MODEL_SPEC_TARGET_LAYER_BY_TYPE } from "../modelSpecV2Contract";
import type { ModelSpecDraft } from "../modelSpecWorkbench";
import { MODEL_TYPE_DESCRIPTIONS, MODEL_TYPE_LABELS, modelSpecEditorCopy } from "../modelSpecWorkbench";
import { ModelSpecDependencyPanel } from "./ModelSpecDependencyPanel";
import { ModelSpecFieldsTab } from "./ModelSpecFieldsTab";
import { ModelSpecStandardsTab } from "./ModelSpecStandardsTab";

const { Text } = Typography;

export type ModelSpecSelectOption = {
	value: string;
	label: string;
	disabled?: boolean;
	revision?: number;
	checksum?: string;
};

type Props = {
	form: FormInstance<ModelSpecDraft>;
	model: CanonicalModelSpecView;
	planOptions: ModelSpecSelectOption[];
	domainOptions: ModelSpecSelectOption[];
	upstreamOptions: ModelSpecSelectOption[];
	dimensionOptions: ModelSpecSelectOption[];
	upstreamValidationAvailable: boolean;
	readOnly: boolean;
	saving: boolean;
	persistedFieldNames: string[];
	onSaveStandardBindings: (bindings: ModelSpecDraft["standardBindings"]) => Promise<boolean>;
};

const grainLabel = (modelType: ModelSpecType) =>
	modelType === "FACT" ? "粒度声明" : modelType === "DIMENSION" ? "维度粒度" : "输出粒度";

export function ModelSpecLogicalDesignStage({
	form,
	model,
	planOptions,
	domainOptions,
	upstreamOptions,
	dimensionOptions,
	upstreamValidationAvailable,
	readOnly,
	saving,
	persistedFieldNames,
	onSaveStandardBindings,
}: Props) {
	const modelType = (Form.useWatch("modelType", form) || "FACT") as ModelSpecType;
	const dimensionScdType = Form.useWatch("dimensionScdType", form);
	const editorCopy = modelSpecEditorCopy(modelType);
	const targetLayer = MODEL_SPEC_TARGET_LAYER_BY_TYPE[modelType];
	const upstreamRequired = modelType === "SUMMARY" || modelType === "APPLICATION";
	const validateUpstreamSelection = async (_: unknown, selectedIds?: string[]) => {
		if (!upstreamValidationAvailable) return;
		if (!Array.isArray(selectedIds) || selectedIds.length === 0) return;
		const optionById = new Map(upstreamOptions.map((option) => [option.value, option]));
		if (selectedIds.some((id) => optionById.get(id)?.disabled !== false)) {
			throw new Error("存在不符合当前模型类别依赖规则的上游模型，请移除后重新选择");
		}
	};

	return (
		<div className="min-w-0" data-testid="model-spec-logical-stage">
			<Alert
				className="mb-4"
				type="info"
				showIcon
				message={`${MODEL_TYPE_LABELS[modelType]}逻辑设计 · 产物 ${targetLayer}`}
				description={`${MODEL_TYPE_DESCRIPTIONS[modelType]} 在此阶段只维护业务语义、粒度、字段和标准；数据来源与物化配置在“数据实现”完成。`}
			/>
			<Tabs
				defaultActiveKey="definition"
				items={[
					{
						key: "definition",
						label: "逻辑定义",
						forceRender: true,
						children: (
							<Space direction="vertical" size={16} className="w-full">
								<Row gutter={12}>
									<Col xs={24} md={12}>
										<Form.Item name="planId" label="建设计划" rules={[{ required: true, message: "请选择建设计划" }]}>
											<Select options={planOptions} disabled />
										</Form.Item>
									</Col>
									<Col xs={24} md={12}>
										<Form.Item name="domainId" label="业务分类" rules={[{ required: true, message: "请选择业务分类" }]}>
											<Select options={domainOptions} disabled />
										</Form.Item>
									</Col>
								</Row>
								<Row gutter={12}>
									<Col xs={24} md={12}>
										<Form.Item name="modelType" label="模型类别（四类表）">
											<Select options={(Object.keys(MODEL_TYPE_LABELS) as ModelSpecType[]).map((value) => ({ value, label: MODEL_TYPE_LABELS[value] }))} disabled />
										</Form.Item>
									</Col>
									<Col xs={24} md={12}>
										<Form.Item name="layer" label="目标分层"><Select options={[{ value: targetLayer, label: `${targetLayer}（系统固定）` }]} disabled /></Form.Item>
									</Col>
								</Row>
								<Form.Item name="name" label={editorCopy.nameLabel} rules={[{ required: true, whitespace: true, message: editorCopy.nameRequiredMessage }, { max: 256, message: "模型名称不能超过 256 个字符" }]}>
									<Input disabled={readOnly} placeholder="填写便于业务人员识别的模型名称，例如：客户事件明细" />
								</Form.Item>
								<Form.Item name="description" label={editorCopy.descriptionLabel}><Input.TextArea disabled={readOnly} rows={2} placeholder="说明模型服务的分析主题、报表或业务问题" /></Form.Item>
								<Row gutter={12}>
									<Col xs={24} md={16}><Form.Item name="grainStatement" label={grainLabel(modelType)} rules={[{ required: true, whitespace: true, message: `请填写${grainLabel(modelType)}` }]}><Input disabled={readOnly} placeholder="说明一行数据代表的业务事实或对象" /></Form.Item></Col>
									<Col xs={24} md={8}><Form.Item name="grainKeysText" label={modelType === "DIMENSION" ? "业务键" : "粒度键"} rules={[{ required: true, whitespace: true, message: "请填写至少一个键" }]}><Input disabled={readOnly} placeholder="唯一标识记录的字段，多个用逗号分隔" /></Form.Item></Col>
								</Row>
								{modelType === "DIMENSION" ? (
									<Space direction="vertical" size={12} className="w-full">
										<Row gutter={12}>
											<Col xs={24} md={8}><Form.Item name="dimensionCode" label="维度系统编码" help="系统生成，保存后不可修改"><Input disabled /></Form.Item></Col>
											<Col xs={24} md={8}><Form.Item name="dimensionScdType" label="SCD 逻辑策略"><Select disabled={readOnly} options={[{ value: "NONE", label: "不保留历史" }, { value: "TYPE1", label: "覆盖更新（TYPE1）" }, { value: "TYPE2", label: "保留历史（TYPE2）" }]} /></Form.Item></Col>
											<Col xs={24} md={8}><Form.Item name="dimensionReuseScope" label="复用范围"><Select disabled={readOnly} options={[{ value: "PLAN", label: "当前建设计划" }, { value: "DOMAIN", label: "当前业务分类" }, { value: "TENANT", label: "全租户" }]} /></Form.Item></Col>
										</Row>
										{dimensionScdType === "TYPE2" ? <Row gutter={12}><Col xs={24} md={8}><Form.Item name="dimensionEffectiveFromField" label="生效时间字段" rules={[{ required: true }]}><Input disabled={readOnly} placeholder="effective_from" /></Form.Item></Col><Col xs={24} md={8}><Form.Item name="dimensionEffectiveToField" label="失效时间字段" rules={[{ required: true }]}><Input disabled={readOnly} placeholder="effective_to" /></Form.Item></Col><Col xs={24} md={8}><Form.Item name="dimensionCurrentFlagField" label="当前记录标志" rules={[{ required: true }]}><Input disabled={readOnly} placeholder="is_current" /></Form.Item></Col></Row> : null}
										<Form.List name="dimensionHierarchies">{(fields, { add, remove }) => <div className="rounded-lg border border-gray-200 p-3"><Text strong>分析层级</Text><Text type="secondary" className="ml-2 text-xs">可选；字段顺序即层级顺序</Text><Space direction="vertical" className="mt-3 w-full">{fields.map((field, index) => <Row key={field.key} gutter={10} align="middle"><Col xs={24} md={6}><Form.Item name={[field.name, "code"]} label={`层级 ${index + 1} 编码`}><Input disabled /></Form.Item></Col><Col xs={24} md={7}><Form.Item name={[field.name, "name"]} label="层级名称" rules={[{ required: true }]}><Input disabled={readOnly} /></Form.Item></Col><Col xs={20} md={9}><Form.Item name={[field.name, "levelFieldsText"]} label="层级字段" rules={[{ required: true }]}><Input disabled={readOnly} placeholder="group_id, department_id" /></Form.Item></Col><Col xs={4} md={2}><Button type="text" danger disabled={readOnly} aria-label={`移除层级 ${index + 1}`} onClick={() => remove(field.name)}><Trash2 size={15} /></Button></Col></Row>)}{!readOnly ? <Button type="dashed" block onClick={() => add({ code: nextDimensionHierarchyCode(form.getFieldValue("dimensionHierarchies") || []), name: "", levelFieldsText: "" })}><Plus size={15} />添加分析层级</Button> : null}</Space></div>}</Form.List>
									</Space>
								) : null}
								{modelType === "FACT" ? <><Row gutter={12}><Col xs={24} md={8}><Form.Item name="factShape" label="事实形态"><Select allowClear disabled={readOnly} options={[{ value: "TRANSACTION", label: "事务记录" }, { value: "PERIODIC_SNAPSHOT", label: "周期快照" }, { value: "ACCUMULATING_SNAPSHOT", label: "生命周期快照" }]} /></Form.Item></Col><Col xs={24} md={8}><Form.Item name="timeSemanticsType" label="业务时间"><Select allowClear disabled={readOnly} options={[{ value: "EVENT_TIME", label: "事件时间" }, { value: "SNAPSHOT_DATE", label: "快照日期" }, { value: "PERIOD", label: "统计周期" }, { value: "MILESTONE_DATES", label: "里程碑日期" }]} /></Form.Item></Col><Col xs={24} md={8}><Form.Item name="timeFieldsText" label="时间字段"><Input disabled={readOnly} placeholder="多个用逗号分隔" /></Form.Item></Col></Row><Form.Item name="dimensionRefIds" label="分析维度"><Select mode="multiple" options={dimensionOptions} disabled={readOnly} placeholder="选择用于分类、筛选和汇总分析的维度（可选）" /></Form.Item><Form.Item name="businessActivityRef" label="相关业务活动"><Input disabled={readOnly} placeholder="例如：客户事件处理" /></Form.Item></> : null}
								{modelType === "FACT" || upstreamRequired ? <Form.Item name="upstreamIds" label="上游逻辑模型" rules={[{ validator: validateUpstreamSelection }, ...(upstreamRequired ? [{ required: true, type: "array" as const, min: 1, message: "请至少选择一个上游模型" }] : [])]}><Select mode="multiple" options={upstreamOptions} disabled={readOnly} placeholder="选择并锁定上游模型版本" /></Form.Item> : null}
								{modelType === "APPLICATION" ? <Form.Item name="consumptionScenario" label="消费场景" rules={[{ required: true, whitespace: true, message: "请说明服务的报表、接口或业务场景" }]}><Input.TextArea disabled={readOnly} rows={2} /></Form.Item> : null}
							</Space>
						),
					},
					{ key: "fields", label: "字段设计", forceRender: true, children: <ModelSpecFieldsTab readOnly={readOnly} persistedFieldNames={persistedFieldNames} /> },
					{ key: "standards", label: "字段标准", forceRender: true, children: <ModelSpecStandardsTab model={model} canEdit={!readOnly} saving={saving} onSaveStandardBindings={onSaveStandardBindings} /> },
					{ key: "dependencies", label: "依赖关系", children: <ModelSpecDependencyPanel modelSpecId={model.id} revision={model.revision} /> },
				]}
			/>
		</div>
	);
}
