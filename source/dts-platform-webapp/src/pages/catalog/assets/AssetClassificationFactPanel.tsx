import { Alert, Descriptions, Space, Spin, Tag } from "antd";
import { useEffect, useMemo, useState } from "react";
import { type ClassificationFactView, getCatalogClassificationFacts } from "@/api/platformApi";
import { CompactTable } from "@/components/table";
import { classificationText, formatTime } from "./assetPageShared";

type Props = {
	assetKey?: string;
	columns?: Array<Record<string, any>>;
};

const columnSubjectKey = (assetKey: string, columnName: string) =>
	`${assetKey}/column:${columnName
		.trim()
		.toLowerCase()
		.replace(/[^a-z0-9_.:-]+/g, "_")
		.replace(/_+/g, "_")
		.replace(/^_+|_+$/g, "")}`;

export function AssetClassificationFactPanel({ assetKey, columns = [] }: Props) {
	const [facts, setFacts] = useState<ClassificationFactView[]>([]);
	const [loading, setLoading] = useState(false);
	const subjects = useMemo(() => {
		if (!assetKey) return [];
		return [
			{ subjectType: "ASSET", subjectKey: assetKey },
			...columns
				.map((column) => String(column.name || "").trim())
				.filter(Boolean)
				.map((name) => ({ subjectType: "COLUMN", subjectKey: columnSubjectKey(assetKey, name) })),
		];
	}, [assetKey, columns]);

	useEffect(() => {
		if (subjects.length === 0) {
			setFacts([]);
			return;
		}
		let cancelled = false;
		setLoading(true);
		void getCatalogClassificationFacts(subjects)
			.then((result) => {
				if (!cancelled) setFacts(Array.isArray(result) ? result : []);
			})
			.catch(() => {
				if (!cancelled) setFacts([]);
			})
			.finally(() => {
				if (!cancelled) setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [subjects]);

	const assetFact = facts.find((fact) => fact.subjectType === "ASSET");
	const columnFactMap = new Map(
		facts.filter((fact) => fact.subjectType === "COLUMN").map((fact) => [fact.subjectKey, fact]),
	);

	if (!assetKey) {
		return <Alert type="info" showIcon message="资产身份合同尚未就绪，无法读取密级事实" />;
	}

	return (
		<Spin spinning={loading}>
			<div className="space-y-4">
				<Alert
					type={assetFact?.sealed ? "success" : "warning"}
					showIcon
					message={assetFact?.sealed ? "密级事实已封存，只能升高不能降低" : "密级事实尚未封存"}
					description="数据标签与合规密级相互独立；本页密级来自不可变来源事实、自动识别、人工下限和血缘继承。"
				/>
				<Descriptions bordered size="small" column={2}>
					<Descriptions.Item label="来源声明">{classificationText(assetFact?.declaredLevel)}</Descriptions.Item>
					<Descriptions.Item label="识别结果">{classificationText(assetFact?.detectedLevel)}</Descriptions.Item>
					<Descriptions.Item label="人工下限">{classificationText(assetFact?.manualFloor)}</Descriptions.Item>
					<Descriptions.Item label="有效密级">
						<Tag color={assetFact?.effectiveLevel ? "orange" : "red"}>
							{classificationText(assetFact?.effectiveLevel)}
						</Tag>
					</Descriptions.Item>
					<Descriptions.Item label="最高来源">
						{assetFact?.highestSourceType || assetFact?.originType || "-"}
					</Descriptions.Item>
					<Descriptions.Item label="传播状态">
						<Tag color={assetFact?.propagationStatus === "PROPAGATED" ? "green" : "gold"}>
							{assetFact?.propagationStatus || "UNSEALED"}
						</Tag>
					</Descriptions.Item>
					<Descriptions.Item label="快照版本">v{assetFact?.snapshotVersion ?? 0}</Descriptions.Item>
					<Descriptions.Item label="封存时间">{formatTime(assetFact?.sealedAt)}</Descriptions.Item>
				</Descriptions>
				<CompactTable
					rowKey={(row) => String(row.id || row.name)}
					pagination={{ pageSize: 20 }}
					dataSource={columns}
					columns={[
						{ title: "字段", dataIndex: "name", width: 220 },
						{ title: "类型", dataIndex: "dataType", width: 160 },
						{
							title: "字段有效密级",
							render: (_, row) => {
								const name = String(row.name || "").trim();
								const fact = name ? columnFactMap.get(columnSubjectKey(assetKey, name)) : undefined;
								return (
									<Space>
										<Tag color={fact?.effectiveLevel ? "orange" : "default"}>
											{classificationText(fact?.effectiveLevel)}
										</Tag>
										<span className="text-xs text-slate-500">
											{fact?.sealed ? `v${fact.snapshotVersion ?? 0}` : "待封存"}
										</span>
									</Space>
								);
							},
						},
						{
							title: "继承来源",
							render: (_, row) => {
								const name = String(row.name || "").trim();
								const fact = name ? columnFactMap.get(columnSubjectKey(assetKey, name)) : undefined;
								return fact?.highestSourceType || fact?.originType || "-";
							},
						},
					]}
				/>
			</div>
		</Spin>
	);
}
