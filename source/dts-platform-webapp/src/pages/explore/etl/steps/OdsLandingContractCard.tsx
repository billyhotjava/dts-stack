import { Alert, Table, Tag, Typography } from "antd";
import type { ExtraColumnDef } from "./types";
import { normalizeText } from "../ingestionFormHelpers";

const { Text } = Typography;

type TechnicalColumn = {
	name: string;
	scope: "通用" | "文件";
	description: string;
};

const COMMON_COLUMNS: TechnicalColumn[] = [
	{ name: "_dts_source_system", scope: "通用", description: "来源系统" },
	{ name: "_dts_source_table", scope: "通用", description: "来源表或资源" },
	{ name: "_dts_import_time", scope: "通用", description: "入湖写入时间" },
	{ name: "_dts_batch_id", scope: "通用", description: "执行批次" },
	{ name: "_dts_execution_id", scope: "通用", description: "执行记录" },
	{ name: "_dts_task_id", scope: "通用", description: "接入任务" },
];

const FILE_COLUMNS: TechnicalColumn[] = [
	{ name: "_dts_source_file", scope: "文件", description: "来源文件" },
	{ name: "_dts_source_sheet", scope: "文件", description: "Excel sheet" },
	{ name: "_dts_file_hash", scope: "文件", description: "文件 hash" },
	{ name: "_dts_row_number", scope: "文件", description: "原始行号" },
];

export type OdsLandingContractCardProps = {
	sourceKind?: "database" | "file";
	columnPrefix?: string;
	columnSuffix?: string;
	extraColumns?: ExtraColumnDef[];
};

export function OdsLandingContractCard({
	sourceKind = "database",
	columnPrefix,
	columnSuffix,
	extraColumns = [],
}: OdsLandingContractCardProps) {
	const legacyRules = [
		normalizeText(columnPrefix) ? `字段名前缀：${normalizeText(columnPrefix)}` : "",
		normalizeText(columnSuffix) ? `字段名后缀：${normalizeText(columnSuffix)}` : "",
		...extraColumns.filter((item) => normalizeText(item.name)).map((item) => `追加字段：${item.name}`),
	].filter(Boolean);
	const columns = sourceKind === "file" ? [...COMMON_COLUMNS, ...FILE_COLUMNS] : COMMON_COLUMNS;

	return (
		<div className="mb-4 space-y-3">
			<Alert
				type="info"
				showIcon
				message="ODS 原样落地"
				description="源字段只复制原始业务值，字段重命名、类型标准化、枚举翻译和派生计算从 dbt stg 开始。"
			/>
			{legacyRules.length ? (
				<Alert
					type="warning"
					showIcon
					message="检测到历史字段改写配置"
					description={`新提交不会继续写入这些 ODS 字段改写规则：${legacyRules.join("；")}`}
				/>
			) : null}
			<Table<TechnicalColumn>
				size="small"
				rowKey="name"
				pagination={false}
				dataSource={columns}
				columns={[
					{
						title: "DTS 技术字段",
						dataIndex: "name",
						key: "name",
						render: (value: string) => <Text code>{value}</Text>,
					},
					{
						title: "范围",
						dataIndex: "scope",
						key: "scope",
						width: 100,
						render: (value: TechnicalColumn["scope"]) => <Tag color={value === "文件" ? "blue" : "green"}>{value}</Tag>,
					},
					{ title: "用途", dataIndex: "description", key: "description" },
				]}
			/>
		</div>
	);
}

export default OdsLandingContractCard;
