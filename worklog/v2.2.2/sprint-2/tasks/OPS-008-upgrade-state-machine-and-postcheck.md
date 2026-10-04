# OPS-008: 升级状态机、启动与升级后校验

## 目标

把离线升级的多个动作串成一个单入口状态机，完成启动和最小 postcheck。

## 状态

- `PRECHECK`
- `VERIFY_OFFLINE_PACKAGE`
- `VERIFY_OLD_CONTAINERS_STOPPED`
- `ACQUIRE_LOCK`
- `BACKUP`
- `LOAD_IMAGES`
- `MERGE_ENV`
- `MERGE_COMPOSE`
- `MERGE_CONFIG`
- `SYNC_NEW_FILES`
- `WRITE_SUMMARY`
- `START_CONTAINERS`
- `POSTCHECK`

## 验收标准

- 单个命令可串起完整升级流程
- 失败状态可定位
- 成功后输出 summary
