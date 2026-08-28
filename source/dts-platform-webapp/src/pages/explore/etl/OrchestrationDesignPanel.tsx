import {
	Alert,
	Button,
	Card,
	Col,
	Empty,
	Form,
	Input,
	Row,
	Segmented,
	Select,
	Space,
	Spin,
	Switch,
	Tag,
	Typography,
} from "antd";
import type { FormInstance } from "antd/es/form";
import type { IngestionTaskDesign, IngestionTopologyProjection, IngestionValidationResult } from "@/api/ingestion";
import type { InfraDataSource } from "@/api/services/dataSourcesService";
import type { CatalogDatasetOption, DesignFormValues } from "./orchestrationDesignModel";

const { Paragraph } = Typography;

export type TopologyView = "DRAFT" | "ACTIVE";

type Props = {
	form: FormInstance<DesignFormValues>;
	design: IngestionTaskDesign | null;
	topology: IngestionTopologyProjection | null;
	topologyView: TopologyView;
	validation: IngestionValidationResult | null;
	sources: InfraDataSource[];
	datasets: CatalogDatasetOption[];
	datasetSearching: boolean;
	loading: boolean;
	onDatasetSearch: (keyword: string) => void;
	onTopologyView: (view: TopologyView) => void;
	onDirty: () => void;
};

function statusColor(status?: string): string {
	switch ((status || "").toUpperCase()) {
		case "ACTIVE":
		case "READY":
		case "BOUND":
		case "CONFIGURED":
		case "ENABLED":
			return "green";
		case "DRAFT":
		case "PAUSED":
			return "gold";
		case "UNRESOLVED":
		case "INVALID":
			return "red";
		default:
			return "blue";
	}
}

function ReadonlyTopology({ topology }: { topology: IngestionTopologyProjection | null }) {
	if (!topology) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可展示的流程拓扑" />;
	return (
		<div data-testid="readonly-topology" style={{ overflowX: "auto", padding: "12px 4px" }}>
			<div style={{ display: "flex", alignItems: "center", minWidth: 620 }}>
				{topology.nodes.map((node, index) => (
					<div key={node.id} style={{ display: "flex", alignItems: "center", flex: 1 }}>
						<div
							style={{
								minWidth: 112,
								padding: "12px 10px",
								border: "1px solid #d9e2ec",
								borderRadius: 8,
								background: "#f8fafc",
								textAlign: "center",
							}}
						>
							<div style={{ fontWeight: 600 }}>{node.label}</div>
							<Tag color={statusColor(node.state)} style={{ marginTop: 8, marginInlineEnd: 0 }}>
								{node.state}
							</Tag>
						</div>
						{index < topology.nodes.length - 1 ? (
							<div aria-hidden="true" style={{ flex: 1, textAlign: "center", color: "#64748b" }}>
								→
							</div>
						) : null}
					</div>
				))}
			</div>
		</div>
	);
}

