'use client';
import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Logo } from '@/components/Logo';
import { Button, Field, Input, Select } from '@/components/ui';
import { ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useI18n } from '@/lib/i18n';
import { errorText } from '@/lib/hooks';
import { cn } from '@/lib/utils';
import { phoneSchema } from '@/lib/validation';

export default function LoginPage() {
  const { t, lang, setLang } = useI18n();
  const { user, login, register: signUp } = useAuth();
  const router = useRouter();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { if (user) router.replace('/dashboard'); }, [user, router]);

  const schema = z.object({
    phone: phoneSchema, password: z.string().min(mode === 'register' ? 8 : 1, t('val.password')),
    fullName: z.string().optional(), role: z.enum(['CUSTOMER', 'MASTER', 'CONTRACTOR', 'COMPANY_ADMIN']).optional(), terms: z.boolean().optional(),
  }).superRefine((v, ctx) => {
    if (mode === 'register') {
      if (!v.fullName || v.fullName.trim().length < 2) ctx.addIssue({ code: 'custom', path: ['fullName'], message: t('val.required') });
      if (!v.terms) ctx.addIssue({ code: 'custom', path: ['terms'], message: t('val.terms') });
    }
  });
  type F = z.infer<typeof schema>;
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<F>({ resolver: zodResolver(schema), defaultValues: { role: 'CUSTOMER', phone: '+7' } });

  const submit = handleSubmit(async (v) => {
    setError(null);
    try {
      if (mode === 'login') await login(v.phone, v.password);
      else await signUp({ phone: v.phone, password: v.password, fullName: v.fullName!.trim(), role: v.role ?? 'CUSTOMER' });
      router.replace('/dashboard');
    } catch (e) { setError(e instanceof ApiError ? errorText(e, t) : t('common.error')); }
  });

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <div className="hidden flex-col justify-between bg-navy p-12 text-white lg:flex">
        <Logo light size={36} />
        <div>
          <h1 className="text-4xl font-extrabold leading-tight">{t('brand.slogan')}</h1>
          <p className="mt-3 max-w-md text-lg text-white/75">{t('brand.tagline')}</p>
          <ul className="mt-8 space-y-2 text-white/85">{['login.p1', 'login.p2', 'login.p3'].map((k) => <li key={k} className="flex gap-2"><span className="text-accent">●</span>{t(k)}</li>)}</ul>
        </div>
        <p className="text-sm text-white/50">Almaty · KZT · kk / ru / en</p>
      </div>
      <div className="flex items-center justify-center p-6">
        <form onSubmit={submit} className="w-full max-w-sm space-y-4" noValidate>
          <div className="lg:hidden"><Logo /></div>
          <div className="flex justify-end gap-1 text-xs font-semibold">{(['kk', 'ru', 'en'] as const).map((l) => <button type="button" key={l} onClick={() => setLang(l)} className={cn('rounded px-2 py-1 uppercase', lang === l ? 'bg-navy text-white' : 'text-ink-soft')}>{l}</button>)}</div>
          <h2 className="text-2xl font-bold">{mode === 'login' ? t('auth.login') : t('auth.register')}</h2>
          {mode === 'register' && (
            <>
              <Field label={t('auth.fullName')} error={errors.fullName?.message}><Input {...register('fullName')} autoComplete="name" /></Field>
              <Field label={t('auth.role')}><Select {...register('role')}>
                <option value="CUSTOMER">{t('role.CUSTOMER')}</option><option value="CONTRACTOR">{t('role.CONTRACTOR')}</option>
                <option value="MASTER">{t('role.MASTER')}</option><option value="COMPANY_ADMIN">{t('role.COMPANY_ADMIN')}</option></Select></Field>
            </>
          )}
          <Field label={t('auth.phone')} error={errors.phone?.message}><Input {...register('phone')} type="tel" inputMode="tel" autoComplete="tel" placeholder="+7 701 123 45 67" /></Field>
          <Field label={t('auth.password')} error={errors.password?.message}><Input {...register('password')} type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></Field>
          {mode === 'register' && (
            <label className="flex items-start gap-2 text-xs text-ink-soft"><input type="checkbox" {...register('terms')} className="mt-0.5" /><span>{t('auth.terms')}{errors.terms && <span className="block text-danger">{errors.terms.message}</span>}</span></label>
          )}
          {error && <p role="alert" className="rounded-control bg-danger-50 p-3 text-sm text-danger">{error}</p>}
          <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>{mode === 'login' ? t('auth.login') : t('auth.register')}</Button>
          <button type="button" className="w-full text-center text-sm text-brand" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(null); }}>
            {mode === 'login' ? t('auth.noAccount') : t('auth.haveAccount')}
          </button>
          <p className="rounded-control bg-accent-50 p-3 text-xs text-accent-600">{t('auth.demoHint')}</p>
        </form>
      </div>
    </div>
  );
}
