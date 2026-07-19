import { Button, Empty, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useNavigate } from "react-router";
import { modelSpecDetailPath } from "../modelSpecDetailNavigation";
import type { CanonicalModelSpecView, ModelSpecStandardBinding } from "../modelSpecV2Contract";

const { Text } = Typography;

type Props = {
	model: CanonicalModelSpecView;
};

type BindingRow = {
	key: string;
	fieldName: string;
	fieldSecurityLevel?: string | null;
	binding?: ModelSpecStandardBinding;
};

const versionedRef = (id?: string | null, version?: number | null) =>
	id && version != null ? `${id} · v${version}` : "—";

export function ModelSpecStandardsTab({ model }: Props) {
	const navigate = useNavigate();
	const standardBindings = model.standardBindings || [];
	const bindingByField = new Map(standardBindings.map((binding) => [binding.fieldName, binding]));
	const rows: BindingRow[] = model.fields.map((field) => ({
		key: field.name,
		fieldName: field.name,
		fieldSecurityLevel: field.securityLevel,
		binding: bindingByField.get(field.name),
	}));
	const columns: ColumnsType<BindingRow> = [
		{ title: "字段", dataIndex: "fieldName", width: 180 },
		{
			title: "数据元",
			render: (_, row) => versionedRef(row.binding?.standardElementId, row.binding?.standardElementVersion),
		},
		{
			title: "公共码表",
			render: (_, row) => versionedRef(row.binding?.referenceCode, row.binding?.referenceCodeVersion),
		},
		{
			title: "度量单位",
			render: (_, row) => versionedRef(row.binding?.measurementUnitId, row.binding?.measurementUnitVersion),
		},
		{ title: "安全等级", render: (_, row) => row.binding?.securityLevel || row.fieldSecurityLevel || "—" },
		{
			title: "状态",
			width: 100,
			render: (_, row) =>
				row.binding ? (
					<Tag color="green">已关联</Tag>
				) : row.fieldSecurityLevel ? (
					<Tag color="blue">已配置</Tag>
				) : (
					<Tag>未关联</Tag>
				),
		},
	];

	const openStandardOwner = () => {
		const returnTo = modelSpecDetailPath(model.id, "standards", model.planId);
		const params = new URLSearchParams({
			modelSpecId: model.id,
			modelRevision: String(model.revision),
			returnTo,
		});
		navigate(`/governance/standards/elements?${params.toString()}`);
	};

	return (
		<div data-testid="model-spec-standards-tab">
			<div className="mb-3 flex flex-wrap items-center justify-between gap-3">
				<Space direction="vertical" size={0}>
					<Text strong>字段标准</Text>
					<Text type="secondary" className="text-xs">
						查看字段已关联的数据元、公共码表、度量单位和安全等级
					</Text>
					<Text type="secondary" className="text-xs">
						当前显示已保存版本 r{model.revision}
					</Text>
				</Space>
				<Button onClick={openStandardOwner}>前往数据元</Button>
			</div>
			{rows.length > 0 ? (
				<Table<BindingRow>
					size="small"
					rowKey="key"
					columns={columns}
					dataSource={rows}
					pagination={false}
					scroll={{ x: 900 }}
				/>
			) : (
				<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请先在“字段设计”中添加字段" />
			)}
		</div>
	);
}
