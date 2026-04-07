// Metabase-style Chart Color Palette

export const CHART_COLORS = {
  // Default Metabase colors
  default: [
    '#509EE3', // Blue
    '#88BF4D', // Green
    '#F9D45C', // Yellow
    '#F2A86F', // Orange
    '#EF8C8C', // Red/Pink
    '#A989C5', // Purple
    '#98D9D9', // Cyan
    '#7172AD', // Indigo
  ],

  // Pastel variant
  pastel: [
    '#C7E0F4', // Light Blue
    '#D4E9C1', // Light Green
    '#FDF0C4', // Light Yellow
    '#FAD9C4', // Light Orange
    '#FACCCC', // Light Red
    '#DCD0E8', // Light Purple
    '#C9EFEF', // Light Cyan
    '#C1C2D8', // Light Indigo
  ],

  // Categorical (high contrast)
  categorical: [
    '#2D7FF9', // Bright Blue
    '#18BFFF', // Cyan
    '#10C96B', // Emerald
    '#F2C94C', // Amber
    '#FF8F3B', // Orange
    '#F25C54', // Red
    '#9B51E0', // Violet
    '#EB5757', // Coral
  ],

  // Sequential (single hue variations)
  sequential: {
    blue: ['#E6F1FB', '#C7E0F4', '#98C8EB', '#6BB0E3', '#509EE3', '#2E6FAF', '#1A4B7C', '#0D2B49'],
    green: ['#E6F5D6', '#C8E8A7', '#A4D77A', '#88BF4D', '#6FA83D', '#568B2F', '#3D6B21', '#264B13'],
    orange: ['#FEF0E3', '#FDDFC7', '#FBCBA3', '#F9B480', '#F2A86F', '#D98A5A', '#B36B45', '#8D4D30'],
  },

  // Diverging (for showing deviation from center)
  diverging: {
    blueRed: ['#2D7FF9', '#6BA5FA', '#A9CBFB', '#E8E8E8', '#FBACAC', '#F86B6B', '#F25C54'],
    greenRed: ['#10C96B', '#5CD999', '#A8E9C8', '#E8E8E8', '#FBACAC', '#F86B6B', '#F25C54'],
  },

  // Semantic colors
  semantic: {
    positive: '#88BF4D',
    negative: '#EF8C8C',
    neutral: '#949AAB',
    highlight: '#509EE3',
  },

  // Dark mode variants
  dark: {
    default: [
      '#6FB3F2', // Blue
      '#9CD65C', // Green
      '#FFE066', // Yellow
      '#FFB380', // Orange
      '#FF9999', // Red/Pink
      '#C4A6E3', // Purple
      '#A8E9E9', // Cyan
      '#8E8FC7', // Indigo
    ],
  },
};

// Get color by index with cycling
export function getChartColor(index: number, palette: keyof typeof CHART_COLORS = 'default'): string {
  const colors = CHART_COLORS[palette];
  if (Array.isArray(colors)) {
    return colors[index % colors.length];
  }
  return CHART_COLORS.default[index % CHART_COLORS.default.length];
}

// Get color array for a specific number of items
export function getChartColors(count: number, palette: keyof typeof CHART_COLORS = 'default'): string[] {
  const colors = CHART_COLORS[palette];
  if (!Array.isArray(colors)) {
    return getChartColors(count, 'default');
  }

  const result: string[] = [];
  for (let i = 0; i < count; i++) {
    result.push(colors[i % colors.length]);
  }
  return result;
}

// Generate gradient stops for area charts
export function getGradientStops(color: string, opacity: [number, number] = [0.3, 0]): { offset: string; color: string }[] {
  return [
    { offset: '0%', color: `${color}${Math.round(opacity[0] * 255).toString(16).padStart(2, '0')}` },
    { offset: '100%', color: `${color}${Math.round(opacity[1] * 255).toString(16).padStart(2, '0')}` },
  ];
}

// Adjust color brightness
export function adjustBrightness(hex: string, percent: number): string {
  const num = parseInt(hex.replace('#', ''), 16);
  const amt = Math.round(2.55 * percent);
  const R = (num >> 16) + amt;
  const G = ((num >> 8) & 0x00ff) + amt;
  const B = (num & 0x0000ff) + amt;
  return (
    '#' +
    (
      0x1000000 +
      (R < 255 ? (R < 1 ? 0 : R) : 255) * 0x10000 +
      (G < 255 ? (G < 1 ? 0 : G) : 255) * 0x100 +
      (B < 255 ? (B < 1 ? 0 : B) : 255)
    )
      .toString(16)
      .slice(1)
  );
}

// Get contrasting text color for background
export function getContrastingTextColor(bgColor: string): string {
  const hex = bgColor.replace('#', '');
  const r = parseInt(hex.substr(0, 2), 16);
  const g = parseInt(hex.substr(2, 2), 16);
  const b = parseInt(hex.substr(4, 2), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.5 ? '#4C5773' : '#FFFFFF';
}

// Format number for display
export function formatChartValue(value: number, options?: {
  compact?: boolean;
  decimals?: number;
  prefix?: string;
  suffix?: string;
}): string {
  const { compact = false, decimals = 2, prefix = '', suffix = '' } = options || {};

  let formatted: string;

  if (compact && Math.abs(value) >= 1000) {
    const units = ['', 'K', 'M', 'B', 'T'];
    let unitIndex = 0;
    let num = value;

    while (Math.abs(num) >= 1000 && unitIndex < units.length - 1) {
      num /= 1000;
      unitIndex++;
    }

    formatted = num.toFixed(1).replace(/\.0$/, '') + units[unitIndex];
  } else {
    formatted = value.toFixed(decimals).replace(/\.?0+$/, '');
  }

  return `${prefix}${formatted}${suffix}`;
}
