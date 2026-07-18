import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const behaviorConfigSectionPath = new URL("./BehaviorConfigSection.tsx", import.meta.url);
const componentRendererPath = new URL("../ComponentRenderer.tsx", import.meta.url);

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

test("drilldown editor is data-source neutral and exposes generic mapping fields", async () => {
    const source = await readFile(behaviorConfigSectionPath, "utf8");

    assert.doesNotMatch(source, /dataSource\?\.type\s*!==\s*['"]card['"]/);
    assert.doesNotMatch(source, /cardId:\s*0,\s*paramName:\s*['"]{2}/);
    assert.match(source, /下一层数据源/);
    assert.match(source, /来源字段/);
    assert.match(source, /目标参数/);
    assert.match(source, /继承上层筛选/);
	assert.match(source, /至少添加一条字段映射/);
	assert.match(source, /目标参数不能为空/);
	assert.match(source, /目标参数不能重复/);
});

test("behavior editor exposes drill-view target, mappings, and reset-compatible action", async () => {
    const [source, rendererSource] = await Promise.all([
        readFile(behaviorConfigSectionPath, "utf8"),
        readFile(componentRendererPath, "utf8"),
    ]);

    assert.match(source, /<option value="drill-view">切换内部视图<\/option>/);
    assert.match(source, /目标视图 ID/);
    assert.match(source, /actionType === 'drill-view'/);
    assert.match(source, /pages\.map\(\(page\)/);
    assert.match(source, /-- 请选择当前大屏页面 --/);
    assert.match(rendererSource, /aria-label="重置下钻"/);
    assert.match(rendererSource, /drillState\.reset\(\)/);
});
