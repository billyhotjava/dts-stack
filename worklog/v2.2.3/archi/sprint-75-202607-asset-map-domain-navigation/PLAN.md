# 资产地图主题域导航与统计口径纠偏 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让资产地图左侧一眼看出各主题域的体量与健康度，右侧不再重复讲同一件事，界面零英文枚举，并修掉三个使数字失真的后端缺陷。

**Architecture:** 后端先把统计口径修正并补上域级 rollup（复用 `listAssets` 的可见性，不写第二套 ABAC），再通过 `domains/tree?withStats=true` 一次性下发树 + 统计；前端先建枚举字典，再用字典构建共享的 `DomainScopeNav`，最后收敛地图页信息架构与控件。

**Tech Stack:** Java 17 / Spring Boot / JPA（`dts-platform`）；React 18 / TypeScript / antd 5 / Tailwind（`dts-platform-webapp`）；测试用 JUnit 5 + Mockito、`node:test`（源码契约）、Vitest + jsdom（组件）。

## Global Constraints

- **可见性规则零改动**：`canRead`、`AccessChecker` 一行不改，统计只复用不重写（ADR-75-01）。
- **界面零英文枚举原值**：一切枚举经 `assetEnumLabels` 字典；未收录时显示 `未知（原值）`，禁止裸原值兜底（ADR-75-13）。
- **数仓分层中文主 + 代号弱化**：`来源层 SOURCE` / `贴源层 ODS` / `暂存层 STG` / `明细层 DWD` / `维度层 DIM` / `汇总层 DWS` / `应用层 ADS` / `未分层`（无代号）。
- **`Hive` / `JDBC` 保持原形**：产品名与技术标准名不翻译。
- **spec 文档不中文化**：`worklog/` 下继续用 `P0`/`DRAFT`/`READY`，与 sprint-70～74 一致。
- **不新增菜单、不新增路由**：台账保持全局菜单一级入口，不升为 tab（ADR-75-10）。
- **`MetricTile` 禁止修改**：被 4 处复用且被 `DatasetsPage.asset-map-visual.source-contract.test.ts:14` 断言为导出符号；新面板必须另建组件。
- **文件规模**：单文件 ≤800 行，新组件目标 200～400 行。
- **Optional**：Java 侧禁用 `Optional.get()`，一律 `orElseThrow()`（`modernizer-maven-plugin` 构建期强制）。
- **提交信息**：约定式提交（`feat:` / `fix:` / `refactor:` / `test:` / `docs:`），无 Co-Authored-By（全局关闭归属）。
- **Java 测试白名单（实测发现，必守）**：`source/dts-platform/pom.xml:526-600` 的 `maven-compiler-plugin` 配了显式
  `<testIncludes>` 允许清单（45 项）。**未列入的测试文件不会被编译**，`./mvnw test -Dtest=X` 会报
  `No tests matching pattern "X" were executed`。新增或启用任何 Java 测试，必须同时把该文件加进这份清单。
  已知 `CatalogAssetOverviewAggregatorTest` **不在清单内**——它当前是死测试代码，从未运行过。

## 命令速查

| 用途 | 命令（工作目录） |
|---|---|
| 源码契约测试（纯 `.ts`，无 JSX） | `node --experimental-strip-types --test <file>`（`source/dts-platform-webapp`）|
| 组件测试 / 任何触及 `.tsx` 的测试 | `npx vitest run <file>`（`source/dts-platform-webapp`）|

> **运行器归属（已实测确认，勿凭直觉选）**：`node --experimental-strip-types --test` **无法 import `.tsx`**，
> 会报 `ERR_UNKNOWN_FILE_EXTENSION`。凡是 import `.tsx`、本身是 `.tsx`、或用 `describe/expect/it` 的测试，
> 一律用 `npx vitest run`。`readFileSync` 把源码当**文本**读的契约测试不受此限，留在 node:test。
> 现存归属：`assetPortalUx.helpers.test.ts`、`*.test.tsx` → vitest；`*.source-contract.test.ts` → node:test。
| 类型检查 | `npx tsc --noEmit`（`source/dts-platform-webapp`）|
| 前端构建 | `pnpm build`（`source/dts-platform-webapp`）|
| Java 单测 | `./mvnw test -Dtest=<ClassName>`（`source/dts-platform`）|

## 文件结构

### 新建

| 文件 | 职责 |
|---|---|
| `dts-platform-webapp/src/pages/catalog/assets/assetEnumLabels.ts` | 枚举字典 + `resolveEnumLabel` 降级策略（约 150 行）|
| `dts-platform-webapp/src/pages/catalog/assets/assetEnumLabels.test.ts` | 字典单测 + 防漂移（直读 Java 枚举源文件）|
| `dts-platform-webapp/src/components/catalog/DomainScopeNav.tsx` | 分区式范围导航，纯展示（约 220 行）|
| `dts-platform-webapp/src/components/catalog/DomainScopeNav.test.tsx` | 组件行为测试 |
| `dts-platform-webapp/src/pages/catalog/assets/GovernanceGapPanel.tsx` | 治理缺口面板（约 120 行）|
| `dts-platform/src/test/java/.../CatalogAssetPortalStatsTest.java` | total / truncated / byDomain / 失效判定 |

### 修改

| 文件 | 改动 |
|---|---|
| `CatalogAssetOverviewAggregator.java` | 新增 `byDomain`；`rowStale` 改判可达枚举 |
| `CatalogAssetPortalService.java:151,307` | legacy 取数改按组合偏移量，不复用 OM 页码 |
| `CatalogAssetPortalService.java:88-96` | 扫描上限常量化、`truncated` 判定、切批量加载、新增 `domainStats` |
| `CatalogDomainResource.java:139` | `tree` 支持 `withStats` |
| `assetPageShared.tsx` | `LAYER_META` 加 `code` 并中文化 label；`buildTreeNodes` 去 fallback key |
| `assetPortalUx.helpers.ts:37` | 失效判定改可达枚举 |
| `AssetOverviewPage.tsx` | 接入导航 + 缺口面板 + 控件裁剪 + URL 绑定 |
| `DatasetsPage.tsx` | 换左侧导航 + URL 绑定 + 失效判定 + 导出中文化 |
| `AssetLedgerView.tsx:166,182` | 消除原值直显 |
| `platformApi.ts:801` | `getDomainTree` 支持 `withStats` |

## 依赖顺序

```
Task 1 (字典)  ─┬─> Task 7 (导航组件) ─> Task 8 (地图接入) ─> Task 10 (缺口面板)
Task 2 (分层)  ─┘                        └─> Task 9 (台账接入)
Task 3 (失效判定) ─> Task 4 (total) ─> Task 5 (批量+上限) ─> Task 6 (域树接口) ─> Task 7
                                                                       Task 11 (矩阵+控件)
                                                                       Task 12 (原值直显+导出)
```

---

### Task 1: 枚举字典与漏译降级

**Files:**
- Create: `source/dts-platform-webapp/src/pages/catalog/assets/assetEnumLabels.ts`
- Test: `source/dts-platform-webapp/src/pages/catalog/assets/assetEnumLabels.test.ts`

**Interfaces:**
- Consumes: 无
- Produces: `resolveEnumLabel(dict: Record<string,string>, value?: string | null, fallback?: string): string`；`GOVERNANCE_STATUS_DICT`、`LIFECYCLE_STATUS_DICT`、`CLASSIFICATION_DICT`、`MATCH_STATUS_DICT`、`ASSET_TYPE_DICT`（均为 `Record<string, string>`）

- [ ] **Step 1: 写失败测试**

```ts
// source/dts-platform-webapp/src/pages/catalog/assets/assetEnumLabels.test.ts
import assert from "node:assert/strict";
import test from "node:test";
import {
	GOVERNANCE_STATUS_DICT,
	LIFECYCLE_STATUS_DICT,
	resolveEnumLabel,
} from "./assetEnumLabels.ts";

test("resolveEnumLabel 返回字典中文", () => {
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, "PENDING_DOMAIN"), "待归域");
});

test("resolveEnumLabel 大小写与空白不敏感", () => {
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, "  pending_domain "), "待归域");
});

test("未收录枚举降级为可见的未知标记，而不是裸原值", () => {
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, "SOMETHING_NEW"), "未知（SOMETHING_NEW）");
});

test("空值返回未设定", () => {
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, ""), "未设定");
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, null), "未设定");
});

test("空值可用 fallback 覆盖", () => {
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, null, "全部"), "全部");
});

test("治理状态字典覆盖后端实有的 8 个值", () => {
	for (const key of [
		"GOVERNED",
		"PENDING_CLAIM",
		"PENDING_CLASSIFICATION",
		"PENDING_DOMAIN",
		"PENDING_GOVERNANCE",
		"PENDING_APPROVAL",
		"PENDING_LINEAGE",
		"PENDING_REVIEW",
		"DISABLED",
	]) {
		assert.ok(GOVERNANCE_STATUS_DICT[key], `治理状态字典缺少 ${key}`);
	}
});

test("生命周期字典覆盖 CatalogAssetLifecycleStatus 全部枚举", () => {
	for (const key of [
		"DISCOVERED",
		"PENDING_GOVERNANCE",
		"DRAFT_GOVERNANCE",
		"TESTING",
		"ACTIVE",
		"DEPRECATED",
		"ARCHIVED",
		"BLOCKED",
		"PENDING_REVIEW",
	]) {
		assert.ok(LIFECYCLE_STATUS_DICT[key], `生命周期字典缺少 ${key}`);
	}
});
```

- [ ] **Step 2: 运行测试确认失败**

工作目录 `source/dts-platform-webapp`：

```bash
node --experimental-strip-types --test src/pages/catalog/assets/assetEnumLabels.test.ts
```

预期：FAIL，`Cannot find module './assetEnumLabels.ts'`

