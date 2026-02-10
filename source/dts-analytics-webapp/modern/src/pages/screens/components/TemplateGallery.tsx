import { useState } from 'react';
import { screenTemplates, createConfigFromTemplate, ScreenTemplate } from '../screenTemplates';
import '../ScreenDesigner.css';

interface TemplateGalleryProps {
    onSelect: (template: ScreenTemplate) => void;
    onClose: () => void;
}

export function TemplateGallery({ onSelect, onClose }: TemplateGalleryProps) {
    const [selectedId, setSelectedId] = useState<string>('blank');

    const handleConfirm = () => {
        const template = screenTemplates.find(t => t.id === selectedId);
        if (template) {
            onSelect(template);
        }
    };

    const categoryLabels: Record<string, string> = {
        business: '商务',
        tech: '科技',
        dashboard: '仪表盘',
        monitor: '监控',
    };

    return (
        <div className="template-gallery-overlay" onClick={onClose}>
            <div className="template-gallery-modal" onClick={e => e.stopPropagation()}>
                <div className="template-gallery-header">
                    <h2>📋 选择模板</h2>
                    <button className="template-gallery-close" onClick={onClose}>✕</button>
                </div>
                <div className="template-gallery-content">
                    <div className="template-gallery-grid">
                        {screenTemplates.map((template) => (
                            <div
                                key={template.id}
                                className={`template-card ${selectedId === template.id ? 'selected' : ''}`}
                                onClick={() => setSelectedId(template.id)}
                            >
                                <div className="template-card-preview" style={{
                                    background: template.config.backgroundColor
                                        ? `linear-gradient(135deg, ${template.config.backgroundColor} 0%, ${template.config.backgroundColor} 100%)`
                                        : undefined,
                                }}>
                                    <span className="template-card-icon">{template.thumbnail}</span>
                                    {template.id !== 'blank' && (
                                        <div className="template-card-badge">
                                            {categoryLabels[template.category] || template.category}
                                        </div>
                                    )}
                                </div>
                                <div className="template-card-info">
                                    <h3>{template.name}</h3>
                                    <p>{template.description}</p>
                                    <div className="template-card-meta">
                                        {template.config.components.length} 个组件
                                    </div>
                                </div>
                                {selectedId === template.id && (
                                    <div className="template-card-check">✓</div>
                                )}
                            </div>
                        ))}
                    </div>
                </div>
                <div className="template-gallery-footer">
                    <button className="template-btn secondary" onClick={onClose}>
                        取消
                    </button>
                    <button className="template-btn primary" onClick={handleConfirm}>
                        使用此模板
                    </button>
                </div>
            </div>

            <style>{`
                .template-gallery-overlay {
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    background: rgba(0, 0, 0, 0.7);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    z-index: 1000;
                    backdrop-filter: blur(4px);
                }

                .template-gallery-modal {
                    background: #1a1f36;
                    border-radius: 12px;
                    width: 90%;
                    max-width: 900px;
                    max-height: 80vh;
                    display: flex;
                    flex-direction: column;
                    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
                    border: 1px solid rgba(255, 255, 255, 0.1);
                }

                .template-gallery-header {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    padding: 20px 24px;
                    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
                }

                .template-gallery-header h2 {
                    margin: 0;
                    font-size: 20px;
                    color: #fff;
                }

                .template-gallery-close {
                    background: none;
                    border: none;
                    color: #888;
                    font-size: 24px;
                    cursor: pointer;
                    padding: 4px 8px;
                    border-radius: 4px;
                    transition: all 0.2s;
                }

                .template-gallery-close:hover {
                    background: rgba(255, 255, 255, 0.1);
                    color: #fff;
                }

                .template-gallery-content {
                    flex: 1;
                    overflow-y: auto;
                    padding: 24px;
                }

                .template-gallery-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
                    gap: 20px;
                }

                .template-card {
                    position: relative;
                    background: #0d1226;
                    border: 2px solid transparent;
                    border-radius: 10px;
                    overflow: hidden;
                    cursor: pointer;
                    transition: all 0.2s ease;
                }

                .template-card:hover {
                    border-color: rgba(0, 212, 255, 0.3);
                    transform: translateY(-2px);
                }

                .template-card.selected {
                    border-color: #00d4ff;
                    box-shadow: 0 0 20px rgba(0, 212, 255, 0.3);
                }

                .template-card-preview {
                    height: 140px;
                    background: linear-gradient(135deg, #0a0e27 0%, #1a1f46 100%);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    position: relative;
                }

                .template-card-icon {
                    font-size: 48px;
                }

                .template-card-badge {
                    position: absolute;
                    top: 10px;
                    right: 10px;
                    padding: 4px 10px;
                    background: rgba(0, 212, 255, 0.2);
                    color: #00d4ff;
                    border-radius: 20px;
                    font-size: 11px;
                    font-weight: 500;
                }

                .template-card-info {
                    padding: 16px;
                }

                .template-card-info h3 {
                    margin: 0 0 8px 0;
                    font-size: 16px;
                    font-weight: 600;
                    color: #fff;
                }

                .template-card-info p {
                    margin: 0 0 10px 0;
                    font-size: 13px;
                    color: #888;
                    line-height: 1.4;
                }

                .template-card-meta {
                    font-size: 12px;
                    color: #666;
                }

                .template-card-check {
                    position: absolute;
                    top: 10px;
                    left: 10px;
                    width: 24px;
                    height: 24px;
                    background: #00d4ff;
                    color: #000;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 14px;
                    font-weight: bold;
                }

                .template-gallery-footer {
                    display: flex;
                    gap: 12px;
                    justify-content: flex-end;
                    padding: 16px 24px;
                    border-top: 1px solid rgba(255, 255, 255, 0.1);
                }

                .template-btn {
                    padding: 10px 24px;
                    border-radius: 6px;
                    font-size: 14px;
                    font-weight: 500;
                    cursor: pointer;
                    transition: all 0.2s;
                }

                .template-btn.secondary {
                    background: transparent;
                    border: 1px solid rgba(255, 255, 255, 0.2);
                    color: #888;
                }

                .template-btn.secondary:hover {
                    border-color: rgba(255, 255, 255, 0.4);
                    color: #fff;
                }

                .template-btn.primary {
                    background: linear-gradient(135deg, #00d4ff 0%, #0066ff 100%);
                    border: none;
                    color: #fff;
                }

                .template-btn.primary:hover {
                    transform: translateY(-1px);
                    box-shadow: 0 4px 12px rgba(0, 212, 255, 0.4);
                }
            `}</style>
        </div>
    );
}
