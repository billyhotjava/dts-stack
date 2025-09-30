import { useTranslation } from "react-i18next";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";

interface FeaturePlaceholderProps {
	titleKey: string;
	descriptionKey?: string;
}

export default function FeaturePlaceholder({ titleKey, descriptionKey }: FeaturePlaceholderProps) {
	const { t } = useTranslation();

	return (
		<section className="flex h-full flex-col items-center justify-center gap-6 px-6 py-12 text-center">
			<div className="flex flex-col items-center gap-4">
				<Badge variant="outline">{t("sys.portal.placeholderBadge")}</Badge>
				<Icon icon="solar:compass-bold-duotone" size="72" className="text-primary/80" />
			</div>
			<div className="flex max-w-3xl flex-col items-center gap-3">
				<h1 className="text-2xl font-semibold text-text-primary">{t(titleKey)}</h1>
				{descriptionKey ? <p className="text-sm leading-relaxed text-text-secondary">{t(descriptionKey)}</p> : null}
				<p className="text-xs text-text-tertiary">{t("sys.portal.placeholderMessage")}</p>
			</div>
		</section>
	);
}