export default function OrchestrationDesignPanel({
	form,
	design,
	topology,
	topologyView,
	validation,
	sources,
	datasets,
	datasetSearching,
	loading,
	onDatasetSearch,
	onTopologyView,
	onDirty,
}: Props) {
	const watchedTargetDatasetId = Form.useWatch("targetDatasetId", form);
	const watchedSourceType = Form.useWatch("sourceType", form);
	const qualityEnabled = Form.useWatch("postIngestionQualityEnabled", form);
	const selectedDataset = datasets.find((item) => item.id === watchedTargetDatasetId);
	const selectedDatasetMatchesDesign =
		Boolean(watchedTargetDatasetId) && watchedTargetDatasetId === design?.destination?.assetRef?.datasetId;
	const sourceCanOmitProfile = /file|excel|csv/i.test(watchedSourceType || "");

	return (
		<Spin spinning={loading}>
			{design ? (
				<Form<DesignFormValues> form={form} layout="vertical" onValuesChange={onDirty}>
					<Row gutter={[16, 16]}>
						<Col xs={24} xl={15}>
							<Space direction="vertical" size={16} style={{ width: "100%" }}>
								<Card title="1. 任务与来源" size="small">
									<Row gutter={16}>
										<Col xs={24} md={12}>
											<Form.Item
												name="taskName"
												label="任务名称"
												rules={[{ required: true, message: "请输入任务名称" }]}
											>
												<Input maxLength={200} />
											</Form.Item>
										</Col>
										<Col xs={24} md={12}>
											<Form.Item
												name="sourceDataSourceId"
												label="来源数据源"
												rules={[{ required: !sourceCanOmitProfile, message: "请选择来源数据源" }]}
											>
												<Select
													showSearch
													optionFilterProp="label"
													options={sources.map((source) => ({
														value: source.id,
														label: `${source.name} · ${source.type}`,
													}))}
												/>
											</Form.Item>
										</Col>
									</Row>
									<Form.Item name="description" label="业务说明">
										<Input.TextArea rows={2} maxLength={2000} showCount />
									</Form.Item>
									<Form.Item
										name="sourceType"
										label="来源连接器类型"
										rules={[{ required: true, message: "请输入来源连接器类型" }]}
									>
										<Input maxLength={50} />
									</Form.Item>
									<Form.Item name="sourceConfigText" label="来源参数（JSON）" rules={[{ required: true }]}>
										<Input.TextArea rows={6} spellCheck={false} />
									</Form.Item>
								</Card>

								<Card title="2. 目标资产与表映射" size="small">
									<Row gutter={16}>
										<Col xs={24} md={12}>
											<Form.Item
												name="destinationType"
												label="目标连接器类型"
												rules={[{ required: true, message: "请输入目标连接器类型" }]}
											>
												<Input maxLength={50} />
											</Form.Item>
										</Col>
										<Col xs={24} md={12}>
											<Form.Item
												name="targetDatasetId"
												label="目标数据资产"
												rules={[{ required: true, message: "请选择唯一的目标数据资产" }]}
											>
												<Select
													showSearch
													filterOption={false}
													loading={datasetSearching}
													onSearch={onDatasetSearch}
													placeholder="请选择 Catalog 中已登记的数据集"
													options={datasets.map((dataset) => ({
														value: dataset.id,
														label: `${dataset.name}${dataset.hiveTable ? ` · ${dataset.hiveDatabase || "-"}.${dataset.hiveTable}` : ""} · ${dataset.type || "DATASET"} · ${dataset.id.slice(-8)}`,
													}))}
												/>
											</Form.Item>
										</Col>
									</Row>
									{selectedDataset ? (
										<Alert
											type="info"
											showIcon
											message={`已关联资产：${selectedDataset.name}`}
											description={`物理对象：${selectedDataset.hiveDatabase || "-"}.${selectedDataset.hiveTable || "-"}；平台将在保存、校验和发布时核对物理目标。`}
										/>
									) : null}
									<Form.Item
										name="destinationConfigText"
										label="目标参数（JSON）"
										rules={[{ required: true }]}
										style={{ marginTop: 16 }}
									>
										<Input.TextArea rows={6} spellCheck={false} />
									</Form.Item>
									<Form.List name="tableMapping">
										{(fields, { add, remove }) => (
											<Space direction="vertical" style={{ width: "100%" }}>
												{fields.map((field) => (
													<Row gutter={8} key={field.key} align="middle">
														<Col span={10}>
															<Form.Item
																{...field}
																name={[field.name, "source"]}
																rules={[{ required: true, message: "请输入来源表" }]}
																style={{ marginBottom: 8 }}
															>
																<Input placeholder="来源表，如 schema.table" />
															</Form.Item>
														</Col>
														<Col span={10}>
															<Form.Item
																{...field}
																name={[field.name, "target"]}
																rules={[{ required: true, message: "请输入目标表" }]}
																style={{ marginBottom: 8 }}
															>
																<Input placeholder="目标表，如 ods.table" />
															</Form.Item>
														</Col>
														<Col span={4}>
															<Button danger type="link" onClick={() => remove(field.name)}>
																移除
															</Button>
														</Col>
													</Row>
												))}
												<Button block type="dashed" onClick={() => add({ source: "", target: "" })}>
													新增表映射
												</Button>
											</Space>
										)}
									</Form.List>
								</Card>

								<Card title="3. 同步与调度" size="small">
									<Row gutter={16}>
										<Col xs={24} md={10}>
											<Form.Item name="syncMode" label="同步方式" rules={[{ required: true }]}>
												<Select
													options={[
														{ value: "full_refresh", label: "全量刷新" },
														{ value: "incremental", label: "增量同步" },
													]}
												/>
											</Form.Item>
										</Col>
										<Col xs={24} md={14}>
											<Form.Item name="syncSchedule" label="调度表达式">
												<Input placeholder="例如：0 0 2 * * *" />
											</Form.Item>
										</Col>
									</Row>
									<Form.Item name="syncConfigText" label="同步参数（JSON）" rules={[{ required: true }]}>
										<Input.TextArea rows={5} spellCheck={false} />
									</Form.Item>
								</Card>

								<Card title="4. 接入后质量验证" size="small">
									<Form.Item name="postIngestionQualityEnabled" label="运行方式" valuePropName="checked">
										<Switch
											checkedChildren="接入成功后执行"
											unCheckedChildren="暂不执行"
											disabled={!watchedTargetDatasetId}
										/>
									</Form.Item>
									<Alert
										type={qualityEnabled ? "info" : "warning"}
										showIcon
										message={
											qualityEnabled ? "质量策略将在数据写入成功后运行" : "目标资产仍会登记，但不会形成可信验证证据"
										}
										description={
											qualityEnabled
												? `已发布质量规则：${selectedDatasetMatchesDesign ? (design.assetProjection?.qualityBindingCount ?? "校验时确认") : "校验时确认"}；质量未通过不会把资产标记为可信可用。`
												: "接入过程负责落地数据；质量模块在接入成功后验证，并将结果投影到资产可消费状态。"
										}
									/>
								</Card>
							</Space>
						</Col>

						<Col xs={24} xl={9}>
							<Space direction="vertical" size={16} style={{ width: "100%" }}>
								<Card
									title="只读执行拓扑"
									extra={
										<Segmented
											value={topologyView}
											options={[
												{ label: "草稿", value: "DRAFT" },
												{ label: "已发布", value: "ACTIVE" },
											]}
											onChange={(value) => onTopologyView(value as TopologyView)}
										/>
									}
								>
									<ReadonlyTopology topology={topology} />
									<Paragraph type="secondary" style={{ marginBottom: 0 }}>
										拓扑由任务版本自动生成，仅用于核对“读取、写入、资产、质量、完成”链路，不支持自由拖拽或绕过版本发布。
									</Paragraph>
								</Card>

								<Card title="发布前检查" size="small">
									{validation?.valid ? (
										<Alert type="success" showIcon message="当前设计校验通过" />
									) : (
										<Alert
											type="warning"
											showIcon
											message={`仍有 ${validation?.issues?.length || 0} 项待完善`}
											description={
												<ul style={{ margin: "8px 0 0", paddingLeft: 20 }}>
													{(validation?.issues || []).map((issue) => (
														<li key={`${issue.code}-${issue.field}`}>{issue.message}</li>
													))}
												</ul>
											}
										/>
									)}
									{design.legacyDsl?.present ? (
										<Alert
											style={{ marginTop: 12 }}
											type="info"
											showIcon
											message={design.legacyDsl.message || "历史画布仅供兼容查看"}
										/>
									) : null}
								</Card>
							</Space>
						</Col>
					</Row>
				</Form>
			) : (
				<Empty description="任务设计加载失败或任务不可访问" />
			)}
		</Spin>
	);
}
