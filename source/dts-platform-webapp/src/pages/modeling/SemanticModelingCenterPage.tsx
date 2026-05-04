import { Navigate } from "react-router";
import { semanticSectionMeta, type SemanticModelingSection } from "@/pages/metrics/semantic/semanticModelingShared";

export type { SemanticModelingSection };

export default function SemanticModelingCenterPage({ section = "overview" }: { section?: SemanticModelingSection }) {
	return <Navigate to={semanticSectionMeta[section]?.path || semanticSectionMeta.overview.path} replace />;
}
