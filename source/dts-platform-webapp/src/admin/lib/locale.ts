import { useMemo } from "react";
import { useBilingualText } from "@/hooks/useBilingualText";

type Translator = (value?: string | null, fallback?: string) => string;

const PREFIX = "sys.admin";

export function useAdminLocale() {
	const bilingual = useBilingualText();

	return useMemo(() => {
		const toTranslator = (category: string): Translator => {
			return (value, fallback) => {
				if (!value) return fallback ?? "";
				const key = `${PREFIX}.${category}.${value}`;
				const translated = bilingual(key);
				if (!translated || translated === key) {
					return fallback ?? value;
				}
				return translated;
			};
		};

		return {
			translateAction: toTranslator("action"),
			translateStatus: toTranslator("status"),
			translateResource: toTranslator("resource"),
			translateOutcome: toTranslator("outcome"),
			translateRole: toTranslator("role"),
			translateTab: toTranslator("tab"),
			translateSensitivity: toTranslator("sensitivity"),
		};
	}, [bilingual]);
}

