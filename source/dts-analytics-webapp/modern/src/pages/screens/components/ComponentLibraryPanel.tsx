import { useDrag } from 'react-dnd';
import { componentLibrary } from '../componentLibrary';
import type { ComponentItem, ComponentCategory } from '../types';

interface DraggableComponentItemProps {
    item: ComponentItem;
}

function DraggableComponentItem({ item }: DraggableComponentItemProps) {
    const [{ isDragging }, drag] = useDrag(() => ({
        type: 'COMPONENT',
        item: item,
        collect: (monitor) => ({
            isDragging: monitor.isDragging(),
        }),
    }));

    return (
        <div
            ref={(node) => { drag(node); }}
            className="component-item"
            style={{ opacity: isDragging ? 0.5 : 1 }}
        >
            <div className="component-item-icon">{item.icon}</div>
            <span className="component-item-name">{item.name}</span>
        </div>
    );
}

export function ComponentLibraryPanel() {
    return (
        <div className="component-library">
            <div className="component-library-header">
                <h3>组件库</h3>
            </div>
            <div className="component-library-content">
                {componentLibrary.map((category: ComponentCategory) => (
                    <div key={category.name} className="component-category">
                        <div className="component-category-title">
                            {category.icon} {category.name}
                        </div>
                        <div className="component-grid">
                            {category.items.map((item: ComponentItem) => (
                                <DraggableComponentItem key={item.type} item={item} />
                            ))}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
