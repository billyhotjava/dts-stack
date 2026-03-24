# 大屏默认可见性调整设计

**背景**

当前 `analytics_screen` 的读权限完全依赖 `analytics_screen_acl`。创建大屏时系统只会给创建人补一条 `MANAGE`，没有任何默认角色级 `READ`。因此 `ROLE_OP_ADMIN` 创建的大屏，除创建人和 superuser 外，部门领导、部门数据管理员、研究所数据管理员等角色默认都看不到。

**目标**

- 新创建的大屏默认允许以下角色读取：
  - `ROLE_DEPT_LEADER`
  - `ROLE_DEPT_DATA_OWNER`
  - `ROLE_INST_DATA_OWNER`
  - `ROLE_INST_LEADER`
- 对历史已有大屏执行同样的默认 `READ` 回填，解决当前现场已创建大屏不可见的问题。
- 不改变编辑、发布、管理权限；默认只新增 `READ`。

**现状约束**

- `AnalyticsScreen` 当前没有“所属部门/责任部门”字段，无法做“仅本部门领导可见”的精细范围控制。
- `ScreenAclService` 的读写逻辑已经成熟，最小风险方案是继续沿用 ACL 模型，而不是在 `snapshot()` 中写死角色绕过 ACL。

**方案比较**

1. 仅新增“新建时默认 ACL”
   - 优点：改动小
   - 缺点：历史大屏仍然不可见

2. 新建默认 ACL + 历史 ACL 回填
   - 优点：一次解决现有和后续问题
   - 缺点：会把现有大屏统一开放给上述角色读

3. 在 `ScreenAclService.snapshot()` 中对角色硬编码全局可读
   - 优点：实现最快
   - 缺点：绕开 ACL 模型，后续很难治理

**结论**

采用方案 2。

**设计要点**

- 在 `ScreenAclService` 增加统一的“默认可读角色”常量与补齐方法。
- `ScreenResource.create()` 在创建后除了 `ensureCreatorManage()`，再调用“补默认可读 ACL”。
- `ScreenResource.list()` 在列屏时对每条 screen 做一次幂等补齐，确保历史数据逐步修复；同时新建链路保证后续不再漏。
- 回填逻辑必须幂等：同一 `screenId + ROLE + READ` 已存在时不重复写。
- 仍然保留显式 ACL 编辑能力；手工编辑 ACL 后不会移除这些默认只读角色，除非后续另做产品规则。

**测试**

- 集成测试新增：
  - 创建 screen 后，`/api/screens/{id}/acl` 可看到四条角色级 `READ`
  - 历史 screen 只有创建人 `MANAGE` 时，普通 `ROLE_DEPT_LEADER` 用户访问 `/api/screens` 后能看到该大屏，且 ACL 被补齐

