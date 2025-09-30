import type { MenuTree } from "#/entity";
import apiClient from "../apiClient";

export enum MenuApi {
	Menu = "/menu",
}

const getMenuList = () => apiClient.get<MenuTree[]>({ url: MenuApi.Menu });

export default {
	getMenuList,
};
