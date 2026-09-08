import {
	Alert,
	Upload as AntdUpload,
	Button,
	Checkbox,
	Form,
	Input,
	message,
	Radio,
	Select,
	Space,
	Switch,
	Tag,
	Typography,
} from "antd";
import type { FormInstance } from "antd/es/form";
import { useEffect, useState } from "react";
import type { DefaultDestinationStatus, ManagedFileColumn, ManagedFileUploadResult } from "@/api/ingestion";
import type { InfraDataSource } from "@/api/services/dataSourcesService";
import { listColumns, listTables, type TableInfo } from "@/api/sql-workbench";
import { Upload as SecureUpload } from "@/components/upload";
import { CLASSIFICATION_LABELS_ZH, type ClassificationLevel, classificationRank } from "@/utils/classification";
import { toAdmissionFile, toManagedFile } from "./accessManagedFile";
import type { AccessPlanFormValues } from "./accessPlan.types";
import { FileFieldMappingEditor } from "./FileFieldMappingEditor";
import { FileFieldClassificationSelect } from "./shared/FileClassificationIntake";
import { applyTargetSchemaTemplate, type TargetSchemaColumn } from "./shared/fileTargetSchemaMapping";

type Props = {
	form: FormInstance<AccessPlanFormValues>;
	phase?: "source" | "resource";
	fileUploadResult: ManagedFileUploadResult | null;
	onFileUploadResultChange: (file: ManagedFileUploadResult | null) => void;
	uploading: boolean;
	userClassificationRank?: number;
	onUpload: (file: File) => Promise<void>;
	targetDataSources?: InfraDataSource[];
	defaultDestination?: DefaultDestinationStatus | null;
};

const MAX_FILE_SIZE = 50 * 1024 * 1024;
const ALLOWED_FILE_TYPES = new Set([
	"text/csv",
	"application/csv",
	"text/plain",
	"application/vnd.ms-excel",
	"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
	"application/wps-office.xlsx",
	"application/zip",
	"application/octet-stream",
]);

