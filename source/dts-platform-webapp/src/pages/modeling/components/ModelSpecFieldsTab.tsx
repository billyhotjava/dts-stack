import { Button, Checkbox, Col, Empty, Form, Input, Row, Select, Space, Typography } from "antd";
import { Plus, Trash2 } from "lucide-react";
import { hasDuplicateModelFieldNames, isProtectedModelFieldName } from "../modelSpecFieldRules";

const { Text } = Typography;

type Props = {
	readOnly: boolean;
	persistedFieldNames: readonly string[];
};

const fieldRoleOptions = [
	{ value: "KEY", label: "键" },
	{ value: "ATTRIBUTE", label: "属性" },
	{ value: "TIME", label: "时间" },
	{ value: "MEASURE", label: "度量" },
];

export function ModelSpecFieldsTab({ readOnly, persistedFieldNames }: Props) {
	const form = Form.useFormInstance();
	const fieldValues = Form.useWatch("fields", form) as Array<{ name?: string }> | undefined;
	const grainKeysText = Form.useWatch("grainKeysText", form) as string | undefined;
	return (
		<div data-testid="model-spec-fields-tab">
			<div className="mb-3">
				<Text strong>字段设计</Text>
				<Text type="secondary" className="ml-2 text-xs">
					已保存字段和当前粒度键需先解除引用后再调整名称
				</Text>
			</div>
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
										<Col xs={24} md={8}>
											<Form.Item
												name={[field.name, "name"]}
												label="字段名"
												rules={[{ required: true, whitespace: true, message: "请输入字段名" }]}
											>
												<Input
													disabled={readOnly || protectedName}
													title={protectedName ? "请先解除该字段的粒度或标准引用" : undefined}
													placeholder="例如：customer_id"
												/>
											</Form.Item>
										</Col>
										<Col xs={24} md={8}>
											<Form.Item
												name={[field.name, "dataType"]}
												label="数据类型"
												rules={[{ required: true, whitespace: true, message: "请输入数据类型" }]}
											>
												<Input disabled={readOnly} placeholder="例如：string" />
											</Form.Item>
										</Col>
										<Col xs={24} md={8}>
											<Form.Item name={[field.name, "role"]} label="字段作用" rules={[{ required: true }]}>
												<Select disabled={readOnly} options={fieldRoleOptions} />
											</Form.Item>
										</Col>
									</Row>
									<Row gutter={12}>
										<Col xs={24} md={12}>
											<Form.Item name={[field.name, "sourceFieldRef"]} label="来源字段" className="mb-0">
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
								</div>
							);
						})}
						{!readOnly ? (
							<Button
								type="dashed"
								block
								onClick={() => add({ name: "", dataType: "string", nullable: true, role: "ATTRIBUTE" })}
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
