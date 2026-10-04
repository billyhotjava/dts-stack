(function () {
  "use strict";

  window.DQPrototype = window.DQPrototype || {};
  var DQPrototype = window.DQPrototype;
  DQPrototype.views = DQPrototype.views || {};

  var fallbackRules = [
    { id: "QR-1024", name: "主键重复率必须为 0", table: "dwd_order_detail", dimension: "唯一性", severity: "阻断", threshold: "重复率 = 0%", enabled: true, latestResult: "通过", owner: "交易域 / 陈晨" },
    { id: "QR-1038", name: "订单金额非负", table: "dwd_order_detail", dimension: "有效性", severity: "告警", threshold: "异常行数 = 0", enabled: true, latestResult: "告警", owner: "交易域 / 陈晨" },
    { id: "QR-1102", name: "客户编码不可为空", table: "dim_customer", dimension: "完整性", severity: "阻断", threshold: "空值率 ≤ 0.1%", enabled: true, latestResult: "失败", owner: "客户域 / 林瑶" },
    { id: "QR-1145", name: "到账日期不晚于业务日期", table: "dwd_payment_flow", dimension: "准确性", severity: "告警", threshold: "异常行数 ≤ 10", enabled: false, latestResult: "未运行", owner: "资金域 / 周齐" },
    { id: "QR-1171", name: "日分区行数波动", table: "ads_trade_daily", dimension: "稳定性", severity: "告警", threshold: "环比波动 ≤ 25%", enabled: true, latestResult: "通过", owner: "经营分析 / 王澜" }
  ];

  var fallbackTemplates = [
    { id: "TPL-01", name: "表行数 · 固定值", dimension: "完整性", scope: "表级", dataRange: "离线表 / 实时表", description: "校验业务表在统计周期内的行数是否满足固定阈值。", enabledRules: 18, totalRules: 21 },
    { id: "TPL-02", name: "表行数 · 1 日波动率", dimension: "稳定性", scope: "表级", dataRange: "离线表", description: "以昨日同分区为基线，对比当日数据量波动率。", enabledRules: 12, totalRules: 12 },
    { id: "TPL-03", name: "字段空值率", dimension: "完整性", scope: "字段级", dataRange: "离线表 / 实时表", description: "统计字段空值占比，并与绿色、红色阈值比较。", enabledRules: 34, totalRules: 37 },
    { id: "TPL-04", name: "字段唯一值个数", dimension: "唯一性", scope: "字段级", dataRange: "离线表", description: "检查字段去重后的值个数，适用于主键和业务编码。", enabledRules: 9, totalRules: 10 },
    { id: "TPL-05", name: "跨表引用完整性", dimension: "一致性", scope: "跨表级", dataRange: "同源 / 跨源", description: "比对事实表外键与维表主键，定位孤立业务记录。", enabledRules: 7, totalRules: 8 }
  ];

  function esc(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function getList(key, fallback) {
    var data = DQPrototype.data || {};
    return Array.isArray(data[key]) && data[key].length ? data[key] : fallback;
  }

  function tone(value) {
    var text = String(value || "");
    if (/通过|正常|启用/.test(text)) return "success";
    if (/失败|阻断|停用/.test(text)) return "danger";
    if (/告警|待处理|弱/.test(text)) return "warning";
    return "info";
  }

  function chip(value) {
    return '<span class="chip ' + tone(value) + '">' + esc(value || "—") + "</span>";
  }

  function checked(value) {
    return value === false ? "" : " checked";
  }

  function pageTabs(active) {
    return '<div class="tabs" role="tablist">' +
      '<button class="tab' + (active === "table" ? " active" : "") + '" data-route="rule-by-table">按表配置</button>' +
      '<button class="tab' + (active === "template" ? " active" : "") + '" data-route="rule-by-template">按模板配置</button>' +
      "</div>";
  }

  function tableRows(rules) {
    var grouped = {};
    rules.forEach(function (rule) {
      var key = rule.table || rule.tableName || "未命名数据表";
      if (!grouped[key]) grouped[key] = [];
      grouped[key].push(rule);
    });
    var sizes = { dwd_order_detail: "18.7 GB", dim_customer: "2.4 GB", dwd_payment_flow: "8.2 GB", ads_trade_daily: "640 MB" };
    return Object.keys(grouped).map(function (name, index) {
      var list = grouped[name];
      var enabled = list.filter(function (item) { return item.enabled !== false; }).length;
      var owner = list[0].owner || (index % 2 ? "客户域 / 林瑶" : "交易域 / 陈晨");
      return '<tr>' +
        '<td><input type="checkbox" aria-label="选择 ' + esc(name) + '"></td>' +
        '<td><button class="link" data-route="table-detail" data-id="' + esc(name) + '"><strong>' + esc(name) + '</strong></button><div class="cell-sub">dts_lakehouse / production / ' + esc(name) + '</div></td>' +
        '<td><strong>' + enabled + '</strong></td><td>' + list.length + '</td>' +
        '<td>' + esc(sizes[name] || ((index + 1) * 1.6).toFixed(1) + " GB") + '</td>' +
        '<td>' + esc(owner) + '</td>' +
        '<td><button class="link" data-route="table-detail" data-id="' + esc(name) + '">配置规则</button><button class="more-btn" data-action="toast" data-message="已打开表配置菜单">•••</button></td>' +
        "</tr>";
    }).join("");
  }

  function renderRuleByTable() {
    var rules = getList("rules", fallbackRules);
    var tableCount = {};
    rules.forEach(function (rule) { tableCount[rule.table || rule.tableName || "未命名数据表"] = true; });
    return '<section class="page">' +
      '<header class="page-head"><div><div class="eyebrow">规则配置 / 数据对象</div><h1>按表配置</h1><p>从 DTS 数据源与模型出发，为整表或字段批量配置质量规则。</p></div>' +
      '<div class="page-actions"><button class="btn ghost" data-action="toast" data-message="数据表清单已刷新">刷新</button><button class="btn primary" data-route="batch-wizard">批量新增规则</button></div></header>' +
      pageTabs("table") +
      '<div class="split-layout"><aside class="filter-rail"><div class="rail-title">数据源</div>' +
      '<button class="source-item active"><span class="source-icon">◫</span><span><strong>DTS 湖仓</strong><small>生产环境</small></span><b>4</b></button>' +
      '<button class="source-item"><span class="source-icon">DB</span><span><strong>核心交易库</strong><small>MySQL 8.0</small></span><b>2</b></button>' +
      '<button class="source-item"><span class="source-icon">PG</span><span><strong>经营分析库</strong><small>PostgreSQL 15</small></span><b>1</b></button>' +
      '<div class="rail-note"><strong>对象同步正常</strong><span>最近同步 21:06</span></div></aside>' +
      '<div class="panel"><div class="filters"><label class="control search"><span>⌕</span><input placeholder="输入表名、描述或路径"></label>' +
      '<label class="check"><input type="checkbox"> 未配置规则</label><label class="check"><input type="checkbox"> 未启用规则</label><label class="check"><input type="checkbox"> 我负责的</label>' +
      '<button class="btn ghost" data-action="toast" data-message="高级筛选已展开">更多筛选</button><button class="btn ghost" data-action="toast" data-message="筛选条件已重置">重置</button></div>' +
      '<div class="panel-head"><div><div class="panel-title">数据表清单</div><div class="panel-sub">已发现 ' + Object.keys(tableCount).length + ' 张表，启用 ' + rules.filter(function (r) { return r.enabled !== false; }).length + ' 条质量规则</div></div><span class="chip info">元数据同步正常</span></div>' +
      '<div class="table-wrap"><table class="data-table"><thead><tr><th><input type="checkbox" aria-label="全选"></th><th>表名 / 描述 / 路径</th><th>启用规则数 ↕</th><th>总规则数 ↕</th><th>存储量 ↕</th><th>表负责人</th><th>操作</th></tr></thead><tbody>' + tableRows(rules) + '</tbody></table></div>' +
      '<div class="table-footer"><span>共 ' + Object.keys(tableCount).length + ' 条</span><span class="pager"><button disabled>‹</button><button class="current">1</button><button disabled>›</button></span></div>' +
      "</div></div></section>";
  }

  function renderRuleByTemplate() {
    var templates = getList("templates", fallbackTemplates);
    var rows = templates.map(function (item) {
      return '<tr><td><strong>' + esc(item.name) + '</strong><div class="cell-sub">' + esc(item.id || "系统模板") + '</div></td>' +
        '<td>' + chip(item.dimension || "完整性") + '</td><td>' + esc(item.scope || "表级") + '</td><td>' + esc(item.dataRange || "离线表") + '</td>' +
        '<td class="wide-cell">' + esc(item.description || "按预设口径生成质量监控规则。") + '</td>' +
        '<td><strong>' + esc(item.enabledRules == null ? 0 : item.enabledRules) + '</strong> / ' + esc(item.totalRules == null ? 0 : item.totalRules) + '</td>' +
        '<td><button class="link" data-route="batch-wizard" data-id="' + esc(item.id || item.name) + '">配置监控规则</button></td></tr>';
    }).join("");
    return '<section class="page"><header class="page-head"><div><div class="eyebrow">规则配置 / 规则模板</div><h1>按模板配置</h1><p>选择质量模板后，快速为一组 DTS 数据对象生成统一口径的规则。</p></div>' +
      '<div class="page-actions"><button class="btn ghost" data-action="toast" data-message="模板清单已刷新">刷新</button><button class="btn primary" data-route="batch-wizard">批量配置</button></div></header>' +
      pageTabs("template") +
      '<div class="stat-grid"><article class="stat-card"><span>可用模板</span><strong>' + templates.length + '</strong><small>系统模板与自定义模板</small></article>' +
      '<article class="stat-card"><span>已关联规则</span><strong>' + templates.reduce(function (sum, t) { return sum + Number(t.enabledRules || 0); }, 0) + '</strong><small>当前启用</small></article>' +
      '<article class="stat-card"><span>覆盖范围</span><strong>表级 + 字段级</strong><small>支持同源与跨源</small></article></div>' +
      '<div class="panel"><div class="filters"><label class="control search"><span>⌕</span><input placeholder="输入关键字搜索模板"></label>' +
      '<label class="control"><span>关联范围</span><select><option>全部</option><option>表级</option><option>字段级</option><option>跨表级</option></select></label>' +
      '<label class="control"><span>质量维度</span><select><option>全部</option><option>完整性</option><option>唯一性</option><option>准确性</option><option>稳定性</option></select></label>' +
      '<label class="check"><input type="checkbox"> 仅看已使用模板</label></div>' +
      '<div class="table-wrap"><table class="data-table"><thead><tr><th>模板名称</th><th>质量维度</th><th>关联范围</th><th>适用数据范围</th><th>模板描述</th><th>启用 / 总规则</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div></div></section>';
  }

  function renderTableDetail() {
    var allRules = getList("rules", fallbackRules);
    var availableTables = allRules.map(function (rule) { return rule.table || rule.tableName; });
    var tableName = availableTables.indexOf(DQPrototype.selectedTableId) >= 0 ? DQPrototype.selectedTableId : availableTables[0] || "dwd_order_detail";
    var rules = allRules.filter(function (rule) { return (rule.table || rule.tableName) === tableName; });
    if (!rules.length) rules = fallbackRules.slice(0, 2);
    var tableOwner = rules[0].owner || "数据治理组";
    var dataSource = rules[0].dataSource || "DTS 湖仓";
    var rows = rules.map(function (rule) {
      return '<tr><td><strong>' + esc(rule.name) + '</strong><div class="cell-sub">' + esc(rule.id || "QR-AUTO") + '</div></td><td>' + chip(rule.dimension) + '</td><td>' + chip(rule.severity || "告警") + '</td><td>' + esc(rule.threshold || "按模板阈值") + '</td><td>' + chip(rule.latestResult || "未运行") + '</td>' +
        '<td><label class="switch"><input type="checkbox"' + checked(rule.enabled) + '><span></span></label></td><td><button class="link" data-route="monitor-editor" data-id="' + esc(rule.id) + '">编辑</button><button class="more-btn" data-action="toast" data-message="已复制规则配置">•••</button></td></tr>';
    }).join("");
    return '<section class="page"><header class="page-head"><div><button class="back-link" data-route="rule-by-table">← 返回按表配置</button><div class="eyebrow">' + esc(dataSource) + ' / 生产环境</div><h1>' + esc(tableName) + '</h1><p>DTS 治理数据表 · production.' + esc(tableName) + '</p></div>' +
      '<div class="page-actions"><button class="btn ghost" data-action="toast" data-message="已发起一次手工质量检测">立即检测</button><button class="btn primary" data-route="monitor-editor" data-new="true">新增规则</button></div></header>' +
      '<div class="detail-grid"><div><span>表负责人</span><strong>' + esc(tableOwner) + '</strong></div><div><span>数据分层</span><strong>DWD 明细层</strong></div><div><span>更新周期</span><strong>' + esc(rules[0].schedule || "每日 02:10") + '</strong></div><div><span>最近产出</span><strong class="status-good">2026-07-31 09:18</strong></div><div><span>存储量</span><strong>18.7 GB</strong></div><div><span>字段数</span><strong>46</strong></div></div>' +
      '<div class="stat-grid"><article class="stat-card"><span>启用规则</span><strong>' + rules.filter(function (r) { return r.enabled !== false; }).length + '</strong><small>共 ' + rules.length + ' 条配置</small></article><article class="stat-card"><span>最近通过率</span><strong>96.8%</strong><small class="status-good">较昨日 +1.2%</small></article><article class="stat-card"><span>待处置问题</span><strong>1</strong><small class="status-warn">金额异常 8 行</small></article></div>' +
      '<div class="panel"><div class="panel-head"><div><div class="panel-title">质量规则</div><div class="panel-sub">配置规则阈值、重要程度与启用状态</div></div><div class="tabs compact"><button class="tab active">规则列表</button><button class="tab" data-route="run-records">运行记录</button></div></div>' +
      '<div class="filters"><label class="control search"><span>⌕</span><input placeholder="搜索规则名称或 ID"></label><label class="control"><span>质量维度</span><select><option>全部</option><option>完整性</option><option>唯一性</option><option>有效性</option></select></label><label class="check"><input type="checkbox"> 仅看异常</label></div>' +
      '<div class="table-wrap"><table class="data-table"><thead><tr><th>规则名称 / ID</th><th>质量维度</th><th>重要程度</th><th>校验阈值</th><th>最近结果</th><th>启用</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div></div></section>';
  }

  function renderBatchWizard() {
    var templates = getList("templates", fallbackTemplates);
    var template = templates.find(function (item) { return String(item.id || item.name) === String(DQPrototype.selectedTemplateId || ""); }) || templates[0] || fallbackTemplates[0];
    return '<section class="page wizard-page"><header class="page-head"><div><button class="back-link" data-route="rule-by-template">← 返回按模板配置</button><div class="eyebrow">批量新增监控规则</div><h1>用模板生成质量规则</h1><p>为多个数据对象应用同一检测口径，生成后仍可逐条调整。</p></div><div class="page-actions"><span class="chip info">草稿自动保存</span></div></header>' +
      '<div class="stepper"><div class="step done"><span class="step-number">1</span><span><strong>规则设定</strong><small>模板与阈值</small></span></div><div class="step active"><span class="step-number">2</span><span><strong>生成规则</strong><small>选择数据对象</small></span></div><div class="step"><span class="step-number">3</span><span><strong>规则验证</strong><small>抽样试跑</small></span></div></div>' +
      '<div class="wizard-grid"><div class="panel"><div class="panel-head"><div><div class="panel-title">1.1 基本属性</div><div class="panel-sub">所有目标表将使用同一套规则设置，可在生成后单独调整。</div></div></div>' +
      '<div class="form-grid"><label class="field"><span>数据源类型 <em>*</em></span><select><option>DTS 湖仓</option><option>MySQL</option><option>PostgreSQL</option></select></label><label class="field"><span>规则来源 <em>*</em></span><select><option>系统模板</option><option>自定义模板</option></select></label>' +
      '<label class="field full"><span>规则模板 <em>*</em></span><select><option>' + esc(template.name) + '</option><option>字段空值率</option><option>主键重复率</option></select></label><label class="field full"><span>规则名称</span><input value="{表名}_' + esc(template.name) + '"></label><label class="field full"><span>描述</span><textarea placeholder="说明规则适用场景与业务口径">' + esc(template.description || "按模板口径批量检测目标数据对象。") + '</textarea></label></div>' +
      '<div class="panel-head section-head"><div><div class="panel-title">1.2 高级属性</div><div class="panel-sub">设置重要程度、比较方式、绿色阈值与红色阈值。</div></div></div>' +
      '<div class="form-grid"><label class="field"><span>重要程度</span><span class="radio-line"><input type="radio" name="severity"> 强 <input type="radio" name="severity" checked> 弱</span></label><label class="field"><span>比较方式</span><select><option>智能动态阈值</option><option>手动设置</option></select></label><label class="field"><span><i class="dot good"></i> 正常阈值</span><div class="compound"><select><option>≤</option></select><input value="15"><span>%</span></div></label><label class="field"><span><i class="dot bad"></i> 红色阈值</span><div class="compound"><select><option>></option></select><input value="25"><span>%</span></div></label><label class="field"><span>启用状态</span><label class="switch"><input type="checkbox" checked><span></span></label></label><label class="field"><span>问题负责人</span><select><option>跟随表负责人</option><option>陈晨</option></select></label></div></div>' +
      '<aside class="panel selection-panel"><div class="panel-head"><div><div class="panel-title">待配置数据对象（3）</div><div class="panel-sub">选择需要应用模板的表或字段。</div></div><button class="btn ghost" data-action="toast" data-message="数据对象选择器已打开">＋ 添加表</button></div>' +
      '<div class="notice info">3 张表已具备质量检测任务；规则生成后将自动关联最近的数据产出节点。</div>' +
      '<div class="selection-list"><label><input type="checkbox" checked><span><strong>dwd_order_detail</strong><small>交易主题 · 46 字段 · 02:10 产出</small></span><span class="chip success">可配置</span></label><label><input type="checkbox" checked><span><strong>dwd_payment_flow</strong><small>资金主题 · 31 字段 · 03:00 产出</small></span><span class="chip success">可配置</span></label><label><input type="checkbox" checked><span><strong>ads_trade_daily</strong><small>经营分析 · 18 字段 · 06:30 产出</small></span><span class="chip warning">需确认基线</span></label></div>' +
      '<div class="selection-summary"><span>预计生成</span><strong>3 条质量规则</strong><small>关联 3 个调度产出节点</small></div></aside></div>' +
      '<footer class="wizard-actions"><button class="btn ghost" data-route="rule-by-template">取消</button><button class="btn ghost" data-action="toast" data-message="规则设置已保存为草稿">保存草稿</button><button class="btn primary" data-action="toast" data-message="已生成 3 条规则，进入规则验证">生成并验证</button></footer></section>';
  }

  DQPrototype.views["rule-by-table"] = renderRuleByTable;
  DQPrototype.views["rule-by-template"] = renderRuleByTemplate;
  DQPrototype.views["table-detail"] = renderTableDetail;
  DQPrototype.views["batch-wizard"] = renderBatchWizard;

  if (typeof document !== "undefined") {
    document.addEventListener("click", function (event) {
      var target = event.target.closest("[data-route][data-id]");
      if (!target) return;
      var route = target.getAttribute("data-route");
      if (route === "table-detail") DQPrototype.selectedTableId = target.getAttribute("data-id");
      if (route === "batch-wizard") DQPrototype.selectedTemplateId = target.getAttribute("data-id");
    });
  }
})();
