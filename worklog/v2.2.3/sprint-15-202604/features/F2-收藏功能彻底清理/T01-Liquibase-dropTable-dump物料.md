# T01: Liquibase dropTable + dump 物料

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

先在 `assets/` 留下 `portal_user_favorite` 全表 dump 作为回滚物料，再新建 Liquibase changeset `dropTable tableName="portal_user_favorite"`，并挂到 master changelog。

## 技术设计

### 第一步：产出 dump

从联调 / staging / 生产某一侧拉 dump（建议用 staging，带一条真实场景数据校验 dump 非空）：

```bash
pg_dump -h <host> -U <user> -d <db> \
  -t portal_user_favorite \
  --data-only --column-inserts \
  -f worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_dump.sql
```

另外再补一份**结构 + 数据**的完整 dump，用于恢复表本身：

```bash
pg_dump -h <host> -U <user> -d <db> \
  -t portal_user_favorite \
  -f worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_full.sql
```

两份 dump 都要入库（敏感数据经脱敏后），并在 README 备注 checksum：

```
sha256sum worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_*.sql > assets/dump.checksum
```

### 第二步：Liquibase changeset

找到项目既有 master changelog（示例路径）：

- `source/dts-platform/src/main/resources/config/liquibase/master.xml`
- 或 `db.changelog-master.yaml` / 分片下的最新 changeset 目录

新建 `source/dts-platform/src/main/resources/config/liquibase/changelog/20260424-1000_drop-portal-user-favorite.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                                       http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.5.xsd">

  <!--
    drops the legacy portal_user_favorite table.
    Prereq: rollback requires the dump stored at
      worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_full.sql
  -->
  <changeSet id="20260424-1000-drop-portal-user-favorite" author="billy">
    <preConditions onFail="MARK_RAN">
      <tableExists tableName="portal_user_favorite"/>
    </preConditions>

    <dropTable tableName="portal_user_favorite"/>

    <rollback>
      <sqlFile path="../../../../../worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_full.sql"
               relativeToChangelogFile="true" splitStatements="true"/>
    </rollback>
  </changeSet>
</databaseChangeLog>
```

> **注意**：rollback 指向 dump 文件的相对路径在 Liquibase 里可能不稳定。更稳妥的做法是把 dump 转成 `<createTable>` + `<insert>` 的 Liquibase 语义写进 rollback 块，但这会非常啰嗦；此处采用"rollback 由运维手动跑 dump"策略，rollback 块内只写说明注释：

```xml
<rollback>
  <!-- Manual: run worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_full.sql -->
  <sql>SELECT 1; -- placeholder; operators must run the dump manually</sql>
</rollback>
```

明确注释优先，避免 Liquibase 自动化回滚失真。

### 第三步：挂到 master

在 master changelog 追加 `<include file="config/liquibase/changelog/20260424-1000_drop-portal-user-favorite.xml" relativeToChangelogFile="false"/>`（路径按项目约定调整）。

## 影响范围

- `worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_dump.sql`（新增）
- `worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_full.sql`（新增）
- `worklog/v2.2.3/sprint-15-202604/assets/dump.checksum`（新增）
- `source/dts-platform/src/main/resources/config/liquibase/changelog/20260424-1000_drop-portal-user-favorite.xml`（新增）
- `source/dts-platform/src/main/resources/config/liquibase/master.xml`（或同等 master 文件，追加 include）

## 验证

- [ ] `./mvnw -pl source/dts-platform liquibase:update -Pdev` 本地执行通过，`portal_user_favorite` 表被删除。
- [ ] `./mvnw -pl source/dts-platform liquibase:rollbackCount -Dliquibase.rollbackCount=1 -Pdev` 运行后 changeset 被标记回滚（验证 changelog 机制；真正恢复数据需手动跑 dump）。
- [ ] dump 文件不包含生产敏感字段（`user_login` 是登录名，属于内部标识符，脱敏策略按项目合规要求处理）。
- [ ] `assets/dump.checksum` 与仓库内两个 `.sql` 文件实际 hash 匹配。

## 完成标准

- [ ] Liquibase update 在 fresh DB 上成功执行：表不存在 → `MARK_RAN` 正确触发。
- [ ] master changelog 已 include 新 changeset。
- [ ] dump 与 checksum 文件齐备，README（Sprint 级）记录了操作人与时间。
- [ ] 已在团队群 / PR 描述里留下"**运维请保留此 dump 至少 90 天**"的交付备注。
