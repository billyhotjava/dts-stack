export type DataStandardStatus = "DRAFT" | "ACTIVE" | "DEPRECATED";

export interface DataStandardDto {
    id: string;
    code: string;
    name: string;
    domain?: string;
    scope?: string;
    status: DataStandardStatus;
    owner?: string;
    tags?: string[];
    currentVersion: string;
    versionNotes?: string;
    description?: string;
    reviewCycle?: string;
    lastReviewAt?: string;
    createdDate?: string;
    createdBy?: string;
    lastModifiedDate?: string;
    lastModifiedBy?: string;
}

export interface DataStandardVersionDto {
    id: string;
    version: string;
    status: "DRAFT" | "IN_REVIEW" | "PUBLISHED" | "ARCHIVED";
    changeSummary?: string;
    snapshotJson?: string;
    releasedAt?: string;
    createdDate?: string;
    createdBy?: string;
}

export interface DataStandardAttachmentDto {
    id: string;
    fileName: string;
    contentType?: string;
    fileSize: number;
    sha256?: string;
    keyVersion?: string;
    version?: string;
    createdDate?: string;
    createdBy?: string;
}

export const STATUS_OPTIONS: { value: DataStandardStatus; label: string }[] = [
    { value: "DRAFT", label: "草稿" },
    { value: "ACTIVE", label: "启用" },
    { value: "DEPRECATED", label: "已归档" },
];

export const statusLabel = (status: DataStandardStatus) =>
    STATUS_OPTIONS.find((item) => item.value === status)?.label ?? status;

export const DOMAIN_OPTIONS = ["主数据域", "共享域", "核心域", "分析域"];

export const toTagList = (input: string): string[] =>
    input
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean);

export const fromTagList = (value?: string[] | null) => (value && value.length ? value.join(", ") : "");

export const formatDate = (value?: string) => {
    if (!value) return "-";
    try {
        return new Date(value).toLocaleString();
    } catch {
        return value;
    }
};

export const humanFileSize = (size: number) => {
    if (!size) return "0";
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
    return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`;
};
