import type {
	ModelRepresentationDependency,
	ModelRepresentationLogicalModel,
} from "@/features/modeling/contracts/modelRepresentationContract";
import { StatusTag } from "../WorkspacePage";

export function BusinessModelVisualization({
	logicalModel,
	dependencyProjection,
}: {
	logicalModel: ModelRepresentationLogicalModel;
	dependencyProjection: ModelRepresentationDependency[];
}) {
	return (
		<div className="dm-model-editor__scroll">
			<section className="dm-editor-section">
				<h2>逻辑设计</h2>
				<div className="dm-model-form">
					<div className="dm-model-form__field">
						<span>模型名称</span>
						<strong>{logicalModel.name}</strong>
					</div>
					<div className="dm-model-form__field">
						<span>模型类型</span>
						<strong>{logicalModel.modelType}</strong>
					</div>
					<div className="dm-model-form__field">
						<span>数仓分层</span>
						<strong>{logicalModel.layer}</strong>
					</div>
					<div className="dm-model-form__field">
						<span>状态</span>
						<StatusTag tone={logicalModel.status === "PUBLISHED" ? "success" : "warning"}>
							{logicalModel.status}
						</StatusTag>
					</div>
					<div className="dm-model-form__field dm-model-form__field--wide">
						<span>业务定义</span>
						<p>{logicalModel.description || "尚未填写业务定义"}</p>
					</div>
					<div className="dm-model-form__field dm-model-form__field--wide">
						<span>物化策略</span>
						<p>{logicalModel.materialization || "尚未选择物化策略"}</p>
					</div>
				</div>
			</section>

			<section className="dm-editor-section">
				<h2>字段设计</h2>
				<div className="dm-field-table-wrap">
					<table className="dm-field-table">
						<thead>
							<tr>
								<th>字段名称</th>
								<th>业务名称</th>
								<th>数据类型</th>
								<th>字段作用</th>
								<th>可空</th>
								<th>事实来源</th>
							</tr>
						</thead>
						<tbody>
							{logicalModel.fields.map((field) => (
								<tr key={field.name}>
									<td>{field.name}</td>
									<td>{field.displayName || "-"}</td>
									<td>{field.dataType}</td>
									<td>{field.role}</td>
									<td>{field.nullable ? "是" : "否"}</td>
									<td>
										<StatusTag tone="info">{field.provenance.source}</StatusTag>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			</section>

			<section className="dm-editor-section">
				<h2>业务与模型依赖</h2>
				{dependencyProjection.length ? (
					<ul className="dm-diagnostic-list">
						{dependencyProjection.map((dependency) => (
							<li key={`${dependency.modelSpecId}-${dependency.revision}`}>
								<strong>{dependency.modelSpecId}</strong>
								<span>修订 r{dependency.revision}</span>
								<StatusTag tone="info">{dependency.provenance.source}</StatusTag>
							</li>
						))}
					</ul>
				) : (
					<p>当前模型没有已声明的上游模型依赖。</p>
				)}
			</section>
		</div>
	);
}
