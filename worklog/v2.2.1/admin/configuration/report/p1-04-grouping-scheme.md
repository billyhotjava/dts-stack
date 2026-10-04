# P1-04 配置细分分组方案（稳定版）

## 设计目标

- 保持一级分类不变：`FEATURE_TOGGLE` / `SECURITY` / `DATABASE` / `INTEGRATION` / `SYSTEM`。
- 每个一级分类下再做“业务子分组”，减少平铺列表长度。
- 分组规则配置化，避免每次改组都改 Java 代码。

## 分组模型

- 数据来源：`config/ops-config-groups.yml`
- 规则匹配：按 `keys` / `prefixes` / `regexes` 命中
- 输出字段：
  - `groupKey`
  - `groupLabel`
  - `groupOrder`

## 当前子分组

- `FEATURE_TOGGLE`
  - 核心能力开关
  - 集成能力开关
- `SECURITY`
  - 认证与登录
  - PKI 配置
  - 访问控制
  - 会话策略
- `INTEGRATION`
  - Airflow 调度
  - OpenMetadata 元数据
  - MDM 网关
  - Analytics OIDC
- `SYSTEM`
  - 审计与日志
  - 运行时参数

## 展示策略

- 页面分三层信息：
  - 一级分类（Tab）
  - 二级分组（Card）
  - 配置项（Item）
- 每个分组展示：
  - 配置项数量
  - 需重启数量
  - 敏感项数量

## 稳定性约束

- 未命中任何规则的配置项落入 `*-other` 兜底分组，不会丢失显示。
- 分组仅影响展示，不影响配置读写语义。
- 规则可热变更（随应用重启加载），不需数据库迁移。
