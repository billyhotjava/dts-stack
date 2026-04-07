export function parseRuntimeTargetsInput(input: string): Array<Record<string, unknown>> {
    const text = String(input || '').trim();
    if (!text) return [];
    const rows = text
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line.length > 0 && !line.startsWith('#'));
    const targets: Array<Record<string, unknown>> = [];
    for (const row of rows) {
        const rawParts = row.split(',');
        const head = rawParts.slice(0, 7).map((item) => item.trim());
        const tail = rawParts.slice(7).join(',').trim();
        const [id, protocol, host, portText, pathOrEmpty, requiredText, expectedStatus] = head;
        const expectedBodyContains = tail || undefined;
        const port = Number(portText || '');
        if (!host || !Number.isFinite(port) || port <= 0) {
            continue;
        }
        const required = requiredText ? !['false', '0', 'no', 'n'].includes(requiredText.toLowerCase()) : true;
        const item: Record<string, unknown> = {
            id: id || undefined,
            protocol: protocol || 'tcp',
            host,
            port,
            required,
        };
        if (pathOrEmpty) {
            item.path = pathOrEmpty;
        }
        if (expectedStatus) {
            item.expectedStatus = expectedStatus;
        }
        if (expectedBodyContains) {
            item.expectedBodyContains = expectedBodyContains;
        }
        targets.push(item);
    }
    return targets;
}
