// @ts-nocheck — extracted from PropertyPanel, pending typed cleanup
import { useRef, useState } from 'react';
import { message } from 'antd';
import apiClient from '@/api/apiClient';

export function BackgroundImageRow({ value, onChange }: { value: string; onChange: (url: string) => void }) {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [uploading, setUploading] = useState(false);

    const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > 10 * 1024 * 1024) { message.error('文件大小不能超过 10MB'); return; }
        const formData = new FormData();
        formData.append('file', file);
        setUploading(true);
        try {
            const res = await apiClient.post<{ data: { url: string } }>({ url: '/infra/screen-images/upload', data: formData });
            const url = (res as any)?.data?.url ?? (res as any)?.url;
            if (url) { onChange(url); message.success('上传成功'); }
            else { message.error('上传返回格式异常'); }
        } catch (err: any) { message.error(err?.message || '上传失败'); }
        finally { setUploading(false); if (fileInputRef.current) fileInputRef.current.value = ''; }
    };

    return (
        <div className="property-row flex items-center mb-3">
            <label className="property-label w-20 text-xs text-text-secondary">背景图</label>
            <div style={{ display: 'flex', gap: 4, alignItems: 'center', flex: 1 }}>
                <input
                    type="text"
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    value={value}
                    onChange={(e) => onChange(e.target.value)}
                    placeholder="图片 URL 或点击上传"
                />
                <button
                    type="button"
                    className="px-2 py-1.5 text-xs border border-border-default rounded bg-surface-card text-text-primary hover:bg-surface-hover"
                    disabled={uploading}
                    onClick={() => fileInputRef.current?.click()}
                >
                    {uploading ? '...' : '上传'}
                </button>
                {value && (
                    <button
                        type="button"
                        className="px-1.5 py-1.5 text-xs border border-border-default rounded bg-surface-card text-text-primary hover:bg-surface-hover"
                        title="清除背景图"
                        onClick={() => onChange('')}
                    >
                        ✕
                    </button>
                )}
                <input ref={fileInputRef} type="file" accept="image/*" style={{ display: 'none' }}
                    onChange={handleUpload} />
            </div>
        </div>
    );
}
