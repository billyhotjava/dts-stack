import { Modal } from '../../../ui/Modal/Modal';

export type StructuredActionField = {
    key: string;
    label: string;
    kind?: 'text' | 'textarea' | 'select';
    value: string;
    placeholder?: string;
    helpText?: string;
    options?: Array<{ label: string; value: string }>;
};

interface StructuredActionDialogProps {
    open: boolean;
    title: string;
    description?: string;
    submitText?: string;
    loading?: boolean;
    error?: string | null;
    fields: StructuredActionField[];
    onClose: () => void;
    onChange: (key: string, value: string) => void;
    onSubmit: () => void | Promise<void>;
}

export function StructuredActionDialog({
    open,
    title,
    description,
    submitText = '确认',
    loading = false,
    error,
    fields,
    onClose,
    onChange,
    onSubmit,
}: StructuredActionDialogProps) {
    return (
        <Modal
            isOpen={open}
            onClose={onClose}
            title={title}
            description={description}
            size="lg"
            footer={(
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <button type="button" className="template-btn secondary" onClick={onClose} disabled={loading}>
                        取消
                    </button>
                    <button type="button" className="template-btn primary" onClick={() => void onSubmit()} disabled={loading}>
                        {loading ? '处理中...' : submitText}
                    </button>
                </div>
            )}
        >
            <div style={{ display: 'grid', gap: 12 }}>
                {fields.map((field) => (
                    <label key={field.key} style={{ display: 'grid', gap: 6 }}>
                        <span style={{ fontSize: 13, fontWeight: 600 }}>{field.label}</span>
                        {field.kind === 'textarea' ? (
                            <textarea
                                value={field.value}
                                onChange={(event) => onChange(field.key, event.target.value)}
                                placeholder={field.placeholder}
                                rows={field.key === 'customTargets' ? 6 : 4}
                                style={{
                                    width: '100%',
                                    padding: '10px 12px',
                                    borderRadius: 10,
                                    border: '1px solid rgba(148,163,184,0.28)',
                                    background: '#0f172a',
                                    color: '#e2e8f0',
                                    resize: 'vertical',
                                }}
                            />
                        ) : field.kind === 'select' ? (
                            <select
                                value={field.value}
                                onChange={(event) => onChange(field.key, event.target.value)}
                                style={{
                                    width: '100%',
                                    padding: '10px 12px',
                                    borderRadius: 10,
                                    border: '1px solid rgba(148,163,184,0.28)',
                                    background: '#0f172a',
                                    color: '#e2e8f0',
                                }}
                            >
                                {(field.options || []).map((option) => (
                                    <option key={option.value} value={option.value}>{option.label}</option>
                                ))}
                            </select>
                        ) : (
                            <input
                                value={field.value}
                                onChange={(event) => onChange(field.key, event.target.value)}
                                placeholder={field.placeholder}
                                style={{
                                    width: '100%',
                                    padding: '10px 12px',
                                    borderRadius: 10,
                                    border: '1px solid rgba(148,163,184,0.28)',
                                    background: '#0f172a',
                                    color: '#e2e8f0',
                                }}
                            />
                        )}
                        {field.helpText ? (
                            <span style={{ fontSize: 12, color: '#94a3b8', lineHeight: 1.6 }}>{field.helpText}</span>
                        ) : null}
                    </label>
                ))}
                {error ? (
                    <div style={{
                        padding: '10px 12px',
                        borderRadius: 10,
                        border: '1px solid rgba(239,68,68,0.28)',
                        background: 'rgba(239,68,68,0.10)',
                        color: '#fecaca',
                        fontSize: 12,
                        whiteSpace: 'pre-wrap',
                    }}
                    >
                        {error}
                    </div>
                ) : null}
            </div>
        </Modal>
    );
}
