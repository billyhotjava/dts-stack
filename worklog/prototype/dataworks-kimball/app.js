(() => {
  const data = window.PROTOTYPE_DATA;
  const app = document.querySelector("#app");

  const state = {
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
            ${["首页", "数仓规划", "数据标准", "维度建模", "数据指标", "通用工具", "关系图"]
              .map((item) => `<button class="${item === "维度建模" ? "active" : ""}">${item}</button>`)
              .join("")}
          </nav>
          <div class="top-actions"><span class="agent-pill">✦ Data Agent</span><span>⌕</span><span>?</span><span>简体</span><span>xzmfly</span></div>
        </header>
        <main class="layout">
          ${renderModuleRail()}
          ${state.section === "modeling" ? renderObjectPanel() : '<div class="object-panel"></div>'}
          <section class="editor">
            ${state.section === "reverse" ? renderReverse() : renderEditor()}
          </section>
        </main>
        ${renderRightRail()}
        ${renderModal()}
        ${state.toast ? `<div class="toast">${escapeHtml(state.toast)}</div>` : ""}
      </div>
    `;
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