- [ ] **Step 3: 实现字典模块**

```ts
// source/dts-platform-webapp/src/pages/catalog/assets/assetEnumLabels.ts

// 资产域枚举的唯一中文字典。界面不得直接显示枚举原值——
// 未收录时降级为"未知（原值）"，使漏译在界面上可见且可被测试断言。

const normalizeKey = (value?: string | null) =>
	String(value ?? "")
		.trim()
		.toUpperCase();

/**
 * 把枚举原值解析为中文标签。
 * @param dict 枚举字典
 * @param value 枚举原值，允许空
 * @param fallback 空值时的显示，默认"未设定"
 */
export function resolveEnumLabel(
	dict: Record<string, string>,
	value?: string | null,
	fallback = "未设定",
): string {
	const key = normalizeKey(value);
	if (!key) return fallback;
	return dict[key] ?? `未知（${key}）`;
}

/** 治理状态：后端散落于 service/catalog 的字符串常量，全集见 sprint-75 spec F5/2.1 */
export const GOVERNANCE_STATUS_DICT: Record<string, string> = {
	GOVERNED: "已治理",
	PENDING_CLAIM: "待认领",
	PENDING_CLASSIFICATION: "待定级",
	PENDING_DOMAIN: "待归域",
	PENDING_GOVERNANCE: "待治理",
	PENDING_APPROVAL: "待审批",
	PENDING_LINEAGE: "待补血缘",
	PENDING_REVIEW: "待复核",
	DISABLED: "已停用",
};

/** 生命周期：对应 Java 枚举 CatalogAssetLifecycleStatus */
export const LIFECYCLE_STATUS_DICT: Record<string, string> = {
	DISCOVERED: "已发现",
	PENDING_GOVERNANCE: "待治理",
	DRAFT_GOVERNANCE: "治理草稿",
	TESTING: "测试中",
	ACTIVE: "生效",
	DEPRECATED: "已弃用",
	ARCHIVED: "已归档",
	BLOCKED: "已阻断",
	PENDING_REVIEW: "待复核",
};

export const CLASSIFICATION_DICT: Record<string, string> = {
	PUBLIC: "公开",
	INTERNAL: "内部",
	SECRET: "秘密",
	CONFIDENTIAL: "机密",
};

export const MATCH_STATUS_DICT: Record<string, string> = {
	MATCHED: "已映射",
	UNMATCHED: "未匹配",
	MANUAL_REVIEW: "人工确认",
};

/** 资产类型：Hive / JDBC 是产品名与技术标准名，按项目语言规范保持原形 */
export const ASSET_TYPE_DICT: Record<string, string> = {
	HIVE: "Hive",
	JDBC: "JDBC",
	FILE: "文件",
};
```

- [ ] **Step 4: 运行测试确认通过**

```bash
node --experimental-strip-types --test src/pages/catalog/assets/assetEnumLabels.test.ts
```

预期：PASS，7 tests

- [ ] **Step 5: 提交**

```bash
git add source/dts-platform-webapp/src/pages/catalog/assets/assetEnumLabels.ts \
        source/dts-platform-webapp/src/pages/catalog/assets/assetEnumLabels.test.ts
git commit -m "feat: 建立资产枚举中文字典与漏译降级策略"
```

---

### Task 2: 分层中文化与防漂移门禁

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/catalog/assets/assetPageShared.tsx:77-85`
- Modify: `source/dts-platform-webapp/src/pages/catalog/assets/assetEnumLabels.test.ts`

**Interfaces:**
- Consumes: Task 1 的 `LIFECYCLE_STATUS_DICT`
- Produces: `LAYER_META[key]` 新增 `code?: string` 字段；`label` 改为中文

- [ ] **Step 1: 追加失败测试**

> **运行器约束**：`node --experimental-strip-types --test` 无法 import `.tsx`（`ERR_UNKNOWN_FILE_EXTENSION`）。
> `assetPageShared.tsx` 含 JSX，所以断言 `LAYER_META` 的测试必须用 **vitest**；只读 `.ts` 的防漂移测试留在 node:test 文件里。

**1a.** 新建 `source/dts-platform-webapp/src/pages/catalog/assets/assetPageShared.layers.test.ts`：

```ts
import { describe, expect, it } from "vitest";
import { LAYER_META, LAYER_ORDER } from "./assetPageShared";

describe("数仓分层标签", () => {
	it("label 为中文，code 保留英文代号", () => {
		const expected: Record<string, { label: string; code?: string }> = {
			SOURCE: { label: "来源层", code: "SOURCE" },
			ODS: { label: "贴源层", code: "ODS" },
			STG: { label: "暂存层", code: "STG" },
			DWD: { label: "明细层", code: "DWD" },
			DIM: { label: "维度层", code: "DIM" },
			DWS: { label: "汇总层", code: "DWS" },
			ADS: { label: "应用层", code: "ADS" },
			OTHER: { label: "未分层", code: undefined },
		};
		for (const key of LAYER_ORDER) {
			expect(LAYER_META[key].label, `${key} 的中文 label 不符`).toBe(expected[key].label);
			expect(LAYER_META[key].code, `${key} 的代号不符`).toBe(expected[key].code);
		}
	});
});
```

**1b.** 把下面这个防漂移测试追加到 `assetEnumLabels.test.ts` 末尾（它只读 `.ts` 与 Java 源文件，留在 node:test）：

```ts
import { readFileSync } from "node:fs";

test("生命周期字典与后端 Java 枚举逐个对齐（防漂移）", () => {
	const javaSource = readFileSync(
		new URL(
			"../../../../../dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetLifecycleStatus.java",
			import.meta.url,
		),
		"utf8",
	);
	const body = javaSource.slice(
		javaSource.indexOf("{"),
		javaSource.indexOf(";", javaSource.indexOf("{")),
	);
	const javaEnums = [...body.matchAll(/^\s{4}([A-Z][A-Z_]*)\s*,?\s*$/gm)].map((m) => m[1]);

	assert.ok(javaEnums.length >= 9, `解析到的 Java 枚举过少：${javaEnums.join(",")}`);
	for (const name of javaEnums) {
		assert.ok(
			LIFECYCLE_STATUS_DICT[name],
			`后端新增枚举 ${name} 未补中文翻译，请更新 LIFECYCLE_STATUS_DICT`,
		);
	}
});
```

- [ ] **Step 2: 运行测试确认失败**

```bash
npx vitest run src/pages/catalog/assets/assetPageShared.layers.test.ts
node --experimental-strip-types --test src/pages/catalog/assets/assetEnumLabels.test.ts
```

预期：vitest FAIL，`ODS 的中文 label 不符`（现值为 `"ODS"`，期望 `"贴源层"`）

- [ ] **Step 3: 改 LAYER_META**

把 `assetPageShared.tsx:77-85` 整体替换为：

```tsx
export const LAYER_META: Record<string, { label: string; code?: string; color: string; tone: string }> = {
	SOURCE: { label: "来源层", code: "SOURCE", color: "magenta", tone: "border-pink-200 bg-pink-50/60" },
	ODS: { label: "贴源层", code: "ODS", color: "default", tone: "border-slate-200 bg-slate-50/70" },
	STG: { label: "暂存层", code: "STG", color: "geekblue", tone: "border-indigo-200 bg-indigo-50/60" },
	DWD: { label: "明细层", code: "DWD", color: "blue", tone: "border-blue-200 bg-blue-50/60" },
	DIM: { label: "维度层", code: "DIM", color: "purple", tone: "border-purple-200 bg-purple-50/60" },
	DWS: { label: "汇总层", code: "DWS", color: "cyan", tone: "border-cyan-200 bg-cyan-50/60" },
	ADS: { label: "应用层", code: "ADS", color: "green", tone: "border-green-200 bg-green-50/60" },
	OTHER: { label: "未分层", color: "default", tone: "border-slate-200 bg-white" },
};
```

- [ ] **Step 4: 运行测试确认通过**

```bash
npx vitest run src/pages/catalog/assets/assetPageShared.layers.test.ts
node --experimental-strip-types --test src/pages/catalog/assets/assetEnumLabels.test.ts
npx tsc --noEmit
```

预期：vitest 1 passed；node:test 8 passed；tsc 无错误

- [ ] **Step 5: 提交**

```bash
git add source/dts-platform-webapp/src/pages/catalog/assets/
git commit -m "feat: 数仓分层改中文主+代号弱化并加枚举防漂移门禁"
```

---

### Task 3: 修正失效判定的不可达枚举

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetOverviewAggregator.java:51`
- Modify: `source/dts-platform-webapp/src/pages/catalog/assetPortalUx.helpers.ts:37`
- Modify: `source/dts-platform-webapp/src/pages/catalog/DatasetsPage.tsx:341`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetOverviewAggregatorTest.java`
- Test: `source/dts-platform-webapp/src/pages/catalog/assetPortalUx.helpers.test.ts`

**Interfaces:**
- Consumes: 无
- Produces: `CatalogAssetOverviewAggregator.STALE_LIFECYCLE_STATUSES`（`Set<String>`，值为 `DEPRECATED`/`ARCHIVED`/`BLOCKED`）

> **背景**：`STALE` 与 `DISABLED` 都不在 `CatalogAssetLifecycleStatus`（9 值）中，全仓检索确认从未被写入，导致"失效资产"指标结构性恒为 0。修复后 `attention` 数值会上升，属修正而非回归。

- [ ] **Step 1: 写失败测试（Java）**

追加到 `CatalogAssetOverviewAggregatorTest.java`。该文件**已有**构造辅助（`:13`），直接复用，不要另造：

```java
private AssetSummary summary(String layer, UUID domainId, String classification, String lifecycle, String governanceStatus)
```

下面测试里的 `summaryWithLifecycle(x)` 即 `summary("ODS", UUID.randomUUID(), "INTERNAL", x, "GOVERNED")`。

```java
@Test
void staleCountsUseReachableLifecycleStatuses() {
    var rows = List.of(
        summaryWithLifecycle("DEPRECATED"),
        summaryWithLifecycle("ARCHIVED"),
        summaryWithLifecycle("BLOCKED"),
        summaryWithLifecycle("ACTIVE")
    );

    var overview = CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), false);

    assertThat(overview.stale()).isEqualTo(3);
}

