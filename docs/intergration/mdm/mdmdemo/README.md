# MDM Demo (单回调、人员+组织同包)

独立的小型 Spring Boot 应用，用来离线验证院方 MDM 平台的交互：
- `/api/mdm/handshake`：我方向院方发起“请求推送”，失败重试/手动触发时调用。  
- `/api/mdm/receive`：院方唯一推送入口，dataType 控制行为；  
  - `dataType=sync-demand`：仅表示“准备数据”，不带文件；  
  - 其他值：multipart 携带 `file`，内容为人员+组织的全量 JSON。

## 目录与离线依赖
- 位置：`docs/intergration/mdm/mdmdemo`（不影响主工程）。  
- 本地仓库：`docs/intergration/mdm/mdmdemo/mvn`。  
- 离线 settings：`docs/intergration/mdm/mdmdemo/offline-settings.xml`（强制使用本地仓库）。

## 运行
```bash
cd docs/intergration/mdm/mdmdemo
mvn spring-boot:run
# 或 mvn package && java -jar target/mdm-demo-0.0.1-SNAPSHOT.jar
```
默认端口 `18080`，可在 `application.yml` 调整。主页 `/` 提供一个按钮可触发 handshake。

### 离线运行示例
- PowerShell（Win11）：
```
mvn -s offline-settings.xml -o "-Dmaven.repo.local=./mvn" spring-boot:run "-Dspring-boot.run.arguments=--server.port=38012"
```
- CMD（Win11）：
```
mvn -s offline-settings.xml -o "-Dmaven.repo.local=./mvn" spring-boot:run "-Dspring-boot.run.arguments=--server.port=38012"
```
- Linux/WSL：
```
mvn -s offline-settings.xml -o -Dmaven.repo.local=./mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=38012
```
离线编译示例：
```
mvn -s offline-settings.xml -o "-Dmaven.repo.local=./mvn" -DskipTests package
```

## 配置（application.yml）
- `mdm.demo.callback.url`：提供给院方的唯一回调 URL（可用 IP 直连）。  
- `mdm.demo.callback.auth-token` / `signature-header`：可选回调鉴权。  
- `mdm.demo.upstream.handshake-url` / `auth-token`：我方调用院方的握手地址与令牌。  
- `mdm.demo.registry.*`：系统编码、安全域、推送模式、数据类型声明；预置了系统名称、IP、业务/安全域、管理部门、联系人、电话、数据方案、是否定时、定时模式（全量/增量）、交换类型、备注等字段，现场直接填入即可。  
- `mdm.demo.required.users|depts`：校验用必填字段。  
- `mdm.demo.storage-path`：落盘目录（按日期/批次存储 .json）。

## 接口示例
院方回调（唯一接口，dataType + 文件）：
```
POST http://10.10.10.135:18080/api/mdm/receive
Header: X-Signature: <token-if-set>
Form-data (multipart):
  dataType=sync-demand              # 若仅通知准备数据，可不带 file
  file=<全量JSON文件>               # dataType≠sync-demand 时必须；接收后按时间戳命名存盘
                                   # JSON示例：
                                   # {"users":[{"userCode":"A10001","userName":"张三","deptCode":"D001",...}],
                                   #  "depts":[{"deptCode":"D001","deptName":"研发部","parentCode":"ROOT",...}]}
```
应用会落盘 `data/mdm/yyyyMMdd/<batch>.json`，校验必填字段，返回记录数、缺失字段、md5 等信息。

握手（我方 → 院方；按院方 multipart 风格，文件部件名 file）：
```
POST http://<handshake-url>
Form-data (multipart):
  file=<包含系统声明的 JSON 文件，示例文件名 orgItDemand<timestamp>.txt>
  systemCode=...
  callbackUrl=http://10.10.10.135:18080/api/mdm/receive
  callbackSecret=...
  securityDomain=SECRET
  pushMode=FULL
  dataTypes=users,depts
  targetNode=B   # 院方示例中的扩展参数，可继续补充
```

