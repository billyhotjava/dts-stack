import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { LocalEnum } from "#/enum";

type LocaleFormatter = (key: string, options?: Record<string, unknown>) => string;

export function useBilingualText(): LocaleFormatter {
	const { i18n } = useTranslation();

	return useMemo(() => {
		const currentLanguage = (i18n.language ?? LocalEnum.zh_CN).toLowerCase();
		const preferZh = currentLanguage.startsWith("zh");

		const primaryTranslator = i18n.getFixedT(preferZh ? LocalEnum.zh_CN : LocalEnum.en_US);
		const fallbackTranslator = i18n.getFixedT(preferZh ? LocalEnum.en_US : LocalEnum.zh_CN);

		return (key: string, options?: Record<string, unknown>) => {
			const primary = (primaryTranslator(key, options) as string | undefined)?.trim();
			if (primary) {
				return primary;
			}
			const fallback = (fallbackTranslator(key, options) as string | undefined)?.trim();
			return fallback ?? "";
		};
	}, [i18n, i18n.language]);
}
