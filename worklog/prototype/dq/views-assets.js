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

  function resultChip(result) {
    var tone = result === "通过" ? "success" : result === "异常" ? "danger" : "info";
    return '<span class="chip ' + tone + '">' + escapeHtml(result) + "</span>";
  }

  function dimensionChip(dimension) {
    var tones = { 完整性: "info", 准确性: "success", 一致性: "warning", 时效性: "warning", 唯一性: "success", 有效性: "info" };
    return '<span class="chip ' + (tones[dimension] || "info") + '">' + escapeHtml(dimension) + "</span>";
  }

  function renderSource(source) {
    return [
      '<button class="filter-link" data-action="toast" data-message="已切换到数据源：', escapeHtml(source.name), '">',
      '  <span><strong>', escapeHtml(source.name), '</strong><small>', escapeHtml(source.type), ' · ', escapeHtml(source.environment), '</small></span>',
      '  <span class="filter-count">', escapeHtml(source.tableCount), '</span>',
      '</button>',
    ].join("");
  }

  function renderRuleRow(rule) {
    return [
      '<tr>',
      '  <td><input type="checkbox" aria-label="选择规则 ', escapeHtml(rule.name), '"></td>',
      '  <td><button class="table-link" data-route="rule-detail" data-id="', escapeHtml(rule.id), '"><strong>', escapeHtml(rule.name), '</strong><small>', escapeHtml(rule.id), '</small></button></td>',
      '  <td>', rule.severity === "强规则" ? '<span class="chip danger">强规则</span>' : '<span class="chip warning">弱规则</span>', '</td>',
      '  <td><code>', escapeHtml(rule.table), '</code><small>', escapeHtml(rule.dataSource), '</small></td>',
      '  <td>', dimensionChip(rule.dimension), '</td>',
      '  <td>', escapeHtml(rule.scope), '<small>', escapeHtml(rule.target), '</small></td>',
      '  <td>', resultChip(rule.latestResult), '<small>', rule.passRate == null ? "暂无结果" : escapeHtml(rule.passRate) + "% 通过", '</small></td>',
      '  <td><span class="chip ', rule.enabled ? "success" : "info", '">', rule.enabled ? "已启用" : "已停用", '</span></td>',
      '  <td class="table-actions"><button data-route="rule-detail" data-id="', escapeHtml(rule.id), '">详情</button><button data-action="', rule.enabled ? "disable" : "enable", '" data-id="', escapeHtml(rule.id), '">', rule.enabled ? "停用" : "启用", '</button></td>',
      '</tr>',
    ].join("");
  }

  function renderTemplateRow(template) {
    return [
      '<tr>',
      '  <td><button class="table-link" data-route="template-detail" data-id="', escapeHtml(template.id), '"><strong>', escapeHtml(template.name), '</strong><small>', escapeHtml(template.id), ' · ', template.system ? "系统模板" : "自定义模板", '</small></button></td>',
      '  <td>', dimensionChip(template.dimension), '</td>',
      '  <td>', escapeHtml(template.scope), '</td>',
      '  <td>', escapeHtml(template.dataRange), '</td>',
      '  <td><strong>', escapeHtml(template.enabledRules), '</strong> / ', escapeHtml(template.totalRules), '</td>',
      '  <td>', escapeHtml(template.description), '</td>',
      '  <td class="table-actions"><button data-route="template-detail" data-id="', escapeHtml(template.id), '">查看</button><button data-route="rule-editor" data-template-id="', escapeHtml(template.id), '">配置规则</button></td>',
      '</tr>',
    ].join("");
  }

  function selectedRule() {
    var data = prototype.data || {};
    var rules = data.rules || [];
    var selectedId = data.selectedRuleId;
    return rules.filter(function (rule) { return rule.id === selectedId; })[0] || rules[0] || {};
  }

  function selectedTemplate() {
    var data = prototype.data || {};
    var templates = data.templates || [];
    var selectedId = data.selectedTemplateId;
    return templates.filter(function (template) { return template.id === selectedId; })[0] || templates[0] || {};
  }

  function optionList(values, current) {
    return values.filter(function (value, index) { return value && values.indexOf(value) === index; }).map(function (value) {
      return '<option' + (value === current ? " selected" : "") + '>' + escapeHtml(value) + '</option>';
    }).join("");
  }

  prototype.views["rule-list"] = function renderRuleList() {
    var data = prototype.data || {};
    var rules = data.rules || [];
    var sources = data.sources || [];

    return [
      '<section class="page" data-page="rule-list">',
      '  <header class="page-head">',
      '    <div><div class="eyebrow">质量资产 / 规则列表</div><h1>质量规则</h1><p>统一管理质量规则、启停状态和最近一次校验结果。</p></div>',
      '    <div class="page-actions"><button class="btn ghost" data-action="export">导出规则</button><button class="btn primary" data-route="rule-editor">＋ 创建规则</button></div>',
      '  </header>',
      '  <div class="split-layout asset-layout">',
      '    <aside class="filter-rail">',
      '      <div class="panel-head"><div><div class="panel-title">数据源</div><p>按已接入数据源筛选</p></div><button class="icon-btn" data-action="refresh" aria-label="刷新数据源">↻</button></div>',
      '      <button class="filter-link active" data-action="toast" data-message="已显示全部数据源"><span><strong>全部数据源</strong><small>生产与开发环境</small></span><span class="filter-count">', sources.length, '</span></button>',
      sources.map(renderSource).join(""),
      '    </aside>',
      '    <section class="panel asset-main">',
      '      <div class="filters">',
      '        <label class="control search-control"><span>规则名称</span><input type="search" placeholder="输入规则名称或 ID"></label>',
      '        <label class="control search-control"><span>数据表</span><input type="search" placeholder="输入表名"></label>',
      '        <label class="control"><span>规则模板</span><select><option>全部模板</option><option>字段非空校验</option><option>数值范围校验</option><option>分区产出时效校验</option></select></label>',
      '        <label class="control"><span>质量维度</span><select><option>全部维度</option><option>完整性</option><option>准确性</option><option>一致性</option><option>时效性</option><option>唯一性</option><option>有效性</option></select></label>',
      '        <label class="control"><span>状态</span><select><option>全部状态</option><option>已启用</option><option>已停用</option></select></label>',
      '        <button class="btn primary" data-action="toast" data-message="已按当前条件筛选规则">查询</button><button class="btn ghost" data-action="toast" data-message="筛选条件已重置">重置</button>',
      '      </div>',
      '      <div class="panel-head table-toolbar"><div><div class="panel-title">规则列表 <span class="chip info">', rules.length, ' 条演示数据</span></div><p>最后同步：', escapeHtml((data.meta || {}).refreshedAt), '</p></div><div class="page-actions"><button class="btn ghost" data-action="enable">批量启用</button><button class="btn ghost" data-action="disable">批量停用</button><button class="btn ghost" data-action="subscribe">订阅管理</button></div></div>',
      '      <div class="table-wrap"><table class="data-table">',
      '        <thead><tr><th><input type="checkbox" aria-label="全选规则"></th><th>ID / 规则名称</th><th>重要程度</th><th>数据表</th><th>质量维度</th><th>关联范围</th><th>最近结果</th><th>状态</th><th>操作</th></tr></thead>',
      '        <tbody>', rules.map(renderRuleRow).join(""), '</tbody>',
      '      </table></div>',
      '      <footer class="table-footer"><span>已选 0 项</span><div>共 ', rules.length, ' 条 <button class="page-number active">1</button></div></footer>',
      '    </section>',
      '  </div>',
      '</section>',
    ].join("");
  };

  prototype.views["rule-template"] = function renderRuleTemplate(state) {
    var data = prototype.data || {};
    var dimensions = data.dimensions || [];
    var templates = data.templates || [];
    var systemCount = templates.filter(function (item) { return item.system; }).length;
    var customCount = templates.length - systemCount;
    var templateView = state && state.view === "custom" ? "custom" : "system";
    var visibleTemplates = templates.filter(function (item) { return templateView === "system" ? item.system : !item.system; });

    return [
      '<section class="page" data-page="rule-template">',
      '  <header class="page-head">',
      '    <div><div class="eyebrow">质量资产 / 规则模板库</div><h1>规则模板库</h1><p>以六大质量维度组织可复用的校验方法，并快速配置到数据资产。</p></div>',
      '    <div class="page-actions"><button class="btn ghost" data-action="refresh">刷新</button><button class="btn primary" data-action="modal" data-modal="new-template">＋ 新建自定义模板</button></div>',
      '  </header>',
      '  <div class="split-layout asset-layout">',
      '    <aside class="filter-rail">',
      '      <div class="panel-title">质量维度</div>',
      '      <button class="filter-link active" data-action="toast" data-message="已显示全部质量维度"><span><strong>全部</strong></span><span class="filter-count">', templates.length, '</span></button>',
      dimensions.map(function (item) { return '<button class="filter-link" data-action="toast" data-message="已筛选：' + escapeHtml(item.name) + '"><span><strong>' + escapeHtml(item.name) + '</strong><small>通过率 ' + escapeHtml(item.passRate) + '%</small></span><span class="filter-count">' + escapeHtml(item.templateCount) + '</span></button>'; }).join(""),
      '      <div class="rail-divider"></div>',
      '      <div class="panel-head"><div class="panel-title">自定义模板组</div><button class="icon-btn" data-action="modal" data-modal="template-group" aria-label="新建模板组">＋</button></div>',
      '      <input class="control" type="search" aria-label="搜索模板组" placeholder="搜索模板组">',
      '      <button class="filter-link" data-action="toast" data-message="已选择默认模板组"><span><strong>默认模板组</strong><small>共享给当前工作空间</small></span><span class="filter-count">', customCount, '</span></button>',
      '    </aside>',
      '    <section class="panel asset-main">',
      '      <div class="panel-head"><div><div class="panel-title">模板目录</div><p>系统模板可直接使用，自定义模板由治理团队维护。</p></div><span class="chip info">关联规则 ', escapeHtml((data.summary || {}).ruleCount), '</span></div>',
      '      <div class="tabs" role="tablist" aria-label="模板类型"><button class="tab ', templateView === "system" ? "active" : "", '" role="tab" aria-selected="', templateView === "system", '" data-action="switch-view" data-value="system">系统模板 (', systemCount, ')</button><button class="tab ', templateView === "custom" ? "active" : "", '" role="tab" aria-selected="', templateView === "custom", '" data-action="switch-view" data-value="custom">自定义模板 (', customCount, ')</button></div>',
      '      <div class="filters compact-filters"><label class="control search-control"><span>模板名称</span><input type="search" placeholder="输入名称或描述"></label><label class="control"><span>关联范围</span><select><option>全部范围</option><option>表级</option><option>字段级</option><option>跨表级</option></select></label><button class="btn primary" data-action="toast" data-message="已按当前条件筛选模板">查询</button></div>',
      '      <div class="table-wrap"><table class="data-table">',
      '        <thead><tr><th>模板名称</th><th>质量维度</th><th>关联范围</th><th>适用数据范围</th><th>启用 / 关联规则</th><th>模板描述</th><th>操作</th></tr></thead>',
      '        <tbody>', visibleTemplates.map(renderTemplateRow).join(""), '</tbody>',
      '      </table></div>',
      '      <footer class="table-footer"><span>', templateView === "system" ? "系统模板只读，可复制为自定义模板" : "自定义模板可编辑和版本化", '</span><div>共 ', visibleTemplates.length, ' 条 <button class="page-number active">1</button></div></footer>',
      '    </section>',
      '  </div>',
      '</section>',
    ].join("");
  };

  prototype.views["rule-detail"] = function renderRuleDetail() {
    var data = prototype.data || {};
    var rule = selectedRule();
    var relatedRuns = (data.runs || []).filter(function (run) { return run.ruleId === rule.id; });
    var relatedTemplate = (data.templates || []).filter(function (template) { return template.name === rule.template; })[0] || {};

    return [
      '<section class="page" data-page="rule-detail">',
      '  <header class="page-head">',
      '    <div><button class="back-link" data-route="rule-list">← 返回规则列表</button><div class="eyebrow">', escapeHtml(rule.id), ' / ', escapeHtml(rule.dimension), '</div><h1>', escapeHtml(rule.name), '</h1><p>', escapeHtml(rule.description), '</p></div>',
      '    <div class="page-actions"><button class="btn ghost" data-action="test-rule" data-id="', escapeHtml(rule.id), '">试跑</button><button class="btn ghost" data-action="run-now" data-id="', escapeHtml(rule.id), '">立即运行</button><button class="btn primary" data-route="rule-editor" data-id="', escapeHtml(rule.id), '">编辑规则</button></div>',
      '  </header>',
      '  <div class="stat-grid">',
      '    <article class="stat-card"><span>运行状态</span><strong>', resultChip(rule.latestResult), '</strong><small>更新于 ', escapeHtml(rule.updatedAt), '</small></article>',
      '    <article class="stat-card"><span>近 30 日通过率</span><strong>', rule.passRate == null ? "--" : escapeHtml(rule.passRate) + "%", '</strong><small>目标不低于 99%</small></article>',
      '    <article class="stat-card"><span>重要程度</span><strong>', rule.severity === "强规则" ? '<span class="chip danger">强规则</span>' : '<span class="chip warning">弱规则</span>', '</strong><small>异常进入质量问题闭环</small></article>',
      '    <article class="stat-card"><span>规则状态</span><strong><span class="chip ', rule.enabled ? "success" : "info", '">', rule.enabled ? "已启用" : "已停用", '</span></strong><small>', escapeHtml(rule.schedule), '</small></article>',
      '  </div>',
      '  <div class="split-layout detail-layout">',
      '    <section class="panel">',
      '      <div class="panel-head"><div><div class="panel-title">规则定义</div><p>校验对象、模板与阈值配置。</p></div><button class="btn ghost" data-route="template-detail" data-id="', escapeHtml(relatedTemplate.id), '">查看模板</button></div>',
      '      <div class="detail-grid">',
      '        <div><span>数据源</span><strong>', escapeHtml(rule.dataSource), '</strong></div><div><span>数据表</span><strong><code>', escapeHtml(rule.table), '</code></strong></div>',
      '        <div><span>关联范围</span><strong>', escapeHtml(rule.scope), '</strong></div><div><span>校验对象</span><strong><code>', escapeHtml(rule.target), '</code></strong></div>',
      '        <div><span>规则模板</span><strong>', escapeHtml(rule.template), '</strong></div><div><span>质量维度</span><strong>', dimensionChip(rule.dimension), '</strong></div>',
      '        <div><span>通过条件</span><strong>', escapeHtml(rule.threshold), '</strong></div><div><span>负责人</span><strong>', escapeHtml(rule.owner), '</strong></div>',
      '      </div>',
      '    </section>',
      '    <section class="panel">',
      '      <div class="panel-head"><div><div class="panel-title">调度与告警</div><p>执行周期及异常通知策略。</p></div><button class="btn ghost" data-action="subscribe">订阅管理</button></div>',
      '      <div class="detail-grid vertical">',
      '        <div><span>运行周期</span><strong>', escapeHtml(rule.schedule), '</strong></div><div><span>失败重试</span><strong>2 次，间隔 5 分钟</strong></div>',
      '        <div><span>异常通知</span><strong>站内通知 · 责任治理组</strong></div><div><span>问题生成</span><strong>强规则自动创建问题单</strong></div>',
      '      </div>',
      '    </section>',
      '  </div>',
      '  <section class="panel">',
      '    <div class="panel-head"><div><div class="panel-title">最近运行</div><p>保留最近的校验结果和处置状态。</p></div><button class="btn ghost" data-route="run-records">查看全部运行记录</button></div>',
      relatedRuns.length ? '<div class="table-wrap"><table class="data-table"><thead><tr><th>实例 ID</th><th>结果</th><th>完成时间</th><th>扫描行数</th><th>耗时</th><th>问题处置</th></tr></thead><tbody>' + relatedRuns.map(function (run) { return '<tr><td><code>' + escapeHtml(run.id) + '</code></td><td>' + resultChip(run.status) + '</td><td>' + escapeHtml(run.finishedAt) + '</td><td>' + escapeHtml(run.rows) + '</td><td>' + escapeHtml(run.duration) + '</td><td>' + escapeHtml(run.disposition) + '</td></tr>'; }).join("") + '</tbody></table></div>' : '<div class="empty-state"><strong>暂无运行记录</strong><p>启用规则并关联调度后，这里将展示执行结果。</p></div>',
      '  </section>',
      '</section>',
    ].join("");
  };

  prototype.views["rule-editor"] = function renderRuleEditor() {
    var data = prototype.data || {};
    var rules = data.rules || [];
    var templates = data.templates || [];
    var rule = rules.filter(function (item) { return item.id === data.selectedRuleId; })[0] || null;
    var template = templates.filter(function (item) { return item.id === data.selectedTemplateId; })[0] ||
      templates.filter(function (item) { return rule && item.name === rule.template; })[0] || templates[0] || {};
    var editing = Boolean(rule);
    var source = rule ? rule.dataSource : ((data.sources || [])[0] || {}).name || "治理演示库";
    var table = rule ? rule.table : (rules[0] || {}).table || "dwd_finance_budget_execution";
    var scope = rule ? rule.scope : template.scope || "字段级";
    var target = rule ? rule.target : scope === "表级" ? "整表" : (rules[0] || {}).target || "budget_execution_id";
    var threshold = rule ? rule.threshold : template.threshold || "空值率 = 0%";
    var comparison = threshold.indexOf("<=") >= 0 ? "<=" : threshold.indexOf(">=") >= 0 ? ">=" : "=";
    var thresholdMatch = threshold.match(/-?\d+(?:\.\d+)?/);
    var thresholdValue = thresholdMatch ? thresholdMatch[0] : "0";
    var name = rule ? rule.name : (template.name ? template.name + "规则" : "新建质量规则");
    var description = rule ? rule.description : template.description || "为所选数据资产配置统一的数据质量校验口径。";
    var severity = rule ? rule.severity : "强规则";
    var schedule = rule ? rule.schedule : "每日 02:10";
    var sourceOptions = optionList((data.sources || []).map(function (item) { return item.name; }).concat([source]), source);
    var tableOptions = optionList(rules.map(function (item) { return item.table; }).concat([table]), table);
    var targetOptions = optionList(rules.map(function (item) { return item.target; }).concat([target]), target);
    var templateOptions = templates.map(function (item) {
      return '<option' + (item.id === template.id ? " selected" : "") + '>' + escapeHtml(item.name) + ' · ' + escapeHtml(item.dimension) + '</option>';
    }).join("");
    var heading = editing ? "编辑质量规则 · " + name : "创建质量规则 · " + template.name;
    var mode = editing ? "编辑 " + rule.id : "新建" + (template.id ? " · " + template.id : "");

    return [
      '<section class="page" data-page="rule-editor">',
      '  <header class="page-head"><div><button class="back-link" data-route="rule-list">← 返回规则列表</button><div class="eyebrow">规则配置 / ', escapeHtml(mode), '</div><h1>', escapeHtml(heading), '</h1><p>选择数据资产与规则模板，设置阈值后发布到质量调度。</p></div><div class="page-actions"><button class="btn ghost" data-action="save">', editing ? "保存变更" : "保存草稿", '</button><button class="btn primary" data-action="publish">', editing ? "保存并发布" : "发布并启用", '</button></div></header>',
      '  <div class="stepper editor-stepper"><div class="step active"><span class="step-number">1</span><div><strong>选择数据资产</strong><small>指定表与字段</small></div></div><div class="step active"><span class="step-number">2</span><div><strong>选择规则模板</strong><small>确定质量维度</small></div></div><div class="step"><span class="step-number">3</span><div><strong>设置校验阈值</strong><small>定义通过条件</small></div></div><div class="step"><span class="step-number">4</span><div><strong>配置调度告警</strong><small>发布运行</small></div></div></div>',
      '  <section class="panel">',
      '    <div class="drawer-section"><div class="panel-head"><div><div class="panel-title">1. 基本信息</div><p>规则名称用于资产目录、运行记录和质量报告。</p></div><span class="chip info">必填</span></div>',
      '      <div class="form-grid"><label class="field"><span>规则名称 <em>*</em></span><input value="', escapeHtml(name), '" placeholder="输入规则名称"></label><label class="field"><span>重要程度 <em>*</em></span><select>', optionList(["强规则", "弱规则"], severity), '</select></label><label class="field full"><span>规则描述</span><textarea rows="3">', escapeHtml(description), '</textarea></label></div>',
      '    </div>',
      '    <div class="drawer-section"><div class="panel-head"><div><div class="panel-title">2. 数据资产</div><p>从 DTS 资产目录选择已接入的数据表。</p></div><button class="btn ghost" data-action="modal" data-modal="asset-picker">从资产目录选择</button></div>',
      '      <div class="form-grid"><label class="field"><span>数据源 <em>*</em></span><select>', sourceOptions, '</select></label><label class="field"><span>数据表 <em>*</em></span><select>', tableOptions, '</select></label><label class="field"><span>关联范围 <em>*</em></span><select>', optionList(["字段级", "表级", "多字段级", "跨表级"], scope), '</select></label><label class="field"><span>校验对象 <em>*</em></span><select>', targetOptions, '</select></label></div>',
      '    </div>',
      '    <div class="drawer-section"><div class="panel-head"><div><div class="panel-title">3. 模板与阈值</div><p>模板决定计算逻辑，阈值决定是否生成异常。</p></div><button class="btn ghost" data-route="rule-template">浏览模板库</button></div>',
      '      <div class="form-grid"><label class="field"><span>规则模板 <em>*</em></span><select>', templateOptions, '</select></label><label class="field"><span>质量维度</span><input value="', escapeHtml(template.dimension || rule && rule.dimension || "完整性"), '" disabled></label><label class="field"><span>比较符 <em>*</em></span><select>', optionList(["=", "<=", ">="], comparison), '</select></label><label class="field"><span>阈值 <em>*</em></span><div class="compound"><input type="number" value="', escapeHtml(thresholdValue), '"><span>%</span></div></label></div>',
      '      <div class="rule-preview"><span>通过条件预览</span><strong>', escapeHtml(threshold), '</strong><small>不满足条件时，本次实例记为异常并创建质量问题。</small></div>',
      '    </div>',
      '    <div class="drawer-section"><div class="panel-head"><div><div class="panel-title">4. 调度与告警</div><p>原型中的保存、试跑和发布为交互演示，不会创建真实任务。</p></div></div>',
      '      <div class="form-grid"><label class="field"><span>触发方式 <em>*</em></span><select><option>周期调度</option><option>手动触发</option><option>上游任务完成后</option></select></label><label class="field"><span>运行周期 <em>*</em></span><select>', optionList(["每日 02:10", "每日 02:20", "每日 01:40", "每小时第 10 分钟", "每周一 03:00"], schedule), '</select></label><label class="field"><span>异常通知</span><select><option>', escapeHtml(rule ? rule.owner : "责任治理组"), ' · 站内通知</option><option>不订阅</option></select></label><label class="field"><span>失败重试</span><select><option>2 次，间隔 5 分钟</option><option>不重试</option></select></label></div>',
      '    </div>',
      '    <footer class="editor-footer"><button class="btn ghost" data-route="rule-list">取消</button><div><button class="btn ghost" data-action="test-rule">试跑校验</button><button class="btn ghost" data-action="save">', editing ? "保存变更" : "保存草稿", '</button><button class="btn primary" data-action="publish">', editing ? "保存并发布" : "发布并启用", '</button></div></footer>',
      '  </section>',
      '</section>',
    ].join("");
  };

  prototype.views["template-detail"] = function renderTemplateDetail() {
    var data = prototype.data || {};
    var template = selectedTemplate();
    var relatedRules = (data.rules || []).filter(function (rule) { return rule.template === template.name; });

    return [
      '<section class="page" data-page="template-detail">',
      '  <header class="page-head"><div><button class="back-link" data-route="rule-template">← 返回模板库</button><div class="eyebrow">', escapeHtml(template.id), ' / ', template.system ? "系统模板" : "自定义模板", '</div><h1>', escapeHtml(template.name), '</h1><p>', escapeHtml(template.description), '</p></div><div class="page-actions"><button class="btn ghost" data-action="toast" data-message="系统模板已复制为自定义草稿">复制模板</button><button class="btn primary" data-route="rule-editor" data-template-id="', escapeHtml(template.id), '">配置监控规则</button></div></header>',
      '  <div class="stat-grid"><article class="stat-card"><span>质量维度</span><strong>', dimensionChip(template.dimension), '</strong><small>六维质量模型</small></article><article class="stat-card"><span>关联范围</span><strong>', escapeHtml(template.scope), '</strong><small>', escapeHtml(template.dataRange), '</small></article><article class="stat-card"><span>已启用规则</span><strong>', escapeHtml(template.enabledRules), '</strong><small>共关联 ', escapeHtml(template.totalRules), ' 条</small></article><article class="stat-card"><span>模板类型</span><strong>', template.system ? "系统模板" : "自定义模板", '</strong><small>更新于 ', escapeHtml(template.updatedAt), '</small></article></div>',
      '  <div class="split-layout detail-layout">',
      '    <section class="panel"><div class="panel-head"><div><div class="panel-title">模板定义</div><p>模板只描述校验方法，不绑定具体业务资产。</p></div></div><div class="detail-grid"><div><span>模板名称</span><strong>', escapeHtml(template.name), '</strong></div><div><span>模板编号</span><strong><code>', escapeHtml(template.id), '</code></strong></div><div><span>质量维度</span><strong>', escapeHtml(template.dimension), '</strong></div><div><span>关联范围</span><strong>', escapeHtml(template.scope), '</strong></div><div><span>适用数据范围</span><strong>', escapeHtml(template.dataRange), '</strong></div><div><span>默认通过条件</span><strong>', escapeHtml(template.threshold), '</strong></div></div></section>',
      '    <section class="panel"><div class="panel-head"><div><div class="panel-title">执行逻辑</div><p>规则发布时由执行引擎解析为校验任务。</p></div></div><div class="code-preview"><div><span>校验表达式</span><br><code>invalid_rate = invalid_rows / checked_rows</code></div><br><div><span>结果判断</span><br><code>invalid_rate &lt;= configured_threshold</code></div><br><small>演示表达式不代表后端真实 DSL 或 SQL。</small></div></section>',
      '  </div>',
      '  <section class="panel"><div class="panel-head"><div><div class="panel-title">关联规则</div><p>使用此模板创建的质量规则。</p></div><button class="btn primary" data-route="rule-editor" data-template-id="', escapeHtml(template.id), '">＋ 配置规则</button></div>',
      relatedRules.length ? '<div class="table-wrap"><table class="data-table"><thead><tr><th>规则名称</th><th>数据表</th><th>重要程度</th><th>状态</th><th>最近结果</th><th>操作</th></tr></thead><tbody>' + relatedRules.map(function (rule) { return '<tr><td><button class="table-link" data-route="rule-detail" data-id="' + escapeHtml(rule.id) + '">' + escapeHtml(rule.name) + '</button></td><td><code>' + escapeHtml(rule.table) + '</code></td><td>' + escapeHtml(rule.severity) + '</td><td>' + (rule.enabled ? "已启用" : "已停用") + '</td><td>' + resultChip(rule.latestResult) + '</td><td class="table-actions"><button data-route="rule-detail" data-id="' + escapeHtml(rule.id) + '">详情</button></td></tr>'; }).join("") + '</tbody></table></div>' : '<div class="empty-state"><strong>暂无关联规则</strong><p>选择“配置监控规则”，可将模板应用到数据资产。</p><button class="btn primary" data-route="rule-editor">配置第一条规则</button></div>',
      '  </section>',
      '</section>',
    ].join("");
  };

  if (typeof document !== "undefined") {
    document.addEventListener("click", function rememberSelectedAsset(event) {
      var ruleTarget = event.target.closest('[data-route="rule-detail"][data-id]');
      var templateTarget = event.target.closest('[data-route="template-detail"][data-id]');
      var editorTarget = event.target.closest('[data-route="rule-editor"]');
      if (ruleTarget && prototype.data) prototype.data.selectedRuleId = ruleTarget.getAttribute("data-id");
      if (templateTarget && prototype.data) prototype.data.selectedTemplateId = templateTarget.getAttribute("data-id");
      if (editorTarget && prototype.data) {
        var ruleId = editorTarget.getAttribute("data-id");
        var templateId = editorTarget.getAttribute("data-template-id");
        if (ruleId) {
          prototype.data.selectedRuleId = ruleId;
          var rule = (prototype.data.rules || []).filter(function (item) { return item.id === ruleId; })[0];
          var template = (prototype.data.templates || []).filter(function (item) { return rule && item.name === rule.template; })[0];
          if (template) prototype.data.selectedTemplateId = template.id;
        } else if (templateId) {
          delete prototype.data.selectedRuleId;
          prototype.data.selectedTemplateId = templateId;
        } else {
          delete prototype.data.selectedRuleId;
          delete prototype.data.selectedTemplateId;
        }
      }
    });
  }
})();
