# T06: 对外开放 API（含 person 统一查询代理）

**优先级**: P0
**状态**: READY
**依赖**: T04

## 目标
发布稳定的对外查询 API（按 code / name / 别名 / 时点查询），提供 openapi；为后续 platform / 填报 / 财务 / PLM / ERP 接入做准备。**person 作为统一查询入口**，内部代理 admin / Keycloak。

## 技术设计
详细方案在 F2 brainstorming 阶段产出，本文件仅占位。

核心端点：

```
# 实存主数据（5 类）
GET  /mdm/v1/projects/{code}
GET  /mdm/v1/depts/{code}
GET  /mdm/v1/subsystems/{code}
GET  /mdm/v1/suppliers/{code}
GET  /mdm/v1/pbs/{code}

# 字典
GET  /mdm/v1/dicts/{dictType}
GET  /mdm/v1/dicts/data-security-levels
GET  /mdm/v1/dicts/personnel-security-levels

# Person 统一查询代理（内部调 admin / Keycloak）
GET  /mdm/v1/persons/{username}
GET  /mdm/v1/persons?dept={deptCode}
```

Person 返回 payload 包含：`username / fullName / deptCode / personnelSecurityLevel / enabled`，不返回密码、认证细节。

## 影响范围
- 对外 REST `/mdm/v1/*`
- person 代理需要 admin 或 Keycloak 的只读 API
- 增量订阅接口预留（Kafka topic 或轮询接口）

## 验证
- [ ] 实存主数据可按 code / 别名 / 时点查询
- [ ] person 代理返回一致数据（和直查 admin 结果对比）
- [ ] 下游系统（platform 等）不再直接调 admin，全部走 MDM

## 完成标准
- [ ] openapi 文档发布，接入示例代码就位
- [ ] person 代理端点稳定
