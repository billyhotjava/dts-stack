import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { analyticsApi, ScreenListItem } from '../../api/analyticsApi';
import '../page.css';

export default function ScreensPage() {
    const navigate = useNavigate();
    const [screens, setScreens] = useState<ScreenListItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const loadScreens = useCallback(() => {
        setLoading(true);
        analyticsApi.listScreens()
            .then((data) => {
                setScreens(data);
                setLoading(false);
            })
            .catch((err) => {
                console.error('Failed to load screens:', err);
                setError('加载大屏列表失败');
                setLoading(false);
            });
    }, []);

    useEffect(() => {
        loadScreens();
    }, [loadScreens]);

    const handleCreate = () => {
        navigate('/screens/new');
    };

    const handleEdit = (id: string | number) => {
        navigate(`/screens/${id}/edit`);
    };

    const handlePreview = (id: string | number) => {
        window.open(`/analytics/screens/${id}/preview`, '_blank');
    };

    const handleDelete = async (id: string | number) => {
        if (!confirm('确定要删除这个大屏吗？')) return;

        try {
            await analyticsApi.deleteScreen(id);
            loadScreens();
        } catch (err) {
            console.error('Failed to delete screen:', err);
            alert('删除失败');
        }
    };

    const formatDate = (dateStr?: string) => {
        if (!dateStr) return '-';
        const date = new Date(dateStr);
        return date.toLocaleDateString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
        });
    };

    return (
        <div className="page-container">
            <div className="page-header">
                <h1 className="page-title">🖥️ 大屏管理</h1>
                <button className="primary-btn" onClick={handleCreate}>
                    ➕ 新建大屏
                </button>
            </div>

            <div className="page-content">
                {loading ? (
                    <div className="loading-state">
                        <div className="loading-spinner" />
                        <span>加载中...</span>
                    </div>
                ) : error ? (
                    <div className="error-state">
                        <span>❌ {error}</span>
                        <button onClick={loadScreens}>重试</button>
                    </div>
                ) : screens.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-state-icon">🖥️</div>
                        <div className="empty-state-text">暂无大屏</div>
                        <div className="empty-state-hint">点击"新建大屏"创建您的第一个数据大屏</div>
                        <button className="primary-btn" onClick={handleCreate}>
                            ➕ 新建大屏
                        </button>
                    </div>
                ) : (
                    <div className="screens-grid">
                        {screens.map((screen) => (
                            <div key={screen.id} className="screen-card">
                                <div
                                    className="screen-card-preview"
                                    onClick={() => handleEdit(screen.id)}
                                >
                                    <div className="screen-card-placeholder">
                                        🖥️
                                    </div>
                                    <div className="screen-card-size">
                                        {screen.width || 1920} × {screen.height || 1080}
                                    </div>
                                </div>
                                <div className="screen-card-info">
                                    <h3 className="screen-card-name">{screen.name || '未命名大屏'}</h3>
                                    <p className="screen-card-desc">
                                        {screen.description || '无描述'}
                                    </p>
                                    <div className="screen-card-meta">
                                        <span>更新: {formatDate(screen.updatedAt)}</span>
                                    </div>
                                </div>
                                <div className="screen-card-actions">
                                    <button
                                        className="action-btn edit"
                                        onClick={() => handleEdit(screen.id)}
                                        title="编辑"
                                    >
                                        ✏️
                                    </button>
                                    <button
                                        className="action-btn preview"
                                        onClick={() => handlePreview(screen.id)}
                                        title="预览"
                                    >
                                        👁️
                                    </button>
                                    <button
                                        className="action-btn delete"
                                        onClick={() => handleDelete(screen.id)}
                                        title="删除"
                                    >
                                        🗑️
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <style>{`
                .screens-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
                    gap: 20px;
                    padding: 20px;
                }
                
                .screen-card {
                    background: var(--color-surface-secondary);
                    border: 1px solid var(--color-border);
                    border-radius: 8px;
                    overflow: hidden;
                    transition: all 0.2s ease;
                }
                
                .screen-card:hover {
                    border-color: var(--color-primary);
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
                }
                
                .screen-card-preview {
                    position: relative;
                    height: 160px;
                    background: #0d1b2a;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    cursor: pointer;
                }
                
                .screen-card-placeholder {
                    font-size: 48px;
                    opacity: 0.5;
                }
                
                .screen-card-size {
                    position: absolute;
                    bottom: 8px;
                    right: 8px;
                    padding: 4px 8px;
                    background: rgba(0, 0, 0, 0.6);
                    color: white;
                    border-radius: 4px;
                    font-size: 11px;
                }
                
                .screen-card-info {
                    padding: 16px;
                }
                
                .screen-card-name {
                    margin: 0 0 8px 0;
                    font-size: 16px;
                    font-weight: 600;
                    color: var(--color-text-primary);
                }
                
                .screen-card-desc {
                    margin: 0 0 8px 0;
                    font-size: 12px;
                    color: var(--color-text-secondary);
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }
                
                .screen-card-meta {
                    font-size: 11px;
                    color: var(--color-text-tertiary);
                }
                
                .screen-card-actions {
                    display: flex;
                    gap: 8px;
                    padding: 12px 16px;
                    border-top: 1px solid var(--color-border);
                }
                
                .action-btn {
                    flex: 1;
                    padding: 8px;
                    border: 1px solid var(--color-border);
                    border-radius: 6px;
                    background: var(--color-surface);
                    cursor: pointer;
                    font-size: 14px;
                    transition: all 0.2s ease;
                }
                
                .action-btn:hover {
                    border-color: var(--color-primary);
                    background: var(--color-primary-light);
                }
                
                .action-btn.delete:hover {
                    border-color: #ef4444;
                    background: rgba(239, 68, 68, 0.1);
                }
                
                .loading-state, .error-state {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    padding: 60px;
                    gap: 16px;
                }
                
                .loading-spinner {
                    width: 32px;
                    height: 32px;
                    border: 3px solid var(--color-border);
                    border-top-color: var(--color-primary);
                    border-radius: 50%;
                    animation: spin 1s linear infinite;
                }
                
                @keyframes spin {
                    to { transform: rotate(360deg); }
                }
            `}</style>
        </div>
    );
}
