import assert from "node:assert/strict";
import test from "node:test";
import JSZip from "jszip";
import {
	SCREEN_PACKAGE_MANIFEST_FILE,
	SCREEN_PACKAGE_SCREEN_FILE,
	buildScreenPackageZip,
	parseScreenImportFile,
} from "./screenPackage";

function asImportFile(name: string, blob: Blob): File {
	return Object.assign(blob, {
		name,
		lastModified: 0,
		webkitRelativePath: "",
	}) as File;
}

test("buildScreenPackageZip exports screen json, manifest, and internal image resources", async () => {
	const screenSpec = {
		name: "项目大屏",
		backgroundImage: "/api/infra/screen-images/bg.png",
		components: [
			{ id: "image-1", type: "image", config: { imageUrl: "/api/infra/screen-images/logo.jpg" } },
			{ id: "external", type: "image", config: { imageUrl: "https://example.com/keep.png" } },
		],
	};
	const fetched: string[] = [];

	const result = await buildScreenPackageZip({
		screenName: "项目大屏",
		screenSpec,
		fetchResource: async (url) => {
			fetched.push(url);
			return new Blob([`resource:${url}`], { type: url.endsWith(".jpg") ? "image/jpeg" : "image/png" });
		},
	});

	assert.equal(result.resourceCount, 2);
	assert.deepEqual(fetched, ["/api/infra/screen-images/bg.png", "/api/infra/screen-images/logo.jpg"]);

	const zip = await JSZip.loadAsync(await result.blob.arrayBuffer());
	assert.ok(zip.file(SCREEN_PACKAGE_SCREEN_FILE));
	assert.ok(zip.file(SCREEN_PACKAGE_MANIFEST_FILE));
	assert.ok(zip.file("resources/images/bg.png"));
	assert.ok(zip.file("resources/images/logo.jpg"));

	const manifest = JSON.parse((await zip.file(SCREEN_PACKAGE_MANIFEST_FILE)?.async("string")) ?? "{}") as {
		schema?: string;
		resources?: Array<{ originalUrl: string; path: string; contentType?: string }>;
	};
	assert.equal(manifest.schema, "dts.screen.package");
	assert.deepEqual(
		manifest.resources?.map((item) => item.originalUrl),
		["/api/infra/screen-images/bg.png", "/api/infra/screen-images/logo.jpg"],
	);

	const screenPayload = JSON.parse((await zip.file(SCREEN_PACKAGE_SCREEN_FILE)?.async("string")) ?? "{}") as {
		screenSpec?: typeof screenSpec;
		packagedResources?: number;
	};
	assert.equal(screenPayload.packagedResources, 2);
	assert.equal(screenPayload.screenSpec?.backgroundImage, "/api/infra/screen-images/bg.png");
});

test("parseScreenImportFile keeps legacy json import compatible", async () => {
	const file = asImportFile(
		"legacy.json",
		new Blob(
			[
				JSON.stringify({
					schema: "dts.screen.spec",
					resourcesInlined: true,
					screenSpec: { name: "旧包", backgroundImage: "data:image/png;base64,AAA=" },
				}),
			],
			{ type: "application/json" },
		),
	);

	const result = await parseScreenImportFile(file);

	assert.equal(result.kind, "json");
	assert.equal(result.resourcesInlined, true);
	assert.equal(result.restoredResourceCount, 0);
	assert.equal(result.source.backgroundImage, "data:image/png;base64,AAA=");
});

test("parseScreenImportFile restores zip resources by uploading images and replacing urls", async () => {
	const zip = new JSZip();
	zip.file(
		SCREEN_PACKAGE_SCREEN_FILE,
		JSON.stringify({
			schema: "dts.screen.spec",
			screenSpec: {
				name: "迁移包",
				backgroundImage: "/api/infra/screen-images/bg.png",
				components: [{ id: "img", type: "image", config: { imageUrl: "/api/infra/screen-images/logo.jpg" } }],
			},
		}),
	);
	zip.file(
		SCREEN_PACKAGE_MANIFEST_FILE,
		JSON.stringify({
			schema: "dts.screen.package",
			version: 1,
			resources: [
				{
					kind: "image",
					originalUrl: "/api/infra/screen-images/bg.png",
					path: "resources/images/bg.png",
					contentType: "image/png",
				},
				{
					kind: "image",
					originalUrl: "/api/infra/screen-images/logo.jpg",
					path: "resources/images/logo.jpg",
					contentType: "image/jpeg",
				},
			],
		}),
	);
	zip.file("resources/images/bg.png", "bg-binary");
	zip.file("resources/images/logo.jpg", "logo-binary");
	const file = asImportFile("screen.zip", await zip.generateAsync({ type: "blob", mimeType: "application/zip" }));
	const uploads: Array<{ fileName: string; contentType: string }> = [];

	const result = await parseScreenImportFile(file, {
		uploadImage: async (blob, fileName, contentType) => {
			uploads.push({ fileName, contentType });
			assert.ok(blob.size > 0);
			return `/api/infra/screen-images/restored-${fileName}`;
		},
	});

	assert.equal(result.kind, "zip");
	assert.equal(result.restoredResourceCount, 2);
	assert.deepEqual(uploads, [
		{ fileName: "bg.png", contentType: "image/png" },
		{ fileName: "logo.jpg", contentType: "image/jpeg" },
	]);
	assert.equal(result.source.backgroundImage, "/api/infra/screen-images/restored-bg.png");
	assert.equal(
		((result.source.components as Array<{ config: { imageUrl: string } }>)[0]).config.imageUrl,
		"/api/infra/screen-images/restored-logo.jpg",
	);
});
