(function () {
  "use strict";

  window.DQPrototype = window.DQPrototype || {};
  window.DQPrototype.views = window.DQPrototype.views || {};

  var prototype = window.DQPrototype;

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  function statusClass(value) {
    if (value >= 98) return "success";
    if (value >= 95) return "warning";
    return "danger";
  }

  function renderDimension(item, index) {
    return [
      '<div class="dimension-row">',
      '  <div class="dimension-label"><span class="dimension-index">', index + 1, '</span><strong>', escapeHtml(item.name), '</strong><span>', escapeHtml(item.passed), '/', escapeHtml(item.total), '</span></div>',
      '  <div class="progress" role="progressbar" aria-label="', escapeHtml(item.name), '通过率" aria-valuemin="0" aria-valuemax="100" aria-valuenow="', escapeHtml(item.passRate), '"><span class="', statusClass(item.passRate), '" style="width:', escapeHtml(item.passRate), '%"></span></div>',
      '  <strong class="dimension-value">', escapeHtml(item.passRate), '%</strong>',
      '</div>',
    ].join("");
  }

  function renderTrendPoint(item) {
    var height = Math.max(26, Math.round((item.passRate - 90) * 9));
    return [
      '<div class="trend-point" title="', escapeHtml(item.label), '：', escapeHtml(item.passRate), '%，', escapeHtml(item.runs), ' 次校验">',
      '  <span class="trend-value">', escapeHtml(item.passRate), '%</span>',
      '  <span class="trend-bar" style="height:', height, 'px"></span>',
      '  <span class="trend-label">', escapeHtml(item.label), '</span>',
      '</div>',
    ].join("");
  }

  prototype.views.overview = function renderOverview(state) {
    var data = prototype.data || {};
    var summary = data.summary || {};
    var period = state && state.period ? state.period : "7d";
    var trendView = state && state.view === "hour" ? "hour" : "day";
    var productionOnly = !state || state.productionOnly !== false;
    var displaySummary = Object.assign({}, summary);
    var dimensions = data.dimensions || [];
    var trend = trendView === "hour" ? data.hourlyTrend || data.trend || [] : data.trend || [];
    var runs = data.runSummary || {};

    if (period === "yesterday") {
      Object.assign(displaySummary, { checksToday: 1237, passRate: 97.4, issueCount: 20, strongIssues: 6, weakIssues: 14 });
    } else if (period === "7d") {
      Object.assign(displaySummary, { checksToday: 8336, passRate: 97.6, issueCount: 43, strongIssues: 12, weakIssues: 31 });
    }
    if (!productionOnly) {
      Object.assign(displaySummary, {
        assetCount: summary.assetCount + 16,
        configuredTables: summary.configuredTables + 8,
        ruleCount: summary.ruleCount + 25,
        enabledRules: summary.enabledRules + 21,
        checksToday: displaySummary.checksToday + 174,
        issueCount: displaySummary.issueCount + 4,
        strongIssues: displaySummary.strongIssues + 1,
        weakIssues: displaySummary.weakIssues + 3,
      });
    }

    return [
      '<section class="page" data-page="overview">',
      '  <header class="page-head">',
      '    <div><div class="eyebrow">质量概览 / 质量大盘</div><h1>数据质量大盘</h1><p>从资产、规则、校验与问题处置四个视角掌握 DTS 数据质量状态。</p></div>',
      '    <div class="page-actions">',
      '      <div class="tabs compact" role="tablist" aria-label="统计周期"><button class="tab ', period === "today" ? "active" : "", '" role="tab" aria-selected="', period === "today", '" data-action="set-period" data-value="today">今日</button><button class="tab ', period === "yesterday" ? "active" : "", '" role="tab" aria-selected="', period === "yesterday", '" data-action="set-period" data-value="yesterday">昨日</button><button class="tab ', period === "7d" ? "active" : "", '" role="tab" aria-selected="', period === "7d", '" data-action="set-period" data-value="7d">近 7 日</button></div>',
      '      <button class="btn ghost ', productionOnly ? "active" : "", '" aria-pressed="', productionOnly, '" data-action="toggle-production">', productionOnly ? "✓ 仅看生产环境" : "查看全部环境", '</button>',
      '      <button class="btn ghost" data-action="refresh">刷新</button>',
      '    </div>',
      '  </header>',

      '  <section class="panel guide-panel">',
      '    <div class="panel-head"><div><div class="panel-title">流程引导 · 新建质量监控</div><p>以数据资产为起点，完成规则配置、调度执行和异常订阅。</p></div><button class="btn ghost" data-action="toast" data-message="已收起流程引导">隐藏引导</button></div>',
      '    <div class="stepper">',
      '      <div class="step active"><span>1</span><div><strong>选择数据资产</strong><small>从资产目录选择需要监控的表。</small><button class="btn ghost" data-route="rule-editor">选择表</button></div></div>',
      '      <div class="step"><span>2</span><div><strong>新建质量规则</strong><small>选择系统模板并设置校验阈值。</small><button class="btn ghost" data-route="rule-editor">新建规则</button></div></div>',
      '      <div class="step"><span>3</span><div><strong>关联调度</strong><small>设置周期、依赖及失败重试策略。</small><button class="btn ghost" data-action="modal" data-modal="schedule">配置调度</button></div></div>',
      '      <div class="step"><span>4</span><div><strong>订阅异常</strong><small>将质量问题通知到责任治理组。</small><button class="btn ghost" data-action="subscribe">告警订阅</button></div></div>',
      '    </div>',
      '  </section>',

      '  <div class="stat-grid">',
      '    <article class="stat-card"><span>纳入监控资产</span><strong>', escapeHtml(displaySummary.configuredTables), '</strong><small>共 ', escapeHtml(displaySummary.assetCount), ' 项候选资产</small></article>',
      '    <article class="stat-card"><span>启用质量规则</span><strong>', escapeHtml(displaySummary.enabledRules), '</strong><small>规则总数 ', escapeHtml(displaySummary.ruleCount), '</small></article>',
      '    <article class="stat-card accent"><span>综合通过率</span><strong>', escapeHtml(displaySummary.passRate), '%</strong><small>累计校验 ', escapeHtml(displaySummary.checksToday), ' 次</small></article>',
      '    <article class="stat-card danger"><span>待处理质量问题</span><strong>', escapeHtml(displaySummary.issueCount), '</strong><small>强规则 ', escapeHtml(displaySummary.strongIssues), ' · 弱规则 ', escapeHtml(displaySummary.weakIssues), '</small></article>',
      '  </div>',

      '  <div class="split-layout overview-layout">',
      '    <section class="panel">',
      '      <div class="panel-head"><div><div class="panel-title">质量维度通过率</div><p>按六大质量维度汇总今日已完成的规则实例。</p></div><span class="chip info">六维模型</span></div>',
      '      <div class="quality-score"><div class="score-ring"><strong>', escapeHtml(displaySummary.passRate), '%</strong><span>综合通过率</span></div><div class="dimension-list">', dimensions.map(renderDimension).join(""), '</div></div>',
      '    </section>',
      '    <section class="panel">',
      '      <div class="panel-head"><div><div class="panel-title">重点关注</div><p>规则覆盖与异常分级概览。</p></div><button class="btn ghost" data-route="rule-list">查看规则</button></div>',
      '      <div class="stat-grid compact">',
      '        <article class="stat-card"><span>已配置规则表</span><strong>', escapeHtml(displaySummary.configuredTables), '</strong><small>覆盖率 66.7%</small></article>',
      '        <article class="stat-card"><span>质量问题表</span><strong>6</strong><small>较昨日减少 2 张</small></article>',
      '        <article class="stat-card danger"><span>强规则问题</span><strong>', escapeHtml(displaySummary.strongIssues), '</strong><small>需优先确认</small></article>',
      '        <article class="stat-card"><span>弱规则问题</span><strong>', escapeHtml(displaySummary.weakIssues), '</strong><small>3 项处理中</small></article>',
      '      </div>',
      '    </section>',
      '  </div>',

      '  <div class="split-layout overview-layout">',
      '    <section class="panel">',
      '      <div class="panel-head"><div><div class="panel-title">实例趋势分析</div><p>', trendView === "hour" ? "今日小时级" : "近 7 日", '规则通过率与运行规模。</p></div><div class="tabs compact" role="tablist" aria-label="趋势粒度"><button class="tab ', trendView === "day" ? "active" : "", '" role="tab" aria-selected="', trendView === "day", '" data-action="switch-view" data-value="day">按天</button><button class="tab ', trendView === "hour" ? "active" : "", '" role="tab" aria-selected="', trendView === "hour", '" data-action="switch-view" data-value="hour">按小时</button></div></div>',
      '      <div class="trend-placeholder"><div class="trend-bars">', trend.map(renderTrendPoint).join(""), '</div></div>',
      '    </section>',
      '    <section class="panel">',
      '      <div class="panel-head"><div><div class="panel-title">实例运行状态</div><p>今日质量校验实例执行分布。</p></div><button class="btn ghost" data-route="run-records">运行记录</button></div>',
      '      <div class="run-status-list">',
      '        <button data-route="run-records"><span class="status-dot success"></span><span>通过</span><strong>', escapeHtml(runs.passed), '</strong></button>',
      '        <button data-route="run-records"><span class="status-dot warning"></span><span>警告</span><strong>', escapeHtml(runs.warning), '</strong></button>',
      '        <button data-route="run-records"><span class="status-dot danger"></span><span>失败</span><strong>', escapeHtml(runs.failed), '</strong></button>',
      '        <button data-route="run-records"><span class="status-dot info"></span><span>运行中</span><strong>', escapeHtml(runs.running), '</strong></button>',
      '      </div>',
      '    </section>',
      '  </div>',
      '</section>',
    ].join("");
  };
})();
