# T02: legacy compose 单独合并策略

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标
确保 legacy 模式不被错误地按 `docker-compose.yml + docker-compose-app.yml` 组合逻辑处理。

## 技术设计
- legacy 模式单独处理：
  - 只读取旧目录 `docker-compose.legacy.yml`
  - 只与新包 `docker-compose.legacy.yml` 做合并
  - 不在客户现场重新从 `docker-compose.yml` / `docker-compose-app.yml` 生成 legacy 文件
- normal 模式继续按 `docker-compose.yml` / `docker-compose-app.yml` 组合处理，但这条规则不回流到 legacy
- 合并基线：
  - 以新包 `docker-compose.legacy.yml` 的结构为主
  - 以旧目录 `docker-compose.legacy.yml` 的运行值为主
- 旧值优先保留的字段：
  - `environment`
  - `ports`
  - `volumes`
  - `extra_hosts`
  - `hostname`
  - `container_name`
  - `labels`
  - `networks` 中的别名和固定地址
- 新包新增的 service/volume/network 自动补入
- 新包已删除的旧 service 默认不自动删，写入升级摘要，交由人工确认
- legacy 升级后的启动和停机都以 `docker-compose.legacy.yml` 为唯一运行态主文件

## 影响范围
- `docker-compose.legacy.yml`
- `docker-compose.yml`
- `docker-compose-app.yml`
- `start.sh`
- `stop.sh`
- compose merge helper
- 升级 summary

## 验证
- [ ] legacy compose 中现场改动能保留
- [ ] normal/legacy 不混用启动文件
- [ ] 新增 service 能自动补入 legacy 文件
- [ ] 删除 service 不会被静默移除

## 完成标准
- [ ] legacy compose 成为独立升级分支
- [ ] legacy 现场自定义运行值不会因新包覆盖丢失
