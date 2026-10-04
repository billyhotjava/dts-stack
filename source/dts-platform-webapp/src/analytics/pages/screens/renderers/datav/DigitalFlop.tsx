import React, { useEffect, useRef, useState, useMemo } from 'react';
import { SCREEN_MONO_FONT_FAMILY } from '../../screenTypography';

interface DigitalFlopProps {
  number?: number;
  content?: string;
  style?: { fontSize?: number; fill?: string; fontFamily?: string };
  backgroundColor?: string;
}

const DigitSpan: React.FC<{ digit: string; fontSize: number; color: string; fontFamily?: string }> = ({
  digit,
  fontSize,
  color,
  fontFamily,
}) => {
  const [displayDigit, setDisplayDigit] = useState(digit);
  const prevRef = useRef(digit);

  useEffect(() => {
    if (prevRef.current !== digit) {
      prevRef.current = digit;
      setDisplayDigit(digit);
    }
  }, [digit]);

  const isNum = /\d/.test(displayDigit);

  return (
    <span
      style={{
        display: 'inline-block',
        overflow: 'hidden',
        fontFamily: fontFamily || SCREEN_MONO_FONT_FAMILY,
        fontVariantNumeric: 'tabular-nums',
        fontSize,
        color,
        width: isNum ? `${fontSize * 0.65}px` : 'auto',
        textAlign: 'center',
        transition: 'transform 0.4s cubic-bezier(0.23, 1, 0.32, 1)',
        lineHeight: 1.2,
      }}
    >
      {displayDigit}
    </span>
  );
};

export const DigitalFlop: React.FC<DigitalFlopProps> = ({
  number = 0,
  content,
  style,
  backgroundColor,
}) => {
  const fontSize = style?.fontSize ?? 30;
  const color = style?.fill ?? 'var(--color-text-primary, #fff)';
  const fontFamily = style?.fontFamily;

  const formatted = useMemo(() => {
    const numStr = number.toLocaleString();
    if (content) {
      return content.replace(/\{nt\}/g, numStr);
    }
    return numStr;
  }, [number, content]);

  const chars = useMemo(() => formatted.split(''), [formatted]);

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'baseline',
        fontVariantNumeric: 'tabular-nums',
        ...(backgroundColor ? { background: backgroundColor, padding: '4px 8px', borderRadius: 4 } : {}),
      }}
    >
      {chars.map((ch, i) => (
        <DigitSpan key={`${i}-${ch}`} digit={ch} fontSize={fontSize} color={color} fontFamily={fontFamily} />
      ))}
    </span>
  );
};
