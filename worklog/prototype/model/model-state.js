import { DEFAULT_FIELDS, DEFAULT_RELATIONS, MOCK_DIMENSIONS, MOCK_MODEL, MOCK_MODELS, MOCK_PROJECT_SPACES, MOCK_STANDARDS } from "./mock-data.js";

const clone = (value) => structuredClone(value);

export function createInitialState() {
  return {
    view: "ledger",
    step: 0,
    toast: null,
    activeModelId: null,
    savedModels: clone(MOCK_MODELS),
    model: clone(MOCK_MODEL),
    validation: null,
    publishResult: null,
  };
}

export function resetDraft(state) {
  state.view = "wizard";
  state.step = 0;
  state.activeModelId = null;
  state.model = clone(MOCK_MODEL);
  state.validation = null;
  state.publishResult = null;
  return state;
}

export function getProject(state) {
  return MOCK_PROJECT_SPACES.find((project) => project.id === state.model.projectId) ?? MOCK_PROJECT_SPACES[0];
}

export function getProcess(state) {
  return getProject(state);
}

export function getLedgerEntries(state, projectId = state.model.projectId) {
  const activeProjectId = projectId ?? MOCK_PROJECT_SPACES[0].id;
  const project = MOCK_PROJECT_SPACES.find((item) => item.id === activeProjectId) ?? MOCK_PROJECT_SPACES[0];
  const typeLabels = { dimension: "维度表", detail: "明细表", summary: "汇总表", application: "应用表" };
  return state.savedModels
    .filter((model) => (model.projectId || model.processId) === project.id)
    .map((model) => {
      const standardCount = model.standardBindings?.length ?? model.standardCount ?? 0;
      return {
        id: model.id,
        name: model.name,
        code: model.code,
        projectId: project.id,
        projectName: project.name,
        projectDomain: project.domain,
        layer: model.layer,
        modelType: typeLabels[model.type] ?? model.type,
        status: model.status,
        statusLabel: model.status === "published" ? "已发布" : "草稿",
        standardCount,
        standardStatus: standardCount > 0 ? "已登记" : "待补齐",
        grain: model.grain || "待补充粒度声明",
        updatedAt: model.updatedAt,
        links: {
          model: "#model/" + model.id,
          fields: "#standard/" + project.id,
          relations: "#relations/" + model.id,
        },
      };
    });
}

export function getWizardTasks(state) {
  const ledgerEntries = getLedgerEntries(state);
  return [
    {
      id: "data-prep",
      title: "数据准备",
      summary: "选择业务表、完成连接测试并确认数据源可用。",
      status: "blocked",
      statusLabel: "待接入",
      links: { primary: "#asset/table", secondary: "#ingestion/task" },
    },
    {
      id: "business-object",
      title: "业务对象确认",
      summary: "补齐项目空间、负责人和主题域，确认业务过程边界。",
      status: "ready",
      statusLabel: "可推进",
      links: { primary: "#project-spaces", secondary: "#asset/catalog" },
    },
    {
      id: "modeling",
      title: "数据建模",
      summary: ledgerEntries.length ? "已有模型台账，可继续补齐标准、粒度和关系。" : "当前项目空间还没有模型登记。",
      status: ledgerEntries.length ? "progress" : "blocked",
      statusLabel: ledgerEntries.length ? "已有台账" : "待建立",
      links: { primary: "#model-ledger", secondary: "#standard-registry" },
    },
    {
      id: "metrics",
      title: "指标设计",
      summary: "定义指标口径、维度、粒度和统计周期，形成可消费数据集。",
      status: "blocked",
      statusLabel: "待处理",
      links: { primary: "#metrics", secondary: "#reports" },
    },
  ];
}

export function getDimensions(state) {
  return MOCK_DIMENSIONS.filter((dimension) => state.model.dimensions.includes(dimension.id));
}

function hasCompleteStandardBindings(model) {
  return Boolean(model.standardBindings?.length >= 1 && model.fields?.length >= 1 && model.fields.every((field) => {
    const standard = MOCK_STANDARDS.find((item) => item.id === field.standard || item.name === field.standard || item.code === field.name);
    return Boolean(standard && model.standardBindings.includes(standard.id));
  }));
}

