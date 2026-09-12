import { useMemo } from "react";
import type { ModelLifecycleArtifact } from "@/api/modelSpecApi";
import { type CompactColumns, CompactTable } from "@/components/table";

export function ModelLifecycleArtifactsTable({ artifacts }: { artifacts: ModelLifecycleArtifact[] }) {
	const columns = useMemo<CompactColumns<ModelLifecycleArtifact>>(
		() => [
			{ title: "制品", dataIndex: "path" },
			{
				title: "实现版本",
				dataIndex: "implementationRevision",
				render: (value: number) => `r${value}`,
			},
			{ title: "存储方式", dataIndex: "materialization" },
			{ title: "状态", dataIndex: "status" },
			{ title: "校验码", dataIndex: "checksum" },
		],
		[],
	);
	return (
		<CompactTable<ModelLifecycleArtifact>
			className="dmx-lifecycle-artifacts-table"
			columns={columns}
			dataSource={artifacts}
			pagination={false}
			rowKey="id"
		/>
	);
}
