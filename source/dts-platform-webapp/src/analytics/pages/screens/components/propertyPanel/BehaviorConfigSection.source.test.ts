import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const behaviorConfigSectionPath = new URL("./BehaviorConfigSection.tsx", import.meta.url);

test("BehaviorConfigSection uses icon-backed action affordances", async () => {
    const source = await readFile(behaviorConfigSectionPath, "utf8");

    assert.match(source, /import \{ Plus, Trash2 \} from 'lucide-react'/);
    assert.match(source, /<Plus size=\{13\} aria-hidden="true" \/>/);
    assert.match(source, /<Trash2 size=\{13\} aria-hidden="true" \/>/);
    assert.equal(source.includes("+ 添加联动规则"), false);
    assert.equal(source.includes("+ 添加变量映射"), false);
    assert.equal(source.includes("+ 添加动作入口"), false);
    assert.equal(source.includes("+ 添加下钻层级"), false);
});
