import { setupWorker } from "msw/browser";
import { mockTokenExpired } from "./handlers/_demo";
import { menuList } from "./handlers/_menu";
import { signIn, logout, userList } from "./handlers/_user";
import { keycloakHandlers } from "./handlers/_keycloak";
import { adminHandlers } from "./handlers/_admin";

const handlers = [
	signIn,
	logout,
	userList,
	mockTokenExpired,
	menuList,
	...keycloakHandlers,
	...adminHandlers,
];
const worker = setupWorker(...handlers);

export { worker };