@Test
void unreachableStaleTokenIsNoLongerCounted() {
    var rows = List.of(summaryWithLifecycle("STALE"));

    var overview = CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), false);

    assertThat(overview.stale()).isZero();
}
```

- [ ] **Step 1b: 把该测试加入 pom 白名单（否则它根本不会被编译）**

`source/dts-platform/pom.xml` 的 `<testIncludes>` 块（约 :560，紧邻其他 `service/catalog` 条目）加入：

```xml
                        <testInclude>**/service/catalog/CatalogAssetOverviewAggregatorTest.java</testInclude>
```

> 该文件此前不在白名单，从未被编译或运行。不加这一行，下一步会报
> `No tests matching pattern "CatalogAssetOverviewAggregatorTest" were executed`，而不是你期望的断言失败。

- [ ] **Step 2: 运行测试确认失败**

工作目录 `source/dts-platform`：

```bash
./mvnw test -Dtest=CatalogAssetOverviewAggregatorTest
```

预期：FAIL，`staleCountsUseReachableLifecycleStatuses` 得到 0，期望 3。
若报 "No tests matching pattern"，说明 Step 1b 的白名单没加对，先修白名单再继续。

- [ ] **Step 3: 改 Java 判定**

在 `CatalogAssetOverviewAggregator.java` 的 `KNOWN_LAYERS` 常量下方新增：

```java
    /**
     * 失效资产的生命周期取值。历史代码比较的 "STALE" 不在
     * CatalogAssetLifecycleStatus 中且从未被写入，导致该指标恒为 0。
     */
    private static final Set<String> STALE_LIFECYCLE_STATUSES = Set.of("DEPRECATED", "ARCHIVED", "BLOCKED");
```

把 `:51` 的 `rowStale` 一行替换为：

```java
            boolean rowStale = STALE_LIFECYCLE_STATUSES.contains(normalizeUpper(row.lifecycleStatus()));
