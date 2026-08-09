import type { ModelLifecycleArtifact } from "@/api/modelSpecApi";

export function ModelLifecycleArtifactsTable({ artifacts }: { artifacts: ModelLifecycleArtifact[] }) {
	return (
		<div className="dmx-table-scroll">
			<table className="dmx-table">
				<thead>
					<tr>
						<th>制品</th>
						<th>实现版本</th>
						<th>物化方式</th>
						<th>状态</th>
						<th>校验和</th>
					</tr>
				</thead>
				<tbody>
					{artifacts.map((item) => (
						<tr key={item.id}>
							<td>{item.path}</td>
							<td>r{item.implementationRevision}</td>
							<td>{item.materialization}</td>
							<td>{item.status}</td>
							<td>{item.checksum}</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	);
}
