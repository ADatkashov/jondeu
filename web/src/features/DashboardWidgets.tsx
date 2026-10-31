'use client';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { AlertTriangle, Calendar as CalIcon, CheckCircle2, Clock, ShieldCheck } from 'lucide-react';
import Link from 'next/link';
import { useMemo } from 'react';
import { StatusBadge } from '@/components/Status';
import { Badge, Card, CardTitle, EmptyState, Progress, Skeleton } from '@/components/ui';
import { useActivity, useCalendar, useMaterials, useMedia, useMembers, useStages } from '@/lib/hooks';
import { fmtDate, fmtDateTime, fmtMoney, isoDate } from '@/lib/format';
import { useI18n } from '@/lib/i18n';
import type { Overview } from '@/lib/types';
import { absoluteUrl } from '@/lib/api';
import { cn } from '@/lib/utils';

export function StagesWidget() {
  const { t, locale } = useI18n();
  const { data, isLoading } = useStages();
  return (
    <Card>
      <CardTitle action={<Link href="/stages" className="text-xs font-semibold text-brand">{t('common.viewAll')}</Link>}>{t('widget.stages')}</CardTitle>
      {isLoading ? <Skeleton className="h-48" /> : !data?.length ? <EmptyState title={t('stages.empty')} text={t('stages.emptyHint')} /> : (
        <ul className="divide-y divide-line">
          {data.map((s) => (
            <li key={s.id} className="flex items-center gap-3 py-2.5">
              <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-canvas text-xs font-bold text-navy">{s.position}</span>
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-2"><span className="truncate text-sm font-medium">{s.title}</span><StatusBadge status={s.status} /></div>
                <div className="mt-1.5 flex items-center gap-3"><Progress value={s.progress} tone={s.status === 'COMPLETED' ? 'success' : s.status === 'DELAYED' ? 'accent' : 'brand'} className="h-1.5" />
                  <span className="tabular w-10 shrink-0 text-right text-xs text-ink-soft">{s.progress}%</span></div>
                <div className="mt-1 text-xs text-ink-soft">{fmtDate(s.startDate, locale)} → {fmtDate(s.actualEndDate ?? s.endDate, locale)}</div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

const DOT: Record<string, string> = { WORK_DEADLINE: 'bg-brand', WORK_START: 'bg-navy', MATERIAL_DELIVERY: 'bg-accent', INSPECTION: 'bg-success', CONTRACTOR_VISIT: 'bg-brand-600', PAYMENT_DEADLINE: 'bg-danger' };

export function MiniCalendar() {
  const { t, locale } = useI18n();
  const today = new Date();
  const first = new Date(today.getFullYear(), today.getMonth(), 1);
  const last = new Date(today.getFullYear(), today.getMonth() + 1, 0);
  const { data } = useCalendar(isoDate(first), isoDate(last));
  const byDay = useMemo(() => { const m = new Map<string, string[]>(); (data ?? []).forEach((e) => m.set(e.date, [...(m.get(e.date) ?? []), e.type])); return m; }, [data]);
  const offset = (first.getDay() + 6) % 7;
  const cells = Array.from({ length: offset + last.getDate() }, (_, i) => (i < offset ? null : i - offset + 1));
  const days = [...Array(7)].map((_, i) => new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(new Date(2024, 0, 1 + i)));
  return (
    <Card>
      <CardTitle action={<Link href="/calendar" className="text-xs font-semibold text-brand">{t('common.viewAll')}</Link>}>{new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(today)}</CardTitle>
      <div className="grid grid-cols-7 gap-1 text-center text-[11px] font-semibold text-ink-soft">{days.map((d) => <div key={d}>{d}</div>)}</div>
      <div className="mt-1 grid grid-cols-7 gap-1 text-center">
        {cells.map((d, i) => {
          if (!d) return <div key={i} />;
          const key = isoDate(new Date(today.getFullYear(), today.getMonth(), d));
          const ev = byDay.get(key) ?? [];
          const isToday = d === today.getDate();
          return (
            <div key={i} className={cn('flex h-10 flex-col items-center justify-center rounded-control text-sm', isToday ? 'bg-navy font-bold text-white' : 'hover:bg-canvas')} title={ev.length ? `${ev.length}` : undefined}>
              {d}<span className="mt-0.5 flex gap-0.5">{[...new Set(ev)].slice(0, 3).map((e) => <i key={e} className={cn('h-1 w-1 rounded-full', isToday ? 'bg-white' : DOT[e] ?? 'bg-ink-soft')} />)}</span>
            </div>
          );
        })}
      </div>
      <div className="mt-3 flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-ink-soft">
        {(['WORK_DEADLINE', 'MATERIAL_DELIVERY', 'INSPECTION', 'PAYMENT_DEADLINE'] as const).map((k) => <span key={k} className="flex items-center gap-1"><i className={cn('h-2 w-2 rounded-full', DOT[k])} />{t(`cal.${k}`)}</span>)}
      </div>
    </Card>
  );
}

const PIE = ['#2477D4', '#E99545', '#36966B'];

export function BudgetWidget({ o }: { o: Overview }) {
  const { t } = useI18n();
  const f = o.finance;
  const data = [{ name: t('cost.LABOR'), v: f.costBreakdown.LABOR ?? 0 }, { name: t('cost.MATERIAL'), v: f.costBreakdown.MATERIAL ?? 0 }, { name: t('cost.OTHER'), v: f.costBreakdown.OTHER ?? 0 }].filter((d) => d.v > 0);
  return (
    <Card>
      <CardTitle action={<Link href="/payments" className="text-xs font-semibold text-brand">{t('nav.payments')}</Link>}>{t('widget.budget')}</CardTitle>
      {f.approvedBudget <= 0 ? <EmptyState title={t('budget.none')} text={t('budget.noneHint')} /> : (
        <div className="grid items-center gap-4">
          <div className="relative mx-auto h-40 w-full max-w-[220px]" aria-label={t('widget.budget')}>
            <ResponsiveContainer><PieChart><Pie data={data} dataKey="v" innerRadius={48} outerRadius={70} paddingAngle={2} stroke="none">{data.map((_, i) => <Cell key={i} fill={PIE[i % 3]} />)}</Pie>
              <Tooltip formatter={(v: number) => fmtMoney(v)} /></PieChart></ResponsiveContainer>
            <div className="pointer-events-none absolute inset-0 grid place-items-center text-center"><div><div className="tabular text-lg font-bold">{f.utilizationPercent.toFixed(0)}%</div><div className="text-[11px] text-ink-soft">{t('budget.used')}</div></div></div>
          </div>
          <ul className="space-y-1.5 text-sm">
            {data.map((d, i) => <li key={d.name} className="flex items-center justify-between gap-2"><span className="flex items-center gap-2"><i className="h-2.5 w-2.5 rounded-full" style={{ background: PIE[i % 3] }} />{d.name}</span><b className="tabular">{fmtMoney(d.v)}</b></li>)}
            <li className="border-t border-line pt-1.5"><div className="flex justify-between"><span className="text-ink-soft">{t('budget.approved')}</span><b className="tabular">{fmtMoney(f.approvedBudget)}</b></div>
              <div className="flex justify-between"><span className="text-ink-soft">{t('budget.spent')}</span><b className="tabular">{fmtMoney(f.paid)}</b></div>
              <div className="flex justify-between"><span className="text-ink-soft">{t('budget.remaining')}</span><b className="tabular text-success">{fmtMoney(f.remaining)}</b></div></li>
          </ul>
        </div>
      )}
    </Card>
  );
}

export function MaterialsWidget() {
  const { t, locale } = useI18n();
  const { data, isLoading } = useMaterials();
  return (
    <Card>
      <CardTitle action={<Link href="/materials" className="text-xs font-semibold text-brand">{t('common.viewAll')}</Link>}>{t('nav.materials')}</CardTitle>
      {isLoading ? <Skeleton className="h-32" /> : !data?.items.length ? <EmptyState title={t('materials.empty')} /> : (
        <>
          <div className="mb-3 grid grid-cols-2 gap-2 text-xs"><div className="rounded-control bg-canvas p-2"><div className="text-ink-soft">{t('materials.planned')}</div><b className="tabular text-sm">{fmtMoney(data.summary.plannedCost)}</b></div>
            <div className="rounded-control bg-canvas p-2"><div className="text-ink-soft">{t('materials.actual')}</div><b className="tabular text-sm">{fmtMoney(data.summary.actualCost)}</b></div></div>
          <ul className="space-y-2">{data.items.slice(0, 4).map((m) => (
            <li key={m.id} className="flex items-center justify-between gap-2 text-sm"><div className="min-w-0"><div className="truncate font-medium">{m.name}</div><div className="text-xs text-ink-soft">{m.supplier} · {fmtDate(m.deliveryDate, locale)}</div></div><StatusBadge status={m.status} /></li>
          ))}</ul>
        </>
      )}
    </Card>
  );
}

export function MastersWidget() {
  const { t } = useI18n();
  const { data, isLoading } = useMembers();
  return (
    <Card>
      <CardTitle action={<Link href="/masters" className="text-xs font-semibold text-brand">{t('nav.masters')}</Link>}>{t('widget.team')}</CardTitle>
      {isLoading ? <Skeleton className="h-32" /> : (
        <ul className="space-y-2.5">{(data ?? []).map((m) => (
          <li key={m.userId} className="flex items-center gap-3"><div className="grid h-9 w-9 place-items-center rounded-full bg-brand-50 text-sm font-bold text-brand">{m.fullName.slice(0, 1)}</div>
            <div className="min-w-0 flex-1"><div className="truncate text-sm font-medium">{m.fullName}</div><div className="text-xs text-ink-soft">{t(`prole.${m.role}`)}</div></div></li>
        ))}</ul>
      )}
    </Card>
  );
}

export function PhotosWidget() {
  const { t } = useI18n();
  const { data, isLoading } = useMedia();
  const imgs = (data ?? []).filter((m) => m.contentType.startsWith('image/'));
  return (
    <Card>
      <CardTitle action={<Link href="/photos" className="text-xs font-semibold text-brand">{t('common.viewAll')}</Link>}>{t('nav.photos')}</CardTitle>
      {isLoading ? <Skeleton className="h-32" /> : !imgs.length ? <EmptyState title={t('photos.empty')} /> : (
        <div className="grid grid-cols-3 gap-2">
          {imgs.slice(0, 5).map((m) => <Link key={m.id} href="/photos" className="aspect-square overflow-hidden rounded-control bg-canvas">{/* eslint-disable-next-line @next/next/no-img-element */}<img src={absoluteUrl(m.url)} alt={m.caption ?? m.filename} className="h-full w-full object-cover" loading="lazy" /></Link>)}
          {imgs.length > 5 && <Link href="/photos" className="grid aspect-square place-items-center rounded-control bg-navy text-lg font-bold text-white">+{imgs.length - 5}</Link>}
        </div>
      )}
    </Card>
  );
}

export function ActivityWidget() {
  const { t, locale } = useI18n();
  const { data, isLoading } = useActivity(10);
  const icon = (type: string) => type.includes('COMPLETED') || type.includes('APPROVED') || type.includes('RECEIVED') || type.includes('DELIVERED') ? <CheckCircle2 className="h-4 w-4 text-success" /> : type.includes('DELAYED') ? <AlertTriangle className="h-4 w-4 text-accent" /> : <Clock className="h-4 w-4 text-brand" />;
  return (
    <Card>
      <CardTitle>{t('widget.activity')}</CardTitle>
      {isLoading ? <Skeleton className="h-40" /> : !data?.length ? <EmptyState title={t('activity.empty')} /> : (
        <ul className="space-y-3">{data.map((e) => (
          <li key={e.seq} className="flex gap-3"><span className="mt-0.5">{icon(e.type)}</span><div className="min-w-0 flex-1 text-sm"><div>{e.message ?? e.type}</div><div className="text-xs text-ink-soft">{fmtDateTime(e.createdAt, locale)}</div></div></li>
        ))}</ul>
      )}
    </Card>
  );
}

export function QualityWidget({ o }: { o: Overview }) {
  const { t } = useI18n();
  const tone = { PASSED: 'green', NEEDS_IMPROVEMENT: 'orange', FAILED: 'red', PENDING: 'neutral' } as const;
  return (
    <Card>
      <CardTitle action={<Link href="/quality" className="text-xs font-semibold text-brand">{t('common.viewAll')}</Link>}>{t('widget.quality')}</CardTitle>
      <div className="flex items-center gap-3"><ShieldCheck className="h-10 w-10 text-success" aria-hidden />
        <div><Badge tone={tone[o.quality.status]}>{t(`status.${o.quality.status}`)}</Badge><div className="mt-1 text-xs text-ink-soft">{t('quality.count', { n: o.quality.inspections })}</div></div></div>
      <div className="mt-4 grid grid-cols-4 gap-2 text-center text-xs">
        {(['PASSED', 'NEEDS_IMPROVEMENT', 'FAILED', 'PENDING'] as const).map((k) => <div key={k} className="rounded-control bg-canvas p-2"><div className="text-lg font-bold">{o.quality.byResult[k] ?? 0}</div><div className="text-ink-soft">{t(`status.${k}`)}</div></div>)}
      </div>
    </Card>
  );
}

export { CalIcon };
