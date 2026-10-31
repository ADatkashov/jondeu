import { z } from 'zod';

/** Kazakhstan mobile number in any common format (+7…, 8…, spaces/dashes allowed). */
export function normalizePhone(raw: string): string | null {
  let d = raw.replace(/\D/g, '');
  if (d.length === 11 && (d.startsWith('8') || d.startsWith('7'))) d = d.slice(1);
  return d.length === 10 && (d.startsWith('7') || d.startsWith('6')) ? `+7${d}` : null;
}
export const phoneSchema = z.string().refine((v) => normalizePhone(v) !== null, { message: 'val.phone' }).transform((v) => normalizePhone(v) as string);

export const moneySchema = z.coerce.number({ invalid_type_error: 'val.number' }).min(0, 'val.number');
export const positiveSchema = z.coerce.number({ invalid_type_error: 'val.number' }).gt(0, 'val.positive');
