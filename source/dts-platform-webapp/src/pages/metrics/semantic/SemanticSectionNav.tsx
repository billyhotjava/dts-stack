import { Card, Segmented } from "antd";
import { useRouter } from "@/routes/hooks";
import { semanticSectionMeta, semanticSections, type SemanticModelingSection } from "./semanticModelingShared";

export function SemanticSectionNav({ activeSection }: { activeSection: SemanticModelingSection }) {
	const router = useRouter();

	return (
		<Card>
			<Segmented
				value={activeSection}
				onChange={(value) => router.push(semanticSectionMeta[value as SemanticModelingSection].path)}
				options={semanticSections.map((key) => ({
					label: semanticSectionMeta[key].title,
					value: key,
				}))}
			/>
		</Card>
	);
}
