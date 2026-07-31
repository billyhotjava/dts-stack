import { Alert, Button, Empty, Form, Input, Radio, Select, Space, Tag, Typography } from "antd";
import type { FormInstance } from "antd/es/form";
import type { TableInfo } from "@/api/ingestion";
import type { InfraDataSource } from "@/api/services/dataSourcesService";
import { CompactTable } from "@/components/table";
import type { AccessPlanFormValues } from "./accessPlan.types";

type Props = {
	form: FormInstance<AccessPlanFormValues>;
	phase?: "source" | "resource";
	dataSources: InfraDataSource[];
	discoveredTables: TableInfo[];
	discovering: boolean;
	discoverError: string;
	onDiscover: () => void;
	onDiscoveryInputChange: () => void;
};

const tableKey = (table: TableInfo) => (table.schema ? `${table.schema}.${table.name}` : table.name);

export function DatabaseAccessStep({
	form,
	phase = "resource",
	dataSources,
	discoveredTables,
	discovering,
	discoverError,
	onDiscover,
	onDiscoveryInputChange,
}: Props) {
	const selectionMode = Form.useWatch("tableSelectionMode", form) || "all";
	const selectedTables = Form.useWatch("selectedTables", form) || [];

	if (phase === "source") {
		return (
			<div className="space-y-5">
				<div>
					<Typography.Title level={4}>选择数据库连接</Typography.Title>
					<Typography.Text type="secondary">
						仅使用平台已托管并验证的连接，不在任务内重复填写账号或 JDBC 地址。
					</Typography.Text>
				</div>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="name" label="任务名称" rules={[{ required: true, message: "请输入任务名称" }]}>
						<Input placeholder="例如：CRM 客户主数据入湖" />
					</Form.Item>
					<Form.Item name="sourceDataSourceId" label="源连接" rules={[{ required: true, message: "请选择数据库连接" }]}>
						<Select
							showSearch
							optionFilterProp="label"
							placeholder="选择已验证连接"
							onChange={onDiscoveryInputChange}
							options={dataSources.map((item) => ({ label: `${item.name} · ${item.type}`, value: item.id }))}
						/>
					</Form.Item>
				</div>
				<Form.Item name="description" label="用途说明">
					<Input.TextArea rows={2} placeholder="可选，说明数据用途和范围" />
				</Form.Item>
			</div>
		);
	}

	return (
		<div className="space-y-5">
			<div>
				<Typography.Title level={4}>定义入湖资源</Typography.Title>
				<Typography.Text type="secondary">表发现只读取结构清单，不扫描全表数据。</Typography.Text>
			</div>
			<div className="grid gap-4 md:grid-cols-3">
				<Form.Item name="sourceSystem" label="来源系统标识">
					<Input placeholder="例如：CRM" />
				</Form.Item>
				<Form.Item name="readerSchema" label="Schema">
					<Input placeholder="可选，例如：crm" onChange={onDiscoveryInputChange} />
				</Form.Item>
				<Form.Item name="readerTablePattern" label="表名筛选">
					<Input placeholder="可选，例如：customer%" onChange={onDiscoveryInputChange} />
				</Form.Item>
			</div>
			<Space wrap>
				<Button type="primary" ghost loading={discovering} onClick={onDiscover}>
					发现源表
				</Button>
				<Typography.Text type="secondary">结果仅用于配置任务，数据质量检查在任务运行后执行。</Typography.Text>
			</Space>
			{discoverError ? <Alert type="warning" showIcon message={discoverError} /> : null}
			<Form.Item name="tableSelectionMode" label="资源范围">
				<Radio.Group>
					<Radio.Button value="all">全部表</Radio.Button>
					<Radio.Button value="manual">手动选择</Radio.Button>
				</Radio.Group>
			</Form.Item>
			{discoveredTables.length ? (
				<CompactTable
					size="small"
					rowKey={tableKey}
					dataSource={discoveredTables}
					pagination={{ pageSize: 10, showSizeChanger: false }}
					rowSelection={
						selectionMode === "manual"
							? {
									selectedRowKeys: selectedTables,
									onChange: (keys) => form.setFieldValue("selectedTables", keys.map(String)),
								}
							: undefined
					}
					columns={[
						{ title: "Schema", dataIndex: "schema", key: "schema", render: (value) => value || "-" },
						{
							title: "表",
							dataIndex: "name",
							key: "name",
							render: (_value, row) => (
								<Space>
									<span>{tableKey(row)}</span>
									<Tag>{row.type || "TABLE"}</Tag>
								</Space>
							),
						},
					]}
				/>
			) : (
				<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未发现源表" />
			)}
			<Form.Item name="syncPrefix" label="ODS 表前缀" extra="平台将按前缀和源表名生成目标表名。">
				<Input placeholder="例如：ods_crm_" />
			</Form.Item>
		</div>
	);
}
