# T05: 集成测试（识别 → 扫描 → 监控 → 转规则闭环）

**优先级**: P1
**状态**: READY
**依赖**: T01-T04

## 目标

端到端验证敏感数据自动识别全链路：规则命中、AI 置信度阈值、原始样本不外泄、建议转规则闭环、定时任务触发。

## TDD 测试先行（RED）

- 新增 `SensitiveDataDiscoveryIT`（放 `dts-platform/src/test/java/.../web/security/`，Spring Boot 集成测试）：
  - **正则命中**：建 REGEX 规则（手机号/邮箱样例列），扫描后监控视图可见命中字段 + 建议脱敏函数。
  - **字典命中**：建 DICTIONARY 规则，样例字段按字典命中并落 `SensitiveScanResult`。
  - **AI 阈值**：AI 推断 `confidence < threshold` 不产出候选；`>= threshold` 产出，断言阈值边界两侧行为。
  - **不外泄**：断言扫描 run 留痕、result、审计日志、API 响应均不含原始样本明文（仅命中计数/掩码摘要）。
  - **闭环**：候选 `convertToMaskingRule` → 落 `CatalogMaskingRule`，再次校验分类映射 `MASKING_GAP` 收敛。
  - **定时任务**：模拟调度触发扫描任务，断言生成 run + result，且同数据集不并发重复扫描。

## 技术设计（GREEN）

- 用 Spring Boot 测试上下文 + 内存/测试库装配 T01-T04 链路；样例数据集与字段以 fixture 准备（含手机号/邮箱/身份证样例列）。
- 鉴权用 INSTITUTE_PRIVILEGED 测试身份；越权用例断言 403。
- 覆盖率 ≥80%，鉴权/口令/会话与阈值分支要求分支覆盖。
- 证据落 `worklog/v2.2.3/sprint-36-202606/it/evidence/sensitive-discovery/`。

## 影响范围

- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/security/SensitiveDataDiscoveryIT.java`（新增）
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/security/`（T01-T03 单测补强）
- `source/dts-platform-webapp/test/**`（监控页 source 契约测试）
- `worklog/v2.2.3/sprint-36-202606/it/evidence/sensitive-discovery/`（证据目录）

## 验证

- [ ] 正则/字典命中样例字段并落库可查。
- [ ] AI 置信度阈值两侧行为正确。
- [ ] 扫描全链路不外泄原始样本明文。
- [ ] 建议转 `CatalogMaskingRule` 闭环且 `MASKING_GAP` 收敛。
- [ ] 定时任务触发生成 run + result，不并发重复。

## 完成标准

- [ ] 全链路集成测试通过，覆盖率 ≥80%，证据归档。
