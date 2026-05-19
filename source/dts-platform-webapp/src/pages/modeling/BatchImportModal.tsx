import { useState, useCallback } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Checkbox,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Tabs,
	Tag,
} from "antd";
import { CompactTable } from "@/components/table";
import { InboxOutlined, } from "@ant-design/icons";
import { Upload } from "@/components/upload";
import type { UploadFile } from "antd/es/upload/interface";
import type { ColumnsType } from "antd/es/table";
import JSZip from "jszip";
import { batchImportSqlModels } from "@/api/platformApi";
import { extractImportedModelNames } from "./batchImportNavigation.helpers";

interface BatchImportModalProps {
	open: boolean;
	onClose: () => void;
	onSuccess: (payload: { planId?: string; importedModelNames: string[] }) => void;
	spaces: Array<{ id?: string; name?: string }>;
	dataSources: Array<{ id?: string; name?: string }>;
	activeSpaceId?: string;
}

interface FileRow {
	key: string;
	fileName: string;
	name: string;
	layer: string;
	materialized: string;
	tags: string;
	file: File;
}

interface ResultRow {
	name: string;
	layer: string;
	status: string;
	message: string;
}

const LAYER_OPTIONS = [
	{ label: "ODS", value: "ODS" },
	{ label: "STG", value: "STG" },
	{ label: "DWD", value: "DWD" },
	{ label: "DWS", value: "DWS" },
	{ label: "ADS", value: "ADS" },
];

const MATERIALIZED_OPTIONS = [
	{ label: "table", value: "table" },
	{ label: "view", value: "view" },
	{ label: "incremental", value: "incremental" },
];

const inferLayer = (filename: string): string => {
	const name = filename.replace(/\.sql$/i, "").toLowerCase();
	if (name.startsWith("ads_") || name.startsWith("biz_ads_")) return "ADS";
	if (name.startsWith("dws_") || name.startsWith("biz_dws_")) return "DWS";
	if (name.startsWith("stg_")) return "STG";
	if (name.startsWith("dim_")) return "DWD";
	return "DWD";
};

const downloadTemplate = () => {
	const header =
		"name\tlayer\tsql_path\tsource_data_source_id\talias\tschema_name\tmaterialized\ttags\tstatus\tenabled\towner_dept\tdescription\tcsv_path\n";
	const blob = new Blob([header], { type: "text/tab-separated-values" });
	const url = URL.createObjectURL(blob);
	const a = document.createElement("a");
	a.href = url;
	a.download = "models.tsv";
	a.click();
	URL.revokeObjectURL(url);
};

