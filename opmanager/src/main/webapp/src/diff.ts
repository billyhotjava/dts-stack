export type DiffRowKind = "equal" | "modified" | "inserted" | "deleted";

export type DiffSideState = "equal" | "modified" | "inserted" | "deleted" | "blank";

export type SideBySideDiffRow = {
  index: number;
  key: string;
  kind: DiffRowKind;
  leftLineNumber: number | null;
  rightLineNumber: number | null;
  leftText: string;
  rightText: string;
  leftState: DiffSideState;
  rightState: DiffSideState;
};

export type SideBySideDiff = {
  rows: SideBySideDiffRow[];
  changedRows: number;
  strategy: "aligned" | "row-by-row";
};

export type DiffHunk = {
  index: number;
  start: number;
  end: number;
  label: string;
};

export type DiffOptions = {
  maxMatrixCells?: number;
};

type Anchor = {
  left: number;
  right: number;
};

const DEFAULT_MAX_MATRIX_CELLS = 250_000;

export function buildSideBySideDiff(leftLines: string[], rightLines: string[], options: DiffOptions = {}): SideBySideDiff {
  const maxMatrixCells = options.maxMatrixCells ?? DEFAULT_MAX_MATRIX_CELLS;
  const matrixCells = (leftLines.length + 1) * (rightLines.length + 1);
  const rows =
    matrixCells > maxMatrixCells
      ? buildRowByRowDiff(leftLines, rightLines)
      : buildAlignedDiff(leftLines, rightLines, findLcsAnchors(leftLines, rightLines));

  return {
    rows,
    changedRows: rows.filter(row => row.kind !== "equal").length,
    strategy: matrixCells > maxMatrixCells ? "row-by-row" : "aligned"
  };
}

export function collectDiffHunks(rows: SideBySideDiffRow[]): DiffHunk[] {
  const hunks: DiffHunk[] = [];
  let start = -1;
  for (let index = 0; index < rows.length; index += 1) {
    if (rows[index].kind !== "equal" && start === -1) {
      start = index;
    }
    const atEnd = index === rows.length - 1;
    const nextEqual = !atEnd && rows[index + 1].kind === "equal";
    if (start !== -1 && (atEnd || nextEqual)) {
      hunks.push({ index: hunks.length, start, end: index, label: `差异 ${hunks.length + 1}` });
      start = -1;
    }
  }
  return hunks;
}

function buildAlignedDiff(leftLines: string[], rightLines: string[], anchors: Anchor[]): SideBySideDiffRow[] {
  const rows: SideBySideDiffRow[] = [];
  let leftCursor = 0;
  let rightCursor = 0;

  for (const anchor of anchors) {
    appendChangedBlock(rows, leftLines, rightLines, leftCursor, anchor.left, rightCursor, anchor.right);
    rows.push(equalRow(rows.length, anchor.left, leftLines[anchor.left], anchor.right, rightLines[anchor.right]));
    leftCursor = anchor.left + 1;
    rightCursor = anchor.right + 1;
  }

  appendChangedBlock(rows, leftLines, rightLines, leftCursor, leftLines.length, rightCursor, rightLines.length);
  return rows;
}

function buildRowByRowDiff(leftLines: string[], rightLines: string[]): SideBySideDiffRow[] {
  const rows: SideBySideDiffRow[] = [];
  const max = Math.max(leftLines.length, rightLines.length);
  for (let index = 0; index < max; index += 1) {
    const hasLeft = index < leftLines.length;
    const hasRight = index < rightLines.length;
    if (hasLeft && hasRight && leftLines[index] === rightLines[index]) {
      rows.push(equalRow(rows.length, index, leftLines[index], index, rightLines[index]));
    } else if (hasLeft && hasRight) {
      rows.push(modifiedRow(rows.length, index, leftLines[index], index, rightLines[index]));
    } else if (hasLeft) {
      rows.push(deletedRow(rows.length, index, leftLines[index]));
    } else {
      rows.push(insertedRow(rows.length, index, rightLines[index]));
    }
  }
  return rows;
}

function appendChangedBlock(
  rows: SideBySideDiffRow[],
  leftLines: string[],
  rightLines: string[],
  leftStart: number,
  leftEnd: number,
  rightStart: number,
  rightEnd: number
) {
  const leftCount = leftEnd - leftStart;
  const rightCount = rightEnd - rightStart;
  const paired = Math.min(leftCount, rightCount);

  for (let offset = 0; offset < paired; offset += 1) {
    rows.push(modifiedRow(rows.length, leftStart + offset, leftLines[leftStart + offset], rightStart + offset, rightLines[rightStart + offset]));
  }
  for (let offset = paired; offset < leftCount; offset += 1) {
    rows.push(deletedRow(rows.length, leftStart + offset, leftLines[leftStart + offset]));
  }
  for (let offset = paired; offset < rightCount; offset += 1) {
    rows.push(insertedRow(rows.length, rightStart + offset, rightLines[rightStart + offset]));
  }
}

function findLcsAnchors(leftLines: string[], rightLines: string[]): Anchor[] {
  const width = rightLines.length + 1;
  const scores = new Uint16Array((leftLines.length + 1) * width);

  for (let left = leftLines.length - 1; left >= 0; left -= 1) {
    for (let right = rightLines.length - 1; right >= 0; right -= 1) {
      const current = left * width + right;
      if (leftLines[left] === rightLines[right]) {
        scores[current] = scores[(left + 1) * width + right + 1] + 1;
      } else {
        scores[current] = Math.max(scores[(left + 1) * width + right], scores[left * width + right + 1]);
      }
    }
  }

  const anchors: Anchor[] = [];
  let left = 0;
  let right = 0;
  while (left < leftLines.length && right < rightLines.length) {
    if (leftLines[left] === rightLines[right]) {
      anchors.push({ left, right });
      left += 1;
      right += 1;
    } else if (scores[(left + 1) * width + right] >= scores[left * width + right + 1]) {
      left += 1;
    } else {
      right += 1;
    }
  }
  return anchors;
}

function equalRow(key: number, leftIndex: number, leftText: string, rightIndex: number, rightText: string): SideBySideDiffRow {
  return {
    index: key,
    key: `equal-${key}`,
    kind: "equal",
    leftLineNumber: leftIndex + 1,
    rightLineNumber: rightIndex + 1,
    leftText,
    rightText,
    leftState: "equal",
    rightState: "equal"
  };
}

function modifiedRow(key: number, leftIndex: number, leftText: string, rightIndex: number, rightText: string): SideBySideDiffRow {
  return {
    index: key,
    key: `modified-${key}`,
    kind: "modified",
    leftLineNumber: leftIndex + 1,
    rightLineNumber: rightIndex + 1,
    leftText,
    rightText,
    leftState: "modified",
    rightState: "modified"
  };
}

function deletedRow(key: number, leftIndex: number, leftText: string): SideBySideDiffRow {
  return {
    index: key,
    key: `deleted-${key}`,
    kind: "deleted",
    leftLineNumber: leftIndex + 1,
    rightLineNumber: null,
    leftText,
    rightText: "",
    leftState: "deleted",
    rightState: "blank"
  };
}

function insertedRow(key: number, rightIndex: number, rightText: string): SideBySideDiffRow {
  return {
    index: key,
    key: `inserted-${key}`,
    kind: "inserted",
    leftLineNumber: null,
    rightLineNumber: rightIndex + 1,
    leftText: "",
    rightText,
    leftState: "blank",
    rightState: "inserted"
  };
}
