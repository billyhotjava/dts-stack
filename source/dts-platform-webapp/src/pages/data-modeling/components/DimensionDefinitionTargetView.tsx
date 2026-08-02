import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import { EmptyState, Panel, StatusTag } from "./WorkspacePage";

export function DimensionDefinitionTargetView({
	definition,
	error,
	loading,
}: {
	definition: DimensionDefinitionView | null;
	error: string | null;
	loading: boolean;
}) {
	if (loading) return <StatusTag tone="info">正在加载固定维度定义修订…</StatusTag>;
	if (error) return <EmptyState title="维度定义不可见" description={error} />;
	if (!definition) return <EmptyState title="未找到维度定义" description="目标维度定义不存在或当前账号无权读取。" />;

	return (
		<section className="dm-model-editor">
			<Panel
				subtitle={`固定修订 r${definition.revision} · ${definition.status}`}
				title={`${definition.name}（${definition.systemCode}）`}
			>
				<div className="dm-reverse-summary">
					<div>
						<span>数据域</span>
						<strong>{definition.domainId}</strong>
					</div>
					<div>
						<span>复用范围</span>
						<strong>{definition.reuseScope}</strong>
					</div>
					<div>
						<span>负责人</span>
						<strong>{definition.ownerId}</strong>
					</div>
					<div>
						<span>引用次数</span>
						<strong>{definition.usageCount}</strong>
					</div>
				</div>
				<p>{definition.definition}</p>
			</Panel>
			<Panel title="业务属性" subtitle="属性来自该维度定义的固定修订，只读展示。">
				<div className="dm-field-table-wrap">
					<table className="dm-field-table">
						<thead>
							<tr>
								<th>顺序</th>
								<th>属性编码</th>
								<th>属性名称</th>
								<th>业务定义</th>
								<th>业务主键</th>
								<th>标准引用</th>
							</tr>
						</thead>
						<tbody>
							{definition.attributes.map((attribute) => (
								<tr key={attribute.code}>
									<td>{attribute.order}</td>
									<td>{attribute.code}</td>
									<td>{attribute.name}</td>
									<td>{attribute.definition}</td>
									<td>{attribute.primaryKey ? "是" : "否"}</td>
									<td>
										{attribute.standardRef
											? `${attribute.standardRef}${attribute.standardVersion ? ` · ${attribute.standardVersion}` : ""}`
											: "—"}
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			</Panel>
		</section>
	);
}
