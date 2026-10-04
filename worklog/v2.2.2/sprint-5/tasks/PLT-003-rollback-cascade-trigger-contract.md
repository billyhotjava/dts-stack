# PLT-003：回滚后二次重建触发契约收口

## 目标

保证 rollback 场景中 dbt rebuild 触发失败时，不会被静默吞掉。

## 主要文件

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/RollbackCascadeService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/RollbackProxyResource.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/`

## 交付标准

- 失败传播策略明确
- 接口返回能反映 rebuild 是否真正触发
- 对应测试覆盖主流程成功/重建失败场景
