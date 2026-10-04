import { InboxOutlined } from "@ant-design/icons";
import { Alert, Button, Form, InputNumber, Select, Space, Tag, Typography } from "antd";
import type { FormInstance } from "antd/es/form";
import type { FileUploadResult } from "@/api/ingestion";
import { Upload } from "@/components/upload";
import {
	CLASSIFICATION_LABELS_ZH,
	type ClassificationLevel,
	classificationRank,
	normalizeClassification,
} from "@/utils/classification";
import { resolveFileAdmissionState, setFileFieldClassification } from "./fileClassificationAdmission.helpers";

const { Text } = Typography;

export const FILE_CLASSIFICATION_OPTIONS: Array<{
	label: string;
	value: ClassificationLevel;
}> = (["PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL"] as ClassificationLevel[]).map((value) => ({
	label: `${CLASSIFICATION_LABELS_ZH[value]} (${value})`,
	value,
}));

type FileClassificationIntakeProps = {
	form: FormInstance;
	fileUploadResult: FileUploadResult | null;
	setFileUploadResult: (result: FileUploadResult | null) => void;
	uploadingFile: boolean;
	onFileUpload: (file: File, onSuccess?: (result: any) => void, onError?: (error: any) => void) => void;
};

type ParsedFileSummaryProps = {
	file: FileUploadResult;
	onSheetChange: (sheetIndex: number) => void;
	previewRows: number;
	setPreviewRows: (value: number) => void;
	previewCols: number;
	setPreviewCols: (value: number) => void;
	refreshPreview: () => void;
	previewRefreshing: boolean;
	openErrorPreview: () => void;
	errorPreviewLoading: boolean;
};

export function ParsedFileSummary({
	file,
	onSheetChange,
	previewRows,
	setPreviewRows,
	previewCols,
	setPreviewCols,
	refreshPreview,
	previewRefreshing,
	openErrorPreview,
	errorPreviewLoading,
}: ParsedFileSummaryProps) {
	return (
		<>
			<Text type="secondary" className="block mb-2">
				文件类型: <Tag>{file.sourceFileType || file.fileType}</Tag>
				检测到 {file.columns?.length || 0} 列
				{typeof file.rowCount === "number" ? (
					<>
						{" · "}预览总行数: <Tag color="blue">{file.rowCount}</Tag>
					</>
				) : null}
				{typeof file.errorCount === "number" ? (
					<>
						{" · "}错误行: <Tag color={file.errorCount > 0 ? "red" : "green"}>{file.errorCount}</Tag>
					</>
				) : null}
			</Text>
			{Array.isArray(file.sheets) && file.sheets.length > 1 ? (
				<Space className="mb-3" wrap>
					<Text type="secondary">选择 Sheet：</Text>
					<Select
						style={{ minWidth: 200 }}
						value={file.sheetIndex}
						options={file.sheets.map((sheet) => ({ label: sheet.name, value: sheet.index }))}
						onChange={onSheetChange}
					/>
				</Space>
			) : null}
			<Space className="mb-3" wrap>
				<Text type="secondary">预览行数</Text>
				<InputNumber
					min={1}
					max={2000}
					value={previewRows}
					onChange={(value) => setPreviewRows(value ? Number(value) : 20)}
				/>
				<Text type="secondary">预览列数</Text>
				<InputNumber
					min={1}
					max={50}
					value={previewCols}
					onChange={(value) => setPreviewCols(value ? Number(value) : 8)}
				/>
				<Button size="small" onClick={refreshPreview} loading={previewRefreshing}>
					刷新预览
				</Button>
				{(file.errorCount || 0) > 0 ? (
					<Button size="small" onClick={openErrorPreview} loading={errorPreviewLoading}>
						查看错误行
					</Button>
				) : null}
			</Space>
		</>
	);
}

type FileFieldClassificationSelectProps = {
	file: FileUploadResult;
	fieldName: string;
	onChange: (file: FileUploadResult) => void;
};

export function FileFieldClassificationSelect({ file, fieldName, onChange }: FileFieldClassificationSelectProps) {
	const fileFloor = normalizeClassification(
		file.classification || file.classificationSeal?.fileFloor || file.classificationSeal?.effectiveLevel,
		undefined,
	);
	const value = normalizeClassification(file.fieldClassifications?.[fieldName], undefined) || fileFloor;
	return (
		<Select
			size="small"
			value={value}
			style={{ width: "100%" }}
			options={FILE_CLASSIFICATION_OPTIONS.map((option) => ({
				...option,
				disabled:
					Boolean(fileFloor) && (classificationRank(option.value) ?? -1) < (classificationRank(fileFloor) ?? -1),
			}))}
			onChange={(nextLevel) => onChange(setFileFieldClassification(file, fieldName, nextLevel))}
		/>
	);
}

export default function FileClassificationIntake({
	form,
	fileUploadResult,
	setFileUploadResult,
	uploadingFile,
	onFileUpload,
}: FileClassificationIntakeProps) {
	const selectedClassification = Form.useWatch("fileClassification", form);
	const normalizedSelection = normalizeClassification(selectedClassification, undefined);
	const admissionState = resolveFileAdmissionState(fileUploadResult);

	return (
		<>
			<Alert
				type="info"
				showIcon
				className="mb-4"
				message="上传前必须选择文件密级"
				description="文件密级是所有字段的最低密级；解析后可逐字段升密，但不能降低。"
			/>
			<Form.Item name="fileClassification" label="文件密级" rules={[{ required: true, message: "请选择文件密级" }]}>
				<Select
					placeholder="请选择文件密级后上传"
					options={FILE_CLASSIFICATION_OPTIONS}
					onChange={(value) => {
						const next = normalizeClassification(value, undefined);
						if (fileUploadResult && next !== fileUploadResult.classification) {
							setFileUploadResult(null);
						}
					}}
				/>
			</Form.Item>
			<Form.Item label="上传文件" required>
				<Upload
					secretModule
					accept=".xlsx,.csv"
					maxCount={1}
					showUploadList={false}
					customRequest={async ({ file, onSuccess, onError }) => {
						onFileUpload(file as File, onSuccess, onError);
					}}
					disabled={uploadingFile || !normalizedSelection}
				>
					<p className="ant-upload-drag-icon">
						<InboxOutlined />
					</p>
					<p className="ant-upload-text">{uploadingFile ? "上传中..." : "点击或拖拽上传 Excel / CSV 文件"}</p>
					<p className="ant-upload-hint">{normalizedSelection ? "支持 .xlsx, .csv 格式" : "请先选择文件密级"}</p>
				</Upload>
			</Form.Item>
			{fileUploadResult ? (
				<Alert
					className="mb-4"
					showIcon
					type={admissionState.ready ? "success" : "error"}
					message={admissionState.reason}
					description={
						admissionState.classification
							? `文件密级：${CLASSIFICATION_LABELS_ZH[admissionState.classification]}`
							: undefined
					}
				/>
			) : null}
		</>
	);
}
