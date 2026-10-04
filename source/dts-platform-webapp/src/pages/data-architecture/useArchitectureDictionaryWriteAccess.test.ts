import { describe, expect, it } from "vitest";
import { hasArchitectureDictionaryWriteAccess } from "./useArchitectureDictionaryWriteAccess";

describe("architecture dictionary write access", () => {
	it.each(["ROLE_ADMIN", "ROLE_OP_ADMIN", "ROLE_INST_DATA_OWNER"])("allows approved platform role %s", (role) => {
		expect(hasArchitectureDictionaryWriteAccess([role])).toBe(true);
	});

	it.each(["ROLE_DEPT_DATA_OWNER", "ROLE_DEPT_LEADER", "ROLE_USER"])("keeps department role %s read-only", (role) => {
		expect(hasArchitectureDictionaryWriteAccess([role])).toBe(false);
	});
});
