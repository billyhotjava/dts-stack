interface Props {
	payloadJson?: string | null;
	diffJson?: string | null;
}

export default function ChangeRequestDiff({ payloadJson, diffJson }: Props) {
	return (
		<div className="grid gap-4 md:grid-cols-2">
			<section className="rounded-lg border bg-muted/40 p-4">
				<h4 className="mb-2 text-sm font-semibold text-muted-foreground">变更内容</h4>
				<pre className="max-h-64 overflow-auto whitespace-pre-wrap text-xs">{pretty(payloadJson) || "--"}</pre>
			</section>
			<section className="rounded-lg border bg-muted/40 p-4">
				<h4 className="mb-2 text-sm font-semibold text-muted-foreground">差异对比</h4>
				<pre className="max-h-64 overflow-auto whitespace-pre-wrap text-xs">{pretty(diffJson) || "--"}</pre>
			</section>
		</div>
	);
}

function pretty(value?: string | null) {
	if (!value) return "";
	try {
		return JSON.stringify(JSON.parse(value), null, 2);
	} catch (error) {
		return value;
	}
}
