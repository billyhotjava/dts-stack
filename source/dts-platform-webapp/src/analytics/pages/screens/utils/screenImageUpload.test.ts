import assert from "node:assert/strict";
import test from "node:test";

import {
	getScreenImageUploadErrorMessage,
	resolveScreenImageUploadUrl,
	SCREEN_IMAGE_UPLOAD_LIMIT_BYTES,
	SCREEN_IMAGE_UPLOAD_LIMIT_LABEL,
} from "./screenImageUpload";

test("screen image uploads keep the UI limit aligned to the backend image cap", () => {
	assert.equal(SCREEN_IMAGE_UPLOAD_LIMIT_BYTES, 20 * 1024 * 1024);
	assert.equal(SCREEN_IMAGE_UPLOAD_LIMIT_LABEL, "20MB");
});

test("screen image uploads show a readable message for gateway 413 responses", () => {
	const message = getScreenImageUploadErrorMessage({
		response: { status: 413 },
		message: "Request failed with status code 413",
	});

	assert.equal(message, "图片文件超过上传限制（20MB），请压缩后重试");
	assert.doesNotMatch(message, /Request failed/);
});

test("screen image uploads preserve backend validation messages when present", () => {
	assert.equal(
		getScreenImageUploadErrorMessage({
			response: {
				status: 400,
				data: { detail: '400 BAD_REQUEST "文件大小不能超过 20MB"' },
			},
		}),
		"文件大小不能超过 20MB",
	);
});

test("screen image uploads resolve URL from supported response shapes", () => {
	const url = "/api/infra/screen-images/demo.png";

	assert.equal(resolveScreenImageUploadUrl({ url }), url);
	assert.equal(resolveScreenImageUploadUrl({ data: { url } }), url);
	assert.equal(resolveScreenImageUploadUrl({ data: { data: { url } } }), url);
	assert.equal(resolveScreenImageUploadUrl({ response: { data: { data: { url } } } }), url);
	assert.equal(resolveScreenImageUploadUrl({ data: { filename: "demo.png" } }), undefined);
});
