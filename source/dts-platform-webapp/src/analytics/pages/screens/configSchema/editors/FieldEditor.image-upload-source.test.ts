import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const fieldEditorPath = new URL("./FieldEditor.tsx", import.meta.url);

test("image config field uses the shared screen image upload guard and readable errors", async () => {
	const source = await readFile(fieldEditorPath, "utf8");

	assert.match(source, /screenImageUpload/);
	assert.match(source, /SCREEN_IMAGE_UPLOAD_LIMIT_BYTES/);
	assert.match(source, /getScreenImageUploadErrorMessage/);
	assert.match(source, /\/infra\/screen-images\/upload/);
	assert.match(source, /_skipErrorToast:\s*true/);
	assert.equal(source.includes("Request failed with status code"), false);
});
