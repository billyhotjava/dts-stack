import { useEffect } from "react";
import { LineLoading } from "@/components/loading";

export type SemanticModelingSection = "overview" | "subjects" | "objects" | "metrics" | "models" | "publish" | "runs";

const semanticServicePath: Record<SemanticModelingSection, string> = {
	overview: "/modeling/metric-workbench",
	subjects: "/governance/subjects",
	objects: "/modeling/semantic/objects",
	metrics: "/modeling/semantic/metrics",
	models: "/modeling/semantic/models",
	publish: "/modeling/semantic/publish",
	runs: "/ops/instances?entryKey=DBT_RUN",
};

export default function SemanticModelingCenterPage({ section = "overview" }: { section?: SemanticModelingSection }) {
	useEffect(() => {
		window.location.replace(semanticServicePath[section] || semanticServicePath.overview);
	}, [section]);

	return <LineLoading />;
}
