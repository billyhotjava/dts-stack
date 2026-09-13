# T01: SensitiveRule 领域模型 + CRUD + changelog

**优先级**: P1
**状态**: READY
**依赖**: —

## 目标

新增敏感数据识别规则领域模型，支持 REGEX/DICTIONARY/AI 三类规则的定义、CRUD 与变更留痕，作为扫描引擎的规则源。

## TDD 测试先行（RED）

- 新增 `SensitiveRuleResourceTest`（放 `dts-platform/src/test/java/.../web/rest/security/`）：
  - `createRule` 三类 type（REGEX/DICTIONARY/AI）各一例，断言落库与响应 DTO 字段齐全。
  - REGEX 缺 `pattern`、DICTIONARY 缺 `dictRef`、AI 缺 `threshold` 时返回 `SENSITIVE_RULE_INVALID`，不抛模糊 500。
  - `threshold` 越界（<0 或 >1）拒绝；`targetClassification` 必须是合法 `DataLevel` 枚举。
  - 非 INSTITUTE_PRIVILEGED 角色调用 CRUD 返回 403（鉴权分支覆盖）。
- 新增 `SensitiveRuleServiceTest`：create/update/delete 后断言写入一条 changelog 记录。

## 技术设计（GREEN）

- 新增实体 `SensitiveRule`（`domain/security/SensitiveRule.java`）：`name`、`type`(REGEX/DICTIONARY/AI 枚举)、`pattern`、`dictRef`、`threshold`(0~1)、`targetClassification`(复用 `security/policy/DataLevel.java`)、`suggestedMaskingFunction`、`enabled`、审计字段。
- 新增 `repository/security/SensitiveRuleRepository.java`、`service/security/SensitiveRuleService.java`、`web/rest/security/SensitiveRuleResource.java`（CRUD + 启停）。
- 建议脱敏函数取值对齐 `service/security/MaskingFunctions.java`，避免再造语义源。
- 变更留痕复用 `domain/catalog/CatalogMetadataChangeLog.java` + `repository/catalog/CatalogMetadataChangeLogRepository.java` 模式，或新增 `SensitiveRuleChangeLog` 同构记录。
- 入参用 `@Valid` + 枚举校验在边界 fail-fast；`Optional` 一律 `orElseThrow()`。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/security/SensitiveRule.java`（新增）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/security/SensitiveRuleRepository.java`（新增）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/SensitiveRuleService.java`（新增）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/security/SensitiveRuleResource.java`（新增）
- 复用：`service/security/MaskingFunctions.java`、`security/policy/DataLevel.java`、`domain/catalog/CatalogMetadataChangeLog.java`（引用前先 `gitnexus_impact`）

## 验证

- [ ] 三类规则可创建并回读，DTO 字段完整。
- [ ] 缺字段/越界/非法密级返回 `SENSITIVE_RULE_INVALID`，无模糊 500。
- [ ] CRUD 鉴权限定 INSTITUTE_PRIVILEGED，越权 403。
- [ ] 每次变更写一条 changelog。

## 完成标准

- [ ] SensitiveRule 实体 + CRUD + changelog 落地，供 T02 扫描引擎消费。