## MDM 环境变量速查（.env）
部署现场如需手动补齐/覆盖，可在 `.env` 中设置（与 `config/mdm/mdm-gateway.yml` 对应）：
- `DTS_MDM_GATEWAY_ENABLED`：true/false  
- `DTS_MDM_GATEWAY_STORAGE_PATH`：落盘目录，默认 `data/mdm`  
- `DTS_MDM_GATEWAY_LOG_PATH`：日志文件，默认 `logs/mdm-gateway.log`
- `DTS_MDM_GATEWAY_REGISTRY_SYSTEM_CODE` / `DATA_RANGE` / `AREA_SECURITY` / `AREA_BUSINESS` / `DATA_TYPE`
- `DTS_MDM_GATEWAY_UPSTREAM_BASE_URL` / `PULL_PATH` / `AUTH_TOKEN`
- `DTS_MDM_GATEWAY_UPSTREAM_USE_MULTIPART`（院方通常为 true）
- `DTS_MDM_GATEWAY_UPSTREAM_FILE_PART_NAME` / `FILE_PREFIX` / `FILE_SUFFIX`
- `DTS_MDM_GATEWAY_UPSTREAM_CONNECT_TIMEOUT` / `READ_TIMEOUT`
- `DTS_MDM_GATEWAY_CALLBACK_URL` / `AUTH_TOKEN` / `SIGNATURE_HEADER` / `ALLOWED_IPS`
- `DTS_MDM_GATEWAY_REQUIRED_FIELDS` / `REQUIRED_USERS` / `REQUIRED_DEPTS`
- `DTS_MDM_GATEWAY_ROOT_CODE`（缺 parentCode 时挂到的根）
- `DTS_MDM_GATEWAY_AUTO_PROVISION_USERS` / `AUTO_PROVISION_ROLES` / `AUTO_PROVISION_ENABLE_LOGIN`
- **院方 multipart 表单参数（默认值见 config/mdm/mdm-gateway.yml，可用 .env 覆盖）：**
  - `DTS_MDM_GATEWAY_UPSTREAM_TARGET_NODE_CODE`（targetNodeCode，默认 9000）
  - `DTS_MDM_GATEWAY_UPSTREAM_TARGET_DOMAIN_CODE`（targetDomainCode，默认 B）
  - `DTS_MDM_GATEWAY_UPSTREAM_TARGET_SERVICE_ID`（targetServiceId，默认 orgit）
  - `DTS_MDM_GATEWAY_UPSTREAM_SOURCE_SERVICE_ID`（sourceServiceId，默认 WXXXXXDDD）
  - `DTS_MDM_GATEWAY_UPSTREAM_APPROVAL`（approval，默认 1）
  - `DTS_MDM_GATEWAY_UPSTREAM_TITLE`（title，默认 9000）
  - `DTS_MDM_GATEWAY_UPSTREAM_DATA_TYPE`（dataType，默认 9000）
  - `DTS_MDM_GATEWAY_UPSTREAM_SECURITY_LEVEL`（securityLevel，默认 9000）
  - `DTS_MDM_GATEWAY_UPSTREAM_FILED_TYPE`（filedType，默认 9000）
  - `DTS_MDM_GATEWAY_UPSTREAM_VERSION`（version，默认 9000）

## 网络放行 38012（院方回调）
- 端口映射：Compose 已将容器 8081 暴露为宿主 38012。
- 如有 firewalld：`sudo firewall-cmd --add-port=38012/tcp --permanent && sudo firewall-cmd --reload`
- 如用 iptables（IPv4）：  
  ```
  sudo iptables -C INPUT -p tcp --dport 38012 -j ACCEPT || sudo iptables -I INPUT -p tcp --dport 38012 -j ACCEPT
  sudo iptables -C DOCKER-USER -p tcp --dport 38012 -j ACCEPT || sudo iptables -I DOCKER-USER 1 -p tcp --dport 38012 -j ACCEPT
  ```
- nftables 示例：  
  ```
  sudo nft add table inet filter 2>/dev/null || true
  sudo nft add chain inet filter input '{ type filter hook input priority 0; }' 2>/dev/null || true
  sudo nft add rule inet filter input tcp dport 38012 accept
  sudo nft add table inet docker-user 2>/dev/null || true
  sudo nft add chain inet docker-user input '{ type filter hook input priority -1; }' 2>/dev/null || true
  sudo nft add rule inet docker-user input tcp dport 38012 accept
  ```
- 验证：`ss -ltnp | grep 38012`（监听），外部 `curl -v http://<宿主IP>:38012/api/mdm/receive` 或 ping 接口验证可达。

## 说明
- 仅用于验证“单回调、人员+组织同包”的全量 JSON 是否符合协议；不做入库。  
- 保留回调落盘与必填校验，便于先对齐格式；调试通过后可将逻辑并入主工程。  
- 日志输出到控制台，可按需改为文件滚动。 
