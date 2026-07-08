# TODO: 连接器目录内置 seed 改为 JSON 配置源

## 背景

当前连接器目录的“同步内置”功能由后端 `ConnectorRegistryService.seedBuiltInConnectors()` 执行。内置连接器定义写在 Java 代码里的 `builtInConnectors()` 方法中，点击“同步内置”后系统按 `connectorKey` 将这些定义写入或更新到 `infra_connector` 表。

当前问题：

- 新增或调整内置连接器需要改 Java 代码。
- 连接器能力、配置模板、敏感字段、兼容性等内容不适合长期硬编码。
- 如果只把定义挪到 classpath resources，仍然需要重新打包镜像，不能满足“现场只改配置文件”的目标。

期望方向：

- 将内置连接器定义改为 JSON 文件。
- 支持外部挂载 JSON，现场可通过修改配置文件扩展或调整连接器目录。
- 系统仍要校验 JSON 是否被当前版本支持，避免配置写了但页面或后端能力不支持。

## 目标

- [ ] 将连接器 seed 的定义源从 Java 硬编码迁移到 JSON。
- [ ] 保留 classpath 默认 JSON，保证无外部配置时系统仍能初始化。
- [ ] 支持外部 JSON 覆盖或扩展，避免每次新增连接器都改代码。
- [ ] 点击“同步内置”时从 JSON 读取并 upsert 到 `infra_connector` 表。
- [ ] 对 JSON 做结构校验、能力校验和兼容性校验。
- [ ] 坏配置不得清空或破坏现有连接器目录。

## 当前事实源

- 前端页面：`source/dts-platform-webapp/src/pages/foundation/ConnectorRegistryPage.tsx`
- 前端接口：`source/dts-platform-webapp/src/api/services/connectorsService.ts`
- 后端接口：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/infra/InfraConnectorResource.java`
- 当前 seed 服务：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/ConnectorRegistryService.java`
- 数据表：`source/dts-platform/src/main/resources/config/liquibase/changelog/20260429_04_infra_connector_registry.xml`

## 推荐配置文件位置

默认内置文件：

```text
source/dts-platform/src/main/resources/config/data/infra-connectors.json
```

部署覆盖文件：

```text
config/infra-connectors.json
```

推荐新增配置项：

```yaml
dts:
  infra:
    connectors:
      seed-file: config/infra-connectors.json
      external-override-enabled: true
```

读取优先级：

1. 如果配置了外部 `seed-file` 且文件存在，优先读取外部文件。
2. 外部文件不存在时，读取 classpath 默认 JSON。
3. 外部文件存在但格式错误时，返回明确错误，不回退覆盖，以免现场误以为配置已生效。

## JSON 草案

```json
[
  {
    "connectorKey": "postgresql",
    "name": "PostgreSQL",
    "category": "DATABASE",
    "sourceType": "postgres",
    "defaultEngine": "ADDAX",
    "status": "ACTIVE",
    "displayOrder": 10,
    "description": "PostgreSQL 数据库批量接入连接器",
    "capabilities": {
      "connectionTest": true,
      "schemaDiscover": true,
      "samplePreview": true,
      "fullRefresh": true,
      "append": true,
      "timestampIncremental": true,
      "primaryKeyIncremental": true,
      "cdc": false,
      "odsGeneration": true,
      "dbtSourceGeneration": true
    },
    "configSchema": {
      "required": ["jdbcUrl", "username", "password"],
      "optional": ["driverClass", "driverVersion", "schemas", "connectTimeoutSeconds", "queryTimeoutSeconds"],
      "defaults": {
        "driverClass": "org.postgresql.Driver"
      }
    },
    "sensitiveFields": ["password"],
    "compatibility": {
      "deployment": ["docker-compose", "offline"],
      "arch": ["x86_64", "arm64"],
      "driverClass": "org.postgresql.Driver"
    }
  }
]
```

## 后端任务