export const validateOfflineFile = (file: File) => {
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
	targetDataSources = [],
	defaultDestination,
}: Props) {
	const targetDataSourceId = Form.useWatch("targetDataSourceId", _form);
	const structureMode = Form.useWatch("fileStructureMode", _form) || "manual";
	const landingMode = Form.useWatch("fileLandingMode", _form) || "create_new";
	const modelTarget = Form.useWatch("modelTarget", {
		form: _form,
		preserve: true,
	}) as AccessPlanFormValues["modelTarget"];
	const [tableKeyword, setTableKeyword] = useState("");
	const [tableResults, setTableResults] = useState<TableInfo[]>([]);
	const [targetColumns, setTargetColumns] = useState<TargetSchemaColumn[]>([]);
	const [searchingTables, setSearchingTables] = useState(false);
	const [loadingColumns, setLoadingColumns] = useState(false);
	useEffect(() => {
		if (modelTarget)
			_form.setFieldsValue({
				fileTargetTable: `${modelTarget.schemaName}.${modelTarget.tableName}`,
				fileAutoId: false,
				fileLandingMode: "create_new",
				fileRecreateConfirmed: false,
			});
	}, [_form, modelTarget]);

	useEffect(() => {
		if (phase !== "resource" || structureMode !== "reference_existing" || !targetDataSourceId) return;
		const keyword = tableKeyword.trim();
		if (keyword.length < 2) {
			setTableResults([]);
			setSearchingTables(false);
			return;
		}
		let active = true;
		const timer = window.setTimeout(() => {
			setSearchingTables(true);
			void listTables(targetDataSourceId, { keyword, limit: 20 })
				.then((tables) => {
					if (active) setTableResults(Array.isArray(tables) ? tables : []);
				})
				.catch(() => {
					if (active) {
						setTableResults([]);
						message.error("目标表结构搜索失败");
					}
				})
				.finally(() => {
					if (active) setSearchingTables(false);
				});
		}, 300);
		return () => {
			active = false;
			window.clearTimeout(timer);
		};
	}, [phase, structureMode, tableKeyword, targetDataSourceId]);

	const updateFileColumns = (columns: ManagedFileColumn[]) => {
		if (!fileUploadResult) return;
		const nextClassifications = { ...(fileUploadResult.fieldClassifications || {}) };
		fileUploadResult.columns.forEach((previous, index) => {
			const next = columns[index];
			if (!next || previous.name === next.name || !nextClassifications[previous.name]) return;
			nextClassifications[next.name] = nextClassifications[previous.name];
			delete nextClassifications[previous.name];
		});
		onFileUploadResultChange({ ...fileUploadResult, columns, fieldClassifications: nextClassifications });
	};

	const selectReferenceTable = async (qualifiedTable: string) => {
		if (!targetDataSourceId || !fileUploadResult) return;
		const separator = qualifiedTable.indexOf(".");
		if (separator <= 0 || separator >= qualifiedTable.length - 1) {
			message.error("目标表标识无效");
			return;
		}
		const schema = qualifiedTable.slice(0, separator);
		const table = qualifiedTable.slice(separator + 1);
		_form.setFieldValue("fileReferenceTable", qualifiedTable);
		if (landingMode === "recreate_existing") _form.setFieldValue("fileTargetTable", qualifiedTable);
		setLoadingColumns(true);
		try {
			const columns = await listColumns(targetDataSourceId, schema, table);
			const schemaColumns = (Array.isArray(columns) ? columns : []).map((column) => ({
				...column,
				ordinalPosition: Number(column.ordinalPosition) || undefined,
			}));
			setTargetColumns(schemaColumns);
			updateFileColumns(applyTargetSchemaTemplate(fileUploadResult.columns, schemaColumns));
		} catch {
			setTargetColumns([]);
			message.error("读取目标表字段失败");
		} finally {
			setLoadingColumns(false);
		}
	};

	const classificationOptions = CLASSIFICATION_OPTIONS.map((option) => ({
		...option,
		disabled:
			userClassificationRank === undefined ||
			(classificationRank(option.value) ?? Number.POSITIVE_INFINITY) > userClassificationRank,
	}));
	if (phase === "resource") {
		const destinationAvailable = Boolean(
			defaultDestination?.available && defaultDestination.writerTypeReady && defaultDestination.writerConfigReady,
		);
		return (
			<div className="space-y-5">
				<div>
					<Typography.Title level={4}>定义文件落地资源</Typography.Title>
					<Typography.Text type="secondary">
						{modelTarget
							? "匹配来源字段到已绑定模型表，目标结构由模型管理。"
							: "可直接编辑字段，也可以引用目标数据库已有表的结构模板。"}
					</Typography.Text>
				</div>
				<Alert
					showIcon
					type={destinationAvailable ? "success" : "warning"}
					message={destinationAvailable ? "平台目标数据湖可用" : "平台目标数据湖不可用"}
					description={[
						defaultDestination?.destinationName,
						defaultDestination?.writerType,
						defaultDestination?.message,
					]
						.filter(Boolean)
						.join(" · ")}
				/>
				<Form.Item
					name="targetDataSourceId"
					label="目标数据源"
					rules={[{ required: true, message: "请选择目标数据源" }]}
				>
					<Select
						showSearch
						optionFilterProp="label"
						onChange={() => {
							_form.setFieldsValue({ fileReferenceTable: undefined, fileRecreateConfirmed: false });
							setTargetColumns([]);
							setTableResults([]);
						}}
						options={targetDataSources.map((item) => ({
							label: `${item.name}${item.recommended ? " · 推荐" : ""} · ${item.type}`,
							value: item.id,
						}))}
					/>
				</Form.Item>
				<div>
					<Typography.Text strong>字段结构定义</Typography.Text>
					<Form.Item name="fileStructureMode" className="mt-2">
						<Radio.Group
							onChange={(event) => {
								if (event.target.value === "manual") {
									_form.setFieldsValue({ fileReferenceTable: undefined, fileRecreateConfirmed: false });
									setTargetColumns([]);
								}
							}}
							options={[
								{ label: "自行编辑", value: "manual" },
								{ label: "引用已有表结构", value: "reference_existing" },
							]}
						/>
					</Form.Item>
				</div>
				{structureMode === "reference_existing" ? (
					<Form.Item
						name="fileReferenceTable"
						label="结构模板表"
						rules={[{ required: true, message: "请选择已有表结构" }]}
						extra="输入表名或字段关键字，平台仅搜索元数据，不扫描表数据。"
					>
						<Select
							showSearch
							filterOption={false}
							loading={searchingTables || loadingColumns}
							placeholder="至少输入 2 个字符，例如：customer 或 project_no"
							onSearch={setTableKeyword}
							onSelect={(value) => void selectReferenceTable(value)}
							options={tableResults.map((table) => ({
								label: `${table.schema}.${table.name}`,
								value: `${table.schema}.${table.name}`,
							}))}
						/>
					</Form.Item>
				) : null}
				{modelTarget ? (
					<Alert
						type="info"
						showIcon
						message="追加写入已绑定模型表"
						description="保留模型已有字段和主键，不新建、不重建目标表，也不自动增加主键。"
					/>
				) : (
					<Form.Item name="fileLandingMode" label="落地方式">
						<Radio.Group
							onChange={(event) => {
								_form.setFieldValue("fileRecreateConfirmed", false);
								if (event.target.value === "recreate_existing") {
									const reference = _form.getFieldValue("fileReferenceTable");
									if (reference) _form.setFieldValue("fileTargetTable", reference);
								}
							}}
							options={[
								{ label: "新建目标表", value: "create_new" },
								{ label: "全量重建原表", value: "recreate_existing", disabled: structureMode !== "reference_existing" },
							]}
						/>
					</Form.Item>
				)}
				<Form.Item
					name="fileTargetTable"
					label="完整目标表名"
					rules={[{ required: true, message: "请输入完整目标表名" }]}
					extra={
						modelTarget
							? "目标身份来自已绑定的模型版本。"
							: "可使用 table 或 schema.table；新建模式下不能与已有目标表重名。"
					}
				>
					<Input
						disabled={Boolean(modelTarget) || landingMode === "recreate_existing"}
						placeholder="例如：public.ods_customer"
					/>
				</Form.Item>
				{!modelTarget && landingMode === "recreate_existing" ? (
					<Alert
						type="error"
						showIcon
						message="全量重建会先删除原表"
						description="系统执行 DROP TABLE（不带 CASCADE）后按当前字段重建。若后续文件写入失败，原表数据不会自动恢复；存在依赖时操作将直接失败。"
						action={
							<Form.Item name="fileRecreateConfirmed" valuePropName="checked" noStyle>
								<Checkbox>我已确认全量重建原表</Checkbox>
							</Form.Item>
						}
					/>
				) : null}
				{!modelTarget ? (
					<Form.Item name="fileAutoId" label="自动增加主键" valuePropName="checked">
						<Switch />
					</Form.Item>
				) : null}
				{fileUploadResult ? (
					<>
						<Alert
							type="info"
							showIcon
							message={`${fileUploadResult.originalName} · ${fileUploadResult.rowCount || 0} 行 · ${fileUploadResult.columns.length} 列`}
							description="字段结构来自平台加密上传后的解析结果；字段只允许在文件密级基础上升密。"
						/>
						<FileFieldMappingEditor
							key={fileUploadResult.fileId}
							columns={fileUploadResult.columns}
							targetColumns={targetColumns}
							preview={fileUploadResult.preview}
							onChange={updateFileColumns}
							renderClassification={(row) => (
								<FileFieldClassificationSelect
									file={toAdmissionFile(fileUploadResult)}
									fieldName={row.name}
									onChange={(file) => onFileUploadResultChange(toManagedFile(file))}
								/>
							)}
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
				<Typography.Text type="secondary">
					支持 CSV/XLSX，最大 50MB；服务端将再次校验内容并生成密级封存。
				</Typography.Text>
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
