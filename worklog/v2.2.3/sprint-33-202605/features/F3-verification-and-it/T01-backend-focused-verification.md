# T01: 后端 focused test 与 compile

**优先级**: P0
**状态**: DONE
**依赖**: F1

## 目标

验证角色成员用户查询契约不会破坏现有用户管理和角色审批链路。

## 验证

- [x] `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-admin -am -Dtest=AdminUserServiceListSnapshotsTest -Dsurefire.failIfNoSpecifiedTests=false test` from `source`
- [x] focused test 已覆盖编译路径；未另跑全模块 compile。

## 完成标准

- [x] 命令输出和结论写入 `it/evidence/backend-focused-20260519.md`。
