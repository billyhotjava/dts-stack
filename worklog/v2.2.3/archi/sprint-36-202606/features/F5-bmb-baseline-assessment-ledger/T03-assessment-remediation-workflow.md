# T03: 测评整改工作流：状态机 + 两轮迭代录入

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

将整改台账由单轮自查升级为支持甲方测评机构离线+在线两轮迭代的整改工作流：状态机 NOT_STARTED→IN_PROGRESS→DONE/WAIVED，支持每轮整改项录入、责任人、闭环时间、复测结论。

## TDD 测试先行（RED）

- 新增 `SecurityBaselineRemediationServiceTest`（放 `.../service/security/baseline/`）。
- 断言：合法流转 NOT_STARTED→IN_PROGRESS→DONE 通过；非法跳变（如 DONE→NOT_STARTED）抛 `IllegalArgumentException`，复用 `normalizeStatus`（`SecurityBaselineService.java:180-190`）的白名单语义并新增前置态校验。
- 断言：可写入 `assessmentRound`（取值 OFFLINE/ONLINE 或 1/2）、`owner`、`closedAt`、`retestConclusion`，第二轮录入不覆盖第一轮记录（追加而非原地改写，符合不可变留痕）。
- 断言：WAIVED 必须带 `notes` 说明，否则拒绝。

## 技术设计（GREEN）

- 扩展实体 `SecurityBaselineRemediation.java` 增列 `assessment_round`、`owner`、`closed_at`、`retest_conclusion`；或新增子表 `security_baseline_assessment_iteration`（推荐，保留两轮独立留痕，外键 `check_key`）。
- 新增/扩展 `SecurityBaselineRemediationUpdateRequest`（`.../baseline/request/`）携带轮次与复测字段；在 `updateRemediation`（`SecurityBaselineService.java:100`）中加入状态机前置态校验与轮次落库。
- `SecurityBaselineRemediationRepository` 增按 `checkKey` 查询迭代记录的方法；查询用 `orElseThrow()`，不得 `Optional.get()`。
- 新增 liquibase changelog 建迭代表并在 master 注册。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/baseline/SecurityBaselineService.java`（改既有 symbol，需 gitnexus_impact）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/security/SecurityBaselineRemediation.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/baseline/request/SecurityBaselineRemediationUpdateRequest.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/security/SecurityBaselineRemediationRepository.java`
- `source/dts-platform/src/main/resources/config/liquibase/changelog/20260608_xx_baseline_assessment_iteration.xml`（新增）+ `master.xml`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/security/baseline/SecurityBaselineRemediationServiceTest.java`（新增）

## 验证

- [ ] 状态机合法流转通过、非法跳变被拒。
- [ ] 离线+在线两轮迭代各自留痕，含责任人/闭环时间/复测结论。
- [ ] WAIVED 强制要求说明 notes。

## 完成标准

- [ ] 整改工作流支持两轮迭代录入与状态机门禁，留痕不可被后轮覆盖。
