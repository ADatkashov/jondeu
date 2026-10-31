import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { messages } from '@/lib/messages';
import { translate } from '@/lib/i18n';

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((f) => { const p = path.join(dir, f); return statSync(p).isDirectory() ? (f === '__tests__' || f === 'node_modules' ? [] : walk(p)) : /\.tsx?$/.test(f) ? [p] : []; });
}
const root = path.resolve(__dirname, '../..');
const sources = [...walk(path.join(root, 'app')), ...walk(path.join(root, 'src'))].filter((f) => !f.endsWith('messages.ts'));

describe('translations', () => {
  it('has identical key sets in kk, ru and en', () => {
    const ru = Object.keys(messages.ru).sort();
    expect(Object.keys(messages.kk).sort()).toEqual(ru);
    expect(Object.keys(messages.en).sort()).toEqual(ru);
  });

  it('has no empty strings', () => {
    for (const lang of ['ru', 'kk', 'en'] as const) for (const [k, v] of Object.entries(messages[lang])) expect(v.trim(), `${lang}:${k}`).not.toBe('');
  });

  it('defines every statically referenced t("key")', () => {
    const missing = new Set<string>();
    for (const f of sources) {
      const src = readFileSync(f, 'utf8');
      for (const m of src.matchAll(/\bt\(\s*['"`]([a-zA-Z0-9_.]+)['"`]/g)) if (!(m[1] in messages.ru)) missing.add(`${m[1]} (${path.relative(root, f)})`);
      for (const m of src.matchAll(/(?:success|label|title):\s*'([a-z]+\.[A-Za-z]+)'/g)) if (!(m[1] in messages.ru)) missing.add(`${m[1]} (${path.relative(root, f)})`);
    }
    expect([...missing]).toEqual([]);
  });

  it('covers every enum value rendered through dynamic keys', () => {
    const enums: Record<string, string[]> = {
      status: ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'PROPOSALS_RECEIVED', 'CONTRACTOR_SELECTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NOT_STARTED', 'BLOCKED', 'DELAYED', 'APPROVED', 'REJECTED', 'CHANGES_REQUESTED', 'SUPERSEDED',
        'REQUESTED', 'PENDING', 'PAID', 'FAILED', 'REFUNDED', 'PLANNED', 'ORDERED', 'PARTIALLY_DELIVERED', 'DELIVERED', 'PASSED', 'NEEDS_IMPROVEMENT', 'ACCEPTED'],
      role: ['CUSTOMER', 'MASTER', 'CONTRACTOR', 'COMPANY_ADMIN', 'PROJECT_MANAGER', 'INSPECTOR', 'PLATFORM_ADMIN', 'PLATFORM_SUPER_ADMIN'],
      prole: ['OWNER', 'CONTRACTOR', 'PROJECT_MANAGER', 'MASTER', 'INSPECTOR', 'ADMIN'],
      method: ['CARD', 'KASPI', 'BANK_TRANSFER', 'CASH'], ptype: ['ADVANCE', 'STAGE', 'MATERIAL', 'FINAL', 'ADDITIONAL'], urgency: ['LOW', 'NORMAL', 'HIGH'],
      mcat: ['BEFORE', 'DURING', 'AFTER', 'STAGE', 'INSPECTION', 'DOCUMENT', 'PLAN'], rt: ['online', 'connecting', 'offline'], sort: ['recommended', 'rating', 'price', 'experience'],
      cal: ['month', 'week', 'WORK_DEADLINE', 'WORK_START', 'MATERIAL_DELIVERY', 'INSPECTION', 'CONTRACTOR_VISIT', 'PAYMENT_DEADLINE'], cost: ['LABOR', 'MATERIAL', 'OTHER'],
    };
    const missing = Object.entries(enums).flatMap(([p, vs]) => vs.map((v) => `${p}.${v}`)).filter((k) => !(k in messages.ru));
    expect(missing).toEqual([]);
  });

  it('interpolates variables and falls back to key', () => {
    expect(translate('en', 'kpi.daysLeft', { n: 5 })).toBe('5 days left');
    expect(translate('kk', 'kpi.daysLeft', { n: 5 })).toBe('5 күн қалды');
    expect(translate('en', 'no.such.key')).toBe('no.such.key');
  });
});
