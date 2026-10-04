import type { ChangeEvent } from 'react';
import { useRef, useState } from 'react';
import { message } from 'antd';
import { X } from 'lucide-react';
import apiClient from '@/api/apiClient';
import {
    getScreenImageUploadErrorMessage,
    resolveScreenImageUploadUrl,
    SCREEN_IMAGE_UPLOAD_LIMIT_BYTES,
    SCREEN_IMAGE_UPLOAD_LIMIT_LABEL,
} from '../../utils/screenImageUpload';

type UploadImageResponse = {
    data?: {
        url?: string;
    };
    url?: string;
};

export function BackgroundImageRow({ value, onChange }: { value: string; onChange: (url: string) => void }) {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [uploading, setUploading] = useState(false);

    const handleUpload = async (e: ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > SCREEN_IMAGE_UPLOAD_LIMIT_BYTES) {
            message.error(`文件大小不能超过 ${SCREEN_IMAGE_UPLOAD_LIMIT_LABEL}`);
            if (fileInputRef.current) fileInputRef.current.value = '';
            return;
        }
        const formData = new FormData();
        formData.append('file', file);
        setUploading(true);
        try {
            const res = await apiClient.post<UploadImageResponse>({
                url: '/infra/screen-images/upload',
                data: formData,
                _skipErrorToast: true,
            } as any);
            const url = resolveScreenImageUploadUrl(res);
            if (url) { onChange(url); message.success('上传成功'); }
            else { message.error('上传返回格式异常'); }
        } catch (err: unknown) { message.error(getScreenImageUploadErrorMessage(err)); }
        finally { setUploading(false); if (fileInputRef.current) fileInputRef.current.value = ''; }
    };

    return (
        <div className="property-row mb-3" style={{ display: 'grid', gap: 6 }}>
            <label className="property-label text-xs text-text-secondary">背景图</label>
            <div style={{ display: 'grid', gap: 6, width: '100%', minWidth: 0 }}>
                <input
                    type="text"
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    value={value}
                    onChange={(e) => onChange(e.target.value)}
                    placeholder="图片 URL 或点击上传"
                    title={value}
                    style={{ width: '100%', minWidth: 0 }}
                />
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 4 }}>
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
                            aria-label="清除背景图"
                            onClick={() => onChange('')}
                        >
                            <X size={14} aria-hidden="true" />
                        </button>
                    )}
                </div>
                <input ref={fileInputRef} type="file" accept="image/*" style={{ display: 'none' }}
                    onChange={handleUpload} />
            </div>
        </div>
    );
}
