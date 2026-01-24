# 平台-数据服务 (Services) 功能落地实现说明

## 定位
提供数据服务化出口，包括 API 发布、数据产品与外链。

## 菜单与页面
- API 服务：`services/api-services`
- 数据产品：`services/data-products`
- Token 管理：`services/tokens`
- BI 链接：`services/bi-links`

## 主要功能
1) API 服务
- 模型/表一键发布为 API
- 统一鉴权与调用统计

2) 数据产品
- 产品化包装(场景/负责人/服务等级)
- 版本管理与下线

3) Token 管理
- 授权应用与访问控制

4) BI 链接
- 外部系统入口统一管理

## 核心对象与数据表
- api_service / api_route
- data_product / data_product_version
- access_token / client_app

## 数据流与依赖
- dts-platform API 网关
- OIDC/Keycloak 鉴权

## 实现要点
- API 发布与权限模型绑定
- Token 生命周期与审计记录

## 边界与异常
- 未授权用户不可见服务入口。
