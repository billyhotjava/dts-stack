export type RuntimeScaleInput = {
    viewportWidth: number;
    viewportHeight: number;
    screenWidth: number;
    screenHeight: number;
    fullscreen: boolean;
    allowUpscale: boolean;
};

export type RuntimeScaleResult = {
    safeWidth: number;
    safeHeight: number;
    scale: number;
    stageWidth: number;
    stageHeight: number;
};

export function resolveRuntimeScale(input: RuntimeScaleInput): RuntimeScaleResult {
    const safeWidth = Math.max(input.viewportWidth - (input.fullscreen ? 0 : 24), 320);
    const safeHeight = Math.max(input.viewportHeight - (input.fullscreen ? 0 : 64), 240);
    const sx = safeWidth / Math.max(input.screenWidth, 1);
    const sy = safeHeight / Math.max(input.screenHeight, 1);
    const rawScale = Math.max(0.1, Math.min(sx, sy));
    const scale = input.allowUpscale ? rawScale : Math.min(rawScale, 1);

    return {
        safeWidth,
        safeHeight,
        scale,
        stageWidth: Math.max(1, input.screenWidth * scale),
        stageHeight: Math.max(1, input.screenHeight * scale),
    };
}