```

同时把类顶部注释里的 `生命周期 STALE` 改为 `生命周期已弃用/已归档/已阻断`。

- [ ] **Step 4: 运行 Java 测试确认通过**

```bash
./mvnw test -Dtest=CatalogAssetOverviewAggregatorTest
```

预期：PASS

- [ ] **Step 5: 写失败测试（前端）**

追加到 `source/dts-platform-webapp/src/pages/catalog/assetPortalUx.helpers.test.ts`。
**该文件是 vitest 测试**（顶部 `import { describe, expect, it } from "vitest"`），必须沿用 vitest 风格，不要引入 `node:test`：

```ts
describe("失效生命周期判定", () => {
	it.each(["DEPRECATED", "ARCHIVED", "BLOCKED"])("%s 判为阻断态", (status) => {
		const readiness = resolveAssetReadiness({
			classification: "INTERNAL",
			domainId: "d1",
			ownerDept: "dept",
			lifecycleStatus: status,
			governanceStatus: "GOVERNED",
			matchStatus: "MATCHED",
		});
		expect(readiness.state).toBe("BLOCKED");
	});

	it("不再比较不可达的 DISABLED 生命周期值", () => {
		const readiness = resolveAssetReadiness({
			classification: "INTERNAL",
			domainId: "d1",
			ownerDept: "dept",
			lifecycleStatus: "DISABLED",
			governanceStatus: "GOVERNED",
			matchStatus: "MATCHED",
		});
		expect(readiness.state).toBe("READY");
	});
});
```

- [ ] **Step 6: 运行前端测试确认失败**

工作目录 `source/dts-platform-webapp`：

```bash
npx vitest run src/pages/catalog/assetPortalUx.helpers.test.ts
```

预期：FAIL，`DEPRECATED 判为阻断态` 得到 `"READY"`

- [ ] **Step 7: 改前端判定**

`assetPortalUx.helpers.ts`，在 `normalize` 定义下方新增常量：

```ts
// 失效生命周期取值。历史代码比较的 "DISABLED" 属治理状态而非生命周期，
// 且 "STALE" 不在 CatalogAssetLifecycleStatus 中，两者均不可达。
const STALE_LIFECYCLE_STATUSES = new Set(["DEPRECATED", "ARCHIVED", "BLOCKED"]);
```

把 `:37` 起的 `if (lifecycleStatus === "DISABLED") {` 块替换为：

```ts
	if (STALE_LIFECYCLE_STATUSES.has(lifecycleStatus)) {
		return {
			state: "BLOCKED",
			label: "已失效",
			color: "red",
			reasons: ["资产生命周期已失效", ...reasons],
		};
	}
```

`DatasetsPage.tsx:341` 的失效行判定替换为：

```tsx
		(row) => ["DEPRECATED", "ARCHIVED", "BLOCKED"].includes(String(row.lifecycleStatus || "").toUpperCase()),
```

（同时删掉原来的 `|| row.status === "停用"` 分支——`status` 字段与生命周期无关，该条件是历史误写。）

- [ ] **Step 8: 运行测试确认通过**

```bash
npx vitest run src/pages/catalog/assetPortalUx.helpers.test.ts
npx tsc --noEmit
```

预期：全部 PASS（原有 3 个 + 新增 4 个）

- [ ] **Step 9: 提交**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetOverviewAggregator.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetOverviewAggregatorTest.java \
        source/dts-platform-webapp/src/pages/catalog/assetPortalUx.helpers.ts \
        source/dts-platform-webapp/src/pages/catalog/assetPortalUx.helpers.test.ts \
        source/dts-platform-webapp/src/pages/catalog/DatasetsPage.tsx
git commit -m "fix: 失效资产判定改用可达的生命周期枚举

STALE 与 DISABLED 均不在 CatalogAssetLifecycleStatus 中且从未被写入，
导致失效资产指标结构性恒为 0。改判 DEPRECATED/ARCHIVED/BLOCKED。
attention 数值将因此上升，属修正而非回归。"
```

---

### Task 4: 修复 legacy 资产的分页不可达

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetPortalService.java:150-156,307-334`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetPortalStatsTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `listLegacyAssets(AssetQuery, String, int offset, int limit, List<UUID>)` —— 第 3/4 个参数由「页码 + 页大小」改为「偏移量 + 取数上限」

> **缺陷本质**：`listLegacyAssets` 与 OpenMetadata 侧共用同一 `page` 索引和 `size`，而 `remainingSlots = size - items.size()`。当 OM 填满首页时 `remainingSlots = 0`，legacy 行被 `.limit(0)` 全部丢弃；翻到下一页时 legacy 却按 `offset = page × size` 查询而落空。结果 **legacy 资产在 OM 有满页时完全不可见**，且 `rows` 永远凑不齐 `total`，使 `truncated` 恒真。
>
> `total = OM 总数 + legacy 总数` 的算术**本身正确，不要改它**。要改的是 legacy 内容的取数位置。
>
> **影响面**（已跑 `gitnexus_impact`，风险 LOW，7 个受影响符号）：`listAssets` → `overview` / `governanceGaps` / `CatalogAssetPortalResource.listAssets`；`governanceGaps` 把 `page.total()` 透传给 `GovernanceGapReport.total`，`lineageFailures` 再经其派生。台账列表、治理缺口报告、血缘失败报告都会因此一并恢复 legacy 资产的可见性。

- [ ] **Step 1: 写失败测试**

```java
// source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetPortalStatsTest.java
package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 统计口径回归测试。构造方式参照同目录 CatalogAssetPortalServicePermissionParityTest：
 * @ExtendWith(MockitoExtension.class) + 全 repository mock + AccessChecker mock。
 * 实现本类时把该文件的 @Mock 字段与 setUp 整体复用过来。
 */
class CatalogAssetPortalStatsTest {

    @Test
    void legacyAssetsAreReachableWhenOpenMetadataFillsFirstPage() {
        // 场景：OM 侧 4 条、legacy 侧 2 条，size=2
        // 组合序列应为 [OM0,OM1] [OM2,OM3] [LG0,LG1]
        givenOpenMetadataAssets(4);
        givenLegacyAssets(2);

        var third = service.listAssets(queryOf(2, 2), ACTIVE_DEPT);

        assertThat(third.content()).hasSize(2);
        assertThat(third.content()).allSatisfy(row -> assertThat(row.metadataSource()).isEqualTo("dts-catalog"));
    }

    @Test
    void pagingCoversEveryRowExactlyOnce() {
        givenOpenMetadataAssets(4);
        givenLegacyAssets(2);

        var ids = new ArrayList<String>();
        for (int page = 0; page < 3; page++) {
            service.listAssets(queryOf(page, 2), ACTIVE_DEPT).content().forEach(row -> ids.add(row.assetKey()));
        }

        assertThat(ids).hasSize(6).doesNotHaveDuplicates();
    }

    @Test
    void totalStaysStableAcrossPages() {
        givenOpenMetadataAssets(4);
        givenLegacyAssets(2);

        var first = service.listAssets(queryOf(0, 2), ACTIVE_DEPT);
        var second = service.listAssets(queryOf(1, 2), ACTIVE_DEPT);
        var third = service.listAssets(queryOf(2, 2), ACTIVE_DEPT);

        assertThat(first.total()).isEqualTo(6);
        assertThat(second.total()).isEqualTo(6);
        assertThat(third.total()).isEqualTo(6);
    }

    @Test
    void totalExcludesRowsHiddenByVisibility() {
        // OM 侧 3 条中 1 条 canRead=false，legacy 侧 2 条全部可见
        givenOpenMetadataAssets(3);
        givenLegacyAssets(2);
        givenFirstOpenMetadataAssetUnreadable();

        var page = service.listAssets(queryOf(0, 10), ACTIVE_DEPT);

        assertThat(page.total()).isEqualTo(4);
        assertThat(page.content()).hasSize(4);
    }
}
```

实现时补齐 `service`、`queryOf`、`ACTIVE_DEPT` 与 `givenXxx` 辅助方法——照抄 `CatalogAssetPortalServicePermissionParityTest` 的 `@Mock` 字段清单与 `@BeforeEach` 构造，`givenXxx` 用 `when(...).thenReturn(new PageImpl<>(...))` 装配。

- [ ] **Step 1b: 把新测试文件加入 pom 白名单**

`source/dts-platform/pom.xml` 的 `<testIncludes>` 块加入：

```xml
                        <testInclude>**/service/catalog/CatalogAssetPortalStatsTest.java</testInclude>
```

> 不加这一行，新建的测试文件不会被编译，`-Dtest=` 会报 "No tests matching pattern"。

- [ ] **Step 2: 运行测试确认失败**

```bash
./mvnw test -Dtest=CatalogAssetPortalStatsTest
```

预期：FAIL，`legacyAssetsAreReachableWhenOpenMetadataFillsFirstPage` 得到空内容——legacy 行在 page=2 落空。
若报 "No tests matching pattern"，说明 Step 1b 的白名单没加对。

- [ ] **Step 3: 把 legacy 取数从页码改为偏移量**

`listLegacyAssets` 的签名与分页计算改为按偏移量取数：

```java
    private AssetPage listLegacyAssets(
        AssetQuery query,
        String activeDept,
        int offset,
        int limit,
        List<UUID> excludedIds
    ) {
        Sort sort = Sort.by(Sort.Direction.DESC, "lastModifiedDate").and(Sort.by(Sort.Direction.DESC, "createdDate"));
        // legacy 与 OpenMetadata 是两个独立数据源，拼成一个逻辑列表时必须按
        // 组合偏移量取数；沿用 OM 的页码会使 legacy 行在 OM 满页时永远取不到。
        int fetchSize = Math.max(1, offset + Math.max(limit, 1));
        Page<CatalogDataset> legacyPage = datasetRepository.findAll(buildLegacySpec(query), PageRequest.of(0, fetchSize, sort));
        List<CatalogDataset> visible = legacyPage
            .getContent()
            .stream()
            .filter(dataset -> dataset.getId() == null || excludedIds == null || !excludedIds.contains(dataset.getId()))
            .filter(dataset -> canRead(null, dataset, activeDept))
            .toList();
        List<AssetSummary> items = visible
            .stream()
            .skip(offset)
            .limit(Math.max(0, limit))
            .map(this::toLegacySummary)
            .toList();
        long hidden = Math.max(0, legacyPage.getNumberOfElements() - visible.size());
        long total = Math.max(0, legacyPage.getTotalElements() - hidden);
        return new AssetPage(items, total, 0, fetchSize, items.size(), "dts-catalog");
    }
```

调用点 `listAssetsWithoutTagFilter` 相应改为传偏移量：

```java
        long openMetadataTotal = Math.max(0, pageData.getTotalElements() - hiddenOnCurrentPage);
        // legacy 在组合列表中排在 OM 之后，其偏移量 = 本页起始位置减去 OM 的总量
        int legacyOffset = (int) Math.max(0, (long) page * size - openMetadataTotal);
        int legacyLimit = Math.max(0, size - items.size());
        AssetPage legacyPage = listLegacyAssets(query, activeDept, legacyOffset, legacyLimit, visibleLegacyIds);
```

注意 `legacyPage` 的赋值必须移到 `openMetadataTotal` 计算之后，`total` 的算术保持 `openMetadataTotal + legacyPage.total()` 不变。

- [ ] **Step 4: 运行测试确认通过**

```bash
./mvnw test -Dtest=CatalogAssetPortalStatsTest
./mvnw test -Dtest=CatalogAssetPortalServicePermissionParityTest
./mvnw test -Dtest=CatalogAssetPortalTagFilterTest
```

预期：三个测试类全部 PASS（后两个用于确认没有回归）

- [ ] **Step 5: 提交**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetPortalService.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetPortalStatsTest.java
git commit -m "fix: legacy 资产改按组合偏移量取数，修复其在 OM 满页时不可见

listLegacyAssets 此前复用 OM 的页码，导致 legacy 行永远取不到，
rows 凑不齐 total 又使 truncated 恒真。台账列表、治理缺口报告与
血缘失败报告一并恢复 legacy 资产可见性。"
```

---

### Task 5: 批量加载、扫描上限与 truncated 判定

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetPortalService.java:86-98`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetPortalStatsTest.java`

**Interfaces:**
- Consumes: Task 4 修正后的 `AssetPage.total()`
- Produces: 常量 `ASSET_STATS_SCAN_CAP = 5000`

- [ ] **Step 1: 追加失败测试**

```java
    @Test
    void truncatedIsFalseWhenAllRowsFitWithinScanCap() {
        var overview = service.overview(queryOf(0, 200), ACTIVE_DEPT);

        assertThat(overview.truncated()).isFalse();
        assertThat(overview.scanned()).isEqualTo(360);
    }

    @Test
    void truncatedBecomesTrueOnlyAtScanCap() {
        givenVisibleAssets(CatalogAssetPortalService.ASSET_STATS_SCAN_CAP);

        var overview = service.overview(queryOf(0, 200), ACTIVE_DEPT);

        assertThat(overview.truncated()).isTrue();
    }
```

`givenVisibleAssets(int)` 为本测试类的辅助方法，按数量装配 mock 返回。

- [ ] **Step 2: 运行测试确认失败**

```bash
./mvnw test -Dtest=CatalogAssetPortalStatsTest
```

预期：FAIL，`truncatedIsFalseWhenAllRowsFitWithinScanCap` 得到 `true`（现状恒真）

- [ ] **Step 3: 改扫描循环**

把 `overview` 方法体开头的：

```java
        final int scanPageSize = 200;
        final int scanMaxPages = 10; // 首版内存聚合上限 2000 条，超限置 truncated
```

替换为：

```java
        final int scanPageSize = 200;
        final int scanMaxPages = ASSET_STATS_SCAN_CAP / scanPageSize;
```

并在类的常量区（`MAX_TAG_FILTER_CANDIDATES` 旁）新增：

```java
    /** 内存聚合的扫描上限。批量元数据加载消除 N+1 后，与标签筛选候选上限同档。 */
    static final int ASSET_STATS_SCAN_CAP = 5_000;
```

把方法末尾的：

```java
        boolean truncated = rows.size() < total;
        return CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), truncated);
```

替换为：

```java
        // 截断只由扫描上限决定。此前用 rows.size() < total 判定，
        // 因 legacy 行不可达使 rows 永远凑不齐 total 而恒真，导致警告长期误报。
        boolean truncated = rows.size() >= ASSET_STATS_SCAN_CAP;
        return CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), truncated);
```

- [ ] **Step 4: 切换到批量元数据加载**

`listAssetsWithoutTagFilter` 中每行调用 `extensionRepository.findFirstByOmAsset(asset)` 与 `mappingRepository.findFirstByFqnIgnoreCase(...)` 构成 N+1。改为在循环前批量载入：

```java
        CandidateMetadata candidateMetadata = loadCandidateMetadata(pageData.getContent());
        List<AssetSummary> items = new ArrayList<>();
        List<UUID> visibleLegacyIds = new ArrayList<>();
        for (OpenMetadataAssetCache asset : pageData.getContent()) {
            CatalogAssetExtension extension = candidateMetadata.extensionsByAssetId().get(asset.getId());
            CatalogAssetMapping mapping = candidateMetadata.mappingsByFqn().get(normalizedFqn(asset.getFqn()));
            CatalogDataset legacy = candidateMetadata.legacyById().get(resolveLegacyDatasetId(extension, mapping));
            if (!canRead(extension, legacy, activeDept)) {
                continue;
            }
            items.add(toSummary(asset, extension, mapping, legacy));
            if (legacy != null && legacy.getId() != null) {
                visibleLegacyIds.add(legacy.getId());
            }
        }
```

这与 `listAssetsByTags:187-198` 的既有写法一致，`canRead` 的入参与语义不变。

- [ ] **Step 5: 运行测试确认通过**

```bash
./mvnw test -Dtest=CatalogAssetPortalStatsTest
./mvnw test -Dtest=CatalogAssetPortalServicePermissionParityTest
./mvnw test -Dtest=CatalogAssetPortalTagFilterTest
```

预期：全部 PASS

- [ ] **Step 6: 提交**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetPortalService.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetPortalStatsTest.java
git commit -m "fix: 消除概览聚合的 N+1 并修正 truncated 误报

扫描上限常量化为 5000，与标签筛选候选上限同档。"
```

---

### Task 6: 域级 rollup 与带统计的域树接口

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetOverviewAggregator.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetPortalService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDomainResource.java:139`
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts:801`
- Test: `CatalogAssetOverviewAggregatorTest.java`、`CatalogAssetPortalStatsTest.java`

**Interfaces:**
- Consumes: Task 5 的扫描路径
- Produces:
  - `CatalogAssetOverviewAggregator.DomainStats(long total, long attention)`
  - `AssetOverview.byDomain()` → `Map<String, DomainStats>`（key 为域 UUID 字符串；`domainId == null` 的行不进此表）
  - `CatalogAssetPortalService.domainStats(String activeDept)` → `AssetOverview`
  - 前端 `getDomainTree(options?: { withStats?: boolean })`

- [ ] **Step 1: 写失败测试（聚合器）**

复用同一个既有辅助（`CatalogAssetOverviewAggregatorTest:13`）：
`summaryWithDomain(d, c)` 即 `summary("ODS", d, c, "ACTIVE", "GOVERNED")`。

```java
@Test
void byDomainRollsUpTotalsAndAttentionPerDomain() {
    var domainA = UUID.randomUUID();
    var rows = List.of(
        summaryWithDomain(domainA, "INTERNAL"),      // 已定密 → 非 attention
        summaryWithDomain(domainA, null),            // 未定密 → attention
        summaryWithDomain(null, "INTERNAL")          // 未归域 → attention
    );

    var overview = CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), false);

    assertThat(overview.byDomain()).containsOnlyKeys(domainA.toString());
    assertThat(overview.byDomain().get(domainA.toString()).total()).isEqualTo(2);
    assertThat(overview.byDomain().get(domainA.toString()).attention()).isEqualTo(1);
}

