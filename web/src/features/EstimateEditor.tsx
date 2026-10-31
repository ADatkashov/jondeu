'use client';
import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Trash2 } from 'lucide-react';
import { useFieldArray, useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button, DialogContent, Field, Input, Select } from '@/components/ui';
import { api } from '@/lib/api';
import { useAction } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import type { Estimate } from '@/lib/types';

const item = z.object({
  kind: z.enum(['LABOR', 'MATERIAL', 'OTHER']), title: z.string().trim().min(2, 'val.required'), unit: z.string().trim().min(1, 'val.required'),
  quantity: z.coerce.number({ invalid_type_error: 'val.number' }).gt(0, 'val.positive'), unitPrice: z.coerce.number({ invalid_type_error: 'val.number' }).min(0, 'val.number'),
});
const schema = z.object({
  items: z.array(item).min(1, 'val.items'), additionalCosts: z.coerce.number().min(0, 'val.number'), discount: z.coerce.number().min(0, 'val.number'),
  taxRate: z.coerce.number().min(0, 'val.number').max(100, 'val.number'),
});
type F = z.infer<typeof schema>;

/** Totals are deliberately NOT computed here: the backend is the only authority and returns them after save. */
export function EstimateEditor({ projectId, estimate, onClose }: { projectId: string; estimate?: Estimate; onClose: () => void }) {
  const { t } = useI18n();
  const { register, control, handleSubmit, formState: { errors } } = useForm<F>({
    resolver: zodResolver(schema),
    defaultValues: estimate
      ? { items: estimate.items.map((i) => ({ kind: i.kind, title: i.title, unit: i.unit, quantity: i.quantity, unitPrice: i.unitPrice })), additionalCosts: estimate.additionalCosts, discount: estimate.discount, taxRate: estimate.taxRate }
      : { items: [{ kind: 'LABOR', title: '', unit: 'м²', quantity: 1, unitPrice: 0 }], additionalCosts: 0, discount: 0, taxRate: 0 },
  });
  const { fields, append, remove } = useFieldArray({ control, name: 'items' });
  const save = useAction((v: F) => (estimate ? api.put<Estimate>(`/api/v1/estimates/${estimate.id}`, v) : api.post<Estimate>(`/api/v1/projects/${projectId}/estimates`, v)),
    { success: 'estimates.saved', invalidate: ['estimates'], onDone: onClose });
  return (
    <DialogContent title={estimate ? t('estimates.edit', { n: `${estimate.number} v${estimate.version}` }) : t('estimates.new')} wide>
      <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate className="space-y-4">
        <div className="space-y-2">
          {fields.map((f, i) => (
            <div key={f.id} className="grid grid-cols-12 items-start gap-2">
              <div className="col-span-12 sm:col-span-2"><Select aria-label={t('estimates.kind')} {...register(`items.${i}.kind`)}>{(['LABOR', 'MATERIAL', 'OTHER'] as const).map((k) => <option key={k} value={k}>{t(`cost.${k}`)}</option>)}</Select></div>
              <div className="col-span-12 sm:col-span-4"><Input aria-label={t('estimates.work')} aria-invalid={!!errors.items?.[i]?.title} placeholder={t('estimates.work')} {...register(`items.${i}.title`)} /></div>
              <div className="col-span-3 sm:col-span-1"><Input aria-label={t('estimates.qty')} type="number" step="any" {...register(`items.${i}.quantity`)} /></div>
              <div className="col-span-3 sm:col-span-1"><Input aria-label={t('estimates.unit')} {...register(`items.${i}.unit`)} /></div>
              <div className="col-span-5 sm:col-span-3"><Input aria-label={t('estimates.price')} type="number" step="any" {...register(`items.${i}.unitPrice`)} /></div>
              <Button variant="ghost" size="icon" aria-label={t('common.delete')} className="col-span-1" onClick={() => remove(i)} disabled={fields.length === 1}><Trash2 className="h-4 w-4" /></Button>
            </div>
          ))}
          {errors.items?.root && <p role="alert" className="text-xs text-danger">{t(errors.items.root.message ?? '')}</p>}
          {(errors.items as any)?.message && <p role="alert" className="text-xs text-danger">{t((errors.items as any).message)}</p>}
          <Button variant="outline" size="sm" onClick={() => append({ kind: 'LABOR', title: '', unit: 'м²', quantity: 1, unitPrice: 0 })}><Plus className="h-4 w-4" />{t('estimates.addItem')}</Button>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label={t('estimates.additional')} error={errors.additionalCosts?.message}><Input type="number" step="any" {...register('additionalCosts')} /></Field>
          <Field label={t('estimates.discount')} error={errors.discount?.message}><Input type="number" step="any" {...register('discount')} /></Field>
          <Field label={t('estimates.tax')} error={errors.taxRate?.message}><Input type="number" step="any" {...register('taxRate')} /></Field>
        </div>
        <p className="text-xs text-ink-soft">{t('estimates.serverTotals')}</p>
        <div className="flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button><Button type="submit" loading={save.isPending}>{t('common.save')}</Button></div>
      </form>
    </DialogContent>
  );
}