export function validateModel(model) {
  const checks = [
    {
      id: "context",
      label: "建模上下文",
      detail: "项目空间、数仓分层和来源数据集已确定",
      passed: Boolean(model.projectId && model.layer && model.datasetId && model.modelType),
      blocking: true,
    },
    {
      id: "standards",
      label: "数据标准",
      detail: "字段类型、长度、非空和默认值已有可引用标准",
      passed: hasCompleteStandardBindings(model),
      blocking: true,
    },
    {
      id: "identity",
      label: "模型基本信息",
      detail: "名称和英文编码符合建模规范",
      passed: Boolean(model.name?.trim() && /^[a-z][a-z0-9_]{3,63}$/.test(model.code ?? "")),
      blocking: true,
    },
    {
      id: "fields",
      label: "字段定义",
      detail: "至少包含一个业务主键和一个可分析字段",
      passed: Boolean(model.fields?.length >= 3 && model.fields.some((field) => field.role === "key") && model.fields.some((field) => ["measure", "attribute"].includes(field.role))),
      blocking: true,
    },
    {
      id: "grain",
      label: "粒度声明",
      detail: "已描述一行代表什么，并绑定粒度键",
      passed: Boolean(model.grainStatement?.trim().length >= 8 && model.grainKeys?.length >= 1),
      blocking: true,
    },
    {
      id: "dimensions",
      label: "一致性维度",
      detail: "至少复用一个登记维度，避免重复建设",
      passed: model.modelType === "dimension" || Boolean(model.dimensions?.length >= 1),
      blocking: false,
    },
    {
      id: "relations",
      label: "关系与分层红线",
      detail: "事实模型只依赖允许的公共维度，关联键完整",
      passed: model.modelType === "dimension" || Boolean(model.relations?.length >= 1 && model.relations.every((relation) => relation.on?.includes("="))),
      blocking: true,
    },
  ];

  const blockers = checks.filter((check) => check.blocking && !check.passed);
  const warnings = checks.filter((check) => !check.blocking && !check.passed);
  return {
    checks,
    blockers,
    warnings,
    passed: blockers.length === 0,
    score: Math.round(((checks.length - blockers.length - warnings.length * 0.5) / checks.length) * 100),
  };
}

export function saveDraft(state) {
  const validation = validateModel(state.model);
  state.model.savedAt = "刚刚";
  state.validation = validation;
  const draft = {
    ...clone(state.model),
    id: state.model.id ?? `draft-${Date.now()}`,
    status: "draft",
    version: "草稿",
    updatedAt: "刚刚",
    type: state.model.modelType,
    projectName: getProject(state).name,
    projectDomain: getProject(state).domain,
    processName: getProject(state).name,
    domain: getProject(state).domain,
    owner: "当前用户",
    quality: validation.score,
    fieldCount: state.model.fields.length,
    grain: state.model.grainStatement,
  };
  const index = state.savedModels.findIndex((model) => model.id === draft.id);
  if (index >= 0) state.savedModels[index] = draft;
  else state.savedModels.unshift(draft);
  state.model.id = draft.id;
  return state;
}

export function publishModel(state) {
  const validation = validateModel(state.model);
  state.validation = validation;
  if (!validation.passed) return { state, published: false };

  const version = "v1.0.0";
  state.model.status = "published";
  state.model.version = version;
  state.model.savedAt = "刚刚";
  const published = {
    ...clone(state.model),
    id: state.model.id ?? `model-${Date.now()}`,
    status: "published",
    version,
    updatedAt: "刚刚",
    type: state.model.modelType,
    projectName: getProject(state).name,
    projectDomain: getProject(state).domain,
    processName: getProject(state).name,
    domain: getProject(state).domain,
    owner: "当前用户",
    quality: validation.score,
    fieldCount: state.model.fields.length,
    grain: state.model.grainStatement,
  };
  const index = state.savedModels.findIndex((model) => model.id === published.id);
  if (index >= 0) state.savedModels[index] = published;
  else state.savedModels.unshift(published);
  state.model.id = published.id;
  state.publishResult = {
    modelId: published.id,
    modelCode: published.code,
    version,
    deployedLayer: published.layer,
    generatedAt: "刚刚",
    checks: validation.checks,
  };
  state.view = "published";
  return { state, published: true };
}

export function addField(state) {
  const nextIndex = state.model.fields.length + 1;
  state.model.fields.push({
    id: `field-new-${nextIndex}`,
    name: `new_field_${nextIndex}`,
    label: "待命名字段",
    type: "VARCHAR(128)",
    role: "attribute",
    standard: "待绑定标准",
    required: false,
  });
  return state;
}

export function removeField(state, fieldId) {
  state.model.fields = state.model.fields.filter((field) => field.id !== fieldId);
  return state;
}

export function toggleDimension(state, dimensionId) {
  state.model.dimensions = state.model.dimensions.includes(dimensionId)
    ? state.model.dimensions.filter((id) => id !== dimensionId)
    : [...state.model.dimensions, dimensionId];
  const dimension = MOCK_DIMENSIONS.find((item) => item.id === dimensionId);
  if (dimension && !state.model.dimensions.includes(dimensionId)) {
    state.model.relations = state.model.relations.filter((relation) => relation.targetId !== dimensionId);
  } else if (dimension && !state.model.relations.some((relation) => relation.targetId === dimensionId)) {
    state.model.relations.push({
      id: `rel-${dimensionId}`,
      targetId: dimensionId,
      targetModel: dimension.sourceModel,
      type: "many-to-one",
      on: `${dimension.keys[0]} = ${dimension.keys[0]}`,
      status: "valid",
    });
  }
  return state;
}

export function hydrateDemoModel(state) {
  state.model.fields = clone(DEFAULT_FIELDS);
  state.model.relations = clone(DEFAULT_RELATIONS);
  state.model.dimensions = ["dim-project", "dim-node-type", "dim-date"];
  return state;
}
