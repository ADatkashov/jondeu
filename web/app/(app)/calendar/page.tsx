'use client';
import { ChevronLeft, ChevronRight, Plus, Trash2 } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Badge, Button, Card, Dialog, DialogContent, EmptyState, Field, Input, PageHeader, Select } from '@/components/ui';
import { api } from '@/lib/api';
import { fmtTime, isoDate } from '@/lib/format';
import { useAction, useCalendar } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';
import { cn } from '@/lib/utils';

const TONE: Record<string, string> = { WORK_DEADLINE: 'bg-brand text-white', WORK_START: 'bg-navy text-white', MATERIAL_DELIVERY: 'bg-accent text-white', INSPECTION: 'bg-success text-white', CONTRACTOR_VISIT: 'bg-brand-100 text-brand', PAYMENT_DEADLINE: 'bg-danger text-white' };
const TYPES = ['CONTRACTOR_VISIT', 'INSPECTION', 'MATERIAL_DELIVERY', 'PAYMENT_DEADLINE', 'WORK_DEADLINE'];

function startOfWeek(d: Date) { const x = new Date(d); x.setDate(d.getDate() - ((d.getDay() + 6) % 7)); x.setHours(0, 0, 0, 0); return x; }

export default function CalendarPage() {
  const { t, locale } = useI18n();
  const { project, projectId } = useProject();
  const [mode, setMode] = useState<'month' | 'week'>('month');
  const [cursor, setCursor] = useState(() => new Date());
  const [adding, setAdding] = useState<string | null>(null);
  const [form, setForm] = useState({ title: '', type: 'CONTRACTOR_VISIT', time: '10:00' });
  const range = useMemo(() => {
    if (mode === 'week') { const s = startOfWeek(cursor); const e = new Date(s); e.setDate(s.getDate() + 6); return { from: s, to: e, days: 7 }; }
    const first = new Date(cursor.getFullYear(), cursor.getMonth(), 1); const s = startOfWeek(first);
    const last = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 0); const e = new Date(startOfWeek(last)); e.setDate(e.getDate() + 6);
    return { from: s, to: e, days: Math.round((+e - +s) / 86400000) + 1 };
  }, [mode, cursor]);
  const q = useCalendar(isoDate(range.from), isoDate(range.to));
  const canEdit = ['OWNER', 'CONTRACTOR', 'PROJECT_MANAGER', 'ADMIN'].includes(project?.myRole ?? '');
  const create = useAction((d: string) => api.post(`/api/v1/projects/${projectId}/calendar`, { title: form.title, type: form.type, startsAt: new Date(`${d}T${form.time}:00+05:00`).toISOString() }), { success: 'cal.added', invalidate: ['calendar'], onDone: () => { setAdding(null); setForm({ ...form, title: '' }); } });
  const del = useAction((id: string) => api.del(`/api/v1/calendar/${id}`), { success: 'cal.deleted', invalidate: ['calendar'] });
  if (!projectId) return <EmptyState title={t('dash.noProject')} />;
  const step = (n: number) => { const d = new Date(cursor); mode === 'week' ? d.setDate(d.getDate() + 7 * n) : d.setMonth(d.getMonth() + n, 1); setCursor(d); };
  const days = Array.from({ length: range.days }, (_, i) => { const d = new Date(range.from); d.setDate(range.from.getDate() + i); return d; });
  const byDay = new Map<string, NonNullable<typeof q.data>>();
  (q.data ?? []).forEach((e) => byDay.set(e.date, [...(byDay.get(e.date) ?? []), e]));
  const todayKey = isoDate(new Date());
  return (
    <>
      <PageHeader title={t('nav.calendar')} actions={<>
        <div className="flex rounded-control border border-line bg-surface p-0.5 text-sm">{(['month', 'week'] as const).map((m) => <button key={m} onClick={() => setMode(m)} aria-pressed={mode === m} className={cn('rounded-[10px] px-3 py-1.5 font-medium', mode === m ? 'bg-navy text-white' : 'text-ink-soft')}>{t(`cal.${m}`)}</button>)}</div>
        {canEdit && <Button onClick={() => setAdding(todayKey)}><Plus className="h-4 w-4" />{t('cal.add')}</Button>}</>} />
      <Card>
        <div className="mb-3 flex items-center justify-between"><Button variant="ghost" size="icon" aria-label={t('common.back')} onClick={() => step(-1)}><ChevronLeft /></Button>
          <h2 className="text-lg font-semibold capitalize">{new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(cursor)}</h2><Button variant="ghost" size="icon" aria-label={t('common.next')} onClick={() => step(1)}><ChevronRight /></Button></div>
        <div className="grid grid-cols-7 gap-px overflow-hidden rounded-control bg-line text-xs">
          {days.slice(0, 7).map((d) => <div key={+d} className="bg-canvas p-2 text-center font-semibold text-ink-soft">{new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(d)}</div>)}
          {days.map((d) => {
            const k = isoDate(d); const ev = byDay.get(k) ?? []; const other = mode === 'month' && d.getMonth() !== cursor.getMonth();
            return (
              <div key={k} className={cn('group min-h-[88px] bg-surface p-1.5', other && 'bg-canvas/60 text-ink-soft/60', mode === 'week' && 'min-h-[260px]')}>
                <div className="flex items-center justify-between"><span className={cn('grid h-6 w-6 place-items-center rounded-full text-xs font-semibold', k === todayKey && 'bg-navy text-white')}>{d.getDate()}</span>
                  {canEdit && <button aria-label={t('cal.add')} className="hidden text-ink-soft hover:text-brand group-hover:block" onClick={() => setAdding(k)}><Plus className="h-3.5 w-3.5" /></button>}</div>
                <ul className="mt-1 space-y-1">{ev.slice(0, mode === 'week' ? 20 : 3).map((e) => (
                  <li key={e.id} className={cn('flex items-center justify-between gap-1 truncate rounded px-1.5 py-0.5 text-[11px] font-medium', TONE[e.type] ?? 'bg-canvas')} title={`${t(`cal.${e.type}`)}: ${e.title}`}>
                    <span className="truncate">{e.startsAt && e.source === 'EVENT' ? `${fmtTime(e.startsAt, locale)} ` : ''}{e.title}</span>
                    {e.editable && canEdit && <button aria-label={t('common.delete')} onClick={() => del.mutate(e.refId)}><Trash2 className="h-3 w-3" /></button>}</li>))}
                  {ev.length > 3 && mode === 'month' && <li className="text-[11px] text-ink-soft">+{ev.length - 3}</li>}</ul>
              </div>
            );
          })}
        </div>
        <div className="mt-3 flex flex-wrap gap-2">{Object.keys(TONE).map((k) => <Badge key={k} className={TONE[k]}>{t(`cal.${k}`)}</Badge>)}</div>
      </Card>
      <Dialog open={!!adding} onOpenChange={(o) => !o && setAdding(null)}>{adding && <DialogContent title={t('cal.add')}>
        <div className="grid gap-3 sm:grid-cols-2"><div className="sm:col-span-2"><Field label={t('cal.eventTitle')}><Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></Field></div>
          <Field label={t('cal.type')}><Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>{TYPES.map((x) => <option key={x} value={x}>{t(`cal.${x}`)}</option>)}</Select></Field>
          <Field label={t('cal.time')}><Input type="time" value={form.time} onChange={(e) => setForm({ ...form, time: e.target.value })} /></Field>
          <Field label={t('cal.date')}><Input type="date" value={adding} onChange={(e) => setAdding(e.target.value)} /></Field></div>
        <div className="mt-4 flex justify-end gap-2"><Button variant="ghost" onClick={() => setAdding(null)}>{t('common.cancel')}</Button><Button loading={create.isPending} disabled={form.title.trim().length < 2} onClick={() => create.mutate(adding)}>{t('common.save')}</Button></div></DialogContent>}</Dialog>
    </>
  );
}
