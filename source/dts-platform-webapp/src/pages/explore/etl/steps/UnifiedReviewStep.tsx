import {
	Alert,
	Button,
	Card,
	Collapse,
	Divider,
	Form,
	Input,
	Select,
	Switch,
	Typography,
} from "antd";
import { CompactTable } from "@/components/table";
import type { IngestionFormContext } from "./types";
import {
	normalizeText,
	writerConfigValidator,
	TABLE_PLACEHOLDER,
} from "../ingestionFormHelpers";
import OdsLandingContractCard from "./OdsLandingContractCard";

const { Text } = Typography;

/* ── local types (same as ReviewStep) ── */

export type PreviewState = {
	config: Record<string, any> | null;
	error: string;
};

export type SqlModel = {
	id?: string;
	name?: string;
	alias?: string;
};

/* ── props ── */

export type UnifiedReviewStepProps = Pick<
	IngestionFormContext,
	| "form"
	| "isFileFlow"
	| "defaultDestinationStatus"
	| "extraColumns"
	| "editorMode"
> & {
	isApiFlow?: boolean;
	previewState: PreviewState;
	sqlModels: SqlModel[];
	loadingSqlModels: boolean;
	onNavigateToModeling?: () => void;
	formValues: Record<string, any> | undefined;
	loadingDefaultDestination: boolean;
	defaultDestinationError: string;
	tableMappingPreview: { source: string; target: string }[];
};

/* ── helpers ── */

const jsonValidator = (label: string) => (_: any, value: string) => {
	if (!normalizeText(value)) return Promise.resolve();
	try {
		JSON.parse(value);
		return Promise.resolve();
	} catch {
		return Promise.reject(new Error(`${label} JSON 格式错误`));
	}
};

/* ── component ── */

