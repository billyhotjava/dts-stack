import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { KeepAliveTab } from "../types";

export function useTabLabelRender() {
	const { t } = useTranslation();

    const specialTabRenderMap = useMemo<Record<string, (tab: KeepAliveTab) => React.ReactNode>>(
        () => ({
            // In non-mock mode, we don't resolve usernames from mock DB; just return default label.
            "sys.nav.system.user_detail": (tab: KeepAliveTab) => t(tab.label),
        }),
        [t],
    );

	const renderTabLabel = (tab: KeepAliveTab) => {
		const specialRender = specialTabRenderMap[tab.label];
		if (specialRender) {
			return specialRender(tab);
		}
		return t(tab.label);
	};

	return renderTabLabel;
}
