import { setupWorker } from "msw/browser";
import { mockTokenExpired } from "./handlers/_demo";
import { menuList } from "./handlers/_menu";
import { signIn, logout, userList } from "./handlers/_user";
import { keycloakHandlers } from "./handlers/_keycloak";
import { adminHandlers } from "./handlers/_admin";
import { iamHandlers } from "./handlers/_iam";
import { reportsHandlers } from "./handlers/_reports";
import { apiHandlers } from "./handlers/_apis";
import { catalogHandlers } from "./handlers/_catalog";

const handlers = [
	signIn,
	logout,
	userList,
	mockTokenExpired,
	menuList,
	...keycloakHandlers,
	...catalogHandlers,
	...adminHandlers,
	...iamHandlers,
	...reportsHandlers,
	...apiHandlers,
];
const worker = setupWorker(...handlers);

export { worker };
