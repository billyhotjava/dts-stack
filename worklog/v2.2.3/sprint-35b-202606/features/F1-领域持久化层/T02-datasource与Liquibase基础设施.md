# T02: datasource / Liquibase / pom 基础设施（mirror 同仓服务）

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

为 dts-metrics 接入数据库基础设施，约定与同仓服务一致。

## 技术设计

- pom.xml：postgresql 依赖从 `test` scope 提为 runtime（并去重当前重复声明的两条）；新增 `spring-boot-starter-data-jpa`、`liquibase-core`、`testcontainers`（test）。
- application.yml：新增 `spring.datasource` + `spring.jpa` + `spring.liquibase`，env 变量命名 mirror dts-platform（如 `DTS_METRICS_DB_URL` 等），默认指向同仓 `services/dts-pg`，**dts-metrics 用独立 schema/database**（事实源隔离）。
- Liquibase master changelog + 首个 changeset（建 T01 四张表）。
- docker-compose（dev / app / legacy 三份）与 init.sh：按 dts-platform 模式补 dts-metrics 的 DB 连接 env 与 depends_on（若 dts-pg 已存在则复用实例）。

## 影响范围

- `source/dts-metrics/pom.xml`、`application.yml`、新增 `resources/config/liquibase/`。
- `docker-compose-app.yml` / `docker-compose.dev.yml` / `docker-compose.legacy.yml`、`init.sh`（DB 初始化片段）。

## 验证
- [ ] 本地 `./mvnw -pl dts-metrics -am compile` 通过。
- [ ] 服务启动时 Liquibase 自动建表，无手工 DDL。
- [ ] 数据库连接参数全部外部化，无硬编码密码。

## 完成标准
- [ ] dts-metrics 拥有独立 schema 与 Liquibase 管理的表，约定与 dts-platform 一致。
