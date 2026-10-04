import { create } from "zustand";
import type { MenuTree } from "#/entity";

type MenuState = {
	menus: MenuTree[];
	loaded: boolean;
	setMenus: (items: MenuTree[]) => void;
	clearMenus: () => void;
};

export const useMenuStore = create<MenuState>((set) => ({
	menus: [],
	loaded: false,
	setMenus: (items) => set({ menus: items, loaded: true }),
	clearMenus: () => set({ menus: [], loaded: false }),
}));

export const getMenus = () => useMenuStore.getState().menus;
