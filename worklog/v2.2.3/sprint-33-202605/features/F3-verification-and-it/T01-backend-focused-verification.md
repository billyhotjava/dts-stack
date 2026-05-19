# T01: 后端 focused test 与 compile

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标

验证角色成员用户查询契约不会破坏现有用户管理和角色审批链路。

## 验证

- [ ] `./mvnw -q -pl dts-admin -Dtest=AdminUserServiceListSnapshotsTest test` from `source`
- [ ] 必要时补充 `./mvnw -q -pl dts-admin -DskipTests compile` from `source`

## 完成标准

- [ ] 命令输出和结论写入 `it/evidence/backend-focused-20260519.md`。

