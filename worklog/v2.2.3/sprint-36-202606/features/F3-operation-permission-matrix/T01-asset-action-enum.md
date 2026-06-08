# T01: AssetAction 动作枚举

**优先级**: P0
**状态**: READY
**依赖**: —

## 目标

定义协议 2.3.2.5 要求的 8 个资产操作动作枚举 `AssetAction`（CREATE/DELETE/UPDATE/COPY/IMPORT/EXPORT/ARCHIVE/DESTROY），并与审计 `AuditOperationType` 语义对齐，作为后续矩阵实体与校验的唯一动作源。

## TDD 测试先行（RED）

- 新增 `AssetActionTest`，放 `dts-platform/src/test/java/com/yuzhi/dts/platform/security/policy/`。
- 断言枚举恰好含 8 个动作，且 `AssetAction.values().length == 8`；逐个断言 code（大写）与中文 displayName（新增/删除/修改/复制/导入/导出/归档/销毁）。
- 断言 `AssetAction.from(raw)` 大小写不敏感、可解析中文别名，未知值抛 `IllegalArgumentException`（fail-fast，不静默回退到放行动作）。
- 断言对齐映射：`COPY`/`DESTROY` 之外的 6 动作能映射到 `AuditOperationType` 对应项（CREATE/DELETE/UPDATE/IMPORT/EXPORT/ARCHIVE）；`COPY`/`DESTROY` 为审计枚举缺失项，需在 `OperationTypeNormalizer` 补别名而非重造一套。

## 技术设计（GREEN）

- 新增 `dts-platform/src/main/java/com/yuzhi/dts/platform/security/policy/AssetAction.java`：`enum`，字段 `code`、`displayName`、`mutating`，提供 `from(String)`（参照 `AuditOperationType.from` 的归一化风格）。
- 语义对齐：6 个动作复用 `dts-admin/.../service/audit/AuditOperationType.java`（已含 CREATE/UPDATE/DELETE/ARCHIVE/IMPORT/EXPORT）的 code 与中文名；`COPY`/`DESTROY` 在 `AuditOperationType` 中缺失，于 `dts-platform/.../service/audit/OperationTypeNormalizer.java` 增补别名映射，保证审计与授权动作语义一致。
- 不在本 task 引入实体/表，仅枚举 + 单测，供 T02 复用。

## 影响范围

- 新增：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/policy/AssetAction.java`
- 改既有（需 `gitnexus_impact`）：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/audit/OperationTypeNormalizer.java`（仅补 COPY/DESTROY 别名）
- 对齐参考（只读）：`source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/audit/AuditOperationType.java`

## 验证

- [ ] 枚举含且仅含 8 个协议动作，code/displayName 与断言一致。
- [ ] `from` 解析中文/英文/大小写，未知动作 fail-fast 抛异常。
- [ ] 6 动作与 `AuditOperationType` 对齐，COPY/DESTROY 已在归一化器补别名。

## 完成标准

- [ ] `AssetAction` 作为唯一动作源被 T02/T03 引用，无第二套动作字符串常量。
