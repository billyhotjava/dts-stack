# 商业智能应用菜单重组验证

## 范围

- `dts-admin` portal 菜单种子从独立 `指标与语义`、`BI 分析`、`行业业务开发` 调整为统一的 `商业智能应用` 分组。
- `指标与语义` 子菜单收敛为 `指标工作台`、`指标资产`、`语义建模`、`发布与运行`。
- `BI 分析` 保留 `数据大屏`、`分析看板`、`分析卡片`、`BI 数据集`，删除旧 `项目看板`。
- `行业业务开发` 和行业子菜单从 DTS 固定 portal 菜单中移除，行业内容后续通过 app/app-pack 配置承载。

## 验证命令

```bash
jq empty source/dts-admin/src/main/resources/config/data/portal-menu-seed.json
jq empty source/dts-admin/src/main/resources/config/data/role-menu-defaults.json
jq -r '.portalNavSections[] | .key + "\t" + .title + "\t" + (.children // [] | map(.title) | join(","))' \
  source/dts-admin/src/main/resources/config/data/portal-menu-seed.json
rg -n "行业业务开发|地铁应用|项目看板|metricsSubjects|metricsObjects|metricsDesigner|metricsModels|metricsRuns|biProjectCockpit" \
  source/dts-admin/src/main/resources/config/data/portal-menu-seed.json \
  source/dts-admin/src/main/resources/config/data/role-menu-defaults.json
```

## 期望结果

- JSON 解析通过。
- portal 根菜单包含 `商业智能应用`，子分组为 `指标与语义`、`BI 分析`。
- `portal-menu-seed.json` 和 `role-menu-defaults.json` 不再包含 `行业业务开发`、`地铁应用`、`项目看板` 和旧的指标步骤型子菜单授权。
- 旧根菜单 key `bi`、`metrics`、`app-pack` 由 `PortalMenuService` 作为 legacy root 连同子树软删除，保留新 `bi-apps` 根菜单。

## 本次结果

```text
portal-menu-seed.json: pass
role-menu-defaults.json: pass

bi-apps  商业智能应用  指标与语义,BI 分析
workbench  工作台  我的概览,待办事项
resource  数据接入中心  连接器目录,数据源管理,驱动管理,元数据采集,数据入湖配置,接入变更记录
studio  数据开发中心  项目空间管理,逻辑建模（SQL）,脚本开发（Python/Spark）,任务编排,即席查询,项目文件浏览
governance  数据治理中心  主题域管理,标准管理,标准模板,质量管控,质量报告,分级分类
portal  数据资产门户  资产地图,数据搜索,资产详情,血缘与影响分析,权限申请
ops  任务运维中心  运行概览,任务实例监控,告警记录,补数管理
services  数据服务中心  数据 API 管理,数据推送,共享交换

obsolete menu keys/text: no matches
```
