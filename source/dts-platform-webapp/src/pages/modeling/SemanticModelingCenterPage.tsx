import { useEffect } from "react";
import { LineLoading } from "@/components/loading";

export type SemanticModelingSection = "overview" | "subjects" | "objects" | "metrics" | "models" | "publish" | "runs";

const semanticServicePath: Record<SemanticModelingSection, string> = {
	overview: "/metrics/semantic",
	subjects: "/metrics/semantic/subjects",
	objects: "/metrics/semantic/objects",
	metrics: "/metrics/semantic/metrics",
	models: "/metrics/semantic/models",
	publish: "/metrics/semantic/publish",
	runs: "/metrics/semantic/runs",
};

export default function SemanticModelingCenterPage({ section = "overview" }: { section?: SemanticModelingSection }) {
	useEffect(() => {
		window.location.replace(semanticServicePath[section] || semanticServicePath.overview);
	}, [section]);

	return <LineLoading />;
}
