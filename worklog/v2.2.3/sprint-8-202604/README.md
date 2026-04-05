# Sprint-8: 指标中心重构

**时间**: 2026-04
**状态**: BLOCKED（依赖 Sprint-7 数据目录/元数据体系完成）
**目标**: 重构指标管理为以主题域为导航轴的完整指标中心——左树接主题域、原始/二次指标分层、导入导出对称格式、主题域详情回填指标统计。

## 阻塞原因

指标创建时 `sourceTable` / `datasetId` 需从数据目录选择数据集，当前数据目录模块不完整。
在数据目录（Sprint-7）完成前，指标与数据资产的绑定无法实现，指标中心重构挂起。

## 头脑风暴决策记录（2026-04-05）

| 决策点 | 结论 |
|--------|------|
| 整体布局 | **A — 左树+右内容**（经典资源管理器风格） |
| 域树数据源 | 来自主题域管理 `GET /api/catalog/domains/tree`，不硬编码 |
| domain 字段耦合方式 | **C — 软校验**：存 subject area code 字符串，服务层校验存在性，无 DB FK |
| 指标展示 | **C — 混合**：默认表格视图，右上角可切卡片，点击行右侧展开详情抽屉 |
| 导入/导出 | 对称 JSON 格式（与 TPL_PJM_*.json 一致），页面提供「下载格式示例」 |
| 数据资产关联 | 暂缓，等 Sprint-7 数据目录完成后接入 |

## 涉及模块

| 模块 | 改动内容 |
|------|---------|
| `IndicatorCenterPage.tsx`（新） | 左树接主题域 API、原始/二次分组、表格+卡片双视图、右侧详情抽屉 |
| `SubjectAreasPage.tsx` | 域详情面板「域级治理指标」接入真实指标统计数（总数/已发布/草稿） |
| `IndicatorTemplatePage.tsx` | 导入/导出对称，模板域过滤改用 subject area code |
| 后端 `IndicatorService` | `create/update` 加 domain code 软校验（验证存在于 catalog_domain） |
| 后端指标列表 API | 支持 `domainCode` 参数过滤 |
| 后端新增端点 | `POST /api/governance/indicator-templates/import`（batch upsert by code） |
| 后端新增端点 | `GET /api/catalog/domains/{id}/indicator-stats`（域指标统计） |

## Features（待 Sprint-7 完成后展开）

- F1: 后端 API 改造（domain 软校验 + domainCode 过滤 + 模板导入端点）
- F2: 指标中心页面重构（左树 + 右内容）
- F3: 主题域管理页面增强（域详情接指标统计）
- F4: 指标模板导入导出对称化
- F5: 数据资产关联（依赖 Sprint-7）
