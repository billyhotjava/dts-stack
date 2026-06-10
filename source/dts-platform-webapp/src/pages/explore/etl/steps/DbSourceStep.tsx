import { useState } from "react";
import {
	Alert,
	Button,
	Card,
	Collapse,
	Divider,
	Form,
	Input,
	Radio,
	Select,
	Space,
	Typography,
} from "antd";
import { CompactTable } from "@/components/table";
import type { TableInfo } from "@/api/ingestion";
import type { IngestionFormContext } from "./types";
import { normalizeText } from "@/utils/textUtils";

const { Text } = Typography;

const buildTableKey = (table: TableInfo) =>
	normalizeText(table.schema) ? `${table.schema}.${table.name}` : table.name;

const jsonValidator = (label: string, _forbidConnection = false) => (_: any, value: string) => {
	if (!normalizeText(value)) return Promise.resolve();
	try {
		JSON.parse(value);
		return Promise.resolve();
	} catch {
		return Promise.reject(new Error(`${label} JSON 格式错误`));
	}
};

export type DbSourceStepProps = Pick<
	IngestionFormContext,
	| "form"
	| "selectedTableKeys"
	| "setSelectedTableKeys"
	| "editorMode"
	| "selectedDataSource"
> & {
	availableTables: TableInfo[];
	loadingTables: boolean;
	discoveredTableKeys: string[];
	discoverError: string;
	loadingDataSources: boolean;
	dataSources: IngestionFormContext["dataSources"];
	onDiscoverTables: () => void;
	onApplyTables: () => void;
	syncSelectedTablesToForm: (tables: string[], opts?: { silent?: boolean }) => void;
	readerTablesValidator: (_: any, value: string) => Promise<void>;
	readerTypeValidator: (_: any, value: string) => Promise<void>;
};

