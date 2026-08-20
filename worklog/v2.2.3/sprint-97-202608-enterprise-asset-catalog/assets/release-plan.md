# 发布安全计划（Gate G3）

**变更类型**：向后兼容 API 增量 + 只读聚合服务 + UI 增量  
**风险等级**：中（目录涉及密级/部门可见性，但不改变现有写权限）

## 变更策略

- 后端为 API 可选参数和新读服务，不做数据库迁移。
- 前端在现有数据资产目录内增量增加页签、家族筛选和详情抽屉，不新增全局菜单或角色绑定。
- 未传 `assetFamily` 的旧调用继续走原 `CatalogAssetPortalService`。
- 不新增或修改 schema，不回填、不复制 owner 数据，不删除旧路由或字段。

## 兼容性

| 消费方 | 是否受影响 | 处置 |
|---|---|---|
| 现有数据表目录 | 否 | 未传 `assetFamily` 时返回原 `AssetPage` |
| 新统一资产目录 UI | 是 | 后端先发布；只有 `ALL` 或非 DATASET 家族才进入新服务 |
| 标签与资产关系 API | 否 | 继续使用 `(CatalogAssetType, CatalogAssetKey)` 和现有 guard |
| 模型、指标、BI、产品、服务 owner | 否 | 只读查询，不反写 owner 主数据 |

## 发布顺序

1. 构建并发布 `dts-platform`。
2. 验证旧 DATASET 查询兼容，再发布 `dts-platform-webapp`。
3. 运行集中 E2E；标签内置包只通过 UI 显式安装。

## 回滚

- 发布前记录两个容器的镜像 ID；回滚顺序为前端、后端，使用记录的旧镜像强制重建对应服务。
- 前端回滚即可隐藏新入口，不影响既有标签关系；后端回滚必须同时回滚前端，避免新 UI 继续发送 `assetFamily`。
- 本 Sprint 无 schema 或数据回填，回滚不删除标签字典和资产标签关系。
- 不可逆部分：E2E 经 UI 新建的标签及关联属于真实治理数据；回滚代码时不自动删除，按验收清单显式清理临时自定义标签。

## 回滚验证

- [x] 记录发布前/后的镜像 ID 和容器健康状态。
- [x] 使用旧镜像恢复两个服务，验证旧入口为 2xx。
- [x] 再部署新镜像，验证统一目录和标签页签恢复。

## 2026-08-21 实演记录

| 服务 | Sprint 前镜像 | 最终镜像 | 最终状态 |
|---|---|---|---|
| `dts-platform` | `sha256:2e6c024defb009bab004225e26014d8a868fe1ad3a9cdddc99bb3c115ea57768` | `sha256:d6d0d6508a94e253163c8274ea13df9e7a55d999b7403ffd38295275baeb2167` | healthy |
| `dts-platform-webapp` | `sha256:efe4667adee1e7b212a64466870f3db50bda90bde5563ec276b812faf8363546` | `sha256:dbda66f4fec1a996560cab4b5cac6e463289c21c61524703f88eef16c2857be7` | running，Nginx 配置检查通过 |

- 已按“前端 → 后端”恢复 Sprint 前镜像，确认镜像 ID、后端健康和首页 2xx；再按“后端 → 前端”恢复新版本。
- 最终标签只读权限补丁前的后端镜像另存为 `dts-platform:sprint97-tag-read-pre`，用于局部回滚。
- 最终部署后首页连续 6 次返回 HTTP 200；后端近 10 分钟日志无新增 ERROR/Exception 命中。