@Test
void byDomainExcludesUnassignedRows() {
    var rows = List.of(summaryWithDomain(null, "INTERNAL"));

    var overview = CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), false);

    assertThat(overview.byDomain()).isEmpty();
    assertThat(overview.missingDomain()).isEqualTo(1);
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
./mvnw test -Dtest=CatalogAssetOverviewAggregatorTest
```

预期：FAIL，`byDomain()` 方法不存在（编译失败）

- [ ] **Step 3: 实现 byDomain**

`CatalogAssetOverviewAggregator.java` 新增 record 并扩展 `AssetOverview`：

```java
    public record DomainStats(long total, long attention) {}
```

`AssetOverview` 的参数列表末尾追加 `Map<String, DomainStats> byDomain`。

`aggregate` 方法内，在返回前由既有 `matrixCells` 归并（不新增统计口径）：

```java
        Map<String, DomainStats> byDomain = new LinkedHashMap<>();
        for (MatrixCell cell : matrix) {
            if (cell.domainId() == null) {
                continue;
            }
            DomainStats current = byDomain.getOrDefault(cell.domainId(), new DomainStats(0, 0));
            byDomain.put(
                cell.domainId(),
                new DomainStats(current.total() + cell.total(), current.attention() + cell.attention())
            );
        }
```

并把 `byDomain` 传入 `new AssetOverview(...)`。

- [ ] **Step 4: 运行测试确认通过**

```bash
./mvnw test -Dtest=CatalogAssetOverviewAggregatorTest
```

预期：PASS

- [ ] **Step 5: 暴露 domainStats 并扩展端点**

`CatalogAssetPortalService` 新增：

```java
    /** 主题域导航用的域级统计。可见性与 listAssets 同源，禁止另写 SQL 聚合。 */
    public CatalogAssetOverviewAggregator.AssetOverview domainStats(String activeDept) {
        return overview(AssetQuery.unscoped(), activeDept);
    }
