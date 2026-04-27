import {
	Alert,
	Divider,
	Form,
	Input,
	Switch,
	Typography,
} from "antd";
import type { IngestionFormContext } from "./types";
import type { FileUploadResult } from "@/api/ingestion";
import {
	normalizeText,
	normalizeTableName,
	buildFileBaseName,
} from "../ingestionFormHelpers";
import OdsLandingContractCard from "./OdsLandingContractCard";

const { Text } = Typography;

export type FileTargetStepProps = Pick<
	IngestionFormContext,
	| "form"
	| "defaultDestinationStatus"
	| "extraColumns"
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

export const FileTargetStep = ({
	form,
	defaultDestinationStatus,
	extraColumns,
	fileUploadResult,
}: FileTargetStepProps) => {
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
			<OdsLandingContractCard
				sourceKind="file"
				columnPrefix={form.getFieldValue("columnPrefix")}
				columnSuffix={form.getFieldValue("columnSuffix")}
				extraColumns={extraColumns}
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
