# MDM Upstream Mock

模拟院方平台的简单服务：接受 `/api/mdm/pull`（handshake/pull），随后回调 `dts-admin` 的 `/api/mdm/receive`，推送一份全量人员+组织 JSON（multipart，文件字段名 `file`）。

## 目录
`docs/intergration/mdm/mdm-upstream-mock`

## 运行（复用 mdmdemo 的离线仓库）
在目录下执行（Win11 PowerShell）：
```powershell
mvn -s ../mdmdemo/offline-settings.xml -o "-Dmaven.repo.local=../mdmdemo/mvn" spring-boot:run "-Dspring-boot.run.arguments=--server.port=28080"
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.address=0.0.0.0 --server.port=28080"
```
Linux/WSL：
```bash
mvn -s ../mdmdemo/offline-settings.xml -o -Dmaven.repo.local=../mdmdemo/mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=28080
```

## 配置（application.yml / 环境变量）
- `MDM_UPSTREAM_MOCK_CALLBACK_URL`：回调目标，默认 `http://localhost:38012/api/mdm/receive`
- `MDM_UPSTREAM_MOCK_DATA_TYPE`：回调 dataType，默认 `full`
- `MDM_UPSTREAM_MOCK_CALLBACK_TOKEN` / `MDM_UPSTREAM_MOCK_SIGNATURE_HEADER`：回调鉴权头与值（可为空）
- `MDM_UPSTREAM_MOCK_FILE_PART` / `MDM_UPSTREAM_MOCK_FILE_PREFIX` / `MDM_UPSTREAM_MOCK_FILE_SUFFIX`
- `MDM_UPSTREAM_MOCK_SAMPLE`：样例 JSON 路径（默认类路径 `sample/orgs-users.json`）

默认样例文件结构：
```json
{
  "desp": {
    "dataRange": "9010",
    "sendTime": 1765057111
  },
  "user": [
    {
      "createTime": "1765057111",
      "deptCode": "9010",
      "diepId": "32",
      "identityCard": "510xxxx",
      "orgCode": "9010",
      "securityLevel": "3",
      "status": "1",
      "updateTime": "202512081765085911",
      "userCode": "ldgbgusd-10",
      "userName": "测试员工1"
    }
  ],
  "orgId": [
    {
      "deptCode": "9010",
      "deptName": "demo app",
      "diepId": "9a3Eaxxx",
      "orgCode": "9010",
      "parentCode": "90",
      "shortName": "十所",
      "sort": "11",
      "status": "1",
      "type": "0"
    }
  ]
}
```

## 用法
1) 启动本 mock。  
2) 对 mock 发起 pull/handshake（POST）：
```
POST http://localhost:28080/api/mdm/pull
Form-data (可选 file 或 body)：
  callbackUrl=http://<dts-admin-IP>:38012/api/mdm/receive   # 若不填，使用配置默认
  dataType=full                                             # 若不填，使用配置默认
  file=<自定义JSON文件，可选，若不传则用内置示例>
```
3) mock 收到后，会立即调用 callbackUrl，multipart 形式上传文件：  
   - 表单字段：`dataType=<同上>`  
   - 文件字段：`file`，文件名如 `mdm-full-<timestamp>.json`，内容为样例/上传 JSON  
   - 如配置了 token，则在回调头部携带 `X-Signature: <token>`（或自定义头名）

日志会输出回调状态码和响应体，便于离线调试 dts-admin 的 MDM 接口。 

## 可视化日志
启动后访问 `http://localhost:28080/`，页面提供两个表格：
- 其他系统主动调用记录（收到 /api/mdm/pull）
- 推送数据记录（回调 /api/mdm/receive 的结果）

最新记录在最上方，页面会每 5 秒自动刷新。
