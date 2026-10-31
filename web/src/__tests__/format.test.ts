import { describe, expect, it } from 'vitest';
import { fmtMoney, fmtPercent, fmtPhone, isoDate } from '@/lib/format';
import { normalizePhone, phoneSchema, positiveSchema } from '@/lib/validation';

describe('money & dates', () => {
  it('formats tenge with grouping', () => {
    expect(fmtMoney(12455000).replace(/\s/g, ' ')).toBe('12 455 000 ₸');
    expect(fmtMoney('8753000.00')).toContain('8');
    expect(fmtMoney(null)).toContain('0');
  });
  it('formats percents and phones', () => {
    expect(fmtPercent(65.04, 1)).toBe('65.0%');
    expect(fmtPhone('+77011234567')).toBe('+7 701 123 45 67');
  });
  it('formats local ISO dates without timezone shift', () => { expect(isoDate(new Date(2024, 4, 5))).toBe('2024-05-05'); });
});

describe('validation', () => {
  it('normalizes Kazakhstan numbers', () => {
    expect(normalizePhone('8 (701) 123-45-67')).toBe('+77011234567');
    expect(normalizePhone('+7 701 123 45 67')).toBe('+77011234567');
    expect(normalizePhone('12345')).toBeNull();
    expect(normalizePhone('+1 202 555 0100')).toBeNull();
  });
  it('rejects bad phone in schema, accepts good', () => {
    expect(phoneSchema.safeParse('abc').success).toBe(false);
    expect(phoneSchema.parse('87011234567')).toBe('+77011234567');
  });
  it('requires positive amounts', () => {
    expect(positiveSchema.safeParse('0').success).toBe(false);
    expect(positiveSchema.safeParse('-5').success).toBe(false);
    expect(positiveSchema.safeParse('1500').success).toBe(true);
  });
});
