/** Markdown rendering utilities for screen components. */

import { escapeHtml } from '../../sanitize';

export function renderMarkdownToHtml(input: string): string {
    const lines = input.replace(/\r\n/g, '\n').split('\n');
    const out: string[] = [];
    let listType: 'ul' | 'ol' | null = null;
    let inCodeBlock = false;
    let codeLines: string[] = [];

    const renderInlineMarkdown = (text: string) => {
        return escapeHtml(text)
            .replace(/\[(.+?)\]\((https?:\/\/[^\s)"]+)\)/g, (_, linkText, url) => {
                const safeUrl = url.replace(/"/g, '&quot;');
                return `<a href="${safeUrl}" target="_blank" rel="noreferrer">${linkText}</a>`;
            })
            .replace(/`([^`]+)`/g, '<code style="padding:1px 4px;border-radius:4px;background:rgba(148,163,184,0.18);">$1</code>')
            .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.+?)\*/g, '<em>$1</em>');
    };

    const closeList = () => {
        if (listType) {
            out.push(listType === 'ul' ? '</ul>' : '</ol>');
            listType = null;
        }
    };

    const flushCodeBlock = () => {
        if (!inCodeBlock) return;
        const code = escapeHtml(codeLines.join('\n'));
        out.push(
            '<pre style="margin:8px 0;padding:10px 12px;border-radius:6px;background:rgba(15,23,42,0.85);border:1px solid rgba(148,163,184,0.25);overflow:auto;">'
            + `<code style="font-family:Consolas,Monaco,monospace;font-size:12px;line-height:1.5;">${code}</code>`
            + '</pre>',
        );
        inCodeBlock = false;
        codeLines = [];
    };

    for (const line of lines) {
        const raw = line.trim();
        if (raw.startsWith('```')) {
            closeList();
            if (inCodeBlock) {
                flushCodeBlock();
            } else {
                inCodeBlock = true;
                codeLines = [];
            }
            continue;
        }
        if (inCodeBlock) {
            codeLines.push(line);
            continue;
        }
        if (!raw) {
            closeList();
            out.push('<br/>');
            continue;
        }
        if (raw === '---' || raw === '***') {
            closeList();
            out.push('<hr style="border:none;border-top:1px solid rgba(148,163,184,0.3);margin:10px 0;"/>');
            continue;
        }
        if (raw.startsWith('### ')) {
            closeList();
            out.push(`<h3>${escapeHtml(raw.slice(4))}</h3>`);
            continue;
        }
        if (raw.startsWith('## ')) {
            closeList();
            out.push(`<h2>${escapeHtml(raw.slice(3))}</h2>`);
            continue;
        }
        if (raw.startsWith('# ')) {
            closeList();
            out.push(`<h1>${escapeHtml(raw.slice(2))}</h1>`);
            continue;
        }
        if (raw.startsWith('> ')) {
            closeList();
            out.push(
                '<blockquote style="margin:8px 0;padding:6px 10px;border-left:3px solid rgba(59,130,246,0.65);background:rgba(59,130,246,0.08);">'
                + `${renderInlineMarkdown(raw.slice(2))}`
                + '</blockquote>',
            );
            continue;
        }
        if (raw.startsWith('- ') || raw.startsWith('* ')) {
            if (listType !== 'ul') {
                closeList();
                out.push('<ul>');
                listType = 'ul';
            }
            out.push(`<li>${renderInlineMarkdown(raw.slice(2))}</li>`);
            continue;
        }
        const orderedMatch = raw.match(/^(\d+)\.\s+(.+)$/);
        if (orderedMatch) {
            if (listType !== 'ol') {
                closeList();
                out.push('<ol>');
                listType = 'ol';
            }
            out.push(`<li>${renderInlineMarkdown(orderedMatch[2])}</li>`);
            continue;
        }
        closeList();
        const safe = renderInlineMarkdown(raw);
        out.push(`<p>${safe}</p>`);
    }
    closeList();
    flushCodeBlock();
    return out.join('');
}
