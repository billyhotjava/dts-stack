# F2: 配置与数据库平滑升级

**优先级**: P0
**状态**: DONE

## 目标
围绕 `.env`、compose、`config/` 和 `services/dts-pg/data` 建立“旧值优先、数据不覆盖、可回滚”的平滑升级策略。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | `.env` 旧值优先合并规则 | P0 | DONE | - |
| T02 | legacy compose 单独合并策略 | P0 | DONE | - |
| T03 | PostgreSQL 数据目录兼容与保护 | P0 | DONE | - |
| T04 | 结构化配置与冲突备份策略 | P1 | DONE | T01 |

## 完成标准
- [x] `.env` 保留现场定制并补入新变量
- [x] legacy 模式单独处理 `docker-compose.legacy.yml`
- [x] `services/dts-pg/data` 不被覆盖，并有兼容性检查
- [x] `config/` 冲突可追踪、可恢复