const BatchImportModal = ({
	open,
	onClose,
	onSuccess,
	spaces,
	dataSources,
	activeSpaceId,
}: BatchImportModalProps) => {
	const [activeTab, setActiveTab] = useState("zip");
	const [zipForm] = Form.useForm();
	const [fileForm] = Form.useForm();
	const [submitting, setSubmitting] = useState(false);
	const [zipFileList, setZipFileList] = useState<UploadFile[]>([]);
	const [fileRows, setFileRows] = useState<FileRow[]>([]);
	const [results, setResults] = useState<ResultRow[] | null>(null);

	const resetState = useCallback(() => {
		setActiveTab("zip");
		zipForm.resetFields();
		fileForm.resetFields();
		setZipFileList([]);
		setFileRows([]);
		setResults(null);
		setSubmitting(false);
	}, [zipForm, fileForm]);

	const handleClose = () => {
		resetState();
		onClose();
	};

	const handleAfterOpenChange = (visible: boolean) => {
		if (visible) {
			const firstDataSourceId = dataSources.find((item) => !!item?.id)?.id;
			const currentZipSourceId = zipForm.getFieldValue("sourceDataSourceId");
			const currentFileSourceId = fileForm.getFieldValue("sourceDataSourceId");
			const validZipSourceId = dataSources.some((item) => item?.id === currentZipSourceId);
			const validFileSourceId = dataSources.some((item) => item?.id === currentFileSourceId);
			const nextZipValues: Record<string, string> = {};
			const nextFileValues: Record<string, string> = {};
			if (activeSpaceId) {
				nextZipValues.planId = activeSpaceId;
				nextFileValues.planId = activeSpaceId;
			}
			if (!validZipSourceId && firstDataSourceId) {
				nextZipValues.sourceDataSourceId = firstDataSourceId;
			}
			if (!validFileSourceId && firstDataSourceId) {
				nextFileValues.sourceDataSourceId = firstDataSourceId;
			}
			if (Object.keys(nextZipValues).length > 0) {
				zipForm.setFieldsValue(nextZipValues);
			}
			if (Object.keys(nextFileValues).length > 0) {
				fileForm.setFieldsValue(nextFileValues);
			}
		}
	};

	const handleSqlFilesSelected = (files: File[]) => {
		const rows: FileRow[] = files.map((file, idx) => {
			const baseName = file.name.replace(/\.sql$/i, "");
			return {
				key: `${file.name}_${idx}`,
				fileName: file.name,
				name: baseName,
				layer: inferLayer(file.name),
				materialized: "table",
				tags: "",
				file,
			};
		});
		setFileRows((prev) => [...prev, ...rows]);
	};

	const updateRow = (key: string, field: keyof FileRow, value: string) => {
		setFileRows((prev) => prev.map((r) => (r.key === key ? { ...r, [field]: value } : r)));
	};

	const showResults = (data: any, planId?: string) => {
		const items: ResultRow[] = Array.isArray(data?.details) ? data.details : [];
		setResults(items);
		const importedModelNames = extractImportedModelNames(items);
		const imported = importedModelNames.length;
		const skipped = items.filter((r) => r.status === "skipped").length;
		const failed = items.length - imported - skipped;
		toast.success(`批量导入完成: ${imported} 已导入, ${skipped} 已跳过, ${failed} 失败`);
		onSuccess({ planId, importedModelNames });
	};

	const submitZip = async () => {
		try {
			const values = await zipForm.validateFields();
			if (zipFileList.length === 0 || !zipFileList[0].originFileObj) {
				toast.error("请选择 ZIP 文件");
				return;
			}
			setSubmitting(true);
			const formData = new FormData();
			formData.append("planId", values.planId);
			formData.append("sourceDataSourceId", values.sourceDataSourceId);
			formData.append("skipExisting", values.skipExisting ? "true" : "false");
			formData.append("cleanOldFiles", values.cleanOldFiles ? "true" : "false");
			formData.append("archive", zipFileList[0].originFileObj);
			const res = await batchImportSqlModels(formData);
			showResults(res, values.planId);
		} catch (err: any) {
			if (err?.errorFields) return; // form validation
		} finally {
			setSubmitting(false);
		}
	};

	const submitFiles = async () => {
		try {
			const values = await fileForm.validateFields();
			if (fileRows.length === 0) {
				toast.error("请添加 SQL 文件");
				return;
			}
			setSubmitting(true);

			const zip = new JSZip();

			// Build TSV
			const tsvHeader =
				"name\tlayer\tsql_path\tsource_data_source_id\talias\tschema_name\tmaterialized\ttags\tstatus\tenabled\towner_dept\tdescription\tcsv_path";
			const tsvRows = fileRows.map(
				(r) =>
					`${r.name}\t${r.layer}\t${r.fileName}\t${values.sourceDataSourceId}\t\t\t${r.materialized}\t${r.tags}\t\ttrue\t\t\t`,
			);
			const tsvContent = [tsvHeader, ...tsvRows].join("\n") + "\n";
			zip.file("models.tsv", tsvContent);

			// Add SQL files
			for (const row of fileRows) {
				const content = await row.file.text();
				zip.file(row.fileName, content);
			}

			const blob = await zip.generateAsync({ type: "blob" });
			const formData = new FormData();
			formData.append("planId", values.planId);
			formData.append("sourceDataSourceId", values.sourceDataSourceId);
			formData.append("skipExisting", values.skipExisting ? "true" : "false");
			formData.append("cleanOldFiles", values.cleanOldFiles ? "true" : "false");
			formData.append("archive", blob, "batch-import.zip");
			const res = await batchImportSqlModels(formData);
			showResults(res, values.planId);
		} catch (err: any) {
			if (err?.errorFields) return;
		} finally {
			setSubmitting(false);
		}
	};

	const fileColumns: ColumnsType<FileRow> = [
		{
			title: "模型名称",
			dataIndex: "name",
			sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
			render: (val: string, record: FileRow) => (
				<Input
					size="small"
					value={val}
					onChange={(e) => updateRow(record.key, "name", e.target.value)}
				/>
			),
		},
		{
			title: "分层",
			dataIndex: "layer",
			width: 120,
			render: (val: string, record: FileRow) => (
				<Select
					size="small"
					value={val}
					options={LAYER_OPTIONS}
					style={{ width: "100%" }}
					onChange={(v) => updateRow(record.key, "layer", v)}
				/>
			),
		},
		{
			title: "物化方式",
			dataIndex: "materialized",
			width: 140,
			render: (val: string, record: FileRow) => (
				<Select
					size="small"
					value={val}
					options={MATERIALIZED_OPTIONS}
					style={{ width: "100%" }}
					onChange={(v) => updateRow(record.key, "materialized", v)}
				/>
			),
		},
		{
			title: "标签",
			dataIndex: "tags",
			render: (val: string, record: FileRow) => (
				<Input
					size="small"
					value={val}
					placeholder="逗号分隔"
					onChange={(e) => updateRow(record.key, "tags", e.target.value)}
				/>
			),
		},
		{
			title: "操作",
			width: 60,
			render: (_: unknown, record: FileRow) => (
				<Button
					type="link"
					danger
					size="small"
					onClick={() => setFileRows((prev) => prev.filter((r) => r.key !== record.key))}
				>
					删除
				</Button>
			),
		},
	];

	const resultColumns: ColumnsType<ResultRow> = [
		{ title: "模型名称", dataIndex: "name" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
		{ title: "分层", dataIndex: "layer", width: 80 },
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
			render: (val: string) => {
				const colorMap: Record<string, string> = {
					imported: "green",
					skipped: "orange",
					failed: "red",
				};
				return <Tag color={colorMap[val] || "default"}>{val}</Tag>;
			},
		},
		{ title: "说明", dataIndex: "message" },
	];

	const renderFormFields = (form: ReturnType<typeof Form.useForm>[0]) => (
		<Form layout="vertical" form={form} disabled={submitting}>
			<div className="grid gap-4 md:grid-cols-2">
				<Form.Item
					name="planId"
					label="项目空间"
					rules={[{ required: true, message: "请选择项目空间" }]}
				>
					<Select
						placeholder="选择项目空间"
						options={spaces.map((s) => ({ label: s.name || "未命名", value: s.id }))}
					/>
				</Form.Item>
				<Form.Item
					name="sourceDataSourceId"
					label="来源数据源"
					rules={[{ required: true, message: "请选择来源数据源" }]}
				>
					<Select
						placeholder="选择来源数据源"
						options={dataSources.map((ds) => ({ label: ds?.name || ds?.id, value: ds?.id }))}
					/>
				</Form.Item>
			</div>
			<Form.Item name="skipExisting" valuePropName="checked" initialValue={false}>
				<Checkbox>跳过已存在的模型</Checkbox>
			</Form.Item>
			<Form.Item name="cleanOldFiles" valuePropName="checked" initialValue={false}>
				<Checkbox>清理旧版文件（删除目标目录中不在此 ZIP 包内的旧 yml/macro 文件）</Checkbox>
			</Form.Item>
		</Form>
	);

	if (results) {
		return (
			<Modal
				open={open}
				title="批量导入结果"
				width={720}
				onCancel={handleClose}
				footer={
					<Button type="primary" onClick={handleClose}>
						关闭
					</Button>
				}
			>
				<CompactTable
					dataSource={results.map((r, i) => ({ ...r, key: i }))}
					columns={resultColumns}
					size="small"
					pagination={false}
				/>
			</Modal>
		);
	}

	return (
		<Modal
			open={open}
			title="批量导入模型"
			width={800}
			onCancel={handleClose}
			afterOpenChange={handleAfterOpenChange}
			footer={null}
		>
			<Alert type="warning" showIcon message="非密模块禁止上传涉密数据" style={{ marginBottom: 16 }} />
			<Tabs
				activeKey={activeTab}
				onChange={setActiveTab}
				items={[
					{
						key: "zip",
						label: "ZIP 上传",
						children: (
							<>
								{renderFormFields(zipForm)}
								<Upload
									accept=".zip"
									beforeUpload={() => false}
									maxCount={1}
									fileList={zipFileList}
									onChange={({ fileList }) => setZipFileList(fileList.slice(-1))}
								>
									<p className="ant-upload-drag-icon">
										<InboxOutlined />
									</p>
									<p className="ant-upload-text">点击或拖拽 ZIP 文件到此区域</p>
									<p className="ant-upload-hint">
										ZIP 包含 models.tsv 和对应 SQL 文件
									</p>
								</Upload>
								<Space style={{ marginTop: 16 }}>
									<Button
										onClick={downloadTemplate}
									>
										下载 TSV 模板
									</Button>
									<Button
										type="primary"
										onClick={submitZip}
										loading={submitting}
									>
										导入
									</Button>
								</Space>
							</>
						),
					},
					{
						key: "files",
						label: "文件选择",
						children: (
							<>
								{renderFormFields(fileForm)}
								<Upload
									dragger={false}
									accept=".sql"
									multiple
									beforeUpload={(file, fileList) => {
										// Only handle once for the whole batch
										if (file === fileList[0]) {
											handleSqlFilesSelected(
												fileList.map((f) => f as unknown as File),
											);
										}
										return false;
									}}
									showUploadList={false}
								>
									<Button>选择 SQL 文件</Button>
								</Upload>
								{fileRows.length > 0 && (
									<CompactTable
										dataSource={fileRows}
										columns={fileColumns}
										size="small"
										pagination={false}
										style={{ marginTop: 16 }}
									/>
								)}
								<div style={{ marginTop: 16, textAlign: "right" }}>
									<Button
										type="primary"
										onClick={submitFiles}
										loading={submitting}
									>
										导入
									</Button>
								</div>
							</>
						),
					},
				]}
			/>
		</Modal>
	);
};

export default BatchImportModal;
