import assert from "node:assert/strict";
import test from "node:test";
import {
	readLocalWorkbenchPreferenceItems,
	resetLocalWorkbenchPreferenceItems,
	resolveWorkbenchPreferenceOwner,
	saveLocalWorkbenchPreferenceItems,
} from "./workbenchLocalPreferences.ts";

function createMemoryStorage(): Storage {
	const data = new Map<string, string>();
	return {
		get length() {
			return data.size;
		},
		clear() {
			data.clear();
		},
		getItem(key: string) {
			return data.get(key) ?? null;
		},
		key(index: number) {
			return Array.from(data.keys())[index] ?? null;
		},
		removeItem(key: string) {
			data.delete(key);
		},
		setItem(key: string, value: string) {
			data.set(key, value);
		},
	};
}

test("local workbench preferences are stored per login user", () => {
	const storage = createMemoryStorage();
	const alice = resolveWorkbenchPreferenceOwner({ username: "alice" });
	const bob = resolveWorkbenchPreferenceOwner({ username: "bob" });

	saveLocalWorkbenchPreferenceItems(alice, [{ key: "todo", visible: false, order: 10 }], storage);
	saveLocalWorkbenchPreferenceItems(bob, [{ key: "screen-strip", visible: true, order: 10 }], storage);

	assert.deepEqual(readLocalWorkbenchPreferenceItems(alice, storage), [{ key: "todo", visible: false, order: 10 }]);
	assert.deepEqual(readLocalWorkbenchPreferenceItems(bob, storage), [{ key: "screen-strip", visible: true, order: 10 }]);
});

test("reset only clears the current user's local workbench preferences", () => {
	const storage = createMemoryStorage();
	const alice = resolveWorkbenchPreferenceOwner({ username: "alice" });
	const bob = resolveWorkbenchPreferenceOwner({ username: "bob" });

	saveLocalWorkbenchPreferenceItems(alice, [{ key: "todo", visible: false, order: 10 }], storage);
	saveLocalWorkbenchPreferenceItems(bob, [{ key: "screen-strip", visible: true, order: 10 }], storage);
	resetLocalWorkbenchPreferenceItems(alice, storage);

	assert.equal(readLocalWorkbenchPreferenceItems(alice, storage), null);
	assert.deepEqual(readLocalWorkbenchPreferenceItems(bob, storage), [{ key: "screen-strip", visible: true, order: 10 }]);
});