export function DbSourceStep({
	form,
	selectedTableKeys,
	setSelectedTableKeys,
	editorMode,
	availableTables,
	loadingTables,
	discoveredTableKeys,
	discoverError,
	loadingDataSources,
	dataSources,
	onDiscoverTables,
	onApplyTables,
	syncSelectedTablesToForm,
	readerTablesValidator,
	readerTypeValidator,
}: DbSourceStepProps) {
	const tableSelectionMode = Form.useWatch("tableSelectionMode", form);
	const [tablePageSize, setTablePageSize] = useState(10);

	return (
		<>
			<Divider orientation="left">Reader 配置</Divider>
			<Form.Item
				name="sourceDataSourceId"
				label="数据源连接"
				rules={[{ required: true, message: "请选择数据源连接" }]}
			>
				<Select
					loading={loadingDataSources}
					placeholder={loadingDataSources ? "加载中..." : "请选择数据源连接"}
					options={dataSources.map((item) => ({
						label: `${item.name} (${item.type || "unknown"})`,
						value: item.id,
					}))}
					showSearch
					optionFilterProp="label"
				/>
			</Form.Item>
			<Form.Item
				name="readerType"
				label="Reader 类型"
				rules={[{ validator: readerTypeValidator }]}
			>
				<Input placeholder="将根据数据源自动生成" disabled />
			</Form.Item>
			<Form.Item name="tableSelectionMode" label="入湖表选择">
				<Radio.Group
					onChange={(e) => {
						const next = normalizeText(e.target?.value) || "all";
						if (next === "all") {
							syncSelectedTablesToForm([], { silent: true });
						}
					}}
				>
					<Radio.Button value="all">全部表（默认）</Radio.Button>
					<Radio.Button value="manual">手动选择</Radio.Button>
				</Radio.Group>
			</Form.Item>
			{tableSelectionMode === "all" ? (
				<Form.Item name="tableExclude" label="排除表（每行一个，可选）">
					<Input.TextArea rows={2} placeholder="schema.table 或 table_name" />
				</Form.Item>
			) : null}
			{editorMode === "json" ? (
				<Form.Item
					name="readerConfig"
					label="Reader 配置 (JSON)"
					rules={[
						{ required: true, message: "请输入 Reader 配置" },
						{ validator: jsonValidator("Reader 配置", true) },
					]}
				>
					<Input.TextArea rows={6} placeholder='{"column":["*"],"table":["table_a"]}' />
				</Form.Item>
			) : (
				<>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item
							name="readerTables"
							label="Reader 表（每行一个）"
							dependencies={["tableSelectionMode"]}
							rules={[{ validator: readerTablesValidator }]}
						>
							<Input.TextArea rows={3} placeholder="source_table" />
						</Form.Item>
						<Form.Item name="readerColumns" label="Reader 字段（逗号分隔）">
							<Input placeholder="* 或 id,name,created_at" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="readerWhere" label="Reader 过滤条件">
							<Input placeholder="可选，例如：status = 1" />
						</Form.Item>
					</div>
					<Collapse
						ghost
						items={[
							{
								key: "reader-advanced",
								label: "Reader 高级参数",
								children: (
									<div className="space-y-4">
										<Form.Item name="readerQuerySql" label="Reader 查询 SQL（每行一条）">
											<Input.TextArea rows={3} placeholder="select * from t where ..." />
										</Form.Item>
										<Form.Item name="readerExtraConfig" label="Reader 扩展配置 JSON">
											<Input.TextArea rows={4} placeholder='{"splitPk":"id"}' />
										</Form.Item>
									</div>
								),
							},
						]}
					/>
				</>
			)}
			<Divider orientation="left">源端表发现</Divider>
			<Card type="inner">
				<div className="grid gap-4 md:grid-cols-3">
					<Form.Item name="readerSchema" label="Schema（可选）">
						<Input placeholder="例如 public" />
					</Form.Item>
					<Form.Item name="readerTablePattern" label="表名筛选（可选）">
						<Input placeholder="支持 SQL LIKE，例如 ods_%" />
					</Form.Item>
					<Form.Item label="操作">
						<Space>
							<Button onClick={onDiscoverTables} loading={loadingTables}>
								获取表清单
							</Button>
							<Button
								onClick={() => {
									setSelectedTableKeys(discoveredTableKeys);
									syncSelectedTablesToForm(discoveredTableKeys, { silent: true });
								}}
								disabled={!discoveredTableKeys.length}
							>
								全选
							</Button>
							<Button
								onClick={() => {
									setSelectedTableKeys([]);
									syncSelectedTablesToForm([], { silent: true });
								}}
								disabled={!selectedTableKeys.length}
							>
								清空
							</Button>
							<Button onClick={onApplyTables} disabled={!selectedTableKeys.length}>
								应用选择
							</Button>
						</Space>
					</Form.Item>
				</div>
				{discoverError ? (
					<Alert type="warning" message={discoverError} showIcon className="mb-3" />
				) : null}
				<CompactTable
					rowKey={(record) => buildTableKey(record)}
					size="small"
					loading={loadingTables}
					dataSource={availableTables}
					rowSelection={{
						selectedRowKeys: selectedTableKeys,
						onChange: (keys) => {
							const nextKeys = keys.map((key) => String(key));
							syncSelectedTablesToForm(nextKeys, { silent: true });
						},
					}}
					columns={[
						{ title: "模式", dataIndex: "schema", width: 140 },
						{ title: "表名", dataIndex: "name" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
						{ title: "类型", dataIndex: "type", width: 120 },
					]}
					pagination={{
						defaultPageSize: tablePageSize,
						showSizeChanger: true,
						pageSizeOptions: [10, 20, 50, 100],
						onShowSizeChange: (_current: number, size: number) => setTablePageSize(size),
					}}
				/>
				<Text type="secondary" className="block mt-2">
					已发现 {availableTables.length} 张表，已选择 {selectedTableKeys.length} 张表
				</Text>
			</Card>
		</>
	);
}

