'use client';
import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useQueryClient } from '@tanstack/react-query';
import { Button, DialogContent, Field, Input, Select, Textarea, Progress } from '@/components/ui';
import { api, ApiError } from '@/lib/api';
import { useCategories, errorText } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';
import { useToast } from '@/lib/toast';
import type { Project } from '@/lib/types';

const schema = z.object({
  name: z.string().trim().min(3, 'val.min3'),
  propertyType: z.enum(['APARTMENT', 'HOUSE']),
  rooms: z.coerce.number().int().min(0).max(100),
  areaM2: z.coerce.number({ invalid_type_error: 'val.number' }).gt(0, 'val.positive').max(100000),
  address: z.string().trim().min(5, 'val.required'),
  description: z.string().trim().min(10, 'val.min10'),
  category: z.string().min(1, 'val.required'),
  budgetMin: z.coerce.number().min(0, 'val.number'),
  budgetMax: z.coerce.number().gt(0, 'val.positive'),
  materialsIncluded: z.enum(['true', 'false']),
  paymentMethod: z.enum(['CARD', 'CASH', 'BANK_TRANSFER', 'KASPI']),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  urgency: z.enum(['LOW', 'NORMAL', 'HIGH']),
}).refine((v) => v.budgetMin <= v.budgetMax, { path: ['budgetMin'], message: 'val.budgetRange' })
  .refine((v) => !v.startDate || !v.endDate || v.endDate >= v.startDate, { path: ['endDate'], message: 'val.dateRange' });
type F = z.infer<typeof schema>;
const STEPS: (keyof F)[][] = [['name', 'propertyType', 'rooms', 'areaM2', 'address'], ['category', 'description'], ['budgetMin', 'budgetMax', 'materialsIncluded', 'paymentMethod'], ['startDate', 'endDate', 'urgency'], []];

