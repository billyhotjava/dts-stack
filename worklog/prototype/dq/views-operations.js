(function () {
  "use strict";

  window.DQPrototype = window.DQPrototype || {};
  var DQPrototype = window.DQPrototype;
  DQPrototype.views = DQPrototype.views || {};

  var fallbackRules = [
    { id: "MON-201", name: "主键重复率必须为 0", table: "dwd_order_detail", dimension: "唯一性", severity: "阻断", latestResult: "通过", trigger: "产出后触发", owner: "陈晨", subscribed: true },
    { id: "MON-208", name: "订单金额非负", table: "dwd_order_detail", dimension: "有效性", severity: "告警", latestResult: "告警", trigger: "产出后触发", owner: "陈晨", subscribed: true },
    { id: "MON-219", name: "客户编码不可为空", table: "dim_customer", dimension: "完整性", severity: "阻断", latestResult: "失败", trigger: "每日 03:30", owner: "林瑶", subscribed: false },
    { id: "MON-224", name: "到账日期不晚于业务日期", table: "dwd_payment_flow", dimension: "准确性", severity: "告警", latestResult: "通过", trigger: "每日 04:00", owner: "周齐", subscribed: false },
    { id: "MON-231", name: "日分区行数波动", table: "ads_trade_daily", dimension: "稳定性", severity: "告警", latestResult: "通过", trigger: "产出后触发", owner: "王澜", subscribed: true }
  ];

  var fallbackRuns = [
    { id: "RUN-260731-0216", ruleName: "主键重复率必须为 0", dimension: "唯一性", status: "通过", disposition: "无需处置", finishedAt: "2026-07-31 02:16:24", table: "dwd_order_detail", scope: "order_id 字段", template: "字段重复率", duration: "18 秒", rows: "8,426,190" },
    { id: "RUN-260731-0217", ruleName: "订单金额非负", dimension: "有效性", status: "告警", disposition: "处理中", finishedAt: "2026-07-31 02:17:02", table: "dwd_order_detail", scope: "pay_amount 字段", template: "字段值域校验", duration: "22 秒", rows: "8,426,190" },
    { id: "RUN-260731-0336", ruleName: "客户编码不可为空", dimension: "完整性", status: "失败", disposition: "待认领", finishedAt: "2026-07-31 03:36:18", table: "dim_customer", scope: "customer_code 字段", template: "字段空值率", duration: "11 秒", rows: "2,108,443" },
    { id: "RUN-260731-0412", ruleName: "到账日期不晚于业务日期", dimension: "准确性", status: "通过", disposition: "已关闭", finishedAt: "2026-07-31 04:12:40", table: "dwd_payment_flow", scope: "整表", template: "自定义 SQL", duration: "31 秒", rows: "3,749,820" },
    { id: "RUN-260731-0637", ruleName: "日分区行数波动", dimension: "稳定性", status: "通过", disposition: "无需处置", finishedAt: "2026-07-31 06:37:55", table: "ads_trade_daily", scope: "dt=20260731", template: "表行数波动率", duration: "8 秒", rows: "18,206" }
  ];

  var fallbackNoise = [
    { date: "2026-07-01 至 2026-07-31", attribute: "月末关账窗口", type: "业务日期", ruleType: "行数波动", creator: "周齐", createdAt: "2026-06-28 16:20", updatedAt: "2026-07-29 09:35", enabled: true },
    { date: "每周日 00:00–08:00", attribute: "例行补数窗口", type: "周期时间", ruleType: "延迟 / 空分区", creator: "陈晨", createdAt: "2026-06-12 11:08", updatedAt: "2026-07-21 14:42", enabled: true },
    { date: "2026-08-02", attribute: "客户主数据迁移", type: "业务日期", ruleType: "唯一值波动", creator: "林瑶", createdAt: "2026-07-30 18:02", updatedAt: "2026-07-30 18:02", enabled: false }
  ];

  function esc(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function list(key, fallback) {
    var value = (DQPrototype.data || {})[key];
    return Array.isArray(value) && value.length ? value : fallback;
  }

  function normalizeResult(value) {
    var text = String(value || "未运行");
    if (/pass|success|通过|正常/i.test(text)) return "通过";
    if (/fail|error|失败|阻断/i.test(text)) return "失败";
    if (/warn|告警/i.test(text)) return "告警";
    if (/running|运行中/i.test(text)) return "运行中";
    return text;
  }

  function tone(value) {
    var text = normalizeResult(value);
    if (/通过|正常|无需处置|已关闭|已启用/.test(text)) return "success";
    if (/失败|阻断|待认领|已停用/.test(text)) return "danger";
    if (/告警|处理中|待复核|待确认/.test(text)) return "warning";
    return "info";
  }

  function chip(value) {
    return '<span class="chip ' + tone(value) + '">' + esc(normalizeResult(value)) + "</span>";
  }

  function monitorTabs(active) {
    return '<div class="tabs"><button class="tab' + (active === "monitor" ? " active" : "") + '" data-route="monitor">质量监控</button><button class="tab' + (active === "runs" ? " active" : "") + '" data-route="run-records">运行记录</button><button class="tab' + (active === "noise" ? " active" : "") + '" data-route="noise">去噪管理</button></div>';
  }

  function resultFor(item, index) {
    var raw = item.status || item.latestResult || "未运行";
    if (/异常/.test(String(raw))) {
      if (/强|阻断/.test(String(item.severity || "")) || /待认领|待确认/.test(String(item.disposition || ""))) return "失败";
      return index % 2 ? "失败" : "告警";
    }
    return normalizeResult(raw);
  }

  function countStatus(items, wanted) {
    return items.filter(function (item, index) { return resultFor(item, index) === wanted; }).length;
  }

  function selectedMonitor() {
    var items = list("monitors", list("rules", fallbackRules));
    return items.find(function (item) { return String(item.id) === String(DQPrototype.selectedMonitorId || ""); }) || items[0] || fallbackRules[0];
  }

  function selectedRun() {
    var items = list("runs", fallbackRuns);
    return items.find(function (item) { return String(item.id) === String(DQPrototype.selectedRunId || ""); }) || items[0] || fallbackRuns[0];
  }

  function renderMonitor() {
    var monitors = list("monitors", list("rules", fallbackRules));
    var recentRuns = list("runs", fallbackRuns);
    var rows = monitors.map(function (item, index) {
      var result = resultFor(item, index);
      var recent = recentRuns.find(function (run) { return run.ruleId === item.id || run.ruleName === item.name; }) || recentRuns[0];
      return '<tr><td><input type="checkbox" aria-label="选择监控"></td><td><button class="link" data-route="monitor-detail" data-id="' + esc(item.id || "MON-" + (201 + index)) + '"><strong>' + esc(item.id || "MON-" + (201 + index)) + ' · ' + esc(item.name || item.ruleName) + '</strong></button><div class="cell-sub">' + esc(item.dimension || "完整性") + ' · ' + esc(item.severity || "告警") + '</div></td>' +
        '<td><strong>' + esc(item.table || "dwd_order_detail") + '</strong><div class="cell-sub">' + esc(item.dataSource || "DTS 湖仓") + ' / production</div></td><td>' + esc(item.trigger || item.schedule || "产出后触发") + '</td><td>' + esc(item.owner || "数据负责人") + '</td><td>' + chip(result) + '</td>' +
        '<td><button class="link" data-route="monitor-detail" data-id="' + esc(item.id || "MON-" + (201 + index)) + '">详情</button><button class="link" data-route="run-detail" data-id="' + esc(recent && recent.id) + '">最近运行</button><button class="more-btn" data-action="toast" data-message="已打开监控操作菜单">•••</button></td></tr>';
    }).join("");
    return '<section class="page"><header class="page-head"><div><div class="eyebrow">质量运维 / 监控对象</div><h1>质量监控</h1><p>集中查看质量监控规则、触发方式与最新检测结果。</p></div><div class="page-actions"><button class="btn ghost" data-route="noise">去噪管理</button><button class="btn primary" data-route="monitor-editor" data-new="true">新建质量监控</button></div></header>' +
      monitorTabs("monitor") +
      '<div class="stat-grid"><article class="stat-card"><span>监控对象</span><strong>' + monitors.length + '</strong><small>覆盖 4 个业务主题</small></article><article class="stat-card"><span>今日通过</span><strong>' + Math.max(countStatus(monitors, "通过"), 3) + '</strong><small class="status-good">整体通过率 97.3%</small></article><article class="stat-card warn"><span>当前告警</span><strong>' + Math.max(countStatus(monitors, "告警"), 1) + '</strong><small>8 行金额异常</small></article><article class="stat-card bad"><span>当前失败</span><strong>' + Math.max(countStatus(monitors, "失败"), 1) + '</strong><small>1 个问题待认领</small></article></div>' +
      '<div class="panel"><div class="filters"><label class="control search"><span>⌕</span><input placeholder="输入表名或监控名称"></label><label class="control search"><span>⌕</span><input placeholder="输入关键词搜索"></label><label class="control"><span>责任人</span><select><option>全部</option><option>我负责的</option></select></label><label class="control"><span>触发方式</span><select><option>全部</option><option>产出后触发</option><option>定时触发</option></select></label><label class="check"><input type="checkbox"> 我的订阅</label></div>' +
      '<div class="panel-head"><div><div class="panel-title">监控规则清单</div><div class="panel-sub">最后刷新 2026-07-31 21:08:32</div></div><button class="btn ghost" data-action="refresh">刷新</button></div>' +
      '<div class="table-wrap"><table class="data-table"><thead><tr><th><input type="checkbox" aria-label="全选"></th><th>质量监控 ID / 名称 / 描述</th><th>表名</th><th>触发方式</th><th>责任人</th><th>最新结果</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div><div class="table-footer"><span>共 ' + monitors.length + ' 条</span><span class="pager"><button disabled>‹</button><button class="current">1</button><button disabled>›</button></span></div></div></section>';
  }

  function renderMonitorDetail() {
    var item = selectedMonitor();
    var items = list("monitors", list("rules", fallbackRules));
    var index = Math.max(items.indexOf(item), 0);
    var result = resultFor(item, index);
    var abnormal = result !== "通过";
    var isFailure = result === "失败";
    var anomalyCount = isFailure ? 19 : abnormal ? 8 : 0;
    var recentRuns = list("runs", fallbackRuns);
    var recent = recentRuns.find(function (run) { return run.ruleId === item.id || run.ruleName === item.name; }) || recentRuns[0];
    var bannerClass = isFailure ? "danger" : abnormal ? "warning" : "";
    var bannerTitle = abnormal ? "最近一次检测" + result : "最近一次检测通过";
    var bannerCopy = abnormal ? "发现 " + anomalyCount + " 行异常数据，已进入质量问题处置流程。" : "本次扫描未发现异常，数据可继续进入下游链路。";
    var disposition = abnormal ? '<div class="panel"><div class="panel-head"><div><div class="panel-title">问题处置进度</div><div class="panel-sub">问题单 DQ-' + esc(String(item.id || "208").replace(/\D/g, "")) + ' · SLA 剩余 2 小时</div></div>' + chip(isFailure ? "待认领" : "处理中") + '</div><ol class="timeline"><li class="done"><b></b><div><strong>检测产生' + esc(result) + '</strong><span>系统已记录 ' + anomalyCount + ' 行异常</span></div></li><li class="' + (isFailure ? "active" : "done") + '"><b></b><div><strong>责任人认领</strong><span>' + esc(item.owner || "数据负责人") + (isFailure ? " 尚未认领" : " 已确认问题") + '</span></div></li><li class="' + (isFailure ? "" : "active") + '"><b></b><div><strong>修复数据并复核</strong><span>' + (isFailure ? "待提交处置方案" : "修复任务正在执行") + '</span></div></li><li><b></b><div><strong>关闭问题</strong><span>等待复检通过</span></div></li></ol><button class="btn primary block" data-action="dispose-problem">处置问题</button></div>' : '<div class="panel"><div class="panel-head"><div><div class="panel-title">问题处置</div><div class="panel-sub">当前规则无需处置</div></div><span class="chip success">正常</span></div><div class="empty-state"><b>本次检测通过</b><p>无质量问题，历史问题均已关闭。</p></div></div>';
    return '<section class="page"><header class="page-head"><div><button class="back-link" data-route="monitor">← 返回质量监控</button><div class="eyebrow">' + esc(item.id || "MON-AUTO") + ' / ' + esc(item.dimension || "完整性") + '</div><h1>' + esc(item.name || item.ruleName) + '</h1><p>' + esc(item.table || "数据表") + '.' + esc(item.target || item.scope || "整表") + ' · ' + esc(item.trigger || item.schedule || "产出后触发") + '</p></div><div class="page-actions"><button class="btn ghost" data-action="subscribe">订阅设置</button><button class="btn ghost" data-action="test-rule">测试规则</button><button class="btn primary" data-route="monitor-editor" data-id="' + esc(item.id) + '">编辑监控</button></div></header>' +
      '<div class="status-banner ' + bannerClass + '"><div><span class="status-mark">' + (isFailure ? "×" : abnormal ? "!" : "✓") + '</span><div><strong>' + esc(bannerTitle) + '</strong><p>' + esc(bannerCopy) + '</p></div></div>' + (abnormal ? '<button class="btn primary" data-action="dispose-problem">处置问题</button>' : '<button class="btn ghost" data-route="run-detail" data-id="' + esc(recent && recent.id) + '">查看运行详情</button>') + '</div>' +
      '<div class="stat-grid"><article class="stat-card"><span>检测结果</span><strong class="status-' + (isFailure ? "bad" : abnormal ? "warn" : "good") + '">' + esc(result) + '</strong><small>' + esc(item.updatedAt || (recent && recent.finishedAt) || "刚刚") + '</small></article><article class="stat-card"><span>异常数据</span><strong>' + anomalyCount + ' 行</strong><small>本次抽检结果</small></article><article class="stat-card"><span>最近通过率</span><strong>' + esc(item.passRate == null ? (abnormal ? "96.8" : "100") : item.passRate) + '%</strong><small>近 30 次运行</small></article><article class="stat-card"><span>执行状态</span><strong>已完成</strong><small>' + esc(item.schedule || "产出后触发") + '</small></article></div>' +
      '<div class="content-grid two"><div class="panel"><div class="panel-head"><div><div class="panel-title">监控配置</div><div class="panel-sub">规则口径与触发策略</div></div>' + chip(item.enabled === false ? "已停用" : "已启用") + '</div><div class="detail-grid vertical"><div><span>质量维度</span><strong>' + esc(item.dimension || "完整性") + '</strong></div><div><span>规则模板</span><strong>' + esc(item.template || "质量规则模板") + '</strong></div><div><span>检测范围</span><strong>' + esc(item.scope || "表级") + ' / ' + esc(item.target || "整表") + '</strong></div><div><span>校验阈值</span><strong>' + esc(item.threshold || "按模板阈值") + '</strong></div><div><span>触发方式</span><strong>' + esc(item.schedule || item.trigger || "产出后触发") + '</strong></div><div><span>问题负责人</span><strong>' + esc(item.owner || "数据负责人") + '</strong></div><div><span>通知渠道</span><strong>站内信、企业微信</strong></div></div></div>' + disposition + '</div>' +
      '<div class="panel"><div class="panel-head"><div><div class="panel-title">近 7 次运行趋势</div><div class="panel-sub">异常行数与阈值趋势</div></div><button class="link" data-route="run-records">查看全部运行记录 →</button></div><div class="trend-placeholder bars"><span style="height:18%"></span><span style="height:10%"></span><span style="height:22%"></span><span style="height:12%"></span><span style="height:14%"></span><span style="height:8%"></span><span class="' + (abnormal ? "warning" : "") + '" style="height:' + (abnormal ? "58" : "12") + '%"></span></div><div class="chart-caption"><span>07-25</span><span>07-26</span><span>07-27</span><span>07-28</span><span>07-29</span><span>07-30</span><span>07-31</span></div></div></section>';
  }

  function renderMonitorEditor() {
    var editing = Boolean(DQPrototype.selectedMonitorId);
    var item = editing ? selectedMonitor() : fallbackRules[1];
    return '<section class="page editor-page"><header class="page-head"><div><button class="back-link" data-route="monitor">← 返回质量监控</button><div class="eyebrow">' + (editing ? "编辑质量监控 / " + esc(item.id) : "新建质量监控") + '</div><h1>' + (editing ? esc(item.name || item.ruleName) : "配置检测规则") + '</h1><p>选择数据对象、检测口径和触发方式，保存后即可开始监控。</p></div><div class="page-actions"><span class="chip info">未保存</span></div></header>' +
      '<div class="editor-layout"><div class="panel"><div class="drawer-section"><div class="section-title"><b>1</b><div><strong>基本信息</strong><span>定义监控名称与责任边界</span></div></div><div class="form-grid"><label class="field full"><span>监控名称 <em>*</em></span><input value="' + esc(item.name || item.ruleName) + '"></label><label class="field"><span>质量维度 <em>*</em></span><select><option>' + esc(item.dimension || "有效性") + '</option><option>完整性</option><option>唯一性</option><option>准确性</option></select></label><label class="field"><span>重要程度 <em>*</em></span><select><option>' + esc(item.severity || "告警") + '</option><option>告警</option><option>阻断</option></select></label><label class="field"><span>问题负责人 <em>*</em></span><select><option>' + esc(item.owner || "跟随表负责人") + '</option><option>跟随表负责人</option></select></label><label class="field"><span>订阅人</span><input value="' + esc(item.owner || "数据负责人") + '、数据治理值班组"></label></div></div>' +
      '<div class="drawer-section"><div class="section-title"><b>2</b><div><strong>检测对象与规则</strong><span>指定 DTS 数据表、字段和阈值</span></div></div><div class="form-grid"><label class="field"><span>数据源 <em>*</em></span><select><option>' + esc(item.dataSource || "DTS 湖仓") + ' / production</option><option>核心交易库</option></select></label><label class="field"><span>数据表 <em>*</em></span><select><option>' + esc(item.table || "dwd_order_detail") + '</option><option>dwd_payment_flow</option></select></label><label class="field"><span>规则模板 <em>*</em></span><select><option>' + esc(item.template || "字段值域校验") + '</option><option>字段空值率</option><option>字段重复率</option></select></label><label class="field"><span>检测字段 <em>*</em></span><select><option>' + esc(item.target || "整表") + '</option><option>业务日期分区</option></select></label><label class="field"><span><i class="dot good"></i> 校验阈值</span><input value="' + esc(item.threshold || "异常行数 = 0") + '"></label><label class="field"><span><i class="dot bad"></i> 红色阈值</span><div class="compound"><select><option>&gt;</option></select><input value="20"><span>行</span></div></label><label class="field full"><span>过滤条件</span><textarea>dt = &#39;${bizdate}&#39;</textarea><small>支持 DTS 变量和目标数据源 SQL 语法。</small></label></div></div>' +
      '<div class="drawer-section"><div class="section-title"><b>3</b><div><strong>调度与通知</strong><span>设置检测触发和异常通知策略</span></div></div><div class="form-grid"><label class="field"><span>触发方式</span><select><option>' + esc(item.schedule || item.trigger || "跟随表产出节点") + '</option><option>定时触发</option><option>手工触发</option></select></label><label class="field"><span>关联产出节点</span><select><option>' + esc(item.table || "目标表") + ' · 自动关联</option></select></label><label class="field"><span>失败重试</span><select><option>间隔 5 分钟，最多 2 次</option><option>不重试</option></select></label><label class="field"><span>通知渠道</span><span class="check-line"><label><input type="checkbox" checked> 站内信</label><label><input type="checkbox" checked> 企业微信</label><label><input type="checkbox"> 邮件</label></span></label></div></div></div>' +
      '<aside class="panel preview-card"><div class="panel-head"><div><div class="panel-title">配置预览</div><div class="panel-sub">保存前确认规则语义</div></div></div><div class="rule-sentence"><span>当</span><strong>' + esc(item.table || "目标表") + '</strong><span>完成数据产出后，执行</span><strong>' + esc(item.name || item.ruleName) + '</strong><span>检测。</span></div><div class="threshold-preview"><div class="good"><span>通过</span><strong>正常阈值</strong></div><div class="warning"><span>告警</span><strong>需关注</strong></div><div class="bad"><span>失败</span><strong>需阻断</strong></div></div><div class="notice info">建议先执行一次“测试规则”，验证 SQL 可执行且扫描范围合理。</div><button class="btn ghost block" data-action="test-rule">测试规则</button></aside></div>' +
      '<footer class="wizard-actions"><button class="btn ghost" data-route="monitor">取消</button><button class="btn ghost" data-action="toast" data-message="监控配置已保存为草稿">保存草稿</button><button class="btn primary" data-action="save">保存并启用</button></footer></section>';
  }

  function renderRunRecords() {
    var runs = list("runs", fallbackRuns);
    var rows = runs.map(function (run, index) {
      var status = resultFor(run, index);
      return '<tr><td><button class="link" data-route="run-detail" data-id="' + esc(run.id) + '"><strong>' + esc(run.id) + ' · ' + esc(run.ruleName) + '</strong></button><div class="cell-sub">' + esc(run.template || "规则模板") + '</div></td><td>' + chip(run.dimension || "完整性") + '</td><td>' + chip(status) + '</td><td>' + chip(run.disposition || (status === "通过" ? "无需处置" : "待认领")) + '</td><td>' + esc(run.finishedAt) + '</td><td><strong>' + esc(run.table) + '</strong><div class="cell-sub">' + esc(run.scope || "整表") + '</div></td><td>' + esc(run.rows || "—") + '</td><td><button class="link" data-route="run-detail" data-id="' + esc(run.id) + '">查看详情</button></td></tr>';
    }).join("");
    return '<section class="page"><header class="page-head"><div><div class="eyebrow">质量运维 / 执行审计</div><h1>运行记录</h1><p>以质量监控和规则粒度查询检测结果、问题处置与扫描范围。</p></div><div class="page-actions"><button class="btn ghost" data-action="export">导出记录</button><button class="btn primary" data-action="run-now">立即检测</button></div></header>' +
      monitorTabs("runs") +
      '<div class="filters filters-wrap"><label class="control search"><span>⌕</span><input placeholder="输入规则名称"></label><label class="control search"><span>⌕</span><input placeholder="输入表名"></label><label class="control"><span>运行时间</span><input value="2026-07-24 00:00 — 2026-07-31 23:59"></label><label class="control"><span>质量结果</span><select><option>全部</option><option>通过</option><option>告警</option><option>失败</option></select></label><label class="control"><span>问题处置</span><select><option>全部</option><option>待认领</option><option>处理中</option><option>已关闭</option></select></label><label class="control"><span>质量维度</span><select><option>全部</option><option>完整性</option><option>唯一性</option><option>有效性</option></select></label><label class="check"><input type="checkbox"> 我的订阅</label><button class="btn ghost" data-action="toast" data-message="筛选条件已重置">重置</button></div>' +
      '<div class="stat-grid"><article class="stat-card"><span>运行总数</span><strong>' + runs.length + '</strong><small>当前查询周期</small></article><article class="stat-card"><span>通过</span><strong>' + countStatus(runs, "通过") + '</strong><small class="status-good">结果正常</small></article><article class="stat-card warn"><span>告警</span><strong>' + countStatus(runs, "告警") + '</strong><small>需关注但未阻断</small></article><article class="stat-card bad"><span>失败</span><strong>' + countStatus(runs, "失败") + '</strong><small>需完成问题处置</small></article></div>' +
      '<div class="panel"><div class="panel-head"><div><div class="panel-title">规则运行明细</div><div class="panel-sub">视角：规则运行 · 最近 7 天</div></div><button class="btn ghost" data-action="refresh">刷新</button></div><div class="table-wrap"><table class="data-table"><thead><tr><th>ID / 规则名称</th><th>质量维度</th><th>校验状态</th><th>问题处置</th><th>结束时间</th><th>表名 / 范围</th><th>扫描行数</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div><div class="table-footer"><span>共 ' + runs.length + ' 条</span><span class="pager"><button disabled>上一页</button><button class="current">1</button><button disabled>下一页</button></span></div></div></section>';
  }

  function renderRunDetail() {
    var run = selectedRun();
    var runs = list("runs", fallbackRuns);
    var status = resultFor(run, Math.max(runs.indexOf(run), 0));
    var abnormal = status !== "通过";
    var failed = status === "失败";
    var anomalyCount = failed ? "2,184" : abnormal ? "19" : "0";
    var disposition = run.disposition || (abnormal ? "待认领" : "无需处置");
    var bannerClass = failed ? "danger" : abnormal ? "warning" : "";
    var problemPanel = abnormal ? '<div class="panel"><div class="panel-head"><div><div class="panel-title">问题处置</div><div class="panel-sub">问题单 DQ-' + esc(String(run.id || "RUN").replace(/\D/g, "").slice(-4)) + ' · SLA 4 小时</div></div>' + chip(disposition) + '</div><ol class="timeline"><li class="done"><b></b><div><strong>检测' + esc(status) + '</strong><span>' + esc(run.finishedAt) + ' · 系统创建质量问题</span></div></li><li class="' + (/待/.test(disposition) ? "active" : "done") + '"><b></b><div><strong>责任人认领</strong><span>' + (/待/.test(disposition) ? "等待数据负责人认领" : "问题已进入处理") + '</span></div></li><li class="' + (/处理中/.test(disposition) ? "active" : "") + '"><b></b><div><strong>修复并复核</strong><span>按处置方案修复异常数据</span></div></li><li><b></b><div><strong>关闭问题</strong><span>等待复检通过</span></div></li></ol><button class="btn primary block" data-action="dispose-problem">立即认领并处置</button></div>' : '<div class="panel"><div class="panel-head"><div><div class="panel-title">问题处置</div><div class="panel-sub">本次运行无需处置</div></div><span class="chip success">已关闭</span></div><div class="empty-state"><b>检测结果正常</b><p>未创建质量问题。</p></div></div>';
    var samplePanel = abnormal ? '<div class="panel"><div class="panel-head"><div><div class="panel-title">异常样例</div><div class="panel-sub">仅展示脱敏后的前 3 行，完整数据可导出。</div></div><span class="chip info">已脱敏</span></div><div class="table-wrap"><table class="data-table"><thead><tr><th>record_id</th><th>检测对象</th><th>异常值</th><th>source_system</th><th>updated_at</th></tr></thead><tbody><tr><td>REC-928104</td><td>' + esc(run.scope || "字段级") + '</td><td><span class="bad-value">NULL / INVALID</span></td><td>DTS</td><td>2026-07-31 03:04:12</td></tr><tr><td>REC-928271</td><td>' + esc(run.scope || "字段级") + '</td><td><span class="bad-value">OUT_OF_RANGE</span></td><td>DTS</td><td>2026-07-31 03:05:44</td></tr></tbody></table></div></div>' : '<div class="panel"><div class="panel-head"><div><div class="panel-title">抽样结果</div><div class="panel-sub">本次运行未发现异常样例</div></div><span class="chip success">数据正常</span></div><div class="empty-state"><b>0 条异常数据</b><p>抽样与全量统计结果一致。</p></div></div>';
    return '<section class="page"><header class="page-head"><div><button class="back-link" data-route="run-records">← 返回运行记录</button><div class="eyebrow">' + esc(run.id) + ' / ' + esc(run.template || "质量规则") + '</div><h1>' + esc(run.ruleName) + '</h1><p>' + esc(run.table) + ' · ' + esc(run.scope || "整表") + ' · ' + esc(run.finishedAt) + ' 完成</p></div><div class="page-actions"><button class="btn ghost" data-action="export">导出' + (abnormal ? "异常数据" : "运行结果") + '</button><button class="btn ghost" data-action="run-now">重新检测</button>' + (abnormal ? '<button class="btn primary" data-action="dispose-problem">处置问题</button>' : "") + '</div></header>' +
      '<div class="status-banner ' + bannerClass + '"><div><span class="status-mark">' + (failed ? "×" : abnormal ? "!" : "✓") + '</span><div><strong>检测' + esc(status) + ' · ' + anomalyCount + ' 行异常</strong><p>' + (abnormal ? "实际值触发质量阈值，系统已记录问题处置状态。" : "检测值处于正常阈值内，可继续进入下游链路。") + '</p></div></div>' + chip(disposition) + '</div>' +
      '<div class="detail-grid"><div><span>运行 ID</span><strong>' + esc(run.id) + '</strong></div><div><span>质量维度</span><strong>' + esc(run.dimension || "完整性") + '</strong></div><div><span>数据范围</span><strong>' + esc(run.scope || "整表") + '</strong></div><div><span>扫描行数</span><strong>' + esc(run.rows || "—") + '</strong></div><div><span>检测耗时</span><strong>' + esc(run.duration || "—") + '</strong></div><div><span>执行引擎</span><strong>DTS Quality Engine</strong></div></div>' +
      '<div class="content-grid two"><div class="panel"><div class="panel-head"><div><div class="panel-title">检测判定</div><div class="panel-sub">实际值与分级阈值</div></div></div><div class="gauge-line"><div class="gauge-marker" style="left:' + (failed ? "84" : abnormal ? "66" : "24") + '%"><span>异常 ' + anomalyCount + ' 行</span></div><div class="gauge-track"><span class="good" style="width:45%"></span><span class="warning" style="width:30%"></span><span class="bad" style="width:25%"></span></div><div class="gauge-labels"><span>正常</span><span>告警</span><span>失败</span></div></div><div class="detail-grid vertical"><div><span>规则模板</span><strong>' + esc(run.template || "质量规则") + '</strong></div><div><span>实际异常数</span><strong class="status-' + (abnormal ? "bad" : "good") + '">' + anomalyCount + ' 行</strong></div><div><span>最终判定</span><strong>' + esc(status) + '</strong></div></div></div>' + problemPanel + '</div>' + samplePanel + '</section>';
  }

  function renderNoise() {
    var noise = list("noiseRules", fallbackNoise);
    var rows = noise.map(function (item) {
      return '<tr><td><strong>' + esc(item.date || item.businessTime) + '</strong><div class="cell-sub">' + esc(item.type || "业务日期") + '</div></td><td>' + esc(item.attribute || item.name || "业务去噪窗口") + '</td><td>' + esc(item.ruleType || "行数波动") + '</td><td>' + esc(item.creator || "数据负责人") + '</td><td>' + esc(item.createdAt || "—") + '</td><td>' + esc(item.updatedAt || "—") + '</td><td>' + chip(item.enabled === false ? "已停用" : "已启用") + '</td><td><button class="link" data-action="modal" data-modal="noise-editor">编辑</button><button class="link danger-link" data-action="toast" data-message="去噪规则已停用">停用</button></td></tr>';
    }).join("");
    return '<section class="page"><header class="page-head"><div><div class="eyebrow">质量运维 / 降噪策略</div><h1>去噪管理</h1><p>对可预期的业务波动设置去噪窗口，减少无效告警但保留完整运行记录。</p></div><div class="page-actions"><button class="btn primary" data-action="modal" data-modal="noise-editor">创建去噪规则</button></div></header>' + monitorTabs("noise") +
      '<div class="notice warning"><strong>去噪不会跳过检测。</strong>命中规则的异常仍会记录，只抑制指定通知或将结果降级为“已去噪”。</div>' +
      '<div class="panel"><div class="filters"><label class="control search"><span>⌕</span><input placeholder="搜索去噪名称或创建人"></label><label class="control"><span>去噪属性</span><select><option>全部</option><option>业务日期</option><option>周期时间</option><option>期望值</option></select></label><label class="control"><span>状态</span><select><option>全部</option><option>已启用</option><option>已停用</option></select></label><button class="btn ghost" data-action="refresh">刷新</button></div><div class="table-wrap"><table class="data-table"><thead><tr><th>业务时间</th><th>去噪属性</th><th>适用规则类型</th><th>创建人</th><th>创建时间</th><th>最近修改时间</th><th>是否启动</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div><div class="table-footer"><span>共 ' + noise.length + ' 条</span><span class="pager"><button disabled>上一页</button><button class="current">1</button><button disabled>下一页</button></span></div></div>' +
      '<div class="panel compact-panel"><div class="panel-head"><div><div class="panel-title">当前生效范围</div><div class="panel-sub">3 条规则覆盖 12 个监控对象</div></div></div><div class="scope-summary"><span><b>7</b> 行数波动</span><span><b>3</b> 空分区 / 延迟</span><span><b>2</b> 唯一值波动</span><span><b>0</b> 规则跳过</span></div></div></section>';
  }

  DQPrototype.views.monitor = renderMonitor;
  DQPrototype.views["monitor-detail"] = renderMonitorDetail;
  DQPrototype.views["monitor-editor"] = renderMonitorEditor;
  DQPrototype.views["run-records"] = renderRunRecords;
  DQPrototype.views["run-detail"] = renderRunDetail;
  DQPrototype.views.noise = renderNoise;

  if (typeof document !== "undefined") {
    document.addEventListener("click", function (event) {
      var target = event.target.closest("[data-route]");
      if (!target) return;
      var route = target.getAttribute("data-route");
      var id = target.getAttribute("data-id");
      if (route === "monitor-editor" && target.getAttribute("data-new") === "true") DQPrototype.selectedMonitorId = null;
      else if ((route === "monitor-detail" || route === "monitor-editor") && id) DQPrototype.selectedMonitorId = id;
      if (route === "run-detail" && id) DQPrototype.selectedRunId = id;
    });
  }
})();
