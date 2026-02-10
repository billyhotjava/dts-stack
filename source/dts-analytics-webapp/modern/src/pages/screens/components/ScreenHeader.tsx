import { useState, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useScreen } from '../ScreenContext';
import { analyticsApi } from '../../../api/analyticsApi';

export function ScreenHeader() {
    const navigate = useNavigate();
    const { id } = useParams<{ id: string }>();
    const { state, updateConfig, isSaving, setIsSaving } = useScreen();
    const { config } = state;
    const [isEditingName, setIsEditingName] = useState(false);
    const [nameValue, setNameValue] = useState(config.name);
    const [isSharing, setIsSharing] = useState(false);

    const handleNameClick = () => {
        setNameValue(config.name);
        setIsEditingName(true);
    };

    const handleNameBlur = () => {
        setIsEditingName(false);
        if (nameValue.trim() && nameValue !== config.name) {
            updateConfig({ name: nameValue.trim() });
        }
    };

    const handleNameKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter') {
            handleNameBlur();
        } else if (e.key === 'Escape') {
            setIsEditingName(false);
            setNameValue(config.name);
        }
    };

    const handleSave = useCallback(async () => {
        if (isSaving) return;

        setIsSaving(true);
        try {
            const payload = {
                name: config.name,
                description: config.description,
                width: config.width,
                height: config.height,
                backgroundColor: config.backgroundColor,
                backgroundImage: config.backgroundImage,
                components: config.components,
            };

            if (id) {
                // Update existing screen
                await analyticsApi.updateScreen(id, payload);
            } else {
                // Create new screen
                const result = await analyticsApi.createScreen(payload);
                if (result.id) {
                    // Navigate to edit page with new ID
                    navigate(`/screens/${result.id}/edit`, { replace: true });
                }
            }
            // Could show success message here
        } catch (error) {
            console.error('Failed to save screen:', error);
            // Could show error message here
        } finally {
            setIsSaving(false);
        }
    }, [config, id, isSaving, navigate, setIsSaving]);

    const handlePreview = () => {
        if (id) {
            window.open(`/analytics/screens/${id}/preview`, '_blank');
        } else {
            // For unsaved screens, could show a warning or save first
            alert('请先保存大屏后再预览');
        }
    };

    const handleShare = async () => {
        if (!id || isSharing) return;
        setIsSharing(true);
        try {
            const { uuid } = await analyticsApi.createScreenPublicLink(id);
            const url = `${window.location.origin}/analytics/public/screen/${uuid}`;
            await navigator.clipboard.writeText(url);
            alert('分享链接已复制到剪贴板');
        } catch (err) {
            console.error('Failed to create public link:', err);
            alert('创建分享链接失败');
        } finally {
            setIsSharing(false);
        }
    };

    const handleBack = () => {
        navigate('/screens');
    };

    return (
        <div className="screen-header">
            <div className="screen-header-left">
                <button className="header-btn back-btn" onClick={handleBack} title="返回列表">
                    ← 返回
                </button>
                <div className="screen-name-container">
                    {isEditingName ? (
                        <input
                            type="text"
                            className="screen-name-input"
                            value={nameValue}
                            onChange={(e) => setNameValue(e.target.value)}
                            onBlur={handleNameBlur}
                            onKeyDown={handleNameKeyDown}
                            autoFocus
                        />
                    ) : (
                        <span className="screen-name" onClick={handleNameClick} title="点击编辑名称">
                            {config.name}
                        </span>
                    )}
                </div>
            </div>

            <div className="screen-header-right">
                {id && (
                    <button
                        className="header-btn share-btn"
                        onClick={handleShare}
                        disabled={isSharing}
                        title="分享大屏"
                    >
                        {isSharing ? '分享中...' : '🔗 分享'}
                    </button>
                )}
                <button
                    className="header-btn preview-btn"
                    onClick={handlePreview}
                    title="预览大屏"
                >
                    👁️ 预览
                </button>
                <button
                    className="header-btn save-btn"
                    onClick={handleSave}
                    disabled={isSaving}
                    title="保存大屏"
                >
                    {isSaving ? '保存中...' : '💾 保存'}
                </button>
            </div>
        </div>
    );
}
