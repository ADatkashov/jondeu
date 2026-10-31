'use client';
import { Printer } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Button, Card, CardTitle, EmptyState, ErrorState, PageHeader, Skeleton } from '@/components/ui';
import { fmtMoney } from '@/lib/format';
import { useOverview, useStages } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';

export default function ReportsPage() {
  const { t } = useI18n();
  const { project, projectId } = useProject();
  const o = useOverview();
  const stages = useStages();
  if (!projectId) return <EmptyState title={t('dash.noProject')} />;
  if (o.isLoading) return <Skeleton className="h-64" />;
  if (o.isError || !o.data) return <ErrorState error={o.error} onRetry={() => o.refetch()} />;
  const f = o.data.finance;
  // earned value: how much of each stage budget corresponds to completed work vs how much budget is allocated
  const rows = (stages.data ?? []).map((s) => ({ name: `${s.position}`, title: s.title, budget: s.budget, earned: Math.round((s.budget * s.progress) / 100) }));
  const earned = rows.reduce((a, r) => a + r.earned, 0);
  return (
    <>
      <PageHeader title={t('nav.reports')} subtitle={project?.name} actions={<Button variant="outline" onClick={() => window.print()}><Printer className="h-4 w-4" />{t('reports.print')}</Button>} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card><CardTitle>{t('reports.financial')}</CardTitle><dl className="grid grid-cols-[1fr_auto] gap-y-2 text-sm">
          <dt>{t('budget.approved')}</dt><dd className="tabular text-right font-semibold">{fmtMoney(f.approvedBudget)}</dd>
          <dt>{t('budget.spent')}</dt><dd className="tabular text-right font-semibold">{fmtMoney(f.paid)}</dd>
          <dt>{t('budget.remaining')}</dt><dd className="tabular text-right font-semibold text-success">{fmtMoney(f.remaining)}</dd>
          <dt>{t('materials.planned')}</dt><dd className="tabular text-right">{fmtMoney(f.materialsPlanned)}</dd>
          <dt>{t('materials.actual')}</dt><dd className="tabular text-right">{fmtMoney(f.materialsActual)}</dd>
          <dt>{t('reports.earned')}</dt><dd className="tabular text-right">{fmtMoney(earned)}</dd>
          <dt>{t('reports.advance')}</dt><dd className={`tabular text-right font-semibold ${f.paid - earned > 0 ? 'text-accent-600' : 'text-success'}`}>{fmtMoney(f.paid - earned)}</dd></dl>
          <p className="mt-3 text-xs text-ink-soft">{t('reports.advanceHint')}</p></Card>
        <Card><CardTitle>{t('reports.schedule')}</CardTitle><dl className="grid grid-cols-[1fr_auto] gap-y-2 text-sm">
          <dt>{t('kpi.progress')}</dt><dd className="tabular text-right font-semibold">{o.data.progress.percent.toFixed(1)}%</dd>
          <dt>{t('reports.stagesDone')}</dt><dd className="text-right">{o.data.progress.stagesCompleted}/{o.data.progress.stagesTotal}</dd>
          <dt>{t('reports.overdue')}</dt><dd className="text-right">{o.data.schedule.overdueStages}</dd>
          <dt>{t('kpi.quality')}</dt><dd className="text-right">{t(`status.${o.data.quality.status}`)}</dd></dl></Card>
      </div>
      <Card className="mt-4"><CardTitle>{t('reports.budgetVsProgress')}</CardTitle>
        <div className="h-72" role="img" aria-label={t('reports.budgetVsProgress')}><ResponsiveContainer><BarChart data={rows}><CartesianGrid strokeDasharray="3 3" stroke="#E8E4DC" /><XAxis dataKey="name" /><YAxis tickFormatter={(v) => `${(v / 1e6).toFixed(1)}M`} />
          <Tooltip formatter={(v: number) => fmtMoney(v)} labelFormatter={(_, p) => p?.[0]?.payload?.title} /><Legend /><Bar dataKey="budget" name={t('reports.stageBudget')} fill="#D3E4F8" radius={[6, 6, 0, 0]} /><Bar dataKey="earned" name={t('reports.earned')} fill="#2477D4" radius={[6, 6, 0, 0]} /></BarChart></ResponsiveContainer></div></Card>
    </>
  );
}
