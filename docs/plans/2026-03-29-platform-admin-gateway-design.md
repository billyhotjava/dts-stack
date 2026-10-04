# Platform Admin Gateway Design

**Sprint Mapping:** `worklog/v2.2.2/sprint-26-202603`, `F1-Platform统一Admin Gateway`

## Goal

将 `dts-platform` 与 `dts-platform-webapp` 对 `dts-admin` 的分散调用彻底收口到 `dts-platform` 后端统一 gateway 机制中，前端以后只调用 `dts-platform` 自身接口。

## Background

当前平台侧已经存在多套直连 `dts-admin` 的实现：

- 后端 client 分散在目录、认证、基础设施、工作流、菜单等多个包中
- 审计日志还以独立 proxy resource 的方式手工拼接调用
- 前端仍有多处直接读取 `adminApiBaseUrl`

这导致以下问题：

- `dts-admin` 的 URL、headers、token、超时、错误处理在多处重复
- 页面和业务服务暴露了对 `dts-admin` 拓扑的认知
- 后续新增跨系统调用时容易继续复制旧模式
- 调试时难以判断故障出在 transport、鉴权、协议还是业务映射

## Scope

本轮做“彻底重构”，但边界明确：

- 建立 `dts-platform` 统一 admin gateway 基础层
- 迁移所有当前 `dts-platform` 后端直连 `dts-admin` 的能力到 gateway
- 清理 `dts-platform-webapp` 直接访问 `dts-admin` 的调用，改为仅调用 `dts-platform`
- 保持现有前端业务路径与主要接口语义稳定，优先内部替换实现

本轮不做：

- 改造 `dts-admin` 的接口协议
- 做动态通用反向代理
- 重命名全部对外 API 路径

## Current Call Inventory

### Backend direct callers in `dts-platform`

- `service/directory/AdminDirectoryClient`
- `service/directory/AdminUserDirectoryClient`
- `service/admin/AdminAuthClient`
- `service/infra/AdminInfraClient`
- `service/workflow/AdminWorkflowConfigClient`
- `service/menu/PortalMenuClient`
- `web/rest/SecurityAuditLogProxyResource`

### Frontend direct callers in `dts-platform-webapp`

- `api/services/deptService.ts`
- `api/services/adminService.ts`
- `api/services/roleService.ts`
- `api/services/pkiService.ts`

## Design Options

### Option A: Keep scattered clients, only switch departments to platform API

优点：

- 改动最小
- 风险最低

缺点：

- 只是修一个点，没有完成架构治理
- 后续仍会继续出现新直连点

结论：不采用。

### Option B: Unified typed gateway inside `dts-platform` and migrate all current callers

优点：

- 分层清晰，前端与 admin 解耦
- 可统一 transport、headers、errors、timeouts
- 当前调用量仍可控，适合一次性收口

缺点：

- 本轮改动面大于“只修部门目录”
- 需要同步补若干平台代理接口

结论：采用。

### Option C: Generic reverse proxy

优点：

- 初看实现快

缺点：

- 类型丢失
- 安全边界模糊
- 容易把 admin 内部协议直接泄露给平台前端

结论：不采用。

## Target Architecture

### Layering

前端调用链统一变为：

`platform-webapp -> dts-platform /api/** -> admin gateway -> dts-admin`

`dts-platform` 内部新增统一 gateway 分层：

- `service/admin/gateway/support`
  - 公共 transport、URI 构建、headers、forwarded headers 传播、envelope 解包、错误包装
- `service/admin/gateway/directory`
  - 组织树、用户目录、角色目录
- `service/admin/gateway/auth`
  - 登录、刷新、登出、PKI challenge / login
- `service/admin/gateway/infra`
  - 默认数仓/数据湖/基础设施相关读取与写入
- `service/admin/gateway/workflow`
  - 工作流模板读取
- `service/admin/gateway/menu`
  - 门户菜单读取与管理
- `service/admin/gateway/audit`
  - 审计日志查询与导出

### Transport Contract

统一 transport 负责：

- 使用 `DtsAdminProperties`
- 统一 `RestTemplate` 配置与超时
- 统一 `Authorization`、`X-DTS-Service`、`X-Audit-Silent`、forwarded headers 处理
- 统一 `ApiEnvelope` 解包
- 统一 `4xx/5xx` 转换为平台内部异常
- 为日志打上 domain / endpoint 标签，方便排查

业务 domain gateway 只做：

- endpoint path 与 query/body 定义
- DTO 映射
- 必要的 domain fallback

## API Strategy

### Keep stable platform APIs where possible

以下现有平台接口保持不变，仅替换底层实现：

- `/api/directory/orgs`
- `/api/directory/users`
- `/api/directory/roles`

### Add platform-owned APIs for remaining frontend direct admin calls

为清理前端直连，本轮新增或收口这些平台接口：

- `whoAmI` 平台代理接口
- realm roles 平台接口
- PKI challenge / login 平台接口

原则：

- 前端不再读取 `adminApiBaseUrl`
- 前端 service 名字可以保留，但 base 指向 `dts-platform`
- 平台对外返回统一语义，不把 admin 内部路径和 envelope 泄露到页面层

## Migration Plan by Domain

### Directory

- 以目录为第一批落地样板
- `DirectoryResource` 改依赖新 `directory gateway`
- `deptService` 去掉直连 admin fallback
- 数据入湖任务“归属部门”改为只走平台目录接口

### Auth

- `AdminAuthClient` 改造成 `auth gateway`
- 为前端现有 `adminService` / `pkiService` 补平台代理接口
- 平台保留现有登录链路行为，不改业务语义

### Infra / Workflow / Menu / Audit

- 逐个迁入对应 typed gateway
- 原有 resource/service 继续做业务编排，不再自己做 transport
- `SecurityAuditLogProxyResource` 改为依赖 `audit gateway`

## Error Handling

- admin 不可用时，平台记录 domain + endpoint + upstream status
- 对前端统一返回平台错误语义，不直接透出 admin URL
- 对列表类接口优先保证“可观测失败”，而不是静默吞掉
- 对认证类接口保留可识别的用户态错误信息

## Testing Strategy

后端：

- transport 单测：headers、URI、envelope、错误转换
- 每个 domain gateway 至少 1 个 happy path 和 1 个 error path
- 资源层回归：目录、认证、PKI、审计、菜单、基础设施关键接口

前端：

- service contract 测试，确认不再使用 `adminApiBaseUrl`
- 受影响页面至少验证部门目录、用户目录、角色、PKI 登录入口

## Acceptance Criteria

- `dts-platform-webapp` 不再直接调用 `dts-admin`
- `dts-platform` 后端所有当前 admin 直连逻辑迁入统一 gateway 机制
- 目录类接口稳定可用，数据入湖任务“归属部门”只走平台目录接口
- worklog 中有完整 sprint / feature / task / IT 跟踪
- 可以输出后续新增 admin 对接时的统一接入规范
