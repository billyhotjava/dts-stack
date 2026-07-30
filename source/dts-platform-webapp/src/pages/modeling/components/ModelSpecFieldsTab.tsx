import { AutoComplete, Button, Checkbox, Empty, Form, Input, Select, Space, Typography } from "antd";
import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
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
	const [expandedGovernanceRows, setExpandedGovernanceRows] = useState<Set<number>>(() => new Set());
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
						{errors.length > 0 ? (
							<div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
								<Form.ErrorList errors={errors} />
							</div>
						) : null}
						{fields.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无字段" /> : null}
						{fields.length > 0 ? (
							<div className="overflow-x-auto rounded-lg border border-slate-200">
								<table aria-label="模型字段设计" className="w-full min-w-[1060px] table-fixed border-collapse">
									<colgroup>
										<col className="w-12" />
										<col className="w-[17%]" />
										<col className="w-[15%]" />
										<col className="w-[13%]" />
										<col className="w-[16%]" />
										<col className="w-[88px]" />
										<col className="w-[20%]" />
										<col className="w-12" />
									</colgroup>
									<thead>
										<tr className="border-b border-slate-200 bg-slate-50 text-left text-xs font-medium text-slate-600">
											<th scope="col" className="px-2 py-2 text-center">
												序号
											</th>
											<th scope="col" className="px-1 py-2">
												技术编码
											</th>
											<th scope="col" className="px-1 py-2">
												业务名称
											</th>
											<th scope="col" className="px-1 py-2">
												数据类型
											</th>
											<th scope="col" className="px-1 py-2">
												字段作用
											</th>
											<th scope="col" className="px-1 py-2">
												允许为空
											</th>
											<th scope="col" className="px-1 py-2">
												维度属性编码
											</th>
											<th scope="col" className="px-1 py-2">
												操作
											</th>
										</tr>
									</thead>
									{fields.map((field, index) => {
										const protectedName = isProtectedModelFieldName(
											fieldValues?.[field.name]?.name,
											persistedFieldNames,
											grainKeysText,
										);
										return (
											<tbody key={field.key} className="border-b border-slate-100 last:border-b-0">
												<tr className="align-top">
													<td className="px-2 py-2 text-center">
														<Text type="secondary" className="text-xs">
															{index + 1}
														</Text>
													</td>
													<td className="px-1 py-2">
														<Form.Item
															name={[field.name, "name"]}
															className="!mb-0"
															rules={[
																{ required: true, whitespace: true, message: "请输入技术编码" },
																{
																	pattern: /^[a-z][a-z0-9_]{0,62}$/,
																	message: "使用小写英文、数字和下划线，最长 63 个字符",
																},
															]}
														>
															<Input
																size="small"
																aria-label={`字段 ${index + 1} 技术编码`}
																disabled={readOnly || protectedName}
																title={protectedName ? "请先解除该字段的粒度或标准引用" : undefined}
																placeholder="customer_id"
															/>
														</Form.Item>
													</td>
													<td className="px-1 py-2">
														<Form.Item
															name={[field.name, "displayName"]}
															className="!mb-0"
															rules={[{ required: true, whitespace: true, message: "请输入业务名称" }]}
														>
															<Input
																size="small"
																aria-label={`字段 ${index + 1} 业务名称`}
																disabled={readOnly}
																placeholder="客户编号"
															/>
														</Form.Item>
													</td>
													<td className="px-1 py-2">
														<Form.Item
															name={[field.name, "dataType"]}
															className="!mb-0"
															rules={[{ required: true, whitespace: true, message: "请输入数据类型" }]}
														>
															<Input
																size="small"
																aria-label={`字段 ${index + 1} 数据类型`}
																disabled={readOnly}
																placeholder="string"
															/>
														</Form.Item>
													</td>
													<td className="px-1 py-2">
														<Form.Item
															name={[field.name, "role"]}
															className="!mb-0"
															rules={[{ required: true, message: "请选择字段作用" }]}
														>
															<Select
																size="small"
																aria-label={`字段 ${index + 1} 字段作用`}
																disabled={readOnly}
																options={fieldRoleOptions}
															/>
														</Form.Item>
													</td>
													<td className="px-1 py-2">
														<Form.Item name={[field.name, "nullable"]} valuePropName="checked" className="!mb-0 pt-1">
															<Checkbox aria-label={`字段 ${index + 1} 允许为空`} disabled={readOnly}>
																是
															</Checkbox>
														</Form.Item>
													</td>
													<td className="px-1 py-2">
														<Form.Item name={[field.name, "dimensionAttributeCode"]} className="!mb-0">
															<AutoComplete
																size="small"
																aria-label={`字段 ${index + 1} 维度属性编码`}
																disabled={readOnly}
																options={dimensionAttributeOptions}
																placeholder={modelType === "DIMENSION" ? "选择或输入属性编码" : "可选"}
																filterOption={(input, option) =>
																	String(option?.label || "")
																		.toLowerCase()
																		.includes(input.toLowerCase())
																}
															/>
														</Form.Item>
													</td>
													<td className="px-1 py-2">
														{!readOnly ? (
															<Button
																size="small"
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
													</td>
												</tr>
												<tr>
													<td
														colSpan={8}
														className="border-t border-dashed border-slate-100 bg-slate-50/60 px-3 py-1.5"
													>
														<Form.Item noStyle shouldUpdate>
															{({ getFieldError }) => {
																const governanceHasErrors = [
																	"sourceFieldRef",
																	"securityLevel",
																	"redundant",
																	"redundancySourceRef",
																].some((property) => getFieldError(["fields", field.name, property]).length > 0);
																return (
																	<details
																		className="group"
																		open={governanceHasErrors || expandedGovernanceRows.has(field.key)}
																		onToggle={(event) => {
																			const open = event.currentTarget.open;
																			setExpandedGovernanceRows((current) => {
																				const next = new Set(current);
																				if (open) next.add(field.key);
																				else if (!governanceHasErrors) next.delete(field.key);
																				return next;
																			});
																		}}
																	>
																		<summary className="cursor-pointer select-none text-xs text-slate-500 hover:text-slate-700">
																			<span className="inline-flex items-center gap-2">
																				来源与治理
																				{governanceHasErrors ? (
																					<Text type="danger" className="text-xs">
																						待修正
																					</Text>
																				) : null}
																			</span>
																		</summary>
																		<div className="grid grid-cols-4 gap-3 py-3">
																			<Form.Item
																				name={[field.name, "sourceFieldRef"]}
																				label="来源字段"
																				extra="记录输出字段对应的输入字段"
																				className="!mb-0"
																			>
																				<Input
																					size="small"
																					aria-label={`字段 ${index + 1} 来源字段`}
																					disabled={readOnly}
																					placeholder="ods_customer.id"
																				/>
																			</Form.Item>
																			<Form.Item
																				name={[field.name, "securityLevel"]}
																				label="安全等级"
																				className="!mb-0"
																			>
																				<Input
																					size="small"
																					aria-label={`字段 ${index + 1} 安全等级`}
																					disabled={readOnly}
																					placeholder="可选"
																				/>
																			</Form.Item>
																			<Form.Item
																				name={[field.name, "redundant"]}
																				label="冗余维度字段"
																				valuePropName="checked"
																				className="!mb-0"
																			>
																				<Checkbox aria-label={`字段 ${index + 1} 冗余维度字段`} disabled={readOnly}>
																					是
																				</Checkbox>
																			</Form.Item>
																			<Form.Item
																				noStyle
																				shouldUpdate={(previous, current) =>
																					previous?.fields?.[field.name]?.redundant !==
																					current?.fields?.[field.name]?.redundant
																				}
																			>
																				{({ getFieldValue }) => {
																					const redundant = Boolean(getFieldValue(["fields", field.name, "redundant"]));
																					return (
																						<Form.Item
																							name={[field.name, "redundancySourceRef"]}
																							label="冗余来源依据"
																							extra="记录冗余值来自哪个上游模型或来源字段"
																							rules={[
																								{
																									required: redundant,
																									whitespace: true,
																									message: "请填写冗余字段的来源依据",
																								},
																							]}
																							className="!mb-0"
																						>
																							<Input
																								size="small"
																								aria-label={`字段 ${index + 1} 冗余来源依据`}
																								disabled={readOnly || !redundant}
																								placeholder="dim_project.project_code"
																							/>
																						</Form.Item>
																					);
																				}}
																			</Form.Item>
																		</div>
																	</details>
																);
															}}
														</Form.Item>
													</td>
												</tr>
											</tbody>
										);
									})}
								</table>
							</div>
						) : null}
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
					</Space>
				)}
			</Form.List>
		</div>
	);
}
