# IT-08 result

**结果**：PASS_WITH_GAPS  
**代码基线**：`82d6e8eec`、`8742e2bfe`、`286ffc68a`

## 已证明

- 只命中固定 5 个旧菜单名，仅修改 `deleted` 和审计字段。
- menu id、角色/visibility binding 均未删除。
- platform/webapp/admin 旧镜像实际回切健康，菜单 rollback 恢复 5 行。
- 候选镜像和 5 行软删除状态均已恢复，最终 health 与 HTTPS 通过。
- admin 的方向性服务令牌正反例通过：缺失/错误为 403，正确令牌进入业务处理。

## 未证明

- 8 条 compatibility route 尚未满足连续两个版本零访问。
- 未做客户环境 90 天画像或旧业务表删除审批。
- 因此不得物理删除 compatibility route、旧 API 或客户表。
