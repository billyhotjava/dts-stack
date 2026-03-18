# OPS-005: `docker-compose*.yml` 旧值优先合并

## 目标

以新包 compose 为结构基线，同时保留旧现场中的关键自定义值。

## 规则

- 新 service / volume / network 自动补入
- 旧环境自定义字段优先：
  - `environment`
  - `ports`
  - `volumes`
  - `extra_hosts`
  - `hostname`
  - `container_name`
  - `labels`
  - `networks` 自定义地址/别名
- 新包删除的旧 service 默认保留并记日志

## 验收标准

- 新旧 compose 可自动合并
- 关键现场配置不丢失
- 遗留 service 会被显式记录
