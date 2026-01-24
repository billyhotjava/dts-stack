# 平台-安全与审批 (Security) 功能落地实现说明

## 定位
统一数据访问审批与数据安全策略入口。

## 菜单与页面
- 数据集访问审批：`security/dataset-access-approval`
- 数据安全策略：`security/data-security`

## 主要功能
1) 数据集访问审批
- 申请、审批、撤回
- 与工作台待办联动

2) 数据安全策略
- 敏感字段标识、脱敏规则、访问范围

## 核心对象与数据表
- dataset_access_request / dataset_access_task
- data_security_policy / data_security_rule

## 数据流与依赖
- 平台审批流引擎
- 资产权限系统

## 实现要点
- 审批状态需同步至资产权限
- 审计日志必须完整记录

## 边界与异常
- 未授权用户只可提交申请，不可查看审批明细。
