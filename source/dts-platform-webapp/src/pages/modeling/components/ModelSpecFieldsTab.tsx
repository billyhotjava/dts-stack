import { AutoComplete, Button, Checkbox, Col, Empty, Form, Input, Row, Select, Space, Typography } from "antd";
import { Plus, Trash2 } from "lucide-react";
import { hasDuplicateModelFieldNames, isProtectedModelFieldName } from "../modelSpecFieldRules";

const { Text } = Typography;

type Props = {
	readOnly: boolean;
	persistedFieldNames: readonly string[];
	dimensionAttributeOptions?: Array<{ value: string; label: string }>;
};

const fieldRoleOptions = [
	{ value: "KEY", label: "键（KEY）" },
	{ value: "ATTRIBUTE", label: "属性" },
	{ value: "TIME", label: "时间（TIME）" },
	{ value: "MEASURE", label: "度量（MEASURE）" },
];

export function ModelSpecFieldsTab({ readOnly, persistedFieldNames, dimensionAttributeOptions = [] }: Props) {
	const form = Form.useFormInstance();
	const fieldValues = Form.useWatch("fields", form) as Array<{ name?: string }> | undefined;
	const grainKeysText = Form.useWatch("grainKeysText", form) as string | undefined;
	const modelType = Form.useWatch("modelType", form) as string | undefined;
	return (
		<div data-testid="model-spec-fields-tab">
			<div className="mb-3">
				<Text strong>字段设计</Text>
				<Text type="secondary" className="ml-2 text-xs">
					先定义本模型输出字段及其业务作用；业务时间引用的字段必须选择“时间（TIME）”
				</Text>
			</div>
			{modelType === "DIMENSION" ? (
				<div className="mb-3 rounded-lg bg-blue-50 px-3 py-2 text-xs text-blue-700">
					维度表字段可映射到业务维度定义中的属性编码。主键属性应映射到作用为“键（KEY）”的字段。
				</div>
			) : null}
			<Form.List
				name="fields"
				rules={[
					{
						validator: async (_, fields) => {
							if (hasDuplicateModelFieldNames(fields)) throw new Error("字段名不能重复");
						},
					},
				]}
			>
				{(fields, { add, remove }, { errors }) => (
					<Space direction="vertical" className="w-full" size={12}>
						{fields.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无字段" /> : null}
						{fields.map((field, index) => {
							const protectedName = isProtectedModelFieldName(
								fieldValues?.[field.name]?.name,
								persistedFieldNames,
								grainKeysText,
							);
							return (
								<div key={field.key} className="rounded-lg border border-gray-200 p-3">
									<div className="mb-2 flex items-center justify-between">
										<Text type="secondary">字段 {index + 1}</Text>
										{!readOnly ? (
											<Button
												type="text"
												danger
												disabled={protectedName}
												title={protectedName ? "请先解除该字段的粒度或标准引用" : undefined}
												aria-label={`删除字段 ${index + 1}`}
												onClick={() => remove(field.name)}
											>
												<Trash2 size={15} />
											</Button>
										) : null}
									</div>
									<Row gutter={12}>
										<Col xs={24} md={6}>
											<Form.Item
												name={[field.name, "name"]}
												label="技术编码"
												rules={[
													{ required: true, whitespace: true, message: "请输入技术编码" },
													{
														pattern: /^[a-z][a-z0-9_]{0,62}$/,
														message: "使用小写英文、数字和下划线，最长 63 个字符",
													},
												]}
											>
												<Input
													disabled={readOnly || protectedName}
													title={protectedName ? "请先解除该字段的粒度或标准引用" : undefined}
													placeholder="例如：customer_id"
												/>
											</Form.Item>
										</Col>
										<Col xs={24} md={6}>
											<Form.Item
												name={[field.name, "displayName"]}
												label="业务名称"
												rules={[{ required: true, whitespace: true, message: "请输入业务名称" }]}
											>
												<Input disabled={readOnly} placeholder="例如：客户编号" />
											</Form.Item>
										</Col>
										<Col xs={24} md={6}>
											<Form.Item
												name={[field.name, "dataType"]}
												label="数据类型"
												rules={[{ required: true, whitespace: true, message: "请输入数据类型" }]}
											>
												<Input disabled={readOnly} placeholder="例如：string" />
											</Form.Item>
										</Col>
										<Col xs={24} md={6}>
											<Form.Item name={[field.name, "role"]} label="字段作用" rules={[{ required: true }]}>
												<Select disabled={readOnly} options={fieldRoleOptions} />
											</Form.Item>
										</Col>
									</Row>
									<Row gutter={12}>
										<Col xs={24} md={12}>
											<Form.Item
												name={[field.name, "sourceFieldRef"]}
												label="来源字段"
												extra="可选：记录该输出字段对应的输入字段；具体输入表或上游模型在“数据实现”中选择"
												className="mb-0"
											>
												<Input disabled={readOnly} placeholder="可选，例如：ods_customer.id" />
											</Form.Item>
										</Col>
										<Col xs={24} md={6}>
											<Form.Item name={[field.name, "securityLevel"]} label="安全等级" className="mb-0">
												<Input disabled={readOnly} placeholder="可选" />
											</Form.Item>
										</Col>
										<Col xs={24} md={6}>
											<Form.Item
												name={[field.name, "nullable"]}
												label="允许为空"
												valuePropName="checked"
												className="mb-0"
											>
												<Checkbox disabled={readOnly}>是</Checkbox>
											</Form.Item>
										</Col>
									</Row>
									<Row gutter={12} className="mt-3">
										<Col xs={24} md={8}>
											<Form.Item
												name={[field.name, "dimensionAttributeCode"]}
												label="维度属性编码"
												extra={
													modelType === "DIMENSION"
														? "填写当前业务维度定义中的属性编码"
														: "非维度模型可用于标明该字段对应的公共维度属性"
												}
												className="mb-0"
											>
												<AutoComplete
													disabled={readOnly}
													options={dimensionAttributeOptions}
													placeholder="选择或输入属性编码"
													filterOption={(input, option) =>
														String(option?.label || "")
															.toLowerCase()
															.includes(input.toLowerCase())
													}
												/>
											</Form.Item>
										</Col>
										<Col xs={24} md={5}>
											<Form.Item
												name={[field.name, "redundant"]}
												label="冗余维度字段"
												valuePropName="checked"
												className="mb-0"
											>
												<Checkbox disabled={readOnly}>是</Checkbox>
											</Form.Item>
										</Col>
										<Col xs={24} md={11}>
											<Form.Item
												noStyle
												shouldUpdate={(previous, current) =>
													previous?.fields?.[field.name]?.redundant !== current?.fields?.[field.name]?.redundant
												}
											>
												{({ getFieldValue }) => {
													const redundant = Boolean(getFieldValue(["fields", field.name, "redundant"]));
													return (
														<Form.Item
															name={[field.name, "redundancySourceRef"]}
															label="冗余来源依据"
															extra="记录该冗余值来自哪个上游模型或来源字段"
															rules={[
																{
																	required: redundant,
																	whitespace: true,
																	message: "请填写冗余字段的来源依据",
																},
															]}
															className="mb-0"
														>
															<Input disabled={readOnly || !redundant} placeholder="例如：dim_project.project_code" />
														</Form.Item>
													);
												}}
											</Form.Item>
										</Col>
									</Row>
								</div>
							);
						})}
						{!readOnly ? (
							<Button
								type="dashed"
								block
								onClick={() =>
									add({
										name: "",
										displayName: "",
										dataType: "string",
										nullable: true,
										role: "ATTRIBUTE",
										redundant: false,
									})
								}
							>
								<Plus size={15} />
								添加字段
							</Button>
						) : null}
						<Form.ErrorList errors={errors} />
					</Space>
				)}
			</Form.List>
		</div>
	);
}
