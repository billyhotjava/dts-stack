import type { ModelWorkbenchSummary } from "@/api/modelDeliveryStatusApi";
import { Status } from "./PrototypePrimitives";

/**
 * F15 K1 cells for the modeling workbench list: the latest build of the current design revision and the
 * latest published version. Registration, quality, analysis and runs are owned by their own pages and are
 * deliberately not shown here.
 */

const ENVIRONMENT_LABELS: Record<string, string> = { dev: "开发环境", test: "测试环境", prod: "生产环境" };

const environmentLabel = (value?: string | null) => (value ? ENVIRONMENT_LABELS[value] || value : "");

const formatTime = (value?: string | null) => {
	if (!value) return "";
	const parsed = new Date(value);
	return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN", { hour12: false });
};

function Note({ text }: { text: string }) {
	return text ? (
		<span className="dmx-summary-note" title={text}>
			{text}
		</span>
	) : null;
}

export function ModelBuildSummaryCell({
	summary,
	loading,
}: {
	summary: ModelWorkbenchSummary | undefined;
	loading: boolean;
}) {
	if (loading && !summary) return <span className="dmx-table-muted">读取中…</span>;
	if (!summary) return <span className="dmx-table-muted">—</span>;
	if (summary.readState === "FAILED") return <Status tone="danger">状态读取失败</Status>;
	const build = summary.build;
	if (!build || build.state === "NOT_STARTED") return <Status tone="info">未构建</Status>;
	const mode = build.buildMode === "SCHEMA_ONLY" ? "仅结构" : "数据构建";
	const facts = [`r${build.modelRevision}`, environmentLabel(build.environment), mode].filter(Boolean).join(" · ");
	if (build.state === "SUCCEEDED")
		return (
			<>
				<Status tone="success">{build.buildMode === "SCHEMA_ONLY" ? "结构已创建" : "构建完成"}</Status>
				<Note text={[facts, formatTime(build.observedAt), build.targetRelation].filter(Boolean).join(" · ")} />
			</>
		);
	if (build.state === "RUNNING")
		return (
			<>
				<Status tone="info">构建中</Status>
				<Note text={facts} />
			</>
		);
	if (build.state === "FAILED")
		return (
			<>
				<Status tone="danger">构建失败</Status>
				<Note text={`${facts} · 打开「构建」查看原因`} />
			</>
		);
	return (
		<>
			<Status tone="warning">待确认</Status>
			<Note text={`${facts} · 结果尚未核验或不属于当前版本`} />
		</>
	);
}

export function ModelPublishedSummaryCell({
	summary,
	loading,
}: {
	summary: ModelWorkbenchSummary | undefined;
	loading: boolean;
}) {
	if (loading && !summary) return <span className="dmx-table-muted">读取中…</span>;
	if (!summary) return <span className="dmx-table-muted">—</span>;
	if (summary.publishedReadState === "FAILED") return <Status tone="danger">发布记录读取失败</Status>;
	const published = summary.published;
	if (!published) return <span className="dmx-table-muted">未发布</span>;
	return (
		<>
			<strong>r{published.modelRevision}</strong>
			<Note
				text={[environmentLabel(published.environment), formatTime(published.publishedAt), "发布"]
					.filter(Boolean)
					.join(" · ")}
			/>
		</>
	);
}
