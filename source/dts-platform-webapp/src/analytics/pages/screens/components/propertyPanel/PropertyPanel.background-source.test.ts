import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const backgroundImageRowPath = new URL("./BackgroundImageRow.tsx", import.meta.url);

test("PropertyPanel delegates background image upload to the extracted row component", async () => {
	const [propertyPanelSource, backgroundRowSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(backgroundImageRowPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/BackgroundImageRow'/);
	assert.equal(propertyPanelSource.includes("function BackgroundImageRow("), false);
	assert.match(backgroundRowSource, /export function BackgroundImageRow/);
	assert.match(backgroundRowSource, /\/infra\/screen-images\/upload/);
	assert.match(backgroundRowSource, /getScreenImageUploadErrorMessage/);
	assert.match(backgroundRowSource, /resolveScreenImageUploadUrl/);
	assert.match(backgroundRowSource, /SCREEN_IMAGE_UPLOAD_LIMIT_BYTES/);
	assert.match(backgroundRowSource, /_skipErrorToast:\s*true/);
	assert.match(backgroundRowSource, /import \{ X \} from 'lucide-react'/);
	assert.equal(backgroundRowSource.includes("@ts-nocheck"), false);
	assert.equal(backgroundRowSource.includes("✕"), false);
	assert.equal(backgroundRowSource.includes("Request failed with status code"), false);
});
