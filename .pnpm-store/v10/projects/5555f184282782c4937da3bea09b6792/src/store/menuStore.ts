import { create } from "zustand";
import type { MenuTree } from "#/entity";

type MenuState = {
	menus: MenuTree[];
	setMenus: (items: MenuTree[]) => void;
	clearMenus: () => void;
};

export const useMenuStore = create<MenuState>((set) => ({
	menus: [],
	setMenus: (items) => set({ menus: items }),
	clearMenus: () => set({ menus: [] }),
}));

export const getMenus = () => useMenuStore.getState().menus;
