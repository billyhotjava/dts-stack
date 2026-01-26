import { ReactNode, useMemo, useState } from 'react';
import GridLayout, { Layout, WidthProvider, Responsive } from 'react-grid-layout';
import './DashboardGrid.css';

const ResponsiveGridLayout = WidthProvider(Responsive);

// Dashboard Card type
export interface DashCard {
  id: number | string;
  row: number;
  col: number;
  size_x: number;
  size_y: number;
  card?: {
    id: number;
    name: string;
    display?: string;
  };
}

export interface DashboardGridProps {
  cards: DashCard[];
  isEditing?: boolean;
  onLayoutChange?: (layout: Layout[]) => void;
  onCardClick?: (cardId: number | string) => void;
  renderCard: (card: DashCard) => ReactNode;
  className?: string;
  cols?: number;
  rowHeight?: number;
  margin?: [number, number];
  containerPadding?: [number, number];
}

export function DashboardGrid({
  cards,
  isEditing = false,
  onLayoutChange,
  onCardClick,
  renderCard,
  className = '',
  cols = 24,
  rowHeight = 40,
  margin = [12, 12],
  containerPadding = [0, 0],
}: DashboardGridProps) {
  // Convert DashCards to react-grid-layout Layout
  const layout = useMemo<Layout[]>(() => {
    return cards.map((card) => ({
      i: String(card.id),
      x: card.col,
      y: card.row,
      w: card.size_x,
      h: card.size_y,
      minW: 2,
      minH: 2,
      static: !isEditing,
    }));
  }, [cards, isEditing]);

  const handleLayoutChange = (newLayout: Layout[]) => {
    if (isEditing && onLayoutChange) {
      onLayoutChange(newLayout);
    }
  };

  const handleCardClick = (cardId: number | string) => {
    if (!isEditing && onCardClick) {
      onCardClick(cardId);
    }
  };

  // Responsive breakpoints
  const breakpoints = { lg: 1200, md: 996, sm: 768, xs: 480, xxs: 0 };
  const colsConfig = { lg: cols, md: 18, sm: 12, xs: 6, xxs: 3 };

  return (
    <div className={`mb-dashboard-grid ${isEditing ? 'mb-dashboard-grid--editing' : ''} ${className}`}>
      <ResponsiveGridLayout
        className="mb-dashboard-grid__layout"
        layouts={{ lg: layout }}
        breakpoints={breakpoints}
        cols={colsConfig}
        rowHeight={rowHeight}
        margin={margin}
        containerPadding={containerPadding}
        isDraggable={isEditing}
        isResizable={isEditing}
        onLayoutChange={handleLayoutChange}
        draggableHandle=".mb-dashboard-card__drag-handle"
        resizeHandles={['se', 'sw', 'ne', 'nw', 'e', 'w', 'n', 's']}
        useCSSTransforms
      >
        {cards.map((card) => (
          <div
            key={String(card.id)}
            className={`mb-dashboard-card ${isEditing ? 'mb-dashboard-card--editing' : ''}`}
            onClick={() => handleCardClick(card.id)}
          >
            {isEditing && (
              <div className="mb-dashboard-card__drag-handle">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="9" cy="5" r="1" />
                  <circle cx="9" cy="12" r="1" />
                  <circle cx="9" cy="19" r="1" />
                  <circle cx="15" cy="5" r="1" />
                  <circle cx="15" cy="12" r="1" />
                  <circle cx="15" cy="19" r="1" />
                </svg>
              </div>
            )}
            <div className="mb-dashboard-card__content">
              {renderCard(card)}
            </div>
          </div>
        ))}
      </ResponsiveGridLayout>
    </div>
  );
}

// Simple Grid Layout (non-responsive, for simpler use cases)
export interface SimpleGridLayoutProps {
  children: ReactNode[];
  layout: Layout[];
  isEditing?: boolean;
  onLayoutChange?: (layout: Layout[]) => void;
  className?: string;
  cols?: number;
  rowHeight?: number;
  width?: number;
}

export function SimpleGridLayout({
  children,
  layout,
  isEditing = false,
  onLayoutChange,
  className = '',
  cols = 12,
  rowHeight = 100,
  width = 1200,
}: SimpleGridLayoutProps) {
  return (
    <div className={`mb-simple-grid ${isEditing ? 'mb-simple-grid--editing' : ''} ${className}`}>
      <GridLayout
        className="mb-simple-grid__layout"
        layout={layout}
        cols={cols}
        rowHeight={rowHeight}
        width={width}
        isDraggable={isEditing}
        isResizable={isEditing}
        onLayoutChange={onLayoutChange}
        margin={[16, 16]}
        useCSSTransforms
      >
        {children}
      </GridLayout>
    </div>
  );
}

// Card Grid (static CSS grid for card layouts)
export interface CardGridProps {
  children: ReactNode;
  columns?: 1 | 2 | 3 | 4 | 5 | 6;
  gap?: 'sm' | 'md' | 'lg';
  className?: string;
}

export function CardGrid({
  children,
  columns = 3,
  gap = 'md',
  className = '',
}: CardGridProps) {
  return (
    <div
      className={`mb-card-grid mb-card-grid--cols-${columns} mb-card-grid--gap-${gap} ${className}`}
    >
      {children}
    </div>
  );
}
