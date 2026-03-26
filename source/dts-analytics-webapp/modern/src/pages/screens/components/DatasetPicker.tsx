import { useEffect, useMemo, useState } from 'react';
import { analyticsApi } from '../../../api/analyticsApi';

export interface DatasetPickerProps {
    onSelect: (ds: { datasetId: string; datasetName: string }) => void;
    selectedId?: string;
}

type DatasetItem = {
    uuid: string;
    name: string;
    originalFileName: string;
    fileType: string;
    rowCount: number;
    fileSize: number;
    createdAt: string;
};

function formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function formatDate(iso: string): string {
    if (!iso) return '';
    return iso.slice(0, 10);
}

function formatRowCount(n: number): string {
    return n.toLocaleString('en-US') + ' 行';
}

export function DatasetPicker({ onSelect, selectedId }: DatasetPickerProps) {
    const [datasets, setDatasets] = useState<DatasetItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState('');
    const [picked, setPicked] = useState<string | undefined>(selectedId);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        analyticsApi.listScreenDatasets().then(list => {
            if (!cancelled) {
                setDatasets(list);
                setLoading(false);
            }
        }).catch(() => {
            if (!cancelled) setLoading(false);
        });
        return () => { cancelled = true; };
    }, []);

    const filtered = useMemo(() => {
        if (!search.trim()) return datasets;
        const q = search.trim().toLowerCase();
        return datasets.filter(d =>
            d.name.toLowerCase().includes(q) ||
            d.originalFileName.toLowerCase().includes(q)
        );
    }, [datasets, search]);

    const handleConfirm = () => {
        const ds = datasets.find(d => d.uuid === picked);
        if (ds) {
            onSelect({ datasetId: ds.uuid, datasetName: ds.name });
        }
    };

    return (
        <div style={styles.container}>
            <input
                type="text"
                placeholder="搜索数据集..."
                value={search}
                onChange={e => setSearch(e.target.value)}
                style={styles.searchInput}
            />
            <div style={styles.listContainer}>
                {loading && (
                    <div style={styles.emptyState}>加载中...</div>
                )}
                {!loading && filtered.length === 0 && (
                    <div style={styles.emptyState}>
                        {datasets.length === 0 ? '暂无已上传的数据集' : '无匹配结果'}
                    </div>
                )}
                {!loading && filtered.map(ds => {
                    const isSelected = ds.uuid === picked;
                    return (
                        <div
                            key={ds.uuid}
                            onClick={() => setPicked(ds.uuid)}
                            style={{
                                ...styles.item,
                                ...(isSelected ? styles.itemSelected : {}),
                            }}
                        >
                            <div style={styles.itemName}>{ds.name}</div>
                            <div style={styles.itemMeta}>
                                <span>{formatRowCount(ds.rowCount)}</span>
                                <span style={styles.metaSep}>·</span>
                                <span>{formatFileSize(ds.fileSize)}</span>
                                <span style={styles.metaSep}>·</span>
                                <span>{formatDate(ds.createdAt)}</span>
                            </div>
                        </div>
                    );
                })}
            </div>
            <button
                style={{
                    ...styles.confirmBtn,
                    ...(picked ? {} : styles.confirmBtnDisabled),
                }}
                disabled={!picked}
                onClick={handleConfirm}
            >
                确定
            </button>
        </div>
    );
}

const styles: Record<string, React.CSSProperties> = {
    container: {
        display: 'flex',
        flexDirection: 'column',
        width: '100%',
        maxWidth: 320,
        gap: 8,
    },
    searchInput: {
        width: '100%',
        padding: '6px 8px',
        fontSize: 12,
        background: '#0d1b2a',
        border: '1px solid #334155',
        borderRadius: 4,
        color: '#e2e8f0',
        outline: 'none',
        boxSizing: 'border-box',
    },
    listContainer: {
        maxHeight: 240,
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
    },
    emptyState: {
        padding: '24px 0',
        textAlign: 'center',
        color: '#94a3b8',
        fontSize: 12,
    },
    item: {
        padding: '8px 10px',
        borderRadius: 4,
        cursor: 'pointer',
        background: '#1a1f36',
        border: '1px solid transparent',
        transition: 'border-color 0.15s, background 0.15s',
    },
    itemSelected: {
        borderColor: '#0ea5e9',
        background: 'rgba(14, 165, 233, 0.15)',
    },
    itemName: {
        fontSize: 13,
        color: '#e2e8f0',
        marginBottom: 4,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
    },
    itemMeta: {
        fontSize: 11,
        color: '#94a3b8',
    },
    metaSep: {
        margin: '0 4px',
    },
    confirmBtn: {
        padding: '6px 0',
        fontSize: 13,
        background: '#0ea5e9',
        color: '#fff',
        border: 'none',
        borderRadius: 4,
        cursor: 'pointer',
    },
    confirmBtnDisabled: {
        opacity: 0.4,
        cursor: 'not-allowed',
    },
};
