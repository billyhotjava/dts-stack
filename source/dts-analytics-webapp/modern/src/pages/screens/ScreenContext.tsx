import { createContext, useContext, useReducer, ReactNode, useCallback } from 'react';
import type { ScreenState, ScreenAction, ScreenConfig, ScreenComponent } from './types';

const defaultConfig: ScreenConfig = {
    id: '',
    name: '未命名大屏',
    width: 1920,
    height: 1080,
    backgroundColor: '#0d1b2a',
    components: [],
};

const initialState: ScreenState = {
    config: defaultConfig,
    selectedIds: [],
    zoom: 100,
    showGrid: true,
    history: [defaultConfig],
    historyIndex: 0,
};

function screenReducer(state: ScreenState, action: ScreenAction): ScreenState {
    switch (action.type) {
        case 'SET_CONFIG': {
            const newHistory = state.history.slice(0, state.historyIndex + 1);
            newHistory.push(action.payload);
            return {
                ...state,
                config: action.payload,
                history: newHistory,
                historyIndex: newHistory.length - 1,
            };
        }

        case 'ADD_COMPONENT': {
            const newConfig = {
                ...state.config,
                components: [...state.config.components, action.payload],
            };
            const newHistory = state.history.slice(0, state.historyIndex + 1);
            newHistory.push(newConfig);
            return {
                ...state,
                config: newConfig,
                selectedIds: [action.payload.id],
                history: newHistory,
                historyIndex: newHistory.length - 1,
            };
        }

        case 'UPDATE_COMPONENT': {
            const newComponents = state.config.components.map((comp) =>
                comp.id === action.payload.id ? { ...comp, ...action.payload.updates } : comp
            );
            const newConfig = { ...state.config, components: newComponents };
            const newHistory = state.history.slice(0, state.historyIndex + 1);
            newHistory.push(newConfig);
            return {
                ...state,
                config: newConfig,
                history: newHistory,
                historyIndex: newHistory.length - 1,
            };
        }

        case 'DELETE_COMPONENTS': {
            const newComponents = state.config.components.filter(
                (comp) => !action.payload.includes(comp.id)
            );
            const newConfig = { ...state.config, components: newComponents };
            const newHistory = state.history.slice(0, state.historyIndex + 1);
            newHistory.push(newConfig);
            return {
                ...state,
                config: newConfig,
                selectedIds: [],
                history: newHistory,
                historyIndex: newHistory.length - 1,
            };
        }

        case 'SELECT_COMPONENTS':
            return { ...state, selectedIds: action.payload };

        case 'MOVE_COMPONENT': {
            const newComponents = state.config.components.map((comp) =>
                comp.id === action.payload.id
                    ? { ...comp, x: action.payload.x, y: action.payload.y }
                    : comp
            );
            const newConfig = { ...state.config, components: newComponents };
            // Don't add to history on every move (too many entries)
            return { ...state, config: newConfig };
        }

        case 'RESIZE_COMPONENT': {
            const newComponents = state.config.components.map((comp) =>
                comp.id === action.payload.id
                    ? { ...comp, width: action.payload.width, height: action.payload.height }
                    : comp
            );
            const newConfig = { ...state.config, components: newComponents };
            return { ...state, config: newConfig };
        }

        case 'REORDER_LAYER': {
            const { id, direction } = action.payload;
            const components = [...state.config.components];
            const index = components.findIndex((c) => c.id === id);
            if (index === -1) return state;

            const maxZIndex = Math.max(...components.map((c) => c.zIndex));
            const minZIndex = Math.min(...components.map((c) => c.zIndex));

            const newComponents = components.map((comp) => {
                if (comp.id !== id) return comp;
                switch (direction) {
                    case 'up':
                        return { ...comp, zIndex: Math.min(comp.zIndex + 1, maxZIndex + 1) };
                    case 'down':
                        return { ...comp, zIndex: Math.max(comp.zIndex - 1, 0) };
                    case 'top':
                        return { ...comp, zIndex: maxZIndex + 1 };
                    case 'bottom':
                        return { ...comp, zIndex: minZIndex - 1 };
                    default:
                        return comp;
                }
            });

            const newConfig = { ...state.config, components: newComponents };
            const newHistory = state.history.slice(0, state.historyIndex + 1);
            newHistory.push(newConfig);
            return {
                ...state,
                config: newConfig,
                history: newHistory,
                historyIndex: newHistory.length - 1,
            };
        }

        case 'SET_ZOOM':
            return { ...state, zoom: action.payload };

        case 'TOGGLE_GRID':
            return { ...state, showGrid: !state.showGrid };

        case 'UNDO': {
            if (state.historyIndex <= 0) return state;
            const newIndex = state.historyIndex - 1;
            return {
                ...state,
                config: state.history[newIndex],
                historyIndex: newIndex,
            };
        }

        case 'REDO': {
            if (state.historyIndex >= state.history.length - 1) return state;
            const newIndex = state.historyIndex + 1;
            return {
                ...state,
                config: state.history[newIndex],
                historyIndex: newIndex,
            };
        }

        default:
            return state;
    }
}

interface ScreenContextValue {
    state: ScreenState;
    dispatch: React.Dispatch<ScreenAction>;
    addComponent: (component: ScreenComponent) => void;
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void;
    deleteComponents: (ids: string[]) => void;
    selectComponents: (ids: string[]) => void;
    undo: () => void;
    redo: () => void;
    canUndo: boolean;
    canRedo: boolean;
}

const ScreenContext = createContext<ScreenContextValue | null>(null);

export function ScreenProvider({ children }: { children: ReactNode }) {
    const [state, dispatch] = useReducer(screenReducer, initialState);

    const addComponent = useCallback((component: ScreenComponent) => {
        dispatch({ type: 'ADD_COMPONENT', payload: component });
    }, []);

    const updateComponent = useCallback((id: string, updates: Partial<ScreenComponent>) => {
        dispatch({ type: 'UPDATE_COMPONENT', payload: { id, updates } });
    }, []);

    const deleteComponents = useCallback((ids: string[]) => {
        dispatch({ type: 'DELETE_COMPONENTS', payload: ids });
    }, []);

    const selectComponents = useCallback((ids: string[]) => {
        dispatch({ type: 'SELECT_COMPONENTS', payload: ids });
    }, []);

    const undo = useCallback(() => {
        dispatch({ type: 'UNDO' });
    }, []);

    const redo = useCallback(() => {
        dispatch({ type: 'REDO' });
    }, []);

    const canUndo = state.historyIndex > 0;
    const canRedo = state.historyIndex < state.history.length - 1;

    return (
        <ScreenContext.Provider
            value={{
                state,
                dispatch,
                addComponent,
                updateComponent,
                deleteComponents,
                selectComponents,
                undo,
                redo,
                canUndo,
                canRedo,
            }}
        >
            {children}
        </ScreenContext.Provider>
    );
}

export function useScreen() {
    const context = useContext(ScreenContext);
    if (!context) {
        throw new Error('useScreen must be used within a ScreenProvider');
    }
    return context;
}
