# Sprint-83 dbt 工程 Fixture 画像

**画像日期**：2026-08-02  
**结论**：83a 的 S1/S2 工程准入通过；客户兼容声明与 materialization 认证仍保持独立 GAP。  
**事实源**：`source/dts-platform/src/test/resources/fixtures/dbt-sprint83/`

## 工程样本

| ID | 类型 | 规模与结构 | 预期结果 | 当前结果 |
|---|---|---|---|---|
| FX-01 | artifact-rich | manifest v12；PostgreSQL；2 model（1 technical + 1 governed）；1 source；1 test；1 macro；4 edges；最大深度 3 | inspection/import projection 可继续；不代表 runtime 已认证 | PASS：解析出 1 个业务模型、technical/source/test/macro 与 catalog 类型证据 |
| FX-03 | 基础 BLOCKED | source-only；1 model；missing literal ref + dynamic ref | 受影响模型 BLOCKED，不能进入 apply 选择 | PASS：返回语义、字段、缺失依赖、动态依赖四类稳定 `SOURCE_*` 阻断码，并保留 unresolved/dynamic 诊断码 |
| FX-05 | 恶意 ZIP | parent traversal、absolute path、duplicate entry、compression budget 四类 | 持久化前拒绝 | PASS：样本清单固定；由安全测试在内存构造 ZIP，不在 Git 保存危险二进制 |

## 数据与许可边界

- 全部样本为本 Sprint 重新构造的合成数据，不含客户数据、账号、凭据、连接串或生产标识符。
- 离线执行，不下载 package，不执行 dbt、SQL 或宏。
- `inventory.json` 冻结版本、节点/边/深度、期望能力与 `materialization=NOT_CERTIFIED`。
- `SHA256SUMS` 固定所有输入；2026-08-02 执行 `sha256sum -c SHA256SUMS`，11/11 通过。

## 可重复验证

```text
flock /tmp/dts-build-maven-2755010465.lock \
  ./mvnw -ntp -Dspotless.apply.skip=true \
  -Dtest=Sprint83DbtFixtureContractTest test
```

结果：`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`，测试阶段耗时 1.369 秒，Maven `BUILD SUCCESS`。首次 RED 分别暴露 fixture test 节点缺少安全资源路径、manifest 版本标准化及 test name 标准化断言问题；修正 fixture/断言后转 GREEN，未修改生产解析逻辑。

## 后续切片边界

- FX-02 enforced/non-enforced/no-column source-only 三分支与 FX-04 base/current/incoming 漂移属于 S4 READY 条件，不反向阻断 83a。
- 客户脱敏包缺失只阻断 `CUSTOMER-VALIDATION`/客户兼容声明。
- H83-01 + F0/T05 唯一拥有 PostgreSQL materialization runtime 候选与认证；FX-01 不得冒充 `CERTIFIED`。
