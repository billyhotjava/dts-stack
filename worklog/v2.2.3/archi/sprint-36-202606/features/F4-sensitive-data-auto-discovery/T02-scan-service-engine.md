# T02: SensitiveScanService 扫描引擎

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标

新增扫描引擎，对 catalog 字段元数据 + 受控抽样数据执行 T01 规则匹配，产出候选敏感字段与建议脱敏规则，原始样本不外泄、抽样有可控上限。

## TDD 测试先行（RED）

- 新增 `SensitiveScanServiceTest`（放 `dts-platform/src/test/java/.../service/security/`）：
  - REGEX 规则命中样例字段（如手机号列）→ 返回候选字段含规则、密级、`confidence` 与 `suggestedMaskingFunction`。
  - DICTIONARY 规则按字典命中 → 候选字段命中标记正确。
  - AI 类型推断结果 `confidence < threshold` 时不产出候选（阈值分支覆盖）。
  - 抽样上限断言：请求 N 行时实际读取 ≤ `maxSampleRows`（默认上限常量），超限不放大。
  - 不外泄断言：返回 DTO 与日志均不含原始样本明文，仅含命中计数/掩码摘要。

## 技术设计（GREEN）

- 新增 `service/security/SensitiveScanService.java`：输入数据集/字段范围 + 规则集，输出候选敏感字段列表（fieldName、命中 ruleId、`targetClassification`、`confidence`、`suggestedMaskingFunction`）。
- 字段元数据来源 `domain/catalog/CatalogColumnSchema.java`（name/dataType/tags/`sensitiveTags`/comment）经 `repository/catalog/CatalogColumnSchemaRepository.java` 读取；命中后回写 `sensitiveTags` 候选标记。
- 抽样数据复用 `web/rest/ExploreResource.java` 的 preview/采样通道（约 line 114 `/query/preview`），但封装为内部受控读取：硬上限 `maxSampleRows` 常量、行级裁剪沿用 `isRowLevelAllowed`，样本仅在内存匹配后丢弃。
- 匹配器分三策略类（Regex/Dictionary/Ai 推断），AI 走 `threshold` 置信度门禁；策略产出统一候选模型。
- 不外泄：匹配只统计命中，不持久化/不回传原始值；`Optional` 用 `orElseThrow()`。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/SensitiveScanService.java`（新增）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/scan/`（匹配策略，新增）
- 复用读取：`domain/catalog/CatalogColumnSchema.java`、`repository/catalog/CatalogColumnSchemaRepository.java`
- 复用抽样：`web/rest/ExploreResource.java`（`isRowLevelAllowed`、preview 通道，改既有 symbol 前先 `gitnexus_impact`）
- 复用规则：T01 `SensitiveRule` + `SensitiveRuleRepository`

## 验证

- [ ] 正则/字典命中样例字段产出候选与建议脱敏函数。
- [ ] AI 置信度低于 threshold 不产出候选。
- [ ] 抽样实际读取不超过 `maxSampleRows` 上限。
- [ ] 候选 DTO 与日志不含原始样本明文。

## 完成标准

- [ ] 扫描引擎可对指定数据集产出候选敏感字段，供 T03 落库。
