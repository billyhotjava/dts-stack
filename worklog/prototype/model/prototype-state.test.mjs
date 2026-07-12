import test from "node:test";
import assert from "node:assert/strict";
import { createInitialState, getLedgerEntries, getWizardTasks, publishModel, saveDraft, toggleDimension, validateModel } from "./model-state.js";

test("project space is the authoritative business-process context", () => {
  const state = createInitialState();
  assert.equal(state.view, "ledger");
  assert.equal(state.model.projectId, "project-metro");
  assert.equal(state.model.projectName, "Metro");
  assert.equal(state.model.projectDomain, "地铁域");
});

test("unbound data standards block model publication", () => {
  const state = createInitialState();
  state.model.standardBindings = [];
  const result = validateModel(state.model);
  assert.equal(result.passed, false);
  assert.equal(result.blockers.some((item) => item.id === "standards"), true);
});

test("partial standard bindings block a model with ungoverned fields", () => {
  const state = createInitialState();
  state.model.standardBindings = ["std-project-id"];
  const result = validateModel(state.model);
  assert.equal(result.passed, false);
  assert.equal(result.blockers.some((item) => item.id === "standards"), true);
});

test("model ledger is scoped to the active project and exposes governance links", () => {
  const state = createInitialState();
  const entries = getLedgerEntries(state);
  assert.equal(entries.length, 2);
  assert.equal(entries.every((entry) => entry.projectId === "project-metro"), true);
  assert.equal(entries.every((entry) => entry.links.model && entry.links.fields && entry.links.relations), true);
  assert.equal(entries[0].standardStatus, "已登记");
});

test("low-code wizard is an overview that links to independent model work", () => {
  const state = createInitialState();
  const tasks = getWizardTasks(state);
  assert.deepEqual(tasks.map((task) => task.id), ["data-prep", "business-object", "modeling", "metrics"]);
  assert.equal(tasks.find((task) => task.id === "modeling").links.primary, "#model-ledger");
  assert.equal(tasks.every((task) => task.summary && task.statusLabel), true);
});

test("mock draft satisfies the complete publishing contract", () => {
  const state = createInitialState();
  const result = validateModel(state.model);
  assert.equal(result.passed, true);
  assert.equal(result.blockers.length, 0);
  assert.equal(result.checks.find((item) => item.id === "grain").passed, true);
});

test("missing grain blocks a detail model before publish", () => {
  const state = createInitialState();
  state.model.grainStatement = "";
  state.model.grainKeys = [];
  const result = validateModel(state.model);
  assert.equal(result.passed, false);
  assert.equal(result.blockers.some((item) => item.id === "grain"), true);
});

test("dimension reuse automatically creates a relation", () => {
  const state = createInitialState();
  state.model.dimensions = [];
  state.model.relations = [];
  toggleDimension(state, "dim-project");
  assert.deepEqual(state.model.dimensions, ["dim-project"]);
  assert.equal(state.model.relations[0].targetModel, "dim_project");
});

test("removing a reused dimension removes its relation", () => {
  const state = createInitialState();
  toggleDimension(state, "dim-project");
  state.model.dimensions = state.model.dimensions.filter((item) => item !== "dim-project");
  state.model.relations = state.model.relations.filter((item) => item.targetId !== "dim-project");
  assert.equal(state.model.relations.some((item) => item.targetId === "dim-project"), false);
});

test("save draft exposes a model list entry with current quality", () => {
  const state = createInitialState();
  saveDraft(state);
  const draft = state.savedModels.find((item) => item.id === state.model.id);
  assert.equal(draft.status, "draft");
  assert.equal(draft.fieldCount, state.model.fields.length);
  assert.equal(draft.quality, 100);
});

test("publish moves the draft to a traceable published result", () => {
  const state = createInitialState();
  const result = publishModel(state);
  assert.equal(result.published, true);
  assert.equal(state.view, "published");
  assert.equal(state.publishResult.version, "v1.0.0");
  assert.equal(state.savedModels.find((item) => item.id === state.model.id).status, "published");
});
