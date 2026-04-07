import { useState, useEffect } from 'react';
import { analyticsApi, type CardListItem } from '../../../api/analyticsApi';

interface CardIdPickerProps {
    value: number;
    onChange: (cardId: number) => void;
    placeholder?: string;
}

// Module-level cache: loaded once, shared by all instances
let cachedCards: CardListItem[] | null = null;
let fetchPromise: Promise<CardListItem[]> | null = null;

function loadCards(): Promise<CardListItem[]> {
    if (cachedCards) return Promise.resolve(cachedCards);
    if (fetchPromise) return fetchPromise;
    fetchPromise = analyticsApi.listCards()
        .then((list) => {
            cachedCards = (list ?? []).filter((c) => !c.archived);
            return cachedCards;
        })
        .catch(() => {
            fetchPromise = null;
            return [] as CardListItem[];
        });
    return fetchPromise;
}

export function CardIdPicker({ value, onChange, placeholder }: CardIdPickerProps) {
    const [cards, setCards] = useState<CardListItem[]>(cachedCards ?? []);
    const [loading, setLoading] = useState(!cachedCards);

    useEffect(() => {
        if (cachedCards) {
            setCards(cachedCards);
            setLoading(false);
            return;
        }
        let cancelled = false;
        setLoading(true);
        loadCards().then((list) => {
            if (!cancelled) {
                setCards(list);
                setLoading(false);
            }
        });
        return () => { cancelled = true; };
    }, []);

    return (
        <select
            className="property-input"
            value={value || 0}
            onChange={(e) => onChange(Number(e.target.value))}
            style={value > 0 ? undefined : { color: '#888' }}
        >
            <option value={0}>
                {loading ? '加载中...' : (placeholder ?? '-- 选择 Card --')}
            </option>
            {cards.map((c) => (
                <option key={c.id} value={c.id}>
                    #{c.id} {c.name || '(未命名)'}
                </option>
            ))}
        </select>
    );
}

/** Force refresh the card list cache (e.g. after creating a new card) */
export function invalidateCardCache() {
    cachedCards = null;
    fetchPromise = null;
}
