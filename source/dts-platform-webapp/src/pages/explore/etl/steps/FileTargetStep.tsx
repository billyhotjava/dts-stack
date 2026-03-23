import {
	Alert,
	Button,
	Collapse,
	Divider,
	Form,
	Input,
	Select,
	Switch,
	Table,
	Typography,
} from "antd";
import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import type { IngestionFormContext } from "./types";
import type { FileUploadResult } from "@/api/ingestion";
import {
	normalizeText,
	normalizeTableName,
	buildFileBaseName,
} from "../ingestionFormHelpers";

const { Text } = Typography;

export type FileTargetStepProps = Pick<
	IngestionFormContext,
	| "form"
	| "defaultDestinationStatus"
	| "extraColumns"
	| "setExtraColumns"
> & {
	fileUploadResult: FileUploadResult | null;
};

const fileTableNameValidator = (_: any, value: string) => {
	const text = normalizeText(value);
	if (!text) {
		return Promise.resolve();
	}
	const normalized = normalizeTableName(text);
	if (!normalized) {
		return Promise.reject(new Error("目标表名仅支持字母、数字、下划线，可包含 schema"));
	}
	return Promise.resolve();
};

export const FileTargetStep: React.FC<FileTargetStepProps> = ({
	form,
	defaultDestinationStatus,
	extraColumns,
	setExtraColumns,
	fileUploadResult,
}) => {
	return (
		<>
			<Divider orientation="left">目标配置</Divider>
			{defaultDestinationStatus ? (
				<Alert
					type={defaultDestinationStatus.available ? "success" : "warning"}
					showIcon
					message="默认数据湖"
					description={[
						defaultDestinationStatus.destinationName
							? `数据湖：${defaultDestinationStatus.destinationName}`
							: null,
						defaultDestinationStatus.writerType
							? `Writer：${defaultDestinationStatus.writerType}`
							: null,
						defaultDestinationStatus.message ? defaultDestinationStatus.message : null,
					]
						.filter(Boolean)
						.join(" · ")}
					className="mb-4"
				/>
			) : null}
			<Form.Item
				name="fileTableName"
				label="目标表名"
				rules={[{ validator: fileTableNameValidator }]}
				tooltip="仅允许字母、数字、下划线，可包含 schema.table"
			>
				<Input placeholder="例如：ods_patent_info" />
			</Form.Item>
			<Form.Item name="syncPrefix" label="目标表前缀">
				<Input placeholder="例如：ods_erp_" />
			</Form.Item>
			<Form.Item
				name="fileAutoId"
				label="自动生成ID"
				valuePropName="checked"
				tooltip="为文件入湖的目标表追加自增 ID 字段（默认开启）"
			>
				<Switch />
			</Form.Item>
			<Text type="secondary" className="block -mt-3 mb-4">
				未填写目标表名时，系统将使用：前缀 + 文件名（去除扩展名）。例如：ods_erp_ + sales_data → ods_erp_sales_data
			</Text>
			<Collapse
				ghost
				className="mb-4"
				items={[
					{
						key: "file-column-rules",
						label: "字段规则（可选）",
						children: (
							<div className="space-y-4">
								<div className="grid gap-4 md:grid-cols-2">
									<Form.Item name="columnPrefix" label="字段名前缀">
										<Input placeholder="例如：src_" />
									</Form.Item>
									<Form.Item name="columnSuffix" label="字段名后缀">
										<Input placeholder="例如：_raw" />
									</Form.Item>
								</div>
								<Text type="secondary" className="block -mt-2 mb-2">
									对目标表所有字段统一添加前缀/后缀。留空则使用文件原始字段名。
								</Text>
								<Divider orientation="left" plain>
									追加字段
								</Divider>
								<Table
									size="small"
									dataSource={extraColumns}
									rowKey={(_: any, index: any) => String(index)}
									pagination={false}
									locale={{ emptyText: "暂无追加字段" }}
									columns={[
										{
											title: "字段名",
											dataIndex: "name",
											render: (value: string, _: any, index: number) => (
												<Input
													size="small"
													value={value}
													placeholder="英文字段名"
													onChange={(e) => {
														const cols = [...extraColumns];
														cols[index] = { ...cols[index], name: e.target.value };
														setExtraColumns(cols);
													}}
												/>
											),
										},
										{
											title: "显示名称",
											dataIndex: "label",
											render: (value: string, _: any, index: number) => (
												<Input
													size="small"
													value={value}
													placeholder="中文名"
													onChange={(e) => {
														const cols = [...extraColumns];
														cols[index] = { ...cols[index], label: e.target.value };
														setExtraColumns(cols);
													}}
												/>
											),
										},
										{
											title: "数据类型",
											dataIndex: "type",
											width: 160,
											render: (value: string, _: any, index: number) => (
												<Select
													size="small"
													value={value}
													style={{ width: "100%" }}
													onChange={(v) => {
														const cols = [...extraColumns];
														cols[index] = { ...cols[index], type: v };
														setExtraColumns(cols);
													}}
													options={[
														{ label: "VARCHAR", value: "string" },
														{ label: "TEXT", value: "text" },
														{ label: "INTEGER", value: "integer" },
														{ label: "BIGINT", value: "long" },
														{ label: "TIMESTAMP", value: "timestamp" },
														{ label: "BOOLEAN", value: "boolean" },
													]}
												/>
											),
										},
										{
											title: "默认值 (SQL)",
											dataIndex: "defaultValue",
											width: 180,
											render: (value: string, _: any, index: number) => (
												<Input
													size="small"
													value={value}
													placeholder="CURRENT_TIMESTAMP"
													onChange={(e) => {
														const cols = [...extraColumns];
														cols[index] = { ...cols[index], defaultValue: e.target.value };
														setExtraColumns(cols);
													}}
												/>
											),
										},
										{
											title: "操作",
											width: 50,
											align: "center" as const,
											render: (_: any, __: any, index: number) => (
												<Button
													type="text"
													danger
													size="small"
													icon={<DeleteOutlined />}
													onClick={() => {
														const cols = [...extraColumns];
														cols.splice(index, 1);
														setExtraColumns(cols);
													}}
												/>
											),
										},
									]}
								/>
								<Button
									type="dashed"
									size="small"
									icon={<PlusOutlined />}
									onClick={() => {
										setExtraColumns([
											...extraColumns,
											{ name: "", label: "", type: "string", defaultValue: "" },
										]);
									}}
								>
									添加字段
								</Button>
							</div>
						),
					},
				]}
			/>
			{fileUploadResult && (
				<Alert
					type="info"
					showIcon
					message={`目标表预览：${normalizeText(form.getFieldValue("fileTableName")) || (normalizeText(form.getFieldValue("syncPrefix")) + buildFileBaseName(fileUploadResult.originalName))}`}
					className="mb-4"
				/>
			)}
			<Divider orientation="left">数据湖连接（可选覆盖）</Divider>
			<Text type="secondary" className="block mb-4">
				若默认数据湖凭据不可用，可在此处手动指定目标库连接信息。留空则使用默认数据湖配置。
			</Text>
			<div className="grid gap-4 md:grid-cols-2">
				<Form.Item name="writerUsername" label="用户名">
					<Input placeholder="数据库账号" />
				</Form.Item>
				<Form.Item name="writerPassword" label="密码">
					<Input.Password placeholder="数据库密码" />
				</Form.Item>
			</div>
			<Form.Item name="writerJdbcUrls" label="JDBC URL（可选覆盖）">
				<Input placeholder="jdbc:postgresql://host:5432/db" />
			</Form.Item>
			<Form.Item name="writerSchema" label="Schema（可选）">
				<Input placeholder="例如 public" />
			</Form.Item>
		</>
	);
};

export default FileTargetStep;
