import { App as AntApp, Button, Empty, Tag } from "antd";
import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { metricService } from "@/mock/services/metricService";
import type { SemanticSubject } from "@/types/metric";
import { SectionTitle, Surface } from "@/ui/components";

export function SemanticTab({ departmentId }: { departmentId: string }) {
	const { message } = AntApp.useApp();
	const [subjects, setSubjects] = useState<SemanticSubject[]>([]);

	useEffect(() => {
		let alive = true;
		void metricService.listSubjects(departmentId).then((r) => {
			if (alive) setSubjects(unwrap(r));
		});
		return () => {
			alive = false;
		};
	}, [departmentId]);

	if (subjects.length === 0) return <Empty description="本部门暂无语义主题" style={{ marginTop: 40 }} />;

	return (
		<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
			<div style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}>
				语义层把数据集对象组织为主题域 → 度量 + 维度，向上支撑指标，向下映射 dbt 模型。
			</div>
			{subjects.map((s) => (
				<Surface key={s.id} pad="md">
					<SectionTitle
						title={s.name}
						desc={`关联数据集：${s.datasetId}`}
						extra={
							<span style={{ display: "inline-flex", gap: 8, alignItems: "center" }}>
								<Tag color={s.status === "published" ? "green" : "default"}>{s.status === "published" ? "已发布" : "草稿"}</Tag>
								<Button size="small" disabled={s.status === "published"} onClick={() => message.success(`${s.name} 已发布`)}>
									发布
								</Button>
							</span>
						}
					/>
					<div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
						<div>
							<span style={{ fontSize: 12, color: "var(--ink-subtle)", marginRight: 8 }}>度量</span>
							{s.metricCodes.map((c) => (
								<Tag key={c} color="blue" style={{ fontFamily: "var(--font-mono, monospace)" }}>{c}</Tag>
							))}
						</div>
						<div>
							<span style={{ fontSize: 12, color: "var(--ink-subtle)", marginRight: 8 }}>维度</span>
							{s.dimensions.map((d) => (
								<Tag key={d}>{d}</Tag>
							))}
						</div>
					</div>
				</Surface>
			))}
		</div>
	);
}
