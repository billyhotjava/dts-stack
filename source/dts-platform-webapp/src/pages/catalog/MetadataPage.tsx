import { useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Col,
	Descriptions,
	Form,
	Input,
	Modal,
	Row,
	Select,
	Space,
	Table,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";

const { Text } = Typography;

type DataSource = {
	id: string;
	name: string;
	type: string;
	owner: string;
};

type AssetColumn = {
	key: string;
	name: string;
	type: string;
	comment: string;
	quality: string;
};

type Asset = {
	id: string;
	label: string;
	table: {
		db: string;
		schema: string;
		name: string;
		comment: string;
		pk: string;
	};
	cols: AssetColumn[];
	diff: {
		newTables: number;
		newCols: number;
		typeChanges: number;
		commentMissing: number;
	};
};

type RunRow = {
	id: string;
	time: string;
	source: string;
	mode: string;
	tables: number;
	columns: number;
	status: string;
};

const dataSources: DataSource[] = [
	{ id: "ds_001", name: "ERP-生产库", type: "MySQL", owner: "谢志民" },
	{ id: "ds_002", name: "MES-测试库", type: "PostgreSQL", owner: "王工" },
	{ id: "ds_003", name: "订单事件流", type: "Kafka", owner: "李工" },
];

const assets: Asset[] = [
	{
		id: "a1",
		label: "erp.so_sales_order（销售订单）",
		table: {
			db: "erp",
			schema: "erp",
			name: "so_sales_order",
			comment: "销售订单主表",
			pk: "pk_order",
		},
		cols: [
			{ key: "pk_order", name: "pk_order", type: "varchar(36)", comment: "订单主键", quality: "唯一性：建议校验" },
			{ key: "order_code", name: "order_code", type: "varchar(64)", comment: "订单编号", quality: "空值率<1%" },
			{ key: "customer_code", name: "customer_code", type: "varchar(64)", comment: "客户编码", quality: "参照完整性" },
			{ key: "order_time", name: "order_time", type: "timestamp", comment: "下单时间", quality: "时间分区候选" },
			{ key: "total_amt", name: "total_amt", type: "decimal(18,2)", comment: "含税金额", quality: "值域：>=0" },
		],
		diff: { newTables: 0, newCols: 2, typeChanges: 0, commentMissing: 1 },
	},
	{
		id: "a2",
		label: "erp.inv_stock_balance（库存余额）",
		table: {
			db: "erp",
			schema: "erp",
			name: "inv_stock_balance",
			comment: "库存日余额",
			pk: "(material_id, wh_id, dt)",
		},
		cols: [
			{ key: "material_id", name: "material_id", type: "varchar(36)", comment: "物料主键", quality: "参照完整性" },
			{ key: "wh_id", name: "wh_id", type: "varchar(36)", comment: "仓库主键", quality: "参照完整性" },
			{ key: "qty", name: "qty", type: "decimal(18,3)", comment: "数量", quality: "值域：>=0" },
			{ key: "dt", name: "dt", type: "date", comment: "业务日期", quality: "分区字段" },
		],
		diff: { newTables: 1, newCols: 0, typeChanges: 1, commentMissing: 0 },
	},
];

const runs: RunRow[] = [
	{ id: "r1", time: "2026-01-22 01:58", source: "ERP-生产库", mode: "增量扫描", tables: 12, columns: 143, status: "SUCCESS" },
	{ id: "r2", time: "2026-01-21 02:01", source: "ERP-生产库", mode: "全量扫描", tables: 12, columns: 141, status: "SUCCESS" },
	{ id: "r3", time: "2026-01-20 15:12", source: "MES-测试库", mode: "增量扫描", tables: 8, columns: 97, status: "FAILED" },
];

const statusTag = (status: string) => {
	if (status === "SUCCESS") return <Tag color="green">成功</Tag>;
	if (status === "FAILED") return <Tag color="red">失败</Tag>;
	return <Tag>未知</Tag>;
};

export default function MetadataPage() {
	const [form] = Form.useForm();
	const [helpOpen, setHelpOpen] = useState(false);
	const [selectedAssetId, setSelectedAssetId] = useState(assets[0]?.id || "");

	const selectedAsset = useMemo(() => assets.find((item) => item.id === selectedAssetId) || null, [selectedAssetId]);

	const assetColumns: ColumnsType<AssetColumn> = [
		{ title: "字段", dataIndex: "name" },
		{ title: "类型", dataIndex: "type" },
		{ title: "备注", dataIndex: "comment" },
		{ title: "质量提示", dataIndex: "quality" },
	];

	const runColumns: ColumnsType<RunRow> = [
		{ title: "时间", dataIndex: "time" },
		{ title: "数据源", dataIndex: "source" },
		{ title: "模式", dataIndex: "mode" },
		{ title: "发现表", dataIndex: "tables" },
		{ title: "发现字段", dataIndex: "columns" },
		{ title: "状态", dataIndex: "status", render: statusTag },
		{ title: "操作", render: () => <Button type="link">查看日志</Button> },
	];

	const diffItems = selectedAsset
		? [
				{ label: "新增表", value: selectedAsset.diff.newTables, color: "blue" },
				{ label: "新增字段", value: selectedAsset.diff.newCols, color: "cyan" },
				{ label: "类型变更", value: selectedAsset.diff.typeChanges, color: "orange" },
				{ label: "备注缺失", value: selectedAsset.diff.commentMissing, color: "red" },
			]
		: [];

	return (
		<div className="space-y-4">
			<PageHeader
				title="元数据采集"
				description="结构扫描：复用数据源连接，触发元数据采集与结构同步（占位）。"
				actions={
					<Space>
						<Button onClick={() => toast.success("已刷新（示例）")}>刷新</Button>
						<Button onClick={() => setHelpOpen(true)}>使用说明</Button>
						<Button type="primary" onClick={() => toast.success("已保存计划（示例）")}>保存计划</Button>
					</Space>
				}
			/>

			<Alert
				type="info"
				showIcon
				message="元数据采集将自动同步表/字段/索引等结构信息，并为质量校验与入湖任务提供基础。"
			/>

			<Row gutter={[16, 16]} align="top">
				<Col xs={24} xl={12}>
					<Card title="创建/维护采集计划">
						<Form
							form={form}
							layout="vertical"
							initialValues={{
								source: dataSources[0]?.id,
								mode: "FULL",
								schedule: "MANUAL",
								profiler: "LIGHT",
								owner: dataSources[0]?.owner,
							}}
						>
							<Row gutter={12}>
								<Col span={12}>
									<Form.Item name="source" label="选择数据源">
										<Select
											options={dataSources.map((item) => ({
												label: `${item.name}（${item.type}）`,
												value: item.id,
											}))}
										/>
									</Form.Item>
								</Col>
								<Col span={12}>
									<Form.Item name="mode" label="采集模式">
										<Select
											options={[
												{ label: "全量扫描（首次/低频）", value: "FULL" },
												{ label: "增量扫描（仅发现变更）", value: "INCR" },
											]}
										/>
									</Form.Item>
								</Col>
							</Row>
							<Row gutter={12}>
								<Col span={12}>
									<Form.Item name="schemaAllow" label="Schema 白名单">
										<Input placeholder="例如：erp, public" />
									</Form.Item>
								</Col>
								<Col span={12}>
									<Form.Item name="tableFilter" label="表过滤（前缀/正则）">
										<Input placeholder="例如：^so_.*" />
									</Form.Item>
								</Col>
							</Row>
							<Row gutter={12}>
								<Col span={8}>
									<Form.Item name="schedule" label="执行计划">
										<Select
											options={[
												{ label: "仅手动", value: "MANUAL" },
												{ label: "每日 02:00", value: "DAILY" },
												{ label: "每小时", value: "HOURLY" },
												{ label: "每周日 02:00", value: "WEEKLY" },
											]}
										/>
									</Form.Item>
								</Col>
								<Col span={8}>
									<Form.Item name="profiler" label="字段剖析">
										<Select
											options={[
												{ label: "关闭", value: "OFF" },
												{ label: "轻量（空值率/基数）", value: "LIGHT" },
												{ label: "完整（分布/直方图）", value: "FULL" },
											]}
										/>
									</Form.Item>
								</Col>
								<Col span={8}>
									<Form.Item name="owner" label="责任人">
										<Input placeholder="例如：数据治理专员A" />
									</Form.Item>
								</Col>
							</Row>
							<Space>
								<Button onClick={() => toast.success("已触发采集（示例）")}>立即采集</Button>
								<Button type="primary" onClick={() => toast.success("已保存计划（示例）")}>
									保存计划
								</Button>
							</Space>
							<div className="mt-3 text-xs text-text-tertiary">
								后端会将数据源参数映射为采集配置，并触发实际运行（重构中，暂用占位数据）。
							</div>
						</Form>
					</Card>
				</Col>
				<Col xs={24} xl={12}>
					<Card title="采集结果预览">
						{selectedAsset ? (
							<Space direction="vertical" className="w-full" size={12}>
								<Form layout="vertical">
									<Form.Item label="已发现资产（按主题/库）">
										<Select
											value={selectedAssetId}
											onChange={setSelectedAssetId}
											options={assets.map((item) => ({ label: item.label, value: item.id }))}
										/>
									</Form.Item>
								</Form>
								<Descriptions size="small" bordered column={1}>
									<Descriptions.Item label="库/Schema">
										{selectedAsset.table.db}.{selectedAsset.table.schema}
									</Descriptions.Item>
									<Descriptions.Item label="表名">{selectedAsset.table.name}</Descriptions.Item>
									<Descriptions.Item label="备注">{selectedAsset.table.comment}</Descriptions.Item>
									<Descriptions.Item label="主键">{selectedAsset.table.pk}</Descriptions.Item>
								</Descriptions>
								<div>
									<Text type="secondary">字段列表</Text>
									<Table
										size="small"
										pagination={false}
										columns={assetColumns}
										dataSource={selectedAsset.cols}
									/>
								</div>
								<div>
									<Text type="secondary">本次扫描变更摘要</Text>
									<Space className="mt-2" wrap>
										{diffItems.map((item) => (
											<Tag key={item.label} color={item.color}>
												{item.label}：{item.value}
											</Tag>
										))}
									</Space>
								</div>
								<Button type="primary" onClick={() => toast.success("已生成入湖候选清单（示例）")}>生成入湖候选清单</Button>
							</Space>
						) : (
							<EmptyState title="暂无资产" description="请先完成采集或选择数据源。" />
						)}
					</Card>
				</Col>
			</Row>

			<Card title="采集历史" extra={<Button onClick={() => toast.success("已导出（示例）")}>导出</Button>}>
				<Table rowKey={(row) => row.id} columns={runColumns} dataSource={runs} pagination={{ pageSize: 6 }} />
			</Card>

			<Modal
				open={helpOpen}
				title="使用说明"
				onCancel={() => setHelpOpen(false)}
				footer={[
					<Button key="close" onClick={() => setHelpOpen(false)}>
						关闭
					</Button>,
				]}
			>
				<div className="space-y-2 text-sm text-slate-600">
					<div>1. 采集计划复用已建数据源连接，避免重复配置连接参数。</div>
					<div>2. 采集结果用于资产入库、质量校验与入湖任务候选清单。</div>
					<div>3. 当前为静态占位数据，后续将对接 OpenMetadata。</div>
				</div>
			</Modal>
		</div>
	);
}
