import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { StorageEnum, ThemeColorPresets, ThemeLayout, ThemeMode } from "#/enum";
import { FontFamilyPreset, typographyTokens } from "@/theme/tokens/typography";

export type SettingsType = {
	themeColorPresets: ThemeColorPresets;
	themeMode: ThemeMode;
	themeLayout: ThemeLayout;
	themeStretch: boolean;
	breadCrumb: boolean;
	accordion: boolean;
	multiTab: boolean;
	darkSidebar: boolean;
	fontFamily: string;
	fontSize: number;
	direction: "ltr" | "rtl";
};
type SettingStore = {
	settings: SettingsType;
	// 使用 actions 命名空间来存放所有的 action
	actions: {
		setSettings: (settings: SettingsType) => void;
		setThemeLayout: (layout: ThemeLayout) => void;
		clearSettings: () => void;
	};
};

const useSettingStore = create<SettingStore>()(
    persist(
        (set) => ({
			settings: {
				themeColorPresets: ThemeColorPresets.Default,
				themeMode: ThemeMode.Light,
				themeLayout: ThemeLayout.Vertical,
				themeStretch: false,
				breadCrumb: true,
				accordion: false,
				multiTab: false,
				darkSidebar: false,
				fontFamily: FontFamilyPreset.openSans,
				fontSize: Number(typographyTokens.fontSize.sm),
				direction: "ltr",
			},
			actions: {
				setSettings: (settings) => {
					set({ settings });
				},
				setThemeLayout: (layout) => {
					set((state) => ({ settings: { ...state.settings, themeLayout: layout } }));
				},
				clearSettings() {
					useSettingStore.persist.clearStorage();
				},
			},
        }),
        {
            name: StorageEnum.Settings, // name of the item in the storage (must be unique)
            storage: createJSONStorage(() => localStorage), // (optional) by default, 'localStorage' is used
            partialize: (state) => ({ [StorageEnum.Settings]: state.settings }),
            version: 2,
            migrate: (persistedState: any, version) => {
                try {
                    // mark version as used to satisfy noUnusedParameters
                    void version;
                    const key = (StorageEnum as any).Settings || "settings";
                    const settings = (persistedState && (persistedState[key] || persistedState.settings)) || {};
                    // Coerce fontSize to a valid number and clamp sane range
                    const raw = settings.fontSize;
                    let next = typeof raw === "string" ? parseFloat(raw) : raw;
                    if (!Number.isFinite(next)) {
                        next = Number(typographyTokens.fontSize.sm);
                    }
                    // Clamp to [12, 24]
                    if (next < 12) next = 12;
                    if (next > 24) next = 24;
                    settings.fontSize = Math.round(next);
                    // Ensure fontFamily is a non-empty string
                    if (!settings.fontFamily || typeof settings.fontFamily !== "string") {
                        settings.fontFamily = FontFamilyPreset.openSans;
                    }
                    persistedState[key] = { ...settings };
                } catch {
                    // ignore migration errors
                }
                return persistedState;
            },
        },
    ),
);

export const useSettings = () => useSettingStore((state) => state.settings);
export const useSettingActions = () => useSettingStore((state) => state.actions);
