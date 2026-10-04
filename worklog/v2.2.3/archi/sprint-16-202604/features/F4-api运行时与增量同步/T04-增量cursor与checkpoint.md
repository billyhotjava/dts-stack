# T04: 增量 cursor 与 checkpoint

**优先级**: P0
**状态**: DRAFT
**依赖**: T02, F3/T03

## 目标

支持 API 增量同步的 cursor/checkpoint 机制，并保证失败恢复和重跑语义明确。

## 范围

- 支持 timestamp cursor、numeric cursor、opaque page token。
- 定义 checkpoint 保存时机：page 成功、batch 成功、task 成功。
- 支持 lookback window 和 late arriving records。
- 支持手工 reset checkpoint 和指定时间回补。

## 数据模型

落库表：`ingestion_api_checkpoint`（拟用 changeset `20260428_02_api_resource_checkpoint.xml`）。

```text
id                bigint pk
task_id           bigint  fk → ingestion_task.id
resource_key      varchar(256)  not null   -- task 下唯一，复用 ApiResourceConfig.name
cursor_type       varchar(32)   not null   -- TIMESTAMP / NUMERIC / OPAQUE_TOKEN
cursor_value      text                     -- 序列化后的 cursor 值（不明文 secret）
last_success_at   timestamp                -- 最后一次成功推进时间（不是任务结束时间）
last_run_id       bigint                   -- 最后写入的 execution id
lookback_window   varchar(32)              -- ISO-8601 duration，如 PT15M
status            varchar(16)              -- ACTIVE / RESET / DISABLED
created_at        timestamp
updated_at        timestamp
unique(task_id, resource_key)
```

写入策略：
- `page success` 增量推进 cursor（业务由 connector 决定原子点）
- `batch success` 把临时 cursor 落 `ingestion_api_checkpoint`
- `task success` 标记 `last_run_id` 与 `last_success_at`
- 失败：cursor 不推进；retry 复用 ACTIVE cursor

手工重置：`POST /api/ingestion/tasks/{id}/checkpoints/{resourceKey}/reset` 支持 `to=<timestamp>` 或 `to=initial`，写审计 + 旧 cursor 备份字段（可加 `previous_cursor_value`）。

## 完成标准

- [ ] checkpoint 原子更新，不因失败误推进 —— 验收口径：DB 事务 + connector 单测覆盖 fail-after-page、fail-after-batch、fail-after-task 三类场景。
- [ ] 增量参数能注入 query/body/header —— `ApiResourceConfig.cursor.injectionTarget` 枚举（QUERY/BODY/HEADER）+ 对应 connector 适配。
- [ ] 手工重跑不会破坏当前水位 —— reset 操作必须显式写 `previous_cursor_value` 与审计；运行时重跑（不带 reset）仅消费 `cursor_value`。
- [ ] execution history 展示 before/after checkpoint —— execution 详情返回 `cursorBefore` / `cursorAfter`（与表无强一致绑定，事件驱动）。

## 实现进展 / 关联代码

- 待办：`IngestionApiCheckpointEntity`、`IngestionApiCheckpointRepository`、`ApiCheckpointService`、`ApiHttpSourceConnector` 中 cursor 推进；reset 接口与审计事件。

