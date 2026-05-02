import LineageColumnsPage from "./LineageColumnsPage";
import LineageDiffPage from "./LineageDiffPage";
import LineageGraphPage from "./LineageGraphPage";
import LineageImpactPage from "./LineageImpactPage";
import LineageImportPage from "./LineageImportPage";
import type { LineageSection } from "./lineageShared";

export type { LineageSection } from "./lineageShared";

export default function LineagePage({ section = "impact" }: { section?: LineageSection }) {
	if (section === "graph") return <LineageGraphPage />;
	if (section === "columns") return <LineageColumnsPage />;
	if (section === "import") return <LineageImportPage />;
	if (section === "diff") return <LineageDiffPage />;
	return <LineageImpactPage />;
}
