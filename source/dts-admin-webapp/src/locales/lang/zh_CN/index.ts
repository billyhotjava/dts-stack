import common from "./common.json";
import sys from "./sys.json";
import keycloak from "./keycloak.json";
import profile from "./profile.json";

export default {
	...common,
	...sys,
	...keycloak,
	...profile,
};
