/**
 * useScreenCarousel — auto-carousel hook for multi-page screens.
 *
 * Manages page index, auto-advance timer, keyboard navigation,
 * and transition state for fade/slide animations.
 */
import { useState, useEffect, useCallback, useRef } from 'react';
import type { CarouselConfig, ScreenPage, ScreenComponent } from '../types';

export interface CarouselState {
    /** Current page index */
    pageIndex: number;
    /** Previous page index (for transition direction) */
    prevPageIndex: number;
    /** Whether a transition is currently active */
    transitioning: boolean;
    /** Components for the current page */
    currentPageComponents: ScreenComponent[];
    /** Current page background color override */
    currentPageBgColor?: string;
    /** Current page background image override */
    currentPageBgImage?: string;
    /** Total page count */
    pageCount: number;
    /** Go to a specific page */
    goToPage: (index: number) => void;
    /** Go to next page */
    nextPage: () => void;
    /** Go to previous page */
    prevPage: () => void;
    /** Whether carousel is active (multi-page + enabled) */
    isCarouselActive: boolean;
    /** Pause auto-advance (on hover) */
    pause: () => void;
    /** Resume auto-advance */
    resume: () => void;
}

/**
 * Resolve the effective pages list.
 * When pages is empty/undefined, wraps top-level components as a single page (backward compat).
 */
function resolvePages(
    pages: ScreenPage[] | undefined,
    components: ScreenComponent[],
): ScreenPage[] {
    if (pages && pages.length > 0) return pages;
    return [{
        id: '__default__',
        name: '页面 1',
        components,
    }];
}

export function useScreenCarousel(
    pages: ScreenPage[] | undefined,
    components: ScreenComponent[],
    carouselConfig: CarouselConfig | undefined,
): CarouselState {
    const resolvedPages = resolvePages(pages, components);
    const pageCount = resolvedPages.length;
    const enabled = carouselConfig?.enabled === true && pageCount > 1;
    const interval = (carouselConfig?.intervalSeconds ?? 30) * 1000;
    const transitionMs = carouselConfig?.transitionDuration ?? 800;
    const loop = carouselConfig?.loop !== false;

    const [pageIndex, setPageIndex] = useState(0);
    const [prevPageIndex, setPrevPageIndex] = useState(0);
    const [transitioning, setTransitioning] = useState(false);
    const [paused, setPaused] = useState(false);
    const transitionTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const doTransition = useCallback((nextIndex: number) => {
        if (nextIndex === pageIndex) return;
        setPrevPageIndex(pageIndex);
        setTransitioning(true);
        setPageIndex(nextIndex);
        if (transitionTimerRef.current) clearTimeout(transitionTimerRef.current);
        transitionTimerRef.current = setTimeout(() => {
            setTransitioning(false);
        }, transitionMs);
    }, [pageIndex, transitionMs]);

    const goToPage = useCallback((index: number) => {
        const clamped = Math.max(0, Math.min(index, pageCount - 1));
        doTransition(clamped);
    }, [doTransition, pageCount]);

    const nextPage = useCallback(() => {
        if (pageIndex >= pageCount - 1) {
            if (loop) doTransition(0);
        } else {
            doTransition(pageIndex + 1);
        }
    }, [doTransition, loop, pageCount, pageIndex]);

    const prevPage = useCallback(() => {
        if (pageIndex <= 0) {
            if (loop) doTransition(pageCount - 1);
        } else {
            doTransition(pageIndex - 1);
        }
    }, [doTransition, loop, pageCount, pageIndex]);

    // Auto-advance timer
    useEffect(() => {
        if (!enabled || paused) return;
        const timer = setInterval(() => {
            setPageIndex(prev => {
                const next = loop
                    ? (prev + 1) % pageCount
                    : Math.min(prev + 1, pageCount - 1);
                if (next !== prev) {
                    setPrevPageIndex(prev);
                    setTransitioning(true);
                    if (transitionTimerRef.current) clearTimeout(transitionTimerRef.current);
                    transitionTimerRef.current = setTimeout(() => setTransitioning(false), transitionMs);
                }
                return next;
            });
        }, interval);
        return () => clearInterval(timer);
    }, [enabled, paused, interval, loop, pageCount, transitionMs]);

    // Keyboard navigation
    useEffect(() => {
        if (pageCount <= 1) return;
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'ArrowLeft') {
                e.preventDefault();
                prevPage();
            } else if (e.key === 'ArrowRight') {
                e.preventDefault();
                nextPage();
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [nextPage, pageCount, prevPage]);

    // Cleanup transition timer
    useEffect(() => {
        return () => {
            if (transitionTimerRef.current) clearTimeout(transitionTimerRef.current);
        };
    }, []);

    // Clamp pageIndex if pages change
    useEffect(() => {
        if (pageIndex >= pageCount) {
            setPageIndex(Math.max(0, pageCount - 1));
        }
    }, [pageCount, pageIndex]);

    const currentPage = resolvedPages[pageIndex] ?? resolvedPages[0];

    return {
        pageIndex,
        prevPageIndex,
        transitioning,
        currentPageComponents: currentPage?.components ?? [],
        currentPageBgColor: currentPage?.backgroundColor,
        currentPageBgImage: currentPage?.backgroundImage,
        pageCount,
        goToPage,
        nextPage,
        prevPage,
        isCarouselActive: enabled,
        pause: () => setPaused(true),
        resume: () => setPaused(false),
    };
}