- [ ] 新增 `InfraConnectorSeedProperties`，读取 seed 文件路径和开关。
- [ ] 新增 JSON DTO，例如 `InfraConnectorSeedSpec`，字段与 `BuiltInConnector` 对齐。
- [ ] 新增 loader：优先外部文件，其次 classpath 默认 JSON。
- [ ] 将 `ConnectorRegistryService.builtInConnectors()` 改为从 loader 读取。
- [ ] 保留 `seedBuiltInConnectors()` 的 upsert 语义：按 `connectorKey` 新建或更新。
- [ ] 记录 seed 结果：创建数、更新数、跳过数、配置来源。
- [ ] 明确失败策略：JSON 解析失败、重复 key、必填缺失时抛出业务错误，不覆盖数据库。
- [ ] 校验 `category` 只能是当前页面支持的分类：`DATABASE`、`FILE`、`API`、`STREAM`。
- [ ] 校验 `status` 合法，默认 `ACTIVE`。
- [ ] 校验 `capabilities` 的 key 在平台已知能力集合内。
- [ ] 校验 `configSchema.required`、`configSchema.optional` 必须是字符串数组。
- [ ] 禁止在 JSON 中保存真实敏感值，只允许声明敏感字段名。

## 前端任务

- [ ] “同步内置”成功提示增加来源信息，例如“已从外部配置同步 18 个连接器”。
- [ ] 同步失败时展示明确错误，不只显示通用接口失败。
- [ ] 详情抽屉可展示配置来源字段时，考虑增加“来源：内置 / 外部配置”。
- [ ] 保持现有表格布局、能力标签和操作按钮不变。

## 测试任务

- [ ] 单元测试：classpath 默认 JSON 可加载。
- [ ] 单元测试：外部 JSON 存在时优先加载。
- [ ] 单元测试：重复 `connectorKey` 时拒绝 seed。
- [ ] 单元测试：必填字段缺失时拒绝 seed。
- [ ] 单元测试：坏 JSON 不会覆盖现有 `infra_connector` 数据。
- [ ] 单元测试：seed 后按 `connectorKey` 更新已有记录而不是新增重复记录。
- [ ] 前端 source-contract：`connectorsService.seed()` 仍调用 `/infra/connectors/seed`。
- [ ] 后端集成测试：`POST /api/infra/connectors/seed` 返回同步后的连接器列表。

## 运维与部署任务

- [ ] 在部署文档中说明默认 JSON 和外部 JSON 的位置。
- [ ] 在 `init.sh` 或环境说明中补充外部 seed 文件挂载建议。
- [ ] 明确修改外部 JSON 后的生效方式：点击“同步内置”或重启触发应用启动 seed。
- [ ] 增加回滚说明：外部 JSON 改坏时如何恢复默认内置连接器。

## 验收标准

- [ ] 不修改 Java 代码，仅修改外部 JSON 后，点击“同步内置”可以更新连接器目录。
- [ ] 外部 JSON 不存在时，系统仍使用默认内置 JSON 初始化。
- [ ] 外部 JSON 配置错误时，页面能看到明确失败原因，数据库旧数据不被破坏。
- [ ] 连接器目录仍能显示分类、能力、配置模板、敏感字段和兼容性。
- [ ] 从连接器目录创建数据源的 `connectorKey` 预选链路不受影响。
- [ ] 相关后端测试、前端 source-contract、`pnpm build` 通过。

## 风险点

- [ ] JSON 可配置不等于运行引擎真实支持；新增连接器必须确认 Addax/API/File 运行链路是否可执行。
- [ ] 配置文件热更新不是自动生效，需要通过“同步内置”或重启触发。
- [ ] 如果 seed 每次都把状态重置为 `ACTIVE`，可能覆盖现场手工停用状态；需要确认是否保留现有状态。
- [ ] 外部 JSON 如果挂载到容器内，需要确认 ARM/Kylin/离线部署路径一致。
- [ ] 连接器定义扩展字段要保持前后端兼容，避免页面 Drawer 渲染异常。

## 待决策

- [ ] 外部 JSON 是“覆盖全部内置连接器”，还是“在默认内置基础上增量合并”？
- [ ] 外部 JSON 是否允许删除默认连接器，还是只能新增/更新？
- [ ] 同步内置时是否保留数据库中已有 `status`，避免把停用连接器重新启用？
- [ ] 是否需要在页面显示 seed 来源和最近同步时间？
- [ ] 是否需要新增“校验配置”按钮，先检查 JSON 再执行写库？
