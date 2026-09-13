# F4: 敏感数据自动识别引擎

**优先级**: P1
**状态**: READY

## 目标

闭合协议 2.3.2.5「敏感数据识别规则定义 + 自动识别 + 监控查询」三项硬要求。当前脱敏链路前置能力缺位：识别规则无法定义、无扫描引擎、脱敏规则全靠人工逐字段标注，仅有基于密级的被动 `MASKING_GAP` warning，无对真实数据内容的扫描打标。本 feature 补齐「规则定义 → 自动扫描 → 结果留痕 → 监控查询 → 建议转脱敏」闭环。

## 协议依据与缺口

- 协议条款：2.3.2.5 数据安全 — 子项 2.2（识别规则定义 + 自动识别 + 监控查询）
- 当前缺口（带证据，源自 `assets/gap-evidence/M05-数据安全.md`）：
  - 全仓 `grep -ilE "sensitiveScan|SensitiveData|autoIdentif|piiDetect|RecognitionRule|IdentificationRule|DiscoveryRule"` 在 dts-platform/dts-admin/dts-analytics main/java 下 **0 命中**，无识别引擎类。
  - 脱敏规则仍人工标注：`dts-platform/.../domain/catalog/CatalogMaskingRule.java` 仅 column/function/args 三字段，由 `web/rest/catalog/CatalogMaskingResource.java` 的 `createMasking/updateMasking` 人工 CRUD。
  - 仅有被动告警：`CatalogMaskingResource.validateClassificationMappingInternal`（约 line 194/265）对高密级数据集无脱敏规则给出 `MASKING_GAP` warning，是基于密级的提醒而非内容扫描识别。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | SensitiveRule 领域模型 + CRUD + changelog | P1 | READY | — |
| T02 | SensitiveScanService 扫描引擎（元数据 + 抽样匹配） | P1 | READY | T01 |
| T03 | SensitiveScanResult 落库 + 扫描任务（手动 + 定时） | P1 | READY | T02 |
| T04 | 敏感数据监控查询视图 + 建议一键转 CatalogMaskingRule | P1 | READY | T03 |
| T05 | 集成测试（正则/字典/AI 阈值 + 不外泄 + 闭环） | P1 | READY | T01-T04 |

## 完成标准

- [ ] 支持 REGEX/DICTIONARY/AI 三类识别规则的定义与 CRUD，变更写 changelog。
- [ ] 扫描引擎对 catalog 字段元数据 + 受控抽样数据执行匹配，产出候选敏感字段与建议脱敏函数。
- [ ] 扫描结果留痕命中字段/规则/密级/置信度，原始样本不外泄、抽样有可控上限。
- [ ] 监控查询视图可见命中字段、规则、密级、是否已配脱敏；扫描建议可一键转 `CatalogMaskingRule`。
- [ ] 扫描任务支持手动触发与定时执行（可挂 airflow-om）。

## TDD 约定

- 每个 task RED→GREEN→REFACTOR，测试先行；覆盖率 ≥80%，鉴权/口令/会话路径要求分支覆盖。
- 改既有 symbol 前先 `gitnexus_impact`；Java 侧禁用 `Optional.get()`，统一用 `orElseThrow()`。
