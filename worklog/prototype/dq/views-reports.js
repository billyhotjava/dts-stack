(function () {
  "use strict";

  window.DQPrototype = window.DQPrototype || {};
  var DQPrototype = window.DQPrototype;
  DQPrototype.views = DQPrototype.views || {};

  var fallbackReports = [
    { id: "RPT-1001", name: "核心数据质量日报", cycle: "每日 08:30", subscribers: 12, status: "已发布", updatedAt: "2026-07-31 08:32", createdAt: "2026-06-18 14:20", owner: "数据治理组", channel: "企业微信 / 邮件", scope: "核心交易、客户、资金" },
    { id: "RPT-1006", name: "交易主题质量周报", cycle: "每周一 09:00", subscribers: 8, status: "已发布", updatedAt: "2026-07-28 09:01", createdAt: "2026-06-25 10:16", owner: "陈晨", channel: "企业微信", scope: "交易主题" },
    { id: "RPT-1012", name: "经营分析质量月报", cycle: "每月 1 日 10:00", subscribers: 5, status: "草稿", updatedAt: "2026-07-30 17:44", createdAt: "2026-07-29 11:08", owner: "王澜", channel: "邮件", scope: "ADS 经营分析" }
  ];

  var fallbackDimensions = [
    { name: "完整性", score: 96.4, rules: 28, pass: 25, warning: 2, failed: 1 },
    { name: "唯一性", score: 99.8, rules: 16, pass: 16, warning: 0, failed: 0 },
    { name: "有效性", score: 97.2, rules: 23, pass: 21, warning: 2, failed: 0 },
    { name: "准确性", score: 94.7, rules: 12, pass: 10, warning: 1, failed: 1 },
    { name: "稳定性", score: 98.5, rules: 18, pass: 17, warning: 1, failed: 0 },
    { name: "一致性", score: 97.9, rules: 15, pass: 14, warning: 1, failed: 0 }
  ];

  function esc(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function reports() {
    var value = (DQPrototype.data || {}).reports;
    return Array.isArray(value) && value.length ? value : fallbackReports;
  }

  function dimensions() {
    var value = (DQPrototype.data || {}).dimensions;
    return Array.isArray(value) && value.length ? value : fallbackDimensions;
  }

  function number(value, fallback) {
    var parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function tone(value) {
    var text = String(value || "");
    if (/发布|启用|订阅中|正常|通过/.test(text)) return "success";
    if (/失败|停用|未订阅/.test(text)) return "danger";
    if (/告警|草稿|待发布/.test(text)) return "warning";
    return "info";
  }

  function chip(value) {
    return '<span class="chip ' + tone(value) + '">' + esc(value || "—") + "</span>";
  }

  function selectedReport() {
    var items = reports();
    return items.find(function (item) { return String(item.id) === String(DQPrototype.selectedReportId || ""); }) || items[0] || fallbackReports[0];
  }

  function renderReport() {
    var items = reports();
    var featured = items[0] || fallbackReports[0];
    var rows = items.map(function (item) {
      return '<tr><td><button class="link" data-route="report-preview" data-id="' + esc(item.id) + '"><strong>' + esc(item.name) + '</strong></button><div class="cell-sub">' + esc(item.id || "RPT-AUTO") + ' · ' + esc(item.scope || "全部数据域") + '</div></td><td>' + esc(item.createdAt || item.updatedAt || "—") + '</td><td>' + esc(item.owner || "数据治理组") + '</td><td>' + esc(item.cycle || "按需发送") + '</td><td><strong>' + esc(item.subscribers == null ? 0 : item.subscribers) + '</strong> 人<div class="cell-sub">' + esc(item.channel || "站内信") + '</div></td><td>' + chip(item.status || "草稿") + '</td>' +
        '<td><button class="link" data-route="report-preview" data-id="' + esc(item.id) + '">预览</button><button class="link" data-route="report-editor" data-id="' + esc(item.id) + '">编辑</button><button class="link" data-action="report-subscribe">订阅</button></td></tr>';
    }).join("");
    var subscribed = items.reduce(function (sum, item) { return sum + number(item.subscribers, 0); }, 0);
    return '<section class="page"><header class="page-head"><div><div class="eyebrow">质量分析 / 分发与复盘</div><h1>质量报告</h1><p>将检测结果、问题处置与质量趋势编排为可订阅的周期报告。</p></div><div class="page-actions"><button class="btn ghost" data-action="report-subscribe">我的订阅</button><button class="btn primary" data-route="report-editor" data-new="true">新建报告模板</button></div></header>' +
      '<div class="stat-grid"><article class="stat-card"><span>报告模板</span><strong>' + items.length + '</strong><small>日报、周报与月报</small></article><article class="stat-card"><span>有效订阅</span><strong>' + subscribed + '</strong><small>覆盖 4 个业务团队</small></article><article class="stat-card"><span>本月发送</span><strong>52 次</strong><small class="status-good">成功率 100%</small></article><article class="stat-card"><span>待发布草稿</span><strong>' + items.filter(function (item) { return /草稿/.test(item.status || ""); }).length + '</strong><small>最近编辑 17:44</small></article></div>' +
      '<div class="content-grid report-showcase"><article class="report-hero"><div class="report-cover-mark">DQ</div><span class="eyebrow">推荐模板</span><h2>' + esc(featured.name) + '</h2><p>每天汇总关键数据对象的规则运行结果、待处置问题和质量趋势，让治理状态及时送达。</p><div class="report-meta"><span>' + esc(featured.cycle || "按需发送") + '</span><span>112 条规则</span><span>' + esc(featured.subscribers || 0) + ' 位订阅人</span></div><div class="page-actions"><button class="btn light" data-route="report-preview" data-id="' + esc(featured.id) + '">查看最新一期</button><button class="btn inverted" data-action="report-subscribe">订阅报告</button></div></article>' +
      '<div class="panel report-guide"><div class="panel-head"><div><div class="panel-title">报告如何工作</div><div class="panel-sub">模板编排、定时生成、按渠道分发</div></div></div><ol class="compact-steps"><li><b>01</b><div><strong>选择范围</strong><span>数据域、数据表与质量维度</span></div></li><li><b>02</b><div><strong>编排内容</strong><span>摘要、趋势、问题与处置进度</span></div></li><li><b>03</b><div><strong>设置订阅</strong><span>周期、接收人和通知渠道</span></div></li></ol></div></div>' +
      '<div class="panel"><div class="panel-head"><div><div class="panel-title">报告模板管理</div><div class="panel-sub">管理模板、发送周期和订阅人</div></div><button class="btn ghost" data-action="refresh">刷新</button></div><div class="filters"><label class="control search"><span>⌕</span><input placeholder="输入报告名称搜索"></label><label class="control"><span>发送周期</span><select><option>全部</option><option>每日</option><option>每周</option><option>每月</option></select></label><label class="control"><span>状态</span><select><option>全部</option><option>已发布</option><option>草稿</option></select></label></div><div class="table-wrap"><table class="data-table"><thead><tr><th>报告模板名称</th><th>创建时间</th><th>创建人</th><th>发送周期</th><th>订阅人</th><th>状态</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div></div></section>';
  }

  function renderReportEditor() {
    var editing = Boolean(DQPrototype.selectedReportId);
    var report = editing ? selectedReport() : fallbackReports[0];
    return '<section class="page editor-page"><header class="page-head"><div><button class="back-link" data-route="report">← 返回质量报告</button><div class="eyebrow">报告模板 / ' + (editing ? "编辑 " + esc(report.id) : "新建") + '</div><h1>' + (editing ? esc(report.name) : "新建报告模板") + '</h1><p>配置统计范围、内容模块与定时分发策略。</p></div><div class="page-actions">' + chip(report.status || "草稿") + '<button class="btn ghost" data-route="report-preview" data-id="' + esc(report.id) + '">预览</button><button class="btn primary" data-action="publish">保存并发布</button></div></header>' +
      '<div class="editor-layout report-editor"><div class="panel"><div class="drawer-section"><div class="section-title"><b>1</b><div><strong>基本信息</strong><span>用于模板清单和报告封面</span></div></div><div class="form-grid"><label class="field full"><span>报告名称 <em>*</em></span><input value="' + esc(report.name) + '"></label><label class="field full"><span>报告说明</span><textarea>汇总' + esc(report.scope || "核心数据域") + '的质量检测结果，重点跟踪阻断问题与 SLA 处置进度。</textarea></label><label class="field"><span>统计周期 <em>*</em></span><select><option>' + esc(report.cycle || "自然日") + '</option><option>自然周</option><option>自然月</option></select></label><label class="field"><span>数据时区</span><select><option>Asia/Shanghai (UTC+8)</option></select></label></div></div>' +
      '<div class="drawer-section"><div class="section-title"><b>2</b><div><strong>统计范围</strong><span>选择报告覆盖的数据对象和质量维度</span></div></div><div class="form-grid"><label class="field full"><span>数据域 <em>*</em></span><div class="tag-selector"><span>核心交易 ×</span><span>客户主题 ×</span><span>资金主题 ×</span><button data-action="toast" data-message="数据域选择器已打开">＋ 添加</button></div></label><label class="field full"><span>数据对象</span><select><option>范围内全部生产表（28 张）</option><option>仅关键数据表</option><option>手工选择</option></select></label><label class="field full"><span>质量维度</span><span class="check-line wrap"><label><input type="checkbox" checked> 完整性</label><label><input type="checkbox" checked> 唯一性</label><label><input type="checkbox" checked> 有效性</label><label><input type="checkbox" checked> 准确性</label><label><input type="checkbox" checked> 稳定性</label><label><input type="checkbox" checked> 一致性</label></span></label></div></div>' +
      '<div class="drawer-section"><div class="section-title"><b>3</b><div><strong>内容编排</strong><span>拖动模块可调整最终报告顺序</span></div></div><div class="module-list"><label><span class="drag">⠿</span><input type="checkbox" checked><span><strong>质量总览</strong><small>质量分、通过率与运行数量</small></span><span class="chip info">必选</span></label><label><span class="drag">⠿</span><input type="checkbox" checked><span><strong>质量趋势</strong><small>近 7 个周期的质量分变化</small></span><span class="chip success">已启用</span></label><label><span class="drag">⠿</span><input type="checkbox" checked><span><strong>维度分析</strong><small>按完整性、唯一性等维度拆分</small></span><span class="chip success">已启用</span></label><label><span class="drag">⠿</span><input type="checkbox" checked><span><strong>重点问题与处置</strong><small>失败、告警及问题 SLA</small></span><span class="chip success">已启用</span></label><label><span class="drag">⠿</span><input type="checkbox"><span><strong>规则明细附件</strong><small>随报告附加全部运行明细 CSV</small></span><span class="chip info">可选</span></label></div></div>' +
      '<div class="drawer-section"><div class="section-title"><b>4</b><div><strong>发送与订阅</strong><span>报告生成后按指定渠道推送</span></div></div><div class="form-grid"><label class="field"><span>发送周期 <em>*</em></span><select><option>每日</option><option>每周一</option><option>每月 1 日</option></select></label><label class="field"><span>发送时间 <em>*</em></span><input type="time" value="08:30"></label><label class="field full"><span>订阅人</span><div class="tag-selector"><span>数据治理组 ×</span><span>交易域值班组 ×</span><span>林瑶 ×</span><button data-action="report-subscribe">＋ 添加订阅人</button></div></label><label class="field full"><span>分发渠道</span><span class="check-line"><label><input type="checkbox" checked> 企业微信</label><label><input type="checkbox" checked> 邮件</label><label><input type="checkbox"> 站内信</label></span></label><label class="field full"><span>异常升级</span><label class="check"><input type="checkbox" checked> 报告中存在阻断问题时，同时通知数据平台主管</label></label></div></div></div>' +
      '<aside class="panel editor-aside"><div class="panel-head"><div><div class="panel-title">模板摘要</div><div class="panel-sub">发布前检查配置完整性</div></div></div><div class="report-mini-cover"><span>DTS · DATA QUALITY</span><strong>' + esc(report.name) + '</strong><small>2026-07-31 · 生产环境</small><div class="mini-score"><b>97.6</b><span>质量分</span></div></div><div class="checklist"><div class="done"><b>✓</b><span>已选择 3 个数据域、28 张表</span></div><div class="done"><b>✓</b><span>已启用 4 个内容模块</span></div><div class="done"><b>✓</b><span>' + esc(report.subscribers || 0) + ' 位订阅人，' + esc(report.channel || "站内信") + '</span></div><div class="done"><b>✓</b><span>发送周期：' + esc(report.cycle || "待设置") + '</span></div></div><button class="btn ghost block" data-route="report-preview" data-id="' + esc(report.id) + '">预览最新数据</button></aside></div></section>';
  }

  function dimensionRows(items) {
    return items.map(function (item) {
      var score = number(item.score == null ? item.passRate : item.score, 0);
      var total = number(item.rules || item.total, 0);
      var passed = number(item.pass || item.passed, Math.round(total * score / 100));
      var warning = number(item.warning || item.warnings, Math.max(total - passed, 0));
      var failed = number(item.failed || item.fail, 0);
      return '<tr><td><strong>' + esc(item.name || item.dimension) + '</strong></td><td><div class="score-cell"><b>' + score.toFixed(1) + '</b><span class="score-track"><span style="width:' + Math.min(score, 100) + '%"></span></span></div></td><td>' + total + '</td><td><span class="status-good">' + passed + '</span></td><td><span class="status-warn">' + warning + '</span></td><td><span class="status-bad">' + failed + '</span></td></tr>';
    }).join("");
  }

  function renderReportPreview() {
    var data = DQPrototype.data || {};
    var report = selectedReport();
    var summary = data.summary || {};
    var runSummary = data.runSummary || {};
    var dims = dimensions();
    var runItems = Array.isArray(data.runs) && data.runs.length ? data.runs : [];
    var ruleItems = Array.isArray(data.rules) && data.rules.length ? data.rules : [];
    var issueRun = runItems.find(function (item) { return item.status !== "通过"; }) || {};
    var issueMonitor = ruleItems.find(function (item) { return item.latestResult !== "通过"; }) || {};
    var score = number(summary.score || summary.qualityScore || summary.passRate, 97.6);
    var total = number(summary.totalRules || summary.ruleRuns || summary.checksToday, 112);
    var passed = number(summary.passed == null ? runSummary.passed : summary.passed, 105);
    var warning = number(summary.warning || summary.warnings || runSummary.warning, 5);
    var failed = number(summary.failed == null ? runSummary.failed : summary.failed, 2);
    return '<section class="page report-preview"><header class="page-head preview-toolbar"><div><button class="back-link" data-route="report">← 返回质量报告</button><div class="eyebrow">报告预览 / ' + esc(report.id || "RPT-AUTO") + '</div><h1>' + esc(report.name) + '</h1></div><div class="page-actions">' + chip(report.status || "草稿") + '<button class="btn ghost" data-action="export">导出 PDF</button><button class="btn ghost" data-action="report-subscribe">订阅设置</button><button class="btn primary" data-route="report-editor" data-id="' + esc(report.id) + '">编辑模板</button></div></header>' +
      '<article class="report-paper"><section class="report-masthead"><div><span class="report-brand">DTS · DATA QUALITY</span><h2>' + esc(report.name) + '</h2><p>生产环境 · 统计周期 2026-07-30 00:00—23:59</p><div class="report-tags"><span>' + esc(report.scope || "全部数据域") + '</span><span>' + esc(report.owner || "数据治理组") + '</span></div></div><div class="quality-seal"><span>质量分</span><strong>' + score.toFixed(1) + '</strong><small class="status-good">较昨日 +0.8</small></div></section>' +
      '<section class="report-section"><div class="report-section-head"><div><span>01 / EXECUTIVE SUMMARY</span><h3>今日质量总览</h3></div><div class="subscription-state"><span class="live-dot"></span><strong>' + (number(report.subscribers, 0) > 0 ? "订阅中" : "未订阅") + '</strong><small>' + esc(report.cycle || "按需发送") + ' · ' + esc(report.channel || "站内信") + '</small></div></div><p class="lead-copy">今日核心数据链路整体稳定，共运行 <strong>' + total + '</strong> 条质量规则；' + failed + ' 条失败规则已生成问题单，其中 1 条仍待认领。交易主题金额异常为业务补录导致，正在复核。</p><div class="report-kpis"><div><span>规则运行</span><strong>' + total + '</strong><small>覆盖 28 张表</small></div><div class="good"><span>通过</span><strong>' + passed + '</strong><small>' + (total ? (passed / total * 100).toFixed(1) : "0.0") + '%</small></div><div class="warning"><span>告警</span><strong>' + warning + '</strong><small>3 个已认领</small></div><div class="bad"><span>失败</span><strong>' + failed + '</strong><small>1 个待认领</small></div></div></section>' +
      '<section class="report-section"><div class="report-section-head"><div><span>02 / QUALITY TREND</span><h3>近 7 日质量趋势</h3></div><span class="chip success">整体向好</span></div><div class="trend-chart"><div class="trend-axis"><span>100</span><span>98</span><span>96</span><span>94</span></div><div class="trend-placeholder line-chart"><svg viewBox="0 0 700 180" preserveAspectRatio="none" aria-label="近七日质量分折线"><defs><linearGradient id="dq-area" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#2f6fed" stop-opacity=".26"/><stop offset="100%" stop-color="#2f6fed" stop-opacity="0"/></linearGradient></defs><path class="area" d="M10 115 L120 90 L230 105 L340 72 L450 82 L560 64 L690 45 L690 170 L10 170 Z" fill="url(#dq-area)"/><path class="line" d="M10 115 L120 90 L230 105 L340 72 L450 82 L560 64 L690 45" fill="none" stroke="#2f6fed" stroke-width="4"/><g fill="#fff" stroke="#2f6fed" stroke-width="3"><circle cx="10" cy="115" r="5"/><circle cx="120" cy="90" r="5"/><circle cx="230" cy="105" r="5"/><circle cx="340" cy="72" r="5"/><circle cx="450" cy="82" r="5"/><circle cx="560" cy="64" r="5"/><circle cx="690" cy="45" r="6"/></g></svg><div class="chart-caption"><span>07-24</span><span>07-25</span><span>07-26</span><span>07-27</span><span>07-28</span><span>07-29</span><span>07-30</span></div></div></div></section>' +
      '<section class="report-section"><div class="report-section-head"><div><span>03 / DIMENSION BREAKDOWN</span><h3>质量维度分析</h3></div><small>完整性与准确性需重点关注</small></div><div class="table-wrap"><table class="data-table report-table"><thead><tr><th>质量维度</th><th>质量分</th><th>规则数</th><th>通过</th><th>告警</th><th>失败</th></tr></thead><tbody>' + dimensionRows(dims) + '</tbody></table></div></section>' +
      '<section class="report-section"><div class="report-section-head"><div><span>04 / KEY ISSUES</span><h3>重点问题与处置</h3></div><button class="link" data-route="run-records">查看全部运行记录 →</button></div><div class="issue-list"><article class="issue danger"><span class="issue-index">01</span><div><div class="issue-title"><strong>' + esc(issueRun.ruleName || "客户编码不可为空") + '</strong><span class="chip danger">失败</span><span class="chip danger">待认领</span></div><p>' + esc(issueRun.table || "dim_customer") + ' 的检测值超过红色阈值，已创建质量问题。</p><div class="issue-meta"><span>运行 ' + esc(issueRun.id || "RUN-FAIL") + '</span><span>SLA 剩余 2 小时 41 分</span></div></div><button class="btn ghost" data-route="run-detail" data-id="' + esc(issueRun.id || "") + '">查看详情</button></article><article class="issue warning"><span class="issue-index">02</span><div><div class="issue-title"><strong>' + esc(issueMonitor.name || "订单金额非负") + '</strong><span class="chip warning">告警</span><span class="chip warning">处理中</span></div><p>' + esc(issueMonitor.table || "dwd_order_detail") + ' 发现业务异常，责任人正在复核规则口径。</p><div class="issue-meta"><span>监控 ' + esc(issueMonitor.id || "MON-WARN") + '</span><span>预计 09:30 复核</span></div></div><button class="btn ghost" data-route="monitor-detail" data-id="' + esc(issueMonitor.id || "") + '">查看处置</button></article></div></section>' +
      '<section class="report-section report-actions"><div><strong>' + (number(report.subscribers, 0) > 0 ? "你正在订阅此报告" : "你尚未订阅此报告") + '</strong><span>' + esc(report.cycle || "按需生成") + ' · ' + esc(report.channel || "站内信") + '。</span></div><button class="btn ghost" data-action="report-subscribe">管理订阅</button></section>' +
      '<footer class="report-footer"><span>DTS 数据治理平台 · ' + esc(report.owner || "数据治理组") + '</span><span>生成时间 2026-07-31 08:31:42 · 数据截止 07-30 23:59:59</span></footer></article></section>';
  }

  DQPrototype.views.report = renderReport;
  DQPrototype.views["report-editor"] = renderReportEditor;
  DQPrototype.views["report-preview"] = renderReportPreview;

  if (typeof document !== "undefined") {
    document.addEventListener("click", function (event) {
      var target = event.target.closest("[data-route]");
      if (!target) return;
      var route = target.getAttribute("data-route");
      var id = target.getAttribute("data-id");
      if (route === "report-editor" && target.getAttribute("data-new") === "true") DQPrototype.selectedReportId = null;
      else if ((route === "report-editor" || route === "report-preview") && id) DQPrototype.selectedReportId = id;
    });
  }
})();
