# T01：Analysis 受控导出

**优先级**：P0  **状态**：READY  **依赖**：F1

## 技术设计

- PlatformPermissionFilter 继续将 `/analysis/{id}/...` 映射为 CARD，并以 `/query/csv|xlsx` 判定 EXPORT。
- Resource 取得 active actor 和 Analysis spec；QueryGateway 重新执行；QueryExportService 流式写出。
- 导出前调用既有 card classification seal；拒绝/缺失分别 403/409。
- 前端只对已保存 Analysis 开放，显示 loading/error，成功从 Content-Disposition 下载。

## RED→GREEN

- [ ] resource test：未认证、合法 CSV/XLSX、密级拒绝、文件头。
- [ ] permission filter contract：export action。
- [ ] API client/unit + Playwright download。
