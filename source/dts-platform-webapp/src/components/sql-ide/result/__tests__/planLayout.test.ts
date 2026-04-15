import { describe, expect, it } from "vitest";
import { layoutPlanNodes, type PlanNode } from "../planLayout";

const sample: PlanNode = {
  id: "n0",
  operator: "Project",
  table: null,
  estimatedRows: 100,
  estimatedCost: 5,
  attributes: {},
  children: [
    {
      id: "n0.0",
      operator: "TableScan",
      table: "users",
      estimatedRows: 100,
      estimatedCost: 1,
      attributes: {},
      children: [],
    },
    {
      id: "n0.1",
      operator: "TableScan",
      table: "orders",
      estimatedRows: 100,
      estimatedCost: 1,
      attributes: {},
      children: [],
    },
  ],
};

describe("layoutPlanNodes", () => {
  it("returns one rf-node per plan node + edges from parent to child", () => {
    const { nodes, edges } = layoutPlanNodes(sample);
    expect(nodes).toHaveLength(3);
    expect(edges).toHaveLength(2);
    expect(edges.map((e) => `${e.source}->${e.target}`).sort()).toEqual([
      "n0->n0.0",
      "n0->n0.1",
    ]);
  });

  it("positions children below parent with horizontal spread", () => {
    const { nodes } = layoutPlanNodes(sample);
    const root = nodes.find((n) => n.id === "n0")!;
    const c0 = nodes.find((n) => n.id === "n0.0")!;
    const c1 = nodes.find((n) => n.id === "n0.1")!;
    expect(c0.position.y).toBeGreaterThan(root.position.y);
    expect(c1.position.x).toBeGreaterThan(c0.position.x);
  });

  it("handles empty children", () => {
    const leaf: PlanNode = {
      id: "n",
      operator: "Leaf",
      table: null,
      estimatedRows: null,
      estimatedCost: null,
      attributes: {},
      children: [],
    };
    const { nodes, edges } = layoutPlanNodes(leaf);
    expect(nodes).toHaveLength(1);
    expect(edges).toHaveLength(0);
  });
});
