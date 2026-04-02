# T02: `portal_sessions` 数据模型与索引迁移

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
让数据库层能够表达浏览器作用域会话，支撑同浏览器多 tab 共存和跨浏览器接管。

## 技术设计
- 在 `portal_sessions` 增加 `browser_id`、`session_scope`、`last_renewed_at` 等字段
- 用新的部分唯一索引替代当前按用户名唯一活跃 session 的约束
- 保留 `revoked_reason`、`revoked_by_session_id` 等审计字段，但 successor 链不再作为运行时机制
- 为历史 session 设计兼容迁移策略，切换窗口内允许旧记录自然失效或强制重新登录
- 增加面向观测的索引，支持按 `browser_id`、`normalized_username`、`revoked_reason` 查询

## 影响范围
- `source/dts-platform/src/main/resources/config/liquibase/master.xml`
- `source/dts-platform/src/main/resources/config/liquibase/changelog/`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/security/PortalSessionEntity.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/security/PortalSessionRepository.java`
- `services/dts-pg/`

## 验证
- [ ] 产出 Liquibase 正向迁移脚本
- [ ] 产出迁移回滚脚本或回滚步骤
- [ ] 在测试库验证唯一约束符合预期

## 完成标准
- [ ] 新模型能表达“一用户一浏览器一活跃会话”
- [ ] 数据迁移对现网历史记录有明确处理方案
