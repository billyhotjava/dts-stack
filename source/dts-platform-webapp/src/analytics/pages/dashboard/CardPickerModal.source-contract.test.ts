import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./CardPickerModal.tsx", import.meta.url), "utf8");

test("empty analysis picker links back to the governed analysis publication journey", () => {
	assert.match(SOURCE, /useNavigate/);
	assert.match(SOURCE, /暂无已发布分析/);
	assert.match(SOURCE, /先创建并发布分析/);
	assert.match(SOURCE, /navigate\("\/bi\/questions"/);
});
