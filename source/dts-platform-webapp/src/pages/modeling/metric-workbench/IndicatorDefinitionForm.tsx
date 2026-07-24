import { Button, Checkbox, Collapse, Form, Input, InputNumber, Select, Tooltip } from "antd";
import type { DatasetField } from "@/api/platformApi";
import { DatasetPicker } from "@/components/catalog/DatasetPicker";
import type { IndicatorDefinition } from "../indicatorDefinitionContract";

export type IndicatorFormValues = Omit<IndicatorDefinition, "id" | "dependencyIndicators" | "dimensionFields"> & {
	dependencyCodes: string[];
	dimensionCodes: string[];
};

type IndicatorDependencyOption = {
	id: string;
	code: string;
	name?: string | null;
};

type IndicatorDefinitionFormProps = {
	currentId?: string;
	isDerived: boolean;
	dependencyCodes: string[];
	dependencyOptions: IndicatorDependencyOption[];
	datasetFields: DatasetField[];
	onDatasetFieldsLoaded: (fields: DatasetField[]) => void;
	onDerivedChange: (checked: boolean) => void;
	onInsertDependencyToken: (code: string) => void;
};

export function IndicatorDefinitionForm({
	currentId,
	isDerived,
	dependencyCodes,
	dependencyOptions,
	datasetFields,
	onDatasetFieldsLoaded,
	onDerivedChange,
	onInsertDependencyToken,
}: IndicatorDefinitionFormProps) {
	return (
		<Collapse
			defaultActiveKey={["basic", "compute"]}
			items={[
				{
					key: "basic",
					label: "基础与治理属性",
					children: (
						<div className="grid gap-x-4 md:grid-cols-2 xl:grid-cols-3">
							<Form.Item name="code" label="指标编码" rules={[{ required: true, message: "请输入稳定指标编码" }]}>
								<Input disabled={Boolean(currentId)} placeholder="例如 GMV" />
							</Form.Item>
							<Form.Item name="name" label="指标名称" rules={[{ required: true, message: "请输入指标名称" }]}>
								<Input placeholder="例如 成交金额" />
							</Form.Item>
							<Form.Item name="category" label="指标分类">
								<Input placeholder="例如 交易、履约、用户增长" />
							</Form.Item>
							<Form.Item className="md:col-span-2 xl:col-span-3" name="definition" label="业务口径">
								<Input.TextArea rows={3} placeholder="说明统计对象、业务范围和排除条件" />
							</Form.Item>
							<Form.Item name="domain" label="业务域">
								<Input placeholder="例如 交易域" />
							</Form.Item>
							<Form.Item name="tags" label="标签（JSON 或文本）">
								<Input placeholder='例如 ["核心","交易"]' />
							</Form.Item>
							<Form.Item name="versionNotes" label="版本说明">
								<Input placeholder="说明本版口径变更" />
							</Form.Item>
							<Form.Item name="owner" label="技术负责人">
								<Input />
							</Form.Item>
							<Form.Item name="businessOwner" label="业务负责人">
								<Input />
							</Form.Item>
							<Form.Item name="ownerDept" label="责任部门">
								<Input />
							</Form.Item>
							<Form.Item name="dataLevel" label="数据密级">
								<Select
									options={["DATA_PUBLIC", "DATA_INTERNAL", "DATA_SENSITIVE", "DATA_SECRET"].map((value) => ({
										value,
										label: value,
									}))}
								/>
							</Form.Item>
							<Form.Item name="dataPrivacy" label="隐私级别">
								<Select
									options={["PUBLIC", "INTERNAL", "SENSITIVE", "SECRET"].map((value) => ({
										value,
										label: value,
									}))}
								/>
							</Form.Item>
							<Form.Item name="humanVerified" valuePropName="checked" label="人工确认">
								<Checkbox>口径已由指标 owner 确认</Checkbox>
							</Form.Item>
						</div>
					),
				},
				{
					key: "compute",
					label: "计算定义与依赖",
					children: (
						<>
							<Form.Item name="isDerived" valuePropName="checked">
								<Checkbox onChange={(event) => onDerivedChange(event.target.checked)}>
									派生指标（从已发布治理指标计算）
								</Checkbox>
							</Form.Item>
							{isDerived ? (
								<div className="grid gap-x-4 md:grid-cols-2">
									<Form.Item
										className="md:col-span-2"
										name="dependencyCodes"
										label="依赖的已发布指标"
										rules={[{ required: true, message: "至少选择一个依赖指标" }]}
									>
										<Select
											mode="multiple"
											showSearch
											optionFilterProp="label"
											options={dependencyOptions.map((item) => ({
												value: item.code,
												label: `${item.name || item.code} (${item.code})`,
											}))}
										/>
									</Form.Item>
									<div className="md:col-span-2 -mt-2 mb-3 flex flex-wrap gap-2">
										{dependencyCodes.map((code) => (
											<Tooltip key={code} title={`插入 {{metric:${code}}}`}>
												<Button size="small" onClick={() => onInsertDependencyToken(code)}>
													插入口径 {code}
												</Button>
											</Tooltip>
										))}
									</div>
									<Form.Item
										className="md:col-span-2"
										name="expressionSql"
										label="受控派生表达式"
										rules={[{ required: true, message: "请输入受控派生表达式" }]}
										extra="仅允许 {{metric:CODE}}、数字、四则运算及 nullif/coalesce/round/abs；发布前由治理指标编译器校验。"
									>
										<Input.TextArea rows={5} placeholder="{{metric:GMV}} / nullif({{metric:ORDER_COUNT}}, 0)" />
									</Form.Item>
								</div>
							) : (
								<div className="grid gap-x-4 md:grid-cols-2">
									<Form.Item
										className="md:col-span-2"
										name="datasetId"
										label="绑定数据集"
										rules={[{ required: true, message: "请选择用于校验计算 SQL 的数据集" }]}
									>
										<DatasetPicker onFieldsLoaded={onDatasetFieldsLoaded} />
									</Form.Item>
									<Form.Item
										name="aggregationType"
										label="聚合方式"
										rules={[{ required: true, message: "请选择聚合方式" }]}
									>
										<Select
											options={["SUM", "COUNT", "COUNT_DISTINCT", "AVG", "MAX", "MIN", "RATIO", "CUSTOM"].map(
												(value) => ({ value, label: value }),
											)}
										/>
									</Form.Item>
									<Form.Item
										name="measureField"
										label="度量字段"
										rules={[{ required: true, message: "请选择度量字段" }]}
									>
										<Select
											showSearch
											options={datasetFields.map((field) => ({
												value: field.name,
												label: field.comment ? `${field.name} · ${field.comment}` : field.name,
											}))}
										/>
									</Form.Item>
									<Form.Item
										className="md:col-span-2"
										name="expressionSql"
										label="可执行计算 SQL"
										rules={[{ required: true }]}
									>
										<Input.TextArea rows={5} placeholder="SELECT SUM(amount) AS metric_value FROM orders" />
									</Form.Item>
									<Form.Item name="numeratorExpression" label="分子表达式">
										<Input />
									</Form.Item>
									<Form.Item name="denominatorExpression" label="分母表达式">
										<Input />
									</Form.Item>
								</div>
							)}
							<div className="grid gap-x-4 md:grid-cols-3">
								<Form.Item name="windowFunction" label="周期修饰">
									<Select
										options={[
											{ value: "NONE", label: "无" },
											{ value: "YOY", label: "同比" },
											{ value: "MOM", label: "环比" },
											{ value: "YTD", label: "年累计" },
										]}
									/>
								</Form.Item>
								<Form.Item name="timeGrain" label="时间粒度">
									<Select
										options={["DAY", "WEEK", "MONTH", "QUARTER", "YEAR"].map((value) => ({
											value,
											label: value,
										}))}
									/>
								</Form.Item>
								<Form.Item name="dateColumn" label="日期字段">
									<Select
										allowClear
										showSearch
										options={datasetFields.map((field) => ({ value: field.name, label: field.name }))}
									/>
								</Form.Item>
							</div>
						</>
					),
				},
				{
					key: "scope",
					label: "过滤、维度与模型落点",
					children: (
						<div className="grid gap-x-4 md:grid-cols-2 xl:grid-cols-3">
							<Form.Item className="md:col-span-2 xl:col-span-3" name="dimensionCodes" label="分析维度">
								<Select
									mode="multiple"
									showSearch
									options={datasetFields.map((field) => ({
										value: field.name,
										label: field.comment ? `${field.name} · ${field.comment}` : field.name,
									}))}
								/>
							</Form.Item>
							<Form.Item name="granularity" label="业务粒度">
								<Input placeholder="例如 SHOP" />
							</Form.Item>
							<Form.Item name="sourceTable" label="来源表/模型">
								<Input />
							</Form.Item>
							<Form.Item name="targetModelName" label="目标模型">
								<Input />
							</Form.Item>
							<Form.Item name="sourceLayer" label="来源分层">
								<Select options={["ODS", "DWD", "DWS", "ADS"].map((value) => ({ value, label: value }))} />
							</Form.Item>
							<Form.Item name="targetLayer" label="目标分层">
								<Select options={["DWD", "DWS", "ADS"].map((value) => ({ value, label: value }))} />
							</Form.Item>
							<Form.Item name="unit" label="计量单位">
								<Input placeholder="例如 元、个、%" />
							</Form.Item>
							<Form.Item className="md:col-span-2 xl:col-span-3" name="staticFilter" label="固定过滤条件">
								<Input.TextArea rows={2} />
							</Form.Item>
							<Form.Item
								className="md:col-span-2 xl:col-span-3"
								name="dynamicFilterConfig"
								label="动态过滤配置（JSON）"
							>
								<Input.TextArea rows={2} />
							</Form.Item>
							<Form.Item className="md:col-span-2 xl:col-span-3" name="joinConfig" label="关联配置（JSON）">
								<Input.TextArea rows={2} />
							</Form.Item>
						</div>
					),
				},
				{
					key: "display",
					label: "展示、阈值与生成来源",
					children: (
						<div className="grid gap-x-4 md:grid-cols-2 xl:grid-cols-4">
							<Form.Item name="precisionScale" label="小数位数">
								<InputNumber min={0} max={12} className="w-full" />
							</Form.Item>
							<Form.Item name="thresholdMin" label="最小阈值">
								<InputNumber className="w-full" />
							</Form.Item>
							<Form.Item name="thresholdMax" label="最大阈值">
								<InputNumber className="w-full" />
							</Form.Item>
							<Form.Item name="direction" label="健康方向">
								<Select options={["POSITIVE", "NEGATIVE", "NEUTRAL"].map((value) => ({ value, label: value }))} />
							</Form.Item>
							<Form.Item name="icon" label="图标">
								<Input />
							</Form.Item>
							<Form.Item name="displayOrder" label="展示顺序">
								<InputNumber className="w-full" />
							</Form.Item>
							<Form.Item name="llmGenerated" valuePropName="checked" label="生成来源">
								<Checkbox>由 LLM 辅助生成</Checkbox>
							</Form.Item>
							<Form.Item name="llmConfidence" label="LLM 置信度">
								<InputNumber min={0} max={1} step={0.01} className="w-full" />
							</Form.Item>
							<Form.Item className="md:col-span-2 xl:col-span-4" name="llmSourceRef" label="生成来源说明">
								<Input />
							</Form.Item>
						</div>
					),
				},
			]}
		/>
	);
}
