import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';

test('useScreenCarousel keeps manual control semantics for sprint-9 playback mode', () => {
    const source = readFileSync(new URL('./useScreenCarousel.ts', import.meta.url), 'utf8');

    assert.match(source, /const autoPlay = carouselConfig\?\.autoPlay \?\? false;/);
    assert.match(source, /\[paused, setPaused\] = useState\(!autoPlay\);/);
    assert.match(source, /const goToPage = useCallback\(\(index: number\) => \{\s*const clamped =[\s\S]*?setPaused\(true\);/);
    assert.match(source, /const nextPage = useCallback\(\(\) => \{[\s\S]*?setPaused\(true\);/);
    assert.match(source, /const prevPage = useCallback\(\(\) => \{[\s\S]*?setPaused\(true\);/);
});
