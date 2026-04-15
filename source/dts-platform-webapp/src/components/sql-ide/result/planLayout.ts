export interface PlanNode {
  id: string;
  operator: string;
  table: string | null;
  estimatedRows: number | null;
  estimatedCost: number | null;
  attributes: Record<string, string>;
  children: PlanNode[];
}

export interface RFNode {
  id: string;
  position: { x: number; y: number };
  data: { node: PlanNode };
  type?: string;
}

export interface RFEdge {
  id: string;
  source: string;
  target: string;
}

const NODE_WIDTH = 200;
const NODE_HEIGHT = 70;
const VERTICAL_GAP = 100;
const HORIZONTAL_GAP = 30;

interface SubtreeBox {
  width: number;
  nodes: RFNode[];
  edges: RFEdge[];
  rootX: number;
}

function layoutSubtree(node: PlanNode, depth: number): SubtreeBox {
  if (node.children.length === 0) {
    const rf: RFNode = {
      id: node.id,
      position: { x: 0, y: depth * (NODE_HEIGHT + VERTICAL_GAP) },
      data: { node },
    };
    return { width: NODE_WIDTH, nodes: [rf], edges: [], rootX: NODE_WIDTH / 2 };
  }

  const childBoxes = node.children.map((c) => layoutSubtree(c, depth + 1));
  const totalWidth =
    childBoxes.reduce((sum, b) => sum + b.width, 0) +
    HORIZONTAL_GAP * (childBoxes.length - 1);

  const allNodes: RFNode[] = [];
  const allEdges: RFEdge[] = [];
  let xCursor = 0;
  for (const box of childBoxes) {
    for (const n of box.nodes) {
      allNodes.push({ ...n, position: { x: n.position.x + xCursor, y: n.position.y } });
    }
    for (const e of box.edges) allEdges.push(e);
    xCursor += box.width + HORIZONTAL_GAP;
  }

  // Root positioned at horizontal center of children subtree
  const rootX = totalWidth / 2;
  const rootRF: RFNode = {
    id: node.id,
    position: { x: rootX - NODE_WIDTH / 2, y: depth * (NODE_HEIGHT + VERTICAL_GAP) },
    data: { node },
  };
  allNodes.push(rootRF);

  for (const child of node.children) {
    allEdges.push({ id: `${node.id}->${child.id}`, source: node.id, target: child.id });
  }

  return { width: Math.max(totalWidth, NODE_WIDTH), nodes: allNodes, edges: allEdges, rootX };
}

export function layoutPlanNodes(root: PlanNode): { nodes: RFNode[]; edges: RFEdge[] } {
  const box = layoutSubtree(root, 0);
  return { nodes: box.nodes, edges: box.edges };
}
