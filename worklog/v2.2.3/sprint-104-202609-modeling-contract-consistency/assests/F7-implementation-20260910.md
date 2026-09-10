# F7 编码续作与验证（2026-09-10）

本次按用户“开始 F7 的编码实现工作”继续既有提交，未重做已通过的原子初始化；核对 T40–T43 后补齐默认上下文读写链路和业务维度权限遗漏。

## 已实现的增量

1. 默认上下文读写一致：新增 `GET /api/modeling/model-specs/creation-context`，响应 `{planId: UUID|null}`、`Cache-Control: no-store`；租户固定取服务端配置。与首次保存共用同一个默认记录查询，GET 不创建记录，只读状态在写入时检查。
2. 前端新建不继承旧规划：来源加载读取服务端默认上下文；五类 ModelSpec 新建草稿 planId 留空，保存由服务端解析。旧模型恢复、更新和 API 显式上下文继续保留既有身份。来源绑定按规划隔离，因此不能从任意旧规划加载来源再向默认规划保存。
3. 业务维度菜单准入：工作台“新增业务维度”走独立 DimensionDefinitionResource，原 CATALOG_MAINTAINERS 会挡住普通菜单用户；已对齐认证准入，继续复用域可见性端口及原 ETag/状态/字段验证。

## 提交与工作区

- `12643f516`：冻结默认上下文读取增量契约。
- `3141b2951748caeb84c4539f1d4a8fe25a27e31b`：读写一致性与新建入口实现。
- `f678d6b9eea8c5ad31656821a22879784a012de6`：业务维度权限与方法权限测试。
- `fc4654ab83c924eb61e38ebb6f95810852e4757c`：修复原未纳入编译的业务维度测试数据（View 缩写、列表参数），并覆盖第五个写入口 delete。
- 共享工作区中，本次先写的两个前端 RED 测试被同期 `4c58ce207` 提交收录；没有撤销该提交或改动其质量 SQL 实现。实现文件随后独立提交。
- GitNexus 本轮返回 LOW；新增初始化服务暂未索引，按现有三个创建资源和新增只读接口核对实际调用。增量一次聚焦检查覆盖读写选择规则、缺省/显式 ID、只读与来源绑定、菜单权限；未改迁移、模型版本、来源有效性或 dbt 构建逻辑。

## 已执行验证

| 范围 | 提交 | 结果 | 日志 |
|---|---|---|---|
| RED：旧规划误入新建草稿 | 12643f516 | 9 失败 / 64 通过，命中所修复问题 | `/tmp/f7-default-context-red.log` |
| 前端定向回归 | 3141b2951 | 87/87 PASS，4 文件 | `/tmp/f7-default-context-frontend.log` |
| 后端主回归 | 3141b2951 | 58/58 PASS，无跳过；其中真实 PG 9 项 | `/tmp/f7-default-context-backend.log` |
| 前端正式源码构建 | 3141b2951 | PASS，TypeScript + Chrome95 target，2m12s | `/tmp/f7-default-context-build.log` |
| 业务维度首次专项 | f678d6b9e | 测试编译失败：旧 View 数据缺少 abbreviation；已修复测试数据 | `/tmp/f7-business-dimension-tests.log` |
| 业务维度补充专项 | fc4654ab8 | 29/29 PASS，无跳过，BUILD SUCCESS | `/tmp/f7-business-dimension-tests-green.log` |

补充专项包含 `DimensionDefinitionApplicationServiceTest` 19 项、`ModelSpecDomainWriteAccessAdapterTest` 3 项、`DimensionDefinitionResourceTest` 7 项。MVC 以 EMPLOYEE 用户执行创建、更新、确认、停用、删除；方法安全代理拒绝匿名创建，租户、严格解码、强 ETag 和业务错误映射继续通过。主回归与补充专项有 3 项域访问测试重叠，合计 84 个不同后端用例；不将两组直接相加。

全部执行在 clean `/opt/prod/s10/deploy`，源代码先在开发目录提交推送，再快进并核对 SHA。Maven 使用仓库正式镜像及同一 workspace flock；已有锁文件在 /tmp 的创建方式被系统拒绝后，改为只读打开现有文件描述符取得同一排他锁，未改文件权限或绕过锁。前端构建后仅变更 Java/文档，不重复构建。

## T43 仍需完成

本轮未生成正式镜像/离线包，未执行容器部署或真实页面验收。新只读 API 和前端必须按同一交付提交发布，先确保后端接口就绪再切换前端；沿用现有 Compose 项目和正式包流程。空业务库的整库迁移、普通菜单用户首次 DWD/DIM 保存与后续编辑、页面错误恢复及 Chrome95 分项验收继续由 IT-44–47 跟踪。源码和专项通过不能将 F7/S10DC-80 标为 DONE。