```

若 `AssetQuery` 无 `unscoped()` 工厂，则内联构造一个全 `null`、`page=0`、`size=200` 的查询，并把该工厂补到 `AssetQuery` 上以免多处重复。

`CatalogDomainResource.java:139` 的 `tree` 方法签名加上：

```java
    @GetMapping("/domains/tree")
    public ApiResponse<Object> tree(@RequestParam(name = "withStats", defaultValue = "false") boolean withStats) {
```

`withStats=false` 时**原样返回**既有结构（向后兼容，`SemanticModelingService` 等既有调用方零影响）；`withStats=true` 时返回：

```java
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tree", treeNodes);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("all", Map.of("total", overview.total(), "attention", overview.attention()));
        stats.put("unassigned", Map.of("total", overview.missingDomain(), "attention", overview.missingDomain()));
        stats.put("byDomain", overview.byDomain());
        stats.put("scanned", overview.scanned());
        stats.put("truncated", overview.truncated());
        payload.put("stats", stats);
```

- [ ] **Step 6: 前端 API 层接入**

`platformApi.ts:801` 替换为：

```ts
export const getDomainTree = (options?: { withStats?: boolean }) =>
	api.get({ url: `/catalog/domains/tree${options?.withStats ? "?withStats=true" : ""}` });
```

无参调用保持原行为，既有调用点无需改动。

- [ ] **Step 7: 追加端点契约测试并运行**

在 `CatalogAssetPortalStatsTest` 追加：

```java
    @Test
    void treeWithoutStatsKeepsLegacyShape() {
        var response = resource.tree(false);

        assertThat(response.getData()).isInstanceOf(List.class);
    }

    @Test
    void treeWithStatsCarriesDomainRollup() {
        var response = resource.tree(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.getData();
        assertThat(payload).containsKeys("tree", "stats");
    }
```

```bash
./mvnw test -Dtest=CatalogAssetPortalStatsTest
cd ../dts-platform-webapp && npx tsc --noEmit
```

预期：全部 PASS

- [ ] **Step 8: 提交**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/ \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDomainResource.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/catalog/ \
        source/dts-platform-webapp/src/api/platformApi.ts
git commit -m "feat: 域树接口支持带统计返回，聚合器新增域级 rollup"
```

---

### Task 7: DomainScopeNav 组件

**Files:**
- Create: `source/dts-platform-webapp/src/components/catalog/DomainScopeNav.tsx`
- Create: `source/dts-platform-webapp/src/components/catalog/DomainScopeNav.test.tsx`
- Modify: `source/dts-platform-webapp/src/pages/catalog/assets/assetPageShared.tsx`（`buildTreeNodes`）

**Interfaces:**
- Consumes: Task 6 的 `stats.byDomain` 形状；Task 1 的字典（用于 tooltip 文案）
- Produces:
  - `DomainScopeStats = { total: number; attention: number }`
  - `DomainScopeNode = { id: string | null; name: string; code?: string; stats?: DomainScopeStats; children?: DomainScopeNode[] }`
  - `DomainScopeNavProps = { nodes; allStats?; unassignedStats?; value?: string; onChange: (next: string | undefined) => void; loading?: boolean; truncated?: boolean }`
  - `buildDomainScopeNodes(nodes: DomainNode[], stats?: Record<string, DomainScopeStats>): DomainScopeNode[]`

- [ ] **Step 1: 写失败测试**

```tsx
// source/dts-platform-webapp/src/components/catalog/DomainScopeNav.test.tsx
// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DomainScopeNav } from "./DomainScopeNav";

let container: HTMLDivElement;
let root: Root;

const render = (element: ReactElement) => {
	act(() => {
		root.render(element);
	});
};

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

const NODES = [
	{ id: "d1", name: "地铁域", code: "DTMS", stats: { total: 0, attention: 0 } },
	{ id: "d2", name: "财务域", code: "FIN", stats: { total: 12, attention: 3 } },
];

describe("DomainScopeNav", () => {
	it("空域整行弱化，非空域不弱化", () => {
		render(<DomainScopeNav nodes={NODES} onChange={vi.fn()} />);

		const empty = container.querySelector('[data-testid="domain-scope-row-d1"]');
		const filled = container.querySelector('[data-testid="domain-scope-row-d2"]');
		expect(empty?.getAttribute("data-empty")).toBe("true");
		expect(filled?.getAttribute("data-empty")).toBe("false");
	});

	it("待处置数仅在大于 0 时显示", () => {
		render(<DomainScopeNav nodes={NODES} onChange={vi.fn()} />);

		expect(container.querySelector('[data-testid="domain-scope-attention-d1"]')).toBeNull();
		expect(container.querySelector('[data-testid="domain-scope-attention-d2"]')?.textContent).toContain("3");
	});

	it("缺少标识的域渲染为禁用且不触发 onChange", () => {
		const onChange = vi.fn();
		render(<DomainScopeNav nodes={[{ id: null, name: "无标识域" }]} onChange={onChange} />);

		const row = container.querySelector('[data-testid="domain-scope-row-unidentified-0"]') as HTMLButtonElement;
		expect(row.disabled).toBe(true);
		act(() => row.click());
		expect(onChange).not.toHaveBeenCalled();
	});

	it("点击业务域回传域 id，点击全部资产回传 undefined", () => {
		const onChange = vi.fn();
		render(<DomainScopeNav nodes={NODES} onChange={onChange} />);

		act(() => (container.querySelector('[data-testid="domain-scope-row-d2"]') as HTMLElement).click());
		expect(onChange).toHaveBeenCalledWith("d2");

		act(() => (container.querySelector('[data-testid="domain-scope-row-all"]') as HTMLElement).click());
		expect(onChange).toHaveBeenCalledWith(undefined);
	});

	it("未归域独立分区且回传 __UNASSIGNED__", () => {
		const onChange = vi.fn();
		render(
			<DomainScopeNav nodes={NODES} unassignedStats={{ total: 360, attention: 360 }} onChange={onChange} />,
		);

		act(() => (container.querySelector('[data-testid="domain-scope-row-unassigned"]') as HTMLElement).click());
		expect(onChange).toHaveBeenCalledWith("__UNASSIGNED__");
	});

	it("域数不超过 8 时不渲染搜索框", () => {
		render(<DomainScopeNav nodes={NODES} onChange={vi.fn()} />);
		expect(container.querySelector('[data-testid="domain-scope-search"]')).toBeNull();
	});

	it("域数超过 8 时渲染搜索框", () => {
		const many = Array.from({ length: 9 }, (_, i) => ({ id: `d${i}`, name: `域${i}` }));
		render(<DomainScopeNav nodes={many} onChange={vi.fn()} />);
		expect(container.querySelector('[data-testid="domain-scope-search"]')).not.toBeNull();
	});

	it("统计被截断时数字加 ≥ 前缀", () => {
		render(<DomainScopeNav nodes={NODES} allStats={{ total: 5000, attention: 10 }} truncated onChange={vi.fn()} />);

		expect(container.querySelector('[data-testid="domain-scope-total-all"]')?.textContent).toContain("≥");
	});

	it("无主题域时给出创建引导", () => {
		render(<DomainScopeNav nodes={[]} onChange={vi.fn()} />);
		expect(container.textContent).toContain("尚未创建主题域");
	});
});
```

- [ ] **Step 2: 运行测试确认失败**

工作目录 `source/dts-platform-webapp`：

```bash
npx vitest run src/components/catalog/DomainScopeNav.test.tsx
```

预期：FAIL，无法解析 `./DomainScopeNav`

- [ ] **Step 3: 实现组件**

创建 `source/dts-platform-webapp/src/components/catalog/DomainScopeNav.tsx`。要点：

- 顶部概览行 `data-testid="domain-scope-row-all"`，非树节点；
- 分区标题「业务主题域」「待治理」为纯文本分隔，不是可选节点；
- 每行 `data-testid="domain-scope-row-<id>"`、`data-empty="true|false"`；待处置徽标 `data-testid="domain-scope-attention-<id>"` 仅在 `attention > 0` 时渲染；
- 缺标识行 `data-testid="domain-scope-row-unidentified-<index>"`，`disabled` + `title="该主题域缺少标识，无法作为筛选条件"`；
- 未归域行 `data-testid="domain-scope-row-unassigned"`，回传常量 `"__UNASSIGNED__"`（从 `@/pages/catalog/assets/assetPageShared` 导入 `UNASSIGNED_DOMAIN_KEY`，不要硬编码字面量）；
- 总数元素 `data-testid="domain-scope-total-<id>"`，`truncated` 时文本前缀 `≥`；
- 搜索框 `data-testid="domain-scope-search"`，仅当扁平化后的域数 `> 8` 时渲染，按 `name` + `code` 过滤；
- 选中行加 `aria-current="true"`，样式为 3px 左侧主色条 + 加深底 + 字重加粗，**不使用** antd `Tree`；
- 层级用 `padding-left` 递进表达，无 `showLine` 虚线；子域仅在存在时渲染展开箭头；
- `loading` 时渲染保留分区骨架的 skeleton；`nodes` 为空时渲染「尚未创建主题域」+ 指向 `/governance/subjects` 的行内链接；
- 所有行是 `<button type="button">`，无 hover 浮动按钮、无右键菜单。

同时在 `assetPageShared.tsx` 中，把 `buildTreeNodes` 替换为：

```tsx
export const buildDomainScopeNodes = (
	nodes: DomainNode[],
	stats?: Record<string, { total: number; attention: number }>,
): DomainScopeNode[] =>
	nodes.map((node) => {
		const id = node.id ? String(node.id) : null;
		return {
			id,
			name: node.name ?? node.code ?? "未命名",
			code: node.code,
			stats: id ? stats?.[id] : undefined,
			children: node.children?.length ? buildDomainScopeNodes(node.children, stats) : undefined,
		};
	});
```

删除原 `buildTreeNodes`（含 `fallback-` key 生成）。`DomainScopeNode` 类型从 `DomainScopeNav.tsx` 导入或就近定义并被其复用，二者只能有一处定义。

- [ ] **Step 4: 运行测试确认通过**

```bash
npx vitest run src/components/catalog/DomainScopeNav.test.tsx
```

预期：PASS，9 tests

- [ ] **Step 5: 提交**

```bash
git add source/dts-platform-webapp/src/components/catalog/DomainScopeNav.tsx \
        source/dts-platform-webapp/src/components/catalog/DomainScopeNav.test.tsx \
        source/dts-platform-webapp/src/pages/catalog/assets/assetPageShared.tsx
git commit -m "feat: 新增分区式主题域范围导航组件

替代两处复制的 antd Tree：带体量与健康度、空域弱化、
缺标识域禁用而非静默降级、域多时才出搜索。"
```

---

### Task 8: 地图页接入导航与 URL 范围

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/catalog/AssetOverviewPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/catalog/AssetOverviewPage.source-contract.test.ts`

**Interfaces:**
- Consumes: Task 7 的 `DomainScopeNav`、`buildDomainScopeNodes`；Task 6 的 `getDomainTree({ withStats: true })`
- Produces: 地图页 `?domain=` 深链契约

- [ ] **Step 1: 追加失败测试**

```ts
// 追加到 AssetOverviewPage.source-contract.test.ts
test("范围选择进入 URL 而非组件内部 state", () => {
	assert.match(SOURCE, /searchParams\.get\("domain"\)/);
	assert.doesNotMatch(SOURCE, /useState<string \| undefined>\(\)/);
});

test("使用共享导航组件，不再自建 antd Tree", () => {
	assert.match(SOURCE, /DomainScopeNav/);
	assert.doesNotMatch(SOURCE, /<Tree\b/);
});

test("域树请求带上统计参数", () => {
	assert.match(SOURCE, /getDomainTree\(\{\s*withStats:\s*true\s*\}\)/);
});

test("不再存在 fallback- 静默降级", () => {
	assert.doesNotMatch(SOURCE, /fallback-/);
});
```

`SOURCE` 为该测试文件已有的源码读取常量，沿用即可。

- [ ] **Step 2: 运行测试确认失败**

```bash
node --experimental-strip-types --test src/pages/catalog/AssetOverviewPage.source-contract.test.ts
```

预期：FAIL，`DomainScopeNav` 未出现

- [ ] **Step 3: 改造页面容器**

`AssetOverviewPage.tsx` 中：

1. 删除 `const [domain, setDomain] = useState<string | undefined>();`，改为从 URL 派生：

```tsx
	const domain = searchParams.get("domain") || undefined;

	const setDomain = useCallback(
		(next: string | undefined) => {
			const params = new URLSearchParams(searchParams);
			if (next) {
				params.set("domain", next);
			} else {
				params.delete("domain");
			}
			setSearchParams(params);
		},
		[searchParams, setSearchParams],
	);
```

2. 用一次带统计的域树请求替换原先的 `listDomains` + `getDomainTree` 两次请求：

```tsx
	const [domainStats, setDomainStats] = useState<{
		all?: DomainScopeStats;
		unassigned?: DomainScopeStats;
		byDomain?: Record<string, DomainScopeStats>;
		truncated?: boolean;
	}>({});
```

在既有的 `useEffect` 中改为单次 `getDomainTree({ withStats: true })`，解析 `tree` 与 `stats` 分别落到 `domainTree` 与 `domainStats`。

3. `treeData` 的 `useMemo` 替换为：

```tsx
	const scopeNodes = useMemo(
		() => buildDomainScopeNodes(domainTree, domainStats.byDomain),
		[domainTree, domainStats.byDomain],
	);
```

4. `Layout.Sider` 内容整体替换为：

```tsx
			<Layout.Sider width={240} breakpoint="md" collapsedWidth={0} theme="light" className="rounded-lg border border-slate-200 bg-white p-3">
				<DomainScopeNav
					nodes={scopeNodes}
					allStats={domainStats.all}
					unassignedStats={domainStats.unassigned}
					value={domain}
					onChange={setDomain}
					loading={treeLoading}
					truncated={domainStats.truncated}
				/>
			</Layout.Sider>
```

5. 矩阵列名的 `domainMap` 改为由 `domainTree` 扁平化派生，删除 `listDomains` 调用与 `domains` state（消除双数据源漂移）。

- [ ] **Step 4: 运行测试确认通过**

```bash
node --experimental-strip-types --test src/pages/catalog/AssetOverviewPage.source-contract.test.ts
npx tsc --noEmit
```

预期：全部 PASS

- [ ] **Step 5: 提交**

```bash
git add source/dts-platform-webapp/src/pages/catalog/AssetOverviewPage.tsx \
        source/dts-platform-webapp/src/pages/catalog/AssetOverviewPage.source-contract.test.ts
git commit -m "refactor: 资产地图接入共享导航并把范围选择迁入 URL"
```

---

### Task 9: 台账页接入同一导航

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/catalog/DatasetsPage.tsx:363-378,545-577`
- Create: `source/dts-platform-webapp/src/pages/catalog/DatasetsPage.domain-scope.source-contract.test.ts`

**Interfaces:**
- Consumes: Task 7 的 `DomainScopeNav`、`buildDomainScopeNodes`
- Produces: 台账页与地图页一致的 `?domain=` 契约

- [ ] **Step 1: 写失败测试**

```ts
// source/dts-platform-webapp/src/pages/catalog/DatasetsPage.domain-scope.source-contract.test.ts
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");

test("台账与地图共用同一范围导航组件", () => {
	assert.match(SOURCE, /DomainScopeNav/);
	assert.doesNotMatch(SOURCE, /<Tree\b/);
});

test("台账范围选择同样进入 URL", () => {
	assert.match(SOURCE, /searchParams\.get\("domain"\)/);
});

test("台账不再存在 fallback- 静默降级", () => {
	assert.doesNotMatch(SOURCE, /fallback-/);
});
```

- [ ] **Step 2: 运行测试确认失败**

```bash
node --experimental-strip-types --test src/pages/catalog/DatasetsPage.domain-scope.source-contract.test.ts
```

预期：FAIL，`DomainScopeNav` 未出现

- [ ] **Step 3: 替换台账左侧**

按 Task 8 相同做法改造 `DatasetsPage.tsx`：`domain` 从 `searchParams` 派生、`treeData` 换 `buildDomainScopeNodes`、`Layout.Sider` 内容换成 `DomainScopeNav`（`breakpoint` 统一为 `md`（Sprint-72 契约已钉住台账为 md））。

台账已有其他筛选参数，`setDomain` 必须基于当前 `searchParams` 复制后再改动，不得整体覆盖：

```tsx
	const setDomain = useCallback(
		(next: string | undefined) => {
			const params = new URLSearchParams(searchParams);
			if (next) {
				params.set("domain", next);
			} else {
				params.delete("domain");
			}
			setSearchParams(params);
		},
		[searchParams, setSearchParams],
	);
```

台账的域树请求保持不带统计（`getDomainTree()`），左侧数字由 `undefined` stats 自然显示为 `—`；避免台账为一个侧栏多付一次全量扫描。

- [ ] **Step 4: 运行测试确认通过**

```bash
node --experimental-strip-types --test src/pages/catalog/DatasetsPage.domain-scope.source-contract.test.ts
node --experimental-strip-types --test src/pages/catalog/DatasetsPage.tags.source-contract.test.ts
node --experimental-strip-types --test src/pages/catalog/DatasetsPage.toolbar-consolidation.source-contract.test.ts
npx tsc --noEmit
```

预期：全部 PASS（后两个用于确认台账既有契约无回归）

- [ ] **Step 5: 提交**

```bash
git add source/dts-platform-webapp/src/pages/catalog/DatasetsPage.tsx \
        source/dts-platform-webapp/src/pages/catalog/DatasetsPage.domain-scope.source-contract.test.ts
git commit -m "refactor: 资产台账改用共享范围导航，消除重复 Tree 实现"
```

---

### Task 10: 治理缺口面板

**Files:**
- Create: `source/dts-platform-webapp/src/pages/catalog/assets/GovernanceGapPanel.tsx`
- Create: `source/dts-platform-webapp/src/pages/catalog/assets/GovernanceGapPanel.test.tsx`
- Modify: `source/dts-platform-webapp/src/pages/catalog/AssetOverviewPage.tsx:295-338`

**Interfaces:**
- Consumes: Task 1 的 `GOVERNANCE_STATUS_DICT`、`resolveEnumLabel`
- Produces: `GovernanceGapPanelProps = { total: number; attention: number; reasons: Array<{ key: string; label: string; count: number }>; onReasonClick: (key: string) => void; loading?: boolean }`

> **约束**：禁止修改 `MetricTile`（被 4 处复用且被契约测试断言为导出符号）。本面板是独立新组件。

- [ ] **Step 1: 写失败测试**

```tsx
// source/dts-platform-webapp/src/pages/catalog/assets/GovernanceGapPanel.test.tsx
// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GovernanceGapPanel } from "./GovernanceGapPanel";

let container: HTMLDivElement;
let root: Root;

const render = (element: ReactElement) => {
	act(() => {
		root.render(element);
	});
};

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

const REASONS = [
	{ key: "PENDING_DOMAIN", label: "待归域", count: 358 },
	{ key: "PENDING_CLAIM", label: "待认领", count: 2 },
	{ key: "PENDING_CLASSIFICATION", label: "未定密", count: 0 },
];

describe("GovernanceGapPanel", () => {
	it("零值原因用中性色，不用成功色", () => {
		render(<GovernanceGapPanel total={360} attention={360} reasons={REASONS} onReasonClick={vi.fn()} />);

		const zero = container.querySelector('[data-testid="gap-reason-PENDING_CLASSIFICATION"]');
		expect(zero?.getAttribute("data-tone")).toBe("neutral");
		expect(zero?.getAttribute("data-tone")).not.toBe("success");
	});

	it("非零原因用警示色", () => {
		render(<GovernanceGapPanel total={360} attention={360} reasons={REASONS} onReasonClick={vi.fn()} />);

		expect(container.querySelector('[data-testid="gap-reason-PENDING_DOMAIN"]')?.getAttribute("data-tone")).toBe(
			"warning",
		);
	});

	it("点击原因回传原因 key", () => {
		const onReasonClick = vi.fn();
		render(<GovernanceGapPanel total={360} attention={360} reasons={REASONS} onReasonClick={onReasonClick} />);

		act(() => (container.querySelector('[data-testid="gap-reason-PENDING_DOMAIN"]') as HTMLElement).click());
		expect(onReasonClick).toHaveBeenCalledWith("PENDING_DOMAIN");
	});

	it("待处置为 0 时整条转为健康态", () => {
		render(<GovernanceGapPanel total={360} attention={0} reasons={[]} onReasonClick={vi.fn()} />);

		expect(container.querySelector('[data-testid="gap-bar"]')?.getAttribute("data-state")).toBe("healthy");
		expect(container.textContent).toContain("当前范围无待处置资产");
	});

	it("总量为 0 时不出现除零产生的 NaN", () => {
		render(<GovernanceGapPanel total={0} attention={0} reasons={[]} onReasonClick={vi.fn()} />);

		expect(container.textContent).not.toContain("NaN");
	});
});
```

- [ ] **Step 2: 运行测试确认失败**

```bash
npx vitest run src/pages/catalog/assets/GovernanceGapPanel.test.tsx
```

预期：FAIL，无法解析 `./GovernanceGapPanel`

- [ ] **Step 3: 实现面板**

创建 `GovernanceGapPanel.tsx`。要点：

- 单条堆叠进度条 `data-testid="gap-bar"`，`data-state="healthy"`（`attention === 0`）或 `"attention"`；
- 百分比 = `total > 0 ? Math.round((attention / total) * 100) : 0`，避免 `total === 0` 时产生 `NaN`；
- 每个原因是 `<button type="button" data-testid="gap-reason-<key>" data-tone="neutral|warning">`，`count === 0` → `neutral`，`count > 0` → `warning`；**零值不得使用绿色/成功色**；
- `attention === 0` 时进度条转绿并显示「当前范围无待处置资产」；
- `loading` 时数字位 skeleton，不整块 spin。

`AssetOverviewPage.tsx` 中，把 `:295-327` 的五张 `MetricTile` 网格与 `:329-338` 的治理状态 chips 一并替换为：左侧一张 `MetricTile`（资产总量，保持复用不改动该组件）+ 右侧 `GovernanceGapPanel`。`reasons` 由 `overview.governanceStatusCounts` 经 `resolveEnumLabel(GOVERNANCE_STATUS_DICT, key)` 生成 label，并补上 `未定密`/`失效` 两项（分别取 `overview.unclassified`、`overview.stale`）。`onReasonClick` 调用既有 `drillToLedger` 并附带治理状态筛选参数。

- [ ] **Step 4: 运行测试确认通过**

```bash
npx vitest run src/pages/catalog/assets/GovernanceGapPanel.test.tsx
npx tsc --noEmit
```

预期：PASS，5 tests

- [ ] **Step 5: 提交**

```bash
git add source/dts-platform-webapp/src/pages/catalog/assets/GovernanceGapPanel.tsx \
        source/dts-platform-webapp/src/pages/catalog/assets/GovernanceGapPanel.test.tsx \
        source/dts-platform-webapp/src/pages/catalog/AssetOverviewPage.tsx
git commit -m "feat: 治理缺口面板取代五张重复 KPI 卡

零值改用中性色，消除未定密 0 配绿色对勾的误导性正反馈。"
```

---

### Task 11: 矩阵自适应与控件裁剪

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/catalog/AssetOverviewPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/catalog/AssetOverviewPage.source-contract.test.ts`

**Interfaces:**
- Consumes: Task 10 的面板；Task 2 的 `LAYER_META.code`
- Produces: 无（页面终态）

- [ ] **Step 1: 追加失败测试**

```ts
test("地图页 chrome 按钮不超过 1 个", () => {
	const buttons = [...SOURCE.matchAll(/<Button\b/g)];
	assert.ok(buttons.length <= 1, `地图页应只保留 1 个 icon-only 刷新按钮，实际 ${buttons.length} 个`);
});

test("不再有进入台账与去台账处置按钮", () => {
	assert.doesNotMatch(SOURCE, /进入台账/);
	assert.doesNotMatch(SOURCE, /去台账处置/);
});

test("矩阵列不再硬编码截断且单域时退化", () => {
	assert.doesNotMatch(SOURCE, /\.slice\(0,\s*8\)/);
	assert.match(SOURCE, /matrixColumns\.length\s*<=\s*1/);
});

test("分层呈现带中文 label 与弱化代号", () => {
	assert.match(SOURCE, /LAYER_META\[[^\]]+\]\.code/);
});

test("截断警告文案反映真实原因", () => {
	assert.doesNotMatch(SOURCE, /资产数量超过扫描上限/);
	assert.match(SOURCE, /统计上限/);
});
```

- [ ] **Step 2: 运行测试确认失败**

```bash
node --experimental-strip-types --test src/pages/catalog/AssetOverviewPage.source-contract.test.ts
```

预期：FAIL，按钮数为 2、`进入台账` 仍存在

- [ ] **Step 3: 裁剪控件与改造矩阵**

1. `PageHeader` 的 `actions` 替换为单个 icon-only 刷新，并入更新时间行：

```tsx
						actions={
							<div className="flex items-center gap-2 text-xs text-slate-500">
								<span>统计更新于 {lastUpdatedText}</span>
								<Button
									type="text"
									size="small"
									icon={<ReloadOutlined />}
									aria-label="刷新统计"
									loading={overviewLoading}
									onClick={() => void loadOverview()}
								/>
							</div>
						}
```

`lastUpdatedText` 由 `loadOverview` 成功后记录的时间戳格式化得到。

2. 删除 Top 5 模块标题右侧的「去台账处置」按钮；卡片本身已可点。

3. 「统计概览视图…」两行说明压成一行 12px 辅助文本，并在其后放当前范围回显（可点，带 `domain` 进台账）。

4. `matrixColumns` 去掉 `.slice(0, 8)`，改为 top 8 + 一列「其他 N 个域」（静态列，`title` 属性列出被合并的域名，无展开交互）。

5. `matrixColumns.length <= 1` 时不渲染表格，改渲染单行「分层分布」横条，每格显示 `LAYER_META[layer].label` + 弱化的 `LAYER_META[layer].code`。

6. `truncated` 警告文案改为：`统计基于前 ${overview.scanned} 条可见资产（已达统计上限），实际总量可能更多`。

- [ ] **Step 4: 运行测试确认通过**

```bash
node --experimental-strip-types --test src/pages/catalog/AssetOverviewPage.source-contract.test.ts
node --experimental-strip-types --test src/pages/catalog/DataAssetPortalMenu.source-contract.test.ts
npx tsc --noEmit
```

预期：全部 PASS（第二个用于确认台账菜单入口唯一性未被破坏）

- [ ] **Step 5: 提交**

```bash
git add source/dts-platform-webapp/src/pages/catalog/AssetOverviewPage.tsx \
        source/dts-platform-webapp/src/pages/catalog/AssetOverviewPage.source-contract.test.ts
git commit -m "refactor: 地图页控件收敛为单个刷新图标，矩阵单域时退化

进入台账与去台账处置按钮删除——台账已是全局菜单一级入口，
下钻改由矩阵格子、缺口原因、Top5 卡片与范围回显承担。"
```

---

### Task 12: 消除台账与导出的原值直显

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/catalog/assets/AssetLedgerView.tsx:166,182`
- Modify: `source/dts-platform-webapp/src/pages/catalog/DatasetsPage.tsx:501,509`
- Create: `source/dts-platform-webapp/src/pages/catalog/assets/AssetLedgerView.i18n.source-contract.test.ts`

**Interfaces:**
- Consumes: Task 1 的全部字典与 `resolveEnumLabel`
- Produces: 无（收尾）

- [ ] **Step 1: 写失败测试**

```ts
// source/dts-platform-webapp/src/pages/catalog/assets/AssetLedgerView.i18n.source-contract.test.ts
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const LEDGER = readFileSync(new URL("./AssetLedgerView.tsx", import.meta.url), "utf8");
const PAGE = readFileSync(new URL("../DatasetsPage.tsx", import.meta.url), "utf8");

test("台账不再直显资产类型原值", () => {
	assert.doesNotMatch(LEDGER, /<Tag>\{row\.type\b/);
	assert.match(LEDGER, /resolveEnumLabel\(ASSET_TYPE_DICT/);
});

test("台账不再以治理状态原值兜底", () => {
	assert.doesNotMatch(LEDGER, /\|\|\s*row\.governanceStatus/);
	assert.match(LEDGER, /resolveEnumLabel\(GOVERNANCE_STATUS_DICT/);
});

test("导出内容经过枚举字典", () => {
	assert.doesNotMatch(PAGE, /^\s*row\.type,\s*$/m);
	assert.match(PAGE, /resolveEnumLabel\(ASSET_TYPE_DICT/);
});
```

- [ ] **Step 2: 运行测试确认失败**

```bash
node --experimental-strip-types --test src/pages/catalog/assets/AssetLedgerView.i18n.source-contract.test.ts
```

预期：FAIL，`<Tag>{row.type` 仍存在

- [ ] **Step 3: 接入字典**

`AssetLedgerView.tsx:166`：

```tsx
									<Tag>{resolveEnumLabel(ASSET_TYPE_DICT, row.type, "未知类型")}</Tag>
```

`AssetLedgerView.tsx:182`：

```tsx
											{readiness.reasons.slice(0, 2).join(" / ") ||
												resolveEnumLabel(GOVERNANCE_STATUS_DICT, row.governanceStatus, "-")}
```

`DatasetsPage.tsx:509` 的导出行中 `row.type` 替换为 `resolveEnumLabel(ASSET_TYPE_DICT, row.type, "未知类型")`；同时检查同一 `map` 内其余列是否有原值直出（密级、治理状态、生命周期），一并接字典。

- [ ] **Step 4: 运行全部相关测试**

```bash
node --experimental-strip-types --test src/pages/catalog/assets/AssetLedgerView.i18n.source-contract.test.ts
node --experimental-strip-types --test src/pages/catalog/assets/assetEnumLabels.test.ts
npx tsc --noEmit
```

预期：全部 PASS

- [ ] **Step 5: 提交**

```bash
git add source/dts-platform-webapp/src/pages/catalog/assets/AssetLedgerView.tsx \
        source/dts-platform-webapp/src/pages/catalog/assets/AssetLedgerView.i18n.source-contract.test.ts \
        source/dts-platform-webapp/src/pages/catalog/DatasetsPage.tsx
git commit -m "fix: 台账表格与导出改经枚举字典，消除英文原值直显"
```

---

### Task 13: 全量验证与响应式走查

**Files:**
- Modify: 视验证结果修复

**Interfaces:**
- Consumes: Task 1～12 全部产出
- Produces: 无

- [ ] **Step 1: 跑通全部前端测试**

工作目录 `source/dts-platform-webapp`：

```bash
node --experimental-strip-types --test src/pages/catalog/assets/assetEnumLabels.test.ts
node --experimental-strip-types --test src/pages/catalog/AssetOverviewPage.source-contract.test.ts
node --experimental-strip-types --test src/pages/catalog/DatasetsPage.domain-scope.source-contract.test.ts
node --experimental-strip-types --test src/pages/catalog/DatasetsPage.tags.source-contract.test.ts
node --experimental-strip-types --test src/pages/catalog/DatasetsPage.toolbar-consolidation.source-contract.test.ts
node --experimental-strip-types --test src/pages/catalog/DatasetsPage.asset-map-visual.source-contract.test.ts
node --experimental-strip-types --test src/pages/catalog/DataAssetPortalMenu.source-contract.test.ts
node --experimental-strip-types --test src/pages/catalog/assets/AssetLedgerView.i18n.source-contract.test.ts
npx vitest run \
	src/pages/catalog/assetPortalUx.helpers.test.ts \
	src/pages/catalog/assets/assetPageShared.layers.test.ts \
	src/components/catalog/DomainScopeNav.test.tsx \
	src/pages/catalog/assets/GovernanceGapPanel.test.tsx
```

预期：全部 PASS。任何 FAIL 都要修到通过，不得跳过。

> **运行器归属**：`node --experimental-strip-types --test` 只能跑纯 `.ts`（无 JSX）的文件；
> 凡是 import `.tsx` 或本身是 `.tsx` 的测试一律用 `npx vitest run`。
> `assetPortalUx.helpers.test.ts` 与 `assetPageShared.layers.test.ts` 属于 vitest。

- [ ] **Step 2: 跑通全部 Java 测试**

工作目录 `source/dts-platform`：

```bash
./mvnw test -Dtest='CatalogAssetPortalServicePermissionParityTest,CatalogAssetPortalTagFilterTest,CatalogAssetPortalStatsTest,CatalogAssetOverviewAggregatorTest'
```

预期：全部 PASS。这四个类是本 sprint 触及且**确认在白名单内**的（前两个原本就在，后两个由 Task 3/4 加入）。

> **不要**用 `CatalogDomain*Test` 之类的通配：`CatalogDomainVisibilityServiceTest`、
> `CatalogDomainFactsLiquibaseTest` 均不在白名单，通配会让整条命令报
> `No tests matching pattern` 而失败。
>
> **本仓库的 Java 测试是 opt-in 的**：374 个测试源文件中只有 76 个进了
> `pom.xml` 的 `<testIncludes>`，其余 298 个从不编译也从不运行。这不是本 sprint 要修的问题，
> 但它意味着"Java 测试全绿"只覆盖白名单内的部分——验收时不要把它当成全量回归证据。

- [ ] **Step 3: 类型检查与构建**

```bash
cd source/dts-platform-webapp && npx tsc --noEmit && pnpm build
```

预期：无类型错误，构建成功

- [ ] **Step 4: 响应式与主题走查**

启动开发服务器，在 320 / 768 / 1024 / 1440 四个宽度下检查 `/catalog/assets` 与 `/catalog/assets/ledger`：

- [ ] 侧栏在 `lg` 以下自动收起，不产生横向滚动
- [ ] 治理缺口面板与分层横条在窄屏内部滚动，`body` 不横向滚动
- [ ] 矩阵在 ≥2 域时横向可滚，表头不错位
- [ ] 界面上找不到任何英文枚举原值，也没有「未知（」出现

- [ ] **Step 5: 提交变更范围核对**

```bash
git status
git log --oneline v2.2.3..HEAD
```

确认改动只落在计划列出的文件上，无意外文件被带入。

- [ ] **Step 6: 提交收尾**

```bash
git add -A
git commit -m "test: sprint-75 全量验证与响应式走查修复"
```

---

## 自查记录

**spec 覆盖核对**：

| spec 项 | 对应 Task |
|---|---|
| ADR-75-01 可见性单一事实源 | Task 4、5（复用 `canRead`，不写 SQL 聚合）|
| ADR-75-02 批量路径 | Task 5/Step 4 |
| ADR-75-03 合并域树接口 | Task 6 |
| ADR-75-04 未归域独立分区 | Task 7 |
| ADR-75-05 去根节点与虚线 | Task 7 |
| ADR-75-06 范围进 URL | Task 8、Task 9 |
| ADR-75-07 缺标识域禁用 | Task 7 |
| ADR-75-08 共享组件 | Task 7、Task 9 |
| ADR-75-09 控件裁剪 | Task 11 |
| ADR-75-10 台账不升 tab | Task 11/Step 4 断言菜单契约 |
| ADR-75-11 零值中性色 | Task 10 |
| ADR-75-12 矩阵退化 | Task 11 |
| ADR-75-13 中文化与字典 | Task 1、2、12 |
| ADR-75-14 失效判定 | Task 3 |
| G-75-01～09 | 依次由 Task 6、5、11、11、7、8/9、12、2、3 覆盖 |

**已知需在实现时就地确认的两点**（不是占位符，是需读代码才能定的细节）：

1. Task 4/Step 3：`listLegacyAssets` 的 `total` 是否随 `limit` 变化，决定修复形态。实现者须先读该方法再动手。
2. Task 6/Step 5：`AssetQuery` 是否已有无参工厂；若无则新增 `unscoped()` 并复用，避免多处内联构造。
