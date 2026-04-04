# F5: 清理废弃代码和表 (Phase 5)

**优先级**: P2
**状态**: READY

## 目标
在所有迁移完成并稳定运行至少一个版本周期后，删除废弃的表、清理冗余代码、精简配置。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 删除废弃数据库表 | P2 | READY | F4, F5 |
| T02 | 清理 AdminKeycloakUser 相关代码 | P2 | READY | F3 |
| T03 | 清理冗余配置和 InMemoryStore | P2 | READY | T01, T02 |

## 完成标准
- [ ] person_profile、admin_keycloak_user、organization_node 表 DROP
- [ ] 相关实体、Repository、Service 代码全部删除
- [ ] InMemoryStore 用户/组织相关部分删除
- [ ] 配置文件中废弃的同步配置删除
