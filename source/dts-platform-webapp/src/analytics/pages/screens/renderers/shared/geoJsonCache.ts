/** GeoJSON fetch cache with LRU eviction for map-chart components. */

const MAX_GEO_CACHE = 16;
const geoJsonCache = new Map<string, Promise<unknown | null>>();

export const MAP_PRESET_URLS: Record<string, string> = {
    china: 'https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json',
    world: 'https://geo.datav.aliyun.com/areas_v3/bound/world.geo.json',
};

/** Province-level GeoJSON presets (adcode → DataV URL). */
export const PROVINCE_PRESETS: Array<{ name: string; code: string; url: string }> = [
    { name: '北京市', code: '110000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/110000_full.json' },
    { name: '天津市', code: '120000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/120000_full.json' },
    { name: '河北省', code: '130000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/130000_full.json' },
    { name: '山西省', code: '140000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/140000_full.json' },
    { name: '内蒙古自治区', code: '150000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/150000_full.json' },
    { name: '辽宁省', code: '210000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/210000_full.json' },
    { name: '吉林省', code: '220000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/220000_full.json' },
    { name: '黑龙江省', code: '230000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/230000_full.json' },
    { name: '上海市', code: '310000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/310000_full.json' },
    { name: '江苏省', code: '320000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/320000_full.json' },
    { name: '浙江省', code: '330000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/330000_full.json' },
    { name: '安徽省', code: '340000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/340000_full.json' },
    { name: '福建省', code: '350000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/350000_full.json' },
    { name: '江西省', code: '360000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/360000_full.json' },
    { name: '山东省', code: '370000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/370000_full.json' },
    { name: '河南省', code: '410000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/410000_full.json' },
    { name: '湖北省', code: '420000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/420000_full.json' },
    { name: '湖南省', code: '430000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/430000_full.json' },
    { name: '广东省', code: '440000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/440000_full.json' },
    { name: '广西壮族自治区', code: '450000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/450000_full.json' },
    { name: '海南省', code: '460000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/460000_full.json' },
    { name: '重庆市', code: '500000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/500000_full.json' },
    { name: '四川省', code: '510000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/510000_full.json' },
    { name: '贵州省', code: '520000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/520000_full.json' },
    { name: '云南省', code: '530000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/530000_full.json' },
    { name: '西藏自治区', code: '540000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/540000_full.json' },
    { name: '陕西省', code: '610000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/610000_full.json' },
    { name: '甘肃省', code: '620000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/620000_full.json' },
    { name: '青海省', code: '630000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/630000_full.json' },
    { name: '宁夏回族自治区', code: '640000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/640000_full.json' },
    { name: '新疆维吾尔自治区', code: '650000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/650000_full.json' },
    { name: '台湾省', code: '710000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/710000_full.json' },
    { name: '香港特别行政区', code: '810000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/810000_full.json' },
    { name: '澳门特别行政区', code: '820000', url: 'https://geo.datav.aliyun.com/areas_v3/bound/820000_full.json' },
];

export function resolvePresetMapUrl(scope?: string): string | undefined {
    const key = String(scope || '').trim().toLowerCase();
    if (!key) return undefined;
    if (MAP_PRESET_URLS[key]) return MAP_PRESET_URLS[key];
    // Check province presets by code or name
    const province = PROVINCE_PRESETS.find(
        p => p.code === key || p.name === key || p.name.startsWith(key),
    );
    return province?.url;
}

export function fetchGeoJsonWithCache(url: string): Promise<unknown | null> {
    const key = String(url || '').trim();
    if (!key) return Promise.resolve(null);
    const cached = geoJsonCache.get(key);
    if (cached) return cached;
    // LRU eviction: remove oldest entry when cache is full
    if (geoJsonCache.size >= MAX_GEO_CACHE) {
        const oldest = geoJsonCache.keys().next().value;
        if (oldest) geoJsonCache.delete(oldest);
    }
    const task = fetch(key, { credentials: 'omit' })
        .then((response) => {
            if (!response.ok) {
                throw new Error(`geojson fetch failed: ${response.status}`);
            }
            return response.json() as Promise<unknown>;
        })
        .catch((error) => {
            console.warn('[map-chart] failed to load geojson:', key, error);
            return null;
        });
    geoJsonCache.set(key, task);
    return task;
}
