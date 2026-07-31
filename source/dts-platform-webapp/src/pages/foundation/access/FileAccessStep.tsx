import { Alert, Button, Form, Input, message, Select, Space, Switch, Tag, Typography, Upload as AntdUpload } from "antd";
import type { FormInstance } from "antd/es/form";
import type { ManagedFileUploadResult } from "@/api/ingestion";
import { CompactTable } from "@/components/table";
import { Upload as SecureUpload } from "@/components/upload";
import { CLASSIFICATION_LABELS_ZH, classificationRank, type ClassificationLevel } from "@/utils/classification";
import { FileFieldClassificationSelect } from "../../explore/etl/steps/FileClassificationIntake";
import { toAdmissionFile, toManagedFile } from "./accessManagedFile";
import type { AccessPlanFormValues } from "./accessPlan.types";

type Props = {
	form: FormInstance<AccessPlanFormValues>;
	phase?: "source" | "resource";
	fileUploadResult: ManagedFileUploadResult | null;
	onFileUploadResultChange: (file: ManagedFileUploadResult | null) => void;
	uploading: boolean;
	userClassificationRank?: number;
	onUpload: (file: File) => Promise<void>;
};

const MAX_FILE_SIZE = 50 * 1024 * 1024;
const ALLOWED_FILE_TYPES = new Set([
	"text/csv",
	"application/csv",
	"text/plain",
	"application/vnd.ms-excel",
	"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
	"application/zip",
	"application/octet-stream",
]);

const validateOfflineFile = (file: File) => {
	const extension = file.name.toLowerCase().match(/\.[^.]+$/)?.[0];
	if (extension !== ".csv" && extension !== ".xlsx") return "仅支持 .csv 和 .xlsx 文件";
	if (file.size <= 0) return "不能上传空文件";
	if (file.size > MAX_FILE_SIZE) return "文件不能超过 50MB";
	if (file.type && !ALLOWED_FILE_TYPES.has(file.type.toLowerCase())) return "文件 MIME 类型与允许格式不匹配";
	return null;
};

const CLASSIFICATION_OPTIONS = (Object.keys(CLASSIFICATION_LABELS_ZH) as ClassificationLevel[]).map((value) => ({
	value,
	label: `${CLASSIFICATION_LABELS_ZH[value]} · ${value}`,
}));

export function FileAccessStep({
	form: _form,
	phase = "source",
	fileUploadResult,
	onFileUploadResultChange,
	uploading,
	userClassificationRank,
	onUpload,
}: Props) {
	const classificationOptions = CLASSIFICATION_OPTIONS.map((option) => ({
		...option,
		disabled:
			userClassificationRank === undefined ||
			(classificationRank(option.value) ?? Number.POSITIVE_INFINITY) > userClassificationRank,
	}));
	if (phase === "resource") {
		return (
			<div className="space-y-5">
				<div>
					<Typography.Title level={4}>定义文件落地资源</Typography.Title>
					<Typography.Text type="secondary">目标表由文件名自动推导，也可以在此显式指定。</Typography.Text>
				</div>
				<Form.Item name="fileTargetTable" label="目标表名">
					<Input placeholder="例如：ods_customer" />
				</Form.Item>
				<Form.Item name="syncPrefix" label="ODS 表前缀">
					<Input placeholder="例如：ods_file_" />
				</Form.Item>
				<Form.Item name="fileAutoId" label="自动增加主键" valuePropName="checked">
					<Switch />
				</Form.Item>
				{fileUploadResult ? (
					<>
						<Alert
							type="info"
							showIcon
							message={`${fileUploadResult.originalName} · ${fileUploadResult.rowCount || 0} 行 · ${fileUploadResult.columns.length} 列`}
							description="字段结构来自平台加密上传后的解析结果；字段只允许在文件密级基础上升密。"
						/>
						<CompactTable
							size="small"
							rowKey="name"
							dataSource={fileUploadResult.columns}
							pagination={{ pageSize: 10, showSizeChanger: false }}
							columns={[
								{ title: "字段", dataIndex: "label", key: "label", render: (value, row) => value || row.name },
								{ title: "落地字段", dataIndex: "name", key: "name" },
								{ title: "类型", dataIndex: "type", key: "type" },
								{
									title: "字段密级",
									key: "classification",
									width: 220,
									render: (_value, row) => (
										<FileFieldClassificationSelect
											file={toAdmissionFile(fileUploadResult)}
											fieldName={row.name}
											onChange={(file) => onFileUploadResultChange(toManagedFile(file))}
										/>
									),
								},
							]}
						/>
					</>
				) : null}
			</div>
		);
	}

	return (
		<div className="space-y-5">
			<div>
				<Typography.Title level={4}>上传离线文件</Typography.Title>
				<Typography.Text type="secondary">先声明文件密级，再交由平台完成加密上传、解析和封存。</Typography.Text>
			</div>
			<div className="grid gap-4 md:grid-cols-2">
				<Form.Item name="name" label="任务名称" rules={[{ required: true, message: "请输入任务名称" }]}>
					<Input placeholder="例如：客户清单导入" />
				</Form.Item>
				<Form.Item
					name="fileClassification"
					label="文件密级"
					rules={[
						{ required: true, message: "请选择文件密级" },
						{
							validator: async (_rule, value) => {
								const selectedRank = classificationRank(value);
								if (userClassificationRank === undefined) {
									throw new Error("当前用户密级未下发，不能声明文件密级");
								}
								if (selectedRank === undefined || selectedRank > userClassificationRank) {
									throw new Error("所选文件密级超出当前用户权限");
								}
							},
						},
					]}
				>
					<Select
						disabled={uploading || userClassificationRank === undefined}
						options={classificationOptions}
						onChange={() => {
							if (fileUploadResult) onFileUploadResultChange(null);
						}}
					/>
				</Form.Item>
			</div>
			<Form.Item name="description" label="用途说明">
				<Input.TextArea rows={2} />
			</Form.Item>
			<Space direction="vertical" size={10}>
				{userClassificationRank === undefined ? (
					<Alert type="error" showIcon message="当前用户密级未下发，已禁止文件上传" />
				) : null}
				<SecureUpload
					secretModule
					userClassificationRank={userClassificationRank}
					accept=".xlsx,.csv"
					showUploadList={false}
					disabled={uploading || userClassificationRank === undefined}
					beforeUpload={(file) => {
						const validationError = validateOfflineFile(file as File);
						if (!validationError) return true;
						message.error(validationError);
						return AntdUpload.LIST_IGNORE;
					}}
					customRequest={({ file, onSuccess, onError }) => {
						void onUpload(file as File)
							.then(() => onSuccess?.({}))
							.catch((error: unknown) => {
								const uploadError = error instanceof Error ? error : new Error("文件上传失败");
								onError?.(uploadError);
							});
					}}
				>
					<Button type="primary" loading={uploading} disabled={userClassificationRank === undefined}>
						选择并上传文件
					</Button>
				</SecureUpload>
				<Typography.Text type="secondary">支持 CSV/XLSX，最大 50MB；服务端将再次校验内容并生成密级封存。</Typography.Text>
			</Space>
			{fileUploadResult ? (
				<Alert
					type="success"
					showIcon
					message={
						<Space>
							<span>{fileUploadResult.originalName}</span>
							<Tag color="blue">{fileUploadResult.classification}</Tag>
						</Space>
					}
					description={`已解析 ${fileUploadResult.columns.length} 个字段，密级封存已生成。`}
				/>
			) : null}
		</div>
	);
}
