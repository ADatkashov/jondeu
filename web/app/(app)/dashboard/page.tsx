'use client';
import { Calendar, CheckCircle2, Gauge, ShieldCheck, Wallet } from 'lucide-react';
import Link from 'next/link';
import { StatusBadge } from '@/components/Status';
import { Button, EmptyState, ErrorState, PageHeader, Progress, Skeleton, StatCard } from '@/components/ui';
import { ActivityWidget, BudgetWidget, MastersWidget, MaterialsWidget, MiniCalendar, PhotosWidget, QualityWidget, StagesWidget } from '@/features/DashboardWidgets';
import { fmtDate, fmtMoney } from '@/lib/format';
import { useOverview } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';

export default function DashboardPage() {
  const { t, locale } = useI18n();
  const { project, loading } = useProject();
  const q = useOverview();
  if (loading) return <Skeleton className="h-64" />;
  if (!project) return <EmptyState title={t('dash.noProject')} text={t('dash.noProjectHint')} action={<Link href="/projects"><Button>{t('projects.create')}</Button></Link>} />;
  if (q.isLoading) return <div className="grid gap-4 md:grid-cols-4">{[...Array(8)].map((_, i) => <Skeleton key={i} className="h-24" />)}</div>;
  if (q.isError || !q.data) return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  const o = q.data;
  const f = o.finance;
  const qualityTone = o.quality.status === 'PASSED' ? 'green' : o.quality.status === 'FAILED' ? 'red' : o.quality.status === 'NEEDS_IMPROVEMENT' ? 'orange' : undefined;
  return (
    <>
      <PageHeader title={t('dash.title')} subtitle={`${project.name} · ${project.areaM2} m² · ${project.address ?? project.city}`} actions={project.demo ? <span className="rounded-full bg-accent-50 px-3 py-1 text-xs font-semibold text-accent-600">{t('demo.label')}</span> : undefined} />
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 2xl:grid-cols-7">
        <StatCard label={t('kpi.status')} value={<StatusBadge status={project.status} />} sub={o.progress.currentStage?.title} icon={<CheckCircle2 className="h-4 w-4" />} />
        <StatCard label={t('kpi.budget')} value={fmtMoney(f.approvedBudget)} sub={t('kpi.approved')} icon={<Wallet className="h-4 w-4" />} />
        <StatCard label={t('kpi.spent')} value={fmtMoney(f.paid)} sub={`${f.utilizationPercent.toFixed(0)}% ${t('budget.used')}`} />
        <StatCard label={t('kpi.remaining')} value={fmtMoney(f.remaining)} tone="green" sub={f.reserved > 0 ? t('kpi.reserved', { v: fmtMoney(f.reserved) }) : undefined} />
        <StatCard label={t('kpi.progress')} value={`${o.progress.percent.toFixed(0)}%`} sub={<Progress value={o.progress.percent} className="mt-1" />} icon={<Gauge className="h-4 w-4" />} />
        <StatCard label={t('kpi.deadline')} value={fmtDate(o.schedule.endDate, locale)} sub={o.schedule.daysLeft != null ? (o.schedule.daysLeft >= 0 ? t('kpi.daysLeft', { n: o.schedule.daysLeft }) : t('kpi.overdue', { n: -o.schedule.daysLeft })) : undefined} tone={o.schedule.overdueStages ? 'orange' : undefined} icon={<Calendar className="h-4 w-4" />} />
        <StatCard label={t('kpi.quality')} value={t(`status.${o.quality.status}`)} tone={qualityTone} icon={<ShieldCheck className="h-4 w-4" />} />
      </div>
      <div className="mt-4 grid gap-4 xl:grid-cols-[1.4fr_1fr]"><StagesWidget /><MiniCalendar /></div>
      <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-4"><BudgetWidget o={o} /><MaterialsWidget /><MastersWidget /><PhotosWidget /></div>
      <div className="mt-4 grid gap-4 lg:grid-cols-[1.4fr_1fr]"><ActivityWidget /><QualityWidget o={o} /></div>
    </>
  );
}
