import {
	Alert,
	Collapse,
	Divider,
	Form,
	Input,
	Typography,
} from "antd";
import type { IngestionFormContext } from "./types";
import {
	normalizeText,
	splitLines,
	writerConfigValidator,
	TABLE_PLACEHOLDER,
} from "../ingestionFormHelpers";
import OdsLandingContractCard from "./OdsLandingContractCard";

const { Text } = Typography;

export type DbTargetStepProps = Pick<
	IngestionFormContext,
	| "form"
	| "defaultDestinationStatus"
	| "extraColumns"
	| "editorMode"
> & {
	formValues: Record<string, any> | undefined;
	loadingDefaultDestination: boolean;
	defaultDestinationError: string;
	resolveSelectedTables: () => string[];
};

export const DbTargetStep = ({
	form,
	defaultDestinationStatus,
	extraColumns,
	editorMode,
	formValues,
	loadingDefaultDestination,
	defaultDestinationError,
	resolveSelectedTables,
}: DbTargetStepProps) => {
	const writerTablesValidator = (_: any, value: string) => {
		const mode = normalizeText(form.getFieldValue("tableSelectionMode")) || "all";
		if (mode === "all") {
			return Promise.resolve();
		}
		if (resolveSelectedTables().length) {
			return Promise.resolve();
		}
		const tables = splitLines(value);
		if (tables.length) {
			return Promise.resolve();
		}
		return Promise.reject(new Error("请填写目标表名"));
	};

	return (
		<>
			<Divider orientation="left">目标端配置</Divider>
			{loadingDefaultDestination ? (
				<Alert
					type="info"
					showIcon
					message="正在加载默认数据湖配置"
					className="mb-4"
				/>
			) : null}
			{defaultDestinationError ? (
				<Alert
					type="error"
					showIcon
					message="默认数据湖不可用"
					description={defaultDestinationError}
					className="mb-4"
				/>
			) : null}
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
			<Form.Item name="syncPrefix" label="目标表前缀">
				<Input placeholder="例如：ods_erp_" />
			</Form.Item>
			<Text type="secondary" className="block -mt-3 mb-4">
				用于自动生成 ODS 表名（如：ods_erp_ + 源表名）。若 Writer 已指定目标表，可留空。
			</Text>
			<OdsLandingContractCard
				sourceKind="database"
				columnPrefix={form.getFieldValue("columnPrefix")}
				columnSuffix={form.getFieldValue("columnSuffix")}
				extraColumns={extraColumns}
			/>
			<Divider orientation="left">Writer 配置</Divider>
			<Form.Item label="Writer 类型" required>
				<Input value={formValues?.writerType || ""} placeholder="由默认数据湖自动提供" disabled />
			</Form.Item>
			{editorMode === "json" ? (
				<Form.Item
					name="writerConfig"
					label="Writer 配置 (JSON)"
					required
					rules={[
						{ required: true, message: "请输入 Writer 配置" },
						{ validator: writerConfigValidator },
					]}
				>
					<Input.TextArea
						rows={6}
						placeholder='{"connection":[{"table":["target_table"]}],"column":["*"]}'
					/>
				</Form.Item>
			) : (
				<>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item
							name="writerJdbcUrls"
							label="Writer JDBC URL（每行一个，可选覆盖）"
						>
							<Input.TextArea rows={3} placeholder="jdbc:postgresql://host:5432/db" />
						</Form.Item>
						<Form.Item
							name="writerTables"
							label="Writer 表（每行一个）"
							rules={[
								{ validator: writerTablesValidator },
							]}
						>
							<Input.TextArea rows={3} placeholder="target_table" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="writerColumns" label="Writer 字段（逗号分隔）">
							<Input placeholder="* 或 id,name,created_at" />
						</Form.Item>
						<Form.Item name="writerWriteMode" label="Writer 写入模式">
							<Input placeholder="insert / replace / update" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item
							name="writerUsername"
							label="Writer 用户名"
						>
							<Input placeholder="数据库账号" />
						</Form.Item>
						<Form.Item
							name="writerPassword"
							label="Writer 密码"
						>
							<Input.Password placeholder="******" />
						</Form.Item>
					</div>
					<Form.Item name="writerSchema" label="Writer Schema">
						<Input placeholder="可选，例如 public" />
					</Form.Item>
					<Collapse
						ghost
						items={[
							{
								key: "writer-advanced",
								label: "Writer 高级参数",
								children: (
									<div className="space-y-4">
										<Form.Item name="writerPreSql" label="Writer 前置 SQL（每行一条）">
											<Input.TextArea rows={3} placeholder="delete from t where ..." />
										</Form.Item>
										<Form.Item name="writerPostSql" label="Writer 后置 SQL（每行一条）">
											<Input.TextArea rows={3} placeholder="analyze table t" />
										</Form.Item>
										<Form.Item name="writerExtraConfig" label="Writer 扩展配置 JSON">
											<Input.TextArea rows={4} placeholder='{"batchSize":1000}' />
										</Form.Item>
									</div>
								),
							},
						]}
					/>
				</>
			)}
			<Text type="secondary" className="block mt-2">
				入湖任务需要提供目标表名，可使用 {TABLE_PLACEHOLDER} 占位符或具体表名。
			</Text>
		</>
	);
};

export default DbTargetStep;