export function UnifiedReviewStep({
	form: _form,
	isFileFlow,
	isApiFlow = false,
	defaultDestinationStatus,
	extraColumns,
	editorMode,
	previewState,
	sqlModels,
	loadingSqlModels,
	onNavigateToModeling,
	formValues,
	loadingDefaultDestination,
	defaultDestinationError,
	tableMappingPreview,
}: UnifiedReviewStepProps) {
	/* ── 表映射预览 ── */

	const tableMappingSection = isApiFlow ? (
		<>
			<Divider orientation="left">API 资源预览</Divider>
			<Alert
				type="info"
				showIcon
				message="API 任务可执行"
				description="任务会保存 API 资源、ODS 原始记录落地契约和 schema snapshot 入口，并通过 Airflow 触发 Java API 执行器。"
				className="mb-4"
			/>
			{tableMappingPreview.length > 0 ? (
				<CompactTable
					size="small"
					dataSource={tableMappingPreview}
					rowKey="source"
					pagination={false}
					columns={[
						{ title: "API 资源", dataIndex: "source", key: "source" },
						{ title: "ODS 原始记录表", dataIndex: "target", key: "target" },
					]}
				/>
			) : (
				<Text type="secondary">填写 API 资源路径后生成 ODS 原始记录表名</Text>
			)}
		</>
	) : (
		<>
			<Divider orientation="left">表映射预览</Divider>
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
						defaultDestinationStatus.message
							? defaultDestinationStatus.message
							: null,
					]
						.filter(Boolean)
						.join(" · ")}
					className="mb-4"
				/>
			) : null}
			{tableMappingPreview.length > 0 ? (
				<CompactTable
					size="small"
					dataSource={tableMappingPreview}
					rowKey="source"
					pagination={false}
					columns={[
						{ title: "源表", dataIndex: "source", key: "source" },
						{ title: "ODS目标表", dataIndex: "target", key: "target" },
					]}
				/>
			) : (
				<Text type="secondary">将根据选中的表和前缀自动生成映射</Text>
			)}
		</>
	);

	/* ── 执行选项 ── */

	const executionSection = isApiFlow ? (
		<>
			<Divider orientation="left">执行选项</Divider>
			<Card type="inner" title="Airflow 触发">
				<Form.Item name="airflowEnabled" label="启用 Airflow" valuePropName="checked">
					<Switch />
				</Form.Item>
				<Form.Item name="runNow" label="立即触发" valuePropName="checked">
					<Switch />
				</Form.Item>
				<Text type="secondary">
					API 任务提交后由 Airflow 触发 Java 执行器，连接测试可在上一步预览首页记录。
				</Text>
			</Card>
		</>
	) : (
		<>
			<Divider orientation="left">执行选项</Divider>
			<Card type="inner" title="Airflow 触发">
				<Form.Item name="airflowEnabled" label="启用 Airflow" valuePropName="checked">
					<Switch />
				</Form.Item>
				<Form.Item name="runNow" label="立即触发" valuePropName="checked">
					<Switch />
				</Form.Item>
				<Text type="secondary">
					若未勾选立即触发，仅保存作业配置，后续可在 Airflow 中手动运行。
				</Text>
			</Card>
		</>
	);

	/* ── dbt 模型关联 (DB flow only) ── */

	const dbtSection = !isFileFlow && !isApiFlow ? (
		<Collapse
			ghost
			className="mb-4"
			items={[
				{
					key: "dbt-models",
					label: "dbt 模型关联",
					children: (
						<div className="space-y-4">
							<Form.Item name="dbtModels" label="选择模型（可选）">
								<Select
									mode="multiple"
									allowClear
									loading={loadingSqlModels}
									placeholder={loadingSqlModels ? "模型加载中..." : "选择需要联动的模型"}
									options={sqlModels.map((model) => ({
										label: model.alias ? `${model.name} (${model.alias})` : model.name,
										value: model.name,
									}))}
									showSearch
									optionFilterProp="label"
								/>
							</Form.Item>
							<Form.Item name="dbtModelSelector" label="模型选择器（可选）">
								<Input placeholder="例如：model:ods_xxx model:dwd_xxx" />
							</Form.Item>
							<Form.Item name="dbtDagSelector" label="DAG 族选择器（可选）">
								<Input placeholder="例如：tab:erp" />
							</Form.Item>
							<Text type="secondary">
								若未填写模型选择器，将根据选中的模型生成 model:xxx 选择器；DAG 族建议使用 tab:源系统。
							</Text>
							{onNavigateToModeling ? (
								<Button size="small" onClick={onNavigateToModeling}>
									进入建模
								</Button>
							) : null}
						</div>
					),
				},
			]}
		/>
	) : null;

	/* ── 作业参数 ── */

	const jobConfigSection = isApiFlow ? (
		<Collapse
			ghost
			className="mb-4"
			items={[
				{
					key: "api-preview",
					label: "API 任务预览",
					children: (
						<Card type="inner" title="API 任务预览">
							{previewState.error ? (
								<Alert type="warning" message={previewState.error} showIcon />
							) : (
								<pre className="bg-muted p-4 rounded overflow-auto">
									{JSON.stringify(previewState.config, null, 2)}
								</pre>
							)}
						</Card>
					),
				},
			]}
		/>
	) : (
		<Collapse
			ghost
			className="mb-4"
			items={[
				{
					key: "job-config",
					label: "作业参数",
					children: (
						<div className="space-y-4">
							<Form.Item
								name="jobConfig"
								label="作业参数 (JSON，可选)"
								rules={[{ validator: jsonValidator("作业参数") }]}
							>
								<Input.TextArea rows={4} placeholder='{"setting":{"speed":{"channel":3}}}' />
							</Form.Item>
							<Card type="inner" title="作业预览">
								{previewState.error ? (
									<Alert type="warning" message={previewState.error} showIcon />
								) : (
									<pre className="bg-muted p-4 rounded overflow-auto">
										{JSON.stringify(previewState.config, null, 2)}
									</pre>
								)}
							</Card>
						</div>
					),
				},
			]}
		/>
	);

	/* ── 目标数据库覆盖 ── */

	const writerOverrideSection = (
		<Collapse
			ghost
			className="mb-4"
			items={[
				{
					key: "writer-override",
					label: "目标数据库覆盖",
					children: (
						<div className="space-y-4">
							<Form.Item label="Writer 类型" required>
								<Input
									value={formValues?.writerType || ""}
									placeholder="由默认数据湖自动提供"
									disabled
								/>
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
										<Form.Item name="writerUsername" label="Writer 用户名">
											<Input placeholder="数据库账号" />
										</Form.Item>
										<Form.Item name="writerPassword" label="Writer 密码">
											<Input.Password placeholder="******" />
										</Form.Item>
									</div>
									<Form.Item name="writerSchema" label="Writer Schema">
										<Input placeholder="可选，例如 public" />
									</Form.Item>
									<Form.Item name="writerPreSql" label="Writer 前置 SQL（每行一条）">
										<Input.TextArea rows={3} placeholder="delete from t where ..." />
									</Form.Item>
									<Form.Item name="writerPostSql" label="Writer 后置 SQL（每行一条）">
										<Input.TextArea rows={3} placeholder="analyze table t" />
									</Form.Item>
									<Form.Item name="writerExtraConfig" label="Writer 扩展配置 JSON">
										<Input.TextArea rows={4} placeholder='{"batchSize":1000}' />
									</Form.Item>
								</>
							)}
							<Text type="secondary" className="block mt-2">
								入湖任务需要提供目标表名，可使用 {TABLE_PLACEHOLDER} 占位符或具体表名。
							</Text>
						</div>
					),
				},
			]}
		/>
	);

	/* ── ODS 契约 ── */

	const columnRulesSection = (
		<OdsLandingContractCard
			sourceKind={isFileFlow ? "file" : "database"}
			columnPrefix={formValues?.columnPrefix}
			columnSuffix={formValues?.columnSuffix}
			extraColumns={extraColumns}
		/>
	);

	/* ── render ── */

	return (
		<>
			{tableMappingSection}
			{executionSection}
			{dbtSection}
			{jobConfigSection}
			{isApiFlow ? null : writerOverrideSection}
			{isApiFlow ? null : columnRulesSection}
		</>
	);
}

export default UnifiedReviewStep;