/** 5-step request wizard: info -> requirements -> budget -> timeline -> confirmation. Persists as a DRAFT first, then submits. */
export function ProjectWizard({ onClose }: { onClose: () => void }) {
  const { t, lang } = useI18n();
  const cats = useCategories();
  const qc = useQueryClient();
  const toast = useToast();
  const { select } = useProject();
  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);
  const { register, trigger, getValues, formState: { errors } } = useForm<F>({
    resolver: zodResolver(schema), mode: 'onTouched',
    defaultValues: { propertyType: 'APARTMENT', rooms: 2, materialsIncluded: 'true', paymentMethod: 'BANK_TRANSFER', urgency: 'NORMAL', budgetMin: 0 } as Partial<F>,
  });

  const next = async () => { if (await trigger(STEPS[step])) setStep((s) => s + 1); };
  const finish = async (submit: boolean) => {
    if (!(await trigger())) { setStep(0); return; }
    const v = schema.parse(getValues());
    setSaving(true);
    try {
      const p = await api.post<Project>('/api/v1/projects', {
        name: v.name, propertyType: v.propertyType, rooms: v.rooms, areaM2: v.areaM2, address: v.address, description: v.description, category: v.category,
        budgetMin: v.budgetMin, budgetMax: v.budgetMax, materialsIncluded: v.materialsIncluded === 'true', urgency: v.urgency, startDate: v.startDate || undefined, endDate: v.endDate || undefined,
      });
      if (submit) await api.post(`/api/v1/projects/${p.id}/submit`);
      await qc.invalidateQueries({ queryKey: ['projects'] });
      select(p.id);
      toast(t(submit ? 'wizard.submitted' : 'wizard.saved'), 'success');
      onClose();
    } catch (e) { toast(e instanceof ApiError ? errorText(e, t) : t('common.error'), 'error'); } finally { setSaving(false); }
  };
  const v = getValues();
  const catName = cats.data?.find((c) => c.code === v.category)?.[lang];
  return (
    <DialogContent title={t('wizard.title')} wide>
      <div className="mb-4"><div className="mb-1 flex justify-between text-xs text-ink-soft"><span>{t('wizard.step', { n: step + 1, total: 5 })}</span><span>{t(`wizard.s${step}`)}</span></div><Progress value={((step + 1) / 5) * 100} /></div>
      <form onSubmit={(e) => e.preventDefault()} noValidate className="grid gap-4 sm:grid-cols-2">
        {step === 0 && (<>
          <div className="sm:col-span-2"><Field label={t('wizard.name')} error={errors.name?.message}><Input {...register('name')} placeholder={t('wizard.namePh')} /></Field></div>
          <Field label={t('wizard.propertyType')}><Select {...register('propertyType')}><option value="APARTMENT">{t('wizard.apartment')}</option><option value="HOUSE">{t('wizard.house')}</option></Select></Field>
          <Field label={t('wizard.rooms')} error={errors.rooms?.message}><Input type="number" min={0} {...register('rooms')} /></Field>
          <Field label={t('wizard.area')} error={errors.areaM2?.message}><Input type="number" step="0.1" {...register('areaM2')} /></Field>
          <Field label={t('wizard.address')} error={errors.address?.message}><Input {...register('address')} autoComplete="street-address" /></Field>
        </>)}
        {step === 1 && (<>
          <div className="sm:col-span-2"><Field label={t('wizard.category')} error={errors.category?.message}><Select {...register('category')} defaultValue=""><option value="" disabled>—</option>{cats.data?.map((c) => <option key={c.code} value={c.code}>{c[lang]}</option>)}</Select></Field></div>
          <div className="sm:col-span-2"><Field label={t('wizard.description')} error={errors.description?.message} hint={t('wizard.attachHint')}><Textarea rows={5} {...register('description')} /></Field></div>
        </>)}
        {step === 2 && (<>
          <Field label={t('wizard.budgetMin')} error={errors.budgetMin?.message}><Input type="number" min={0} step="50000" {...register('budgetMin')} /></Field>
          <Field label={t('wizard.budgetMax')} error={errors.budgetMax?.message}><Input type="number" min={0} step="50000" {...register('budgetMax')} /></Field>
          <Field label={t('wizard.materials')}><Select {...register('materialsIncluded')}><option value="true">{t('wizard.matIn')}</option><option value="false">{t('wizard.matOut')}</option></Select></Field>
          <Field label={t('wizard.payMethod')}><Select {...register('paymentMethod')}>{(['BANK_TRANSFER', 'CARD', 'KASPI', 'CASH'] as const).map((m) => <option key={m} value={m}>{t(`method.${m}`)}</option>)}</Select></Field>
        </>)}
        {step === 3 && (<>
          <Field label={t('wizard.start')}><Input type="date" {...register('startDate')} /></Field>
          <Field label={t('wizard.end')} error={errors.endDate?.message}><Input type="date" {...register('endDate')} /></Field>
          <Field label={t('wizard.urgency')}><Select {...register('urgency')}>{(['LOW', 'NORMAL', 'HIGH'] as const).map((u) => <option key={u} value={u}>{t(`urgency.${u}`)}</option>)}</Select></Field>
        </>)}
        {step === 4 && (
          <dl className="sm:col-span-2 grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            {[[t('wizard.name'), v.name], [t('wizard.propertyType'), `${t(v.propertyType === 'HOUSE' ? 'wizard.house' : 'wizard.apartment')}, ${v.rooms} · ${v.areaM2} m²`], [t('wizard.address'), v.address], [t('wizard.category'), catName],
              [t('wizard.description'), v.description], [t('wizard.budget'), `${Number(v.budgetMin).toLocaleString('ru-RU')} – ${Number(v.budgetMax).toLocaleString('ru-RU')} ₸`],
              [t('wizard.materials'), t(v.materialsIncluded === 'true' ? 'wizard.matIn' : 'wizard.matOut')], [t('wizard.payMethod'), t(`method.${v.paymentMethod}`)], [t('wizard.timeline'), `${v.startDate || '—'} → ${v.endDate || '—'}`]]
              .map(([k, val]) => <div key={k as string} className="contents"><dt className="text-ink-soft">{k}</dt><dd className="font-medium">{val}</dd></div>)}
          </dl>
        )}
      </form>
      <div className="mt-6 flex flex-wrap justify-between gap-2">
        <Button variant="ghost" onClick={() => (step === 0 ? onClose() : setStep(step - 1))}>{step === 0 ? t('common.cancel') : t('common.back')}</Button>
        {step < 4 ? <Button onClick={next}>{t('common.next')}</Button> : (
          <div className="flex gap-2"><Button variant="outline" loading={saving} onClick={() => finish(false)}>{t('wizard.saveDraft')}</Button><Button loading={saving} onClick={() => finish(true)}>{t('wizard.submit')}</Button></div>
        )}
      </div>
    </DialogContent>
  );
}
