'use client';
import { zodResolver } from '@hookform/resolvers/zod';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { StatusBadge } from '@/components/Status';
import { Button, Card, Dialog, DialogContent, EmptyState, ErrorState, Field, Input, PageHeader, Skeleton, StatCard } from '@/components/ui';
import { api } from '@/lib/api';
import { fmtDate, fmtMoney } from '@/lib/format';
import { useAction, useMaterials } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';
import type { Material } from '@/lib/types';

const schema = z.object({ name: z.string().trim().min(2, 'val.required'), unit: z.string().trim().min(1, 'val.required'), plannedQty: z.coerce.number().gt(0, 'val.positive'),
  unitPrice: z.coerce.number().min(0, 'val.number'), supplier: z.string().optional(), deliveryDate: z.string().optional() });
type F = z.infer<typeof schema>;

function MaterialForm({ onClose }: { onClose: () => void }) {
  const { t } = useI18n();
  const { projectId } = useProject();
  const { register, handleSubmit, formState: { errors } } = useForm<F>({ resolver: zodResolver(schema), defaultValues: { unit: 'шт' } as Partial<F> });
  const save = useAction((v: F) => api.post(`/api/v1/projects/${projectId}/materials`, { ...v, deliveryDate: v.deliveryDate || undefined }), { success: 'materials.added', invalidate: ['materials'], onDone: onClose });
  return (
    <DialogContent title={t('materials.add')}>
      <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate className="grid gap-3 sm:grid-cols-2">
        <div className="sm:col-span-2"><Field label={t('materials.name')} error={errors.name?.message}><Input {...register('name')} /></Field></div>
        <Field label={t('estimates.qty')} error={errors.plannedQty?.message}><Input type="number" step="any" {...register('plannedQty')} /></Field>
        <Field label={t('estimates.unit')} error={errors.unit?.message}><Input {...register('unit')} /></Field>
        <Field label={t('estimates.price')} error={errors.unitPrice?.message}><Input type="number" step="any" {...register('unitPrice')} /></Field>
        <Field label={t('materials.supplier')}><Input {...register('supplier')} /></Field>
        <Field label={t('materials.delivery')}><Input type="date" {...register('deliveryDate')} /></Field>
        <div className="flex justify-end gap-2 sm:col-span-2"><Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button><Button type="submit" loading={save.isPending}>{t('common.save')}</Button></div>
      </form>
    </DialogContent>
  );
}

function Receive({ m, onClose }: { m: Material; onClose: () => void }) {
  const { t } = useI18n();
  const [qty, setQty] = useState(String(Math.max(m.plannedQty - m.receivedQty, 0)));
  const [price, setPrice] = useState('');
  const go = useAction(() => api.post(`/api/v1/materials/${m.id}/receive`, { quantity: Number(qty), actualUnitPrice: price ? Number(price) : undefined }), { success: 'materials.receivedOk', invalidate: ['materials'], onDone: onClose });
  return (<DialogContent title={`${t('materials.receive')}: ${m.name}`}><div className="grid gap-3 sm:grid-cols-2">
    <Field label={`${t('estimates.qty')} (${m.unit})`} hint={`${t('materials.left')}: ${m.plannedQty - m.receivedQty}`}><Input type="number" step="any" value={qty} onChange={(e) => setQty(e.target.value)} /></Field>
    <Field label={t('materials.actualPrice')}><Input type="number" step="any" value={price} onChange={(e) => setPrice(e.target.value)} placeholder={String(m.unitPrice)} /></Field></div>
    <div className="mt-4 flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button><Button loading={go.isPending} disabled={!(Number(qty) > 0)} onClick={() => go.mutate(undefined)}>{t('common.save')}</Button></div></DialogContent>);
}

export default function MaterialsPage() {
  const { t, locale } = useI18n();
  const { project, projectId } = useProject();
  const q = useMaterials();
  const [adding, setAdding] = useState(false);
  const [receiving, setReceiving] = useState<Material | null>(null);
  const can = ['OWNER', 'CONTRACTOR', 'PROJECT_MANAGER', 'ADMIN'].includes(project?.myRole ?? '');
  const order = useAction((id: string) => api.post(`/api/v1/materials/${id}/order`), { success: 'materials.ordered', invalidate: ['materials'] });
  const cancel = useAction((id: string) => api.post(`/api/v1/materials/${id}/cancel`), { success: 'materials.cancelled', invalidate: ['materials'] });
  if (!projectId) return <EmptyState title={t('dash.noProject')} />;
  const s = q.data?.summary;
  return (
    <>
      <PageHeader title={t('nav.materials')} actions={can && <Button onClick={() => setAdding(true)}><Plus className="h-4 w-4" />{t('materials.add')}</Button>} />
      {s && <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4"><StatCard label={t('materials.planned')} value={fmtMoney(s.plannedCost)} /><StatCard label={t('materials.actual')} value={fmtMoney(s.actualCost)} />
        <StatCard label={t('materials.variance')} value={fmtMoney(s.variance)} tone={s.variance > 0 ? 'red' : 'green'} /><StatCard label={t('materials.delivered')} value={`${s.delivered}/${s.total}`} /></div>}
      {q.isLoading ? <Skeleton className="h-64" /> : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : !q.data?.items.length ? <EmptyState title={t('materials.empty')} text={t('materials.emptyHint')} /> : (
        <Card className="overflow-x-auto p-0"><table className="w-full min-w-[820px] text-sm"><thead><tr className="border-b border-line text-left text-xs text-ink-soft"><th className="p-3">{t('materials.name')}</th><th>{t('materials.supplier')}</th><th className="text-right">{t('estimates.qty')}</th><th className="text-right">{t('materials.received')}</th><th className="text-right">{t('estimates.price')}</th><th className="text-right">{t('estimates.sum')}</th><th>{t('materials.delivery')}</th><th>{t('common.status')}</th><th /></tr></thead>
          <tbody className="divide-y divide-line">{q.data.items.map((m) => (
            <tr key={m.id}><td className="p-3 font-medium">{m.name}</td><td className="text-ink-soft">{m.supplier}</td><td className="tabular text-right">{m.plannedQty} {m.unit}</td><td className="tabular text-right">{m.receivedQty}</td>
              <td className="tabular text-right">{fmtMoney(m.unitPrice)}</td><td className="tabular text-right font-semibold">{fmtMoney(m.plannedCost)}</td><td>{fmtDate(m.deliveryDate, locale)}</td><td><StatusBadge status={m.status} /></td>
              <td className="p-3 text-right"><div className="flex justify-end gap-1">{can && m.status === 'PLANNED' && <Button size="sm" variant="outline" onClick={() => order.mutate(m.id)}>{t('materials.order')}</Button>}
                {can && ['ORDERED', 'PARTIALLY_DELIVERED'].includes(m.status) && <Button size="sm" onClick={() => setReceiving(m)}>{t('materials.receive')}</Button>}
                {can && m.receivedQty === 0 && !['CANCELLED'].includes(m.status) && <Button size="sm" variant="ghost" onClick={() => cancel.mutate(m.id)}>{t('common.cancel')}</Button>}</div></td></tr>
          ))}</tbody></table></Card>
      )}
      <Dialog open={adding} onOpenChange={setAdding}>{adding && <MaterialForm onClose={() => setAdding(false)} />}</Dialog>
      <Dialog open={!!receiving} onOpenChange={(o) => !o && setReceiving(null)}>{receiving && <Receive m={receiving} onClose={() => setReceiving(null)} />}</Dialog>
    </>
  );
}
