import { Alert, Button, Form, Input, Modal, Select, Space, Switch, Upload } from "antd";
import type { UploadFile } from "antd/es/upload/interface";
import type { ProjectSpace } from "../sqlModeling.types";
import type { InfraDataSource } from "@/api/services/dataSourcesService";

export type ImportModelModalProps = {
	open: boolean;
	onClose: () => void;
	onSubmit: () => void;
	submitting: boolean;
	spaces: ProjectSpace[];
	dataSources: InfraDataSource[];
	sqlFileList: UploadFile[];
	onSqlFileListChange: (fileList: UploadFile[]) => void;
	csvFileList: UploadFile[];
	onCsvFileListChange: (fileList: UploadFile[]) => void;
	form: ReturnType<typeof Form.useForm>[0];
};

export default function ImportModelModal({
	open,
	onClose,
	onSubmit,
	submitting,
	spaces,
	dataSources,
	sqlFileList,
	onSqlFileListChange,
	csvFileList,
	onCsvFileListChange,
	form,
}: ImportModelModalProps) {
	return (
		<Modal
			open={open}
			title="导入模型 (SQL + CSV)"
			onCancel={onClose}
			footer={
				<Space>
					<Button onClick={onClose}>取消</Button>
					<Button type="primary" onClick={onSubmit} loading={submitting}>
						导入
					</Button>
				</Space>
			}
		>
			<Alert type="warning" showIcon message="非密模块禁止上传涉密数据" style={{ marginBottom: 16 }} />
			<Form layout="vertical" form={form} disabled={submitting}>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="planId" label="项目空间" rules={[{ required: true, message: "请选择项目空间" }]}>
						<Select
							placeholder="选择项目空间"
							options={spaces.map((space) => ({ label: space.name || "未命名", value: space.id }))}
						/>
					</Form.Item>
					<Form.Item name="layer" label="分层" rules={[{ required: true, message: "请选择分层" }]}>
						<Select
							placeholder="选择分层"
							options={[
								{ label: "ODS", value: "ODS" },
								{ label: "DWD", value: "DWD" },
								{ label: "DWS", value: "DWS" },
								{ label: "ADS", value: "ADS" },
							]}
						/>
					</Form.Item>
				</div>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="name" label="模型名称" rules={[{ required: true, message: "请输入模型名称" }]}>
						<Input placeholder="例如 dwd_sales_order" />
					</Form.Item>
					<Form.Item name="alias" label="物理表别名">
						<Input placeholder="可选" />
					</Form.Item>
				</div>
				<Form.Item
					name="sourceDataSourceId"
					label="来源数据源"
					rules={[{ required: true, message: "请选择来源数据源" }]}
				>
					<Select
						placeholder="选择来源数据源"
						options={dataSources.map((ds) => ({
							label: ds?.name || ds?.id,
							value: ds?.id,
						}))}
					/>
				</Form.Item>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="schemaName" label="目标 Schema">
						<Input placeholder="例如 ods" />
					</Form.Item>
					<Form.Item name="materialized" label="物化方式">
						<Select
							placeholder="选择物化方式"
							options={[
								{ label: "table", value: "table" },
								{ label: "view", value: "view" },
								{ label: "incremental", value: "incremental" },
							]}
						/>
					</Form.Item>
				</div>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="tags" label="标签 (逗号分隔)">
						<Input placeholder="如 sales,ods" />
					</Form.Item>
					<Form.Item name="status" label="状态">
						<Select
							placeholder="选择状态"
							options={[
								{ label: "草稿", value: "DRAFT" },
								{ label: "已发布", value: "PUBLISHED" },
							]}
						/>
					</Form.Item>
				</div>
				<Form.Item name="description" label="描述">
					<Input.TextArea rows={2} placeholder="模型说明" />
				</Form.Item>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="enabled" label="启用" valuePropName="checked">
						<Switch />
					</Form.Item>
					<Form.Item name="ownerDept" label="归属部门">
						<Input placeholder="可选" />
					</Form.Item>
				</div>
				<Form.Item label="SQL 文件" required>
					<Upload
						accept=".sql"
						beforeUpload={() => false}
						maxCount={1}
						fileList={sqlFileList}
						onChange={({ fileList }) => onSqlFileListChange(fileList.slice(-1))}
					>
						<Button>选择 SQL</Button>
					</Upload>
				</Form.Item>
				<Form.Item label="CSV 文件 (可选)">
					<Upload
						accept=".csv"
						beforeUpload={() => false}
						maxCount={1}
						fileList={csvFileList}
						onChange={({ fileList }) => onCsvFileListChange(fileList.slice(-1))}
					>
						<Button>选择 CSV</Button>
					</Upload>
				</Form.Item>
			</Form>
		</Modal>
	);
}
