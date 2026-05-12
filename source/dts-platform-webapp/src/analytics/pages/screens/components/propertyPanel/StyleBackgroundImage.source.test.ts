import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const backgroundImageRowPath = new URL("./BackgroundImageRow.tsx", import.meta.url);
const basicSchemaPath = new URL("../../configSchema/schemas/basic.ts", import.meta.url);
const schemaRendererPath = new URL("../../configSchema/editors/SchemaConfigRenderer.tsx", import.meta.url);
const fieldEditorPath = new URL("../../configSchema/editors/FieldEditor.tsx", import.meta.url);
const basicRendererPath = new URL("../../renderers/BasicRenderer.tsx", import.meta.url);

test("style tab background image controls share the hardened screen-image upload flow", async () => {
	const [
		propertyPanelSource,
		backgroundRowSource,
		basicSchemaSource,
		schemaRendererSource,
		fieldEditorSource,
		basicRendererSource,
	] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(backgroundImageRowPath, "utf8"),
		readFile(basicSchemaPath, "utf8"),
		readFile(schemaRendererPath, "utf8"),
		readFile(fieldEditorPath, "utf8"),
		readFile(basicRendererPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /<BackgroundImageRow/);
	assert.match(propertyPanelSource, /updateConfig\(\{ backgroundImage: url \}\)/);
	assert.match(backgroundRowSource, /url: '\/infra\/screen-images\/upload'/);
	assert.match(backgroundRowSource, /resolveScreenImageUploadUrl/);
	assert.match(backgroundRowSource, /getScreenImageUploadErrorMessage/);
	assert.match(backgroundRowSource, /SCREEN_IMAGE_UPLOAD_LIMIT_BYTES/);

	assert.match(basicSchemaSource, /key: 'backgroundImage', label: '背景图', type: 'image-url'/);
	assert.match(schemaRendererSource, /fullWidth=\{field\.type === 'image-url'\}/);
	assert.match(fieldEditorSource, /url: '\/infra\/screen-images\/upload'/);
	assert.match(fieldEditorSource, /resolveScreenImageUploadUrl/);
	assert.match(basicRendererSource, /c\.backgroundImage && isSafeSrcUrl\(c\.backgroundImage\)/);
	assert.match(basicRendererSource, /backgroundImage: `url\(\$\{c\.backgroundImage as string\}\)`/);
});
