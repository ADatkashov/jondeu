const TZ = 'Asia/Almaty';

export function fmtMoney(v: number | string | null | undefined, locale = 'ru-RU'): string {
  const n = Number(v ?? 0);
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits: 0 }).format(Math.round(n))} ₸`;
}
export function fmtMoneyPrecise(v: number | string, locale = 'ru-RU'): string {
  return `${new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(v))} ₸`;
}
export function fmtDate(d?: string | null, locale = 'ru-RU'): string {
  if (!d) return '—';
  const date = d.length <= 10 ? new Date(`${d}T00:00:00`) : new Date(d);
  return new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: d.length <= 10 ? undefined : TZ }).format(date);
}
export function fmtDateTime(d?: string | null, locale = 'ru-RU'): string {
  if (!d) return '—';
  return new Intl.DateTimeFormat(locale, { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: TZ }).format(new Date(d));
}
export function fmtTime(d: string, locale = 'ru-RU'): string {
  return new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', timeZone: TZ }).format(new Date(d));
}
export function fmtPercent(v: number | string, digits = 0): string { return `${Number(v).toFixed(digits)}%`; }
/** Kazakhstan phone display: +7 701 123 45 67 */
export function fmtPhone(p: string): string {
  const d = p.replace(/\D/g, '');
  return d.length === 11 ? `+${d[0]} ${d.slice(1, 4)} ${d.slice(4, 7)} ${d.slice(7, 9)} ${d.slice(9)}` : p;
}
export function isoDate(d: Date): string {
  const y = d.getFullYear(), m = String(d.getMonth() + 1).padStart(2, '0'), day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
