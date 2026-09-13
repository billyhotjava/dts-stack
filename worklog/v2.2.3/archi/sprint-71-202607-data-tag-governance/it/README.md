# Sprint-71 集成验证证据

本目录存放**真实执行证据**，不接受空占位。参照 sprint-queue.md 2026-07-16 审计标准：
必须同时具备实现、测试、构建、数据库迁移、运行容器与浏览器证据，才判定为真正完成。

## 证据清单

| 编号 | 验证项 | 归属 | 证据形式 | 状态 |
|------|--------|------|----------|------|
| IT-01 | 三表迁移在干净库执行成功 | F1/T01 | `liquibase:update` 输出 + 表结构 dump | TODO |
| IT-02 | 标签 CRUD 与删除保护 | F1/T02 | IT 测试报告 | TODO |
| IT-03 | 预置标签包幂等安装与升级不覆盖 | F1/T03 | IT 测试报告 + 安装报告 | TODO |
| IT-04 | 打标 API 全类型覆盖（含非 dataset） | F2/T02 | IT 测试报告 | TODO |
| IT-05 | 按标签检索 AND 语义与无 N+1 | F2/T03 | IT 测试报告 + 查询计数断言 | TODO |
| IT-06 | 审计分类正确（不落未分类） | F2/T04 | 审计表查询结果 | TODO |
| IT-07 | 前端 source-contract 全通过 | F3 全部 | 测试输出 | TODO |
| IT-08 | `tsc --noEmit` + 前端 build 通过 | F3 全部 | 构建输出 | TODO |
| IT-09 | 浏览器 smoke：标签 CRUD 闭环 | F3/T01 | 截图 | TODO |
| IT-10 | 浏览器 smoke：资产打标（dataset + 非 dataset） | F3/T02 | 截图 | TODO |
| IT-11 | 浏览器 smoke：按标签筛选 + URL 分享复现 | F3/T03 | 截图 | TODO |
| IT-12 | Chrome95 兼容性验证 | F3 全部 | 截图 | TODO |
| IT-13 | 迁移 dry-run 报告 + 按批次回滚 | F4/T01 | 执行日志 + 回滚验证 | TODO |
| IT-14 | 现网 tags 字段探查统计 | F4/T01 | 统计结果（存 assets/） | TODO |

## 已知阻断风险

sprint-queue.md 的 2026-07-16 审计显示，Sprint-61~64 的浏览器 smoke 长期被
**登录 / DNS 基线问题**阻断，导致多个 sprint 停留在「实现完成、交付未完成」。

本 sprint 的 IT-09~IT-12 依赖同一套浏览器验证基线。**实施前须先确认该基线是否已恢复**；
若仍被阻断，应在 sprint 启动时即标注为 BLOCKED 并说明，而不是等到收尾才发现无法取证。
