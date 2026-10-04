(() => {
  const data = window.PROTOTYPE_DATA;
  const app = document.querySelector("#app");

  const state = {
    workspace: "modeling",
    workspaceSub: "",
    planningGroups: { public: true, application: true },
    metricCurrent: "",
    section: "modeling",
    layer: "公共层",
    currentType: "dimension-table",
    currentCode: "dim_budget_account",
    currentName: "预算科目维度表",
    createMenu: false,
    modal: "",
    publishTab: "publish",
    reverseWizard: false,
    toast: "",
    fieldRows: JSON.parse(JSON.stringify(data.fields["dimension-table"])),
  };

  const workspaceNav = [
    ["home", "首页"],
    ["planning", "数仓规划"],
    ["standards", "数据标准"],
    ["modeling", "维度建模"],
    ["metrics", "数据指标"],
    ["tools", "通用工具"],
    ["graph", "关系图"],
  ];

  const icons = {
    plus: "＋",
    import: "⇩",
    export: "⇧",
    display: "☷",
    refresh: "↻",
  };

  function model(type = state.currentType) {
    return data.modelTypes[type];
  }

  function currentModelCode() {
    if (state.currentCode) return state.currentCode;
    const codeField = model().fields.find(([label]) => label === "表名" || label === "英文缩写");
    return codeField?.[1] || "new_model";
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;");
  }

  function render() {
    app.innerHTML = `
      <div class="shell">
        <div class="notice">
          <span>智能数据建模体验环境 · 本页面为 DTS 交互原型</span>
          <a href="../SCREEN-INVENTORY.md">查看真实页面清单</a>
        </div>
        <header class="topbar">
          <div class="brand"><span class="brand-mark">D</span><span>DTS · 智能数据建模</span></div>
          <div class="workspace"><span>▣</span><span>默认工作空间</span><span>⌄</span></div>
          <nav class="topnav">
            ${workspaceNav
              .map(
                ([key, label]) =>
                  `<button class="${state.workspace === key ? "active" : ""}" data-workspace="${key}">${label}</button>`,
              )
              .join("")}
          </nav>
          <div class="top-actions"><span class="agent-pill">✦ Data Agent</span><span>⌕</span><span>?</span><span>简体</span><span>xzmfly</span></div>
        </header>
        ${
          state.workspace === "modeling"
            ? `<main class="layout">
                ${renderModuleRail()}
                ${state.section === "modeling" ? renderObjectPanel() : '<div class="object-panel"></div>'}
                <section class="editor">
                  ${state.section === "reverse" ? renderReverse() : renderEditor()}
                </section>
              </main>`
            : state.workspace === "metrics"
              ? `<main class="metric-layout">
                  ${renderMetricTypeRail()}
                  ${renderMetricCatalog()}
                  <section class="metric-editor">${renderMetrics()}</section>
                </main>`
              : `<main class="workspace-layout ${state.workspace === "planning" ? "planning-layout" : ""}">
                  ${renderWorkspaceRail()}
                  <section class="workspace-content">${renderWorkspacePage()}</section>
                </main>`
        }
        ${state.workspace === "modeling" ? renderRightRail() : ""}
        ${renderModal()}
        ${state.toast ? `<div class="toast">${escapeHtml(state.toast)}</div>` : ""}
      </div>
    `;
  }

  function renderWorkspaceRail() {
    if (state.workspace === "planning") return renderPlanningRail();
    const configs = {
      home: {
        title: "首页",
        items: [
          ["工作台", 1],
          ["最近访问", 5],
          ["我的任务", 3],
        ],
      },
      planning: { title: "数仓规划", items: data.planning.nav },
      standards: { title: "数据标准", items: data.standards.nav },
      metrics: { title: "数据指标", items: data.metrics.nav },
      tools: {
        title: "通用工具",
        items: [
          ["工具箱", 6],
          ["导入记录", 2],
          ["导出记录", 1],
        ],
      },
      graph: {
        title: "关系图",
        items: [
          ["模型关系", 9],
          ["标准关系", 6],
          ["指标血缘", 5],
        ],
      },
    };
    const config = configs[state.workspace];
    const selected = state.workspaceSub || config.items[0][0];
    return `
      <aside class="workspace-rail">
        <div class="workspace-rail-title">${config.title}</div>
        <div class="workspace-rail-search"><input placeholder="搜索" /></div>
        <nav>
          ${config.items
            .map(
              ([label, count]) =>
                `<button class="${selected === label ? "active" : ""}" data-workspace-sub="${label}"><span>${label}</span><span>${count}</span></button>`,
            )
            .join("")}
        </nav>
      </aside>
    `;
  }

  function renderPlanningRail() {
    const selected = state.workspaceSub || "业务过程";
    const item = (label, icon = "") =>
      `<button class="planning-nav-item ${selected === label ? "active" : ""}" data-workspace-sub="${label}"><span class="planning-nav-icon">${icon}</span><span>${label}</span></button>`;
    const group = (key, label, icon, children) => `
      <div class="planning-nav-group">
        <button class="planning-nav-heading" data-planning-toggle="${key}">
          <span class="planning-nav-icon">${icon}</span><span>${label}</span><span>${state.planningGroups[key] ? "⌄" : "›"}</span>
        </button>
        ${state.planningGroups[key] ? `<div class="planning-nav-children">${children.map((child) => item(child)).join("")}</div>` : ""}
      </div>`;
    return `
      <aside class="workspace-rail planning-rail">
        <nav>
          ${item("业务分类", "▦")}
          ${item("数仓分层", "◆")}
          ${group("public", "公共层", "⌘", ["数据域", "业务过程"])}
          ${group("application", "应用层", "▣", ["数据集市", "主题域"])}
          ${item("建模空间", "⬢")}
          ${item("系统管理", "⎊")}
        </nav>
      </aside>
    `;
  }

  function metricType() {
    return state.workspaceSub || "原子指标";
  }

  function renderMetricTypeRail() {
    const selected = metricType();
    const iconsByType = {
      复合指标: "△",
      派生指标: "▥",
      原子指标: "▲",
      修饰词: "⌁",
      时间周期: "▣",
    };
    return `
      <aside class="metric-type-rail">
        ${data.metrics.nav
          .map(
            ([label]) => `
              <button class="${selected === label ? "active" : ""}" data-metric-type="${label}">
                <span>${iconsByType[label]}</span><strong>${label}</strong>
              </button>`,
          )
          .join("")}
      </aside>
    `;
  }

  function renderMetricCatalog() {
    const type = metricType();
    const rows = data.metrics.catalog[type] || [];
    const groups = rows.reduce((result, row) => {
      (result[row[0]] ||= []).push(row);
      return result;
    }, {});
    return `
      <aside class="metric-catalog">
        <header class="metric-catalog-header">
          <h2>${type}</h2>
          <div>
            <button title="新建" data-metric-action="new">＋</button>
            <button title="导入" data-metric-action="import">⇩</button>
            <button title="显示设置" data-metric-action="display">☷</button>
            <button title="刷新" data-metric-action="refresh">↻</button>
          </div>
        </header>
        <div class="metric-layer">公共层</div>
        <div class="metric-domain-filter"><select><option>🌐　请选择数据域</option><option>财务域</option><option>项目域</option></select></div>
        <div class="metric-search"><input placeholder="搜索" /></div>
        <div class="metric-tree">
          <div class="metric-tree-root"><span>⌄</span><span>▱</span><strong>${type}</strong><span>(${rows.length})</span></div>
          ${Object.entries(groups)
            .map(
              ([groupName, groupRows]) => `
                <div class="metric-tree-group">
                  <div class="metric-tree-domain"><span>⌄</span><span>🌐</span><strong>${groupName}</strong><span>(${groupRows.length})</span></div>
                  ${groupRows
                    .map(
                      ([, code, name]) =>
                        `<button class="${state.metricCurrent === code ? "active" : ""}" data-metric-node="${code}|${name}"><span>△</span><span><b>${code}</b><small>${name}</small></span></button>`,
                    )
                    .join("")}
                </div>`,
            )
            .join("")}
        </div>
      </aside>
    `;
  }

  function renderWorkspacePage() {
    return {
      home: renderHome,
      planning: renderPlanning,
      standards: renderStandards,
      metrics: renderMetrics,
      tools: renderTools,
      graph: renderGraph,
    }[state.workspace]();
  }

  function renderPageHeader(title, description, actions = "") {
    return `
      <header class="page-header">
        <div><h1>${title}</h1><p>${description}</p></div>
        <div class="page-actions">${actions}</div>
      </header>
    `;
  }

  function renderHome() {
    const cards = [
      ["数据域", "2", "财务域、项目域", "planning"],
      ["业务维度", "5", "3 个已发布", "modeling"],
      ["逻辑模型", "9", "5 个已发布", "modeling"],
      ["字段标准", "12", "10 个已生效", "standards"],
      ["数据指标", "13", "8 个已发布", "metrics"],
    ];
    return `
      ${renderPageHeader("智能数据建模", "默认工作空间 · 财务与项目管理 Demo", '<button class="small-button" data-workspace="graph">查看全景关系</button>')}
      <div class="workspace-scroll">
        <section class="summary-strip">
          ${cards
            .map(
              ([label, value, hint, target]) => `
                <button class="summary-card" data-workspace="${target}">
                  <span>${label}</span><strong>${value}</strong><small>${hint}</small>
                </button>`,
            )
            .join("")}
        </section>
        <div class="home-grid">
          <section class="work-panel span-2">
            <div class="work-panel-header"><h2>最近模型</h2><button class="link-button" data-workspace="modeling">进入维度建模 ›</button></div>
            ${renderTable(
              ["模型名称", "模型类型", "数据域", "版本", "状态", "更新时间"],
              [
                ["预算科目维度表", "维度表", "财务域", "r2", "已发布", "今天 09:35"],
                ["预算执行明细表", "明细表", "财务域", "r1", "已发布", "昨天 17:10"],
                ["月度预算执行汇总表", "汇总表", "财务域", "v1", "草稿", "昨天 15:42"],
                ["项目周进展汇总表", "汇总表", "项目域", "v1", "草稿", "07-29 16:18"],
              ],
            )}
          </section>
          <section class="work-panel">
            <div class="work-panel-header"><h2>交付状态</h2></div>
            <div class="pipeline-list">
              ${[
                ["规划完成", 2, "100%"],
                ["标准映射", 10, "83%"],
                ["模型发布", 5, "56%"],
                ["物化成功", 4, "44%"],
              ]
                .map(
                  ([label, count, width]) =>
                    `<div><span>${label}<b>${count}</b></span><i><em style="width:${width}"></em></i></div>`,
                )
                .join("")}
            </div>
          </section>
          <section class="work-panel">
            <div class="work-panel-header"><h2>待处理</h2></div>
            <ul class="task-list"><li><b>3</b><span>模型等待发布</span></li><li><b>2</b><span>标准等待生效</span></li><li><b>1</b><span>物化任务失败</span></li></ul>
          </section>
          <section class="work-panel span-2">
            <div class="work-panel-header"><h2>快速入口</h2></div>
            <div class="quick-actions">
              ${[
                ["规划数据域", "planning"],
                ["维护字段标准", "standards"],
                ["创建维度表", "modeling"],
                ["定义派生指标", "metrics"],
                ["查看关系图", "graph"],
              ]
                .map(([label, target]) => `<button data-workspace="${target}">＋ ${label}</button>`)
                .join("")}
            </div>
          </section>
        </div>
      </div>
    `;
  }

  function renderPlanning() {
    const selected = state.workspaceSub || "业务过程";
    const pages = {
      业务分类: {
        description: "业务分类用于组织企业的数据业务板块，为数仓分层、数据域和业务过程提供统一归属。",
        fields: [["英文缩写", ""], ["英文名", ""], ["中文名", ""]],
        action: "新建业务分类",
        headers: ["英文缩写", "中文名", "英文名", "说明"],
        rows: [
          ["default", "默认", "default business category", "系统默认业务分类"],
          ["finance", "财务管理", "finance management", "预算、成本及资金业务"],
          ["project", "项目管理", "project management", "项目计划、质量与风险业务"],
        ],
      },
      数仓分层: {
        description: "数仓分层用于定义数据从贴源、公共加工到应用交付的组织结构和建模职责。",
        fields: [["分层缩写", ""], ["分层名称", ""], ["所属类型", ""]],
        action: "新建数仓分层",
        headers: ["分层缩写", "分层名称", "分层类型", "说明", "状态"],
        rows: [
          ["ODS", "贴源层", "贴源层", "保留源系统数据结构", "系统"],
          ["DIM", "维度层", "公共层", "维护一致性维度", "系统"],
          ["DWD", "明细层", "公共层", "沉淀业务过程明细", "系统"],
          ["DWS", "汇总层", "公共层", "按公共粒度汇总", "系统"],
          ["ADS", "应用层", "应用层", "面向场景交付数据", "系统"],
        ],
      },
      数据域: {
        description: "数据域是面向业务主题的数据集合，用于约束业务过程、维度、模型与指标的归属范围。",
        fields: [["英文缩写", ""], ["英文名", ""], ["中文名", ""]],
        action: "新建数据域",
        headers: ["英文缩写", "中文名", "英文名", "业务分类", "状态"],
        rows: [
          ["default", "默认", "default data domain", "默认", "系统"],
          ["fin", "财务域", "finance domain", "财务管理", "已发布"],
          ["prj", "项目域", "project domain", "项目管理", "草稿"],
        ],
      },
      业务过程: {
        description: "业务过程指企业的业务活动事件，是对业务系统和业务事实进行的抽象，可用于订单、支付、预算执行及项目节点等过程建模。",
        fields: [["英文缩写", ""], ["英文名", ""], ["中文名", ""]],
        action: "新建业务过程",
        headers: ["英文缩写", "中文名", "英文名", "数据域"],
        rows: [
          ["default", "默认", "default business process", "默认"],
          ["fin_default", "财务域_默认", "fin_default", "财务域"],
          ["budget_execution", "预算执行", "budget execution", "财务域"],
          ["milestone_tracking", "节点跟踪", "milestone tracking", "项目域"],
        ],
      },
      数据集市: {
        description: "数据集市面向特定部门或分析场景组织应用数据，是应用层模型和数据服务的交付边界。",
        fields: [["英文缩写", ""], ["英文名", ""], ["中文名", ""]],
        action: "新建数据集市",
        headers: ["英文缩写", "中文名", "英文名", "负责人", "状态"],
        rows: [
          ["finance_mart", "财务分析集市", "finance analytics mart", "xzmfly", "已发布"],
          ["project_mart", "项目管理集市", "project management mart", "xzmfly", "草稿"],
        ],
      },
      主题域: {
        description: "主题域用于在数据集市内进一步组织具体分析主题，并约束应用表和指标看板的归属。",
        fields: [["英文缩写", ""], ["英文名", ""], ["中文名", ""]],
        action: "新建主题域",
        headers: ["英文缩写", "中文名", "英文名", "数据集市", "状态"],
        rows: [
          ["budget_cockpit", "预算驾驶舱", "budget cockpit", "财务分析集市", "已发布"],
          ["project_progress", "项目进展", "project progress", "项目管理集市", "草稿"],
        ],
      },
      建模空间: {
        description: "建模空间用于隔离不同团队的规划、标准和模型对象，并维护成员与默认数据源。",
        fields: [["空间标识", ""], ["空间名称", ""], ["负责人", ""]],
        action: "新建建模空间",
        headers: ["空间标识", "空间名称", "负责人", "数据源", "成员数", "状态"],
        rows: [
          ["default_workspace", "默认工作空间", "xzmfly", "DTS Demo PostgreSQL", "3", "启用"],
          ["finance_demo", "财务 Demo 空间", "xzmfly", "财务 Demo 数据源", "2", "启用"],
        ],
      },
      系统管理: {
        description: "系统管理集中维护规划模块的默认规则、初始化对象和编码生成策略。",
        fields: [["配置编码", ""], ["配置名称", ""], ["配置值", ""]],
        action: "新增配置",
        headers: ["配置编码", "配置名称", "配置值", "作用范围", "状态"],
        rows: [
          ["DEFAULT_DOMAIN", "默认数据域", "default", "租户", "启用"],
          ["MODEL_CODE_RULE", "模型编码规则", "{layer}_{subject}", "工作空间", "启用"],
          ["AUTO_SYSTEM_OBJECT", "自动创建系统对象", "true", "租户", "启用"],
        ],
      },
    };
    const page = pages[selected] || pages.业务过程;
    return `
      <div class="planning-page">
        <header class="planning-hero">
          <h1>${selected}</h1>
          <p>${page.description}</p>
        </header>
        <div class="planning-page-scroll">
          <section class="planning-entity-panel">
            <div class="planning-filter-form">
              ${page.fields
                .map(
                  ([label, value]) =>
                    `<label><span>${label}：</span><input value="${escapeHtml(value)}" /></label>`,
                )
                .join("")}
            </div>
            <button class="planning-create-button" data-planning-create="${selected}">${page.action}</button>
            ${renderPlanningTable(page.headers, page.rows)}
          </section>
        </div>
      </div>
    `;
  }

  function renderPlanningTable(headers, rows) {
    return `
      <table class="planning-table">
        <thead><tr>${headers.map((header) => `<th>${header}</th>`).join("")}</tr></thead>
        <tbody>
          ${rows
            .map(
              (row) =>
                `<tr>${row
                  .map(
                    (cell, index) =>
                      `<td>${index === 0 ? `<span>${escapeHtml(cell)}</span>${["default", "fin_default", "ODS", "DIM", "DWD", "DWS", "ADS"].includes(cell) ? '<b class="system-badge">系统</b>' : ""}` : renderStatusCell(cell)}</td>`,
                  )
                  .join("")}</tr>`,
            )
            .join("")}
        </tbody>
      </table>
    `;
  }

  function renderStandards() {
    const selected = state.workspaceSub || data.standards.nav[0][0];
    return `
      ${renderPageHeader(
        selected,
        "集中维护可复用的数据定义、类型、代码与命名约束。",
        '<button class="small-button" data-modal-open="import">导入标准</button><button class="tool-button primary" data-work-action="new-standard">＋ 新建标准</button>',
      )}
      <div class="workspace-scroll">
        <section class="work-panel">
          ${renderListToolbar("搜索标准编码或中文名称", ["全部分类", "全部状态"])}
          ${renderTable(["标准编码", "标准名称", "数据类型", "业务定义", "分类", "版本", "状态"], data.standards.rows, true)}
        </section>
      </div>
    `;
  }

  function renderMetrics() {
    const type = metricType();
    const currentRow = (data.metrics.catalog[type] || []).find((row) => row[1] === state.metricCurrent);
    const title = currentRow ? currentRow[2] : `新建${type}`;
    const configs = {
      原子指标: [
        [
          "原子指标基本信息",
          [
            ["英文缩写", currentRow?.[1] || "", "input", true, "英文缩写是指标的唯一性标识，一经保存，无法二次修改。"],
            ["英文名称", currentRow ? `${currentRow[1]} metric` : "", "input"],
            ["中文名称", currentRow?.[2] || "", "input", true],
            ["业务口径", currentRow ? `${currentRow[2]}的统一业务统计口径。` : "", "textarea", true, "输入 @ 可以引用其他原子指标"],
            ["业务分类", "财务管理", "select"],
            ["业务过程", "预算执行", "select", true],
            ["负责人", "xzmfly@163.com", "select", true],
            ["描述", "", "textarea"],
          ],
        ],
        [
          "计算逻辑",
          [
            ["计算函数", "SUM", "select"],
            ["小数位数", "0", "input"],
            ["数据单位", "元", "select"],
            ["是否去重", "否", "radio"],
          ],
        ],
      ],
      派生指标: [
        [
          "派生指标基本信息",
          [
            ["英文缩写", currentRow?.[1] || "", "input", true, "英文缩写保存后不可修改。"],
            ["英文名称", "", "input"],
            ["中文名称", currentRow?.[2] || "", "input", true],
            ["原子指标", "executed_amount", "select", true],
            ["业务分类", "财务管理", "select"],
            ["负责人", "xzmfly@163.com", "select", true],
            ["描述", "", "textarea"],
          ],
        ],
        [
          "派生规则",
          [
            ["时间周期", "月", "select", true],
            ["修饰词", "累计", "select"],
            ["统计粒度", "项目 + 预算科目", "select", true],
            ["计算表达式", "executed_amount / budget_amount", "textarea", true],
          ],
        ],
      ],
      复合指标: [
        [
          "复合指标基本信息",
          [
            ["英文缩写", currentRow?.[1] || "", "input", true, "英文缩写保存后不可修改。"],
            ["英文名称", "", "input"],
            ["中文名称", currentRow?.[2] || "", "input", true],
            ["业务分类", "财务管理", "select"],
            ["负责人", "xzmfly@163.com", "select", true],
            ["描述", "", "textarea"],
          ],
        ],
        [
          "计算逻辑",
          [
            ["依赖指标", "monthly_execution_rate, project_budget_variance", "select", true],
            ["计算表达式", "0.6 * monthly_execution_rate + 0.4 * variance_score", "textarea", true],
            ["小数位数", "2", "input"],
            ["数据单位", "分", "select"],
          ],
        ],
      ],
      修饰词: [
        [
          "修饰词基本信息",
          [
            ["英文缩写", currentRow?.[1] || "", "input", true],
            ["英文名称", "", "input"],
            ["中文名称", currentRow?.[2] || "", "input", true],
            ["修饰词类型", "统计方式", "select", true],
            ["负责人", "xzmfly@163.com", "select", true],
            ["描述", "", "textarea"],
          ],
        ],
        [
          "应用范围",
          [
            ["数据域", "全部数据域", "select"],
            ["适用指标类型", "原子指标、派生指标", "select"],
            ["排序", "10", "input"],
          ],
        ],
      ],
      时间周期: [
        [
          "时间周期基本信息",
          [
            ["英文缩写", currentRow?.[1] || "", "input", true],
            ["英文名称", "", "input"],
            ["中文名称", currentRow?.[2] || "", "input", true],
            ["周期类型", "自然周期", "select", true],
            ["负责人", "xzmfly@163.com", "select", true],
            ["描述", "", "textarea"],
          ],
        ],
        [
          "周期规则",
          [
            ["时间粒度", type === "时间周期" ? "月" : "", "select", true],
            ["开始偏移", "0", "input"],
            ["结束偏移", "0", "input"],
            ["日期格式", "YYYY-MM", "select"],
          ],
        ],
      ],
    };
    return `
      <div class="metric-editor-tabs">
        <div class="metric-editor-tab"><span>△</span><strong>${title}</strong><button>×</button></div>
      </div>
      <div class="metric-editor-toolbar">
        <button data-metric-save="save">▣ 保存</button>
        <button data-metric-save="submit">⇧ 提交</button>
      </div>
      <div class="metric-editor-scroll">
        ${(configs[type] || configs.原子指标)
          .map(
            ([sectionTitle, fields]) => `
              <section class="metric-form-section">
                <h3>${sectionTitle}</h3>
                <div class="metric-form">
                  ${fields.map((field) => renderMetricField(field)).join("")}
                </div>
              </section>`,
          )
          .join("")}
      </div>
    `;
  }

  function renderMetricField([label, value, kind, required = false, note = ""]) {
    const placeholder = value ? "" : `请输入${label}`;
    let control = `<input value="${escapeHtml(value)}" placeholder="${placeholder}" />`;
    if (kind === "select") {
      control = `<select><option>${escapeHtml(value || `请选择${label}`)}</option><option>请选择</option></select>`;
    } else if (kind === "textarea") {
      control = `<textarea placeholder="${placeholder}">${escapeHtml(value)}</textarea>`;
    } else if (kind === "radio") {
      control = `<div class="metric-radio"><label><input type="radio" name="${label}" ${value === "是" ? "checked" : ""} /> 是</label><label><input type="radio" name="${label}" ${value !== "是" ? "checked" : ""} /> 否</label></div>`;
    }
    return `
      <div class="metric-form-row ${kind === "textarea" ? "textarea-row" : ""}">
        <label class="${required ? "metric-required" : ""}">${label}：</label>
        <div>${control}${note ? `<p class="${label === "英文缩写" ? "metric-warning" : "metric-note"}">${label === "英文缩写" ? "⚠ " : ""}${note}</p>` : ""}</div>
      </div>
    `;
  }

  function renderTools() {
    return `
      ${renderPageHeader("通用工具", "为批量建模、命名检查和交付提供轻量工具。")}
      <div class="workspace-scroll">
        <section class="tool-grid">
          ${data.tools
            .map(
              ([name, icon, description, action]) => `
                <article class="tool-card">
                  <span class="tool-symbol">${icon}</span>
                  <div><h2>${name}</h2><p>${description}</p></div>
                  <button class="small-button" data-tool-action="${name}">${action}</button>
                </article>`,
            )
            .join("")}
        </section>
        <section class="work-panel" style="margin-top:14px">
          <div class="work-panel-header"><h2>最近执行</h2></div>
          ${renderTable(
            ["任务", "工具", "对象", "状态", "执行人", "时间"],
            [
              ["IMPORT-20260730-03", "Excel 字段识别", "budget.xlsx", "成功", "xzmfly", "17:08"],
              ["CHECK-20260730-02", "FML 校验器", "dim_budget_account", "成功", "xzmfly", "16:42"],
              ["EXPORT-20260730-01", "模型批量导出", "财务域", "成功", "xzmfly", "16:10"],
            ],
          )}
        </section>
      </div>
    `;
  }

  function renderGraph() {
    return `
      ${renderPageHeader(
        state.workspaceSub || "模型关系",
        "查看规划、标准、模型、指标与应用之间的引用和交付关系。",
        '<button class="small-button" data-graph-action="fit">适应画布</button><button class="small-button" data-graph-action="export">导出图片</button>',
      )}
      <div class="graph-toolbar">
        <select><option>财务域</option><option>项目域</option></select>
        <label><input type="checkbox" checked /> 业务规划</label>
        <label><input type="checkbox" checked /> 数据标准</label>
        <label><input type="checkbox" checked /> 逻辑模型</label>
        <label><input type="checkbox" checked /> 数据指标</label>
        <span></span>
        <button class="icon-button">−</button><button class="icon-button">＋</button><button class="icon-button">⛶</button>
      </div>
      <div class="relation-workspace">
        <svg class="graph-lines" viewBox="0 0 1200 680" preserveAspectRatio="none" aria-hidden="true">
          <path d="M190 338 C260 338 250 160 340 160" />
          <path d="M190 338 C260 338 250 338 340 338" />
          <path d="M190 338 C260 338 250 520 340 520" />
          <path d="M510 160 C590 160 570 250 650 250" />
          <path d="M510 338 C590 338 570 250 650 250" />
          <path d="M510 338 C590 338 570 430 650 430" />
          <path d="M510 520 C590 520 570 430 650 430" />
          <path d="M820 250 C900 250 890 338 970 338" />
          <path d="M820 430 C900 430 890 338 970 338" />
        </svg>
        <button class="graph-node domain" style="left:40px;top:288px" data-workspace="planning"><small>数据域</small><strong>财务域</strong><span>2 个业务过程</span></button>
        <button class="graph-node standard" style="left:340px;top:110px" data-workspace="standards"><small>字段标准</small><strong>DATE_KEY</strong><span>日期业务键</span></button>
        <button class="graph-node standard" style="left:340px;top:288px" data-workspace="standards"><small>字段标准</small><strong>ACCOUNT_CODE</strong><span>预算科目编码</span></button>
        <button class="graph-node standard" style="left:340px;top:470px" data-workspace="standards"><small>字段标准</small><strong>AMOUNT</strong><span>财务金额</span></button>
        <button class="graph-node model" style="left:650px;top:200px" data-workspace="modeling"><small>维度表</small><strong>dim_budget_account</strong><span>预算科目维度表</span></button>
        <button class="graph-node model" style="left:650px;top:380px" data-workspace="modeling"><small>明细表</small><strong>fct_budget_execution</strong><span>预算执行明细表</span></button>
        <button class="graph-node metric" style="left:970px;top:288px" data-workspace="metrics"><small>派生指标</small><strong>monthly_execution_rate</strong><span>月度预算执行率</span></button>
      </div>
    `;
  }

  function renderListToolbar(placeholder, filters) {
    return `
      <div class="list-toolbar">
        <input placeholder="${placeholder}" />
        ${filters.map((filter) => `<select><option>${filter}</option></select>`).join("")}
        <button class="small-button">刷新</button>
        <span>共 ${state.workspace === "standards" ? data.standards.rows.length : state.workspace === "metrics" ? data.metrics.rows.length : 2} 条</span>
      </div>
    `;
  }

  function renderTable(headers, rows, actionable = false) {
    return `
      <table class="workspace-table">
        <thead><tr>${headers.map((header) => `<th>${header}</th>`).join("")}${actionable ? "<th>操作</th>" : ""}</tr></thead>
        <tbody>${rows
          .map(
            (row) =>
              `<tr>${row
                .map((cell, index) => `<td>${index === 0 && actionable ? `<button class="table-link" data-row-open="${escapeHtml(cell)}">${escapeHtml(cell)}</button>` : renderStatusCell(cell)}</td>`)
                .join("")}${actionable ? '<td><button class="link-button" data-work-action="edit">编辑</button></td>' : ""}</tr>`,
          )
          .join("")}</tbody>
      </table>
    `;
  }

  function renderStatusCell(value) {
    if (["已发布", "已生效", "成功"].includes(value)) return `<span class="state-tag success">${value}</span>`;
    if (["草稿", "待处理"].includes(value)) return `<span class="state-tag draft">${value}</span>`;
    return escapeHtml(value);
  }

  function renderModuleRail() {
    return `
      <aside class="module-rail">
        <button class="module-item ${state.section === "modeling" ? "active" : ""}" data-section="modeling">
          <span class="module-icon">◇</span><span>维度建模</span>
        </button>
        <button class="module-item ${state.section === "reverse" ? "active" : ""}" data-section="reverse">
          <span class="module-icon">↩</span><span>逆向建模</span>
        </button>
        <span class="collapse">◁</span>
      </aside>
    `;
  }

  function renderObjectPanel() {
    return `
      <aside class="object-panel">
        <div class="object-header">
          <h2>维度建模</h2>
          <div class="iconbar">
            ${Object.entries(icons)
              .map(
                ([name, icon]) =>
                  `<button class="icon-button" data-tree-action="${name}" title="${treeActionTitle(name)}">${icon}</button>`,
              )
              .join("")}
          </div>
        </div>
        <div class="layer-tabs">
          ${["贴源层", "公共层", "应用层"]
            .map(
              (layer) =>
                `<button class="${state.layer === layer ? "active" : ""}" data-layer="${layer}">${layer}</button>`,
            )
            .join("")}
        </div>
        <div class="filter"><select><option>🌐 请选择数据域</option><option>财务域</option><option>项目域</option></select></div>
        <div class="search"><input id="tree-search" placeholder="搜索" /></div>
        <div class="tree">
          ${data.domains
            .map(
              (domain) => `
                <div class="tree-domain">
                  <button class="tree-domain-title"><span>›</span><span>🌐</span><span>${domain.name}</span><span>(${domain.count})</span></button>
                  ${domain.nodes
                    .map(
                      ([type, code, name]) => `
                        <button class="tree-node ${state.currentCode === code ? "active" : ""}" data-model-node="${type}|${code}|${name}">
                          <span>▦</span><span class="node-code" title="${name}">${code}</span>
                        </button>`,
                    )
                    .join("")}
                </div>`,
            )
            .join("")}
        </div>
        ${
          state.createMenu
            ? `<div class="create-menu">
                <div class="menu-group">概念模型</div>
                <button class="menu-item" data-create-type="dimension">创建维度</button>
                <div class="menu-group">逻辑模型</div>
                ${[
                  ["source", "创建贴源表"],
                  ["dimension-table", "创建维度表"],
                  ["fact", "创建明细表"],
                  ["aggregate", "创建汇总表"],
                  ["application", "创建应用表"],
                ]
                  .map(([type, label]) => `<button class="menu-item" data-create-type="${type}">${label}</button>`)
                  .join("")}
              </div>`
            : ""
        }
      </aside>
    `;
  }

  function treeActionTitle(name) {
    return { plus: "新建", import: "导入", export: "导出", display: "显示设置", refresh: "刷新" }[name];
  }

  function renderEditor() {
    if (!state.currentType) {
      return `<div class="editor-tabs"></div><div class="empty"><div class="empty-card"><div class="empty-icon">▤</div><p>请点击左侧文件开始编辑</p></div></div>`;
    }

    const current = model();
    return `
      <div class="editor-tabs">
        <div class="editor-tab"><span style="color:#ff8a00">▤</span><span>${escapeHtml(state.currentName || current.label)}</span><span style="color:#8b929d">×</span></div>
      </div>
      <div class="toolbar">
        ${current.toolbar
          .map(
            (item) =>
              `<button class="tool-button ${item === "保存" ? "primary" : ""}" data-toolbar="${item}">${toolbarIcon(item)} ${item}</button>`,
          )
          .join("")}
        <span class="toolbar-spacer"></span>
        <span class="version-state"><span class="status-dot"></span>当前版本：草稿 v1　未发布</span>
      </div>
      <div class="editor-scroll">
        ${renderBasicInfo(current)}
        ${state.currentType === "dimension" ? "" : renderFieldManagement()}
      </div>
    `;
  }

  function toolbarIcon(label) {
    return (
      {
        保存: "▣",
        提交: "⇧",
        刷新: "↻",
        关联关系: "⌘",
        发布: "▷",
        日志: "▤",
        质量规则: "♢",
        模型开发: "↗",
        导出: "⇧",
      }[label] || ""
    );
  }

  function renderBasicInfo(current) {
    return `
      <section class="panel">
        <h3 class="panel-title">基本信息</h3>
        <div class="basic-grid">
          ${current.fields
            .map(([label, value, kind, required]) => renderControl(label, value, kind, required))
            .join("")}
        </div>
      </section>
    `;
  }

  function renderControl(label, value, kind, required) {
    const full = kind === "textarea";
    let control = `<input class="control" value="${escapeHtml(value)}" />`;
    if (kind === "select") {
      control = `<select class="control"><option>${escapeHtml(value)}</option><option>请选择</option></select>`;
    } else if (kind === "textarea") {
      control = `<textarea class="control">${escapeHtml(value)}</textarea>`;
    } else if (kind === "number") {
      control = `<div style="display:flex;align-items:center;gap:8px"><input class="control" type="number" value="${escapeHtml(value)}" /><span>天</span></div>`;
    }
    return `<div class="form-row ${full ? "full" : ""}"><label class="${required ? "required" : ""}">${label}：</label>${control}</div>`;
  }

  function renderFieldManagement() {
    return `
      <section class="panel">
        <h3 class="panel-title">字段管理</h3>
        <div class="mode-switch">
          <button class="active">快捷模式</button>
          <button data-modal-open="code">代码模式</button>
        </div>
        <div class="import-row">
          <span>从表/视图导入</span>
          <button class="link-button" data-modal-open="field-import">展开 ›</button>
          <button class="link-button" data-modal-open="association" style="margin-left:auto">字段关联</button>
        </div>
        <div class="table-tools">
          <button class="small-button" data-remove-empty>移除空白行</button>
          <button class="small-button" data-insert-row>插入</button>
          <input class="control" style="width:54px;height:29px;min-height:29px" value="1" />
          <span>行</span>
          <button class="small-button right" data-modal-open="display">字段显示设置</button>
        </div>
        ${renderFieldTable()}
      </section>
      <section class="panel">
        <h3 class="panel-title">分区字段管理</h3>
        <div class="table-tools">
          <button class="small-button">移除空白行</button><button class="small-button">插入</button>
          <input class="control" style="width:54px;height:29px;min-height:29px" value="1" /><span>行</span>
          <button class="small-button right" data-modal-open="display">字段显示设置</button>
        </div>
        <table class="field-table">
          <thead><tr><th style="width:45px">序号</th><th>字段名称</th><th>类型</th><th>字段显示名</th><th style="width:70px">主键</th><th style="width:70px">非空</th></tr></thead>
          <tbody><tr><td>1</td><td>ds</td><td>STRING</td><td>业务日期, yyyymmdd</td><td></td><td><input class="checkbox" type="checkbox" checked /></td></tr></tbody>
        </table>
      </section>
    `;
  }

  function renderFieldTable() {
    const rows = state.fieldRows.length ? state.fieldRows : [["", "STRING", "", false, false, ""]];
    return `
      <table class="field-table">
        <thead>
          <tr>
            <th style="width:45px">序号</th><th>字段名称</th><th style="width:130px">类型</th><th>字段显示名</th>
            <th style="width:66px">主键</th><th style="width:66px">非空</th><th>维度属性编码</th><th style="width:72px">操作</th>
          </tr>
        </thead>
        <tbody>
          ${rows
            .map(
              (row, index) => `
                <tr>
                  <td>${index + 1}</td>
                  <td><input value="${escapeHtml(row[0])}" /></td>
                  <td><select><option>${escapeHtml(row[1])}</option><option>STRING</option><option>BIGINT</option><option>DECIMAL(18,2)</option></select></td>
                  <td><input value="${escapeHtml(row[2])}" /></td>
                  <td style="text-align:center"><input class="checkbox" type="checkbox" ${row[3] ? "checked" : ""} /></td>
                  <td style="text-align:center"><input class="checkbox" type="checkbox" ${row[4] ? "checked" : ""} /></td>
                  <td><input value="${escapeHtml(row[5])}" placeholder="选择属性编码" /></td>
                  <td><button class="link-button" data-delete-row="${index}">删除</button></td>
                </tr>`,
            )
            .join("")}
        </tbody>
      </table>
    `;
  }

  function renderRightRail() {
    if (state.section !== "modeling" || !state.currentType) return "";
    return `
      <aside class="right-rail">
        <button data-modal-open="versions">版本管理</button>
        <button data-modal-open="releases">发布记录</button>
      </aside>
    `;
  }

  function renderReverse() {
    if (!state.reverseWizard) {
      return `
        <div class="editor-tabs"></div>
        <div class="reverse">
          <div class="reverse-card">
            <div class="empty-icon">↩</div>
            <h2>逆向建模</h2>
            <p>可以帮助您快速将已创建的数据表生成规范的数仓模型。</p>
            <button class="tool-button primary" data-reverse-start>快速开始</button>
          </div>
        </div>
      `;
    }
    return `
      <div class="editor-tabs"><div class="editor-tab">建模列表　/　逆向建模</div></div>
      <div class="editor-scroll" style="height:calc(100% - 40px)">
        <section class="panel" style="max-width:980px;margin:20px auto">
          <h2 style="margin:0 0 24px">逆向建模</h2>
          <div class="stepper">
            ${["逆向策略", "确认模型信息", "生成模型", "完成"]
              .map(
                (name, index) =>
                  `<div class="step ${index === 0 ? "active" : ""}"><span class="step-number">${index + 1}</span><span>${name}</span></div>`,
              )
              .join("")}
          </div>
          <div class="basic-grid">
            ${renderControl("项目空间", "默认工作空间", "select", true)}
            ${renderControl("数据源类型", "PostgreSQL", "select", true)}
            ${renderControl("数据源名称", "dts_demo", "select", true)}
            ${renderControl("表名匹配规则", "模糊匹配", "select", true)}
            ${renderControl("模型所在分层", "贴源层", "select", true)}
            ${renderControl("表命名规范", "表名检查器", "select", true)}
            ${renderControl("执行方式", "增量更新", "select", true)}
          </div>
          <div style="margin-top:24px;text-align:right">
            <button class="small-button" data-reverse-cancel>取消</button>
            <button class="tool-button primary" data-reverse-next>开始创建模型</button>
          </div>
        </section>
      </div>
    `;
  }

  function renderModal() {
    if (!state.modal) return "";
    const contents = {
      display: {
        title: "字段显示设置",
        body: `<div class="display-options">${[
          "序号",
          "字段名称",
          "类型",
          "字段显示名",
          "描述",
          "主键",
          "非空",
          "来源表",
          "来源字段",
          "字段类别",
          "关联字段标准",
          "关联标准代码",
          "操作",
        ]
          .map((item, index) => `<label><input class="checkbox" type="checkbox" ${index < 6 || item === "操作" ? "checked" : ""} /> ${item}</label>`)
          .join("")}</div>`,
      },
      association: {
        title: "设置字段关联",
        wide: true,
        body: `<div class="association-canvas">
          <div style="position:absolute;left:18px;top:18px;width:125px;padding:12px;border:1px solid var(--border);background:#fff">
            <strong>类型图例</strong><hr style="border:0;border-top:1px solid var(--border)" />
            <p>△ 原子指标</p><p>▧ 标准代码</p><p>⌞ 字段标准</p>
          </div>
          <div class="relation-line"></div>
          <div class="relation-node left"><strong>${escapeHtml(currentModelCode())}</strong><br /><span style="color:var(--muted)">${escapeHtml(state.currentName)}</span></div>
          <div class="relation-node right"><strong>account_code</strong><br /><span style="color:var(--muted)">科目编码 · ACCOUNT_CODE</span></div>
        </div>`,
      },
      "field-import": {
        title: "从表/视图导入字段",
        body: `<div class="basic-grid">
          ${renderControl("数据源", "财务 Demo 数据源", "select", true)}
          ${renderControl("源表", "ods_budget_execution", "select", true)}
        </div>
        <table class="field-table" style="margin-top:20px"><thead><tr><th style="width:55px">选择</th><th>源字段</th><th>类型</th><th>字段说明</th></tr></thead>
        <tbody>${[
          ["project_no", "varchar(500)", "项目号"],
          ["budget_no", "varchar(500)", "预算编号"],
          ["budget_amount_adjusted", "varchar(500)", "预算金额（调整后）"],
          ["book_cost_amount", "varchar(500)", "账面成本"],
        ]
          .map(([field, type, name]) => `<tr><td><input class="checkbox" type="checkbox" checked /></td><td>${field}</td><td>${type}</td><td>${name}</td></tr>`)
          .join("")}</tbody></table>`,
      },
      code: {
        title: "代码模式（FML）",
        body: `<textarea class="control" style="height:390px;font-family:ui-monospace,monospace;background:#172033;color:#d9e2f1">TABLE ${escapeHtml(
          currentModelCode(),
        )} {\n  account_code STRING NOT NULL PRIMARY KEY COMMENT '科目编码';\n  account_name STRING NOT NULL COMMENT '科目名称';\n  account_category STRING COMMENT '科目类别';\n  enabled_flag BOOLEAN NOT NULL COMMENT '是否启用';\n  ds STRING NOT NULL COMMENT '业务日期, yyyymmdd';\n}</textarea>`,
      },
      publish: {
        title: "发布与物化",
        wide: true,
        body: renderPublishBody(),
        footer: false,
      },
      import: {
        title: "导入模型",
        body: `<div class="basic-grid">${renderControl("导入类型", "Excel 模板", "select", true)}${renderControl(
          "目标分层",
          state.layer,
          "select",
          true,
        )}</div><div style="height:150px;margin-top:18px;display:grid;place-items:center;border:1px dashed #aab4c3;color:var(--muted)">拖拽文件到此处，或点击选择文件</div>`,
      },
      export: {
        title: "导出模型",
        body: `<div class="basic-grid">${renderControl("导出范围", "当前数据域", "select", true)}${renderControl(
          "文件格式",
          "Excel",
          "select",
          true,
        )}</div>`,
      },
      versions: {
        title: "版本管理",
        body: renderRecordTable([
          ["草稿 v1", "当前编辑版本", "xzmfly", "刚刚"],
          ["r2", "已发布", "xzmfly", "2026-07-30 09:32"],
        ]),
      },
      releases: {
        title: "发布记录",
        body: renderRecordTable([
          ["REL-20260730-002", "发布成功", "xzmfly", "2026-07-30 09:35"],
          ["REL-20260729-001", "物化成功", "xzmfly", "2026-07-29 17:10"],
        ]),
      },
      logs: {
        title: "操作日志",
        body: renderRecordTable([
          ["字段设计", "保存成功", "xzmfly", "刚刚"],
          ["逻辑定义", "保存成功", "xzmfly", "2026-07-30 09:30"],
        ]),
      },
      quality: {
        title: "质量规则",
        body: `<table class="field-table"><thead><tr><th>规则名称</th><th>规则类型</th><th>字段</th><th>状态</th></tr></thead><tbody><tr><td>科目编码非空</td><td>完整性</td><td>account_code</td><td>已启用</td></tr><tr><td>科目编码唯一</td><td>唯一性</td><td>account_code</td><td>已启用</td></tr></tbody></table>`,
      },
    };
    const current = contents[state.modal] || { title: state.modal, body: `<p>原型占位页面</p>` };
    const hasCustomFooter = current.footer === false;
    return `
      <div class="modal-backdrop" data-close-backdrop>
        <section class="modal ${current.wide ? "wide" : ""}" role="dialog" aria-modal="true">
          <header class="modal-header"><h3>${current.title}</h3><button class="modal-close" data-modal-close>×</button></header>
          <div class="modal-body">${current.body}</div>
          ${
            hasCustomFooter
              ? ""
              : `<footer class="modal-footer"><button class="small-button" data-modal-close>取消</button><button class="tool-button primary" data-modal-confirm>确认</button></footer>`
          }
        </section>
      </div>
    `;
  }

  function renderRecordTable(rows) {
    return `<table class="field-table"><thead><tr><th>版本/记录</th><th>状态</th><th>操作人</th><th>时间</th></tr></thead><tbody>${rows
      .map((row) => `<tr>${row.map((cell) => `<td>${cell}</td>`).join("")}</tr>`)
      .join("")}</tbody></table>`;
  }

  function renderPublishBody() {
    const isPublish = state.publishTab === "publish";
    return `
      <div class="publish-grid">
        <nav class="publish-nav">
          <button class="${isPublish ? "active" : ""}" data-publish-tab="publish">发布模型</button>
          <button class="${!isPublish ? "active" : ""}" data-publish-tab="materialize">生成物化任务</button>
        </nav>
        <div class="publish-content">
          ${
            isPublish
              ? `<h3>发布检查</h3>
                <dl class="summary-list">
                  <dt>模型</dt><dd>${escapeHtml(state.currentName)}</dd>
                  <dt>目标分层</dt><dd>${escapeHtml(model().layer)}</dd>
                  <dt>检查结果</dt><dd><span style="color:#1d8b4c">✓ 基础信息、字段映射和业务主键检查通过</span></dd>
                  <dt>版本说明</dt><dd><input class="control" value="财务 Demo 首次发布" /></dd>
                </dl>
                <div style="margin-top:28px;text-align:right"><button class="small-button" data-modal-close>取消</button> <button class="tool-button primary" data-publish-submit>发布</button></div>`
              : `<h3>生成物化任务</h3>
                <div class="basic-grid">
                  ${renderControl("目标数据源", "DTS Demo PostgreSQL", "select", true)}
                  ${renderControl("目标 Schema", "dwd", "select", true)}
                  ${renderControl("运行方式", "立即运行", "select", true)}
                  ${renderControl("写入策略", "覆盖目标表", "select", true)}
                </div>
                <dl class="summary-list" style="margin-top:24px"><dt>物化对象</dt><dd>${escapeHtml(
                  currentModelCode(),
                )}</dd><dt>依赖检查</dt><dd><span style="color:#1d8b4c">✓ 上游模型与维度引用可用</span></dd></dl>
                <div style="margin-top:28px;text-align:right"><button class="small-button" data-modal-close>取消</button> <button class="tool-button primary" data-materialize-submit>创建并运行</button></div>`
          }
        </div>
      </div>
    `;
  }

  function setToast(message) {
    state.toast = message;
    render();
    window.clearTimeout(setToast.timer);
    setToast.timer = window.setTimeout(() => {
      state.toast = "";
      render();
    }, 2200);
  }

  app.addEventListener("click", (event) => {
    const target = event.target.closest("button, [data-close-backdrop]");
    if (!target) return;

    if (target.dataset.workspace) {
      state.workspace = target.dataset.workspace;
      state.workspaceSub = "";
      state.metricCurrent = "";
      state.createMenu = false;
      state.modal = "";
      render();
      return;
    }
    if (target.dataset.metricType) {
      state.workspaceSub = target.dataset.metricType;
      state.metricCurrent = "";
      render();
      return;
    }
    if (target.dataset.metricAction) {
      if (target.dataset.metricAction === "new") {
        state.metricCurrent = "";
        render();
      } else {
        const messages = {
          import: `${metricType()}导入入口已打开`,
          display: "目录显示设置已应用",
          refresh: "指标目录已刷新",
        };
        setToast(messages[target.dataset.metricAction] || "操作已执行");
      }
      return;
    }
    if (target.dataset.metricNode) {
      state.metricCurrent = target.dataset.metricNode.split("|")[0];
      render();
      return;
    }
    if (target.dataset.metricSave) {
      setToast(target.dataset.metricSave === "save" ? `${metricType()}草稿已保存` : `${metricType()}已提交校验`);
      return;
    }
    if (target.dataset.planningToggle) {
      const key = target.dataset.planningToggle;
      state.planningGroups[key] = !state.planningGroups[key];
      render();
      return;
    }
    if (target.dataset.planningCreate) {
      setToast(`${target.dataset.planningCreate}已进入新建状态`);
      return;
    }
    if (target.dataset.workspaceSub) {
      state.workspaceSub = target.dataset.workspaceSub;
      render();
      return;
    }
    if (target.dataset.workAction) {
      const messages = {
        "new-planning": "已打开规划对象新建入口",
        "new-standard": "已打开字段标准新建入口",
        "new-metric": "已打开指标新建入口",
        edit: "已进入对象编辑模式",
      };
      setToast(messages[target.dataset.workAction] || "操作已执行");
      return;
    }
    if (target.dataset.toolAction) {
      setToast(`${target.dataset.toolAction}已打开`);
      return;
    }
    if (target.dataset.graphAction) {
      setToast(target.dataset.graphAction === "fit" ? "画布已适应窗口" : "关系图导出任务已创建");
      return;
    }
    if (target.dataset.rowOpen) {
      setToast(`正在查看：${target.dataset.rowOpen}`);
      return;
    }
    if (target.dataset.section) {
      state.section = target.dataset.section;
      state.createMenu = false;
      render();
      return;
    }
    if (target.dataset.layer) {
      state.layer = target.dataset.layer;
      render();
      return;
    }
    if (target.dataset.treeAction === "plus") {
      state.createMenu = !state.createMenu;
      render();
      return;
    }
    if (target.dataset.treeAction === "import") {
      state.modal = "import";
      render();
      return;
    }
    if (target.dataset.treeAction === "export") {
      state.modal = "export";
      render();
      return;
    }
    if (target.dataset.treeAction === "refresh") {
      setToast("对象目录已刷新");
      return;
    }
    if (target.dataset.treeAction === "display") {
      setToast("目录显示设置已应用");
      return;
    }
    if (target.dataset.createType) {
      const type = target.dataset.createType;
      state.currentType = type;
      state.currentCode = "";
      state.currentName = data.modelTypes[type].label;
      state.fieldRows = JSON.parse(JSON.stringify(data.fields[type] || []));
      state.createMenu = false;
      render();
      return;
    }
    if (target.dataset.modelNode) {
      const [type, code, name] = target.dataset.modelNode.split("|");
      state.currentType = type;
      state.currentCode = code;
      state.currentName = name;
      state.fieldRows = JSON.parse(JSON.stringify(data.fields[type] || data.fields["dimension-table"]));
      render();
      return;
    }
    if (target.dataset.toolbar) {
      handleToolbar(target.dataset.toolbar);
      return;
    }
    if (target.dataset.modalOpen) {
      state.modal = target.dataset.modalOpen;
      render();
      return;
    }
    if (target.hasAttribute("data-modal-close")) {
      state.modal = "";
      render();
      return;
    }
    if (target.hasAttribute("data-close-backdrop") && target === event.target) {
      state.modal = "";
      render();
      return;
    }
    if (target.hasAttribute("data-modal-confirm")) {
      state.modal = "";
      setToast("设置已应用");
      return;
    }
    if (target.hasAttribute("data-insert-row")) {
      state.fieldRows.push(["", "STRING", "", false, false, ""]);
      render();
      return;
    }
    if (target.hasAttribute("data-remove-empty")) {
      state.fieldRows = state.fieldRows.filter((row) => row[0]);
      render();
      return;
    }
    if (target.dataset.deleteRow !== undefined) {
      state.fieldRows.splice(Number(target.dataset.deleteRow), 1);
      render();
      return;
    }
    if (target.hasAttribute("data-reverse-start")) {
      state.reverseWizard = true;
      render();
      return;
    }
    if (target.hasAttribute("data-reverse-cancel")) {
      state.reverseWizard = false;
      render();
      return;
    }
    if (target.hasAttribute("data-reverse-next")) {
      setToast("已进入模型确认步骤（原型不执行真实逆向任务）");
      return;
    }
    if (target.dataset.publishTab) {
      state.publishTab = target.dataset.publishTab;
      render();
      return;
    }
    if (target.hasAttribute("data-publish-submit")) {
      state.modal = "";
      setToast("模型发布任务已创建");
      return;
    }
    if (target.hasAttribute("data-materialize-submit")) {
      state.modal = "";
      setToast("物化任务已创建并进入运行队列");
    }
  });

  function handleToolbar(action) {
    const modalMap = {
      关联关系: "association",
      发布: "publish",
      日志: "logs",
      质量规则: "quality",
      模型开发: "code",
      导出: "export",
    };
    if (modalMap[action]) {
      state.modal = modalMap[action];
      if (action === "发布") state.publishTab = "publish";
      render();
      return;
    }
    if (action === "保存") setToast("当前输入已保留");
    else if (action === "提交") setToast("逻辑设计已提交校验");
    else if (action === "刷新") setToast("模型信息已刷新");
  }

  render();
})();
